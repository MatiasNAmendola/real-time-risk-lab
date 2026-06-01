# 45 — Requisitos y estilos arquitectónicos

Esta página consolida los requisitos funcionales, requisitos no funcionales y estilos arquitectónicos que el repo demuestra. El objetivo es tener una referencia única para explicar qué problema se está resolviendo, qué restricciones importan y por qué existen varias PoCs en lugar de una sola implementación.

## 1. Caso de negocio simulado

El dominio principal es una decisión de riesgo transaccional en tiempo real.

Una request representa una operación de pago que debe recibir una decisión online:

- aprobar;
- rechazar;
- enviar a revisión.

La respuesta online debe ser rápida y trazable. Todo lo que no sea estrictamente necesario para responder debe salir del camino crítico y ejecutarse por mecanismos asíncronos: auditoría, eventos, downstream, features futuras o pipelines analíticos.

## 2. Requisitos funcionales

| ID | Requisito funcional | Dónde se demuestra |
|---|---|---|
| RF-01 | Recibir una transacción de pago/riesgo por HTTP o comando local. | `no-vertx-clean-engine`, `vertx-*` |
| RF-02 | Evaluar reglas de riesgo sobre monto, cliente, dispositivo y señales disponibles. | `no-vertx-clean-engine`, `vertx-monolith-inprocess`, `vertx-layer-as-pod-*` |
| RF-03 | Consultar features de cliente desde un repositorio o adapter. | PoCs Java con Postgres/in-memory según variante |
| RF-04 | Combinar reglas, features y score/modelo para emitir una decisión. | Core Java y servicios Vert.x |
| RF-05 | Devolver una decisión online con `correlationId` trazable. | Endpoints `/risk` |
| RF-06 | Publicar eventos de decisión para auditoría/downstream. | Outbox/event publishers, Kafka/Tansu/SQS según PoC |
| RF-07 | Soportar idempotencia por request o mensaje. | Java idempotency stores; NestJS/Hono con Valkey/BullMQ |
| RF-08 | Ejecutar smoke/demo automatizable desde CLI. | `./nx demo`, `cli/risk-smoke`, scripts de PoCs |
| RF-09 | Exponer health/readiness para operación local. | `/healthz`, `/ready`, scripts `up.sh` |
| RF-10 | Mostrar CQRS/Event Sourcing con cuenta bancaria simple. | `nestjs-distributed-transactions`, `hono-distributed-transactions` |
| RF-11 | Consultar estado materializado/proyección y eventos históricos. | Endpoints `/accounts/:id` y `/accounts/:id/events` |
| RF-12 | Ejecutar una Saga orquestada con compensaciones. | Demo avanzada NestJS/Hono |
| RF-13 | Representar una frontera de ledger financiero. | Adapter TigerBeetle/fallback en demo avanzada |
| RF-14 | Publicar/procesar mensajes EDA con idempotencia de dominio. | BullMQ + Valkey + checksum MD5 |

## 3. Requisitos no funcionales

| ID | Requisito no funcional | Criterio usado en el repo | Evidencia/documentación |
|---|---|---|---|
| RNF-01 | Latencia online | Presupuesto p99 < 300 ms para la decisión de riesgo. | `README.md`, `docs/15-mapa-tecnico.md`, benchmarks/k6 |
| RNF-02 | Throughput | Objetivo conversacional: 150 TPS sostenidos. | `README.md`, `AGENTS.md`, ADRs de performance |
| RNF-03 | Camino crítico acotado | Sólo lo necesario para decidir entra en la respuesta online. | `docs/15-mapa-tecnico.md`, `docs/44-guion-presentacion-tecnica.md` |
| RNF-04 | Trabajo asíncrono | Auditoría, eventos y downstream no deben bloquear la respuesta principal. | Outbox, Tansu/Kafka, SQS, BullMQ |
| RNF-05 | Observabilidad | Correlation id, logs estructurados, métricas y trazas OTel. | OTel/OpenObserve, reglas `.ai`, PoCs Vert.x |
| RNF-06 | Resiliencia | Timeouts, circuit breakers, fallbacks y degradación explícita. | Core Java, ADRs, lessons learned |
| RNF-07 | Idempotencia | Misma intención de negocio no debe duplicar efectos. | Idempotency keys, Valkey, checksum MD5 |
| RNF-08 | Testabilidad | Unit, integration, e2e, ATDD, smoke, k6 y guardrails. | `docs/27-test-runner.md`, `docs/QUICK-REFERENCE.md` |
| RNF-09 | Boundaries limpios | Dominio/aplicación no dependen de infraestructura/framework. | ArchUnit, ESLint, `quick-check.py` |
| RNF-10 | Operación local reproducible | Levantar/apagar servicios sin matar procesos ajenos. | `./nx up/down/stop`, scripts por PoC |
| RNF-11 | Seguridad de tooling | Bun con lifecycle scripts bloqueados; secrets/mocks separados. | `docs/40-bun-package-manager-security.md` |
| RNF-12 | Evolución documentada | Decisiones y deuda explícitas, no implícitas. | `vault/02-Decisions`, auditoría transversal |

## 4. Qué entra en el presupuesto p99

En la decisión online entran sólo los pasos necesarios para responder al cliente:

1. parseo/validación mínima de request;
2. carga de features necesarias;
3. evaluación de reglas;
4. score/modelo si está dentro de timeout;
5. decisión final;
6. respuesta HTTP con correlation id.

No deberían bloquear la respuesta principal:

- auditoría durable;
- publicación a downstream;
- reentrenamiento o features futuras;
- notificaciones;
- procesamiento de colas;
- ledger financiero si no es requisito de autorización inmediata.

Esta separación permite explicar por qué el repo combina flujo sincrónico y flujo asíncrono.

## 5. Estilos arquitectónicos

### 5.1 Monolito modular

Un monolito modular concentra el runtime en un único proceso/deployable, pero mantiene módulos internos claros.

**Ventajas:**

- menor latencia por no tener hops de red;
- debugging más simple;
- despliegue más directo;
- buen punto de partida cuando el dominio todavía evoluciona rápido.

**Riesgos:**

- límites de ownership menos fuertes si no hay guardrails;
- escalado menos granular;
- riesgo de mezclar dominio e infraestructura si no se controla.

**PoC relacionada:**

- `poc/vertx-monolith-inprocess`.

### 5.2 Macroservicio

Un macroservicio agrupa varias capacidades relacionadas en un servicio deployable más grande que un microservicio estricto, pero más delimitado que un monolito general.

Es útil cuando se quiere separar un área de negocio completa sin pagar todavía el costo operativo de muchos servicios pequeños.

**Ventajas:**

- ownership más claro que en un monolito grande;
- menos overhead operativo que muchos microservicios;
- boundaries de dominio más visibles;
- buen paso intermedio para evolucionar una arquitectura.

**Riesgos:**

- puede convertirse en un monolito distribuido si no tiene límites internos;
- puede mezclar demasiadas responsabilidades;
- si se separa por capas técnicas en vez de capacidades, no resuelve ownership real.

**Cómo aparece en el repo:**

- Las PoCs Java permiten discutir cuándo quedarse con monolito modular y cuándo partir por bounded context.
- La idea de macroservicio sirve como punto intermedio conceptual, aunque no hay una PoC llamada literalmente `macroservice`.

### 5.3 Microservicio

Un microservicio representa una capacidad de negocio autónoma, con contrato propio, datos o ownership claros, despliegue independiente y observabilidad propia.

**Ventajas:**

- ownership y evolución independiente;
- escalado por capacidad;
- límites más fuertes;
- aislamiento de fallas si está bien diseñado.

**Riesgos:**

- más latencia por red/serialización;
- más complejidad operativa;
- transacciones distribuidas y consistencia eventual;
- testing end-to-end más costoso;
- riesgo de distributed monolith si se separa sin autonomía real.

**PoC relacionada:**

- `poc/vertx-service-mesh-bounded-contexts`.

### 5.4 Layer-as-pod

Layer-as-pod separa capas técnicas en procesos/pods distintos: controller, use case, repository, consumer.

Esto **no es microservicios reales** porque la separación se hace por capa técnica, no por capacidad de negocio autónoma.

**Ventajas:**

- muestra costo de red/serialización entre capas;
- permite demostrar permisos de infraestructura por proceso;
- hace visible qué componente puede acceder a datos, secretos o brokers.

**Riesgos:**

- puede agregar latencia sin ganar autonomía de negocio;
- puede generar un monolito distribuido;
- requiere coordinación fuerte entre capas.

**PoCs relacionadas:**

- `poc/vertx-layer-as-pod-eventbus`;
- `poc/vertx-layer-as-pod-http`.

### 5.5 Service-to-service por bounded contexts

Este estilo separa servicios por responsabilidad de negocio y comunicación explícita. Es el paso más cercano al microservicio real dentro de las PoCs Java.

**Ventajas:**

- boundaries de negocio más claros;
- contratos explícitos;
- ownership más defendible;
- permite discutir service mesh, retries, timeouts y trazabilidad.

**Riesgos:**

- exige observabilidad madura;
- requiere manejo de fallas parciales;
- aumenta complejidad de pruebas y operación.

**PoC relacionada:**

- `poc/vertx-service-mesh-bounded-contexts`.

## 6. Matriz de estilos por PoC

| PoC | Estilo principal | Qué permite discutir | Advertencia |
|---|---|---|---|
| `no-vertx-clean-engine` | Core limpio sin framework | Latencia base, Clean Architecture, dominio puro | No demuestra operación distribuida. |
| `vertx-monolith-inprocess` | Monolito modular Vert.x | EventBus local, infra realista, cero hops entre capas | No tiene boundaries físicas fuertes. |
| `vertx-layer-as-pod-eventbus` | Layer-as-pod por EventBus clustered | Permisos por red, costo de distribución, EventBus | No es microservicios reales. |
| `vertx-layer-as-pod-http` | Layer-as-pod por HTTP/tokens | Contratos HTTP internos, tokens, separación física | Puede ser monolito distribuido si se usa como patrón final. |
| `vertx-service-mesh-bounded-contexts` | Service-to-service / bounded contexts | Microservicios reales, fallas parciales, trazabilidad | Mayor complejidad operativa. |
| `k8s-local` | Plataforma local/GitOps | Rollouts, SLOs, ArgoCD, operación | No es app de dominio. |
| `kafka-s3-tansu` | Infra/broker | Broker Kafka-wire con storage S3 | No es app de dominio. |
| `nestjs-distributed-transactions` | App TypeScript con framework opinionado | CQRS, Event Sourcing, Saga, EDA, DI/decorators | Demo didáctica, no producción financiera. |
| `hono-distributed-transactions` | App TypeScript con wiring manual | Misma semántica con menos framework | Útil para contraste, no para mostrar DI enterprise. |

## 7. Cómo contarlo en una discusión técnica

Una forma ordenada de presentarlo:

1. **Primero el problema:** decisión de riesgo online con 150 TPS y p99 < 300 ms.
2. **Después los requisitos:** separar respuesta sincrónica de efectos asíncronos.
3. **Luego los estilos:** empezar simple y medir antes de distribuir.
4. **Después las PoCs:** monolito modular, layer-as-pod, bounded contexts reales.
5. **Finalmente el método:** tests, guardrails, documentación, lifecycle seguro.

Frase útil:

> “No parto de microservicios por default. Primero defino el camino crítico, mido latencia y mantengo boundaries limpios. Si aparece una razón real —ownership, escalado, seguridad, despliegue independiente o aislamiento de fallas— parto por bounded context, no por capa técnica.”

## 8. Preguntas frecuentes

### ¿Por qué no microservicios desde el día uno?

Porque para 150 TPS el problema principal no es la cantidad de servicios, sino el budget p99, la consistencia de la decisión, la observabilidad y la resiliencia. Microservicios agregan hops, fallas parciales y coordinación. Convienen cuando hay una razón medida u organizacional clara.

### ¿Layer-as-pod es microservicios?

No. Layer-as-pod separa capas técnicas. Microservicios separan capacidades de negocio autónomas. En el repo se mantiene esta distinción para evitar vender una separación física como si fuera autonomía de dominio.

### ¿Dónde entra macroservicio?

Como punto intermedio: un servicio más grande que un microservicio granular, pero con una frontera de negocio más clara que un monolito general. Puede ser una buena transición si todavía no se justifica partir en muchos servicios.

### ¿Qué PoC mostraría primero?

Para una demo corta:

1. `no-vertx-clean-engine` o `vertx-monolith-inprocess` para explicar el core;
2. `vertx-layer-as-pod-http` para mostrar separación física y smoke;
3. NestJS/Hono sólo como deep dive si quieren hablar de CQRS, Event Sourcing o Saga.

## 9. Links relacionados

- [`README.md`](../README.md)
- [`docs/00-START-HERE.md`](00-START-HERE.md)
- [`docs/15-mapa-tecnico.md`](15-mapa-tecnico.md)
- [`docs/27-test-runner.md`](27-test-runner.md)
- [`docs/41-cqrs-event-sourcing-transacciones.md`](41-cqrs-event-sourcing-transacciones.md)
- [`docs/43-auditoria-transversal-apps.md`](43-auditoria-transversal-apps.md)
- [`docs/44-guion-presentacion-tecnica.md`](44-guion-presentacion-tecnica.md)
- [`vault/04-Concepts/In-Process-vs-Distributed.md`](../vault/04-Concepts/In-Process-vs-Distributed.md)
- [`vault/05-Methodology/Technical-Leadership-Mindset.md`](../vault/05-Methodology/Technical-Leadership-Mindset.md)
