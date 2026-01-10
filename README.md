# ThesisApp - Cloud-Based Machine Learning Platform

A comprehensive full-stack ML platform for training models with **Weka algorithms** and **custom Python algorithms**, deployed on Kubernetes.

## 📚 Quick Guide for Teachers/Instructors

**Using Windows?** Jump to: [WSL2 Complete Setup](#wsl2-complete-setup-windows)
**Network Issues?** See: [WSL2 Service Access](#wsl2-service-access-solutions)
**Testing**: After setup, run tests with [Testing Guide](#testing)

**Recommended for Teaching:**
- Use **Windows with WSL2** (most students have Windows)
- Allocate **16GB RAM** for WSL2 in `.wslconfig`
- Use **Docker Desktop** (easier than Docker Engine)
- Use **Ingress + minikube tunnel** (production-like, works perfectly with WSL2)

---

## Table of Contents

- [Prerequisites](#prerequisites)
- [WSL2 Complete Setup (Windows)](#wsl2-complete-setup-windows)
- [Quick Start](#quick-start)
- [WSL2 Service Access Solutions](#wsl2-service-access-solutions)
- [Features](#features)
- [Training Models](#training-models)
- [Making Predictions](#making-predictions)
- [Testing](#testing)
- [Troubleshooting](#troubleshooting-wsl2)
- [API Documentation](#api-documentation)

---

## Prerequisites

### System Requirements
- **RAM**: 16GB recommended (12GB minimum)
- **Disk**: 20GB free space
- **CPU**: 4+ cores recommended

### Required Software
- **Docker** (20.10+)
- **kubectl** (1.25+)
- **Minikube** (1.30+)
- **Maven** (3.8+) for building backend
- **Java 21** for backend development

---

## WSL2 Complete Setup (Windows)

### Step 1: Enable WSL2

Open **PowerShell as Administrator**:

```powershell
# Enable WSL
wsl --install

# Set WSL2 as default
wsl --set-default-version 2

# Install Ubuntu
wsl --install -d Ubuntu-22.04
```

**Restart your computer**.

### Step 2: Configure WSL2 Resources

Create `C:\Users\YourUsername\.wslconfig`:

```ini
[wsl2]
memory=16GB
processors=6
swap=8GB
localhostForwarding=true
```

**Restart WSL2** (PowerShell as Admin):
```powershell
wsl --shutdown
```

Then reopen Ubuntu from Start Menu.

### Step 3: Install Docker

**Option A: Docker Desktop (Recommended)**

1. Download from https://www.docker.com/products/docker-desktop
2. Install on Windows
3. Open Docker Desktop → Settings → Resources → WSL Integration
4. Enable integration with Ubuntu
5. Restart Docker Desktop

**Option B: Docker Engine (Lightweight)**

```bash
# Add Docker repository
sudo apt-get update
sudo apt-get install -y ca-certificates curl gnupg
sudo install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg

echo \
  "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu \
  $(lsb_release -cs) stable" | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null

# Install Docker
sudo apt-get update
sudo apt-get install -y docker-ce docker-ce-cli containerd.io

# Add user to docker group
sudo usermod -aG docker $USER

# Start Docker and enable auto-start
sudo service docker start
echo 'sudo service docker start 2>/dev/null' >> ~/.bashrc
```

### Step 4: Install kubectl and Minikube

```bash
# Install kubectl
curl -LO "https://dl.k8s.io/release/$(curl -L -s https://dl.k8s.io/release/stable.txt)/bin/linux/amd64/kubectl"
sudo install -o root -g root -m 0755 kubectl /usr/local/bin/kubectl
rm kubectl

# Install Minikube
curl -LO https://storage.googleapis.com/minikube/releases/latest/minikube-linux-amd64
sudo install minikube-linux-amd64 /usr/local/bin/minikube
rm minikube-linux-amd64

# Install Maven and Java
sudo apt-get install -y maven openjdk-21-jdk
```

### Step 5: Start Minikube

```bash
# Start with sufficient resources
minikube start --driver=docker --memory=8192 --cpus=4

# Verify
minikube status
docker ps
```

**Verify installations:**
```bash
docker --version
kubectl version --client
minikube version
mvn --version
java -version
```

---

## Quick Start

### 1. Clone and Deploy

```bash
cd ~  # Work in WSL2 filesystem (NOT /mnt/c/)
git clone https://github.com/yourusername/ThesisApp.git
cd ThesisApp/kubernetes
./deploy-to-minikube.sh
```

### 2. Wait for Pods

```bash
kubectl get pods -n thesisapp -w
# Wait until all pods show "Running" and "1/1" ready
# Press Ctrl+C to exit
```

### 3. Start Ingress Tunnel (Required for WSL2)

In a **new terminal** (keep it running):

```bash
cd ~/ThesisApp
./kubernetes/start-tunnel.sh
```

This script will:
- ✅ Add `thesisapp.local` to `/etc/hosts` (requires sudo)
- ✅ Start `minikube tunnel` (requires sudo password)
- ✅ Keep the tunnel running (don't close this terminal!)

**Output:**
```
🚇 Starting Minikube Tunnel for Ingress...

After tunnel starts, access services at:
  📱 Frontend:      http://thesisapp.local
  🔧 Backend API:   http://thesisapp.local/api
  📧 MailHog:       http://mailhog.thesisapp.local
  💾 MinIO Console: http://minio.thesisapp.local

⚠️  Keep this terminal open - tunnel will stop if you close it!
```

### 4. Access Services

Open your **Windows browser** (Chrome, Firefox, Edge):

| Service | URL | Purpose |
|---------|-----|---------|
| **Frontend** | http://thesisapp.local | Main web interface |
| **Backend API** | http://thesisapp.local/api | REST API endpoints |
| **Swagger UI** | http://thesisapp.local/api/swagger-ui/index.html | API documentation |
| **MailHog** | http://mailhog.thesisapp.local | View test emails |
| **MinIO Console** | http://minio.thesisapp.local | File storage |

**Note**: WSL2 can access these domains directly from Windows browser! No additional port-forwarding needed.

---

## WSL2 Service Access Solutions

### ✅ Recommended: Ingress + minikube tunnel (Currently Configured)

**What is it?**
- Uses Kubernetes Ingress for routing (production-like)
- `minikube tunnel` creates network bridge between WSL2 and Minikube
- Access services via clean domains: `http://thesisapp.local`

**How to use:**
```bash
# Terminal 1: Start tunnel (keep running)
./kubernetes/start-tunnel.sh

# Terminal 2: Access from Windows browser
open http://thesisapp.local
```

**Pros:**
- ✅ Production-like setup (teaches real Kubernetes)
- ✅ Clean URLs (no random ports)
- ✅ Single entry point for all services
- ✅ Works perfectly with WSL2
- ✅ Path-based routing (`/api` → backend, `/` → frontend)

**Cons:**
- ⚠️ Requires keeping terminal open for tunnel

---

### Alternative: Port-Forwarding (For debugging)

If tunnel isn't working, use port-forwarding:

```bash
kubectl port-forward -n thesisapp svc/frontend 5173:5173
kubectl port-forward -n thesisapp svc/backend 8080:8080
```

Then access at `http://localhost:5173` and `http://localhost:8080`

---

## Features

- ✅ Train models with **Weka algorithms** (J48, RandomForest, LinearRegression, etc.)
- ✅ Upload and train **custom Python algorithms** (Docker-based)
- ✅ Make predictions on new data
- ✅ **Automatic categorical target handling** for custom algorithms
- ✅ View training metrics and visualizations
- ✅ Share models with other users
- ✅ **NodePort services** (no port-forwarding needed on native Linux)
- ✅ **WSL2-optimized** with `minikube service` support

---

## Training Models

### Weka Algorithms (Predefined)

```bash
# 1. Login and get token
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"bigspy","password":"adminPassword"}'

export TOKEN="your_jwt_token_here"

# 2. Train model
curl -X POST http://localhost:8080/api/train/train-model \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@training_data.csv" \
  -F "algorithmId=10" \
  -F "basicCharacteristicsColumns=1,2,3" \
  -F "targetClassColumn=4"

# 3. Track progress
curl -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/api/tasks/{taskId}

# 4. Get model ID when completed
curl -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/api/tasks/{taskId}/model-id
```

### Custom Python Algorithms

See example in: `backend/src/test/resources/custom_test/customer_purchase_predictor/`

```bash
# 1. Build Docker image
cd your_algorithm_directory
docker build -t my_algo:latest .
docker save my_algo:latest -o my_algo.tar

# 2. Upload algorithm
curl -X POST http://localhost:8080/api/algorithms/createCustomAlgorithm \
  -H "Authorization: Bearer $TOKEN" \
  -F "name=my_algorithm" \
  -F "version=1.0.0" \
  -F "accessibility=PUBLIC" \
  -F "parametersFile=@parameters.json" \
  -F "dockerTarFile=@my_algo.tar"

# 3. Train with custom algorithm
curl -X POST http://localhost:8080/api/train/custom \
  -H "Authorization: Bearer $TOKEN" \
  -F "algorithmId=10" \
  -F "datasetFile=@training_data.csv"
```

---

## Making Predictions

```bash
# 1. Execute prediction
curl -X POST http://localhost:8080/api/model-exec/execute \
  -H "Authorization: Bearer $TOKEN" \
  -F "modelId=42" \
  -F "predictionFile=@test_data.csv"

# 2. Get execution ID
curl -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/api/tasks/{taskId}/execution-id

# 3. Download results
curl -H "Authorization: Bearer $TOKEN" \
  -o predictions.csv \
  http://localhost:8080/api/model-exec/{executionId}/result
```

---

## Testing

### Run Integration Tests

```bash
cd backend

# Get backend URL (if using minikube service)
BACKEND_URL=$(minikube service backend -n thesisapp --url)
BACKEND_HOST=$(echo $BACKEND_URL | cut -d: -f1-2)
BACKEND_PORT=$(echo $BACKEND_URL | cut -d: -f3)

# Run tests
mvn test -Dtest=BasicFullWekaFlowIT \
  -Dtest.host=$BACKEND_HOST \
  -Dtest.port=$BACKEND_PORT
```

**Or with port-forwarding:**
```bash
# Terminal 1: Port-forward backend
kubectl port-forward -n thesisapp svc/backend 8080:8080

# Terminal 2: Run test
mvn test -Dtest=BasicFullWekaFlowIT
```

---

## Troubleshooting (WSL2)

### Docker Not Starting

```bash
# Check status
sudo service docker status

# Start Docker
sudo service docker start

# Auto-start on WSL launch (add to ~/.bashrc)
echo 'sudo service docker start 2>/dev/null' >> ~/.bashrc
```

### Cannot Access Services from Windows Browser

✅ **Solution**: Use `minikube service` command instead of NodePort URLs:

```bash
minikube service frontend -n thesisapp
minikube service backend -n thesisapp --url
```

### Port Already in Use

```bash
# Find process using port 8080
sudo lsof -i :8080

# Kill it
sudo kill -9 <PID>
```

### Minikube Won't Start

```bash
# Delete and recreate
minikube delete
minikube start --driver=docker --memory=8192 --cpus=4
```

### Pods Crashing (OOMKilled)

Increase WSL2 memory in `C:\Users\YourUsername\.wslconfig`:

```ini
[wsl2]
memory=20GB
processors=8
```

Then restart WSL2 (PowerShell as Admin):
```powershell
wsl --shutdown
```

### File Performance Issues

✅ Work in WSL2 filesystem (`~/ThesisApp`), NOT Windows filesystem (`/mnt/c/`)

```bash
# BAD (slow):
cd /mnt/c/Users/yourname/ThesisApp

# GOOD (fast):
cd ~/ThesisApp
```

### Git Line Ending Issues

```bash
# Configure Git for Unix line endings
git config --global core.autocrlf input
git config --global core.eol lf

# Fix existing files
sudo apt-get install dos2unix
find . -name "*.sh" -exec dos2unix {} \;
```

---

## API Documentation

Access Swagger UI at:
- Port-forward: http://localhost:8080/swagger-ui/index.html
- Minikube service: Get URL from `minikube service backend --url`

---

## Default Test Users

| Username | Password | Role |
|----------|----------|------|
| bigspy | adminPassword | ADMIN |
| johnken | adminPassword | ADMIN |
| nickriz | userPassword | USER |

---

## MinIO Credentials

```bash
# Get credentials
kubectl get secret minio-secret -n thesisapp -o jsonpath='{.data.access-key}' | base64 -d
kubectl get secret minio-secret -n thesisapp -o jsonpath='{.data.secret-key}' | base64 -d
```

---

## Cleanup

```bash
# Delete all resources
kubectl delete namespace thesisapp

# Stop Minikube
minikube stop

# Delete Minikube cluster
minikube delete
```

---

## Architecture

```
┌─────────────────────────────────────────────┐
│         Frontend (TypeScript)                │
│         NodePort: 30173                      │
└──────────────────┬──────────────────────────┘
                   │ REST API
┌──────────────────▼──────────────────────────┐
│      Backend (Spring Boot + Java 21)        │
│         NodePort: 30080                      │
│  ┌─────────┐  ┌──────────┐  ┌─────────┐    │
│  │  Weka   │  │  Custom  │  │  Model  │    │
│  │Training │  │ Training │  │  Exec   │    │
│  └─────────┘  └──────────┘  └─────────┘    │
└───────┬────────────┬───────────┬────────────┘
        │            │           │
┌───────▼────┐  ┌────▼─────┐  ┌─▼──────────┐
│ PostgreSQL │  │  MinIO   │  │   Docker   │
│  Database  │  │ Storage  │  │   Engine   │
└────────────┘  └──────────┘  └────────────┘
```

---

**Happy Machine Learning! 🚀🤖**
