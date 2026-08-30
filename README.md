# my-first-project-backend

Spring Boot REST API with JPA and MySQL persistence.

## Prerequisites

- Java 17+ (JDK)
- MySQL 8.x running on `localhost:3306` (dev profile)
- Docker Desktop running (`./mvnw test` uses Testcontainers)

### Database setup

```bash
# Create the dev database once (fresh Homebrew MySQL: root with empty password)
mysql -uroot -e "CREATE DATABASE IF NOT EXISTS wanderchina CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
```

## Quick Start

```bash
# Run tests (requires Docker Desktop running; spins up a MySQL 8.4 container)
./mvnw test

# Start the application (requires MySQL running on localhost:3306)
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

## API Endpoints

| Method | Path              | Description       | Request Body                          |
|--------|-------------------|-------------------|---------------------------------------|
| GET    | /api/users        | List all users    | —                                     |
| GET    | /api/users/{id}   | Get user by ID    | —                                     |
| POST   | /api/users        | Create a user     | `{"name": "...", "email": "..."}`    |
| PUT    | /api/users/{id}   | Update a user     | `{"name": "...", "email": "..."}`    |
| DELETE | /api/users/{id}   | Delete a user     | —                                     |

## Project Structure

```
src/main/java/com/first/app/
├── MyApplication.java         # Entry point
├── controller/UserController  # REST endpoints
├── service/UserService        # Business logic
├── repository/UserRepository  # Data access
├── entity/User                # JPA entity
├── dto/                       # Request DTOs
└── exception/                 # Error handling
```

## Profiles

- `dev` (default) — connects to MySQL at `localhost:3306/wanderchina` (`jdbc:mysql://localhost:3306/wanderchina?connectionTimeZone=LOCAL`, user `root`, empty password)
- `test` — Testcontainers MySQL 8.4 container (used by the test suite automatically; requires Docker Desktop)
