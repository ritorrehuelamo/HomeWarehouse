# HomeWarehouse - REST API Specification

## Purpose

This document defines the complete REST API for HomeWarehouse, including all endpoints, authentication requirements, request/response formats, and RBAC permissions.

## Scope

### In Scope

- All REST endpoints organized by module
- Authentication and authorization requirements per endpoint
- Request/response schemas
- Error handling and codes
- RBAC permission matrix
- Pagination and filtering conventions

### Out of Scope

- WebSocket/real-time APIs (not in v1)
- GraphQL (not used)
- Implementation details (see Architecture docs)

## API Conventions

### Base URL

```
/api/v1
```

### Content Type

- Request: `application/json`
- Response: `application/json`

### Authentication

All endpoints except `/auth/login` require a valid JWT access token:

```
Authorization: Bearer <access_token>
```

### Common Headers

| Header | Required | Description |
|--------|----------|-------------|
| Authorization | Yes* | Bearer JWT token (*except login) |
| Content-Type | Yes (POST/PUT/PATCH) | application/json |
| X-Correlation-Id | No | Client-provided correlation ID |
| Accept-Language | No | Preferred language for errors |

### Pagination

Paginated endpoints use:

| Parameter | Type | Default | Max | Description |
|-----------|------|---------|-----|-------------|
| page | integer | 0 | - | Zero-indexed page number |
| size | integer | 20 | 100 | Items per page |
| sort | string | varies | - | Field,direction (e.g., `createdAt,desc`) |

**Paginated Response Format:**
```json
{
  "content": [...],
  "page": {
    "number": 0,
    "size": 20,
    "totalElements": 150,
    "totalPages": 8
  }
}
```

### Error Response Format

```json
{
  "error": {
    "code": "ERROR_CODE",
    "message": "Human-readable error message",
    "details": [
      {
        "field": "fieldName",
        "message": "Field-specific error"
      }
    ],
    "correlationId": "uuid"
  }
}
```

### Common Error Codes

| HTTP Status | Error Code | Description |
|-------------|------------|-------------|
| 400 | VALIDATION_ERROR | Request validation failed |
| 400 | BAD_REQUEST | Malformed request |
| 401 | UNAUTHORIZED | Missing or invalid token |
| 401 | TOKEN_EXPIRED | Access token has expired |
| 403 | FORBIDDEN | Insufficient permissions |
| 404 | NOT_FOUND | Resource not found |
| 409 | CONFLICT | Resource conflict (duplicate, state) |
| 422 | BUSINESS_RULE_VIOLATION | Domain rule violated |
| 429 | RATE_LIMITED | Too many requests |
| 500 | INTERNAL_ERROR | Server error |

---

## Authentication Endpoints

### POST /api/v1/auth/login

Authenticate user and obtain tokens.

| Attribute | Value |
|-----------|-------|
| Authentication | None |
| Permission | None |
| Rate Limit | 5/minute per IP |

**Request:**
```json
{
  "username": "string",
  "password": "string"
}
```

**Response (200 OK):**
```json
{
  "accessToken": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refreshToken": "opaque-refresh-token-string",
  "expiresAt": "2024-01-15T10:45:00Z"
}
```

**Errors:**
| Status | Code | Condition |
|--------|------|-----------|
| 401 | INVALID_CREDENTIALS | Wrong username or password |
| 401 | ACCOUNT_DISABLED | User account is disabled |
| 429 | RATE_LIMITED | Too many failed attempts |

---

### POST /api/v1/auth/refresh

Refresh access token using refresh token.

| Attribute | Value |
|-----------|-------|
| Authentication | None (uses refresh token in body) |
| Permission | None |

**Request:**
```json
{
  "refreshToken": "string"
}
```

**Response (200 OK):**
```json
{
  "accessToken": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refreshToken": "new-opaque-refresh-token",
  "expiresAt": "2024-01-15T10:45:00Z"
}
```

**Errors:**
| Status | Code | Condition |
|--------|------|-----------|
| 401 | INVALID_REFRESH_TOKEN | Token not found or expired |
| 401 | TOKEN_REVOKED | Token was explicitly revoked |

---

### POST /api/v1/auth/logout

Revoke refresh token and end session.

| Attribute | Value |
|-----------|-------|
| Authentication | Bearer JWT |
| Permission | None (authenticated users) |

**Request:**
```json
{
  "refreshToken": "string"
}
```

**Response (204 No Content)**

---

## IAM Endpoints (Admin Only)

### GET /api/v1/iam/users

List all users.

| Attribute | Value |
|-----------|-------|
| Authentication | Bearer JWT |
| Permission | identity:users:read |

**Query Parameters:** `page`, `size`, `sort`, `enabled` (filter)

**Response (200 OK):**
```json
{
  "content": [
    {
      "id": "uuid",
      "username": "string",
      "email": "string",
      "enabled": true,
      "roles": ["ADMIN", "USER"],
      "createdAt": "2024-01-15T10:30:00Z"
    }
  ],
  "page": {...}
}
```

---

### GET /api/v1/iam/users/{id}

Get user by ID.

| Attribute | Value |
|-----------|-------|
| Authentication | Bearer JWT |
| Permission | identity:users:read |

**Response (200 OK):**
```json
{
  "id": "uuid",
  "username": "string",
  "email": "string",
  "enabled": true,
  "roles": ["ADMIN", "USER"],
  "createdAt": "2024-01-15T10:30:00Z",
  "updatedAt": "2024-01-15T10:30:00Z"
}
```

---

### POST /api/v1/iam/users

Create new user.

| Attribute | Value |
|-----------|-------|
| Authentication | Bearer JWT |
| Permission | identity:users:write |
| Audited | Yes |

**Request:**
```json
{
  "username": "string",
  "email": "string",
  "password": "string",
  "roleIds": ["uuid"]
}
```

**Response (201 Created):**
```json
{
  "id": "uuid",
  "username": "string",
  "email": "string",
  "enabled": true,
  "roles": ["USER"],
  "createdAt": "2024-01-15T10:30:00Z"
}
```

---

### PUT /api/v1/iam/users/{id}

Update user.

| Attribute | Value |
|-----------|-------|
| Authentication | Bearer JWT |
| Permission | identity:users:write |
| Audited | Yes |

**Request:**
```json
{
  "email": "string",
  "enabled": true
}
```

---

### POST /api/v1/iam/users/{id}/roles

Assign roles to user.

| Attribute | Value |
|-----------|-------|
| Authentication | Bearer JWT |
| Permission | identity:roles:write |
| Audited | Yes |

**Request:**
```json
{
  "roleIds": ["uuid"]
}
```

---

### DELETE /api/v1/iam/users/{id}/roles/{roleId}

Remove role from user.

| Attribute | Value |
|-----------|-------|
| Authentication | Bearer JWT |
| Permission | identity:roles:write |
| Audited | Yes |

---

### GET /api/v1/iam/roles

List all roles.

| Attribute | Value |
|-----------|-------|
| Authentication | Bearer JWT |
| Permission | identity:roles:read |

---

### POST /api/v1/iam/roles

Create new role.

| Attribute | Value |
|-----------|-------|
| Authentication | Bearer JWT |
| Permission | identity:roles:write |
| Audited | Yes |

**Request:**
```json
{
  "name": "string",
  "description": "string",
  "permissionIds": ["uuid"]
}
```

---

### PUT /api/v1/iam/roles/{id}

Update role.

| Attribute | Value |
|-----------|-------|
| Authentication | Bearer JWT |
| Permission | identity:roles:write |
| Audited | Yes |

---

### POST /api/v1/iam/roles/{id}/permissions

Assign permissions to role.

| Attribute | Value |
|-----------|-------|
| Authentication | Bearer JWT |
| Permission | identity:permissions:assign |
| Audited | Yes |

---

### GET /api/v1/iam/permissions

List all permissions.

| Attribute | Value |
|-----------|-------|
| Authentication | Bearer JWT |
| Permission | identity:permissions:read |

---

## Ledger Endpoints

### GET /api/v1/ledger/accounts

List user's accounts.

| Attribute | Value |
|-----------|-------|
| Authentication | Bearer JWT |
| Permission | ledger:accounts:read |

**Query Parameters:** `page`, `size`, `sort`, `active` (filter), `type` (filter)

**Response (200 OK):**
```json
{
  "content": [
    {
      "id": "uuid",
      "name": "Checking Account",
      "accountType": "CHECKING",
      "currency": "USD",
      "active": true,
      "createdAt": "2024-01-15T10:30:00Z"
    }
  ],
  "page": {...}
}
```

---

### POST /api/v1/ledger/accounts

Create account.

| Attribute | Value |
|-----------|-------|
| Authentication | Bearer JWT |
| Permission | ledger:accounts:write |
| Audited | Yes |

**Request:**
```json
{
  "name": "string",
  "accountType": "CHECKING",
  "currency": "USD"
}
```

---

### GET /api/v1/ledger/accounts/{id}

Get account details.

| Attribute | Value |
|-----------|-------|
| Authentication | Bearer JWT |
| Permission | ledger:accounts:read |
| Ownership | User must own account |

---

### PUT /api/v1/ledger/accounts/{id}

Update account.

| Attribute | Value |
|-----------|-------|
| Authentication | Bearer JWT |
| Permission | ledger:accounts:write |
| Ownership | User must own account |
| Audited | Yes |

---

### DELETE /api/v1/ledger/accounts/{id}

Deactivate account (soft delete).

| Attribute | Value |
|-----------|-------|
| Authentication | Bearer JWT |
| Permission | ledger:accounts:write |
| Ownership | User must own account |
| Audited | Yes |

---

### GET /api/v1/ledger/transactions

List transactions.

| Attribute | Value |
|-----------|-------|
| Authentication | Bearer JWT |
| Permission | ledger:transactions:read |

**Query Parameters:**
- `page`, `size`, `sort`
- `accountId` - Filter by account
- `categoryId` - Filter by category
- `type` - INCOME, EXPENSE, TRANSFER
- `dateFrom` - Start date (YYYY-MM-DD)
- `dateTo` - End date (YYYY-MM-DD)
- `search` - Text search in description/counterparty

**Response (200 OK):**
```json
{
  "content": [
    {
      "id": "uuid",
      "accountId": "uuid",
      "accountName": "Checking Account",
      "categoryId": "uuid",
      "categoryName": "Groceries",
      "transactionType": "EXPENSE",
      "amount": "125.50",
      "currency": "USD",
      "description": "Weekly groceries",
      "transactionDate": "2024-01-15",
      "counterparty": "Supermarket",
      "reference": null,
      "createdAt": "2024-01-15T10:30:00Z"
    }
  ],
  "page": {...}
}
```

---

### POST /api/v1/ledger/transactions

Create transaction.

| Attribute | Value |
|-----------|-------|
| Authentication | Bearer JWT |
| Permission | ledger:transactions:write |
| Audited | Yes |

**Request:**
```json
{
  "accountId": "uuid",
  "categoryId": "uuid",
  "transactionType": "EXPENSE",
  "amount": "125.50",
  "currency": "USD",
  "description": "string",
  "transactionDate": "2024-01-15",
  "counterparty": "string",
  "reference": "string",
  "idempotencyKey": "uuid"
}
```

---

### GET /api/v1/ledger/transactions/{id}

Get transaction details.

---

### PUT /api/v1/ledger/transactions/{id}

Update transaction.

---

### DELETE /api/v1/ledger/transactions/{id}

Delete transaction.

---

### GET /api/v1/ledger/categories

List categories.

| Attribute | Value |
|-----------|-------|
| Authentication | Bearer JWT |
| Permission | ledger:transactions:read |

**Query Parameters:** `type` (INCOME, EXPENSE, TRANSFER)

---

### POST /api/v1/ledger/categories

Create category.

---

### GET /api/v1/ledger/export

Export transactions.

| Attribute | Value |
|-----------|-------|
| Authentication | Bearer JWT |
| Permission | ledger:export:execute |

**Query Parameters:**
- `format` - csv, json
- `accountId` - Filter by account
- `dateFrom`, `dateTo` - Date range

**Response:** File download with appropriate Content-Type

---

## CSV Import Endpoints

### POST /api/v1/imports/csv

Upload CSV file for import.

| Attribute | Value |
|-----------|-------|
| Authentication | Bearer JWT |
| Permission | ledger:import:execute |
| Content-Type | multipart/form-data |

**Request:** Form with `file` field containing CSV

**Response (201 Created):**
```json
{
  "jobId": "uuid",
  "status": "UPLOADED",
  "fileName": "bank_statement.csv",
  "createdAt": "2024-01-15T10:30:00Z"
}
```

---

### GET /api/v1/imports

List import jobs.

| Attribute | Value |
|-----------|-------|
| Authentication | Bearer JWT |
| Permission | ledger:import:execute |

---

### GET /api/v1/imports/{jobId}

Get import job details.

---

### GET /api/v1/imports/mappings

List saved mapping templates.

---

### POST /api/v1/imports/mappings

Create mapping template.

**Request:**
```json
{
  "name": "My Bank Template",
  "bankName": "First National",
  "columnMappings": {
    "dateColumn": "Transaction Date",
    "dateFormat": "MM/dd/yyyy",
    "amountColumn": "Amount",
    "descriptionColumn": "Description",
    "referenceColumn": "Reference",
    "counterpartyColumn": "Payee",
    "signConvention": "NEGATIVE_IS_EXPENSE",
    "skipHeaderRows": 1,
    "delimiter": ","
  }
}
```

---

### POST /api/v1/imports/{jobId}/mapping

Apply mapping to import job.

**Request:**
```json
{
  "mappingId": "uuid"
}
```

---

### GET /api/v1/imports/{jobId}/preview

Preview parsed import data.

**Query Parameters:** `page`, `size`

**Response (200 OK):**
```json
{
  "jobId": "uuid",
  "status": "PREVIEW_READY",
  "totalRows": 150,
  "validRows": 145,
  "invalidRows": 3,
  "duplicateRows": 2,
  "rows": [
    {
      "rowNumber": 1,
      "status": "VALID",
      "parsedData": {
        "date": "2024-01-15",
        "amount": "125.50",
        "description": "Grocery Store",
        "reference": "TXN123"
      },
      "error": null
    },
    {
      "rowNumber": 2,
      "status": "INVALID",
      "parsedData": null,
      "error": "Invalid date format in column 'Transaction Date'"
    }
  ],
  "page": {...}
}
```

---

### POST /api/v1/imports/{jobId}/confirm

Confirm and start import.

| Attribute | Value |
|-----------|-------|
| Authentication | Bearer JWT |
| Permission | ledger:import:execute |
| Audited | Yes |

**Request:**
```json
{
  "accountId": "uuid",
  "defaultCategoryId": "uuid"
}
```

**Response (202 Accepted):**
```json
{
  "jobId": "uuid",
  "status": "PROCESSING",
  "correlationId": "uuid"
}
```

---

### DELETE /api/v1/imports/{jobId}

Cancel import job.

---

## Asset Endpoints

### GET /api/v1/assets

List assets.

| Attribute | Value |
|-----------|-------|
| Authentication | Bearer JWT |
| Permission | assets:entities:read |

**Query Parameters:**
- `page`, `size`, `sort`
- `category` - Filter by asset category
- `active` - Filter active/inactive

---

### POST /api/v1/assets

Create asset.

| Attribute | Value |
|-----------|-------|
| Authentication | Bearer JWT |
| Permission | assets:entities:write |
| Audited | Yes |

**Request:**
```json
{
  "name": "2020 Toyota Camry",
  "assetCategory": "VEHICLE",
  "description": "Family car",
  "currency": "USD"
}
```

---

### GET /api/v1/assets/{id}

Get asset with latest valuation.

---

### PUT /api/v1/assets/{id}

Update asset.

---

### DELETE /api/v1/assets/{id}

Deactivate asset.

---

### GET /api/v1/assets/{id}/valuations

List valuations for asset.

---

### POST /api/v1/assets/{id}/valuations

Record valuation.

| Attribute | Value |
|-----------|-------|
| Authentication | Bearer JWT |
| Permission | assets:valuations:write |
| Audited | Yes |

**Request:**
```json
{
  "valuationDate": "2024-01-15",
  "value": "25000.00",
  "source": "MANUAL",
  "notes": "Based on KBB estimate",
  "idempotencyKey": "uuid"
}
```

---

### GET /api/v1/assets/net-worth

Get current net worth.

| Attribute | Value |
|-----------|-------|
| Authentication | Bearer JWT |
| Permission | assets:entities:read |

**Response (200 OK):**
```json
{
  "asOf": "2024-01-15T10:30:00Z",
  "currency": "USD",
  "totalAssets": "150000.00",
  "totalLiabilities": "75000.00",
  "netWorth": "75000.00",
  "breakdown": [
    {
      "category": "REAL_ESTATE",
      "value": "100000.00",
      "assetType": "ASSET"
    },
    {
      "category": "MORTGAGE",
      "value": "70000.00",
      "assetType": "LIABILITY"
    }
  ]
}
```

---

### GET /api/v1/assets/net-worth/history

Get net worth history.

**Query Parameters:**
- `period` - MONTHLY, QUARTERLY, YEARLY
- `from`, `to` - Date range

---

## Inventory Endpoints

### GET /api/v1/inventory/items

List inventory items.

| Attribute | Value |
|-----------|-------|
| Authentication | Bearer JWT |
| Permission | inventory:items:read |

**Query Parameters:**
- `page`, `size`, `sort`
- `category` - Filter by category
- `perishable` - Filter perishable only
- `search` - Text search

---

### POST /api/v1/inventory/items

Create inventory item.

| Attribute | Value |
|-----------|-------|
| Authentication | Bearer JWT |
| Permission | inventory:items:write |
| Audited | Yes |

**Request:**
```json
{
  "name": "Canned Tomatoes 400g",
  "description": "Diced tomatoes in juice",
  "category": "Canned Goods",
  "isPerishable": true,
  "defaultExpiryDays": 730
}
```

---

### GET /api/v1/inventory/items/{id}

Get item with unit count.

---

### PUT /api/v1/inventory/items/{id}

Update item.

---

### DELETE /api/v1/inventory/items/{id}

Delete item (only if no units exist).

---

### GET /api/v1/inventory/items/{id}/units

List units of item.

---

### GET /api/v1/inventory/units

List all units.

| Attribute | Value |
|-----------|-------|
| Authentication | Bearer JWT |
| Permission | inventory:units:read |

**Query Parameters:**
- `page`, `size`, `sort`
- `itemId` - Filter by item
- `locationId` - Filter by location
- `status` - AVAILABLE, CONSUMED, EXPIRED, DISPOSED
- `expiringBefore` - Date filter for expiring soon

---

### POST /api/v1/inventory/units

Add units (batch).

| Attribute | Value |
|-----------|-------|
| Authentication | Bearer JWT |
| Permission | inventory:units:write |
| Audited | Yes |

**Request:**
```json
{
  "itemId": "uuid",
  "locationId": "uuid",
  "quantity": 5,
  "purchaseDate": "2024-01-15",
  "bestBefore": "2025-01-15",
  "expiresAt": "2025-06-15",
  "purchasePrice": "2.50",
  "currency": "USD",
  "idempotencyKey": "uuid"
}
```

**Response (201 Created):**
```json
{
  "createdUnits": [
    {"id": "uuid", "itemId": "uuid", "locationId": "uuid"},
    {"id": "uuid", "itemId": "uuid", "locationId": "uuid"}
  ],
  "count": 5
}
```

---

### POST /api/v1/inventory/units/{id}/consume

Consume a unit.

| Attribute | Value |
|-----------|-------|
| Authentication | Bearer JWT |
| Permission | inventory:units:write |
| Audited | Yes |

**Response (200 OK):**
```json
{
  "id": "uuid",
  "status": "CONSUMED",
  "updatedAt": "2024-01-15T10:30:00Z"
}
```

---

### POST /api/v1/inventory/units/{id}/move

Move unit to different location.

| Attribute | Value |
|-----------|-------|
| Authentication | Bearer JWT |
| Permission | inventory:units:write |
| Audited | Yes |

**Request:**
```json
{
  "newLocationId": "uuid"
}
```

---

### POST /api/v1/inventory/units/{id}/dispose

Dispose of a unit.

---

### GET /api/v1/inventory/locations

List locations (flat or tree).

| Attribute | Value |
|-----------|-------|
| Authentication | Bearer JWT |
| Permission | inventory:locations:read |

**Query Parameters:**
- `tree` - If true, return as nested tree

---

### POST /api/v1/inventory/locations

Create location.

| Attribute | Value |
|-----------|-------|
| Authentication | Bearer JWT |
| Permission | inventory:locations:write |
| Audited | Yes |

**Request:**
```json
{
  "name": "Shelf 2",
  "parentId": "uuid"
}
```

---

### GET /api/v1/inventory/locations/{id}

Get location with contents.

---

### GET /api/v1/inventory/locations/{id}/units

List units at location.

---

### PUT /api/v1/inventory/locations/{id}

Update location.

---

### DELETE /api/v1/inventory/locations/{id}

Delete location (only if empty).

---

### GET /api/v1/inventory/alerts

Get expiry alerts.

| Attribute | Value |
|-----------|-------|
| Authentication | Bearer JWT |
| Permission | inventory:units:read |

**Query Parameters:**
- `daysAhead` - Days to look ahead (default: 7)
- `includeExpired` - Include already expired (default: true)

**Response (200 OK):**
```json
{
  "expiringSoon": [
    {
      "unitId": "uuid",
      "itemId": "uuid",
      "itemName": "Milk 1L",
      "locationPath": "Kitchen > Refrigerator",
      "expiresAt": "2024-01-17",
      "daysRemaining": 2
    }
  ],
  "expired": [
    {
      "unitId": "uuid",
      "itemId": "uuid",
      "itemName": "Yogurt",
      "locationPath": "Kitchen > Refrigerator",
      "expiresAt": "2024-01-14",
      "daysOverdue": 1
    }
  ]
}
```

---

## Purchase Registration Endpoint

### POST /api/v1/purchases

Register purchase (creates ledger transaction + inventory units).

| Attribute | Value |
|-----------|-------|
| Authentication | Bearer JWT |
| Permission | ledger:transactions:write, inventory:units:write |
| Audited | Yes |
| Workflow | PurchaseRegistrationWorkflow |

**Request:**
```json
{
  "accountId": "uuid",
  "transactionDate": "2024-01-15",
  "totalAmount": "25.00",
  "currency": "USD",
  "counterparty": "Grocery Store",
  "description": "Weekly groceries",
  "categoryId": "uuid",
  "items": [
    {
      "itemId": "uuid",
      "locationId": "uuid",
      "quantity": 2,
      "unitPrice": "3.50",
      "bestBefore": "2025-01-15",
      "expiresAt": "2025-06-15"
    },
    {
      "itemId": "uuid",
      "locationId": "uuid",
      "quantity": 1,
      "unitPrice": "18.00"
    }
  ],
  "idempotencyKey": "uuid"
}
```

**Response (202 Accepted):**
```json
{
  "correlationId": "uuid",
  "status": "PROCESSING",
  "message": "Purchase registration initiated"
}
```

**Response (201 Created - when workflow completes synchronously):**
```json
{
  "correlationId": "uuid",
  "transactionId": "uuid",
  "unitIds": ["uuid", "uuid", "uuid"],
  "status": "COMPLETED"
}
```

---

## Notification Endpoints

### GET /api/v1/notifications

List notifications.

| Attribute | Value |
|-----------|-------|
| Authentication | Bearer JWT |
| Permission | notifications:read |

**Query Parameters:**
- `page`, `size`, `sort`
- `status` - UNREAD, READ, DISMISSED
- `type` - Notification type filter

---

### GET /api/v1/notifications/unread-count

Get unread notification count.

**Response (200 OK):**
```json
{
  "count": 5
}
```

---

### POST /api/v1/notifications/{id}/read

Mark notification as read.

---

### POST /api/v1/notifications/{id}/dismiss

Dismiss notification.

---

### POST /api/v1/notifications/dismiss-all

Dismiss all notifications.

---

## Audit Endpoints

### GET /api/v1/audit

Query audit logs (admin only).

| Attribute | Value |
|-----------|-------|
| Authentication | Bearer JWT |
| Permission | audit:logs:read |

**Query Parameters:**
- `page`, `size`, `sort`
- `userId` - Filter by user
- `action` - Filter by action type
- `entityType` - Filter by entity type
- `entityId` - Filter by entity ID
- `from`, `to` - Date/time range
- `correlationId` - Filter by correlation ID

**Response (200 OK):**
```json
{
  "content": [
    {
      "id": "uuid",
      "userId": "uuid",
      "username": "admin",
      "action": "CREATE",
      "entityType": "ledger_transaction",
      "entityId": "uuid",
      "beforeState": null,
      "afterState": {"amount": "125.50", "...": "..."},
      "ipAddress": "192.168.1.100",
      "correlationId": "uuid",
      "occurredAt": "2024-01-15T10:30:00Z"
    }
  ],
  "page": {...}
}
```

---

## Health & Metrics Endpoints

### GET /healthz

Health check endpoint.

| Attribute | Value |
|-----------|-------|
| Authentication | None |

**Response (200 OK):**
```json
{
  "status": "UP",
  "components": {
    "db": {"status": "UP"},
    "redis": {"status": "UP"},
    "rabbitmq": {"status": "UP"},
    "temporal": {"status": "UP"}
  }
}
```

---

### GET /metrics

Prometheus metrics endpoint.

| Attribute | Value |
|-----------|-------|
| Authentication | None (internal network only) |
| Content-Type | text/plain |

---

## RBAC Permission Matrix

| Endpoint Pattern | ADMIN | USER | READONLY |
|------------------|-------|------|----------|
| POST /auth/* | - | - | - |
| GET /iam/* | Y | - | - |
| POST/PUT/DELETE /iam/* | Y | - | - |
| GET /ledger/* | Y | Y | Y |
| POST/PUT/DELETE /ledger/* | Y | Y | - |
| GET /assets/* | Y | Y | Y |
| POST/PUT/DELETE /assets/* | Y | Y | - |
| GET /inventory/* | Y | Y | Y |
| POST/PUT/DELETE /inventory/* | Y | Y | - |
| POST /purchases | Y | Y | - |
| GET /notifications/* | Y | Y | Y |
| POST /notifications/* | Y | Y | Y |
| GET /audit | Y | - | - |

---

## Related Documents

- [Architecture](../architecture/01-architecture.md) - API implementation structure
- [Security](../security/02-security.md) - Authentication and authorization details
- [Data Model](../database/03-data-model.md) - Underlying data structures
- [Workflows](../workflows/05-workflows-temporal.md) - Async workflow endpoints
