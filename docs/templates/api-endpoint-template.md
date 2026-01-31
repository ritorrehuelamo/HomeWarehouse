# API Endpoint: [Endpoint Name]

## Endpoint

`[METHOD] /api/v1/[path]`

## Method

`GET` | `POST` | `PUT` | `PATCH` | `DELETE`

## Description

Brief description of what this endpoint does.

## Authentication

| Requirement | Value |
|-------------|-------|
| Authentication Required | Yes / No |
| Token Type | Bearer JWT |
| Token Location | Authorization Header |

## Authorization (RBAC)

| Role | Permission | Access |
|------|------------|--------|
| ADMIN | `resource:action` | Full access |
| USER | `resource:read` | Read-only |

### Required Permissions

- `permission.name` - Description of what this permission grants

## Request

### Headers

| Header | Required | Description |
|--------|----------|-------------|
| Authorization | Yes | Bearer {accessToken} |
| Content-Type | Yes (POST/PUT/PATCH) | application/json |
| X-Correlation-Id | No | Optional correlation ID for tracing |

### Path Parameters

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| id | UUID | Yes | Resource identifier |

### Query Parameters

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| page | integer | No | 0 | Page number (0-indexed) |
| size | integer | No | 20 | Page size (max 100) |
| sort | string | No | createdAt,desc | Sort field and direction |

### Request Body

```json
{
  "field1": "string",
  "field2": 123,
  "nested": {
    "nestedField": "value"
  }
}
```

#### Field Definitions

| Field | Type | Required | Constraints | Description |
|-------|------|----------|-------------|-------------|
| field1 | string | Yes | max 255 chars | Description |
| field2 | integer | No | min 0, max 1000 | Description |
| nested.nestedField | string | Yes | - | Description |

## Response

### Success Response

**Status Code:** `200 OK` | `201 Created` | `204 No Content`

```json
{
  "id": "uuid",
  "field1": "string",
  "createdAt": "2024-01-15T10:30:00Z",
  "updatedAt": "2024-01-15T10:30:00Z"
}
```

#### Response Field Definitions

| Field | Type | Description |
|-------|------|-------------|
| id | UUID | Unique identifier |
| field1 | string | Description |
| createdAt | ISO-8601 | Creation timestamp |
| updatedAt | ISO-8601 | Last update timestamp |

### Paginated Response (if applicable)

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

## Errors

### Error Response Format

```json
{
  "error": {
    "code": "ERROR_CODE",
    "message": "Human-readable error message",
    "details": [...],
    "correlationId": "uuid"
  }
}
```

### Possible Errors

| Status Code | Error Code | Description | Resolution |
|-------------|------------|-------------|------------|
| 400 | VALIDATION_ERROR | Invalid request body | Check request body against schema |
| 401 | UNAUTHORIZED | Missing or invalid token | Provide valid access token |
| 403 | FORBIDDEN | Insufficient permissions | Ensure user has required permissions |
| 404 | NOT_FOUND | Resource not found | Verify resource ID exists |
| 409 | CONFLICT | Resource conflict | Check for duplicate or state conflict |
| 422 | UNPROCESSABLE_ENTITY | Business rule violation | Review business logic constraints |
| 500 | INTERNAL_ERROR | Server error | Contact support with correlationId |

## Examples

### Example 1: Successful Request

**Request:**

```bash
curl -X POST 'https://api.example.com/api/v1/resource' \
  -H 'Authorization: Bearer eyJhbGc...' \
  -H 'Content-Type: application/json' \
  -d '{
    "field1": "example value",
    "field2": 42
  }'
```

**Response:**

```json
{
  "id": "123e4567-e89b-12d3-a456-426614174000",
  "field1": "example value",
  "field2": 42,
  "createdAt": "2024-01-15T10:30:00Z",
  "updatedAt": "2024-01-15T10:30:00Z"
}
```

### Example 2: Error Response

**Request with validation error:**

```bash
curl -X POST 'https://api.example.com/api/v1/resource' \
  -H 'Authorization: Bearer eyJhbGc...' \
  -H 'Content-Type: application/json' \
  -d '{
    "field1": ""
  }'
```

**Response:**

```json
{
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Request validation failed",
    "details": [
      {
        "field": "field1",
        "message": "must not be empty"
      }
    ],
    "correlationId": "abc123-def456"
  }
}
```

## Rate Limiting

| Limit Type | Value | Window |
|------------|-------|--------|
| Requests per user | 100 | 1 minute |
| Requests per IP | 1000 | 1 minute |

## Audit

| Audited | Event Type | Details |
|---------|------------|---------|
| Yes / No | RESOURCE_CREATED | Records who, when, what was created |

## Notes

- Additional implementation notes
- Known limitations
- Future considerations

## Related Endpoints

- [GET /api/v1/resource/{id}](./get-resource.md) - Retrieve single resource
- [PUT /api/v1/resource/{id}](./update-resource.md) - Update resource

## Changelog

| Version | Date | Changes |
|---------|------|---------|
| 1.0.0 | YYYY-MM-DD | Initial version |
