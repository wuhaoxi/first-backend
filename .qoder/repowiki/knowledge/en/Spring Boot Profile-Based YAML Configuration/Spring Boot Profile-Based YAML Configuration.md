---
kind: configuration_system
name: Spring Boot Profile-Based YAML Configuration
category: configuration_system
scope:
    - '**'
source_files:
    - src/main/resources/application.yml
    - src/main/resources/application-dev.yml
    - src/main/resources/application-test.yml
    - src/test/resources/application-test.yml
---

This project uses Spring Boot's built-in profile-based configuration system with YAML files under src/main/resources/. There is no custom configuration loader — all settings are declared as plain YAML and resolved by Spring Boot at startup.

Active profiles and base config:
- application.yml defines the application name (my-first-project-backend) and sets spring.profiles.active: dev, making the development profile the default.
- server.port is set to 8080 in the base file.

Environment-specific overrides:
- application-dev.yml configures a MySQL datasource (jdbc:mysql://localhost:3306/myapp), enables Hibernate DDL auto-update, and turns on SQL logging/formatting for development.
- application-test.yml (present both under src/main/resources/ and src/test/resources/) switches to an in-memory H2 database with create-drop schema generation and disables SQL logging.

How profiles are activated:
The active profile is controlled via spring.profiles.active in the base YAML. Tests can override it through the test classpath or by setting SPRING_PROFILES_ACTIVE as an environment variable; there is no explicit @ActiveProfiles annotation visible in the scanned code, so the default dev profile applies unless overridden externally.

What is NOT present:
- No .env files, no @ConfigurationProperties classes, no @Value injection of custom properties, no externalized secrets management, and no programmatic PropertySource manipulation.
- All configuration is declarative YAML only.