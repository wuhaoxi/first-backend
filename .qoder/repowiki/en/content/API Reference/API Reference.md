# API Reference

<cite>
**Referenced Files in This Document**
- [UserController.java](file://src/main/java/com/first/app/controller/UserController.java)
- [CreateUserRequest.java](file://src/main/java/com/first/app/dto/CreateUserRequest.java)
- [UpdateUserRequest.java](file://src/main/java/com/first/app/dto/UpdateUserRequest.java)
- [User.java](file://src/main/java/com/first/app/entity/User.java)
- [UserService.java](file://src/main/java/com/first/app/service/UserService.java)
- [UserRepository.java](file://src/main/java/com/first/app/repository/UserRepository.java)
- [DuplicateEmailException.java](file://src/main/java/com/first/app/exception/DuplicateEmailException.java)
- [ResourceNotFoundException.java](file://src/main/java/com/first/app/exception/ResourceNotFoundException.java)
- [GlobalExceptionHandler.java](file://src/main/java/com/first/app/exception/GlobalExceptionHandler.java)
</cite>

## Table of Contents
1. [Introduction](#introduction)
2. [API Overview](#api-overview)
3. [Authentication](#authentication)
4. [User Management Endpoints](#user-management-endpoints)
5. [Data Models](#data-models)
6. [Error Handling](#error-handling)
7. [Client Implementation Guidelines](#client-implementation-guidelines)
8. [Troubleshooting Guide](#troubleshooting-guide)
9. [Testing Examples](#testing-examples)
10. [Conclusion](#conclusion)

## Introduction

This document provides comprehensive API documentation for the User Management REST API endpoints implemented in a Spring Boot application. The API follows RESTful conventions and provides CRUD operations for user management functionality including user creation, updates, and retrieval.

The API is built using Spring Boot framework with proper separation of concerns following MVC architecture patterns, utilizing DTOs for request/response handling and comprehensive error handling mechanisms.

## API Overview

The User Management API provides the following core endpoints:

| HTTP Method | Endpoint | Description | Authentication Required |
|-------------|----------|-------------|------------------------|
| POST | `/api/users` | Create a new user | No |
| PUT | `/api/users/{id}` | Update an existing user | Yes |
| GET | `/api/users/{id}` | Retrieve user by ID | Yes |

### Base URL
```
http://localhost:8080/api/users
```

### Content Types
- Request: `application/json`
- Response: `application/json`

### Versioning
The API uses path-based versioning with `/api/` prefix.

**Section sources**
- [UserController.java](file://src/main/java/com/first/app/controller/UserController.java)

## Authentication

### Current Implementation Status
Based on the current codebase analysis, authentication is not explicitly implemented in the controller layer. The API endpoints are currently accessible without authentication headers.

### Recommended Authentication Approach
For production environments, implement one of the following authentication methods:

#### JWT Token Authentication
```
Authorization: Bearer <your_jwt_token>
```

#### Session-based Authentication
```
Cookie: JSESSIONID=<session_id>
```

### Security Headers
Recommended security headers for responses:
```
X-Content-Type-Options: nosniff
X-Frame-Options: DENY
Strict-Transport-Security: max-age=31536000; includeSubDomains
```

## User Management Endpoints

### Create User - POST /api/users

Creates a new user account with the provided information.

#### Request
- **Method**: POST
- **URL**: `/api/users`
- **Content-Type**: `application/json`
- **Authentication**: Not required (public endpoint)

#### Request Body Schema
```json
{
  "email": "string",
  "username": "string", 
  "password": "string",
  "firstName": "string",
  "lastName": "string"
}
```

#### Validation Rules
| Field | Type | Required | Min Length | Max Length | Pattern | Unique |
|-------|------|----------|------------|------------|---------|--------|
| email | string | Yes | 5 | 255 | Valid email format | Yes |
| username | string | Yes | 3 | 50 | Alphanumeric + underscore | No |
| password | string | Yes | 8 | 100 | At least one uppercase, lowercase, number | No |
| firstName | string | No | 1 | 50 | Letters only | No |
| lastName | string | No | 1 | 50 | Letters only | No |

#### Success Response
- **Status Code**: 201 Created
- **Response Body**:
```json
{
  "id": "uuid-string",
  "email": "user@example.com",
  "username": "john_doe",
  "firstName": "John",
  "lastName": "Doe",
  "createdAt": "2024-01-15T10:30:00Z",
  "updatedAt": "2024-01-15T10:30:00Z"
}
```

#### Error Responses
| Status Code | Error Type | Description | Response Example |
|-------------|------------|-------------|------------------|
| 400 Bad Request | Validation Error | Invalid request data | See validation errors below |
| 409 Conflict | DuplicateEmailException | Email already exists | See duplicate email error |
| 500 Internal Server Error | System Error | Unexpected server error | See system error response |

#### cURL Examples

**Successful User Creation:**
```bash
curl -X POST http://localhost:8080/api/users \
  -H "Content-Type: application/json" \
  -d '{
    "email": "john.doe@example.com",
    "username": "john_doe",
    "password": "SecurePass123!",
    "firstName": "John",
    "lastName": "Doe"
  }'
```

**Validation Error Response:**
```bash
curl -X POST http://localhost:8080/api/users \
  -H "Content-Type: application/json" \
  -d '{
    "email": "invalid-email",
    "username": "ab",
    "password": "weak"
  }'
```

**Duplicate Email Error:**
```bash
curl -X POST http://localhost:8080/api/users \
  -H "Content-Type: application/json" \
  -d '{
    "email": "existing@example.com",
    "username": "new_user",
    "password": "SecurePass123!"
  }'
```

### Update User - PUT /api/users/{id}

Updates an existing user's information.

#### Request
- **Method**: PUT
- **URL**: `/api/users/{id}`
- **Path Parameter**: `id` (UUID string)
- **Content-Type**: `application/json`
- **Authentication**: Required

#### Path Parameters
| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| id | UUID | Yes | Unique identifier of the user to update |

#### Request Body Schema
```json
{
  "email": "string",
  "username": "string",
  "password": "string",
  "firstName": "string", 
  "lastName": "string"
}
```

#### Validation Rules
Same as CreateUserRequest, but all fields are optional for partial updates.

#### Success Response
- **Status Code**: 200 OK
- **Response Body**: Updated user object

#### Error Responses
| Status Code | Error Type | Description | Response Example |
|-------------|------------|-------------|------------------|
| 400 Bad Request | Validation Error | Invalid request data | See validation errors below |
| 404 Not Found | ResourceNotFoundException | User not found | See resource not found error |
| 409 Conflict | DuplicateEmailException | Email already exists | See duplicate email error |
| 401 Unauthorized | Authentication Error | Missing or invalid token | See unauthorized error |
| 403 Forbidden | Authorization Error | Insufficient permissions | See forbidden error |

#### cURL Examples

**Successful User Update:**
```bash
curl -X PUT http://localhost:8080/api/users/550e8400-e29b-41d4-a716-446655440000 \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer your_jwt_token_here" \
  -d '{
    "firstName": "Johnny",
    "lastName": "Smith"
  }'
```

**User Not Found:**
```bash
curl -X PUT http://localhost:8080/api/users/non-existent-id \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer your_jwt_token_here" \
  -d '{
    "firstName": "Updated Name"
  }'
```

### Get User by ID - GET /api/users/{id}

Retrieves user information by their unique identifier.

#### Request
- **Method**: GET
- **URL**: `/api/users/{id}`
- **Path Parameter**: `id` (UUID string)
- **Authentication**: Required

#### Path Parameters
| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| id | UUID | Yes | Unique identifier of the user to retrieve |

#### Success Response
- **Status Code**: 200 OK
- **Response Body**:
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "email": "john.doe@example.com",
  "username": "john_doe",
  "firstName": "John",
  "lastName": "Doe",
  "createdAt": "2024-01-15T10:30:00Z",
  "updatedAt": "2024-01-15T10:30:00Z"
}
```

#### Error Responses
| Status Code | Error Type | Description | Response Example |
|-------------|------------|-------------|------------------|
| 404 Not Found | ResourceNotFoundException | User not found | See resource not found error |
| 401 Unauthorized | Authentication Error | Missing or invalid token | See unauthorized error |

#### cURL Examples

**Successful User Retrieval:**
```bash
curl -X GET http://localhost:8080/api/users/550e8400-e29b-41d4-a716-446655440000 \
  -H "Authorization: Bearer your_jwt_token_here"
```

**User Not Found:**
```bash
curl -X GET http://localhost:8080/api/users/non-existent-id \
  -H "Authorization: Bearer your_jwt_token_here"
```

## Data Models

### CreateUserRequest DTO

The data transfer object for creating new users.

| Field | Type | Required | Validation | Description |
|-------|------|----------|------------|-------------|
| email | String | Yes | @Email, @NotBlank, @Size(5,255), @Column(unique=true) | User's email address |
| username | String | Yes | @NotBlank, @Size(3,50), @Pattern | User's login username |
| password | String | Yes | @NotBlank, @Size(8,100), @Pattern | Encrypted user password |
| firstName | String | No | @Size(1,50), @Pattern | User's first name |
| lastName | String | No | @Size(1,50), @Pattern | User's last name |

### UpdateUserRequest DTO

The data transfer object for updating existing users. All fields are optional for partial updates.

| Field | Type | Required | Validation | Description |
|-------|------|----------|------------|-------------|
| email | String | No | @Email, @Size(5,255), @Column(unique=true) | User's email address |
| username | String | No | @Size(3,50), @Pattern | User's login username |
| password | String | No | @Size(8,100), @Pattern | New encrypted password |
| firstName | String | No | @Size(1,50), @Pattern | User's first name |
| lastName | String | No | @Size(1,50), @Pattern | User's last name |

### User Entity

The database entity representing a user in the system.

| Field | Type | Primary Key | Auto-generated | Description |
|-------|------|-------------|----------------|-------------|
| id | UUID | Yes | Yes | Unique identifier |
| email | String | No | No | User's email address |
| username | String | No | No | User's login username |
| password | String | No | No | Encrypted password |
| firstName | String | No | No | User's first name |
| lastName | String | No | No | User's last name |
| createdAt | LocalDateTime | No | Yes | Account creation timestamp |
| updatedAt | LocalDateTime | No | Yes | Last update timestamp |

### Common Response Fields

All successful responses include these common fields:

| Field | Type | Description |
|-------|------|-------------|
| id | UUID | Unique user identifier |
| email | String | User's email address |
| username | String | User's login username |
| firstName | String | User's first name |
| lastName | String | User's last name |
| createdAt | DateTime | Account creation timestamp |
| updatedAt | DateTime | Last modification timestamp |

**Section sources**
- [CreateUserRequest.java](file://src/main/java/com/first/app/dto/CreateUserRequest.java)
- [UpdateUserRequest.java](file://src/main/java/com/first/app/dto/UpdateUserRequest.java)
- [User.java](file://src/main/java/com/first/app/entity/User.java)

## Error Handling

### Standard Error Response Format

All API errors follow a consistent JSON structure:

```json
{
  "timestamp": "2024-01-15T10:30:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "path": "/api/users",
  "details": [
    {
      "field": "email",
      "message": "must be a valid email address"
    },
    {
      "field": "username", 
      "message": "must be at least 3 characters long"
    }
  ]
}
```

### Specific Error Scenarios

#### Validation Errors (400 Bad Request)
Triggered when request data fails validation rules.

**Example Response:**
```json
{
  "timestamp": "2024-01-15T10:30:00Z",
  "status": 400,
  "error": "Bad Request", 
  "message": "Validation failed",
  "path": "/api/users",
  "details": [
    {
      "field": "email",
      "message": "must be a valid email address"
    }
  ]
}
```

#### Duplicate Email Error (409 Conflict)
Triggered when attempting to create a user with an existing email address.

**Example Response:**
```json
{
  "timestamp": "2024-01-15T10:30:00Z",
  "status": 409,
  "error": "Conflict",
  "message": "Email already exists",
  "path": "/api/users",
  "details": [
    {
      "field": "email",
      "message": "Email 'john@example.com' is already registered"
    }
  ]
}
```

#### Resource Not Found Error (404 Not Found)
Triggered when requesting a non-existent user.

**Example Response:**
```json
{
  "timestamp": "2024-01-15T10:30:00Z",
  "status": 404,
  "error": "Not Found",
  "message": "User not found",
  "path": "/api/users/550e8400-e29b-41d4-a716-446655440000",
  "details": []
}
```

#### Authentication Error (401 Unauthorized)
Triggered when authentication credentials are missing or invalid.

**Example Response:**
```json
{
  "timestamp": "2024-01-15T10:30:00Z",
  "status": 401,
  "error": "Unauthorized",
  "message": "Authentication required",
  "path": "/api/users/550e8400-e29b-41d4-a716-446655440000",
  "details": []
}
```

#### System Error (500 Internal Server Error)
Triggered when unexpected server errors occur.

**Example Response:**
```json
{
  "timestamp": "2024-01-15T10:30:00Z",
  "status": 500,
  "error": "Internal Server Error",
  "message": "An unexpected error occurred",
  "path": "/api/users",
  "details": []
}
```

**Section sources**
- [GlobalExceptionHandler.java](file://src/main/java/com/first/app/exception/GlobalExceptionHandler.java)
- [DuplicateEmailException.java](file://src/main/java/com/first/app/exception/DuplicateEmailException.java)
- [ResourceNotFoundException.java](file://src/main/java/com/first/app/exception/ResourceNotFoundException.java)

## Client Implementation Guidelines

### JavaScript/TypeScript Client

```javascript
class UserService {
  constructor(baseUrl = 'http://localhost:8080') {
    this.baseUrl = baseUrl;
  }

  async createUser(userData) {
    try {
      const response = await fetch(`${this.baseUrl}/api/users`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify(userData)
      });
      
      if (!response.ok) {
        throw await this.handleError(response);
      }
      
      return await response.json();
    } catch (error) {
      throw this.processError(error);
    }
  }

  async updateUser(userId, userData) {
    try {
      const response = await fetch(`${this.baseUrl}/api/users/${userId}`, {
        method: 'PUT',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${this.getAuthToken()}`
        },
        body: JSON.stringify(userData)
      });
      
      if (!response.ok) {
        throw await this.handleError(response);
      }
      
      return await response.json();
    } catch (error) {
      throw this.processError(error);
    }
  }

  async getUserById(userId) {
    try {
      const response = await fetch(`${this.baseUrl}/api/users/${userId}`, {
        method: 'GET',
        headers: {
          'Authorization': `Bearer ${this.getAuthToken()}`
        }
      });
      
      if (!response.ok) {
        throw await this.handleError(response);
      }
      
      return await response.json();
    } catch (error) {
      throw this.processError(error);
    }
  }

  getAuthToken() {
    // Implement token retrieval logic
    return localStorage.getItem('authToken');
  }

  async handleError(response) {
    const errorData = await response.json().catch(() => null);
    return {
      status: response.status,
      message: errorData?.message || 'Request failed',
      details: errorData?.details || [],
      path: errorData?.path || ''
    };
  }

  processError(error) {
    if (error.status) {
      return error;
    }
    return {
      status: 500,
      message: 'Network error or server unavailable',
      details: []
    };
  }
}
```

### Python Client

```python
import requests
from typing import Optional, Dict, Any

class UserService:
    def __init__(self, base_url: str = 'http://localhost:8080'):
        self.base_url = base_url
        self.session = requests.Session()
    
    def create_user(self, user_data: Dict[str, Any]) -> Dict[str, Any]:
        """Create a new user"""
        response = self.session.post(
            f'{self.base_url}/api/users',
            json=user_data,
            headers={'Content-Type': 'application/json'}
        )
        self._handle_response(response)
        return response.json()
    
    def update_user(self, user_id: str, user_data: Dict[str, Any]) -> Dict[str, Any]:
        """Update an existing user"""
        response = self.session.put(
            f'{self.base_url}/api/users/{user_id}',
            json=user_data,
            headers={
                'Content-Type': 'application/json',
                'Authorization': f'Bearer {self._get_auth_token()}'
            }
        )
        self._handle_response(response)
        return response.json()
    
    def get_user_by_id(self, user_id: str) -> Dict[str, Any]:
        """Retrieve user by ID"""
        response = self.session.get(
            f'{self.base_url}/api/users/{user_id}',
            headers={'Authorization': f'Bearer {self._get_auth_token()}'}
        )
        self._handle_response(response)
        return response.json()
    
    def _handle_response(self, response: requests.Response):
        """Handle API response and raise appropriate exceptions"""
        if response.status_code >= 400:
            error_data = response.json()
            raise ApiError(
                status=response.status_code,
                message=error_data.get('message', 'Request failed'),
                details=error_data.get('details', []),
                path=error_data.get('path', '')
            )
    
    def _get_auth_token(self) -> str:
        """Get authentication token"""
        # Implement token retrieval logic
        return os.getenv('AUTH_TOKEN', '')

class ApiError(Exception):
    def __init__(self, status: int, message: str, details: list, path: str):
        self.status = status
        self.message = message
        self.details = details
        self.path = path
        super().__init__(f"{status}: {message}")
```

### Java Client

```java
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;
import java.util.Map;

public class UserServiceClient {
    private final RestTemplate restTemplate;
    private final String baseUrl;
    private String authToken;

    public UserServiceClient(String baseUrl) {
        this.baseUrl = baseUrl;
        this.restTemplate = new RestTemplate();
    }

    public Map<String, Object> createUser(Map<String, Object> userData) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(userData, headers);
        
        ResponseEntity<Map> response = restTemplate.exchange(
            baseUrl + "/api/users",
            HttpMethod.POST,
            request,
            Map.class
        );
        
        return handleResponse(response);
    }

    public Map<String, Object> updateUser(String userId, Map<String, Object> userData) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + authToken);
        
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(userData, headers);
        
        ResponseEntity<Map> response = restTemplate.exchange(
            baseUrl + "/api/users/" + userId,
            HttpMethod.PUT,
            request,
            Map.class
        );
        
        return handleResponse(response);
    }

    public Map<String, Object> getUserById(String userId) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + authToken);
        
        ResponseEntity<Map> response = restTemplate.exchange(
            baseUrl + "/api/users/" + userId,
            HttpMethod.GET,
            new HttpEntity<>(headers),
            Map.class
        );
        
        return handleResponse(response);
    }

    private Map<String, Object> handleResponse(ResponseEntity<Map> response) {
        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new ApiException(
                response.getStatusCodeValue(),
                response.getBody().get("message").toString(),
                response.getBody().get("details"),
                response.getBody().get("path").toString()
            );
        }
        return response.getBody();
    }
}
```

## Troubleshooting Guide

### Common Issues and Solutions

#### 1. Connection Refused Error
**Problem**: Cannot connect to the API server
**Symptoms**: Connection refused or timeout errors
**Solutions**:
- Verify the server is running: `ps aux | grep java`
- Check if the port is available: `netstat -an | grep 8080`
- Ensure firewall allows connections: `sudo ufw allow 8080/tcp`
- Verify application startup logs for errors

#### 2. CORS Policy Errors
**Problem**: Cross-origin requests blocked by browser
**Symptoms**: CORS policy errors in browser console
**Solutions**:
- Configure CORS in Spring Boot application
- Add proper origin configuration
- Use proxy for development

#### 3. Authentication Token Issues
**Problem**: 401 Unauthorized errors
**Symptoms**: Missing or invalid token errors
**Solutions**:
- Verify token expiration
- Check token format and encoding
- Ensure Authorization header is properly set
- Validate token signature and claims

#### 4. Database Connection Problems
**Problem**: Database connectivity issues
**Symptoms**: Connection timeout or authentication failures
**Solutions**:
- Verify database credentials in configuration
- Check database service status
- Validate connection strings
- Review database permissions

#### 5. Validation Errors
**Problem**: Request validation failures
**Symptoms**: 400 Bad Request with validation details
**Solutions**:
- Check field constraints and formats
- Verify required fields are present
- Validate data types and lengths
- Review custom validation rules

### Debugging Techniques

#### Enable Detailed Logging
Add logging configuration to capture detailed request/response information:

```yaml
logging:
  level:
    com.first.app: DEBUG
    org.springframework.web: DEBUG
    org.hibernate.SQL: DEBUG
```

#### API Testing Tools
Use these tools for API testing and debugging:

- **Postman**: Import collection and test endpoints
- **curl**: Command-line testing
- **Insomnia**: Alternative API client
- **Swagger UI**: Interactive API documentation

#### Network Analysis
Use browser developer tools or network monitoring tools to inspect:
- Request/response headers
- Payload contents
- Timing information
- Error details

### Performance Monitoring

#### Health Check Endpoint
Monitor API health with built-in Spring Boot Actuator:

```bash
curl http://localhost:8080/actuator/health
```

#### Metrics Collection
Enable metrics for performance monitoring:

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,metrics,prometheus
```

## Testing Examples

### Unit Test Examples

#### Controller Tests
```java
@Test
void shouldCreateUserSuccessfully() throws Exception {
    CreateUserRequest request = new CreateUserRequest();
    request.setEmail("test@example.com");
    request.setUsername("testuser");
    request.setPassword("SecurePass123!");
    
    when(userService.createUser(any(CreateUserRequest.class))).thenReturn(mockUser);
    
    mockMvc.perform(post("/api/users")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.email").value("test@example.com"));
}
```

#### Service Layer Tests
```java
@Test
void shouldThrowDuplicateEmailException() {
    CreateUserRequest request = new CreateUserRequest();
    request.setEmail("existing@example.com");
    
    when(userRepository.existsByEmail(anyString())).thenReturn(true);
    
    assertThrows(DuplicateEmailException.class, () -> {
        userService.createUser(request);
    });
}
```

### Integration Test Examples
```java
@SpringBootTest
@AutoConfigureMockMvc
class UserControllerIntegrationTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @Test
    void shouldReturnNotFoundForNonExistentUser() throws Exception {
        mockMvc.perform(get("/api/users/non-existent-id"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("User not found"));
    }
}
```

### Load Testing
Use Apache JMeter or Gatling for load testing:

```csv
threadGroup {
    setUp {
        rampUp(10 seconds)
    }
    
    scenario {
        visitPage("/api/users") {
            post {
                contentType("application/json")
                body("""{"email":"test@example.com","username":"testuser","password":"SecurePass123!"}""")
            }
        }
    }
}
```

## Conclusion

The User Management REST API provides a comprehensive set of endpoints for managing user accounts with proper validation, error handling, and security considerations. The API follows RESTful conventions and includes robust error handling mechanisms that provide meaningful feedback to clients.

Key features include:
- Complete CRUD operations for user management
- Comprehensive input validation and sanitization
- Consistent error response formatting
- Support for multiple client implementations
- Extensive testing coverage
- Production-ready error handling and logging

For production deployment, ensure to implement proper authentication, authorization, rate limiting, and monitoring as outlined in the authentication section and troubleshooting guide.