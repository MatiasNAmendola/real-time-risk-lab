"""
Pre-persistence guard for agent memory.

Detects prompt injection attempts, secret exfiltration patterns, and
invisible Unicode characters before content is written to agent memory,
context stores, or audit logs.

Usage:
    from memory_scanner import scan, ScanResult
    result = scan(content)
    if not result.safe:
        raise ValueError(f"unsafe content: {result.threats}")
"""

from dataclasses import dataclass, field
import re


@dataclass(frozen=True)
class ScanResult:
    safe: bool
    threats: list  # list of (category, pattern_name, snippet)


INJECTION_PATTERNS = [
    (
        "ignore_previous",
        re.compile(
            r"ignore (all|previous|the above|prior) (instructions|prompts?|system)",
            re.I,
        ),
    ),
    (
        "you_are_now",
        re.compile(r"you are now ([a-z ]{1,40}) (?:that|who|which) ", re.I),
    ),
    (
        "system_prompt_override",
        re.compile(r"system prompt:|<\|im_start\|>system|<system>", re.I),
    ),
    (
        "forget_role",
        re.compile(r"forget (your|the) (role|previous|instructions)", re.I),
    ),
    (
        "jailbreak",
        re.compile(r"DAN mode|developer mode enabled|jailbreak", re.I),
    ),
]

EXFIL_PATTERNS = [
    (
        "curl_with_secret",
        re.compile(
            r"curl[^\n]*\$\{?(AWS|GH|GITHUB|SECRET|TOKEN|API)_[A-Z_]+\}?", re.I
        ),
    ),
    (
        "wget_secret",
        re.compile(
            r"wget[^\n]*\$\{?(AWS|GH|GITHUB|SECRET|TOKEN)_[A-Z_]+\}?", re.I
        ),
    ),
    (
        "cat_env",
        re.compile(r"cat\s+\.env(\.\w+)?(\s|$)"),
    ),
    (
        "authorized_keys",
        re.compile(r"~?/\.ssh/authorized_keys|/root/\.ssh/"),
    ),
    (
        "private_key_read",
        re.compile(r"cat\s+[^\s]*\.pem|cat\s+~?/\.ssh/id_(rsa|ecdsa|ed25519)"),
    ),
    (
        "history_dump",
        re.compile(r"\.bash_history|\.zsh_history"),
    ),
]

# Unicode invisible/dangerous characters
# Zero-width space, non-joiner, joiner, word joiner, zero-width no-break space,
# and bidirectional override characters (LRO, RLO, PDF, LRE, RLE)
INVISIBLE_CHARS = [
    "​",  # zero-width space
    "‌",  # zero-width non-joiner
    "‍",  # zero-width joiner
    "⁠",  # word joiner
    "﻿",  # zero-width no-break space (BOM)
    "‪",  # left-to-right embedding
    "‫",  # right-to-left embedding
    "‬",  # pop directional formatting
    "‭",  # left-to-right override (LRO)
    "‮",  # right-to-left override (RLO)
]

INVISIBLE_RX = re.compile(
    "[" + "".join(re.escape(c) for c in INVISIBLE_CHARS) + "]"
)


def scan(content: str) -> ScanResult:
    """Scan content for injection, exfil, and invisible Unicode threats.

    Returns a ScanResult with safe=True only when no threats are detected.
    Each threat is a tuple of (category, pattern_name, snippet).
    """
    threats = []

    for name, rx in INJECTION_PATTERNS:
        if m := rx.search(content):
            threats.append(("injection", name, m.group(0)[:80]))

    for name, rx in EXFIL_PATTERNS:
        if m := rx.search(content):
            threats.append(("exfil", name, m.group(0)[:80]))

    if INVISIBLE_RX.search(content):
        threats.append(("invisible_unicode", "zero_width_or_bidi", ""))

    return ScanResult(safe=(len(threats) == 0), threats=threats)
