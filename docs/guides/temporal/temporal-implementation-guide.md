# Temporal Implementation Guide

## Purpose

This guide provides step-by-step instructions for implementing Temporal workflows and activities in HomeWarehouse, including setup, testing, and best practices.

## Prerequisites

Before implementing Temporal workflows, ensure:

- [ ] Temporal server is running (via docker-compose or deployed)
- [ ] Spring Boot application is configured with Temporal client
- [ ] Database is accessible for activity implementations
- [ ] Understanding of Temporal concepts (workflows, activities, signals, queries)

## Project Setup

### Dependencies

Add to `backend/build.gradle.kts`:

```kotlin
dependencies {
    // Temporal SDK
    implementation("io.temporal:temporal-sdk:1.22.0")

    // Spring Boot integration
    implementation("io.temporal.spring:spring-boot-starter-temporal:1.22.0")

    // Testing
    testImplementation("io.temporal:temporal-testing:1.22.0")
}
```

### Spring Configuration

Create `TemporalConfiguration.java`:

```java
package com.homewarehouse.app.config;

import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowClientOptions;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkflowFactory;
import io.temporal.worker.WorkerOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.configuration.

@Configuration
public class TemporalConfiguration {

    @Value("${temporal.target:localhost:7233}")
    private String temporalTarget;

    @Value("${temporal.namespace:homewarehouse}")
    private String namespace;

    @Value("${temporal.task-queue:homewarehouse-tasks}")
    private String taskQueue;

    @Bean
    public WorkflowServiceStubs workflowServiceStubs() {
        WorkflowServiceStubsOptions options = WorkflowServiceStubsOptions.newBuilder()
            .setTarget(temporalTarget)
            .build();

        return WorkflowServiceStubs.newServiceStubs(options);
    }

    @Bean
    public WorkflowClient workflowClient(WorkflowServiceStubs serviceStubs) {
        WorkflowClientOptions options = WorkflowClientOptions.newBuilder()
            .setNamespace(namespace)
            .build();

        return WorkflowClient.newInstance(serviceStubs, options);
    }

    @Bean
    public Worker worker(
            WorkflowClient workflowClient,
            WorkflowFactory workflowFactory
    ) {
        Worker worker = workflowFactory.newWorker(taskQueue);

        // Register workflows
        worker.registerWorkflowImplementationTypes(
            PurchaseRegistrationWorkflowImpl.class,
            CsvImportWorkflowImpl.class,
            ExpiryCheckWorkflowImpl.class
        );

        // Activities are registered via Spring annotations
        return worker;
    }

    @Bean
    public WorkerFactory workerFactory(
            WorkflowClient workflowClient,
            ApplicationContext applicationContext
    ) {
        WorkerFactory factory = WorkerFactory.newInstance(workflowClient);

        Worker worker = factory.newWorker(taskQueue, WorkerOptions.newBuilder()
            .setMaxConcurrentActivityExecutionSize(20)
            .setMaxConcurrentWorkflowTaskExecutionSize(10)
            .build());

        // Register all Spring beans annotated with @ActivityImpl
        applicationContext.getBeansWithAnnotation(ActivityImpl.class)
            .values()
            .forEach(worker::registerActivitiesImplementations);

        return factory;
    }

    @Bean(destroyMethod = "shutdown")
    public WorkerFactoryStarter workerFactoryStarter(WorkerFactory factory) {
        factory.start();
        return new WorkerFactoryStarter(factory);
    }

    public static class WorkerFactoryStarter {
        private final WorkerFactory factory;

        public WorkerFactoryStarter(WorkerFactory factory) {
            this.factory = factory;
        }

        public void shutdown() {
            factory.shutdown();
        }
    }
}
```

### Application Properties

```yaml
# application.yml
temporal:
  target: ${TEMPORAL_TARGET:localhost:7233}
  namespace: ${TEMPORAL_NAMESPACE:homewarehouse}
  task-queue: homewarehouse-tasks
  worker:
    max-concurrent-activities: 20
    max-concurrent-workflows: 10
```

---

## Implementing a Workflow

### Step 1: Define Workflow Interface

Create the workflow interface in the `application` layer:

```java
// backend/ledger/src/main/java/com/homewarehouse/ledger/application/workflow/PurchaseRegistrationWorkflow.java

package com.homewarehouse.ledger.application.workflow;

import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;
import io.temporal.workflow.QueryMethod;

@WorkflowInterface
public interface PurchaseRegistrationWorkflow {

    /**
     * Main workflow method - executed when workflow starts
     */
    @WorkflowMethod
    PurchaseRegistrationResult execute(PurchaseRegistrationCommand command);

    /**
     * Query method - can be called while workflow is running
     */
    @QueryMethod
    PurchaseRegistrationStatus getStatus();

    /**
     * Signal method - can send signals to running workflow
     * (Example - not used in this workflow but shown for reference)
     */
    @SignalMethod
    void cancel();
}
```

### Step 2: Define Command and Result DTOs

```java
// PurchaseRegistrationCommand.java
package com.homewarehouse.ledger.application.workflow;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Value
@Builder
public class PurchaseRegistrationCommand {
    UUID userId;
    UUID accountId;
    LocalDate transactionDate;
    Money totalAmount;
    String counterparty;
    String description;
    UUID categoryId;
    List<PurchaseItem> items;
    UUID idempotencyKey;
    CorrelationId correlationId;
}

@Value
@Builder
public class PurchaseItem {
    UUID itemId;
    UUID locationId;
    int quantity;
    Money unitPrice;
    LocalDate bestBefore;
    LocalDate expiresAt;
}

// PurchaseRegistrationResult.java
@Value
@Builder
public class PurchaseRegistrationResult {
    UUID correlationId;
    UUID transactionId;
    List<UUID> unitIds;
    PurchaseRegistrationStatus status;
    String errorMessage;

    public static PurchaseRegistrationResult success(
            UUID correlationId,
            UUID transactionId,
            List<UUID> unitIds
    ) {
        return PurchaseRegistrationResult.builder()
            .correlationId(correlationId)
            .transactionId(transactionId)
            .unitIds(unitIds)
            .status(PurchaseRegistrationStatus.COMPLETED)
            .build();
    }

    public static PurchaseRegistrationResult failed(
            UUID correlationId,
            String errorMessage
    ) {
        return PurchaseRegistrationResult.builder()
            .correlationId(correlationId)
            .status(PurchaseRegistrationStatus.FAILED)
            .errorMessage(errorMessage)
            .build();
    }
}

// PurchaseRegistrationStatus.java
public enum PurchaseRegistrationStatus {
    PROCESSING,
    VALIDATING,
    CREATING_TRANSACTION,
    CREATING_UNITS,
    PUBLISHING_EVENT,
    AUDITING,
    COMPLETED,
    ROLLING_BACK,
    FAILED
}
```

### Step 3: Implement the Workflow

```java
// PurchaseRegistrationWorkflowImpl.java
package com.homewarehouse.ledger.infrastructure.temporal;

import io.temporal.activity.ActivityOptions;
import io.temporal.workflow.Workflow;
import io.temporal.common.RetryOptions;

import java.time.Duration;

public class PurchaseRegistrationWorkflowImpl implements PurchaseRegistrationWorkflow {

    // Workflow state (must be serializable)
    private PurchaseRegistrationStatus status = PurchaseRegistrationStatus.PROCESSING;
    private String errorMessage;

    // Activity stubs - configured with retry policies
    private final ValidationActivities validationActivities = Workflow.newActivityStub(
        ValidationActivities.class,
        ActivityOptions.newBuilder()
            .setStartToCloseTimeout(Duration.ofSeconds(30))
            .setRetryOptions(RetryOptions.newBuilder()
                .setInitialInterval(Duration.ofMillis(100))
                .setMaximumInterval(Duration.ofSeconds(10))
                .setBackoffCoefficient(2.0)
                .setMaximumAttempts(3)
                .build())
            .build()
    );

    private final LedgerActivities ledgerActivities = Workflow.newActivityStub(
        LedgerActivities.class,
        ActivityOptions.newBuilder()
            .setStartToCloseTimeout(Duration.ofMinutes(2))
            .setRetryOptions(RetryOptions.newBuilder()
                .setInitialInterval(Duration.ofSeconds(1))
                .setMaximumInterval(Duration.ofMinutes(1))
                .setBackoffCoefficient(2.0)
                .setMaximumAttempts(5)
                // Don't retry business logic errors
                .setDoNotRetry(
                    BusinessRuleViolationException.class.getName(),
                    ValidationException.class.getName()
                )
                .build())
            .build()
    );

    private final InventoryActivities inventoryActivities = Workflow.newActivityStub(
        InventoryActivities.class,
        ActivityOptions.newBuilder()
            .setStartToCloseTimeout(Duration.ofMinutes(2))
            .setRetryOptions(RetryOptions.newBuilder()
                .setInitialInterval(Duration.ofSeconds(1))
                .setMaximumInterval(Duration.ofMinutes(1))
                .setBackoffCoefficient(2.0)
                .setMaximumAttempts(5)
                .setDoNotRetry(
                    BusinessRuleViolationException.class.getName()
                )
                .build())
            .build()
    );

    private final EventActivities eventActivities = Workflow.newActivityStub(
        EventActivities.class,
        ActivityOptions.newBuilder()
            .setStartToCloseTimeout(Duration.ofSeconds(30))
            .setRetryOptions(RetryOptions.newBuilder()
                .setInitialInterval(Duration.ofSeconds(1))
                .setMaximumInterval(Duration.ofMinutes(5))
                .setBackoffCoefficient(2.0)
                .setMaximumAttempts(10) // Retry more for external service
                .build())
            .build()
    );

    private final AuditActivities auditActivities = Workflow.newActivityStub(
        AuditActivities.class,
        ActivityOptions.newBuilder()
            .setStartToCloseTimeout(Duration.ofSeconds(30))
            .setRetryOptions(RetryOptions.newBuilder()
                .setMaximumAttempts(3)
                .build())
            .build()
    );

    @Override
    public PurchaseRegistrationResult execute(PurchaseRegistrationCommand command) {
        UUID transactionId = null;
        List<UUID> unitIds = new ArrayList<>();

        try {
            // Step 1: Validate
            status = PurchaseRegistrationStatus.VALIDATING;
            Workflow.getLogger(this.getClass()).info("Validating purchase request");

            ValidationResult validation = validationActivities.validatePurchaseRequest(command);

            if (!validation.isValid()) {
                status = PurchaseRegistrationStatus.FAILED;
                errorMessage = formatValidationErrors(validation.getErrors());

                Workflow.getLogger(this.getClass())
                    .warn("Validation failed: {}", errorMessage);

                // Audit failure
                auditActivities.auditPurchaseFailure(AuditPurchaseFailureData.builder()
                    .userId(command.getUserId())
                    .correlationId(command.getCorrelationId())
                    .reason("VALIDATION_FAILED")
                    .details(errorMessage)
                    .build());

                return PurchaseRegistrationResult.failed(
                    command.getCorrelationId().value(),
                    errorMessage
                );
            }

            // Step 2: Create Transaction
            status = PurchaseRegistrationStatus.CREATING_TRANSACTION;
            Workflow.getLogger(this.getClass()).info("Creating ledger transaction");

            CreateTransactionData transactionData = CreateTransactionData.builder()
                .accountId(command.getAccountId())
                .categoryId(command.getCategoryId())
                .amount(command.getTotalAmount())
                .description(command.getDescription())
                .transactionDate(command.getTransactionDate())
                .counterparty(command.getCounterparty())
                .reference(null)
                .idempotencyKey(command.getIdempotencyKey())
                .correlationId(command.getCorrelationId())
                .build();

            transactionId = ledgerActivities.createTransaction(transactionData);

            Workflow.getLogger(this.getClass())
                .info("Transaction created: {}", transactionId);

            // Step 3: Create Inventory Units
            status = PurchaseRegistrationStatus.CREATING_UNITS;
            Workflow.getLogger(this.getClass()).info("Creating inventory units");

            CreateUnitsData unitsData = CreateUnitsData.builder()
                .items(command.getItems().stream()
                    .map(this::mapToCreateUnitItem)
                    .collect(Collectors.toList()))
                .baseIdempotencyKey(command.getIdempotencyKey())
                .correlationId(command.getCorrelationId())
                .build();

            unitIds = inventoryActivities.createUnits(unitsData);

            Workflow.getLogger(this.getClass())
                .info("Created {} inventory units", unitIds.size());

            // Step 4: Publish Event
            status = PurchaseRegistrationStatus.PUBLISHING_EVENT;
            Workflow.getLogger(this.getClass()).info("Publishing domain event");

            PublishPurchaseEventData eventData = PublishPurchaseEventData.builder()
                .correlationId(command.getCorrelationId().value())
                .transactionId(transactionId)
                .unitIds(unitIds)
                .accountId(command.getAccountId())
                .totalAmount(command.getTotalAmount().getAmount())
                .currency(command.getTotalAmount().getCurrency().getCurrencyCode())
                .purchaseDate(command.getTransactionDate())
                .counterparty(command.getCounterparty())
                .itemCount(command.getItems().size())
                .build();

            eventActivities.publishPurchaseRegistered(eventData);

            // Step 5: Audit Success
            status = PurchaseRegistrationStatus.AUDITING;
            Workflow.getLogger(this.getClass()).info("Writing audit record");

            AuditPurchaseData auditData = AuditPurchaseData.builder()
                .userId(command.getUserId())
                .transactionId(transactionId)
                .unitIds(unitIds)
                .accountId(command.getAccountId())
                .totalAmount(command.getTotalAmount().getAmount())
                .currency(command.getTotalAmount().getCurrency().getCurrencyCode())
                .correlationId(command.getCorrelationId().value())
                .build();

            auditActivities.auditPurchaseRegistration(auditData);

            // Complete
            status = PurchaseRegistrationStatus.COMPLETED;
            Workflow.getLogger(this.getClass())
                .info("Purchase registration completed successfully");

            return PurchaseRegistrationResult.success(
                command.getCorrelationId().value(),
                transactionId,
                unitIds
            );

        } catch (Exception e) {
            // Compensation (Saga pattern)
            status = PurchaseRegistrationStatus.ROLLING_BACK;

            Workflow.getLogger(this.getClass())
                .error("Purchase registration failed, rolling back", e);

            try {
                // Rollback units if created
                if (!unitIds.isEmpty()) {
                    Workflow.getLogger(this.getClass())
                        .info("Deleting {} units", unitIds.size());
                    inventoryActivities.deleteUnits(unitIds);
                }

                // Rollback transaction if created
                if (transactionId != null) {
                    Workflow.getLogger(this.getClass())
                        .info("Deleting transaction {}", transactionId);
                    ledgerActivities.deleteTransaction(transactionId);
                }

                // Audit rollback
                auditActivities.auditPurchaseRollback(AuditPurchaseRollbackData.builder()
                    .userId(command.getUserId())
                    .correlationId(command.getCorrelationId())
                    .transactionId(transactionId)
                    .unitIds(unitIds)
                    .reason(e.getMessage())
                    .build());

                Workflow.getLogger(this.getClass())
                    .info("Rollback completed successfully");

            } catch (Exception rollbackException) {
                // Log but don't fail - manual intervention may be needed
                Workflow.getLogger(this.getClass())
                    .error("Rollback failed - manual intervention required", rollbackException);

                // Could send alert here
            }

            status = PurchaseRegistrationStatus.FAILED;
            errorMessage = "Purchase registration failed: " + e.getMessage();

            return PurchaseRegistrationResult.failed(
                command.getCorrelationId().value(),
                errorMessage
            );
        }
    }

    @Override
    public PurchaseRegistrationStatus getStatus() {
        return status;
    }

    @Override
    public void cancel() {
        // Implementation for cancellation if needed
        Workflow.getLogger(this.getClass()).info("Cancellation requested");
        // Set flag and check in workflow execution
    }

    private String formatValidationErrors(List<ValidationError> errors) {
        return errors.stream()
            .map(e -> e.getField() + ": " + e.getMessage())
            .collect(Collectors.joining("; "));
    }

    private CreateUnitItem mapToCreateUnitItem(PurchaseItem item) {
        return CreateUnitItem.builder()
            .itemId(item.getItemId())
            .locationId(item.getLocationId())
            .quantity(item.getQuantity())
            .unitPrice(item.getUnitPrice())
            .purchaseDate(LocalDate.now())
            .bestBefore(item.getBestBefore())
            .expiresAt(item.getExpiresAt())
            .build();
    }
}
```

---

## Implementing Activities

### Step 1: Define Activity Interface

```java
// ValidationActivities.java
package com.homewarehouse.ledger.application.workflow;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

@ActivityInterface
public interface ValidationActivities {

    @ActivityMethod
    ValidationResult validatePurchaseRequest(PurchaseRegistrationCommand command);
}
```

### Step 2: Implement the Activity

Activities are implemented in the infrastructure layer:

```java
// ValidationActivitiesImpl.java
package com.homewarehouse.ledger.infrastructure.temporal;

import io.temporal.activity.Activity;
import io.temporal.spring.boot.ActivityImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@ActivityImpl(taskQueues = "homewarehouse-tasks")
@RequiredArgsConstructor
@Slf4j
public class ValidationActivitiesImpl implements ValidationActivities {

    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final InventoryItemRepository itemRepository;
    private final LocationRepository locationRepository;
    private final IdempotencyService idempotencyService;

    @Override
    public ValidationResult validatePurchaseRequest(PurchaseRegistrationCommand command) {
        // Get activity info for logging
        String activityType = Activity.getExecutionContext().getInfo().getActivityType();
        log.info("Executing activity: {}", activityType);

        List<ValidationError> errors = new ArrayList<>();

        try {
            // Record heartbeat for long-running activities
            Activity.getExecutionContext().heartbeat(null);

            // 1. Validate user
            User user = userRepository.findById(command.getUserId())
                .orElseThrow(() -> new UserNotFoundException(command.getUserId()));

            if (!user.hasPermission("ledger:transactions:write")) {
                errors.add(new ValidationError(
                    "permissions",
                    "Missing ledger write permission"
                ));
            }

            if (!user.hasPermission("inventory:units:write")) {
                errors.add(new ValidationError(
                    "permissions",
                    "Missing inventory write permission"
                ));
            }

            // 2. Validate account
            Account account = accountRepository.findById(command.getAccountId())
                .orElse(null);

            if (account == null) {
                errors.add(new ValidationError(
                    "accountId",
                    "Account not found"
                ));
            } else {
                if (!account.getUserId().equals(command.getUserId())) {
                    errors.add(new ValidationError(
                        "accountId",
                        "Account does not belong to user"
                    ));
                }

                if (!account.isActive()) {
                    errors.add(new ValidationError(
                        "accountId",
                        "Account is not active"
                    ));
                }
            }

            // Record heartbeat again
            Activity.getExecutionContext().heartbeat(null);

            // 3. Validate items (implementation continues...)
            validateItems(command, errors);

            // 4. Validate locations
            validateLocations(command, errors);

            // 5. Validate amounts
            validateAmounts(command, errors);

            // 6. Check idempotency
            if (idempotencyService.isProcessed(command.getIdempotencyKey())) {
                errors.add(new ValidationError(
                    "idempotencyKey",
                    "Purchase already processed"
                ));
            }

            return new ValidationResult(errors.isEmpty(), errors);

        } catch (Exception e) {
            log.error("Validation activity failed", e);
            // Temporal will retry based on retry policy
            throw new RuntimeException("Validation failed", e);
        }
    }

    private void validateItems(
            PurchaseRegistrationCommand command,
            List<ValidationError> errors
    ) {
        // Implementation...
    }

    private void validateLocations(
            PurchaseRegistrationCommand command,
            List<ValidationError> errors
    ) {
        // Implementation...
    }

    private void validateAmounts(
            PurchaseRegistrationCommand command,
            List<ValidationError> errors
    ) {
        BigDecimal calculatedTotal = command.getItems().stream()
            .map(item -> item.getUnitPrice().getAmount()
                .multiply(BigDecimal.valueOf(item.getQuantity())))
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (calculatedTotal.compareTo(command.getTotalAmount().getAmount()) != 0) {
            errors.add(new ValidationError(
                "totalAmount",
                String.format("Total mismatch: expected %s, got %s",
                    calculatedTotal,
                    command.getTotalAmount().getAmount())
            ));
        }
    }
}
```

### Activity Best Practices

#### 1. Heartbeats for Long Activities

```java
@Override
public void longRunningActivity(Data data) {
    for (int i = 0; i < data.getItems().size(); i++) {
        processItem(data.getItems().get(i));

        // Send heartbeat every 10 items
        if (i % 10 == 0) {
            Activity.getExecutionContext().heartbeat(i);
        }
    }
}
```

#### 2. Idempotency

```java
@Override
public UUID createTransaction(CreateTransactionData data) {
    // Check if already processed
    Optional<Transaction> existing = transactionRepository
        .findByIdempotencyKey(data.getIdempotencyKey());

    if (existing.isPresent()) {
        log.info("Transaction already exists with idempotency key: {}",
            data.getIdempotencyKey());
        return existing.get().getId().value();
    }

    // Create new transaction
    Transaction transaction = // ... create transaction
    Transaction saved = transactionRepository.save(transaction);

    return saved.getId().value();
}
```

#### 3. Error Handling

```java
@Override
public void publishEvent(EventData data) {
    try {
        eventPublisher.publish(data);
    } catch (MessageNotConfirmedException e) {
        // Retryable error
        log.warn("Event not confirmed, will retry", e);
        throw e; // Let Temporal retry
    } catch (ValidationException e) {
        // Non-retryable error
        log.error("Event validation failed", e);
        throw Activity.wrap(e); // Fail immediately, don't retry
    }
}
```

---

## Starting a Workflow

### From REST Controller

```java
@RestController
@RequestMapping("/api/v1/purchases")
public class PurchaseController {

    private final WorkflowClient temporalClient;

    @PostMapping
    public ResponseEntity<PurchaseResponse> registerPurchase(
            @Valid @RequestBody RegisterPurchaseRequest request
    ) {
        // Build command
        PurchaseRegistrationCommand command = buildCommand(request);

        // Create workflow stub
        String workflowId = "purchase-" + command.getIdempotencyKey();

        PurchaseRegistrationWorkflow workflow = temporalClient.newWorkflowStub(
            PurchaseRegistrationWorkflow.class,
            WorkflowOptions.newBuilder()
                .setWorkflowId(workflowId)
                .setTaskQueue("homewarehouse-tasks")
                .setWorkflowExecutionTimeout(Duration.ofMinutes(5))
                .setWorkflowRunTimeout(Duration.ofMinutes(5))
                .setWorkflowIdReusePolicy(
                    WorkflowIdReusePolicy.WORKFLOW_ID_REUSE_POLICY_REJECT_DUPLICATE
                )
                .build()
        );

        // Start workflow asynchronously
        WorkflowClient.start(workflow::execute, command);

        // Or start and wait for result (synchronous)
        // PurchaseRegistrationResult result = workflow.execute(command);

        return ResponseEntity.accepted()
            .body(new PurchaseResponse(command.getCorrelationId().value()));
    }
}
```

### Querying Workflow Status

```java
// Query while workflow is running
PurchaseRegistrationWorkflow workflow = temporalClient.newWorkflowStub(
    PurchaseRegistrationWorkflow.class,
    workflowId
);

PurchaseRegistrationStatus status = workflow.getStatus();
log.info("Workflow status: {}", status);
```

---

## Testing Workflows

### Unit Test with TestWorkflowEnvironment

```java
@ExtendWith(MockitoExtension.class)
class PurchaseRegistrationWorkflowTest {

    private TestWorkflowEnvironment testEnv;
    private Worker worker;

    @Mock
    private ValidationActivities validationActivities;

    @Mock
    private LedgerActivities ledgerActivities;

    @Mock
    private InventoryActivities inventoryActivities;

    @BeforeEach
    void setUp() {
        testEnv = TestWorkflowEnvironment.newInstance();
        worker = testEnv.newWorker("homewarehouse-tasks");

        // Register workflow
        worker.registerWorkflowImplementationTypes(
            PurchaseRegistrationWorkflowImpl.class
        );

        // Register activities
        worker.registerActivitiesImplementations(
            validationActivities,
            ledgerActivities,
            inventoryActivities
        );

        testEnv.start();
    }

    @AfterEach
    void tearDown() {
        testEnv.close();
    }

    @Test
    void shouldCompletePurchaseSuccessfully() {
        // Arrange
        PurchaseRegistrationCommand command = buildTestCommand();
        UUID transactionId = UUID.randomUUID();
        List<UUID> unitIds = List.of(UUID.randomUUID(), UUID.randomUUID());

        when(validationActivities.validatePurchaseRequest(any()))
            .thenReturn(ValidationResult.valid());

        when(ledgerActivities.createTransaction(any()))
            .thenReturn(transactionId);

        when(inventoryActivities.createUnits(any()))
            .thenReturn(unitIds);

        // Act
        PurchaseRegistrationWorkflow workflow = testEnv.newWorkflowStub(
            PurchaseRegistrationWorkflow.class
        );

        PurchaseRegistrationResult result = workflow.execute(command);

        // Assert
        assertThat(result.getStatus()).isEqualTo(PurchaseRegistrationStatus.COMPLETED);
        assertThat(result.getTransactionId()).isEqualTo(transactionId);
        assertThat(result.getUnitIds()).containsExactlyElementsOf(unitIds);

        verify(validationActivities).validatePurchaseRequest(command);
        verify(ledgerActivities).createTransaction(any());
        verify(inventoryActivities).createUnits(any());
    }

    @Test
    void shouldRollbackOnInventoryFailure() {
        // Arrange
        UUID transactionId = UUID.randomUUID();

        when(validationActivities.validatePurchaseRequest(any()))
            .thenReturn(ValidationResult.valid());

        when(ledgerActivities.createTransaction(any()))
            .thenReturn(transactionId);

        when(inventoryActivities.createUnits(any()))
            .thenThrow(new RuntimeException("DB Error"));

        // Act
        PurchaseRegistrationWorkflow workflow = testEnv.newWorkflowStub(
            PurchaseRegistrationWorkflow.class
        );

        PurchaseRegistrationResult result = workflow.execute(buildTestCommand());

        // Assert
        assertThat(result.getStatus()).isEqualTo(PurchaseRegistrationStatus.FAILED);

        verify(ledgerActivities).deleteTransaction(transactionId);
    }
}
```

### Integration Test with Testcontainers

```java
@SpringBootTest
@Testcontainers
class PurchaseWorkflowIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Container
    static GenericContainer<?> temporal = new GenericContainer<>("temporalio/auto-setup:1.22")
        .withExposedPorts(7233);

    @Autowired
    private WorkflowClient temporalClient;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private InventoryUnitRepository unitRepository;

    @Test
    void shouldCreateTransactionAndUnits() {
        // Arrange
        PurchaseRegistrationCommand command = buildTestCommand();

        // Act
        PurchaseRegistrationWorkflow workflow = temporalClient.newWorkflowStub(
            PurchaseRegistrationWorkflow.class,
            WorkflowOptions.newBuilder()
                .setWorkflowId("test-" + UUID.randomUUID())
                .setTaskQueue("homewarehouse-tasks")
                .build()
        );

        PurchaseRegistrationResult result = workflow.execute(command);

        // Assert
        assertThat(result.getStatus()).isEqualTo(PurchaseRegistrationStatus.COMPLETED);

        // Verify transaction in database
        Transaction transaction = transactionRepository
            .findById(new TransactionId(result.getTransactionId()))
            .orElseThrow();

        assertThat(transaction.getAmount()).isEqualByComparingTo(command.getTotalAmount());

        // Verify units in database
        List<InventoryUnit> units = unitRepository
            .findAllById(result.getUnitIds().stream()
                .map(InventoryUnitId::new)
                .collect(Collectors.toList()));

        assertThat(units).hasSize(command.getItems().size());
    }
}
```

---

## Monitoring and Debugging

### Temporal Web UI

Access at `http://localhost:8088` (when using docker-compose)

**Key Features:**
- View all workflows
- See workflow execution history
- Query workflow state
- View activity executions
- Debug failed workflows

### Logging

```java
// In workflow
Workflow.getLogger(this.getClass()).info("Creating transaction");

// In activity
log.info("Executing validation activity");
```

### Metrics

```java
// Example: Custom metrics in activity
@Override
public void createTransaction(CreateTransactionData data) {
    long startTime = System.currentTimeMillis();

    try {
        // ... implementation

        long duration = System.currentTimeMillis() - startTime;
        metricsService.recordActivityDuration("create_transaction", duration);

    } catch (Exception e) {
        metricsService.incrementActivityFailure("create_transaction");
        throw e;
    }
}
```

---

## Best Practices

### Workflow Design

- ✅ Keep workflows deterministic
- ✅ Use activities for all external operations
- ✅ Use signals for external events
- ✅ Use queries for status checks
- ✅ Implement proper compensation (Saga pattern)
- ❌ Don't use random numbers or current time directly
- ❌ Don't call external services from workflow
- ❌ Don't use mutable global state

### Activity Design

- ✅ Make activities idempotent
- ✅ Use heartbeats for long-running activities
- ✅ Handle retriable vs non-retriable errors
- ✅ Use proper timeout configurations
- ❌ Don't access workflow state from activities
- ❌ Don't assume activities run on same machine

### Error Handling

- ✅ Use typed exceptions for business errors
- ✅ Configure retry policies appropriately
- ✅ Implement compensation for failures
- ✅ Log errors with correlation IDs
- ❌ Don't swallow exceptions silently
- ❌ Don't retry non-retryable errors

---

## Common Patterns

### Pattern: Saga (Compensation)

Already shown in main workflow example.

### Pattern: Child Workflow

```java
// Parent workflow
@Override
public void parentWorkflow() {
    ChildWorkflow child = Workflow.newChildWorkflowStub(ChildWorkflow.class);
    child.execute();
}
```

### Pattern: Continue-As-New (for long-running workflows)

```java
@Override
public void processLargeDataset(List<Item> items, int processedCount) {
    if (items.isEmpty()) {
        return; // Done
    }

    // Process batch
    List<Item> batch = items.subList(0, Math.min(100, items.size()));
    processBatch(batch);

    // Continue with remaining items
    List<Item> remaining = items.subList(batch.size(), items.size());
    Workflow.continueAsNew(remaining, processedCount + batch.size());
}
```

### Pattern: Async Activity Completion

For activities that need to complete externally:

```java
// Activity that returns immediately but completes later
@Override
public void submitForApproval(ApprovalRequest request) {
    String taskToken = Activity.getExecutionContext().getTaskToken();

    // Store token for later completion
    approvalService.submitWithToken(request, taskToken);

    // Activity blocks here until completed externally
    Activity.getExecutionContext().doNotCompleteOnReturn();
}

// Elsewhere, when approval is done:
temporalClient.completeActivityByTaskToken(
    taskToken,
    result,
    null // no exception
);
```

---

## References

- [Temporal Java SDK Documentation](https://docs.temporal.io/dev-guide/java)
- [Temporal Best Practices](https://docs.temporal.io/dev-guide/java/best-practices)
- [Workflow Design Patterns](https://docs.temporal.io/dev-guide/java/patterns)
