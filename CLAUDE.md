# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

ThesisApp is a cloud-based machine learning platform that allows users to upload datasets, train ML models (both predefined Weka algorithms and custom Docker-based algorithms), and execute predictions. The application consists of a Spring Boot backend (Java 21), TypeScript frontend (Vite), and uses PostgreSQL for data, MinIO for object storage, and MailHog for email testing.

## Architecture

### Backend (Spring Boot 3.4.2, Java 21)
- **Main Entry**: `backend/src/main/java/com/cloud_ml_app_thesis/CloudMlAppThesis.java`
- **Package**: `com.cloud_ml_app_thesis`
- **Authentication**: JWT-based with RSA key signing (keys in `backend/src/main/resources/keys/`)
- **Database**: PostgreSQL with Flyway migrations (`backend/src/main/resources/db/migration/`)
- **Object Storage**: MinIO for datasets, algorithms, models, predictions, metrics, and parameters
- **ML Training**:
  - **Weka Algorithms**: Built-in support for predefined ML algorithms via Weka library
  - **Custom Algorithms**: User-provided Docker images that train models (uses Docker-in-Docker via `/var/run/docker.sock`)
- **Docker Integration**: Backend spawns Docker containers for custom ML training via `DockerContainerRunner` utility
- **Async Tasks**: Training and prediction operations are asynchronous, tracked via `AsyncTaskStatus` entity
- **Shared Volume**: Training containers use a shared volume (`/app/shared` in Docker) for data exchange between host and containers

### Frontend (TypeScript + Vite)
- **Framework**: Vanilla TypeScript with Vite for bundling
- **Structure**: Feature-based organization under `frontend/src/features/`
  - `auth/` - Login, registration, password reset
  - `datasets/` - Dataset upload, configuration, sharing
  - `algorithms/` - Custom algorithm management
  - `trainings/` - Training workflows (Weka and custom)
  - `executions/` - Prediction execution
  - `models/` - Trained model management
  - `results/` - Training/prediction results
  - `categories/` - Category management
  - `tasks/` - Task status monitoring
- **Routing**: Custom router in `frontend/src/app/router.ts`
- **API Client**: Centralized HTTP client in `frontend/src/core/http.ts`

### Key Services (Backend)

- **CustomTrainingService**: Orchestrates custom algorithm training via Docker containers
- **CustomPredictionService**: Executes predictions using custom trained models
- **TrainService**: Handles Weka-based ML training
- **ModelExecutionService**: Manages model execution workflows
- **MinioService**: All object storage operations (upload/download from MinIO)
- **DatasetService**: Dataset CRUD and configuration
- **AlgorithmService**: Algorithm management (predefined and custom)
- **ModelService**: Model persistence and retrieval
- **TaskStatusService**: Async task tracking and status updates
- **UserService**: User management and authentication

### Docker-in-Docker Pattern

The backend runs in Docker and spawns Docker containers for custom ML training:
1. Backend downloads dataset and user's Docker image from MinIO
2. Backend creates temp directories in shared volume (`/app/shared/training-ds-*`, `/app/shared/training-out-*`)
3. Backend runs user's Docker image with mounted volumes: `/data` (input) and `/model` (output)
4. Container trains model, writes model file and `metrics.json` to `/model`
5. Backend uploads results to MinIO and creates database records

**Important**: When migrating to Kubernetes, replace Docker container spawning with Kubernetes Jobs (see `kubernetes/README.md` for details).

## Development Commands

### Local Development (Docker Compose)

```bash
# Start all services (recommended for development)
docker-compose up

# Start specific service
docker-compose up backend
docker-compose up frontend

# View logs
docker-compose logs -f backend
docker-compose logs -f postgres

# Stop all services
docker-compose down

# Rebuild and restart (after code changes)
docker-compose up --build backend
docker-compose up --build frontend
```

**Service URLs**:
- Frontend: http://localhost:5173
- Backend API: http://localhost:8080
- Swagger UI: http://localhost:8080/swagger-ui.html
- MinIO Console: http://localhost:9001
- MailHog UI: http://localhost:8025
- PostgreSQL: localhost:5432

### Backend

```bash
cd backend

# Build (skip tests)
./mvnw clean package -DskipTests

# Build with tests
./mvnw clean package

# Run tests only
./mvnw test

# Run specific test
./mvnw test -Dtest=CustomTrainingServiceTest

# Run Spring Boot locally (without Docker)
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

**Spring Profiles**:
- `local`: Local development without Docker (uses localhost for DB/MinIO)
- `docker`: Runs inside Docker Compose (uses service names for DB/MinIO)
- Active profile set in `application.yaml` or via `SPRING_PROFILES_ACTIVE` env var

### Frontend

```bash
cd frontend

# Install dependencies
npm install

# Run development server
npm run dev

# Build for production
npm run build

# Preview production build
npm run preview
```

### Database

```bash
# Connect to PostgreSQL in Docker
docker exec -it thesis_postgres psql -U postgres -d thesis_db

# Dump database
docker exec thesis_postgres pg_dump -U postgres thesis_db > backup.sql

# Restore database
docker exec -i thesis_postgres psql -U postgres -d thesis_db < backup.sql
```

## Configuration Files

### Backend Configuration
- `backend/src/main/resources/application.yaml` - Base configuration
- `backend/src/main/resources/application-local.yaml` - Local profile overrides
- `backend/src/main/resources/application-docker.yaml` - Docker profile overrides

### Environment Variables (Docker Compose)
All services configured via `docker-compose.yml`. Key environment variables:
- **Database**: `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`, `POSTGRES_HOST`
- **MinIO**: `MINIO_ACCESS_KEY`, `MINIO_SECRET_KEY`, `MINIO_HOST`, `MINIO_PORT`, `MINIO_BUCKET_*`
- **Mail**: `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`
- **Backend**: `SPRING_PROFILES_ACTIVE`, `SERVER_PORT`, `SHARED_VOLUME`, `RUNNING_IN_DOCKER`
- **Frontend**: `VITE_API_URL`

## Deployment

### Kubernetes

Full Kubernetes manifests available in `kubernetes/base/`:
- `namespace.yaml` - Creates `thesisapp` namespace
- `postgres-statefulset.yaml` - PostgreSQL with persistent storage
- `minio-statefulset.yaml` - MinIO object storage
- `backend-deployment.yaml` - Backend Spring Boot app
- `frontend-deployment.yaml` - Frontend Vite app
- `secrets-template.yaml` - Template for creating secrets
- `configmap.yaml` - Configuration values
- `ingress.yaml` - Ingress routing

**Deploy to Kubernetes**:
```bash
# Create secrets first
kubectl create secret generic postgres-secret \
  --from-literal=username=postgres \
  --from-literal=password=YOUR_PASSWORD \
  -n thesisapp

kubectl create secret generic minio-secret \
  --from-literal=access-key=minioadmin \
  --from-literal=secret-key=YOUR_SECRET \
  -n thesisapp

# Apply all manifests
kubectl apply -k kubernetes/base/

# Check status
kubectl get pods -n thesisapp
kubectl logs -f deployment/backend -n thesisapp
```

**Important**: For Kubernetes deployments, the Docker-in-Docker pattern must be replaced with Kubernetes Jobs. See `kubernetes/README.md` lines 122-171 for implementation guidance.

### Cloud Migration

See `CLOUD_MIGRATION_GUIDE.md` for comprehensive cloud deployment guide covering:
- AWS ECS/EKS deployment
- RDS PostgreSQL setup
- S3 object storage (replacing MinIO)
- Container orchestration patterns
- Monitoring and logging setup
- Security hardening

Key migration considerations:
1. Replace MinIO with cloud object storage (S3, GCS, Azure Blob)
2. Replace self-hosted PostgreSQL with managed database (RDS, Cloud SQL, Azure Database)
3. Replace Docker-in-Docker with cloud container services (ECS Fargate, GKE Jobs, AKS Jobs)
4. Externalize all configuration to environment variables/secrets manager
5. Implement CloudWatch/Stackdriver logging

## Key Entities

### Database Schema (JPA Entities)
- **User**: User accounts with roles (ADMIN, USER)
- **Dataset**: User-uploaded datasets with categories and keywords
- **DatasetConfiguration**: Dataset preprocessing configuration (target column, features, etc.)
- **Algorithm**: Predefined Weka algorithms
- **CustomAlgorithm**: User-provided Docker-based algorithms
- **CustomAlgorithmImage**: Docker images for custom algorithms (TAR or DockerHub URL)
- **AlgorithmConfiguration**: Parameter configuration for predefined algorithms
- **CustomAlgorithmConfiguration**: Parameter configuration for custom algorithms
- **Training**: Training execution records (links dataset config + algorithm config + results)
- **Model**: Trained models with MinIO URLs for model file and metrics
- **Execution**: Prediction execution records
- **AsyncTaskStatus**: Async task tracking (training/prediction status, progress, errors)
- **Category**: Dataset categories (user-requested, admin-approved)
- **ModelAccessibility**: Model sharing permissions
- **DatasetAccessibility**: Dataset sharing permissions

### Accessibility Pattern
Entities like Dataset, Model, and CustomAlgorithm use an accessibility system:
- **PUBLIC**: Visible to all users
- **PRIVATE**: Only visible to owner
- **SHARED**: Shared with specific users (via join tables)

## Testing

### Backend Tests
Located in `backend/src/test/java/com/cloud_ml_app_thesis/`:
- `unit_tests/service/` - Service layer unit tests (Mockito-based)
- Integration tests use in-memory database or test containers

### Test Execution
```bash
cd backend

# Run all tests
./mvnw test

# Run specific test class
./mvnw test -Dtest=CustomTrainingServiceTest

# Run with coverage
./mvnw test jacoco:report
```

## API Documentation

Swagger UI available at http://localhost:8080/swagger-ui.html when running locally.

Key API endpoints:
- `/api/auth/*` - Authentication (login, register, password reset)
- `/api/datasets/*` - Dataset management
- `/api/datasets/configurations/*` - Dataset configuration
- `/api/algorithms/*` - Algorithm management
- `/api/trainings/*` - Training workflows
- `/api/executions/*` - Prediction execution
- `/api/models/*` - Model management
- `/api/tasks/*` - Async task status
- `/api/categories/*` - Category management

## Common Tasks

### Adding a New Predefined Algorithm
1. Add algorithm metadata to database via migration
2. Update `AlgorithmType` enum if needed
3. Implement training logic in `TrainService`
4. Add corresponding configuration DTOs

### Adding a New Custom Algorithm
Users upload:
1. Docker image (TAR) or DockerHub URL via UI
2. Algorithm parameters as JSON schema
3. System validates image contains `/app/algorithm.py`
4. Training uses standardized `/templates/train.py` that imports user's `algorithm.py`

### Debugging Training Issues
1. Check task status: `GET /api/tasks/{taskId}`
2. View training logs: `docker-compose logs -f backend`
3. For Docker issues, check mounted volumes: `docker inspect thesis_backend`
4. Verify shared volume exists: `ls -la /var/lib/docker/volumes/`
5. Set `PRESERVE_SHARED_DEBUG=true` to prevent cleanup of temp directories

### Database Migrations
Flyway migrations in `backend/src/main/resources/db/migration/` are applied on startup.

**Create new migration**:
1. Create file: `V{version}__{description}.sql` (e.g., `V2__add_user_role.sql`)
2. Restart backend to apply

## Important Notes

1. **Weka Configuration**: System property `weka.core.ClassDiscovery.enableCache=false` is set in main class to prevent JAR scanning issues
2. **Async Operations**: Training and prediction are async - always return taskId and poll `/api/tasks/{taskId}` for status
3. **Transaction Management**: `CustomTrainingService` uses `PROPAGATION_NOT_SUPPORTED` and manual transaction boundaries to avoid optimistic locking conflicts
4. **File Permissions**: Training directories are created with 777 permissions for Docker container access
5. **Docker Socket**: Backend mounts `/var/run/docker.sock` for Docker-in-Docker - this is the primary integration point
6. **Shared Volume**: Critical for Docker training - data exchange happens via mounted filesystem at `/app/shared`
7. **Maven Wrapper**: Always use `./mvnw` not `mvn` to ensure consistent Maven version
8. **Frontend Build**: TypeScript compilation happens before Vite build via `npm run build`

## Cloud-Native Considerations

When deploying to production cloud environments:
1. **Replace Docker-in-Docker** with Kubernetes Jobs or cloud container services (see `kubernetes/README.md`)
2. **Externalize secrets** using Kubernetes Secrets, AWS Secrets Manager, or equivalent
3. **Use managed services** for PostgreSQL and object storage instead of self-hosted
4. **Implement autoscaling** for backend pods based on CPU/memory
5. **Add monitoring** via Prometheus/Grafana or cloud-native solutions (CloudWatch, Stackdriver)
6. **Enable HTTPS** with cert-manager (Kubernetes) or cloud load balancer SSL termination
7. **Configure backup strategies** for database and object storage
