# Docker Compose Local Development Guide

## Overview

This guide provides a complete Docker Compose setup for running HomeWarehouse locally with all dependencies.

## Directory Structure

```
infrastructure/docker/
├── docker-compose.yml           # Main compose file
├── docker-compose.dev.yml       # Development overrides
├── .env.example                 # Environment template
├── postgres/
│   └── init.sql                 # Database initialization
├── rabbitmq/
│   └── rabbitmq.conf           # RabbitMQ configuration
└── temporal/
    └── development.yaml        # Temporal configuration
```

## Main Docker Compose File

```yaml
# infrastructure/docker/docker-compose.yml
version: '3.8'

services:
  # PostgreSQL Database
  postgres:
    image: postgres:16-alpine
    container_name: homewarehouse-postgres
    environment:
      POSTGRES_DB: homewarehouse
      POSTGRES_USER: homewarehouse
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD:-homewarehouse}
      PGDATA: /var/lib/postgresql/data/pgdata
    ports:
      - "5432:5432"
    volumes:
      - postgres-data:/var/lib/postgresql/data
      - ./postgres/init.sql:/docker-entrypoint-initdb.d/init.sql
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U homewarehouse"]
      interval: 10s
      timeout: 5s
      retries: 5
    networks:
      - homewarehouse-network

  # Redis Cache
  redis:
    image: redis:7-alpine
    container_name: homewarehouse-redis
    command: redis-server --requirepass ${REDIS_PASSWORD:-homewarehouse} --maxmemory 256mb --maxmemory-policy allkeys-lru
    ports:
      - "6379:6379"
    volumes:
      - redis-data:/data
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 10s
      timeout: 5s
      retries: 5
    networks:
      - homewarehouse-network

  # RabbitMQ Message Broker
  rabbitmq:
    image: rabbitmq:3.12-management-alpine
    container_name: homewarehouse-rabbitmq
    environment:
      RABBITMQ_DEFAULT_USER: ${RABBITMQ_USER:-homewarehouse}
      RABBITMQ_DEFAULT_PASS: ${RABBITMQ_PASSWORD:-homewarehouse}
      RABBITMQ_DEFAULT_VHOST: /
    ports:
      - "5672:5672"   # AMQP
      - "15672:15672" # Management UI
    volumes:
      - rabbitmq-data:/var/lib/rabbitmq
      - ./rabbitmq/rabbitmq.conf:/etc/rabbitmq/rabbitmq.conf
    healthcheck:
      test: ["CMD", "rabbitmq-diagnostics", "ping"]
      interval: 10s
      timeout: 5s
      retries: 5
    networks:
      - homewarehouse-network

  # Temporal Server
  temporal:
    image: temporalio/auto-setup:1.22.0
    container_name: homewarehouse-temporal
    environment:
      - DB=postgresql
      - DB_PORT=5432
      - POSTGRES_USER=homewarehouse
      - POSTGRES_PWD=${POSTGRES_PASSWORD:-homewarehouse}
      - POSTGRES_SEEDS=postgres
      - DYNAMIC_CONFIG_FILE_PATH=config/dynamicconfig/development.yaml
    ports:
      - "7233:7233"  # gRPC
      - "8233:8233"  # HTTP
    volumes:
      - ./temporal/development.yaml:/etc/temporal/config/dynamicconfig/development.yaml
    depends_on:
      postgres:
        condition: service_healthy
    networks:
      - homewarehouse-network

  # Temporal Web UI
  temporal-ui:
    image: temporalio/ui:2.21.0
    container_name: homewarehouse-temporal-ui
    environment:
      - TEMPORAL_ADDRESS=temporal:7233
      - TEMPORAL_CORS_ORIGINS=http://localhost:3000
    ports:
      - "8080:8080"
    depends_on:
      - temporal
    networks:
      - homewarehouse-network

  # Backend Application (Development)
  backend:
    build:
      context: ../../backend
      dockerfile: Dockerfile
      target: development
    container_name: homewarehouse-backend
    environment:
      SPRING_PROFILES_ACTIVE: development
      DATABASE_URL: jdbc:postgresql://postgres:5432/homewarehouse
      DATABASE_USERNAME: homewarehouse
      DATABASE_PASSWORD: ${POSTGRES_PASSWORD:-homewarehouse}
      REDIS_HOST: redis
      REDIS_PORT: 6379
      REDIS_PASSWORD: ${REDIS_PASSWORD:-homewarehouse}
      RABBITMQ_HOST: rabbitmq
      RABBITMQ_PORT: 5672
      RABBITMQ_USERNAME: ${RABBITMQ_USER:-homewarehouse}
      RABBITMQ_PASSWORD: ${RABBITMQ_PASSWORD:-homewarehouse}
      TEMPORAL_HOST: temporal
      TEMPORAL_PORT: 7233
      JWT_PUBLIC_KEY: ${JWT_PUBLIC_KEY}
      JWT_PRIVATE_KEY: ${JWT_PRIVATE_KEY}
    ports:
      - "8081:8080"
    volumes:
      - ../../backend:/app
      - gradle-cache:/root/.gradle
    depends_on:
      postgres:
        condition: service_healthy
      redis:
        condition: service_healthy
      rabbitmq:
        condition: service_healthy
      temporal:
        condition: service_started
    networks:
      - homewarehouse-network
    command: ["./gradlew", "bootRun"]

  # Frontend Application (Development)
  frontend:
    build:
      context: ../../web
      dockerfile: Dockerfile
      target: development
    container_name: homewarehouse-frontend
    environment:
      VITE_API_BASE_URL: http://localhost:8081
    ports:
      - "3000:3000"
    volumes:
      - ../../web:/app
      - /app/node_modules
    networks:
      - homewarehouse-network
    command: ["npm", "run", "dev", "--", "--host"]

volumes:
  postgres-data:
    driver: local
  redis-data:
    driver: local
  rabbitmq-data:
    driver: local
  gradle-cache:
    driver: local

networks:
  homewarehouse-network:
    driver: bridge
```

## Development Overrides

```yaml
# infrastructure/docker/docker-compose.dev.yml
version: '3.8'

services:
  backend:
    build:
      args:
        - GRADLE_BUILD_ARGS=--no-daemon
    environment:
      SPRING_DEVTOOLS_RESTART_ENABLED: "true"
      LOGGING_LEVEL_ROOT: DEBUG
    volumes:
      - ../../backend:/app:cached
      - gradle-cache:/root/.gradle

  frontend:
    environment:
      VITE_ENABLE_DEVTOOLS: "true"
    volumes:
      - ../../web:/app:cached
      - /app/node_modules

  # Development tools
  adminer:
    image: adminer:latest
    container_name: homewarehouse-adminer
    ports:
      - "8082:8080"
    environment:
      ADMINER_DEFAULT_SERVER: postgres
    networks:
      - homewarehouse-network

  # Redis Commander
  redis-commander:
    image: rediscommander/redis-commander:latest
    container_name: homewarehouse-redis-commander
    environment:
      REDIS_HOSTS: local:redis:6379:0:${REDIS_PASSWORD:-homewarehouse}
    ports:
      - "8083:8081"
    networks:
      - homewarehouse-network
```

## Environment File

```bash
# infrastructure/docker/.env.example
# Copy this file to .env and update values

# PostgreSQL
POSTGRES_PASSWORD=homewarehouse_dev_password

# Redis
REDIS_PASSWORD=redis_dev_password

# RabbitMQ
RABBITMQ_USER=homewarehouse
RABBITMQ_PASSWORD=rabbitmq_dev_password

# JWT Keys (Generate with: ssh-keygen -t rsa -b 4096 -m PEM)
JWT_PUBLIC_KEY="-----BEGIN PUBLIC KEY-----\n...\n-----END PUBLIC KEY-----"
JWT_PRIVATE_KEY="-----BEGIN PRIVATE KEY-----\n...\n-----END PRIVATE KEY-----"
```

## PostgreSQL Initialization

```sql
-- infrastructure/docker/postgres/init.sql

-- Create extensions
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pg_trgm";

-- Create schemas
CREATE SCHEMA IF NOT EXISTS iam;
CREATE SCHEMA IF NOT EXISTS ledger;
CREATE SCHEMA IF NOT EXISTS inventory;
CREATE SCHEMA IF NOT EXISTS assets;
CREATE SCHEMA IF NOT EXISTS audit;

-- Grant permissions
GRANT ALL PRIVILEGES ON SCHEMA iam TO homewarehouse;
GRANT ALL PRIVILEGES ON SCHEMA ledger TO homewarehouse;
GRANT ALL PRIVILEGES ON SCHEMA inventory TO homewarehouse;
GRANT ALL PRIVILEGES ON SCHEMA assets TO homewarehouse;
GRANT ALL PRIVILEGES ON SCHEMA audit TO homewarehouse;

-- Create initial admin user (password: admin123)
-- BCrypt hash for 'admin123' with work factor 12
INSERT INTO iam.users (
    id,
    username,
    email,
    password_hash,
    enabled,
    created_at,
    updated_at
) VALUES (
    uuid_generate_v4(),
    'admin',
    'admin@homewarehouse.local',
    '$2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQJqhN8/LewY5lDxAW0Dx.Rgy',
    true,
    NOW(),
    NOW()
) ON CONFLICT DO NOTHING;
```

## RabbitMQ Configuration

```conf
# infrastructure/docker/rabbitmq/rabbitmq.conf

# Networking
listeners.tcp.default = 5672

# Management plugin
management.tcp.port = 15672

# Virtual hosts
default_vhost = /

# Memory and disk limits
vm_memory_high_watermark.relative = 0.6
disk_free_limit.absolute = 2GB

# Message TTL
consumer_timeout = 3600000

# Queue master locator
queue_master_locator = min-masters
```

## Temporal Configuration

```yaml
# infrastructure/docker/temporal/development.yaml
---
limit.maxIDLength:
  - value: 255
    constraints: {}

limit.blobSize.error:
  - value: 2097152
    constraints: {}

limit.blobSize.warn:
  - value: 262144
    constraints: {}

system.enableActivityEagerExecution:
  - value: true
    constraints: {}
```

## Backend Dockerfile

```dockerfile
# backend/Dockerfile

# Development stage
FROM eclipse-temurin:21-jdk-alpine AS development

WORKDIR /app

# Copy Gradle wrapper
COPY gradlew .
COPY gradle gradle

# Copy build files
COPY build.gradle.kts settings.gradle.kts ./
COPY gradle.properties ./

# Download dependencies
RUN ./gradlew dependencies --no-daemon

# Copy source code
COPY . .

# Expose port
EXPOSE 8080

# Run application
CMD ["./gradlew", "bootRun", "--no-daemon"]

# Production stage
FROM eclipse-temurin:21-jdk-alpine AS build

WORKDIR /app

COPY . .

RUN ./gradlew clean build -x test --no-daemon

FROM eclipse-temurin:21-jre-alpine AS production

WORKDIR /app

COPY --from=build /app/backend/app/build/libs/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
```

## Frontend Dockerfile

```dockerfile
# web/Dockerfile

# Development stage
FROM node:18-alpine AS development

WORKDIR /app

# Copy package files
COPY package*.json ./

# Install dependencies
RUN npm ci

# Copy source
COPY . .

# Expose Vite dev server port
EXPOSE 3000

# Start dev server
CMD ["npm", "run", "dev"]

# Build stage
FROM node:18-alpine AS build

WORKDIR /app

COPY package*.json ./
RUN npm ci

COPY . .
RUN npm run build

# Production stage
FROM nginx:alpine AS production

COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/nginx.conf

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
```

## Usage Commands

### Start All Services

```bash
# First time setup
cd infrastructure/docker
cp .env.example .env
# Edit .env with your values

# Start all services
docker-compose up -d

# Start with development overrides
docker-compose -f docker-compose.yml -f docker-compose.dev.yml up -d

# View logs
docker-compose logs -f

# View specific service logs
docker-compose logs -f backend
```

### Stop Services

```bash
# Stop all services
docker-compose down

# Stop and remove volumes (WARNING: deletes all data)
docker-compose down -v
```

### Database Operations

```bash
# Access PostgreSQL CLI
docker exec -it homewarehouse-postgres psql -U homewarehouse -d homewarehouse

# Run migrations
docker exec homewarehouse-backend ./gradlew flywayMigrate

# Create database backup
docker exec homewarehouse-postgres pg_dump -U homewarehouse homewarehouse > backup.sql

# Restore database
cat backup.sql | docker exec -i homewarehouse-postgres psql -U homewarehouse -d homewarehouse
```

### Service Health Checks

```bash
# Check all services status
docker-compose ps

# Health check specific service
docker exec homewarehouse-postgres pg_isready -U homewarehouse
docker exec homewarehouse-redis redis-cli ping
docker exec homewarehouse-rabbitmq rabbitmq-diagnostics ping
```

## Accessing Services

| Service | URL | Credentials |
|---------|-----|-------------|
| Backend API | http://localhost:8081 | - |
| Frontend | http://localhost:3000 | - |
| PostgreSQL | localhost:5432 | homewarehouse/homewarehouse |
| Redis | localhost:6379 | Password: homewarehouse |
| RabbitMQ Management | http://localhost:15672 | homewarehouse/homewarehouse |
| Temporal UI | http://localhost:8080 | - |
| Adminer (DB UI) | http://localhost:8082 | - |
| Redis Commander | http://localhost:8083 | - |

## Troubleshooting

### Port Conflicts

```bash
# Check what's using a port
lsof -i :8080  # macOS/Linux
netstat -ano | findstr :8080  # Windows

# Change ports in docker-compose.yml or use different host ports
```

### Container Won't Start

```bash
# View detailed logs
docker-compose logs backend

# Check container status
docker inspect homewarehouse-backend

# Rebuild container
docker-compose build --no-cache backend
docker-compose up -d backend
```

### Database Connection Issues

```bash
# Verify PostgreSQL is running
docker exec homewarehouse-postgres pg_isready

# Check network connectivity
docker exec homewarehouse-backend ping postgres

# Verify environment variables
docker exec homewarehouse-backend env | grep DATABASE
```

### Volume Permissions

```bash
# Fix volume permissions (Linux)
sudo chown -R $USER:$USER ./backend
sudo chown -R $USER:$USER ./web

# Reset volumes
docker-compose down -v
docker-compose up -d
```

## Development Workflow

### Hot Reload

Both backend and frontend support hot reload:

- **Backend:** Spring Boot DevTools automatically restarts on code changes
- **Frontend:** Vite HMR (Hot Module Replacement) updates instantly

### Running Tests

```bash
# Backend tests
docker exec homewarehouse-backend ./gradlew test

# Frontend tests
docker exec homewarehouse-frontend npm test

# Integration tests
docker exec homewarehouse-backend ./gradlew integrationTest
```

### Debugging

#### Backend (Java)

```yaml
# Add to backend service in docker-compose.dev.yml
environment:
  JAVA_TOOL_OPTIONS: "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005"
ports:
  - "5005:5005"
```

Then connect your IDE debugger to `localhost:5005`.

#### Frontend (TypeScript)

Use browser DevTools or VS Code debugger with source maps.

## Best Practices

1. **Use .env file** for all sensitive configuration
2. **Never commit .env** to version control
3. **Use named volumes** for persistent data
4. **Health checks** ensure services are ready
5. **Dependency order** with depends_on and conditions
6. **Network isolation** with custom bridge network
7. **Resource limits** to prevent resource exhaustion
8. **Regular cleanup** of unused images and volumes

## Cleanup

```bash
# Remove stopped containers
docker-compose down

# Remove all containers, networks, and volumes
docker-compose down -v

# Remove unused images
docker image prune -a

# Full cleanup (nuclear option)
docker system prune -a --volumes
```

## Summary

This Docker Compose setup provides:
- Complete local development environment
- All services containerized
- Hot reload for rapid development
- Easy database management
- Development tools (Adminer, Redis Commander)
- Production-like configuration
- Simple commands for common operations
