#!/bin/bash

# ThesisApp Docker Startup Script
# Compatible with Linux and Windows (via WSL/Git Bash)

echo "🚀 Starting ThesisApp with Docker Compose..."

# Check if docker-compose is available
if ! command -v docker-compose &> /dev/null && ! command -v docker &> /dev/null; then
    echo "❌ Docker or docker-compose not found. Please install Docker Desktop."
    exit 1
fi

# Create .env from example if it doesn't exist
if [ ! -f .env ]; then
    echo "📝 Creating .env file from .env.example..."
    cp .env.example .env
    echo "✅ Please review and modify .env file if needed"
fi

# Use docker compose (newer) or docker-compose (legacy)
DOCKER_COMPOSE_CMD="docker compose"
if ! command -v docker &> /dev/null || ! docker compose version &> /dev/null; then
    DOCKER_COMPOSE_CMD="docker-compose"
fi

echo "🛠️  Using: $DOCKER_COMPOSE_CMD"

# Stop any existing containers
echo "🛑 Stopping existing containers..."
$DOCKER_COMPOSE_CMD down

# Build and start services
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