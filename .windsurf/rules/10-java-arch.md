---
trigger: glob
glob: "**/*.java"
description: Clean Architecture and Java baseline conventions
---

# Architecture rules for Java code

Full rule: .ai/primitives/rules/architecture-clean.md

## Canonical layout

domain/{entity,repository,usecase,service,rule}
application/{usecase/<aggregate>,mapper,dto}
infrastructure/{controller,consumer,repository,resilience,time}
config/ cmd/

## Dependency rule

domain/ <- application/ <- infrastructure/ <- config/cmd/
domain/ must NOT import from application/ or infrastructure/.

## Java baseline

- Java 21 LTS (`--release 21`) in the current build; Java 25 is a documented target
- Virtual threads for blocking I/O
- Records for Value Objects

See: .ai/primitives/rules/naming-conventions.md
