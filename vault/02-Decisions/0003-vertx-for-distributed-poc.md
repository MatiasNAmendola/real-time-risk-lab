---
adr: "0003"
title: Vert.x 5 for Distributed PoC
status: accepted
date: 2026-05-07
deciders: [Mati]
tags: [decision/accepted, area/distributed, area/framework]
---

# ADR-0003: Vert.x 5 con Hazelcast para la PoC distribuida

## Estado

Aceptado el 2026-05-07.

## Contexto

La PoC distribuida (`poc/java-vertx-distributed/`) debe demostrar que un layering de clean architecture puede ejecutarse como unidades desplegables físicamente separadas, comunicándose sobre un bus de mensajes tipado: el patrón Layer-as-Pod (ADR-0013). El requerimiento técnico es claro: cuatro capas (controller, usecase, repository, consumer) deben correr como procesos JVM independientes, comunicarse de forma asíncrona, soportar routing cluster-aware e integrarse con OTEL tracing end-to-end.

Spring Boot puede correr como microservicios separados, pero la comunicación entre servicios requiere REST o infraestructura de messaging (Kafka, RabbitMQ, Spring Cloud Bus). El modelo de event bus —donde cualquier verticle puede dirigirse a otro por nombre sin conocer la dirección de red— mapea más directamente al modelo conceptual de layer-as-pod.

La restricción es la señal de diseño: el framework debe defenderse no como "la opción segura" sino como "la herramienta correcta para este patrón".

## Decisión

Se elige Vert.x 5 (`io.vertx:vertx-stack-depchain:5.0.12`) con Hazelcast TCP cluster manager para la PoC distribuida. Cada módulo Gradle (`controller-app`, `usecase-app`, `repository-app`, `consumer-app`) se empaqueta como fat JAR independiente. El event bus clusterizado de Vert.x maneja el messaging intra-cluster; Kafka (Redpanda) maneja la publicación asíncrona de eventos hacia consumers externos.

## Alternativas consideradas

### Opción A: Vert.x 5 con Hazelcast cluster manager (elegida)
- **Ventajas**: el event bus clusterizado provee routing automático a cualquier verticle por dirección, sin configuración de service discovery; el modelo reactivo non-blocking se alinea con el target de p99 de 300ms; incluye WebSocket, SSE y router HTTP en un único módulo, sin web framework adicional; el agente Java de OTEL instrumenta los handlers HTTP de Vert.x de forma transparente; la lista TCP de miembros de Hazelcast resuelve las limitaciones de multicast en la bridge de Docker; Vert.x 5 corre en producción a escala comparable en servicios transaccionales.
- **Desventajas**: el modelo de verticle (callbacks asíncronos o coroutines de Kotlin) tiene curva de aprendizaje frente al modelo síncrono de Spring; la formación del cluster Hazelcast sobre la bridge de Docker requiere lista TCP explícita porque multicast no funciona; Vert.x es menos mainstream que Spring Boot en el ecosistema Java de Latinoamérica.
- **Por qué se eligió**: el modelo de event bus hace el patrón Layer-as-Pod demostrable sin boilerplate REST. Una llamada al event bus clusterizado se ve idéntica a una llamada local: la distribución es transparente a nivel aplicación.

### Opción B: Spring Boot + Spring Cloud + Spring Integration Bus
- **Ventajas**: máxima familiaridad mainstream; Spring Cloud Bus + RabbitMQ provee messaging entre servicios similar; la auto-configuración reduce boilerplate; la mayoría de los ingenieros Java lo conocen.
- **Desventajas**: "Layer-as-Pod con Spring Boot" implica cuatro microservicios comunicándose vía REST o Spring Cloud Bus —REST agrega serialización HTTP en cada borde de capa; Spring Cloud Bus requiere RabbitMQ/Kafka como transporte, sumando una dependencia de infraestructura a la capa de framework; el startup de Spring Boot es de 2-4 segundos por servicio contra menos de 1 segundo de Vert.x.
- **Por qué no**: el objetivo es demostrar el modelo de comunicación por event bus entre capas. Spring no tiene un event bus clusterizado nativo; el equivalente más cercano (Spring Cloud Bus) suma infraestructura que oscurece el patrón arquitectónico.

### Opción C: Quarkus + Mutiny + Vert.x Eventbus (embebido)
- **Ventajas**: historia reactiva fuerte con Mutiny; Quarkus puede embeber una instancia de Vert.x; la compilación nativa con GraalVM es un argumento de performance fuerte; modelo non-blocking similar a Vert.x standalone.
- **Desventajas**: Quarkus + Vert.x embebido es una combinación de nicho con pocos ejemplos; el soporte de cluster manager en Quarkus es menos maduro que en Vert.x standalone; la capa adicional de Quarkus suma curva de aprendizaje sin agregar capacidades sobre Vert.x standalone para este caso de uso.
- **Por qué no**: Vert.x standalone es más limpio para demostrar el patrón de event bus que Quarkus embebiendo Vert.x. La capa extra agrega complejidad sin beneficio proporcional.

### Opción D: Micronaut + gRPC para comunicación entre capas
- **Ventajas**: Micronaut tiene soporte gRPC sólido; DI en compile-time (sin reflection); fuertes características de performance; gRPC entre capas es type-safe.
- **Desventajas**: gRPC entre capas requiere archivos `.proto` para cada interfaz inter-capa, con overhead significativo; el request-response síncrono de gRPC no provee semántica de broadcast/fan-out de un event bus; no hay cluster manager nativo equivalente a Hazelcast.
- **Por qué no**: gRPC modela request-response síncrono; el event bus modela routing asíncrono de mensajes con fan-out. Para Layer-as-Pod, el modelo de event bus es más fiel.

## Consecuencias

### Positivo
- El event bus clusterizado vuelve transparente el Layer-as-Pod: `eventBus.request("usecase.evaluate", payload)` funciona igual aunque `usecase-app` esté local o en otro nodo.
- Vert.x Web Router maneja HTTP, SSE, WebSocket y webhook fan-out con una sola dependencia.
- El agente Java de OTEL instrumenta los handlers HTTP de Vert.x automáticamente; el contexto de trace se propaga sin instrumentación manual.
- La suite ATDD (Karate) cubre los 9 canales de comunicación contra el stack levantado.

### Negativo
- La lista TCP de miembros de Hazelcast requiere IPs hardcodeadas o hostnames resolubles por DNS; en k8s se resuelve con headless service DNS, pero requiere configuración explícita.
- El modelo de verticle es menos familiar para ingenieros que vienen de Spring Boot; el code review requiere conocimiento de Vert.x.
- Overhead de aproximadamente 19ms por request frente al modo in-process (documentado en doc 12).

### Mitigaciones
- La lista TCP de Hazelcast se configura vía variables de entorno en docker-compose.yml y ConfigMap de k8s.
- La documentación de Vert.x 5 y el ADR-0013 explican el modelo de event bus.

## Validación

- `docker compose up -d && curl localhost:8080/health` devuelve 200 desde `controller-app`.
- La suite ATDD Karate (`atdd-tests/`) pasa todos los escenarios HTTP, SSE, WebSocket, Kafka y webhook.
- Los traces OTEL en OpenObserve muestran spans cruzando `controller-app → usecase-app → repository-app`.

## Relacionado

- [[0013-layer-as-pod]]
- [[0030-redpanda-vs-kafka]]
- [[0001-java-25-lts]]
- [[Layer-as-Pod]]

## Referencias

- Vert.x Clustered EventBus: https://vertx.io/docs/vertx-hazelcast/java/
- Vert.x 5 release: https://vertx.io/blog/eclipse-vert-x-5-is-here/
- doc 12: `docs/12-rendimiento-y-separacion.md`
