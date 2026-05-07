# GEMINI.md — Risk Decision Platform (Google Antigravity)

## Proyecto

Exploración técnica de un use case de detección de fraude en tiempo real.
Detección de fraude en tiempo real: 150 TPS, p99 < 300ms.
Stack: Java 25 LTS, Vert.x 5.0.12, Maven, Postgres 16, Valkey 8, Redpanda, k3d/OrbStack.

Contexto completo: .ai/context/architecture.md

## Reglas non-negotiable

1. Solo Java 25 LTS. NO bajar a 21. Usar --release 25.
2. Clean Architecture: domain/ NO importa de application/ ni infrastructure/.
3. ATDD primero: escribir el .feature antes que el código de producción.
4. Cada request debe producir trace + log + métrica vía OpenTelemetry.
5. correlationId en MDC y en el header de respuesta X-Correlation-Id.

## Skills y reglas

Antes de implementar cualquier cosa, revisar .ai/primitives/skills/ y .ai/primitives/rules/.

## No tocar

poc/, tests/, cli/, docs/, vault/ — propiedad exclusiva del usuario.
