# Local Kubernetes Testing Guide

This guide shows you how to test ThesisApp on Kubernetes locally before deploying to the cloud.

## Choose Your Local Kubernetes Option

| Option | Best For | Resource Usage | Setup Time |
|--------|----------|----------------|------------|
| **Docker Desktop** | Mac/Windows users | Medium | 5 min |
| **Minikube** | Linux or more control | Medium-High | 10 min |
| **k3d** | Lightweight/Fast | Low | 5 min |

---

## Option 1: Docker Desktop (Easiest - Recommended for Testing)

### Prerequisites
- Docker Desktop installed
- At least 8GB RAM, 4 CPU cores allocated to Docker

### Setup Steps

1. **Enable Kubernetes in Docker Desktop**
   ```bash
   # Mac: Docker Desktop → Settings → Kubernetes → Enable Kubernetes
   # Windows: Docker Desktop → Settings → Kubernetes → Enable Kubernetes
   # Wait 2-5 minutes for Kubernetes to start
   ```

2. **Verify Kubernetes is running**
   ```bash
   kubectl cluster-info
   kubectl get nodes

   # Should show:
   # NAME             STATUS   ROLES           AGE   VERSION
   # docker-desktop   Ready    control-plane   1m    v1.28.2
   ```

3. **Continue to "Deploy ThesisApp Locally" section below**

---

## Option 2: Minikube (Cross-platform)

### Installation

**Linux:**
```bash
curl -LO https://storage.googleapis.com/minikube/releases/latest/minikube-linux-amd64
sudo install minikube-linux-amd64 /usr/local/bin/minikube
```

**Mac:**
```bash
brew install minikube
```

**Windows:**
```powershell
choco install minikube
```

### Start Minikube

```bash
# Start with appropriate resources
minikube start \
  --cpus=4 \
  --memory=8192 \
  --disk-size=20g \
  --driver=docker

# Verify
minikube status
kubectl get nodes
```

### Enable Addons

```bash
# Enable ingress for accessing the app
minikube addons enable ingress

# Enable metrics for monitoring
minikube addons enable metrics-server
```

---

## Option 3: k3d (Lightweight - Good for CI/CD)

### Installation

```bash
# Linux/Mac
curl -s https://raw.githubusercontent.com/k3d-io/k3d/main/install.sh | bash

# Or with brew
brew install k3d
```

### Start k3d Cluster

```bash
k3d cluster create thesisapp \
  --api-port 6550 \
  --servers 1 \
  --agents 2 \
  --port 8080:80@loadbalancer \
  --port 5173:5173@loadbalancer

# Verify
kubectl get nodes
```

---

## Deploy ThesisApp Locally

### Step 1: Build Docker Images Locally

```bash
cd /home/nspyrou/ThesisApp

# Build backend image
cd backend
docker build -t thesis-backend:local .

# Build frontend image
cd ../frontend
docker build -t thesis-frontend:local .

cd ..
```

### Step 2: Load Images into Local Kubernetes

**For Docker Desktop:**
```bash
# Images are already available (same Docker daemon)
# No action needed!
```

**For Minikube:**
```bash
# Load images into Minikube
minikube image load thesis-backend:local
minikube image load thesis-frontend:local

# Verify
minikube image ls | grep thesis
```

**For k3d:**
```bash
# Import images into k3d
k3d image import thesis-backend:local -c thesisapp
k3d image import thesis-frontend:local -c thesisapp
```

### Step 3: Update Image References for Local Testing

```bash
cd kubernetes/base

# Update backend-deployment.yaml
sed -i.bak 's|<YOUR_REGISTRY>/thesis-backend:latest|thesis-backend:local|g' backend-deployment.yaml

# Update frontend-deployment.yaml
sed -i.bak 's|<YOUR_REGISTRY>/thesis-frontend:latest|thesis-frontend:local|g' frontend-deployment.yaml

# Set imagePullPolicy to Never (don't try to pull from registry)
sed -i.bak 's|imagePullPolicy: Always|imagePullPolicy: Never|g' backend-deployment.yaml
sed -i.bak 's|imagePullPolicy: Always|imagePullPolicy: Never|g' frontend-deployment.yaml
```

### Step 4: Create Namespace

```bash
kubectl apply -f namespace.yaml
```

### Step 5: Create Secrets

```bash
# PostgreSQL secret
kubectl create secret generic postgres-secret \
  --from-literal=username=postgres \
  --from-literal=password=postgres123 \
  -n thesisapp

# MinIO secret
kubectl create secret generic minio-secret \
  --from-literal=access-key=minioadmin \
  --from-literal=secret-key=minioadmin123 \
  -n thesisapp

# Email secret (optional for local testing)
kubectl create secret generic email-secret \
  --from-literal=username=test@example.com \
  --from-literal=password=testpass \
  -n thesisapp
```

### Step 6: Deploy Application

```bash
# Apply all manifests
kubectl apply -f configmap.yaml
kubectl apply -f postgres-statefulset.yaml
kubectl apply -f minio-statefulset.yaml

# Wait for database and storage to be ready
kubectl wait --for=condition=ready pod -l app=postgres -n thesisapp --timeout=300s
kubectl wait --for=condition=ready pod -l app=minio -n thesisapp --timeout=300s

# Deploy backend and frontend
kubectl apply -f backend-deployment.yaml
kubectl apply -f frontend-deployment.yaml
```

### Step 7: Check Deployment Status

```bash
# Watch pods start up
kubectl get pods -n thesisapp -w

# You should see:
# NAME                        READY   STATUS    RESTARTS   AGE
# postgres-0                  1/1     Running   0          2m
# minio-0                     1/1     Running   0          2m
# backend-xxxxxxxxx-xxxxx     1/1     Running   0          1m
# frontend-xxxxxxxxx-xxxxx    1/1     Running   0          1m

# Check logs if any pod is not running
kubectl logs -n thesisapp deployment/backend
kubectl logs -n thesisapp deployment/frontend
```

### Step 8: Access the Application

**Method 1: Port Forwarding (Simplest)**

```bash
# Forward frontend
kubectl port-forward -n thesisapp svc/frontend 5173:5173

# Forward backend (in another terminal)
kubectl port-forward -n thesisapp svc/backend 8080:8080

# Access in browser
open http://localhost:5173
```

**Method 2: Using Minikube Service (Minikube only)**

```bash
# Get service URL
minikube service frontend -n thesisapp --url

# Open in browser
minikube service frontend -n thesisapp
```

**Method 3: Ingress (More Production-like)**

```bash
# Only if you enabled ingress addon
kubectl apply -f ingress.yaml

# Get ingress IP
kubectl get ingress -n thesisapp

# Add to /etc/hosts (Linux/Mac) or C:\Windows\System32\drivers\etc\hosts (Windows)
# <INGRESS_IP> thesisapp.local api.thesisapp.local

# Access
open http://thesisapp.local
```

---

## Testing ML Training Locally

Since we're using Kubernetes Jobs instead of Docker-in-Docker, you need to update the code.

### Quick Fix for Local Testing

For now, keep using Docker-in-Docker by mounting Docker socket:

```bash
# Edit backend-deployment.yaml and add:
```

```yaml
# Add to backend container spec:
volumeMounts:
- name: docker-socket
  mountPath: /var/run/docker.sock

# Add to volumes:
volumes:
- name: docker-socket
  hostPath:
    path: /var/run/docker.sock
    type: Socket
```

Then redeploy:
```bash
kubectl apply -f backend-deployment.yaml
```

---

## Common Issues & Solutions

### Issue 1: Pods in ImagePullBackOff

```bash
# Check the error
kubectl describe pod <pod-name> -n thesisapp

# Solution: Make sure imagePullPolicy is set to Never
# and images are loaded into cluster
```

### Issue 2: Backend CrashLoopBackOff

```bash
# Check logs
kubectl logs -n thesisapp deployment/backend

# Common causes:
# - Database not ready yet (wait longer)
# - Wrong environment variables
# - Missing secrets

# Check backend can reach database
kubectl exec -n thesisapp deployment/backend -- nc -zv postgres 5432
```

### Issue 3: PersistentVolumeClaim Pending

```bash
# Check PVC status
kubectl get pvc -n thesisapp

# For local testing, you may need to use hostPath instead
# Edit backend-deployment.yaml and replace PVC with:
```

```yaml
volumes:
- name: shared-storage
  hostPath:
    path: /tmp/thesisapp-shared
    type: DirectoryOrCreate
```

### Issue 4: MinIO/PostgreSQL Won't Start

```bash
# Check persistent volume
kubectl get pv,pvc -n thesisapp

# For Minikube, you may need to create StorageClass
kubectl apply -f - <<EOF
apiVersion: storage.k8s.io/v1
kind: StorageClass
metadata:
  name: standard
provisioner: k8s.io/minikube-hostpath
volumeBindingMode: Immediate
EOF
```

---

## Useful Commands

### Monitoring

```bash
# Watch all resources
kubectl get all -n thesisapp

# Stream logs
kubectl logs -f -n thesisapp deployment/backend
kubectl logs -f -n thesisapp deployment/frontend

# Get pod details
kubectl describe pod <pod-name> -n thesisapp

# Execute into pod
kubectl exec -it -n thesisapp deployment/backend -- /bin/bash
```

### Database Access

```bash
# Connect to PostgreSQL
kubectl exec -it -n thesisapp postgres-0 -- psql -U postgres -d thesis_db

# Backup database
kubectl exec -n thesisapp postgres-0 -- pg_dump -U postgres thesis_db > backup.sql

# Restore database
cat backup.sql | kubectl exec -i -n thesisapp postgres-0 -- psql -U postgres -d thesis_db
```

### MinIO Access

```bash
# Port-forward MinIO console
kubectl port-forward -n thesisapp svc/minio 9001:9001

# Open browser
open http://localhost:9001
# Login: minioadmin / minioadmin123
```

### Cleanup

```bash
# Delete all resources
kubectl delete namespace thesisapp

# Or individual resources
kubectl delete -f backend-deployment.yaml
kubectl delete -f frontend-deployment.yaml
kubectl delete -f postgres-statefulset.yaml
kubectl delete -f minio-statefulset.yaml
```

### Restart Deployment

```bash
# Restart backend (useful after code changes)
kubectl rollout restart deployment/backend -n thesisapp

# Check rollout status
kubectl rollout status deployment/backend -n thesisapp
```

---

## Development Workflow

### Making Code Changes

```bash
# 1. Make changes to code
vim backend/src/main/java/...

# 2. Rebuild image
cd backend
docker build -t thesis-backend:local .

# 3. Reload image (Minikube only)
minikube image load thesis-backend:local

# 4. Restart deployment
kubectl rollout restart deployment/backend -n thesisapp

# 5. Watch new pods come up
kubectl get pods -n thesisapp -w
```

### Hot Reload (Development)

For faster development, use port-forwarding and run locally:

```bash
# Run backend locally
cd backend
./mvnw spring-boot:run

# Run frontend locally
cd frontend
npm run dev

# Port-forward only database and MinIO
kubectl port-forward -n thesisapp svc/postgres 5432:5432
kubectl port-forward -n thesisapp svc/minio 9000:9000
```

---

## Performance Testing

```bash
# Check resource usage
kubectl top pods -n thesisapp
kubectl top nodes

# Load testing with curl
for i in {1..100}; do
  curl http://localhost:8080/actuator/health &
done

# Or use Apache Bench
ab -n 1000 -c 10 http://localhost:8080/actuator/health
```

---

## Next Steps After Local Testing

Once everything works locally:

1. ✅ **Test all features** (training, prediction, file uploads)
2. ✅ **Verify database persistence** (restart pods, data should remain)
3. ✅ **Test scaling** (`kubectl scale deployment backend --replicas=3`)
4. ✅ **Ready for cloud!** Deploy to AWS EKS, GCP GKE, or Azure AKS

---

## Comparison: Docker Compose vs Kubernetes Local

| Feature | Docker Compose | Kubernetes Local |
|---------|----------------|------------------|
| Start time | ~30s | ~2min |
| Resource usage | Lower | Higher |
| Production parity | Low | High ✅ |
| Learning curve | Easy | Medium |
| Cloud-ready | No | Yes ✅ |

**Recommendation:** Use Docker Compose for quick development, Kubernetes for testing before cloud deployment.
