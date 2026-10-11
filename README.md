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

## AI Assistant Configuration

The homepage AI assistant streams replies from DeepSeek (`POST /api/assistant/chat`, SSE). Keep the API key in your shell environment only — never write it into a tracked file, this README, or shell history.

```bash
# Export in the shell that starts the backend (environment-only, never committed)
export DEEPSEEK_API_KEY=…
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

Without the key the app still boots; chat requests fail cleanly with `503` (`The assistant is not configured`). Redis on `localhost:6379` is required for conversation memory and rate limiting.

`app.assistant.*` settings (`src/main/resources/application.yml`):

| Key                    | Default              | Purpose                                                                  |
|------------------------|----------------------|--------------------------------------------------------------------------|
| `api-key`              | `${DEEPSEEK_API_KEY:}` | Blank → assistant disabled (`503`)                                     |
| `history-ttl`          | `24h`                | TTL of `assistant:conversation:{userId}:{conversationId}`, refreshed per turn |
| `window-messages`      | `20`                 | Max history turns replayed into the prompt                               |
| `window-chars`         | `6000`               | Max history characters replayed into the prompt                          |
| `max-message-length`   | `2000`               | Longer user messages are rejected with `400`                             |
| `rate-limit-per-minute`| `10`                 | Per-user limit; exceeding it returns `429`                               |

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
