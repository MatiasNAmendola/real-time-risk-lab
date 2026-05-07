---
name: post-tool-use-test
trigger: PostToolUse
applies_to: [Edit, Write]
harnesses: [claude-code]
---

# Hook: post-tool-use-test

## Proposito

Despues de editar un archivo Java o Go, correr automaticamente los tests del modulo afectado para detectar regresiones inmediatamente.

## Logica

1. Identificar el modulo al que pertenece el archivo editado.
2. Correr solo los tests de ese modulo (no el build completo).
3. Si los tests fallan: alertar con el mensaje de error.

## Mapeo archivo → modulo de tests

| Path del archivo editado | Comando de test |
|---|---|
| `poc/java-risk-engine/src/**` | `cd poc/java-risk-engine && ./scripts/test.sh` |
| `poc/java-vertx-distributed/<module>/src/**` | `mvn test -pl poc/java-vertx-distributed/<module>` |
| `poc/java-vertx-distributed/atdd-tests/**` | `mvn test -pl poc/java-vertx-distributed/atdd-tests` |
| `poc/vertx-risk-platform/src/**` | `mvn test -pl poc/vertx-risk-platform` |
| `tests/risk-engine-atdd/**` | `mvn test -pl tests/risk-engine-atdd` |
| `cli/risk-smoke/**/*.go` | `cd cli/risk-smoke && go test ./...` |

## Implementacion en Claude Code

```json
{
  "hooks": {
    "PostToolUse": [
      {
        "matcher": "Edit|Write",
        "hooks": [
          {
            "type": "command",
            "command": "bash -c 'bash .ai/scripts/run-module-tests.sh \"$CLAUDE_TOOL_INPUT\"'"
          }
        ]
      }
    ]
  }
}
```

## Script run-module-tests.sh (referencia)

```bash
#!/usr/bin/env bash
FILE_PATH=$(echo "$1" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('path',''))" 2>/dev/null || echo "")

if [[ "$FILE_PATH" == poc/java-vertx-distributed/* ]]; then
    MODULE=$(echo "$FILE_PATH" | cut -d/ -f1-3)
    echo "Running tests for $MODULE..."
    mvn test -pl "$MODULE" -q 2>&1 | tail -5
elif [[ "$FILE_PATH" == poc/java-risk-engine/* ]]; then
    echo "Running java-risk-engine tests..."
    cd poc/java-risk-engine && ./scripts/test.sh 2>&1 | tail -5
elif [[ "$FILE_PATH" == cli/risk-smoke/* ]]; then
    echo "Running Go tests..."
    cd cli/risk-smoke && go test ./... 2>&1 | tail -5
fi
```

## Notas

- Este hook es opcional y puede ser costoso en tiempo. Desactivar si los tests son lentos.
- Solo correr en archivos de produccion, no en tests mismos (evitar loops).
- Timeout: 60s. Si excede, reportar y continuar.
