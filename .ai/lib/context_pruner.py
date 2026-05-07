"""
context_pruner.py — Cheap deduplication pass for tool results without LLM calls.
Stdlib only. Principle: Context is the bottleneck.
"""

import hashlib
from typing import Optional


def prune_duplicates(events: list[dict]) -> list[dict]:
    """Replaces older identical tool outputs with one-line stubs.

    The FIRST occurrence of a unique digest is kept in place.
    Subsequent identical occurrences become lightweight reference stubs.
    """
    seen: dict[str, int] = {}  # md5 -> first occurrence index in pruned list
    pruned: list[dict] = []

    for i, event in enumerate(events):
        if event.get("kind") != "tool_result":
            pruned.append(event)
            continue

        content = event.get("content", "")
        digest = hashlib.md5(content.encode()).hexdigest()

        if digest in seen:
            pruned.append({
                "kind": "tool_result_stub",
                "ref": seen[digest],
                "digest": digest[:8],
                "original_index": i,
            })
        else:
            seen[digest] = len(pruned)
            pruned.append(event)

    return pruned


def stub_old_tool_results(events: list[dict], keep_last_n: int = 5) -> list[dict]:
    """Replaces tool results older than the last N occurrences with stubs.

    Keeps the most recent `keep_last_n` tool_result events intact.
    Earlier ones are replaced by reference stubs pointing to the kept index.
    """
    # Collect positions of all tool_result events
    tool_result_positions = [
        i for i, e in enumerate(events) if e.get("kind") == "tool_result"
    ]

    if len(tool_result_positions) <= keep_last_n:
        return list(events)

    # Positions to stub (all except last keep_last_n)
    to_stub = set(tool_result_positions[:-keep_last_n])
    # Map from stubbed index -> kept index (the last kept occurrence of same content)
    # For simplicity, stub without content ref (just index-based)
    pruned = []
    for i, event in enumerate(events):
        if i in to_stub:
            pruned.append({
                "kind": "tool_result_stub",
                "original_index": i,
                "digest": hashlib.md5(event.get("content", "").encode()).hexdigest()[:8],
                "reason": "older than keep_last_n",
            })
        else:
            pruned.append(event)
    return pruned


def estimate_savings(original: list[dict], pruned: list[dict]) -> dict:
    """Returns a dict with before/after char counts and stub count."""
    def total_chars(events: list[dict]) -> int:
        return sum(len(e.get("content", "")) for e in events if e.get("kind") == "tool_result")

    stubs = sum(1 for e in pruned if e.get("kind") == "tool_result_stub")
    return {
        "original_chars": total_chars(original),
        "pruned_chars": total_chars(pruned),
        "stubs_introduced": stubs,
    }
