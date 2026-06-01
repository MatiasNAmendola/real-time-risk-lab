# 46 — Decisiones de stack, plataforma e IaC

Esta página consolida criterios para justificar decisiones de stack y plataforma: cuándo elegir Java, TypeScript, Go u otro runtime; cuándo construir tooling de Developer Experience; cuándo adoptar IaC; cuándo pasar a multi-AZ, multi-región o multi-cloud; y cuándo construir una consola interna agnóstica al proveedor cloud.

La idea no es vender una tecnología como respuesta universal, sino mostrar un método: partir de restricciones medibles, costo operativo, madurez del equipo, riesgo de negocio y capacidad de evolución.

## 1. Principio general

Una decisión de stack se justifica cuando mejora una restricción concreta:

- latencia p99;
- throughput;
- costo por transacción;
- confiabilidad;
- seguridad/compliance;
- mantenibilidad;
- velocidad de entrega;
- experiencia de desarrollo;
- disponibilidad operativa;
- capacidad de contratar o formar equipo.

Si no hay una restricción clara, cambiar stack suele ser una forma cara de mover complejidad de lugar.

Frase útil:

> “No cambio de tecnología por preferencia estética. Primero identifico el cuello de botella, lo mido, pruebo una mejora incremental y recién después evalúo migrar stack o plataforma.”

## 2. Cómo elegir stack por tipo de problema

| Stack | Cuándo lo elegiría | Por qué | Riesgos |
|---|---|---|---|
| Java / JVM | Core transaccional, baja latencia sostenida, ecosistema enterprise, observabilidad madura, librerías robustas. | Performance estable, tooling maduro, buen soporte para concurrencia, profiling, GC tuning y operación. | Verbosidad, builds pesados, curva de tuning. |
| TypeScript / Node / Bun | APIs de borde, BFF, prototipos rápidos, tooling, integraciones web, equipos full-stack. | Velocidad de desarrollo, tipado razonable, ecosistema amplio, DX alta. | CPU-bound limitado, runtime single-thread por proceso, dependencia fuerte de librerías. |
| Go | CLIs, servicios livianos, networking, workers, sidecars, tooling de plataforma, agentes, componentes con footprint bajo. | Binario único, arranque rápido, memoria contenida, concurrencia simple, distribución fácil. | Menos expresividad en dominio rico, error handling repetitivo, ecosistema enterprise menos homogéneo que JVM. |
| Python | Automatización, data, scripts, glue code, prototipos ML/analytics. | Productividad, ecosistema data, scripting excelente. | Performance y empaquetado requieren disciplina; no ideal para camino crítico ultra sensible sin cuidado. |
| Rust | Componentes de alto rendimiento, seguridad de memoria, parsers, infraestructura crítica. | Control fino, performance, safety. | Curva alta, compilación más lenta, menor disponibilidad de equipo. |

## 3. Cuándo pasar de TypeScript a Go

No migraría de TypeScript a Go sólo porque Go “es más performante”. Lo haría cuando el problema encaje mejor con las propiedades operativas de Go.

Señales fuertes:

- el servicio es CPU-light pero necesita mucha concurrencia de I/O con footprint bajo;
- se necesita distribuir un binario único sin runtime Node/Bun;
- se está construyendo una CLI, agente, sidecar o herramienta de plataforma;
- los cold starts o tiempos de arranque importan;
- el consumo de memoria por proceso afecta costos;
- la lógica de negocio es simple y el valor está en networking, scheduling, IO o integración de sistema;
- el equipo necesita una herramienta portable para CI/local/devops.

Señales débiles o insuficientes:

- “TypeScript está de moda/Go está de moda”;
- problemas de diseño del dominio;
- deuda de arquitectura que puede resolverse con boundaries;
- performance no medida;
- errores por falta de tests o contratos.

Ejemplo de criterio:

| Caso | Mantendría TypeScript | Pasaría a Go |
|---|---|---|
| API CRUD/BFF con muchas integraciones web | Sí | No necesariamente |
| CLI de smoke/demo para todo el repo | No necesariamente | Sí |
| Worker liviano con alto fan-out HTTP | Depende | Probablemente |
| Servicio de dominio con reglas complejas y equipo Node fuerte | Sí | Sólo si hay evidencia |
| Sidecar/agent de plataforma | No ideal | Sí |

## 4. Cuándo construir herramientas de Developer Experience

Construir tooling propio se justifica cuando reduce fricción repetida y riesgo operativo.

Buenas razones:

- hay comandos largos, frágiles o difíciles de recordar;
- varios stacks necesitan una interfaz común;
- hay que levantar/apagar servicios sin dañar procesos ajenos;
- los tests tienen dependencias de infraestructura y conviene orquestarlos;
- se quiere estandarizar outputs para CI y humanos;
- hay tareas repetidas que consumen tiempo y generan errores;
- se quiere bajar la barrera de entrada para nuevos contributors o agentes.

Malas razones:

- reemplazar herramientas estándar sin necesidad;
- esconder complejidad sin documentarla;
- construir una plataforma interna antes de tener patrones repetidos;
- crear abstracciones que sólo entiende una persona.

En este repo, `./nx` se justifica porque unifica:

- build;
- test;
- bench;
- demo;
- auditorías;
- lifecycle de servicios;
- integración con infra local;
- comandos seguros para apagar apps propias.

## 5. Cuándo empezar con IaC

IaC conviene cuando la infraestructura deja de ser incidental y pasa a ser parte del producto o de la operación.

Señales para empezar:

- más de un ambiente: dev, staging, prod;
- recursos cloud creados manualmente;
- configuración difícil de reproducir;
- permisos, redes o secrets con riesgo de drift;
- onboarding lento;
- necesidad de revisar cambios de infraestructura por PR;
- auditoría/compliance;
- disaster recovery;
- costos que dependen de configuración reproducible.

No esperaría a tener “muchísima infraestructura”. Empezaría temprano con una base mínima:

- red/VPC;
- subnets;
- cluster o runtime principal;
- base de datos/cache;
- buckets/colas/topics;
- IAM/roles;
- secrets;
- observabilidad básica.

## 6. Terraform, Pulumi o CDK

| Herramienta | Cuándo la elegiría | Fortalezas | Riesgos |
|---|---|---|---|
| Terraform / OpenTofu | Equipos grandes, multi-cloud, necesidad de estándar amplio y módulos reutilizables. | Ecosistema enorme, lenguaje declarativo, plan/apply conocido, adopción amplia. | HCL limitado para lógica compleja; manejo de estado exige disciplina. |
| Pulumi | Equipos que prefieren lenguajes generales y necesitan abstraer patrones complejos. | TypeScript/Go/Python/C#, testing más natural, composición fuerte. | Puede habilitar demasiada lógica imperativa; menor estándar organizacional que Terraform. |
| CDK | Foco fuerte en un cloud específico y equipos cómodos con su SDK/ecosistema. | Integración nativa, alto nivel de abstracción, buen fit si ya se eligió ese cloud. | Menor portabilidad; puede acoplarse mucho al proveedor. |

Criterio práctico:

- si quiero estándar transversal y portabilidad: Terraform/OpenTofu;
- si quiero plataforma interna con componentes programables: Pulumi;
- si estoy profundamente acoplado a un proveedor y quiero velocidad ahí: CDK.

## 7. Cuándo pasar a multi-AZ

Multi-AZ suele ser el primer salto serio de disponibilidad.

Lo justificaría cuando:

- el negocio no tolera caída por falla de una zona;
- el RTO/RPO exige alta disponibilidad real;
- la base de datos/cache/colas soportan replicación zonal;
- el tráfico justifica balanceo y redundancia;
- el costo extra es menor que el costo esperado de downtime.

Para sistemas transaccionales, multi-AZ suele ser razonable antes que multi-región porque mejora disponibilidad sin introducir tanta complejidad de consistencia global.

## 8. Cuándo pasar a multi-región

Multi-región es más costoso y complejo. Lo justificaría por una o más de estas razones:

- uptime objetivo muy alto que una sola región no puede garantizar;
- recuperación ante desastre regional;
- latencia geográfica para usuarios distribuidos;
- requisitos regulatorios de residencia de datos;
- dependencia crítica de una región con historial de incidentes;
- necesidad de active-active o active-passive con RTO bajo.

Preguntas antes de decidir:

- ¿El negocio necesita active-active o alcanza active-passive?
- ¿Cuál es el RTO y RPO real?
- ¿Qué datos pueden replicarse eventualmente?
- ¿Qué operaciones requieren consistencia fuerte?
- ¿Cómo se resuelven idempotencia, ordering y conflictos?
- ¿Cuánto cuesta probar failover regularmente?

Regla práctica:

> “Multi-región no es un switch de infraestructura; es una decisión de arquitectura de datos, operación y producto.”

## 9. Cuándo pasar a multi-cloud

Multi-cloud sólo se justifica con razones muy fuertes. No lo adoptaría por default.

Buenas razones:

- exigencia contractual o regulatoria;
- riesgo de concentración de proveedor inaceptable;
- estrategia corporativa explícita;
- necesidad de continuidad ante caída de proveedor completo;
- poder de negociación/costos a gran escala;
- workloads específicos donde otro proveedor tiene una ventaja clara.

Costos reales:

- duplicación de skills;
- observabilidad más compleja;
- networking e identidad más difíciles;
- IaC y módulos más abstractos;
- soporte operativo 24/7 más exigente;
- menor uso de servicios administrados específicos si se busca portabilidad.

Regla práctica:

> “Multi-cloud para uptime sólo se justifica si el costo de una caída del proveedor supera claramente el costo permanente de operar dos plataformas.”

## 10. Uptime como justificación

Una forma simple de ordenar la conversación:

| Objetivo | Posible estrategia | Comentario |
|---|---|---|
| 99.5% | Una región, backups, buen monitoreo | Puede alcanzar para sistemas internos o no críticos. |
| 99.9% | Multi-AZ, healthchecks, deploy seguro | Suele ser el primer estándar razonable. |
| 99.95% | Multi-AZ fuerte, DR probado, SLOs | Requiere operación madura. |
| 99.99% | Multi-región active-passive o active-active parcial | Ya exige diseño de datos y failover. |
| 99.999% | Multi-región activo-activo, automatización avanzada | Muy caro; sólo para dominios críticos. |

El número de nueves no debe elegirse por marketing. Debe salir del costo de downtime:

```text
costo esperado = probabilidad de caída × duración × impacto por minuto
```

Si ese costo supera la inversión de arquitectura, se justifica avanzar.

## 11. Cuándo construir una consola interna agnóstica al cloud

Una consola interna o plataforma de developer self-service se justifica cuando la organización necesita abstraer complejidad repetida y estandarizar operación.

Buenas señales:

- muchos equipos creando servicios;
- onboarding de servicios lento o inconsistente;
- demasiados caminos manuales para deploy, permisos, secretos, observabilidad o rollback;
- cloud provider expone demasiada superficie para equipos de producto;
- se necesita golden path para crear APIs, workers, topics, buckets, dashboards y alertas;
- hay requirements de compliance/auditoría;
- se quiere medir ownership, costos, health y SLOs por servicio;
- hay múltiples clouds, regiones o runtimes que conviene ocultar detrás de una experiencia común.

No la construiría si:

- hay pocos equipos;
- el problema se resuelve con documentación y templates;
- todavía no hay patrones estables;
- el equipo plataforma no puede mantenerla;
- se convierte en cuello de botella centralizado.

## 12. Backstage vs consola propia

| Opción | Cuándo conviene |
|---|---|
| Backstage | Catálogo de servicios, documentación, ownership, templates y plugins con estándar OSS. Buen primer paso. |
| Consola propia | Cuando hay workflows internos muy específicos, integración profunda con permisos/costos/deploys o experiencia de plataforma diferenciada. |
| Híbrido | Backstage como base de catálogo + plugins propios para workflows críticos. Suele ser el camino más pragmático. |

Criterio recomendado:

1. empezar con catálogo, ownership y templates;
2. medir fricción real;
3. agregar plugins/workflows internos;
4. sólo construir consola completa si hay suficiente escala y necesidad diferenciada.

## 13. Relación con este repo

Este repo demuestra una versión pequeña de esos criterios:

- `./nx` funciona como interfaz común de ingeniería local;
- las PoCs comparan monolito, layer-as-pod y service-to-service;
- los guardrails simulan políticas de plataforma;
- los scripts de lifecycle evitan matar servicios externos;
- la documentación y primitivas funcionan como golden path para agentes y humanos;
- k6, smoke y ATDD aportan evidencia antes de prometer performance o disponibilidad.

La idea escalada sería convertir estos patrones en capacidades de plataforma:

- scaffolding de servicios;
- templates con observabilidad y tests;
- creación de topics/colas/buckets vía IaC;
- dashboards y alertas estándar;
- ownership y runbooks;
- comandos seguros de deploy/rollback;
- catálogo de APIs/eventos;
- controles de costo y compliance.

## 14. Preguntas para discusión técnica

- ¿Qué métrica justificaría migrar un servicio de TypeScript a Go?
- ¿Qué parte del sistema debería seguir en JVM?
- ¿Qué tooling de DX construirías y qué delegarías a herramientas estándar?
- ¿Cuándo una CLI interna se vuelve plataforma?
- ¿Qué recursos cloud deberían entrar primero en IaC?
- ¿Qué criterio usarías para elegir Terraform/OpenTofu, Pulumi o CDK?
- ¿Cuándo alcanza multi-AZ y cuándo hace falta multi-región?
- ¿Qué costo de downtime justifica active-active?
- ¿Qué perderías buscando portabilidad multi-cloud?
- ¿Cuándo Backstage alcanza y cuándo hace falta una consola propia?

## 15. Links relacionados

- [`docs/45-requisitos-y-estilos-arquitectonicos.md`](45-requisitos-y-estilos-arquitectonicos.md)
- [`docs/44-guion-presentacion-tecnica.md`](44-guion-presentacion-tecnica.md)
- [`docs/42-documentacion-metodica.md`](42-documentacion-metodica.md)
- [`docs/27-test-runner.md`](27-test-runner.md)
- [`vault/02-Decisions/0044-lambda-vs-eks-positioning.md`](../vault/02-Decisions/0044-lambda-vs-eks-positioning.md)
- [`vault/05-Methodology/Technical-Leadership-Mindset.md`](../vault/05-Methodology/Technical-Leadership-Mindset.md)
