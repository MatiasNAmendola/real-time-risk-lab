"""
context_diet.py — Select the minimal rule subset for a given task type.
Stdlib only. Principle: Primitives over context.
"""

from pathlib import Path

RULE_BUNDLES: dict[str, list[str]] = {
    "implement": ["java-version", "architecture-clean", "communication-patterns", "observability-otel"],
    "review":    ["clean-arch-boundaries", "naming-conventions", "error-handling"],
    "debug":     ["observability-otel", "error-handling"],
    "explore":   ["naming-conventions"],
    "test":      ["testing-atdd", "java-version"],
}


def select_rules(task_type: str, all_rules_dir: Path) -> list[Path]:
    """Returns minimal subset of rule files for the given task type.

    Falls back to the 'implement' bundle for unknown task types.
    Only returns paths that actually exist on disk.
    """
    bundle = RULE_BUNDLES.get(task_type, list(RULE_BUNDLES["implement"]))
    return [all_rules_dir / f"{name}.md" for name in bundle if (all_rules_dir / f"{name}.md").exists()]


def list_bundles() -> dict[str, list[str]]:
    """Returns all defined bundles (useful for CLI display)."""
    return dict(RULE_BUNDLES)
