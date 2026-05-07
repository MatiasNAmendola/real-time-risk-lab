# Architecture Rules

## Java layout (canonical)

domain/{entity,repository,usecase,service,rule}
application/{usecase/<aggregate>,mapper,dto}
infrastructure/{controller,consumer,repository,resilience,time}
config/ cmd/

## Dependency rule

domain/ must NOT import from application/ or infrastructure/ — ever.
Outbound ports: interface in domain/repository/, impl in infrastructure/repository/.

See: .ai/primitives/rules/architecture-clean.md
