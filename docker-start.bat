@echo off
REM ThesisApp Docker Startup Script for Windows
REM Compatible with Windows Command Prompt and PowerShell

echo 🚀 Starting ThesisApp with Docker Compose...

REM Check if docker is available
docker --version >nul 2>&1
if errorlevel 1 (
    echo ❌ Docker not found. Please install Docker Desktop for Windows.
    pause
    exit /b 1
)

REM Create .env from example if it doesn't exist
if not exist .env (
    echo 📝 Creating .env file from .env.example...
    copy .env.example .env >nul
    echo ✅ Please review and modify .env file if needed
)

REM Use docker compose (newer) or docker-compose (legacy)
set DOCKER_COMPOSE_CMD=docker compose
docker compose version >nul 2>&1
if errorlevel 1 (
    set DOCKER_COMPOSE_CMD=docker-compose
)

echo 🛠️  Using: %DOCKER_COMPOSE_CMD%

REM Stop any existing containers
echo 🛑 Stopping existing containers...
%DOCKER_COMPOSE_CMD% down

REM Build and start services
echo 🔨 Building and starting services...
%DOCKER_COMPOSE_CMD% up --build -d

echo.
echo ✅ Services are starting up...
echo 📊 Backend will be available at: http://localhost:8080
echo 🌐 Frontend will be available at: http://localhost:5173
echo 📧 MailHog will be available at: http://localhost:8025
echo 🗄️  MinIO Console will be available at: http://localhost:9001
echo.
echo 📋 To view logs, run: %DOCKER_COMPOSE_CMD% logs -f
echo 🛑 To stop services, run: %DOCKER_COMPOSE_CMD% down
echo.
echo ⏳ Please wait a few moments for all services to be ready...
pause