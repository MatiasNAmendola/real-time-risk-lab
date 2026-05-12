---
adr: "0013"
title: Layer-as-Pod — cada capa arquitectónica en una JVM separada
status: accepted
date: 2026-05-07
deciders: [Mati]
tags: [decision/accepted, area/architecture, area/distributed]
---

# ADR-0013: Layer-as-Pod — cada capa arquitectónica en una JVM separada

## Estado

Aceptado el 2026-05-07.

## Contexto

La PoC distribuida (`poc/vertx-layer-as-pod-eventbus/`) debe demostrar que un layering de clean architecture —controller, usecase, repository, consumer— puede materializarse como unidades desplegables físicamente separadas en lugar de paquetes dentro de un único binario. Este es el patrón "layer-as-pod": cada capa se corresponde con un módulo Gradle, un contenedor Docker y un pod de Kubernetes, comunicándose exclusivamente a través del event bus clusterizado de Vert.x.

La motivación viene del target productivo: a esta escala (150 TPS de riesgo transaccional), los equipos pueden necesitar escalar capas de forma independiente. La capa controller recibe ráfagas HTTP; la capa repository queda limitada por las conexiones a Postgres; el scorer ML (capa usecase) es CPU-bound. Empaquetar todas las capas en un único binario fuerza el mismo replica count para todas las preocupaciones.

La restricción es la complejidad: un binario único con interfaces internas es mucho más simple de operar. Distribuir capas a través de procesos JVM agrega coordinación de cluster, latencia de red (medidos unos 19ms), límites de aislamiento de fallas y requerimientos de tracing distribuido.

## Decisión

Se estructura `poc/vertx-layer-as-pod-eventbus/` como un reactor Gradle de cinco módulos donde cada módulo no compartido (`controller-app`, `usecase-app`, `repository-app`, `consumer-app`) compila a un fat JAR independiente y corre en un contenedor Docker separado. El módulo `shared` contiene value objects y contratos de eventos. La comunicación entre capas usa el event bus clusterizado de Vert.x con Hazelcast TCP cluster manager. Cada módulo tiene exactamente una clase `Main` que bootstrapea solo sus propios verticles.

## Alternativas consideradas

### Opción A: Layer-as-Pod vía event bus clusterizado de Vert.x (elegida)
- **Ventajas**: la separación física queda enforced por el límite del proceso, no por convención; el escalado por capa es posible en k8s; el event bus de Vert.x provee messaging asíncrono type-safe con delivery options (timeout, semántica de reply); la formación del cluster es automática una vez configurada la lista TCP de Hazelcast; mapea directo al narrative de diseño sobre escalado independiente.
- **Desventajas**: unos 19ms adicionales de latencia por request frente al modo in-process (medidos en bench/); el multicast de Hazelcast no funciona en redes bridge de Docker, hace falta lista TCP explícita; cinco JVMs contra una significan unos 600MB de RSS contra unos 150MB; el tracing distribuido es necesario para debuggear flujos cross-process.
- **Por qué se eligió**: la separación es el punto. Un revisor que pregunta "¿cómo escalarías la capa de scoring de forma independiente?" recibe una demo corriendo, no un diagrama de pizarrón.

### Opción B: binario único, capas como paquetes (modular monolith)
- **Ventajas**: operación más simple; sin cluster manager; sin serialización de red; menor latencia; un solo JVM significa heap compartido y debugging más fácil.
- **Desventajas**: no permite demostrar escalado independiente; no permite enforcear en runtime que el controller no llame directamente a código del repository; no muestra conocimiento de sistemas distribuidos.
- **Por qué no**: la PoC bare-javac ya cubre esto. Dos PoCs con la misma topología no aportan señal adicional.

### Opción C: microservicios con REST entre capas
- **Ventajas**: protocolo neutral entre capas (HTTP); fácilmente observable con herramientas estándar; cada capa es desplegable de forma independiente sin cluster manager.
- **Desventajas**: REST entre capas acopla el protocolo a la arquitectura; suma serialización HTTP en cada borde de capa; no hay semántica de broadcast/fan-out sin infraestructura adicional; más boilerplate por servicio.
- **Por qué no**: el modelo de event bus refleja mejor la estrategia de messaging objetivo (Kafka para asíncrono, event bus para sync intra-cluster). REST entre capas crearía una cadena síncrona chatty, no un pipeline reactivo.

### Opción D: capas como pods separados de Kubernetes compartiendo base de datos, sin event bus
- **Ventajas**: cada pod es desplegable y escalable de forma independiente; sin dependencia de cluster manager; patrones k8s estándar.
- **Desventajas**: requiere un data store compartido para coordinación síncrona; elimina la característica event-driven; en la práctica deviene en microservicios-con-DB-compartida, un anti-patrón.
- **Por qué no**: microservicios con base compartida son un anti-patrón de deployment que socava el modelo de bounded context.

## Consecuencias

### Positivo
- El escalado por capa en k8s es demostrable: `kubectl scale deployment usecase-app --replicas=4`.
- El aislamiento de fallas es real: si `repository-app` crashea, `controller-app` recibe un timeout desde el event bus, no un NullPointerException.
- Fuerza contratos de eventos explícitos en `shared/`, sin acoplamiento accidental por estado mutable compartido.
- Los traces OTEL end-to-end cruzan los límites de proceso de forma visible en OpenObserve.

### Negativo
- Unos 19ms de overhead por request frente al modo in-process (medidos, documentados en doc 12).
- La lista TCP de Hazelcast debe actualizarse cuando cambian las IPs de los pods; en k8s se maneja con headless service DNS, pero requiere configuración explícita.
- Cinco arranques de JVM para una demo local; `docker compose up` toma unos 30 segundos.
- El debugging requiere correlación de traces distribuidos; no hay un único stream de logs.

### Mitigaciones
- doc 12 documenta el overhead de 19ms explícitamente y lo contextualiza: los 20-160ms del scorer ML dominan, dejando el overhead de distribución como efecto de segundo orden.
- Hazelcast queda configurado con discovery por DNS en los manifests de k8s (`poc/k8s-local/`).
- El script `docker compose up -d` maneja el orden de arranque vía `depends_on` con healthchecks.

## Validación

- `docker compose up` levanta los cinco contenedores; `curl localhost:8080/health` devuelve 200 desde `controller-app`.
- El script de bench distribuido (`bench/scripts/run-distributed.sh`) mide latencia p99 end-to-end.
- Los traces OTEL en OpenObserve muestran spans cruzando `controller-app → usecase-app → repository-app` con `traceId` único.

## Relacionado

- [[0003-vertx-for-distributed-poc]]
- [[0012-two-parallel-pocs]]
- [[0030-redpanda-vs-kafka]]
- Docs: doc 12 (números de performance), doc 06 (contratos de eventos)

## Referencias

- Vert.x Clustered EventBus: https://vertx.io/docs/vertx-hazelcast/java/
- doc 12: `vault/04-Concepts/In-Process-vs-Distributed.md`
