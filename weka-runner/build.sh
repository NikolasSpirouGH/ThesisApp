#!/bin/bash
# Build script for Weka Runner Docker image

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "🔧 Building Weka Runner JAR..."
mvn clean package -DskipTests -B

echo "🐳 Building Docker image..."
docker build -t thesisapp/weka-runner:latest .

echo "✅ Weka Runner image built successfully: thesisapp/weka-runner:latest"

# If running in minikube, load the image
if command -v minikube &> /dev/null; then
    echo "📦 Loading image into Minikube..."
    minikube image load thesisapp/weka-runner:latest
    echo "✅ Image loaded into Minikube"
fi
