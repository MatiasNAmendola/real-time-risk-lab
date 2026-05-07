#!/usr/bin/env python3
"""
usage-stats.py — Compute usage telemetry from .ai/logs/skill-routing-*.jsonl
and .ai/logs/workflow-runs/*.jsonl.

Usage:
    usage-stats.py                    ASCII table (default)
    usage-stats.py --json             JSON output
    usage-stats.py --report-md        Markdown output
    usage-stats.py --last N           Only events from last N days
    usage-stats.py --no-color         Disable ANSI colors in table output
    usage-stats.py --help
"""
from __future__ import annotations

import argparse
import json
import os
import sys
from collections import Counter, defaultdict
from datetime import datetime, timezone, timedelta
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parent
REPO_ROOT = SCRIPT_DIR.parent.parent
LOGS_DIR = REPO_ROOT / ".ai" / "logs"
ROUTING_LOGS = LOGS_DIR  # skill-routing-*.jsonl live here
WORKFLOW_LOGS = LOGS_DIR / "workflow-runs"

SCORE_THRESHOLD = 0.5


# ---------------------------------------------------------------------------
# Data loading
# ---------------------------------------------------------------------------

def _parse_ts(ts_str: str) -> datetime | None:
    if not ts_str:
        return None
    for fmt in ("%Y-%m-%dT%H:%M:%S.%f%z", "%Y-%m-%dT%H:%M:%S%z", "%Y-%m-%dT%H:%M:%SZ"):
        try:
            dt = datetime.strptime(ts_str, fmt)
            if dt.tzinfo is None:
                dt = dt.replace(tzinfo=timezone.utc)
            return dt
        except ValueError:
            continue
    return None


def load_routing_events(since: datetime | None = None) -> list[dict]:
    events: list[dict] = []
    if not ROUTING_LOGS.exists():
        return events
    for log_file in sorted(ROUTING_LOGS.glob("skill-routing-*.jsonl")):
        try:
            with log_file.open(encoding="utf-8") as f:
                for line in f:
                    line = line.strip()
                    if not line:
                        continue
                    try:
                        event = json.loads(line)
                        if since:
                            ts = _parse_ts(event.get("ts", ""))
                            if ts and ts < since:
                                continue
                        events.append(event)
                    except json.JSONDecodeError:
                        continue
        except OSError:
            continue
    return events


def load_workflow_events(since: datetime | None = None) -> list[dict]:
    events: list[dict] = []
    if not WORKFLOW_LOGS.exists():
        return events
    for log_file in sorted(WORKFLOW_LOGS.glob("*.jsonl")):
        try:
            with log_file.open(encoding="utf-8") as f:
                for line in f:
                    line = line.strip()
                    if not line:
                        continue
                    try:
                        event = json.loads(line)
                        if since:
                            ts = _parse_ts(event.get("ts", ""))
                            if ts and ts < since:
                                continue
                        events.append(event)
                    except json.JSONDecodeError:
                        continue
        except OSError:
            continue
    return events


# ---------------------------------------------------------------------------
# Computation
# ---------------------------------------------------------------------------

def compute_stats(routing: list[dict], workflow: list[dict]) -> dict:
    total_tool_calls = len(routing)

    # Events where top_skill score > threshold
    skill_activated = [
        e for e in routing
        if float(e.get("score", 0) or 0) > SCORE_THRESHOLD
    ]
    pct_skill = (len(skill_activated) / total_tool_calls * 100) if total_tool_calls else 0.0

    # Skill distribution
    skill_counter: Counter = Counter()
    for e in skill_activated:
        name = e.get("top_skill") or e.get("skill") or ""
        if name:
            skill_counter[name] += 1

    # No-match events (score == 0 or missing top_skill)
    no_match = [
        e for e in routing
        if not e.get("top_skill") and not e.get("skill")
    ]
    pct_no_match = (len(no_match) / total_tool_calls * 100) if total_tool_calls else 0.0

    # Workflows
    workflow_names: Counter = Counter()
    for e in workflow:
        name = e.get("workflow", "")
        if name:
            workflow_names[name] += 1

    # Unique workflow runs (count distinct log files via first event per file is hard; use unique ts+workflow)
    unique_runs: set = set()
    for e in workflow:
        # A run = (workflow, mode, day). Approximate.
        ts = _parse_ts(e.get("ts", ""))
        day = ts.date().isoformat() if ts else "unknown"
        unique_runs.add((e.get("workflow", ""), day))

    return {
        "total_tool_calls": total_tool_calls,
        "skill_activated_count": len(skill_activated),
        "pct_skill_activated": round(pct_skill, 1),
        "no_match_count": len(no_match),
        "pct_no_match": round(pct_no_match, 1),
        "top_skills": skill_counter.most_common(10),
        "total_workflow_events": len(workflow),
        "unique_workflow_runs": len(unique_runs),
        "workflows_run": dict(workflow_names.most_common(10)),
        "score_threshold": SCORE_THRESHOLD,
    }


# ---------------------------------------------------------------------------
# Output formatters
# ---------------------------------------------------------------------------

def _color(text: str, code: str, use_color: bool) -> str:
    if not use_color:
        return text
    return f"\033[{code}m{text}\033[0m"


def format_table(stats: dict, use_color: bool = True) -> str:
    lines: list[str] = []

    def h(text: str) -> str:
        return _color(text, "1;34", use_color)  # bold blue

    def ok(text: str) -> str:
        return _color(text, "0;32", use_color)  # green

    def warn(text: str) -> str:
        return _color(text, "0;33", use_color)  # yellow

    total = stats["total_tool_calls"]
    if total == 0:
        lines.append("0 events tracked. No skill-routing logs found.")
        lines.append(f"  Expected logs in: {ROUTING_LOGS}")
        return "\n".join(lines)

    lines.append(h("=== Primitive Usage Stats ==="))
    lines.append(f"  Tool calls tracked:       {total}")
    pct = stats['pct_skill_activated']
    pct_str = f"{pct}%"
    lines.append(f"  Skills activated (>{SCORE_THRESHOLD}):   {stats['skill_activated_count']} ({ok(pct_str) if pct >= 50 else warn(pct_str)})")
    pct_nm = stats['pct_no_match']
    nm_str = f"{pct_nm}%"
    lines.append(f"  No-skill-match (gap):      {stats['no_match_count']} ({warn(nm_str) if pct_nm > 20 else ok(nm_str)})")
    lines.append("")
    lines.append(h("  Top skills invoked:"))
    if stats["top_skills"]:
        for name, count in stats["top_skills"]:
            bar = "#" * min(count, 20)
            lines.append(f"    {name:<35} {count:>4}  {bar}")
    else:
        lines.append("    (none)")
    lines.append("")
    lines.append(h("  Workflow runs:"))
    lines.append(f"    Total events:            {stats['total_workflow_events']}")
    lines.append(f"    Unique runs (approx):    {stats['unique_workflow_runs']}")
    if stats["workflows_run"]:
        for wf, count in stats["workflows_run"].items():
            lines.append(f"    {wf:<35} {count:>4}")
    else:
        lines.append("    (none)")

    return "\n".join(lines)


def format_json(stats: dict) -> str:
    # Convert Counter tuples to lists for JSON
    out = dict(stats)
    out["top_skills"] = [{"name": n, "count": c} for n, c in stats["top_skills"]]
    return json.dumps(out, indent=2)


def format_markdown(stats: dict) -> str:
    lines: list[str] = []
    ts = datetime.now(timezone.utc).strftime("%Y-%m-%d")
    lines.append(f"## Usage Telemetry — {ts}")
    lines.append("")
    lines.append("### Tool Call Routing")
    lines.append("")
    lines.append("| Metric | Value |")
    lines.append("|---|---|")
    total = stats["total_tool_calls"]
    if total == 0:
        lines.append("| Total tool calls | 0 |")
        lines.append("| Status | No logs yet |")
        return "\n".join(lines)
    lines.append(f"| Total tool calls tracked | {total} |")
    lines.append(f"| Skills activated (score > {SCORE_THRESHOLD}) | {stats['skill_activated_count']} ({stats['pct_skill_activated']}%) |")
    lines.append(f"| No-skill-match (gap) | {stats['no_match_count']} ({stats['pct_no_match']}%) |")
    lines.append("")
    lines.append("### Top Skills Invoked")
    lines.append("")
    lines.append("| Skill | Invocations |")
    lines.append("|---|---|")
    if stats["top_skills"]:
        for name, count in stats["top_skills"]:
            lines.append(f"| `{name}` | {count} |")
    else:
        lines.append("| (none) | — |")
    lines.append("")
    lines.append("### Workflow Runs")
    lines.append("")
    lines.append("| Metric | Value |")
    lines.append("|---|---|")
    lines.append(f"| Total workflow step events | {stats['total_workflow_events']} |")
    lines.append(f"| Unique workflow runs | {stats['unique_workflow_runs']} |")
    if stats["workflows_run"]:
        lines.append("")
        lines.append("| Workflow | Runs |")
        lines.append("|---|---|")
        for wf, count in stats["workflows_run"].items():
            lines.append(f"| `{wf}` | {count} |")
    return "\n".join(lines)


# ---------------------------------------------------------------------------
# CLI
# ---------------------------------------------------------------------------

def main() -> None:
    parser = argparse.ArgumentParser(
        description="Compute usage telemetry for .ai/primitives usage.",
        epilog="Examples:\n"
               "  usage-stats.py\n"
               "  usage-stats.py --json\n"
               "  usage-stats.py --report-md\n"
               "  usage-stats.py --last 7",
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )
    parser.add_argument("--json", action="store_true", help="Output as JSON")
    parser.add_argument("--report-md", action="store_true", help="Output as Markdown")
    parser.add_argument("--last", metavar="N", type=int, default=None,
                        help="Only include events from last N days")
    parser.add_argument("--no-color", action="store_true", help="Disable ANSI color in table output")
    args = parser.parse_args()

    since: datetime | None = None
    if args.last:
        since = datetime.now(timezone.utc) - timedelta(days=args.last)

    routing = load_routing_events(since)
    workflow = load_workflow_events(since)
    stats = compute_stats(routing, workflow)

    if args.json:
        print(format_json(stats))
    elif args.report_md:
        print(format_markdown(stats))
    else:
        use_color = not args.no_color and sys.stdout.isatty()
        print(format_table(stats, use_color=use_color))


if __name__ == "__main__":
    main()
