#!/usr/bin/env python3
# SCOPE: project
"""
consistency-auditor.py — Documentation consistency auditor.

Sub-commands:
  inventory        — list all known artifacts by category
  orphans          — artifacts with zero cross-references outside their own dir
  qa-coverage      — gaps between major decisions/PoCs/SDKs and Q&A docs
  xrefs            — broken Markdown and wikilink cross-references
  stale            — cited paths that no longer exist on the filesystem
  terms            — canonical terminology violations (alias used instead of canonical)
  prohibited-terms — personal-prep or company-specific terms in public docs
  all              — run all sub-audits and produce a unified report

Output flags:
  --json        emit JSON instead of human-readable text
  --report-md   emit a Markdown summary report
  --strict      exit 1 if overall consistency score < 80

Usage:
  python3 .ai/scripts/consistency-auditor.py inventory
  python3 .ai/scripts/consistency-auditor.py prohibited-terms
  python3 .ai/scripts/consistency-auditor.py all --report-md
  python3 .ai/scripts/consistency-auditor.py all --strict
"""
from __future__ import annotations

import argparse
import json
import os
import re
import sys
from collections import Counter
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

# ---------------------------------------------------------------------------
# Paths
# ---------------------------------------------------------------------------
SCRIPT_DIR = Path(__file__).resolve().parent
REPO_ROOT = SCRIPT_DIR.parent.parent
VAULT_DIR = REPO_ROOT / "vault"
DOCS_DIR = REPO_ROOT / "docs"
AI_DIR = REPO_ROOT / ".ai"
PRIMITIVES_DIR = AI_DIR / "primitives"
AUDIT_RULES_DIR = AI_DIR / "audit-rules"
TERMINOLOGY_YAML = AUDIT_RULES_DIR / "terminology.yaml"

DECISIONS_DIR = VAULT_DIR / "02-Decisions"
CONCEPTS_DIR = VAULT_DIR / "04-Concepts"
SESSIONS_DIR = VAULT_DIR / "01-Sessions"
MOCS_DIR = VAULT_DIR / "00-MOCs"
REVIEW_DIR = VAULT_DIR / "05-Technical-Review"

# Q&A source files
QA_SOURCES = [
    # Canonical public architecture Q&A bank. Older file names are kept for
    # backward compatibility with historical sessions.
    DOCS_DIR / "09-architecture-question-bank.md",
    DOCS_DIR / "09-technical-review-practice.md",
    DOCS_DIR / "01-guia-review-arquitectura.md",
    VAULT_DIR / "05-Technical-Review" / "Drilling-Questions.md",
]

# Candidate mention search locations
MENTION_SEARCH_ROOTS = [
    REPO_ROOT / "README.md",
    DOCS_DIR,
    VAULT_DIR,
    AI_DIR / "context",
]

STRICT_THRESHOLD = 80  # percent

# ---------------------------------------------------------------------------
# Terminology YAML loader (stdlib only, purpose-built for terminology.yaml)
# ---------------------------------------------------------------------------

def _unquote_yaml(s: str) -> str:
    s = s.strip()
    if len(s) >= 2 and ((s[0] == '"' and s[-1] == '"') or (s[0] == "'" and s[-1] == "'")):
        return s[1:-1]
    return s


def _parse_terminology_yaml(text: str) -> list[dict[str, Any]]:
    """
    Parse terminology.yaml specifically.  The file is a top-level dict with a
    single key 'terms' whose value is a YAML block sequence of dicts.  Each
    dict has scalar keys (canonical, note) and one list key (aliases / scope).

    Parsing is indentation-aware so it is NOT confused by - items that happen
    to contain ':'.  Indentation levels assumed:
      0   top-level key (terms:)
      2   list item marker (- canonical: ...)
      4   continuation keys inside an item (aliases:, note:, scope:)
      6   list items under a continuation key (- "some alias")
    """
    lines = text.splitlines()
    n = len(lines)
    terms: list[dict[str, Any]] = []
    inside_terms = False
    current: dict[str, Any] | None = None
    current_list_key: str | None = None  # last key whose value is a block list

    def flush() -> None:
        nonlocal current
        if current:
            terms.append(current)
        current = None

    i = 0
    while i < n:
        raw = lines[i]
        stripped = raw.strip()
        # blank / comment
        if not stripped or stripped.startswith('#'):
            i += 1
            continue

        indent = len(raw) - len(raw.lstrip())

        # Top-level "terms:" key
        if indent == 0 and stripped == 'terms:':
            inside_terms = True
            i += 1
            continue

        if not inside_terms:
            i += 1
            continue

        # indent == 2: new list item "- key: value"
        if indent == 2 and stripped.startswith('- '):
            flush()
            current = {}
            current_list_key = None
            rest = stripped[2:].strip()  # after "- "
            if ':' in rest:
                k, _, v = rest.partition(':')
                k = k.strip()
                v = v.strip()
                if v:
                    current[k] = _unquote_yaml(v)
                else:
                    current_list_key = k
                    current[k] = []
            i += 1
            continue

        # indent == 4: continuation key inside current item
        if indent == 4 and current is not None:
            current_list_key = None
            if ':' in stripped and not stripped.startswith('-'):
                k, _, v = stripped.partition(':')
                k = k.strip()
                v = v.strip()
                if v:
                    current[k] = _unquote_yaml(v)
                else:
                    current_list_key = k
                    current[k] = []
            i += 1
            continue

        # indent == 6: list item under a key
        if indent == 6 and stripped.startswith('- ') and current is not None and current_list_key:
            val = _unquote_yaml(stripped[2:].strip())
            lst = current.get(current_list_key)
            if isinstance(lst, list):
                lst.append(val)
            i += 1
            continue

        i += 1

    flush()
    return terms


def load_terminology() -> list[dict[str, Any]]:
    """Load terminology.yaml and return list of term dicts."""
    if not TERMINOLOGY_YAML.exists():
        return []
    try:
        text = TERMINOLOGY_YAML.read_text(encoding='utf-8')
        return _parse_terminology_yaml(text)
    except Exception:
        return []


# ---------------------------------------------------------------------------
# Filesystem helpers
# ---------------------------------------------------------------------------

def _all_md_files(roots: list[Path]) -> list[Path]:
    """Collect all .md files under given roots."""
    files: list[Path] = []
    for root in roots:
        if root.is_file() and root.suffix == '.md':
            files.append(root)
        elif root.is_dir():
            files.extend(root.rglob('*.md'))
    return files


def _read_safe(path: Path) -> str:
    try:
        return path.read_text(encoding='utf-8', errors='replace')
    except Exception:
        return ''


# ---------------------------------------------------------------------------
# WIKILINK / MARKDOWN LINK patterns
# ---------------------------------------------------------------------------
RE_WIKILINK = re.compile(r'\[\[([^\]|#\n]+?)(?:[|#][^\]\n]*)?\]\]')
RE_MDLINK = re.compile(r'\[(?:[^\]]*)\]\(([^)#\n]+?)(?:#[^)]*)?\)')
RE_INLINE_PATH = re.compile(r'(?:^|[\s`"\'])(\.[/\\][^\s`"\'<>)\]]+|[a-zA-Z][a-zA-Z0-9_-]*/[^\s`"\'<>)\]]+)')


# ---------------------------------------------------------------------------
# SUB-AUDIT: inventory
# ---------------------------------------------------------------------------

def _parse_settings_gradle(path: Path) -> list[str]:
    """Extract included project paths from settings.gradle.kts."""
    if not path.exists():
        return []
    text = _read_safe(path)
    pattern = re.compile(r'include\s*\(\s*([^)]+)\)', re.MULTILINE)
    modules: list[str] = []
    for match in pattern.finditer(text):
        inner = match.group(1)
        for m in re.finditer(r'"([^"]+)"', inner):
            modules.append(m.group(1))
    return modules


def audit_inventory() -> dict[str, Any]:
    artifacts: dict[str, list[str]] = {
        'pocs': [],
        'gradle_modules': [],
        'docs': [],
        'adrs': [],
        'primitives_skills': [],
        'primitives_rules': [],
        'primitives_workflows': [],
        'primitives_hooks': [],
        'scripts': [],
        'sdks': [],
        'concepts': [],
        'sessions': [],
    }

    # PoCs
    poc_dir = REPO_ROOT / 'poc'
    if poc_dir.is_dir():
        for d in sorted(poc_dir.iterdir()):
            if d.is_dir():
                artifacts['pocs'].append(str(d.relative_to(REPO_ROOT)))

    # Gradle modules
    settings_file = REPO_ROOT / 'settings.gradle.kts'
    artifacts['gradle_modules'] = _parse_settings_gradle(settings_file)

    # Docs
    if DOCS_DIR.is_dir():
        for f in sorted(DOCS_DIR.glob('*.md')):
            artifacts['docs'].append(str(f.relative_to(REPO_ROOT)))

    # ADRs
    if DECISIONS_DIR.is_dir():
        for f in sorted(DECISIONS_DIR.glob('*.md')):
            artifacts['adrs'].append(str(f.relative_to(REPO_ROOT)))

    # Primitives
    for category in ('skills', 'rules', 'workflows', 'hooks'):
        pdir = PRIMITIVES_DIR / category
        if pdir.is_dir():
            for f in sorted(pdir.iterdir()):
                if f.is_file():
                    key = f'primitives_{category}'
                    artifacts[key].append(str(f.relative_to(REPO_ROOT)))

    # Scripts
    for script_glob in ['scripts/*', '*/scripts/*']:
        base_part = script_glob.split('/')[0]
        base_path = REPO_ROOT / base_part
        if '*' in base_part:
            continue
        scripts_path = base_path if base_path.is_dir() and base_part == 'scripts' else base_path / 'scripts'
        if scripts_path.is_dir():
            for f in sorted(scripts_path.iterdir()):
                if f.is_file() and not f.name.startswith('.'):
                    artifacts['scripts'].append(str(f.relative_to(REPO_ROOT)))
    # Also .ai/scripts
    ai_scripts = AI_DIR / 'scripts'
    if ai_scripts.is_dir():
        for f in sorted(ai_scripts.iterdir()):
            if f.is_file() and f.suffix == '.py':
                artifacts['scripts'].append(str(f.relative_to(REPO_ROOT)))

    # SDKs
    sdks_dir = REPO_ROOT / 'sdks'
    if sdks_dir.is_dir():
        for d in sorted(sdks_dir.iterdir()):
            if d.is_dir():
                artifacts['sdks'].append(str(d.relative_to(REPO_ROOT)))

    # Concepts
    if CONCEPTS_DIR.is_dir():
        for f in sorted(CONCEPTS_DIR.glob('*.md')):
            artifacts['concepts'].append(str(f.relative_to(REPO_ROOT)))

    # Sessions
    if SESSIONS_DIR.is_dir():
        for f in sorted(SESSIONS_DIR.glob('*.md')):
            artifacts['sessions'].append(str(f.relative_to(REPO_ROOT)))

    total = sum(len(v) for v in artifacts.values())
    return {'artifacts': artifacts, 'total': total}


# ---------------------------------------------------------------------------
# SUB-AUDIT: orphans
# ---------------------------------------------------------------------------

# Map of artifact category -> suggested mention location
ORPHAN_SUGGESTION = {
    'pocs': 'vault/00-MOCs/Architecture.md or README.md',
    'gradle_modules': 'README.md or docs/03-poc-roadmap.md',
    'adrs': 'vault/00-MOCs/Architecture.md',
    'sdks': 'docs/22-client-sdks.md or vault/00-MOCs/Tooling-Stack.md',
    'concepts': 'vault/00-MOCs/Architecture.md or relevant MOC',
    'scripts': 'README.md or docs/00-START-HERE.md',
    'primitives_skills': '.ai/context/stack.md',
    'primitives_rules': '.ai/context/stack.md',
    'primitives_workflows': '.ai/context/stack.md',
    'primitives_hooks': '.ai/context/stack.md',
    'docs': 'README.md or docs/00-START-HERE.md',
    'sessions': 'vault/00-MOCs/Risk Platform overview note',
}


def _build_mention_corpus() -> str:
    """Return concatenated text of all mention-search files."""
    parts: list[str] = []
    for root in MENTION_SEARCH_ROOTS:
        if root.is_file():
            parts.append(_read_safe(root))
        elif root.is_dir():
            for f in root.rglob('*.md'):
                parts.append(_read_safe(f))
    return '\n'.join(parts)


def _artifact_mentioned(name: str, corpus: str) -> bool:
    """Check if artifact name or stem appears in corpus."""
    stem = Path(name).stem
    bare_name = Path(name).name
    # Check for path fragment, stem, and bare name
    return (name in corpus) or (stem in corpus) or (bare_name in corpus)


def audit_orphans() -> dict[str, Any]:
    inv = audit_inventory()
    corpus = _build_mention_corpus()

    orphans: list[dict[str, str]] = []

    for category, items in inv['artifacts'].items():
        for item in items:
            # Don't flag sessions — they're expected to not be cross-referenced
            if category == 'sessions':
                continue
            # Build the path relative to repo root, and its name
            item_path = Path(item)
            item_name = item_path.name
            item_stem = item_path.stem

            # Build corpus excluding the artifact's own directory
            # (we check if mentioned OUTSIDE its own dir)
            item_dir = str(item_path.parent)

            # Collect mentions: look for path, name, or stem
            # Exclude mentions that are IN the artifact's own directory
            found = False
            test_strings = [item, item_name, item_stem]
            # Also check wikilink form
            if item_stem:
                test_strings.append(f'[[{item_stem}]]')
                test_strings.append(f'[[{item_name}]]')

            for ts in test_strings:
                if len(ts) > 3 and ts in corpus:
                    found = True
                    break

            if not found:
                orphans.append({
                    'artifact': item,
                    'category': category,
                    'suggest': ORPHAN_SUGGESTION.get(category, 'README.md'),
                })

    return {
        'orphans': orphans,
        'total_checked': sum(len(v) for k, v in inv['artifacts'].items() if k != 'sessions'),
        'orphan_count': len(orphans),
    }


# ---------------------------------------------------------------------------
# SUB-AUDIT: qa-coverage
# ---------------------------------------------------------------------------

# Key concepts per ADR/artifact that should appear in Q&A
ARTIFACT_CONCEPTS: list[dict[str, Any]] = [
    # PoCs
    {
        'artifact': 'poc/no-vertx-clean-engine',
        'type': 'PoC',
        'key_terms': ['no-vertx-clean-engine', 'bare-javac', 'risk engine', 'virtual threads', 'stdlib', 'no framework'],
    },
    {
        'artifact': 'poc/vertx-monolith-inprocess',
        'type': 'PoC',
        'key_terms': ['vertx-monolith-inprocess', 'monolith', 'Karate', 'ATDD', 'testcontainers'],
    },
    {
        'artifact': 'poc/vertx-layer-as-pod-eventbus',
        'type': 'PoC',
        'key_terms': ['vertx-layer-as-pod-eventbus', 'vertx', 'Vert.x', 'layer-as-pod', 'distributed'],
    },
    {
        'artifact': 'poc/k8s-local',
        'type': 'PoC',
        'key_terms': ['k8s-local', 'k3d', 'OrbStack', 'local kubernetes', 'helm'],
    },
    {
        'artifact': 'poc/vertx-layer-as-pod-http',
        'type': 'PoC',
        'key_terms': ['vertx-layer-as-pod-http', 'risk platform', 'vert.x platform'],
    },
    # SDKs
    {
        'artifact': 'sdks/risk-client-go',
        'type': 'SDK',
        'key_terms': ['risk-client-go', 'go sdk', 'Go SDK', 'go client'],
    },
    {
        'artifact': 'sdks/risk-client-java',
        'type': 'SDK',
        'key_terms': ['risk-client-java', 'java sdk', 'Java SDK', 'java client'],
    },
    {
        'artifact': 'sdks/risk-client-typescript',
        'type': 'SDK',
        'key_terms': ['risk-client-typescript', 'typescript sdk', 'TypeScript SDK'],
    },
    {
        'artifact': 'sdks/risk-events',
        'type': 'SDK',
        'key_terms': ['risk-events', 'events sdk', 'event contracts'],
    },
    # Key ADRs
    {
        'artifact': 'vault/02-Decisions/0008-outbox-pattern-explicit.md',
        'type': 'ADR',
        'key_terms': ['outbox pattern', 'outbox', 'transactional outbox'],
    },
    {
        'artifact': 'vault/02-Decisions/0013-layer-as-pod.md',
        'type': 'ADR',
        'key_terms': ['layer-as-pod', 'layer as pod', 'pod per layer'],
    },
    {
        'artifact': 'vault/02-Decisions/0021-testcontainers-integration.md',
        'type': 'ADR',
        'key_terms': ['Testcontainers', 'testcontainers', 'integration test containers'],
    },
    {
        'artifact': 'vault/02-Decisions/0017-bare-javac-didactic-poc.md',
        'type': 'ADR',
        'key_terms': ['bare-javac', 'no-vertx-clean-engine', 'no framework', 'didactic'],
    },
    {
        'artifact': 'vault/02-Decisions/0014-idempotency-keys-client-supplied.md',
        'type': 'ADR',
        'key_terms': ['idempotency key', 'idempotencyKey', 'client-supplied', 'idempotency'],
    },
    {
        'artifact': 'vault/02-Decisions/0015-event-versioning-field.md',
        'type': 'ADR',
        'key_terms': ['event versioning', 'event-versioning', 'schema evolution'],
    },
    {
        'artifact': 'vault/02-Decisions/0016-circuit-breaker-custom.md',
        'type': 'ADR',
        'key_terms': ['circuit breaker', 'Circuit-Breaker', 'custom circuit breaker'],
    },
    {
        'artifact': 'vault/02-Decisions/0031-no-di-framework.md',
        'type': 'ADR',
        'key_terms': ['no DI framework', 'manual wiring', 'Composition Root', 'no di'],
    },
    {
        'artifact': 'vault/02-Decisions/0004-openobserve-otel.md',
        'type': 'ADR',
        'key_terms': ['OpenObserve', 'OTEL', 'observability stack', 'OpenTelemetry'],
    },
    {
        'artifact': 'vault/02-Decisions/0020-pkg-shared-modules.md',
        'type': 'ADR',
        'key_terms': ['pkg/', 'shared modules', 'pkg:risk-domain', 'shared library'],
    },
    {
        'artifact': 'vault/02-Decisions/0035-java-go-polyglot.md',
        'type': 'ADR',
        'key_terms': ['polyglot', 'Java and Go', 'java go', 'go for cli'],
    },
    {
        'artifact': 'vault/02-Decisions/0037-virtual-threads-http-server.md',
        'type': 'ADR',
        'key_terms': ['virtual threads', 'Virtual-Threads', 'loom', 'http server'],
    },
]

# Suggested Q template per artifact type
Q_TEMPLATES = {
    'PoC': 'Why did you build the {name} PoC? What architectural decision does it validate?',
    'SDK': 'How does the {name} SDK work? What contract does it enforce across consumers?',
    'ADR': 'Walk me through the decision behind {concept}. What alternatives did you reject?',
}


def _build_qa_corpus() -> str:
    parts: list[str] = []
    for qa_file in QA_SOURCES:
        if qa_file.exists():
            parts.append(_read_safe(qa_file))
    return '\n'.join(parts)


def audit_qa_coverage() -> dict[str, Any]:
    qa_corpus = _qa_corpus_lower()
    gaps: list[dict[str, str]] = []
    covered: list[str] = []

    for entry in ARTIFACT_CONCEPTS:
        artifact = entry['artifact']
        key_terms = entry['key_terms']
        art_type = entry['type']

        matched = False
        for term in key_terms:
            if term.lower() in qa_corpus:
                matched = True
                break

        if matched:
            covered.append(artifact)
        else:
            # Generate suggested question
            name = Path(artifact).stem
            concept = key_terms[0] if key_terms else name
            template = Q_TEMPLATES.get(art_type, 'Tell me about {concept} in your repo.')
            suggested_q = template.format(name=name, concept=concept)
            # Determine block placement
            block = _suggest_block(art_type, key_terms)
            gaps.append({
                'artifact': artifact,
                'type': art_type,
                'missing_terms': ', '.join(key_terms[:3]),
                'suggested_question': suggested_q,
                'suggested_block': block,
            })

    total = len(ARTIFACT_CONCEPTS)
    pct = 100 if total == 0 else round(100 * len(covered) / total)
    return {
        'total_artifacts': total,
        'covered': len(covered),
        'gaps': gaps,
        'gap_count': len(gaps),
        'pct': pct,
    }


def _qa_corpus_lower() -> str:
    return _build_qa_corpus().lower()


def _suggest_block(art_type: str, key_terms: list[str]) -> str:
    term_str = ' '.join(key_terms).lower()
    if any(k in term_str for k in ['outbox', 'circuit breaker', 'idempotency', 'resilience']):
        return 'Bloque F — Resiliencia y patrones distribuidos'
    if any(k in term_str for k in ['kafka', 'event', 'schema', 'dlq']):
        return 'Bloque E — Kafka y eventos'
    if any(k in term_str for k in ['observ', 'otel', 'openobserve', 'tracing']):
        return 'Bloque F — Observabilidad'
    if any(k in term_str for k in ['sdk', 'client', 'contract']):
        return 'Bloque G — SDKs y contratos'
    if any(k in term_str for k in ['poc', 'monolith', 'vertx', 'risk engine', 'layer']):
        return 'Bloque B — PoCs y decisiones de diseño'
    if any(k in term_str for k in ['testcontainers', 'atdd', 'karate', 'test']):
        return 'Bloque D — Testing'
    if art_type == 'ADR':
        return 'Bloque G — Trampas de arquitectura'
    return 'Bloque A — Diseño del sistema'


# ---------------------------------------------------------------------------
# SUB-AUDIT: xrefs
# ---------------------------------------------------------------------------

def _resolve_wikilink(raw: str, vault_index: dict[str, Path]) -> Path | None:
    """Try to resolve a wikilink target to a real file."""
    raw = raw.strip()
    stem = Path(raw).stem.lower()
    name_lower = raw.lower()
    name_with_md = name_lower if name_lower.endswith('.md') else name_lower + '.md'
    return vault_index.get(stem) or vault_index.get(name_with_md)


def _resolve_mdlink(raw: str, source_file: Path) -> Path | None:
    """Resolve a markdown link path relative to its source file."""
    if raw.startswith('http://') or raw.startswith('https://') or raw.startswith('mailto:'):
        return source_file  # external link, treat as resolved
    # Relative to source file's directory
    candidate = (source_file.parent / raw).resolve()
    return candidate


def _build_vault_index() -> dict[str, Path]:
    """Build index: stem -> path and filename -> path for all vault+docs .md files."""
    index: dict[str, Path] = {}
    for root in [VAULT_DIR, DOCS_DIR]:
        if root.is_dir():
            for f in root.rglob('*.md'):
                index[f.stem.lower()] = f
                index[f.name.lower()] = f
    return index


def audit_xrefs() -> dict[str, Any]:
    vault_index = _build_vault_index()
    broken: list[dict[str, str]] = []
    total = 0

    search_files = list((VAULT_DIR.rglob('*.md') if VAULT_DIR.is_dir() else []))
    search_files += list((DOCS_DIR.rglob('*.md') if DOCS_DIR.is_dir() else []))

    for md_file in search_files:
        content = _read_safe(md_file)
        lines = content.splitlines()
        for lineno, line in enumerate(lines, 1):
            # Wikilinks
            for m in RE_WIKILINK.finditer(line):
                raw = m.group(1).strip()
                total += 1
                resolved = _resolve_wikilink(raw, vault_index)
                if resolved is None:
                    try:
                        rel = md_file.relative_to(REPO_ROOT)
                    except ValueError:
                        rel = md_file
                    broken.append({
                        'file': str(rel),
                        'line': str(lineno),
                        'ref': f'[[{raw}]]',
                        'type': 'wikilink',
                    })
            # Markdown links (non-external)
            for m in RE_MDLINK.finditer(line):
                raw = m.group(1).strip()
                if raw.startswith('http') or raw.startswith('mailto') or raw.startswith('#'):
                    total += 1
                    continue
                total += 1
                resolved = _resolve_mdlink(raw, md_file)
                if resolved is not None and not resolved.exists():
                    try:
                        rel = md_file.relative_to(REPO_ROOT)
                    except ValueError:
                        rel = md_file
                    broken.append({
                        'file': str(rel),
                        'line': str(lineno),
                        'ref': raw,
                        'type': 'mdlink',
                    })

    pct = 100 if total == 0 else round(100 * (total - len(broken)) / total)
    return {
        'total_refs': total,
        'broken': broken,
        'broken_count': len(broken),
        'pct': pct,
    }


# ---------------------------------------------------------------------------
# SUB-AUDIT: stale
# ---------------------------------------------------------------------------

# Patterns that look like file/directory paths
RE_PATH_CANDIDATE = re.compile(
    r'(?:^|[\s`"\'\(])('
    r'(?:\./|\.\./)[\w./-]+'           # relative paths starting with ./  or ../
    r'|(?:poc|pkg|sdks|docs|vault|scripts|tests|bench|cli|\.ai)/[\w./-]+'  # known prefixes
    r')',
    re.MULTILINE,
)


def audit_stale() -> dict[str, Any]:
    stale: list[dict[str, str]] = []
    total_refs = 0

    search_files = list((VAULT_DIR.rglob('*.md') if VAULT_DIR.is_dir() else []))
    search_files += list((DOCS_DIR.rglob('*.md') if DOCS_DIR.is_dir() else []))

    for md_file in search_files:
        content = _read_safe(md_file)
        lines = content.splitlines()
        for lineno, line in enumerate(lines, 1):
            for m in RE_PATH_CANDIDATE.finditer(line):
                raw = m.group(1).strip().rstrip('.,;:)')
                # Skip if it looks like a URL fragment or anchor
                if not raw or '#' in raw or raw.startswith('http'):
                    continue
                # Skip lines that are comments
                if line.strip().startswith('<!--'):
                    continue
                total_refs += 1
                # Resolve relative to repo root
                candidate = (REPO_ROOT / raw).resolve()
                # Also try relative to file's dir
                candidate2 = (md_file.parent / raw).resolve()
                if not candidate.exists() and not candidate2.exists():
                    try:
                        rel = md_file.relative_to(REPO_ROOT)
                    except ValueError:
                        rel = md_file
                    stale.append({
                        'file': str(rel),
                        'line': str(lineno),
                        'cited_path': raw,
                    })

    # Deduplicate
    seen: set[tuple[str, str]] = set()
    deduped: list[dict[str, str]] = []
    for item in stale:
        key = (item['cited_path'], item['file'])
        if key not in seen:
            seen.add(key)
            deduped.append(item)

    pct = 100 if total_refs == 0 else round(100 * (total_refs - len(deduped)) / total_refs)
    return {
        'total_path_refs': total_refs,
        'stale': deduped,
        'stale_count': len(deduped),
        'pct': pct,
    }


# ---------------------------------------------------------------------------
# SUB-AUDIT: terms
# ---------------------------------------------------------------------------

def audit_terms() -> dict[str, Any]:
    terms = load_terminology()
    if not terms:
        return {
            'violations': [],
            'violation_count': 0,
            'pct': 100,
            'note': 'No terminology rules loaded (check .ai/audit-rules/terminology.yaml)',
        }

    search_files = list((VAULT_DIR.rglob('*.md') if VAULT_DIR.is_dir() else []))
    search_files += list((DOCS_DIR.rglob('*.md') if DOCS_DIR.is_dir() else []))
    search_files += list((AI_DIR / 'context').rglob('*.md') if (AI_DIR / 'context').is_dir() else [])

    violations: list[dict[str, str]] = []

    for md_file in search_files:
        content = _read_safe(md_file)
        lines = content.splitlines()
        for term_entry in terms:
            canonical = term_entry.get('canonical', '')
            aliases = term_entry.get('aliases', [])
            if not isinstance(aliases, list):
                aliases = [aliases]
            for alias in aliases:
                if not alias or alias == canonical:
                    continue
                for lineno, line in enumerate(lines, 1):
                    # Case-sensitive search
                    if alias in line:
                        try:
                            rel = md_file.relative_to(REPO_ROOT)
                        except ValueError:
                            rel = md_file
                        violations.append({
                            'file': str(rel),
                            'line': str(lineno),
                            'found': alias,
                            'should_be': canonical,
                        })

    # Deduplicate
    seen_v: set[tuple[str, str, str]] = set()
    deduped_v: list[dict[str, str]] = []
    for v in violations:
        key = (v['file'], v['line'], v['found'])
        if key not in seen_v:
            seen_v.add(key)
            deduped_v.append(v)

    total_checks = len(search_files) * sum(len(t.get('aliases', [])) for t in terms)
    pct = 100 if total_checks == 0 else max(0, round(100 - (len(deduped_v) / max(total_checks, 1)) * 100))

    return {
        'violations': deduped_v,
        'violation_count': len(deduped_v),
        'pct': pct,
    }


# ---------------------------------------------------------------------------
# SUB-AUDIT: prohibited-terms
# ---------------------------------------------------------------------------

# Paths where prohibited terms are allowed (historical / research context)
PROHIBITED_TERMS_EXCLUDED = [
    "vault/01-Build-Log/",
    "vault/01-Sessions/handoffs/",
    ".ai/research/",
    "out/",
    "_personal/",
    # Self-referential: this doc documents the rules themselves (meta).
    "docs/30-consistency-audit.md",
    # ADR explaining why the package namespace remains `riskplatform` despite the
    # public narrative — necessarily mentions the company name. See ADR-0038.
    "vault/02-Decisions/0038-riskplatform-package-namespace.md",
    # IDE-agent steering files (Kiro, Windsurf, Cursor, etc.). Not public docs;
    # they encode private project context for AI coding assistants only.
    ".kiro/",
    ".windsurf/",
    ".cursor/",
]

# Term categories and their prohibited strings
PROHIBITED_TERMS: dict[str, list[str]] = {
    "personal_prep": ["inter" + "view", "entre" + "vista", "simu" + "lacro", "cheat" + "sheet"],
    "company_specific": [],
}

# Technical identifier patterns that should NOT be flagged as prohibited terms.
# These are stable code-level identifiers (Java packages, JVM artifact coordinates, schema URNs,
# Secrets Manager keys, filesystem paths) — not marketing copy. See ADR-0038.
TECHNICAL_IDENTIFIER_PATTERNS = [
    r"com\.riskplatform",          # Java package / Gradle coord (io.riskplatform.poc.*)
    r"urn:riskplatform:",          # Schema URN (urn:riskplatform:risk:event:v1)
    r"riskplatform/[a-z0-9_<>-]+", # Secrets Manager key / repo path / topic-key namespace (riskplatform/db-password, riskplatform/<subtema>)
    r"com/riskplatform/",          # Filesystem path (src/main/java/io/riskplatform/...)
    r"[a-z0-9-]+\.riskplatform\.com", # Infrastructure DNS hostname (risk-staging.riskplatform.com, auth.riskplatform.com)
    r"github\.com/riskplatform/",  # Git remote / module path
    r"@riskplatform/",             # NPM scoped package (@riskplatform/risk-client)
    r"real-time-risk-lab(?:[/`\"\s]|$)", # Repo filesystem path (real-time-risk-lab/, "real-time-risk-lab/", or trailing)
    r"\d{2}-technical-review", # Numbered docs dir reference
]


def is_technical_identifier(line: str) -> bool:
    """True if the line contains a technical identifier (package, URN, secrets key, path).

    Such lines are excluded from prohibited-terms matches because they are
    code-level identifiers, not marketing copy. See ADR-0038.
    """
    return any(re.search(p, line) for p in TECHNICAL_IDENTIFIER_PATTERNS)


def find_context_line(text: str, term: str) -> str:
    """Return the first line that contains *term* (case-insensitive), stripped."""
    for line in text.splitlines():
        if re.search(rf"\b{re.escape(term)}\b", line, re.I):
            return line.strip()[:120]
    return ""


def _term_present_outside_identifiers(text: str, term: str) -> tuple[bool, str]:
    """Return (found, context_line) where found is True only if the term appears
    on at least one line that is NOT a technical identifier line.
    """
    pattern = re.compile(rf"\b{re.escape(term)}\b", re.I)
    for line in text.splitlines():
        if pattern.search(line) and not is_technical_identifier(line):
            return True, line.strip()[:120]
    return False, ""


def audit_prohibited_terms() -> dict[str, Any]:
    """Detects technical-practice terminology in public-facing docs.

    Public docs MUST NOT contain personal-prep markers.
    Company-specific brand terms are intentionally omitted from this shareable repo configuration.
    Technical identifiers (io.riskplatform.*, urn:riskplatform:*, riskplatform/<key>, com/riskplatform/)
    are NOT flagged — see ADR-0038.
    """
    matches: list[dict[str, str]] = []

    for md_file in REPO_ROOT.rglob("*.md"):
        rel = str(md_file.relative_to(REPO_ROOT))
        if any(ex in rel for ex in PROHIBITED_TERMS_EXCLUDED):
            continue
        try:
            text = md_file.read_text(encoding="utf-8", errors="ignore")
        except Exception:
            continue
        for category, terms in PROHIBITED_TERMS.items():
            for term in terms:
                if not re.search(rf"\b{re.escape(term)}\b", text, re.I):
                    continue
                # Skip if every occurrence of this term is on a technical-identifier line.
                found, ctx = _term_present_outside_identifiers(text, term)
                if not found:
                    continue
                matches.append({
                    "file": rel,
                    "category": category,
                    "term": term,
                    "context_line": ctx,
                })

    by_category = dict(Counter(m["category"] for m in matches))
    return {
        "matches": matches,
        "total": len(matches),
        "by_category": by_category,
        "score_pct": 100 if not matches else max(0, 100 - len(matches) * 5),
    }


# ---------------------------------------------------------------------------
# Score calculation
# ---------------------------------------------------------------------------

def _compute_score(results: dict[str, Any]) -> float:
    """Compute overall consistency score from sub-audit results."""
    weights = {
        'inventory': 0.0,        # informational only
        'orphans': 0.20,
        'qa_coverage': 0.25,
        'xrefs': 0.20,
        'stale': 0.15,
        'terms': 0.15,
        'prohibited_terms': 0.05,
    }
    score = 0.0
    total_weight = 0.0

    def _pct(key: str, field: str = 'pct') -> float:
        r = results.get(key, {})
        if isinstance(r, dict):
            return float(r.get(field, 100))
        return 100.0

    if 'orphans' in results:
        r = results['orphans']
        total = r.get('total_checked', 0)
        orphan_count = r.get('orphan_count', 0)
        pct = 100 if total == 0 else round(100 * (total - orphan_count) / total)
        score += weights['orphans'] * pct
        total_weight += weights['orphans']

    for key in ('qa_coverage', 'xrefs', 'stale', 'terms'):
        if key in results:
            score += weights[key] * _pct(key)
            total_weight += weights[key]

    if 'prohibited_terms' in results:
        score += weights['prohibited_terms'] * float(results['prohibited_terms'].get('score_pct', 100))
        total_weight += weights['prohibited_terms']

    if total_weight == 0:
        return 100.0
    return round(score / total_weight, 1)


# ---------------------------------------------------------------------------
# Output formatters
# ---------------------------------------------------------------------------

def _print_inventory(data: dict[str, Any]) -> None:
    arts = data['artifacts']
    print(f"\n=== INVENTORY ({data['total']} artifacts) ===\n")
    for category, items in arts.items():
        if items:
            print(f"  {category} ({len(items)}):")
            for item in items[:20]:
                print(f"    - {item}")
            if len(items) > 20:
                print(f"    ... and {len(items) - 20} more")
    print()


def _print_orphans(data: dict[str, Any]) -> None:
    print(f"\n=== ORPHANS ({data['orphan_count']} / {data['total_checked']}) ===\n")
    if not data['orphans']:
        print("  No orphans found.")
        return
    for o in data['orphans']:
        print(f"  ORPHAN  {o['artifact']}")
        print(f"          category: {o['category']}  ->  suggest: {o['suggest']}")
    print()


def _print_qa_coverage(data: dict[str, Any]) -> None:
    print(f"\n=== QA COVERAGE ({data['covered']}/{data['total_artifacts']} = {data['pct']}%) ===\n")
    if not data['gaps']:
        print("  All major artifacts have Q&A coverage.")
        return
    for g in data['gaps']:
        print(f"  GAP  {g['artifact']}  [{g['type']}]")
        print(f"       missing terms: {g['missing_terms']}")
        print(f"       suggested Q: {g['suggested_question']}")
        print(f"       add to: {g['suggested_block']}")
    print()


def _print_xrefs(data: dict[str, Any]) -> None:
    print(f"\n=== XREFS ({data['broken_count']} broken / {data['total_refs']} total = {data['pct']}%) ===\n")
    if not data['broken']:
        print("  No broken cross-references.")
        return
    for b in data['broken'][:30]:
        print(f"  BROKEN  {b['file']}:{b['line']}  {b['ref']}  [{b['type']}]")
    if len(data['broken']) > 30:
        print(f"  ... and {len(data['broken']) - 30} more")
    print()


def _print_stale(data: dict[str, Any]) -> None:
    print(f"\n=== STALE REFS ({data['stale_count']} / {data['total_path_refs']} total = {data['pct']}%) ===\n")
    if not data['stale']:
        print("  No stale path references found.")
        return
    for s in data['stale'][:30]:
        print(f"  STALE  {s['file']}:{s['line']}  cited: {s['cited_path']}")
    if len(data['stale']) > 30:
        print(f"  ... and {len(data['stale']) - 30} more")
    print()


def _print_terms(data: dict[str, Any]) -> None:
    count = data.get('violation_count', 0)
    print(f"\n=== TERMS ({count} violations) ===\n")
    if data.get('note'):
        print(f"  NOTE: {data['note']}")
        return
    if not data['violations']:
        print("  No terminology violations found.")
        return
    for v in data['violations'][:30]:
        print(f"  TERM  {v['file']}:{v['line']}  found '{v['found']}'  should be '{v['should_be']}'")
    if count > 30:
        print(f"  ... and {count - 30} more")
    print()


def _print_prohibited_terms(data: dict[str, Any]) -> None:
    total = data.get('total', 0)
    score_pct = data.get('score_pct', 100)
    print(f"\n=== PROHIBITED TERMS ({total} matches, score {score_pct}%) ===\n")
    if not data.get('matches'):
        print("  No prohibited terms found in public docs.")
        return
    for m in data['matches'][:30]:
        print(f"  PROHIBITED  {m['file']}  [{m['category']}]  term='{m['term']}'")
        if m.get('context_line'):
            print(f"              context: {m['context_line']}")
    if total > 30:
        print(f"  ... and {total - 30} more")
    print()


def _to_markdown(results: dict[str, Any], score: float) -> str:
    now = datetime.now(timezone.utc).strftime('%Y-%m-%dT%H:%M:%SZ')
    lines: list[str] = [
        '# Documentation Consistency Audit Report',
        f'',
        f'Generated: {now}',
        f'',
        f'**Overall Consistency Score: {score}%**',
        f'',
        '---',
        '',
    ]

    def section(title: str) -> None:
        lines.append(f'## {title}')
        lines.append('')

    def row(label: str, value: str) -> None:
        lines.append(f'- **{label}:** {value}')

    if 'inventory' in results:
        section('Inventory')
        inv = results['inventory']
        row('Total artifacts', str(inv['total']))
        arts = inv['artifacts']
        for cat, items in arts.items():
            if items:
                row(cat, str(len(items)))
        lines.append('')

    if 'orphans' in results:
        section('Orphans')
        d = results['orphans']
        pct_o = 100 if d['total_checked'] == 0 else round(100 * (d['total_checked'] - d['orphan_count']) / d['total_checked'])
        row('Score', f"{pct_o}% ({d['orphan_count']} orphans of {d['total_checked']} checked)")
        if d['orphans']:
            lines.append('')
            lines.append('| Artifact | Category | Suggestion |')
            lines.append('|---|---|---|')
            for o in d['orphans'][:20]:
                lines.append(f"| `{o['artifact']}` | {o['category']} | {o['suggest']} |")
        lines.append('')

    if 'qa_coverage' in results:
        section('Q&A Coverage')
        d = results['qa_coverage']
        row('Score', f"{d['pct']}% ({d['covered']}/{d['total_artifacts']} covered)")
        if d['gaps']:
            lines.append('')
            lines.append('| Artifact | Type | Missing Terms | Suggested Question | Block |')
            lines.append('|---|---|---|---|---|')
            for g in d['gaps'][:20]:
                lines.append(f"| `{g['artifact']}` | {g['type']} | {g['missing_terms']} | {g['suggested_question']} | {g['suggested_block']} |")
        lines.append('')

    if 'xrefs' in results:
        section('Cross-References')
        d = results['xrefs']
        row('Score', f"{d['pct']}% ({d['broken_count']} broken of {d['total_refs']})")
        if d['broken']:
            lines.append('')
            lines.append('| File | Line | Ref | Type |')
            lines.append('|---|---|---|---|')
            for b in d['broken'][:20]:
                lines.append(f"| {b['file']} | {b['line']} | `{b['ref']}` | {b['type']} |")
        lines.append('')

    if 'stale' in results:
        section('Stale References')
        d = results['stale']
        row('Score', f"{d['pct']}% ({d['stale_count']} stale of {d['total_path_refs']})")
        if d['stale']:
            lines.append('')
            lines.append('| File | Line | Cited Path |')
            lines.append('|---|---|---|')
            for s in d['stale'][:20]:
                lines.append(f"| {s['file']} | {s['line']} | `{s['cited_path']}` |")
        lines.append('')

    if 'terms' in results:
        section('Terminology')
        d = results['terms']
        row('Violations', str(d.get('violation_count', 0)))
        if d.get('violations'):
            lines.append('')
            lines.append('| File | Line | Found | Should Be |')
            lines.append('|---|---|---|---|')
            for v in d['violations'][:20]:
                lines.append(f"| {v['file']} | {v['line']} | `{v['found']}` | `{v['should_be']}` |")
        lines.append('')

    if 'prohibited_terms' in results:
        section('Prohibited Terms in Public Docs')
        d = results['prohibited_terms']
        row('Score', f"{d.get('score_pct', 100)}% ({d.get('total', 0)} matches)")
        by_cat = d.get('by_category', {})
        if by_cat:
            row('By category', ', '.join(f"{k}: {v}" for k, v in by_cat.items()))
        if d.get('matches'):
            lines.append('')
            lines.append('| File | Category | Term | Context |')
            lines.append('|---|---|---|---|')
            for m in d['matches'][:20]:
                ctx = m.get('context_line', '').replace('|', '\\|')
                lines.append(f"| {m['file']} | {m['category']} | `{m['term']}` | {ctx} |")
        lines.append('')

    lines.append('---')
    lines.append('')
    lines.append(f'Score: **{score}%** (threshold: {STRICT_THRESHOLD}%)')
    lines.append('')
    return '\n'.join(lines)


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------

SUBCOMMANDS = ['inventory', 'orphans', 'qa-coverage', 'xrefs', 'stale', 'terms', 'prohibited-terms', 'all']


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(
        prog='consistency-auditor.py',
        description='Documentation consistency auditor for the real-time-risk-lab repo.',
    )
    parser.add_argument(
        'subcommand',
        nargs='?',
        default='all',
        choices=SUBCOMMANDS,
        help='Sub-audit to run (default: all)',
    )
    parser.add_argument('--json', action='store_true', help='Emit JSON output')
    parser.add_argument('--report-md', action='store_true', help='Emit Markdown report')
    parser.add_argument('--strict', action='store_true',
                        help=f'Exit 1 if overall score < {STRICT_THRESHOLD}')
    parser.add_argument('--out-dir', default=None,
                        help='Write report files to this directory')
    args = parser.parse_args(argv)

    results: dict[str, Any] = {}
    cmd = args.subcommand

    if cmd in ('inventory', 'all'):
        results['inventory'] = audit_inventory()
    if cmd in ('orphans', 'all'):
        results['orphans'] = audit_orphans()
    if cmd in ('qa-coverage', 'all'):
        results['qa_coverage'] = audit_qa_coverage()
    if cmd in ('xrefs', 'all'):
        results['xrefs'] = audit_xrefs()
    if cmd in ('stale', 'all'):
        results['stale'] = audit_stale()
    if cmd in ('terms', 'all'):
        results['terms'] = audit_terms()
    if cmd in ('prohibited-terms', 'all'):
        results['prohibited_terms'] = audit_prohibited_terms()

    score = _compute_score(results) if cmd == 'all' else 0.0

    # Output
    if args.json:
        output = json.dumps({'results': results, 'score': score}, indent=2, default=str)
        print(output)
    elif args.report_md:
        output = _to_markdown(results, score)
        print(output)
        if args.out_dir:
            out = Path(args.out_dir)
            out.mkdir(parents=True, exist_ok=True)
            (out / 'summary.md').write_text(output, encoding='utf-8')
            # Also write per-sub-audit detail
            for key, val in results.items():
                detail_path = out / f'{key}.json'
                detail_path.write_text(json.dumps(val, indent=2, default=str), encoding='utf-8')
            print(f'\nReport written to: {out}', file=sys.stderr)
    else:
        # Human-readable
        if 'inventory' in results:
            _print_inventory(results['inventory'])
        if 'orphans' in results:
            _print_orphans(results['orphans'])
        if 'qa_coverage' in results:
            _print_qa_coverage(results['qa_coverage'])
        if 'xrefs' in results:
            _print_xrefs(results['xrefs'])
        if 'stale' in results:
            _print_stale(results['stale'])
        if 'terms' in results:
            _print_terms(results['terms'])
        if 'prohibited_terms' in results:
            _print_prohibited_terms(results['prohibited_terms'])
        if cmd == 'all':
            print(f'\n=== OVERALL CONSISTENCY SCORE: {score}% (threshold: {STRICT_THRESHOLD}%) ===\n')

        if args.out_dir:
            out = Path(args.out_dir)
            out.mkdir(parents=True, exist_ok=True)
            # Write summary.txt
            import io
            buf = io.StringIO()
            old_stdout = sys.stdout
            sys.stdout = buf
            if 'inventory' in results:
                _print_inventory(results['inventory'])
            if 'orphans' in results:
                _print_orphans(results['orphans'])
            if 'qa_coverage' in results:
                _print_qa_coverage(results['qa_coverage'])
            if 'xrefs' in results:
                _print_xrefs(results['xrefs'])
            if 'stale' in results:
                _print_stale(results['stale'])
            if 'terms' in results:
                _print_terms(results['terms'])
            if 'prohibited_terms' in results:
                _print_prohibited_terms(results['prohibited_terms'])
            if cmd == 'all':
                print(f'\n=== OVERALL CONSISTENCY SCORE: {score}% (threshold: {STRICT_THRESHOLD}%) ===\n')
            sys.stdout = old_stdout
            (out / 'summary.txt').write_text(buf.getvalue(), encoding='utf-8')
            # Write markdown summary
            md = _to_markdown(results, score)
            (out / 'summary.md').write_text(md, encoding='utf-8')
            # Write per-sub-audit JSON
            for key, val in results.items():
                (out / f'{key}.json').write_text(json.dumps(val, indent=2, default=str), encoding='utf-8')
            print(f'\nReport written to: {out}', file=sys.stderr)

    if args.strict and cmd == 'all' and score < STRICT_THRESHOLD:
        print(f'STRICT: score {score}% < {STRICT_THRESHOLD}% threshold', file=sys.stderr)
        return 1

    return 0


if __name__ == '__main__':
    raise SystemExit(main())
