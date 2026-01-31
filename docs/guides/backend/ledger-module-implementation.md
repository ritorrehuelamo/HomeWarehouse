# Ledger Module Implementation Guide

## Purpose

This guide provides step-by-step instructions for implementing the Ledger module following Hexagonal Architecture, Domain-Driven Design, and Vertical Slicing patterns.

## Module Overview

| Attribute | Value |
|-----------|-------|
| Module Name | ledger |
| Bounded Context | Financial Ledger |
| Responsibility | Accounts, transactions, categories, CSV import |
| Dependencies | shared-kernel only |
| Databases | PostgreSQL |
| External Services | None (publishes events via RabbitMQ) |

## Module Structure

```
backend/ledger/
├── build.gradle.kts
└── src/
    ├── main/
    │   ├── java/
    │   │   └── com/homewarehouse/ledger/
    │   │       ├── domain/
    │   │       │   ├── model/
    │   │       │   │   ├── Account.java
    │   │       │   │   ├── AccountId.java
    │   │       │   │   ├── Transaction.java
    │   │       │   │   ├── TransactionId.java
    │   │       │   │   ├── Category.java
    │   │       │   │   └── CategoryId.java
    │   │       │   ├── service/
    │   │       │   │   └── TransactionValidationService.java
    │   │       │   ├── event/
    │   │       │   │   ├── TransactionCreated.java
    │   │       │   │   ├── TransactionUpdated.java
    │   │       │   │   └── TransactionDeleted.java
    │   │       │   └── repository/
    │   │       │       ├── AccountRepository.java
    │   │       │       ├── TransactionRepository.java
    │   │       │       └── CategoryRepository.java
    │   │       ├── application/
    │   │       │   ├── command/
    │   │       │   │   ├── createaccount/
    │   │       │   │   │   ├── CreateAccountCommand.java
    │   │       │   │   │   ├── CreateAccountHandler.java
    │   │       │   │   │   └── CreateAccountResult.java
    │   │       │   │   ├── createtransaction/
    │   │       │   │   │   ├── CreateTransactionCommand.java
    │   │       │   │   │   ├── CreateTransactionHandler.java
    │   │       │   │   │   └── CreateTransactionResult.java
    │   │       │   │   └── updatetransaction/
    │   │       │   ├── query/
    │   │       │   │   ├── getaccount/
    │   │       │   │   ├── gettransactions/
    │   │       │   │   └── getaccountbalance/
    │   │       │   └── port/
    │   │       │       ├── in/
    │   │       │       │   ├── CreateAccountUseCase.java
    │   │       │       │   └── CreateTransactionUseCase.java
    │   │       │       └── out/
    │   │       │           └── PublishEventPort.java
    │   │       └── infrastructure/
    │   │           ├── persistence/
    │   │           │   ├── JpaAccountRepository.java
    │   │           │   ├── AccountJpaEntity.java
    │   │           │   ├── JpaTransactionRepository.java
    │   │           │   └── TransactionJpaEntity.java
    │   │           ├── messaging/
    │   │           │   └── LedgerEventPublisher.java
    │   │           ├── temporal/
    │   │           │   └── LedgerActivitiesImpl.java
    │   │           └── web/
    │   │               ├── AccountController.java
    │   │               ├── TransactionController.java
    │   │               └── dto/
    │   │                   ├── CreateAccountRequest.java
    │   │                   ├── AccountResponse.java
    │   │                   ├── CreateTransactionRequest.java
    │   │                   └── TransactionResponse.java
    │   └── resources/
    │       ├── db/migration/
    │       │   └── V3__create_ledger_tables.sql
    │       └── application-ledger.yml
    └── test/
        ├── java/
        │   └── com/homewarehouse/ledger/
        │       ├── domain/
        │       │   └── model/
        │       │       └── TransactionTest.java
        │       ├── application/
        │       │   └── command/
        │       │       └── createtransaction/
        │       │           └── CreateTransactionHandlerTest.java
        │       └── infrastructure/
        │           ├── persistence/
        │           │   └── JpaTransactionRepositoryTest.java
        │           └── web/
        │               └── TransactionControllerTest.java
        └── resources/
            └── application-test.yml
```

---

## Step 1: Define Domain Model

### Value Objects

Start with value objects (immutable, no identity):

```java
// TransactionId.java
package com.homewarehouse.ledger.domain.model;

import java.util.UUID;

public record TransactionId(UUID value) {

    public static TransactionId generate() {
        return new TransactionId(UUID.randomUUID());
    }

    public static TransactionId from(String value) {
        return new TransactionId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
```

```java
// AccountId.java - Similar structure
package com.homewarehouse.ledger.domain.model;

import java.util.UUID;

public record AccountId(UUID value) {
    public static AccountId generate() {
        return new AccountId(UUID.randomUUID());
    }

    public static AccountId from(String value) {
        return new AccountId(UUID.fromString(value));
    }
}
```

```java
// Money.java (in shared-kernel)
package com.homewarehouse.shared.domain;

import java.math.BigDecimal;
import java.util.Currency;

public record Money(BigDecimal amount, Currency currency) {

    public Money {
        if (amount == null) {
            throw new IllegalArgumentException("Amount cannot be null");
        }
        if (currency == null) {
            throw new IllegalArgumentException("Currency cannot be null");
        }
    }

    public static Money of(String amount, String currencyCode) {
        return new Money(
            new BigDecimal(amount),
            Currency.getInstance(currencyCode)
        );
    }

    public Money add(Money other) {
        if (!this.currency.equals(other.currency)) {
            throw new IllegalArgumentException("Cannot add different currencies");
        }
        return new Money(this.amount.add(other.amount), this.currency);
    }

    public Money subtract(Money other) {
        if (!this.currency.equals(other.currency)) {
            throw new IllegalArgumentException("Cannot subtract different currencies");
        }
        return new Money(this.amount.subtract(other.amount), this.currency);
    }

    public boolean isNegative() {
        return amount.compareTo(BigDecimal.ZERO) < 0;
    }

    public boolean isPositive() {
        return amount.compareTo(BigDecimal.ZERO) > 0;
    }

    public boolean isZero() {
        return amount.compareTo(BigDecimal.ZERO) == 0;
    }
}
```

### Enums

```java
// TransactionType.java
package com.homewarehouse.ledger.domain.model;

public enum TransactionType {
    INCOME,
    EXPENSE,
    TRANSFER
}

// AccountType.java
public enum AccountType {
    CHECKING,
    SAVINGS,
    CREDIT_CARD,
    CASH,
    LOAN,
    MORTGAGE,
    INVESTMENT,
    OTHER
}
```

### Aggregates

```java
// Account.java
package com.homewarehouse.ledger.domain.model;

import com.homewarehouse.shared.domain.AggregateRoot;
import com.homewarehouse.shared.domain.UserId;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;
import java.util.Currency;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Account implements AggregateRoot {

    private final AccountId id;
    private final UserId userId;
    private String name;
    private final AccountType type;
    private final Currency currency;
    private boolean active;
    private final Instant createdAt;
    private Instant updatedAt;
    private int version;

    /**
     * Factory method to create a new account
     */
    public static Account create(
            AccountId id,
            UserId userId,
            String name,
            AccountType type,
            Currency currency
    ) {
        validateName(name);

        Instant now = Instant.now();

        return new Account(
            id,
            userId,
            name,
            type,
            currency,
            true, // active by default
            now,
            now,
            1
        );
    }

    /**
     * Business logic: rename account
     */
    public void rename(String newName) {
        validateName(newName);
        this.name = newName;
        this.updatedAt = Instant.now();
    }

    /**
     * Business logic: deactivate account
     */
    public void deactivate() {
        if (!active) {
            throw new AccountAlreadyInactiveException(id);
        }
        this.active = false;
        this.updatedAt = Instant.now();
    }

    /**
     * Business logic: reactivate account
     */
    public void reactivate() {
        if (active) {
            throw new AccountAlreadyActiveException(id);
        }
        this.active = true;
        this.updatedAt = Instant.now();
    }

    private static void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Account name cannot be empty");
        }
        if (name.length() > 200) {
            throw new IllegalArgumentException("Account name too long (max 200 characters)");
        }
    }

    @Override
    public Object getId() {
        return id;
    }
}
```

```java
// Transaction.java
package com.homewarehouse.ledger.domain.model;

import com.homewarehouse.shared.domain.AggregateRoot;
import com.homewarehouse.shared.domain.CorrelationId;
import com.homewarehouse.shared.domain.Money;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Transaction implements AggregateRoot {

    private final TransactionId id;
    private final AccountId accountId;
    private CategoryId categoryId;
    private final TransactionType type;
    private Money amount;
    private String description;
    private final LocalDate transactionDate;
    private String counterparty;
    private String reference;
    private final UUID idempotencyKey;
    private final CorrelationId correlationId;
    private final Instant createdAt;
    private Instant updatedAt;
    private int version;

    /**
     * Factory method: create expense transaction
     */
    public static Transaction createExpense(
            TransactionId id,
            AccountId accountId,
            CategoryId categoryId,
            Money amount,
            String description,
            LocalDate transactionDate,
            String counterparty,
            String reference,
            UUID idempotencyKey,
            CorrelationId correlationId
    ) {
        validateAmount(amount, TransactionType.EXPENSE);
        validateTransactionDate(transactionDate);

        Instant now = Instant.now();

        return new Transaction(
            id,
            accountId,
            categoryId,
            TransactionType.EXPENSE,
            amount,
            description,
            transactionDate,
            counterparty,
            reference,
            idempotencyKey,
            correlationId,
            now,
            now,
            1
        );
    }

    /**
     * Factory method: create income transaction
     */
    public static Transaction createIncome(
            TransactionId id,
            AccountId accountId,
            CategoryId categoryId,
            Money amount,
            String description,
            LocalDate transactionDate,
            String counterparty,
            String reference,
            UUID idempotencyKey,
            CorrelationId correlationId
    ) {
        validateAmount(amount, TransactionType.INCOME);
        validateTransactionDate(transactionDate);

        Instant now = Instant.now();

        return new Transaction(
            id,
            accountId,
            categoryId,
            TransactionType.INCOME,
            amount,
            description,
            transactionDate,
            counterparty,
            reference,
            idempotencyKey,
            correlationId,
            now,
            now,
            1
        );
    }

    /**
     * Business logic: update transaction amount
     */
    public void updateAmount(Money newAmount) {
        validateAmount(newAmount, this.type);
        this.amount = newAmount;
        this.updatedAt = Instant.now();
    }

    /**
     * Business logic: update category
     */
    public void updateCategory(CategoryId newCategoryId) {
        this.categoryId = newCategoryId;
        this.updatedAt = Instant.now();
    }

    /**
     * Business logic: update description
     */
    public void updateDescription(String newDescription) {
        if (newDescription != null && newDescription.length() > 500) {
            throw new IllegalArgumentException("Description too long (max 500 characters)");
        }
        this.description = newDescription;
        this.updatedAt = Instant.now();
    }

    /**
     * Domain invariant: Amount must be positive
     */
    private static void validateAmount(Money amount, TransactionType type) {
        if (amount.isNegative() || amount.isZero()) {
            throw new InvalidTransactionException(
                String.format("%s amount must be positive", type)
            );
        }
    }

    /**
     * Domain invariant: Transaction date cannot be in the future
     */
    private static void validateTransactionDate(LocalDate date) {
        if (date.isAfter(LocalDate.now())) {
            throw new InvalidTransactionException("Transaction date cannot be in the future");
        }
    }

    @Override
    public Object getId() {
        return id;
    }
}
```

### Domain Events

```java
// TransactionCreatedEvent.java
package com.homewarehouse.ledger.domain.event;

import com.homewarehouse.shared.events.DomainEvent;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Value
@Builder
public class TransactionCreatedEvent implements DomainEvent {
    UUID transactionId;
    UUID accountId;
    String transactionType;
    BigDecimal amount;
    String currency;
    LocalDate transactionDate;
    String description;
    UUID categoryId;

    @Override
    public String getEventType() {
        return "ledger.transaction.created";
    }
}
```

### Repository Interfaces (Ports)

```java
// AccountRepository.java
package com.homewarehouse.ledger.domain.repository;

import com.homewarehouse.ledger.domain.model.Account;
import com.homewarehouse.ledger.domain.model.AccountId;
import com.homewarehouse.shared.domain.UserId;

import java.util.List;
import java.util.Optional;

public interface AccountRepository {

    Account save(Account account);

    Optional<Account> findById(AccountId id);

    List<Account> findByUserId(UserId userId);

    List<Account> findActiveByUserId(UserId userId);

    void deleteById(AccountId id);

    boolean existsByUserIdAndName(UserId userId, String name);
}
```

```java
// TransactionRepository.java
package com.homewarehouse.ledger.domain.repository;

import com.homewarehouse.ledger.domain.model.Transaction;
import com.homewarehouse.ledger.domain.model.TransactionId;
import com.homewarehouse.ledger.domain.model.AccountId;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TransactionRepository {

    Transaction save(Transaction transaction);

    Optional<Transaction> findById(TransactionId id);

    List<Transaction> findByAccountId(AccountId accountId);

    List<Transaction> findByAccountIdAndDateRange(
        AccountId accountId,
        LocalDate startDate,
        LocalDate endDate
    );

    Optional<Transaction> findByIdempotencyKey(UUID idempotencyKey);

    void deleteById(TransactionId id);
}
```

---

## Step 2: Implement Application Layer (Use Cases)

### Command (Write Operation)

```java
// CreateTransactionCommand.java
package com.homewarehouse.ledger.application.command.createtransaction;

import com.homewarehouse.shared.domain.CorrelationId;
import com.homewarehouse.shared.domain.Money;
import com.homewarehouse.shared.domain.UserId;
import lombok.Builder;
import lombok.Value;

import java.time.LocalDate;
import java.util.UUID;

@Value
@Builder
public class CreateTransactionCommand {
    UserId userId;
    UUID accountId;
    UUID categoryId;
    String transactionType; // INCOME, EXPENSE, TRANSFER
    Money amount;
    String description;
    LocalDate transactionDate;
    String counterparty;
    String reference;
    UUID idempotencyKey;
    CorrelationId correlationId;
}
```

```java
// CreateTransactionResult.java
package com.homewarehouse.ledger.application.command.createtransaction;

import lombok.Builder;
import lombok.Value;

import java.util.UUID;

@Value
@Builder
public class CreateTransactionResult {
    UUID transactionId;
    UUID accountId;
    String status; // CREATED
}
```

```java
// CreateTransactionHandler.java
package com.homewarehouse.ledger.application.command.createtransaction;

import com.homewarehouse.ledger.domain.event.TransactionCreatedEvent;
import com.homewarehouse.ledger.domain.model.*;
import com.homewarehouse.ledger.domain.repository.AccountRepository;
import com.homewarehouse.ledger.domain.repository.TransactionRepository;
import com.homewarehouse.shared.events.DomainEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class CreateTransactionHandler {

    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final DomainEventPublisher eventPublisher;

    @Transactional
    public CreateTransactionResult handle(CreateTransactionCommand command) {
        log.info("Creating transaction for account: {}", command.getAccountId());

        // 1. Validate account exists and belongs to user
        Account account = accountRepository
            .findById(new AccountId(command.getAccountId()))
            .orElseThrow(() -> new AccountNotFoundException(command.getAccountId()));

        if (!account.getUserId().equals(command.getUserId())) {
            throw new AccountAccessDeniedException(command.getAccountId());
        }

        if (!account.isActive()) {
            throw new AccountNotActiveException(command.getAccountId());
        }

        // 2. Check idempotency
        if (command.getIdempotencyKey() != null) {
            transactionRepository.findByIdempotencyKey(command.getIdempotencyKey())
                .ifPresent(existing -> {
                    log.info("Transaction already exists with idempotency key");
                    throw new DuplicateTransactionException(command.getIdempotencyKey());
                });
        }

        // 3. Create transaction using domain factory
        TransactionType type = TransactionType.valueOf(command.getTransactionType());
        TransactionId transactionId = TransactionId.generate();

        Transaction transaction;
        if (type == TransactionType.EXPENSE) {
            transaction = Transaction.createExpense(
                transactionId,
                account.getId(),
                command.getCategoryId() != null ? new CategoryId(command.getCategoryId()) : null,
                command.getAmount(),
                command.getDescription(),
                command.getTransactionDate(),
                command.getCounterparty(),
                command.getReference(),
                command.getIdempotencyKey(),
                command.getCorrelationId()
            );
        } else if (type == TransactionType.INCOME) {
            transaction = Transaction.createIncome(
                transactionId,
                account.getId(),
                command.getCategoryId() != null ? new CategoryId(command.getCategoryId()) : null,
                command.getAmount(),
                command.getDescription(),
                command.getTransactionDate(),
                command.getCounterparty(),
                command.getReference(),
                command.getIdempotencyKey(),
                command.getCorrelationId()
            );
        } else {
            throw new UnsupportedOperationException("TRANSFER not yet implemented");
        }

        // 4. Save transaction
        Transaction saved = transactionRepository.save(transaction);

        log.info("Transaction created: {}", saved.getId());

        // 5. Publish domain event
        TransactionCreatedEvent event = TransactionCreatedEvent.builder()
            .transactionId(saved.getId().value())
            .accountId(saved.getAccountId().value())
            .transactionType(saved.getType().name())
            .amount(saved.getAmount().amount())
            .currency(saved.getAmount().currency().getCurrencyCode())
            .transactionDate(saved.getTransactionDate())
            .description(saved.getDescription())
            .categoryId(saved.getCategoryId() != null ? saved.getCategoryId().value() : null)
            .build();

        eventPublisher.publish(
            event,
            saved.getCorrelationId().value(),
            command.getUserId().value()
        );

        // 6. Return result
        return CreateTransactionResult.builder()
            .transactionId(saved.getId().value())
            .accountId(saved.getAccountId().value())
            .status("CREATED")
            .build();
    }
}
```

### Query (Read Operation)

```java
// GetTransactionsQuery.java
package com.homewarehouse.ledger.application.query.gettransactions;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDate;
import java.util.UUID;

@Value
@Builder
public class GetTransactionsQuery {
    UUID userId;
    UUID accountId;
    UUID categoryId;
    String transactionType;
    LocalDate dateFrom;
    LocalDate dateTo;
    String search;
    int page;
    int size;
    String sort;
}
```

```java
// GetTransactionsHandler.java
package com.homewarehouse.ledger.application.query.gettransactions;

import com.homewarehouse.ledger.domain.model.AccountId;
import com.homewarehouse.ledger.domain.model.Transaction;
import com.homewarehouse.ledger.domain.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GetTransactionsHandler {

    private final TransactionRepository transactionRepository;

    @Transactional(readOnly = true)
    public Page<TransactionDto> handle(GetTransactionsQuery query) {
        // Implementation using JPA Criteria or QueryDSL
        // This would typically use a read model or projection

        PageRequest pageRequest = PageRequest.of(
            query.getPage(),
            query.getSize(),
            Sort.by(Sort.Direction.DESC, "transactionDate")
        );

        // Simplified - actual implementation would use specifications
        Page<Transaction> transactions = transactionRepository
            .findByAccountIdAndDateRange(
                new AccountId(query.getAccountId()),
                query.getDateFrom(),
                query.getDateTo(),
                pageRequest
            );

        return transactions.map(this::toDto);
    }

    private TransactionDto toDto(Transaction transaction) {
        return TransactionDto.builder()
            .id(transaction.getId().value())
            .accountId(transaction.getAccountId().value())
            .categoryId(transaction.getCategoryId() != null ?
                transaction.getCategoryId().value() : null)
            .transactionType(transaction.getType().name())
            .amount(transaction.getAmount().amount().toString())
            .currency(transaction.getAmount().currency().getCurrencyCode())
            .description(transaction.getDescription())
            .transactionDate(transaction.getTransactionDate())
            .counterparty(transaction.getCounterparty())
            .createdAt(transaction.getCreatedAt())
            .build();
    }
}
```

---

## Step 3: Implement Infrastructure Layer

### JPA Entities

```java
// TransactionJpaEntity.java
package com.homewarehouse.ledger.infrastructure.persistence;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "ledger_transactions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionJpaEntity {

    @Id
    private UUID id;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(name = "category_id")
    private UUID categoryId;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 20)
    private String transactionType;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(length = 500)
    private String description;

    @Column(name = "transaction_date", nullable = false)
    private LocalDate transactionDate;

    @Column(length = 200)
    private String counterparty;

    @Column(length = 200)
    private String reference;

    @Column(name = "idempotency_key", unique = true)
    private UUID idempotencyKey;

    @Column(name = "correlation_id")
    private UUID correlationId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Integer version;
}
```

### Repository Implementation

```java
// JpaTransactionRepository.java
package com.homewarehouse.ledger.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface JpaTransactionRepository
        extends JpaRepository<TransactionJpaEntity, UUID>,
                JpaSpecificationExecutor<TransactionJpaEntity> {

    Optional<TransactionJpaEntity> findByIdempotencyKey(UUID idempotencyKey);
}
```

```java
// TransactionRepositoryAdapter.java
package com.homewarehouse.ledger.infrastructure.persistence;

import com.homewarehouse.ledger.domain.model.*;
import com.homewarehouse.ledger.domain.repository.TransactionRepository;
import com.homewarehouse.shared.domain.CorrelationId;
import com.homewarehouse.shared.domain.Money;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Currency;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class TransactionRepositoryAdapter implements TransactionRepository {

    private final JpaTransactionRepository jpaRepository;

    @Override
    public Transaction save(Transaction transaction) {
        TransactionJpaEntity entity = toEntity(transaction);
        TransactionJpaEntity saved = jpaRepository.save(entity);
        return toDomain(saved);
    }

    @Override
    public Optional<Transaction> findById(TransactionId id) {
        return jpaRepository.findById(id.value())
            .map(this::toDomain);
    }

    @Override
    public List<Transaction> findByAccountId(AccountId accountId) {
        // Implementation using JPA query methods
        return jpaRepository.findByAccountId(accountId.value()).stream()
            .map(this::toDomain)
            .collect(Collectors.toList());
    }

    @Override
    public Optional<Transaction> findByIdempotencyKey(UUID idempotencyKey) {
        return jpaRepository.findByIdempotencyKey(idempotencyKey)
            .map(this::toDomain);
    }

    @Override
    public void deleteById(TransactionId id) {
        jpaRepository.deleteById(id.value());
    }

    // Mappers

    private TransactionJpaEntity toEntity(Transaction transaction) {
        return TransactionJpaEntity.builder()
            .id(transaction.getId().value())
            .accountId(transaction.getAccountId().value())
            .categoryId(transaction.getCategoryId() != null ?
                transaction.getCategoryId().value() : null)
            .transactionType(transaction.getType().name())
            .amount(transaction.getAmount().amount())
            .currency(transaction.getAmount().currency().getCurrencyCode())
            .description(transaction.getDescription())
            .transactionDate(transaction.getTransactionDate())
            .counterparty(transaction.getCounterparty())
            .reference(transaction.getReference())
            .idempotencyKey(transaction.getIdempotencyKey())
            .correlationId(transaction.getCorrelationId().value())
            .createdAt(transaction.getCreatedAt())
            .updatedAt(transaction.getUpdatedAt())
            .version(transaction.getVersion())
            .build();
    }

    private Transaction toDomain(TransactionJpaEntity entity) {
        return Transaction.reconstitute(
            new TransactionId(entity.getId()),
            new AccountId(entity.getAccountId()),
            entity.getCategoryId() != null ? new CategoryId(entity.getCategoryId()) : null,
            TransactionType.valueOf(entity.getTransactionType()),
            Money.of(
                entity.getAmount().toString(),
                entity.getCurrency()
            ),
            entity.getDescription(),
            entity.getTransactionDate(),
            entity.getCounterparty(),
            entity.getReference(),
            entity.getIdempotencyKey(),
            new CorrelationId(entity.getCorrelationId()),
            entity.getCreatedAt(),
            entity.getUpdatedAt(),
            entity.getVersion()
        );
    }
}
```

### REST Controller

```java
// TransactionController.java
package com.homewarehouse.ledger.infrastructure.web;

import com.homewarehouse.ledger.application.command.createtransaction.*;
import com.homewarehouse.ledger.infrastructure.web.dto.*;
import com.homewarehouse.shared.domain.UserId;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ledger/transactions")
@RequiredArgsConstructor
@Validated
public class TransactionController {

    private the CreateTransactionHandler createTransactionHandler;
    private final GetTransactionsHandler getTransactionsHandler;

    @PostMapping
    @PreAuthorize("hasAuthority('ledger:transactions:write')")
    public ResponseEntity<TransactionResponse> createTransaction(
            @Valid @RequestBody CreateTransactionRequest request,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        CreateTransactionCommand command = CreateTransactionCommand.builder()
            .userId(new UserId(UUID.fromString(userDetails.getUsername())))
            .accountId(request.getAccountId())
            .categoryId(request.getCategoryId())
            .transactionType(request.getTransactionType())
            .amount(Money.of(request.getAmount(), request.getCurrency()))
            .description(request.getDescription())
            .transactionDate(request.getTransactionDate())
            .counterparty(request.getCounterparty())
            .reference(request.getReference())
            .idempotencyKey(request.getIdempotencyKey())
            .correlationId(new CorrelationId(UUID.randomUUID()))
            .build();

        CreateTransactionResult result = createTransactionHandler.handle(command);

        TransactionResponse response = TransactionResponse.builder()
            .id(result.getTransactionId())
            .accountId(result.getAccountId())
            .status(result.getStatus())
            .build();

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('ledger:transactions:read')")
    public ResponseEntity<Page<TransactionDto>> getTransactions(
            @RequestParam(required = false) UUID accountId,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "transactionDate,desc") String sort,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        GetTransactionsQuery query = GetTransactionsQuery.builder()
            .userId(UUID.fromString(userDetails.getUsername()))
            .accountId(accountId)
            .categoryId(categoryId)
            .transactionType(type)
            .dateFrom(dateFrom)
            .dateTo(dateTo)
            .search(search)
            .page(page)
            .size(size)
            .sort(sort)
            .build();

        Page<TransactionDto> result = getTransactionsHandler.handle(query);

        return ResponseEntity.ok(result);
    }

    // Additional endpoints...
}
```

---

## Step 4: Testing

### Domain Model Test

```java
// TransactionTest.java
package com.homewarehouse.ledger.domain.model;

import com.homewarehouse.shared.domain.CorrelationId;
import com.homewarehouse.shared.domain.Money;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Currency;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class TransactionTest {

    @Test
    void shouldCreateExpenseTransaction() {
        // Arrange
        Money amount = Money.of("100.00", "USD");

        // Act
        Transaction transaction = Transaction.createExpense(
            TransactionId.generate(),
            AccountId.generate(),
            null,
            amount,
            "Groceries",
            LocalDate.now(),
            "Supermarket",
            null,
            UUID.randomUUID(),
            new CorrelationId(UUID.randomUUID())
        );

        // Assert
        assertThat(transaction.getType()).isEqualTo(TransactionType.EXPENSE);
        assertThat(transaction.getAmount()).isEqualTo(amount);
        assertThat(transaction.getDescription()).isEqualTo("Groceries");
    }

    @Test
    void shouldRejectNegativeAmount() {
        // Arrange
        Money negativeAmount = Money.of("-100.00", "USD");

        // Act & Assert
        assertThatThrownBy(() ->
            Transaction.createExpense(
                TransactionId.generate(),
                AccountId.generate(),
                null,
                negativeAmount,
                "Test",
                LocalDate.now(),
                null,
                null,
                UUID.randomUUID(),
                new CorrelationId(UUID.randomUUID())
            )
        ).isInstanceOf(InvalidTransactionException.class)
         .hasMessageContaining("must be positive");
    }

    @Test
    void shouldRejectFutureDate() {
        // Arrange
        LocalDate futureDate = LocalDate.now().plusDays(1);

        // Act & Assert
        assertThatThrownBy(() ->
            Transaction.createExpense(
                TransactionId.generate(),
                AccountId.generate(),
                null,
                Money.of("100.00", "USD"),
                "Test",
                futureDate,
                null,
                null,
                UUID.randomUUID(),
                new CorrelationId(UUID.randomUUID())
            )
        ).isInstanceOf(InvalidTransactionException.class)
         .hasMessageContaining("cannot be in the future");
    }

    @Test
    void shouldUpdateAmount() {
        // Arrange
        Transaction transaction = createTestTransaction();
        Money newAmount = Money.of("200.00", "USD");

        // Act
        transaction.updateAmount(newAmount);

        // Assert
        assertThat(transaction.getAmount()).isEqualTo(newAmount);
    }

    private Transaction createTestTransaction() {
        return Transaction.createExpense(
            TransactionId.generate(),
            AccountId.generate(),
            null,
            Money.of("100.00", "USD"),
            "Test",
            LocalDate.now(),
            null,
            null,
            UUID.randomUUID(),
            new CorrelationId(UUID.randomUUID())
        );
    }
}
```

### Integration Test

```java
// CreateTransactionHandlerIntegrationTest.java
package com.homewarehouse.ledger.application.command.createtransaction;

import com.homewarehouse.ledger.domain.model.*;
import com.homewarehouse.ledger.domain.repository.AccountRepository;
import com.homewarehouse.ledger.domain.repository.TransactionRepository;
import com.homewarehouse.shared.domain.Money;
import com.homewarehouse.shared.domain.UserId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@Testcontainers
@Transactional
@TestPropertySource(properties = {
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
class CreateTransactionHandlerIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    private CreateTransactionHandler handler;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Test
    void shouldCreateTransactionSuccessfully() {
        // Arrange
        UserId userId = new UserId(UUID.randomUUID());
        Account account = createAndSaveTestAccount(userId);

        CreateTransactionCommand command = CreateTransactionCommand.builder()
            .userId(userId)
            .accountId(account.getId().value())
            .transactionType("EXPENSE")
            .amount(Money.of("100.00", "USD"))
            .description("Test transaction")
            .transactionDate(LocalDate.now())
            .idempotencyKey(UUID.randomUUID())
            .correlationId(new CorrelationId(UUID.randomUUID()))
            .build();

        // Act
        CreateTransactionResult result = handler.handle(command);

        // Assert
        assertThat(result.getTransactionId()).isNotNull();
        assertThat(result.getStatus()).isEqualTo("CREATED");

        // Verify in database
        Transaction saved = transactionRepository
            .findById(new TransactionId(result.getTransactionId()))
            .orElseThrow();

        assertThat(saved.getAmount().amount()).isEqualByComparingTo("100.00");
        assertThat(saved.getDescription()).isEqualTo("Test transaction");
    }

    @Test
    void shouldPreventDuplicateWithSameIdempotencyKey() {
        // Arrange
        UserId userId = new UserId(UUID.randomUUID());
        Account account = createAndSaveTestAccount(userId);
        UUID idempotencyKey = UUID.randomUUID();

        CreateTransactionCommand command = CreateTransactionCommand.builder()
            .userId(userId)
            .accountId(account.getId().value())
            .transactionType("EXPENSE")
            .amount(Money.of("100.00", "USD"))
            .description("Test transaction")
            .transactionDate(LocalDate.now())
            .idempotencyKey(idempotencyKey)
            .correlationId(new CorrelationId(UUID.randomUUID()))
            .build();

        // First call succeeds
        handler.handle(command);

        // Act & Assert - Second call should fail
        assertThatThrownBy(() -> handler.handle(command))
            .isInstanceOf(DuplicateTransactionException.class);
    }

    private Account createAndSaveTestAccount(UserId userId) {
        Account account = Account.create(
            AccountId.generate(),
            userId,
            "Test Account",
            AccountType.CHECKING,
            Currency.getInstance("USD")
        );
        return accountRepository.save(account);
    }
}
```

---

## Best Practices Summary

### Domain Layer

- ✅ Keep pure Java, no framework annotations
- ✅ Use factory methods for creation
- ✅ Validate invariants in constructors/factories
- ✅ Make aggregates transaction boundaries
- ✅ Use value objects for concepts without identity
- ❌ Don't leak infrastructure concerns
- ❌ Don't depend on frameworks

### Application Layer

- ✅ Organize by use case (vertical slices)
- ✅ One handler per command/query
- ✅ Use transactions at this layer
- ✅ Publish events after persistence
- ❌ Don't put business logic here
- ❌ Don't access repositories from multiple aggregates in complex ways

### Infrastructure Layer

- ✅ Implement repository interfaces from domain
- ✅ Map between domain and JPA entities
- ✅ Validate DTOs at controller level
- ✅ Use PreAuthorize for permissions
- ❌ Don't expose JPA entities via REST
- ❌ Don't put business logic in controllers

---

## Related Documentation

- [Architecture](../../architecture/01-architecture.md)
- [Temporal Workflows](../temporal/temporal-implementation-guide.md)
- [RabbitMQ Events](../rabbitmq/rabbitmq-implementation-guide.md)
