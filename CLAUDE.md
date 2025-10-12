# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Overview

ThesisApp is a cloud-based machine learning platform that allows users to train and execute ML models. The application consists of a Spring Boot backend (Java 21) and a Vite/TypeScript frontend, orchestrated via Docker Compose with supporting services (PostgreSQL, MinIO, MailHog).

## Development Commands

### Running the Application

**Quick Start (Recommended):**
```bash
./docker-start.sh
```
This script handles Git submodules, Maven wrapper permissions, environment setup, and starts all services.

**Manual Docker Compose:**
```bash
# Start all services
docker compose up --build -d

# View logs
docker compose logs -f

# Stop services
docker compose down
```

**Access Points:**
- Backend API: http://localhost:8080
- Frontend: http://localhost:5173
- MinIO Console: http://localhost:9001
- MailHog (Email Testing): http://localhost:8025
- Swagger UI: http://localhost:8080/swagger-ui.html

### Backend (Spring Boot)

**Build:**
```bash
cd backend
./mvnw clean package
```

**Run Tests:**
```bash
cd backend
./mvnw test
```

**Run Specific Test:**
```bash
cd backend
./mvnw test -Dtest=ClassName#methodName
```

**Run Integration Tests:**
```bash
cd backend
./mvnw verify -P integration-tests
```

### Frontend (Vite + TypeScript)

**Development:**
```bash
cd frontend
npm install
npm run dev
```

**Build:**
```bash
cd frontend
npm run build
```

**Preview Production Build:**
```bash
cd frontend
npm run preview
```

## Architecture

### Backend Structure

The backend follows a layered architecture pattern:

```
backend/src/main/java/com/cloud_ml_app_thesis/
├── controller/          # REST API endpoints
├── service/            # Business logic
├── repository/         # Data access layer
├── entity/             # JPA entities
├── dto/                # Data transfer objects
├── config/             # Spring configuration (including security)
├── util/               # Utility classes
├── validation/         # Custom validators
├── enumeration/        # Enums (status, action, accessibility)
├── exception/          # Custom exceptions
├── helper/             # Helper classes
└── specification/      # JPA specifications for dynamic queries
```

**Key Packages:**
- `controller/` - RESTful endpoints for datasets, algorithms, models, training, and execution
- `service/` - Core services including `CustomTrainingService`, `CustomPredictionService`, `MinioService`, `DatasetService`
- `util/` - Contains `DockerContainerRunner` for ML workload orchestration

### Frontend Structure

```
frontend/src/
├── app/                # Main app setup
├── core/               # Core functionality (routing, API)
├── features/           # Feature modules
│   ├── auth/
│   ├── datasets/
│   ├── algorithms/
│   ├── models/
│   ├── trainings/
│   ├── executions/
│   ├── results/
│   └── tasks/
└── shared/             # Shared components and utilities
```

### Docker-in-Docker ML Execution

The backend spawns Docker containers to execute custom ML algorithms via `DockerContainerRunner.java`. This is a critical architectural component:

- Training and prediction workloads run in isolated Docker containers
- Containers use a shared volume (`/app/shared`) to access datasets and write outputs
- Custom algorithms are user-uploaded Docker images stored in MinIO
- The backend mounts the Docker socket (`/var/run/docker.sock`) to spawn containers

**Important:** When migrating to Kubernetes, replace `DockerContainerRunner` with Kubernetes Jobs (see `kubernetes/README.md` for implementation guidance).

### Data Flow

1. **Dataset Upload** → MinIO (`datasets` bucket) + PostgreSQL metadata
2. **Algorithm Upload** → MinIO (`algorithms` bucket) + PostgreSQL configuration
3. **Training Request** → Backend spawns Docker container → Outputs to MinIO (`models` bucket)
4. **Prediction Request** → Backend spawns Docker container → Results to MinIO (`predictions` bucket)
5. **Task Status** → Async task tracking via `AsyncTaskStatus` entity

### Configuration Profiles

The backend supports multiple Spring profiles:
- `local` - Local development (default)
- `docker` - Running in Docker Compose
- Cloud profiles can be added (see `CLOUD_MIGRATION_GUIDE.md`)

Profile-specific configs: `backend/src/main/resources/application-{profile}.yaml`

## Database

**Technology:** PostgreSQL 15

**Migration Tool:** Flyway (enabled by default)

**Migration Location:** `backend/src/main/resources/db/migration/`

**Common Commands:**
```bash
# Access database in Docker
docker exec -it thesis_postgres psql -U postgres -d thesis_db

# Backup
docker exec thesis_postgres pg_dump -U postgres thesis_db > backup.sql

# Restore
cat backup.sql | docker exec -i thesis_postgres psql -U postgres -d thesis_db
```

## Object Storage (MinIO)

**Buckets:**
- `datasets` - Uploaded datasets
- `algorithms` - Custom algorithm Docker images
- `models` - Trained models
- `predictions` - Prediction inputs
- `prediction-results` - Prediction outputs
- `metrics` - Training metrics
- `parameters` - Model parameters

MinIO is used for local development. For production, migrate to S3/GCS/Azure Blob (see `CLOUD_MIGRATION_GUIDE.md`).

## Testing

**Test Structure:**
- Unit tests: `backend/src/test/java/com/cloud_ml_app_thesis/`
- Integration tests: `backend/src/test/java/com/cloud_ml_app_thesis/intergration/`

**Integration Test Categories:**
- `isolated/` - Individual service tests
- `user/` - User-related workflows
- `category/` - Category management
- `full_flow_IT/` - End-to-end workflows (Weka and Custom algorithms)

**Running Specific Integration Test Suites:**
```bash
cd backend
./mvnw test -Dtest="*IT"
./mvnw test -Dtest="CustomServiceTrainingIT"
```

## Security

- JWT-based authentication using RSA keys (`backend/src/main/resources/keys/`)
- Security configuration in `config/security/`
- User roles defined in `entity/Role.java`
- Password reset tokens supported

## Kubernetes Deployment

Full Kubernetes deployment manifests are in `kubernetes/`. Key considerations:

1. **Replace Docker-in-Docker with Kubernetes Jobs** for ML workloads
2. **Use cloud-managed databases** instead of PostgreSQL pod
3. **Replace MinIO with S3/GCS** for production
4. **Configure ReadWriteMany storage** for shared ML data volumes

See `kubernetes/README.md` for deployment instructions.

## Cloud Migration

A comprehensive cloud migration guide is available in `CLOUD_MIGRATION_GUIDE.md`. It covers:
- Externalizing configuration
- Replacing MinIO with S3/GCS/Azure Blob
- Database migration strategies
- Replacing Docker-in-Docker with ECS Fargate/Kubernetes Jobs
- CI/CD pipelines
- Monitoring and logging
- Security hardening

## Environment Variables

Copy `.env.example` to `.env` and customize:

```bash
cp .env.example .env
```

Key variables:
- `POSTGRES_*` - Database credentials
- `MINIO_*` - Object storage configuration
- `VITE_API_URL` - Frontend API endpoint

## Git Submodules

This repository uses Git submodules. Ensure they are initialized:

```bash
git submodule update --init --recursive
```

The `docker-start.sh` script handles this automatically.

## Common Workflows

### Adding a New ML Algorithm

1. Upload algorithm Docker image via frontend
2. Image stored in MinIO `algorithms` bucket
3. Metadata saved to `CustomAlgorithm` entity
4. Training/prediction uses `DockerContainerRunner` to spawn container

### Training a Model

1. Select dataset and algorithm configuration
2. Backend creates `Training` entity
3. `CustomTrainingService` or Weka service spawns Docker container
4. Container reads from `/app/shared/training-ds-{taskId}`
5. Model saved to MinIO `models` bucket
6. Training status tracked via `AsyncTaskStatus`

### Debugging Training Issues

1. Check logs: `docker compose logs -f backend`
2. Verify shared volume: `docker exec -it thesis_backend ls /app/shared`
3. Check MinIO buckets: http://localhost:9001
4. Review async task status via API: `/api/tasks/{taskId}`

## Known Limitations

- Docker-in-Docker requires `/var/run/docker.sock` mount (not cloud-native)
- File size limit: 512MB (configurable in `application.yaml`)
- MailHog is for development only; use SendGrid/SES for production
