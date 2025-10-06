# ThesisApp Kubernetes Deployment

This directory contains Kubernetes manifests to deploy ThesisApp to any Kubernetes cluster.

## Prerequisites

1. **Kubernetes Cluster** (choose one):
   - AWS EKS: `eksctl create cluster --name thesisapp --region us-east-1`
   - Google GKE: `gcloud container clusters create thesisapp --zone us-central1-a`
   - Azure AKS: `az aks create --resource-group thesisapp-rg --name thesisapp`
   - Local: Minikube, k3s, or Docker Desktop

2. **kubectl** installed and configured
3. **Container Registry**:
   - Docker Hub
   - AWS ECR
   - Google GCR
   - Azure ACR

## Quick Start

### 1. Build and Push Docker Images

```bash
# Backend
cd backend
docker build -t <YOUR_REGISTRY>/thesis-backend:latest .
docker push <YOUR_REGISTRY>/thesis-backend:latest

# Frontend
cd frontend
docker build -t <YOUR_REGISTRY>/thesis-frontend:latest .
docker push <YOUR_REGISTRY>/thesis-frontend:latest
```

### 2. Update Image References

Edit `kubernetes/base/backend-deployment.yaml` and `frontend-deployment.yaml`:
```yaml
image: <YOUR_REGISTRY>/thesis-backend:latest  # Replace with your registry
```

### 3. Create Secrets

```bash
# PostgreSQL credentials
kubectl create secret generic postgres-secret \
  --from-literal=username=postgres \
  --from-literal=password=YOUR_SECURE_PASSWORD \
  -n thesisapp

# MinIO credentials
kubectl create secret generic minio-secret \
  --from-literal=access-key=minioadmin \
  --from-literal=secret-key=YOUR_SECURE_SECRET \
  -n thesisapp

# Email credentials (optional)
kubectl create secret generic email-secret \
  --from-literal=username=your-email@example.com \
  --from-literal=password=YOUR_EMAIL_PASSWORD \
  -n thesisapp
```

### 4. Deploy to Kubernetes

```bash
# Apply all manifests
kubectl apply -k kubernetes/base/

# Check deployment status
kubectl get pods -n thesisapp
kubectl get svc -n thesisapp

# Watch rollout
kubectl rollout status deployment/backend -n thesisapp
kubectl rollout status deployment/frontend -n thesisapp
```

### 5. Access the Application

```bash
# Port-forward for local testing
kubectl port-forward -n thesisapp svc/frontend 5173:5173
kubectl port-forward -n thesisapp svc/backend 8080:8080

# Open browser
open http://localhost:5173
```

## Storage Classes

Different cloud providers use different storage classes for ReadWriteMany (required for shared ML storage):

### AWS EKS - Use EFS

```bash
# Install EFS CSI driver
kubectl apply -k "github.com/kubernetes-sigs/aws-efs-csi-driver/deploy/kubernetes/overlays/stable/?ref=release-1.5"

# Create EFS filesystem in AWS console or CLI
# Update backend-deployment.yaml:
storageClassName: efs-sc
```

### Google GKE - Use Filestore

```bash
# Filestore CSI driver is pre-installed
# Update backend-deployment.yaml:
storageClassName: filestore-sc
```

### Azure AKS - Use Azure Files

```bash
# Azure Files CSI driver is pre-installed
# Update backend-deployment.yaml:
storageClassName: azure-file
```

## Handling Docker-in-Docker (ML Training)

Your backend spawns Docker containers for ML training. In Kubernetes, replace this with **Kubernetes Jobs**.

### Option 1: Kubernetes Jobs (Recommended)

Update `DockerContainerRunner.java` to create Kubernetes Jobs instead of Docker containers:

```java
@Service
public class KubernetesJobRunner {
    private final KubernetesClient kubernetesClient;

    public void runTrainingJob(String imageName, String taskId, Path dataDir, Path outputDir) {
        String namespace = System.getenv("K8S_NAMESPACE");

        Job job = new JobBuilder()
            .withNewMetadata()
                .withName("training-" + taskId)
                .withNamespace(namespace)
            .endMetadata()
            .withNewSpec()
                .withBackoffLimit(0)
                .withNewTemplate()
                    .withNewSpec()
                        .addNewContainer()
                            .withName("trainer")
                            .withImage(imageName)
                            .withEnv(
                                new EnvVar("DATA_DIR", "/shared/" + dataDir.getFileName(), null),
                                new EnvVar("MODEL_DIR", "/shared/" + outputDir.getFileName(), null)
                            )
                            .withVolumeMounts(new VolumeMount("/shared", "shared-storage", false, null, null))
                        .endContainer()
                        .addNewVolume()
                            .withName("shared-storage")
                            .withNewPersistentVolumeClaim("shared-pvc", false)
                        .endVolume()
                        .withRestartPolicy("Never")
                    .endSpec()
                .endTemplate()
            .endSpec()
            .build();

        kubernetesClient.batch().v1().jobs()
            .inNamespace(namespace)
            .create(job);
    }
}
```

Add dependency to `pom.xml`:
```xml
<dependency>
    <groupId>io.fabric8</groupId>
    <artifactId>kubernetes-client</artifactId>
    <version>6.9.2</version>
</dependency>
```

## Monitoring

### Install Prometheus & Grafana

```bash
# Add Helm repo
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
helm repo update

# Install kube-prometheus-stack
helm install monitoring prometheus-community/kube-prometheus-stack \
  --namespace monitoring \
  --create-namespace

# Access Grafana
kubectl port-forward -n monitoring svc/monitoring-grafana 3000:80
# Username: admin, Password: prom-operator
```

### View Logs

```bash
# Real-time logs
kubectl logs -f deployment/backend -n thesisapp
kubectl logs -f deployment/frontend -n thesisapp

# Previous logs (if pod crashed)
kubectl logs deployment/backend -n thesisapp --previous
```

## Scaling

```bash
# Manual scaling
kubectl scale deployment backend --replicas=5 -n thesisapp

# Autoscaling
kubectl autoscale deployment backend \
  --cpu-percent=80 \
  --min=2 \
  --max=10 \
  -n thesisapp
```

## Updating the Application

```bash
# Build new image
docker build -t <YOUR_REGISTRY>/thesis-backend:v2 .
docker push <YOUR_REGISTRY>/thesis-backend:v2

# Update deployment
kubectl set image deployment/backend \
  backend=<YOUR_REGISTRY>/thesis-backend:v2 \
  -n thesisapp

# Or edit the deployment YAML and apply
kubectl apply -k kubernetes/base/
```

## Cleanup

```bash
# Delete all resources
kubectl delete -k kubernetes/base/

# Or delete namespace (deletes everything in it)
kubectl delete namespace thesisapp
```

## Production Considerations

1. **Use Helm** for easier management:
   ```bash
   helm install thesisapp ./kubernetes/helm/
   ```

2. **Enable HTTPS** with cert-manager:
   ```bash
   kubectl apply -f https://github.com/cert-manager/cert-manager/releases/download/v1.13.0/cert-manager.yaml
   ```

3. **Backup databases** regularly:
   ```bash
   kubectl exec -n thesisapp postgres-0 -- pg_dump -U postgres thesis_db > backup.sql
   ```

4. **Use managed services** for production:
   - Database: AWS RDS, Cloud SQL, Azure Database
   - Object Storage: AWS S3, GCS, Azure Blob (instead of MinIO)
   - Email: SendGrid, AWS SES, Mailgun (instead of MailHog)

## Troubleshooting

```bash
# Check pod status
kubectl get pods -n thesisapp

# Describe pod (shows events)
kubectl describe pod <pod-name> -n thesisapp

# Get logs
kubectl logs <pod-name> -n thesisapp

# Execute into pod
kubectl exec -it <pod-name> -n thesisapp -- /bin/bash

# Check persistent volumes
kubectl get pv,pvc -n thesisapp
```

## Cost Optimization

- Use **node autoscaling** to scale down during off-hours
- Use **spot instances** for non-critical workloads
- Implement **pod disruption budgets** for high availability
- Set **resource requests and limits** accurately
