# Skill Router

A zero-dependency CLI tool that indexes `.ai/primitives/skills/*.md` by frontmatter and returns the most relevant skills for a natural-language query.

Built for use as an **external tool** that any IDE, AI agent, or shell script can invoke.

---

## What it is

`skill-router.py` parses the YAML frontmatter of every skill file in `.ai/primitives/skills/`, builds a lightweight index, and scores skills against your query using a hybrid keyword + TF-IDF + fuzzy matching algorithm.

It answers questions like:
- "What skill do I use to add a Kafka consumer?"
- "How do I expose a new REST endpoint?"
- "Which skill covers the outbox pattern?"

---

## Use cases

### AI agent calling it via Bash tool (Claude Code)

```python
# In an agent system prompt or skill:
result = bash(".ai/scripts/skill-router.py --json 'agregar un consumer Kafka'")
skills = json.loads(result)["results"]
top_skill_path = skills[0]["path"]
```

### Human developer from the terminal

```bash
.ai/scripts/skill-router.py "outbox pattern"
.ai/scripts/skill-router.py --top 5 --json "REST endpoint"
```

---

## CLI flags

| Flag | Description |
|---|---|
| `<query>` | Natural language query (positional) |
| `--top N` | Return top N results (default: 3) |
| `--json` | Output as machine-readable JSON |
| `--list` | List all indexed skill names and tags |
| `--skill NAME` | Direct lookup by exact skill name |
| `--reindex` | Re-index skills, print count |
| `--rebuild-cache` | Force rebuild the cache file |

---

## How scoring works

Each query is matched against each skill using three signals combined into a weighted score:

| Signal | Weight | Description |
|---|---|---|
| Keyword match | 50% | Exact token overlap between query and frontmatter fields (`name`, `intent`, `tags`, `related_rules`) |
| TF-IDF (body) | 30% | Term frequency / inverse document frequency over the full skill body + intent text |
| Fuzzy name match | 20% | `difflib.SequenceMatcher` ratio between query words and skill `name` — handles typos |

**Formula:**

```
score = 0.50 * keyword_score + 0.30 * tfidf_score + 0.20 * fuzzy_score
```

All three are normalised to [0, 1]. The final score is also in [0, 1].

---

## How to add a new skill

1. Create a new `.md` file in `.ai/primitives/skills/` following the frontmatter schema:

```yaml
---
name: my-new-skill           # kebab-case, unique
intent: One-line description of what this skill does
inputs: [input1, input2]
preconditions: [precond1]
postconditions: [postcond1]
related_rules: [rule-name]
tags: [tag1, tag2]
---
# my-new-skill

Detailed instructions for the skill...
```

2. The router will pick it up automatically on the next query (cache is invalidated by mtime check).

3. To force re-index immediately:

```bash
.ai/scripts/skill-router.py --rebuild-cache
```

For the full skill authoring workflow, see `.ai/primitives/workflows/` (if present in your project).

---

## Performance

- **Cache**: on first run, all skills are indexed into `.ai/scripts/.cache/skills-index.json`.
- **mtime check**: subsequent runs skip re-indexing unless any `.md` file has changed.
- **Complexity**: O(n × m) where n = number of skills, m = number of tokens in query. Negligible for typical skill libraries (< 200 skills).
- **Cold start** (no cache, 100 skills): ~50–100 ms.
- **Warm start** (cache valid): ~5–15 ms.

The `.cache/` directory is gitignored automatically if your `.gitignore` includes `.cache/` entries.

---

## Limitations

- **No semantic embeddings**: scoring is purely lexical. For highly ambiguous or domain-specific queries, keyword overlap may be insufficient.
- **Language sensitivity**: the tokenizer is basic (regex word extraction). Mixing languages (Spanish / English) in a single query may reduce accuracy slightly.
- **No stemming**: "consumer" and "consumers" are different tokens. Write intent fields using base forms.
- **Exact-match bias**: TF-IDF rewards exact term matches; synonyms are not handled.

For semantic search, consider wrapping this CLI with an embedding-based reranker (e.g., via the Anthropic API) — but that requires a network call and API key.

---

## How to invoke from each IDE

### Claude Code (Bash tool)

```bash
# In any skill or system prompt:
.ai/scripts/skill-router.py --json "agregar un consumer Kafka"
```

Or via the wrapper:
```bash
bash .ai/scripts/skill-router.sh --json "outbox pattern"
```

### Cursor

Option A — Direct terminal call:
```bash
python3 .ai/scripts/skill-router.py "REST endpoint"
```

Option B — MCP tool wrapper: create an MCP server that shells out to `skill-router.py` and exposes a `query_skill` tool.

### Windsurf / GitHub Copilot

Call directly as a CLI tool from the terminal panel:
```bash
.ai/scripts/skill-router.py --list
.ai/scripts/skill-router.py "domain event"
```

### Codex CLI

Same as any shell call — Codex can invoke it via its `bash` executor:
```bash
python3 .ai/scripts/skill-router.py --json "add hexagonal port"
```

---

## Files

```
.ai/scripts/
├── skill-router.py          # Main CLI (Python 3.11+, stdlib only)
├── skill-router.sh          # Bash wrapper — detects Python 3.11+
├── test_skill_router.py     # unittest suite (stdlib only)
├── README-router.md         # This file
└── .cache/
    └── skills-index.json    # Auto-generated index cache (gitignored)
```
