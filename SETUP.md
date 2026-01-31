# HomeWarehouse - Setup Guide

## Prerequisites

- **Java 21** - Download from [Adoptium](https://adoptium.net/) or use SDKMAN
- **Node.js 18+** - Download from [nodejs.org](https://nodejs.org/)
- **Docker Desktop** - Download from [docker.com](https://www.docker.com/products/docker-desktop/)

## Quick Start

### 1. Start Infrastructure Services

Start PostgreSQL, Redis, and RabbitMQ using Docker Compose:

```bash
cd infrastructure/docker
docker-compose up -d
```

Verify services are running:

```bash
docker-compose ps
```

All services should show as "healthy".

### 2. Build Backend

From the root directory:

```bash
# Windows
gradlew.bat build

# Linux/Mac
./gradlew build
```

### 3. Start Backend Application

```bash
# Windows
gradlew.bat :backend:app:bootRun

# Linux/Mac
./gradlew :backend:app:bootRun
```

The application will start on http://localhost:8080

Verify the health endpoint:

```bash
curl http://localhost:8080/health
```

Expected response:
```json
{"status":"UP"}
```

### 4. Start Frontend

In a new terminal:

```bash
cd web
npm install
npm run dev
```

The frontend will start on http://localhost:5173

Open your browser and navigate to http://localhost:5173 - you should see the "HomeWarehouse" heading.

## Service Ports

- **Backend API**: http://localhost:8080
- **Frontend**: http://localhost:5173
- **PostgreSQL**: localhost:5432
- **Redis**: localhost:6379
- **RabbitMQ AMQP**: localhost:5672
- **RabbitMQ Management UI**: http://localhost:15672 (username: homewarehouse, password: homewarehouse)

## Stopping Services

### Stop Backend
Press `Ctrl+C` in the terminal running the Spring Boot application

### Stop Frontend
Press `Ctrl+C` in the terminal running Vite dev server

### Stop Infrastructure
```bash
cd infrastructure/docker
docker-compose down
```

To remove all data volumes:
```bash
docker-compose down -v
```

## Current Status

This is a **basic skeleton** with:
- Multi-module Gradle build structure
- Hexagonal architecture package layout (empty)
- Spring Boot application with health endpoint
- React frontend with minimal UI
- Docker Compose infrastructure services

**No functionality is implemented yet.** This skeleton demonstrates the project structure and verifies all services can start successfully.

## Next Steps

Refer to `CLAUDE.md` for:
- Implementation guides for each module
- Coding conventions
- Testing strategies
- Development workflow

See `docs/backlog/09-backlog.md` for the implementation roadmap.
