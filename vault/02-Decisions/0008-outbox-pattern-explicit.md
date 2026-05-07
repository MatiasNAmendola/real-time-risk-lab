---
adr: "0008"
title: Outbox Pattern agregado explícitamente en la PoC
status: accepted
date: 2026-05-07
deciders: [Mati]
tags: [decision/accepted, area/events, area/reliability]
---

# ADR-0008: Outbox Pattern — agregado explícitamente en la PoC

## Estado

Aceptado el 2026-05-07.

## Contexto

Durante la investigación de patrones comunes en servicios Go, se observó que muchos servicios productivos publican eventos directamente después de las escrituras a la base de datos dentro de la misma función: un patrón de dual-write que puede perder eventos ante un crash. La escritura a la base se commitea, el proceso muere antes del produce a Kafka y el evento se pierde silenciosamente. Los consumers río abajo nunca ven la decisión y el log de auditoría queda con un hueco.

Para el caso de uso target (riesgo transaccional en tiempo real a 150 TPS), esto representa una brecha arquitectónica significativa. Un evento `DecisionEvaluated` perdido implica: el log de auditoría queda incompleto, el pipeline de analytics de fraude pierde un dato y el feature store de ML puede calcular features incorrectos para la próxima evaluación.

La pregunta es: ¿la PoC debe replicar el enfoque de publicación directa para reflejar la práctica común, o debe demostrar el patrón productivo correcto?

## Decisión

Se agrega el Outbox Pattern en `poc/java-risk-engine/` como `InMemoryOutboxRepository` + `OutboxRelay`. El relay corre en un executor dedicado de virtual threads que poolea el outbox store, publica los eventos pendientes y los marca como publicados. Este es el enfoque arquitectónicamente correcto para un motor de riesgo transaccional.

La PoC de Vert.x publica directo a Kafka (sin outbox), de manera intencional, para evidenciar el contraste entre ambas PoCs.

## Alternativas consideradas

### Opción A: Outbox Pattern con relay por polling (elegida)
- **Ventajas**: entrega exactly-once de eventos, ya que la escritura a la base y el append al outbox son atómicos; el relay publica idempotentemente y marca como publicado solo en éxito; sobrevive a crashes del proceso porque al reiniciar el relay levanta los eventos no publicados; sirve como talking point de diseño: "así implemento entrega confiable de eventos para un servicio audit-critical".
- **Desventajas**: agrega complejidad al PoC bare-javac; el polling suma latencia (intervalo por defecto de unos 100ms); el store en memoria no es durable entre reinicios —un outbox real exige un store persistente (tabla Postgres); el relay requiere un thread de fondo.
- **Por qué se eligió**: la PoC busca demostrar patrones productivos. Mostrar conciencia de las fallas de dual-write y cómo prevenirlas es una señal de diseño directa. La limitación in-memory es aceptable para una PoC; la solución real usaría una tabla `outbox` en Postgres.

### Opción B: Publicación directa
- **Ventajas**: código más simple; sin thread de relay; sin outbox store.
- **Desventajas**: el modo de falla del dual-write es silencioso —commit en base + falla al producir en Kafka termina en un evento perdido sin error visible al caller; no demuestra el patrón correcto; pierde el punto de diseño sobre reconocer la brecha.
- **Por qué no**: el objetivo de la PoC es demostrar conocimiento de modos de falla productivos. Usar un patrón conocido como no confiable transmitiría la señal equivocada.

### Opción C: Change Data Capture con Debezium
- **Ventajas**: enfoque productivamente correcto: Debezium lee el WAL de Postgres y stream-ea cambios a Kafka de forma confiable, sin relay a nivel aplicación; sin polling; sin tabla outbox gestionada por código.
- **Desventajas**: requiere una instancia Postgres corriendo (la PoC bare-javac es in-memory), un connector de Debezium e infraestructura de Kafka Connect; suma tres servicios al stack local; demasiada infraestructura para una PoC.
- **Por qué no**: CDC es la implementación productiva correcta, pero operativamente es muy pesada para una PoC didáctica. La respuesta arquitectónica defendible es: "en producción usaría CDC; para esta PoC uso el Outbox relay para demostrar el concepto".

### Opción D: Kafka transactions (produce exactly-once)
- **Ventajas**: el productor transaccional de Kafka garantiza entrega exactly-once a Kafka; sin tabla outbox separada.
- **Desventajas**: requiere Kafka (la PoC bare-javac no tiene cliente de Kafka); las transacciones requieren transactional producer ID, transaction coordinator y consumer idempotente; resuelve el lado de entrega a Kafka pero no la atomicidad entre escritura en base y publicación —si la base falla después del produce, el evento queda publicado sin registro DB asociado.
- **Por qué no**: las transacciones de Kafka atacan la entrega a Kafka, no la atomicidad entre escritura a base y publicación de evento. El outbox resuelve esa atomicidad; las transacciones de Kafka son complementarias, no alternativas.

## Consecuencias

### Positivo
- La PoC demuestra semántica exactly-once de eventos vía outbox + relay.
- `OutboxRelay` corre sobre un executor de virtual threads, consistente con el tema general de virtual threads.
- Talking point de diseño: "este es el enfoque productivo para entrega confiable de eventos en un motor de riesgo".
- La interfaz `InMemoryOutboxRepository` es reemplazable por `PostgresOutboxRepository` en producción.

### Negativo
- El `InMemoryOutboxRepository` no sobrevive a reinicios; los eventos en el outbox se pierden si la JVM reinicia antes de que el relay los publique.
- El polling agrega unos 100ms de latencia al evento (no a la respuesta HTTP, que se devuelve antes de que el relay corra).
- Suma un thread de fondo `OutboxRelay` al lifecycle gestionado por `RiskApplicationFactory`.

### Mitigaciones
- La limitación in-memory está documentada en `poc/java-risk-engine/README.md`.
- La interfaz `OutboxRepository` permite reemplazo drop-in: `PostgresOutboxRepository` usaría `INSERT INTO outbox ... ON CONFLICT DO NOTHING` + `UPDATE outbox SET status = 'published' WHERE id = ?`.

## Validación

- `OutboxSmokeTest` verifica que una evaluación de riesgo produce una entrada pending en el outbox y que el relay la publica.
- `tests/risk-engine-atdd/` incluye un escenario que verifica que el mismo `idempotencyKey` no produce entradas duplicadas.
- `InMemoryOutboxRepository.saveIfAbsent()` usa `ConcurrentHashMap.putIfAbsent` para thread safety.

## Relacionado

- [[0014-idempotency-keys-client-supplied]]
- [[0015-event-versioning-field]]
- [[Outbox-Pattern]]
- Docs: doc 06 (`docs/06-eventos-versionados.md`)

## Referencias

- Outbox Pattern: https://microservices.io/patterns/data/transactional-outbox.html
- Debezium CDC: https://debezium.io/
- doc 06: `docs/06-eventos-versionados.md`
