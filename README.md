# ThesisApp

ThesisApp is a full-stack application deployed locally on Kubernetes with Minikube. The deploy script builds the backend and frontend images, loads them into Minikube, and creates the required services (Postgres, MinIO, MailHog).

## Prerequisites

- Docker
- kubectl
- Minikube
- Bash

## Quick start (Minikube)

1. From the repository root, run the deploy script:

```bash
./kubernetes/deploy-to-minikube.sh
```

If the script is not executable, run:

```bash
chmod +x kubernetes/deploy-to-minikube.sh
```

2. Wait for pods to be ready:

```bash
kubectl get pods -n thesisapp -w
```

3. Port-forward services (run each in a separate terminal):

```bash
kubectl port-forward -n thesisapp svc/frontend 5173:5173
kubectl port-forward -n thesisapp svc/backend 8080:8080
kubectl port-forward -n thesisapp svc/mailhog 8025:8025
kubectl port-forward -n thesisapp svc/minio 9001:9001
```

4. Open the services:

- Frontend: http://localhost:5173
- Backend: http://localhost:8080
- MailHog UI: http://localhost:8025
- MinIO Console: http://localhost:9001 (user: minioadmin, pass: minioadmin)

## Quick start (Docker Compose)

1. Build and start services:

```bash
docker compose up --build
```

If your Docker user mapping differs, run:

```bash
UID=$(id -u) GID=$(id -g) docker compose up --build
```

2. Wait until the backend is healthy:

```bash
curl http://localhost:8080/actuator/health
```

3. Open the services:

- Frontend: http://localhost:5173
- Backend: http://localhost:8080
- MailHog UI: http://localhost:8025
- MinIO Console: http://localhost:9101 (user: minioadmin, pass: minioadmin)
- MinIO API: http://localhost:9900
- Postgres: localhost:5434

To stop containers:

```bash
docker compose down
```

## API endpoints

Base URL: `http://localhost:8080`

Authentication: most endpoints require `Authorization: Bearer <token>` from `/api/auth/login`.

API docs: `http://localhost:8080/swagger-ui/index.html` (OpenAPI JSON at `http://localhost:8080/v3/api-docs`)

Public endpoints (no auth):

- `POST /api/auth/register`
- `POST /api/auth/login`
- `POST /api/auth/forgot-password`
- `POST /api/auth/reset-password`
- `GET /api/algorithms/get-algorithms`
- `GET /api/algorithms/weka/{id}/options`
- `POST /api/train/parse-dataset-columns`
- `GET /actuator/health`

Endpoints overview:

- Auth
  - `POST /api/auth/register`
  - `POST /api/auth/login`
  - `POST /api/auth/logout`
  - `PATCH /api/auth/change-password`
  - `POST /api/auth/forgot-password`
  - `POST /api/auth/reset-password`
- Users
  - `GET /api/users/me`
  - `GET /api/users/all`
  - `GET /api/users/details/{username}`
  - `PUT /api/users/update`
  - `PUT /api/users/updateByAdmin/{username}`
  - `PATCH /api/users/delete`
  - `PATCH /api/users/delete/{username}`
- Algorithms
  - `POST /api/algorithms/createCustomAlgorithm` (multipart/form-data)
  - `GET /api/algorithms/get-algorithms`
  - `GET /api/algorithms/weka/{id}/options`
  - `GET /api/algorithms/get-custom-algorithms`
  - `POST /api/algorithms`
  - `PATCH /api/algorithms/weka/update/{id}`
  - `POST /api/algorithms/choose-algorithm`
  - `GET /api/algorithms/{id}`
  - `POST /api/algorithms/search-custom-algorithms`
  - `POST /api/algorithms/search-weka-algorithms`
  - `PATCH /api/algorithms/custom/update/{id}`
  - `DELETE /api/algorithms/{id}`
  - `DELETE /api/algorithms/custom/delete/{id}`
- Algorithm configurations
  - `POST /api/algorithm-configurations/{algoId}`
  - `PATCH /api/algorithm-configurations/{id}`
  - `DELETE /api/algorithm-configurations/{id}`
- Categories
  - `GET /api/categories`
  - `GET /api/categories/{id}`
  - `GET /api/categories/requests/pending`
  - `GET /api/categories/requests/all`
  - `POST /api/categories/addCategory`
  - `POST /api/categories/{requestId}/approve`
  - `PATCH /api/categories/{requestId}/reject`
  - `PATCH /api/categories/{id}/update`
  - `DELETE /api/categories/{id}/delete`
- Datasets
  - `POST /api/datasets/search`
  - `POST /api/datasets/upload` (multipart/form-data)
  - `PATCH /api/datasets` (multipart/form-data)
  - `GET /api/datasets`
  - `GET /api/datasets/infos/{id}`
  - `GET /api/datasets/{id}`
  - `GET /api/datasets/info/{id}`
  - `GET /api/datasets/download/{id}`
  - `DELETE /api/datasets/{id}`
  - `GET /api/datasets/{email}/category`
- Dataset configurations
  - `POST /api/dataset-configurations/upload-dataset-configuration`
  - `GET /api/dataset-configurations/configurations`
  - `POST /api/dataset-configurations/create-dataset-conf`
- Dataset sharing (no /api prefix)
  - `POST /dataset/share/{datasetId}`
  - `PATCH /dataset/share/{datasetId}/share`
  - `POST /dataset/share/{datasetId}/copy`
  - `POST /dataset/share/{datasetId}/decline`
- Training
  - `POST /api/train/train-model` (multipart/form-data)
  - `POST /api/train/custom` (multipart/form-data)
  - `GET /api/train/trainings`
  - `GET /api/train/retrain/options`
  - `GET /api/train/retrain/trainings/{trainingId}`
  - `GET /api/train/retrain/models/{modelId}`
  - `DELETE /api/train/delete/{id}`
  - `GET /api/train/used-algorithms`
  - `POST /api/train/parse-dataset-columns` (multipart/form-data)
- Models
  - `GET /api/models`
  - `GET /api/models/metrics/{modelId}`
  - `GET /api/models/metrics-bar/model/{modelId}`
  - `GET /api/models/metrics-confusion/model/{id}`
  - `GET /api/models/metrics-scatter/model/{id}`
  - `GET /api/models/metrics-residual/model/{id}`
  - `GET /api/models/metrics-cluster-sizes/model/{modelId}`
  - `GET /api/models/metrics-scatter-cluster/model/{modelId}`
  - `GET /api/models/status/{modelId}`
  - `POST /api/models/finalize/{modelId}`
  - `GET /api/models/training/{trainingId}/download-model`
  - `GET /api/models/{modelId}`
  - `PUT /api/models/update/{modelId}`
  - `PATCH /api/models/updateContent/{modelId}`
  - `DELETE /api/models/delete/{modelId}`
  - `POST /api/models/search`
- Model sharing
  - `POST /api/models/sharing/{modelId}/share`
  - `POST /api/models/sharing/{modelId}/revoke`
- Model executions
  - `POST /api/model-exec/execute` (multipart/form-data)
  - `GET /api/model-exec/{executionId}/result`
  - `DELETE /api/model-exec/delete/{executionId}`
  - `POST /api/model-exec/search`
  - `GET /api/model-exec/{executionId}`
- Task status
  - `GET /api/tasks/{trackingId}`
  - `GET /api/tasks/{taskId}/model-id`
  - `GET /api/tasks/{taskId}/training-id`
  - `GET /api/tasks/{taskId}/execution-id`
  - `PUT /api/tasks/{taskId}/stop`

## Training and execution guides

All training/execution endpoints are asynchronous. They return a `taskId`, which you can track via `/api/tasks/{taskId}` and use to fetch generated IDs.

### Weka (predefined) training

1. Choose a predefined algorithm:

```bash
curl http://localhost:8080/api/algorithms/get-algorithms
```

2. (Optional) Inspect dataset columns:

```bash
curl -F "file=@/path/to/train.csv" \
  http://localhost:8080/api/train/parse-dataset-columns
```

3. Start training:

```bash
curl -H "Authorization: Bearer $TOKEN" \
  -F "file=@/path/to/train.csv" \
  -F "algorithmId=1" \
  -F "basicCharacteristicsColumns=1,2,3" \
  -F "targetClassColumn=4" \
  -F "options=-K 3" \
  http://localhost:8080/api/train/train-model
```

4. Track the task and fetch model/training IDs:

```bash
curl -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/api/tasks/{taskId}

curl -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/api/tasks/{taskId}/model-id

curl -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/api/tasks/{taskId}/training-id
```

### Custom algorithm training

1. Upload a custom algorithm (TAR or Docker Hub URL):

```bash
curl -H "Authorization: Bearer $TOKEN" \
  -F "name=MyCustomAlgo" \
  -F "version=1.0.0" \
  -F "accessibility=PUBLIC" \
  -F "keywords=custom" \
  -F "keywords=python" \
  -F "parametersFile=@/path/to/params.json" \
  -F "dockerTarFile=@/path/to/algorithm-image.tar" \
  http://localhost:8080/api/algorithms/createCustomAlgorithm
```

2. Train using the custom algorithm ID returned above:

```bash
curl -H "Authorization: Bearer $TOKEN" \
  -F "algorithmId=10" \
  -F "datasetFile=@/path/to/train.csv" \
  -F "parametersFile=@/path/to/params.json" \
  -F "basicAttributesColumns=feature1,feature2" \
  -F "targetColumn=class" \
  http://localhost:8080/api/train/custom
```

3. Track the task and fetch IDs (same task endpoints as Weka training).

### Model execution (prediction)

Use the same execution endpoint for Weka or custom models. It returns a `taskId`, which you can use to fetch the execution ID and download results.

```bash
curl -H "Authorization: Bearer $TOKEN" \
  -F "modelId=123" \
  -F "predictionFile=@/path/to/predict.csv" \
  http://localhost:8080/api/model-exec/execute
```

```bash
curl -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/api/tasks/{taskId}/execution-id

curl -H "Authorization: Bearer $TOKEN" \
  -o prediction-result.csv \
  http://localhost:8080/api/model-exec/{executionId}/result
```

## Notes

- The deploy script uses local images: `thesis-backend:local` and `thesis-frontend:local`.
- It creates default secrets for Postgres and MinIO for local development.

## Cleanup

To remove the namespace and all resources:

```bash
kubectl delete namespace thesisapp
```

To stop Minikube:

```bash
minikube stop
```
