# CLAUDE.md - HomeWarehouse Development Guide

This file provides guidance for AI agents (Claude) and developers working on the HomeWarehouse project.

## Project Overview

HomeWarehouse is a personal wealth and home inventory management system built with:
- **Backend**: Java 21 + Spring Boot 3.x (multi-module Gradle monolith)
- **Frontend**: React 18 + TypeScript + Vite
- **Database**: PostgreSQL 16
- **Cache**: Redis 7
- **Messaging**: RabbitMQ 3.x
- **Workflows**: Temporal.io
- **Deployment**: Kubernetes (home cluster) via Terraform + Helm

## Repository Structure

```
HomeWarehouse/
├── CLAUDE.md                    # This file
├── AGENTS.md                    # Agent operating guidelines
├── docs/                        # Project documentation
│   ├── templates/               # Documentation templates
│   ├── overview/                # Product overview
│   ├── architecture/            # Architecture docs
│   ├── security/                # Security requirements
│   ├── database/                # Data model
│   ├── api/                     # API specification
│   ├── workflows/               # Temporal workflows
│   ├── events/                  # RabbitMQ events
│   ├── frontend/                # Frontend architecture
│   ├── infra/                   # Infrastructure plan
│   ├── backlog/                 # Implementation backlog
│   └── adrs/                    # Architecture Decision Records
├── backend/                     # Java Spring Boot backend
│   ├── shared-kernel/           # Cross-cutting primitives
│   ├── identity-access/         # Auth, users, roles, permissions
│   ├── ledger/                  # Transactions, accounts, categories
│   ├── assets/                  # Asset tracking, valuations
│   ├── inventory/               # Items, units, locations, expiry
│   ├── audit/                   # Audit logging
│   └── app/                     # Spring Boot main module
├── web/                         # React frontend
├── infrastructure/              # IaC and deployment
│   ├── docker/                  # Docker Compose for local dev
│   ├── helm/                    # Helm charts
│   └── terraform/               # Terraform modules
└── gradle/                      # Gradle wrapper and catalogs
```

## Key Conventions

### Language & Documentation

- All code, comments, and documentation in **English**
- All diagrams must use **Mermaid** (no ASCII diagrams)
- Document significant decisions in `/docs/adrs/`

### Backend Architecture (MANDATORY)

The backend implements four patterns simultaneously:

1. **Hexagonal Architecture**: Domain core is framework-free
2. **Domain-Driven Design**: Aggregates, value objects, domain services
3. **Vertical Slicing**: Code organized by use case/feature
4. **Screaming Architecture**: Package names reflect business domains

Each module structure:
```
backend/{module}/
└── src/main/java/com/homewarehouse/{module}/
    ├── domain/           # Pure domain model (no Spring)
    │   ├── model/        # Aggregates, entities, value objects
    │   ├── service/      # Domain services
    │   ├── event/        # Domain events
    │   └── repository/   # Repository interfaces (ports)
    ├── application/      # Use cases (vertical slices)
    │   ├── command/      # Write operations
    │   │   └── {usecase}/
    │   │       ├── {UseCase}Command.java
    │   │       ├── {UseCase}Handler.java
    │   │       └── {UseCase}Result.java
    │   ├── query/        # Read operations
    │   └── port/         # Application ports
    │       ├── in/       # Inbound ports
    │       └── out/      # Outbound ports
    └── infrastructure/   # Adapters
        ├── persistence/  # JPA repositories
        ├── messaging/    # RabbitMQ publishers/consumers
        ├── temporal/     # Temporal activities
        └── web/          # REST controllers
```

### Module Dependency Rules

- Domain depends on **nothing**
- Application depends on **domain only**
- Infrastructure depends on **application and domain**
- `shared-kernel` contains **only** cross-cutting primitives
- **No shared infrastructure libraries** across modules
- Cross-module communication via **events or Temporal workflows**

### Gradle Build

- Use Gradle Kotlin DSL (`build.gradle.kts`)
- Version catalog at `gradle/libs.versions.toml`
- Convention plugins in `build-logic/`
- Add new modules in `settings.gradle.kts`

### Adding a New Module

1. Create directory `backend/{module-name}/`
2. Create `build.gradle.kts` applying convention plugins:
   ```kotlin
   plugins {
       id("homewarehouse.java-conventions")
       id("homewarehouse.spring-boot-conventions")
   }

   dependencies {
       implementation(project(":backend:shared-kernel"))
   }
   ```
3. Add to `settings.gradle.kts`:
   ```kotlin
   include(":backend:{module-name}")
   ```
4. Create package structure following hexagonal layout
5. Register configuration in `app` module

### Adding a New Vertical Slice (Use Case)

1. Identify the module (e.g., `ledger`)
2. Create command/query package: `application/command/{usecasename}/`
3. Create:
   - `{UseCase}Command.java` - Input DTO
   - `{UseCase}Handler.java` - Orchestration logic
   - `{UseCase}Result.java` - Output DTO
4. Implement domain logic in `domain/` if needed
5. Create controller in `infrastructure/web/`
6. Add tests

### Security Requirements

- **JWT tokens**: RS256 algorithm only, never accept "none"
- **Access tokens**: 10-15 minute lifetime
- **Refresh tokens**: Hashed in Redis, rotate on every refresh
- **RBAC**: Check permissions on every endpoint
- **Audit**: Log all mutations with correlation ID
- **No secrets in code**: Use environment variables

### Testing Strategy

- Unit tests for domain logic (pure Java, no Spring)
- Integration tests with Testcontainers
- Temporal workflow tests with test framework
- Frontend tests with Vitest + React Testing Library

## Common Commands

### Backend

```bash
# Build all modules
./gradlew build

# Run tests
./gradlew test

# Run specific module tests
./gradlew :backend:ledger:test

# Format code
./gradlew spotlessApply

# Check code quality
./gradlew check

# Start application (requires docker-compose services)
./gradlew :backend:app:bootRun
```

### Frontend

```bash
cd web

# Install dependencies
npm install

# Start dev server
npm run dev

# Build for production
npm run build

# Run tests
npm test

# Lint
npm run lint
```

### Local Development

```bash
cd infrastructure/docker

# Start all services
docker-compose up -d

# View logs
docker-compose logs -f

# Stop services
docker-compose down

# Reset all data
docker-compose down -v
```

## Coding Standards

### Java

- Follow Google Java Style Guide
- Use `final` for fields and parameters where possible
- Prefer records for value objects
- Use `Optional` for nullable returns
- No `null` parameters in public APIs

### TypeScript

- Use strict mode
- Prefer functional components with hooks
- Use TypeScript interfaces/types for all data
- No `any` type
- Use Zod for runtime validation

### Commit Messages

Follow Conventional Commits:
```
feat(ledger): add transaction export endpoint
fix(inventory): correct expiry date calculation
docs(api): update endpoint documentation
refactor(identity): extract token validation service
test(workflows): add purchase workflow tests
```

## Security Guardrails

When implementing:

1. **Never** log sensitive data (passwords, tokens, PII)
2. **Never** commit secrets or credentials
3. **Always** validate and sanitize input
4. **Always** use parameterized queries (JPA handles this)
5. **Always** check permissions before accessing resources
6. **Always** audit security-relevant actions
7. **Always** use HTTPS in production

## Reference Documentation

### Core Documentation

- [Overview](docs/overview/00-overview.md) - Product scope and user stories
- [Architecture](docs/architecture/01-architecture.md) - System design
- [Security](docs/security/02-security.md) - Security requirements
- [Data Model](docs/database/03-data-model.md) - Database schema
- [API](docs/api/04-api.md) - REST endpoints
- [Workflows](docs/workflows/05-workflows-temporal.md) - Temporal patterns
- [Events](docs/events/06-events-rabbitmq.md) - RabbitMQ messaging
- [Frontend](docs/frontend/07-frontend.md) - React architecture
- [Infrastructure](docs/infra/08-infra-plan.md) - Deployment
- [Backlog](docs/backlog/09-backlog.md) - Implementation plan

### Implementation Guides

**When implementing a new module or feature, follow these guides:**

#### Backend Module Implementation
- [Ledger Module Guide](docs/guides/backend/ledger-module-implementation.md) - Complete example with transactions, accounts, categories
- [Inventory Module Guide](docs/guides/backend/inventory-module-implementation.md) - Items, units, locations with expiry tracking
- [Identity & Access Guide](docs/guides/backend/identity-access-module-implementation.md) - Authentication, users, roles, permissions
- [Assets Module Guide](docs/guides/backend/assets-module-implementation.md) - Asset tracking and valuations

**Use these as templates for:**
- Domain model design (aggregates, entities, value objects)
- Application layer structure (command/query handlers)
- Infrastructure adapters (JPA, REST, messaging)
- Testing strategies (unit, integration)

#### Frontend Implementation
- [React Implementation Guide](docs/guides/frontend/react-implementation-guide.md) - Complete patterns for:
  - API client with interceptors
  - State management (Zustand + TanStack Query)
  - Form handling (React Hook Form + Zod)
  - Component testing (Vitest + RTL)

#### Infrastructure Implementation
- [Temporal Implementation Guide](docs/guides/temporal/temporal-implementation-guide.md) - Workflows, activities, saga pattern
- [RabbitMQ Implementation Guide](docs/guides/rabbitmq/rabbitmq-implementation-guide.md) - Event topology, publishers, consumers

#### Deployment Guides
- [Docker Compose Guide](docs/guides/deployment/docker-compose-guide.md) - Local development with hot reload
- [Kubernetes Deployment Guide](docs/guides/deployment/kubernetes-deployment-guide.md) - Production deployment with Helm
- [CI/CD Pipeline Guide](docs/guides/deployment/cicd-pipeline-guide.md) - GitHub Actions workflows

#### Operations Guides
- [Monitoring & Observability Guide](docs/guides/operations/monitoring-observability-guide.md) - Metrics, logs, traces, alerts
- [Testing Strategy Guide](docs/guides/testing/testing-strategy-guide.md) - Unit, integration, E2E testing

#### Setup Guides
- [Development Setup Guide](docs/guides/setup/development-setup-guide.md) - Complete environment setup from scratch

### Process Documentation

**When implementing flows that span multiple systems, refer to:**

- [User Authentication Process](docs/processes/01-user-authentication-process.md) - Login with JWT, BCrypt, account lockout
- [Token Refresh Process](docs/processes/03-token-refresh-process.md) - Secure token rotation and refresh
- [Purchase Registration Process](docs/processes/02-purchase-registration-process.md) - Temporal workflow with saga compensation
- [CSV Import Process](docs/processes/04-csv-import-process.md) - Async bulk import with validation
- [Expiry Check Process](docs/processes/05-expiry-check-process.md) - Scheduled inventory monitoring

**Each process document includes:**
- Complete flow diagrams (Mermaid)
- Step-by-step implementation
- Full code examples
- Error scenarios
- Testing strategies
- Performance considerations

## Implementation Workflow

### For a New Backend Module

1. **Study Similar Module**: Read one of the module implementation guides
2. **Design Domain Model**: Create aggregates, value objects, domain services
3. **Implement Use Cases**: Add command/query handlers in application layer
4. **Add Infrastructure**: JPA repositories, REST controllers, event publishers
5. **Write Tests**: Follow testing strategy guide
6. **Document**: Update process documentation if needed

### For a New Frontend Feature

1. **Read React Guide**: Follow patterns in react-implementation-guide.md
2. **Create API Client**: Add functions to api/ directory
3. **Build Components**: Use hooks, forms, state management patterns
4. **Add Tests**: Component tests with RTL, hook tests
5. **Integrate**: Connect with backend API

### For a New Workflow

1. **Read Temporal Guide**: Study temporal-implementation-guide.md
2. **Design Workflow**: Define activities, compensation logic
3. **Implement Activities**: Create activity classes with idempotency
4. **Test Workflow**: Use TestWorkflowEnvironment
5. **Monitor**: Add metrics and tracing

## Getting Help

- **Implementation Questions**: Check relevant implementation guide
- **Process Questions**: Check process documentation
- **Architecture Decisions**: Review ADRs in `/docs/adrs/`
- **Code Patterns**: Look at existing modules as examples
- **Before Committing**: Run quality checks and tests
