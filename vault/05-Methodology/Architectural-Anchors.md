---
title: Architectural Anchors — Principios clave de diseño
tags: [methodology, design-principles]
created: 2026-05-07
---

# Architectural Anchors

Principios de diseño con peso. Internalizar 3-5 antes de entrar a cualquier discusión de arquitectura. Cada uno se enuncia como un claim citable.

## Arquitectura

- "El package domain no tiene ningún `import` que mencione HTTP, Spring o SQL. Esa es la regla de dependencias en la práctica, no en la teoría."
- "El port no sabe si el mensaje vino de HTTP o SQS. Eso es por diseño — el use case es agnóstico al canal."
- "Layer-as-Pod no es solo un diagrama de arquitectura — es una herramienta operacional. Escalás la capa que está bajo presión, no la aplicación entera."

## Resiliencia

- "Sin el outbox tenés dos escrituras sin coordinador. Una de ellas va a fallar tarde o temprano. El outbox hace que la DB sea la única fuente de verdad."
- "El circuit breaker es la diferencia entre un sistema degradado y un sistema muerto. Te compra tiempo para recuperarte sin cascada."
- "Bulkheads y circuit breakers son complementarios. El breaker deja de reintentar; el bulkhead garantiza que esa falla no se lleve puesto al barco entero."
- "La idempotency key es el contrato entre caller y servicio. Sin ella, los retries se vuelven bugs."
- "El modelo ML es una optimización, no una dependencia. El fallback no es un modo degradado — es el comportamiento diseñado."

## Performance

- "Presupuesto la latencia como si fuera dinero. Cada hop tiene asignación. Si el modelo ML se pasa de budget, o lo optimizamos o lo sacamos del hot path."
- "Los virtual threads te dan la escalabilidad de lo reactivo sin la complejidad de los callbacks. El código bloqueante se lee como secuencial y escala como async."

## Observabilidad

- "El correlation ID es el hilo que cose un request a través de 4 hops y 3 protocolos. Sin él, tenés líneas de log, no una historia."
- "El error budget convierte a la confiabilidad en una métrica de producto de primer nivel. Cuando el budget está lleno, podés shippear rápido. Cuando está vacío, la confiabilidad es la feature."
- "Un canary es una hipótesis: 'este cambio es seguro'. El AnalysisTemplate es cómo lo falsificamos antes de que llegue al 100% del tráfico."

## Eventos y Kafka

- "La evolución de schemas es un problema de coordinación de equipos disfrazado de problema técnico. El registry hace el contrato explícito y aplicado."
- "La DLQ es la válvula de seguridad. Convierte una falla bloqueante en una falla observable — podés alertar sobre la profundidad de la DLQ y replayar cuando estés listo."

## Testing

- "ATDD cierra el loop entre los requerimientos de negocio y la cobertura de tests. Si el test de aceptación pasa, el requerimiento está cumplido — por definición."
- "TDD en la capa de dominio es barato — sin contenedores, sin red. Los tests corren en milisegundos y te dicen exactamente qué se rompió."

## Staff y Mentoring

- "Construyo sistemas que degradan con elegancia, no que fallan en silencio."

## Backlinks

[[Architecture-Question-Bank]] · [[Design-Anti-Patterns]] · [[Project-Pitch]]
