---
adr: "0039"
title: Configurar EventBusOptions.setHost() en cada Main del PoC distribuido
status: accepted
date: 2026-05-07
tags: [decision/accepted, distributed, vertx, networking, observability]
---

# ADR-0039: Configurar `EventBusOptions.setHost()` en cada Main del PoC distribuido

## Estado

Aceptado, 2026-05-07.

## Contexto

Vert.x clustered mode tiene **dos canales de comunicación inter-pod** que se confunden con frecuencia:

1. **Hazelcast cluster** — discovery + member management (puerto 5701/TCP).
2. **Vert.x EventBus** — un servidor TCP propio que cada nodo abre y advierte a los peers (puerto random + handshake address).

Cuando una app envía `eventBus.request("usecase.evaluate", ..., reply)`, Vert.x:

1. Resuelve el nodo destino vía Hazelcast.
2. Abre conexión TCP al `eventBus.host:port` advertised por el nodo destino.
3. Recibe reply en `__vertx.reply.<uuid>` registrado en SU PROPIO host advertised.

**El bug.** Si `EventBusOptions.host` no se setea, Vert.x usa `InetAddress.getLocalHost().getHostName()`. En Docker eso devuelve el container ID random (ej: `182a8eec74d9`). Los peers no resuelven ese hostname (NXDOMAIN) — solo el service alias de docker-compose (`controller-app`, `usecase-app`, `repository-app`) resuelve.

**Síntoma observado.** `Hazelcast Members{size:3}` formado correctamente; verticles deployados; consumers registrados; pero el primer `request()` timeoutea en 15 s con `address: __vertx.reply.<uuid>, repliedAddress: usecase.evaluate`. Se documenta en `out/e2e-verification/20260507T213455Z/13a-demo-rest-infra.log`.

## Decisión

Cada Main del PoC distribuido (controller, usecase, repository) configura el host del EventBus con una env var inyectada desde docker-compose:

```java
String ebHost = System.getenv().getOrDefault("EVENT_BUS_HOST", "<service-name>");
VertxOptions opts = new VertxOptions()
    .setEventBusOptions(new EventBusOptions()
        .setHost(ebHost)
        .setClusterPublicHost(ebHost));
```

`EVENT_BUS_HOST` proviene de docker-compose con el service alias correspondiente. El default hardcoded al service name se mantiene como defensa.

## Consecuencias

**Positivas**

- EventBus reply addresses se anuncian con hostnames resolvibles → cluster funcional end-to-end.
- Cold-start del primer POST baja a ~110 ms al combinarlo con el AWS SDK warmup fix (Phase 9i).

**Negativas**

- Acopla las apps al naming de docker-compose: los service aliases deben matchear el valor de `EVENT_BUS_HOST`. En k8s habría que migrar a Pod IP o a un headless service.
- `clusterPublicHost` duplica el config — Vert.x necesita ambos para advertise correcto.

**Operacional**

Si una app queda mal configurada, el síntoma es exactamente el mismo (timeout 15 s en reply, sin error de cluster). Diagnóstico recomendado:

```bash
docker exec <container> hostname
docker exec <peer-container> getent hosts <hostname>
```

## Alternativas consideradas

**A — `EventBusOptions.setHost()` con env var (elegida).** Costo: 3 líneas por Main. Riesgo: cosmético. Preserva el modelo de service discovery de docker-compose sin agregar dependencias.

**B — Usar Hazelcast `local.publicAddress` solamente.** Costo: 0. Riesgo alto: NO funciona. `publicAddress` afecta gossip Hazelcast, no Vert.x EventBus host. Probado, descartado.

**C — DNS sidecar / Consul para resolver hostnames de container random.** Costo: complejidad operacional alta (sidecar + registro dinámico). Riesgo: alta superficie operacional para un PoC.

**D — Bind a `0.0.0.0` y advertise el container IP resuelto en runtime.** Costo: medio (resolver IP en runtime, validar). Riesgo: las IPs cambian al recrear containers; menos estable que service names; rompe entre `docker compose down`/`up`.

## Verificación

```bash
# Después del fix
docker exec compose-controller-app-1 sh -c \
  'cat /proc/1/environ | tr "\0" "\n" | grep EVENT_BUS_HOST'
# debe mostrar EVENT_BUS_HOST=controller-app
```

## Referencias

- Vert.x docs — Event Bus: <https://vertx.io/docs/vertx-core/java/#_event_bus>
- Engram observation: topic_key `hazelcast-eventbus-fix`, id 17615.
- Bug surfaced en e2e #3 step 13a: `out/e2e-verification/20260507T213455Z/13a-demo-rest-infra.log`.
