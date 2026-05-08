# 39 — Share-ready baseline v0.1

**Fecha**: 2026-05-08  
**Anchor recomendado**: `v0.1-shareable` / `share/v0.1`

Este documento marca el primer baseline público razonable de **Real-Time Risk Lab**. No intenta reescribir la historia de Git; deja explícito qué commit es el punto estable para compartir y qué partes de la historia previa tienen baja auditabilidad.

## Qué queda marcado como v0.1

- **Real-Time Risk Lab** presentado como laboratorio técnico de riesgo/fraude en tiempo real.
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
- `b2f8dc1 refactor: organize real-time risk lab modules` — 347 archivos. Se preserva el subject original del commit, aunque el nombre canónico del proyecto en la documentación es **Real-Time Risk Lab**.

Son aceptables como sprint de preparación pública, pero no son ideales para bisect ni review fina. A partir de v0.1, el criterio recomendado es continuar con commits chicos, scope único y mensaje preciso, como en la wave de estabilización laptop-safe.

## Revisión puntual antes del tag

### `scripts/process-guard.py`

Resultado: apto para v0.1 con defaults conservadores. Criterios verificados:

- **Scope**: `status` sólo lista procesos relacionados al repo por defecto; no hace matching global salvo el caso explícito de Gradle daemons.
- **Seguridad operacional**: `stop` es dry-run salvo `--yes`; además excluye el propio helper y su shell padre.
- **Gradle global**: Gradle daemons globales quedan fuera salvo `--include-gradle-daemons` explícito.
- **UX de error**: señales inválidas devuelven exit `2` con mensaje `invalid signal: <value>`, evitando stack traces en CLI.

### `HttpVerticle.java` del EventBus stack

Resultado: apto para v0.1 con hardening menor. Criterios verificados:

- **Correlación/trace**: `/risk` mantiene `X-Correlation-Id`; las respuestas `200` y `502` exponen trace headers para diagnóstico local.
- **Body inválido**: `/webhooks` responde `400` JSON ante JSON inválido o body vacío, en vez de permitir NPE.
- **Filtros de webhook**: `APPROVE, REVIEW` se normaliza a `APPROVE,REVIEW`. Si el trim deja lista vacía, el endpoint responde `400`; no registra un webhook imposible de disparar silenciosamente.
- **Dominio permitido**: filtros fuera de `APPROVE`, `REVIEW`, `DECLINE` responden `400` con valores inválidos y permitidos.

## Próximo criterio de trabajo

- No reescribir `main` sólo para partir commits históricos ya publicados.
- Usar `share/v0.1` como rama congelada para compartir ese snapshot.
- Seguir iterando en `main` con commits pequeños y verificaciones laptop-safe.
