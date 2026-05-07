# 24 — Proteccion de secrets, PII e injection

## Por que este toolkit

Los agentes IA pueden exponer secrets de tres formas: (1) hardcodearlos en codigo que escriben, (2) loggearlos al ejecutar comandos, (3) propagarlos via mensajes inter-agent. Adicionalmente, contenido externo puede inyectar instrucciones maliciosas en la memoria del agente. Este toolkit cubre las tres superficies.

## Capas

### Capa 1: PreToolUse hook — secret detection en entrada

Antes de que Claude ejecute un Edit/Write/Bash, los inputs se escanean. Si hay secret detectado, se redacta in-place y el tool sigue ejecutando con el valor `[REDACTED]`. Si todo el input era secret, se bloquea con exit 2.

Patterns cubiertos: AWS keys, GitHub tokens, Slack tokens, Stripe live, OpenAI/Anthropic keys, npm tokens, JWT, private key headers, connection strings con password.

El hook escribe cada deteccion a `.ai/logs/secret-detections-YYYY-MM-DD.jsonl` con timestamp, tool, campos redactados, nombres de patterns y los primeros 8 chars del valor original para correlacion forense sin exponer el secret completo.

### Capa 2: SecretRef late-binding

La config no contiene secrets; contiene descriptores `{"source": "env", "id": "..."}` que se resuelven en runtime via `resolve_in_place()`. Para logging, `mask_secrets()` recorre el dict y reemplaza cualquier valor cuya clave contiene una hint sensible (`password`, `token`, `key`, `api_key`, etc.) con `***`.

Esto elimina la superficie de leak en logs, dumps de config y trazas de agente sin requerir que cada caller recuerde omitir campos.

### Capa 3: Memory scanner — injection + exfil + Unicode invisible

Antes de persistir cualquier contenido a memoria del agente (engram, vault, contexto), `scan()` detecta:

- Prompt injection: "ignore previous instructions", "you are now X", system prompt overrides, jailbreaks.
- Exfil: curl/wget con env vars de secret, cat .env, authorized_keys, private keys, history dumps.
- Unicode invisible: ZWSP, ZWJ, word joiner, BOM, y bidi overrides LRO/RLO — vectores no obvios que pueden ocultar instrucciones maliciosas al ojo humano.

Resultado: bloqueo total. No hay redaction parcial en este layer — si el contenido tiene threat, no se persiste.

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

## Como correr

```bash
# Verificar sintaxis del hook
bash -n .ai/hooks/secret-detector.sh

# Tests del secret detector (bash)
bash .ai/hooks/test_secret_detector.sh

# Tests Python
python3 -m unittest .ai/lib/test_secret_ref.py
python3 -m unittest .ai/lib/test_memory_scanner.py

# Inspeccionar log de detecciones del dia
tail -f .ai/logs/secret-detections-$(date -u +%Y-%m-%d).jsonl
```

## Key Design Principle

> "Un agente IA con acceso a Bash es un side-channel de exfiltracion a escala. La separacion humano/no-humano que existe en logs tradicionales aca no existe — el agente PUEDE pegar tu private key en un log si no lo paramos. Tres capas: redact en input, mask en config, block en memoria. La defensa mas facil es la que el agente no tiene que recordar aplicar."

## Lo que NO cubre (por diseno)

- Git history scrubbing: requiere `git-filter-repo` como dep externa + operacion destructiva, not applicable for a PoC-scope project.
- Egress firewall: limitar a que dominios el agente puede curl. Es feature de plataforma, no de toolkit.
- Network exfil raw: si el agente abre un socket TCP directo, este toolkit no lo detecta. Mitigacion: sandbox a nivel SO.
- ML-based PII detection (NER): caro en runtime, falsos positivos altos. Si en serio importa, integrar Microsoft Presidio o spaCy con NER customizado.

## Anti-patterns

- **Detectar y bloquear sin loggear**: pierde la traza. Siempre escribir a `.ai/logs/`.
- **Patterns hardcoded en el hook**: lo hicimos asi por simplicidad de PoC. En prod va a config externa con TTL y versionado.
- **Confiar en regex para PII amplia**: emails y telefonos son OK. DNI/tarjetas con regex tienen falsos positivos altos. Para PII estricta, libreria dedicada.

## Archivos del toolkit

| Archivo | Rol |
|---|---|
| `.ai/hooks/secret-detector.sh` | PreToolUse hook: redact-and-allow |
| `.ai/hooks/test_secret_detector.sh` | Tests bash del hook |
| `.ai/lib/secret_ref.py` | Late-binding secret resolution + masking |
| `.ai/lib/test_secret_ref.py` | Tests unitarios secret_ref |
| `.ai/lib/memory_scanner.py` | Pre-persistence guard: injection/exfil/unicode |
| `.ai/lib/test_memory_scanner.py` | Tests unitarios memory_scanner |
| `.claude/settings.json` | Wire del hook en PreToolUse |

Referencia cruzada: [[docs/16-agent-os-principles]] — principios de isolation y trust boundaries del OS de agentes.
