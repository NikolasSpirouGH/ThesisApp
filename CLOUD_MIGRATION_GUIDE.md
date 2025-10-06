# Cloud Migration Guide for ThesisApp

## Overview
This guide provides a step-by-step approach to migrate ThesisApp from local Docker Compose to cloud infrastructure.

---

## Phase 1: Application Preparation (1-2 weeks)

### 1.1 Externalize Configuration
**Current Issue**: Hardcoded configurations in docker-compose.yml

**Solution**: Use environment variables and cloud-native config management

```java
// backend/src/main/resources/application.yml
spring:
  profiles:
    active: ${SPRING_PROFILES_ACTIVE:local}

  datasource:
    url: jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/${DB_NAME:thesis_db}
    username: ${DB_USERNAME:postgres}
    password: ${DB_PASSWORD:password}

  cloud:
    aws:
      credentials:
        access-key: ${AWS_ACCESS_KEY}
        secret-key: ${AWS_SECRET_KEY}
      region:
        static: ${AWS_REGION:us-east-1}

minio:
  endpoint: ${MINIO_ENDPOINT:http://localhost:9000}
  accessKey: ${MINIO_ACCESS_KEY}
  secretKey: ${MINIO_SECRET_KEY}
```

### 1.2 Replace MinIO with Cloud Object Storage

#### Option A: AWS S3
**Dependencies** (add to `pom.xml`):
```xml
<dependency>
    <groupId>software.amazon.awssdk</groupId>
    <artifactId>s3</artifactId>
    <version>2.20.0</version>
</dependency>
```

**Create S3 Service Adapter**:
```java
// backend/src/main/java/com/cloud_ml_app_thesis/service/S3Service.java
@Service
@Profile("cloud")
public class S3Service implements ObjectStorageService {
    private final S3Client s3Client;

    public void uploadFile(String bucket, String key, InputStream data, long size) {
        s3Client.putObject(
            PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build(),
            RequestBody.fromInputStream(data, size)
        );
    }

    // Implement download, delete, etc.
}
```

#### Option B: Keep MinIO (Cloud-Agnostic)
Deploy MinIO as a StatefulSet in Kubernetes or use MinIO Gateway mode for S3.

### 1.3 Database Migration Strategy

**Step 1: Backup current data**
```bash
docker exec thesis_postgres pg_dump -U postgres thesis_db > backup.sql
```

**Step 2: Create managed database**
- AWS RDS PostgreSQL
- Google Cloud SQL
- Azure Database for PostgreSQL

**Step 3: Restore data**
```bash
psql -h <cloud-db-endpoint> -U <username> -d thesis_db < backup.sql
```

### 1.4 Handle Docker-in-Docker for ML Workloads

**Current Challenge**: Backend spawns Docker containers for custom algorithms

**Cloud Solutions**:

#### Option A: AWS ECS Fargate RunTask
```java
@Service
public class CloudMLExecutor {
    private final EcsClient ecsClient;

    public void runTraining(String algorithmImage, Map<String, String> env) {
        RunTaskRequest request = RunTaskRequest.builder()
            .cluster("ml-cluster")
            .taskDefinition("custom-training-task")
            .launchType(LaunchType.FARGATE)
            .overrides(TaskOverride.builder()
                .containerOverrides(ContainerOverride.builder()
                    .name("training-container")
                    .image(algorithmImage)
                    .environment(env)
                    .build())
                .build())
            .build();

        ecsClient.runTask(request);
    }
}
```

#### Option B: Kubernetes Jobs
```yaml
apiVersion: batch/v1
kind: Job
metadata:
  name: custom-training-{{ taskId }}
spec:
  template:
    spec:
      containers:
      - name: trainer
        image: {{ algorithmImage }}
        env:
        - name: DATA_DIR
          value: /shared/training-ds-{{ taskId }}
        volumeMounts:
        - name: shared-storage
          mountPath: /shared
      volumes:
      - name: shared-storage
        persistentVolumeClaim:
          claimName: ml-shared-pvc
      restartPolicy: Never
```

---

## Phase 2: Infrastructure Setup (2-3 weeks)

### 2.1 AWS Infrastructure with Terraform

**File Structure**:
```
terraform/
├── main.tf
├── variables.tf
├── outputs.tf
├── modules/
│   ├── vpc/
│   ├── rds/
│   ├── s3/
│   ├── ecs/
│   └── alb/
```

**terraform/main.tf**:
```hcl
terraform {
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
  backend "s3" {
    bucket = "thesisapp-terraform-state"
    key    = "prod/terraform.tfstate"
    region = "us-east-1"
  }
}

provider "aws" {
  region = var.aws_region
}

# VPC
module "vpc" {
  source = "./modules/vpc"

  vpc_cidr = "10.0.0.0/16"
  azs      = ["us-east-1a", "us-east-1b"]

  private_subnets = ["10.0.1.0/24", "10.0.2.0/24"]
  public_subnets  = ["10.0.101.0/24", "10.0.102.0/24"]

  enable_nat_gateway = true
}

# RDS PostgreSQL
module "rds" {
  source = "./modules/rds"

  identifier     = "thesisapp-postgres"
  engine_version = "15.4"
  instance_class = "db.t3.medium"

  database_name = "thesis_db"
  username      = var.db_username
  password      = var.db_password

  vpc_id     = module.vpc.vpc_id
  subnet_ids = module.vpc.private_subnets

  backup_retention_period = 7
  multi_az               = true
}

# S3 Buckets
module "s3" {
  source = "./modules/s3"

  buckets = [
    "thesisapp-datasets",
    "thesisapp-algorithms",
    "thesisapp-models",
    "thesisapp-predictions"
  ]

  enable_versioning = true
  enable_encryption = true
}

# ECS Cluster
module "ecs" {
  source = "./modules/ecs"

  cluster_name = "thesisapp-cluster"

  services = {
    backend = {
      image          = "${var.ecr_backend_repo}:latest"
      cpu            = 1024
      memory         = 2048
      desired_count  = 2
      container_port = 8080
    }
    frontend = {
      image          = "${var.ecr_frontend_repo}:latest"
      cpu            = 512
      memory         = 1024
      desired_count  = 2
      container_port = 5173
    }
  }

  vpc_id         = module.vpc.vpc_id
  private_subnets = module.vpc.private_subnets
}

# Application Load Balancer
module "alb" {
  source = "./modules/alb"

  name    = "thesisapp-alb"
  vpc_id  = module.vpc.vpc_id
  subnets = module.vpc.public_subnets

  target_groups = {
    backend  = { port = 8080, health_check_path = "/actuator/health" }
    frontend = { port = 5173, health_check_path = "/" }
  }

  certificate_arn = var.ssl_certificate_arn
}
```

### 2.2 Kubernetes Deployment Files

**kubernetes/backend-deployment.yaml**:
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: thesis-backend
  namespace: thesisapp
spec:
  replicas: 3
  selector:
    matchLabels:
      app: backend
  template:
    metadata:
      labels:
        app: backend
    spec:
      containers:
      - name: backend
        image: <YOUR_REGISTRY>/thesis-backend:latest
        ports:
        - containerPort: 8080
        env:
        - name: SPRING_PROFILES_ACTIVE
          value: "cloud"
        - name: DB_HOST
          valueFrom:
            secretKeyRef:
              name: db-credentials
              key: host
        - name: DB_USERNAME
          valueFrom:
            secretKeyRef:
              name: db-credentials
              key: username
        - name: DB_PASSWORD
          valueFrom:
            secretKeyRef:
              name: db-credentials
              key: password
        - name: AWS_ACCESS_KEY
          valueFrom:
            secretKeyRef:
              name: aws-credentials
              key: access-key
        - name: AWS_SECRET_KEY
          valueFrom:
            secretKeyRef:
              name: aws-credentials
              key: secret-key
        resources:
          requests:
            memory: "2Gi"
            cpu: "1000m"
          limits:
            memory: "4Gi"
            cpu: "2000m"
        livenessProbe:
          httpGet:
            path: /actuator/health
            port: 8080
          initialDelaySeconds: 120
          periodSeconds: 10
        readinessProbe:
          httpGet:
            path: /actuator/health
            port: 8080
          initialDelaySeconds: 30
          periodSeconds: 5
---
apiVersion: v1
kind: Service
metadata:
  name: backend-service
  namespace: thesisapp
spec:
  selector:
    app: backend
  ports:
  - protocol: TCP
    port: 80
    targetPort: 8080
  type: LoadBalancer
```

**kubernetes/secrets.yaml** (use kubectl create secret or sealed-secrets):
```yaml
apiVersion: v1
kind: Secret
metadata:
  name: db-credentials
  namespace: thesisapp
type: Opaque
data:
  host: <base64-encoded-host>
  username: <base64-encoded-username>
  password: <base64-encoded-password>
---
apiVersion: v1
kind: Secret
metadata:
  name: aws-credentials
  namespace: thesisapp
type: Opaque
data:
  access-key: <base64-encoded-key>
  secret-key: <base64-encoded-secret>
```

---

## Phase 3: CI/CD Pipeline (1 week)

### 3.1 GitHub Actions Workflow

**.github/workflows/deploy.yml**:
```yaml
name: Build and Deploy to Cloud

on:
  push:
    branches: [main]
  pull_request:
    branches: [main]

env:
  AWS_REGION: us-east-1
  ECR_BACKEND_REPO: thesis-backend
  ECR_FRONTEND_REPO: thesis-frontend

jobs:
  build-backend:
    runs-on: ubuntu-latest
    steps:
    - uses: actions/checkout@v3

    - name: Set up JDK 21
      uses: actions/setup-java@v3
      with:
        java-version: '21'
        distribution: 'temurin'

    - name: Build with Maven
      run: |
        cd backend
        ./mvnw clean package -DskipTests

    - name: Run Tests
      run: |
        cd backend
        ./mvnw test

    - name: Configure AWS credentials
      uses: aws-actions/configure-aws-credentials@v2
      with:
        aws-access-key-id: ${{ secrets.AWS_ACCESS_KEY_ID }}
        aws-secret-access-key: ${{ secrets.AWS_SECRET_ACCESS_KEY }}
        aws-region: ${{ env.AWS_REGION }}

    - name: Login to Amazon ECR
      id: login-ecr
      uses: aws-actions/amazon-ecr-login@v1

    - name: Build and push Docker image
      env:
        ECR_REGISTRY: ${{ steps.login-ecr.outputs.registry }}
        IMAGE_TAG: ${{ github.sha }}
      run: |
        cd backend
        docker build -t $ECR_REGISTRY/$ECR_BACKEND_REPO:$IMAGE_TAG .
        docker push $ECR_REGISTRY/$ECR_BACKEND_REPO:$IMAGE_TAG
        docker tag $ECR_REGISTRY/$ECR_BACKEND_REPO:$IMAGE_TAG $ECR_REGISTRY/$ECR_BACKEND_REPO:latest
        docker push $ECR_REGISTRY/$ECR_BACKEND_REPO:latest

  build-frontend:
    runs-on: ubuntu-latest
    steps:
    - uses: actions/checkout@v3

    - name: Setup Node.js
      uses: actions/setup-node@v3
      with:
        node-version: '18'

    - name: Install dependencies
      run: |
        cd frontend
        npm ci

    - name: Build
      run: |
        cd frontend
        npm run build

    - name: Configure AWS credentials
      uses: aws-actions/configure-aws-credentials@v2
      with:
        aws-access-key-id: ${{ secrets.AWS_ACCESS_KEY_ID }}
        aws-secret-access-key: ${{ secrets.AWS_SECRET_ACCESS_KEY }}
        aws-region: ${{ env.AWS_REGION }}

    - name: Login to Amazon ECR
      id: login-ecr
      uses: aws-actions/amazon-ecr-login@v1

    - name: Build and push Docker image
      env:
        ECR_REGISTRY: ${{ steps.login-ecr.outputs.registry }}
        IMAGE_TAG: ${{ github.sha }}
      run: |
        cd frontend
        docker build -t $ECR_REGISTRY/$ECR_FRONTEND_REPO:$IMAGE_TAG .
        docker push $ECR_REGISTRY/$ECR_FRONTEND_REPO:$IMAGE_TAG
        docker tag $ECR_REGISTRY/$ECR_FRONTEND_REPO:$IMAGE_TAG $ECR_REGISTRY/$ECR_FRONTEND_REPO:latest
        docker push $ECR_REGISTRY/$ECR_FRONTEND_REPO:latest

  deploy:
    needs: [build-backend, build-frontend]
    runs-on: ubuntu-latest
    if: github.ref == 'refs/heads/main'
    steps:
    - name: Configure AWS credentials
      uses: aws-actions/configure-aws-credentials@v2
      with:
        aws-access-key-id: ${{ secrets.AWS_ACCESS_KEY_ID }}
        aws-secret-access-key: ${{ secrets.AWS_SECRET_ACCESS_KEY }}
        aws-region: ${{ env.AWS_REGION }}

    - name: Update ECS service
      run: |
        aws ecs update-service \
          --cluster thesisapp-cluster \
          --service backend-service \
          --force-new-deployment

        aws ecs update-service \
          --cluster thesisapp-cluster \
          --service frontend-service \
          --force-new-deployment
```

---

## Phase 4: Monitoring & Logging (1 week)

### 4.1 AWS CloudWatch Integration

**Add dependencies**:
```xml
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-cloudwatch2</artifactId>
</dependency>
```

**Configuration**:
```yaml
# application-cloud.yml
management:
  metrics:
    export:
      cloudwatch:
        namespace: ThesisApp
        enabled: true

logging:
  level:
    com.cloud_ml_app_thesis: INFO
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss} - %msg%n"

cloud:
  aws:
    cloudwatch:
      enabled: true
```

### 4.2 Application Performance Monitoring

**Option 1: AWS X-Ray** (Distributed Tracing)
```xml
<dependency>
    <groupId>com.amazonaws</groupId>
    <artifactId>aws-xray-recorder-sdk-spring</artifactId>
</dependency>
```

**Option 2: Prometheus + Grafana**
```yaml
# kubernetes/monitoring/prometheus-config.yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: prometheus-config
data:
  prometheus.yml: |
    global:
      scrape_interval: 15s

    scrape_configs:
    - job_name: 'thesis-backend'
      kubernetes_sd_configs:
      - role: pod
      relabel_configs:
      - source_labels: [__meta_kubernetes_pod_label_app]
        regex: backend
        action: keep
```

---

## Phase 5: Security Hardening (Ongoing)

### 5.1 Secrets Management

**AWS Secrets Manager**:
```java
@Configuration
public class SecretsConfig {

    @Bean
    public DataSource dataSource(
        @Value("${aws.secretsmanager.secret-name}") String secretName) {

        SecretsManagerClient client = SecretsManagerClient.create();
        GetSecretValueResponse response = client.getSecretValue(
            r -> r.secretId(secretName)
        );

        ObjectMapper mapper = new ObjectMapper();
        DatabaseCredentials creds = mapper.readValue(
            response.secretString(),
            DatabaseCredentials.class
        );

        return DataSourceBuilder.create()
            .url(creds.getUrl())
            .username(creds.getUsername())
            .password(creds.getPassword())
            .build();
    }
}
```

### 5.2 Network Security

**terraform/modules/security-groups/main.tf**:
```hcl
resource "aws_security_group" "backend" {
  name        = "thesisapp-backend-sg"
  description = "Security group for backend service"
  vpc_id      = var.vpc_id

  ingress {
    description     = "Allow from ALB"
    from_port       = 8080
    to_port         = 8080
    protocol        = "tcp"
    security_groups = [aws_security_group.alb.id]
  }

  egress {
    description = "Allow all outbound"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

resource "aws_security_group" "rds" {
  name        = "thesisapp-rds-sg"
  description = "Security group for RDS database"
  vpc_id      = var.vpc_id

  ingress {
    description     = "PostgreSQL from backend"
    from_port       = 5432
    to_port         = 5432
    protocol        = "tcp"
    security_groups = [aws_security_group.backend.id]
  }
}
```

---

## Cost Estimation

### AWS Pricing (Monthly, US-East-1)

| Service | Configuration | Cost |
|---------|--------------|------|
| RDS PostgreSQL | db.t3.medium, Multi-AZ | ~$120 |
| ECS Fargate | 2 vCPU, 4GB RAM, 2 tasks | ~$150 |
| S3 Storage | 100 GB + transfers | ~$30 |
| Application Load Balancer | 1 ALB | ~$20 |
| CloudWatch Logs | 10 GB | ~$5 |
| **Total** | | **~$325/month** |

### Cost Optimization Tips:
1. Use **Reserved Instances** for RDS (40-60% savings)
2. Enable **S3 Intelligent Tiering**
3. Use **Spot Instances** for ML training workloads
4. Implement **auto-scaling** to scale down during off-hours

---

## Migration Checklist

- [ ] Phase 1: Application Preparation
  - [ ] Externalize all configuration
  - [ ] Implement cloud storage adapter (S3/GCS)
  - [ ] Update email service (SES/SendGrid)
  - [ ] Modify Docker-in-Docker to use cloud container services

- [ ] Phase 2: Infrastructure
  - [ ] Set up cloud account and billing alerts
  - [ ] Provision managed database
  - [ ] Create object storage buckets
  - [ ] Set up container registry
  - [ ] Deploy orchestration platform (ECS/GKE/AKS)

- [ ] Phase 3: CI/CD
  - [ ] Create automated build pipeline
  - [ ] Set up deployment automation
  - [ ] Implement blue-green or canary deployments

- [ ] Phase 4: Monitoring
  - [ ] Configure application metrics
  - [ ] Set up logging aggregation
  - [ ] Create alerting rules
  - [ ] Set up dashboards

- [ ] Phase 5: Security
  - [ ] Enable HTTPS/TLS
  - [ ] Implement secrets management
  - [ ] Configure network security groups
  - [ ] Set up IAM roles and policies
  - [ ] Enable audit logging

---

## Next Steps

1. **Choose your cloud provider** (AWS, GCP, or Azure)
2. **Start with a staging environment** (don't migrate production immediately)
3. **Test thoroughly** in the cloud environment
4. **Plan data migration** during low-traffic period
5. **Monitor closely** after migration

For detailed implementation, refer to the specific cloud provider documentation.
