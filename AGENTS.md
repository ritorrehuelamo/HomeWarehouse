# AGENTS.md - Agent Operating Guidelines

This document defines the operating guidelines for AI agents working on HomeWarehouse.

## Agent Roles

### Code Implementation Agent

Responsible for implementing features according to specifications.

**Capabilities:**
- Write and modify source code
- Create tests
- Update documentation
- Run build and test commands

**Constraints:**
- Follow architecture patterns defined in CLAUDE.md
- Adhere to coding standards
- Respect security guardrails

### Code Review Agent

Responsible for reviewing code changes.

**Focus Areas:**
- Architecture compliance
- Security vulnerabilities
- Code quality
- Test coverage
- Documentation completeness

### Documentation Agent

Responsible for maintaining documentation.

**Scope:**
- Update existing docs when code changes
- Create new docs as needed
- Maintain consistency with templates

## Definition of Done (DoD)

A task is complete when ALL of the following are true:

### Code Quality

- [ ] Code compiles without errors
- [ ] Code follows the hexagonal + DDD + vertical slice patterns
- [ ] No code duplication
- [ ] No hardcoded values (use constants/config)
- [ ] No commented-out code
- [ ] Clear variable and method names
- [ ] Methods are focused (single responsibility)

### Testing

- [ ] Unit tests for domain logic
- [ ] Integration tests for repositories and APIs
- [ ] All tests pass (`./gradlew test`)
- [ ] Reasonable test coverage (target: 80% for domain)
- [ ] Edge cases considered

### Security

- [ ] No secrets in code
- [ ] Input validation implemented
- [ ] Authorization checks in place
- [ ] Audit logging for mutations
- [ ] No security vulnerabilities introduced

### Documentation

- [ ] Code comments for complex logic
- [ ] API documentation updated if endpoints changed
- [ ] ADR created for significant decisions

### Build & Quality

- [ ] `./gradlew build` succeeds
- [ ] `./gradlew check` passes (formatting, linting, static analysis)
- [ ] No new high/critical dependency vulnerabilities

## Coding Standards

### Java Code

```java
// DO: Use records for value objects
public record TransactionId(UUID value) {}

// DO: Use Optional for nullable returns
public Optional<Transaction> findById(TransactionId id);

// DO: Use final for fields
private final TransactionRepository repository;

// DON'T: Use null for parameters
// Bad: void process(Transaction tx) { if (tx == null) ... }
// Good: void process(@NonNull Transaction tx)

// DO: Use domain exceptions
throw new TransactionNotFoundException(transactionId);

// DON'T: Catch generic exceptions
// Bad: catch (Exception e)
// Good: catch (SpecificException e)
```

### Package Organization

```java
// Domain layer - no framework annotations
package com.homewarehouse.ledger.domain.model;
public class Transaction { ... }

// Application layer - use case handlers
package com.homewarehouse.ledger.application.command.createtransaction;
public class CreateTransactionHandler { ... }

// Infrastructure layer - framework-specific
package com.homewarehouse.ledger.infrastructure.web;
@RestController
public class TransactionController { ... }
```

### TypeScript Code

```typescript
// DO: Use interfaces for data shapes
interface Transaction {
  id: string;
  amount: string;
  currency: string;
}

// DO: Use Zod for runtime validation
const TransactionSchema = z.object({
  id: z.string().uuid(),
  amount: z.string().regex(/^\d+(\.\d{1,2})?$/),
  currency: z.string().length(3),
});

// DON'T: Use 'any' type
// Bad: const data: any = response.data;
// Good: const data: Transaction = response.data;

// DO: Handle errors explicitly
try {
  await createTransaction(data);
} catch (error) {
  if (error instanceof ApiError) {
    handleApiError(error);
  } else {
    throw error;
  }
}
```

## Commit Message Format

Use Conventional Commits:

```
<type>(<scope>): <description>

[optional body]

[optional footer]
```

### Types

| Type | Description |
|------|-------------|
| feat | New feature |
| fix | Bug fix |
| docs | Documentation only |
| style | Formatting, no code change |
| refactor | Code change, no feature/fix |
| test | Adding/updating tests |
| chore | Build, config, deps |

### Scopes

| Scope | Description |
|-------|-------------|
| identity | identity-access module |
| ledger | ledger module |
| assets | assets module |
| inventory | inventory module |
| audit | audit module |
| shared | shared-kernel |
| web | frontend |
| infra | infrastructure |
| api | API endpoints |
| workflow | Temporal workflows |
| events | RabbitMQ events |

### Examples

```
feat(ledger): add transaction export endpoint

Implements GET /api/v1/ledger/export with CSV and JSON format support.
Includes date range filtering and account selection.

Closes #42
```

```
fix(inventory): correct expiry notification timing

The expiry check was using wrong timezone, causing notifications
to fire at incorrect times.

Fixes #78
```

```
refactor(identity): extract token validation to service

Moved JWT validation logic from filter to dedicated service
for better testability and reuse.
```

## Prohibited Actions

Agents MUST NOT:

1. **Commit secrets or credentials** to the repository
2. **Disable security checks** without explicit approval
3. **Skip tests** to speed up development
4. **Introduce dependencies** without security review
5. **Bypass RBAC** or authentication checks
6. **Log sensitive data** (passwords, tokens, PII)
7. **Use eval()** or dynamic code execution
8. **Ignore validation errors** or exceptions
9. **Create backdoors** or hidden access methods
10. **Modify audit logs** or bypass audit logging

## Pull Request Checklist

Before submitting code:

### Pre-Submission

- [ ] Read the relevant documentation
- [ ] Understand the acceptance criteria
- [ ] Follow architecture patterns

### Code Review Ready

- [ ] Self-reviewed the code
- [ ] All DoD items checked
- [ ] No TODO comments left (or they're tracked)
- [ ] Branch is up to date with main

### PR Description Template

```markdown
## Summary

Brief description of the changes.

## Changes

- List of specific changes
- Organized by area

## Testing

- How the changes were tested
- What scenarios were covered

## Screenshots (if UI changes)

[Add screenshots]

## Checklist

- [ ] DoD items verified
- [ ] Tests added/updated
- [ ] Documentation updated
- [ ] No security issues introduced
```

## Error Handling Patterns

### Backend

```java
// Domain exception
public class InsufficientFundsException extends DomainException {
    public InsufficientFundsException(AccountId accountId, Money requested, Money available) {
        super(ErrorCode.INSUFFICIENT_FUNDS,
              String.format("Account %s has insufficient funds: requested %s, available %s",
                           accountId, requested, available));
    }
}

// Controller exception handling
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ErrorResponse> handleDomainException(DomainException e) {
        return ResponseEntity
            .status(HttpStatus.UNPROCESSABLE_ENTITY)
            .body(new ErrorResponse(e.getCode(), e.getMessage()));
    }
}
```

### Frontend

```typescript
// API error handling
export async function createTransaction(data: CreateTransactionRequest): Promise<Transaction> {
  try {
    const response = await apiClient.post<Transaction>('/ledger/transactions', data);
    return response.data;
  } catch (error) {
    if (axios.isAxiosError(error) && error.response) {
      throw new ApiError(
        error.response.data.error.code,
        error.response.data.error.message
      );
    }
    throw error;
  }
}
```

## Performance Guidelines

### Backend

- Use pagination for list endpoints (max 100 items)
- Use database indexes for frequent queries
- Cache expensive computations in Redis
- Use async processing for non-critical operations

### Frontend

- Lazy load routes and heavy components
- Use React.memo for expensive renders
- Debounce search inputs
- Use optimistic updates where appropriate

## Monitoring & Observability

### Logging

```java
// DO: Use structured logging
log.info("Transaction created", Map.of(
    "transactionId", transactionId,
    "accountId", accountId,
    "correlationId", correlationId
));

// DON'T: Log sensitive data
// log.info("User logged in with password: " + password);
```

### Metrics

- Expose meaningful metrics via `/metrics`
- Track business metrics (transactions created, imports completed)
- Track technical metrics (latency, error rates)

## Implementation Resources

When implementing features, consult these guides for patterns and examples:

### Backend Implementation Patterns

- [Ledger Module](docs/guides/backend/ledger-module-implementation.md) - Reference implementation for hexagonal + DDD
- [Inventory Module](docs/guides/backend/inventory-module-implementation.md) - Expiry tracking and domain services
- [Identity & Access](docs/guides/backend/identity-access-module-implementation.md) - Authentication patterns
- [Assets Module](docs/guides/backend/assets-module-implementation.md) - Valuation and calculation patterns

**Use these for:**
- Domain model structure (aggregates, value objects, entities)
- Application layer organization (command/query handlers)
- Infrastructure adapters (JPA, REST, messaging)
- Testing patterns (unit, integration, mocking)

### Frontend Patterns

- [React Implementation Guide](docs/guides/frontend/react-implementation-guide.md) - Complete frontend patterns

**Covers:**
- API client setup with interceptors
- State management (Zustand + TanStack Query)
- Form handling (React Hook Form + Zod)
- Component testing (Vitest + RTL)

### Process Flows

When implementing flows that span multiple systems:

- [User Authentication](docs/processes/01-user-authentication-process.md) - Complete auth flow
- [Token Refresh](docs/processes/03-token-refresh-process.md) - Token rotation pattern
- [Purchase Registration](docs/processes/02-purchase-registration-process.md) - Temporal saga pattern
- [CSV Import](docs/processes/04-csv-import-process.md) - Async bulk processing
- [Expiry Check](docs/processes/05-expiry-check-process.md) - Scheduled workflows

**Each includes:**
- Mermaid flow diagrams
- Complete code examples
- Error handling scenarios
- Testing strategies

### Infrastructure Patterns

- [Temporal Guide](docs/guides/temporal/temporal-implementation-guide.md) - Workflow and activity patterns
- [RabbitMQ Guide](docs/guides/rabbitmq/rabbitmq-implementation-guide.md) - Event messaging patterns
- [Testing Strategy](docs/guides/testing/testing-strategy-guide.md) - Comprehensive testing approach

### Deployment & Operations

- [Docker Compose](docs/guides/deployment/docker-compose-guide.md) - Local development setup
- [Kubernetes](docs/guides/deployment/kubernetes-deployment-guide.md) - Production deployment
- [CI/CD Pipeline](docs/guides/deployment/cicd-pipeline-guide.md) - Automated workflows
- [Monitoring](docs/guides/operations/monitoring-observability-guide.md) - Observability setup

### Quick Reference

- [Development Setup](docs/guides/setup/development-setup-guide.md) - Environment setup from scratch
- [CLAUDE.md](CLAUDE.md) - Development conventions
- [docs/](docs/) - Full documentation
- [docs/templates/](docs/templates/) - Documentation templates
- [docs/adrs/](docs/adrs/) - Architecture Decision Records

## Agent Workflow

### Before Starting a Task

1. **Read relevant documentation**
   - Check process documentation for similar flows
   - Review module implementation guides for patterns
   - Study existing code in the same module

2. **Understand the architecture**
   - Review hexagonal architecture boundaries
   - Identify which layer you're working in
   - Plan dependencies according to rules

3. **Plan testing approach**
   - Identify test types needed (unit, integration, E2E)
   - Refer to testing strategy guide
   - Consider edge cases

### During Implementation

1. **Follow established patterns**
   - Use implementation guides as templates
   - Match naming conventions
   - Respect layer boundaries

2. **Document as you go**
   - Add comments for complex logic
   - Update API documentation if needed
   - Create ADR for significant decisions

3. **Test continuously**
   - Write tests alongside code
   - Run tests frequently
   - Verify edge cases

### After Implementation

1. **Self-review against DoD**
   - Check all Definition of Done items
   - Run full build and test suite
   - Verify documentation is updated

2. **Clean up**
   - Remove commented code
   - Remove debug logging
   - Ensure no TODOs left untracked

3. **Prepare for review**
   - Write clear PR description
   - Include testing notes
   - Highlight architectural decisions
