#!/bin/bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

require_cmd() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Missing dependency: $1" >&2
    exit 1
  fi
}

require_cmd docker
require_cmd kubectl
require_cmd minikube
require_cmd mvn

# -----------------------------
# Start Minikube if needed
# -----------------------------
if ! minikube status >/dev/null 2>&1; then
  echo "⚙️  Starting Minikube..."
  minikube start
fi

echo "🚀 Deploying ThesisApp to Minikube..."

# -----------------------------
# Enable Ingress addon if not enabled
# -----------------------------
if ! minikube addons list | grep -q "ingress.*enabled"; then
  echo "🔌 Enabling Ingress addon..."
  minikube addons enable ingress
fi

# -----------------------------
# Patch Ingress Controller to LoadBalancer (for WSL2 tunnel)
# -----------------------------
echo "🔧 Patching ingress-nginx-controller to LoadBalancer type..."
kubectl wait --namespace ingress-nginx \
  --for=condition=ready pod \
  --selector=app.kubernetes.io/component=controller \
  --timeout=120s >/dev/null 2>&1 || true

kubectl patch svc ingress-nginx-controller -n ingress-nginx \
  -p '{"spec":{"type":"LoadBalancer"}}' >/dev/null 2>&1 || echo "  (already patched or not ready yet)"

# -----------------------------
# Build backend JAR + Docker image
# -----------------------------
echo "🧪 Building backend JAR with Maven..."
cd "$ROOT_DIR/backend"
mvn clean package -DskipTests

echo "📦 Building backend Docker image on host..."
DOCKER_BUILDKIT=1 docker build --progress=plain -t thesis-backend:local .

# -----------------------------
# Build frontend Docker image
# -----------------------------
echo "📦 Building frontend Docker image on host..."
cd "$ROOT_DIR/frontend"
DOCKER_BUILDKIT=1 docker build --progress=plain -t thesis-frontend:local .

echo "✅ Images built on host!"

# -----------------------------
# Ensure namespace exists early (so scaling doesn't error)
# -----------------------------
echo "📋 Creating/Updating namespace..."
kubectl apply -f "$ROOT_DIR/kubernetes/base/namespace.yaml"

# -----------------------------
# Force-remove old cached images in Minikube (containerd-safe)
# (because you reuse :local and containerd may not overwrite tags)
# -----------------------------
echo "🧹 Forcing Minikube to drop old cached :local images (if any)..."

# Scale down backend/frontend so no pods hold the old images
kubectl -n thesisapp scale deploy/backend --replicas=0 >/dev/null 2>&1 || true
kubectl -n thesisapp scale deploy/frontend --replicas=0 >/dev/null 2>&1 || true

kubectl -n thesisapp wait --for=delete pod -l app=backend --timeout=120s >/dev/null 2>&1 || true
kubectl -n thesisapp wait --for=delete pod -l app=frontend --timeout=120s >/dev/null 2>&1 || true

# Remove any leftover containers referencing our images (even stopped)
minikube ssh -- "sudo crictl ps -a | awk 'tolower(\$0) ~ /thesis-backend|thesis-frontend/ {print \$1}' | xargs -r sudo crictl rm" >/dev/null 2>&1 || true

# Remove ALL digests tagged as thesis-backend:local
minikube ssh -- "sudo crictl images -v \
  | awk '
      BEGIN{inblk=0}
      /RepoTags: thesis-backend:local/{inblk=1}
      inblk && /^ID: sha256:/{print \$2; inblk=0}
    ' \
  | xargs -r sudo crictl rmi" >/dev/null 2>&1 || true

# Remove ALL digests tagged as thesis-frontend:local
minikube ssh -- "sudo crictl images -v \
  | awk '
      BEGIN{inblk=0}
      /RepoTags: thesis-frontend:local/{inblk=1}
      inblk && /^ID: sha256:/{print \$2; inblk=0}
    ' \
  | xargs -r sudo crictl rmi" >/dev/null 2>&1 || true

# Optional: prune dangling images
minikube ssh -- "sudo crictl rmi --prune" >/dev/null 2>&1 || true

echo "✅ Minikube image cache cleanup done."

# -----------------------------
# Load images into Minikube
# -----------------------------
echo "📤 Loading backend image into Minikube..."
minikube image load thesis-backend:local

echo "📤 Loading frontend image into Minikube..."
minikube image load thesis-frontend:local

echo "✅ Images loaded into Minikube!"

echo "🔎 Minikube runtime images (containerd):"
minikube ssh -- "sudo crictl images | awk 'NR==1 || /thesis-backend|thesis-frontend/'" || true

# -----------------------------
# Secrets (idempotent)
# -----------------------------
echo "🔐 Creating/Updating secrets..."
kubectl create secret generic postgres-secret \
  --from-literal=username=postgres \
  --from-literal=password=password \
  -n thesisapp --dry-run=client -o yaml | kubectl apply -f -

kubectl create secret generic minio-secret \
  --from-literal=access-key=minioadmin \
  --from-literal=secret-key=minioadmin \
  -n thesisapp --dry-run=client -o yaml | kubectl apply -f -

echo "📧 Skipping email secret (using MailHog)"

# -----------------------------
# Deploy manifests
# -----------------------------
echo "☸️ Deploying Kubernetes manifests..."
cd "$ROOT_DIR/kubernetes/base"

kubectl apply -n thesisapp -f configmap.yaml
kubectl apply -n thesisapp -f postgres-statefulset.yaml
kubectl apply -n thesisapp -f minio-statefulset.yaml
kubectl apply -n thesisapp -f mailhog-deployment.yaml
kubectl apply -n thesisapp -f backend-deployment-local-final.yaml
kubectl apply -n thesisapp -f frontend-deployment.yaml
kubectl apply -f ingress.yaml  # Ingress for routing

# -----------------------------
# Ensure pods come back up and pick the new image
# -----------------------------
echo "🔁 Restarting backend/frontend to ensure new images are used..."
kubectl -n thesisapp rollout restart deploy/backend || true
kubectl -n thesisapp rollout restart deploy/frontend || true
kubectl -n thesisapp rollout status deploy/backend
kubectl -n thesisapp rollout status deploy/frontend

echo ""
echo "✅ Deployment complete!"
echo ""
echo "📊 Checking pod status..."
kubectl get pods -n thesisapp

echo ""
echo "🧾 Verifying running backend imageID (source of truth):"
kubectl get pods -n thesisapp -l app=backend -o jsonpath='{range .items[*]}{.metadata.name}{"\n  image: "}{.spec.containers[0].image}{"\n  imageID: "}{.status.containerStatuses[0].imageID}{"\n\n"}{end}' || true

echo ""
echo "💡 Next steps:"
echo "   1. Watch pods: kubectl get pods -n thesisapp -w"
echo ""
echo "   2. Start Ingress tunnel (required for WSL2):"
echo "      cd ~/ThesisApp && ./kubernetes/start-tunnel.sh"
echo ""
echo "   3. Access services from Windows browser:"
echo "      Frontend:      http://thesisapp.local"
echo "      Backend API:   http://thesisapp.local/api"
echo "      Swagger:       http://thesisapp.local/api/swagger-ui/index.html"
echo "      MailHog UI:    http://mailhog.thesisapp.local"
echo "      MinIO Console: http://minio.thesisapp.local"
echo ""
echo "   Note: Keep the tunnel terminal open while using the app!"