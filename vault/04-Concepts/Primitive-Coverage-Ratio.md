---
title: Primitive Coverage Ratio
tags: [concept, methodology/coverage, meta]
created: 2026-05-12
source_archive: docs/33-codebase-access-audit.md (migrado 2026-05-12, Fase 3 Round 1)
---

# 33 - Codebase Access Audit

## Que mide

El **primitive coverage ratio**: que porcentaje de las operaciones de lectura van a primitivas (`.ai/primitives/`, `vault/`) versus directamente al codebase (`poc/`, `pkg/`, `sdks/`, `cli/`, `bench/`, `tests/`).

Formula:

```
ratio = (reads a PRIMITIVE + reads a VAULT) / (reads a PRIMITIVE + reads a VAULT + reads a CODEBASE) * 100
```

Interpretacion:
- **HIGH (>= 50%)** — las primitivas guian el trabajo, el agente consulta abstracciones antes del codigo.
- **MEDIUM (25-49%)** — adopcion parcial, algunos patrones van directo al codigo.
- **LOW (< 25%)** — el agente ignora las primitivas y lee codigo directamente.

## Como funciona

1. **Hook `read-tracker.sh`** — se ejecuta en cada `PreToolUse` para `Read`, `Glob` y `Grep`. Extrae el path del JSON de entrada via `jq` y lo escribe a `out/audit/reads-YYYY-MM-DD.jsonl`. Es no-bloqueante: siempre sale con exit 0.

2. **`codebase-access-auditor.py`** — lee los logs de los ultimos N dias, categoriza cada path segun las reglas en `CATEGORIES`, calcula el ratio y genera un informe. Stdlib Python puro.

Flujo:

```
Read/Glob/Grep tool call
       |
read-tracker.sh (PreToolUse hook)
       |
out/audit/reads-YYYY-MM-DD.jsonl
       |
codebase-access-auditor.py
       |
out/codebase-access-audit/<ts>/summary.md
```

## Como correr

```bash
# Informe rapido (stdout)
python3 .ai/scripts/codebase-access-auditor.py

# Via nx
./nx audit codebase-access

# Con opciones
python3 .ai/scripts/codebase-access-auditor.py --since 14 --json
python3 .ai/scripts/codebase-access-auditor.py --strict --threshold 30

# Tests
python3 -m unittest .ai/scripts/test_codebase_access_auditor.py
```

## Por que importa

Las primitivas son documentos de alto nivel que encapsulan patrones, decisiones y contratos del sistema. Si el agente las ignora y lee directamente el codigo fuente para resolver cada tarea, las primitivas son decoracion — existen pero no funcionan.

Este auditor hace observable lo que de otro modo seria invisible: si el sistema de primitivas realmente reduce la carga cognitiva sobre el codebase, o si es letra muerta.

Un ratio bajo no es un error de infraestructura — es una senal de que los primitivos no son suficientemente utiles, no estan bien indexados, o el skill-router no los sugiere en el momento correcto.

## Como aumentar el ratio

1. **Invocar skill-router antes de editar**: antes de escribir codigo, consultar `.ai/scripts/skill-router.py` para ver si existe un skill que ya resuelva el problema.
2. **Leer skills antes de codear**: si el skill-router sugiere un skill con score >= 0.80, leerlo en `.ai/primitives/` antes de abrir cualquier archivo de codigo.
3. **Mantener primitivos actualizados**: un primitivo desactualizado no se usa. El ratio bajo puede indicar que las primitivas no reflejan el estado actual del sistema.
4. **Agregar primitivos para patrones frecuentes**: si `top_files` muestra que un archivo de codebase se lee muchas veces, es candidato a tener un primitivo que lo documente.

## Design principle

> "Construi el sistema de primitivas, despues construi el sistema que mide si mi sistema usa el sistema. Esa segunda capa es la que distingue arquitectura propuesta de arquitectura observable."

## Related

- [[Meta-Coverage]]
- [[2026-05-07-primitive-usage-retro]]
- [[Agent-OS-Principles]]
- [[Risk-Platform-Overview]]
