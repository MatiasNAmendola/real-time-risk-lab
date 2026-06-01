# 44 — Guion de presentación para discusiones técnicas

Este guion ordena el relato del repo para discusiones técnicas. La idea es mostrar criterio arquitectónico, evidencia empírica y método de ingeniería sin convertir la conversación en un tour exhaustivo por todos los archivos.

## Principio de presentación

Presentar el repositorio como un laboratorio técnico de arquitectura, no como un producto terminado. El foco es discutir trade-offs: latencia, boundaries, resiliencia, auditoría, eventos, transacciones distribuidas, testing y operación local reproducible.

Regla práctica: elegir dos o tres demos según el tiempo disponible y dejar el resto como deep dive opcional.

## Relato recomendado

### 1. Java / Vert.x / Clean Architecture

Usar este bloque para explicar el núcleo de riesgo en tiempo real.

Qué enfatizar:

- foco en decisiones de riesgo/fraude en tiempo real;
- throughput objetivo de 150 TPS sostenidos;
- presupuesto de latencia p99 bajo 300 ms;
- arquitectura híbrida: decisión sincrónica + auditoría/eventos/ML/downstream asíncronos;
- separación explícita entre critical path y trabajo fuera del budget;
- ATDD como contrato antes de implementación;
- observabilidad con correlation id, logs estructurados, métricas y trazas;
- resiliencia con timeouts, circuit breakers, fallbacks e idempotencia;
- Clean Architecture y boundaries verificables entre dominio, aplicación e infraestructura.

Cómo decirlo en voz alta:

> “El primer bloque demuestra cómo diseñaría un motor de decisión online: lo que afecta la respuesta entra en el camino crítico, y lo que puede ejecutarse después sale por eventos. La comparación entre PoCs permite discutir cuánto cuesta cada decisión de distribución.”

PoCs para mostrar:

- `poc/no-vertx-clean-engine`: baseline limpio sin framework;
- `poc/vertx-monolith-inprocess`: Vert.x in-process con infraestructura realista;
- `poc/vertx-layer-as-pod-http` o `poc/vertx-layer-as-pod-eventbus`: separación física de capas;
- `poc/vertx-service-mesh-bounded-contexts`: bounded contexts reales, si hay tiempo.

Comandos útiles:

```bash
./nx test --composite quick
./nx run no-vertx-clean-engine
./nx bench inproc
```

### 2. NestJS / Hono

Usar este bloque para explicar patrones transaccionales y comparar ergonomía de frameworks TypeScript.

Qué enfatizar:

- NestJS muestra DI, decorators, módulos y CQRS con estructura framework-driven;
- Hono muestra el mismo dominio con wiring manual y menos magia;
- ambos comparten el ejemplo simple de cuenta bancaria para CQRS/Event Sourcing;
- el ejemplo simple separa commands, queries, eventos, event store in-memory, proyección de balance y rehidratación;
- la demo avanzada queda separada para Saga, compensaciones, ledger, idempotencia y EDA;
- BullMQ + Valkey se usa para mostrar procesamiento asíncrono e idempotencia por mensaje de dominio;
- el checksum MD5 permite detectar payload drift o reprocesos inconsistentes en la demo;
- TigerBeetle aparece como frontera de ledger financiero, no como requisito para entender Event Sourcing.

Cómo decirlo en voz alta:

> “Uso NestJS y Hono para contrastar dos estilos sobre el mismo problema: uno con framework opinionado y otro con wiring explícito. El ejemplo simple enseña CQRS/Event Sourcing; la demo avanzada agrega Saga, idempotencia, cola y ledger.”

Endpoints didácticos del ejemplo simple:

```text
POST /accounts/:id/open
POST /accounts/:id/deposit
GET  /accounts/:id
GET  /accounts/:id/events
```

Comandos útiles:

```bash
./nx test --composite typescript-transactional-pocs
cd poc/nestjs-distributed-transactions && bun run test:atdd
cd poc/hono-distributed-transactions && bun run test:atdd
```

### 3. Repo como sistema de ingeniería

Usar este bloque para mostrar que el valor no está sólo en el código de las PoCs, sino también en la forma de operar y evolucionar el repo.

Qué enfatizar:

- primitivas para agentes e IDEs en `.ai/`;
- documentación metódica sincronizada entre `docs/`, `vault/`, `.ai/context`, README y adapters;
- guardrails automáticos para boundaries, consistencia, confidencialidad y documentación;
- tests unitarios, integración, e2e, ATDD, smoke y k6;
- runner `./nx` como interfaz única para demo, test, bench, audit, logs y lifecycle;
- scripts seguros para levantar y apagar servicios sin matar procesos por puerto de forma indiscriminada;
- auditoría transversal de PoCs con cumplimiento, warnings, deuda aceptada y fixes rápidos.

Cómo decirlo en voz alta:

> “Además del código, el repo intenta demostrar sistema de trabajo: cómo documento decisiones, cómo evito romper boundaries, cómo ejecuto pruebas por contexto y cómo dejo una demo apagable y reproducible.”

Comandos útiles:

```bash
./nx audit consistency
./nx audit confidentiality
python3 .ai/scripts/app-compliance-audit.py
./nx stop all --yes
```

## Recorrido por tiempo disponible

### 5 minutos

1. Abrir con el posicionamiento del repo.
2. Explicar caso: 150 TPS, p99 < 300 ms, sync + async.
3. Mostrar `./nx test --composite quick`.
4. Mostrar un request HTTP al motor de riesgo.
5. Cerrar con el mapa de PoCs.

### 15 minutos

1. Recorrido de 5 minutos.
2. Mostrar baseline Java/Vert.x y benchmark in-process.
3. Mostrar separación por capas/pods o smoke HTTP.
4. Explicar NestJS/Hono como deep dive transaccional.
5. Mostrar tests TypeScript o ATDD.

### 30 minutos

1. Recorrido de 15 minutos.
2. Comparar NestJS vs Hono sobre CQRS/Event Sourcing.
3. Explicar Saga orquestada, compensaciones e idempotencia.
4. Mostrar auditoría transversal y documentación metódica.
5. Discutir deuda aceptada y próximos pasos.


## Pulido final antes de una discusión técnica exigente

Antes de compartir o presentar el repo en una conversación exigente, conviene cerrar estos puntos:

- preparar un guion corto de demo de 10 a 15 minutos;
- elegir 2 o 3 PoCs máximo para no dispersar la conversación;
- dejar explícito qué es demo principal y qué queda como deep dive opcional;
- tener a mano comandos de arranque, test y apagado;
- evitar mencionar empresas, clientes o motivaciones específicas tanto oralmente como en slides o notas de apoyo.

Checklist mínimo recomendado:

```bash
./nx test --composite quick
./nx test --composite typescript-transactional-pocs
python3 .ai/scripts/app-compliance-audit.py
./nx stop all --yes
```

## Preguntas que conviene invitar

- ¿Qué entra y qué no entra en el budget p99 < 300 ms?
- ¿Cuándo conviene distribuir y cuándo no?
- ¿Por qué Saga no es rollback ACID global?
- ¿Qué gana y qué pierde NestJS frente a Hono?
- ¿Dónde vive la lógica de negocio?
- ¿Cómo se verifica que domain/application no dependan de infraestructura?
- ¿Qué pruebas correrías antes de compartir el repo?
- ¿Cómo se apagaría la demo sin matar servicios ajenos?

## Lo que no hay que sobreprometer

- No presentar el repo como producción cerrada.
- No decir que todos los caminos son la arquitectura recomendada; varios son comparaciones intencionales.
- No mezclar el ejemplo simple de CQRS/Event Sourcing con TigerBeetle como si fueran inseparables.
- No prometer rollback transaccional global en Saga: son transacciones locales más compensaciones.
- No depender de la demo distribuida completa si la conversación tiene poco tiempo.

## Links relacionados

- [`DEMO_SCRIPT.md`](../DEMO_SCRIPT.md)
- [`docs/00-START-HERE.md`](00-START-HERE.md)
- [`docs/41-cqrs-event-sourcing-transacciones.md`](41-cqrs-event-sourcing-transacciones.md)
- [`docs/42-documentacion-metodica.md`](42-documentacion-metodica.md)
- [`docs/43-auditoria-transversal-apps.md`](43-auditoria-transversal-apps.md)
- [`vault/05-Methodology/Technical-Positioning.md`](../vault/05-Methodology/Technical-Positioning.md)
- [`vault/05-Methodology/Technical-Discussion-Simulation.md`](../vault/05-Methodology/Technical-Discussion-Simulation.md)
