#!/usr/bin/env python3
"""
auto-fix-dispatcher.py — reads failures-aggregator output and proposes fix prompts.

Does NOT apply changes automatically. Proposes diffs / Claude prompts for human review.

Safeguards (hardcoded, non-negotiable):
  - FORBIDDEN_FIX_PATTERNS: never suggest disabling tests, modifying assertions,
    or lowering coverage thresholds.
  - MAX_FILES_PER_FIX: require extra confirmation for fixes touching > 5 files.

Usage:
  python3 auto-fix-dispatcher.py --dry-run         # print plan only (default)
  python3 auto-fix-dispatcher.py --apply            # interactive confirm per cluster
  python3 auto-fix-dispatcher.py --max-iter 3       # convergence loop
  python3 auto-fix-dispatcher.py --autonomous       # NO confirm (warns loudly + requires typed confirmation)
"""

import argparse
import json
import os
import re
import sys

# ---------------------------------------------------------------------------
# Safeguards — HARDCODED, non-negotiable
# ---------------------------------------------------------------------------

FORBIDDEN_FIX_PATTERNS = [
    # NEVER disable tests
    r"@(Disabled|Ignore|Skip|wip)",
    r"\.skip\s*\(",
    r"//\s*TODO:?\s*re-enable",
    # NEVER modify assertions
    r"//\s*assert\s",
    r"#\s*assert\s",
    # NEVER lower coverage thresholds
    r"coverage.*=\s*[0-9]+\s*[#//]",
]

MAX_FILES_PER_FIX = 5

# ---------------------------------------------------------------------------
# Root cause -> prompt template mapping
# ---------------------------------------------------------------------------

PROMPT_TEMPLATES = {
    "service down": """
# Fix: Service Down

## Context
The following tests failed because a service was unreachable (Connection refused / EHOSTUNREACH):

{failure_list}

## Investigation steps
1. Check docker compose ps to see which services are down.
2. Look at logs of the failed service: docker compose logs <service>
3. Check if the service depends_on any other service that might be unhealthy.

## Proposed fix
- If the service is crashing on start, read its logs and fix the startup error.
- If it's a missing dependency, add a healthcheck or depends_on condition.
- Do NOT disable the failing test. Fix the underlying service.

## Files likely to change (check first)
- compose/docker-compose.yml or compose.override.yml (healthcheck / depends_on)
- Application startup code (if it crashes)

Note: Review each change. Do not touch test files.
""",
    "slow service": """
# Fix: Slow Service / Timeout

## Context
Tests timed out waiting for a service response:

{failure_list}

## Investigation steps
1. Check service CPU/mem usage: docker stats --no-stream
2. Look for GC pauses or slow initialization in logs.
3. Check healthcheck retries configuration.

## Proposed fix
- Increase timeout in test configuration (not by disabling the test).
- Increase healthcheck retries in compose file.
- Investigate if service has a memory limit that is too low.

## Files likely to change
- compose/docker-compose.yml (mem_limit, healthcheck)
- Test configuration YAML (timeout values only — do NOT change assertions)

Note: Review each change. Do not touch assertion logic.
""",
    "network/DNS issue": """
# Fix: Network / DNS Issue

## Context
Tests failed due to DNS resolution failures (NXDOMAIN / UnknownHostException):

{failure_list}

## Investigation steps
1. Run: docker compose exec <service> sh -c "getent hosts <other-service>"
2. Check that both services share the same docker network.
3. Verify service names in docker-compose files match what tests/code expect.

## Proposed fix
- Add the missing network to one or both services in compose file.
- Ensure service hostnames are consistent across compose files.

## Files likely to change
- compose/docker-compose.yml (networks section)
- compose.override.yml files

Note: Review each change. Do not touch test files.
""",
    "test logic issue": """
# Fix: Test Logic Issue (AssertionError)

## Context
Tests failed due to assertion mismatches:

{failure_list}

## IMPORTANT
This root cause type requires HUMAN REVIEW ONLY.
- Do NOT modify assertions without understanding the expected behavior.
- Do NOT disable the test.
- The test may be catching a genuine regression.

## Investigation steps
1. Read the full error message and stack trace.
2. Determine if the assertion expectation is still correct.
3. If the test is wrong, fix the test expectation after team review.
4. If the code is wrong, fix the production code.

AUTO-FIX BLOCKED: assertion changes require manual human review.
""",
    "mem_limit too low": """
# Fix: Memory Limit Too Low

## Context
Services ran out of memory (OutOfMemoryError):

{failure_list}

## Proposed fix
Increase mem_limit in compose file for affected service(s).

Example:
  deploy:
    resources:
      limits:
        memory: 512m  # increase this value

## Files likely to change
- compose/docker-compose.yml
- compose.override.yml

Note: Review each change.
""",
    "classpath issue": """
# Fix: Classpath / Class Not Found

## Context
Tests failed due to missing classes:

{failure_list}

## Proposed fix
1. Run: ./gradlew clean build
2. Check that all required dependencies are declared in build.gradle.kts
3. Verify no circular dependencies between modules.

## Files likely to change
- build.gradle.kts files (dependencies block only)

Note: Do NOT remove any test dependencies.
""",
    "unknown": """
# Fix: Unknown Root Cause

## Context
The following failures have an unrecognised root cause:

{failure_list}

## Investigation steps
1. Read full stack traces in test reports.
2. Check docker compose logs for all services.
3. Run: ./nx debug diagnose

No automated fix suggested. Human investigation required.
""",
}

# ---------------------------------------------------------------------------
# Data model
# ---------------------------------------------------------------------------

class Cluster:
    def __init__(self, root_cause: str, failures: list):
        self.root_cause = root_cause
        self.failures = failures

    @property
    def failure_list_text(self) -> str:
        lines = []
        for f in self.failures:
            lines.append(f"  - [{f['suite']}] {f['location']}: {f['name']}")
            if f.get("message"):
                lines.append(f"    Error: {f['message'][:100]}")
        return "\n".join(lines)

    def build_prompt(self) -> str:
        template = PROMPT_TEMPLATES.get(self.root_cause, PROMPT_TEMPLATES["unknown"])
        return template.format(failure_list=self.failure_list_text).strip()


# ---------------------------------------------------------------------------
# Safety checks
# ---------------------------------------------------------------------------

def _check_forbidden(prompt_text: str) -> list:
    """Return list of forbidden patterns found in the proposed prompt/fix."""
    hits = []
    for pattern in FORBIDDEN_FIX_PATTERNS:
        if re.search(pattern, prompt_text, re.IGNORECASE):
            hits.append(pattern)
    return hits


def _estimate_file_count(prompt_text: str) -> int:
    """Heuristic: count file references in the prompt."""
    return len(re.findall(r"\b\w[\w/.\-]+\.(kts|yml|yaml|java|go|py|json)\b", prompt_text))


# ---------------------------------------------------------------------------
# Cluster builder
# ---------------------------------------------------------------------------

def cluster_failures(failures: list) -> list:
    """Group failures by likely root cause."""
    groups: dict = {}
    for f in failures:
        cause = f.get("root_cause", "unknown")
        groups.setdefault(cause, []).append(f)
    return [Cluster(cause, fs) for cause, fs in groups.items()]


# ---------------------------------------------------------------------------
# Output helpers
# ---------------------------------------------------------------------------

_CYAN = "\033[36m"
_YELLOW = "\033[33m"
_RED = "\033[31m"
_RESET = "\033[0m"


def _color(text: str, code: str) -> str:
    if sys.stdout.isatty():
        return f"{code}{text}{_RESET}"
    return text


def _print_cluster_plan(cluster: Cluster, index: int, total: int):
    print(f"\n{'='*60}")
    print(f"Cluster {index}/{total}: {_color(cluster.root_cause.upper(), _CYAN)}")
    print(f"  Failures: {len(cluster.failures)}")
    print(f"  Affected tests:")
    for f in cluster.failures:
        print(f"    - [{f['suite']}] {f['location']}")
    print()
    print("Proposed prompt to pass to Claude Code:")
    print("-" * 40)
    print(cluster.build_prompt())
    print("-" * 40)


def _safety_check_and_warn(cluster: Cluster) -> bool:
    """Returns True if safe to proceed, False if requires extra confirmation."""
    prompt = cluster.build_prompt()
    forbidden = _check_forbidden(prompt)
    file_count = _estimate_file_count(prompt)

    if forbidden:
        print(_color(
            f"\nSAFETY: Forbidden pattern detected in fix for '{cluster.root_cause}':", _RED
        ))
        for p in forbidden:
            print(f"  Pattern: {p}")
        return False

    if file_count > MAX_FILES_PER_FIX:
        print(_color(
            f"\nSAFETY: Fix for '{cluster.root_cause}' references ~{file_count} files "
            f"(limit: {MAX_FILES_PER_FIX}). Extra confirmation required.", _YELLOW
        ))
        return False

    return True


def _interactive_confirm(prompt_text: str) -> bool:
    """Ask user for confirmation. Returns True if confirmed."""
    try:
        answer = input(f"\n{prompt_text} [y/N] ").strip().lower()
        return answer in ("y", "yes")
    except (EOFError, KeyboardInterrupt):
        return False


# ---------------------------------------------------------------------------
# Autonomous mode warning
# ---------------------------------------------------------------------------

def _autonomous_gate():
    """Require explicit typed confirmation for --autonomous mode."""
    print()
    print(_color("=" * 70, _RED))
    print(_color("WARNING: --autonomous mode disables human review.", _RED))
    print(_color("This is the worst case for AI alignment. Use --apply with", _RED))
    print(_color("confirmation instead.", _RED))
    print(_color("=" * 70, _RED))
    print()
    try:
        answer = input("Are you sure? Type 'YES I UNDERSTAND THE RISK': ").strip()
    except (EOFError, KeyboardInterrupt):
        print("\nAborted.")
        sys.exit(1)
    if answer != "YES I UNDERSTAND THE RISK":
        print("Aborted. Use --apply for interactive mode.")
        sys.exit(1)
    print(_color("Proceeding in autonomous mode. All safety confirmations bypassed.", _RED))
    print()


# ---------------------------------------------------------------------------
# Main dispatcher
# ---------------------------------------------------------------------------

def load_failures(path: str = None) -> list:
    """Load failures from a JSON file or from the latest aggregator output."""
    if path:
        with open(path) as fh:
            data = json.load(fh)
        return data.get("totals", {}).get("failures", [])

    # Try out/failures/latest/summary.md (non-JSON) — fallback: scan for JSON
    script_dir = os.path.dirname(os.path.abspath(__file__))
    repo_root = os.path.abspath(os.path.join(script_dir, "..", ".."))

    # Run aggregator in-process to get fresh data
    sys.path.insert(0, script_dir)
    try:
        import failures_aggregator as fa
        results = fa.collect_results()
        all_failures = [f.to_dict() for r in results for f in r.failures]
        return all_failures
    except ImportError:
        pass

    return []


def run_dispatcher(args):
    failures = load_failures(getattr(args, "input", None))

    if not failures:
        print("No failures found. Nothing to dispatch.")
        return

    clusters = cluster_failures(failures)
    total = len(clusters)

    print(f"\nAuto-fix dispatcher — {len(failures)} failure(s) in {total} cluster(s)\n")

    if args.autonomous:
        _autonomous_gate()

    for i, cluster in enumerate(clusters, 1):
        _print_cluster_plan(cluster, i, total)

        safe = _safety_check_and_warn(cluster)

        if args.dry_run:
            status = _color("DRY-RUN: fix not applied", _YELLOW)
            print(f"\n{status}")
            continue

        if not safe:
            if not args.autonomous:
                confirmed = _interactive_confirm(
                    f"This fix has safety concerns. Apply anyway? (strongly discouraged)"
                )
                if not confirmed:
                    print("Skipped (safety concern).")
                    continue
            else:
                print(_color("AUTONOMOUS: applying despite safety concerns.", _RED))

        if args.apply or args.autonomous:
            if not args.autonomous:
                confirmed = _interactive_confirm(
                    f"Apply fix for cluster '{cluster.root_cause}'?"
                )
                if not confirmed:
                    print("Skipped.")
                    continue

            # Output the prompt that the user or automation can pass to Claude
            prompt_file = f"/tmp/autofix-cluster-{i}-{cluster.root_cause.replace(' ', '_')}.txt"
            with open(prompt_file, "w") as pf:
                pf.write(cluster.build_prompt())
            print(f"\nPrompt written to: {prompt_file}")
            print("To apply: claude code -p \"$(cat " + prompt_file + ")\"")
            print("Or copy the prompt above and paste it into Claude Code.")


def main():
    parser = argparse.ArgumentParser(
        description="Auto-fix dispatcher: clusters failures and proposes fix prompts",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
SAFEGUARDS (hardcoded, non-negotiable):
  - Never suggests disabling tests (@Disabled, .skip, TODO: re-enable)
  - Never suggests modifying assertions
  - Never suggests lowering coverage thresholds
  - Requires extra confirmation for fixes touching > 5 files

MODES:
  --dry-run   (default) Print plan and prompts only. No changes.
  --apply     Interactive confirmation per cluster before outputting prompt.
  --autonomous WARNING: bypasses all human review. Requires typed confirmation.
"""
    )
    parser.add_argument("--dry-run", action="store_true", default=True,
                        help="Print plan only, no prompts written (default)")
    parser.add_argument("--apply", action="store_true",
                        help="Interactive confirm per cluster, write prompt files")
    parser.add_argument("--autonomous", action="store_true",
                        help="Skip all confirmations (requires typed safety acknowledgement)")
    parser.add_argument("--max-iter", type=int, default=1, metavar="N",
                        help="Run dispatcher N times (convergence loop)")
    parser.add_argument("--input", metavar="FILE",
                        help="Path to failures-aggregator JSON output file")
    args = parser.parse_args()

    # --apply and --autonomous override --dry-run
    if args.apply or args.autonomous:
        args.dry_run = False

    for iteration in range(1, args.max_iter + 1):
        if args.max_iter > 1:
            print(f"\n[auto-fix-dispatcher] Iteration {iteration}/{args.max_iter}")
        run_dispatcher(args)

        if args.max_iter > 1 and iteration < args.max_iter:
            print("\n[auto-fix-dispatcher] Re-scanning failures for next iteration...")


if __name__ == "__main__":
    main()
