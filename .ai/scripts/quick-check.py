#!/usr/bin/env python3
"""
quick-check.py — sub-second live-demo guardrail.

This check is intentionally not a Gradle/JUnit suite. It validates the things a
reviewer needs before a live walkthrough:

1. whether core Java artifacts from the last build exist and look fresh
   (warning only; `quick` must never hide a Gradle build);
2. the most important Clean Architecture boundaries are still true at source
   level;
3. the distributed Vert.x modules do not directly import each other.

If the artifact snapshot warns, run `./nx build` before a runtime demo. Full
bytecode-level validation remains in `./nx test --composite ci-fast`.
"""
from __future__ import annotations

import re
import sys
from dataclasses import dataclass
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]


@dataclass(frozen=True)
class Violation:
    file: Path
    message: str

    def render(self) -> str:
        try:
            rel = self.file.relative_to(REPO_ROOT)
        except ValueError:
            rel = self.file
        return f"{rel}: {self.message}"


def _java_files(root: Path) -> list[Path]:
    if not root.exists():
        return []
    return sorted(root.rglob("*.java"))


def _newest_mtime(paths: list[Path]) -> float:
    return max((p.stat().st_mtime for p in paths if p.exists()), default=0.0)


def _oldest_mtime(paths: list[Path]) -> float:
    existing = [p.stat().st_mtime for p in paths if p.exists()]
    return min(existing) if existing else 0.0


def check_artifacts_snapshot() -> list[Violation]:
    """Warn when the last build snapshot is missing/stale without invoking Gradle."""
    checks = [
        (
            REPO_ROOT / "poc/java-risk-engine/src/main/java",
            REPO_ROOT / "poc/java-risk-engine/build/classes/java/main",
            "risk-engine compiled classes are missing/stale; run ./nx build before runtime demo",
        ),
        (
            REPO_ROOT / "pkg/risk-domain/src/main/java",
            REPO_ROOT / "pkg/risk-domain/build/classes/java/main",
            "risk-domain compiled classes are missing/stale; run ./nx build before runtime demo",
        ),
    ]
    violations: list[Violation] = []
    for src_dir, classes_dir, msg in checks:
        srcs = _java_files(src_dir)
        classes = sorted(classes_dir.rglob("*.class")) if classes_dir.exists() else []
        if not srcs:
            violations.append(Violation(src_dir, "expected Java sources were not found"))
            continue
        if not classes:
            violations.append(Violation(classes_dir, msg))
            continue
        if _oldest_mtime(classes) < _newest_mtime(srcs):
            violations.append(Violation(classes_dir, msg))
    return violations


IMPORT_RE = re.compile(r"^\s*import\s+([^;]+);", re.MULTILINE)


def _imports(java_file: Path) -> list[str]:
    return IMPORT_RE.findall(java_file.read_text(encoding="utf-8", errors="ignore"))


def check_clean_arch_source_boundaries() -> list[Violation]:
    base = REPO_ROOT / "poc/java-risk-engine/src/main/java/io/riskplatform/engine"
    violations: list[Violation] = []

    for f in _java_files(base / "domain"):
        for imp in _imports(f):
            if imp.startswith("io.riskplatform.engine.application."):
                violations.append(Violation(f, f"domain imports application: {imp}"))
            if imp.startswith("io.riskplatform.engine.infrastructure."):
                violations.append(Violation(f, f"domain imports infrastructure: {imp}"))

    for f in _java_files(base / "application"):
        for imp in _imports(f):
            if imp.startswith("io.riskplatform.engine.infrastructure."):
                violations.append(Violation(f, f"application imports infrastructure: {imp}"))

    service = base / "application/usecase/risk/EvaluateTransactionRiskService.java"
    if service.exists():
        text = service.read_text(encoding="utf-8", errors="ignore")
        if "application.port.out.CircuitBreakerPort" not in text:
            violations.append(Violation(service, "use case must depend on CircuitBreakerPort"))
        if "infrastructure.resilience.CircuitBreaker" in text:
            violations.append(Violation(service, "use case imports concrete CircuitBreaker"))

    return violations


def check_distributed_source_boundaries() -> list[Violation]:
    root = REPO_ROOT / "poc/java-vertx-distributed"
    modules = {
        "controller-app": "io.riskplatform.distributed.controller.",
        "usecase-app": "io.riskplatform.distributed.usecase.",
        "repository-app": "io.riskplatform.distributed.repository.",
        "consumer-app": "io.riskplatform.distributed.consumer.",
    }
    violations: list[Violation] = []
    for module, own_pkg in modules.items():
        forbidden = [pkg for other, pkg in modules.items() if other != module]
        for f in _java_files(root / module / "src/main/java"):
            for imp in _imports(f):
                if any(imp.startswith(pkg) for pkg in forbidden):
                    violations.append(Violation(f, f"{module} imports another concrete module: {imp}"))

    for f in _java_files(root / "shared/src/main/java"):
        for imp in _imports(f):
            if any(imp.startswith(pkg) for pkg in modules.values()):
                violations.append(Violation(f, f"shared imports concrete module: {imp}"))

    return violations


def main() -> int:
    checks = [
        ("clean-arch source boundaries", check_clean_arch_source_boundaries),
        ("distributed source boundaries", check_distributed_source_boundaries),
    ]

    all_violations: list[Violation] = []
    artifact_warnings = check_artifacts_snapshot()
    if artifact_warnings:
        print(f"WARN artifact snapshot: {len(artifact_warnings)} warning(s)")
    else:
        print("OK   artifact snapshot")

    for name, fn in checks:
        violations = fn()
        if violations:
            print(f"FAIL {name}: {len(violations)} violation(s)")
            all_violations.extend(violations)
        else:
            print(f"OK   {name}")

    if all_violations:
        print()
        for v in all_violations:
            print(f"- {v.render()}")
        return 1

    if artifact_warnings:
        print()
        for v in artifact_warnings:
            print(f"- WARN {v.render()}")

    print("\nquick-check OK")
    return 0


if __name__ == "__main__":
    sys.exit(main())
