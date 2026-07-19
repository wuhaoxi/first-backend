---
kind: build_system
name: Maven + Spring Boot Starter Parent Build
category: build_system
scope:
    - '**'
source_files:
    - pom.xml
    - .mvn/wrapper/maven-wrapper.properties
---

This project uses a standard Maven build driven by the Spring Boot starter parent. There is no custom Makefile, Dockerfile, CI pipeline, or shell-based build script — the entire build and packaging flow is defined in `pom.xml` and invoked via the Maven Wrapper (`mvnw`).

**Build toolchain**
- Maven 3.9.9 (pinned via `.mvn/wrapper/maven-wrapper.properties`) with the wrapper JAR checked in so builds are reproducible without a pre-installed Maven.
- Java 17 enforced through `<java.version>` in properties.
- Spring Boot 3.3.6 as the parent POM, which manages dependency versions for all starters and plugins.

**Packaging & lifecycle**
- `spring-boot-maven-plugin` is configured to exclude Lombok from the final fat jar (Lombok is only needed at compile time).
- `maven-surefire-plugin` 3.2.5 runs unit tests during `mvn test` / `mvn package`.
- The default Spring Boot packaging produces an executable `target/my-first-project-backend-0.0.1-SNAPSHOT.jar` runnable via `java -jar`.

**Profiles / environments**
- No explicit Maven profiles are declared; environment-specific configuration is handled purely through Spring profiles (`application-dev.yml`, `application-test.yml`, `application.yml`).
- H2 is scoped to `test`, so it is available only when running tests; MySQL connector is `runtime` scope for production.

**What is NOT present**
- No Dockerfile, docker-compose, or container build step.
- No CI/CD configuration (no `.github/workflows`, Jenkinsfile, etc.).
- No versioning strategy beyond the conventional `0.0.1-SNAPSHOT` snapshot artifact.
- No cross-compilation, multi-module layout, or shade/relocation rules.

**Developer workflow**
- Build: `./mvnw clean package`
- Run dev server: `./mvnw spring-boot:run`
- Run tests: `./mvnw test`
- Produce a deployable jar: `./mvnw package` (produces a Spring Boot executable jar in `target/`).