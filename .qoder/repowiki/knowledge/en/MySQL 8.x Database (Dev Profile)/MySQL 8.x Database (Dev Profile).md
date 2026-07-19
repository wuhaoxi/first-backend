---
kind: external_dependency
name: MySQL 8.x Database (Dev Profile)
slug: mysql
category: external_dependency
category_hints:
    - vendor_identity
    - client_constraint
scope:
    - '**'
---

### MySQL 8.x
- Production/dev data store: the `dev` profile targets a local MySQL instance at `localhost:3306` with database name `myapp`, using the `com.mysql.cj.jdbc.Driver` connector.
- Hibernate DDL mode is `update` in dev, so schema changes are applied automatically on startup.
- Tests bypass MySQL entirely by activating the `test` profile, which uses H2 in-memory instead.