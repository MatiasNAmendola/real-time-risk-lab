---
adr: "0044"
title: Lambda vs EKS — posicionamiento arquitectónico y checklist de readiness
status: accepted
date: 2026-05-12
tags: [decision/accepted, adr, lambda, eks, kubernetes, java, positioning]
source_archive: docs/07-lambda-vs-eks.md (migrado 2026-05-12)
---

# ADR-0044: Lambda vs EKS — posicionamiento arquitectónico y checklist de readiness

## Contexto

La pregunta "¿por qué migrar a EKS?" no tiene una respuesta universal. La respuesta correcta es condicional: depende de qué dolor concreto tiene Lambda hoy y si EKS lo resuelve sin agregar un dolor mayor.

Frase útil:
> "No migraría a EKS sin un motivo claro medido: latencia p99, costo por transacción, límites operativos de Lambda o necesidad de control que Lambda no da."

## Decisión

Documentar los criterios medibles que justifican o descartan la migración de Lambda a EKS, con una tabla comparativa y un checklist de readiness para producción.

## Tabla comparativa con criterios medibles

| Criterio | Lambda (Java) | EKS (pod Java) | Notas |
|---|---|---|---|
| Cold start p99 | 2,000–8,000 ms (sin provisioned concurrency) | < 1 ms (pod ya caliente) | Lambda con provisioned concurrency mejora, pero agrega costo y complejidad |
| Cold start con Provisioned Concurrency | 100–300 ms (warmup precargado) | N/A | Costo fijo por concurrencia reservada aunque no haya tráfico |
| Latencia p99 en caliente | 50–120 ms (JVM caliente, misma VPC) | 20–80 ms (JVM caliente, pod estable) | EKS da más estabilidad si está bien tuneado |
| Jitter p99 vs p50 | Alto sin PC (GC cold, classloading) | Bajo con JVM bien configurada | EKS permite JVM tuning fino: ZGC, heap explícito |
| Costo por millón de requests (estimado) | USD 0.20 compute + USD 0.20 invocaciones = ~USD 0.40/M | Variable: ~USD 0.15–0.30/M con spot/graviton | Lambda más predecible en burst; EKS más barato en carga constante y alta |
| Control de JVM / GC | Limitado: no se puede elegir GC, heap máx 10GB, sin JFR directo fácil | Total: GC collector, heap, JFR, async-profiler, flags arbitrarios | Para fraude en tiempo real, control de GC importa para p99 |
| Conexiones persistentes (DB, Redis) | Problemáticas: Lambda no mantiene conexiones entre invocaciones por defecto | Nativas: conexiones HTTP/JDBC/Redis persisten durante la vida del pod | Connection pooling real solo en EKS |
| Observabilidad | CloudWatch Logs + X-Ray básico; OpenTelemetry posible pero con overhead | OpenTelemetry nativo, sidecars (Fluent Bit, OTEL collector), Jaeger/Datadog/Grafana | EKS permite sidecars sin modificar la app |
| Tiempo operativo del equipo | Bajo: AWS gestiona runtime, scaling, parches | Alto: nodos, upgrades EKS, networking, probes, rolling updates, HPA | EKS sin experiencia del equipo puede ser más inestable que Lambda |
| Complejidad de despliegue | Baja: deploy = subir zip o image, AWS gestiona el resto | Alta: Helm/Kustomize, GitOps, ArgoCD, IRSA, resource requests, PDB, rollout strategy | EKS requiere madurez DevOps/Platform Engineering |
| Autoscaling | Automático basado en concurrencia | HPA (CPU/memoria) o KEDA (métricas externas: SQS depth, custom metric) | KEDA permite escalar por backlog de SQS, más alineado con fraude |
| Límites de concurrencia | Cuenta AWS tiene límite por región (default ~1,000 por función) | Sin límite conceptual; el límite es la capacidad del cluster | Lambda puede provocar throttling en burst |
| Networking / VPC | Lambda en VPC agrega latencia de ENI attachment (~1s extra en cold start) | Pod en VPC nativo, sin overhead de ENI attachment | Tener Lambda fuera de VPC no es opción si necesita acceso a RDS/Redis privado |

## Matriz de decisión: ¿cuándo se justifica migrar?

| Pain principal hoy | Migración a EKS se justifica | No se justifica sin más |
|---|---|---|
| Cold starts afectan p99 de forma medida y Provisioned Concurrency es muy caro | Si | Si el problema se resuelve con PC a menor costo |
| Latencia inconsistente (jitter) en p99 que no se corrige con tuning Lambda | Si | Si el jitter es por GC y se puede configurar en Lambda (RAM más alta = más CPU = mejor GC) |
| Límites de concurrencia generan throttling en picos | Si (si el equipo puede operar EKS) | Si el throttling se resuelve aumentando límites en AWS |
| Costo de Lambda es dominante comparado con EKS en carga sostenida alta | Si, evaluar con cálculo real | No migrar solo por costo estimado sin benchmark real |
| Necesidad de control de JVM, profiling, conexiones persistentes, sidecars | Si, estos son los argumentos más fuertes para EKS | No si el equipo no tiene madurez para operar Kubernetes |
| El único motivo es "EKS es más moderno" | No | Este es el anti-patrón más común |

## Consecuencias

### Se gana con EKS

- Control total de JVM: GC collector (ZGC para latencia baja), heap, flags, JFR, async-profiler.
- Conexiones persistentes a DB, Redis, endpoints ML.
- Sidecars para observabilidad sin modificar la app (OTEL collector, Fluent Bit).
- Autoscaling por métricas de negocio con KEDA (ej: profundidad de SQS).
- Latencia más consistente si el pod está caliente y bien configurado.
- No hay cold start (si los pods siempre están calientes).

### Se pierde con EKS

- Simplicidad operativa: Lambda escala a cero automáticamente, no hay que gestionar nodos.
- Gestión de parches del OS, runtime del nodo, upgrades de EKS.
- Escalar a cero sin complejidad adicional (en EKS se puede hacer pero requiere KEDA + configuración).
- AWS gestiona disponibilidad del control plane en Lambda; en EKS el equipo es responsable de los workers.

### Se complica con EKS

- Capacity planning: cuántos nodos, qué tipo de instancia, spot vs on-demand.
- Rolling updates sin downtime: readiness probes, PodDisruptionBudgets, graceful shutdown.
- Multi-AZ: pods distribuidos entre AZ pero que también necesitan acceso a dependencias en la misma AZ.
- Gestión de secretos e IRSA (IAM Roles for Service Accounts): más seguro que variables de entorno, pero requiere configuración.
- Observabilidad: más poderosa pero más compleja de configurar desde cero.

## Checklist de readiness para migrar a EKS

### Aplicación

- [ ] La app arranca y cierra limpiamente (graceful shutdown implementado: captura SIGTERM, drena requests en curso, cierra conexiones).
- [ ] Health probes configuradas: readiness (la app está lista para recibir tráfico) y liveness (la app sigue viva).
- [ ] Resource requests y limits definidos para CPU y memoria (sin esto, el scheduler no puede planificar bien).
- [ ] La app no guarda estado local que no pueda perderse en un restart de pod.
- [ ] JVM warmup contemplado: primeros N requests son lentos, readiness probe lo tiene en cuenta.

```yaml
# Ejemplo de probes en Kubernetes
readinessProbe:
  httpGet:
    path: /actuator/health/readiness
    port: 8080
  initialDelaySeconds: 30
  periodSeconds: 10
  failureThreshold: 3

livenessProbe:
  httpGet:
    path: /actuator/health/liveness
    port: 8080
  initialDelaySeconds: 60
  periodSeconds: 15
```

### Autoscaling

- [ ] HPA configurado con métricas de CPU/memoria o KEDA con métricas de negocio.
- [ ] minReplicas >= 2 para alta disponibilidad (nunca minReplicas=1 en producción de fraude).
- [ ] PodDisruptionBudget definido: cuántos pods pueden estar down simultáneamente durante mantenimiento.

```yaml
# PodDisruptionBudget básico
apiVersion: policy/v1
kind: PodDisruptionBudget
metadata:
  name: risk-engine-pdb
spec:
  minAvailable: 1
  selector:
    matchLabels:
      app: risk-decision-engine
```

### Despliegue

- [ ] GitOps configurado: ArgoCD o Flux sincroniza el estado del cluster desde el repo.
- [ ] Rolling update strategy con maxUnavailable: 0 y maxSurge: 1 (zero downtime deploys).
- [ ] Rollback automatizable: `kubectl rollout undo` o ArgoCD rollback en < 2 minutos.
- [ ] Canary o blue/green para cambios de alto riesgo (cambios de reglas, nuevo modelo).

### Seguridad y acceso a AWS

- [ ] IRSA configurado: el ServiceAccount del pod tiene una IAM role, no hay credenciales hardcodeadas.
- [ ] Secretos gestionados con AWS Secrets Manager o Vault, no en ConfigMaps en texto plano.
- [ ] Network policies para aislar el namespace del engine de otros namespaces.

### Observabilidad

- [ ] OpenTelemetry agent como sidecar o en la app, exportando trazas a Jaeger/Datadog/X-Ray.
- [ ] Métricas Micrometer expuestas en `/actuator/prometheus` y scrapeadas por Prometheus.
- [ ] Logs estructurados (JSON) enviados por Fluent Bit sidecar a CloudWatch Logs o Elasticsearch.
- [ ] Alertas en p99 de latencia end-to-end, error rate y pool exhaustion.

### JVM

- [ ] GC collector explícito: `-XX:+UseZGC` para p99 bajo o `-XX:+UseG1GC` para balance.
- [ ] Heap size explícito: `-Xms` y `-Xmx` iguales para evitar expansión dinámica.
- [ ] JFR habilitado en staging para profiling sin impacto de producción.
- [ ] Thread pool sizes revisados: no dejar defaults de Spring Boot sin ajustar para el workload real.

## Alternativas consideradas

- Mantener Lambda con Provisioned Concurrency — válido si el pain principal son cold starts y el costo es aceptable.
- Lambda + ALB/API Gateway sin VPC — descartado para casos con acceso a RDS/Redis.
- Fargate en lugar de EKS — simplifica operación de nodos pero mantiene overhead de cold start en tareas.

## Relacionado

- [[Layer-as-Pod]] — el PoC `vertx-layer-as-pod-http` implementa separación de pods por permisos.
- [[0041-k8s-deployment-test-strategy]] — estrategia de tests para mecanismos de despliegue.
- [[Latency-Budget]] — presupuesto de latencia que motiva la decisión.
- [[IRSA]] — gestión de IAM por ServiceAccount en EKS.
- [[Risk-Platform-Overview]]
