#!/bin/bash
# Local Kubernetes Deployment Script for ThesisApp

set -e

echo "🚀 ThesisApp Local Kubernetes Deployment"
echo "========================================"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Check if kubectl is installed
if ! command -v kubectl &> /dev/null; then
    echo -e "${RED}❌ kubectl not found. Please install kubectl first.${NC}"
    exit 1
fi

# Check if cluster is running
if ! kubectl cluster-info &> /dev/null; then
    echo -e "${RED}❌ Kubernetes cluster not running.${NC}"
    echo "Please start one of:"
    echo "  - Docker Desktop Kubernetes"
    echo "  - minikube start"
    echo "  - k3d cluster create"
    exit 1
fi

echo -e "${GREEN}✅ Kubernetes cluster detected${NC}"
kubectl cluster-info

# Detect cluster type
CLUSTER_TYPE="unknown"
if kubectl config current-context | grep -q "docker-desktop"; then
    CLUSTER_TYPE="docker-desktop"
elif kubectl config current-context | grep -q "minikube"; then
    CLUSTER_TYPE="minikube"
elif kubectl config current-context | grep -q "k3d"; then
    CLUSTER_TYPE="k3d"
fi

echo -e "${GREEN}Cluster type: ${CLUSTER_TYPE}${NC}"

# Navigate to project root
cd "$(dirname "$0")/.."

# Step 1: Build Docker images
echo ""
echo "📦 Step 1: Building Docker images..."
echo "-----------------------------------"

cd backend
docker build -t thesis-backend:local -f Dockerfile . || {
    echo -e "${RED}❌ Backend build failed${NC}"
    exit 1
}
echo -e "${GREEN}✅ Backend image built${NC}"

cd ../frontend
docker build -t thesis-frontend:local -f Dockerfile . || {
    echo -e "${RED}❌ Frontend build failed${NC}"
    exit 1
}
echo -e "${GREEN}✅ Frontend image built${NC}"

cd ..

# Step 2: Load images into cluster
echo ""
echo "📥 Step 2: Loading images into cluster..."
echo "----------------------------------------"

if [ "$CLUSTER_TYPE" = "minikube" ]; then
    minikube image load thesis-backend:local
    minikube image load thesis-frontend:local
    echo -e "${GREEN}✅ Images loaded into Minikube${NC}"
elif [ "$CLUSTER_TYPE" = "k3d" ]; then
    CLUSTER_NAME=$(kubectl config current-context | sed 's/k3d-//')
    k3d image import thesis-backend:local -c "$CLUSTER_NAME"
    k3d image import thesis-frontend:local -c "$CLUSTER_NAME"
    echo -e "${GREEN}✅ Images imported into k3d${NC}"
else
    echo -e "${GREEN}✅ Images available (Docker Desktop)${NC}"
fi

# Step 3: Update manifests for local use
echo ""
echo "📝 Step 3: Preparing Kubernetes manifests..."
echo "-------------------------------------------"

cd kubernetes/base

# Create temporary local versions
cp backend-deployment.yaml backend-deployment-local.yaml
cp frontend-deployment.yaml frontend-deployment-local.yaml

# Update image references and pull policy
sed -i.bak 's|image: .*thesis-backend.*|image: thesis-backend:local|g' backend-deployment-local.yaml
sed -i.bak 's|image: .*thesis-frontend.*|image: thesis-frontend:local|g' frontend-deployment-local.yaml
sed -i.bak 's|imagePullPolicy: Always|imagePullPolicy: Never|g' backend-deployment-local.yaml
sed -i.bak 's|imagePullPolicy: Always|imagePullPolicy: Never|g' frontend-deployment-local.yaml

# For local testing, use hostPath instead of PVC for shared storage
cat > backend-deployment-local-pvc-fix.yaml <<'EOF'
# Replace PVC with hostPath for local testing
# Apply this after backend-deployment-local.yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: backend-volume-override
  namespace: thesisapp
data:
  note: "Using hostPath for local development"
EOF

echo -e "${GREEN}✅ Manifests prepared for local deployment${NC}"

# Step 4: Create namespace
echo ""
echo "🏗️  Step 4: Creating namespace..."
echo "--------------------------------"

kubectl apply -f namespace.yaml
echo -e "${GREEN}✅ Namespace created${NC}"

# Step 5: Create secrets
echo ""
echo "🔐 Step 5: Creating secrets..."
echo "-----------------------------"

# Delete existing secrets if they exist
kubectl delete secret postgres-secret -n thesisapp 2>/dev/null || true
kubectl delete secret minio-secret -n thesisapp 2>/dev/null || true
kubectl delete secret email-secret -n thesisapp 2>/dev/null || true

kubectl create secret generic postgres-secret \
  --from-literal=username=postgres \
  --from-literal=password=postgres123 \
  -n thesisapp

kubectl create secret generic minio-secret \
  --from-literal=access-key=minioadmin \
  --from-literal=secret-key=minioadmin123 \
  -n thesisapp

kubectl create secret generic email-secret \
  --from-literal=username=test@example.com \
  --from-literal=password=testpass \
  -n thesisapp

echo -e "${GREEN}✅ Secrets created${NC}"

# Step 6: Deploy ConfigMap
echo ""
echo "⚙️  Step 6: Deploying ConfigMap..."
echo "--------------------------------"

kubectl apply -f configmap.yaml
echo -e "${GREEN}✅ ConfigMap deployed${NC}"

# Step 7: Deploy PostgreSQL
echo ""
echo "🗄️  Step 7: Deploying PostgreSQL..."
echo "----------------------------------"

kubectl apply -f postgres-statefulset.yaml
echo -e "${YELLOW}⏳ Waiting for PostgreSQL to be ready...${NC}"
kubectl wait --for=condition=ready pod -l app=postgres -n thesisapp --timeout=300s || {
    echo -e "${RED}❌ PostgreSQL failed to start${NC}"
    kubectl logs -n thesisapp -l app=postgres --tail=50
    exit 1
}
echo -e "${GREEN}✅ PostgreSQL is ready${NC}"

# Step 8: Deploy MinIO
echo ""
echo "📦 Step 8: Deploying MinIO..."
echo "----------------------------"

kubectl apply -f minio-statefulset.yaml
echo -e "${YELLOW}⏳ Waiting for MinIO to be ready...${NC}"
kubectl wait --for=condition=ready pod -l app=minio -n thesisapp --timeout=300s || {
    echo -e "${RED}❌ MinIO failed to start${NC}"
    kubectl logs -n thesisapp -l app=minio --tail=50
    exit 1
}
echo -e "${GREEN}✅ MinIO is ready${NC}"

# Step 9: Deploy Backend
echo ""
echo "⚙️  Step 9: Deploying Backend..."
echo "------------------------------"

# Use hostPath for local shared storage
cat > backend-deployment-local-final.yaml <<EOF
$(cat backend-deployment-local.yaml | sed '/persistentVolumeClaim:/,+1d' | sed '/- name: shared-storage/a\      hostPath:\n        path: /tmp/thesisapp-shared\n        type: DirectoryOrCreate')
EOF

kubectl apply -f backend-deployment-local-final.yaml
echo -e "${YELLOW}⏳ Waiting for Backend to be ready...${NC}"
sleep 10  # Give it time to pull image and start

# Check backend status
kubectl rollout status deployment/backend -n thesisapp --timeout=300s || {
    echo -e "${RED}❌ Backend failed to start${NC}"
    echo "Logs:"
    kubectl logs -n thesisapp -l app=backend --tail=100
    exit 1
}
echo -e "${GREEN}✅ Backend is ready${NC}"

# Step 10: Deploy Frontend
echo ""
echo "🎨 Step 10: Deploying Frontend..."
echo "--------------------------------"

kubectl apply -f frontend-deployment-local.yaml
echo -e "${YELLOW}⏳ Waiting for Frontend to be ready...${NC}"
kubectl rollout status deployment/frontend -n thesisapp --timeout=300s || {
    echo -e "${RED}❌ Frontend failed to start${NC}"
    kubectl logs -n thesisapp -l app=frontend --tail=50
    exit 1
}
echo -e "${GREEN}✅ Frontend is ready${NC}"

# Step 11: Display status
echo ""
echo "📊 Deployment Summary"
echo "===================="
kubectl get all -n thesisapp

echo ""
echo -e "${GREEN}🎉 ThesisApp deployed successfully!${NC}"
echo ""
echo "Access your application:"
echo "------------------------"
echo ""
echo "Frontend:"
echo "  kubectl port-forward -n thesisapp svc/frontend 5173:5173"
echo "  Then open: http://localhost:5173"
echo ""
echo "Backend API:"
echo "  kubectl port-forward -n thesisapp svc/backend 8080:8080"
echo "  Then open: http://localhost:8080/actuator/health"
echo ""
echo "MinIO Console:"
echo "  kubectl port-forward -n thesisapp svc/minio 9001:9001"
echo "  Then open: http://localhost:9001"
echo "  Login: minioadmin / minioadmin123"
echo ""
echo "PostgreSQL:"
echo "  kubectl port-forward -n thesisapp svc/postgres 5432:5432"
echo "  Then connect: psql -h localhost -U postgres -d thesis_db"
echo ""
echo "Useful commands:"
echo "----------------"
echo "  View logs:     kubectl logs -f -n thesisapp deployment/backend"
echo "  Get pods:      kubectl get pods -n thesisapp"
echo "  Restart:       kubectl rollout restart deployment/backend -n thesisapp"
echo "  Clean up:      kubectl delete namespace thesisapp"
echo ""
