#!/bin/bash

# ThesisApp Docker Startup Script
# Compatible with Linux and Windows (via WSL/Git Bash)

echo "🚀 Starting ThesisApp with Docker Compose..."

# --- Check Docker ---
if ! command -v docker &> /dev/null; then
    echo "❌ Docker not found. Please install Docker Desktop."
    exit 1
fi

# --- Check docker-compose (legacy vs new plugin) ---
DOCKER_COMPOSE_CMD="docker compose"
if ! docker compose version &> /dev/null; then
    if command -v docker-compose &> /dev/null; then
        DOCKER_COMPOSE_CMD="docker-compose"
    else
        echo "❌ docker-compose not found. Please install Docker Compose."
        exit 1
    fi
fi
echo "🛠️  Using: $DOCKER_COMPOSE_CMD"

# --- Ensure Maven is installed (optional) ---
if ! command -v mvn &> /dev/null; then
    echo "⚠️ Maven not found. Installing Maven..."
    if [[ "$OSTYPE" == "linux-gnu"* ]]; then
        sudo apt-get update && sudo apt-get install -y maven
    elif [[ "$OSTYPE" == "darwin"* ]]; then
        brew install maven
    else
        echo "⚠️ Please install Maven manually (Windows or unsupported OS)."
    fi
fi

# --- Ensure mvnw is executable ---
if [ -f "backend/mvnw" ]; then
    chmod +x backend/mvnw
    echo "✅ Fixed permissions on backend/mvnw"
fi

# --- Create .env if missing ---
if [ ! -f .env ]; then
    echo "📝 Creating .env file from .env.example..."
    cp .env.example .env
    echo "✅ Please review and modify .env file if needed"
fi

# --- Stop existing containers ---
echo "🛑 Stopping existing containers..."
$DOCKER_COMPOSE_CMD down

# --- Build and start ---
echo "🔨 Building and starting services..."
$DOCKER_COMPOSE_CMD up --build -d

echo ""
echo "✅ Services are starting up..."
echo "📊 Backend will be available at: http://localhost:8080"
echo "🌐 Frontend will be available at: http://localhost:5173"
echo "📧 MailHog will be available at: http://localhost:8025"
echo "🗄️  MinIO Console will be available at: http://localhost:9001"
echo ""
echo "📋 To view logs, run: $DOCKER_COMPOSE_CMD logs -f"
echo "🛑 To stop services, run: $DOCKER_COMPOSE_CMD down"
echo ""
echo "⏳ Please wait a few moments for all services to be ready..."
