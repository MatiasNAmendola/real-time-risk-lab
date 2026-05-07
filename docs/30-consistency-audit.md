---
title: Documentation Consistency Audit
tags: [tooling, docs, quality, audit]
created: 2026-05-07
updated: 2026-05-07
---

# 30 — Documentation Consistency Audit

## Por qué existe este auditor

`coverage-audit.py` mide si los artefactos tienen documentación (README presente, frontmatter válido, wikilinks íntegros).  Lo que no mide es si esa documentación está *conectada*: si hay Q&A que cubra cada decisión mayor, si los paths citados siguen existiendo, si todos los artifacts aparecen en algún MOC o doc de referencia, o si el vocabulario es consistente en todo el repo.

Este auditor llena ese hueco.  Sus seis sub-audits en conjunto responden la pregunta: *"¿Puede un nuevo colaborador navegar este repo sin perderse?"*

> "Coverage de tests es lo que mide casi todo el mundo. Coverage de **menciones cruzadas** entre artifacts y documentación es lo que distingue un repo navegable de un repo que parece bien pero está lleno de orphans."

---

## Los seis sub-audits

### 1. `inventory`

Escanea el filesystem y produce un catálogo por categoría:

| Categoría | Fuente |
|---|---|
| PoCs | `poc/*/` |
| Módulos Gradle | `settings.gradle.kts` (bloque `include`) |
| Docs | `docs/*.md` |
| ADRs | `vault/02-Decisions/*.md` |
| Primitivas | `.ai/primitives/{skills,rules,workflows,hooks}/` |
| Scripts | `scripts/`, `.ai/scripts/*.py` |
| SDKs | `sdks/*/` |
| Conceptos | `vault/04-Concepts/*.md` |
| Sessions | `vault/01-Sessions/*.md` |

Solo informa; no produce score.  Útil como punto de partida antes de los otros sub-audits.

### 2. `orphans`

Para cada artifact (excepto sessions), verifica que aparezca mencionado al menos una vez *fuera* de su propio directorio en:
- `README.md` raíz
- `docs/*.md`
- `vault/**/*.md`
- `.ai/context/*.md`

Un artifact con cero menciones externas es un *orphan*: existe en el repo pero nadie llega a él navegando la documentación.

Por cada orphan el auditor sugiere dónde linkarlo según su categoría (ej. ADRs -> `vault/00-MOCs/Architecture.md`).

**Score:** `(artifacts_con_menciones / artifacts_totales) * 100`

### 3. `qa-coverage`

Para cada decisión mayor (ADRs clave, PoCs, SDKs) verifica si alguna pregunta en los archivos Q&A (`docs/09-architecture-question-bank.md`, `docs/01-design-conversation-framework.md`, `vault/05-Methodology/Architecture-Question-Bank.md`) hace referencia al artifact por nombre, path o concepto-clave.

Heurística: al menos uno de los `key_terms` del artifact debe aparecer (case-insensitive) en el corpus Q&A.

Para cada gap el auditor genera una pregunta-sugerida con el template por tipo:
- PoC: "Why did you build the {name} PoC? What architectural decision does it validate?"
- SDK: "How does the {name} SDK work? What contract does it enforce across consumers?"
- ADR: "Walk me through the decision behind {concept}. What alternatives did you reject?"

**Score:** `(artifacts_con_coverage / total_artifacts) * 100`

### 4. `xrefs`

Parsea wikilinks `[[...]]` y links Markdown `[texto](path)` en todos los `.md` bajo `vault/` y `docs/`.  Para cada destino verifica que el archivo exista en el vault index o en el filesystem.

Links externos (`https://`, `mailto:`) se ignoran.

Reporta: `file:line  [[ref]]  [wikilink|mdlink]`

**Score:** `(refs_válidos / refs_totales) * 100`

### 5. `stale`

Detecta menciones a paths relativos o con prefijos conocidos (`poc/`, `docs/`, `vault/`, `sdks/`, `.ai/`, `scripts/`, etc.) en el contenido de todos los `.md`.  Para cada path citado verifica que exista en el filesystem (relativo a la raíz del repo o al directorio del archivo).

Útil para detectar referencias a archivos que se renombraron o movieron.

**Score:** `(refs_existentes / refs_totales) * 100`

### 6. `terms`

Carga `.ai/audit-rules/terminology.yaml` y para cada término con aliases escanea todos los `.md` buscando el alias en lugar del canonical.

No auto-corrige: imprime `found 'X' at file:line — should be 'Y'` y deja la decisión al autor.

**Score:** `max(0, 100 - (violations / total_checks) * 100)`

### 7. `prohibited-terms`

Detecta vocabulario relacionado con interview-prep o nombres de empresa específicos en docs públicos.

#### Qué considera prohibido

| Categoría | Términos |
|---|---|
| `interview` | `interview`, `entrevista`, `simulacro`, `cheatsheet` |
| `company_specific` | `Naranja X`, `NaranjaX`, `Naranja-X` |

#### Por qué

El repo se posiciona como exploración técnica de una plataforma de fraude de nivel productivo.  Los términos de `interview` rompen ese posicionamiento y señalan que el contenido es material de preparación de entrevista, no ingeniería genuina.  Los nombres de empresa `Naranja X` / `NaranjaX` exponen al empleador en contextos públicos donde la intención es hablar de patrones y decisiones genéricos.

#### Excepciones permitidas

Los siguientes paths son excluidos del audit porque el contexto histórico los justifica:

| Path | Razón |
|---|---|
| `vault/01-Build-Log/` | Narrativa histórica; los términos pueden aparecer como referencia al origen del proyecto |
| `.ai/research/` | Resultados de WebSearch; mencionan IDEs y productos nominalmente |
| `out/` | Outputs de runs, no docs públicas |
| `_personal/` | Carpeta privada (reservada para uso futuro) |
| `docs/30-consistency-audit.md` | Self-reference: este documento *describe* las reglas, por lo que necesariamente cita los términos prohibidos como ejemplos |
| `vault/02-Decisions/0038-naranjax-package-namespace.md` | ADR que justifica la decisión de mantener `naranjax` como namespace de packages — necesariamente menciona el nombre |
| `.kiro/`, `.windsurf/`, `.cursor/` | Steering files de IDE-agents (Kiro, Windsurf, Cursor): contexto privado para asistentes de código, no docs públicas |

- Excludes technical identifiers (see ADR-0038): líneas que contienen `com.naranjax`, `urn:naranjax:`, `naranjax/<key>` o `com/naranjax/` no se marcan, porque son identifiers de código (Java packages, Maven coords, schema URNs, Secrets Manager keys, paths) y no copy de marketing. La whitelist está definida en `TECHNICAL_IDENTIFIER_PATTERNS` (`.ai/scripts/consistency-auditor.py`) y documentada en `.ai/audit-rules/terminology.yaml` sección `technical_identifiers_excluded`.

#### Cómo agregar más términos prohibidos

1. Editar `.ai/audit-rules/terminology.yaml`, sección `prohibited_in_public`:
   ```yaml
   prohibited_in_public:
     - terms: ["nuevo-termino", "variante"]
       rationale: "Por qué no debe aparecer en docs publicos"
       excluded_paths: ["vault/01-Build-Log/"]
   ```

2. Editar `.ai/scripts/consistency-auditor.py`, dict `PROHIBITED_TERMS`:
   ```python
   PROHIBITED_TERMS: dict[str, list[str]] = {
       "interview": [...],
       "company_specific": [...],
       "nueva_categoria": ["nuevo-termino", "variante"],
   }
   ```

   Si la nueva categoría debe ignorarse en paths distintos a los globales, agregar el path a `PROHIBITED_TERMS_EXCLUDED`.

**Score:** `max(0, 100 - matches * 5)`  (cada match resta 5 puntos)

---

## Cómo leer un report

```
=== ORPHANS (32 / 209) ===

  ORPHAN  docs/22-client-sdks.md
          category: docs  ->  suggest: README.md or docs/00-START-HERE.md
```

- El número entre paréntesis es `(orphans / artifacts_checked)`.
- `suggest` indica dónde agregar el link para resolver el orphan.

```
=== QA COVERAGE (9/21 = 43%) ===

  GAP  sdks/risk-client-go  [SDK]
       missing terms: risk-client-go, go sdk, Go SDK
       suggested Q: How does the risk-client-go SDK work? ...
       add to: Bloque G — SDKs y contratos
```

- `missing terms` son los términos que deberían aparecer en el Q&A pero no están.
- `suggested Q` es una pregunta lista para agregar.
- ``add to` indicates the question bank block where it fits.

```
=== OVERALL CONSISTENCY SCORE: 77.8% (threshold: 80%) ===
```

Score ponderado de los seis sub-audits con score (orphans 20%, qa-coverage 25%, xrefs 20%, stale 15%, terms 15%, prohibited-terms 5%).

---

## Cómo agregar un nuevo término canonical

Editar `.ai/audit-rules/terminology.yaml`:

```yaml
terms:
  - canonical: "mi-termino-canonical"
    aliases:
      - "alias-1"
      - "alias 2"
    note: "Opcional: aclaración sobre cuándo usar este término."
```

Reglas de formato:
- `canonical`: la forma exacta que deben usar todos los docs.
- `aliases`: lista de formas alternativas que el auditor buscará.
- Indentación con 2 espacios; los aliases con 4 espacios + `- `.
- El auditor es case-sensitive: si el alias puede aparecer en mayúsculas y minúsculas, listar ambas variantes.

---

## Cómo agregar una nueva categoría de artifact

1. En `.ai/scripts/consistency-auditor.py`, en la función `audit_inventory()`, agregar un nuevo bloque que scanee el directorio o pattern correspondiente y lo append a `artifacts['nueva_categoria']`.

2. En el dict `ORPHAN_SUGGESTION` agregar: `'nueva_categoria': 'vault/00-MOCs/MiMOC.md'`.

3. Si la categoría tiene artifacts que deben aparecer en Q&A, agregar entradas en `ARTIFACT_CONCEPTS` con `artifact`, `type`, y `key_terms`.

4. Agregar la clave al init de `artifacts` en `audit_inventory()`:
   ```python
   artifacts['nueva_categoria'] = []
   ```

---

## Invocación

```bash
# Sub-audit individual
python3 .ai/scripts/consistency-auditor.py inventory
python3 .ai/scripts/consistency-auditor.py orphans
python3 .ai/scripts/consistency-auditor.py qa-coverage
python3 .ai/scripts/consistency-auditor.py xrefs
python3 .ai/scripts/consistency-auditor.py stale
python3 .ai/scripts/consistency-auditor.py terms
python3 .ai/scripts/consistency-auditor.py prohibited-terms

# Todo + report
python3 .ai/scripts/consistency-auditor.py all
python3 .ai/scripts/consistency-auditor.py all --report-md
python3 .ai/scripts/consistency-auditor.py all --strict   # exit 1 si score < 80%

# Via nx
./nx audit consistency

# Output en directorio específico
python3 .ai/scripts/consistency-auditor.py all --out-dir out/audit-consistency/run-1
```

El output en `--out-dir` incluye: `summary.md`, `summary.txt`, y un JSON por sub-audit (`orphans.json`, `qa_coverage.json`, etc.).

---

## Relación con coverage-audit.py

`coverage-audit.py docs` invoca este auditor internamente y usa su score como factor adicional en `overall_pct`.  La ponderación dentro de `audit_docs` lo trata como un eje más entre los seis existentes, contribuyendo al score agregado de documentación que aparece en `./nx audit docs`.
