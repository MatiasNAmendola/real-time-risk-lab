---
name: pre-tool-use-block-secrets
trigger: PreToolUse
applies_to: [Edit, Write, Bash]
harnesses: [claude-code]
---

# Hook: pre-tool-use-block-secrets

## Proposito

Prevenir que un agente escriba secrets hardcodeados en archivos del repo.

## Patrones a bloquear

Si el payload de Edit o Write contiene alguno de estos patrones, el hook debe alertar:

```
- password\s*=\s*["'][^"']{4,}["']
- secret\s*=\s*["'][^"']{8,}["']
- api[_-]?key\s*=\s*["'][^"']{8,}["']
- token\s*=\s*["'][^"']{8,}["']
- AWS_SECRET_ACCESS_KEY\s*=\s*[A-Za-z0-9+/]{20,}
- private[_-]?key.*BEGIN
```

## Excepciones legitimas (no bloquear)

```
- "test", "test-password", "changeme" — credenciales de desarrollo conocidas
- getenv("..."), System.getenv — lectura de variable de entorno
- ${...}, {{ ... }} — referencias a templates/variables
- minioadmin — credencial conocida de MinIO dev
- root — token de OpenBao dev mode
```

## Implementacion en Claude Code (.claude/settings.json)

```json
{
  "hooks": {
    "PreToolUse": [
      {
        "matcher": "Edit|Write",
        "hooks": [
          {
            "type": "command",
            "command": "bash -c 'echo \"$CLAUDE_TOOL_INPUT\" | python3 .ai/scripts/check-secrets.py'"
          }
        ]
      }
    ]
  }
}
```

## Script check-secrets.py (referencia)

```python
import sys, re, json

PATTERNS = [
    r'password\s*=\s*["\'][^"\']{6,}["\']',
    r'secret\s*=\s*["\'][^"\']{8,}["\']',
    r'api_key\s*=\s*["\'][^"\']{8,}["\']',
]
SAFE = ['getenv', 'System.getenv', '${', '{{', 'test', 'changeme', 'minioadmin', 'root']

input_data = json.loads(sys.stdin.read())
content = str(input_data.get('new_string', '') or input_data.get('content', ''))

for pattern in PATTERNS:
    match = re.search(pattern, content, re.IGNORECASE)
    if match:
        matched = match.group(0)
        if not any(safe in content for safe in SAFE):
            print(f"BLOCKED: Possible secret detected: {matched[:20]}...")
            sys.exit(1)
sys.exit(0)
```

## Notas

- Este hook es una red de seguridad, no un auditor completo.
- Para auditoria seria: usar `gitleaks` o `truffleHog` en CI.
- En produccion real: `git-secrets` o similar en pre-commit de git.
