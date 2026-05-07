#!/usr/bin/env python3
"""
workflow-runner.py — Parse and dispatch .ai/primitives/workflows/<name>.md step by step.

Usage:
    workflow-runner.py <workflow-name>          interactive mode
    workflow-runner.py --dry-run <workflow>     show plan, do not advance
    workflow-runner.py --auto <workflow>        CI mode: print plan and exit 0
    workflow-runner.py --help
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
WORKFLOWS_DIR = REPO_ROOT / ".ai" / "primitives" / "workflows"
LOGS_DIR = REPO_ROOT / ".ai" / "logs" / "workflow-runs"


# ---------------------------------------------------------------------------
# Parsing
# ---------------------------------------------------------------------------

def _parse_frontmatter(text: str) -> tuple[dict, str]:
    """Extract simple YAML frontmatter. Returns (meta, body)."""
    lines = text.splitlines()
    if not lines or lines[0].strip() != "---":
        return {}, text
    end = None
    for i, line in enumerate(lines[1:], 1):
        if line.strip() == "---":
            end = i
            break
    if end is None:
        return {}, text
    meta: dict[str, Any] = {}
    for line in lines[1:end]:
        if ":" in line:
            key, _, val = line.partition(":")
            meta[key.strip()] = val.strip()
    body = "\n".join(lines[end + 1:])
    return meta, body


def parse_steps(body: str) -> list[dict]:
    """
    Parse steps from workflow body.
    Supports:
      ## 1. Step title   (or ## Step 1 / ## 1. Title)
      1. Step title      (numbered list at top level, possibly with sub-content)
    Returns list of {"number": int, "title": str, "content": str}
    """
    steps: list[dict] = []

    # Strategy 1: ## headings with numbers
    heading_pattern = re.compile(
        r'^##\s+(?:Step\s+)?(\d+)[.:)]\s*(.+)', re.MULTILINE
    )
    matches = list(heading_pattern.finditer(body))
    if matches:
        for idx, m in enumerate(matches):
            start = m.end()
            end = matches[idx + 1].start() if idx + 1 < len(matches) else len(body)
            content = body[start:end].strip()
            steps.append({
                "number": int(m.group(1)),
                "title": m.group(2).strip(),
                "content": content,
            })
        return steps

    # Strategy 2: numbered list items (1. ... 2. ...)
    list_pattern = re.compile(r'^(\d+)\.\s+(.+)', re.MULTILINE)
    matches = list(list_pattern.finditer(body))
    if matches:
        for idx, m in enumerate(matches):
            start = m.end()
            end = matches[idx + 1].start() if idx + 1 < len(matches) else len(body)
            # Only grab content until next numbered item
            content_lines = []
            for line in body[start:end].splitlines():
                if re.match(r'^\d+\.', line):
                    break
                content_lines.append(line)
            steps.append({
                "number": int(m.group(1)),
                "title": m.group(2).strip(),
                "content": "\n".join(content_lines).strip(),
            })
        return steps

    return steps


def route_skill_for_step(title: str, content: str) -> dict | None:
    """Ask skill-router for the best skill for this step. Returns top result or None."""
    query = f"{title} {content[:100]}"
    try:
        result = subprocess.run(
            ["python3", str(SCRIPT_DIR / "skill-router.py"), "--json", "--top", "1", query],
            capture_output=True,
            text=True,
            timeout=5,
            cwd=str(REPO_ROOT),
        )
        if result.returncode == 0:
            data = json.loads(result.stdout)
            results = data.get("results", [])
            if results:
                return results[0]
    except Exception:
        pass
    return None


# ---------------------------------------------------------------------------
# Logging
# ---------------------------------------------------------------------------

def log_step(log_path: Path, event: dict) -> None:
    """Append one JSON line to the workflow run log."""
    LOGS_DIR.mkdir(parents=True, exist_ok=True)
    with log_path.open("a", encoding="utf-8") as f:
        f.write(json.dumps(event) + "\n")


# ---------------------------------------------------------------------------
# Display
# ---------------------------------------------------------------------------

def print_plan(workflow_name: str, steps: list[dict], skill_map: dict[int, dict | None]) -> None:
    print(f"\nWorkflow: {workflow_name}")
    print(f"Steps: {len(steps)}")
    print("-" * 60)
    for step in steps:
        skill = skill_map.get(step["number"])
        skill_info = ""
        if skill:
            skill_info = f"  -> skill: {skill['name']} (score: {skill['score']:.2f})"
        else:
            skill_info = "  -> no skill match"
        print(f"  Step {step['number']}: {step['title']}")
        print(f"  {skill_info}")
        if step["content"]:
            first_line = step["content"].splitlines()[0] if step["content"].splitlines() else ""
            if first_line:
                print(f"     {first_line[:80]}")
        print()


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------

def load_workflow(name: str) -> tuple[dict, list[dict]]:
    """Load and parse a workflow. Raises FileNotFoundError if not found."""
    # Accept name with or without .md
    stem = name.replace(".md", "")
    path = WORKFLOWS_DIR / f"{stem}.md"
    if not path.exists():
        raise FileNotFoundError(f"Workflow not found: {path}")
    text = path.read_text(encoding="utf-8")
    meta, body = _parse_frontmatter(text)
    steps = parse_steps(body)
    return meta, steps


def run_interactive(workflow_name: str, steps: list[dict], skill_map: dict, log_path: Path) -> None:
    print(f"\nWorkflow: {workflow_name} — interactive mode")
    print(f"Total steps: {len(steps)}")
    print("Press Enter after each step to advance. Ctrl+C to abort.\n")

    for step in steps:
        skill = skill_map.get(step["number"])
        print(f"--- Step {step['number']}: {step['title']} ---")
        if step["content"]:
            print(step["content"])
        print()
        if skill:
            print(f"  Recommended skill: {skill['name']} (score: {skill['score']:.2f})")
            print(f"  Invoke: SKILL: Load .ai/primitives/skills/{skill['name']}.md as your guide.")
        else:
            print("  No skill match found for this step.")
        print()

        event = {
            "ts": datetime.now(timezone.utc).isoformat(),
            "workflow": workflow_name,
            "step": step["number"],
            "title": step["title"],
            "skill": skill["name"] if skill else None,
            "score": skill["score"] if skill else None,
            "mode": "interactive",
        }
        log_step(log_path, event)

        try:
            input("  [Press Enter to continue to next step, Ctrl+C to stop] ")
        except (KeyboardInterrupt, EOFError):
            print("\nAborted.")
            sys.exit(0)

    print(f"\nWorkflow {workflow_name} completed. Log: {log_path}")


def run_auto(workflow_name: str, steps: list[dict], skill_map: dict, log_path: Path) -> None:
    print(f"\nWorkflow: {workflow_name} — auto/CI mode")
    print_plan(workflow_name, steps, skill_map)
    for step in steps:
        skill = skill_map.get(step["number"])
        event = {
            "ts": datetime.now(timezone.utc).isoformat(),
            "workflow": workflow_name,
            "step": step["number"],
            "title": step["title"],
            "skill": skill["name"] if skill else None,
            "score": skill["score"] if skill else None,
            "mode": "auto",
        }
        log_step(log_path, event)
    print(f"Log: {log_path}")


def run_dry_run(workflow_name: str, steps: list[dict], skill_map: dict) -> None:
    print(f"\nWorkflow: {workflow_name} — dry-run (no execution, no logging)")
    print_plan(workflow_name, steps, skill_map)


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Parse and dispatch .ai/primitives/workflows/*.md step by step.",
        epilog="Examples:\n"
               "  workflow-runner.py add-comm-pattern\n"
               "  workflow-runner.py --dry-run add-comm-pattern\n"
               "  workflow-runner.py --auto new-feature-atdd",
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )
    parser.add_argument("workflow", nargs="?", help="Workflow name (without .md)")
    parser.add_argument("--dry-run", action="store_true", help="Show plan only, no execution")
    parser.add_argument("--auto", action="store_true", help="CI mode: print plan + log, no interaction")
    parser.add_argument("--list", action="store_true", help="List available workflows")
    args = parser.parse_args()

    if args.list:
        if not WORKFLOWS_DIR.exists():
            print("No workflows directory found.")
            sys.exit(0)
        for f in sorted(WORKFLOWS_DIR.glob("*.md")):
            print(f.stem)
        sys.exit(0)

    if not args.workflow:
        parser.print_help()
        sys.exit(0)

    try:
        meta, steps = load_workflow(args.workflow)
    except FileNotFoundError as e:
        print(f"Error: {e}", file=sys.stderr)
        sys.exit(1)

    workflow_name = args.workflow.replace(".md", "")

    if not steps:
        print(f"Warning: no steps found in workflow '{workflow_name}'.", file=sys.stderr)
        print("Check that the file uses '## N. Title' or '1. Title' step format.")
        sys.exit(0)

    # Route skills for each step (best effort)
    skill_map: dict[int, dict | None] = {}
    for step in steps:
        skill_map[step["number"]] = route_skill_for_step(step["title"], step["content"])

    if args.dry_run:
        run_dry_run(workflow_name, steps, skill_map)
        sys.exit(0)

    ts = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
    log_path = LOGS_DIR / f"{workflow_name}-{ts}.jsonl"

    if args.auto:
        run_auto(workflow_name, steps, skill_map, log_path)
    else:
        run_interactive(workflow_name, steps, skill_map, log_path)


if __name__ == "__main__":
    main()
