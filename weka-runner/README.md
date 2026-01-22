# Weka Runner

Standalone Java application for running Weka machine learning algorithms as Kubernetes jobs.

## Architecture Overview

The Weka Runner is part of the containerized ML training/prediction architecture:

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              BACKEND (Spring Boot)                          │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  TrainingOrchestrator                    PredictionOrchestrator             │
│         │                                        │                          │
│         ▼                                        ▼                          │
│  WekaContainerTrainingService           WekaContainerPredictionService      │
│         │                                        │                          │
│         │  1. Download dataset from MinIO        │  1. Download model/data  │
│         │  2. Create params.json                 │  2. Create params.json   │
│         │  3. Run K8s Job                        │  3. Run K8s Job          │
│         │  4. Upload model.ser, metrics.json     │  4. Upload predictions   │
│         ▼                                        ▼                          │
│    ContainerRunner.runWekaTrainingContainer()   ContainerRunner.runWeka...  │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                         KUBERNETES JOB (weka-runner)                        │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  WekaRunner (main)                                                          │
│      │                                                                      │
│      ├── train  ──► WekaTrainer                                             │
│      │                  │                                                   │
│      │                  ├── Load dataset.csv                                │
│      │                  ├── Read params.json (algorithm, options, columns)  │
│      │                  ├── selectColumns() - filter to selected columns    │
│      │                  ├── Train classifier/clusterer                      │
│      │                  ├── Evaluate model                                  │
│      │                  └── Save model.ser, metrics.json                    │
│      │                                                                      │
│      └── predict ──► WekaPredictor                                          │
│                          │                                                  │
│                          ├── Load test_data.csv                             │
│                          ├── Read params.json                               │
│                          ├── Load model.ser                                 │
│                          ├── selectColumnsAndEnsureTarget()                 │
│                          ├── Predict with classifier/clusterer              │
│                          └── Save predictions.csv                           │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

## Components

### WekaRunner (Main Entry Point)
- Entry point for the container
- Accepts commands: `train` or `predict`
- Reads `DATA_DIR` and `MODEL_DIR` environment variables

### WekaTrainer
- Handles model training for Classification, Regression, and Clustering
- Reads `params.json` for algorithm configuration
- Applies column selection (same as `DatasetUtil.selectColumns` in backend)
- Applies `fixNestedOptions()` for Weka option compatibility
- Outputs:
  - `model.ser` - Serialized Weka model
  - `metrics.json` - Evaluation metrics (compatible with VisualizationService)

### WekaPredictor
- Handles predictions using trained models
- Adds placeholder target column if missing (for Weka compatibility)
- Outputs:
  - `predictions.csv` - Original data with predictions in target column

## Input/Output Files

### Training

**Input (DATA_DIR):**
```
├── dataset.csv          # Training data
└── params.json          # Configuration
    {
      "algorithmClassName": "weka.classifiers.functions.LinearRegression",
      "algorithmType": "REGRESSION",
      "options": "-S 0 -R 1.0E-8",
      "targetColumn": "4",
      "basicAttributesColumns": "1,2,3"
    }
```

**Output (MODEL_DIR):**
```
├── model.ser            # Serialized Weka model
└── metrics.json         # Evaluation metrics
```

### Prediction

**Input (DATA_DIR):**
```
├── test_data.csv        # Data to predict
└── params.json          # Configuration
    {
      "algorithmType": "REGRESSION",
      "targetColumn": "4",
      "basicAttributesColumns": "1,2,3"
    }
```

**Input (MODEL_DIR):**
```
└── model.ser            # Trained model (copied from MinIO)
```

**Output (MODEL_DIR):**
```
└── predictions.csv      # Predictions
```

## Metrics Format

### Classification (metrics.json)
```json
{
  "accuracy": "85.50%",
  "precision": "84.20%",
  "recall": "85.50%",
  "f1Score": "84.80%",
  "summary": "...",
  "confusionMatrix": [[10, 2], [3, 15]],
  "classLabels": ["yes", "no"]
}
```

### Regression (metrics.json)
```json
{
  "rmse": 12.345,
  "mae": 8.765,
  "rSquared": 0.92,
  "summary": "...",
  "actualValues": [100, 150, ...],
  "predictedValues": [98, 152, ...]
}
```

### Clustering (metrics.json)
```json
{
  "numClusters": 3,
  "logLikelihood": -123.45,
  "summary": "...",
  "clusterAssignments": ["Instance 0 assigned to cluster 0", ...],
  "projection2D": [{"x": 1.2, "y": 3.4}, ...]
}
```

## Building

```bash
# Build JAR
mvn clean package -DskipTests -B

# Build Docker image
docker build -t thesisapp/weka-runner:latest .

# Load into Minikube
minikube image load thesisapp/weka-runner:latest
```

## Docker Image

The Dockerfile uses a multi-stage build:
1. **Build stage**: Maven builds the uber-JAR with all dependencies
2. **Runtime stage**: Eclipse Temurin JRE 21 runs the application

## Comparison with Previous Architecture

| Aspect | Old (In-Memory) | New (Kubernetes Jobs) |
|--------|-----------------|----------------------|
| Training | `TrainService.train()` | `WekaContainerTrainingService` + `WekaTrainer` |
| Prediction | `ModelExecutionService.executePredefined()` | `WekaContainerPredictionService` + `WekaPredictor` |
| Execution | In backend JVM | Separate K8s Job container |
| Scalability | Limited by backend resources | Independent, scalable |
| Isolation | Shared with backend | Isolated container |

## Related Backend Services

- `WekaContainerTrainingService` - Orchestrates training jobs
- `WekaContainerPredictionService` - Orchestrates prediction jobs
- `ContainerRunner` - Interface for running containers (K8s or Docker)
- `KubernetesJobRunner` - K8s implementation
- `DockerContainerRunner` - Docker implementation (for local dev)
