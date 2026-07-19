---
kind: external_dependency
name: Spring Boot 3.3.6 Application Framework
slug: spring-boot
category: external_dependency
category_hints:
    - framework_behavior
scope:
    - '**'
---

- Runtime entry point is `com.first.app.MyApplication`; the app listens on port 8080 and defaults to the `dev` profile via `application.yml`.
- Profiles drive environment-specific wiring: `dev` connects to MySQL, `test` switches to H2 in-memory — switch with `-Dspring-boot.run.profiles=<profile>` or `./mvnw test` for the test profile.
- DevTools (`spring-boot-devtools`) is enabled at runtime for hot-reload during development.