#!/usr/bin/env python3
"""
debug-runner.py — debug sub-commands for service investigation.

Sub-commands:
  logs      -- tail / filter service logs
  diagnose  -- heuristic root cause analysis
  snapshot  -- forensic bundle
  trace     -- OpenObserve trace lookup
  probe     -- connection probe between services

Usage:
  python3 debug-runner.py logs [--service NAME] [--since 5m] [--grep PATTERN]
                               [--errors] [--correlation-id ID]
  python3 debug-runner.py diagnose
  python3 debug-runner.py snapshot
  python3 debug-runner.py trace <trace-id>
  python3 debug-runner.py probe
"""

import argparse
import datetime
import json
import os
import re
import shutil
import subprocess
import sys
import urllib.request
import urllib.error

# ---------------------------------------------------------------------------
# Root detection
# ---------------------------------------------------------------------------
_HERE = os.path.dirname(os.path.abspath(__file__))
REPO_ROOT = os.path.abspath(os.path.join(_HERE, "..", ".."))

# ---------------------------------------------------------------------------
# Output helpers
# ---------------------------------------------------------------------------

def _info(msg):    print(f"[debug] {msg}")
def _ok(msg):      print(f"[debug] OK: {msg}")
def _warn(msg):    print(f"[debug] WARN: {msg}", file=sys.stderr)
def _err(msg):     print(f"[debug] ERROR: {msg}", file=sys.stderr)


# ---------------------------------------------------------------------------
# Docker compose helpers
# ---------------------------------------------------------------------------

def _compose_files():
    """Return list of -f args for the main compose setup."""
    main = os.path.join(REPO_ROOT, "compose", "docker-compose.yml")
    files = []
    if os.path.isfile(main):
        files.extend(["-f", main])
    return files


def _run(cmd: list, capture: bool = True, timeout: int = 30) -> str:
    """Run a subprocess, return stdout as string or empty on failure."""
    try:
        result = subprocess.run(
            cmd,
            capture_output=capture,
            text=True,
            timeout=timeout,
        )
        if capture:
            return result.stdout or ""
        return ""
    except (subprocess.TimeoutExpired, FileNotFoundError, OSError):
        return ""


def _docker_available() -> bool:
    return shutil.which("docker") is not None


def _compose_ps() -> str:
    if not _docker_available():
        return ""
    cmd = ["docker", "compose"] + _compose_files() + ["ps", "-a"]
    return _run(cmd)


def _list_services() -> list:
    """Return list of service names from docker compose config."""
    if not _docker_available():
        return []
    cmd = ["docker", "compose"] + _compose_files() + ["config", "--services"]
    output = _run(cmd)
    return [s.strip() for s in output.splitlines() if s.strip()]


def _service_logs(service: str, tail: int = 200) -> str:
    if not _docker_available():
        return ""
    cmd = ["docker", "compose"] + _compose_files() + ["logs", "--no-color", f"--tail={tail}", service]
    return _run(cmd, timeout=15)


def _docker_stats() -> str:
    if not _docker_available():
        return ""
    return _run(["docker", "stats", "--no-stream", "--format",
                 "table {{.Name}}\t{{.CPUPerc}}\t{{.MemUsage}}\t{{.MemPerc}}"])


# ---------------------------------------------------------------------------
# Time parsing
# ---------------------------------------------------------------------------

def _since_to_docker_flag(since: str) -> str:
    """Convert '5m' -> '--since=5m' for docker logs."""
    return f"--since={since}"


# ---------------------------------------------------------------------------
# Heuristic patterns
# ---------------------------------------------------------------------------

LOG_PATTERNS = [
    (r"OutOfMemoryError|java\.lang\.OutOfMemoryError", "HIGH",
     "memory exhausted",
     "increase mem_limit in compose file for this service"),
    (r"NXDOMAIN|no such host|UnknownHostException", "HIGH",
     "DNS/network resolution failure",
     "check 'networks' section; ensure services share the same docker network"),
    (r"Connection refused|EHOSTUNREACH", "HIGH",
     "service unreachable",
     "run: docker compose up <service> — check if dependency is healthy"),
    (r"Timed out after waiting|SocketTimeoutException", "MEDIUM",
     "service responding slowly",
     "increase healthcheck retries or timeout values in compose file"),
    (r"panic:|Caused by:", "HIGH",
     "application crash / unhandled exception",
     "check full stack trace in logs above this line"),
    (r"ERROR|FATAL", "LOW",
     "application error",
     "review full log context"),
]

RESOURCE_CPU_THRESHOLD = 80.0
RESOURCE_MEM_THRESHOLD = 90.0


# ---------------------------------------------------------------------------
# Command: logs
# ---------------------------------------------------------------------------

def cmd_logs(args):
    if not _docker_available():
        _warn("docker not found — cannot tail logs")
        return

    services = [args.service] if args.service else _list_services()
    if not services:
        _warn("No services found. Is compose configured?")
        return

    for svc in services:
        _info(f"Logs for: {svc}")
        cmd = ["docker", "compose"] + _compose_files() + ["logs", "--no-color", f"--tail={args.tail}", svc]
        if args.since:
            cmd.append(_since_to_docker_flag(args.since))

        try:
            output = _run(cmd, timeout=20)
        except Exception as e:
            _warn(f"Could not get logs for {svc}: {e}")
            continue

        lines = output.splitlines()

        # Filter: --errors
        if args.errors:
            lines = [l for l in lines if re.search(r"ERROR|FATAL|WARN|Exception|panic", l, re.IGNORECASE)]

        # Filter: --grep
        if args.grep:
            lines = [l for l in lines if re.search(args.grep, l, re.IGNORECASE)]

        # Filter: --correlation-id
        if args.correlation_id:
            lines = [l for l in lines if args.correlation_id in l]

        for line in lines:
            print(f"  {line}")

        if not lines:
            print(f"  (no matching lines for {svc})")


# ---------------------------------------------------------------------------
# Command: diagnose
# ---------------------------------------------------------------------------

def cmd_diagnose(args):
    print("\nDiagnosis report")
    print("=" * 60)

    # 1. Service status
    ps_output = _compose_ps()
    if not ps_output:
        print("docker compose: not running or docker unavailable\n")
    else:
        services = _list_services()
        total = len(services)
        healthy = 0
        unhealthy = 0
        for line in ps_output.splitlines():
            if "healthy" in line.lower() and "unhealthy" not in line.lower():
                healthy += 1
            elif "unhealthy" in line.lower() or "exit" in line.lower():
                unhealthy += 1
        running = total - unhealthy
        print(f"docker compose: {running}/{total} up (healthy: {healthy}, unhealthy: {unhealthy})")

    # 2. Log error patterns
    services = _list_services()
    diagnoses = []

    for svc in services:
        logs = _service_logs(svc, tail=200)
        if not logs:
            continue
        lines = logs.splitlines()[-200:]
        for pattern, severity, cause, fix in LOG_PATTERNS:
            matches = [l for l in lines if re.search(pattern, l, re.IGNORECASE)]
            if matches:
                count = len(matches)
                diagnoses.append({
                    "service": svc,
                    "severity": severity,
                    "pattern": pattern,
                    "cause": cause,
                    "fix": fix,
                    "count": count,
                    "sample": matches[0][:100],
                })
                break  # one diagnosis per service (highest priority first)

    # 3. Resource pressure
    stats_output = _docker_stats()
    resource_issues = []
    if stats_output:
        for line in stats_output.splitlines()[1:]:
            parts = line.split()
            if len(parts) < 4:
                continue
            name = parts[0]
            try:
                cpu = float(parts[1].replace("%", ""))
                mem_pct = float(parts[3].replace("%", ""))
                if cpu > RESOURCE_CPU_THRESHOLD:
                    resource_issues.append(f"{name}: CPU {cpu:.0f}% (>{RESOURCE_CPU_THRESHOLD}%)")
                if mem_pct > RESOURCE_MEM_THRESHOLD:
                    resource_issues.append(f"{name}: MEM {mem_pct:.0f}% (>{RESOURCE_MEM_THRESHOLD}%)")
            except (ValueError, IndexError):
                continue

    # 4. OpenObserve telemetry sanity
    oo_status = _check_openobserve()

    # 5. Disk/memory
    disk_info = _host_disk_info()
    mem_info = _host_mem_info()

    # Print summary
    if diagnoses:
        for d in diagnoses:
            marker = "X" if d["severity"] == "HIGH" else "!"
            print(f"{marker} {d['service']}: {d['count']} '{d['pattern'][:30]}' in last logs -- {d['cause']}")
    else:
        if services:
            print("No error patterns found in recent logs")

    for ri in resource_issues:
        print(f"! RESOURCE: {ri}")

    if disk_info:
        print(f"  disk: {disk_info}")
    if mem_info:
        print(f"  memory: {mem_info}")
    if oo_status:
        print(f"  OpenObserve: {oo_status}")

    # Ranked diagnoses
    sorted_diag = sorted(diagnoses, key=lambda d: {"HIGH": 0, "MEDIUM": 1, "LOW": 2}.get(d["severity"], 3))
    if sorted_diag:
        print(f"\nRanked diagnoses ({len(sorted_diag)}):")
        for i, d in enumerate(sorted_diag, 1):
            print(f"  {i}. [{d['severity']}] {d['service']}: {d['cause']}")
            print(f"     Suggested: {d['fix']}")
            if d["severity"] == "HIGH":
                print(f"     Fix: docker compose restart {d['service']}")
    else:
        if not services:
            print("\nNo compose services found. Is compose running?")
        else:
            print("\nNo diagnoses to report. System appears healthy.")

    print()


def _check_openobserve() -> str:
    """Query OpenObserve for traces in the last hour. Returns status string."""
    url = "http://localhost:5080/api/default/_search"
    try:
        req = urllib.request.Request(url, method="GET")
        req.add_header("Accept", "application/json")
        with urllib.request.urlopen(req, timeout=3) as resp:
            data = json.loads(resp.read())
            hits = data.get("hits", {}).get("total", {})
            count = hits.get("value", 0) if isinstance(hits, dict) else int(hits or 0)
            if count == 0:
                return "receiving 0 traces (last hour) — telemetry may be broken"
            return f"receiving traces ({count} in last hour)"
    except Exception:
        return "not reachable (OpenObserve down or not in profile)"


def _host_disk_info() -> str:
    """Return disk usage percentage of /."""
    try:
        import shutil as _shutil
        usage = _shutil.disk_usage("/")
        pct = usage.used * 100 // usage.total
        return f"{pct}% used"
    except Exception:
        return ""


def _host_mem_info() -> str:
    """Return memory usage if /proc/meminfo is available (Linux) or vm_stat (macOS)."""
    # macOS
    if sys.platform == "darwin":
        output = _run(["vm_stat"])
        if output:
            pages_free = 0
            pages_active = 0
            page_size = 4096
            for line in output.splitlines():
                m = re.match(r"Pages free:\s+(\d+)", line)
                if m:
                    pages_free = int(m.group(1))
                m = re.match(r"Pages active:\s+(\d+)", line)
                if m:
                    pages_active = int(m.group(1))
            used_gb = (pages_active * page_size) / (1024 ** 3)
            return f"{used_gb:.1f} GB active"
        return ""
    # Linux
    try:
        with open("/proc/meminfo") as f:
            lines = f.readlines()
        mem_total = mem_free = mem_avail = 0
        for line in lines:
            if line.startswith("MemTotal:"):
                mem_total = int(line.split()[1]) // 1024
            elif line.startswith("MemAvailable:"):
                mem_avail = int(line.split()[1]) // 1024
        used = mem_total - mem_avail
        pct = used * 100 // mem_total if mem_total else 0
        return f"{used}/{mem_total} MB ({pct}%)"
    except Exception:
        return ""


# ---------------------------------------------------------------------------
# Command: snapshot
# ---------------------------------------------------------------------------

def cmd_snapshot(args):
    ts = datetime.datetime.utcnow().strftime("%Y%m%dT%H%M%SZ")
    out_dir = os.path.join(REPO_ROOT, "out", "debug", ts)
    os.makedirs(out_dir, exist_ok=True)

    start = datetime.datetime.utcnow()
    _info(f"Taking forensic snapshot -> {out_dir}")

    # compose-ps.txt
    ps = _compose_ps()
    _write(out_dir, "compose-ps.txt", ps or "(docker not available or no compose running)")

    # docker-stats.txt
    stats = _docker_stats()
    _write(out_dir, "docker-stats.txt", stats or "(no stats available)")

    # logs per service
    for svc in _list_services():
        logs = _service_logs(svc, tail=200)
        _write(out_dir, f"logs-{svc}.log", logs or "(no logs)")

    # host-info.txt
    host_info = _collect_host_info()
    _write(out_dir, "host-info.txt", host_info)

    # openobserve-summary.json
    oo = _openobserve_summary()
    _write(out_dir, "openobserve-summary.json", json.dumps(oo, indent=2))

    # meta.json
    duration = (datetime.datetime.utcnow() - start).total_seconds()
    meta = {
        "ts": ts,
        "mode": "compose",
        "duration_seconds": round(duration, 2),
        "services": _list_services(),
    }
    _write(out_dir, "meta.json", json.dumps(meta, indent=2))

    # Update latest symlink
    latest = os.path.join(REPO_ROOT, "out", "debug", "latest")
    if os.path.islink(latest):
        os.unlink(latest)
    os.symlink(ts, latest)

    _ok(f"Snapshot saved: {out_dir}")
    return out_dir


def _write(directory: str, filename: str, content: str):
    path = os.path.join(directory, filename)
    with open(path, "w") as fh:
        fh.write(content)


def _collect_host_info() -> str:
    lines = []
    for cmd in [["uname", "-a"], ["uptime"]]:
        out = _run(cmd)
        if out:
            lines.append(out.strip())
    lines.append(_host_mem_info() or "(no mem info)")
    lines.append(_host_disk_info() or "(no disk info)")
    return "\n".join(lines)


def _openobserve_summary() -> dict:
    url = "http://localhost:5080/api/default/_search"
    try:
        req = urllib.request.Request(url, method="GET")
        with urllib.request.urlopen(req, timeout=3) as resp:
            data = json.loads(resp.read())
            return {"status": "ok", "response": data}
    except Exception as e:
        return {"status": "unavailable", "error": str(e)}


# ---------------------------------------------------------------------------
# Command: trace
# ---------------------------------------------------------------------------

def cmd_trace(args):
    trace_id = args.trace_id
    if not trace_id:
        _err("Usage: debug-runner.py trace <trace-id>")
        sys.exit(1)

    _info(f"Looking up trace: {trace_id}")
    url = f"http://localhost:5080/api/default/_search?trace_id={trace_id}"
    try:
        req = urllib.request.Request(url, method="GET")
        req.add_header("Accept", "application/json")
        with urllib.request.urlopen(req, timeout=5) as resp:
            data = json.loads(resp.read())

        hits = data.get("hits", {}).get("hits", [])
        if not hits:
            print(f"No spans found for trace ID: {trace_id}")
            return

        print(f"\nTrace {trace_id} — {len(hits)} span(s):\n")
        # Sort by timestamp
        spans = sorted(hits, key=lambda h: h.get("_source", {}).get("@timestamp", ""))
        for span in spans:
            src = span.get("_source", {})
            ts = src.get("@timestamp", "?")
            svc = src.get("service_name", src.get("service", "?"))
            op = src.get("operation_name", src.get("span_name", "?"))
            dur = src.get("duration", "?")
            status = src.get("status", "")
            print(f"  {ts}  [{svc}]  {op}  {dur}ms  {status}")

        ui_url = f"http://localhost:5080/traces/{trace_id}"
        print(f"\nUI: {ui_url}")

    except urllib.error.URLError:
        print("OpenObserve not reachable. Is it running?")
    except Exception as e:
        _err(f"Trace lookup failed: {e}")


# ---------------------------------------------------------------------------
# Command: probe
# ---------------------------------------------------------------------------

def cmd_probe(args):
    """Probe DNS connectivity between all Java service pairs."""
    if not _docker_available():
        _warn("docker not available — cannot probe")
        return

    services = _list_services()
    if not services:
        _warn("No services found in compose config.")
        return

    # Heuristic: java services are those with 'app', 'engine', 'service' in name
    java_services = [s for s in services
                     if any(kw in s.lower() for kw in ("app", "engine", "service", "api", "controller", "usecase"))]

    if not java_services:
        java_services = services[:3]  # fallback: probe first 3

    print(f"\nProbing DNS between {len(java_services)} service(s):\n")
    any_fail = False

    for source in java_services:
        for target in java_services:
            if source == target:
                continue
            cmd = [
                "docker", "compose"
            ] + _compose_files() + [
                "exec", "-T", source,
                "sh", "-c", f"getent hosts {target} 2>&1 || nslookup {target} 2>&1 || echo NXDOMAIN"
            ]
            output = _run(cmd, timeout=5)
            if not output or "NXDOMAIN" in output or "not found" in output.lower():
                print(f"  FAIL  {source} -> {target}: NXDOMAIN / unreachable")
                print(f"        Suggested: verify both services share the same docker network")
                any_fail = True
            else:
                print(f"  OK    {source} -> {target}")

    if not any_fail:
        print("\nAll DNS probes passed.")
    else:
        print("\nSome probes failed. Check docker network configuration.")


# ---------------------------------------------------------------------------
# CLI
# ---------------------------------------------------------------------------

def main():
    parser = argparse.ArgumentParser(
        description="Debug runner — logs, diagnose, snapshot, trace, probe",
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )
    sub = parser.add_subparsers(dest="command", metavar="COMMAND")

    # logs
    logs_p = sub.add_parser("logs", help="Tail and filter service logs")
    logs_p.add_argument("--service", metavar="NAME", help="Only this service")
    logs_p.add_argument("--since", metavar="DURATION", help="e.g. 5m, 1h")
    logs_p.add_argument("--grep", metavar="PATTERN", help="Filter lines matching regex")
    logs_p.add_argument("--errors", action="store_true",
                        help="Only ERROR|FATAL|WARN|Exception|panic lines")
    logs_p.add_argument("--correlation-id", metavar="ID", dest="correlation_id",
                        help="Filter lines containing this correlation/trace ID")
    logs_p.add_argument("--tail", type=int, default=100,
                        help="Number of lines to tail per service (default: 100)")

    # diagnose
    sub.add_parser("diagnose", help="Heuristic root cause analysis")

    # snapshot
    sub.add_parser("snapshot", help="Collect forensic bundle to out/debug/<ts>/")

    # trace
    trace_p = sub.add_parser("trace", help="Look up a trace in OpenObserve")
    trace_p.add_argument("trace_id", metavar="TRACE-ID", nargs="?", help="Trace ID to look up")

    # probe
    sub.add_parser("probe", help="DNS connectivity probe between services")

    args = parser.parse_args()

    if not args.command:
        parser.print_help()
        sys.exit(0)

    dispatch = {
        "logs": cmd_logs,
        "diagnose": cmd_diagnose,
        "snapshot": cmd_snapshot,
        "trace": cmd_trace,
        "probe": cmd_probe,
    }
    dispatch[args.command](args)


if __name__ == "__main__":
    main()
