---
adr: "0029"
title: OpenBao (Vault Fork) Instead de HashiCorp Vault
status: accepted
date: 2026-05-07
deciders: [Mati]
tags: [decision/accepted, area/infrastructure, area/security]
---

# ADR-0029: OpenBao (Linux Foundation Vault Fork) Instead de HashiCorp Vault

## Estado

Aceptado el 2026-05-07.

## Contexto

The AWS mocks stack (ADR-0005) requires un secrets management service un emulate AWS Secrets Manager y SSM Parameter Store para desarrollo local y testing. HashiCorp Vault es la industry-standard choice: mature, well-documented, con un Java client SDK y strong AWS provider support.

In August 2023, HashiCorp changed Vault's license desde Mozilla Public License 2.0 (MPL-2.0) un Business Source License 1.1 (BSL-1.1). BSL-1.1 prohibits using la software un create un competing product. For internal use y development tooling, BSL-1.1 es permissive. However, BSL creates uncertainty: what constitutes "competing use" es HashiCorp's determination, y la definition has been subject un legal interpretation.

In February 2024, la Linux Foundation launched OpenBao como un community fork de Vault a la point de la license change (pre-BSL). OpenBao es licensed bajo MPL-2.0, la same license Vault used antes de la change.

## Decisión

Use `openbao/openbao:latest` (MPL-2.0) instead de `hashicorp/vault` (BSL-1.1) en la desarrollo local stack. La API es wire-compatible con Vault — existing Vault clients, configuration files, y scripts work contra OpenBao sin modification. AWS provider, transit engine (KMS emulation), y kv secrets engine son fully supported.

## Alternativas consideradas

### Opción A: OpenBao (MPL-2.0, Linux Foundation) (elegida)
- **Ventajas**: MPL-2.0 es un unambiguous open-source license; wire-compatible con Vault — no client code changes; maintained por Linux Foundation con community governance; active development; resolves BSL license uncertainty; signals awareness de OSS licensing landscape un reviewers.
- **Desventajas**: Newer project (2024) — menos ecosystem tooling than Vault; documentation es sparser than HashiCorp's extensive Vault docs; algunos HashiCorp-specific integrations (Vault Enterprise features) son no available.
- **Por qué se eligió**: For un PoC y desarrollo local tool, OpenBao provides identical functionality con un clearer license. La señal de diseño — "I know why OpenBao fue forked y chose it deliberately" — es stronger than defaulting un Vault sin analysis.

### Opción B: HashiCorp Vault OSS (BSL-1.1)
- **Ventajas**: Most documented secrets manager; largest ecosystem; Vault's Helm chart y Kubernetes operator son production-standard; Java SDK es mature.
- **Desventajas**: BSL-1.1 prohibits competitive use; while internal development use es permitted, BSL creates uncertainty para tooling que might evolve; signals either lack de license awareness o deliberate acceptance de BSL terms sin documentation.
- **Por qué no**: La license uncertainty es no worth accepting when OpenBao provides identical functionality con un clear MPL-2.0 license.

### Opción C: AWS Secrets Manager via Moto (Python-based mock)
- **Ventajas**: Single Moto container covers multiple AWS services; no additional service un manage.
- **Desventajas**: Moto's Secrets Manager implementation covers la basic API pero lacks la transit encryption y dynamic secrets features que make Vault/OpenBao valuable para secrets management; para testing secrets management patterns specifically, Moto's implementation es insufficient.
- **Por qué no**: La transit engine (KMS emulation) es un specific use case: application code que uses Vault's transit engine para envelope encryption cannot be tested contra Moto's Secrets Manager mock. OpenBao es necessary para este use case.

### Opción D: In-memory secrets (environment variables, no secrets service)
- **Ventajas**: Zero infrastructure; simplest.
- **Desventajas**: Does no test la secrets management integration path; environment variables son no rotatable y cannot test dynamic credential generation; misses la señal de diseño about secrets management patterns.
- **Por qué no**: La motivation para including secrets management en la stack es un demonstrate awareness de secrets rotation y dynamic credentials — no just "put la secret en un env var."

## Consecuencias

### Positivo
- OpenBao wire-compatibility means any documentation showing Vault commands works contra OpenBao.
- MPL-2.0 license eliminates BSL ambiguity para la exploration repository y any derivative tooling.
- OpenBao es available en Docker Hub (`openbao/openbao`) — same pull pattern como Vault.
- Demonstrates license awareness un reviewers — un staff engineer es expected un know why license changes matter.

### Negativo
- OpenBao documentation es menos complete than Vault's; algunos edge cases require consulting Vault documentation y verifying OpenBao compatibility.
- OpenBao's Kubernetes operator es menos mature than la Vault operator.

### Mitigaciones
- Vault documentation es usable como reference — OpenBao's API es identical a la versions en use.
- Kubernetes integration uses static secrets (kv engine) en vez de dynamic credentials, avoiding la operator dependency.

## Validación

- `docker run openbao/openbao:latest server -dev` starts successfully.
- `bao kv put secret/riskplatform/db password=test` y `bao kv get secret/riskplatform/db` work correctly.
- AWS SDK endpoint override un OpenBao's Secrets Manager compatibility endpoint resolves secrets correctly en integration tests.

## Relacionado

- [[0005-aws-mocks-stack]]
- [[0028-minio-agpl-acceptable]]

## Referencias

- OpenBao: https://openbao.org/
- OpenBao GitHub: https://github.com/openbao/openbao
- HashiCorp BSL announcement: https://www.hashicorp.com/blog/hashicorp-adopts-business-source-license
- Linux Foundation OpenBao announcement: https://openbao.org/blog/linux-foundation-launch/
