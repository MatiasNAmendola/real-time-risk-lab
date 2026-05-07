#!/usr/bin/env python3
"""
coverage-audit.py — Framework de coverage audit para ejes no-test:
documentacion, CLI, primitivas y cross-axis.

Usage:
    coverage-audit.py docs              # docs coverage
    coverage-audit.py cli               # CLI coverage
    coverage-audit.py primitives        # primitives coverage
    coverage-audit.py all               # los 3 + cross-axis
    coverage-audit.py --json
    coverage-audit.py --report-md
    coverage-audit.py --strict          # exit 1 si coverage < threshold
    coverage-audit.py --help
"""
from __future__ import annotations

import argparse
import json
import os
import re
import subprocess
import sys
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

SCRIPT_DIR = Path(__file__).resolve().parent
REPO_ROOT = SCRIPT_DIR.parent.parent
VAULT_DIR = REPO_ROOT / "vault"
DOCS_DIR = REPO_ROOT / "docs"
PRIMITIVES_DIR = REPO_ROOT / ".ai" / "primitives"
ADAPTERS_DIR = REPO_ROOT / ".ai" / "adapters"
LOGS_DIR = REPO_ROOT / ".ai" / "logs"
SETTINGS_JSON = REPO_ROOT / ".claude" / "settings.json"

STRICT_THRESHOLD = 70  # percent


# ---------------------------------------------------------------------------
# Mini YAML frontmatter parser (stdlib only)
# ---------------------------------------------------------------------------

def parse_frontmatter(text: str) -> dict[str, Any]:
    """Extract YAML frontmatter from a markdown file. Returns {} if absent.

    Handles:
    - Simple scalars: key: value
    - Inline lists: key: [a, b, c]
    - Block lists (YAML block sequences):
        key:
          - item1
          - item2
    """
    if not text.startswith("---"):
        return {}
    end = text.find("\n---", 3)
    if end == -1:
        return {}
    fm_block = text[3:end].strip()
    result: dict[str, Any] = {}
    lines = fm_block.splitlines()
    i = 0
    while i < len(lines):
        line = lines[i]
        # Skip blank lines and comment lines
        if not line.strip() or line.strip().startswith("#"):
            i += 1
            continue
        # Only process lines that start at column 0 (top-level keys)
        if line and not line[0].isspace() and ":" in line:
            key, _, val = line.partition(":")
            key = key.strip()
            val = val.strip()
            if val.startswith("[") and val.endswith("]"):
                # Inline list
                inner = val[1:-1]
                items = [x.strip().strip('"').strip("'") for x in inner.split(",") if x.strip()]
                result[key] = items
            elif val == "" or val == "|" or val == ">":
                # Possibly block list or block scalar — collect indented lines
                block_items = []
                j = i + 1
                while j < len(lines):
                    nxt = lines[j]
                    if nxt and not nxt[0].isspace():
                        break
                    stripped = nxt.strip()
                    if stripped.startswith("- "):
                        block_items.append(stripped[2:].strip())
                    elif stripped and not stripped.startswith("#"):
                        # Non-list indented content — treat as scalar continuation
                        if not block_items:
                            pass  # skip
                    j += 1
                result[key] = block_items if block_items else val
                i = j
                continue
            else:
                # Simple scalar
                val = val.strip('"').strip("'")
                result[key] = val
        i += 1
    return result


def has_command(cmd: str) -> bool:
    try:
        subprocess.run(["command", "-v", cmd], shell=False, capture_output=True,
                       executable="/bin/sh", timeout=2)
        result = subprocess.run(["which", cmd], capture_output=True, timeout=2)
        return result.returncode == 0
    except Exception:
        return False


def run_external(args: list[str], timeout: int = 60) -> tuple[int, str, str]:
    try:
        proc = subprocess.run(args, capture_output=True, text=True, timeout=timeout)
        return proc.returncode, proc.stdout, proc.stderr
    except subprocess.TimeoutExpired:
        return 1, "", "timeout"
    except Exception as e:
        return 1, "", str(e)


# ---------------------------------------------------------------------------
# Docs audit
# ---------------------------------------------------------------------------

def audit_docs() -> dict[str, Any]:
    results: dict[str, Any] = {}

    # 1. Wikilink integrity
    all_md_files = list(VAULT_DIR.rglob("*.md")) if VAULT_DIR.exists() else []
    all_md_files += list(DOCS_DIR.rglob("*.md")) if DOCS_DIR.exists() else []

    # Build stem index for resolution
    vault_stems: set[str] = set()
    if VAULT_DIR.exists():
        for f in VAULT_DIR.rglob("*.md"):
            vault_stems.add(f.stem.lower())
            vault_stems.add(f.name.lower())

    total_links = 0
    broken_links: list[str] = []
    wikilink_pattern = re.compile(r"\[\[([^\]|#]+)(?:[|#][^\]]*)?\]\]")

    for md_file in (VAULT_DIR.rglob("*.md") if VAULT_DIR.exists() else []):
        try:
            content = md_file.read_text(encoding="utf-8", errors="replace")
        except Exception:
            continue
        for match in wikilink_pattern.finditer(content):
            raw = match.group(1).strip()
            total_links += 1
            target_stem = Path(raw).stem.lower()
            target_name = raw.lower() if raw.lower().endswith(".md") else raw.lower() + ".md"
            if target_stem not in vault_stems and target_name not in vault_stems:
                try:
                    rel = md_file.relative_to(REPO_ROOT)
                except ValueError:
                    rel = md_file
                broken_links.append(f"{rel}:[[{raw}]]")

    wikilink_pct = 100 if total_links == 0 else round(100 * (total_links - len(broken_links)) / total_links)
    results["wikilink_integrity"] = {
        "total": total_links,
        "broken": len(broken_links),
        "broken_list": broken_links[:10],
        "pct": wikilink_pct,
    }

    # 2. README presence
    relevant_dirs: list[Path] = []
    for pattern in ["poc/*", "pkg/*", "sdks/*", "cli/*", "tests/*", "bench/*"]:
        base, _, sub = pattern.partition("/")
        base_path = REPO_ROOT / base
        if not base_path.exists():
            continue
        if sub == "*":
            for d in base_path.iterdir():
                if d.is_dir():
                    relevant_dirs.append(d)
        else:
            p = base_path / sub
            if p.is_dir():
                relevant_dirs.append(p)

    for special in ["dashboard", ".ai"]:
        p = REPO_ROOT / special
        if p.is_dir():
            relevant_dirs.append(p)

    readme_present = [d for d in relevant_dirs if (d / "README.md").exists()]
    readme_missing = [str(d.relative_to(REPO_ROOT)) for d in relevant_dirs if not (d / "README.md").exists()]
    readme_pct = 100 if not relevant_dirs else round(100 * len(readme_present) / len(relevant_dirs))
    results["readme_presence"] = {
        "total": len(relevant_dirs),
        "present": len(readme_present),
        "missing": readme_missing,
        "pct": readme_pct,
    }

    # 3. ADR completeness
    adr_dir = VAULT_DIR / "02-Decisions" if VAULT_DIR.exists() else REPO_ROOT / "vault" / "02-Decisions"
    adr_files = list(adr_dir.glob("*.md")) if adr_dir.exists() else []
    required_sections = ["context", "decision", "alternatives", "consequences"]
    adr_complete = []
    adr_partial: list[str] = []
    for adr in adr_files:
        try:
            content = adr.read_text(encoding="utf-8", errors="replace").lower()
        except Exception:
            adr_partial.append(adr.name)
            continue
        found = sum(1 for s in required_sections if s in content)
        if found >= len(required_sections):
            adr_complete.append(adr.name)
        else:
            adr_partial.append(adr.name)
    adr_pct = 100 if not adr_files else round(100 * len(adr_complete) / len(adr_files))
    results["adr_completeness"] = {
        "total": len(adr_files),
        "complete": len(adr_complete),
        "partial": adr_partial,
        "pct": adr_pct,
    }

    # 4. Doc-to-code link (source: in frontmatter or Related: section)
    doc_files = list(DOCS_DIR.glob("*.md")) if DOCS_DIR.exists() else []
    doc_linked = 0
    doc_orphan: list[str] = []
    for doc in doc_files:
        try:
            content = doc.read_text(encoding="utf-8", errors="replace")
        except Exception:
            doc_orphan.append(doc.name)
            continue
        fm = parse_frontmatter(content)
        has_source = "source" in fm and fm["source"]
        # Also check for Related: section or explicit path references
        has_related = bool(re.search(r"(?i)related[:\s]", content) and re.search(r"(?:poc|pkg|sdks|cli|tests|bench|dashboard|\.ai)/", content))
        if has_source:
            # Verify path exists
            source_path = REPO_ROOT / fm["source"]
            if source_path.exists():
                doc_linked += 1
            else:
                doc_orphan.append(doc.name)
        elif has_related:
            doc_linked += 1
        else:
            doc_orphan.append(doc.name)
    doc_pct = 100 if not doc_files else round(100 * doc_linked / len(doc_files))
    results["doc_to_code_links"] = {
        "total": len(doc_files),
        "linked": doc_linked,
        "orphan": doc_orphan,
        "pct": doc_pct,
    }

    # 5. Frontmatter validity (vault/*.md must have title + tags)
    vault_files = list(VAULT_DIR.rglob("*.md")) if VAULT_DIR.exists() else []
    fm_valid = 0
    fm_invalid: list[str] = []
    for vf in vault_files:
        try:
            content = vf.read_text(encoding="utf-8", errors="replace")
        except Exception:
            fm_invalid.append(str(vf.relative_to(REPO_ROOT)))
            continue
        fm = parse_frontmatter(content)
        if fm.get("title") and fm.get("tags") is not None:
            fm_valid += 1
        else:
            fm_invalid.append(str(vf.relative_to(REPO_ROOT)))
    fm_pct = 100 if not vault_files else round(100 * fm_valid / len(vault_files))
    results["frontmatter_validity"] = {
        "total": len(vault_files),
        "valid": fm_valid,
        "invalid_count": len(fm_invalid),
        "pct": fm_pct,
    }

    # External tools
    external: dict[str, Any] = {}
    if has_command("lychee"):
        paths_to_check = [str(DOCS_DIR)] if DOCS_DIR.exists() else []
        if paths_to_check:
            rc, out, err = run_external(["lychee", *paths_to_check, "--no-progress", "--format", "json"])
            try:
                external["lychee"] = {"available": True, "data": json.loads(out)}
            except Exception:
                external["lychee"] = {"available": True, "raw": out[:200]}
        else:
            external["lychee"] = {"available": True, "note": "no docs to check"}
    else:
        external["lychee"] = {"available": False, "install": "brew install lychee"}

    if has_command("markdownlint"):
        paths = [str(DOCS_DIR)] if DOCS_DIR.exists() else []
        if paths:
            rc, out, err = run_external(["markdownlint", *paths])
            external["markdownlint"] = {"available": True, "exit_code": rc, "issues": out.count("\n")}
        else:
            external["markdownlint"] = {"available": True, "note": "no docs"}
    else:
        external["markdownlint"] = {"available": False, "install": "brew install markdownlint-cli"}

    if has_command("vale"):
        external["vale"] = {"available": True, "note": "vale present but not run (configure .vale.ini first)"}
    else:
        external["vale"] = {"available": False, "install": "brew install vale"}

    results["external_tools"] = external

    # 6. Aggregated JaCoCo coverage present
    aggregate_report = REPO_ROOT / "out" / "coverage" / "latest" / "index.html"
    aggregate_present = aggregate_report.exists()
    results["aggregated_coverage_present"] = {
        "present": aggregate_present,
        "path": str(aggregate_report.relative_to(REPO_ROOT)),
        "pct": 100 if aggregate_present else 0,
        "note": "Run './nx test --coverage' or './gradlew jacocoAggregateReport' to generate" if not aggregate_present else None,
    }

    # Consistency sub-score (from consistency-auditor.py)
    consistency_pct = _run_consistency_audit()
    results["consistency_audit"] = {
        "pct": consistency_pct,
        "note": "Run 'python3 .ai/scripts/consistency-auditor.py all' for full detail",
    }

    # Confidentiality scan sub-score
    confidentiality_result = _run_confidentiality_scan()
    results["confidentiality_clean"] = confidentiality_result

    # Overall
    pcts = [
        results["wikilink_integrity"]["pct"],
        results["readme_presence"]["pct"],
        results["adr_completeness"]["pct"],
        results["doc_to_code_links"]["pct"],
        results["frontmatter_validity"]["pct"],
        results["aggregated_coverage_present"]["pct"],
        consistency_pct,
    ]
    # Only include confidentiality_clean in overall when blocklist is present.
    if confidentiality_result.get("status") != "skipped":
        pcts.append(confidentiality_result["pct"])
    results["overall_pct"] = round(sum(pcts) / len(pcts))
    return results


def _run_consistency_audit() -> int:
    """Run consistency-auditor.py all and return overall score (0-100)."""
    auditor = SCRIPT_DIR / "consistency-auditor.py"
    if not auditor.exists():
        return 100  # auditor not present; don't penalise
    try:
        import importlib.util as _ilu
        spec = _ilu.spec_from_file_location("_consistency_auditor", auditor)
        if spec is None or spec.loader is None:
            return 100
        mod = _ilu.module_from_spec(spec)
        spec.loader.exec_module(mod)  # type: ignore[union-attr]
        # Redirect stdout to suppress human output
        import io
        old_stdout = sys.stdout
        sys.stdout = io.StringIO()
        try:
            results: dict = {}
            results["orphans"] = mod.audit_orphans()
            results["qa_coverage"] = mod.audit_qa_coverage()
            results["xrefs"] = mod.audit_xrefs()
            results["stale"] = mod.audit_stale()
            results["terms"] = mod.audit_terms()
            score = mod._compute_score(results)
        finally:
            sys.stdout = old_stdout
        return int(score)
    except Exception:
        return 100


def _run_confidentiality_scan() -> dict[str, Any]:
    """
    Run the confidentiality scanner and return a factor dict.

    Returns:
        status "skipped"  -> blocklist absent; pct not included in overall.
        status "clean"    -> exit 0; pct 100.
        status "dirty"    -> exit 1; pct 0. Match count reported, no plaintext.
    """
    scanner = SCRIPT_DIR / "confidentiality-scanner.py"
    blocklist = REPO_ROOT / ".ai" / "blocklist.sha256"

    if not scanner.exists():
        return {"status": "skipped", "pct": 100, "note": "scanner not found"}
    if not blocklist.exists():
        return {
            "status": "skipped",
            "pct": 100,
            "note": "blocklist not found — run 'cp .ai/blocklist.sha256.example .ai/blocklist.sha256' to bootstrap",
        }

    rc, stdout, _stderr = run_external(
        [sys.executable, str(scanner), "--blocklist", str(blocklist), str(REPO_ROOT)],
        timeout=60,
    )
    if rc == 0:
        return {"status": "clean", "pct": 100, "note": "no prohibited terms detected"}

    # Count matches but do NOT surface plaintext.
    match_count = 0
    for line in stdout.splitlines():
        stripped = line.strip()
        if "prohibited term(s) detected" in stripped:
            try:
                match_count += int(stripped.split(":")[1].split("prohibited")[0].strip())
            except Exception:
                match_count += 1

    return {
        "status": "dirty",
        "pct": 0,
        "match_count": match_count,
        "note": (
            f"{match_count} prohibited term match(es) detected. "
            "Run './nx audit confidentiality' to review (hash prefixes only, no plaintext shown)."
        ),
    }


# ---------------------------------------------------------------------------
# CLI audit
# ---------------------------------------------------------------------------

def audit_cli() -> dict[str, Any]:
    results: dict[str, Any] = {}

    # 1. Script inventory
    sh_scripts: list[Path] = []
    for pattern in ["**/*.sh"]:
        for f in REPO_ROOT.rglob("*.sh"):
            # Skip node_modules, .git, build dirs
            parts = f.parts
            if any(p in parts for p in [".git", "node_modules", "build", "gradle", ".gradle"]):
                continue
            sh_scripts.append(f)

    results["scripts_found"] = {
        "total": len(sh_scripts),
        "list": [str(f.relative_to(REPO_ROOT)) for f in sh_scripts[:50]],
    }

    # 2. --help support
    help_ok: list[str] = []
    help_fail: list[str] = []
    for script in sh_scripts:
        try:
            proc = subprocess.run(
                ["bash", str(script), "--help"],
                capture_output=True, text=True, timeout=2
            )
            if proc.returncode == 0 or "usage" in (proc.stdout + proc.stderr).lower() or "help" in (proc.stdout + proc.stderr).lower():
                help_ok.append(str(script.relative_to(REPO_ROOT)))
            else:
                help_fail.append(str(script.relative_to(REPO_ROOT)))
        except subprocess.TimeoutExpired:
            help_fail.append(str(script.relative_to(REPO_ROOT)))
        except Exception:
            help_fail.append(str(script.relative_to(REPO_ROOT)))

    help_pct = 100 if not sh_scripts else round(100 * len(help_ok) / len(sh_scripts))
    results["help_support"] = {
        "total": len(sh_scripts),
        "supported": len(help_ok),
        "failed": help_fail[:10],
        "pct": help_pct,
    }

    # 3. Structured output (scripts that write to out/)
    structured: list[str] = []
    unstructured: list[str] = []
    output_pattern = re.compile(r"out/|OUT_DIR|OUT_BASE|init_output|mkdir.*out")
    for script in sh_scripts:
        try:
            content = script.read_text(encoding="utf-8", errors="replace")
        except Exception:
            unstructured.append(str(script.relative_to(REPO_ROOT)))
            continue
        if output_pattern.search(content):
            structured.append(str(script.relative_to(REPO_ROOT)))
        else:
            unstructured.append(str(script.relative_to(REPO_ROOT)))
    struct_pct = 100 if not sh_scripts else round(100 * len(structured) / len(sh_scripts))
    results["structured_output"] = {
        "total": len(sh_scripts),
        "structured": len(structured),
        "pct": struct_pct,
    }

    # 4. Master CLI presence
    master_cli = REPO_ROOT / "nx"
    master_cli_exists = master_cli.exists() and os.access(master_cli, os.X_OK)
    results["master_cli"] = {
        "exists": master_cli_exists,
        "path": "./nx",
        "recommendation": None if master_cli_exists else "implement ./nx as unified entry point",
        "pct": 100 if master_cli_exists else 0,
    }

    # 5. Cross-platform awareness check
    # Scans for non-portable patterns in shell scripts.
    # Patterns considered non-portable:
    #   sed -i ''       (BSD/Mac only — requires -i.bak or command -v check)
    #   top -l 1        (Mac only for single-shot CPU; Linux top has no -l flag)
    #   stat -f%z       (BSD/Mac stat file size flag)
    #   date -j -f      (BSD/Mac date parsing flag)
    #   brew --prefix (without command -v brew guard)
    NON_PORTABLE_PATTERNS = [
        (re.compile(r"sed -i ''"), "sed -i '' (BSD only)"),
        (re.compile(r"top -l 1"), "top -l 1 (Mac only)"),
        (re.compile(r"stat -f%z|stat -f %z"), "stat -f%z (BSD only)"),
        (re.compile(r"date -j -f|date -j\b"), "date -j (BSD only)"),
    ]
    # brew --prefix without a preceding command -v brew guard (within 3 lines)
    BREW_BARE = re.compile(r"brew --prefix")
    BREW_GUARD = re.compile(r"command -v brew")

    portable_scripts: list[str] = []
    non_portable_scripts: list[dict[str, Any]] = []

    # Exclude gradlew, wrappers, generated dirs
    EXCLUDE_NAMES = {"gradlew", "gradlew.bat"}
    EXCLUDE_DIRS = {".git", "node_modules", "build", "target", "out", ".gradle", "gradle"}

    for script in sh_scripts:
        if script.name in EXCLUDE_NAMES:
            continue
        if any(p in script.parts for p in EXCLUDE_DIRS):
            continue
        try:
            content = script.read_text(encoding="utf-8", errors="replace")
        except Exception:
            continue

        issues: list[str] = []
        for pattern, label in NON_PORTABLE_PATTERNS:
            if pattern.search(content):
                issues.append(label)

        # Check brew --prefix without guard: look for brew --prefix occurrences
        # where the surrounding 3 lines don't have "command -v brew"
        if BREW_BARE.search(content):
            lines_list = content.splitlines()
            for idx, line in enumerate(lines_list):
                if BREW_BARE.search(line):
                    # Check window of 3 lines before this line
                    window_start = max(0, idx - 3)
                    window = "\n".join(lines_list[window_start:idx + 1])
                    if not BREW_GUARD.search(window):
                        issues.append("brew --prefix without command -v brew guard")
                        break

        rel = str(script.relative_to(REPO_ROOT))
        if issues:
            non_portable_scripts.append({"script": rel, "issues": issues})
        else:
            portable_scripts.append(rel)

    total_checked = len(portable_scripts) + len(non_portable_scripts)
    xplat_pct = 100 if total_checked == 0 else round(
        100 * len(portable_scripts) / total_checked
    )

    results["cross_platform_aware"] = {
        "total_checked": total_checked,
        "portable": len(portable_scripts),
        "non_portable": len(non_portable_scripts),
        "issues": non_portable_scripts[:20],
        "pct": xplat_pct,
    }

    # Overall including master CLI presence and cross-platform check
    master_cli_pct = 100 if master_cli_exists else 0
    pcts = [help_pct, struct_pct, master_cli_pct, xplat_pct]
    results["overall_pct"] = round(sum(pcts) / len(pcts))
    return results


# ---------------------------------------------------------------------------
# Primitives audit
# ---------------------------------------------------------------------------

def audit_primitives() -> dict[str, Any]:
    results: dict[str, Any] = {}

    skills_dir = PRIMITIVES_DIR / "skills"
    rules_dir = PRIMITIVES_DIR / "rules"
    workflows_dir = PRIMITIVES_DIR / "workflows"
    hooks_dir = PRIMITIVES_DIR / "hooks"

    # 1. Skill frontmatter validity
    required_skill_fields = ["name", "intent", "inputs", "preconditions", "postconditions", "related_rules", "tags"]
    skill_files = list(skills_dir.glob("*.md")) if skills_dir.exists() else []
    skill_valid = []
    skill_invalid: list[str] = []
    skill_missing_fields: dict[str, list[str]] = {}

    for sf in skill_files:
        try:
            content = sf.read_text(encoding="utf-8", errors="replace")
        except Exception:
            skill_invalid.append(sf.name)
            continue
        fm = parse_frontmatter(content)
        missing = [f for f in required_skill_fields if f not in fm]
        if not missing:
            skill_valid.append(sf.name)
        else:
            skill_invalid.append(sf.name)
            skill_missing_fields[sf.name] = missing

    skill_fm_pct = 100 if not skill_files else round(100 * len(skill_valid) / len(skill_files))
    results["skill_frontmatter"] = {
        "total": len(skill_files),
        "valid": len(skill_valid),
        "invalid": skill_invalid,
        "missing_fields": skill_missing_fields,
        "pct": skill_fm_pct,
    }

    # 2. Workflow → skill linkage
    workflow_files = list(workflows_dir.glob("*.md")) if workflows_dir.exists() else []
    existing_skill_names = {sf.stem.lower() for sf in skill_files}
    # Also build name-based index from skill frontmatter
    for sf in skill_files:
        try:
            content = sf.read_text(encoding="utf-8", errors="replace")
            fm = parse_frontmatter(content)
            if fm.get("name"):
                existing_skill_names.add(fm["name"].lower())
        except Exception:
            pass

    workflows_ok = []
    workflows_broken: list[str] = []
    broken_refs: dict[str, list[str]] = {}

    for wf in workflow_files:
        try:
            content = wf.read_text(encoding="utf-8", errors="replace").lower()
        except Exception:
            workflows_broken.append(wf.name)
            continue
        # Find explicit skill references only from backtick-quoted names and step lists
        # Backtick pattern: `skill-name` — only accept if it looks like an action verb (add-, run-, etc.)
        action_prefixes = ("add-", "run-", "create-", "update-", "verify-", "check-",
                           "generate-", "deploy-", "build-", "setup-", "configure-",
                           "register-", "install-")
        candidate_refs = re.findall(r"`([a-z][a-z0-9-]+)`", content)
        # Also check steps: frontmatter-style list items
        fm = parse_frontmatter(wf.read_text(encoding="utf-8", errors="replace"))
        steps = fm.get("steps", [])
        if isinstance(steps, list):
            candidate_refs += [s.lower() for s in steps]

        # Only flag refs that look like skill names (start with action prefix)
        # and don't exist in the skills directory
        bad_refs = []
        for ref in candidate_refs:
            if not any(ref.startswith(p) for p in action_prefixes):
                continue  # not a skill-shaped name, skip
            if ref not in existing_skill_names:
                bad_refs.append(ref)

        if bad_refs:
            workflows_broken.append(wf.name)
            broken_refs[wf.name] = bad_refs[:5]
        else:
            workflows_ok.append(wf.name)

    workflow_pct = 100 if not workflow_files else round(100 * len(workflows_ok) / len(workflow_files))
    results["workflow_skill_linkage"] = {
        "total": len(workflow_files),
        "ok": len(workflows_ok),
        "broken": workflows_broken,
        "broken_refs": broken_refs,
        "pct": workflow_pct,
    }

    # 3. Rule → test enforcement
    rule_files = list(rules_dir.glob("*.md")) if rules_dir.exists() else []
    # ArchUnit tests in pkg/ and tests/ directories
    arch_test_content = ""
    for ext in ["*.java", "*.go", "*.py"]:
        for tf in REPO_ROOT.rglob(ext):
            if "test" in str(tf).lower() or "arch" in str(tf).lower():
                try:
                    arch_test_content += tf.read_text(encoding="utf-8", errors="replace").lower() + "\n"
                except Exception:
                    pass

    rules_enforced = []
    rules_unenforced: list[str] = []
    for rf in rule_files:
        rule_name = rf.stem.lower()
        # Check if rule name or key words appear in test files
        keywords = [word for word in rule_name.replace("-", " ").split() if len(word) > 3]
        found = any(kw in arch_test_content for kw in keywords) if keywords else False
        if found:
            rules_enforced.append(rf.name)
        else:
            rules_unenforced.append(rf.name)

    rules_pct = 100 if not rule_files else round(100 * len(rules_enforced) / len(rule_files))
    results["rule_enforcement"] = {
        "total": len(rule_files),
        "enforced": len(rules_enforced),
        "unenforced": rules_unenforced,
        "pct": rules_pct,
    }

    # 4. Hook → event wiring
    hook_files = list(hooks_dir.glob("*.md")) if hooks_dir.exists() else []
    wired_events: set[str] = set()
    if SETTINGS_JSON.exists():
        try:
            settings = json.loads(SETTINGS_JSON.read_text())
            hooks_cfg = settings.get("hooks", {})
            wired_events = set(hooks_cfg.keys())
        except Exception:
            pass

    hooks_wired = []
    hooks_unwired: list[str] = []
    for hf in hook_files:
        try:
            content = hf.read_text(encoding="utf-8", errors="replace")
        except Exception:
            hooks_unwired.append(hf.name)
            continue
        fm = parse_frontmatter(content)
        trigger = fm.get("trigger", "")
        if trigger and trigger in wired_events:
            hooks_wired.append(hf.name)
        elif not trigger:
            # Check in content
            content_lower = content.lower()
            found_any = any(ev.lower() in content_lower for ev in wired_events)
            if found_any:
                hooks_wired.append(hf.name)
            else:
                hooks_unwired.append(hf.name)
        else:
            hooks_unwired.append(hf.name)

    hooks_pct = 100 if not hook_files else round(100 * len(hooks_wired) / len(hook_files))
    results["hook_wiring"] = {
        "total": len(hook_files),
        "wired": len(hooks_wired),
        "unwired": hooks_unwired,
        "wired_events": sorted(wired_events),
        "pct": hooks_pct,
    }

    # 5. Adapter completeness
    adapter_dirs = [d for d in ADAPTERS_DIR.iterdir() if d.is_dir()] if ADAPTERS_DIR.exists() else []
    adapters_complete = []
    adapters_incomplete: list[str] = []
    for ad in adapter_dirs:
        has_readme = (ad / "README.md").exists()
        has_install = (ad / "install.sh").exists()
        # install.sh executable check
        install_sh = ad / "install.sh"
        is_executable = install_sh.exists() and os.access(install_sh, os.X_OK)
        # Complete = has README + (has install.sh OR not expected)
        if has_readme and (has_install or not any((ad / f).exists() for f in ["install.sh"])):
            adapters_complete.append(ad.name)
        elif has_readme and has_install:
            adapters_complete.append(ad.name)
        else:
            adapters_incomplete.append(ad.name)

    adapters_pct = 100 if not adapter_dirs else round(100 * len(adapters_complete) / len(adapter_dirs))
    results["adapter_completeness"] = {
        "total": len(adapter_dirs),
        "complete": len(adapters_complete),
        "incomplete": adapters_incomplete,
        "pct": adapters_pct,
    }

    # 6. Skill router activation rate (from logs)
    routing_logs = list(LOGS_DIR.glob("skill-routing-*.jsonl")) if LOGS_DIR.exists() else []
    total_events = 0
    activated_events = 0
    SCORE_THRESHOLD = 0.5
    for log_file in routing_logs:
        try:
            for line in log_file.read_text(encoding="utf-8", errors="replace").splitlines():
                if not line.strip():
                    continue
                try:
                    entry = json.loads(line)
                    total_events += 1
                    score = entry.get("score", entry.get("top_score", 0))
                    if isinstance(score, (int, float)) and score >= SCORE_THRESHOLD:
                        activated_events += 1
                except Exception:
                    pass
        except Exception:
            pass

    activation_pct = 0 if total_events == 0 else round(100 * activated_events / total_events)
    results["skill_router_activation"] = {
        "total_events": total_events,
        "activated": activated_events,
        "pct": activation_pct,
        "note": "primer dia, pocos eventos" if total_events == 0 else None,
    }

    # Overall (skill_router excluded from main score if 0 events)
    pcts = [skill_fm_pct, workflow_pct, rules_pct, hooks_pct, adapters_pct]
    if total_events > 0:
        pcts.append(activation_pct)
    results["overall_pct"] = round(sum(pcts) / len(pcts))
    return results


# ---------------------------------------------------------------------------
# Cross-axis matrix
# ---------------------------------------------------------------------------

def audit_cross_axis(docs_results: dict, cli_results: dict, prim_results: dict) -> dict[str, Any]:
    """Build a cross-axis matrix: area x (test, doc, cli, primitive)."""

    # Define repo areas
    areas: list[str] = []
    for pattern in ["poc/*", "pkg/*", "sdks/*", "cli/*", "tests/*", "bench/*", "dashboard"]:
        base, _, sub = pattern.partition("/")
        base_path = REPO_ROOT / base
        if not base_path.exists():
            continue
        if sub == "*":
            for d in sorted(base_path.iterdir()):
                if d.is_dir():
                    areas.append(str(d.relative_to(REPO_ROOT)))
        else:
            areas.append(base)

    areas.append(".ai")

    matrix: list[dict[str, Any]] = []

    for area in areas:
        area_path = REPO_ROOT / area
        if not area_path.exists():
            continue

        row: dict[str, Any] = {"area": area}

        # Test coverage: check for test files
        test_files = list(area_path.rglob("*Test*.java")) + list(area_path.rglob("*_test.go")) + \
                     list(area_path.rglob("test_*.py")) + list(area_path.rglob("*.feature"))
        src_files = list(area_path.rglob("*.java")) + list(area_path.rglob("*.go")) + \
                    list(area_path.rglob("*.py")) + list(area_path.rglob("*.ts"))
        if not src_files and not test_files:
            row["tests"] = "n/a"
        elif not src_files:
            row["tests"] = "100%"
        else:
            ratio = len(test_files) / max(len(src_files), 1)
            row["tests"] = f"{min(100, round(ratio * 100))}%"

        # Doc coverage: README present + any doc reference
        has_readme = (area_path / "README.md").exists()
        doc_refs = 0
        if DOCS_DIR.exists():
            area_stem = Path(area).stem.lower()
            for df in DOCS_DIR.glob("*.md"):
                try:
                    if area_stem in df.read_text(encoding="utf-8", errors="replace").lower():
                        doc_refs += 1
                except Exception:
                    pass
        doc_pct = (50 if has_readme else 0) + (50 if doc_refs > 0 else 0)
        row["docs"] = f"{doc_pct}%"

        # CLI coverage: scripts that reference this area
        cli_scripts = 0
        for sf in REPO_ROOT.rglob("*.sh"):
            if any(p in sf.parts for p in [".git", "node_modules", "build"]):
                continue
            try:
                if area.split("/")[-1] in sf.read_text(encoding="utf-8", errors="replace"):
                    cli_scripts += 1
            except Exception:
                pass
        if area == ".ai":
            row["cli"] = f"{cli_results.get('overall_pct', 0)}%"
        else:
            row["cli"] = "100%" if cli_scripts >= 1 else ("n/a" if area in ["sdks", "pkg"] else "0%")

        # Primitive coverage: skills/workflows referencing this area
        prim_refs = 0
        area_stem2 = area.replace("/", "-").replace("poc-", "").replace("pkg-", "").lower()
        for sf in (PRIMITIVES_DIR / "skills").glob("*.md") if (PRIMITIVES_DIR / "skills").exists() else []:
            try:
                if area_stem2 in sf.read_text(encoding="utf-8", errors="replace").lower():
                    prim_refs += 1
            except Exception:
                pass
        row["primitives"] = "n/a" if area in ["sdks", "cli"] else (f"{min(100, prim_refs * 20)}%" if prim_refs > 0 else "0%")

        # Overall
        numeric_vals = []
        for key in ["tests", "docs", "cli", "primitives"]:
            v = row.get(key, "n/a")
            if v != "n/a" and v.endswith("%"):
                try:
                    numeric_vals.append(int(v.rstrip("%")))
                except Exception:
                    pass
        row["overall"] = f"{round(sum(numeric_vals) / len(numeric_vals))}%" if numeric_vals else "n/a"

        matrix.append(row)

    # Project overall
    all_overalls = [
        int(r["overall"].rstrip("%")) for r in matrix if r["overall"] != "n/a"
    ]
    project_overall = round(sum(all_overalls) / len(all_overalls)) if all_overalls else 0

    return {"matrix": matrix, "project_overall_pct": project_overall}


# ---------------------------------------------------------------------------
# Output formatters
# ---------------------------------------------------------------------------

def _pct_indicator(pct: int) -> str:
    if pct >= 90:
        return "OK"
    elif pct >= 70:
        return "WARN"
    else:
        return "FAIL"


def format_docs_table(r: dict) -> str:
    lines = ["docs coverage:"]
    wi = r["wikilink_integrity"]
    lines.append(f"  wikilink integrity:    {wi['total']} links, {wi['broken']} broken"
                 f"  [{_pct_indicator(wi['pct'])} {wi['pct']}%]")
    rp = r["readme_presence"]
    missing_str = f" (missing: {', '.join(rp['missing'][:3])})" if rp["missing"] else ""
    lines.append(f"  README presence:       {rp['present']}/{rp['total']} dirs{missing_str}"
                 f"  [{_pct_indicator(rp['pct'])} {rp['pct']}%]")
    ac = r["adr_completeness"]
    lines.append(f"  ADR completeness:      {ac['complete']}/{ac['total']} with all sections"
                 f"  [{_pct_indicator(ac['pct'])} {ac['pct']}%]")
    dl = r["doc_to_code_links"]
    lines.append(f"  doc-to-code links:     {dl['linked']}/{dl['total']} with valid source link"
                 f"  [{_pct_indicator(dl['pct'])} {dl['pct']}%]")
    fv = r["frontmatter_validity"]
    lines.append(f"  frontmatter valid:     {fv['valid']}/{fv['total']} vault notes"
                 f"  [{_pct_indicator(fv['pct'])} {fv['pct']}%]")
    acp = r.get("aggregated_coverage_present", {})
    if acp:
        status = "yes" if acp.get("present") else "no"
        note = f" -- {acp['note']}" if acp.get("note") else ""
        lines.append(f"  aggregated coverage:   {status}{note}"
                     f"  [{_pct_indicator(acp['pct'])} {acp['pct']}%]")
    lines.append(f"overall: {r['overall_pct']}%")
    # External tools
    ext = r.get("external_tools", {})
    for tool, info in ext.items():
        if info.get("available"):
            lines.append(f"  {tool}: available")
        else:
            lines.append(f"  {tool}: not installed — {info.get('install', '')}")
    return "\n".join(lines)


def format_cli_table(r: dict) -> str:
    lines = ["cli coverage:"]
    lines.append(f"  scripts found:         {r['scripts_found']['total']}")
    hs = r["help_support"]
    lines.append(f"  --help support:        {hs['supported']}/{hs['total']}"
                 f"  [{_pct_indicator(hs['pct'])} {hs['pct']}%]")
    so = r["structured_output"]
    lines.append(f"  structured output:     {so['structured']}/{so['total']}"
                 f"  [{_pct_indicator(so['pct'])} {so['pct']}%]")
    mc = r["master_cli"]
    status = "yes" if mc["exists"] else "no"
    rec = f" -- {mc['recommendation']}" if mc.get("recommendation") else ""
    lines.append(f"  master CLI exists:     {status}{rec}")
    xp = r.get("cross_platform_aware", {})
    if xp:
        lines.append(
            f"  cross-platform aware:  {xp['portable']}/{xp['total_checked']}"
            f"  [{_pct_indicator(xp['pct'])} {xp['pct']}%]"
        )
        if xp.get("issues"):
            for item in xp["issues"][:5]:
                issues_str = ", ".join(item["issues"])
                lines.append(f"    {item['script']}: {issues_str}")
    lines.append(f"overall: {r['overall_pct']}%")
    return "\n".join(lines)


def format_primitives_table(r: dict) -> str:
    lines = ["primitives coverage:"]
    sf = r["skill_frontmatter"]
    lines.append(f"  skills with valid frontmatter:        {sf['valid']}/{sf['total']}"
                 f"  [{_pct_indicator(sf['pct'])} {sf['pct']}%]")
    wl = r["workflow_skill_linkage"]
    lines.append(f"  workflows referencing valid skills:   {wl['ok']}/{wl['total']}"
                 f"  [{_pct_indicator(wl['pct'])} {wl['pct']}%]")
    re_ = r["rule_enforcement"]
    lines.append(f"  rules with enforced test:             {re_['enforced']}/{re_['total']}"
                 f"  [{_pct_indicator(re_['pct'])} {re_['pct']}%]")
    hw = r["hook_wiring"]
    lines.append(f"  hooks wired in settings.json:         {hw['wired']}/{hw['total']}"
                 f"  [{_pct_indicator(hw['pct'])} {hw['pct']}%]")
    ac = r["adapter_completeness"]
    lines.append(f"  adapters complete:                    {ac['complete']}/{ac['total']}"
                 f"  [{_pct_indicator(ac['pct'])} {ac['pct']}%]")
    sra = r["skill_router_activation"]
    note = f" — {sra['note']}" if sra.get("note") else ""
    lines.append(f"  skill router activation rate:         {sra['pct']}%{note}")
    lines.append(f"overall: {r['overall_pct']}%")
    return "\n".join(lines)


def format_cross_axis_table(r: dict) -> str:
    matrix = r["matrix"]
    if not matrix:
        return "cross-axis: no areas found"

    header = f"{'Area':<32} {'Tests':<8} {'Docs':<8} {'CLI':<8} {'Primitives':<12} {'Overall'}"
    sep = "-" * 80
    lines = ["Cross-axis coverage matrix:", "", header, sep]
    for row in matrix:
        lines.append(
            f"{row['area']:<32} {row.get('tests','n/a'):<8} {row.get('docs','n/a'):<8} "
            f"{row.get('cli','n/a'):<8} {row.get('primitives','n/a'):<12} {row.get('overall','n/a')}"
        )
    lines.append(sep)
    lines.append(f"Overall project coverage: {r['project_overall_pct']}%")
    return "\n".join(lines)


def format_report_md(all_results: dict) -> str:
    ts = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
    lines = [f"# Coverage Audit Report\n\nGenerated: {ts}\n"]

    if "docs" in all_results:
        r = all_results["docs"]
        lines.append("## Docs Coverage\n")
        lines.append(f"**Overall: {r['overall_pct']}%**\n")
        lines.append("| Check | Score | Status |")
        lines.append("|-------|-------|--------|")
        for key, label in [
            ("wikilink_integrity", "Wikilink integrity"),
            ("readme_presence", "README presence"),
            ("adr_completeness", "ADR completeness"),
            ("doc_to_code_links", "Doc-to-code links"),
            ("frontmatter_validity", "Frontmatter validity"),
            ("aggregated_coverage_present", "Aggregated JaCoCo coverage"),
        ]:
            d = r.get(key, {})
            if not d:
                continue
            lines.append(f"| {label} | {d['pct']}% | {_pct_indicator(d['pct'])} |")
        lines.append("")

    if "cli" in all_results:
        r = all_results["cli"]
        lines.append("## CLI Coverage\n")
        lines.append(f"**Overall: {r['overall_pct']}%**\n")
        hs = r["help_support"]
        so = r["structured_output"]
        mc = r["master_cli"]
        lines.append("| Check | Score | Status |")
        lines.append("|-------|-------|--------|")
        lines.append(f"| --help support | {hs['pct']}% | {_pct_indicator(hs['pct'])} |")
        lines.append(f"| Structured output | {so['pct']}% | {_pct_indicator(so['pct'])} |")
        lines.append(f"| Master CLI | {'present' if mc['exists'] else 'missing'} | {'OK' if mc['exists'] else 'TODO'} |")
        lines.append("")

    if "primitives" in all_results:
        r = all_results["primitives"]
        lines.append("## Primitives Coverage\n")
        lines.append(f"**Overall: {r['overall_pct']}%**\n")
        lines.append("| Check | Score | Status |")
        lines.append("|-------|-------|--------|")
        for key, label in [
            ("skill_frontmatter", "Skill frontmatter"),
            ("workflow_skill_linkage", "Workflow-skill linkage"),
            ("rule_enforcement", "Rule enforcement"),
            ("hook_wiring", "Hook wiring"),
            ("adapter_completeness", "Adapter completeness"),
        ]:
            d = r[key]
            lines.append(f"| {label} | {d['pct']}% | {_pct_indicator(d['pct'])} |")
        sra = r["skill_router_activation"]
        lines.append(f"| Skill router activation | {sra['pct']}% | {_pct_indicator(sra['pct'])} |")
        lines.append("")

    if "cross_axis" in all_results:
        r = all_results["cross_axis"]
        lines.append("## Cross-axis Matrix\n")
        lines.append("| Area | Tests | Docs | CLI | Primitives | Overall |")
        lines.append("|------|-------|------|-----|------------|---------|")
        for row in r["matrix"]:
            lines.append(
                f"| {row['area']} | {row.get('tests','n/a')} | {row.get('docs','n/a')} | "
                f"{row.get('cli','n/a')} | {row.get('primitives','n/a')} | {row.get('overall','n/a')} |"
            )
        lines.append(f"\n**Overall project coverage: {r['project_overall_pct']}%**\n")

    return "\n".join(lines)


# ---------------------------------------------------------------------------
# Output directory
# ---------------------------------------------------------------------------

def save_outputs(all_results: dict, out_dir: Path, args: argparse.Namespace) -> None:
    out_dir.mkdir(parents=True, exist_ok=True)
    # Always save JSON
    json_path = out_dir / "coverage-audit.json"
    json_path.write_text(json.dumps(all_results, indent=2, default=str))
    # Always save MD report
    md_path = out_dir / "coverage-audit.md"
    md_path.write_text(format_report_md(all_results))
    # Symlink latest
    latest = out_dir.parent / "latest"
    try:
        if latest.is_symlink():
            latest.unlink()
        latest.symlink_to(out_dir.name)
    except Exception:
        pass


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------

def main() -> int:
    parser = argparse.ArgumentParser(
        description="coverage-audit.py — coverage audit for docs, CLI, and primitives",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog=__doc__,
    )
    parser.add_argument(
        "axis",
        nargs="?",
        choices=["docs", "cli", "primitives", "all"],
        default="all",
        help="Which audit axis to run (default: all)",
    )
    parser.add_argument("--json", action="store_true", help="Output JSON")
    parser.add_argument("--report-md", action="store_true", help="Output Markdown report")
    parser.add_argument("--strict", action="store_true",
                        help=f"Exit 1 if any axis overall_pct < {STRICT_THRESHOLD}")
    args = parser.parse_args()

    axis = args.axis or "all"
    all_results: dict[str, Any] = {}

    run_docs = axis in ("docs", "all")
    run_cli = axis in ("cli", "all")
    run_primitives = axis in ("primitives", "all")
    run_cross = axis == "all"

    if run_docs:
        all_results["docs"] = audit_docs()
    if run_cli:
        all_results["cli"] = audit_cli()
    if run_primitives:
        all_results["primitives"] = audit_primitives()
    if run_cross:
        all_results["cross_axis"] = audit_cross_axis(
            all_results.get("docs", {}),
            all_results.get("cli", {}),
            all_results.get("primitives", {}),
        )

    # Save outputs
    ts = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H-%M-%S")
    out_dir = REPO_ROOT / "out" / "coverage-audit" / ts
    save_outputs(all_results, out_dir, args)

    # Print
    if args.json:
        print(json.dumps(all_results, indent=2, default=str))
    elif args.report_md:
        print(format_report_md(all_results))
    else:
        if "docs" in all_results:
            print(format_docs_table(all_results["docs"]))
            print()
        if "cli" in all_results:
            print(format_cli_table(all_results["cli"]))
            print()
        if "primitives" in all_results:
            print(format_primitives_table(all_results["primitives"]))
            print()
        if "cross_axis" in all_results:
            print(format_cross_axis_table(all_results["cross_axis"]))
            print()
        print(f"Output saved to: {out_dir}")

    # Strict mode
    if args.strict:
        for ax_name, ax_results in all_results.items():
            if ax_name == "cross_axis":
                pct = ax_results.get("project_overall_pct", 100)
            else:
                pct = ax_results.get("overall_pct", 100)
            if pct < STRICT_THRESHOLD:
                print(f"STRICT FAIL: {ax_name} overall {pct}% < {STRICT_THRESHOLD}%", file=sys.stderr)
                return 1

    return 0


if __name__ == "__main__":
    sys.exit(main())
