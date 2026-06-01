#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

APP_DOMAIN_PATHS=(
  "src/internal/domain"
  "src/internal/application"
)

FORBIDDEN_IMPORT_PATTERN='@nestjs|class-validator|bullmq|ioredis|express|typeorm|kysely|Redis|Queue|Worker|\.\./\.\./infrastructure|\.\./\.\./\.\./infrastructure|from ['"'"'"][^'"'"']*infrastructure'

echo "Checking Clean Architecture boundaries for NestJS PoC..."

if rg -n "$FORBIDDEN_IMPORT_PATTERN" "${APP_DOMAIN_PATHS[@]}"; then
  cat <<'MSG'

BOUNDARY VIOLATION:
- src/internal/domain and src/internal/application must not import NestJS, HTTP, validation decorators,
  BullMQ/Valkey clients, ORM/query libraries, or infrastructure adapters.
- Move framework-specific code to src/internal/infrastructure.
- Depend on domain/application ports instead.
MSG
  exit 1
fi

echo "Boundaries OK: domain/application are framework-adapter free."
