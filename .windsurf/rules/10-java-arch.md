---
trigger: glob
glob: "**/*.java"
description: Clean Architecture and Java baseline conventions
---

# Architecture rules for Java code

Full rule: .ai/primitives/rules/architecture-clean.md

## Layout canonico

domain/{entity,repository,usecase,service,rule}
application/{usecase/<aggregate>,mapper,dto}
infrastructure/{controller,consumer,repository,resilience,time}
config/ cmd/

## Dependency rule

domain/ <- application/ <- infrastructure/ <- config/cmd/
domain/ must NOT import from application/ or infrastructure/.

## Java 25

- --release 21 en todo build.gradle.kts
- Virtual threads para I/O bloqueante
- Records para Value Objects

See: .ai/primitives/rules/naming-conventions.md
