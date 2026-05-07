---
name: pre-commit-format
trigger: pre-commit
applies_to: ["**/*.java", "**/*.go", "**/*.json", "**/*.yaml", "**/*.md"]
harnesses: [claude-code, cursor, windsurf]
---

# Hook: pre-commit-format

## Proposito

Formatear codigo antes de cada commit para mantener consistencia sin esfuerzo manual.

## Implementacion por tipo de archivo

### Java (poc/java-*/, tests/)

```bash
# Verificar que maven-fmt-plugin esta en el pom del modulo
mvn fmt:format -pl <modulo-modificado>
# O con google-java-format directamente:
# java -jar google-java-format.jar --replace $(git diff --cached --name-only --diff-filter=ACM | grep "\.java$")
```

### Go (cli/risk-smoke/)

```bash
gofmt -w $(git diff --cached --name-only --diff-filter=ACM | grep "\.go$")
goimports -w $(git diff --cached --name-only --diff-filter=ACM | grep "\.go$")
```

### JSON / YAML

```bash
# Con prettier si esta instalado:
npx prettier --write $(git diff --cached --name-only --diff-filter=ACM | grep -E "\.(json|yaml|yml)$")
```

### Markdown

```bash
# Con markdownlint-cli si esta instalado:
npx markdownlint --fix $(git diff --cached --name-only --diff-filter=ACM | grep "\.md$")
```

## Configuracion en Claude Code (.claude/settings.json)

```json
{
  "hooks": {
    "PreToolUse": [
      {
        "matcher": "Bash",
        "hooks": [{ "type": "command", "command": "echo 'Bash hook: pre-commit-format active'" }]
      }
    ]
  }
}
```

## Script standalone

```bash
#!/usr/bin/env bash
# .ai/scripts/format-changed.sh
set -e
JAVA_FILES=$(git diff --cached --name-only --diff-filter=ACM | grep "\.java$" || true)
GO_FILES=$(git diff --cached --name-only --diff-filter=ACM | grep "\.go$" || true)

if [ -n "$JAVA_FILES" ]; then
    echo "Formatting Java files..."
    for f in $JAVA_FILES; do
        MODULE=$(echo "$f" | cut -d/ -f1-2)
        mvn fmt:format -pl "$MODULE" -q 2>/dev/null || true
    done
fi

if [ -n "$GO_FILES" ]; then
    echo "Formatting Go files..."
    gofmt -w $GO_FILES
fi
```

## Notas

- Este hook es informativo. Si el formatter no esta instalado, debe fallar graciosamente (no bloquear el commit).
- El formateo debe ser idempotente: correrlo dos veces no debe cambiar nada.
