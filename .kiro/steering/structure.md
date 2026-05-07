---
inclusion: fileMatch
filePatterns:
  - "**/*.java"
  - "**/pom.xml"
---

# Project Structure

## Canonical Java layout (all poc/ modules must follow)

domain/{entity,repository,usecase,service,rule}
application/{usecase/<aggregate>,mapper,dto}
infrastructure/{controller,consumer,repository,resilience,time}
config/ cmd/

## Dependency rule

domain/ <- application/ <- infrastructure/ <- config/cmd/

domain/ must NOT import from application/ or infrastructure/ — ever.
Outbound ports: interface in domain/repository/, impl in infrastructure/repository/.

## Naming

- Classes: PascalCase
- Methods/fields: camelCase
- SQL: snake_case
- Files: PascalCase.java

See: .ai/primitives/rules/naming-conventions.md
See: .ai/primitives/rules/architecture-clean.md
