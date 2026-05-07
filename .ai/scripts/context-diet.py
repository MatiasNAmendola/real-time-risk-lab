#!/usr/bin/env python3
"""
context-diet.py — CLI wrapper for context_diet.select_rules.

Usage:
    python3 .ai/scripts/context-diet.py --task implement
    python3 .ai/scripts/context-diet.py --task debug --rules-dir .ai/primitives/rules
    python3 .ai/scripts/context-diet.py --list-bundles

Output: one rule file path per line (only existing paths).
The orchestrator reads this output to inject the minimal rule subset into sub-agents.
"""

import sys
import argparse
from pathlib import Path

_SCRIPT_DIR = Path(__file__).parent
_LIB_DIR = _SCRIPT_DIR.parent / "lib"
if str(_LIB_DIR) not in sys.path:
    sys.path.insert(0, str(_LIB_DIR))

from context_diet import select_rules, list_bundles, RULE_BUNDLES  # noqa: E402


DEFAULT_RULES_DIR = _SCRIPT_DIR.parent / "primitives" / "rules"


def main():
    parser = argparse.ArgumentParser(
        description="Print the minimal rule-file subset for a given task type."
    )
    parser.add_argument(
        "--task",
        choices=list(RULE_BUNDLES.keys()),
        help="Task type (implement, review, debug, explore, test)",
    )
    parser.add_argument(
        "--rules-dir",
        default=str(DEFAULT_RULES_DIR),
        help=f"Directory containing rule .md files (default: {DEFAULT_RULES_DIR})",
    )
    parser.add_argument(
        "--list-bundles",
        action="store_true",
        help="Print all defined bundles and exit",
    )
    args = parser.parse_args()

    if args.list_bundles:
        bundles = list_bundles()
        for task, rules in bundles.items():
            print(f"{task}: {', '.join(rules)}")
        sys.exit(0)

    if not args.task:
        parser.error("--task is required unless --list-bundles is used")

    rules_dir = Path(args.rules_dir)
    paths = select_rules(args.task, rules_dir)

    if not paths:
        # Print bundle names even if files don't exist yet (useful for dev)
        bundle = RULE_BUNDLES.get(args.task, [])
        for name in bundle:
            print(rules_dir / f"{name}.md")
    else:
        for p in paths:
            print(p)


if __name__ == "__main__":
    main()
