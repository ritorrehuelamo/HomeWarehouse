# HomeWarehouse

A comprehensive personal wealth and home inventory management system built with modern enterprise architecture patterns.

> **Current Status**: Basic application skeleton only - no functionality implemented yet. All services start successfully, demonstrating the project structure. See [SETUP.md](SETUP.md) for quick start instructions.

## Overview

HomeWarehouse helps you manage:
- **Financial Ledger**: Track accounts, transactions, budgets, and expenses
- **Inventory Management**: Monitor items, locations, quantities, and expiration dates
- **Asset Tracking**: Record valuations and depreciation of property and belongings
- **Audit Trail**: Complete history of all system changes

## Technology Stack

### Backend
- **Language**: Java 21
- **Framework**: Spring Boot 3.x
- **Architecture**: Hexagonal + Domain-Driven Design + Vertical Slicing
- **Build**: Gradle (multi-module monolith)
- **Database**: PostgreSQL 16
- **Cache**: Redis 7
- **Messaging**: RabbitMQ 3.x
- **Workflows**: Temporal.io

### Frontend
- **Framework**: React 18
- **Language**: TypeScript
- **Build**: Vite
- **State**: Zustand
- **Validation**: Zod
- **UI**: TailwindCSS + Headless UI

### Infrastructure
- **Deployment**: Kubernetes (home cluster)
- **IaC**: Terraform + Helm
- **Local Dev**: Docker Compose
- **CI/CD**: GitHub Actions

## Quick Start

### Prerequisites

- Java 21 or higher
- Node.js 20 or higher
- Docker and Docker Compose
- Gradle 8.5+ (or use wrapper)

### Local Development Setup

1. **Clone the repository**
   ```bash
   git clone <repository-url>
   cd HomeWarehouse
   ```

2. **Start infrastructure services**
   ```bash
   cd infrastructure/docker
   docker-compose up -d
   ```

3. **Start the backend**
   ```bash
   ./gradlew :backend:app:bootRun
   ```

4. **Start the frontend**
   ```bash
   cd web
   npm install
   npm run dev
   ```

5. **Access the application**
   - Frontend: http://localhost:5173
   - Backend API: http://localhost:8080
   - API Docs: http://localhost:8080/swagger-ui.html

For detailed setup instructions, see [SETUP.md](SETUP.md).

## Documentation

### Getting Started

| Document | Description |
|----------|-------------|
| [Product Overview](docs/overview/00-overview.md) | Product vision, features, and user stories |
| [Development Setup](docs/guides/setup/development-setup-guide.md) | Complete local environment setup |
| [Architecture](docs/architecture/01-architecture.md) | System design and architectural decisions |
| [CLAUDE.md](CLAUDE.md) | AI agent development guide |
| [AGENTS.md](AGENTS.md) | Agent operating guidelines and standards |

### Core Documentation

| Document | Description |
|----------|-------------|
| [Security Requirements](docs/security/02-security.md) | Authentication, authorization, and security practices |
| [Data Model](docs/database/03-data-model.md) | Database schema and entity relationships |
| [API Specification](docs/api/04-api.md) | REST endpoints and contracts |
| [Temporal Workflows](docs/workflows/05-workflows-temporal.md) | Long-running process definitions |
| [RabbitMQ Events](docs/events/06-events-rabbitmq.md) | Event-driven messaging patterns |
| [Frontend Architecture](docs/frontend/07-frontend.md) | React application structure |
| [Infrastructure](docs/infra/08-infra-plan.md) | Deployment and operations |
| [Implementation Backlog](docs/backlog/09-backlog.md) | Development roadmap |

### Implementation Guides

#### Backend Modules
- [Ledger Module](docs/guides/backend/ledger-module-implementation.md) - Accounts, transactions, categories
- [Inventory Module](docs/guides/backend/inventory-module-implementation.md) - Items, locations, expiry tracking
- [Identity & Access](docs/guides/backend/identity-access-module-implementation.md) - Authentication and authorization
- [Assets Module](docs/guides/backend/assets-module-implementation.md) - Asset tracking and valuations

#### Frontend
- [React Implementation Guide](docs/guides/frontend/react-implementation-guide.md) - Component architecture and patterns

#### Infrastructure
- [Temporal Implementation](docs/guides/temporal/temporal-implementation-guide.md) - Workflow development
- [RabbitMQ Implementation](docs/guides/rabbitmq/rabbitmq-implementation-guide.md) - Event messaging patterns

#### Deployment
- [Docker Compose Guide](docs/guides/deployment/docker-compose-guide.md) - Local development environment
- [Kubernetes Deployment](docs/guides/deployment/kubernetes-deployment-guide.md) - Production deployment
- [CI/CD Pipeline](docs/guides/deployment/cicd-pipeline-guide.md) - Automated build and deployment

#### Operations
- [Monitoring & Observability](docs/guides/operations/monitoring-observability-guide.md) - Logging, metrics, and tracing
- [Testing Strategy](docs/guides/testing/testing-strategy-guide.md) - Unit, integration, and e2e tests

### Process Documentation

Detailed process flows with step-by-step implementation:

| Process | Description |
|---------|-------------|
| [User Authentication](docs/processes/01-user-authentication-process.md) | Login flow with JWT tokens |
| [Token Refresh](docs/processes/03-token-refresh-process.md) | Secure token rotation |
| [Purchase Registration](docs/processes/02-purchase-registration-process.md) | Multi-system purchase workflow |
| [CSV Import](docs/processes/04-csv-import-process.md) | Bulk transaction import |
| [Expiry Check](docs/processes/05-expiry-check-process.md) | Inventory expiration monitoring |

### Architecture Decision Records

All significant architectural decisions are documented in [docs/adrs/](docs/adrs/).

## Repository Structure

```
HomeWarehouse/
├── README.md                    # This file
├── CLAUDE.md                    # AI agent development guide
├── AGENTS.md                    # Agent operating guidelines
├── docs/                        # Complete documentation
│   ├── overview/                # Product overview
│   ├── architecture/            # System architecture
│   ├── security/                # Security requirements
│   ├── database/                # Data model
│   ├── api/                     # API specification
│   ├── workflows/               # Temporal workflows
│   ├── events/                  # RabbitMQ events
│   ├── frontend/                # Frontend architecture
│   ├── infra/                   # Infrastructure
│   ├── backlog/                 # Implementation backlog
│   ├── adrs/                    # Architecture Decision Records
│   ├── processes/               # Detailed process flows
│   ├── guides/                  # Implementation guides
│   │   ├── backend/             # Module implementation guides
│   │   ├── frontend/            # Frontend guides
│   │   ├── temporal/            # Workflow guides
│   │   ├── rabbitmq/            # Messaging guides
│   │   ├── deployment/          # Deployment guides
│   │   ├── operations/          # Operations guides
│   │   ├── testing/             # Testing guides
│   │   └── setup/               # Setup guides
│   └── templates/               # Documentation templates
├── backend/                     # Java Spring Boot backend
│   ├── shared-kernel/           # Cross-cutting primitives
│   ├── identity-access/         # Auth, users, roles
│   ├── ledger/                  # Financial transactions
│   ├── assets/                  # Asset tracking
│   ├── inventory/               # Inventory management
│   ├── audit/                   # Audit logging
│   └── app/                     # Main Spring Boot application
├── web/                         # React frontend
├── infrastructure/              # IaC and deployment
│   ├── docker/                  # Docker Compose
│   ├── helm/                    # Helm charts
│   └── terraform/               # Terraform modules
└── gradle/                      # Gradle wrapper and catalogs
```

## Development Workflow

### Adding a New Feature

1. **Design**
   - Review [Architecture](docs/architecture/01-architecture.md)
   - Create ADR if needed (see [templates](docs/templates/))
   - Update relevant process documentation

2. **Backend Implementation**
   - Follow [module implementation guide](docs/guides/backend/) for your domain
   - Implement domain model (no framework dependencies)
   - Create application use cases (command/query handlers)
   - Add infrastructure adapters (REST, JPA, messaging)
   - Write tests (unit + integration)

3. **Frontend Implementation**
   - Follow [React implementation guide](docs/guides/frontend/react-implementation-guide.md)
   - Create components and hooks
   - Add API client functions
   - Implement state management
   - Write tests (Vitest + RTL)

4. **Quality Checks**
   ```bash
   # Backend
   ./gradlew check        # Run all checks
   ./gradlew test         # Run tests
   ./gradlew spotlessApply # Format code

   # Frontend
   npm run lint           # Lint code
   npm test               # Run tests
   npm run type-check     # TypeScript check
   ```

5. **Documentation**
   - Update API documentation
   - Add/update process flows if needed
   - Document significant decisions in ADRs

### Common Commands

#### Backend

```bash
# Build all modules
./gradlew build

# Run specific module tests
./gradlew :backend:ledger:test

# Start application
./gradlew :backend:app:bootRun

# Code quality
./gradlew check
./gradlew spotlessApply
```

#### Frontend

```bash
cd web

# Development
npm run dev

# Production build
npm run build

# Testing
npm test
npm run test:coverage

# Code quality
npm run lint
npm run type-check
```

#### Infrastructure

```bash
cd infrastructure/docker

# Start all services
docker-compose up -d

# View logs
docker-compose logs -f [service-name]

# Stop services
docker-compose down

# Reset all data
docker-compose down -v
```

## Key Architecture Patterns

HomeWarehouse implements four architectural patterns simultaneously:

### 1. Hexagonal Architecture (Ports & Adapters)
- Domain logic isolated from infrastructure
- Inbound and outbound ports define boundaries
- Adapters implement infrastructure concerns

### 2. Domain-Driven Design (DDD)
- Bounded contexts align with business domains
- Aggregates ensure consistency boundaries
- Value objects encapsulate business concepts
- Domain events for cross-context communication

### 3. Vertical Slicing
- Features organized by use case, not technical layer
- Each slice is a complete vertical through all layers
- Reduces coupling, increases cohesion

### 4. Screaming Architecture
- Package names reflect business domains
- Project structure reveals system purpose
- Technical details are secondary

See [Architecture Documentation](docs/architecture/01-architecture.md) for details.

## Module Dependency Rules

- **Domain**: Depends on nothing (pure Java)
- **Application**: Depends on domain only
- **Infrastructure**: Depends on application and domain
- **shared-kernel**: Contains only cross-cutting primitives
- **Cross-module**: Communication via events or Temporal workflows

## Security

Security is a first-class concern in HomeWarehouse:

- **Authentication**: JWT with RS256, short-lived access tokens
- **Authorization**: RBAC with granular permissions
- **Audit**: Complete audit trail of all mutations
- **Data Protection**: Encryption at rest and in transit
- **Secrets**: Environment variables, never in code
- **Rate Limiting**: Protection against abuse

See [Security Documentation](docs/security/02-security.md) for complete requirements.

## Testing Strategy

| Test Type | Tool | Coverage |
|-----------|------|----------|
| Unit Tests | JUnit 5, AssertJ | Domain logic, pure functions |
| Integration Tests | Testcontainers | Repositories, APIs, workflows |
| E2E Tests | Playwright | Critical user journeys |
| Contract Tests | Spring Cloud Contract | API contracts |
| Performance Tests | Gatling | Load and stress testing |

See [Testing Strategy Guide](docs/guides/testing/testing-strategy-guide.md) for details.

## Monitoring & Observability

- **Logging**: Structured JSON logs with correlation IDs
- **Metrics**: Micrometer with Prometheus
- **Tracing**: OpenTelemetry distributed tracing
- **Dashboards**: Grafana for visualization
- **Alerts**: Prometheus Alertmanager

See [Monitoring Guide](docs/guides/operations/monitoring-observability-guide.md) for setup.

## Contributing

### Code Standards

- **Java**: Google Java Style Guide, spotless enforced
- **TypeScript**: ESLint + Prettier, strict mode
- **Commits**: Conventional Commits format
- **Testing**: 80% coverage target for domain logic
- **Documentation**: Update docs with code changes

### Definition of Done

Before marking work complete:

- [ ] Code compiles and passes all tests
- [ ] Follows architecture patterns (hexagonal, DDD, vertical slice)
- [ ] Security requirements met
- [ ] Documentation updated
- [ ] Code reviewed
- [ ] CI/CD pipeline passes

See [AGENTS.md](AGENTS.md) for complete checklist.

### Pull Request Process

1. Create feature branch from `main`
2. Implement changes following guidelines
3. Run quality checks locally
4. Create PR with description template
5. Address review feedback
6. Merge after approval

## Troubleshooting

### Common Issues

**Backend won't start**
- Ensure PostgreSQL, Redis, RabbitMQ are running
- Check `infrastructure/docker/docker-compose.yml`
- Verify Java 21 is installed: `java -version`

**Frontend build errors**
- Clear node_modules: `rm -rf node_modules && npm install`
- Check Node version: `node --version` (should be 20+)

**Test failures**
- Ensure Testcontainers can access Docker
- Check test database configuration
- Run tests with `./gradlew test --info` for details

**Database migration errors**
- Check Flyway migration files in `src/main/resources/db/migration/`
- Ensure migrations are sequential and idempotent
- Reset database: `docker-compose down -v && docker-compose up -d`

See individual guides for module-specific troubleshooting.

## Resources

### External Documentation
- [Spring Boot Reference](https://docs.spring.io/spring-boot/docs/current/reference/html/)
- [React Documentation](https://react.dev/)
- [Temporal Documentation](https://docs.temporal.io/)
- [PostgreSQL Manual](https://www.postgresql.org/docs/)

### Learning Resources
- [Domain-Driven Design](https://domainlanguage.com/ddd/)
- [Hexagonal Architecture](https://alistair.cockburn.us/hexagonal-architecture/)
- [Vertical Slice Architecture](https://www.youtube.com/watch?v=SUiWfhAhgQw)

## License

This is a personal project for learning and home use.

## Contact

For questions or issues, please create an issue in the repository.

---

**Happy Coding!** Remember to follow the architecture patterns and keep security top of mind.
