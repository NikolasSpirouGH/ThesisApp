# Docker Setup Guide

This guide explains how to run the ThesisApp using Docker on different platforms.

## Prerequisites

- Docker Desktop (Windows/Mac) or Docker Engine (Linux)
- Docker Compose
- Git (with submodules support)

## Quick Start

### Linux / macOS / WSL (Recommended)

Use the provided startup script:

```bash
./docker-start.sh
```

This script will:
- Initialize Git submodules
- Set correct file permissions
- Build and start all services
- Export UID/GID for proper file ownership

### Windows (PowerShell/CMD)

If you're running Docker Desktop on Windows (not WSL):

```powershell
# Initialize submodules
git submodule update --init --recursive

# Start services
docker compose up --build -d
```

**Note for Windows users:** The `user:` configuration in `docker-compose.yml` is ignored on Windows Docker Desktop, which handles file permissions automatically.

## Manual Start (All Platforms)

If you prefer to run commands manually:

```bash
# Linux/macOS/WSL
export UID=$(id -u)
export GID=$(id -g)
docker compose up --build -d

# Windows PowerShell (optional, but doesn't hurt)
docker compose up --build -d
```

## Verify Services

Once started, the following services will be available:

- **Backend API:** http://localhost:8080
- **Frontend:** http://localhost:5173
- **MinIO Console:** http://localhost:9001
- **MailHog (Email Testing):** http://localhost:8025
- **Swagger UI:** http://localhost:8080/swagger-ui.html

Check if all containers are running:

```bash
docker compose ps
```

## Troubleshooting

### Permission Errors (Linux/macOS/WSL Only)

If you encounter permission errors:

```bash
# Fix ownership of all files
sudo chown -R $USER:$USER .

# Restart with proper user mapping
export UID=$(id -u)
export GID=$(id -g)
docker compose up --build -d
```

### Containers Not Starting

View logs for a specific service:

```bash
docker compose logs -f backend
docker compose logs -f frontend
```

### Clean Restart

Stop everything and remove volumes:

```bash
docker compose down -v
docker compose up --build -d
```

## For Developers (IntelliJ/VSCode)

### Backend (Java)

The backend compiles fine from command line. If IntelliJ shows errors:

1. Close IntelliJ
2. Delete `.idea` folder in `backend/`
3. Reopen project in IntelliJ as Maven project
4. Enable Lombok plugin and annotation processing
5. File → Invalidate Caches → Restart

### Frontend (TypeScript)

If your IDE shows TypeScript errors but the app runs fine:

```bash
# Copy node_modules from container to local filesystem
cd frontend
docker cp thesis_frontend:/app/node_modules ./
```

This gives your IDE the type definitions it needs.

## Git Submodules

This project uses Git submodules for backend and frontend. Make sure to initialize them:

```bash
git submodule update --init --recursive
```

## Environment Variables

Copy `.env.example` to `.env` and customize if needed:

```bash
cp .env.example .env
```

Default credentials:
- **PostgreSQL:** postgres/password
- **MinIO:** minioadmin/minioadmin
- **MailHog:** No authentication needed

## Platform-Specific Notes

### Linux
- ✅ Fully supported
- ✅ User mapping works correctly
- ⚠️ May need to fix permissions after first run

### macOS
- ✅ Fully supported
- ✅ User mapping works correctly
- ℹ️ Docker Desktop handles most permission issues automatically

### Windows (WSL)
- ✅ Fully supported (recommended for Windows)
- ✅ Use `./docker-start.sh` script
- ℹ️ Best development experience on Windows

### Windows (Native Docker Desktop)
- ✅ Works fine
- ⚠️ Use `docker compose` commands directly (not the bash script)
- ℹ️ User mapping is ignored (Docker Desktop handles permissions)

## Stopping the Application

```bash
docker compose down
```

To also remove volumes (database data, uploaded files):

```bash
docker compose down -v
```
