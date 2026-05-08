#!/usr/bin/env python3
"""Repo-scoped process inventory and safe termination helper.

This intentionally replaces ad-hoc commands like:
  ps ax | grep gradle | grep <repo>

Defaults are conservative:
- `status` only shows processes related to this repository.
- `stop` is dry-run unless `--yes` is passed.
- global Gradle daemons are only included when explicitly requested.
"""
from __future__ import annotations

import argparse
import json
import os
import signal
import subprocess
import sys
from dataclasses import dataclass, asdict
from pathlib import Path
from typing import Iterable

REPO_ROOT = Path(__file__).resolve().parents[1]
SELF_PID = os.getpid()

INTERESTING_TOKENS = (
    "test-runner.py",
    "gradle-wrapper.jar",
    "GradleDaemon",
    "org.gradle.wrapper.GradleWrapperMain",
    "npm test",
    "go test",
    "docker compose",
    "./nx test",
)

CLASSIFIERS = (
    ("nx-test", ("./nx test", "bash ./nx test")),
    ("test-runner", ("scripts/test-runner.py", "test-runner.py")),
    ("gradle-wrapper", ("gradle-wrapper.jar", "org.gradle.wrapper.GradleWrapperMain")),
    ("gradle-daemon", ("GradleDaemon", "org.gradle.launcher.daemon.bootstrap.GradleDaemon")),
    ("npm", ("npm test", " jest ", "ts-jest")),
    ("go-test", ("go test",)),
    ("docker-compose", ("docker compose", "docker-compose")),
)


@dataclass(frozen=True)
class Proc:
    pid: int
    ppid: int
    stat: str
    etime: str
    kind: str
    scope: str
    command: str


def run_ps() -> list[tuple[int, int, str, str, str]]:
    fixture = os.environ.get("NX_PROC_GUARD_PS_FIXTURE")
    if fixture == "empty":
        return []
    if fixture:
        text = Path(fixture).read_text(encoding="utf-8")
        return parse_ps_output(text)

    proc = subprocess.run(
        ["ps", "ax", "-o", "pid=,ppid=,stat=,etime=,command="],
        check=True,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
    )
    return parse_ps_output(proc.stdout)


def parse_ps_output(output: str) -> list[tuple[int, int, str, str, str]]:
    rows: list[tuple[int, int, str, str, str]] = []
    for line in output.splitlines():
        parts = line.strip().split(None, 4)
        if len(parts) < 5:
            continue
        try:
            pid = int(parts[0])
            ppid = int(parts[1])
        except ValueError:
            continue
        rows.append((pid, ppid, parts[2], parts[3], parts[4]))
    return rows


def classify(command: str) -> str:
    for kind, needles in CLASSIFIERS:
        if any(n in command for n in needles):
            return kind
    return "other"


def is_interesting(command: str) -> bool:
    return any(token in command for token in INTERESTING_TOKENS)


def collect(include_global_gradle_daemons: bool = False) -> list[Proc]:
    repo = str(REPO_ROOT)
    rows = run_ps()
    by_pid = {pid: (pid, ppid, stat, etime, command) for pid, ppid, stat, etime, command in rows}

    repo_pids: set[int] = set()
    global_daemon_pids: set[int] = set()

    for pid, _ppid, _stat, _etime, command in rows:
        if pid == SELF_PID:
            continue
        if repo in command or f"cd {repo}" in command:
            repo_pids.add(pid)
        elif "GradleDaemon" in command:
            global_daemon_pids.add(pid)

    # Include descendants of repo-scoped processes. This catches Java daemons
    # while their Gradle wrapper parent is still alive, without matching every
    # Java process on the machine.
    changed = True
    while changed:
        changed = False
        for pid, ppid, _stat, _etime, _command in rows:
            if pid not in repo_pids and ppid in repo_pids:
                repo_pids.add(pid)
                changed = True

    selected: set[int] = set(repo_pids)
    if include_global_gradle_daemons:
        selected.update(global_daemon_pids)

    procs: list[Proc] = []
    for pid in sorted(selected):
        row = by_pid.get(pid)
        if not row:
            continue
        _pid, ppid, stat, etime, command = row
        kind = classify(command)
        if pid in repo_pids:
            scope = "repo"
        elif pid in global_daemon_pids:
            scope = "global-gradle-daemon"
        else:
            scope = "unknown"
        if kind == "other" and not is_interesting(command):
            continue
        procs.append(Proc(pid, ppid, stat, etime, kind, scope, command))
    return procs


def short(command: str, width: int) -> str:
    if width <= 0 or len(command) <= width:
        return command
    return command[: max(0, width - 1)] + "…"


def print_table(procs: Iterable[Proc], truncate: int) -> None:
    rows = list(procs)
    if not rows:
        print("No matching repo-scoped processes found.")
        return
    print(f"{'PID':>7} {'PPID':>7} {'STAT':<6} {'ELAPSED':>10} {'KIND':<16} {'SCOPE':<21} COMMAND")
    print(f"{'-'*7} {'-'*7} {'-'*6} {'-'*10} {'-'*16} {'-'*21} {'-'*40}")
    for p in rows:
        print(
            f"{p.pid:>7} {p.ppid:>7} {p.stat:<6} {p.etime:>10} "
            f"{p.kind:<16} {p.scope:<21} {short(p.command, truncate)}"
        )


def parse_signal(value: str) -> signal.Signals:
    raw = value.upper().removeprefix("SIG")
    try:
        if raw.isdigit():
            return signal.Signals(int(raw))
        return getattr(signal, f"SIG{raw}")
    except (AttributeError, ValueError) as exc:
        raise argparse.ArgumentTypeError(f"invalid signal: {value}") from exc


def cmd_status(args: argparse.Namespace) -> int:
    procs = collect(include_global_gradle_daemons=args.include_gradle_daemons)
    if args.json:
        print(json.dumps([asdict(p) for p in procs], indent=2))
    else:
        print_table(procs, args.truncate)
        if not args.include_gradle_daemons:
            print("\nTip: add --include-gradle-daemons to also show global Gradle daemons.")
    return 0


def cmd_stop(args: argparse.Namespace) -> int:
    procs = collect(include_global_gradle_daemons=args.include_gradle_daemons)
    # Do not let this helper terminate itself or its direct shell accidentally.
    procs = [p for p in procs if p.pid not in {SELF_PID, os.getppid()}]
    try:
        sig = parse_signal(args.signal)
    except argparse.ArgumentTypeError as exc:
        print(str(exc), file=sys.stderr)
        return 2

    if args.only_kind:
        allowed = set(args.only_kind)
        procs = [p for p in procs if p.kind in allowed]

    if not procs:
        print("No matching processes to stop.")
        return 0

    print_table(procs, args.truncate)
    if not args.yes:
        print(f"\nDry-run: would send {sig.name} to {len(procs)} process(es). Re-run with --yes to execute.")
        return 0

    failures = 0
    for p in procs:
        try:
            os.kill(p.pid, sig)
            print(f"sent {sig.name} pid={p.pid} kind={p.kind} scope={p.scope}")
        except ProcessLookupError:
            print(f"already exited pid={p.pid}")
        except PermissionError as exc:
            failures += 1
            print(f"permission denied pid={p.pid}: {exc}", file=sys.stderr)
    return 1 if failures else 0


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Repo-scoped process guard for nx/test/Gradle runs")
    sub = parser.add_subparsers(dest="command", required=True)

    def common(p: argparse.ArgumentParser) -> None:
        p.add_argument("--include-gradle-daemons", action="store_true", help="include global Gradle daemon processes")
        p.add_argument("--truncate", type=int, default=140, help="max command width; 0 disables truncation")

    status = sub.add_parser("status", help="show matching processes")
    common(status)
    status.add_argument("--json", action="store_true", help="emit JSON")
    status.set_defaults(func=cmd_status)

    stop = sub.add_parser("stop", help="safely stop matching processes; dry-run by default")
    common(stop)
    stop.add_argument("--signal", default="TERM", help="signal name/number; default TERM")
    stop.add_argument("--yes", action="store_true", help="actually send the signal")
    stop.add_argument("--only-kind", action="append", choices=[k for k, _ in CLASSIFIERS] + ["other"], help="limit to one kind; repeatable")
    stop.set_defaults(func=cmd_stop)

    return parser


def main() -> int:
    parser = build_parser()
    args = parser.parse_args()
    return args.func(args)


if __name__ == "__main__":
    raise SystemExit(main())
