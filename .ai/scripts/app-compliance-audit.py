#!/usr/bin/env python3
"""Transversal app compliance audit for Real-Time Risk Lab.

This is an offline guardrail: it does not call Gradle, Bun, Docker, npm, or the
network. It checks the repo shape reviewers usually inspect before asking for a
full build: PoC inventory, docs/test surfaces, source dependency direction, and
local compose image pinning.
"""
from __future__ import annotations

import argparse
import re
import sys
from dataclasses import dataclass, field
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]

IMPORT_JAVA_RE = re.compile(r"^\s*import\s+([^;]+);", re.MULTILINE)
IMPORT_TS_RE = re.compile(r"^\s*import(?:\s+type)?(?:[^'\"\n]+from\s+)?['\"]([^'\"]+)['\"]", re.MULTILINE)
IMAGE_RE = re.compile(r"image:\s*['\"]?([^\s'\"]+)")

LAYER_NAMES = {"cmd", "config", "domain", "application", "infrastructure"}
FORBIDDEN_TS_PACKAGES = {"hono", "bullmq", "ioredis", "class-validator", "class-transformer", "express", "zod"}
FORBIDDEN_TS_PREFIXES = ("@nestjs/",)
ALLOWED_LATEST_IMAGES = {"anapsix/webdis"}  # upstream publishes only latest for this PoC use.
FORBIDDEN_IMAGE_TAGS = {
    "valkey/valkey:8-alpine",
    "ghcr.io/tigerbeetle/tigerbeetle:0.16.66",
    "postgres:16-alpine",
    "confluentinc/cp-kafka:7.0.0",
    "otel/opentelemetry-collector-contrib:0.141.0",
}


@dataclass(frozen=True)
class AppProfile:
    path: str
    kind: str
    clean_target: str
    expected_readme: bool = True
    expected_build: tuple[str, ...] = ()
    expected_tests: tuple[str, ...] = ()
    accepted_debt: tuple[str, ...] = ()


@dataclass
class AppResult:
    app: AppProfile
    docs: str = "N/A"
    build: str = "N/A"
    tests: str = "N/A"
    clean: str = "N/A"
    compose: str = "N/A"
    violations: list[str] = field(default_factory=list)
    warnings: list[str] = field(default_factory=list)

    @property
    def state(self) -> str:
        if self.violations:
            return "FAIL"
        if self.warnings or self.app.accepted_debt:
            return "WARN"
        return "OK"


APPS: tuple[AppProfile, ...] = (
    AppProfile(
        "poc/no-vertx-clean-engine",
        "Java app",
        "Clean Architecture estricta",
        expected_build=("build.gradle.kts",),
        expected_tests=("src/test/java", "README.md"),
    ),
    AppProfile(
        "poc/vertx-monolith-inprocess",
        "Java Vert.x app",
        "Monolito modular; adapters en repository/ y unit/integration tests",
        expected_build=("build.gradle.kts",),
        expected_tests=("src/test/java", "atdd-tests"),
        accepted_debt=("No usa layout domain/application/infrastructure completo porque contrasta un monolito Vert.x in-process.",),
    ),
    AppProfile(
        "poc/vertx-layer-as-pod-eventbus",
        "Java distributed app",
        "Separación física por capas + shared module",
        expected_build=("build.gradle.kts", "controller-app/build.gradle.kts", "usecase-app/build.gradle.kts", "repository-app/build.gradle.kts"),
        expected_tests=("atdd-tests",),
        accepted_debt=("La regla principal es aislamiento entre JVMs/módulos, no layout Clean Architecture dentro de cada módulo.",),
    ),
    AppProfile(
        "poc/vertx-layer-as-pod-http",
        "Java distributed app",
        "HTTP layer-as-pod + tokens",
        expected_build=("build.gradle.kts",),
        expected_tests=("src/test/java", "atdd-tests"),
        accepted_debt=("Persistencia in-memory por diseño para aislar la discusión HTTP+tokens vs EventBus.",),
    ),
    AppProfile(
        "poc/vertx-service-mesh-bounded-contexts",
        "Java service mesh app",
        "Bounded contexts separados por servicio",
        expected_build=("build.gradle.kts", "risk-decision-service/build.gradle.kts", "fraud-rules-service/build.gradle.kts", "ml-scorer-service/build.gradle.kts", "audit-service/build.gradle.kts"),
        expected_tests=("scripts/demo.sh",),
        accepted_debt=("PoC mínima: falta suite k6/ATDD dedicada comparable a las otras PoCs.",),
    ),
    AppProfile(
        "poc/k8s-local",
        "Kubernetes platform PoC",
        "Infraestructura/GitOps, no aplicación de dominio",
        expected_tests=("scripts",),
        accepted_debt=("No aplica Clean Architecture; se audita como infraestructura declarativa.",),
    ),
    AppProfile(
        "poc/kafka-s3-tansu",
        "Kafka/S3 broker PoC",
        "Infraestructura broker, no aplicación de dominio",
        expected_build=("compose.override.yml",),
        expected_tests=("README.md",),
        accepted_debt=("Mantiene texto en inglés como referencia histórica upstream; no bloquea la demo principal.",),
    ),
    AppProfile(
        "poc/nestjs-distributed-transactions",
        "TypeScript NestJS app",
        "Clean Architecture bajo internal/transactional-risk/{domain,application,infrastructure}",
        expected_build=("package.json", "tsconfig.json"),
        expected_tests=("src", "tests/integration", "tests/e2e", "tests/atdd", "tests/smoke", "tests/k6"),
    ),
    AppProfile(
        "poc/hono-distributed-transactions",
        "TypeScript Hono app",
        "Clean Architecture con wiring manual",
        expected_build=("package.json", "tsconfig.json"),
        expected_tests=("src", "tests/integration", "tests/e2e", "tests/atdd", "tests/smoke", "tests/k6"),
    ),
    AppProfile(
        "cli/risk-smoke",
        "Go CLI",
        "CLI internal packages con dependencias dirigidas",
        expected_build=("go.mod", "Makefile"),
        expected_tests=("README.md",),
        accepted_debt=("La documentación del CLI sigue en inglés; conviene traducirla si se busca consistencia total de docs.",),
    ),
)


def rel(path: Path) -> str:
    try:
        return str(path.relative_to(REPO_ROOT))
    except ValueError:
        return str(path)


def files_under(root: Path, suffix: str) -> list[Path]:
    if not root.exists():
        return []
    ignored = {"build", "dist", "node_modules", "out", ".gradle", ".turbo", "vendor"}
    return sorted(p for p in root.rglob(f"*{suffix}") if not ignored.intersection(p.parts))


def java_imports(path: Path) -> list[str]:
    return IMPORT_JAVA_RE.findall(path.read_text(encoding="utf-8", errors="ignore"))


def ts_imports(path: Path) -> list[str]:
    return IMPORT_TS_RE.findall(path.read_text(encoding="utf-8", errors="ignore"))


def check_docs(result: AppResult, root: Path) -> None:
    if not result.app.expected_readme:
        return
    readme = root / "README.md"
    if readme.exists():
        result.docs = "OK"
    else:
        result.docs = "FAIL"
        result.violations.append(f"{result.app.path}: falta README.md")


def check_build(result: AppResult, root: Path) -> None:
    if not result.app.expected_build:
        result.build = "N/A"
        return
    missing = [name for name in result.app.expected_build if not (root / name).exists()]
    if missing:
        result.build = "FAIL"
        result.violations.append(f"{result.app.path}: faltan descriptores de build {', '.join(missing)}")
    else:
        result.build = "OK"


def check_tests(result: AppResult, root: Path) -> None:
    if not result.app.expected_tests:
        result.tests = "N/A"
        return
    missing = [name for name in result.app.expected_tests if not (root / name).exists()]
    if missing:
        result.tests = "WARN"
        result.warnings.append(f"{result.app.path}: superficie de tests/docs incompleta: {', '.join(missing)}")
    else:
        result.tests = "OK"


def check_java_clean_boundaries(result: AppResult, root: Path) -> None:
    java_files = files_under(root, ".java")
    if not java_files:
        return

    hard = 0
    for f in java_files:
        parts = set(f.parts)
        for imp in java_imports(f):
            if "domain" in parts:
                if ".application." in imp or ".infrastructure." in imp:
                    result.violations.append(f"{rel(f)}: domain importa capa externa: {imp}")
                    hard += 1
                if imp.startswith(("io.vertx", "org.springframework", "jakarta.", "javax.servlet")):
                    result.violations.append(f"{rel(f)}: domain importa framework: {imp}")
                    hard += 1
            if "application" in parts:
                if ".infrastructure." in imp:
                    result.violations.append(f"{rel(f)}: application importa infrastructure: {imp}")
                    hard += 1
                if imp.startswith(("io.vertx", "org.springframework")):
                    result.violations.append(f"{rel(f)}: application importa framework: {imp}")
                    hard += 1

    if hard:
        result.clean = "FAIL"
    elif any(part in {"domain", "application", "infrastructure"} for f in java_files for part in f.parts):
        result.clean = "OK"
    else:
        result.clean = "WARN"
        if not result.app.accepted_debt:
            result.warnings.append(f"{result.app.path}: no expone layout domain/application/infrastructure; documentar deuda aceptada")


def check_eventbus_module_boundaries(result: AppResult, root: Path) -> None:
    if root.name != "vertx-layer-as-pod-eventbus":
        return
    modules = {
        "controller-app": "io.riskplatform.riskdecision.layerpodeventbus.controller.",
        "usecase-app": "io.riskplatform.riskdecision.layerpodeventbus.usecase.",
        "repository-app": "io.riskplatform.riskdecision.layerpodeventbus.repository.",
        "consumer-app": "io.riskplatform.riskdecision.layerpodeventbus.consumer.",
    }
    for module, own_pkg in modules.items():
        forbidden = [pkg for other, pkg in modules.items() if other != module]
        for f in files_under(root / module / "src/main/java", ".java"):
            for imp in java_imports(f):
                if any(imp.startswith(pkg) for pkg in forbidden):
                    result.violations.append(f"{rel(f)}: {module} importa otro módulo concreto: {imp}")
    for f in files_under(root / "shared/src/main/java", ".java"):
        for imp in java_imports(f):
            if any(imp.startswith(pkg) for pkg in modules.values()):
                result.violations.append(f"{rel(f)}: shared importa módulo concreto: {imp}")
    if result.clean != "FAIL":
        result.clean = "OK"


def check_ts_boundaries(result: AppResult, root: Path) -> None:
    internal = root / "src/internal"
    if not internal.exists():
        return
    hard = 0
    layer_dirs = [d for d in internal.rglob("*") if d.is_dir() and d.name in {"domain", "application"}]
    for layer_dir in layer_dirs:
        layer = layer_dir.name
        for f in files_under(layer_dir, ".ts"):
            for imp in ts_imports(f):
                if imp in FORBIDDEN_TS_PACKAGES or imp.startswith(FORBIDDEN_TS_PREFIXES):
                    result.violations.append(f"{rel(f)}: {layer} importa framework/adapter: {imp}")
                    hard += 1
                if "infrastructure" in imp.split("/") or imp.startswith("@infrastructure/"):
                    result.violations.append(f"{rel(f)}: {layer} importa infrastructure: {imp}")
                    hard += 1
    result.clean = "FAIL" if hard else "OK"


def check_compose_images(result: AppResult, root: Path) -> None:
    compose_files = list(root.glob("*compose*.yml")) + list(root.glob("*compose*.yaml"))
    if not compose_files:
        result.compose = "N/A"
        return
    warnings = 0
    for f in compose_files:
        for image in IMAGE_RE.findall(f.read_text(encoding="utf-8", errors="ignore")):
            image = image.strip()
            name = image.split(":", 1)[0]
            if image in FORBIDDEN_IMAGE_TAGS:
                result.violations.append(f"{rel(f)}: imagen obsoleta detectada: {image}")
            elif image.endswith(":latest") and name not in ALLOWED_LATEST_IMAGES and not name.startswith("riskplatform/"):
                result.violations.append(f"{rel(f)}: evitar latest no permitido: {image}")
            elif image.endswith(":latest") and name in ALLOWED_LATEST_IMAGES:
                warnings += 1
    compose_fail = any(rel(f) in v and ("imagen" in v or "latest" in v) for f in compose_files for v in result.violations)
    result.compose = "FAIL" if compose_fail else ("WARN" if warnings else "OK")
    if warnings:
        result.warnings.append(f"{result.app.path}: usa latest aceptado para Webdis porque no hay tag estable publicado en la PoC")


def audit() -> list[AppResult]:
    results: list[AppResult] = []
    for app in APPS:
        root = REPO_ROOT / app.path
        result = AppResult(app=app)
        if not root.exists():
            result.violations.append(f"{app.path}: path inexistente")
            results.append(result)
            continue
        check_docs(result, root)
        check_build(result, root)
        check_tests(result, root)
        check_java_clean_boundaries(result, root)
        check_eventbus_module_boundaries(result, root)
        check_ts_boundaries(result, root)
        check_compose_images(result, root)
        if result.clean == "N/A" and app.accepted_debt:
            result.clean = "N/A"
        results.append(result)
    return results


def md_table(results: list[AppResult]) -> str:
    lines = [
        "| App/PoC | Tipo | Regla auditada | Docs | Build | Tests | Boundaries | Compose | Estado |",
        "|---|---|---|---:|---:|---:|---:|---:|---:|",
    ]
    for r in results:
        lines.append(
            f"| `{r.app.path}` | {r.app.kind} | {r.app.clean_target} | {r.docs} | {r.build} | {r.tests} | {r.clean} | {r.compose} | **{r.state}** |"
        )
    return "\n".join(lines)


def markdown_report(results: list[AppResult]) -> str:
    hard = [v for r in results for v in r.violations]
    warnings = [w for r in results for w in r.warnings]
    debt = [(r.app.path, item) for r in results for item in r.app.accepted_debt]
    return "\n".join([
        "# Auditoría transversal de apps y PoCs",
        "",
        "## Criterio de lectura",
        "",
        "- **OK**: cumple lo esperable para el tipo de PoC auditada.",
        "- **WARN**: no hay violación bloqueante, pero existe deuda aceptada o una decisión intencional que hay que poder explicar.",
        "- **FAIL**: violación bloqueante; no debería entrar a una review exigente sin fix.",
        "- **N/A**: no aplica porque la PoC es infraestructura, broker o tooling, no una app de dominio.",
        "",
        "La auditoría distingue entre **Clean Architecture estricta** y **arquitecturas comparativas**. No todas las PoCs tienen que verse iguales: algunas existen para contrastar monolito, layer-as-pod, HTTP entre pods, EventBus clustered, service-to-service, infraestructura k8s o broker Kafka/S3.",
        "",
        "> Snapshot generado por `.ai/scripts/app-compliance-audit.py`. Es un guardrail offline: valida estructura, documentación mínima, superficie de tests, boundaries de código fuente y pinning básico de imágenes Compose sin ejecutar builds ni red.",
        "",
        "## Matriz de cumplimiento por PoC",
        "",
        md_table(results),
        "",
        "## Violaciones encontradas",
        "",
        *(f"- {v}" for v in hard),
        *( ["- No hay violaciones bloqueantes detectadas por el guardrail offline."] if not hard else [] ),
        "",
        "## Fixes rápidos recomendados",
        "",
        "- Correr `python3 .ai/scripts/app-compliance-audit.py` antes de una review exigente o de tocar una PoC.",
        "- Correr `python3 .ai/scripts/quick-check.py` para boundaries fuente rápidos y freshness de artefactos.",
        "- Correr `./nx test architecture` para ArchUnit bytecode/source-level en Java.",
        "- Correr `./nx test --composite typescript-transactional-pocs --parallel 1 --max-cpu 50 --max-ram 4000` si se toca NestJS/Hono.",
        "- Para PoCs con deuda aceptada, promover primero las warnings a tests ejecutables antes de agregar funcionalidad nueva.",
        "",
        "## Warnings y deuda aceptada explícitamente",
        "",
        *(f"- {w}" for w in warnings),
        *(f"- `{path}`: {item}" for path, item in debt),
        *( ["- Sin warnings ni deuda aceptada."] if not warnings and not debt else [] ),
        "",
        "## Guardrails automáticos disponibles",
        "",
        "| Guardrail | Comando | Qué cubre |",
        "|---|---|---|",
        "| Auditoría transversal offline | `python3 .ai/scripts/app-compliance-audit.py` | Matriz PoC, docs/build/tests mínimos, boundaries Java/TS y Compose pinning. |",
        "| Quick check de demo | `python3 .ai/scripts/quick-check.py` | Boundaries críticos Java/TS/Go + freshness de build artifacts. |",
        "| Primitivas IA | `./.ai/scripts/verify-primitives.sh` | Frontmatter, links, estructura de reglas/skills/workflows. |",
        "| Arquitectura Java | `./nx test architecture` | ArchUnit y reglas estructurales Java. |",
        "| Suite TS transaccional | `./nx test --composite typescript-transactional-pocs --parallel 1 --max-cpu 50 --max-ram 4000` | Unit/integration/e2e/ATDD/smoke/k6 NestJS y Hono. |",
        "| CI rápido compuesto | `./nx test --composite ci-fast --parallel 1 --max-cpu 50 --max-ram 4000` | Smoke/arquitectura/tests rápidos del repo. |",
        "",
    ])


def console_report(results: list[AppResult]) -> None:
    print(md_table(results))
    hard = [v for r in results for v in r.violations]
    warnings = [w for r in results for w in r.warnings]
    if hard:
        print("\nViolaciones bloqueantes:")
        for v in hard:
            print(f"- {v}")
    if warnings:
        print("\nWarnings:")
        for w in warnings:
            print(f"- {w}")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--markdown", action="store_true", help="print a full Markdown report")
    parser.add_argument("--write-doc", type=Path, help="write Markdown report to a file")
    args = parser.parse_args()

    results = audit()
    if args.write_doc:
        target = args.write_doc if args.write_doc.is_absolute() else REPO_ROOT / args.write_doc
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(markdown_report(results), encoding="utf-8")
        print(f"wrote {rel(target)}")
    elif args.markdown:
        print(markdown_report(results))
    else:
        console_report(results)

    return 1 if any(r.violations for r in results) else 0


if __name__ == "__main__":
    sys.exit(main())
