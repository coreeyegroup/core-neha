# ADR-0001: Modular monolith with Spring Modulith

- Status: Accepted (brief), pending formal sign-off
- Date: 2026-11-04

One Spring Boot application containing all modules (`kernel`, `org`, `iam`, `workforce`, `approval`, `audit`, then later modules), each a top-level package with `api`, `internal` and `web` sub-packages. Spring Modulith verifies module dependencies in every build; ArchUnit adds rules Modulith does not cover. Each module owns its own PostgreSQL schema.

**Why:** clear boundaries without the operational cost of services; one transaction can span closely related changes; modules can be split out later if a measured need appears.

**Rejected:** microservices (operational load, distributed transactions); a generic plugin engine (speculative).
