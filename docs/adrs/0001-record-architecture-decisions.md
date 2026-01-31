# ADR-0001: Record Architecture Decisions

## Status

Accepted

## Date

2024-01-15

## Context

HomeWarehouse is a personal wealth and home inventory management system with significant architectural complexity:
- Multi-module backend with hexagonal architecture
- Domain-Driven Design patterns
- Multiple infrastructure dependencies (PostgreSQL, Redis, RabbitMQ, Temporal)
- Strict security requirements
- Future microservices extraction potential

We need a way to document significant architectural decisions so that:
1. The rationale behind decisions is preserved
2. Future developers understand why things are the way they are
3. Decisions can be revisited when context changes
4. The project maintains consistency over time

## Decision

We will adopt the Architecture Decision Record (ADR) process for documenting significant technical decisions. ADRs will be stored in `/docs/adrs/` and follow a consistent template.

### ADR Conventions

1. **Numbering**: ADRs are numbered sequentially (0001, 0002, etc.)
2. **Immutability**: ADRs are not modified after acceptance (except status)
3. **Superseding**: New decisions reference and supersede old ones
4. **Template**: All ADRs follow `/docs/templates/adr-template.md`

### What Warrants an ADR

- Technology choices (frameworks, databases, messaging)
- Architectural patterns (hexagonal, DDD, CQRS)
- Security approaches
- Build and deployment strategies
- Major trade-offs affecting the system

### What Does NOT Need an ADR

- Implementation details
- Bug fixes
- Minor refactoring
- Library version updates (unless major)

## Consequences

### Positive

- Architectural knowledge is preserved and searchable
- Onboarding new contributors is easier
- Decisions are made more deliberately
- Technical debt is better understood

### Negative

- Overhead of writing ADRs for every significant decision
- ADRs may become outdated if not maintained
- Risk of analysis paralysis

### Neutral

- Requires discipline to maintain
- Part of the definition of done for architectural changes

## Key Architectural Decisions for HomeWarehouse

This ADR establishes the following foundational decisions:

### 1. Architectural Style: Hexagonal + DDD + Vertical Slices + Screaming Architecture

**Decision:** Implement all four patterns simultaneously.

**Rationale:**
- Hexagonal: Keeps domain logic framework-free and testable
- DDD: Provides clear domain boundaries and ubiquitous language
- Vertical Slices: Organizes code by feature for maintainability
- Screaming Architecture: Makes the domain visible in package structure

### 2. Backend: Java + Spring Boot Multi-Module Monolith

**Decision:** Use Java 21 with Spring Boot 3.x in a Gradle multi-module structure.

**Rationale:**
- Java: Mature ecosystem, strong typing, excellent tooling
- Spring Boot: Industry standard, comprehensive features
- Multi-module: Clear boundaries, future microservices extraction

**Alternatives Considered:**
- Kotlin: Excellent but team familiarity with Java is higher
- Microservices from start: Too much operational complexity for home deployment

### 3. Build System: Gradle with Kotlin DSL

**Decision:** Use Gradle with Kotlin DSL and convention plugins.

**Rationale:**
- Better performance than Maven
- Kotlin DSL provides IDE support and type safety
- Convention plugins ensure consistency across modules
- Version catalogs centralize dependency management

**Alternatives Considered:**
- Maven: More familiar but slower and less flexible
- Gradle Groovy: Less type-safe than Kotlin DSL

### 4. Database: PostgreSQL

**Decision:** Use PostgreSQL 16 as the primary database.

**Rationale:**
- Robust, mature, and feature-rich
- Excellent JSON support for flexible schemas
- Strong data integrity guarantees
- Free and open source

**Alternatives Considered:**
- MySQL: Less feature-rich for complex queries
- SQLite: Not suitable for multi-process access

### 5. Message Broker: RabbitMQ

**Decision:** Use RabbitMQ for domain events and async processing.

**Rationale:**
- Well-suited for at-least-once delivery patterns
- Rich routing capabilities with topic exchanges
- Lower operational complexity than Kafka for this scale
- Publisher confirms and consumer acks for reliability

**Alternatives Considered:**
- Kafka: Overkill for expected message volume
- Redis Streams: Less mature for messaging patterns

### 6. Workflow Orchestration: Temporal.io

**Decision:** Use Temporal for durable workflow orchestration.

**Rationale:**
- Handles failure, retries, and compensation automatically
- Code-based workflow definitions (not YAML/XML)
- Excellent visibility into workflow state
- Java SDK with Spring Boot integration

**Alternatives Considered:**
- Camunda: Heavier BPMN focus
- Custom saga implementation: Error-prone and complex

### 7. Cache and Token Store: Redis

**Decision:** Use Redis for caching, rate limiting, and refresh token storage.

**Rationale:**
- Fast key-value operations
- Native TTL support for token expiry
- Widely adopted and well-documented
- Low operational overhead

### 8. Frontend: React + TypeScript

**Decision:** Use React 18 with TypeScript for the web frontend.

**Rationale:**
- Component-based architecture
- Strong type safety with TypeScript
- Large ecosystem and community
- Good developer tooling

**Alternatives Considered:**
- Vue: Excellent but smaller ecosystem
- Angular: Too heavy for this application size

### 9. Diagrams: Mermaid Only

**Decision:** All architecture diagrams must use Mermaid syntax.

**Rationale:**
- Version-controlled as text
- Renders in GitHub, IDEs, and documentation sites
- Consistent rendering across platforms
- No external tools required

**Alternatives Considered:**
- ASCII diagrams: Poor rendering, hard to maintain
- PlantUML: Requires Java runtime
- Draw.io: Binary files, harder to diff

### 10. Deployment: Home Kubernetes with Terraform + Helm

**Decision:** Deploy to a home Kubernetes cluster using Terraform for provisioning and Helm for application deployment.

**Rationale:**
- Reproducible infrastructure as code
- Kubernetes provides container orchestration
- Helm provides templated deployments
- Terraform manages the full stack

**Alternatives Considered:**
- Docker Compose in production: Less robust than Kubernetes
- Manual deployment: Not reproducible

## References

- [Michael Nygard's ADR process](https://cognitect.com/blog/2011/11/15/documenting-architecture-decisions)
- [ADR GitHub organization](https://adr.github.io/)
- [Hexagonal Architecture](https://alistair.cockburn.us/hexagonal-architecture/)
- [Domain-Driven Design](https://domainlanguage.com/ddd/)

## Notes

All future ADRs should reference this document as the starting point for the ADR process in HomeWarehouse.
