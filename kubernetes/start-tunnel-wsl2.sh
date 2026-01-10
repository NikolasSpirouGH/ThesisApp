#!/bin/bash
set -euo pipefail

echo "🚇 Starting WSL2-friendly Port Forward (NO hosts editing)"
echo ""

WSL_IP=$(hostname -I | awk '{print $1}')
MINIKUBE_IP=$(minikube ip 2>/dev/null || true)

if [ -z "${MINIKUBE_IP:-}" ]; then
  echo "❌ Minikube is not running. Start it with: minikube start"
  exit 1
fi

echo "✅ Minikube IP: $MINIKUBE_IP"
echo "✅ WSL2 IP:     $WSL_IP"
echo ""
echo "📝 One-time Windows hosts entry (do it manually):"
echo "   $WSL_IP thesisapp.local mailhog.thesisapp.local minio.thesisapp.local"
echo ""
echo "🌐 Then open in Windows browser:"
echo "   📱 Frontend:      http://thesisapp.local"
echo "   🔧 Backend API:   http://thesisapp.local/api"
echo "   📧 MailHog:       http://mailhog.thesisapp.local"
echo "   💾 MinIO Console: http://minio.thesisapp.local"
echo ""
echo "⏳ Waiting for ingress-nginx controller..."
kubectl wait --namespace ingress-nginx \
  --for=condition=ready pod \
  --selector=app.kubernetes.io/component=controller \
  --timeout=120s

echo "✅ Ingress controller is ready"
echo ""
echo "🚀 Port-forwarding 0.0.0.0:80 → ingress-nginx-controller:80 (requires sudo)"
echo "⚠️ Keep this terminal open. Ctrl+C to stop."
echo ""

sudo -E env "PATH=$PATH" HOME="$HOME" \
  kubectl port-forward -n ingress-nginx \
  --address 0.0.0.0 \
  svc/ingress-nginx-controller 80:80
