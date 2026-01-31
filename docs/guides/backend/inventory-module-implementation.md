# Inventory Module Implementation Guide

## Overview

The **Inventory** module manages items, units, locations, and expiry tracking. It follows hexagonal architecture, domain-driven design, and vertical slicing principles.

## Module Structure

```
backend/inventory/
└── src/main/java/com/homewarehouse/inventory/
    ├── domain/
    │   ├── model/
    │   │   ├── Item.java                  # Item aggregate root
    │   │   ├── Unit.java                  # Unit entity
    │   │   ├── Location.java              # Location aggregate root
    │   │   ├── ItemId.java                # Value object
    │   │   ├── UnitId.java                # Value object
    │   │   ├── LocationId.java            # Value object
    │   │   ├── Barcode.java               # Value object
    │   │   ├── Quantity.java              # Value object
    │   │   └── ExpiryDate.java            # Value object
    │   ├── service/
    │   │   └── ExpiryCheckService.java    # Domain service
    │   ├── event/
    │   │   ├── ItemCreatedEvent.java
    │   │   ├── ItemUpdatedEvent.java
    │   │   ├── UnitCreatedEvent.java
    │   │   ├── UnitConsumedEvent.java
    │   │   ├── UnitExpiredEvent.java
    │   │   └── LocationCreatedEvent.java
    │   └── repository/
    │       ├── ItemRepository.java        # Port
    │       ├── UnitRepository.java        # Port
    │       └── LocationRepository.java    # Port
    ├── application/
    │   ├── command/
    │   │   ├── createitem/
    │   │   │   ├── CreateItemCommand.java
    │   │   │   ├── CreateItemHandler.java
    │   │   │   └── CreateItemResult.java
    │   │   ├── addunit/
    │   │   │   ├── AddUnitCommand.java
    │   │   │   ├── AddUnitHandler.java
    │   │   │   └── AddUnitResult.java
    │   │   ├── consumeunit/
    │   │   │   ├── ConsumeUnitCommand.java
    │   │   │   ├── ConsumeUnitHandler.java
    │   │   │   └── ConsumeUnitResult.java
    │   │   └── createlocation/
    │   │       ├── CreateLocationCommand.java
    │   │       ├── CreateLocationHandler.java
    │   │       └── CreateLocationResult.java
    │   ├── query/
    │   │   ├── listitem/
    │   │   │   ├── ListItemsQuery.java
    │   │   │   └── ListItemsHandler.java
    │   │   ├── getitem/
    │   │   │   ├── GetItemQuery.java
    │   │   │   └── GetItemHandler.java
    │   │   └── checkexpiry/
    │   │       ├── CheckExpiryQuery.java
    │   │       └── CheckExpiryHandler.java
    │   └── port/
    │       ├── in/
    │       │   ├── CreateItemUseCase.java
    │       │   ├── AddUnitUseCase.java
    │       │   └── ConsumeUnitUseCase.java
    │       └── out/
    │           ├── ItemEventPublisher.java
    │           └── NotificationService.java
    └── infrastructure/
        ├── persistence/
        │   ├── ItemJpaRepository.java
        │   ├── ItemEntity.java
        │   ├── UnitEntity.java
        │   ├── LocationEntity.java
        │   └── ItemRepositoryAdapter.java
        ├── messaging/
        │   └── RabbitMQItemEventPublisher.java
        ├── temporal/
        │   └── ExpiryCheckActivity.java
        └── web/
            ├── ItemController.java
            ├── dto/
            │   ├── CreateItemRequest.java
            │   ├── AddUnitRequest.java
            │   └── ItemResponse.java
            └── mapper/
                └── ItemMapper.java
```

## Domain Layer

### Value Objects

#### ItemId

```java
package com.homewarehouse.inventory.domain.model;

import java.util.UUID;

public record ItemId(UUID value) {

    public static ItemId generate() {
        return new ItemId(UUID.randomUUID());
    }

    public static ItemId of(UUID value) {
        if (value == null) {
            throw new IllegalArgumentException("ItemId cannot be null");
        }
        return new ItemId(value);
    }

    public static ItemId of(String value) {
        try {
            return new ItemId(UUID.fromString(value));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid ItemId format: " + value);
        }
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
```

#### Barcode

```java
package com.homewarehouse.inventory.domain.model;

public record Barcode(String value, BarcodeType type) {

    public Barcode {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Barcode value cannot be null or blank");
        }

        if (type == null) {
            throw new IllegalArgumentException("Barcode type cannot be null");
        }

        validateFormat(value, type);
    }

    private static void validateFormat(String value, BarcodeType type) {
        switch (type) {
            case EAN13 -> {
                if (!value.matches("^\\d{13}$")) {
                    throw new IllegalArgumentException(
                        "EAN-13 barcode must be exactly 13 digits"
                    );
                }
            }
            case UPC -> {
                if (!value.matches("^\\d{12}$")) {
                    throw new IllegalArgumentException(
                        "UPC barcode must be exactly 12 digits"
                    );
                }
            }
            case CODE128 -> {
                if (value.length() > 80) {
                    throw new IllegalArgumentException(
                        "CODE128 barcode must be max 80 characters"
                    );
                }
            }
            case QR_CODE -> {
                if (value.length() > 1000) {
                    throw new IllegalArgumentException(
                        "QR Code data must be max 1000 characters"
                    );
                }
            }
        }
    }

    public enum BarcodeType {
        EAN13,
        UPC,
        CODE128,
        QR_CODE
    }
}
```

#### Quantity

```java
package com.homewarehouse.inventory.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

public record Quantity(BigDecimal amount, String unit) {

    private static final int SCALE = 3;

    public Quantity {
        if (amount == null) {
            throw new IllegalArgumentException("Quantity amount cannot be null");
        }

        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Quantity amount cannot be negative");
        }

        if (unit == null || unit.isBlank()) {
            throw new IllegalArgumentException("Quantity unit cannot be null or blank");
        }

        // Normalize scale
        amount = amount.setScale(SCALE, RoundingMode.HALF_UP);
    }

    public static Quantity of(double amount, String unit) {
        return new Quantity(
            BigDecimal.valueOf(amount).setScale(SCALE, RoundingMode.HALF_UP),
            unit
        );
    }

    public static Quantity zero(String unit) {
        return new Quantity(BigDecimal.ZERO.setScale(SCALE), unit);
    }

    public Quantity add(Quantity other) {
        if (!this.unit.equals(other.unit)) {
            throw new IllegalArgumentException(
                "Cannot add quantities with different units: " + unit + " and " + other.unit
            );
        }

        return new Quantity(
            this.amount.add(other.amount).setScale(SCALE, RoundingMode.HALF_UP),
            this.unit
        );
    }

    public Quantity subtract(Quantity other) {
        if (!this.unit.equals(other.unit)) {
            throw new IllegalArgumentException(
                "Cannot subtract quantities with different units: " + unit + " and " + other.unit
            );
        }

        BigDecimal result = this.amount.subtract(other.amount);

        if (result.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Resulting quantity cannot be negative");
        }

        return new Quantity(result.setScale(SCALE, RoundingMode.HALF_UP), this.unit);
    }

    public boolean isZero() {
        return amount.compareTo(BigDecimal.ZERO) == 0;
    }

    public boolean isGreaterThan(Quantity other) {
        if (!this.unit.equals(other.unit)) {
            throw new IllegalArgumentException("Cannot compare quantities with different units");
        }
        return this.amount.compareTo(other.amount) > 0;
    }
}
```

#### ExpiryDate

```java
package com.homewarehouse.inventory.domain.model;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public record ExpiryDate(LocalDate date) {

    public ExpiryDate {
        if (date == null) {
            throw new IllegalArgumentException("Expiry date cannot be null");
        }
    }

    public static ExpiryDate of(LocalDate date) {
        return new ExpiryDate(date);
    }

    public boolean isExpired() {
        return LocalDate.now().isAfter(date);
    }

    public boolean isExpiringSoon(int daysThreshold) {
        if (daysThreshold < 0) {
            throw new IllegalArgumentException("Days threshold cannot be negative");
        }

        LocalDate thresholdDate = LocalDate.now().plusDays(daysThreshold);
        return !isExpired() && !date.isAfter(thresholdDate);
    }

    public long daysUntilExpiry() {
        return ChronoUnit.DAYS.between(LocalDate.now(), date);
    }

    public boolean isBefore(ExpiryDate other) {
        return this.date.isBefore(other.date);
    }
}
```

### Aggregate: Item

```java
package com.homewarehouse.inventory.domain.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class Item {

    private final ItemId id;
    private String name;
    private String description;
    private Barcode barcode;
    private final List<Unit> units;
    private final Instant createdAt;
    private Instant updatedAt;

    // Private constructor - use factory methods
    private Item(
        ItemId id,
        String name,
        String description,
        Barcode barcode,
        Instant createdAt
    ) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.barcode = barcode;
        this.units = new ArrayList<>();
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    // Factory method
    public static Item create(
        ItemId id,
        String name,
        String description,
        Barcode barcode
    ) {
        validateName(name);

        return new Item(
            id,
            name,
            description,
            barcode,
            Instant.now()
        );
    }

    // Reconstruct from database
    public static Item reconstruct(
        ItemId id,
        String name,
        String description,
        Barcode barcode,
        List<Unit> units,
        Instant createdAt,
        Instant updatedAt
    ) {
        Item item = new Item(id, name, description, barcode, createdAt);
        item.units.addAll(units);
        item.updatedAt = updatedAt;
        return item;
    }

    // Business logic
    public void updateDetails(String name, String description) {
        validateName(name);

        this.name = name;
        this.description = description;
        this.updatedAt = Instant.now();
    }

    public Unit addUnit(
        UnitId unitId,
        LocationId locationId,
        Quantity quantity,
        ExpiryDate expiryDate
    ) {
        Unit unit = Unit.create(unitId, id, locationId, quantity, expiryDate);
        units.add(unit);
        this.updatedAt = Instant.now();
        return unit;
    }

    public void consumeUnit(UnitId unitId, Quantity quantityConsumed) {
        Unit unit = findUnit(unitId)
            .orElseThrow(() -> new IllegalArgumentException(
                "Unit not found: " + unitId
            ));

        unit.consume(quantityConsumed);
        this.updatedAt = Instant.now();

        // Remove unit if fully consumed
        if (unit.getQuantity().isZero()) {
            units.remove(unit);
        }
    }

    public Quantity getTotalQuantity() {
        if (units.isEmpty()) {
            return Quantity.zero("units");
        }

        // Get unit from first unit (assuming all units have same unit of measure)
        String unitOfMeasure = units.get(0).getQuantity().unit();

        return units.stream()
            .map(Unit::getQuantity)
            .reduce(
                Quantity.zero(unitOfMeasure),
                Quantity::add
            );
    }

    public List<Unit> getExpiringUnits(int daysThreshold) {
        return units.stream()
            .filter(unit -> unit.isExpiringSoon(daysThreshold))
            .toList();
    }

    public List<Unit> getExpiredUnits() {
        return units.stream()
            .filter(Unit::isExpired)
            .toList();
    }

    // Validation
    private static void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Item name cannot be null or blank");
        }

        if (name.length() > 200) {
            throw new IllegalArgumentException("Item name cannot exceed 200 characters");
        }
    }

    // Helper methods
    private Optional<Unit> findUnit(UnitId unitId) {
        return units.stream()
            .filter(u -> u.getId().equals(unitId))
            .findFirst();
    }

    // Getters
    public ItemId getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public Barcode getBarcode() {
        return barcode;
    }

    public List<Unit> getUnits() {
        return Collections.unmodifiableList(units);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
```

### Entity: Unit

```java
package com.homewarehouse.inventory.domain.model;

import java.time.Instant;

public class Unit {

    private final UnitId id;
    private final ItemId itemId;
    private final LocationId locationId;
    private Quantity quantity;
    private final ExpiryDate expiryDate;
    private final Instant createdAt;
    private Instant updatedAt;

    private Unit(
        UnitId id,
        ItemId itemId,
        LocationId locationId,
        Quantity quantity,
        ExpiryDate expiryDate,
        Instant createdAt
    ) {
        this.id = id;
        this.itemId = itemId;
        this.locationId = locationId;
        this.quantity = quantity;
        this.expiryDate = expiryDate;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    public static Unit create(
        UnitId id,
        ItemId itemId,
        LocationId locationId,
        Quantity quantity,
        ExpiryDate expiryDate
    ) {
        if (quantity.isZero()) {
            throw new IllegalArgumentException("Cannot create unit with zero quantity");
        }

        return new Unit(id, itemId, locationId, quantity, expiryDate, Instant.now());
    }

    public static Unit reconstruct(
        UnitId id,
        ItemId itemId,
        LocationId locationId,
        Quantity quantity,
        ExpiryDate expiryDate,
        Instant createdAt,
        Instant updatedAt
    ) {
        return new Unit(id, itemId, locationId, quantity, expiryDate, createdAt);
    }

    public void consume(Quantity quantityToConsume) {
        this.quantity = this.quantity.subtract(quantityToConsume);
        this.updatedAt = Instant.now();
    }

    public boolean isExpired() {
        return expiryDate.isExpired();
    }

    public boolean isExpiringSoon(int daysThreshold) {
        return expiryDate.isExpiringSoon(daysThreshold);
    }

    public long daysUntilExpiry() {
        return expiryDate.daysUntilExpiry();
    }

    // Getters
    public UnitId getId() {
        return id;
    }

    public ItemId getItemId() {
        return itemId;
    }

    public LocationId getLocationId() {
        return locationId;
    }

    public Quantity getQuantity() {
        return quantity;
    }

    public ExpiryDate getExpiryDate() {
        return expiryDate;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
```

### Aggregate: Location

```java
package com.homewarehouse.inventory.domain.model;

import java.time.Instant;

public class Location {

    private final LocationId id;
    private String name;
    private String description;
    private final LocationId parentLocationId; // null for root locations
    private final Instant createdAt;
    private Instant updatedAt;

    private Location(
        LocationId id,
        String name,
        String description,
        LocationId parentLocationId,
        Instant createdAt
    ) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.parentLocationId = parentLocationId;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    public static Location create(
        LocationId id,
        String name,
        String description,
        LocationId parentLocationId
    ) {
        validateName(name);

        return new Location(id, name, description, parentLocationId, Instant.now());
    }

    public static Location reconstruct(
        LocationId id,
        String name,
        String description,
        LocationId parentLocationId,
        Instant createdAt,
        Instant updatedAt
    ) {
        Location location = new Location(id, name, description, parentLocationId, createdAt);
        location.updatedAt = updatedAt;
        return location;
    }

    public void updateDetails(String name, String description) {
        validateName(name);
        this.name = name;
        this.description = description;
        this.updatedAt = Instant.now();
    }

    public boolean isRootLocation() {
        return parentLocationId == null;
    }

    private static void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Location name cannot be null or blank");
        }

        if (name.length() > 100) {
            throw new IllegalArgumentException("Location name cannot exceed 100 characters");
        }
    }

    // Getters
    public LocationId getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public LocationId getParentLocationId() {
        return parentLocationId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
```

### Domain Events

```java
package com.homewarehouse.inventory.domain.event;

import com.homewarehouse.inventory.domain.model.ItemId;
import java.time.Instant;
import java.util.UUID;

public record ItemCreatedEvent(
    UUID eventId,
    ItemId itemId,
    String itemName,
    String barcode,
    Instant occurredAt
) {
    public static ItemCreatedEvent of(ItemId itemId, String itemName, String barcode) {
        return new ItemCreatedEvent(
            UUID.randomUUID(),
            itemId,
            itemName,
            barcode,
            Instant.now()
        );
    }
}
```

```java
package com.homewarehouse.inventory.domain.event;

import com.homewarehouse.inventory.domain.model.UnitId;
import com.homewarehouse.inventory.domain.model.ItemId;
import com.homewarehouse.inventory.domain.model.Quantity;
import java.time.Instant;
import java.util.UUID;

public record UnitConsumedEvent(
    UUID eventId,
    UnitId unitId,
    ItemId itemId,
    Quantity quantityConsumed,
    Quantity remainingQuantity,
    Instant occurredAt
) {
    public static UnitConsumedEvent of(
        UnitId unitId,
        ItemId itemId,
        Quantity quantityConsumed,
        Quantity remainingQuantity
    ) {
        return new UnitConsumedEvent(
            UUID.randomUUID(),
            unitId,
            itemId,
            quantityConsumed,
            remainingQuantity,
            Instant.now()
        );
    }
}
```

### Repository Interfaces (Ports)

```java
package com.homewarehouse.inventory.domain.repository;

import com.homewarehouse.inventory.domain.model.Item;
import com.homewarehouse.inventory.domain.model.ItemId;
import java.util.List;
import java.util.Optional;

public interface ItemRepository {

    Item save(Item item);

    Optional<Item> findById(ItemId id);

    List<Item> findAll(int page, int size);

    List<Item> findByNameContaining(String searchTerm, int page, int size);

    List<Item> findByBarcode(String barcode);

    boolean existsById(ItemId id);

    void deleteById(ItemId id);

    long count();
}
```

## Application Layer

### Command: Create Item

```java
package com.homewarehouse.inventory.application.command.createitem;

import com.homewarehouse.inventory.domain.model.Barcode;

public record CreateItemCommand(
    String name,
    String description,
    String barcodeValue,
    Barcode.BarcodeType barcodeType
) {
    public CreateItemCommand {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Name is required");
        }
    }
}
```

```java
package com.homewarehouse.inventory.application.command.createitem;

import com.homewarehouse.inventory.domain.model.ItemId;

public record CreateItemResult(
    ItemId itemId,
    String name
) {}
```

```java
package com.homewarehouse.inventory.application.command.createitem;

import com.homewarehouse.inventory.domain.model.*;
import com.homewarehouse.inventory.domain.repository.ItemRepository;
import com.homewarehouse.inventory.domain.event.ItemCreatedEvent;
import com.homewarehouse.inventory.application.port.out.ItemEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CreateItemHandler {

    private final ItemRepository itemRepository;
    private final ItemEventPublisher eventPublisher;

    public CreateItemHandler(
        ItemRepository itemRepository,
        ItemEventPublisher eventPublisher
    ) {
        this.itemRepository = itemRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public CreateItemResult handle(CreateItemCommand command) {
        // Create value objects
        ItemId itemId = ItemId.generate();
        Barcode barcode = new Barcode(command.barcodeValue(), command.barcodeType());

        // Check if item with barcode already exists
        List<Item> existingItems = itemRepository.findByBarcode(barcode.value());
        if (!existingItems.isEmpty()) {
            throw new IllegalArgumentException(
                "Item with barcode " + barcode.value() + " already exists"
            );
        }

        // Create item aggregate
        Item item = Item.create(
            itemId,
            command.name(),
            command.description(),
            barcode
        );

        // Persist
        itemRepository.save(item);

        // Publish domain event
        ItemCreatedEvent event = ItemCreatedEvent.of(
            item.getId(),
            item.getName(),
            item.getBarcode().value()
        );
        eventPublisher.publish(event);

        return new CreateItemResult(item.getId(), item.getName());
    }
}
```

### Command: Add Unit

```java
package com.homewarehouse.inventory.application.command.addunit;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record AddUnitCommand(
    UUID itemId,
    UUID locationId,
    BigDecimal quantity,
    String unit,
    LocalDate expiryDate
) {
    public AddUnitCommand {
        if (itemId == null) {
            throw new IllegalArgumentException("Item ID is required");
        }
        if (locationId == null) {
            throw new IllegalArgumentException("Location ID is required");
        }
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }
    }
}
```

```java
package com.homewarehouse.inventory.application.command.addunit;

import com.homewarehouse.inventory.domain.model.*;
import com.homewarehouse.inventory.domain.repository.ItemRepository;
import com.homewarehouse.inventory.domain.repository.LocationRepository;
import com.homewarehouse.inventory.domain.event.UnitCreatedEvent;
import com.homewarehouse.inventory.application.port.out.ItemEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AddUnitHandler {

    private final ItemRepository itemRepository;
    private final LocationRepository locationRepository;
    private final ItemEventPublisher eventPublisher;

    public AddUnitHandler(
        ItemRepository itemRepository,
        LocationRepository locationRepository,
        ItemEventPublisher eventPublisher
    ) {
        this.itemRepository = itemRepository;
        this.locationRepository = locationRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public AddUnitResult handle(AddUnitCommand command) {
        // Fetch item
        ItemId itemId = ItemId.of(command.itemId());
        Item item = itemRepository.findById(itemId)
            .orElseThrow(() -> new IllegalArgumentException("Item not found: " + itemId));

        // Validate location exists
        LocationId locationId = LocationId.of(command.locationId());
        if (!locationRepository.existsById(locationId)) {
            throw new IllegalArgumentException("Location not found: " + locationId);
        }

        // Create value objects
        UnitId unitId = UnitId.generate();
        Quantity quantity = new Quantity(command.quantity(), command.unit());
        ExpiryDate expiryDate = ExpiryDate.of(command.expiryDate());

        // Add unit to item
        Unit unit = item.addUnit(unitId, locationId, quantity, expiryDate);

        // Persist
        itemRepository.save(item);

        // Publish event
        UnitCreatedEvent event = UnitCreatedEvent.of(
            unit.getId(),
            item.getId(),
            quantity,
            expiryDate
        );
        eventPublisher.publish(event);

        return new AddUnitResult(unit.getId(), item.getId());
    }
}
```

### Query: List Items

```java
package com.homewarehouse.inventory.application.query.listitem;

public record ListItemsQuery(
    String searchTerm,
    int page,
    int size
) {
    public ListItemsQuery {
        if (page < 0) {
            throw new IllegalArgumentException("Page cannot be negative");
        }
        if (size <= 0 || size > 100) {
            throw new IllegalArgumentException("Size must be between 1 and 100");
        }
    }
}
```

```java
package com.homewarehouse.inventory.application.query.listitem;

import com.homewarehouse.inventory.domain.model.Item;
import com.homewarehouse.inventory.domain.repository.ItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
public class ListItemsHandler {

    private final ItemRepository itemRepository;

    public ListItemsHandler(ItemRepository itemRepository) {
        this.itemRepository = itemRepository;
    }

    @Transactional(readOnly = true)
    public List<Item> handle(ListItemsQuery query) {
        if (query.searchTerm() != null && !query.searchTerm().isBlank()) {
            return itemRepository.findByNameContaining(
                query.searchTerm(),
                query.page(),
                query.size()
            );
        }

        return itemRepository.findAll(query.page(), query.size());
    }
}
```

## Infrastructure Layer

### JPA Entities

```java
package com.homewarehouse.inventory.infrastructure.persistence;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "inventory_items")
public class ItemEntity {

    @Id
    @Column(name = "id", columnDefinition = "UUID")
    private UUID id;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "barcode_value", length = 100)
    private String barcodeValue;

    @Column(name = "barcode_type", length = 20)
    @Enumerated(EnumType.STRING)
    private BarcodeTypeEntity barcodeType;

    @OneToMany(mappedBy = "item", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<UnitEntity> units = new ArrayList<>();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // Constructors, getters, setters
    protected ItemEntity() {}

    public ItemEntity(UUID id, String name, String description,
                      String barcodeValue, BarcodeTypeEntity barcodeType) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.barcodeValue = barcodeValue;
        this.barcodeType = barcodeType;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    // Getters and setters omitted for brevity
}

@Entity
@Table(name = "inventory_units")
public class UnitEntity {

    @Id
    @Column(name = "id", columnDefinition = "UUID")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id", nullable = false)
    private ItemEntity item;

    @Column(name = "location_id", columnDefinition = "UUID", nullable = false)
    private UUID locationId;

    @Column(name = "quantity", nullable = false, precision = 15, scale = 3)
    private BigDecimal quantity;

    @Column(name = "unit", nullable = false, length = 20)
    private String unit;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // Constructors, getters, setters omitted
}

enum BarcodeTypeEntity {
    EAN13, UPC, CODE128, QR_CODE
}
```

### Repository Adapter

```java
package com.homewarehouse.inventory.infrastructure.persistence;

import com.homewarehouse.inventory.domain.model.*;
import com.homewarehouse.inventory.domain.repository.ItemRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
public class ItemRepositoryAdapter implements ItemRepository {

    private final ItemJpaRepository jpaRepository;

    public ItemRepositoryAdapter(ItemJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Item save(Item item) {
        ItemEntity entity = toEntity(item);
        ItemEntity saved = jpaRepository.save(entity);
        return toDomain(saved);
    }

    @Override
    public Optional<Item> findById(ItemId id) {
        return jpaRepository.findById(id.value())
            .map(this::toDomain);
    }

    @Override
    public List<Item> findAll(int page, int size) {
        return jpaRepository.findAll(PageRequest.of(page, size))
            .map(this::toDomain)
            .toList();
    }

    @Override
    public List<Item> findByBarcode(String barcode) {
        return jpaRepository.findByBarcodeValue(barcode).stream()
            .map(this::toDomain)
            .collect(Collectors.toList());
    }

    @Override
    public boolean existsById(ItemId id) {
        return jpaRepository.existsById(id.value());
    }

    @Override
    public void deleteById(ItemId id) {
        jpaRepository.deleteById(id.value());
    }

    @Override
    public long count() {
        return jpaRepository.count();
    }

    // Mapping methods
    private ItemEntity toEntity(Item item) {
        ItemEntity entity = new ItemEntity(
            item.getId().value(),
            item.getName(),
            item.getDescription(),
            item.getBarcode().value(),
            toBarcodeTypeEntity(item.getBarcode().type())
        );

        // Map units
        List<UnitEntity> unitEntities = item.getUnits().stream()
            .map(unit -> toUnitEntity(unit, entity))
            .collect(Collectors.toList());

        entity.setUnits(unitEntities);

        return entity;
    }

    private Item toDomain(ItemEntity entity) {
        Barcode barcode = new Barcode(
            entity.getBarcodeValue(),
            toBarcodeType(entity.getBarcodeType())
        );

        List<Unit> units = entity.getUnits().stream()
            .map(this::toUnitDomain)
            .collect(Collectors.toList());

        return Item.reconstruct(
            ItemId.of(entity.getId()),
            entity.getName(),
            entity.getDescription(),
            barcode,
            units,
            entity.getCreatedAt(),
            entity.getUpdatedAt()
        );
    }

    // Additional mapping methods omitted for brevity
}
```

### REST Controller

```java
package com.homewarehouse.inventory.infrastructure.web;

import com.homewarehouse.inventory.application.command.createitem.*;
import com.homewarehouse.inventory.application.query.listitem.*;
import com.homewarehouse.inventory.infrastructure.web.dto.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/inventory/items")
public class ItemController {

    private final CreateItemHandler createItemHandler;
    private final ListItemsHandler listItemsHandler;
    private final ItemMapper mapper;

    public ItemController(
        CreateItemHandler createItemHandler,
        ListItemsHandler listItemsHandler,
        ItemMapper mapper
    ) {
        this.createItemHandler = createItemHandler;
        this.listItemsHandler = listItemsHandler;
        this.mapper = mapper;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('item:create')")
    public ResponseEntity<ItemResponse> createItem(
        @RequestBody CreateItemRequest request
    ) {
        CreateItemCommand command = mapper.toCommand(request);
        CreateItemResult result = createItemHandler.handle(command);

        ItemResponse response = mapper.toResponse(result);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('item:read')")
    public ResponseEntity<List<ItemResponse>> listItems(
        @RequestParam(required = false) String search,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        ListItemsQuery query = new ListItemsQuery(search, page, size);
        List<Item> items = listItemsHandler.handle(query);

        List<ItemResponse> response = items.stream()
            .map(mapper::toResponse)
            .toList();

        return ResponseEntity.ok(response);
    }
}
```

## Testing

### Unit Test Example

```java
package com.homewarehouse.inventory.domain.model;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.*;

class QuantityTest {

    @Test
    void should_CreateQuantity_When_ValidInput() {
        Quantity quantity = Quantity.of(10.5, "kg");

        assertThat(quantity.amount()).isEqualByComparingTo("10.500");
        assertThat(quantity.unit()).isEqualTo("kg");
    }

    @Test
    void should_ThrowException_When_NegativeAmount() {
        assertThatThrownBy(() -> Quantity.of(-5, "kg"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("cannot be negative");
    }

    @Test
    void should_AddQuantities_When_SameUnit() {
        Quantity q1 = Quantity.of(10, "kg");
        Quantity q2 = Quantity.of(5, "kg");

        Quantity result = q1.add(q2);

        assertThat(result.amount()).isEqualByComparingTo("15.000");
        assertThat(result.unit()).isEqualTo("kg");
    }

    @Test
    void should_ThrowException_When_AddingDifferentUnits() {
        Quantity q1 = Quantity.of(10, "kg");
        Quantity q2 = Quantity.of(5, "lbs");

        assertThatThrownBy(() -> q1.add(q2))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("different units");
    }
}
```

### Integration Test Example

```java
package com.homewarehouse.inventory.application.command.createitem;

import com.homewarehouse.inventory.domain.model.Barcode;
import com.homewarehouse.inventory.domain.repository.ItemRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@Testcontainers
class CreateItemHandlerIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
        .withDatabaseName("test")
        .withUsername("test")
        .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private CreateItemHandler handler;

    @Autowired
    private ItemRepository repository;

    @Test
    void should_CreateItem_When_ValidCommand() {
        CreateItemCommand command = new CreateItemCommand(
            "Apple",
            "Fresh red apple",
            "1234567890123",
            Barcode.BarcodeType.EAN13
        );

        CreateItemResult result = handler.handle(command);

        assertThat(result.itemId()).isNotNull();
        assertThat(result.name()).isEqualTo("Apple");

        assertThat(repository.existsById(result.itemId())).isTrue();
    }
}
```

## Summary

This guide demonstrates complete inventory module implementation following hexagonal architecture with:
- Rich domain model with value objects and aggregates
- Clear separation of concerns (domain, application, infrastructure)
- Command/query segregation
- Domain events for integration
- Comprehensive testing strategies
