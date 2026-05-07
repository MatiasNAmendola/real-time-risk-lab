# 02 — Preguntas de discovery de plataforma

Preguntas que hago al evaluar una plataforma nueva o unirme a un sistema existente. Son preguntas de discovery estilo consultoría — el objetivo es armar un mapa del sistema antes de proponer nada.

---

## SLA y performance

Lo que el sistema necesita demostrar: ~300ms, ~150 TPS, p99 como objetivo real.

Cómo sacar el detalle:
- Siempre clarificar si los 300ms son media, p95 o p99 antes de involucrarse con cualquier pregunta de diseño.
- Discutir presupuesto de latencia por componente, no latencia total.
- Nombrar al jitter como driver del p99 (GC, agotamiento de pools, contención de locks).
- Referenciar tracing distribuido como el instrumento de diagnóstico, no solo logging.

Principio clave: "Escalar sin entender el cuello de botella amplifica el problema."

---

## Arquitectura sync + eventos

Lo que el sistema necesita demostrar: decisión sincrónica en el camino crítico, auditoría y analytics vía eventos asincrónicos.

Cómo sacar el detalle:
- Separar el camino crítico (decisión + trace mínimo) del flujo async (auditoría enriquecida, analytics, training).
- Referenciar el patrón outbox como garantía de consistencia entre decisión y evento.
- Demostrar entendimiento de que la entrega at-least-once requiere idempotencia en los consumers.
- Discutir manejo de duplicados, retries y DLQs.

Principio clave: "La decisión y el evento en la misma transacción — o ninguno existe."

---

## Migración de Lambda a EKS

Lo que el sistema necesita demostrar: una migración en curso o planeada, motivada por cold starts, latencia o control operacional.

Cómo sacar el detalle:
- Referenciar cold starts, Provisioned Concurrency y límites de concurrencia como drivers medibles de migración.
- Reconocer que EKS agrega complejidad operacional real (probes, HPA, rolling updates, GitOps).
- Mostrar que el codebase está — o debería estar — desacoplado del runtime (arquitectura hexagonal).
- Demostrar que una migración bien ejecutada cambia el adapter, no el core.

Principio clave: "EKS no es un upgrade — es una elección de trade-off operacional."

---

## Eventos y contratos

Lo que el sistema necesita demostrar: eventos versionados con schema explícito, posiblemente sin schema registry todavía.

Cómo sacar el detalle:
- Tratar los eventos como APIs públicas con contratos explícitos.
- Agregar campos nuevos como opcionales; los breaking changes requieren nueva versión y dual-publish.
- Nombrar los campos requeridos: eventId, correlationId, idempotencyKey, occurredAt, eventVersion, producer.
- Si hay schema registry: validación en CI. Si no: oportunidad para proponerlo.

Principio clave: "Un evento es una API pública — si lo rompés, rompés a quien lo consume."

---

## Auditoría y decisiones de fraude

Lo que el sistema necesita demostrar: capacidad de explicar decisiones meses después, potencialmente para fines regulatorios o de soporte.

Cómo sacar el detalle:
- Distinguir entre logs de observabilidad y traces de auditoría (objetos distintos, requisitos de durabilidad distintos).
- Enumerar qué guarda el trace: versión del ruleset, versión del modelo, features usados o snapshot, score, reglas activadas, fallbacks aplicados, dependencias que fallaron.
- Mostrar que el trace se persiste en la misma transacción que la decisión.

Principio clave: "Sin trace de auditoría no hay auditoría real — solo logs."

---

## ML y data science

Lo que el sistema necesita demostrar: un modelo de ML en el camino crítico o cerca, con fallback cuando el modelo no está disponible.

Cómo sacar el detalle:
- Diseñar el modelo detrás de un timeout duro y un circuit breaker.
- Fallback en capas: score cacheado → reglas determinísticas → política por monto.
- Versión del modelo y set de features deben viajar juntos para hacer posible el rollback.
- Monitorear drift: no solo latencia del endpoint, sino distribución del score y tasas de APPROVE/DECLINE/REVIEW.

Principio clave: "El modelo enriquece la decisión cuando está disponible; no la bloquea cuando no lo está."

---

## Rol y contexto de liderazgo

Qué evaluar en el nivel staff/architect: no solo ejecución, sino definir estándares, proponer mejoras con evidencia y escuchar primero.

Cómo encararlo:
- En discusiones de "primeros 30 días": mapear antes de proponer. No entrar a cambiar sin entender.
- Mostrar que los trade-offs se hacen con evidencia medida, no con preferencias tecnológicas.
- Distinguir decisiones técnicas de decisiones de negocio (ej.: la política de fallback para fraude es una decisión de negocio).
- Discutir autonomía y estándares como algo construido con el equipo, no impuesto sobre él.

Principio clave: "Los primeros 30 días son para escuchar y mapear, no para proponer."
