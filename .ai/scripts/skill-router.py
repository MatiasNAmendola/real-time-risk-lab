#!/usr/bin/env python3
"""
skill-router.py — CLI tool to index and query AI skills by natural language.

Usage:
    skill-router.py "agregar un consumer Kafka"
    skill-router.py --top 5 "como expongo un endpoint REST nuevo"
    skill-router.py --json "outbox pattern"
    skill-router.py --reindex
    skill-router.py --list
    skill-router.py --skill add-kafka-consumer
    skill-router.py --rebuild-cache
"""
from __future__ import annotations

import argparse
import json
import math
import os
import re
import sys
import difflib
from pathlib import Path
from typing import Any


# ---------------------------------------------------------------------------
# Paths
# ---------------------------------------------------------------------------
SCRIPT_DIR = Path(__file__).resolve().parent
REPO_ROOT = SCRIPT_DIR.parent.parent
SKILLS_DIR = REPO_ROOT / ".ai" / "primitives" / "skills"
CACHE_DIR = SCRIPT_DIR / ".cache"
CACHE_FILE = CACHE_DIR / "skills-index.json"


# ---------------------------------------------------------------------------
# Mini YAML frontmatter parser (stdlib only)
# ---------------------------------------------------------------------------

def _parse_yaml_value(raw: str) -> Any:
    """Parse a simple YAML value: string, list, null, bool."""
    raw = raw.strip()
    if raw.startswith("[") and raw.endswith("]"):
        inner = raw[1:-1]
        if not inner.strip():
            return []
        parts = re.findall(r'"([^"]*?)"|\'([^\']*?)\'|([^,\[\]]+)', inner)
        items = []
        for p in parts:
            item = (p[0] or p[1] or p[2]).strip()
            if item:
                items.append(item)
        return items
    if raw.lower() == "null" or raw == "~":
        return None
    if raw.lower() == "true":
        return True
    if raw.lower() == "false":
        return False
    # strip optional quotes
    if (raw.startswith('"') and raw.endswith('"')) or (
        raw.startswith("'") and raw.endswith("'")
    ):
        return raw[1:-1]
    return raw


def parse_frontmatter(text: str) -> tuple[dict[str, Any] | None, str]:
    """
    Extract YAML frontmatter delimited by --- from *text*.

    Returns (frontmatter_dict, body) or (None, full_text) on failure.
    Handles inline lists and multiline block lists (- item).
    """
    lines = text.splitlines()
    if not lines or lines[0].strip() != "---":
        return None, text

    end_idx = None
    for i, line in enumerate(lines[1:], start=1):
        if line.strip() == "---":
            end_idx = i
            break
    if end_idx is None:
        return None, text

    fm_lines = lines[1:end_idx]
    body = "\n".join(lines[end_idx + 1 :])

    result: dict[str, Any] = {}
    current_key: str | None = None
    current_list: list[str] | None = None

    for line in fm_lines:
        # blank line
        if not line.strip():
            continue
        # list item continuation
        m_item = re.match(r"^(\s+)-\s+(.*)", line)
        if m_item and current_key and current_list is not None:
            current_list.append(m_item.group(2).strip().strip("\"'"))
            continue
        # key: value
        m_kv = re.match(r"^([A-Za-z_][A-Za-z0-9_\-]*):\s*(.*)", line)
        if m_kv:
            # flush previous list
            if current_key and current_list is not None:
                result[current_key] = current_list
                current_list = None
            current_key = m_kv.group(1)
            val_raw = m_kv.group(2).strip()
            if val_raw == "" or val_raw == "|" or val_raw == ">":
                # start block list or multiline — init list
                current_list = []
                result[current_key] = current_list
            else:
                parsed = _parse_yaml_value(val_raw)
                result[current_key] = parsed
                if isinstance(parsed, list):
                    current_list = parsed
                else:
                    current_list = None
        # else: unrecognised line, skip

    # flush trailing list
    if current_key and current_list is not None:
        result[current_key] = current_list

    return result, body


# ---------------------------------------------------------------------------
# Cache helpers
# ---------------------------------------------------------------------------

def _load_cache() -> dict | None:
    if not CACHE_FILE.exists():
        return None
    try:
        with CACHE_FILE.open() as f:
            return json.load(f)
    except Exception:
        return None


def _save_cache(data: dict) -> None:
    CACHE_DIR.mkdir(parents=True, exist_ok=True)
    with CACHE_FILE.open("w") as f:
        json.dump(data, f, indent=2)


def _skills_mtime_map() -> dict[str, float]:
    if not SKILLS_DIR.exists():
        return {}
    return {
        str(p): p.stat().st_mtime
        for p in sorted(SKILLS_DIR.glob("*.md"))
    }


def _cache_is_valid(cache: dict) -> bool:
    stored_mtimes: dict[str, float] = cache.get("mtimes", {})
    current_mtimes = _skills_mtime_map()
    return stored_mtimes == current_mtimes


# ---------------------------------------------------------------------------
# Indexing
# ---------------------------------------------------------------------------

def index_skills(force: bool = False) -> list[dict]:
    """Return list of indexed skill dicts, using cache when valid."""
    if not SKILLS_DIR.exists():
        print(
            "ERROR: .ai/primitives/skills/ does not exist yet.\n"
            "Run skill-router after .ai/primitives/skills/ is populated.",
            file=sys.stderr,
        )
        sys.exit(1)

    cache = _load_cache()
    if not force and cache and _cache_is_valid(cache):
        return cache.get("skills", [])

    skills: list[dict] = []
    mtimes = _skills_mtime_map()

    for path_str, _ in mtimes.items():
        path = Path(path_str)
        try:
            text = path.read_text(encoding="utf-8")
        except Exception as exc:
            print(f"WARN: cannot read {path}: {exc}", file=sys.stderr)
            continue

        fm, body = parse_frontmatter(text)
        if fm is None:
            print(f"WARN: no valid frontmatter in {path.name}, skipping", file=sys.stderr)
            continue
        if not isinstance(fm, dict):
            print(f"WARN: invalid frontmatter in {path.name}, skipping", file=sys.stderr)
            continue

        skill = {
            "name": fm.get("name", path.stem),
            "intent": fm.get("intent", ""),
            "inputs": fm.get("inputs", []),
            "preconditions": fm.get("preconditions", []),
            "postconditions": fm.get("postconditions", []),
            "related_rules": fm.get("related_rules", []),
            "tags": fm.get("tags", []),
            "frontmatter": fm,
            "body": body,
            "path": str(path),
        }
        skills.append(skill)

    _save_cache({"mtimes": mtimes, "skills": skills})
    return skills


# ---------------------------------------------------------------------------
# TF-IDF helpers (manual, stdlib only)
# ---------------------------------------------------------------------------

def _tokenize(text: str) -> list[str]:
    return re.findall(r"[a-záéíóúüñA-ZÁÉÍÓÚÜÑ]+", text.lower())


def _tf(tokens: list[str]) -> dict[str, float]:
    if not tokens:
        return {}
    from collections import Counter
    counts = Counter(tokens)
    total = len(tokens)
    return {t: c / total for t, c in counts.items()}


def _idf(term: str, docs: list[list[str]]) -> float:
    n_docs = len(docs)
    if n_docs == 0:
        return 0.0
    containing = sum(1 for doc in docs if term in doc)
    if containing == 0:
        return 0.0
    return math.log((n_docs + 1) / (containing + 1)) + 1.0


def build_tfidf_corpus(skills: list[dict]) -> list[list[str]]:
    corpus = []
    for s in skills:
        tokens = _tokenize(s.get("body", "") + " " + s.get("intent", ""))
        corpus.append(tokens)
    return corpus


def tfidf_score(query_tokens: list[str], doc_tokens: list[str], corpus: list[list[str]]) -> float:
    if not query_tokens or not doc_tokens:
        return 0.0
    doc_tf = _tf(doc_tokens)
    score = 0.0
    for qt in query_tokens:
        idf = _idf(qt, corpus)
        score += doc_tf.get(qt, 0.0) * idf
    # normalise by query length
    return score / len(query_tokens)


# ---------------------------------------------------------------------------
# Scoring
# ---------------------------------------------------------------------------

def _ensure_list(v: Any) -> list[str]:
    if isinstance(v, list):
        return [str(x) for x in v]
    if v is None:
        return []
    return [str(v)]


def keyword_score(query_tokens: list[str], skill: dict) -> float:
    """Score based on exact token overlap with frontmatter fields."""
    fields = [
        skill.get("name", ""),
        skill.get("intent", ""),
    ] + _ensure_list(skill.get("tags")) + _ensure_list(skill.get("related_rules"))
    combined = " ".join(fields).lower()
    fm_tokens = _tokenize(combined)
    if not fm_tokens or not query_tokens:
        return 0.0
    fm_set = set(fm_tokens)
    matches = sum(1 for qt in query_tokens if qt in fm_set)
    return matches / len(query_tokens)


def fuzzy_score(query: str, skill: dict) -> float:
    """Fuzzy match of query against skill name."""
    name = skill.get("name", "")
    # Also try matching individual words of query against name
    ratio = difflib.SequenceMatcher(None, query.lower(), name.lower()).ratio()
    # word-level: try each word
    word_ratios = [
        difflib.SequenceMatcher(None, w, name.lower()).ratio()
        for w in query.lower().split()
        if len(w) >= 3
    ]
    best_word = max(word_ratios, default=0.0)
    return max(ratio, best_word)


def score_skill(
    query: str,
    query_tokens: list[str],
    skill: dict,
    doc_tokens: list[str],
    corpus: list[list[str]],
) -> float:
    kw = keyword_score(query_tokens, skill)
    tf = tfidf_score(query_tokens, doc_tokens, corpus)
    fz = fuzzy_score(query, skill)
    # Weighted combination: 50% keyword, 30% tfidf, 20% fuzzy
    return 0.50 * kw + 0.30 * tf + 0.20 * fz


def rank_skills(query: str, skills: list[dict], top_k: int = 3) -> list[dict]:
    query_tokens = _tokenize(query)
    corpus = build_tfidf_corpus(skills)
    scored = []
    for i, skill in enumerate(skills):
        doc_tokens = _tokenize(skill.get("body", "") + " " + skill.get("intent", ""))
        s = score_skill(query, query_tokens, skill, doc_tokens, corpus)
        scored.append({**skill, "score": round(s, 4)})
    scored.sort(key=lambda x: x["score"], reverse=True)
    return scored[:top_k]


# ---------------------------------------------------------------------------
# Output formatters
# ---------------------------------------------------------------------------

def _relative_path(abs_path: str) -> str:
    try:
        return str(Path(abs_path).relative_to(REPO_ROOT))
    except ValueError:
        return abs_path


def print_table(query: str, results: list[dict]) -> None:
    if not results:
        print("No skills indexed yet.")
        return
    print(f'\nTop {len(results)} skills for query: "{query}"\n')
    for i, r in enumerate(results, 1):
        tags = ", ".join(_ensure_list(r.get("tags"))) or "—"
        rel = _relative_path(r["path"])
        print(f"{i}. {r['name']:<50} score {r['score']:.2f}")
        print(f"   intent:  {r.get('intent', '')}")
        print(f"   tags:    {tags}")
        print(f"   path:    {rel}")
        print()


def print_json(query: str, results: list[dict]) -> None:
    output = {
        "query": query,
        "results": [
            {
                "name": r["name"],
                "score": r["score"],
                "intent": r.get("intent", ""),
                "path": _relative_path(r["path"]),
                "frontmatter": r.get("frontmatter", {}),
            }
            for r in results
        ],
    }
    print(json.dumps(output, indent=2, ensure_ascii=False))


# ---------------------------------------------------------------------------
# CLI
# ---------------------------------------------------------------------------

def build_parser() -> argparse.ArgumentParser:
    p = argparse.ArgumentParser(
        prog="skill-router.py",
        description=(
            "Index and query AI skills from .ai/primitives/skills/*.md.\n"
            "Returns the most relevant skills for a natural-language query."
        ),
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
Examples:
  skill-router.py "agregar un consumer Kafka"
  skill-router.py --top 5 "como expongo un endpoint REST nuevo"
  skill-router.py --json "outbox pattern"
  skill-router.py --reindex
  skill-router.py --list
  skill-router.py --skill add-kafka-consumer
  skill-router.py --rebuild-cache
        """,
    )
    p.add_argument("query", nargs="?", help="Natural language query")
    p.add_argument("--top", type=int, default=3, metavar="N", help="Return top N results (default: 3)")
    p.add_argument("--json", action="store_true", help="Output results as JSON")
    p.add_argument("--reindex", action="store_true", help="Re-index skills and show count")
    p.add_argument("--rebuild-cache", action="store_true", help="Force rebuild cache")
    p.add_argument("--list", action="store_true", help="List all indexed skill names")
    p.add_argument("--skill", metavar="NAME", help="Direct lookup by skill name")
    return p


def main() -> None:
    parser = build_parser()
    args = parser.parse_args()

    # --rebuild-cache / --reindex
    if args.rebuild_cache or args.reindex:
        skills = index_skills(force=True)
        print(f"Indexed {len(skills)} skill(s) into {CACHE_FILE}")
        if not skills:
            print("No skills found yet. Populate .ai/primitives/skills/ first.")
        return

    # --list
    if args.list:
        skills = index_skills()
        if not skills:
            print("No skills indexed yet.")
            return
        print(f"{'Name':<45} {'Tags'}")
        print("-" * 70)
        for s in sorted(skills, key=lambda x: x["name"]):
            tags = ", ".join(_ensure_list(s.get("tags"))) or "—"
            print(f"{s['name']:<45} {tags}")
        return

    # --skill NAME
    if args.skill:
        skills = index_skills()
        matches = [s for s in skills if s["name"] == args.skill]
        if not matches:
            print(f"Skill '{args.skill}' not found.", file=sys.stderr)
            sys.exit(1)
        if args.json:
            print_json(args.skill, [{**m, "score": 1.0} for m in matches])
        else:
            print_table(args.skill, [{**m, "score": 1.0} for m in matches])
        return

    # query
    if not args.query:
        parser.print_help()
        sys.exit(0)

    skills = index_skills()
    if not skills:
        print("No skills indexed yet. Populate .ai/primitives/skills/ first.")
        return

    results = rank_skills(args.query, skills, top_k=args.top)

    if args.json:
        print_json(args.query, results)
    else:
        print_table(args.query, results)


if __name__ == "__main__":
    main()
