# Glosario del proyecto

Terminos usados en el repo y documentacion.

---

**ATDD (Acceptance Test-Driven Development)**
Metodologia donde los tests de aceptacion se escriben antes del codigo de produccion. Los tests expresan el comportamiento esperado desde la perspectiva del usuario o negocio. Herramientas: Karate, Cucumber.

**BDD (Behavior-Driven Development)**
Extension de TDD donde los tests se expresan en lenguaje natural (Gherkin: Given/When/Then) para que puedan ser leidos por stakeholders no tecnicos.

**Bulkhead**
Patron de resiliencia que limita la concurrencia de un recurso para prevenir que la falla de un componente afecte a otros. Similar a los compartimentos estancos de un barco. Implementado con semaforos.

**Canary Deployment**
Estrategia de deployment donde una nueva version se despliega a un porcentaje pequeno del trafico (ej. 5%) antes de hacer rollout completo. Permite validar la nueva version con trafico real antes de afectar a todos los usuarios.

**Circuit Breaker**
Patron de resiliencia con tres estados: CLOSED (normal), OPEN (falla, rechaza requests), HALF-OPEN (prueba recuperacion). Previene cascadas de fallos cuando un servicio downstream falla.

**Clean Architecture**
Arquitectura propuesta por Robert Martin donde las capas de negocio (domain) no dependen de infraestructura. Las dependencias apuntan hacia adentro: infrastructure → application → domain.

**CQRS (Command Query Responsibility Segregation)**
Patron donde las operaciones de escritura (commands) y lectura (queries) usan modelos separados. Permite escalar independientemente la lectura y la escritura.

**correlationId**
Identificador unico generado al inicio de cada request que se propaga por todos los servicios, logs, y eventos. Permite trazar el flujo completo de una transaccion a traves del sistema.

**DLQ (Dead Letter Queue)**
Cola donde se envian los mensajes que no pudieron ser procesados despues de N reintentos. Permite procesamiento manual o reingesta posterior.

**Domain Event**
Evento que representa algo que ocurrio en el dominio de negocio. Inmutable, con timestamp y correlationId. Ejemplos: `RiskDecisionMade`, `FraudAlertGenerated`.

**Event Sourcing**
Patron donde el estado de una entidad se reconstruye a partir del historial de eventos en lugar de leer el estado actual de la base de datos.

**Hexagonal Architecture**
Nombre alternativo de Clean Architecture. El "hexagono" representa el dominio, y los puertos son las interfaces que comunican el dominio con el mundo exterior.

**Idempotency Key**
Identificador unico por operacion que permite a un consumidor detectar si ya proceso un request/evento y evitar efectos duplicados. Fundamental en sistemas distribuidos con reintentos.

**IRSA (IAM Roles for Service Accounts)**
Mecanismo de AWS para asignar permisos IAM a pods de Kubernetes sin usar credenciales estaticas. El pod asume un role IAM via Service Account.

**Outbox Pattern**
Patron que garantiza consistencia eventual entre la base de datos y el broker de mensajes. El evento se escribe en una tabla "outbox" dentro de la misma transaccion que el dato de negocio, y un relay lo publica al broker.

**p50 / p95 / p99**
Percentiles de latencia. p99 = 300ms significa que el 99% de los requests se resuelven en 300ms o menos. El 1% puede tardar mas (outliers). Los SLOs se definen tipicamente en p99.

**Port (Puerto)**
En arquitectura hexagonal: interfaz que define como el dominio se comunica con el exterior. Puerto de entrada (driving port): como el exterior invoca al dominio. Puerto de salida (driven port): como el dominio accede a infraestructura.

**Redpanda**
Broker de mensajes 100% API-compatible con Apache Kafka. Implementado en C++, single binary, sin ZooKeeper. Usado en este proyecto como alternativa local a Kafka/MSK.

**Schema Registry**
Servicio que almacena y valida el schema de los mensajes Kafka (Avro, Protobuf, JSON Schema). Garantiza que producers y consumers acuerden el formato del mensaje. No usado en este proyecto (JSON simple).

**SLI (Service Level Indicator)**
Metrica que mide el comportamiento del servicio. Ejemplo: porcentaje de requests con latencia < 300ms.

**SLO (Service Level Objective)**
Target para un SLI. Ejemplo: "p99 latencia < 300ms medida en ventana de 5 minutos". Los SLOs se monitoran con Prometheus y alertan si se violan.

**TPS (Transactions Per Second)**
Throughput del sistema. Target de este proyecto: 150 TPS sostenidos con p99 < 300ms.

**Transactional Risk**
Caso de uso del repo: sistema de deteccion de fraude en tiempo real para transacciones de pago. Este proyecto es una exploracion tecnica de ese caso.

**Valkey**
Fork open source de Redis (BSD 3-Clause) creado despues de los cambios de licencia de Redis Ltd. API 100% compatible con Redis.

**Virtual Threads (Project Loom)**
Feature de Java 25 que permite crear millones de threads "virtuales" sobre un pool de OS threads. Util para I/O bloqueante: el thread virtual se suspende sin bloquear el OS thread subyacente.

**Vert.x**
Framework reactivo para Java basado en event loop. Un solo thread maneja miles de requests concurrentes sin bloqueo. Version usada: 5.0.12.

**OTEL / OpenTelemetry**
Standard abierto para instrumentacion de observabilidad (traces, metrics, logs). Incluye API, SDK, y protocolo OTLP. Usado via Java agent 2.x en este proyecto.

**ArgoCD**
Herramienta de GitOps para Kubernetes. Lee manifiestos de un repo Git y los aplica al cluster automaticamente (reconciliation loop).

**Argo Rollouts**
Extension de ArgoCD para deployments avanzados: canary, blue-green, con analysis basado en metricas Prometheus.

**External Secrets Operator (ESO)**
Operador de Kubernetes que sincroniza secrets desde stores externos (AWS Secrets Manager, Vault/OpenBao) como Kubernetes Secrets.

**OpenObserve**
Backend de observabilidad que unifica traces, logs y metricas. Alternativa open source a Datadog/Axiom para uso local.

**OpenBao**
Fork open source de HashiCorp Vault (MPL 2.0). Usado como mock de AWS Secrets Manager en desarrollo local.

**kube-prometheus-stack**
Helm chart que instala Prometheus, Alertmanager, Grafana, y varios exporters como un stack unificado.
