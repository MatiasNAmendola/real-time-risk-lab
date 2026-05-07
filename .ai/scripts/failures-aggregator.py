#!/usr/bin/env python3
"""
failures-aggregator.py — unified test failure aggregator for all suite formats.

Parses: Gradle Surefire XML, Cucumber JSON, Karate summary JSON,
        Smoke runner meta.json / checks/*.md, Test runner summary.md

Usage:
  python3 failures-aggregator.py                  # scan all suites
  python3 failures-aggregator.py --suite atdd-karate
  python3 failures-aggregator.py --json
  python3 failures-aggregator.py --since 1h
"""

import argparse
import datetime
import glob
import json
import os
import re
import sys
import xml.etree.ElementTree as ET

# ---------------------------------------------------------------------------
# Root detection
# ---------------------------------------------------------------------------
_HERE = os.path.dirname(os.path.abspath(__file__))
REPO_ROOT = os.path.abspath(os.path.join(_HERE, "..", ".."))


# ---------------------------------------------------------------------------
# Data model
# ---------------------------------------------------------------------------

class Failure:
    def __init__(self, suite: str, location: str, name: str, message: str):
        self.suite = suite
        self.location = location
        self.name = name
        self.message = message
        self.root_cause = _heuristic_root_cause(message)
        self.suggested_fix = _suggested_fix(self.root_cause, message)

    def to_dict(self):
        return {
            "suite": self.suite,
            "location": self.location,
            "name": self.name,
            "message": self.message,
            "root_cause": self.root_cause,
            "suggested_fix": self.suggested_fix,
        }


class SuiteResult:
    def __init__(self, name: str, total: int, passed: int, failed: int, skipped: int,
                 note: str = "", failures: list = None):
        self.name = name
        self.total = total
        self.passed = passed
        self.failed = failed
        self.skipped = skipped
        self.note = note
        self.failures: list = failures or []

    def to_dict(self):
        return {
            "name": self.name,
            "total": self.total,
            "passed": self.passed,
            "failed": self.failed,
            "skipped": self.skipped,
            "note": self.note,
            "failures": [f.to_dict() for f in self.failures],
        }


# ---------------------------------------------------------------------------
# Root cause heuristics
# ---------------------------------------------------------------------------

_HEURISTICS = [
    (r"Connection refused|EHOSTUNREACH|connection refused", "service down"),
    (r"Timeout|Timed out|timed out|TimeoutException", "slow service"),
    (r"NXDOMAIN|UnknownHostException|no such host", "network/DNS issue"),
    (r"AssertionError|AssertionFailedError|Expected.*but.*was", "test logic issue"),
    (r"OutOfMemoryError|java\.lang\.OutOfMemoryError", "mem_limit too low"),
    (r"ClassNotFoundException|NoClassDefFoundError", "classpath issue"),
    (r"NullPointerException|NPE", "null pointer / missing initialization"),
    (r"SocketException|SocketTimeoutException", "network socket issue"),
    (r"FileNotFoundException|No such file", "missing file/resource"),
]

_SUGGESTED_FIXES = {
    "service down": "check that the service is running: docker compose up <service>",
    "slow service": "increase timeout configuration or healthcheck retries",
    "network/DNS issue": "verify shared docker networks and service hostnames",
    "test logic issue": "review assertion logic — no auto-fix available",
    "mem_limit too low": "increase mem_limit in compose file for affected service",
    "classpath issue": "run ./gradlew clean build to regenerate classpath",
    "null pointer / missing initialization": "check initialization order in test setup",
    "network socket issue": "verify service is accepting connections on expected port",
    "missing file/resource": "check that required test fixtures/resources exist",
    "unknown": "review full error message and stack trace",
}


def _heuristic_root_cause(message: str) -> str:
    if not message:
        return "unknown"
    for pattern, cause in _HEURISTICS:
        if re.search(pattern, message, re.IGNORECASE):
            return cause
    return "unknown"


def _suggested_fix(root_cause: str, message: str) -> str:
    base = _SUGGESTED_FIXES.get(root_cause, _SUGGESTED_FIXES["unknown"])
    # Enrich with context from message
    if root_cause == "service down":
        m = re.search(r"([\w\-]+):\d+", message)
        if m:
            svc = m.group(1)
            return f"check {svc} is running: docker compose up {svc}"
    if root_cause == "network/DNS issue":
        m = re.search(r"NXDOMAIN.*?'(\S+)'|UnknownHostException.*?'?(\S+)'?", message)
        if m:
            host = m.group(1) or m.group(2) or ""
            if host:
                return f"check '{host}' hostname/DNS; verify docker networks"
    return base


# ---------------------------------------------------------------------------
# Parsers
# ---------------------------------------------------------------------------

def _parse_surefire_xml(path: str, suite_name: str) -> SuiteResult:
    """Parse a single Surefire XML file."""
    try:
        tree = ET.parse(path)
        root = tree.getroot()
    except ET.ParseError:
        return SuiteResult(suite_name, 0, 0, 0, 0, note="XML parse error")

    # Handle both <testsuite> and <testsuites> root
    suites = []
    if root.tag == "testsuite":
        suites = [root]
    elif root.tag == "testsuites":
        suites = list(root.iter("testsuite"))
    else:
        suites = list(root.iter("testsuite")) or [root]

    total = passed = failed = skipped = 0
    failures = []

    for ts in suites:
        total += int(ts.get("tests", 0))
        failed += int(ts.get("failures", 0)) + int(ts.get("errors", 0))
        skipped += int(ts.get("skipped", 0))

        for tc in ts.iter("testcase"):
            failure_el = tc.find("failure")
            if failure_el is None:
                failure_el = tc.find("error")
            if failure_el is not None:
                msg = (failure_el.get("message", "") or failure_el.text or "").strip()
                classname = tc.get("classname", "")
                testname = tc.get("name", "unknown")
                location = f"{classname}.{testname}" if classname else testname
                failures.append(Failure(suite_name, location, testname, msg[:300]))

    passed = total - failed - skipped
    return SuiteResult(suite_name, total, max(0, passed), failed, skipped, failures=failures)


def _aggregate_surefire(suite_name: str = "unit") -> SuiteResult:
    """Aggregate all Surefire XML files found in build output."""
    pattern = os.path.join(REPO_ROOT, "**/build/test-results/test/*.xml")
    files = glob.glob(pattern, recursive=True)
    if not files:
        return SuiteResult(suite_name, 0, 0, 0, 0, note="no Surefire XML found")

    total = passed = failed = skipped = 0
    failures = []

    for f in sorted(files):
        r = _parse_surefire_xml(f, suite_name)
        total += r.total
        passed += r.passed
        failed += r.failed
        skipped += r.skipped
        failures.extend(r.failures)

    return SuiteResult(suite_name, total, passed, failed, skipped, failures=failures)


def _parse_cucumber_json(path: str, suite_name: str) -> SuiteResult:
    """Parse a Cucumber JSON report."""
    try:
        with open(path) as fh:
            data = json.load(fh)
    except (json.JSONDecodeError, OSError):
        return SuiteResult(suite_name, 0, 0, 0, 0, note="JSON parse error")

    total = passed = failed = skipped = 0
    failures = []

    for feature in data:
        feature_name = feature.get("name", "unknown")
        uri = feature.get("uri", feature_name)
        for element in feature.get("elements", []):
            scenario_name = element.get("name", "unknown")
            steps = element.get("steps", [])
            if not steps:
                skipped += 1
                total += 1
                continue
            scenario_failed = False
            scenario_skipped = True
            fail_msg = ""
            for step in steps:
                result = step.get("result", {})
                status = result.get("status", "undefined")
                if status in ("passed",):
                    scenario_skipped = False
                elif status in ("failed",):
                    scenario_skipped = False
                    scenario_failed = True
                    fail_msg = result.get("error_message", "")[:300]
                elif status in ("skipped", "pending", "undefined"):
                    pass

            total += 1
            if scenario_failed:
                failed += 1
                location = f"{uri}:{scenario_name}"
                failures.append(Failure(suite_name, location, scenario_name, fail_msg))
            elif scenario_skipped:
                skipped += 1
            else:
                passed += 1

    return SuiteResult(suite_name, total, passed, failed, skipped, failures=failures)


def _aggregate_cucumber(suite_name: str = "atdd-cucumber") -> SuiteResult:
    """Find and parse Cucumber JSON reports."""
    patterns = [
        os.path.join(REPO_ROOT, "tests/risk-engine-atdd/target/cucumber.json"),
        os.path.join(REPO_ROOT, "tests/**/target/cucumber.json"),
        os.path.join(REPO_ROOT, "**/target/cucumber.json"),
    ]
    files = set()
    for p in patterns:
        files.update(glob.glob(p, recursive=True))

    if not files:
        return SuiteResult(suite_name, 0, 0, 0, 0, note="no Cucumber JSON found")

    total = passed = failed = skipped = 0
    failures = []
    for f in sorted(files):
        r = _parse_cucumber_json(f, suite_name)
        total += r.total
        passed += r.passed
        failed += r.failed
        skipped += r.skipped
        failures.extend(r.failures)

    return SuiteResult(suite_name, total, passed, failed, skipped, failures=failures)


def _parse_karate_summary(path: str, suite_name: str) -> SuiteResult:
    """Parse a Karate summary JSON report."""
    try:
        with open(path) as fh:
            data = json.load(fh)
    except (json.JSONDecodeError, OSError):
        return SuiteResult(suite_name, 0, 0, 0, 0, note="JSON parse error")

    # Karate summary format varies; handle both common shapes
    failures = []
    total = data.get("scenarioCount", data.get("featureCount", 0))
    failed = data.get("scenariosFailed", data.get("failureCount", 0))
    skipped = data.get("scenariosPending", 0)
    passed = total - failed - skipped

    # Drill into feature results for failure details
    feature_results = data.get("featureResults", [])
    for fr in feature_results:
        feature_file = fr.get("relativeUri", fr.get("displayName", "unknown"))
        for sc in fr.get("scenarioResults", []):
            if sc.get("failed", False):
                name = sc.get("name", "unknown")
                line = sc.get("line", 0)
                location = f"{feature_file}:{line}"
                error = sc.get("errorMessage", sc.get("error", ""))[:300]
                failures.append(Failure(suite_name, location, name, error))

    return SuiteResult(suite_name, total, max(0, passed), failed, skipped, failures=failures)


def _aggregate_karate(suite_name: str = "atdd-karate") -> SuiteResult:
    """Find and parse Karate summary JSON reports."""
    patterns = [
        os.path.join(REPO_ROOT, "poc/*/atdd-tests/target/karate-reports/karate-summary.json"),
        os.path.join(REPO_ROOT, "**/karate-reports/karate-summary.json"),
    ]
    files = set()
    for p in patterns:
        files.update(glob.glob(p, recursive=True))

    if not files:
        return SuiteResult(suite_name, 0, 0, 0, 0, note="no Karate summary JSON found")

    total = passed = failed = skipped = 0
    failures = []
    for f in sorted(files):
        r = _parse_karate_summary(f, suite_name)
        total += r.total
        passed += r.passed
        failed += r.failed
        skipped += r.skipped
        failures.extend(r.failures)

    return SuiteResult(suite_name, total, passed, failed, skipped, failures=failures)


def _aggregate_smoke(suite_name: str = "smoke") -> SuiteResult:
    """Parse smoke runner output from out/smoke/latest/."""
    latest_dir = os.path.join(REPO_ROOT, "out/smoke/latest")
    if not os.path.isdir(latest_dir):
        return SuiteResult(suite_name, 0, 0, 0, 0, note="no smoke output found")

    # Try meta.json first
    meta_path = os.path.join(latest_dir, "meta.json")
    if os.path.isfile(meta_path):
        try:
            with open(meta_path) as fh:
                meta = json.load(fh)
            total = meta.get("total", 0)
            passed = meta.get("passed", 0)
            failed = meta.get("failed", 0)
            skipped = meta.get("skipped", 0)
            failures = []
            for f in meta.get("failures", []):
                failures.append(Failure(
                    suite_name,
                    f.get("check", "unknown"),
                    f.get("name", "unknown"),
                    f.get("message", ""),
                ))
            return SuiteResult(suite_name, total, passed, failed, skipped, failures=failures)
        except (json.JSONDecodeError, OSError):
            pass

    # Fall back to checks/*.md files
    checks_dir = os.path.join(latest_dir, "checks")
    md_files = glob.glob(os.path.join(checks_dir, "*.md"))
    if not md_files:
        return SuiteResult(suite_name, 0, 0, 0, 0, note="no smoke checks found")

    total = len(md_files)
    passed = failed = skipped = 0
    failures = []
    for md in sorted(md_files):
        try:
            content = open(md).read()
        except OSError:
            continue
        if re.search(r"PASS|OK|success", content, re.IGNORECASE):
            passed += 1
        elif re.search(r"FAIL|ERROR|FATAL", content, re.IGNORECASE):
            failed += 1
            check_name = os.path.basename(md).replace(".md", "")
            # Extract first error line
            lines = content.splitlines()
            msg = next((l for l in lines if re.search(r"FAIL|ERROR|FATAL", l, re.IGNORECASE)), "")
            failures.append(Failure(suite_name, check_name, check_name, msg[:300]))
        else:
            skipped += 1

    return SuiteResult(suite_name, total, passed, failed, skipped, failures=failures)


def _aggregate_test_runner(suite_name: str = "integration") -> SuiteResult:
    """Parse test runner summary.md from out/test-runner/latest/."""
    summary_path = os.path.join(REPO_ROOT, "out/test-runner/latest/summary.md")
    if not os.path.isfile(summary_path):
        return SuiteResult(suite_name, 0, 0, 0, 0, note="no test-runner summary found")

    try:
        content = open(summary_path).read()
    except OSError:
        return SuiteResult(suite_name, 0, 0, 0, 0, note="cannot read test-runner summary")

    total = passed = failed = skipped = 0
    failures = []
    note = ""

    # Look for summary table lines like: | unit | 124 | 124 | 0 | 0 |
    for line in content.splitlines():
        m = re.match(r"\|\s*(\w[\w\-]+)\s*\|\s*(\d+)\s*\|\s*(\d+)\s*\|\s*(\d+)\s*\|\s*(\d+)", line)
        if m:
            t, p, f, s = int(m.group(2)), int(m.group(3)), int(m.group(4)), int(m.group(5))
            total += t
            passed += p
            failed += f
            skipped += s

    # Look for explicit failure blocks
    fail_blocks = re.findall(r"\[FAIL\]\s+(.*?)\n(.*?)\n", content)
    for loc, msg in fail_blocks:
        failures.append(Failure(suite_name, loc.strip(), loc.strip(), msg.strip()[:300]))

    # Detect skip reason
    if re.search(r"Docker not running|skipped", content, re.IGNORECASE):
        note = "Docker not running — skipped"

    return SuiteResult(suite_name, total, passed, failed, skipped, note=note, failures=failures)


# ---------------------------------------------------------------------------
# Time filter
# ---------------------------------------------------------------------------

def _parse_since(since: str) -> datetime.datetime:
    """Parse --since value like 1h, 30m, 2d into a datetime threshold."""
    m = re.match(r"^(\d+)([smhd])$", since.strip())
    if not m:
        raise ValueError(f"Invalid --since value: {since}. Use e.g. 1h, 30m, 2d")
    n = int(m.group(1))
    unit = m.group(2)
    delta = {"s": 1, "m": 60, "h": 3600, "d": 86400}[unit]
    return datetime.datetime.utcnow() - datetime.timedelta(seconds=n * delta)


def _file_newer_than(path: str, threshold: datetime.datetime) -> bool:
    try:
        mtime = os.path.getmtime(path)
        return datetime.datetime.utcfromtimestamp(mtime) >= threshold
    except OSError:
        return False


# ---------------------------------------------------------------------------
# Aggregation
# ---------------------------------------------------------------------------

ALL_SUITES = {
    "unit": lambda: _aggregate_surefire("unit"),
    "arch": lambda: _aggregate_surefire("arch"),
    "atdd-cucumber": lambda: _aggregate_cucumber("atdd-cucumber"),
    "atdd-karate": lambda: _aggregate_karate("atdd-karate"),
    "smoke": lambda: _aggregate_smoke("smoke"),
    "integration": lambda: _aggregate_test_runner("integration"),
}


def collect_results(suite_filter: str = None) -> list:
    if suite_filter:
        if suite_filter not in ALL_SUITES:
            print(f"Unknown suite: {suite_filter}. Available: {', '.join(ALL_SUITES)}", file=sys.stderr)
            sys.exit(1)
        return [ALL_SUITES[suite_filter]()]
    return [fn() for fn in ALL_SUITES.values()]


# ---------------------------------------------------------------------------
# Output formatters
# ---------------------------------------------------------------------------

def _col(s, width):
    return str(s).ljust(width)


def format_table(results: list) -> str:
    lines = []
    ts = datetime.datetime.utcnow().strftime("%Y-%m-%dT%H:%M:%SZ")
    lines.append(f"Failures summary -- {ts}")
    lines.append("")
    header = f"  {'Suite':<22} {'Total':>6}  {'Pass':>5}  {'Fail':>5}  {'Skip':>5}"
    lines.append(header)
    lines.append("  " + "-" * 49)

    total_t = total_p = total_f = total_s = 0
    for r in results:
        note_str = f"   ({r.note})" if r.note else ""
        lines.append(
            f"  {r.name:<22} {r.total:>6}  {r.passed:>5}  {r.failed:>5}  {r.skipped:>5}{note_str}"
        )
        total_t += r.total
        total_p += r.passed
        total_f += r.failed
        total_s += r.skipped

    lines.append("  " + "-" * 49)
    lines.append(f"  {'TOTAL':<22} {total_t:>6}  {total_p:>5}  {total_f:>5}  {total_s:>5}")

    # Failures detail
    all_failures = [f for r in results for f in r.failures]
    if all_failures:
        lines.append("")
        lines.append(f"Failures ({len(all_failures)}):")
        lines.append("")
        for i, f in enumerate(all_failures, 1):
            lines.append(f"  [{i}] {f.suite}/{f.location}")
            lines.append(f'      "{f.name}"')
            if f.message:
                msg_short = f.message.replace("\n", " ")[:120]
                lines.append(f"      -> {msg_short}")
            lines.append(f"      Likely root cause (heuristic): {f.root_cause}")
            lines.append(f"      Suggested fix: {f.suggested_fix}")
            lines.append("")
    else:
        lines.append("")
        lines.append("No failures found.")

    return "\n".join(lines)


def format_json(results: list) -> str:
    return json.dumps(
        {
            "generated_at": datetime.datetime.utcnow().isoformat() + "Z",
            "suites": [r.to_dict() for r in results],
            "totals": {
                "total": sum(r.total for r in results),
                "passed": sum(r.passed for r in results),
                "failed": sum(r.failed for r in results),
                "skipped": sum(r.skipped for r in results),
                "failures": [f.to_dict() for r in results for f in r.failures],
            },
        },
        indent=2,
    )


# ---------------------------------------------------------------------------
# Output writer
# ---------------------------------------------------------------------------

def write_output(content: str, ts_dir: str):
    out_dir = os.path.join(REPO_ROOT, "out", "failures", ts_dir)
    os.makedirs(out_dir, exist_ok=True)
    summary_path = os.path.join(out_dir, "summary.md")
    with open(summary_path, "w") as fh:
        fh.write(content)
    # Update latest symlink
    latest_link = os.path.join(REPO_ROOT, "out", "failures", "latest")
    if os.path.islink(latest_link):
        os.unlink(latest_link)
    os.symlink(ts_dir, latest_link)
    return summary_path


# ---------------------------------------------------------------------------
# CLI
# ---------------------------------------------------------------------------

def main():
    parser = argparse.ArgumentParser(
        description="Aggregate test failures from all suite formats",
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )
    parser.add_argument("--suite", metavar="NAME",
                        help="Only scan one suite: unit, arch, atdd-cucumber, atdd-karate, smoke, integration")
    parser.add_argument("--json", action="store_true",
                        help="Output as JSON")
    parser.add_argument("--since", metavar="DURATION",
                        help="Only include results newer than this (e.g. 1h, 30m, 2d)")
    parser.add_argument("--no-save", action="store_true",
                        help="Do not write output file")
    args = parser.parse_args()

    results = collect_results(args.suite)

    if args.json:
        output = format_json(results)
    else:
        output = format_table(results)

    print(output)

    if not args.no_save and not args.json:
        ts = datetime.datetime.utcnow().strftime("%Y%m%dT%H%M%SZ")
        saved = write_output(output, ts)
        print(f"\n[failures-aggregator] Saved: {saved}", file=sys.stderr)


if __name__ == "__main__":
    main()
