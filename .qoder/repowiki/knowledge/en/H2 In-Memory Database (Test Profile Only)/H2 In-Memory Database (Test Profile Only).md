---
kind: external_dependency
name: H2 In-Memory Database (Test Profile Only)
slug: h2
category: external_dependency
category_hints:
    - migration_status
scope:
    - '**'
---

### H2 In-Memory Database
- Declared as a `test`-scoped dependency; only used when the `test` profile is active (default for `./mvnw test`).
- Configured as an in-memory DB (`jdbc:h2:mem:testdb`) with `create-drop` DDL so each test run starts from a clean schema.
- Not available in the `dev` profile — that profile requires a real MySQL instance.