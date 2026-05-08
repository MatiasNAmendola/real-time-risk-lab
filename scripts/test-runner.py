#!/usr/bin/env python3
"""
test-runner.py -- Intelligent parallel test runner with resource throttling.

Usage:
    scripts/test-runner.py --list
    scripts/test-runner.py --group unit
    scripts/test-runner.py --groups unit,arch
    scripts/test-runner.py --composite ci-fast
    scripts/test-runner.py --composite ci-fast --auto-parallel
    scripts/test-runner.py --composite ci-fast --dry-run
    scripts/test-runner.py --group integration --auto-infra
    scripts/test-runner.py --composite all --max-cpu 70 --max-ram 6000
    scripts/test-runner.py --json
"""
from __future__ import annotations

import argparse
import json
import os
import subprocess
import sys
import threading
import time
from concurrent.futures import ProcessPoolExecutor, Future, FIRST_COMPLETED, wait as futures_wait
from dataclasses import dataclass, field
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

REPO_ROOT = Path(__file__).resolve().parent.parent
GROUPS_FILE = REPO_ROOT / ".ai" / "test-groups.yaml"
DEFAULT_OUT_DIR = REPO_ROOT / "out" / "test-runner"

# ---------------------------------------------------------------------------
# Mini YAML parser (stdlib only) — handles the test-groups.yaml format
# ---------------------------------------------------------------------------

def _coerce_yaml_scalar(val: str) -> Any:
    """Type-coerce a YAML scalar string. Strips surrounding quotes if present."""
    # Strip a single pair of surrounding quotes (single or double) so that
    # cmd values like  cmd: "./gradlew test"  don't end up as the literal
    # string '"./gradlew test"' (which fails with shell=True as exit 127).
    if len(val) >= 2 and val[0] == val[-1] and val[0] in ('"', "'"):
        val = val[1:-1]
    # Inline flow-style list: [a, b, c]  -> ["a", "b", "c"]
    if len(val) >= 2 and val.startswith("[") and val.endswith("]"):
        inner = val[1:-1].strip()
        if not inner:
            return []
        items = []
        for item in inner.split(","):
            item = item.strip()
            if len(item) >= 2 and item[0] == item[-1] and item[0] in ('"', "'"):
                item = item[1:-1]
            items.append(item)
        return items
    if val.lower() in ("true", "yes"):
        return True
    if val.lower() in ("false", "no"):
        return False
    try:
        return int(val)
    except ValueError:
        pass
    try:
        return float(val)
    except ValueError:
        pass
    return val


def _parse_yaml_testgroups(text: str) -> dict[str, Any]:
    """
    Minimal parser for test-groups.yaml.

    Handles the exact structure produced by test-groups.yaml:
      - Top-level sections: groups, composites
      - Under groups: each entry is a 2-space-indented key (group name) with
        4-space-indented scalar properties
      - Under composites: each entry is a 2-space-indented key (composite name)
        with 4-space-indented list items ("    - item")

    The parser distinguishes group entries (properties) from composite entries
    (list items) by looking ahead: if the first non-blank child line starts
    with "- ", it's a composite list; otherwise it's a property dict.
    """
    result: dict[str, Any] = {}
    lines = [ln.rstrip() for ln in text.splitlines()]
    total = len(lines)
    i = 0

    def _indent(ln: str) -> int:
        return len(ln) - len(ln.lstrip())

    def _skip_blank(pos: int) -> int:
        while pos < total and (not lines[pos].strip() or lines[pos].strip().startswith("#")):
            pos += 1
        return pos

    def _read_props_dict(pos: int, min_indent: int) -> tuple[dict[str, Any], int]:
        """Read key: value lines at >= min_indent until indent drops below min_indent."""
        props: dict[str, Any] = {}
        while True:
            pos = _skip_blank(pos)
            if pos >= total:
                break
            ln = lines[pos]
            ind = _indent(ln)
            if ind < min_indent:
                break
            stripped = ln.strip()
            if ":" in stripped:
                pk, _, pv = stripped.partition(":")
                props[pk.strip()] = _coerce_yaml_scalar(pv.strip())
            pos += 1
        return props, pos

    def _read_list(pos: int, min_indent: int) -> tuple[list[str], int]:
        """Read '- item' lines at >= min_indent."""
        items: list[str] = []
        while True:
            pos = _skip_blank(pos)
            if pos >= total:
                break
            ln = lines[pos]
            ind = _indent(ln)
            if ind < min_indent:
                break
            stripped = ln.strip()
            if stripped.startswith("- "):
                items.append(stripped[2:].strip())
            pos += 1
        return items, pos

    while i < total:
        i = _skip_blank(i)
        if i >= total:
            break
        ln = lines[i]
        ind = _indent(ln)
        stripped = ln.strip()

        if ind == 0 and ":" in stripped:
            # Top-level key (groups: or composites:)
            top_key, _, top_val = stripped.partition(":")
            top_key = top_key.strip()
            top_val = top_val.strip()
            i += 1
            if top_val and not top_val.startswith("#"):
                result[top_key] = _coerce_yaml_scalar(top_val)
                continue

            # Block follows: read 2-space-indented sub-keys
            block: dict[str, Any] = {}
            while True:
                i = _skip_blank(i)
                if i >= total:
                    break
                sub_ln = lines[i]
                sub_ind = _indent(sub_ln)
                if sub_ind == 0:
                    break  # back to top level
                if sub_ind != 2:
                    i += 1
                    continue
                sub_stripped = sub_ln.strip()
                if ":" not in sub_stripped:
                    i += 1
                    continue

                sub_key, _, sub_val = sub_stripped.partition(":")
                sub_key = sub_key.strip()
                sub_val = sub_val.strip()
                i += 1

                if sub_val and not sub_val.startswith("#"):
                    # Inline scalar — store directly
                    block[sub_key] = _coerce_yaml_scalar(sub_val)
                    continue

                # Look ahead to determine if children are list items or props
                peek = _skip_blank(i)
                if peek < total and _indent(lines[peek]) >= 4:
                    peek_stripped = lines[peek].strip()
                    if peek_stripped.startswith("- "):
                        # Composite: list of group names
                        items, i = _read_list(i, 4)
                        block[sub_key] = items
                    else:
                        # Group: property dict
                        props, i = _read_props_dict(i, 4)
                        block[sub_key] = props
                else:
                    block[sub_key] = {}

            result[top_key] = block
        else:
            i += 1

    return result


def load_test_groups() -> tuple[dict[str, Any], dict[str, list[str]]]:
    """Return (groups_dict, composites_dict). Raises FileNotFoundError if missing."""
    text = GROUPS_FILE.read_text(encoding="utf-8")
    parsed = _parse_yaml_testgroups(text)
    groups = parsed.get("groups", {})
    composites = parsed.get("composites", {})
    return groups, composites


# ---------------------------------------------------------------------------
# Cross-platform system stats
# ---------------------------------------------------------------------------

def get_cpu_count() -> int:
    return os.cpu_count() or 1


def get_load_avg() -> float:
    """Return 1-min load average. Returns 0.0 on Windows (no os.getloadavg)."""
    try:
        return os.getloadavg()[0]
    except (AttributeError, OSError):
        return 0.0


def get_total_ram_mb() -> int:
    # Linux
    try:
        with open("/proc/meminfo", encoding="utf-8") as f:
            for line in f:
                if line.startswith("MemTotal:"):
                    return int(line.split()[1]) // 1024
    except FileNotFoundError:
        pass
    # macOS
    try:
        out = subprocess.check_output(
            ["sysctl", "-n", "hw.memsize"], stderr=subprocess.DEVNULL
        ).decode().strip()
        return int(out) // 1024 // 1024
    except Exception:
        pass
    # Windows
    try:
        out = subprocess.check_output(
            ["wmic", "OS", "get", "TotalVisibleMemorySize", "/value"],
            stderr=subprocess.DEVNULL,
        ).decode()
        for part in out.split():
            if "=" in part:
                return int(part.split("=")[1]) // 1024
    except Exception:
        pass
    return 8000  # conservative default


def get_available_ram_mb() -> int:
    # Linux
    try:
        with open("/proc/meminfo", encoding="utf-8") as f:
            for line in f:
                if line.startswith("MemAvailable:"):
                    return int(line.split()[1]) // 1024
    except FileNotFoundError:
        pass
    # macOS via vm_stat
    try:
        out = subprocess.check_output(
            ["vm_stat"], stderr=subprocess.DEVNULL
        ).decode()
        page_size = 4096
        free_pages = 0
        for line in out.splitlines():
            if "page size of" in line:
                try:
                    page_size = int(line.split()[-2])
                except (ValueError, IndexError):
                    pass
            if "Pages free:" in line or "Pages inactive:" in line:
                try:
                    free_pages += int(line.split(":")[1].strip().rstrip("."))
                except (ValueError, IndexError):
                    pass
        return (free_pages * page_size) // 1024 // 1024
    except Exception:
        pass
    # Fallback: assume half of total available
    return get_total_ram_mb() // 2


CPU_COST_MAP = {"low": 15, "medium": 35, "high": 70}


# ---------------------------------------------------------------------------
# Data model
# ---------------------------------------------------------------------------

@dataclass
class TestGroup:
    name: str
    cmd: str
    needs_infra: str = "false"   # false | compose | docker | k8s
    cost_cpu: str = "medium"
    cost_ram_mb: int = 500
    duration_estimate_sec: int = 60
    exclusive: bool = False
    requires: list[str] = field(default_factory=list)  # external tools (npm, go, ./gradlew...)

    @property
    def cost_cpu_pct(self) -> int:
        return CPU_COST_MAP.get(self.cost_cpu, 35)

    @classmethod
    def from_dict(cls, name: str, d: dict[str, Any]) -> "TestGroup":
        req = d.get("requires", []) or []
        if isinstance(req, str):
            # Allow comma-separated string fallback for non-list YAML inputs
            req = [r.strip() for r in req.split(",") if r.strip()]
        return cls(
            name=name,
            cmd=d.get("cmd", ""),
            needs_infra=str(d.get("needs_infra", "false")),
            cost_cpu=d.get("cost_cpu", "medium"),
            cost_ram_mb=int(d.get("cost_ram_mb", 500)),
            duration_estimate_sec=int(d.get("duration_estimate_sec", 60)),
            exclusive=bool(d.get("exclusive", False)),
            requires=list(req),
        )

    def missing_tool(self) -> str | None:
        """Return the first required tool not found in PATH, or None if all present.

        Each entry in `requires` may be a single tool name (e.g. ``"docker"``) or
        a pipe-separated alternation (e.g. ``"fnm|npm"``) meaning *any one of*
        the listed tools satisfies the requirement. This lets groups that need
        Node.js work with either `fnm` (which lazy-activates node/npm on
        demand) OR a directly-installed `npm`.
        """
        import shutil
        for spec in self.requires:
            alternatives = [t.strip() for t in spec.split("|") if t.strip()]
            if not alternatives:
                continue
            if not any(shutil.which(t) is not None for t in alternatives):
                return spec
        return None


@dataclass
class JobResult:
    name: str
    status: str          # PASS | FAIL | SKIP | DRY
    returncode: int = 0
    stdout: str = ""
    stderr: str = ""
    duration_sec: float = 0.0
    error: str = ""


# ---------------------------------------------------------------------------
# Infra helpers
# ---------------------------------------------------------------------------

INFRA_COMPOSE_JOB = "_infra_compose_up"

def _compose_up(dry_run: bool = False) -> bool:
    """Bring up docker-compose stack via the canonical up.sh (waits for
    controller-app healthcheck). Returns True on success.

    Uses scripts/up.sh which composes the shared infra
    (compose/docker-compose.yml) plus the vertx app override
    (poc/java-vertx-distributed/compose.override.yml) and blocks until the
    stack is healthy. The stack is left UP for the duration of the test run
    and is intentionally NOT torn down between jobs (compose-dependent jobs
    must observe a stable, long-lived stack)."""
    up_script = REPO_ROOT / "poc" / "java-vertx-distributed" / "scripts" / "up.sh"
    if not up_script.exists():
        print(f"[runner] WARN: up.sh not found: {up_script}")
        return False
    cmd = ["bash", str(up_script)]
    if dry_run:
        print(f"[runner] DRY: {' '.join(cmd)}")
        return True
    result = subprocess.run(cmd, capture_output=False, text=True)
    return result.returncode == 0


# ---------------------------------------------------------------------------
# Job execution (runs in subprocess via ProcessPoolExecutor)
# ---------------------------------------------------------------------------

def _run_job_fn(name: str, cmd: str, out_dir_str: str) -> JobResult:
    """Execute a single test group. Designed to be picklable for ProcessPoolExecutor."""
    import subprocess, time, os
    from pathlib import Path

    out_dir = Path(out_dir_str)
    log_path = out_dir / f"job-{name}.log"

    start = time.monotonic()
    try:
        proc = subprocess.run(
            cmd,
            shell=True,
            cwd=str(Path(out_dir_str).parent.parent.parent),  # REPO_ROOT
            capture_output=True,
            text=True,
        )
        duration = time.monotonic() - start

        # Write combined log
        with open(log_path, "w", encoding="utf-8") as f:
            f.write(f"# Job: {name}\n")
            f.write(f"# Command: {cmd}\n")
            f.write(f"# Duration: {duration:.1f}s\n")
            f.write(f"# Exit code: {proc.returncode}\n\n")
            f.write("## STDOUT\n")
            f.write(proc.stdout)
            f.write("\n## STDERR\n")
            f.write(proc.stderr)

        status = "PASS" if proc.returncode == 0 else "FAIL"
        return JobResult(
            name=name,
            status=status,
            returncode=proc.returncode,
            stdout=proc.stdout[-2000:] if len(proc.stdout) > 2000 else proc.stdout,
            stderr=proc.stderr[-1000:] if len(proc.stderr) > 1000 else proc.stderr,
            duration_sec=duration,
        )
    except Exception as exc:
        duration = time.monotonic() - start
        return JobResult(
            name=name,
            status="FAIL",
            returncode=-1,
            error=str(exc),
            duration_sec=duration,
        )


# ---------------------------------------------------------------------------
# Topological sort
# ---------------------------------------------------------------------------

def _topological_levels(jobs: list[TestGroup]) -> list[list[TestGroup]]:
    """
    Split jobs into dependency levels.
    Level 0: jobs with no infra dependency.
    Level 1: jobs requiring `compose` infra (run BEFORE docker/Testcontainers
             jobs so Testcontainers' Ryuk reaper does not interfere with the
             long-running shared compose stack).
    Level 2: jobs requiring `docker` (Testcontainers) — they spin up/down
             their own throwaway containers in isolation from compose.
    Exclusive jobs are placed in their own single-job levels.
    """
    no_infra: list[TestGroup] = []
    needs_compose: list[TestGroup] = []
    needs_docker: list[TestGroup] = []
    needs_other: list[TestGroup] = []

    for j in jobs:
        kind = (j.needs_infra or "false").lower()
        if kind in ("false", "no", "none", ""):
            no_infra.append(j)
        elif kind == "compose":
            needs_compose.append(j)
        elif kind == "docker":
            needs_docker.append(j)
        else:
            needs_other.append(j)

    levels: list[list[TestGroup]] = []

    def _emit(bucket: list[TestGroup]) -> None:
        if not bucket:
            return
        non_excl = [j for j in bucket if not j.exclusive]
        excl = [j for j in bucket if j.exclusive]
        if non_excl:
            levels.append(non_excl)
        for j in excl:
            levels.append([j])

    _emit(no_infra)
    # Run compose-needing jobs (smoke against the shared stack) BEFORE
    # Testcontainers (`docker`) jobs to prevent Ryuk teardown races.
    _emit(needs_compose)
    _emit(needs_docker)
    _emit(needs_other)

    return levels


def _needs_compose(jobs: list[TestGroup]) -> bool:
    return any(
        j.needs_infra.lower() in ("compose",) for j in jobs
    )


# ---------------------------------------------------------------------------
# Scheduler / Runner
# ---------------------------------------------------------------------------

class ResourceMonitor:
    """Background thread that refreshes system resource readings every 2s."""

    def __init__(self) -> None:
        self._lock = threading.Lock()
        self._cpu_load: float = get_load_avg()
        self._avail_ram_mb: int = get_available_ram_mb()
        self._cpu_count: int = get_cpu_count()
        self._stop = threading.Event()
        self._thread = threading.Thread(target=self._loop, daemon=True)

    def start(self) -> None:
        self._thread.start()

    def stop(self) -> None:
        self._stop.set()

    def _loop(self) -> None:
        while not self._stop.is_set():
            self._stop.wait(2.0)
            load = get_load_avg()
            avail = get_available_ram_mb()
            with self._lock:
                self._cpu_load = load
                self._avail_ram_mb = avail

    @property
    def cpu_load(self) -> float:
        with self._lock:
            return self._cpu_load

    @property
    def avail_ram_mb(self) -> int:
        with self._lock:
            return self._avail_ram_mb

    @property
    def cpu_count(self) -> int:
        return self._cpu_count


class TestRunner:
    def __init__(
        self,
        parallel: int,
        max_cpu_pct: int,
        max_ram_mb: int,
        dry_run: bool,
        out_dir: Path,
        auto_infra: bool,
        watch: bool,
        json_output: bool,
    ) -> None:
        self.parallel = parallel
        self.max_cpu_pct = max_cpu_pct
        self.max_ram_mb = max_ram_mb
        self.dry_run = dry_run
        self.out_dir = out_dir
        self.auto_infra = auto_infra
        self.watch = watch
        self.json_output = json_output

        self._reserved_cpu_pct: int = 0
        self._reserved_ram_mb: int = 0
        self._exclusive_running: bool = False
        self._lock = threading.Lock()
        self._monitor = ResourceMonitor()
        self._results: list[JobResult] = []

    def _can_dispatch(self, job: TestGroup) -> bool:
        """Check if there is enough headroom to start this job."""
        if self._exclusive_running:
            return False
        if job.exclusive and (self._reserved_cpu_pct > 0 or self._reserved_ram_mb > 0):
            return False
        new_cpu = self._reserved_cpu_pct + job.cost_cpu_pct
        new_ram = self._reserved_ram_mb + job.cost_ram_mb
        if new_cpu > self.max_cpu_pct:
            return False
        if new_ram > self.max_ram_mb:
            return False
        return True

    def _reserve(self, job: TestGroup) -> None:
        with self._lock:
            self._reserved_cpu_pct += job.cost_cpu_pct
            self._reserved_ram_mb += job.cost_ram_mb
            if job.exclusive:
                self._exclusive_running = True

    def _release(self, job: TestGroup) -> None:
        with self._lock:
            self._reserved_cpu_pct = max(0, self._reserved_cpu_pct - job.cost_cpu_pct)
            self._reserved_ram_mb = max(0, self._reserved_ram_mb - job.cost_ram_mb)
            if job.exclusive:
                self._exclusive_running = False

    def _print_status(self, msg: str) -> None:
        if not self.json_output:
            ts = datetime.now().strftime("%H:%M:%S")
            print(f"[{ts}] {msg}")

    def run(self, jobs: list[TestGroup]) -> list[JobResult]:
        self.out_dir.mkdir(parents=True, exist_ok=True)
        self._monitor.start()
        start_total = time.monotonic()

        # Pre-filter: SKIP jobs whose required tools are missing from PATH.
        # This prevents exit-127 failures and yields a clear verdict.
        skip_results: list[JobResult] = []
        runnable_jobs: list[TestGroup] = []
        for j in jobs:
            missing = j.missing_tool()
            if missing is not None:
                reason = (
                    f"required tool '{missing}' not found in PATH "
                    f"(install it to enable {j.name} tests)"
                )
                self._print_status(f"  SKIP     {j.name:<22} ({reason})")
                skip_results.append(
                    JobResult(
                        name=j.name,
                        status="SKIP",
                        returncode=0,
                        duration_sec=0.0,
                        error=reason,
                    )
                )
            else:
                runnable_jobs.append(j)

        # Auto-infra: bring up compose if needed
        if self.auto_infra and _needs_compose(runnable_jobs):
            self._print_status("Auto-infra: starting docker-compose stack...")
            if not self.dry_run:
                ok = _compose_up(dry_run=False)
                if not ok:
                    self._print_status("WARN: docker-compose up failed, continuing anyway")
            else:
                self._print_status("DRY: docker-compose up (skipped)")

        levels = _topological_levels(runnable_jobs)

        if self.dry_run:
            self._print_status("DRY RUN -- plan:")
            for lvl_idx, level in enumerate(levels):
                names = ", ".join(j.name for j in level)
                self._print_status(f"  Level {lvl_idx}: [{names}]")
            if skip_results:
                skip_names = ", ".join(r.name for r in skip_results)
                self._print_status(f"  SKIPPED (missing tools): [{skip_names}]")
            # Return dry results: DRY for runnable, SKIP for missing-tool jobs
            results = [
                JobResult(name=j.name, status="DRY", returncode=0, duration_sec=0.0)
                for j in runnable_jobs
            ] + skip_results
            self._save_plan(jobs, levels)
            return results

        self._print_status(
            f"Starting {len(runnable_jobs)} job(s) across {len(levels)} level(s), "
            f"parallel={self.parallel}, max_cpu={self.max_cpu_pct}%, max_ram={self.max_ram_mb}MB"
            + (f" (skipping {len(skip_results)} missing-tool job(s))" if skip_results else "")
        )

        all_results: list[JobResult] = list(skip_results)

        with ProcessPoolExecutor(max_workers=self.parallel) as pool:
            for level in levels:
                level_results = self._run_level(level, pool)
                all_results.extend(level_results)

        self._monitor.stop()
        total_duration = time.monotonic() - start_total

        self._print_summary(all_results, total_duration)
        self._save_outputs(jobs, levels, all_results, total_duration)
        return all_results

    def _run_level(
        self, jobs: list[TestGroup], pool: ProcessPoolExecutor
    ) -> list[JobResult]:
        pending = list(jobs)  # jobs not yet submitted
        futures: dict[Future, TestGroup] = {}
        results: list[JobResult] = []

        while pending or futures:
            # Dispatch ready jobs
            dispatched_any = True
            while dispatched_any and pending:
                dispatched_any = False
                for i, job in enumerate(pending):
                    if self._can_dispatch(job):
                        self._reserve(job)
                        future = pool.submit(
                            _run_job_fn, job.name, job.cmd, str(self.out_dir)
                        )
                        futures[future] = job
                        pending.pop(i)
                        self._print_status(
                            f"  STARTED  {job.name:<22} "
                            f"(cpu+{job.cost_cpu_pct}%, ram+{job.cost_ram_mb}MB)"
                        )
                        dispatched_any = True
                        break  # restart scan from beginning

            if not futures:
                # Nothing running, nothing dispatchable — resource deadlock safety
                if pending:
                    # Force dispatch first pending job to avoid stall
                    job = pending.pop(0)
                    self._reserve(job)
                    future = pool.submit(
                        _run_job_fn, job.name, job.cmd, str(self.out_dir)
                    )
                    futures[future] = job
                    self._print_status(
                        f"  STARTED  {job.name:<22} [forced, resource headroom tight]"
                    )
                else:
                    break

            # Wait for at least one to finish
            done_set, _ = futures_wait(futures.keys(), return_when=FIRST_COMPLETED)
            for done_future in done_set:
                job = futures.pop(done_future)
                try:
                    result = done_future.result()
                except Exception as exc:
                    result = JobResult(
                        name=job.name,
                        status="FAIL",
                        returncode=-1,
                        error=str(exc),
                    )
                self._release(job)
                results.append(result)
                status_icon = "PASS" if result.status == "PASS" else "FAIL"
                self._print_status(
                    f"  {status_icon:<6}   {result.name:<22} "
                    f"({result.duration_sec:.1f}s)"
                )

        return results

    def _print_summary(self, results: list[JobResult], total_sec: float) -> None:
        if self.json_output:
            return
        passed = sum(1 for r in results if r.status == "PASS")
        failed = sum(1 for r in results if r.status == "FAIL")
        skipped = sum(1 for r in results if r.status == "SKIP")
        print()
        print("=" * 60)
        print(f"  Test Run Summary")
        print("=" * 60)
        print(f"  Total jobs : {len(results)}")
        print(f"  Passed     : {passed}")
        print(f"  Failed     : {failed}")
        print(f"  Skipped    : {skipped}")
        print(f"  Duration   : {total_sec:.1f}s")
        print("=" * 60)
        if failed > 0:
            print("  FAILED JOBS:")
            for r in results:
                if r.status == "FAIL":
                    print(f"    - {r.name}  (exit {r.returncode})")
                    if r.error:
                        print(f"      error: {r.error}")
        if skipped > 0:
            print("  SKIPPED JOBS:")
            for r in results:
                if r.status == "SKIP":
                    print(f"    - {r.name}: {r.error or 'skipped'}")
        print()
        print(f"  Logs: {self.out_dir}/")

    def _save_plan(self, jobs: list[TestGroup], levels: list[list[TestGroup]]) -> None:
        plan = {
            "jobs": [
                {
                    "name": j.name,
                    "cmd": j.cmd,
                    "needs_infra": j.needs_infra,
                    "cost_cpu": j.cost_cpu,
                    "cost_ram_mb": j.cost_ram_mb,
                    "exclusive": j.exclusive,
                }
                for j in jobs
            ],
            "levels": [[j.name for j in lvl] for lvl in levels],
        }
        plan_path = self.out_dir / "plan.json"
        self.out_dir.mkdir(parents=True, exist_ok=True)
        plan_path.write_text(json.dumps(plan, indent=2), encoding="utf-8")

    def _save_outputs(
        self,
        jobs: list[TestGroup],
        levels: list[list[TestGroup]],
        results: list[JobResult],
        total_sec: float,
    ) -> None:
        self._save_plan(jobs, levels)

        # summary.md
        passed = sum(1 for r in results if r.status == "PASS")
        failed = sum(1 for r in results if r.status == "FAIL")
        ts = datetime.now(timezone.utc).isoformat()
        lines = [
            "# Test Runner Summary",
            "",
            f"Date: {ts}",
            f"Duration: {total_sec:.1f}s",
            f"Jobs: {len(results)}  Passed: {passed}  Failed: {failed}",
            "",
            "## Results",
            "",
            "| Job | Status | Duration | Exit |",
            "|-----|--------|----------|------|",
        ]
        for r in results:
            lines.append(
                f"| {r.name} | {r.status} | {r.duration_sec:.1f}s | {r.returncode} |"
            )
        lines.append("")
        (self.out_dir / "summary.md").write_text("\n".join(lines), encoding="utf-8")

        # latest symlink
        latest = self.out_dir.parent / "latest"
        try:
            if latest.is_symlink():
                latest.unlink()
            latest.symlink_to(self.out_dir.name)
        except OSError:
            pass

        # JSON results for CI
        results_json = {
            "timestamp": ts,
            "duration_sec": total_sec,
            "passed": passed,
            "failed": failed,
            "jobs": [
                {
                    "name": r.name,
                    "status": r.status,
                    "returncode": r.returncode,
                    "duration_sec": r.duration_sec,
                    "error": r.error,
                }
                for r in results
            ],
        }
        (self.out_dir / "results.json").write_text(
            json.dumps(results_json, indent=2), encoding="utf-8"
        )


# ---------------------------------------------------------------------------
# CLI
# ---------------------------------------------------------------------------

def _build_parser() -> argparse.ArgumentParser:
    p = argparse.ArgumentParser(
        description="test-runner.py -- parallel test runner with resource throttling",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog=__doc__,
    )
    sel = p.add_mutually_exclusive_group()
    sel.add_argument("--group", metavar="NAME", help="Run a single group by name")
    sel.add_argument(
        "--groups",
        metavar="A,B,C",
        help="Comma-separated list of group names",
    )
    sel.add_argument("--composite", metavar="NAME", help="Run a named composite")
    sel.add_argument("--list", action="store_true", help="List all groups and composites")

    p.add_argument(
        "--parallel",
        type=int,
        default=0,
        metavar="N",
        help="Max concurrent jobs (0 = auto)",
    )
    p.add_argument(
        "--auto-parallel",
        action="store_true",
        help="Auto-detect parallelism from CPU count",
    )
    p.add_argument(
        "--max-cpu",
        type=int,
        default=80,
        metavar="PCT",
        help="Max CPU reservation percent across all running jobs (default: 80)",
    )
    p.add_argument(
        "--max-ram",
        type=int,
        default=0,
        metavar="MB",
        help="Max RAM reservation in MB (default: 80%% of total)",
    )
    p.add_argument(
        "--auto-infra",
        action="store_true",
        help="Auto docker-compose up if any job needs it",
    )
    p.add_argument(
        "--dry-run",
        action="store_true",
        help="Print plan without executing",
    )
    p.add_argument(
        "--out-dir",
        metavar="PATH",
        help="Output directory (default: out/test-runner/<ts>/)",
    )
    p.add_argument("--json", action="store_true", help="JSON output for CI")
    p.add_argument("--watch", action="store_true", help="Live dashboard (refresh every 1s)")
    return p


def _resolve_jobs(
    args: argparse.Namespace,
    groups: dict[str, Any],
    composites: dict[str, list[str]],
) -> list[TestGroup]:
    names: list[str] = []

    if args.group:
        names = [args.group]
    elif args.groups:
        names = [n.strip() for n in args.groups.split(",") if n.strip()]
    elif args.composite:
        c = args.composite
        if c not in composites:
            print(f"ERROR: Unknown composite '{c}'. Available: {', '.join(sorted(composites))}", file=sys.stderr)
            sys.exit(1)
        names = composites[c]
    else:
        names = list(groups.keys())

    result: list[TestGroup] = []
    for n in names:
        if n not in groups:
            print(f"ERROR: Unknown group '{n}'. Run --list to see available groups.", file=sys.stderr)
            sys.exit(1)
        result.append(TestGroup.from_dict(n, groups[n]))
    return result


def cmd_list(groups: dict[str, Any], composites: dict[str, list[str]]) -> None:
    print("Available test groups:")
    print()
    total_ram = get_total_ram_mb()
    print(f"  {'Name':<22} {'Infra':<10} {'CPU':<8} {'RAM':<8} {'~Sec':<6} {'Exclusive'}")
    print(f"  {'-'*22} {'-'*10} {'-'*8} {'-'*8} {'-'*6} {'-'*9}")
    for name, props in groups.items():
        g = TestGroup.from_dict(name, props)
        excl = "yes" if g.exclusive else "no"
        print(
            f"  {name:<22} {g.needs_infra:<10} {g.cost_cpu:<8} "
            f"{g.cost_ram_mb:<8} {g.duration_estimate_sec:<6} {excl}"
        )
    print()
    print("Available composites:")
    print()
    for name, members in composites.items():
        print(f"  {name:<20} [{', '.join(members)}]")
    print()
    print(f"System: {get_cpu_count()} CPUs, {total_ram} MB RAM total")


def main() -> int:
    parser = _build_parser()
    args = parser.parse_args()

    try:
        groups, composites = load_test_groups()
    except FileNotFoundError:
        print(f"ERROR: {GROUPS_FILE} not found.", file=sys.stderr)
        return 1

    if args.list or (
        not args.group and not args.groups and not args.composite
        and not args.dry_run
    ):
        cmd_list(groups, composites)
        return 0

    jobs = _resolve_jobs(args, groups, composites)

    # Parallelism
    cpu_count = get_cpu_count()
    total_ram = get_total_ram_mb()

    if args.auto_parallel or args.parallel == 0:
        parallel = max(1, cpu_count - 1)
    else:
        parallel = args.parallel

    max_ram = args.max_ram if args.max_ram > 0 else int(total_ram * 0.80)

    # Output dir
    ts = datetime.now().strftime("%Y%m%dT%H%M%S")
    if args.out_dir:
        out_dir = Path(args.out_dir)
    else:
        out_dir = DEFAULT_OUT_DIR / ts

    runner = TestRunner(
        parallel=parallel,
        max_cpu_pct=args.max_cpu,
        max_ram_mb=max_ram,
        dry_run=args.dry_run,
        out_dir=out_dir,
        auto_infra=args.auto_infra,
        watch=args.watch,
        json_output=args.json,
    )

    results = runner.run(jobs)

    if args.json:
        passed = sum(1 for r in results if r.status == "PASS")
        failed = sum(1 for r in results if r.status == "FAIL")
        print(json.dumps({
            "passed": passed,
            "failed": failed,
            "total": len(results),
            "jobs": [
                {"name": r.name, "status": r.status, "duration_sec": r.duration_sec}
                for r in results
            ],
        }, indent=2))

    if any(r.status == "FAIL" for r in results):
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
