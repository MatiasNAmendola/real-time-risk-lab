---
title: Secrets y PII Protection — toolkit de defense-in-depth para agentes IA
tags: [concept, pattern/security, secrets, pii, injection, agent-os]
created: 2026-05-12
source_archive: docs/24-secrets-pii-protection.md (migrado 2026-05-12)
---

# Secrets y PII Protection — toolkit de defense-in-depth para agentes IA

## Por qué este toolkit

Los agentes IA pueden exponer secrets de tres formas: (1) hardcodearlos en código que escriben, (2) loggearlos al ejecutar comandos, (3) propagarlos via mensajes inter-agent. Adicionalmente, contenido externo puede inyectar instrucciones maliciosas en la memoria del agente. Este toolkit cubre las tres superficies.

## Capas

### Capa 1: PreToolUse hook — secret detection en entrada

Antes de que Claude ejecute un Edit/Write/Bash, los inputs se escanean. Si hay secret detectado, se redacta in-place y el tool sigue ejecutando con el valor `[REDACTED]`. Si todo el input era secret, se bloquea con exit 2.

Patterns cubiertos: AWS keys, GitHub tokens, Slack tokens, Stripe live, OpenAI/Anthropic keys, npm tokens, JWT, private key headers, connection strings con password.

El hook escribe cada detección a `out/security/secret-detections-YYYY-MM-DD.jsonl` con timestamp, tool, campos redactados, nombres de patterns y los primeros 8 chars del valor original para correlación forense sin exponer el secret completo.

### Capa 2: SecretRef late-binding

La config no contiene secrets; contiene descriptores `{"source": "env", "id": "..."}` que se resuelven en runtime via `resolve_in_place()`. Para logging, `mask_secrets()` recorre el dict y reemplaza cualquier valor cuya clave contiene una hint sensible (`password`, `token`, `key`, `api_key`, etc.) con `***`.

### Capa 3: Memory scanner — injection + exfil + Unicode invisible

Antes de persistir cualquier contenido a memoria del agente, `scan()` detecta:

- Prompt injection: "ignore previous instructions", "you are now X", system prompt overrides, jailbreaks.
- Exfil: curl/wget con env vars de secret, cat .env, authorized_keys, private keys, history dumps.
- Unicode invisible: ZWSP, ZWJ, word joiner, BOM, y bidi overrides LRO/RLO — vectores no obvios que pueden ocultar instrucciones maliciosas al ojo humano.

Resultado: bloqueo total. No hay redaction parcial en este layer.

## Arquitectura de defense-in-depth

```
Tool call (Bash/Edit/Write)
        |
        v
[ Capa 1: secret-detector.sh ]  <-- PreToolUse hook
        |  redact en campo, permite ejecucion
        |  bloquea si 100% secret (exit 2)
        v
   Tool ejecuta
        |
        v
[ Capa 2: secret_ref.py ]       <-- en config load time
        |  resolve_in_place() reemplaza descriptores
        |  mask_secrets() antes de loggear
        v
[ Capa 3: memory_scanner.py ]   <-- antes de persistir a memoria
        |  scan() detecta injection + exfil + unicode invisible
        v
   Memoria del agente (safe)
```

## Cómo correr

```bash
# Verificar sintaxis del hook
bash -n .ai/hooks/secret-detector.sh

# Tests del secret detector (bash)
bash .ai/hooks/test_secret_detector.sh

# Tests Python
python3 -m unittest .ai/lib/test_secret_ref.py
python3 -m unittest .ai/lib/test_memory_scanner.py

# Inspeccionar log de detecciones del día
tail -f out/security/secret-detections-$(date -u +%Y-%m-%d).jsonl
```

## Lo que NO cubre (por diseño)

- Git history scrubbing: requiere `git-filter-repo` como dep externa + operación destructiva.
- Egress firewall: limitar a qué dominios el agente puede curl. Es feature de plataforma, no de toolkit.
- Network exfil raw: si el agente abre un socket TCP directo, este toolkit no lo detecta.
- ML-based PII detection (NER): caro en runtime, falsos positivos altos.

## Anti-patterns

- **Detectar y bloquear sin loggear**: pierde la traza. Siempre escribir a `.ai/logs/`.
- **Patterns hardcoded en el hook**: hecho así por simplicidad de PoC. En prod va a config externa con TTL y versionado.
- **Confiar en regex para PII amplia**: emails y teléfonos son OK. DNI/tarjetas con regex tienen falsos positivos altos.

## Key Design Principle

> "Un agente IA con acceso a Bash es un side-channel de exfiltración a escala. La separación humano/no-humano que existe en logs tradicionales acá no existe — el agente PUEDE pegar tu private key en un log si no lo paramos. Tres capas: redact en input, mask en config, block en memoria. La defensa más fácil es la que el agente no tiene que recordar aplicar."

## Archivos del toolkit

| Archivo | Rol |
|---|---|
| `.ai/hooks/secret-detector.sh` | PreToolUse hook: redact-and-allow |
| `.ai/lib/secret_ref.py` | Late-binding secret resolution + masking |
| `.ai/lib/memory_scanner.py` | Pre-persistence guard: injection/exfil/unicode |
| `.claude/settings.json` | Wire del hook en PreToolUse |

## Related

- [[External-Secrets-Operator]] — gestión de secrets en Kubernetes.
- [[IRSA]] — IAM Roles for Service Accounts en EKS.
- [[Risk-Platform-Overview]]
