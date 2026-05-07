# 09 — Banco de preguntas de arquitectura con análisis modelo

Un banco de preguntas de arquitectura con análisis modelo. Cada entrada es una herramienta pedagógica y un ejercicio arquitectónico. Las preguntas están organizadas por bloque temático con dificultad creciente dentro de cada bloque. El bloque G contiene trampas deliberadas — preguntas diseñadas para exponer fallas de razonamiento comunes.

## Cómo usar este doc

Cada entrada tiene:
- **Pregunta** — formulada directamente, como aparecería en una discusión de diseño.
- **Qué mide** — el criterio o dimensión que se evalúa.
- **Análisis modelo** — 4-8 líneas con principios clave embebidos.
- **Modo de falla común** — cómo se ve una respuesta floja.

---

## Bloque A — Diseño de sistema de fraude en tiempo real

### A1. "Diseñá un sistema de fraude en tiempo real para 150 TPS y SLA de 300ms."

**Qué mide:** separación de camino crítico vs flujo async, claridad de SLA, disciplina de Clean Architecture.

**Análisis modelo:**
> El primer paso es confirmar si los 300ms son p99 o media end-to-end — eso cambia todo. Después separo el flujo: el camino crítico mantiene validación, features cacheadas, reglas determinísticas, scoring de ML con timeout duro y un trace mínimo; el flujo async maneja auditoría enriquecida, eventos y analytics. Cada dependencia tiene timeout, circuit breaker y fallback explícito. Todo el flujo viaja con un correlationId y un idempotencyKey. La arquitectura es hexagonal: el core de decisión no sabe si corre en Lambda o en un contenedor — el runtime es un adapter.

**Modo de falla común:** listar tecnologías sin clarificar primero los supuestos de SLA.

---

### A2. "¿Cómo definís un presupuesto de latencia por dependencia?"

**Qué mide:** pensamiento cuantitativo, conocimiento de GC y jitter en p99.

**Análisis modelo:**
> Empiezo del SLA total (300ms p99) y descompongo por componente: auth ~15ms, features cache local ~10ms, features cache remoto ~25ms, reglas ~20ms, ML ~60ms con timeout duro, persistencia mínima ~25ms, overhead de framework/red ~30ms, margen de GC/jitter ~100ms. ML se queda con el slot más generoso porque es la dependencia más variable. Si lo excede, el circuit breaker activa el fallback antes de consumir el margen de GC. El margen de GC pesa más en p99 que en p50: una pausa de 80ms es invisible en la media pero destruye p99.

**Modo de falla común:** dar latencias sin margen de GC/jitter, o sin justificar por qué ML se queda con el slot más grande.

---

### A3. "¿Cómo garantizás que una decisión pueda explicarse 6 meses después?"

**Qué mide:** pensamiento de auditoría y compliance, no solo debugging operacional.

**Análisis modelo:**
> El decision trace es un objeto de primera clase, no una entrada de log. Guarda: transactionId, correlationId, versión del ruleset, versión del modelo de ML, featureVersion, features usados o snapshot en ese momento, score, reglas activadas, fallbacks aplicados y motivo, dependencias que fallaron. Se persiste en el mismo commit de DB que la decisión (o vía outbox). El objetivo es reconstruir exactamente qué predijo el sistema para esa transacción con esos datos, sin importar qué versión corre hoy. Sin eso, no hay auditoría real — solo logs.

**Modo de falla común:** confundir logs de observabilidad con traces de auditoría — son objetos distintos con durabilidad y semántica distintas.

---

### A4. "¿Cómo diseñás idempotencia en el motor de decisión?"

**Qué mide:** experiencia en sistemas distribuidos, manejo de entrega at-least-once.

**Análisis modelo:**
> Cada request lleva un idempotencyKey determinístico (idealmente generado por el canal que llama, derivado del ID de transacción y número de intento). El engine consulta un store de idempotencia antes de procesar: si la key existe, devuelve la decisión guardada sin reevaluar. Si no existe, procesa, persiste decisión e idempotencyKey en una sola transacción y devuelve el resultado. El store puede ser Redis con TTL o una tabla de DB. Los eventos publicados también llevan idempotencyKey para que los consumers downstream puedan deduplicar. El objetivo es que un retry de un request que hizo timeout no genere una segunda decisión ni un segundo evento.

**Modo de falla común:** describir idempotencia solo en el borde de la API REST e ignorar la idempotencia downstream de eventos.

---

### A5. "¿Cómo desplegás un cambio de reglas de fraude de manera segura?"

**Qué mide:** separación entre deploy de código y deploy de configuración/reglas, experiencia con feature flags.

**Análisis modelo:**
> Las reglas versionadas son un contrato. Un cambio de reglas no requiere redeploy de código si el engine las carga dinámicamente desde un config store o feature flag. La secuencia de rollout: shadow (la versión nueva corre en paralelo y compara resultados sin afectar decisiones), canary (5-10% del tráfico con monitoreo de distribución de decisiones y métricas de negocio), después roll completo. El rollback significa revertir el feature flag a la versión anterior. Cada decisión registra qué versión de ruleset usó, así el rollback es auditable.

**Modo de falla común:** tratar reglas y código como la misma unidad de deploy.

---

### A6. "¿Cómo evitás que dos instancias del engine tomen decisiones distintas para la misma transacción?"

**Qué mide:** idempotencia distribuida, diferencia entre determinismo del engine e idempotencia del sistema.

**Análisis modelo:**
> Con idempotencia centralizada. Antes de procesar, cada instancia consulta un store compartido (Redis o DB) usando el idempotencyKey del request. Si la key existe, devuelve la decisión guardada sin reevaluar. Si no existe, intenta un upsert atómico. Eso garantiza que aunque dos instancias reciban el mismo request simultáneamente (retry, entrega at-least-once), solo una toma la decisión y la segunda devuelve el resultado ya guardado. El motor de decisión en sí debe ser determinístico para el mismo input: mismos features + mismas reglas + mismo modelo = mismo output, lo que simplifica la estrategia.

**Modo de falla común:** asumir que el determinismo del engine alcanza para resolver concurrencia sin necesidad de idempotencia centralizada.

---

## Bloque B — Performance y cuellos de botella

### B1. "No estamos llegando a 300ms. ¿Cómo empezás a diagnosticar?"

**Qué mide:** metodología de diagnóstico, no soluciones por intuición; conocimiento de tooling.

**Análisis modelo:**
> Primero, tracing distribuido: p95 y p99 por span, no promedios. Quiero ver qué componente tiene la latencia absoluta más alta y cuál tiene más jitter (diferencia entre p50 y p99). Si p50 está bien y p99 está roto, el problema es jitter: pausa de GC, contención de locks, agotamiento de pool. Si la latencia es alta y consistente, es I/O o una query lenta. Cada categoría tiene su herramienta: JFR + flamegraph para CPU/GC, Micrometer + métricas de Hikari para pool, EXPLAIN para queries, span de red para hops innecesarios. Escalar horizontalmente viene después de entender el cuello de botella.

**Modo de falla común:** "escalaría horizontalmente" sin demostrar primero entendimiento del root cause.

---

### B2. "El p50 está bien pero el p99 está roto. ¿Qué buscás?"

**Qué mide:** diferencia entre latencia media y jitter; conocimiento de GC, contención y agotamiento de pools.

**Análisis modelo:**
> p99 roto con p50 sano es casi siempre jitter, no latencia base. Candidatos por orden de frecuencia: pausa de GC (una STW de 80ms es invisible en la media pero destruye p99), agotamiento de pool (el request que llega cuando todos los threads o conexiones están ocupadas paga el costo de espera), contención de locks sobre estructuras compartidas, o un hot path que se activa esporádicamente. JFR revela pausas de GC; las métricas de Hikari revelan tiempo de espera del pool; async-profiler o flamegraph revela locks calientes. Principio clave: "Escalar sin entender el cuello de botella amplifica el problema."

**Modo de falla común:** ir directo a "tunearía el heap" sin medir qué porcentaje del p99 explica el GC vs otros factores.

---

### B3. "¿Cómo evitás que una dependencia lenta consuma todos los threads del pool?"

**Qué mide:** patrón bulkhead, aislamiento de fallas, diseño defensivo de concurrencia.

**Análisis modelo:**
> Con bulkheads: cada dependencia tiene su propio thread pool o connection pool con límites separados. Si el modelo de ML se enlentece y empieza a retener threads, no puede agotar el pool del servicio de features ni el pool de DB porque cada uno es independiente. Lo complemento con timeouts duros por dependencia y backpressure en los pools más críticos. El objetivo es que una dependencia degradada quede contenida y no genere cascada al resto del sistema. En Resilience4j esto es un `ThreadPoolBulkhead`; en Spring WebFlux, un scheduler aislado por dependencia.

**Modo de falla común:** confundir timeout con bulkhead — el timeout mata el request individual; el bulkhead aísla el pool.

---

### B4. "¿Cuándo evaluarías virtual threads para este sistema?"

**Qué mide:** criterio de adopción de tecnología, no hype; conocer cuándo virtual threads ayudan y cuándo no.

**Análisis modelo:**
> Los virtual threads ayudan cuando el cuello de botella es I/O bloqueante con muchos threads esperando — exactamente el caso de un motor de decisión que llama a DB, Redis y un modelo de ML secuencialmente. Antes de adoptarlos, mediría: si el throughput actual ya alcanza con platform threads, no cambia nada. Si hay evidencia de que el thread pool es el techo (alta utilización, cola de requests creciente), los virtual threads pueden multiplicar concurrencia sin cambiar la API. La salvedad: librerías que usan `synchronized` o estructuras nativas pueden hacer pinning del carrier thread y anular el beneficio — la validación con profiling es obligatoria.

**Modo de falla común:** presentar virtual threads como solución universal sin condicionar la adopción a evidencia medida.

---

## Bloque C — Eventos versionados y outbox

### C1. "¿Cómo diseñás la publicación de eventos para que no se pierdan si el servicio crashea después de persistir la decisión?"

**Qué mide:** conocimiento de consistencia distribuida, patrón outbox, entendimiento de at-least-once vs at-most-once.

**Análisis modelo:**
> Con el patrón outbox. La decisión y el evento se persisten en la misma transacción de DB: si el commit falla, ninguno existe; si el commit tiene éxito, el evento existe en la tabla outbox aunque el servicio crashee inmediatamente después. Un publisher separado (job de polling o CDC con Debezium) lee el outbox y publica a SNS/SQS. Si la publicación falla, reintenta; si tiene éxito, marca el evento como publicado. Los consumers son idempotentes vía idempotencyKey. Eso garantiza entrega at-least-once sin pérdida de eventos.

**Modo de falla común:** proponer "publicar primero y persistir después" — eso invierte la garantía y puede generar eventos sin decisión correspondiente.

---

### C2. "¿Cómo versionás el evento DecisionEvaluated para agregar campos nuevos sin romper consumers?"

**Qué mide:** experiencia con diseño contract-first, evolución de APIs async.

**Análisis modelo:**
> El evento es una API pública. Los campos nuevos se agregan como opcionales (nullable o con default), nunca modificando el tipo ni renombrando un campo existente. El envelope siempre lleva eventVersion para que los consumers puedan manejar múltiples versiones con un switch. Si el cambio es breaking (campo requerido, rename, cambio de tipo), creo v2, mantengo dual-publish durante una ventana de 4 semanas, verifico que todos los consumers migraron, y deprecate v1. Con un schema registry, la compatibilidad se valida en CI antes de llegar a producción.

**Modo de falla común:** "avisamos a todos los consumers y migramos juntos" — en sistemas distribuidos nunca hay un cutover sincrónico limpio.

---

### C3. "¿Cuáles son los campos mínimos requeridos para un evento en este sistema y por qué?"

**Qué mide:** diseño contract-first de eventos, capacidad de diseñar un contrato explícito.

**Análisis modelo:**
> Como mínimo: `eventId` (UUID único, para deduplicación), `correlationId` (para tracing end-to-end entre servicios), `idempotencyKey` (para que los consumers deduplique retries), `occurredAt` (timestamp de cuándo ocurrió el hecho, no cuándo se publicó), `eventVersion` (para manejo de evolución de schema), `producer` (quién emitió el evento, útil para debugging y auditoría). Para eventos de decisión de fraude agrego: `transactionId`, `decision`, `rulesetVersion`, `modelVersion`. Sin correlationId, la auditoría es imposible en sistemas distribuidos.

**Modo de falla común:** omitir `idempotencyKey` o confundirlo con `eventId` — son conceptos distintos.

---

### C4. "¿Cómo garantizás que los consumers de eventos sean idempotentes?"

**Qué mide:** diseño defensivo en sistemas distribuidos, entrega at-least-once real.

**Análisis modelo:**
> Cada consumer mantiene un registro de idempotencyKeys procesados (en DB o Redis con TTL). Antes de procesar un evento, verifica si la key ya existe: si existe, descarta; si no existe, procesa y registra. La ventana de TTL debe cubrir la ventana esperada de retries más un margen. Para operaciones de DB, el approach más limpio es un upsert o una constraint de unicidad sobre el idempotencyKey — la DB rechaza el duplicado y el consumer trata eso como éxito silencioso. Los consumers que actualizan estado externo (llamadas HTTP, emails) necesitan trackear el idempotencyKey por separado porque no hay garantía transaccional.

**Modo de falla común:** asumir que el broker garantiza exactly-once y no implementar idempotencia en el consumer.

---

## Bloque D — Lambda a EKS

### D1. "¿Por qué migrarías de Lambda a EKS? ¿Cuándo no lo harías?"

**Qué mide:** criterio basado en evidencia, no opinión; conocimiento operacional real de ambas opciones.

**Análisis modelo:**
> Migraría si hay evidencia medida de que Lambda es el problema: cold starts rompiendo p99, latencia inconsistente no resuelta por Provisioned Concurrency, límites de concurrencia generando throttling en pico, o necesidad de control de JVM y conexiones persistentes que Lambda no provee. No migraría por novedad o porque "EKS es más moderno". EKS suma complejidad operacional real: nodos, probes, rolling updates, HPA, GitOps. Sin un equipo con experiencia en Kubernetes, EKS puede ser menos estable que un Lambda bien configurado.

**Modo de falla común:** presentar EKS como upgrade natural sin nombrar el costo operacional real que suma.

---

### D2. "Si el código corre hoy en Lambda, ¿cómo lo diseñás para que migrar a EKS sea un cambio de adapter, no un rewrite?"

**Qué mide:** arquitectura hexagonal real, no solo el concepto; capacidad de separar core de runtime.

**Análisis modelo:**
> La clave es arquitectura hexagonal desde el día uno: el core de decisión (reglas, features, scoring, trace) no tiene dependencia del runtime. El handler de Lambda y el controller de Spring son adapters distintos llamando al mismo use case. Las dependencias externas (DB, Redis, modelo de ML) también son ports con adapters: en Lambda puede ser DynamoDB, en EKS puede ser RDS — el core no sabe ni le importa. Si el sistema está bien desacoplado, migrar de Lambda a EKS significa: cambiar el adapter de input (Lambda handler → controller HTTP) y los adapters de output si hace falta. Sin tocar el core.

**Modo de falla común:** "empaquetamos el Lambda en un contenedor y listo" — eso no es una migración, es una transferencia de deuda técnica.

---

### D3. "¿Cómo diseñás el rolling update de EKS para un sistema de fraude sin downtime?"

**Qué mide:** conocimiento operacional de Kubernetes, experiencia en sistemas de alta disponibilidad.

**Análisis modelo:**
> Configuro `maxUnavailable: 0` y `maxSurge: 1` en el RollingUpdate para evitar bajar pods antes de que los nuevos estén listos. Las probes son críticas: una readinessProbe que verifica que el pod ya tiene features cacheadas y conexiones de pool establecidas antes de recibir tráfico — no solo que HTTP responde. El `terminationGracePeriodSeconds` debe ser mayor al timeout máximo de requests in-flight (ej.: si un request puede tardar 300ms, el grace period debería ser de al menos 30 segundos para cubrir la cola). Hooks de prestart para warm cache si el sistema depende de cache local. Sin esto, el pod nuevo entra frío y viola el SLA en su primer minuto.

**Modo de falla común:** asumir que `readinessProbe: GET /health 200` alcanza cuando el sistema tiene estado caliente (cache, pool).

---

## Bloque E — ML online

### E1. "¿Cómo evitás que el modelo de ML sea un single point of failure?"

**Qué mide:** resiliencia de sistemas, patrón circuit breaker, diseño de fallback en capas.

**Análisis modelo:**
> El modelo vive detrás de un timeout duro y un circuit breaker. Si responde dentro del budget (~60ms), se usa el score. Si hace timeout o falla, el circuit breaker activa fallbacks en capas: primero score cliente cacheado si todavía es válido (TTL en minutos), después solo reglas determinísticas, después política conservadora por monto y tipo de transacción. El trace siempre registra qué camino se tomó y por qué. El modelo no bloquea la decisión; la enriquece cuando está disponible. Un fallback que no está documentado ni monitoreado no es un fallback — es un bug latente.

**Modo de falla común:** describir solo el circuit breaker sin el fallback en capas — el CB sin fallback solo transforma lentitud en error.

---

### E2. "¿Cómo monitoreás que el modelo no se haya degradado en producción?"

**Qué mide:** conocimiento de MLOps, detección de drift, observabilidad de ML en producción.

**Análisis modelo:**
> Monitoreo en dos dimensiones: performance operacional (latencia del endpoint, tasa de timeout, tasa de activación de fallback) y performance de negocio (distribución de score, tasas de APPROVE/DECLINE/REVIEW, tasa de falsos positivos capturada vía feedback diferido). Drift de datos: si la distribución de los features de entrada cambia significativamente respecto del set de entrenamiento, el modelo puede estar extrapolando. Con un feature store registro los features usados en cada predicción, lo que permite comparar distribuciones en tiempo real contra la baseline de entrenamiento. Si la tasa de fallback sube o la distribución de score se mueve, algo cambió.

**Modo de falla común:** monitorear solo la latencia del modelo y no la distribución de sus salidas.

---

### E3. "¿Qué necesitás para hacer rollback del modelo en producción?"

**Qué mide:** madurez de MLOps, versionado de modelos y features, capacidad de rollback auditable.

**Análisis modelo:**
> Tres condiciones: el modelo está versionado con un identificador inmutable (no "latest"), cada decisión registra la versión del modelo y los features usados, y el sistema de serving puede cambiar de versión sin redeploy de código (feature flag o config). Si esas condiciones se cumplen, el rollback es revertir el feature flag a la versión anterior. La complejidad real es que el rollback de modelo puede requerir rollback del set de features: si v2 usa features que v1 no conoce, no son intercambiables. Por eso el versionado de modelo y de feature set deben viajar juntos.

**Modo de falla común:** proponer rollback de modelo sin considerar la compatibilidad del feature set con la versión previa.

---

## Bloque F — Concurrencia, idempotencia y consistencia

### F1. "¿Cómo manejás los connection pools en un sistema con múltiples dependencias?"

**Qué mide:** diseño defensivo de recursos, prevención de fallas en cascada.

**Análisis modelo:**
> Cada dependencia tiene su propio pool con límites separados: pool de DB, pool de Redis, pool HTTP para el modelo de ML. Los tamaños de pool no son arbitrarios: se calculan desde el throughput esperado, latencia media por dependencia y SLA del sistema. Una fórmula base: `pool_size = (requests_por_segundo * latencia_segundos) + buffer`. Con Hikari expongo utilización del pool, pool wait time y count de connection timeout — si el wait time sube, el pool está chico o la dependencia se está degradando. Sin pools separados, una dependencia lenta consume todos los recursos del sistema.

**Modo de falla común:** usar un único pool compartido para todas las dependencias, o dimensionar pools sin baseline de latencia medida.

---

### F2. "¿Cómo hacés rollback seguro de un deploy de código en este sistema?"

**Qué mide:** capacidad de rollback auditable, conocimiento de compatibilidad de schema y contratos.

**Análisis modelo:**
> Separo el rollback en tres dimensiones: código, datos y contratos. Para código: rolling update inverso o rollback de canary, con health checks. Para datos: si el deploy incluyó una migración de schema, la migración debe ser backward-compatible (patrón expand/contract) — nunca romper el schema en el mismo deploy que cambia el código. Para contratos de eventos: si v2 publicó eventos con campos nuevos, v1 debe ignorar esos campos sin fallar. Sin las tres condiciones, el rollback puede romper consumers que ya procesaron eventos en formato nuevo o dejar datos en estado inconsistente.

**Modo de falla común:** tratar el rollback de código como suficiente sin considerar las dimensiones de schema y contratos de eventos.

---

### F3. "¿Qué es el patrón expand/contract y cuándo lo usarías acá?"

**Qué mide:** madurez de migración de schema en sistemas productivos, deploys zero-downtime.

**Análisis modelo:**
> Expand/contract es una técnica para migrar schema sin downtime: en la fase expand, se agrega la columna/campo nueva (nullable, con default), el código viejo sigue funcionando sin saber. En la fase contract (un deploy posterior), los campos viejos se borran una vez que todos los consumers migraron. En este sistema lo usaría para: agregar campos a la tabla de decisiones, agregar campos a eventos, migrar la estructura del audit trace. La alternativa — hacer todo en un solo deploy — requiere downtime o rollbacks muy riesgosos. El costo del expand/contract es más deploys; el beneficio es rollback seguro en cada paso.

**Modo de falla común:** hacer migraciones destructivas de schema en el mismo deploy que el código que las requiere.

---

### F4. "¿Cómo diseñás la concurrencia del motor de decisión para que una transacción de alto riesgo no bloquee una de bajo riesgo?"

**Qué mide:** diseño de prioridad y aislamiento en sistemas concurrentes, experiencia con queuing.

**Análisis modelo:**
> Con colas separadas por prioridad o tipo de transacción, o con thread pools dedicados. Si las transacciones de alto riesgo requieren más trabajo (más reglas, más features, más tiempo de ML), no deberían compartir pool con transacciones de bajo riesgo que se resuelven en 50ms. En la práctica: si el sistema tiene múltiples perfiles de transacción, los fast paths deben aislarse de los slow paths tanto en threading como en pools de dependencias. En Kubernetes esto puede traducirse a pods dedicados o executor services separados dentro del mismo pod. La clave es prevenir que el peor caso de un tipo de transacción degrade el p50 del otro.

**Modo de falla común:** asumir que todo el tráfico es homogéneo y diseñar un único pool para todos los casos.

---

## Bloque G — Trampas y preguntas blandas con profundidad técnica

### G1. TRAMPA: "¿Y si simplemente aprobamos todo cuando falla el modelo? Es lo más simple."

**Qué mide:** capacidad de cuestionar premisas técnicas con argumento de negocio; no aceptar simplificaciones sin análisis.

**Análisis modelo:**
> Aprobar todo cuando ML falla es una política válida solo si el riesgo residual es aceptable para el negocio y está documentado explícitamente como decisión consciente. En fraude, eso puede significar aprobar transacciones que las reglas determinísticas habrían marcado. Mi preferencia es un fallback en capas: primero reglas determinísticas, después política conservadora por monto (montos bajos APPROVE, montos altos REVIEW). Si el negocio decide que el riesgo de aprobar todo es aceptable durante una ventana de degradación corta, esa es una decisión de negocio — no técnica — y debe estar documentada, monitoreada y alertada cuando se activa. "Simple" no implica correcto.

**Modo de falla común:** aceptar la premisa sin cuestionarla, o rechazarla sin ofrecer una alternativa concreta.

---

### G2. TRAMPA: "¿Por qué no usar Kafka en lugar de SNS/SQS?"

**Qué mide:** ausencia de dogmatismo tecnológico; saber cuándo Kafka aporta valor real vs cuándo es over-engineering.

**Análisis modelo:**
> Si el stack existente es AWS y los requisitos son mensajería asincrónica con entrega at-least-once, retries y DLQ, SNS/SQS resuelve el problema sin sumar infraestructura nueva. Kafka aporta valor cuando se necesita retención larga de mensajes para replay, múltiples consumer groups leyendo el mismo stream en offsets distintos, stream processing (KStreams/Flink), o throughput muy alto (millones de eventos por segundo). Para un use case de fraude a 150 TPS con consumers independientes por tipo de evento, SNS/SQS alcanza y tiene menos overhead operacional. No cambiaría a Kafka sin una razón concreta y medida: Kafka hay que operarlo, monitorearlo y escalarlo.

**Modo de falla común:** defender Kafka como "la elección correcta para arquitecturas serias" sin articular qué problema concreto resuelve que SNS/SQS no.

---

### G3. TRAMPA: "¿Por qué no usar Redis directamente en lugar de cache local en el pod?"

**Qué mide:** trade-off latencia vs consistencia; no hay respuesta universalmente correcta.

**Análisis modelo:**
> Depende de qué se cachea y qué consistencia se requiere. El cache local (in-process, Caffeine/Guava) tiene latencia sub-milisegundo y no agrega hop de red. Es ideal para datos que cambian lento: parámetros de reglas, configuración, scores de device con TTL en minutos. Redis tiene latencia ~5-15ms, pero da consistencia entre todas las instancias y manejo centralizado de TTL. Para features de cliente de alta frecuencia (velocity de transacciones en la última hora), Redis tiene sentido. Para configuración estática o features donde la consistencia eventual es aceptable, el cache local es más barato y rápido. La elección depende del TTL aceptable y de si la inconsistencia entre pods tiene consecuencias de negocio.

**Modo de falla común:** elegir Redis por default sin justificar qué consecuencia tendría realmente la inconsistencia de cache entre pods.

---

### G4. TRAMPA: "¿Qué harías en tus primeros 30 días si entrás al equipo?"

**Qué mide:** humildad y juicio de un staff engineer; no entrar a cambiar cosas sin entender primero.

**Análisis modelo:**
> Los primeros 30 días son para escuchar y mapear, no para proponer. Quiero entender: el p99 real end-to-end con traces reales, dónde está el cuello de botella medido actualmente, cómo están estructurados los eventos y si hay un schema registry, cuánto del código está desacoplado del runtime actual, cómo es el proceso de deploy de reglas, y qué cobertura de observabilidad existe hoy. Con ese mapa, en el día 30 puedo proponer un plan concreto basado en evidencia, con prioridades, no asunciones. Las primeras semanas no son para destacar; son para no romper lo que funciona y entender por qué se tomaron las decisiones actuales.

**Modo de falla común:** hablar de lo que "cambiarías" antes de decir que primero entenderías el estado actual y el contexto de las decisiones existentes.

---

### G5. TRAMPA: "Tu reporte de cobertura muestra 44% — ¿no es bajo?"

**Qué mide:** capacidad de interpretar métricas de cobertura en contexto, articulación honesta y confiada del scope de tests.

**Análisis modelo:**
> 44% es solo el slice de Cucumber-JVM. Cucumber está diseñado para validar comportamiento de negocio end-to-end vía invocación directa al use case, no vía HTTP. El controller HTTP, el `main`, la factory — esos son scope de tests unitarios JUnit y de integración con Testcontainers, no de Cucumber. Mergear los `.exec` de los tres tipos de tests da un agregado cercano al 80%. La métrica que importa para ATDD no es porcentaje global de líneas — es el porcentaje de comportamiento de negocio cubierto. `domain.rule` está al 100%, `application.usecase.risk` al 93%, `domain.service` al 88%. Los paquetes en 0% no son fallas — son scope mismatch: Cucumber no entra por HTTP, así que el `HttpController` aparece sin cubrir ahí, pero está cubierto por `HttpControllerSmokeTest` (JUnit). El número que importa es el agregado cross-suite.

**Modo de falla común:** aceptar la premisa "44% es bajo" y entrar en modo defensivo o hacer promesas de mejora. La respuesta correcta es un reframe: explicar que el número engaña en aislamiento y que la métrica que importa es el agregado por capa, no por suite individual.

**Datos de soporte:** `out/coverage/aggregated/index.html` o por capa: rule 100, service 88, usecase 93.

---

---

## Bloque H — PoCs, SDKs y decisiones de diseño

### H1. "¿Por qué construiste la PoC k8s-local? ¿Qué valida que docker compose no pueda?"

**Qué mide:** entendimiento de la diferencia entre orquestación local y producción, propósito arquitectónico de cada PoC.

**Análisis modelo:**
> Docker compose sirve para levantar dependencias rápido, pero no replica el modelo operacional de Kubernetes: no hay HPA, ni health checks de kubelet, ni rolling updates, ni RBAC, ni aislamiento por namespace. k8s-local (k3d + OrbStack) valida que el chart de Helm arranca correctamente, que las probes liveness/readiness están bien configuradas y que el HPA reacciona bajo carga simulada. También valida que los secrets vía ExternalSecrets o variables de entorno llegan bien al pod. Es el ambiente donde detectás errores de configuración que no existen en compose: imagePullPolicy, resource limits, PodDisruptionBudget. El objetivo no es reproducir producción al 100% en local, sino eliminar la categoría de bugs "anda en compose, falla en Kubernetes en el primer deploy".

**Modo de falla común:** decir que k8s-local "es para aprender Kubernetes". El propósito es validar artefactos de deploy, no aprender la herramienta.

---

### H2. "¿Qué agrega vertx-risk-platform sobre java-vertx-distributed? ¿No es duplicación?"

**Qué mide:** capacidad de articular cuándo dos PoCs con scope solapado tienen justificación arquitectónica vs deuda técnica.

**Análisis modelo:**
> java-vertx-distributed valida la separación de capas como pods con comunicación HTTP entre servicios: controller, usecase, repository, consumer como procesos independientes. vertx-risk-platform escala esa idea a una plataforma completa con gestión de reglas, soporte multi-tenant y orquestación de decisiones complejas. No es duplicación: la primera valida la topología distribuida, la segunda valida que la topología puede manejar use cases productivos con lógica de negocio más rica. Si la segunda PoC rompe la separación de concerns que la primera estableció, es señal de que el diseño tiene problemas antes de llegar a producción.

**Modo de falla común:** no poder explicar la diferencia de scope y responder "son variantes de la misma PoC". Eso señala que no hay decisión arquitectónica clara detrás de cada una.

---

### H3. "Tenés SDKs en Go, Java y TypeScript para el mismo cliente de risk. ¿Cómo evitás que diverjan?"

**Qué mide:** estrategias de contract testing, generación de código desde schema, manejo de SDKs multi-lenguaje.

**Análisis modelo:**
> El contrato de la API vive en `sdks/risk-events` como source of truth: schemas de eventos y contratos de request/response. Los SDKs específicos de cada lenguaje (risk-client-go, risk-client-java, risk-client-typescript) se generan o validan contra ese contrato vía contract tests en `sdks/contract-test`. CI verifica que los tres SDKs son compatibles con la misma versión del contrato antes de mergear cambios del servidor. El ADR de idempotency keys establece que el cliente genera la key, no el servidor: los tres SDKs implementan ese invariante. Si un SDK diverge en ese comportamiento, el contract test falla. La clave es que el contrato es código, no documentación: vive en el repo, tiene tests y es la autoridad final.

**Modo de falla común:** describir "comunicación entre equipos" como estrategia de consistencia. Sin un contrato ejecutable y tests que lo validen, la consistencia es intención, no garantía.

---

### H4. "¿Por qué separaste los eventos en risk-events como SDK propio?"

**Qué mide:** entendimiento de contratos de eventos, versionado de schemas y el valor de tratar al contrato como artefacto publicable.

**Análisis modelo:**
> Si los schemas de eventos están embebidos en el servicio productor, cada consumer tiene que copiar-pegar la definición o asumir la forma del JSON sin contrato explícito. risk-events es el SDK del contrato: define los tipos de eventos que el engine publica, sus versiones y los invariantes que se mantienen entre versiones (compatibilidad BACKWARD). Cualquier consumer importa ese SDK y tiene garantía de compile-time de que su deserialización es compatible con la versión del producer. Cuando ocurre un cambio de schema, risk-events bumpea su versión y todos los consumers ven el breaking change en build time, no en producción. Esa es la implementación concreta del ADR de versionado de eventos con discriminator de campo.

**Modo de falla común:** decir que es "comodidad de import". El punto real es que mueve los errores de compatibilidad de eventos de runtime a compile time.

---

### H5. "¿Por qué java-risk-engine no usa framework? ¿No es reinventar la rueda?"

**Qué mide:** capacidad de articular trade-offs entre simplicidad y conveniencia, y de distinguir una PoC pedagógica de una decisión de producción.

**Análisis modelo:**
> El objetivo de java-risk-engine (bare-javac) es pedagógico: demostrar que la arquitectura hexagonal es independiente del framework, no consecuencia de él. Si la primera PoC usa Spring, hay riesgo de que la arquitectura hexagonal quede enmascarada por convenciones del framework: `@Component`, `@Service`, inyección automática. Construirla sin framework fuerza las preguntas: "¿Dónde vive el Composition Root?", "¿Cómo cruza el request el borde del port?", "¿El domain sabe algo del adapter HTTP?". La decisión de producción es distinta: en un servicio real usaría Vert.x o Spring con los mismos bordes. La PoC sin framework no es una recomendación de arquitectura productiva; es una herramienta pedagógica para que el equipo entienda la separación antes de que el framework la oscurezca.

**Modo de falla común:** defender el approach sin framework como decisión productiva, o descartarlo como "purismo excesivo sin valor práctico". El valor es pedagógico y de validación de bordes, no operacional.

---

### H6. "¿Qué problema resuelve layer-as-pod vs todo en un único servicio?"

**Qué mide:** entendimiento de trade-offs de granularidad de deploy, costo operacional de la distribución, cuándo la separación física tiene sentido.

**Análisis modelo:**
> layer-as-pod (ADR-0013) separa físicamente controller, usecase, repository y consumer en pods independientes para validar que la arquitectura hexagonal aguanta bajo presión de deploy: si el usecase intenta importar directamente el repository porque "están en el mismo pod", el test de arquitectura falla. La separación fuerza la disciplina de las interfaces. En producción, ese modelo tiene costos reales: más latencia de red inter-pod, más overhead operacional, más complejidad de debugging. La decisión no es "siempre separar capas como pods"; es "usar este modelo para validar que los bordes son reales, no solo nominales". Si las capas están desacopladas a nivel código, colapsar varias en un solo pod es una optimización trivial de deploy. Si no lo están, la separación de pods lo expone antes de producción.

**Modo de falla común:** presentar layer-as-pod como la topología productiva recomendada. Es una herramienta de validación arquitectónica, no un patrón universal de deploy.

---

### H7. "¿Por qué no hay framework de DI en las PoCs? ¿Eso no complica el testing?"

**Qué mide:** entendimiento de Composition Root, inyección manual y cuándo un framework de DI aporta valor vs oscurece la arquitectura.

**Análisis modelo:**
> El Composition Root manual vive en el `main` del adapter HTTP. Es un método factory que construye el grafo de objetos explícitamente: `new RiskScoringUseCase(new FraudRuleRepository(...), new MLScorerAdapter(...))`. En testing, el grafo se arma con test doubles directos: sin magia de inyección, sin scanning de classpath, sin `@MockBean`. Eso hace los tests más rápidos (sin contexto de Spring) y más explícitos sobre qué se está inyectando. ADR-0031 elige esto para las PoCs porque un framework de DI en una PoC chica oscurece la pregunta clave: "¿Cuántas dependencias tiene realmente este use case?" Cuando el constructor manual es largo, es señal. Un framework lo oculta. En un servicio productivo con 30+ beans, un framework de DI tiene sentido. En una PoC de 5 clases, es overhead que oscurece la arquitectura.

**Modo de falla común:** decir que sin framework de DI "no se puede testear" o que "Spring siempre es mejor". Testear sin framework es más explícito, no más difícil.

---

## Preguntas de discovery al cierre de una discusión de diseño

Cuando la discusión llega al "qué nos querés preguntar", estas preguntas demuestran profundidad y entendimiento genuino del sistema. Elegir 2-3 según el contexto de la conversación:

1. "Cuando dicen SLA ~300ms, ¿es p95 o p99, y es end-to-end desde el canal o solo el engine interno?"
2. "¿Dónde está el cuello de botella medido actualmente? ¿Tienen tracing distribuido activo que muestre el breakdown por componente?"
3. "¿Qué motivó específicamente la migración de Lambda a EKS: cold starts, latencia, costo o control operacional?"
4. "¿Pueden reconstruir una decisión completa de hace 6 meses, incluyendo la versión de reglas, versión de modelo, features y fallbacks aplicados?"
5. "¿Tienen schema registry o algún mecanismo de validación de compatibilidad de eventos en CI?"
6. "¿Qué nivel de autonomía tiene el rol para definir estándares de arquitectura, observabilidad y performance? ¿O es más sobre ejecutar decisiones ya tomadas?"
