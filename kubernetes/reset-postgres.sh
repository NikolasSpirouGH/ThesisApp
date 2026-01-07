#!/bin/bash
set -e

echo "🧹 Cleaning up Postgres completely..."

# Scale down backend to release connections
echo "📉 Scaling down backend..."
kubectl scale deploy/backend -n thesisapp --replicas=0

# Wait for backend pods to terminate
kubectl wait --for=delete pod -l app=backend -n thesisapp --timeout=60s 2>/dev/null || true

# Delete StatefulSet
echo "🗑️  Deleting Postgres StatefulSet..."
kubectl delete statefulset postgres -n thesisapp --ignore-not-found=true

# Wait for pod to be deleted
kubectl wait --for=delete pod -l app=postgres -n thesisapp --timeout=60s 2>/dev/null || true

# Get the PV name
PV_NAME=$(kubectl get pvc postgres-storage-postgres-0 -n thesisapp -o jsonpath='{.spec.volumeName}' 2>/dev/null || echo "")

# Delete PVC
echo "🗑️  Deleting Postgres PVC..."
kubectl delete pvc postgres-storage-postgres-0 -n thesisapp --ignore-not-found=true

# Delete the PV if it exists
if [ -n "$PV_NAME" ]; then
  echo "🗑️  Deleting Postgres PV: $PV_NAME..."
  kubectl delete pv "$PV_NAME" --ignore-not-found=true
fi

# Also clean up any Released PVs
echo "🧹 Cleaning up Released PVs..."
kubectl get pv | grep Released | grep postgres | awk '{print $1}' | xargs -r kubectl delete pv

echo "✅ Postgres cleanup complete!"
echo ""
echo "Now run: ./deploy-to-minikube.sh"
