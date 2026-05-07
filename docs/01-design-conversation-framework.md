# 01 — Framework de conversación de diseño

Cómo encaro las discusiones de systems design. Una metodología para descomponer problemas, identificar cuellos de botella y articular trade-offs con claridad.

## Framework: seis pasos para cualquier problema de diseño

1. Clarificar supuestos antes de tocar el pizarrón.
2. Separar el camino crítico de los flujos no críticos.
3. Establecer medición: p95/p99, tracing, métricas — antes de afirmar nada sobre performance.
4. Proponer un diseño.
5. Articular trade-offs de forma explícita.
6. Cerrar con riesgos y próximos pasos.

Evitar listar tecnologías como respuesta. En su lugar, mostrar criterio y juicio.

---

## Problema: sistema de fraude en tiempo real a 150 TPS con p99 < 300ms

Enfoque de diseño:

> El primer paso es confirmar si los 300ms son media, p95 o p99 end-to-end — eso cambia todas las decisiones siguientes. Después separo el flujo: el camino crítico mantiene validación, lectura de features cacheadas, reglas determinísticas, scoring de ML bajo timeout duro, y un trace mínimo de la decisión. El flujo asincrónico maneja la auditoría enriquecida, analytics y señales de entrenamiento. Cada dependencia lleva timeout, circuit breaker y fallback explícito. Todo viaja con un correlation ID y una idempotency key.

Lo que fortalece el diseño:

- Presupuesto de latencia por dependencia.
- Decisiones de fallback en capas.
- Idempotencia en el borde de decisión y en cada consumer downstream.
- Reglas y modelos versionados.
- Patrón outbox para publicación confiable de eventos.

---

## Problema: no llegamos a 300ms — metodología de diagnóstico

Enfoque:

> Primero mido — no optimizo por intuición. Traces distribuidos primero: p95/p99 por span, no promedios. Después categorizo: si el problema es I/O, miro timeouts, pools, cache y cantidad de hops. Si es CPU, perfilo con JFR/async-profiler. Si es contención, miro locks, colas y diseño de concurrencia. El escalado horizontal viene después de entender el cuello de botella.

Principio clave:

> Escalar sin entender el cuello de botella amplifica el problema.

---

## Problema: migración de Lambda a EKS

Enfoque de diseño:

> La justificación depende de evidencia medida. Si Lambda introduce cold starts, latencia variable, límites de concurrencia, restricciones de networking o pérdida de control sobre JVM/conexiones, EKS puede ofrecer pods calientes, conexiones persistentes y tuning fino. Pero EKS no es gratis: agrega overhead operacional — capacity planning, probes, rollouts, HPA, nodos y observabilidad. No migraría por novedad; migraría cuando la latencia, el costo o el control operacional lo justifiquen con datos.

---

## Problema: eventos versionados

Enfoque de diseño:

> Trato al evento como una API pública. Defino un contrato explícito, una versión, compatibilidad backward/forward, nuevos campos opcionales y validación en CI. Cada evento lleva eventId, correlationId, occurredAt, producer, eventVersion e idempotencyKey. Los consumers deben tolerar duplicados y campos desconocidos. Cuando un breaking change es inevitable, creo una nueva versión y dejo que ambos consumers coexistan durante la ventana de migración.

---

## Problema: trazabilidad y auditoría

Enfoque de diseño:

> Uso correlation IDs end-to-end, logs estructurados, tracing distribuido y un trace de decisión persistido. El registro de decisión debe almacenar la versión del ruleset, la versión del modelo, los features usados o el snapshot, el score, las reglas activadas, los fallbacks aplicados y las dependencias que fallaron. El objetivo es reconstruir y explicar una decisión meses después, no solo debuggear el request de hoy.

---

## Problema: latencia o falla del modelo de ML

Enfoque de diseño:

> El modelo no debe ser un single point of failure. Si está en el camino crítico, necesita timeout duro, circuit breaker y fallback. La estrategia de fallback depende de la postura de riesgo: solo reglas, score cacheado, REVIEW, o decline/approve por política. La versión del modelo y de los features debe registrarse con cada decisión para habilitar rollback auditable.

---

## Problema: control de concurrencia

Enfoque de diseño:

> Controlo pools por dependencia para prevenir que una falla consuma todos los threads. Uso timeouts, bulkheads y backpressure. Evito locks globales en el motor de decisión y prefiero estructuras inmutables para reglas y configuración. Si el stack es Java moderno y el cuello de botella es I/O bloqueante, los virtual threads valen la pena de evaluar — pero solo después de medir. Prefiero aislamiento de fallas antes que maximizar throughput a costa de estabilidad.

---

## Problema: rollback seguro

Enfoque de diseño:

> Separo el rollback por dimensión: código, reglas, modelos y contratos. Para código, rolling o canary con health checks. Para reglas y modelos, feature flags y versionado. Para eventos, compatibilidad de versión a través de evolución de schema. Cada decisión debe registrar qué versión usó para que el rollback sea auditable.

---

## Cierre cuando se discute el sistema

Cuando una discusión llega a la etapa "qué más te importa", este es el framing que demuestra profundidad:

> El sistema combina toma de decisiones en tiempo real, restricciones de performance, arquitectura event-driven, trazabilidad y migración operacional a contenedores — exactamente la intersección donde los trade-offs de diseño tienen consecuencias reales. Preguntas abiertas clave para un sistema así: cómo se mide hoy el p99 end-to-end, qué motivó específicamente la decisión de migrar desde Lambda, y qué nivel de autonomía arquitectónica tiene el rol para definir estándares de observabilidad y performance.

Esto no es una lista de preguntas-interrogatorio — es mostrar que entendés el sistema y llegaste con preparación.
