---
kind: error_handling
name: Spring Boot Global Exception Handling with Domain Exceptions
category: error_handling
scope:
    - '**'
source_files:
    - src/main/java/com/first/app/exception/GlobalExceptionHandler.java
    - src/main/java/com/first/app/exception/DuplicateEmailException.java
    - src/main/java/com/first/app/exception/ResourceNotFoundException.java
---

This Spring Boot project implements a centralized error handling strategy using `@RestControllerAdvice` to convert domain and framework exceptions into consistent HTTP responses.

**Exception Types**
- `ResourceNotFoundException` — thrown when a requested resource does not exist; mapped to HTTP 404 Not Found
- `DuplicateEmailException` — thrown on duplicate email creation; mapped to HTTP 409 Conflict
- Both are unchecked (`RuntimeException`) subclasses carrying only a message string, with no additional fields or error codes.

**Global Handler**
`GlobalExceptionHandler` is the single point of exception-to-response mapping:
- `@RestControllerAdvice` class with `@ExceptionHandler` methods for each exception type
- Returns `ResponseEntity<Map<String, ...>>` bodies with a uniform shape: `{"message": ..., "errors": [...]}` (validation) or `{"message": ...}` (domain errors)
- Also handles Spring's built-in exceptions:
  - `DataIntegrityViolationException` → 409 Conflict, wrapping the underlying cause message
  - `MethodArgumentNotValidException` → 400 Bad Request, returning field-level validation errors as an array of `{field, message}` objects

**Propagation Pattern**
Exceptions are thrown from service/controller layers and caught centrally — there is no try/catch in business logic. The handler translates them to appropriate HTTP status codes without leaking stack traces to clients.

**Conventions Observed**
- All custom exceptions extend `RuntimeException` (unchecked), so callers do not need to declare throws clauses
- No structured error response DTO exists yet; responses use raw `Map` literals, which limits consistency and testability
- No global fallback handler for unhandled exceptions (e.g., `Exception.class`) — such cases would fall through to Spring Boot's default `/error` endpoint
- No correlation IDs, request tracing, or logging within the handler itself