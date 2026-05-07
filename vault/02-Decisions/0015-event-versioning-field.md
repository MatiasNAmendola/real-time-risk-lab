---
adr: "0015"
title: Versionado de eventos vía campo eventVersion (no Schema Registry)
status: accepted
date: 2026-05-07
deciders: [Mati]
tags: [decision/accepted, area/events, area/schema]
---

# ADR-0015: Versionado de eventos vía campo eventVersion, no Avro + Schema Registry

## Estado

Aceptado el 2026-05-07.

## Contexto

Los eventos publicados por el motor de riesgo (`DecisionEvaluated`, `FallbackApplied`) son consumidos por al menos tres sistemas río abajo: el servicio de audit log, el pipeline de features de ML y el equipo de analytics de fraude. Cuando el schema del evento evoluciona —agregar `featureSnapshot` en v2, agregar `explainabilityTokens` en una hipotética v3— los consumers que solo entienden v1 no deben romperse.

Existen dos enfoques estándar: embeber un indicador de versión en el envelope del evento, o usar un schema registry (Avro + Confluent Schema Registry, o Protobuf + Buf) que enforcea compatibilidad en tiempo de produce. Una tercera opción liviana es JSON Schema con referencias `$schema`.

La restricción de la PoC es local-first: el ambiente de desarrollo corre en una sola MacBook Pro con k3d y Redpanda. Un schema registry suma un servicio stateful, un daemon de enforcement de compatibilidad y dependencias de SDK. Para una PoC que demuestra intención arquitectónica, el valor marginal de un schema registry sobre un campo de versión bien diseñado debe justificar el costo operativo.

## Decisión

Se embebe `eventVersion` como string top-level en cada envelope. Los incrementos de versión son semánticos: v1 → v2 es backward-compatible (solo nuevos campos opcionales); un breaking change requiere un nuevo tipo de evento, no incrementar la versión de uno existente. El campo `schemaRef` (`urn:naranjax:risk:DecisionEvaluated:1`) provee un identificador estable y resoluble para una futura adopción de schema registry sin requerirlo ahora.

Los consumers inspeccionan `eventVersion` para decidir si usan los campos extendidos o caen al procesamiento v1. El diseño del envelope (todos los campos nuevos opcionales, los campos v1 nunca se eliminan) garantiza que un consumer v1 ignore silenciosamente campos desconocidos al deserializar JSON.

## Alternativas consideradas

### Opción A: campo eventVersion en el envelope JSON (elegida)
- **Ventajas**: cero infraestructura adicional; la versión queda visible en el payload sin lookup externo; la evolución backward-compatible se logra por disciplina (agregar campos opcionales, nunca eliminar); el campo `schemaRef` preserva un camino de migración a schema registry; la deserialización permisiva de JSON absorbe campos desconocidos automáticamente.
- **Desventajas**: la compatibilidad queda enforced por convención, no por tooling —un producer puede publicar un breaking change sin error en build; no hay catálogo central de schemas; el descubrimiento exige leer código fuente o documentación.
- **Por qué se eligió**: para una PoC y una plataforma en etapa temprana, el requerimiento de disciplina es manejable. La URN de `schemaRef` en cada evento implica que sumar un schema registry más adelante es un ejercicio de mapping, no un rediseño de schemas. La señal de diseño es: "sé qué hace un schema registry y por qué lo necesitaríamos a escala; elegí no sumarlo acá porque el costo operativo no se justifica en esta etapa".

### Opción B: Avro + Confluent Schema Registry
- **Ventajas**: enforcement de compatibilidad en tiempo de produce (modos BACKWARD, FORWARD, FULL); la serialización binaria es más compacta que JSON; las reglas de evolución quedan codificadas y son chequeables por máquina; estándar en organizaciones Kafka-heavy.
- **Desventajas**: agrega un servicio stateful Schema Registry (Confluent o Apicurio) al stack local; Avro requiere generación de código o deserialización por reflection; el formato binario es más difícil de inspeccionar sin herramientas; Confluent Schema Registry tiene licencia BSL para la versión community (a 2023); suma unos 20ms al primer produce mientras busca el schema ID.
- **Por qué no**: el costo operativo —servicio stateful adicional, paso de generación de código y consideración de licencia— no se justifica para una PoC. Las garantías que da Avro se logran a esta escala con disciplina de diseño del envelope.

### Opción C: Protobuf + Buf Schema Registry
- **Ventajas**: mejor soporte multilenguaje que Avro; Buf schema registry tiene licencia MIT; tipado fuerte; binario compacto.
- **Desventajas**: Protobuf exige paso de compilación de .proto y stubs Java generados; no es estándar en stacks Go JSON-first; suma complejidad al toolchain de build.
- **Por qué no**: Protobuf agrega valor cuando la type safety entre lenguajes es crítica y la compilación de schemas ya está en el pipeline. Ninguna condición se cumple en esta PoC.

### Opción D: JSON Schema con referencias `$schema` (validación inline)
- **Ventajas**: legible por humanos; sin encoding binario; compatible con tooling JSON existente; la URI `$schema` ya está presente en el diseño del envelope vía `schemaRef`.
- **Desventajas**: la validación de JSON Schema en runtime suma costo de CPU por evento; los catálogos de schema deben distribuirse a los consumers; no hay enforcement nativo en el producer de Kafka; tooling limitado frente a Avro/Protobuf.
- **Por qué no**: logra el beneficio de documentación del versionado de schemas sin la garantía de enforcement. El campo `schemaRef` actual ya provee el ancla URN para un binding futuro a JSON Schema.

## Consecuencias

### Positivo
- El envelope es self-describing: `eventType + eventVersion` alcanza para que un consumer elija el deserializador correcto.
- Sin dependencia de infraestructura adicional.
- El campo `schemaRef` (`urn:naranjax:risk:{eventType}:{version}`) provee un handle estable para integración futura con schema registry.
- Los breaking changes requieren nuevo tipo de evento por convención, lo que naturalmente fuerza un plan de migración para los consumers.

### Negativo
- Sin enforcement de reglas de compatibilidad en build time; un producer que elimine un campo requerido no falla en compilación.
- Sin catálogo central de schemas; el descubrimiento exige leer código o documentación.
- El tamaño JSON es mayor que Avro/Protobuf; a 150 TPS es despreciable, pero a 10.000 TPS importa.

### Mitigaciones
- El contrato de schema queda documentado en doc 06 con ejemplos v1 y v2 y reglas explícitas de compatibilidad.
- Los contratos de consumer se testean en escenarios ATDD: `07_idempotency.feature` y los tests del consumer de Kafka verifican que los consumers v1 reciben correctamente los campos del envelope v1.
- A escala productiva, la migración a Avro + Schema Registry sigue la URN de `schemaRef`: la URL del registry se vuelve resolver del namespace `urn:naranjax:risk:`.

## Validación

- Los ejemplos de eventos en doc 06 muestran payloads v1 y v2 con todos los campos nuevos opcionales en v2.
- Los tests ATDD Karate verifican que el evento consumido desde Kafka contiene los campos `eventVersion`, `eventType` e `idempotencyKey`.
- `DecisionEvaluated` v1 y v2 son JSON válidos con diferencias solo aditivas.

## Relacionado

- [[0014-idempotency-keys-client-supplied]]
- [[0008-outbox-pattern-explicit]]
- Docs: doc 06 (`docs/06-eventos-versionados.md`)

## Referencias

- Confluent Schema Registry: https://docs.confluent.io/platform/current/schema-registry/
- Buf schema registry: https://buf.build/
- doc 06: `docs/06-eventos-versionados.md`
