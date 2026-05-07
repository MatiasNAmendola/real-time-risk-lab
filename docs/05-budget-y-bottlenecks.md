# 05 — Budget de latencia y diagnóstico de bottlenecks

## 1. Por qué hablar de budget, no de promedio

When the system is not reaching 300ms, la respuesta correcta no es "le agrego más pods".
La respuesta es: "¿p99 o p95? ¿End-to-end o solo el engine? ¿Qué tramo corre más lento?".

Frase útil:

> "No puedo optimizar lo que no mido. El primer paso es tracing distribuido con p95/p99 por tramo,
> no escalar por intuición."

---

## 2. Budget de latencia por dependencia

Presupuesto para un SLA de 300ms p99 end-to-end:

| Tramo | Budget sugerido | Tipo |
|---|---:|---|
| API Gateway / LB / parsing de request | 10 ms | red + CPU |
| Autenticación / auth token validation | 15 ms | red o cache |
| Obtención de features (cache local) | 10 ms | CPU + memoria |
| Obtención de features (cache remota, Redis) | 25 ms | red + I/O |
| Reglas determinísticas (motor de decisión) | 20 ms | CPU |
| Modelo ML online (con timeout estricto) | 60 ms | red + I/O |
| Persistencia mínima (audit record / outbox write) | 25 ms | I/O |
| Serialización de respuesta | 5 ms | CPU |
| Red interna + overhead de framework | 30 ms | red |
| Margen para GC, jitter, cola de threads | 100 ms | JVM / OS |
| **Total** | **300 ms** | |

Notas:
- Si el modelo ML supera su timeout (ej. 60ms), se activa fallback antes de que afecte el budget total.
- El margen de GC/jitter importa más en p99 que en p50: un GC pause de 80ms puede romper el SLA.
- Si hay cold start (Lambda o JVM sin warmup), este budget no aplica hasta que el proceso este caliente.

---

## 3. Checklist de diagnóstico: orden correcto

```
1. Obtener traza distribuida real (no logs puntuales):
   - Herramienta: OpenTelemetry + Jaeger/Zipkin/X-Ray/Datadog APM
   - Mirar p95 y p99 por span, no promedios
   - Identificar el tramo con mayor latencia absoluta y mayor varianza

2. Confirmar si el problema es consistente o jitter (p50 ok pero p99 roto):
   - Jitter en p99 -> GC, lock contention, pool exhaustion
   - Latencia alta consistente -> I/O lento, query sin índice, red entre zonas AZ

3. Identificar categoría del bottleneck (ver sección 4)

4. Aplicar herramienta específica a esa categoría

5. Verificar mejora con métrica, no con "se siente más rápido"
```

---

## 4. Categorías de bottleneck: herramienta, señal, mitigación

### 4.1 CPU (lógica pura, serialización, regex, GC)

| Campo | Detalle |
|---|---|
| Herramienta | async-profiler, JFR (Java Flight Recorder), flamegraphs |
| Señal típica | CPU > 70% sostenido, flamegraph muestra hotspot en parser/regex/serialización |
| Mitigación | Cachear resultados costosos, evitar regex en hot path, cambiar formato (JSON -> protobuf), thread pool sizing |

Comando JFR básico:
```bash
jcmd <pid> JFR.start duration=60s filename=/tmp/recording.jfr
jcmd <pid> JFR.dump filename=/tmp/recording.jfr
# abrir con JDK Mission Control o convertir con jfr print
```

### 4.2 I/O bloqueante (DB, HTTP externo, disco)

| Campo | Detalle |
|---|---|
| Herramienta | Tracing por span + OpenTelemetry JDBC instrumentation, hikari metrics (Micrometer) |
| Señal típica | Span de DB > 50ms, hikari.connections.pending > 0, slow query log activo |
| Mitigación | Revisar queries con EXPLAIN, agregar índices, connection pool sizing, cache de resultados frecuentes, timeouts por dependencia |

```yaml
# Micrometer + Hikari: exponer métricas de pool
management.metrics.enable.hikaricp: true
# alertar si hikari.connections.pending > 0 por mas de 500ms
```

### 4.3 Red (latencia entre servicios, entre AZ, externa)

| Campo | Detalle |
|---|---|
| Herramienta | OpenTelemetry span (client vs server), AWS X-Ray, network flow logs |
| Señal típica | Diferencia grande entre span client y span server (time in flight), latencia inter-AZ > 5ms |
| Mitigación | Agrupar dependencias en misma AZ si es posible, reducir hops innecesarios, HTTP/2 keep-alive, connection pool para HTTP clients |

### 4.4 Modelo ML (servicio externo o endpoint SageMaker)

| Campo | Detalle |
|---|---|
| Herramienta | Span dedicado para ML call, percentiles propios del endpoint (SageMaker metrics) |
| Señal típica | Span ML > 60ms p99, alta varianza (spikes cada N requests = cold container en SageMaker) |
| Mitigación | Timeout estricto + circuit breaker + fallback, provisioned concurrency en SageMaker, feature caching, considerar mover ML a asincrónico |

### 4.5 Contención / locks

| Campo | Detalle |
|---|---|
| Herramienta | async-profiler modo "lock", JFR event monitor blocked threads, thread dump (`jstack`) |
| Señal típica | Flamegraph muestra muchos frames en `java.util.concurrent.locks`, thread dump con BLOCKED threads esperando el mismo monitor |
| Mitigación | Reemplazar synchronized por ConcurrentHashMap/CAS/immutable state, revisar singleton stateful, partition por correlationId para evitar contención |

### 4.6 GC / JVM

| Campo | Detalle |
|---|---|
| Herramienta | JFR GC events, GC log (`-Xlog:gc*`), Micrometer jvm.gc.pause |
| Señal típica | GC pauses de 50-200ms en p99, heap casi lleno, muchas allocations por request (objetos temporales grandes) |
| Mitigación | Ajustar heap size, cambiar GC collector (G1 -> ZGC para latencia), reducir allocations en hot path, object pooling donde aplique |

```bash
# Activar GC logging en JVM (Spring Boot / Java 21+)
JAVA_OPTS="-Xlog:gc*:file=/var/log/gc.log:time,uptime,level,tags"
```

### 4.7 Serialización

| Campo | Detalle |
|---|---|
| Herramienta | JFR + flamegraph, benchmark con JMH |
| Señal típica | Flamegraph muestra hotspot en Jackson / protobuf / Avro deserializer |
| Mitigación | Configurar ObjectMapper como singleton (no new por request), habilitar módulos de Jackson para Java 8 types, evaluar protobuf si el payload es masivo |

### 4.8 Logging sincrónico

| Campo | Detalle |
|---|---|
| Herramienta | JFR I/O events, comparar latencia con logback vs async appender |
| Señal típica | Threads bloqueados en FileOutputStream o SocketAppender durante requests |
| Mitigación | Cambiar a AsyncAppender (Logback), filtrar log level a WARN en producción, evitar logging de payloads grandes en hot path |

```xml
<!-- logback.xml: async appender -->
<appender name="ASYNC" class="ch.qos.logback.classic.AsyncAppender">
    <queueSize>512</queueSize>
    <discardingThreshold>0</discardingThreshold>
    <appender-ref ref="STDOUT"/>
</appender>
```

---

## 5. Matriz síntoma → hipótesis → herramienta → fix probable

| Síntoma observable | Hipótesis | Herramienta | Fix probable |
|---|---|---|---|
| p99 roto, p50 ok | GC pause, lock contention, pool exhaustion | JFR + thread dump | ZGC, reducir allocations, aumentar pool |
| Latencia alta y consistente en un tramo | Query lenta, timeout default alto, hop extra | Tracing span + EXPLAIN query | Índice, timeout explícito, eliminar hop |
| Spike de latencia cada N requests | Cold container ML, cold thread, warmup JVM | Correlacionar con ML span + JVM start events | Provisioned concurrency, warmup endpoint, JVM warmup |
| Latencia crece con TPS (no con el tiempo) | Pool agotado, backpressure ausente | Hikari metrics, thread pool queue size | Aumentar pool, bulkhead, rechazar requests con 429 |
| Latencia alta solo en algunos pods | GC config distinta, JVM flag diferente, versión de lib | JFR comparativo entre pods | Uniformar JVM flags, actualizar dependencia |
| Latencia alta en endpoints nuevos | Missing cache warm, query sin índice en tabla nueva | Tracing + slow query log | Warmup explícito, índice, cache pre-populada |
| ML p99 > 200ms | SageMaker cold container, instancia insuficiente | Span ML + SageMaker CloudWatch | Provisioned concurrency, instancia más grande, fallback |

---

## 6. Anti-Patterns to Avoid

### Escalar pods sin diagnóstico

Agregar réplicas solo escala el throughput si el bottleneck es CPU pura. Si el problema es una query lenta, una dependencia bloqueante o contención en un recurso compartido, más pods empeoran la situación (más conexiones al mismo DB, más contención en el mismo endpoint ML).

Frase útil:
> "Escalar sin entender el bottleneck es amplificar el problema."

### Optimizar por intuición

Reescribir código "que parece lento" sin medir es tiempo perdido. La sección más obvia del flamegraph no siempre es el hotspot real en producción.

### Microbenchmarks fuera de contexto

JMH es útil para aislar una función, pero no reemplaza a profiling en producción real. Un serializer que en JMH tarda 1µs puede tardar 10ms en producción por contención de GC, tamaño real de payload o thread scheduling. Siempre validar en staging con tráfico realista.

### Timeout único global

Un timeout de 5 segundos global para todas las dependencias hace que un fallo de ML afecte el budget completo. Cada dependencia debe tener su propio timeout, alineado con el budget asignado.

### Cache sin TTL o TTL demasiado largo

Features de cliente cacheadas sin expiración pueden tomar decisiones sobre datos obsoletos. En fraude, datos viejos tienen riesgo real. El TTL debe ser explícito y documentado como parte del diseño.
