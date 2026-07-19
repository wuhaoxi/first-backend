---
kind: external_dependency
name: Lombok Annotation Processor
slug: lombok
category: external_dependency
category_hints:
    - framework_behavior
scope:
    - '**'
---

### Lombok
- Added as an `optional` compile-time dependency; code generation annotations (e.g. `@Data`, `@Getter`) are used across entities/DTOs to avoid boilerplate.
- Excluded from the `spring-boot-maven-plugin` repackage configuration so Lombok does not end up in the produced fat jar.