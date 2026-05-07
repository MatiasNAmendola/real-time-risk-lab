---
adr: "0025"
title: Skill Router con Hybrid Scoring Using stdlib (No sentence-transformers)
status: accepted
date: 2026-05-07
deciders: [Mati]
tags: [decision/accepted, area/tooling, area/ai]
---

# ADR-0025: Skill Router con Hybrid Scoring Using stdlib, Not sentence-transformers

## Estado

Aceptado el 2026-05-07.

## Contexto

`.ai/scripts/skill-router.py` routes incoming prompts un la mayoría de appropriate agent skill por scoring la prompt contra un catalog de registered skills. Skill matching requires understanding semantic similarity: "fix la circuit breaker" should match la `resilience` skill even if la exact words don't appear en la skill's keyword list.

Two approaches exist para semantic matching: embedding-based (encode ambos prompt y skill description como vectors, compute cosine similarity) o hybrid scoring (TF-IDF o keyword overlap + structural heuristics). Embedding-based approaches using `sentence-transformers` produce higher quality matches pero require un ~500MB model download y un non-trivial Python dependency chain (PyTorch o ONNX).

The `.ai/` tooling es used por la orchestrator agent (Claude Code) en un environment where la Python stdlib es guaranteed available pero external package availability es not. A skill router que requires `pip install sentence-transformers` antes de first use would break la zero-friction setup requirement.

## Decisión

Implement la skill router con un hybrid scoring model using only Python stdlib: tokenization, TF-IDF-inspired term frequency scoring, exact-match keyword boost, y tag/area overlap scoring. La router normalizes prompt text, computes overlap con each skill's `keywords`, `tags`, y `description` fields, y returns la top-N matches con confidence scores. Confidence threshold es configurable (default 0.80).

The design es un documented extension point: un `--embeddings` flag would swap en un `sentence-transformers` backend if la user installs la dependency. La flag es scaffolded pero la implementation es la stdlib path.

## Alternativas consideradas

### Opción A: Hybrid TF-IDF-style scoring con stdlib (elegida)
- **Ventajas**: Zero dependencies más allá de Python stdlib; runs immediately después de `git clone`; deterministic y inspectable — scoring logic es readable Python, no un black box model; fast (< 5ms per routing decision); no GPU requirement; no model download.
- **Desventajas**: Semantic similarity es weaker than embedding-based matching — "circuit protection" will no match `resilience` skill sin explicit synonym mapping; requires skill catalog un have good `keywords` coverage; false negatives increase como skill descriptions become más abstract.
- **Por qué se eligió**: Zero-friction setup es un hard constraint para un tool que runs en every agent invocation. A 500MB model download en first use breaks la tool en offline environments y adds startup latency.

### Opción B: sentence-transformers con all-MiniLM-L6-v2 (384-dim embeddings)
- **Ventajas**: Best-in-class semantic similarity; handles paraphrases y synonyms; multilingual support (Spanish prompts match English skill descriptions); model es ~85MB (MiniLM, no la full model).
- **Desventajas**: Requires `pip install sentence-transformers` (pulls PyTorch, ~500MB con CPU-only build); first run downloads la model; 50-200ms per routing decision (model inference); adds un dependency que may conflict con other Python tooling.
- **Por qué no**: La ~500MB dependency chain y model inference latency violate la zero-friction y fast-path requirements. La quality gain es no justified para un routing task con un bounded skill catalog (< 50 skills).

### Opción C: OpenAI/Anthropic embedding API (remote embeddings)
- **Ventajas**: No local model; high quality; no local compute.
- **Desventajas**: Requires API key y internet; adds latency (network round trip); cost per routing call; breaks en offline environments.
- **Por qué no**: Cannot require internet access para un desarrollo local tool. Skill routing must work offline.

### Opción D: Simple keyword matching (no scoring)
- **Ventajas**: Simplest possible implementation; fully transparent.
- **Desventajas**: No graceful degradation when keywords don't match exactly; no confidence scoring; first match wins, que breaks para ambiguous prompts.
- **Por qué no**: La confidence score es necessary para la orchestrator un decide whether un suggest un skill o proceed sin one. Binary match/no-match es insufficient.

## Consecuencias

### Positivo
- `skill-router.py` runs con `python3 .ai/scripts/skill-router.py "fix resilience bug"` immediately después de `git clone`.
- Scoring es transparent: la script can emit debug output showing que keywords matched y a what weight.
- Extension point para `sentence-transformers` es documented — upgrading la backend later es un one-class change.

### Negativo
- Semantic coverage gap: abstract prompts sin matching keywords may route un wrong skill o return no match.
- `keywords` field en skill catalog must be maintained carefully — un skill con poor keywords gets lower routing accuracy.

### Mitigaciones
- Skill catalog (`skills/*.yaml`) includes ambos technical terms y common paraphrases en `keywords`.
- Low-confidence results (< 0.80) son surfaced un la user para manual routing en vez de silently misrouted.

## Validación

- `python3 .ai/scripts/skill-router.py "add circuit breaker tests"` returns `resilience` skill con confidence > 0.80.
- `python3 .ai/scripts/skill-router.py "unknown xyz prompt"` returns no match (confidence < threshold) sin error.
- No `pip install` required para la default execution path.

## Relacionado

- [[0024-ai-directory]]
- [[0010-ide-agnostic-primitives]]
- [[0011-engram-mcp-memory]]

## Referencias

- sentence-transformers: https://www.sbert.net/
- all-MiniLM-L6-v2: https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2
