---
kind: logging_system
name: No custom logging system — relies on Spring Boot defaults
category: logging_system
scope:
    - '**'
source_files:
    - src/main/resources/application.yml
---

This repository does not implement a custom logging system. There are no logger instances, log statements, or logging framework integrations in any of the application classes (UserController, UserService, exception handlers, etc.). The project also contains no `logging.*` configuration in `application.yml`, `application-dev.yml`, or `application-test.yml`, and no dedicated logging dependencies beyond what `spring-boot-starter-web` transitively brings in.

As a result, all runtime output is produced by Spring Boot's default SLF4J + Logback setup with its standard console sink and default log levels. No structured logging fields, no log rotation, no centralized sinks, and no log-level strategy have been configured.