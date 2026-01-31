# Development Setup Guide

## Overview

This guide provides step-by-step instructions for setting up the HomeWarehouse development environment on your local machine.

## Prerequisites

### Required Software

| Tool | Version | Purpose | Installation |
|------|---------|---------|--------------|
| Git | Latest | Version control | [Download](https://git-scm.com/downloads) |
| Java | 21+ | Backend runtime | [Download](https://adoptium.net/) |
| Node.js | 18+ | Frontend runtime | [Download](https://nodejs.org/) |
| Docker | Latest | Container runtime | [Download](https://www.docker.com/products/docker-desktop) |
| Docker Compose | Latest | Multi-container orchestration | Included with Docker Desktop |

### Optional but Recommended

| Tool | Purpose | Installation |
|------|---------|--------------|
| IntelliJ IDEA | Java IDE | [Download](https://www.jetbrains.com/idea/) |
| VS Code | Frontend IDE | [Download](https://code.visualstudio.com/) |
| Postman | API testing | [Download](https://www.postman.com/) |
| DBeaver | Database GUI | [Download](https://dbeaver.io/) |

## System Requirements

### Minimum

- **CPU:** 4 cores
- **RAM:** 8 GB
- **Disk:** 20 GB free space
- **OS:** Windows 10/11, macOS 11+, Ubuntu 20.04+

### Recommended

- **CPU:** 8 cores
- **RAM:** 16 GB
- **Disk:** 50 GB SSD
- **OS:** macOS or Linux for better Docker performance

## Initial Setup

### 1. Clone Repository

```bash
# Clone the repository
git clone https://github.com/yourusername/HomeWarehouse.git
cd HomeWarehouse

# Verify structure
ls -la
```

Expected structure:
```
HomeWarehouse/
├── backend/           # Java Spring Boot backend
├── web/              # React TypeScript frontend
├── infrastructure/    # Docker, Kubernetes, Terraform
├── docs/             # Documentation
├── CLAUDE.md         # Development guide
├── AGENTS.md         # Agent guidelines
└── README.md
```

### 2. Verify Java Installation

```bash
# Check Java version
java -version

# Should output: openjdk version "21.x.x"

# Check JAVA_HOME
echo $JAVA_HOME

# Set JAVA_HOME if not set (macOS/Linux)
export JAVA_HOME=$(/usr/libexec/java_home -v 21)

# Set JAVA_HOME (Windows)
setx JAVA_HOME "C:\Program Files\Eclipse Adoptium\jdk-21.x.x"
```

### 3. Verify Node.js Installation

```bash
# Check Node.js version
node --version  # Should be v18.x.x or higher

# Check npm version
npm --version

# Update npm to latest
npm install -g npm@latest
```

### 4. Configure Git

```bash
# Set your identity
git config --global user.name "Your Name"
git config --global user.email "your.email@example.com"

# Configure line endings (Windows)
git config --global core.autocrlf true

# Configure line endings (macOS/Linux)
git config --global core.autocrlf input

# Set default branch name
git config --global init.defaultBranch main
```

## Backend Setup

### 1. Build Backend

```bash
cd backend

# First build (downloads dependencies)
./gradlew build

# This may take 5-10 minutes on first run
```

### 2. Configure Environment Variables

```bash
# Create application-local.yml
cat > backend/app/src/main/resources/application-local.yml << 'EOF'
spring:
  profiles:
    active: local

  datasource:
    url: jdbc:postgresql://localhost:5432/homewarehouse
    username: homewarehouse
    password: homewarehouse

  redis:
    host: localhost
    port: 6379
    password: homewarehouse

  rabbitmq:
    host: localhost
    port: 5672
    username: homewarehouse
    password: homewarehouse

jwt:
  public-key: |
    -----BEGIN PUBLIC KEY-----
    [Your public key here]
    -----END PUBLIC KEY-----
  private-key: |
    -----BEGIN PRIVATE KEY-----
    [Your private key here]
    -----END PRIVATE KEY-----
EOF
```

### 3. Generate JWT Keys

```bash
# Generate RSA key pair
ssh-keygen -t rsa -b 4096 -m PEM -f jwt-key -N ""

# Extract public key
openssl rsa -in jwt-key -pubout -outform PEM -out jwt-key.pub

# View keys
cat jwt-key.pub    # Public key
cat jwt-key        # Private key

# Add keys to application-local.yml
```

### 4. Run Backend Tests

```bash
# Unit tests only
./gradlew test

# All tests (requires Docker for integration tests)
./gradlew check
```

## Frontend Setup

### 1. Install Dependencies

```bash
cd web

# Install all npm packages
npm install

# This may take 2-5 minutes
```

### 2. Configure Environment

```bash
# Create .env.local file
cat > .env.local << 'EOF'
VITE_API_BASE_URL=http://localhost:8080
VITE_ENVIRONMENT=development
EOF
```

### 3. Run Frontend Tests

```bash
# Run tests once
npm test

# Run tests in watch mode
npm run test:watch

# Run tests with coverage
npm run test:coverage
```

## Database Setup

### Option 1: Docker Compose (Recommended)

```bash
# Start all infrastructure services
cd infrastructure/docker
docker-compose up -d postgres redis rabbitmq

# Verify services are running
docker-compose ps

# View logs
docker-compose logs -f postgres
```

### Option 2: Manual Installation

#### PostgreSQL

```bash
# macOS with Homebrew
brew install postgresql@16
brew services start postgresql@16

# Ubuntu
sudo apt install postgresql-16
sudo systemctl start postgresql

# Create database and user
psql -U postgres
CREATE DATABASE homewarehouse;
CREATE USER homewarehouse WITH PASSWORD 'homewarehouse';
GRANT ALL PRIVILEGES ON DATABASE homewarehouse TO homewarehouse;
\q
```

#### Redis

```bash
# macOS
brew install redis
brew services start redis

# Ubuntu
sudo apt install redis-server
sudo systemctl start redis
```

#### RabbitMQ

```bash
# macOS
brew install rabbitmq
brew services start rabbitmq

# Ubuntu
sudo apt install rabbitmq-server
sudo systemctl start rabbitmq-server

# Enable management plugin
rabbitmq-plugins enable rabbitmq_management

# Create user
rabbitmqctl add_user homewarehouse homewarehouse
rabbitmqctl set_permissions -p / homewarehouse ".*" ".*" ".*"
```

## Running the Application

### Start Infrastructure

```bash
# Using Docker Compose
cd infrastructure/docker
docker-compose up -d

# Verify all services are healthy
docker-compose ps
```

### Start Backend

```bash
cd backend

# Run with Gradle
./gradlew :backend:app:bootRun

# Or run specific profile
./gradlew :backend:app:bootRun --args='--spring.profiles.active=local'

# Backend will start on http://localhost:8080
```

### Start Frontend

```bash
cd web

# Start Vite dev server
npm run dev

# Frontend will start on http://localhost:3000
```

### Verify Everything Works

```bash
# Test backend health
curl http://localhost:8080/actuator/health

# Should return: {"status":"UP"}

# Test frontend loads
open http://localhost:3000  # macOS
xdg-open http://localhost:3000  # Linux
start http://localhost:3000  # Windows
```

## IDE Setup

### IntelliJ IDEA (Backend)

#### 1. Import Project

1. Open IntelliJ IDEA
2. File → Open → Select `HomeWarehouse/backend` folder
3. Wait for Gradle import to complete

#### 2. Configure JDK

1. File → Project Structure → Project
2. Set SDK to Java 21
3. Set language level to 21

#### 3. Install Plugins

- Lombok (for `@Data`, `@Builder` annotations)
- SonarLint (code quality)
- GitToolBox (Git integration)

#### 4. Code Style

1. File → Settings → Editor → Code Style
2. Import scheme from `backend/config/intellij-codestyle.xml`

#### 5. Run Configuration

1. Run → Edit Configurations
2. Add new → Spring Boot
3. Main class: `com.homewarehouse.Application`
4. Active profiles: `local`

### VS Code (Frontend)

#### 1. Install Extensions

```bash
# Essential
- ESLint
- Prettier
- TypeScript Vue Plugin (Volar)
- Tailwind CSS IntelliSense

# Recommended
- GitLens
- Error Lens
- Auto Rename Tag
- Import Cost
```

#### 2. Configure Settings

Create `.vscode/settings.json`:

```json
{
  "editor.defaultFormatter": "esbenp.prettier-vscode",
  "editor.formatOnSave": true,
  "editor.codeActionsOnSave": {
    "source.fixAll.eslint": true
  },
  "typescript.tsdk": "node_modules/typescript/lib",
  "typescript.enablePromptUseWorkspaceTsdk": true
}
```

#### 3. Debug Configuration

Create `.vscode/launch.json`:

```json
{
  "version": "0.2.0",
  "configurations": [
    {
      "type": "chrome",
      "request": "launch",
      "name": "Launch Chrome",
      "url": "http://localhost:3000",
      "webRoot": "${workspaceFolder}/web"
    }
  ]
}
```

## Common Development Tasks

### Database Migrations

```bash
# Run Flyway migrations
cd backend
./gradlew flywayMigrate

# Create new migration
# Create file: backend/src/main/resources/db/migration/V{version}__{description}.sql
```

### Code Formatting

```bash
# Backend (Java)
cd backend
./gradlew spotlessApply

# Frontend (TypeScript)
cd web
npm run format
```

### Running Tests

```bash
# Backend - Unit tests only
./gradlew test

# Backend - Integration tests
./gradlew integrationTest

# Backend - All tests
./gradlew check

# Frontend - Unit tests
cd web && npm test

# Frontend - E2E tests
cd web && npm run test:e2e
```

### Building for Production

```bash
# Backend
./gradlew clean build -x test

# Frontend
cd web && npm run build
```

## Troubleshooting

### Port Already in Use

```bash
# Find process using port (macOS/Linux)
lsof -i :8080

# Kill process
kill -9 <PID>

# Find process (Windows)
netstat -ano | findstr :8080
taskkill /PID <PID> /F
```

### Gradle Build Failed

```bash
# Clean Gradle cache
./gradlew clean --refresh-dependencies

# Delete .gradle folder
rm -rf ~/.gradle/caches

# Rebuild
./gradlew build
```

### Node Modules Issues

```bash
# Delete node_modules and package-lock.json
cd web
rm -rf node_modules package-lock.json

# Reinstall
npm install
```

### Database Connection Failed

```bash
# Check PostgreSQL is running
docker ps | grep postgres

# Check credentials in application-local.yml
# Verify with direct connection
psql -h localhost -U homewarehouse -d homewarehouse
```

### Docker Issues

```bash
# Restart Docker Desktop

# Remove all containers and volumes
docker-compose down -v

# Clean Docker system
docker system prune -a --volumes

# Restart services
docker-compose up -d
```

## Development Workflow

### 1. Start New Feature

```bash
# Update main branch
git checkout main
git pull origin main

# Create feature branch
git checkout -b feature/your-feature-name

# Start coding
```

### 2. Make Changes

```bash
# Backend changes
cd backend
./gradlew test  # Run tests after changes

# Frontend changes
cd web
npm test  # Run tests after changes
```

### 3. Commit Changes

```bash
# Stage changes
git add .

# Commit with conventional commit message
git commit -m "feat(ledger): add transaction export feature"

# Commit message format: type(scope): description
# Types: feat, fix, docs, style, refactor, test, chore
```

### 4. Push and Create PR

```bash
# Push branch
git push origin feature/your-feature-name

# Create pull request on GitHub
# Wait for CI checks to pass
# Request code review
```

## Performance Tips

### Docker Performance (macOS/Windows)

```bash
# Allocate more resources to Docker Desktop
# Settings → Resources
# - CPUs: 4-6
# - Memory: 8-12 GB
# - Disk: 50+ GB

# Use volumes for better performance
# Already configured in docker-compose.yml
```

### Gradle Performance

```bash
# Add to ~/.gradle/gradle.properties
org.gradle.daemon=true
org.gradle.parallel=true
org.gradle.caching=true
org.gradle.configureondemand=true
org.gradle.jvmargs=-Xmx4g -XX:MaxMetaspaceSize=512m
```

### npm Performance

```bash
# Use npm ci instead of npm install
npm ci

# Enable caching
npm config set cache ~/.npm-cache --global
```

## Next Steps

1. ✅ Development environment set up
2. 📖 Read [CLAUDE.md](../../../CLAUDE.md) for coding standards
3. 📖 Read [AGENTS.md](../../../AGENTS.md) for agent guidelines
4. 🔍 Explore the codebase
5. 🧪 Run all tests to verify setup
6. 🚀 Start building features!

## Resources

- [Spring Boot Documentation](https://spring.io/projects/spring-boot)
- [React Documentation](https://react.dev/)
- [Temporal Documentation](https://docs.temporal.io/)
- [RabbitMQ Documentation](https://www.rabbitmq.com/documentation.html)
- [PostgreSQL Documentation](https://www.postgresql.org/docs/)

## Getting Help

- 📚 Check documentation in `/docs`
- 🔍 Search existing issues on GitHub
- 💬 Ask in team Slack channel
- 📧 Contact tech lead

## Summary

You now have a complete development environment with:
- ✅ All required tools installed
- ✅ Backend built and tested
- ✅ Frontend built and tested
- ✅ Database and infrastructure running
- ✅ IDE configured
- ✅ Ready to develop!
