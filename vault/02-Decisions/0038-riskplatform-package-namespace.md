---
adr: "0038"
title: Keep io.riskplatform.poc.* package namespace as legacy technical identifier
status: accepted
date: 2026-05-07
tags: [decision/accepted, architecture, java, packaging]
---

# ADR-0038: Keep `io.riskplatform.poc.*` package namespace as legacy technical identifier

## Context

El repo se está reformulando narrativamente como **"Risk Decision Platform — Three-Architecture Exploration"**: una exploración técnica genérica sobre patrones de fraude en tiempo real, desacoplada del sponsor original. La narrativa pública (READMEs, docs, agent configs, vault) se está neutralizando.

Sin embargo, los identificadores técnicos del código siguen usando el namespace heredado:

- Java packages: `io.riskplatform.rules.*`, `io.riskplatform.events.*`, etc.
- JVM artifact coordinates: `io.riskplatform.poc:risk-client-java:1.x`, `io.riskplatform.poc:risk-events:1.x`.
- Schema URNs: `urn:riskplatform:risk:event:v1`, `urn:riskplatform:idempotency:v1`.
- Secrets Manager keys / config paths: `riskplatform/db-password`, `riskplatform/kafka-creds`.
- Filesystem paths: `src/main/java/io/riskplatform/poc/...`.

Recuento aproximado: ~120 ocurrencias distribuidas en 4 PoCs (`java-risk-engine`, `java-vertx-distributed`, `vertx-risk-platform`, `k8s-local`) + 3 SDKs publicables (`risk-client-java`, `risk-client-go`, `risk-client-typescript`) + tests Karate/Cucumber + manifests Helm/k8s.

Renombrar requiere refactor masivo, version-bump de SDKs publicados, re-cableado de imports, regeneración de schemas, y actualización de smoke tests / ATDD steps.

## Decision

Mantener el namespace técnico `io.riskplatform.poc.*` (y sus variantes URN / path) como **identificador heredado del sponsor de la exploración**. No es copy de marketing: es un identifier estable que vive en imports, coordinates Gradle y URNs de schema. Renombrarlo no aporta valor técnico y rompe la identidad publicable de los SDKs.

La narrativa pública (README, docs, vault, agent configs) sí se neutraliza — esa es la capa donde el posicionamiento importa. El consistency-auditor excluye explícitamente los matches que caen dentro de package paths e identifiers técnicos (ver Tarea 2 / `audit_prohibited_terms`).

## Consequences

- **Pros:**
  - Zero churn en código de producción y tests.
  - Los SDKs (`io.riskplatform.poc:risk-client-java:1.x`, etc.) mantienen identidad estable y no requieren re-publish.
  - Schemas URN y Secrets paths permanecen válidos sin migración.
- **Cons:**
  - Reviewers ven `riskplatform` en imports al leer el código, lo cual contradice superficialmente la narrativa pública.
  - Mitigación: este ADR documenta explícitamente la razón, y el auditor está configurado para no marcarlo como falso positivo.
- **Mitigations:**
  - `consistency-auditor.py` agrega whitelist de `TECHNICAL_IDENTIFIER_PATTERNS` (ver `.ai/audit-rules/terminology.yaml` sección `technical_identifiers_excluded`).
  - El README aclara que `riskplatform` en code-paths es el namespace heredado, no marketing.

## Alternatives Considered

- **A (chosen): Keep namespace, document.**
  - Cost: 0.
  - Risk: cosmetic mismatch entre narrativa pública e imports.
- **B: Rename to `com.example.risk.*` o `io.platform.risk.*`.**
  - Cost: 4-8h refactor + version bump SDKs + re-publish.
  - Risk: rompe wiring downstream, requires consumer migration, schema URN re-issue.
- **C: Rename to `com.<personal-handle>.risk.*`.**
  - Cost: igual a B.
  - Risk: ata la identidad del repo al autor individual; menos genérico que B.

## Related

- [[0024-ai-directory]]
- [[0034-doc-driven-vault-structure]]
- `.ai/audit-rules/terminology.yaml` (sección `technical_identifiers_excluded`)
- `.ai/scripts/consistency-auditor.py` (función `audit_prohibited_terms`)
