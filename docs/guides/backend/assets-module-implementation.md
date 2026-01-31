# Assets Module Implementation Guide

## Overview

The **Assets** module manages wealth tracking, asset valuations, and net worth calculations. It follows hexagonal architecture, domain-driven design, and vertical slicing principles.

## Module Structure

```
backend/assets/
└── src/main/java/com/homewarehouse/assets/
    ├── domain/
    │   ├── model/
    │   │   ├── Asset.java                  # Asset aggregate root
    │   │   ├── Valuation.java              # Valuation entity
    │   │   ├── AssetId.java                # Value object
    │   │   ├── ValuationId.java            # Value object
    │   │   ├── AssetType.java              # Enum
    │   │   └── ValuationSource.java        # Enum
    │   ├── service/
    │   │   └── NetWorthCalculator.java     # Domain service
    │   ├── event/
    │   │   ├── AssetCreatedEvent.java
    │   │   ├── ValuationRecordedEvent.java
    │   │   └── AssetDeletedEvent.java
    │   └── repository/
    │       ├── AssetRepository.java        # Port
    │       └── ValuationRepository.java    # Port
    ├── application/
    │   ├── command/
    │   │   ├── createasset/
    │   │   ├── recordvaluation/
    │   │   └── updateasset/
    │   └── query/
    │       ├── getnetworth/
    │       ├── getasset/
    │       └── listassets/
    └── infrastructure/
        ├── persistence/
        ├── messaging/
        └── web/
```

## Domain Layer

### Value Objects

#### AssetId

```java
package com.homewarehouse.assets.domain.model;

import java.util.UUID;

public record AssetId(UUID value) {

    public static AssetId generate() {
        return new AssetId(UUID.randomUUID());
    }

    public static AssetId of(UUID value) {
        if (value == null) {
            throw new IllegalArgumentException("AssetId cannot be null");
        }
        return new AssetId(value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
```

### Enums

#### AssetType

```java
package com.homewarehouse.assets.domain.model;

public enum AssetType {
    REAL_ESTATE("Real Estate", true),
    VEHICLE("Vehicle", true),
    BANK_ACCOUNT("Bank Account", false),
    INVESTMENT("Investment", false),
    CRYPTOCURRENCY("Cryptocurrency", false),
    PRECIOUS_METAL("Precious Metal", true),
    COLLECTIBLE("Collectible", true),
    OTHER("Other", true);

    private final String displayName;
    private final boolean requiresManualValuation;

    AssetType(String displayName, boolean requiresManualValuation) {
        this.displayName = displayName;
        this.requiresManualValuation = requiresManualValuation;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean requiresManualValuation() {
        return requiresManualValuation;
    }
}
```

#### ValuationSource

```java
package com.homewarehouse.assets.domain.model;

public enum ValuationSource {
    MANUAL("Manual Entry"),
    AUTOMATIC("Automatic from Account"),
    MARKET_DATA("Market Data API"),
    APPRAISAL("Professional Appraisal");

    private final String displayName;

    ValuationSource(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
```

### Aggregate: Asset

```java
package com.homewarehouse.assets.domain.model;

import com.homewarehouse.sharedkernel.Money;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class Asset {

    private final AssetId id;
    private String name;
    private String description;
    private final AssetType type;
    private final List<Valuation> valuations;
    private final Instant createdAt;
    private Instant updatedAt;

    private Asset(
        AssetId id,
        String name,
        String description,
        AssetType type,
        Instant createdAt
    ) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.type = type;
        this.valuations = new ArrayList<>();
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    public static Asset create(
        AssetId id,
        String name,
        String description,
        AssetType type
    ) {
        validateName(name);

        return new Asset(id, name, description, type, Instant.now());
    }

    public static Asset reconstruct(
        AssetId id,
        String name,
        String description,
        AssetType type,
        List<Valuation> valuations,
        Instant createdAt,
        Instant updatedAt
    ) {
        Asset asset = new Asset(id, name, description, type, createdAt);
        asset.valuations.addAll(valuations);
        asset.updatedAt = updatedAt;
        return asset;
    }

    public void updateDetails(String name, String description) {
        validateName(name);
        this.name = name;
        this.description = description;
        this.updatedAt = Instant.now();
    }

    public Valuation recordValuation(
        ValuationId valuationId,
        Money value,
        ValuationSource source,
        String notes
    ) {
        Valuation valuation = Valuation.create(
            valuationId,
            id,
            value,
            source,
            notes
        );

        valuations.add(valuation);
        this.updatedAt = Instant.now();

        return valuation;
    }

    public Optional<Valuation> getLatestValuation() {
        return valuations.stream()
            .max((v1, v2) -> v1.getValuationDate().compareTo(v2.getValuationDate()));
    }

    public Money getCurrentValue() {
        return getLatestValuation()
            .map(Valuation::getValue)
            .orElse(Money.zero("USD"));
    }

    public List<Valuation> getValuationHistory() {
        return valuations.stream()
            .sorted((v1, v2) -> v2.getValuationDate().compareTo(v1.getValuationDate()))
            .toList();
    }

    private static void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Asset name cannot be null or blank");
        }

        if (name.length() > 200) {
            throw new IllegalArgumentException("Asset name cannot exceed 200 characters");
        }
    }

    // Getters
    public AssetId getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public AssetType getType() {
        return type;
    }

    public List<Valuation> getValuations() {
        return Collections.unmodifiableList(valuations);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
```

### Entity: Valuation

```java
package com.homewarehouse.assets.domain.model;

import com.homewarehouse.sharedkernel.Money;
import java.time.Instant;

public class Valuation {

    private final ValuationId id;
    private final AssetId assetId;
    private final Money value;
    private final ValuationSource source;
    private final String notes;
    private final Instant valuationDate;

    private Valuation(
        ValuationId id,
        AssetId assetId,
        Money value,
        ValuationSource source,
        String notes,
        Instant valuationDate
    ) {
        this.id = id;
        this.assetId = assetId;
        this.value = value;
        this.source = source;
        this.notes = notes;
        this.valuationDate = valuationDate;
    }

    public static Valuation create(
        ValuationId id,
        AssetId assetId,
        Money value,
        ValuationSource source,
        String notes
    ) {
        if (value.isNegative()) {
            throw new IllegalArgumentException("Asset valuation cannot be negative");
        }

        return new Valuation(id, assetId, value, source, notes, Instant.now());
    }

    public static Valuation reconstruct(
        ValuationId id,
        AssetId assetId,
        Money value,
        ValuationSource source,
        String notes,
        Instant valuationDate
    ) {
        return new Valuation(id, assetId, value, source, notes, valuationDate);
    }

    // Getters
    public ValuationId getId() {
        return id;
    }

    public AssetId getAssetId() {
        return assetId;
    }

    public Money getValue() {
        return value;
    }

    public ValuationSource getSource() {
        return source;
    }

    public String getNotes() {
        return notes;
    }

    public Instant getValuationDate() {
        return valuationDate;
    }
}
```

### Domain Service: NetWorthCalculator

```java
package com.homewarehouse.assets.domain.service;

import com.homewarehouse.assets.domain.model.Asset;
import com.homewarehouse.sharedkernel.Money;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class NetWorthCalculator {

    public NetWorthSummary calculateNetWorth(
        List<Asset> assets,
        Money totalLiabilities
    ) {
        Money totalAssets = calculateTotalAssets(assets);
        Money netWorth = totalAssets.subtract(totalLiabilities);

        Map<String, Money> assetsByType = groupAssetsByType(assets);

        return new NetWorthSummary(
            totalAssets,
            totalLiabilities,
            netWorth,
            assetsByType
        );
    }

    private Money calculateTotalAssets(List<Asset> assets) {
        if (assets.isEmpty()) {
            return Money.zero("USD");
        }

        return assets.stream()
            .map(Asset::getCurrentValue)
            .reduce(Money.zero("USD"), Money::add);
    }

    private Map<String, Money> groupAssetsByType(List<Asset> assets) {
        return assets.stream()
            .collect(Collectors.groupingBy(
                asset -> asset.getType().getDisplayName(),
                Collectors.reducing(
                    Money.zero("USD"),
                    Asset::getCurrentValue,
                    Money::add
                )
            ));
    }

    public record NetWorthSummary(
        Money totalAssets,
        Money totalLiabilities,
        Money netWorth,
        Map<String, Money> assetsByType
    ) {
        public BigDecimal getNetWorthAmount() {
            return netWorth.amount();
        }

        public boolean isPositive() {
            return netWorth.amount().compareTo(BigDecimal.ZERO) > 0;
        }
    }
}
```

### Domain Events

```java
package com.homewarehouse.assets.domain.event;

import com.homewarehouse.assets.domain.model.AssetId;
import com.homewarehouse.assets.domain.model.AssetType;
import java.time.Instant;
import java.util.UUID;

public record AssetCreatedEvent(
    UUID eventId,
    AssetId assetId,
    String name,
    AssetType type,
    Instant occurredAt
) {
    public static AssetCreatedEvent of(
        AssetId assetId,
        String name,
        AssetType type
    ) {
        return new AssetCreatedEvent(
            UUID.randomUUID(),
            assetId,
            name,
            type,
            Instant.now()
        );
    }
}
```

```java
package com.homewarehouse.assets.domain.event;

import com.homewarehouse.assets.domain.model.AssetId;
import com.homewarehouse.assets.domain.model.ValuationId;
import com.homewarehouse.sharedkernel.Money;
import java.time.Instant;
import java.util.UUID;

public record ValuationRecordedEvent(
    UUID eventId,
    ValuationId valuationId,
    AssetId assetId,
    Money value,
    Instant occurredAt
) {
    public static ValuationRecordedEvent of(
        ValuationId valuationId,
        AssetId assetId,
        Money value
    ) {
        return new ValuationRecordedEvent(
            UUID.randomUUID(),
            valuationId,
            assetId,
            value,
            Instant.now()
        );
    }
}
```

## Application Layer

### Command: Create Asset

```java
package com.homewarehouse.assets.application.command.createasset;

import com.homewarehouse.assets.domain.model.AssetType;

public record CreateAssetCommand(
    String name,
    String description,
    AssetType type,
    Double initialValue,
    String currency
) {
    public CreateAssetCommand {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Name is required");
        }

        if (type == null) {
            throw new IllegalArgumentException("Asset type is required");
        }
    }
}
```

```java
package com.homewarehouse.assets.application.command.createasset;

import com.homewarehouse.assets.domain.model.AssetId;

public record CreateAssetResult(
    AssetId assetId,
    String name
) {}
```

```java
package com.homewarehouse.assets.application.command.createasset;

import com.homewarehouse.assets.domain.model.*;
import com.homewarehouse.assets.domain.repository.AssetRepository;
import com.homewarehouse.assets.domain.event.AssetCreatedEvent;
import com.homewarehouse.assets.application.port.out.AssetEventPublisher;
import com.homewarehouse.sharedkernel.Money;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CreateAssetHandler {

    private final AssetRepository assetRepository;
    private final AssetEventPublisher eventPublisher;

    public CreateAssetHandler(
        AssetRepository assetRepository,
        AssetEventPublisher eventPublisher
    ) {
        this.assetRepository = assetRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public CreateAssetResult handle(CreateAssetCommand command) {
        // Generate ID
        AssetId assetId = AssetId.generate();

        // Create asset
        Asset asset = Asset.create(
            assetId,
            command.name(),
            command.description(),
            command.type()
        );

        // Record initial valuation if provided
        if (command.initialValue() != null) {
            Money initialValue = Money.of(command.initialValue(), command.currency());

            asset.recordValuation(
                ValuationId.generate(),
                initialValue,
                ValuationSource.MANUAL,
                "Initial valuation"
            );
        }

        // Persist
        assetRepository.save(asset);

        // Publish event
        AssetCreatedEvent event = AssetCreatedEvent.of(
            asset.getId(),
            asset.getName(),
            asset.getType()
        );
        eventPublisher.publish(event);

        return new CreateAssetResult(asset.getId(), asset.getName());
    }
}
```

### Command: Record Valuation

```java
package com.homewarehouse.assets.application.command.recordvaluation;

import com.homewarehouse.assets.domain.model.ValuationSource;
import java.util.UUID;

public record RecordValuationCommand(
    UUID assetId,
    Double value,
    String currency,
    ValuationSource source,
    String notes
) {
    public RecordValuationCommand {
        if (assetId == null) {
            throw new IllegalArgumentException("Asset ID is required");
        }

        if (value == null || value <= 0) {
            throw new IllegalArgumentException("Value must be positive");
        }
    }
}
```

```java
package com.homewarehouse.assets.application.command.recordvaluation;

import com.homewarehouse.assets.domain.model.*;
import com.homewarehouse.assets.domain.repository.AssetRepository;
import com.homewarehouse.assets.domain.event.ValuationRecordedEvent;
import com.homewarehouse.assets.application.port.out.AssetEventPublisher;
import com.homewarehouse.sharedkernel.Money;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RecordValuationHandler {

    private final AssetRepository assetRepository;
    private final AssetEventPublisher eventPublisher;

    public RecordValuationHandler(
        AssetRepository assetRepository,
        AssetEventPublisher eventPublisher
    ) {
        this.assetRepository = assetRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public RecordValuationResult handle(RecordValuationCommand command) {
        // Fetch asset
        AssetId assetId = AssetId.of(command.assetId());
        Asset asset = assetRepository.findById(assetId)
            .orElseThrow(() -> new IllegalArgumentException("Asset not found: " + assetId));

        // Create valuation
        Money value = Money.of(command.value(), command.currency());
        ValuationId valuationId = ValuationId.generate();

        Valuation valuation = asset.recordValuation(
            valuationId,
            value,
            command.source(),
            command.notes()
        );

        // Persist
        assetRepository.save(asset);

        // Publish event
        ValuationRecordedEvent event = ValuationRecordedEvent.of(
            valuation.getId(),
            asset.getId(),
            value
        );
        eventPublisher.publish(event);

        return new RecordValuationResult(valuation.getId(), value);
    }
}
```

### Query: Get Net Worth

```java
package com.homewarehouse.assets.application.query.getnetworth;

import com.homewarehouse.assets.domain.model.Asset;
import com.homewarehouse.assets.domain.repository.AssetRepository;
import com.homewarehouse.assets.domain.service.NetWorthCalculator;
import com.homewarehouse.sharedkernel.Money;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
public class GetNetWorthHandler {

    private final AssetRepository assetRepository;
    private final NetWorthCalculator netWorthCalculator;

    public GetNetWorthHandler(
        AssetRepository assetRepository,
        NetWorthCalculator netWorthCalculator
    ) {
        this.assetRepository = assetRepository;
        this.netWorthCalculator = netWorthCalculator;
    }

    @Transactional(readOnly = true)
    public NetWorthCalculator.NetWorthSummary handle() {
        // Fetch all assets
        List<Asset> assets = assetRepository.findAll();

        // TODO: Fetch total liabilities from ledger module
        Money totalLiabilities = Money.zero("USD");

        // Calculate net worth
        return netWorthCalculator.calculateNetWorth(assets, totalLiabilities);
    }
}
```

## Infrastructure Layer

### REST Controller

```java
package com.homewarehouse.assets.infrastructure.web;

import com.homewarehouse.assets.application.command.createasset.*;
import com.homewarehouse.assets.application.command.recordvaluation.*;
import com.homewarehouse.assets.application.query.getnetworth.*;
import com.homewarehouse.assets.infrastructure.web.dto.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/assets")
public class AssetController {

    private final CreateAssetHandler createAssetHandler;
    private final RecordValuationHandler recordValuationHandler;
    private final GetNetWorthHandler getNetWorthHandler;
    private final AssetMapper mapper;

    public AssetController(
        CreateAssetHandler createAssetHandler,
        RecordValuationHandler recordValuationHandler,
        GetNetWorthHandler getNetWorthHandler,
        AssetMapper mapper
    ) {
        this.createAssetHandler = createAssetHandler;
        this.recordValuationHandler = recordValuationHandler;
        this.getNetWorthHandler = getNetWorthHandler;
        this.mapper = mapper;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('asset:create')")
    public ResponseEntity<AssetResponse> createAsset(
        @RequestBody CreateAssetRequest request
    ) {
        CreateAssetCommand command = mapper.toCommand(request);
        CreateAssetResult result = createAssetHandler.handle(command);

        AssetResponse response = mapper.toResponse(result);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{assetId}/valuations")
    @PreAuthorize("hasAuthority('asset:update')")
    public ResponseEntity<ValuationResponse> recordValuation(
        @PathVariable UUID assetId,
        @RequestBody RecordValuationRequest request
    ) {
        RecordValuationCommand command = mapper.toCommand(assetId, request);
        RecordValuationResult result = recordValuationHandler.handle(command);

        ValuationResponse response = mapper.toResponse(result);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/net-worth")
    @PreAuthorize("hasAuthority('asset:read')")
    public ResponseEntity<NetWorthResponse> getNetWorth() {
        NetWorthCalculator.NetWorthSummary summary = getNetWorthHandler.handle();

        NetWorthResponse response = mapper.toResponse(summary);
        return ResponseEntity.ok(response);
    }
}
```

## Testing

### Unit Test: Asset Aggregate

```java
package com.homewarehouse.assets.domain.model;

import com.homewarehouse.sharedkernel.Money;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class AssetTest {

    @Test
    void should_CreateAsset_When_ValidInput() {
        Asset asset = Asset.create(
            AssetId.generate(),
            "My House",
            "Primary residence",
            AssetType.REAL_ESTATE
        );

        assertThat(asset.getId()).isNotNull();
        assertThat(asset.getName()).isEqualTo("My House");
        assertThat(asset.getType()).isEqualTo(AssetType.REAL_ESTATE);
    }

    @Test
    void should_RecordValuation_When_ValidValue() {
        Asset asset = Asset.create(
            AssetId.generate(),
            "My House",
            "Primary residence",
            AssetType.REAL_ESTATE
        );

        Money value = Money.of(250000.00, "USD");

        Valuation valuation = asset.recordValuation(
            ValuationId.generate(),
            value,
            ValuationSource.APPRAISAL,
            "Professional appraisal"
        );

        assertThat(valuation).isNotNull();
        assertThat(asset.getCurrentValue()).isEqualTo(value);
    }

    @Test
    void should_ReturnLatestValuation_When_MultipleValuations() {
        Asset asset = Asset.create(
            AssetId.generate(),
            "My House",
            "Primary residence",
            AssetType.REAL_ESTATE
        );

        asset.recordValuation(
            ValuationId.generate(),
            Money.of(250000.00, "USD"),
            ValuationSource.MANUAL,
            "Initial"
        );

        Valuation latest = asset.recordValuation(
            ValuationId.generate(),
            Money.of(275000.00, "USD"),
            ValuationSource.APPRAISAL,
            "Latest"
        );

        assertThat(asset.getLatestValuation()).contains(latest);
        assertThat(asset.getCurrentValue()).isEqualByComparingTo(Money.of(275000.00, "USD"));
    }
}
```

### Integration Test

```java
package com.homewarehouse.assets.application.command.createasset;

import com.homewarehouse.assets.domain.model.AssetType;
import com.homewarehouse.assets.domain.repository.AssetRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@Testcontainers
class CreateAssetHandlerIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    private CreateAssetHandler handler;

    @Autowired
    private AssetRepository repository;

    @Test
    void should_CreateAsset_When_ValidCommand() {
        CreateAssetCommand command = new CreateAssetCommand(
            "Test Asset",
            "Test Description",
            AssetType.REAL_ESTATE,
            250000.0,
            "USD"
        );

        CreateAssetResult result = handler.handle(command);

        assertThat(result.assetId()).isNotNull();
        assertThat(repository.existsById(result.assetId())).isTrue();
    }
}
```

## Summary

This guide demonstrates complete Assets module implementation with:
- Asset tracking across multiple types
- Valuation history tracking
- Net worth calculation domain service
- Hexagonal architecture with clean separation
- Domain events for integration
- Comprehensive testing strategies
