#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

APP_DOMAIN_PATHS=("src/internal/transactional-risk/domain" "src/internal/transactional-risk/application")
FORBIDDEN_IMPORT_PATTERN='hono|@hono|zod|bullmq|ioredis|express|@nestjs|class-validator|typeorm|kysely|Redis|Queue|Worker|\.\./\.\./infrastructure|\.\./\.\./\.\./infrastructure|from ['"'"'"][^'"'"']*infrastructure'

echo "Checking Clean Architecture boundaries for Hono PoC..."
if rg -n "$FORBIDDEN_IMPORT_PATTERN" "${APP_DOMAIN_PATHS[@]}"; then
  cat <<'MSG'

BOUNDARY VIOLATION:
- src/internal/transactional-risk/domain and src/internal/transactional-risk/application must not import Hono, Zod, BullMQ/Valkey clients,
  NestJS, HTTP, validation decorators, ORM/query libraries, or infrastructure adapters.
- Move framework-specific code to src/internal/transactional-risk/infrastructure.
- Depend on domain/application ports instead.
MSG
  exit 1
fi

echo "Boundaries OK: domain/application are framework-adapter free."
