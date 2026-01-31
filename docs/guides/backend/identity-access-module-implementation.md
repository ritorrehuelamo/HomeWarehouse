# Identity & Access Module Implementation Guide

## Overview

The **Identity & Access** module handles authentication, authorization, users, roles, and permissions. It follows hexagonal architecture, domain-driven design, and vertical slicing principles with security as a first-class concern.

## Module Structure

```
backend/identity-access/
└── src/main/java/com/homewarehouse/identityaccess/
    ├── domain/
    │   ├── model/
    │   │   ├── User.java                  # User aggregate root
    │   │   ├── Role.java                  # Role entity
    │   │   ├── Permission.java            # Permission value object
    │   │   ├── UserId.java                # Value object
    │   │   ├── RoleId.java                # Value object
    │   │   ├── Username.java              # Value object
    │   │   ├── Email.java                 # Value object
    │   │   └── PasswordHash.java          # Value object
    │   ├── service/
    │   │   ├── PasswordService.java       # Domain service
    │   │   └── PermissionEvaluator.java   # Domain service
    │   ├── event/
    │   │   ├── UserCreatedEvent.java
    │   │   ├── UserDeletedEvent.java
    │   │   ├── RoleAssignedEvent.java
    │   │   └── PasswordChangedEvent.java
    │   └── repository/
    │       ├── UserRepository.java        # Port
    │       └── RoleRepository.java        # Port
    ├── application/
    │   ├── command/
    │   │   ├── createuser/
    │   │   │   ├── CreateUserCommand.java
    │   │   │   ├── CreateUserHandler.java
    │   │   │   └── CreateUserResult.java
    │   │   ├── assignrole/
    │   │   │   ├── AssignRoleCommand.java
    │   │   │   ├── AssignRoleHandler.java
    │   │   │   └── AssignRoleResult.java
    │   │   ├── changepassword/
    │   │   │   ├── ChangePasswordCommand.java
    │   │   │   ├── ChangePasswordHandler.java
    │   │   │   └── ChangePasswordResult.java
    │   │   └── authenticate/
    │   │       ├── AuthenticateCommand.java
    │   │       ├── AuthenticateHandler.java
    │   │       └── AuthenticationResult.java
    │   ├── query/
    │   │   ├── getuser/
    │   │   │   ├── GetUserQuery.java
    │   │   │   └── GetUserHandler.java
    │   │   └── listusers/
    │   │       ├── ListUsersQuery.java
    │   │       └── ListUsersHandler.java
    │   └── port/
    │       ├── in/
    │       │   ├── CreateUserUseCase.java
    │       │   └── AuthenticateUseCase.java
    │       └── out/
    │           ├── UserEventPublisher.java
    │           ├── TokenService.java
    │           └── AuditService.java
    └── infrastructure/
        ├── persistence/
        │   ├── UserJpaRepository.java
        │   ├── RoleJpaRepository.java
        │   ├── UserEntity.java
        │   ├── RoleEntity.java
        │   └── UserRepositoryAdapter.java
        ├── security/
        │   ├── JwtTokenService.java
        │   ├── BCryptPasswordService.java
        │   ├── SecurityConfiguration.java
        │   └── JwtAuthenticationFilter.java
        ├── messaging/
        │   └── RabbitMQUserEventPublisher.java
        └── web/
            ├── AuthController.java
            ├── UserController.java
            ├── dto/
            │   ├── LoginRequest.java
            │   ├── AuthResponse.java
            │   ├── CreateUserRequest.java
            │   └── UserResponse.java
            └── mapper/
                └── UserMapper.java
```

## Domain Layer

### Value Objects

#### UserId

```java
package com.homewarehouse.identityaccess.domain.model;

import java.util.UUID;

public record UserId(UUID value) {

    public static UserId generate() {
        return new UserId(UUID.randomUUID());
    }

    public static UserId of(UUID value) {
        if (value == null) {
            throw new IllegalArgumentException("UserId cannot be null");
        }
        return new UserId(value);
    }

    public static UserId of(String value) {
        try {
            return new UserId(UUID.fromString(value));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid UserId format: " + value);
        }
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
```

#### Username

```java
package com.homewarehouse.identityaccess.domain.model;

import java.util.regex.Pattern;

public record Username(String value) {

    private static final int MIN_LENGTH = 3;
    private static final int MAX_LENGTH = 50;
    private static final Pattern VALID_PATTERN = Pattern.compile("^[a-zA-Z0-9._-]+$");

    public Username {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Username cannot be null or blank");
        }

        if (value.length() < MIN_LENGTH || value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException(
                String.format("Username must be between %d and %d characters",
                    MIN_LENGTH, MAX_LENGTH)
            );
        }

        if (!VALID_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException(
                "Username can only contain letters, numbers, dots, underscores, and hyphens"
            );
        }
    }

    public static Username of(String value) {
        return new Username(value);
    }
}
```

#### Email

```java
package com.homewarehouse.identityaccess.domain.model;

import java.util.regex.Pattern;

public record Email(String value) {

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
        "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"
    );

    public Email {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Email cannot be null or blank");
        }

        if (!EMAIL_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid email format: " + value);
        }

        // Normalize to lowercase
        value = value.toLowerCase();
    }

    public static Email of(String value) {
        return new Email(value);
    }
}
```

#### PasswordHash

```java
package com.homewarehouse.identityaccess.domain.model;

public record PasswordHash(String value) {

    private static final int BCRYPT_HASH_LENGTH = 60;

    public PasswordHash {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Password hash cannot be null or blank");
        }

        // BCrypt hashes are always 60 characters
        if (value.length() != BCRYPT_HASH_LENGTH) {
            throw new IllegalArgumentException("Invalid BCrypt hash format");
        }

        // BCrypt hashes start with $2a$, $2b$, or $2y$
        if (!value.startsWith("$2")) {
            throw new IllegalArgumentException("Invalid BCrypt hash format");
        }
    }

    public static PasswordHash of(String value) {
        return new PasswordHash(value);
    }
}
```

#### Permission

```java
package com.homewarehouse.identityaccess.domain.model;

public record Permission(String resource, String action) {

    public Permission {
        if (resource == null || resource.isBlank()) {
            throw new IllegalArgumentException("Permission resource cannot be null or blank");
        }

        if (action == null || action.isBlank()) {
            throw new IllegalArgumentException("Permission action cannot be null or blank");
        }
    }

    public static Permission of(String resource, String action) {
        return new Permission(resource, action);
    }

    public static Permission parse(String permissionString) {
        if (permissionString == null || !permissionString.contains(":")) {
            throw new IllegalArgumentException(
                "Permission must be in format 'resource:action'"
            );
        }

        String[] parts = permissionString.split(":", 2);
        return new Permission(parts[0], parts[1]);
    }

    @Override
    public String toString() {
        return resource + ":" + action;
    }

    public boolean matches(Permission required) {
        // Support wildcard permissions
        boolean resourceMatches = "*".equals(this.resource) ||
                                  this.resource.equals(required.resource);

        boolean actionMatches = "*".equals(this.action) ||
                                this.action.equals(required.action);

        return resourceMatches && actionMatches;
    }
}
```

### Aggregate: User

```java
package com.homewarehouse.identityaccess.domain.model;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.Collections;

public class User {

    private final UserId id;
    private Username username;
    private Email email;
    private PasswordHash passwordHash;
    private final Set<Role> roles;
    private boolean enabled;
    private int failedLoginAttempts;
    private Instant lockedUntil;
    private final Instant createdAt;
    private Instant updatedAt;

    // Private constructor - use factory methods
    private User(
        UserId id,
        Username username,
        Email email,
        PasswordHash passwordHash,
        Instant createdAt
    ) {
        this.id = id;
        this.username = username;
        this.email = email;
        this.passwordHash = passwordHash;
        this.roles = new HashSet<>();
        this.enabled = true;
        this.failedLoginAttempts = 0;
        this.lockedUntil = null;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    // Factory method
    public static User create(
        UserId id,
        Username username,
        Email email,
        PasswordHash passwordHash
    ) {
        return new User(id, username, email, passwordHash, Instant.now());
    }

    // Reconstruct from database
    public static User reconstruct(
        UserId id,
        Username username,
        Email email,
        PasswordHash passwordHash,
        Set<Role> roles,
        boolean enabled,
        int failedLoginAttempts,
        Instant lockedUntil,
        Instant createdAt,
        Instant updatedAt
    ) {
        User user = new User(id, username, email, passwordHash, createdAt);
        user.roles.addAll(roles);
        user.enabled = enabled;
        user.failedLoginAttempts = failedLoginAttempts;
        user.lockedUntil = lockedUntil;
        user.updatedAt = updatedAt;
        return user;
    }

    // Business logic: Role management
    public void assignRole(Role role) {
        if (role == null) {
            throw new IllegalArgumentException("Role cannot be null");
        }

        roles.add(role);
        this.updatedAt = Instant.now();
    }

    public void removeRole(RoleId roleId) {
        roles.removeIf(role -> role.getId().equals(roleId));
        this.updatedAt = Instant.now();
    }

    public boolean hasRole(String roleName) {
        return roles.stream()
            .anyMatch(role -> role.getName().equals(roleName));
    }

    // Business logic: Password management
    public void changePassword(PasswordHash newPasswordHash) {
        if (newPasswordHash == null) {
            throw new IllegalArgumentException("Password hash cannot be null");
        }

        this.passwordHash = newPasswordHash;
        this.updatedAt = Instant.now();
    }

    // Business logic: Account lockout
    public void incrementFailedLoginAttempts() {
        this.failedLoginAttempts++;
        this.updatedAt = Instant.now();
    }

    public void resetFailedLoginAttempts() {
        this.failedLoginAttempts = 0;
        this.updatedAt = Instant.now();
    }

    public void lockAccount(Instant until) {
        this.lockedUntil = until;
        this.updatedAt = Instant.now();
    }

    public void unlockAccount() {
        this.lockedUntil = null;
        this.failedLoginAttempts = 0;
        this.updatedAt = Instant.now();
    }

    public boolean isLocked() {
        if (lockedUntil == null) {
            return false;
        }

        // Check if lock has expired
        if (Instant.now().isAfter(lockedUntil)) {
            return false;
        }

        return true;
    }

    // Business logic: Enable/Disable
    public void enable() {
        this.enabled = true;
        this.updatedAt = Instant.now();
    }

    public void disable() {
        this.enabled = false;
        this.updatedAt = Instant.now();
    }

    // Business logic: Permission checking
    public boolean hasPermission(Permission requiredPermission) {
        return roles.stream()
            .flatMap(role -> role.getPermissions().stream())
            .anyMatch(permission -> permission.matches(requiredPermission));
    }

    public Set<Permission> getAllPermissions() {
        Set<Permission> allPermissions = new HashSet<>();
        roles.forEach(role -> allPermissions.addAll(role.getPermissions()));
        return Collections.unmodifiableSet(allPermissions);
    }

    // Getters
    public UserId getId() {
        return id;
    }

    public Username getUsername() {
        return username;
    }

    public Email getEmail() {
        return email;
    }

    public PasswordHash getPasswordHash() {
        return passwordHash;
    }

    public Set<Role> getRoles() {
        return Collections.unmodifiableSet(roles);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public int getFailedLoginAttempts() {
        return failedLoginAttempts;
    }

    public Instant getLockedUntil() {
        return lockedUntil;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
```

### Entity: Role

```java
package com.homewarehouse.identityaccess.domain.model;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.Collections;

public class Role {

    private final RoleId id;
    private String name;
    private String description;
    private final Set<Permission> permissions;
    private final Instant createdAt;
    private Instant updatedAt;

    private Role(
        RoleId id,
        String name,
        String description,
        Instant createdAt
    ) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.permissions = new HashSet<>();
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    public static Role create(
        RoleId id,
        String name,
        String description
    ) {
        validateName(name);

        return new Role(id, name, description, Instant.now());
    }

    public static Role reconstruct(
        RoleId id,
        String name,
        String description,
        Set<Permission> permissions,
        Instant createdAt,
        Instant updatedAt
    ) {
        Role role = new Role(id, name, description, createdAt);
        role.permissions.addAll(permissions);
        role.updatedAt = updatedAt;
        return role;
    }

    public void addPermission(Permission permission) {
        permissions.add(permission);
        this.updatedAt = Instant.now();
    }

    public void removePermission(Permission permission) {
        permissions.remove(permission);
        this.updatedAt = Instant.now();
    }

    public boolean hasPermission(Permission permission) {
        return permissions.contains(permission);
    }

    private static void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Role name cannot be null or blank");
        }

        if (name.length() > 50) {
            throw new IllegalArgumentException("Role name cannot exceed 50 characters");
        }
    }

    // Getters
    public RoleId getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public Set<Permission> getPermissions() {
        return Collections.unmodifiableSet(permissions);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
```

### Domain Service: PasswordService

```java
package com.homewarehouse.identityaccess.domain.service;

import com.homewarehouse.identityaccess.domain.model.PasswordHash;

/**
 * Domain service for password operations.
 * Implementation details (BCrypt) hidden behind this interface.
 */
public interface PasswordService {

    /**
     * Hash a plain text password.
     *
     * @param plainPassword The plain text password
     * @return The hashed password
     */
    PasswordHash hashPassword(String plainPassword);

    /**
     * Verify a plain text password against a hash.
     *
     * @param plainPassword The plain text password
     * @param passwordHash The stored password hash
     * @return true if password matches, false otherwise
     */
    boolean verifyPassword(String plainPassword, PasswordHash passwordHash);

    /**
     * Validate password strength.
     *
     * @param plainPassword The plain text password
     * @throws IllegalArgumentException if password doesn't meet requirements
     */
    void validatePasswordStrength(String plainPassword);
}
```

### Domain Events

```java
package com.homewarehouse.identityaccess.domain.event;

import com.homewarehouse.identityaccess.domain.model.UserId;
import java.time.Instant;
import java.util.UUID;

public record UserCreatedEvent(
    UUID eventId,
    UserId userId,
    String username,
    String email,
    Instant occurredAt
) {
    public static UserCreatedEvent of(UserId userId, String username, String email) {
        return new UserCreatedEvent(
            UUID.randomUUID(),
            userId,
            username,
            email,
            Instant.now()
        );
    }
}
```

```java
package com.homewarehouse.identityaccess.domain.event;

import com.homewarehouse.identityaccess.domain.model.UserId;
import com.homewarehouse.identityaccess.domain.model.RoleId;
import java.time.Instant;
import java.util.UUID;

public record RoleAssignedEvent(
    UUID eventId,
    UserId userId,
    RoleId roleId,
    String roleName,
    Instant occurredAt
) {
    public static RoleAssignedEvent of(UserId userId, RoleId roleId, String roleName) {
        return new RoleAssignedEvent(
            UUID.randomUUID(),
            userId,
            roleId,
            roleName,
            Instant.now()
        );
    }
}
```

### Repository Interfaces (Ports)

```java
package com.homewarehouse.identityaccess.domain.repository;

import com.homewarehouse.identityaccess.domain.model.User;
import com.homewarehouse.identityaccess.domain.model.UserId;
import com.homewarehouse.identityaccess.domain.model.Username;
import com.homewarehouse.identityaccess.domain.model.Email;
import java.util.List;
import java.util.Optional;

public interface UserRepository {

    User save(User user);

    Optional<User> findById(UserId id);

    Optional<User> findByUsername(Username username);

    Optional<User> findByEmail(Email email);

    List<User> findAll(int page, int size);

    boolean existsByUsername(Username username);

    boolean existsByEmail(Email email);

    void deleteById(UserId id);

    long count();
}
```

## Application Layer

### Command: Create User

```java
package com.homewarehouse.identityaccess.application.command.createuser;

public record CreateUserCommand(
    String username,
    String email,
    String password
) {
    public CreateUserCommand {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Username is required");
        }

        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Email is required");
        }

        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("Password is required");
        }
    }
}
```

```java
package com.homewarehouse.identityaccess.application.command.createuser;

import com.homewarehouse.identityaccess.domain.model.UserId;

public record CreateUserResult(
    UserId userId,
    String username,
    String email
) {}
```

```java
package com.homewarehouse.identityaccess.application.command.createuser;

import com.homewarehouse.identityaccess.domain.model.*;
import com.homewarehouse.identityaccess.domain.repository.UserRepository;
import com.homewarehouse.identityaccess.domain.service.PasswordService;
import com.homewarehouse.identityaccess.domain.event.UserCreatedEvent;
import com.homewarehouse.identityaccess.application.port.out.UserEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CreateUserHandler {

    private final UserRepository userRepository;
    private final PasswordService passwordService;
    private final UserEventPublisher eventPublisher;

    public CreateUserHandler(
        UserRepository userRepository,
        PasswordService passwordService,
        UserEventPublisher eventPublisher
    ) {
        this.userRepository = userRepository;
        this.passwordService = passwordService;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public CreateUserResult handle(CreateUserCommand command) {
        // Create value objects
        Username username = Username.of(command.username());
        Email email = Email.of(command.email());

        // Check for duplicates
        if (userRepository.existsByUsername(username)) {
            throw new IllegalArgumentException("Username already exists: " + username.value());
        }

        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Email already exists: " + email.value());
        }

        // Validate and hash password
        passwordService.validatePasswordStrength(command.password());
        PasswordHash passwordHash = passwordService.hashPassword(command.password());

        // Create user aggregate
        UserId userId = UserId.generate();
        User user = User.create(userId, username, email, passwordHash);

        // Persist
        userRepository.save(user);

        // Publish domain event
        UserCreatedEvent event = UserCreatedEvent.of(
            user.getId(),
            user.getUsername().value(),
            user.getEmail().value()
        );
        eventPublisher.publish(event);

        return new CreateUserResult(user.getId(), username.value(), email.value());
    }
}
```

### Command: Authenticate

```java
package com.homewarehouse.identityaccess.application.command.authenticate;

public record AuthenticateCommand(
    String username,
    String password
) {
    public AuthenticateCommand {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Username is required");
        }

        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("Password is required");
        }
    }
}
```

```java
package com.homewarehouse.identityaccess.application.command.authenticate;

public record AuthenticationResult(
    String accessToken,
    String refreshToken,
    String expiresAt
) {}
```

```java
package com.homewarehouse.identityaccess.application.command.authenticate;

import com.homewarehouse.identityaccess.domain.model.*;
import com.homewarehouse.identityaccess.domain.repository.UserRepository;
import com.homewarehouse.identityaccess.domain.service.PasswordService;
import com.homewarehouse.identityaccess.application.port.out.TokenService;
import com.homewarehouse.identityaccess.application.port.out.AuditService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Duration;
import java.time.Instant;

@Service
public class AuthenticateHandler {

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final Duration LOCKOUT_DURATION = Duration.ofMinutes(15);

    private final UserRepository userRepository;
    private final PasswordService passwordService;
    private final TokenService tokenService;
    private final AuditService auditService;

    public AuthenticateHandler(
        UserRepository userRepository,
        PasswordService passwordService,
        TokenService tokenService,
        AuditService auditService
    ) {
        this.userRepository = userRepository;
        this.passwordService = passwordService;
        this.tokenService = tokenService;
        this.auditService = auditService;
    }

    @Transactional
    public AuthenticationResult handle(AuthenticateCommand command) {
        // Find user
        Username username = Username.of(command.username());
        User user = userRepository.findByUsername(username)
            .orElseThrow(() -> {
                auditService.recordFailedLogin(username.value(), "USER_NOT_FOUND");
                return new InvalidCredentialsException("Invalid username or password");
            });

        // Check if account is locked
        if (user.isLocked()) {
            auditService.recordFailedLogin(user.getId().toString(), "ACCOUNT_LOCKED");
            throw new AccountLockedException("Account is temporarily locked");
        }

        // Check if account is enabled
        if (!user.isEnabled()) {
            auditService.recordFailedLogin(user.getId().toString(), "ACCOUNT_DISABLED");
            throw new AccountDisabledException("Account is disabled");
        }

        // Verify password
        boolean passwordMatches = passwordService.verifyPassword(
            command.password(),
            user.getPasswordHash()
        );

        if (!passwordMatches) {
            handleFailedLogin(user);
            throw new InvalidCredentialsException("Invalid username or password");
        }

        // Reset failed attempts on successful login
        if (user.getFailedLoginAttempts() > 0) {
            user.resetFailedLoginAttempts();
            userRepository.save(user);
        }

        // Generate tokens
        String accessToken = tokenService.generateAccessToken(user);
        String refreshToken = tokenService.generateRefreshToken(user);
        Instant expiresAt = Instant.now().plus(Duration.ofMinutes(15));

        // Audit successful login
        auditService.recordSuccessfulLogin(user.getId().toString());

        return new AuthenticationResult(
            accessToken,
            refreshToken,
            expiresAt.toString()
        );
    }

    private void handleFailedLogin(User user) {
        user.incrementFailedLoginAttempts();

        if (user.getFailedLoginAttempts() >= MAX_FAILED_ATTEMPTS) {
            Instant lockUntil = Instant.now().plus(LOCKOUT_DURATION);
            user.lockAccount(lockUntil);

            auditService.recordEvent(
                user.getId().toString(),
                "ACCOUNT_LOCKED",
                "Locked due to excessive failed login attempts"
            );
        }

        userRepository.save(user);

        auditService.recordFailedLogin(
            user.getId().toString(),
            "INVALID_PASSWORD"
        );
    }
}

// Custom exceptions
class InvalidCredentialsException extends RuntimeException {
    public InvalidCredentialsException(String message) {
        super(message);
    }
}

class AccountLockedException extends RuntimeException {
    public AccountLockedException(String message) {
        super(message);
    }
}

class AccountDisabledException extends RuntimeException {
    public AccountDisabledException(String message) {
        super(message);
    }
}
```

## Infrastructure Layer

### BCrypt Password Service Implementation

```java
package com.homewarehouse.identityaccess.infrastructure.security;

import com.homewarehouse.identityaccess.domain.model.PasswordHash;
import com.homewarehouse.identityaccess.domain.service.PasswordService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import java.util.regex.Pattern;

@Service
public class BCryptPasswordService implements PasswordService {

    private static final int BCRYPT_STRENGTH = 12; // 2^12 = 4096 rounds
    private static final int MIN_PASSWORD_LENGTH = 8;
    private static final int MAX_PASSWORD_LENGTH = 128;

    private final BCryptPasswordEncoder encoder;

    public BCryptPasswordService() {
        this.encoder = new BCryptPasswordEncoder(BCRYPT_STRENGTH);
    }

    @Override
    public PasswordHash hashPassword(String plainPassword) {
        validatePasswordStrength(plainPassword);

        String hash = encoder.encode(plainPassword);
        return PasswordHash.of(hash);
    }

    @Override
    public boolean verifyPassword(String plainPassword, PasswordHash passwordHash) {
        return encoder.matches(plainPassword, passwordHash.value());
    }

    @Override
    public void validatePasswordStrength(String plainPassword) {
        if (plainPassword == null) {
            throw new IllegalArgumentException("Password cannot be null");
        }

        if (plainPassword.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalArgumentException(
                "Password must be at least " + MIN_PASSWORD_LENGTH + " characters"
            );
        }

        if (plainPassword.length() > MAX_PASSWORD_LENGTH) {
            throw new IllegalArgumentException(
                "Password cannot exceed " + MAX_PASSWORD_LENGTH + " characters"
            );
        }

        // Require at least one uppercase, one lowercase, one digit, one special char
        if (!Pattern.compile("(?=.*[a-z])").matcher(plainPassword).find()) {
            throw new IllegalArgumentException(
                "Password must contain at least one lowercase letter"
            );
        }

        if (!Pattern.compile("(?=.*[A-Z])").matcher(plainPassword).find()) {
            throw new IllegalArgumentException(
                "Password must contain at least one uppercase letter"
            );
        }

        if (!Pattern.compile("(?=.*\\d)").matcher(plainPassword).find()) {
            throw new IllegalArgumentException(
                "Password must contain at least one digit"
            );
        }

        if (!Pattern.compile("(?=.*[@#$%^&+=!])").matcher(plainPassword).find()) {
            throw new IllegalArgumentException(
                "Password must contain at least one special character (@#$%^&+=!)"
            );
        }
    }
}
```

### JWT Token Service Implementation

```java
package com.homewarehouse.identityaccess.infrastructure.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.homewarehouse.identityaccess.domain.model.User;
import com.homewarehouse.identityaccess.application.port.out.TokenService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class JwtTokenService implements TokenService {

    private final RSAPublicKey publicKey;
    private final RSAPrivateKey privateKey;

    public JwtTokenService(
        @Value("${jwt.public-key}") String publicKeyPem,
        @Value("${jwt.private-key}") String privateKeyPem
    ) throws Exception {
        this.publicKey = loadPublicKey(publicKeyPem);
        this.privateKey = loadPrivateKey(privateKeyPem);
    }

    @Override
    public String generateAccessToken(User user) {
        Instant now = Instant.now();
        Instant expiration = now.plus(Duration.ofMinutes(15));

        return JWT.create()
            .withIssuer("homewarehouse")
            .withAudience("homewarehouse-api")
            .withSubject(user.getId().toString())
            .withIssuedAt(Date.from(now))
            .withExpiresAt(Date.from(expiration))
            .withNotBefore(Date.from(now))
            .withJWTId(UUID.randomUUID().toString())
            .withClaim("username", user.getUsername().value())
            .withClaim("roles", user.getRoles().stream()
                .map(role -> role.getName())
                .collect(Collectors.toList()))
            .withClaim("permissions", user.getAllPermissions().stream()
                .map(permission -> permission.toString())
                .collect(Collectors.toList()))
            .sign(Algorithm.RSA256(publicKey, privateKey));
    }

    @Override
    public String generateRefreshToken(User user) {
        // Generate secure random token
        byte[] randomBytes = new byte[32];
        new java.security.SecureRandom().nextBytes(randomBytes);

        return Base64.getUrlEncoder().withoutPadding()
            .encodeToString(randomBytes);
    }

    private RSAPublicKey loadPublicKey(String keyPem) throws Exception {
        String publicKeyPEM = keyPem
            .replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----END PUBLIC KEY-----", "")
            .replaceAll("\\s", "");

        byte[] encoded = Base64.getDecoder().decode(publicKeyPEM);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        X509EncodedKeySpec keySpec = new X509EncodedKeySpec(encoded);

        return (RSAPublicKey) keyFactory.generatePublic(keySpec);
    }

    private RSAPrivateKey loadPrivateKey(String keyPem) throws Exception {
        String privateKeyPEM = keyPem
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replaceAll("\\s", "");

        byte[] encoded = Base64.getDecoder().decode(privateKeyPEM);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(encoded);

        return (RSAPrivateKey) keyFactory.generatePrivate(keySpec);
    }
}
```

### REST Controller

```java
package com.homewarehouse.identityaccess.infrastructure.web;

import com.homewarehouse.identityaccess.application.command.authenticate.*;
import com.homewarehouse.identityaccess.application.command.createuser.*;
import com.homewarehouse.identityaccess.infrastructure.web.dto.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthenticateHandler authenticateHandler;
    private final CreateUserHandler createUserHandler;

    public AuthController(
        AuthenticateHandler authenticateHandler,
        CreateUserHandler createUserHandler
    ) {
        this.authenticateHandler = authenticateHandler;
        this.createUserHandler = createUserHandler;
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody LoginRequest request) {
        AuthenticateCommand command = new AuthenticateCommand(
            request.username(),
            request.password()
        );

        AuthenticationResult result = authenticateHandler.handle(command);

        AuthResponse response = new AuthResponse(
            result.accessToken(),
            result.refreshToken(),
            result.expiresAt()
        );

        return ResponseEntity.ok(response);
    }

    @PostMapping("/register")
    @PreAuthorize("hasAuthority('user:create')")
    public ResponseEntity<UserResponse> register(@RequestBody CreateUserRequest request) {
        CreateUserCommand command = new CreateUserCommand(
            request.username(),
            request.email(),
            request.password()
        );

        CreateUserResult result = createUserHandler.handle(command);

        UserResponse response = new UserResponse(
            result.userId().toString(),
            result.username(),
            result.email()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
```

## Testing

### Unit Test: Value Object

```java
package com.homewarehouse.identityaccess.domain.model;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class UsernameTest {

    @Test
    void should_CreateUsername_When_Valid() {
        Username username = Username.of("john.doe");

        assertThat(username.value()).isEqualTo("john.doe");
    }

    @Test
    void should_ThrowException_When_TooShort() {
        assertThatThrownBy(() -> Username.of("ab"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("must be between");
    }

    @Test
    void should_ThrowException_When_InvalidCharacters() {
        assertThatThrownBy(() -> Username.of("user@name"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("can only contain");
    }
}
```

### Integration Test: Create User

```java
package com.homewarehouse.identityaccess.application.command.createuser;

import com.homewarehouse.identityaccess.domain.model.Username;
import com.homewarehouse.identityaccess.domain.repository.UserRepository;
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
class CreateUserHandlerIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private CreateUserHandler handler;

    @Autowired
    private UserRepository repository;

    @Test
    void should_CreateUser_When_ValidCommand() {
        CreateUserCommand command = new CreateUserCommand(
            "john.doe",
            "john@example.com",
            "SecureP@ssw0rd"
        );

        CreateUserResult result = handler.handle(command);

        assertThat(result.userId()).isNotNull();
        assertThat(result.username()).isEqualTo("john.doe");

        assertThat(repository.existsByUsername(Username.of("john.doe"))).isTrue();
    }

    @Test
    void should_ThrowException_When_UsernameExists() {
        CreateUserCommand command1 = new CreateUserCommand(
            "jane.doe",
            "jane@example.com",
            "SecureP@ssw0rd"
        );
        handler.handle(command1);

        CreateUserCommand command2 = new CreateUserCommand(
            "jane.doe",
            "different@example.com",
            "SecureP@ssw0rd"
        );

        assertThatThrownBy(() -> handler.handle(command2))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Username already exists");
    }
}
```

## Summary

This guide demonstrates complete Identity & Access module implementation with:
- Security-first design with strong password policies
- Account lockout protection against brute force
- JWT token generation with RS256 algorithm
- RBAC with flexible permission model
- Comprehensive domain model with validation
- Clean separation of concerns via hexagonal architecture
- Thorough testing strategies
