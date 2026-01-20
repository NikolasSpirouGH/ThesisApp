# ThesisApp - Cloud-Based Machine Learning Platform

A comprehensive full-stack machine learning platform for training models with Weka algorithms and custom Python algorithms, deployed on Kubernetes. This system demonstrates cloud-native architecture, microservices design, and scalable ML workload orchestration.

## Overview

ThesisApp provides a complete environment for:
- Training machine learning models using built-in Weka algorithms or custom Python implementations
- Executing predictions on trained models
- Managing datasets, algorithms, and model lifecycle
- Collaborative model sharing between users
- Production-ready deployment on Kubernetes with proper ingress routing

**Key Technologies**: Spring Boot (Java 21), TypeScript, PostgreSQL, MinIO, Kubernetes, Docker, Weka ML Library

---


## Table of Contents

- [Prerequisites](#prerequisites)
- [System Architecture](#system-architecture)
- [Deployment Guide (WSL2 + Windows)](#deployment-guide-wsl2--windows)
- [Quick Start](#quick-start)
- [Accessing Services](#accessing-services)
- [Testing](#testing)
- [API Usage Examples](#api-usage-examples)
- [Troubleshooting](#troubleshooting)
- [Configuration Reference](#configuration-reference)

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
- **Git** for cloning repository

### Optional (Development Only)
- **Maven** (3.8+) - Only needed for local backend development
- **Java 21** - Only needed for local backend development
- **Node.js** (18+) - Only needed for local frontend development

**Note**: Maven and Java are NOT required for deployment. The Docker build process handles all compilation.

---

## System Architecture

### Network Architecture (WSL2 + Windows)

```
┌──────────────────────────────────────────────────────────────────┐
│                    Windows Browser                               │
│                 (Chrome, Firefox, Edge)                          │
│                                                                  │
│         Accesses: http://thesisapp.local                         │
│         Windows hosts: 172.22.x.x thesisapp.local                │
└────────────────────────────┬─────────────────────────────────────┘
                             │
                             │ (HTTP Request)
                             ▼
┌──────────────────────────────────────────────────────────────────┐
│                         WSL2 Ubuntu                              │
│                      IP: 172.22.x.x                              │
│                                                                  │
│  ┌────────────────────────────────────────────────────────┐    │
│  │  Port Forward (socat)                                  │    │
│  │  0.0.0.0:80 → 192.168.49.2:80                          │    │
│  └────────────────────┬───────────────────────────────────┘    │
│                       │                                         │
│                       │ WSL2 hosts: 192.168.49.2 thesisapp.local│
│                       ▼                                         │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │              Minikube Cluster                            │  │
│  │              IP: 192.168.49.2                            │  │
│  │                                                          │  │
│  │  ┌────────────────────────────────────────────────┐    │  │
│  │  │         Nginx Ingress Controller               │    │  │
│  │  │      Routes by hostname and path               │    │  │
│  │  └─────────┬────────────────────────────────┬─────┘    │  │
│  │            │                                │          │  │
│  │   ┌────────▼────────┐           ┌──────────▼────────┐ │  │
│  │   │   Frontend      │           │    Backend        │ │  │
│  │   │  (TypeScript)   │           │  (Spring Boot)    │ │  │
│  │   │   Port: 5174    │           │   Port: 8080      │ │  │
│  │   └─────────────────┘           └────────┬──────────┘ │  │
│  │                                           │            │  │
│  │       ┌───────────────────────────────────┼─────────┐ │  │
│  │       │                   │               │         │ │  │
│  │  ┌────▼─────┐      ┌──────▼──────┐  ┌────▼──────┐  │ │  │
│  │  │PostgreSQL│      │    MinIO    │  │  MailHog  │  │ │  │
│  │  │(Database)│      │  (Storage)  │  │  (Email)  │  │ │  │
│  │  └──────────┘      └──────┬──────┘  └───────────┘  │ │  │
│  │                           │                         │ │  │
│  │                   ┌───────▼────────┐                │ │  │
│  │                   │  Kubernetes    │                │ │  │
│  │                   │  Jobs/Pods     │                │ │  │
│  │                   │  (ML Training) │                │ │  │
│  │                   └────────────────┘                │ │  │
│  └──────────────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────────────┘
```

### Application Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    Kubernetes Ingress                        │
│              (nginx - Route: /api, /)                        │
└──────────────────────┬──────────────────────────────────────┘
                       │
       ┌───────────────┴────────────────┐
       │                                │
┌──────▼────────┐              ┌───────▼─────────┐
│   Frontend    │              │    Backend      │
│  (TypeScript) │              │  (Spring Boot)  │
│   Port: 5174  │              │   Port: 8080    │
└───────────────┘              └────────┬────────┘
                                        │
                    ┌───────────────────┼─────────────────┐
                    │                   │                 │
             ┌──────▼───────┐   ┌──────▼──────┐   ┌─────▼──────┐
             │  PostgreSQL  │   │    MinIO    │   │  MailHog   │
             │  (Database)  │   │  (Storage)  │   │  (Email)   │
             └──────────────┘   └─────────────┘   └────────────┘
                                        │
                                ┌───────▼────────┐
                                │  Kubernetes    │
                                │  Jobs/Pods     │
                                │  (ML Training) │
                                └────────────────┘
```

### Architecture Highlights

**Ingress-Based Routing**: Uses Kubernetes Ingress with nginx controller for production-like routing:
- `/` routes to frontend service
- `/api` routes to backend service
- Separate subdomains for auxiliary services (mailhog, minio)

**Microservices Design**: Separate deployments for frontend, backend, and data services, allowing independent scaling and updates.

**Stateful Storage**: PostgreSQL and MinIO use StatefulSets with persistent volumes for data durability.

**Dynamic ML Workloads**: Custom algorithms run as Kubernetes Jobs, providing isolation and resource management.

### Understanding the Network Setup

**What is `thesisapp.local`?**

`thesisapp.local` is a **custom domain name** used for accessing the application through the Kubernetes Ingress Controller.

**Two-Layer DNS Resolution:**

1. **From Windows** (your browser):
   - Windows hosts file: `172.22.x.x thesisapp.local`
   - `thesisapp.local` resolves to your **WSL2 IP address**
   - Requests go to WSL2 on port 80

2. **From WSL2** (internal):
   - WSL2 hosts file: `192.168.49.2 thesisapp.local`
   - Port-forward (socat) redirects traffic from WSL2:80 → Minikube:80
   - Inside Minikube, `thesisapp.local` is handled by the Ingress Controller
   - Ingress routes requests based on hostname and path

**Why not use `localhost`?**

- `localhost` always points to `127.0.0.1` (loopback)
- The Ingress Controller needs a **hostname** to route traffic correctly
- Multiple services (frontend, mailhog, minio) use the **same IP** but **different hostnames**
- Using custom domains allows proper hostname-based routing

**Example:**
- `http://thesisapp.local` → Frontend
- `http://thesisapp.local/api` → Backend
- `http://mailhog.thesisapp.local` → MailHog
- `http://minio.thesisapp.local` → MinIO Console

All these resolve to the same IP but route to different services based on the hostname!

---

## Deployment Guide (WSL2 + Windows)

This guide is optimized for Windows users deploying to WSL2, which is the most common scenario for students and educational environments.

### Step 1: Enable WSL2

Open PowerShell as Administrator:

```powershell
# Enable WSL
wsl --install

# Set WSL2 as default
wsl --set-default-version 2

# Install Ubuntu
wsl --install -d Ubuntu-22.04
```

Restart your computer after installation.

### Step 2: Configure WSL2 Resources

Create or edit `C:\Users\YourUsername\.wslconfig`:

```ini
[wsl2]
memory=16GB
processors=6
swap=8GB
localhostForwarding=true
```

Restart WSL2 (PowerShell as Administrator):
```powershell
wsl --shutdown
```

Then reopen Ubuntu from the Start Menu.

### Step 3: Install Docker

**Option A: Docker Desktop (Recommended for Students)**

1. Download from https://www.docker.com/products/docker-desktop
2. Install on Windows
3. Open Docker Desktop > Settings > Resources > WSL Integration
4. Enable integration with your Ubuntu distribution
5. Restart Docker Desktop

**Option B: Docker Engine (Lightweight Alternative)**

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

# Start Docker
sudo service docker start

# Enable auto-start on WSL launch
echo 'sudo service docker start 2>/dev/null' >> ~/.bashrc
```

Log out and log back into WSL2 for group changes to take effect.

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

# Verify installations
docker --version
kubectl version --client
minikube version
```

### Step 5: Start Minikube

```bash
# Start Minikube with appropriate resources
minikube start --driver=docker --memory=8192 --cpus=4

# Verify cluster is running
minikube status
kubectl get nodes
```

---

## Quick Start

### 1. Clone Repository

```bash
# Work in WSL2 filesystem for better performance
cd ~
git clone https://github.com/yourusername/ThesisApp.git
cd ThesisApp
```

**Important**: Always work in the WSL2 filesystem (`~/ThesisApp`), NOT the Windows filesystem (`/mnt/c/...`). This ensures optimal file I/O performance.

### 2. Deploy Application

```bash
cd kubernetes
./deploy-to-minikube.sh
```

This script will:
1. Build backend JAR with Maven (inside Docker)
2. Build Docker images for backend and frontend
3. Load images into Minikube
4. Deploy all Kubernetes resources (namespace, secrets, deployments, services, ingress)
5. Patch ingress controller for WSL2 compatibility

Expected duration: 5-10 minutes depending on your system.

### 3. Wait for Pods to be Ready

```bash
kubectl get pods -n thesisapp -w
```

Wait until all pods show `Running` status and `1/1` ready. Press Ctrl+C to exit watch mode.

Expected pods:
- backend (2 replicas)
- frontend (2 replicas)
- postgres-0
- minio-0
- mailhog

### 4. Start Port-Forward for WSL2

In a **separate terminal window** (keep this running):

```bash
cd ~/ThesisApp/kubernetes
./start-tunnel-wsl2.sh
```

**What this does**:
- Displays your current WSL2 IP address
- Shows the hosts file entry you need to add to Windows
- Starts port-forwarding from WSL2:80 to Kubernetes ingress

**Manual Step Required**: Add the displayed entry to your Windows hosts file:

1. Open Notepad as Administrator (Right-click > Run as administrator)
2. Open: `C:\Windows\System32\drivers\etc\hosts`
3. Add the line shown by the script (e.g., `172.22.101.50 thesisapp.local mailhog.thesisapp.local minio.thesisapp.local`)
4. Save and close

**Understanding the hosts file entry:**

The entry you add maps domain names to your WSL2 IP address. For example:

```
172.22.101.50 thesisapp.local mailhog.thesisapp.local minio.thesisapp.local
```

This means:
- When you type `http://thesisapp.local` in your Windows browser, it resolves to `172.22.101.50` (your WSL2 IP)
- The request goes to WSL2, which forwards it to Minikube
- Minikube's Ingress Controller receives the request and routes it based on the hostname

**Important Notes:**
- The WSL2 IP address (`172.22.x.x`) may **change** after restarting WSL2 or Windows
- If you can't access the app after a restart, check if the IP changed and update the hosts file
- To get your current WSL2 IP, run in WSL2 terminal: `hostname -I | awk '{print $1}'`
- Keep the port-forward terminal open. Port-forwarding will stop if you close it.

---

## Accessing Services

Open your Windows browser (Chrome, Firefox, Edge) and navigate to:

| Service | URL | Description |
|---------|-----|-------------|
| **Frontend** | http://thesisapp.local | Main web application interface |
| **Backend API** | http://thesisapp.local/api | REST API endpoints |
| **Swagger UI** | http://thesisapp.local/api/swagger-ui/index.html | Interactive API documentation |
| **MailHog** | http://mailhog.thesisapp.local | Email testing interface |
| **MinIO Console** | http://minio.thesisapp.local | Object storage management |

### Default User Accounts

| Username | Password | Role |
|----------|----------|------|
| bigspy | adminPassword | ADMIN |
| johnken | adminPassword | ADMIN |
| nickriz | userPassword | USER |

### Accessing PostgreSQL Database

**Important**: PostgreSQL is **NOT** exposed through ingress for security reasons. The database should only be accessed by the backend and for administrative purposes via port-forwarding.

To access PostgreSQL from your local machine (for database management tools like DBeaver, pgAdmin, or psql):

```bash
# Forward PostgreSQL to local port 15432 (in a separate terminal)
kubectl port-forward -n thesisapp pod/postgres-0 15432:5432
```

**Keep this terminal open while accessing the database.**

Now connect using your database client:

| Setting | Value |
|---------|-------|
| **Host** | `localhost` (or `127.0.0.1`) |
| **Port** | `15432` |
| **Database** | `thesis_db` |
| **Username** | Get from secret (see below) |
| **Password** | Get from secret (see below) |

**Get Database Credentials:**

```bash
# Get username
kubectl get secret postgres-secret -n thesisapp -o jsonpath='{.data.username}' | base64 -d
# Output: postgres

# Get password
kubectl get secret postgres-secret -n thesisapp -o jsonpath='{.data.password}' | base64 -d
# Output: (your password)
```

**Using psql (PostgreSQL CLI):**

```bash
# Install psql (if not already installed)
sudo apt-get install postgresql-client

# Connect to database (with port-forward running)
psql -h localhost -p 15432 -U postgres -d thesis_db
# Enter password when prompted

# Example queries
\dt                          # List all tables
\d users                     # Describe users table
SELECT * FROM users LIMIT 5; # Query users
\q                           # Quit
```

**Why is PostgreSQL not in ingress?**
- **Security**: Databases should never be publicly exposed
- **Protocol**: Ingress handles HTTP/HTTPS only, PostgreSQL uses a binary protocol
- **Best Practice**: Only backend services should access the database directly

---

## Cluster Management with k9s

k9s is a powerful terminal-based UI for managing and monitoring your Kubernetes cluster. It provides real-time visualization of cluster resources, making it much easier to debug issues and monitor your ML application.

### Why Use k9s?

- **Real-time monitoring**: Watch pods, deployments, and services update live
- **Quick debugging**: View logs, describe resources, and exec into containers with single keystrokes
- **Efficient navigation**: Switch between namespaces and resource types instantly
- **Resource management**: Delete, edit, scale resources without typing kubectl commands
- **Visual feedback**: Color-coded status indicators (green=running, yellow=pending, red=errors)

### Installing k9s

```bash
# Install k9s using webi installer
curl -sS https://webi.sh/k9s | sh

# Add k9s to PATH
echo 'source ~/.config/envman/load.sh' >> ~/.bashrc
source ~/.bashrc

# Verify installation
k9s version
```

### Starting k9s

```bash
# Launch k9s (starts in default namespace)
k9s

# Launch k9s in your application namespace
k9s -n thesisapp

# Launch k9s showing all namespaces
k9s -A
```

### Essential Keyboard Shortcuts

#### Navigation and Views
| Shortcut | Action |
|----------|--------|
| `:` | Enter command mode (type resource name like `pods`, `deploy`, `svc`) |
| `:q` or `Ctrl+c` | Quit k9s |
| `?` | Show all keyboard shortcuts |
| `Esc` | Go back / Cancel current action |
| `0` | Toggle showing all namespaces |
| `Ctrl+a` | Show all available resources |

#### Common Resource Views
| Command | Description |
|---------|-------------|
| `:pod` or `:po` | View pods |
| `:deploy` | View deployments |
| `:svc` | View services |
| `:ing` | View ingress rules |
| `:job` | View jobs (ML training tasks) |
| `:sts` | View StatefulSets (postgres, minio) |
| `:pvc` | View persistent volume claims |
| `:ns` | View/switch namespaces |
| `:secret` | View secrets |
| `:cm` | View ConfigMaps |

#### Resource Actions
| Shortcut | Action |
|----------|--------|
| `Enter` | View selected resource details |
| `d` | Describe selected resource (detailed info) |
| `l` | View logs for selected pod |
| `Shift+f` | Port-forward to selected pod/service |
| `s` | Shell into selected pod (exec) |
| `e` | Edit selected resource (YAML) |
| `Ctrl+d` | Delete selected resource (confirmation required) |
| `Ctrl+k` | Kill selected pod (force delete) |
| `y` | View YAML definition |

#### Navigation Within Views
| Shortcut | Action |
|----------|--------|
| `↑` `↓` or `j` `k` | Navigate up/down |
| `g` | Go to top |
| `Shift+g` | Go to bottom |
| `/` | Filter resources (type to search) |
| `Esc` | Clear filter |

#### Advanced Features
| Shortcut | Action |
|----------|--------|
| `Shift+p` | Toggle port-forwarding |
| `Ctrl+z` | Show previous screen |
| `Shift+c` | Sort by CPU usage |
| `Shift+m` | Sort by memory usage |
| `Shift+r` | Refresh/reload view |
| `u` | Show resource usage (CPU/Memory) |

### Common k9s Workflows for ThesisApp

#### 1. Monitor Application Health
```bash
# Start k9s in thesisapp namespace
k9s -n thesisapp

# View all pods (default view)
# Look for:
# - backend (should have 2 replicas running)
# - frontend (should have 2 replicas running)
# - postgres-0 (StatefulSet)
# - minio-0 (StatefulSet)
# - mailhog (single pod)
```

#### 2. Check ML Training Jobs
```bash
# In k9s, type:
:job

# You'll see Kubernetes Jobs created for ML training
# Jobs are named: train-<model-id>-<random>
# Status colors:
# - Green: Completed successfully
# - Yellow: Running
# - Red: Failed
```

#### 3. View Application Logs
```bash
# In k9s, navigate to pods view (:pod)
# Use arrow keys to select a pod (e.g., backend-xxx)
# Press 'l' to view logs
# Press '0' to see logs from all containers in pod
# Press '1', '2', etc. to switch between containers
# Press 's' to toggle auto-scroll
# Press 'w' to toggle line wrap
```

#### 4. Debug Backend Issues
```bash
# View backend pods
:pod
# Filter for backend: type /backend then Enter
# Select a backend pod
# Press 'd' to describe (check events, restarts)
# Press 'l' to view logs
# Press 's' to shell into the pod
```

#### 5. Check Database Status
```bash
# View postgres pod
:pod
# Type /postgres to filter
# Select postgres-0
# Press 'l' to view logs
# Press 's' to shell into pod
# Inside shell: psql -U postgres -d thesis_db
```

#### 6. Monitor Resource Usage
```bash
# In any resource view
# Press 'u' to show CPU/Memory usage
# Press Shift+c to sort by CPU
# Press Shift+m to sort by memory
# Great for identifying resource-hungry ML training jobs
```

#### 7. Scale Deployments
```bash
# View deployments
:deploy
# Select backend or frontend
# Press 's' to scale
# Enter desired number of replicas
# Press Enter to confirm
```

#### 8. Quick Port-Forward
```bash
# Navigate to services (:svc)
# Select a service (e.g., backend)
# Press Shift+f
# Enter local port (e.g., 8080)
# Press Enter
# Service is now accessible at localhost:8080
```

### Tips for Effective k9s Usage

1. **Keep k9s Running**: Open k9s in a dedicated terminal window for continuous monitoring
2. **Use Filters**: Press `/` and type keywords to quickly find resources
3. **Watch Training Jobs**: Use `:job` view to monitor ML model training progress
4. **Check Events**: Press `d` on any resource to see recent events and errors
5. **Namespace Context**: Start with `k9s -n thesisapp` to focus on your application
6. **Learn Shortcuts**: Press `?` anytime to see available shortcuts for current view
7. **Multiple Terminals**: Run k9s alongside other terminals for kubectl commands
8. **Resource Cleanup**: Use k9s to quickly identify and delete old completed Jobs

### k9s vs kubectl

| Task | kubectl Command | k9s Shortcut |
|------|----------------|--------------|
| List pods | `kubectl get pods -n thesisapp` | `:pod` (from any view) |
| View logs | `kubectl logs -n thesisapp pod-name` | Select pod → `l` |
| Describe pod | `kubectl describe pod -n thesisapp pod-name` | Select pod → `d` |
| Delete pod | `kubectl delete pod -n thesisapp pod-name` | Select pod → `Ctrl+d` |
| Shell into pod | `kubectl exec -it pod-name -n thesisapp -- /bin/bash` | Select pod → `s` |
| Port-forward | `kubectl port-forward svc/backend 8080:8080 -n thesisapp` | Select service → `Shift+f` |
| View YAML | `kubectl get pod pod-name -n thesisapp -o yaml` | Select pod → `y` |

### Troubleshooting with k9s

**Pod Stuck in Pending**:
- Select pod → Press `d` to describe
- Look for events at bottom of screen
- Common issues: Insufficient resources, PVC not bound

**CrashLoopBackOff**:
- Select pod → Press `l` to view logs
- Look for error messages
- Press `d` to see restart count and recent events

**Service Not Responding**:
- Check pod status first (`:pod`)
- Verify service endpoints (`:svc` → Select service → `d`)
- Check ingress rules (`:ing`)

**Training Job Failed**:
- View jobs (`:job`)
- Select failed job → Press `d` for events
- Find the job pod (`:pod` and filter by job name)
- View pod logs (`l`) for error details

---

## Testing

The application includes integration tests for both Weka and custom algorithm workflows.

### Option 1: Test Through Ingress (Recommended for WSL2)

Requires `start-tunnel-wsl2.sh` to be running.

```bash
cd ~/ThesisApp/backend

# Test Weka algorithm workflow
mvn test -Dtest=BasicFullWekaFlowIT \
  -Dtest.host=http://thesisapp.local \
  -Dtest.port=80 \
  -Dtest.basePath=/api

# Test custom algorithm workflow
mvn test -Dtest=BasicFullCustomFlowIT \
  -Dtest.host=http://thesisapp.local \
  -Dtest.port=80 \
  -Dtest.basePath=/api
```

### Option 2: Test via Port-Forward

Open a separate terminal:

```bash
# Terminal 1: Port-forward backend
kubectl port-forward -n thesisapp svc/backend 8080:8080
```

Then run tests (Terminal 2):

```bash
cd ~/ThesisApp/backend

# Tests will use default localhost:8080
mvn test -Dtest=BasicFullWekaFlowIT
mvn test -Dtest=BasicFullCustomFlowIT
```

### Option 3: Test via NodePort

```bash
cd ~/ThesisApp/backend

# Get NodePort URL
BACKEND_URL=$(minikube service backend -n thesisapp --url)
BACKEND_HOST=$(echo $BACKEND_URL | cut -d: -f1-2)
BACKEND_PORT=$(echo $BACKEND_URL | cut -d: -f3)

# Run tests
mvn test -Dtest=BasicFullWekaFlowIT \
  -Dtest.host=$BACKEND_HOST \
  -Dtest.port=$BACKEND_PORT
```

### Test Coverage

**BasicFullWekaFlowIT**: Tests the complete lifecycle with built-in Weka algorithms:
- User authentication
- Dataset upload
- Model training with Weka algorithm (e.g., LinearRegression)
- Task monitoring
- Model prediction
- Result download

**BasicFullCustomFlowIT**: Tests custom Python algorithm workflows:
- Custom algorithm upload (Docker image as .tar)
- Dataset upload
- Model training with custom algorithm
- Task monitoring
- Model prediction
- Result download

**Note**: Custom algorithm tests take longer (3-5 minutes) due to Docker image loading into Minikube.

---

## API Usage Examples

All examples assume you're using the ingress setup. Replace `http://thesisapp.local` with `http://localhost:8080` if using port-forward.

### Authentication

```bash
# Login
curl -X POST http://thesisapp.local/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"bigspy","password":"adminPassword"}'

# Extract token from response
export TOKEN="your_jwt_token_here"
```

### Training with Weka Algorithms

```bash
# 1. Train model with built-in Weka algorithm
curl -X POST http://thesisapp.local/api/train/train-model \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@training_data.csv" \
  -F "algorithmId=10" \
  -F "basicCharacteristicsColumns=1,2,3" \
  -F "targetClassColumn=4"

# Response contains taskId
# {"taskId":"abc-123-def","message":"Training started"}

# 2. Monitor training progress
curl -H "Authorization: Bearer $TOKEN" \
  http://thesisapp.local/api/tasks/{taskId}

# 3. Get model ID when completed
curl -H "Authorization: Bearer $TOKEN" \
  http://thesisapp.local/api/tasks/{taskId}/model-id
```

### Training with Custom Python Algorithms

See example implementations in: `backend/src/test/resources/custom_test/`

#### Phase 1: Create Custom Algorithm (One-Time)

Users can provide the Docker image in **TWO ways**:

**Option A: Upload Docker TAR file**
```bash
# 1. Build and save Docker image
cd your_algorithm_directory
docker build -t my_algorithm:latest .
docker save my_algorithm:latest -o my_algorithm.tar

# 2. Create algorithm with TAR file
curl -X POST http://thesisapp.local/api/algorithms/createCustomAlgorithm \
  -H "Authorization: Bearer $TOKEN" \
  -F "name=my_algorithm" \
  -F "version=1.0.0" \
  -F "accessibility=PUBLIC" \
  -F "keywords=classification,custom" \
  -F "description=My custom logistic regression" \
  -F "parametersFile=@parameters.json" \
  -F "dockerTarFile=@my_algorithm.tar"
```

**Option B: Use Docker Hub URL**
```bash
# 1. Push to Docker Hub
docker tag my_algorithm:latest myusername/my_algorithm:latest
docker push myusername/my_algorithm:latest

# 2. Create algorithm with Docker Hub URL
curl -X POST http://thesisapp.local/api/algorithms/createCustomAlgorithm \
  -H "Authorization: Bearer $TOKEN" \
  -F "name=my_algorithm" \
  -F "version=1.0.0" \
  -F "accessibility=PUBLIC" \
  -F "keywords=classification,custom" \
  -F "description=My custom logistic regression" \
  -F "parametersFile=@parameters.json" \
  -F "dockerHubUrl=docker.io/myusername/my_algorithm:latest"
```

**Important Notes**:
- `parametersFile`: Defines **DEFAULT** parameters for the algorithm (required)
- Choose **EITHER** `dockerTarFile` **OR** `dockerHubUrl` (not both)
- TAR files can be large (200-600MB) - ensure adequate upload timeout
- Docker Hub images must be public (or cluster must have pull credentials)

#### Phase 2: Train with Custom Algorithm (Can Repeat Many Times)

```bash
# Train using the algorithm's default parameters
curl -X POST http://thesisapp.local/api/train/custom \
  -H "Authorization: Bearer $TOKEN" \
  -F "algorithmId={algorithmId}" \
  -F "datasetFile=@training_data.csv"

# OR: Train with custom parameters (overrides defaults)
curl -X POST http://thesisapp.local/api/train/custom \
  -H "Authorization: Bearer $TOKEN" \
  -F "algorithmId={algorithmId}" \
  -F "datasetFile=@training_data.csv" \
  -F "parametersFile=@custom_params.json"
```

**Parameters in Training**:
- `algorithmId`: References algorithm created in Phase 1 (required)
- `datasetFile`: Training dataset CSV/ARFF/XLSX (required)
- `parametersFile`: **OPTIONAL** - overrides default parameters for this training run
  - If omitted: Uses defaults from algorithm creation
  - If provided: Merges with defaults (custom values override defaults)
- `basicAttributesColumns`: Optional feature column selection
- `targetColumn`: Optional target column specification (defaults to last column)

**Parameter Override Example**:
```json
// Algorithm creation (parameters.json):
[
  {"name": "learning_rate", "value": "0.01"},
  {"name": "n_epochs", "value": "1000"}
]

// Training with override (custom_params.json):
{
  "learning_rate": 0.05,
  "n_epochs": 500
}

// Final merged parameters used:
{
  "learning_rate": 0.05,    // Overridden
  "n_epochs": 500           // Overridden
}
```

---

## Custom Algorithm Architecture

### Overview

The application supports custom Python ML algorithms through a containerized execution system. Users provide their algorithm implementation, and the platform handles data preprocessing, training execution, and prediction workflows automatically.

### How It Works

```
┌─────────────┐
│   User      │
│  Uploads    │
└──────┬──────┘
       │
       ├─► algorithm.py      (ML implementation)
       ├─► Dockerfile        (Container definition)
       ├─► requirements.txt  (Python dependencies)
       ├─► parameters.json   (Configurable params)
       └─► *.tar            (Docker image)
              │
              ▼
┌──────────────────────────────────────────────┐
│          Backend Service                      │
│  ┌──────────────────────────────────────┐   │
│  │ 1. Loads Docker image to cluster      │   │
│  │ 2. Downloads dataset from MinIO       │   │
│  │ 3. Converts categorical → numeric     │   │
│  │ 4. Injects train.py template          │   │
│  │ 5. Extracts user's algorithm.py       │   │
│  └──────────────┬───────────────────────┘   │
└─────────────────┼───────────────────────────┘
                  ▼
┌──────────────────────────────────────────────┐
│   Container Runner (Docker/K8s)               │
│  ┌────────────────────────────────────────┐  │
│  │  Shared Volume Mount                   │  │
│  │  ├─ /data/                             │  │
│  │  │  ├─ dataset.csv                     │  │
│  │  │  ├─ params.json                     │  │
│  │  │  ├─ train.py (platform template)    │  │
│  │  │  └─ algorithm.py (user code)        │  │
│  │  └─ /model/ (output)                   │  │
│  │     ├─ trained_model.pkl               │  │
│  │     ├─ metrics.json                    │  │
│  │     ├─ label_mapping.json (optional)   │  │
│  │     └─ feature_columns.json (optional) │  │
│  └────────────────────────────────────────┘  │
└──────────────────┬───────────────────────────┘
                   ▼
         ┌──────────────────┐
         │  Upload to MinIO  │
         └──────────────────┘
```

### Python Templates

The platform provides two standardized Python templates that bridge user algorithms with the system:

#### 1. `train.py` - Training Template

**Location**: `backend/src/main/resources/templates/train.py`

**Responsibilities**:
- Loads dataset from `/data/dataset.csv`
- Reads parameters from `/data/params.json`
- **Automatically handles categorical data**:
  - Features: One-hot encoding (e.g., `color=red` → `color_red=1, color_blue=0`)
  - Target: Label encoding (e.g., `spam/ham` → `0/1` with mapping file)
- Imports user's `algorithm.py`
- Calls `Algorithm.fit(X, y)` with pure numeric data
- Saves trained model to `/model/trained_model.pkl`
- Generates required output files

**Key Feature - Automatic Data Conversion**:
```python
# train.py handles this automatically:
# Input CSV:  color,size,animal
#             red,large,dog
#             blue,small,cat
#
# Becomes:    color_red,color_blue,size_large,size_small,animal
#             1,0,1,0,0
#             0,1,0,1,1
#
# User's algorithm receives pure numeric arrays!
```

**Generated Artifacts**:
- `trained_model.pkl` - Serialized model (required)
- `metrics.json` - Training metrics (required)
- `label_mapping.json` - Categorical target mapping (auto-generated if needed)
- `feature_columns.json` - Feature names after encoding (auto-generated if categorical features exist)

#### 2. `predict.py` - Prediction Template

**Location**: `backend/src/main/resources/templates/predict.py`

**Responsibilities**:
- Loads trained model from `/model/trained_model.pkl`
- Loads prediction dataset from `/data/test_data.csv`
- **Applies same categorical encoding as training**:
  - Uses `feature_columns.json` to ensure identical feature order
  - Adds missing columns (zero-filled)
  - Removes extra columns
- Calls `Algorithm.predict(X)` with transformed data
- **Converts numeric predictions back to original labels** (if `label_mapping.json` exists)
- Saves results to `/model/predictions.csv`

**Key Feature - Consistent Encoding**:
```python
# predict.py ensures predictions match training:
# Training had: color_red,color_blue,size_large,size_small
#
# New prediction data with "green" color?
# → Automatically adds color_green=0 (unseen during training)
# → Reorders columns to match training exactly
#
# Numeric prediction: 1
# → Converts back using label_mapping.json: "spam"
```

### Workflow Summary

| Phase | What You Do Locally | What You Upload to Platform |
|-------|---------------------|----------------------------|
| **Build** | 1. Write `algorithm.py`<br>2. Create `Dockerfile`<br>3. Create `requirements.txt`<br>4. Run `docker build`<br>5. Run `docker save` (if using TAR) | ❌ Nothing yet |
| **Create Algorithm** | Already built image | ✅ `my_algorithm.tar` OR Docker Hub URL<br>✅ `parameters.json` (defaults) |
| **Train** | Prepare training dataset | ✅ `datasetFile` (CSV/ARFF/XLSX)<br>✅ `parametersFile` (optional overrides) |
| **Predict** | Prepare test dataset | ✅ `predictionFile` (CSV) |

**Key Point**: You build the Docker image locally (which packages algorithm.py, requirements, etc.), then upload ONLY the final image (as TAR or via Docker Hub) + parameters.json.

---

### What You Actually Upload

When creating a custom algorithm, you upload:

1. **Docker Image** (choose ONE option):
   - **Option A**: `my_algorithm.tar` (built with `docker save`)
   - **Option B**: Docker Hub URL (e.g., `docker.io/username/my_algorithm:latest`)

2. **parameters.json** - Configuration file defining default parameters

**That's it!** You do NOT upload Dockerfile, algorithm.py, or requirements.txt separately - they're already packaged inside the Docker image.

---

### Building Your Custom Algorithm Locally

**Step 1: Create your files locally**

Your local directory structure:
```
my_algorithm/
├── algorithm.py          # Your ML implementation
├── requirements.txt      # Python dependencies
├── Dockerfile           # Build instructions
└── parameters.json      # Will be uploaded separately
```

**Step 2: Build Docker image**
```bash
cd my_algorithm/
docker build -t my_algorithm:latest .
```

**Step 3: Save as TAR (if using Option A)**
```bash
docker save my_algorithm:latest -o my_algorithm.tar
```

**Step 4: Upload to platform**
- Upload `my_algorithm.tar` OR provide Docker Hub URL
- Upload `parameters.json`

---

### Requirements for Your Docker Image

Here's what your Docker image must contain:

#### 1. **algorithm.py** (Required - Must be in Docker image at `/app/algorithm.py`)

Your algorithm implementation must follow this interface:

```python
import numpy as np

class Algorithm:
    def __init__(self, params: dict):
        """
        Initialize algorithm with parameters from params.json

        Args:
            params: Dictionary of parameters (e.g., {"learning_rate": 0.01})
        """
        self.learning_rate = float(params.get("learning_rate", 0.01))
        self.n_epochs = int(params.get("n_epochs", 100))
        # ... initialize your model

    def fit(self, X, y):
        """
        Train the model

        Args:
            X: numpy array of shape (n_samples, n_features)
               - ALL NUMERIC (categorical already encoded)
            y: numpy array of shape (n_samples,)
               - ALL NUMERIC (0, 1, 2... if originally categorical)

        Note: You receive PURE NUMERIC data. Platform handles conversion.
        """
        # Your training logic here
        print(f"Training on {X.shape[0]} samples with {X.shape[1]} features")
        # ...

    def predict(self, X):
        """
        Make predictions

        Args:
            X: numpy array of shape (n_samples, n_features)
               - Same encoding as training data

        Returns:
            numpy array of predictions (numeric: 0, 1, 2...)
            - Platform converts back to original labels automatically
        """
        # Your prediction logic here
        return predictions  # Return numeric array
```

**Important Notes**:
- ✅ You receive pure numeric data - no need to handle categorical encoding
- ✅ Return numeric predictions - platform converts back to original labels
- ✅ Use standard pickle-compatible objects (numpy, scikit-learn compatible)
- ❌ Don't manually encode/decode categorical data
- ❌ Don't use GPU-only libraries unless your Docker image includes GPU support

#### 2. **Dockerfile** (Used locally to build your image)

**Example Dockerfile** (you use this to build locally):

```dockerfile
FROM python:3.11-slim

WORKDIR /app

# Install dependencies
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Copy algorithm implementation to /app/algorithm.py
# IMPORTANT: Must be at /app/algorithm.py (platform extracts from this path)
COPY algorithm.py .

# IMPORTANT: Do NOT specify CMD/ENTRYPOINT for train/predict
# The platform will inject train.py/predict.py automatically
```

**Then build and save**:
```bash
docker build -t my_algorithm:latest .
docker save my_algorithm:latest -o my_algorithm.tar
```

**Important**:
- ✅ `algorithm.py` must be at `/app/algorithm.py` inside the image
- ✅ Use official Python base images (3.9+)
- ✅ Keep images small (use `-slim` variants)
- ✅ Pin dependency versions in requirements.txt
- ❌ Don't add ENTRYPOINT/CMD (platform controls execution)
- ❌ Don't hardcode file paths (use environment variables `DATA_DIR`, `MODEL_DIR`)

#### 3. **requirements.txt** (Used locally during Docker build)

**Example** (used by your Dockerfile):

```
numpy>=1.24.0
pandas>=2.0.0
scikit-learn>=1.3.0
# Add your dependencies here
```

**Required packages**:
- `numpy` - For array operations
- `pandas` - For data loading (used by platform's train.py/predict.py templates)

**Optional packages**:
- `scikit-learn` - For ML utilities
- `scipy` - For scientific computing
- Custom ML libraries (ensure compatibility)

**Note**: These get installed INSIDE your Docker image during build. You don't upload this file separately.

#### 4. **parameters.json** (Required for Algorithm Creation)

Defines **DEFAULT** configurable hyperparameters for the algorithm.

**Format for Algorithm Creation** (array of parameter definitions):

```json
[
  {
    "name": "learning_rate",
    "type": "DOUBLE",
    "description": "Step size for gradient descent",
    "value": "0.01",
    "range": "0.001-1.0"
  },
  {
    "name": "n_epochs",
    "type": "INTEGER",
    "description": "Number of training iterations",
    "value": "1000",
    "range": "10-10000"
  },
  {
    "name": "regularization",
    "type": "BOOLEAN",
    "description": "Enable L2 regularization",
    "value": "true"
  }
]
```

**Parameter Types**:
- `DOUBLE` - Floating point numbers
- `INTEGER` - Whole numbers
- `BOOLEAN` - true/false
- `STRING` - Text values

**Format for Training Override** (optional, simple key-value):

```json
{
  "learning_rate": 0.05,
  "n_epochs": 500,
  "regularization": false
}
```

**Two Use Cases**:
1. **Algorithm Creation**: Defines defaults (array format with metadata)
2. **Training Time**: Overrides defaults (simple object format)

#### 5. **Dataset Format Requirements**

When you upload datasets for training/prediction, they must follow these formats:

**Training Data Format** (uploaded during training, not algorithm creation):
```csv
feature1,feature2,categorical_feature,target
1.5,2.3,red,positive
3.2,1.1,blue,negative
...
```

**Rules**:
- Last column = target variable
- All other columns = features
- Can mix numeric and categorical columns
- Platform handles categorical conversion automatically

**Prediction Data Format**:
```csv
feature1,feature2,categorical_feature
1.8,2.1,red
2.9,1.5,green
...
```

**Rules**:
- Same features as training (order doesn't matter)
- No target column
- New categorical values are handled (zero-filled)

### Container Execution

The platform supports two execution modes, selected via `CONTAINER_RUNNER` environment variable:

#### Docker Mode (`docker`)

**Used for**: Docker Compose deployments

**How it works**:
```
Backend Container
  │
  ├─ Mounts /var/run/docker.sock
  ├─ Creates temp directories in /app/shared
  │  ├─ training-ds-XXXXX/  (input data)
  │  └─ training-out-XXXXX/ (output models)
  │
  └─ Runs Docker container:
     docker run --rm \
       -v /app/shared/training-ds-XXXXX:/data \
       -v /app/shared/training-out-XXXXX:/model \
       -e DATA_DIR=/data \
       -e MODEL_DIR=/model \
       user/algorithm:latest \
       python /data/train.py
```

**Benefits**:
- Faster startup (no Kubernetes overhead)
- Simpler for local development
- Direct Docker API access

**Limitations**:
- Requires Docker socket access
- Less isolation than Kubernetes
- No resource quotas

#### Kubernetes Mode (`kubernetes`)

**Used for**: Production Kubernetes deployments

**How it works**:
```
Backend Pod
  │
  ├─ Mounts shared PVC at /app/shared
  ├─ Creates temp directories in PVC
  │  ├─ training-ds-XXXXX/
  │  └─ training-out-XXXXX/
  │
  └─ Creates Kubernetes Job:
     apiVersion: batch/v1
     kind: Job
     metadata:
       name: training-1234567890
     spec:
       template:
         spec:
           containers:
           - name: trainer
             image: user/algorithm:latest
             command: ["sh", "-c", "python /shared/.../train.py"]
             env:
             - name: DATA_DIR
               value: "/shared/training-ds-XXXXX"
             - name: MODEL_DIR
               value: "/shared/training-out-XXXXX"
             volumeMounts:
             - name: shared-storage
               mountPath: /shared
           volumes:
           - name: shared-storage
             persistentVolumeClaim:
               claimName: shared-pvc
           restartPolicy: Never
       backoffLimit: 0
       ttlSecondsAfterFinished: 300
```

**Benefits**:
- Better isolation (separate pods)
- Resource quotas (memory/CPU limits)
- Auto-cleanup (TTL after completion)
- Scales horizontally
- Production-grade orchestration

**Features**:
- **Shared PVC**: `shared-pvc` mounted at `/shared` on all pods
- **Resource Limits**: 2Gi memory limit per training job
- **Auto-cleanup**: Jobs deleted 5 minutes after completion
- **No Retries**: `backoffLimit: 0` prevents infinite loops on failure
- **Log Streaming**: Real-time logs via Kubernetes API

**Configuration**:
```yaml
# kubernetes/backend-deployment.yaml
env:
- name: CONTAINER_RUNNER
  value: "kubernetes"  # or "docker"
- name: K8S_NAMESPACE
  value: "thesisapp"
- name: K8S_SHARED_PVC
  value: "shared-pvc"
- name: SHARED_VOLUME
  value: "/app/shared"
```

### Complete Examples

#### Example 1: Email Spam Classifier (Binary Classification)

**Step 1: Create files locally**

Create directory: `email_spam_classifier/`

**File: algorithm.py**
```python
import numpy as np

class Algorithm:
    def __init__(self, params: dict):
        self.learning_rate = float(params.get("learning_rate", 0.01))
        self.n_epochs = int(params.get("n_epochs", 1000))
        self.weights = None
        self.bias = None
        self.mean = None
        self.std = None

    def _sigmoid(self, z):
        return 1 / (1 + np.exp(-np.clip(z, -500, 500)))

    def fit(self, X, y):
        X = np.array(X)
        y = np.array(y)

        # Normalize
        self.mean = X.mean(axis=0)
        self.std = X.std(axis=0) + 1e-8
        X = (X - self.mean) / self.std

        # Initialize
        n_samples, n_features = X.shape
        self.weights = np.zeros(n_features)
        self.bias = 0

        # Gradient descent
        for epoch in range(self.n_epochs):
            linear_model = np.dot(X, self.weights) + self.bias
            y_predicted = self._sigmoid(linear_model)

            dw = (1 / n_samples) * np.dot(X.T, (y_predicted - y))
            db = (1 / n_samples) * np.sum(y_predicted - y)

            self.weights -= self.learning_rate * dw
            self.bias -= self.learning_rate * db

    def predict(self, X):
        X = np.array(X)
        X = (X - self.mean) / self.std
        linear_model = np.dot(X, self.weights) + self.bias
        y_predicted = self._sigmoid(linear_model)
        return (y_predicted > 0.5).astype(int)
```

**Training Data** (`training_data.csv`):
```csv
word_count,contains_money,contains_free,exclamation_marks,link_count,class
50,0,0,0,1,ham
120,1,1,5,3,spam
45,0,0,1,0,ham
200,1,1,8,5,spam
```

**Prediction Data** (`prediction_data.csv`):
```csv
word_count,contains_money,contains_free,exclamation_marks,link_count
55,0,0,0,1
180,1,1,7,5
```

**Expected Output**:
```csv
word_count,contains_money,contains_free,exclamation_marks,link_count,prediction
55,0,0,0,1,ham
180,1,1,7,5,spam
```

#### Example 2: Animal Classifier (Multi-class with Categorical Features)

**Training Data** (`training_data.csv`):
```csv
color,size,weight,legs,type
brown,large,500,4,mammal
yellow,small,0.5,2,bird
green,small,2,4,reptile
brown,medium,80,4,mammal
```

**What Happens**:
1. Platform encodes categorical features:
   ```
   color_brown,color_yellow,color_green,size_large,size_medium,size_small,weight,legs,type
   1,0,0,1,0,0,500,4,0
   0,1,0,0,0,1,0.5,2,1
   0,0,1,0,0,1,2,4,2
   1,0,0,0,1,0,80,4,0
   ```

2. Your algorithm receives pure numeric data
3. Saves `label_mapping.json`: `["mammal", "bird", "reptile"]`
4. Saves `feature_columns.json`: `["color_brown", "color_yellow", ...]`

**Prediction Data**:
```csv
color,size,weight,legs
brown,large,520,4
blue,tiny,0.3,2
```

**What Happens**:
1. Platform applies same encoding
2. New values (`blue`, `tiny`) → zero-filled columns
3. Predictions: `[0, 1]` (numeric)
4. Platform converts back: `["mammal", "bird"]` (using label_mapping.json)

**Expected Output**:
```csv
color,size,weight,legs,prediction
brown,large,520,4,mammal
blue,tiny,0.3,2,bird
```

### Data Flow Diagram

```
┌──────────────────────────────────────────────────────────┐
│ 1. Upload Phase                                          │
│ ────────────────────────────────────────────────────────│
│ User → API → MinIO                                       │
│   • algorithm.tar → algorithms bucket                    │
│   • training_data.csv → datasets bucket                  │
│   • parameters.json → parameters bucket                  │
└──────────────────────────────────────────────────────────┘
                         │
                         ▼
┌──────────────────────────────────────────────────────────┐
│ 2. Training Preparation                                  │
│ ────────────────────────────────────────────────────────│
│ CustomTrainingService:                                   │
│   • Load Docker image from tar                           │
│   • Download dataset from MinIO                          │
│   • Convert categorical → numeric                        │
│   • Create /data/ with:                                  │
│     - dataset.csv (encoded)                              │
│     - params.json                                        │
│     - train.py (template)                                │
│     - algorithm.py (extracted from Docker image)         │
│   • Create /model/ (empty, for output)                   │
└──────────────────────────────────────────────────────────┘
                         │
                         ▼
┌──────────────────────────────────────────────────────────┐
│ 3. Container Execution                                    │
│ ────────────────────────────────────────────────────────│
│ ContainerRunner (Docker or Kubernetes):                  │
│   • Mount /data/ → /shared/training-ds-XXX               │
│   • Mount /model/ → /shared/training-out-XXX             │
│   • Run: python /data/train.py                           │
│                                                           │
│ Inside Container:                                         │
│   train.py:                                              │
│     1. Load dataset.csv                                  │
│     2. Load params.json                                  │
│     3. Import algorithm.py                               │
│     4. Call Algorithm.fit(X, y)                          │
│     5. Save trained_model.pkl                            │
│     6. Save metrics.json                                 │
│     7. Save label_mapping.json (if categorical target)   │
│     8. Save feature_columns.json (if categorical features)│
└──────────────────────────────────────────────────────────┘
                         │
                         ▼
┌──────────────────────────────────────────────────────────┐
│ 4. Result Collection                                      │
│ ────────────────────────────────────────────────────────│
│ CustomTrainingService:                                   │
│   • Read from /model/:                                   │
│     - trained_model.pkl                                  │
│     - metrics.json                                       │
│     - label_mapping.json (optional)                      │
│     - feature_columns.json (optional)                    │
│   • Upload to MinIO:                                     │
│     - models/{username}_{timestamp}/trained_model.pkl    │
│     - models/{username}_{timestamp}/label_mapping.json   │
│     - models/{username}_{timestamp}/feature_columns.json │
│     - metrics/{username}_{timestamp}_metrics.json        │
│   • Save Model entity in PostgreSQL                      │
│   • Update Training status → COMPLETED                   │
│   • Update AsyncTask status → COMPLETED                  │
└──────────────────────────────────────────────────────────┘
                         │
                         ▼
┌──────────────────────────────────────────────────────────┐
│ 5. Prediction Phase                                       │
│ ────────────────────────────────────────────────────────│
│ CustomModelExecutionService:                             │
│   • Download from MinIO:                                 │
│     - trained_model.pkl                                  │
│     - label_mapping.json                                 │
│     - feature_columns.json                               │
│     - prediction dataset                                 │
│   • Create /data/ with:                                  │
│     - test_data.csv                                      │
│     - predict.py (template)                              │
│     - algorithm.py (extracted)                           │
│   • Create /model/ with:                                 │
│     - trained_model.pkl                                  │
│     - label_mapping.json                                 │
│     - feature_columns.json                               │
│   • Run container: python /data/predict.py               │
│                                                           │
│ Inside Container:                                         │
│   predict.py:                                            │
│     1. Load trained_model.pkl                            │
│     2. Load test_data.csv                                │
│     3. Apply feature encoding (using feature_columns.json)│
│     4. Call Algorithm.predict(X)                         │
│     5. Convert predictions (using label_mapping.json)    │
│     6. Save predictions.csv                              │
│                                                           │
│   • Upload predictions.csv to MinIO                      │
│   • Save ModelExecution entity                           │
│   • Update AsyncTask with execution ID                   │
└──────────────────────────────────────────────────────────┘
```

### Troubleshooting

**Training fails with "No model file (.pkl) generated"**:
- Ensure your `fit()` method completes without errors
- Check that you're not exiting early
- Verify pickle can serialize your model: `pickle.dumps(model)`

**Prediction fails with dimension mismatch**:
- Ensure prediction data has same features as training (order doesn't matter)
- Platform handles new categorical values automatically
- Check you're not manually encoding data

**Container fails to start**:
- Check Dockerfile builds successfully locally: `docker build -t test .`
- Ensure requirements.txt has valid packages
- Check image size (should be < 1GB for efficiency)

**Categorical encoding issues**:
- Don't manually encode in your algorithm - platform handles it
- Use string values for categorical columns
- Last column of training data must be the target

**k9s monitoring**:
```bash
# View training jobs
k9s
:job
/train  # Filter for training jobs

# Check job logs
# Select job → press 'l'

# Check for errors
# Select job → press 'd' (describe)
```

---

### Making Predictions

```bash
# 1. Execute prediction
curl -X POST http://thesisapp.local/api/model-exec/execute \
  -H "Authorization: Bearer $TOKEN" \
  -F "modelId={modelId}" \
  -F "predictionFile=@test_data.csv"

# Response contains taskId for the prediction job

# 2. Monitor prediction task
curl -H "Authorization: Bearer $TOKEN" \
  http://thesisapp.local/api/tasks/{taskId}

# 3. Get execution ID
curl -H "Authorization: Bearer $TOKEN" \
  http://thesisapp.local/api/tasks/{taskId}/execution-id

# 4. Download prediction results
curl -H "Authorization: Bearer $TOKEN" \
  -o predictions.csv \
  http://thesisapp.local/api/model-exec/{executionId}/result
```

---

## Troubleshooting

### Cannot Access thesisapp.local from Browser

**Symptoms**: Browser shows "This site can't be reached" or DNS errors.

**Solution**:

1. Verify Windows hosts file entry:
   - Open `C:\Windows\System32\drivers\etc\hosts` (as Administrator)
   - Ensure entry matches your current WSL2 IP: `172.22.x.x thesisapp.local mailhog.thesisapp.local minio.thesisapp.local`
   - **Important**: WSL2 IP may change after restarting WSL2 or Windows!

2. Get current WSL2 IP (from WSL2 terminal):
   ```bash
   hostname -I | awk '{print $1}'
   ```

3. Update Windows hosts file with current IP if changed

4. Flush Windows DNS cache (CMD/PowerShell as Administrator):
   ```cmd
   ipconfig /flushdns
   ```

5. Verify port-forward is running:
   - Check terminal running `start-tunnel-wsl2.sh`
   - Should show: `Forwarding from 0.0.0.0:80 -> 80`

6. Test from WSL2 first:
   ```bash
   curl -H "Host: thesisapp.local" http://localhost
   ```
   If this returns HTML, the issue is Windows-side (hosts file or DNS cache).

### Password Reset Link Not Working

**Symptoms**: Password reset email contains wrong URL or clicking the link doesn't work.

**Common Issues:**

1. **Link shows `localhost:5173` instead of `thesisapp.local`**:
   - Check `FRONTEND_RESET_PASSWORD_URL` environment variable in `kubernetes/base/backend-deployment-local-final.yaml`
   - Should be: `http://thesisapp.local/#/reset-password`
   - Restart backend after changes: `kubectl rollout restart deployment/backend -n thesisapp`

2. **Link shows correct URL but doesn't open**:
   - Verify Windows hosts file has the entry: `172.22.x.x thesisapp.local`
   - Check that port-forward (`start-tunnel-wsl2.sh`) is running
   - Flush DNS cache: `ipconfig /flushdns`

3. **Link opens but shows connection error**:
   - Ensure WSL2 IP in Windows hosts file is current
   - Get current IP: `hostname -I | awk '{print $1}'` (in WSL2)
   - Update Windows hosts file if IP changed

**Verification Steps:**

```bash
# 1. Check current backend configuration
kubectl get deployment backend -n thesisapp -o yaml | grep FRONTEND_RESET_PASSWORD_URL -A 1

# 2. Check backend logs for email sending
kubectl logs -n thesisapp -l app=backend --tail=50 | grep -i "password reset"

# 3. Test password reset flow:
# - Request reset from http://thesisapp.local
# - Check email in http://mailhog.thesisapp.local
# - Verify URL in email matches thesisapp.local
```

### Pods Stuck in Pending or CrashLoopBackOff

**Symptoms**: Pods don't reach Running state.

**Check pod status**:
```bash
kubectl get pods -n thesisapp
kubectl describe pod -n thesisapp {pod-name}
kubectl logs -n thesisapp {pod-name}
```

**Common causes**:

1. **Insufficient resources**: Increase memory/CPU in `.wslconfig`, restart WSL2
2. **Image pull issues**: Images should use `imagePullPolicy: Never` for local images
3. **PostgreSQL not ready**: Backend pods wait for database. Give postgres-0 time to initialize.
4. **PVC issues**: Check persistent volume claims:
   ```bash
   kubectl get pvc -n thesisapp
   ```

### Docker Not Starting in WSL2

```bash
# Check Docker status
sudo service docker status

# Start Docker
sudo service docker start

# If permission denied
sudo usermod -aG docker $USER
# Then logout/login to WSL2
```

### Port 80 Permission Denied

**Symptoms**: `start-tunnel-wsl2.sh` fails with "permission denied" on port 80.

**Solution**: The script uses `sudo` for port 80. Enter your sudo password when prompted.

### Minikube Won't Start

```bash
# Delete and recreate cluster
minikube delete
minikube start --driver=docker --memory=8192 --cpus=4
```

### Tests Timeout

**Symptoms**: Tests fail with "did not reach expected status within timeout".

**Causes**:
- Custom algorithm tests may take 3-5 minutes due to Docker image loading
- Backend pods not ready
- Network connectivity issues

**Solutions**:
- Increase test timeout (already set to 4 minutes for custom tests)
- Verify backend pods are running: `kubectl get pods -n thesisapp -l app=backend`
- Check backend logs: `kubectl logs -n thesisapp -l app=backend --tail=50`

### File Performance Issues

**Problem**: Slow builds or file operations.

**Solution**: Always work in WSL2 filesystem (`~/ThesisApp`), NOT Windows filesystem (`/mnt/c/...`).

```bash
# Bad (slow)
cd /mnt/c/Users/yourname/ThesisApp

# Good (fast)
cd ~/ThesisApp
```

### Git Line Ending Issues

```bash
# Configure Git for Unix line endings
git config --global core.autocrlf input
git config --global core.eol lf

# Fix existing shell scripts
sudo apt-get install dos2unix
find . -name "*.sh" -exec dos2unix {} \;
chmod +x kubernetes/*.sh
```

---

## Configuration Reference

### Environment Variables (Backend)

Configured via `kubernetes/base/backend-deployment-local-final.yaml`:

| Variable | Default | Description |
|----------|---------|-------------|
| SPRING_PROFILES_ACTIVE | k8s | Spring Boot profile (k8s, docker, or local) |
| POSTGRES_HOST | postgres.thesisapp.svc.cluster.local | PostgreSQL hostname |
| POSTGRES_PORT | 5432 | PostgreSQL port |
| MINIO_HOST | minio.thesisapp.svc.cluster.local | MinIO hostname |
| MINIO_PORT | 9000 | MinIO API port |
| MAIL_HOST | mailhog.thesisapp.svc.cluster.local | SMTP hostname (MailHog for testing) |
| MAIL_PORT | 1025 | SMTP port |
| FRONTEND_RESET_PASSWORD_URL | http://thesisapp.local/#/reset-password | Password reset link URL in emails |
| RUNNING_IN_K8S | true | Enables Kubernetes Job execution |
| K8S_NAMESPACE | thesisapp | Kubernetes namespace for job creation |
| CORS_ALLOWED_ORIGINS | http://localhost:5173,http://localhost:5174,... | Allowed CORS origins |

### Email Configuration

The backend sends emails for password reset functionality. In Kubernetes deployment, emails are sent to **MailHog** for testing purposes.

**Configuration Files:**
- `backend/src/main/resources/application-k8s.yaml` - Kubernetes profile config
- `kubernetes/base/backend-deployment-local-final.yaml` - Environment variables

**Password Reset Email URLs:**

The password reset email contains a link that users click to reset their password. This URL **must match** how you access the application.

| Environment | Configuration | URL in Email |
|-------------|---------------|--------------|
| Kubernetes (Ingress) | `FRONTEND_RESET_PASSWORD_URL=http://thesisapp.local/#/reset-password` | `http://thesisapp.local/#/reset-password?token=...` |
| Docker Compose | Set in `application-docker.yaml` | `http://localhost:5173/#/reset-password?token=...` |
| Local Dev | Set in `application-local.yaml` | `http://localhost:5174/#/reset-password?token=...` |

**Important**: If you change how you access the application (e.g., different port or domain), you must update the `FRONTEND_RESET_PASSWORD_URL` environment variable in the deployment configuration and restart the backend pods.

**Testing Email Functionality:**

1. Access the application at `http://thesisapp.local`
2. Click "Forgot Password" and enter a user email
3. Open MailHog at `http://mailhog.thesisapp.local`
4. View the password reset email
5. Click the reset link (should open `http://thesisapp.local/#/reset-password?token=...`)
6. Enter new password and submit

### Ingress Configuration

File: `kubernetes/base/ingress.yaml`

**Key Settings**:
- `proxy-body-size: 1000m` - Allows uploads up to 1GB (for Docker tar files)
- `proxy-read-timeout: 600` - 10-minute timeout for long-running operations
- `proxy-send-timeout: 600` - 10-minute upload timeout

**Routing Rules**:
- `thesisapp.local/api` → backend:8080
- `thesisapp.local/` → frontend:5174
- `mailhog.thesisapp.local` → mailhog:8025
- `minio.thesisapp.local` → minio-console:9001

**How Ingress Routing Works:**

The Ingress Controller uses both **hostname** and **path** to route traffic:

1. **Hostname-based routing**: Different hostnames (`thesisapp.local`, `mailhog.thesisapp.local`) route to different services
2. **Path-based routing**: On `thesisapp.local`, paths starting with `/api` go to backend, all others go to frontend
3. **Order matters**: `/api` prefix rule is checked before the `/` catch-all rule

This allows multiple services to share the same IP address (WSL2 IP) while maintaining separate access points.

### Resource Limits

**Backend**:
- Requests: 2Gi memory, 1000m CPU
- Limits: 4Gi memory, 2000m CPU

**Frontend**:
- Requests: 256Mi memory, 250m CPU
- Limits: 512Mi memory, 500m CPU

Adjust these in deployment YAML files based on your system capacity.

---

## Cleanup

### Stop Services (Preserve Data)

```bash
# Stop Minikube (keeps cluster state)
minikube stop
```

### Delete All Resources (Removes Data)

```bash
# Delete application namespace (removes all pods, services, data)
kubectl delete namespace thesisapp

# Delete Minikube cluster entirely
minikube delete
```

### Delete Docker Images

```bash
# List images
docker images | grep thesis

# Remove images
docker rmi thesis-backend:local thesis-frontend:local
```

---

## MinIO Credentials

Default credentials for MinIO console:

```bash
# Access Key
kubectl get secret minio-secret -n thesisapp -o jsonpath='{.data.access-key}' | base64 -d

# Secret Key
kubectl get secret minio-secret -n thesisapp -o jsonpath='{.data.secret-key}' | base64 -d
```

Default values: `minioadmin` / `minioadmin`

---

## Features

- **Built-in Weka Algorithms**: J48, RandomForest, LinearRegression, Logistic, NaiveBayes, SMO, IBk, and more
- **Custom Python Algorithms**: Upload Docker images with custom ML implementations
- **Automatic Data Preprocessing**: Handles categorical features, missing values, normalization
- **Asynchronous Task Execution**: Long-running training jobs with progress monitoring
- **Model Persistence**: Trained models stored in MinIO object storage
- **User Management**: Role-based access control (ADMIN, USER)
- **Model Sharing**: Share models between users with access control
- **RESTful API**: Comprehensive API with Swagger documentation
- **Production-Ready Deployment**: Kubernetes-native with proper ingress, secrets, and persistent storage

---

## Technology Stack

**Backend**:
- Java 21, Spring Boot 3.x
- Spring Security with JWT authentication
- Spring Data JPA with Hibernate
- Weka ML Library 3.8.6
- Fabric8 Kubernetes Client

**Frontend**:
- TypeScript
- Vite build tool
- Modern ES6+ features

**Infrastructure**:
- Kubernetes 1.25+
- Docker containerization
- PostgreSQL 15 (database)
- MinIO (S3-compatible object storage)
- MailHog (email testing)
- nginx Ingress Controller

**Development**:
- Maven (backend build)
- npm (frontend build)
- Docker BuildKit

---

## License

This project is submitted as part of a thesis requirement. All rights reserved.

---

## Support

For issues, questions, or contributions, please refer to the project repository or contact the author.
