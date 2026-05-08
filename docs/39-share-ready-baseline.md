# 39 — Share-ready baseline v0.1

**Fecha**: 2026-05-08  
**Anchor recomendado**: `v0.1-shareable` / `share/v0.1`

Este documento marca el primer baseline público razonable de Real-Time Risk Lab después del reframing masivo a “real-time risk lab”. No intenta reescribir la historia de Git; deja explícito qué commits son el punto estable para compartir y qué partes de la historia previa tienen baja auditabilidad.

## Qué queda marcado como v0.1

- Repo framed como laboratorio técnico de riesgo/fraude en tiempo real.
- Baseline ejecutable: **Java 21 LTS** con `--release 21`.
- Objetivo documentado, no operativo: **Java 25 LTS** cuando Gradle/JMH/Karate/ArchUnit soporten classfile 25 sin fricción.
- Test runner local más seguro para laptop:
  ```bash
  ./nx proc status --include-gradle-daemons --truncate 120 && \
    ./nx test all --with-infra-compose --parallel 1 --max-cpu 50 --max-ram 6000
  ```
- `./nx proc status/stop` reemplaza pipelines ad-hoc `ps | grep` para inventario y corte conservador de procesos del repo.

## Nota de auditabilidad histórica

La wave de reframing dejó dos commits grandes:

- `a48734f chore: prepare risk platform repo for sharing` — 667 archivos.
- `b2f8dc1 refactor: organize real-time risk lab modules` — 347 archivos.

Son aceptables como sprint de preparación pública, pero no son ideales para bisect ni review fina. A partir de v0.1, el criterio recomendado es continuar con commits chicos, scope único y mensaje preciso, como en la wave de estabilización laptop-safe.

## Revisión puntual antes del tag

### `scripts/process-guard.py`

Resultado: apto para v0.1 con defaults conservadores.

- `status` sólo lista procesos relacionados al repo por defecto.
- `stop` es dry-run salvo `--yes`.
- Gradle daemons globales quedan fuera salvo `--include-gradle-daemons` explícito.
- Se agregó manejo amable para señales inválidas, evitando stack traces en CLI.

### `HttpVerticle.java` del EventBus stack

Resultado: apto para v0.1 con hardening menor.

- `/risk` mantiene `X-Correlation-Id` y ahora también agrega trace headers en errores 502.
- `/webhooks` valida body vacío como 400 JSON en vez de permitir NPE.
- Los filtros de webhook se trimmean para que `APPROVE, REVIEW` matchee igual que `APPROVE,REVIEW`.

## Próximo criterio de trabajo

- No reescribir `main` sólo para partir commits históricos ya publicados.
- Usar `share/v0.1` como rama congelada para compartir ese snapshot.
- Seguir iterando en `main` con commits pequeños y verificaciones laptop-safe.
