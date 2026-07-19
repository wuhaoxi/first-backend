---
kind: frontend_style
name: No frontend styling system — backend-only Spring Boot REST API
category: frontend_style
scope:
    - '**'
---

This repository is a pure backend Spring Boot 3.3 project exposing a JSON REST API for user CRUD operations. There is no frontend code, CSS, SCSS, Tailwind configuration, or any UI-related assets anywhere in the source tree. The `src/main/resources` directory contains only YAML application configuration files (`application.yml`, `application-dev.yml`, `application-test.yml`) and there is no `static/` or `templates/` directory that would typically hold frontend assets. Consequently, the `frontend_style` category does not apply to this repository.