---
adr: "0046"
title: Motor de reglas declarativo — decisiones de diseño
status: accepted
date: 2026-05-12
tags: [decision/accepted, adr, rules-engine, declarative, fraud, aggregation, hot-reload]
source_archive: docs/18-rules-engine-design.md (migrado 2026-05-12, parte de decisión)
---

# ADR-0046: Motor de reglas declarativo — decisiones de diseño

## Contexto

Las reglas de fraude responden a dos drivers que IT no controla:

1. **Presion regulatoria**: el BCRA o la red (Visa/MC) emiten circulares con plazos de horas. Esperar un release cycle significa incumplimiento.
2. **Descubrimiento de patrones**: el equipo de Riesgo corre analisis ad-hoc y detecta un patron nuevo (ej. chargebacks concentrados en MCC 7995 los lunes). Necesitan activar la regla ese dia, no en dos semanas.

Si las reglas viven en codigo Java, cada cambio pasa por: PR review, CI, artefacto, deploy. Eso es incompatible con la velocidad que requiere la operacion de fraude en una fintech.

**El contrato es**: Riesgo define la politica, IT construye el interprete. El interprete no cambia; la politica cambia todo el tiempo.

### Restricciones operativas

| Constraint | Valor |
|---|---|
| Throughput objetivo | 150 TPS |
| Latencia p99 | 300ms (budget total del request) |
| Budget del rules engine | < 10ms p99 (< 3.3% del budget total) |
| Auditabilidad | Reconstruir decision N meses despues con la version exacta de reglas usada |
| Hot reload | Sin reinicio; decisiones in-flight no deben ver estado inconsistente |

## Decisión

### ADR-001: Worst Case Wins con Allowlist Override como Aggregation Policy

**Status**: Accepted

Adoptar `worst_case_with_allowlist_override`: el peor resultado (DECLINE > REVIEW > FLAG > ALLOW) gana, excepto cuando una regla de tipo `allowlist` con `override: true` dispara, en cuyo caso el resultado es ALLOW independientemente de otras reglas.

**Razonamiento**:

1. **Worst case** es el mas conservador para fraude: si alguna regla dice DECLINE, no queremos que un score bajo de otra regla diluya esa senal. En fraude, el costo de un falso negativo supera ampliamente el costo de un falso positivo.
2. **Allowlist override** cortocircuita la evaluacion para clientes de confianza verificados.
3. **Timeout cap**: si la evaluacion supera `timeout_ms`, se devuelve `fallback_decision: REVIEW`. Esto evita que un lookup lento bloquee el request.

**Lo que se resigna**: no hay scoring continuo. Si el equipo de Riesgo quiere scoring, se puede agregar `"weighted_score"` como politica alternativa sin cambiar la arquitectura del engine — es un parametro, no un cambio de codigo.

### ADR-002: YAML en disco para PoC, progresion a ConfigMap y AppConfig

**Status**: Accepted

Tres etapas: YAML en disco (PoC), ConfigMap k8s (cluster), AWS AppConfig (prod). El schema YAML es estable entre etapas; solo cambia el mecanismo de entrega.

| Opcion | Pros | Contras |
|---|---|---|
| YAML en disco | Versionable en git, simple, legible por humanos | Requiere file watch; no observable out-of-the-box |
| ConfigMap k8s | Nativo al cluster; watch via API; GitOps con ArgoCD | Acoplado a k8s; no util en local |
| DB tabla | Flexible; admin UI natural | Latencia en reads; esquema extra; no readable por humanos directamente |
| Remote config service (AppConfig/LaunchDarkly/Unleash) | Audit incorporado; rollout gradual; observable | Dependencia externa; coste; over-engineering para PoC |

La razon de la progresion es **reversibilidad**: el contrato entre el interprete y el config es el schema YAML. Si ese schema es estable, cambiar el mecanismo de entrega no requiere modificar el engine.

### Hot reload: swap atomico con AtomicReference

```java
AtomicReference<RuleConfig> currentConfig = new AtomicReference<>(loadFromDisk());
```

Swap directo con `AtomicReference`. Cada request lee `currentConfig.get()` al inicio de la evaluacion y usa esa referencia hasta el final.

La alternativa (drain + swap) introduce latencia bajo carga, que es el peor momento para que ocurra un reload.

### Arquitectura de evaluacion: FeatureSnapshot pre-materializado

```
RiskRequest
    |
    v
FeatureExtractor  --> FeatureSnapshot { amountCents, newDevice, customerAgeDays, ... }
    |
    v
RuleEngine.evaluate(FeatureSnapshot, RuleConfig)
    |
    v
RuleDecision { action, triggeredRules, rulesVersionHash, evalMs }
```

`FeatureExtractor` materializa todos los campos antes de que el engine empiece. Esto permite paralelismo, testabilidad, e indexado por campo.

### Pre-compilacion

Cada regla se compila a un `Predicate<FeatureSnapshot>` al momento del load, no en cada evaluacion.

### Targets de performance

| Metrica | Target |
|---|---|
| Evaluacion de 100 reglas | < 1ms p99 |
| Hot reload (swap AtomicReference) | < 100 microsegundos |
| FeatureExtraction (sin IO remoto) | < 5ms p99 |
| FeatureExtraction (con IO: chargeback store) | < 20ms p99 |

## Consecuencias

- Mas facil: la logica de aggregation es determinista y explicable.
- Mas dificil: no hay scoring continuo; no hay estado persistido del circuito entre reinicios.
- AppConfig tiene coste y dependencia externa. En prod hay que planificar el acceso offline (cache local) para casos de degradacion del servicio de config.

## Estado de implementacion (2026-05-07)

| Componente | Clase / Modulo | Estado | Tests |
|---|---|---|---|
| FraudRule interface | `pkg/risk-domain` — `rule/FraudRule.java` | DONE | — |
| RuleAction enum (severity ordering) | `rule/RuleAction.java` | DONE | RULE-01..04 |
| FeatureSnapshot (signal bag) | `engine/FeatureSnapshot.java` | DONE | — |
| ThresholdRule | `rule/threshold/ThresholdRule.java` | DONE | RULE-05..07 |
| CombinationRule | `rule/combination/CombinationRule.java` | DONE | RULE-08..10 |
| VelocityRule | `rule/velocity/VelocityRule.java` | DONE | RULE-11..13 |
| RuleEngineImpl (AtomicReference reload) | `engine/RuleEngineImpl.java` | DONE | ENG-01..18 |
| AggregationPolicy (worst-case + allowlist override) | `engine/AggregationPolicy.java` | DONE | ENG-05..09 |
| RulesConfigLoader (YAML + SHA-256) | `config/RulesConfigLoader.java` | DONE | CFG-01..06 |
| RulesAuditTrail (bounded deque) | `audit/RulesAuditTrail.java` | DONE | AUD-01..05 |
| BlockingBarrier | `mode/BlockingBarrier.java` | DONE | MODE-01 |
| ShadowMode (virtual thread) | `mode/ShadowMode.java` | DONE | MODE-02 |
| CircuitMode (p99 sliding window) | `mode/CircuitMode.java` | DONE | MODE-03 |

**Total tests**: 57 (unit + integration). Todos pasan. BUILD SUCCESSFUL (119 tasks).

## Principio de diseño clave

> Las reglas de fraude no las decide IT — las decide Riesgo. Por eso el engine no es un set de classes Java, es un interprete de configuracion con audit trail. La diferencia entre una decision correcta y un escandalo regulatorio puede ser una regla cargada con error humano; ese error tiene que ser auditable, reversible y atribuible.

## Relacionado

- [[Rules-Engine]] — concepto: qué es un rules engine, modelo, patrones y tipos de reglas.
- [[Barrier-Shadow-Circuit-Modes]] — los tres modos de operación del engine.
- [[Idempotency]] — idempotencia de decisiones.
- [[Latency-Budget]] — presupuesto de latencia que el engine debe respetar.
- [[Risk-Platform-Overview]]
