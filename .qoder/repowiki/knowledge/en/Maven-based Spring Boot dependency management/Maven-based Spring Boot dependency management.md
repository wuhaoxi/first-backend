---
kind: dependency_management
name: Maven-based Spring Boot dependency management
category: dependency_management
scope:
    - '**'
source_files:
    - pom.xml
    - .mvn/wrapper/maven-wrapper.properties
    - mvnw
    - mvnw.cmd
---

This project uses Maven as its build and dependency-management tool, built on top of the Spring Boot BOM via spring-boot-starter-parent. All third-party libraries are declared in the root pom.xml under <dependencies> and rely on Spring Boot's managed versions for starters.

System and approach
- Build tool: Apache Maven (wrapper pinned to 3.9.9 via .mvn/wrapper/maven-wrapper.properties).
- Dependency source: Central Maven repository (repo.maven.apache.org) — no private registry or local mirror is configured.
- Version strategy: Versions are inherited from spring-boot-starter-parent:3.3.6; explicit versions appear only for non-Starter artifacts (e.g., mysql-connector-j, h2, lombok, maven-surefire-plugin).

Key files
- pom.xml — single source of truth for all dependencies, plugins, and properties.
- .mvn/wrapper/maven-wrapper.properties — pins Maven distribution and wrapper JAR URLs.
- mvnw / mvnw.cmd — platform wrappers that bootstrap the pinned Maven version.

Architecture and conventions
- Starter-first: Web, JPA, validation, DevTools, and test dependencies come through spring-boot-starter-* artifacts so their transitive versions are centrally managed by the parent POM.
- Scope discipline: Runtime-only deps (mysql-connector-j, spring-boot-devtools) use <scope>runtime</scope>; test-only deps (h2, spring-boot-starter-test) use <scope>test</scope>.
- Optional compile-time helpers: Lombok is marked <optional>true> and excluded from the Spring Boot executable jar via the spring-boot-maven-plugin <excludes> block.
- No vendoring or lockfile: There is no dependencyManagement section overriding versions, no custom BOM, and no lockfile committed to VCS. The target/ directory is gitignored, so resolved jars live only in the local Maven cache (~/.m2/repository).

Rules developers should follow
- Add new libraries as direct dependencies in pom.xml; prefer spring-boot-starter-* artifacts to inherit stable versions.
- Use <scope>runtime</scope> for DB drivers and dev-only tools; use <scope>test</scope> for test-only libraries like H2.
- Do not pin versions unless you must override a Starter-managed one — keep the parent POM as the single source of truth.
- If adding Lombok or other annotation processors, mark them <optional>true> and ensure they are excluded from the packaged jar.
- Keep .mvn/wrapper/maven-wrapper.properties at the pinned Maven 3.9.9 URL; update it only when upgrading the Maven wrapper.