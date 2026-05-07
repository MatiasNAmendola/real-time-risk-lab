# 07 — Mindset de diseño staff: defender decisiones arquitectónicas

Este documento captura cómo articular y defender un diseño cuando los reviewers presionan. Es un banco de preguntas para conversaciones de diseño, no un script de evaluación.

## Marco mental para cualquier respuesta

Usar esta estructura:

1. Plantear el supuesto crítico.
2. Separar camino crítico vs trabajo asincrónico.
3. Definir trade-offs.
4. Bajar a mecanismos concretos.
5. Cerrar con observabilidad y operación.

Framing ancla:

> Lo primero a evitar es diseñar por intuición. Definir el SLA real, separar el camino crítico, medir p95/p99, y recién ahí optimizar el cuello de botella medido.

---

## 1. "Diseñá un sistema de fraude en tiempo real a 150 TPS con 300ms"

Respuesta:

> Primero confirmaría si los 300ms son media, p95 o p99 end-to-end. Asumiendo p95/p99, partiría el flujo en camino crítico y asincrónico. El camino crítico mantiene validación, lectura de features críticos, reglas determinísticas, scoring de ML bajo timeout estricto, decisión y trace mínimo. Todo lo demás — auditoría enriquecida, analytics, training, dashboards — sale por eventos.
>
> El motor lleva correlation ID, idempotency key, presupuesto de latencia por dependencia, circuit breakers y fallback. La decisión se persiste junto con su trace y un evento outbox para garantizar auditoría.

Si presionan con "¿por qué outbox?":

> Porque en fraude no me puedo permitir decidir y perder el evento auditable. Persistir decisión y outbox en la misma transacción da consistencia; la publicación puede ser asincrónica y reintentable.

---

## 2. "¿Qué va en el camino crítico?"

Respuesta:

> Solo lo que cambia la decisión inmediata. Validación, features esenciales, reglas, el modelo si entra en el budget, decisión y trace mínimo. Si algo no cambia el APPROVE/DECLINE/REVIEW de este request, sale del camino crítico.

Ejemplo:

> Enviar eventos para entrenamiento del modelo, dashboards o auditoría enriquecida no debería bloquear la respuesta transaccional.

---

## 3. "No estamos llegando a 300ms. ¿Y ahora?"

Respuesta:

> Primero miro traces distribuidos y percentiles, no promedios. Quiero un breakdown por span: controller, engine, features, DB/cache, ML, serialización, red y GC. Si el cuello de botella es red, reduzco hops o cacheo. Si es DB, reviso índices/queries/N+1. Si es CPU, perfilo con JFR o async-profiler. Si es contención, locks/pools. Escalar pods viene después de entender el cuello de botella.

Principio:

> Escalar sin entender el cuello de botella amplifica el problema.

---

## 4. "¿Por qué migrar Lambda a EKS?"

Respuesta:

> No migraría por moda. Lo justificaría con evidencia de cold starts, latencia variable, límites de concurrencia, networking caro, conexiones persistentes necesarias, tuning de JVM o mejor observabilidad. EKS da pods calientes, control de runtime, sidecars, tuning de recursos, HPA y rollouts más finos. Pero suma complejidad operacional: capacity planning, probes, node pools, NetworkPolicies, IAM por ServiceAccount y costo.

Cierre fuerte:

> La migración sana no es mover Lambdas a pods; es aislar el core de decisión para que el runtime sea reemplazable.

---

## 5. "¿Cómo dividirías servicios/pods en EKS?"

Respuesta:

> Dividiría por borde operacional y permisos, no solo por capas de código. Por ejemplo: un adapter/controller público, un engine/usecase de decisión interno, y componentes de persistencia/eventos con permisos restringidos. El controller no debería tener credenciales de DB; solo permiso para invocar al use case. El usecase tendría permiso mínimo para leer features, llamar a ML y persistir decisión/outbox. Repository/event-publisher tendrían permisos limitados a storage/SQS/SNS.

Si presionan con "¿no es overkill?":

> Depende del tamaño y criticidad. Para 150 TPS podría empezar con un monolito modular o pocos servicios, pero mantendría los bordes limpios para poder partirlos cuando la latencia, la seguridad o el escalado lo justifiquen. Partir pods sin razón suma hops y latencia.

---

## 6. "¿Cómo diseñás eventos versionados?"

Respuesta:

> Trato al evento como una API pública. Tiene que tener un contrato explícito: eventId, eventType, eventVersion, occurredAt, correlationId e idempotency key. Los cambios deben ser backward-compatible: agregar campos opcionales, nunca renombrar ni borrar. Si rompo compatibilidad, las versiones coexisten. Idealmente valido contratos en CI con un schema registry o tests de compatibilidad.

Principio:

> El evento no es un DTO interno; es un contrato con consumers que no controlo.

---

## 7. "¿Cómo garantizás auditoría end-to-end?"

Respuesta:

> Cada decisión tiene que poder reconstruirse meses después: request/correlationId, features usados, reglas evaluadas, versión del ruleset, versión del modelo, score, fallback aplicado, dependencias que fallaron, decisión final y eventos emitidos. Me apoyo en logs estructurados, tracing distribuido y un DecisionTrace persistido atado a la decisión.

Principio:

> Saber qué pasó no alcanza; hay que explicar por qué pasó.

---

## 8. "¿Cómo integrás ML online?"

Respuesta:

> El modelo no puede ser un single point of failure. Si está en el camino crítico, tiene timeout estricto, circuit breaker, fallback y métricas propias. Versiono modelo y features para auditoría. Si su p95/p99 no entra en el budget, lo uso como señal asincrónica o precomputo features/scores.

Fallbacks posibles:

- reglas determinísticas;
- score cacheado;
- REVIEW;
- APPROVE/DECLINE según política de riesgo.

---

## 9. "¿Cómo manejás la concurrencia?"

Respuesta:

> Aíslo recursos por dependencia: pools separados, timeouts, bulkheads y backpressure. Evito locks globales en el engine. Las reglas/config tienen que ser inmutables o versionadas. Si el cuello de botella es I/O bloqueante en Java moderno, evaluaría virtual threads — pero midiendo. Prefiero estabilidad y aislamiento de fallas antes que exprimir throughput hasta saturar todo.

---

## 10. "¿Cómo evitás duplicados e inconsistencias?"

Respuesta:

> Para requests, idempotency key. Para eventos, eventId e idempotencia del lado del consumer. Para decisión más evento, outbox transaccional. Para reglas/modelos, versionado. Para retries, operaciones determinísticas dentro de una ventana o devolver la decisión ya persistida.

---

## 11. "¿Cómo hacés rollback?"

Respuesta:

> Separo rollback para código, reglas, modelos y eventos. Código con canary/rolling y health checks. Reglas/modelos con versionado y feature flags. Eventos con compatibilidad backward. Lo importante es que cada decisión registre qué versión de regla/modelo usó, para que el rollback sea auditable.

---

## 12. "¿Cómo liderarías técnicamente al equipo?"

Respuesta:

> Apuntaría a subir el nivel del sistema, no solo a cerrar tickets. Definiría estándares de arquitectura, ADRs para decisiones importantes, guías de performance, criterios de observabilidad, contratos de eventos, definiciones de camino crítico y prácticas de profiling. En mentoría empujaría al equipo a explicar trade-offs y operar lo que construyen.

Principio:

> Prefiero subir el nivel del sistema antes que ser el cuello de botella técnico.

---

# Resumen de posicionamiento final

Cuando piden resumir el ángulo de interés:

> Lo que hace que este espacio de problema valga la pena profundizar es la combinación de decisioning en tiempo real, baja latencia, arquitectura event-driven, trazabilidad fuerte, ML online y migración operacional a EKS. El enfoque: aislar el core de decisión, medir p95/p99 end-to-end, separar camino crítico del trabajo asincrónico, persistir decisión y outbox para auditoría, y aplicar mínimo de permisos por borde. No "más pods" primero — primero entender el cuello de botella y los trade-offs.
