# Process: Purchase Registration

## Process Overview

| Attribute | Value |
|-----------|-------|
| Process ID | PROC-PURCH-001 |
| Process Name | Purchase Registration (Dual Write) |
| Domain | Cross-Domain (Ledger + Inventory) |
| Trigger | User registers a purchase via API or UI |
| Frequency | On-demand |
| Participants | User, Frontend, Backend API, Temporal, PostgreSQL, Redis, RabbitMQ |
| Workflow | PurchaseRegistrationWorkflow |

## Process Goal

Atomically record a purchase in both the financial ledger (as an expense transaction) and inventory system (as new units), ensuring consistency through Temporal workflow orchestration.

## Business Rules

| Rule | Description |
|------|-------------|
| BR-001 | A purchase MUST create both a ledger transaction AND inventory units |
| BR-002 | If either creation fails, the entire purchase MUST be rolled back |
| BR-003 | Total transaction amount MUST equal sum of (unit price × quantity) for all items |
| BR-004 | All inventory items MUST exist before purchase can be registered |
| BR-005 | User MUST have permission `ledger:transactions:write` AND `inventory:units:write` |
| BR-006 | Each purchase MUST be idempotent (duplicate requests with same key are ignored) |
| BR-007 | Each inventory unit MUST have a location assigned |
| BR-008 | Perishable items MUST have expiry dates |

## Detailed Process Flow

```mermaid
stateDiagram-v2
    [*] --> UserInitiatesPurchase
    UserInitiatesPurchase --> FrontendValidation

    FrontendValidation --> APIRequest: Valid
    FrontendValidation --> ShowValidationErrors: Invalid
    ShowValidationErrors --> UserInitiatesPurchase

    APIRequest --> StartWorkflow
    StartWorkflow --> WorkflowValidation

    WorkflowValidation --> CreateTransaction: All checks pass
    WorkflowValidation --> WorkflowFailed: Validation failed

    CreateTransaction --> CheckTransactionCreated
    CheckTransactionCreated --> CreateUnits: Success
    CheckTransactionCreated --> Compensate: Failed

    CreateUnits --> CheckUnitsCreated
    CheckUnitsCreated --> PublishEvent: Success
    CheckUnitsCreated --> Compensate: Failed

    PublishEvent --> WriteAudit
    WriteAudit --> WorkflowCompleted

    Compensate --> DeleteUnits: If units created
    DeleteUnits --> DeleteTransaction: If transaction created
    DeleteTransaction --> AuditRollback
    AuditRollback --> WorkflowFailed

    WorkflowCompleted --> [*]
    WorkflowFailed --> [*]
```

## Step-by-Step Process

### Step 1: User Initiates Purchase

**Actor:** User
**Interface:** Purchase Registration Form

**Form Fields:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| accountId | UUID | Yes | Financial account to charge |
| transactionDate | Date | Yes | Date of purchase |
| counterparty | String | No | Where purchase was made (e.g., "Supermarket") |
| description | String | No | Overall purchase description |
| categoryId | UUID | No | Expense category |
| items[] | Array | Yes | List of purchased items (min 1) |
| items[].itemId | UUID | Yes | Reference to inventory item |
| items[].locationId | UUID | Yes | Where to store the units |
| items[].quantity | Integer | Yes | Number of units (min 1) |
| items[].unitPrice | Decimal | Yes | Price per unit |
| items[].bestBefore | Date | Conditional | Required if item is perishable |
| items[].expiresAt | Date | Conditional | Required if item is perishable |

**Example Form Data:**
```json
{
  "accountId": "acc-uuid",
  "transactionDate": "2024-01-15",
  "counterparty": "Whole Foods",
  "description": "Weekly groceries",
  "categoryId": "cat-groceries-uuid",
  "items": [
    {
      "itemId": "item-milk-uuid",
      "locationId": "loc-fridge-uuid",
      "quantity": 2,
      "unitPrice": "3.50",
      "bestBefore": "2024-01-22",
      "expiresAt": "2024-01-25"
    },
    {
      "itemId": "item-bread-uuid",
      "locationId": "loc-pantry-uuid",
      "quantity": 1,
      "unitPrice": "2.99",
      "bestBefore": "2024-01-18",
      "expiresAt": "2024-01-20"
    }
  ]
}
```

---

### Step 2: Frontend Validation

**Actor:** Frontend
**Action:** Validate form data before API call

**Validation Schema:**
```typescript
const purchaseItemSchema = z.object({
  itemId: z.string().uuid('Invalid item ID'),
  locationId: z.string().uuid('Invalid location ID'),
  quantity: z.number()
    .int('Quantity must be a whole number')
    .min(1, 'Quantity must be at least 1')
    .max(1000, 'Quantity too large'),
  unitPrice: z.string()
    .regex(/^\d+(\.\d{1,2})?$/, 'Invalid price format'),
  bestBefore: z.string()
    .regex(/^\d{4}-\d{2}-\d{2}$/)
    .optional(),
  expiresAt: z.string()
    .regex(/^\d{4}-\d{2}-\d{2}$/)
    .optional()
}).refine(
  (data) => {
    // If expiresAt is provided, bestBefore must also be provided
    if (data.expiresAt && !data.bestBefore) {
      return false;
    }
    return true;
  },
  { message: 'Best-before date required when expiration is set' }
).refine(
  (data) => {
    // Expiration must be >= bestBefore
    if (data.expiresAt && data.bestBefore) {
      return new Date(data.expiresAt) >= new Date(data.bestBefore);
    }
    return true;
  },
  { message: 'Expiration date must be on or after best-before date' }
);

const purchaseSchema = z.object({
  accountId: z.string().uuid(),
  transactionDate: z.string().regex(/^\d{4}-\d{2}-\d{2}$/),
  counterparty: z.string().max(200).optional(),
  description: z.string().max(500).optional(),
  categoryId: z.string().uuid().optional(),
  items: z.array(purchaseItemSchema)
    .min(1, 'At least one item required')
    .max(100, 'Too many items in single purchase')
});
```

**Total Calculation:**
```typescript
function calculateTotal(items: PurchaseItem[]): string {
  const total = items.reduce((sum, item) => {
    const itemTotal = new Decimal(item.unitPrice).times(item.quantity);
    return sum.plus(itemTotal);
  }, new Decimal(0));

  return total.toFixed(2);
}
```

---

### Step 3: API Request

**Actor:** Frontend
**Action:** POST request to `/api/v1/purchases`

**HTTP Request:**
```http
POST /api/v1/purchases HTTP/1.1
Host: homewarehouse.local
Authorization: Bearer eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...
Content-Type: application/json
X-Idempotency-Key: purchase-20240115-uuid

{
  "accountId": "acc-uuid",
  "transactionDate": "2024-01-15",
  "totalAmount": "9.99",
  "currency": "USD",
  "counterparty": "Whole Foods",
  "description": "Weekly groceries",
  "categoryId": "cat-groceries-uuid",
  "items": [
    {
      "itemId": "item-milk-uuid",
      "locationId": "loc-fridge-uuid",
      "quantity": 2,
      "unitPrice": "3.50",
      "bestBefore": "2024-01-22",
      "expiresAt": "2024-01-25"
    },
    {
      "itemId": "item-bread-uuid",
      "locationId": "loc-pantry-uuid",
      "quantity": 1,
      "unitPrice": "2.99",
      "bestBefore": "2024-01-18",
      "expiresAt": "2024-01-20"
    }
  ],
  "idempotencyKey": "purchase-20240115-uuid"
}
```

**Idempotency Key Generation:**
```typescript
// Option 1: Client-generated UUID
const idempotencyKey = uuidv4();

// Option 2: Hash of purchase details
const idempotencyKey = await hashPurchaseData({
  accountId,
  transactionDate,
  items: items.map(i => ({
    itemId: i.itemId,
    quantity: i.quantity,
    unitPrice: i.unitPrice
  }))
});
```

---

### Step 4: Controller Request Handling

**Actor:** Backend (PurchaseController)
**Action:** Receive and validate request

**Controller Code:**
```java
@RestController
@RequestMapping("/api/v1/purchases")
@PreAuthorize("hasAuthority('ledger:transactions:write') and hasAuthority('inventory:units:write')")
public class PurchaseController {

    private final TemporalClient temporalClient;
    private final IdempotencyService idempotencyService;

    @PostMapping
    public ResponseEntity<PurchaseRegistrationResponse> registerPurchase(
            @Valid @RequestBody RegisterPurchaseRequest request,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        // Generate idempotency key if not provided
        UUID finalIdempotencyKey = idempotencyKey != null
            ? UUID.fromString(idempotencyKey)
            : UUID.randomUUID();

        // Check idempotency
        Optional<PurchaseRegistrationResponse> cachedResponse =
            idempotencyService.getCachedResponse(finalIdempotencyKey);

        if (cachedResponse.isPresent()) {
            return ResponseEntity.ok(cachedResponse.get());
        }

        // Generate correlation ID
        CorrelationId correlationId = new CorrelationId(UUID.randomUUID());

        // Build workflow command
        PurchaseRegistrationCommand command = PurchaseRegistrationCommand.builder()
            .userId(UUID.fromString(userDetails.getUsername()))
            .accountId(request.getAccountId())
            .transactionDate(request.getTransactionDate())
            .totalAmount(new Money(
                new BigDecimal(request.getTotalAmount()),
                Currency.getInstance(request.getCurrency())
            ))
            .counterparty(request.getCounterparty())
            .description(request.getDescription())
            .categoryId(request.getCategoryId())
            .items(request.getItems().stream()
                .map(this::mapToPurchaseItem)
                .collect(Collectors.toList()))
            .idempotencyKey(finalIdempotencyKey)
            .correlationId(correlationId)
            .build();

        // Start workflow
        String workflowId = "purchase-" + finalIdempotencyKey;

        PurchaseRegistrationWorkflow workflow = temporalClient.newWorkflowStub(
            PurchaseRegistrationWorkflow.class,
            WorkflowOptions.newBuilder()
                .setWorkflowId(workflowId)
                .setTaskQueue("homewarehouse-tasks")
                .setWorkflowExecutionTimeout(Duration.ofMinutes(5))
                .build()
        );

        // Execute workflow asynchronously
        WorkflowClient.start(workflow::execute, command);

        // Return immediate response
        PurchaseRegistrationResponse response = PurchaseRegistrationResponse.builder()
            .correlationId(correlationId.value())
            .status("PROCESSING")
            .message("Purchase registration initiated")
            .build();

        // Cache response for idempotency
        idempotencyService.cacheResponse(finalIdempotencyKey, response, Duration.ofHours(24));

        return ResponseEntity.accepted()
            .header("X-Correlation-Id", correlationId.value())
            .body(response);
    }
}
```

---

### Step 5: Workflow Initialization

**Actor:** Temporal Server
**Action:** Start PurchaseRegistrationWorkflow

**Workflow Interface:**
```java
@WorkflowInterface
public interface PurchaseRegistrationWorkflow {

    @WorkflowMethod
    PurchaseRegistrationResult execute(PurchaseRegistrationCommand command);

    @QueryMethod
    PurchaseRegistrationStatus getStatus();
}
```

**Workflow Implementation Skeleton:**
```java
public class PurchaseRegistrationWorkflowImpl implements PurchaseRegistrationWorkflow {

    private PurchaseRegistrationStatus status = PurchaseRegistrationStatus.PROCESSING;
    private String errorMessage;

    private final ValidationActivities validationActivities = Workflow.newActivityStub(
        ValidationActivities.class,
        ActivityOptions.newBuilder()
            .setStartToCloseTimeout(Duration.ofSeconds(30))
            .setRetryOptions(RetryOptions.newBuilder()
                .setMaximumAttempts(3)
                .build())
            .build()
    );

    private final LedgerActivities ledgerActivities = Workflow.newActivityStub(
        LedgerActivities.class,
        ActivityOptions.newBuilder()
            .setStartToCloseTimeout(Duration.ofMinutes(2))
            .setRetryOptions(RetryOptions.newBuilder()
                .setMaximumAttempts(5)
                .setDoNotRetry(BusinessRuleViolationException.class)
                .build())
            .build()
    );

    private final InventoryActivities inventoryActivities = Workflow.newActivityStub(
        InventoryActivities.class,
        // ... similar configuration
    );

    private final EventActivities eventActivities = Workflow.newActivityStub(
        EventActivities.class,
        // ... similar configuration
    );

    private final AuditActivities auditActivities = Workflow.newActivityStub(
        AuditActivities.class,
        // ... similar configuration
    );

    @Override
    public PurchaseRegistrationResult execute(PurchaseRegistrationCommand command) {
        // Implementation in next steps
    }

    @Override
    public PurchaseRegistrationStatus getStatus() {
        return status;
    }
}
```

---

### Step 6: Workflow Validation Activity

**Actor:** ValidationActivities (Temporal Activity)
**Action:** Validate purchase request

**Activity Implementation:**
```java
@Component
public class ValidationActivitiesImpl implements ValidationActivities {

    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final InventoryItemRepository itemRepository;
    private final LocationRepository locationRepository;
    private final IdempotencyService idempotencyService;

    @Override
    @Activity
    public ValidationResult validatePurchaseRequest(PurchaseRegistrationCommand command) {
        List<ValidationError> errors = new ArrayList<>();

        // 1. Validate user exists and has permissions
        User user = userRepository.findById(command.getUserId())
            .orElseThrow(() -> new UserNotFoundException(command.getUserId()));

        if (!user.hasPermission("ledger:transactions:write")) {
            errors.add(new ValidationError("permissions", "Missing ledger write permission"));
        }

        if (!user.hasPermission("inventory:units:write")) {
            errors.add(new ValidationError("permissions", "Missing inventory write permission"));
        }

        // 2. Validate account exists and belongs to user
        Account account = accountRepository.findById(command.getAccountId())
            .orElseThrow(() -> new AccountNotFoundException(command.getAccountId()));

        if (!account.getUserId().equals(command.getUserId())) {
            errors.add(new ValidationError("accountId", "Account does not belong to user"));
        }

        if (!account.isActive()) {
            errors.add(new ValidationError("accountId", "Account is not active"));
        }

        // 3. Validate all items exist
        Set<UUID> itemIds = command.getItems().stream()
            .map(PurchaseItem::getItemId)
            .collect(Collectors.toSet());

        Map<UUID, InventoryItem> itemsById = itemRepository.findAllById(itemIds).stream()
            .collect(Collectors.toMap(InventoryItem::getId, Function.identity()));

        for (PurchaseItem purchaseItem : command.getItems()) {
            InventoryItem item = itemsById.get(purchaseItem.getItemId());

            if (item == null) {
                errors.add(new ValidationError(
                    "items",
                    "Item not found: " + purchaseItem.getItemId()
                ));
                continue;
            }

            // Validate perishable items have expiry dates
            if (item.isPerishable()) {
                if (purchaseItem.getBestBefore() == null) {
                    errors.add(new ValidationError(
                        "items",
                        "Perishable item requires best-before date: " + item.getName()
                    ));
                }

                if (purchaseItem.getExpiresAt() == null) {
                    errors.add(new ValidationError(
                        "items",
                        "Perishable item requires expiration date: " + item.getName()
                    ));
                }

                // Validate expiry dates logic
                if (purchaseItem.getExpiresAt() != null &&
                    purchaseItem.getBestBefore() != null &&
                    purchaseItem.getExpiresAt().isBefore(purchaseItem.getBestBefore())) {

                    errors.add(new ValidationError(
                        "items",
                        "Expiration must be on or after best-before: " + item.getName()
                    ));
                }
            }

            // Validate item belongs to user
            if (!item.getUserId().equals(command.getUserId())) {
                errors.add(new ValidationError(
                    "items",
                    "Item does not belong to user: " + item.getName()
                ));
            }
        }

        // 4. Validate all locations exist and belong to user
        Set<UUID> locationIds = command.getItems().stream()
            .map(PurchaseItem::getLocationId)
            .collect(Collectors.toSet());

        Map<UUID, Location> locationsById = locationRepository.findAllById(locationIds).stream()
            .collect(Collectors.toMap(Location::getId, Function.identity()));

        for (PurchaseItem purchaseItem : command.getItems()) {
            Location location = locationsById.get(purchaseItem.getLocationId());

            if (location == null) {
                errors.add(new ValidationError(
                    "items",
                    "Location not found: " + purchaseItem.getLocationId()
                ));
                continue;
            }

            if (!location.getUserId().equals(command.getUserId())) {
                errors.add(new ValidationError(
                    "items",
                    "Location does not belong to user: " + location.getName()
                ));
            }
        }

        // 5. Validate amounts
        BigDecimal calculatedTotal = command.getItems().stream()
            .map(item -> item.getUnitPrice().getAmount().multiply(
                BigDecimal.valueOf(item.getQuantity())
            ))
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (calculatedTotal.compareTo(command.getTotalAmount().getAmount()) != 0) {
            errors.add(new ValidationError(
                "totalAmount",
                String.format("Total amount mismatch: expected %s, got %s",
                    calculatedTotal, command.getTotalAmount().getAmount())
            ));
        }

        // 6. Check idempotency
        if (idempotencyService.isProcessed(command.getIdempotencyKey())) {
            errors.add(new ValidationError(
                "idempotencyKey",
                "Purchase already processed with this idempotency key"
            ));
        }

        return new ValidationResult(errors.isEmpty(), errors);
    }
}
```

**Validation Result:**
```java
public record ValidationResult(
    boolean isValid,
    List<ValidationError> errors
) {}

public record ValidationError(
    String field,
    String message
) {}
```

---

### Step 7: Create Ledger Transaction Activity

**Actor:** LedgerActivities (Temporal Activity)
**Action:** Create expense transaction in ledger

**Activity Implementation:**
```java
@Component
public class LedgerActivitiesImpl implements LedgerActivities {

    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;

    @Override
    @Activity
    public UUID createTransaction(CreateTransactionData data) {
        // Check idempotency first
        Optional<Transaction> existing = transactionRepository
            .findByIdempotencyKey(data.getIdempotencyKey());

        if (existing.isPresent()) {
            return existing.get().getId().value();
        }

        // Load account
        Account account = accountRepository.findById(data.getAccountId())
            .orElseThrow(() -> new AccountNotFoundException(data.getAccountId()));

        // Create transaction using domain model
        Transaction transaction = Transaction.createExpense(
            TransactionId.generate(),
            account.getId(),
            data.getCategoryId(),
            data.getAmount(),
            data.getDescription(),
            data.getTransactionDate(),
            data.getCounterparty(),
            data.getReference(),
            data.getIdempotencyKey(),
            data.getCorrelationId()
        );

        // Persist
        Transaction saved = transactionRepository.save(transaction);

        return saved.getId().value();
    }

    @Override
    @Activity
    public void deleteTransaction(UUID transactionId) {
        transactionRepository.deleteById(new TransactionId(transactionId));
    }
}
```

**Transaction Creation Data:**
```java
public record CreateTransactionData(
    UUID accountId,
    UUID categoryId,
    Money amount,
    String description,
    LocalDate transactionDate,
    String counterparty,
    String reference,
    UUID idempotencyKey,
    CorrelationId correlationId
) {}
```

---

### Step 8: Create Inventory Units Activity

**Actor:** InventoryActivities (Temporal Activity)
**Action:** Create inventory units for each purchased item

**Activity Implementation:**
```java
@Component
public class InventoryActivitiesImpl implements InventoryActivities {

    private final InventoryUnitRepository unitRepository;
    private final InventoryItemRepository itemRepository;

    @Override
    @Activity
    public List<UUID> createUnits(CreateUnitsData data) {
        List<UUID> createdUnitIds = new ArrayList<>();

        for (int itemIndex = 0; itemIndex < data.getItems().size(); itemIndex++) {
            CreateUnitItem item = data.getItems().get(itemIndex);

            // Generate per-unit idempotency key
            UUID unitIdempotencyKey = generateUnitIdempotencyKey(
                data.getBaseIdempotencyKey(),
                itemIndex,
                0 // will increment for each unit
            );

            // Load item for validation
            InventoryItem inventoryItem = itemRepository.findById(item.getItemId())
                .orElseThrow(() -> new InventoryItemNotFoundException(item.getItemId()));

            // Create units (one at a time for individual tracking)
            for (int unitIndex = 0; unitIndex < item.getQuantity(); unitIndex++) {
                UUID currentUnitIdempotencyKey = generateUnitIdempotencyKey(
                    data.getBaseIdempotencyKey(),
                    itemIndex,
                    unitIndex
                );

                // Check idempotency
                Optional<InventoryUnit> existing = unitRepository
                    .findByIdempotencyKey(currentUnitIdempotencyKey);

                if (existing.isPresent()) {
                    createdUnitIds.add(existing.get().getId().value());
                    continue;
                }

                // Create unit
                InventoryUnit unit = InventoryUnit.create(
                    InventoryUnitId.generate(),
                    item.getItemId(),
                    item.getLocationId(),
                    item.getPurchaseDate(),
                    item.getBestBefore(),
                    item.getExpiresAt(),
                    item.getUnitPrice(),
                    currentUnitIdempotencyKey,
                    data.getCorrelationId()
                );

                InventoryUnit saved = unitRepository.save(unit);
                createdUnitIds.add(saved.getId().value());
            }
        }

        return createdUnitIds;
    }

    private UUID generateUnitIdempotencyKey(UUID baseKey, int itemIndex, int unitIndex) {
        String combined = String.format("%s-%d-%d", baseKey, itemIndex, unitIndex);
        return UUID.nameUUIDFromBytes(combined.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    @Activity
    public void deleteUnits(List<UUID> unitIds) {
        List<InventoryUnitId> ids = unitIds.stream()
            .map(InventoryUnitId::new)
            .collect(Collectors.toList());

        unitRepository.deleteAllById(ids);
    }
}
```

---

### Step 9: Publish Domain Event

**Actor:** EventActivities (Temporal Activity)
**Action:** Publish PurchaseRegistered event to RabbitMQ

**Event Definition:**
```java
public record PurchaseRegisteredEvent(
    UUID correlationId,
    UUID transactionId,
    List<UUID> unitIds,
    UUID accountId,
    BigDecimal totalAmount,
    String currency,
    LocalDate purchaseDate,
    String counterparty,
    int itemCount
) {}
```

**Activity Implementation:**
```java
@Component
public class EventActivitiesImpl implements EventActivities {

    private final DomainEventPublisher eventPublisher;

    @Override
    @Activity
    public void publishPurchaseRegistered(PublishPurchaseEventData data) {
        PurchaseRegisteredEvent event = new PurchaseRegisteredEvent(
            data.getCorrelationId(),
            data.getTransactionId(),
            data.getUnitIds(),
            data.getAccountId(),
            data.getTotalAmount(),
            data.getCurrency(),
            data.getPurchaseDate(),
            data.getCounterparty(),
            data.getItemCount()
        );

        eventPublisher.publishWithConfirm(
            event,
            "purchase.registered",
            new CorrelationId(data.getCorrelationId())
        );
    }
}
```

**RabbitMQ Message:**
```json
{
  "envelope": {
    "eventId": "evt-uuid",
    "eventType": "purchase.registered",
    "version": 1,
    "occurredAt": "2024-01-15T10:30:00Z",
    "correlationId": "corr-uuid",
    "idempotencyKey": "idmp-uuid",
    "source": "homewarehouse-backend",
    "userId": "user-uuid"
  },
  "payload": {
    "correlationId": "corr-uuid",
    "transactionId": "tx-uuid",
    "unitIds": ["unit-uuid-1", "unit-uuid-2"],
    "accountId": "acc-uuid",
    "totalAmount": "9.99",
    "currency": "USD",
    "purchaseDate": "2024-01-15",
    "counterparty": "Whole Foods",
    "itemCount": 2
  }
}
```

---

### Step 10: Write Audit Record

**Actor:** AuditActivities (Temporal Activity)
**Action:** Record purchase registration in audit log

**Activity Implementation:**
```java
@Component
public class AuditActivitiesImpl implements AuditActivities {

    private final AuditLogRepository auditRepository;

    @Override
    @Activity
    public void auditPurchaseRegistration(AuditPurchaseData data) {
        AuditEntry entry = AuditEntry.builder()
            .id(UUID.randomUUID())
            .userId(data.getUserId())
            .action("PURCHASE_REGISTERED")
            .entityType("purchase")
            .entityId(data.getTransactionId())
            .beforeState(null) // New purchase
            .afterState(buildAfterState(data))
            .ipAddress(null) // From workflow, no IP available
            .userAgent(null)
            .correlationId(data.getCorrelationId())
            .occurredAt(Instant.now())
            .build();

        auditRepository.save(entry);
    }

    private Map<String, Object> buildAfterState(AuditPurchaseData data) {
        return Map.of(
            "transactionId", data.getTransactionId(),
            "unitIds", data.getUnitIds(),
            "accountId", data.getAccountId(),
            "totalAmount", data.getTotalAmount(),
            "currency", data.getCurrency(),
            "itemCount", data.getUnitIds().size()
        );
    }
}
```

---

### Step 11: Workflow Completion

**Actor:** PurchaseRegistrationWorkflow
**Action:** Return result to caller

**Complete Workflow Implementation:**
```java
@Override
public PurchaseRegistrationResult execute(PurchaseRegistrationCommand command) {
    UUID transactionId = null;
    List<UUID> unitIds = new ArrayList<>();

    try {
        // Step 1: Validate
        status = PurchaseRegistrationStatus.VALIDATING;
        ValidationResult validation = validationActivities.validatePurchaseRequest(command);

        if (!validation.isValid()) {
            status = PurchaseRegistrationStatus.FAILED;
            errorMessage = formatValidationErrors(validation.errors());

            auditActivities.auditPurchaseFailure(new AuditPurchaseFailureData(
                command.getUserId(),
                command.getCorrelationId(),
                "VALIDATION_FAILED",
                errorMessage
            ));

            return PurchaseRegistrationResult.failed(
                command.getCorrelationId().value(),
                errorMessage
            );
        }

        // Step 2: Create Transaction
        status = PurchaseRegistrationStatus.CREATING_TRANSACTION;
        CreateTransactionData transactionData = buildTransactionData(command);
        transactionId = ledgerActivities.createTransaction(transactionData);

        // Step 3: Create Units
        status = PurchaseRegistrationStatus.CREATING_UNITS;
        CreateUnitsData unitsData = buildUnitsData(command, transactionId);
        unitIds = inventoryActivities.createUnits(unitsData);

        // Step 4: Publish Event
        status = PurchaseRegistrationStatus.PUBLISHING_EVENT;
        PublishPurchaseEventData eventData = buildEventData(
            command,
            transactionId,
            unitIds
        );
        eventActivities.publishPurchaseRegistered(eventData);

        // Step 5: Audit
        status = PurchaseRegistrationStatus.AUDITING;
        AuditPurchaseData auditData = buildAuditData(command, transactionId, unitIds);
        auditActivities.auditPurchaseRegistration(auditData);

        // Complete
        status = PurchaseRegistrationStatus.COMPLETED;

        return PurchaseRegistrationResult.success(
            command.getCorrelationId().value(),
            transactionId,
            unitIds
        );

    } catch (Exception e) {
        status = PurchaseRegistrationStatus.ROLLING_BACK;

        // Compensating transactions (Saga pattern)
        try {
            // Rollback units if created
            if (!unitIds.isEmpty()) {
                inventoryActivities.deleteUnits(unitIds);
            }

            // Rollback transaction if created
            if (transactionId != null) {
                ledgerActivities.deleteTransaction(transactionId);
            }

            // Audit rollback
            auditActivities.auditPurchaseRollback(new AuditPurchaseRollbackData(
                command.getUserId(),
                command.getCorrelationId(),
                transactionId,
                unitIds,
                e.getMessage()
            ));

        } catch (Exception rollbackException) {
            // Log but don't fail - manual intervention may be needed
            Workflow.getLogger(PurchaseRegistrationWorkflowImpl.class)
                .error("Rollback failed", rollbackException);
        }

        status = PurchaseRegistrationStatus.FAILED;
        errorMessage = "Purchase registration failed: " + e.getMessage();

        return PurchaseRegistrationResult.failed(
            command.getCorrelationId().value(),
            errorMessage
        );
    }
}
```

---

## Saga Compensation (Rollback)

### Compensation Flow

```mermaid
graph TD
    Start[Exception Thrown] --> CheckUnits{Units Created?}

    CheckUnits -->|Yes| DeleteUnits[Delete All Units]
    CheckUnits -->|No| CheckTx

    DeleteUnits --> CheckTx{Transaction Created?}

    CheckTx -->|Yes| DeleteTx[Delete Transaction]
    CheckTx -->|No| AuditRollback

    DeleteTx --> AuditRollback[Audit Rollback]
    AuditRollback --> End[Return Failed Result]
```

### Compensation Guarantees

| Scenario | Compensation Action | Result |
|----------|-------------------|--------|
| Validation fails | None needed | Clean failure, no data created |
| Transaction creation fails | None needed | No inventory units created yet |
| Units creation fails | Delete transaction | Ledger rolled back |
| Event publishing fails | Delete units, delete transaction | Full rollback |
| Audit fails | Continue (log warning) | Don't fail for audit issues |

---

## Testing Scenarios

### Happy Path
- [x] Valid purchase with single item
- [x] Valid purchase with multiple items
- [x] Perishable items with expiry dates
- [x] Non-perishable items without expiry dates

### Validation Failures
- [x] Missing required fields
- [x] Invalid account (doesn't exist)
- [x] Invalid account (doesn't belong to user)
- [x] Invalid item (doesn't exist)
- [x] Invalid location (doesn't exist)
- [x] Total amount mismatch
- [x] Perishable without expiry dates
- [x] Expiry before best-before
- [x] Duplicate idempotency key

### Compensation Scenarios
- [x] Transaction creation succeeds, units creation fails
- [x] Both succeed, event publishing fails
- [x] Rollback succeeds
- [x] Rollback partially fails (manual intervention needed)

---

## Performance Metrics

### Target Latencies

| Operation | Target | Actual |
|-----------|--------|--------|
| Validation | < 100ms | - |
| Transaction creation | < 50ms | - |
| Units creation (per unit) | < 20ms | - |
| Event publishing | < 30ms | - |
| Audit logging | < 20ms | - |
| **Total (5 items)** | **< 500ms** | - |

### Throughput

- **Expected:** 10 purchases/minute
- **Peak:** 50 purchases/minute
- **Workflow timeout:** 5 minutes

---

## Monitoring & Alerts

### Metrics

```java
// Workflow metrics
counter("workflow.purchase_registration.started")
counter("workflow.purchase_registration.completed")
counter("workflow.purchase_registration.failed")
counter("workflow.purchase_registration.rolled_back")
timer("workflow.purchase_registration.duration")

// Activity metrics
timer("activity.create_transaction.duration")
timer("activity.create_units.duration")
counter("activity.create_units.count", tags("quantity", quantity))
```

### Alerts

| Alert | Condition | Action |
|-------|-----------|--------|
| High Failure Rate | > 10% failures in 5 min | Investigate logs |
| Slow Workflow | p95 > 2 seconds | Check activity performance |
| Rollback Spike | > 5 rollbacks in 5 min | Check for systematic issues |
| Rollback Failure | Any rollback failure | Manual investigation required |

---

## Related Documentation

- [Temporal Workflows Guide](../guides/temporal/purchase-registration-workflow.md)
- [Ledger Module Guide](../guides/backend/ledger-module.md)
- [Inventory Module Guide](../guides/backend/inventory-module.md)
- [RabbitMQ Events](../events/06-events-rabbitmq.md)
