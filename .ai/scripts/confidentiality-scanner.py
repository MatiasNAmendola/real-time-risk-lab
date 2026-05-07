#!/usr/bin/env python3
"""
confidentiality-scanner.py — Detect prohibited terms via SHA-256 hashing.

The blocklist stores only hashes of prohibited terms, never plaintext.
This means the scanner itself never reveals what it is protecting against.

Usage:
    python3 .ai/scripts/confidentiality-scanner.py [paths...]
    python3 .ai/scripts/confidentiality-scanner.py --blocklist <path>
    python3 .ai/scripts/confidentiality-scanner.py --add "<term>"
    python3 .ai/scripts/confidentiality-scanner.py --remove "<term>"
    python3 .ai/scripts/confidentiality-scanner.py --check-word "<term>"
    python3 .ai/scripts/confidentiality-scanner.py --help
"""
from __future__ import annotations

import argparse
import hashlib
import re
import sys
from pathlib import Path

# ---------------------------------------------------------------------------
# Defaults
# ---------------------------------------------------------------------------

SCRIPT_DIR = Path(__file__).resolve().parent
REPO_ROOT = SCRIPT_DIR.parent.parent
DEFAULT_BLOCKLIST = REPO_ROOT / ".ai" / "blocklist.sha256"

SCAN_EXTENSIONS = {
    ".md", ".txt", ".yaml", ".yml", ".json",
    ".sh", ".py", ".kts", ".gradle",
    ".java", ".go", ".ts", ".tsx", ".js",
}

EXCLUDED_DIRS = {
    ".git", "build", "target", "out", "node_modules",
    ".cache", "wrapper",
}

# Candidate words: start with a letter, 4-30 chars, alphanumeric + dash/underscore.
_CANDIDATE_RE = re.compile(r"\b[a-zA-Z][a-zA-Z0-9\-_]{3,29}\b")


# ---------------------------------------------------------------------------
# Core primitives
# ---------------------------------------------------------------------------

def normalize(word: str) -> str:
    """Return canonical form: lowercase, stripped, no dashes or underscores."""
    return word.lower().strip().replace("-", "").replace("_", "")


def sha256_hex(word: str) -> str:
    """Return hex SHA-256 of the normalized word."""
    return hashlib.sha256(normalize(word).encode("utf-8")).hexdigest()


def load_blocklist(path: Path) -> set[str]:
    """Load hashes from file. Returns empty set if file is missing or unreadable."""
    if not path.exists():
        return set()
    hashes: set[str] = set()
    try:
        for line in path.read_text(encoding="utf-8").splitlines():
            stripped = line.strip()
            if stripped and not stripped.startswith("#"):
                hashes.add(stripped.lower())
    except OSError:
        return set()
    return hashes


def extract_candidate_words(text: str) -> list[str]:
    """Extract candidate words matching [a-zA-Z][a-zA-Z0-9-_]{3,29}."""
    return _CANDIDATE_RE.findall(text)


def scan(text: str, blocklist: set[str]) -> list[str]:
    """
    Hash each candidate word and compare against the blocklist.

    Returns a list of matching hashes (12-char prefix only).
    Never returns the plaintext of matched words.
    """
    if not blocklist:
        return []
    matches: list[str] = []
    seen: set[str] = set()
    for word in extract_candidate_words(text):
        h = sha256_hex(word)
        if h in blocklist and h not in seen:
            seen.add(h)
            matches.append(h[:12])
    return matches


# ---------------------------------------------------------------------------
# File scanning
# ---------------------------------------------------------------------------

def _is_excluded(path: Path) -> bool:
    """Return True if any path component is in the excluded set."""
    return any(part in EXCLUDED_DIRS for part in path.parts)


def _collect_files(roots: list[Path]) -> list[Path]:
    """Walk roots, collecting files with allowed extensions."""
    collected: list[Path] = []
    for root in roots:
        if root.is_file():
            if root.suffix in SCAN_EXTENSIONS and not _is_excluded(root):
                collected.append(root)
        elif root.is_dir():
            for f in root.rglob("*"):
                if f.is_file() and f.suffix in SCAN_EXTENSIONS and not _is_excluded(f):
                    collected.append(f)
    return collected


def scan_paths(paths: list[Path], blocklist: set[str]) -> dict[Path, list[str]]:
    """
    Scan each file in paths.

    Returns a mapping of file -> list of hash prefixes detected.
    Files with no matches are excluded from the result.
    """
    results: dict[Path, list[str]] = {}
    files = _collect_files(paths)
    for f in files:
        try:
            text = f.read_text(encoding="utf-8", errors="replace")
        except OSError:
            continue
        matches = scan(text, blocklist)
        if matches:
            results[f] = matches
    return results


# ---------------------------------------------------------------------------
# Blocklist management
# ---------------------------------------------------------------------------

def _write_blocklist(path: Path, hashes: set[str]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    lines = sorted(hashes)
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")


def add_term(term: str, blocklist_path: Path) -> str:
    """Add a term's hash to the blocklist. Returns the hash."""
    h = sha256_hex(term)
    existing = load_blocklist(blocklist_path)
    if h in existing:
        return h
    existing.add(h)
    _write_blocklist(blocklist_path, existing)
    return h


def remove_term(term: str, blocklist_path: Path) -> bool:
    """Remove a term's hash from the blocklist. Returns True if it was present."""
    h = sha256_hex(term)
    existing = load_blocklist(blocklist_path)
    if h not in existing:
        return False
    existing.discard(h)
    _write_blocklist(blocklist_path, existing)
    return True


def check_word(term: str, blocklist_path: Path) -> bool:
    """Return True if the term's hash is in the blocklist."""
    h = sha256_hex(term)
    return h in load_blocklist(blocklist_path)


# ---------------------------------------------------------------------------
# CLI
# ---------------------------------------------------------------------------

def _build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        prog="confidentiality-scanner",
        description=(
            "Detect prohibited terms via SHA-256 hashing. "
            "The blocklist stores only hashes; plaintext is never retained."
        ),
    )
    parser.add_argument(
        "paths",
        nargs="*",
        type=Path,
        help="Files or directories to scan (default: repo root).",
    )
    parser.add_argument(
        "--blocklist",
        type=Path,
        default=DEFAULT_BLOCKLIST,
        metavar="PATH",
        help=f"Path to the .sha256 blocklist file (default: {DEFAULT_BLOCKLIST}).",
    )
    parser.add_argument(
        "--add",
        metavar="<term>",
        help="Hash <term> and add the hash to the blocklist.",
    )
    parser.add_argument(
        "--remove",
        metavar="<term>",
        help="Remove <term>'s hash from the blocklist.",
    )
    parser.add_argument(
        "--check-word",
        metavar="<term>",
        dest="check_word",
        help="Exit 1 if <term> is blocked, exit 0 otherwise.",
    )
    return parser


def main(argv: list[str] | None = None) -> int:
    parser = _build_parser()
    args = parser.parse_args(argv)
    blocklist_path: Path = args.blocklist

    # --add
    if args.add:
        h = add_term(args.add, blocklist_path)
        print(f"ADDED: {h[:12]}... to {blocklist_path}")
        return 0

    # --remove
    if args.remove:
        removed = remove_term(args.remove, blocklist_path)
        if removed:
            print(f"REMOVED: hash of <term> from {blocklist_path}")
        else:
            print(f"NOT FOUND: hash of <term> was not in {blocklist_path}")
        return 0

    # --check-word
    if args.check_word:
        blocked = check_word(args.check_word, blocklist_path)
        if blocked:
            h = sha256_hex(args.check_word)
            print(f"BLOCKED: {h[:12]}... — term is in blocklist")
            return 1
        print("OK: term is not in blocklist")
        return 0

    # Scanning
    blocklist = load_blocklist(blocklist_path)
    if not blocklist:
        print(
            f"WARNING: blocklist not found or empty ({blocklist_path}). "
            "No terms to check. Exiting without scanning.",
            file=sys.stderr,
        )
        return 0

    scan_roots: list[Path] = args.paths if args.paths else [REPO_ROOT]
    findings = scan_paths(scan_roots, blocklist)

    if not findings:
        print("OK: no prohibited terms detected.")
        return 0

    total_matches = 0
    for file_path, hashes in sorted(findings.items()):
        try:
            rel = file_path.relative_to(REPO_ROOT)
        except ValueError:
            rel = file_path
        n = len(hashes)
        total_matches += n
        print(f"{rel}: {n} prohibited term(s) detected")
        for h in hashes:
            print(f"  BLOCKED: {h}... — review file manually")

    print(f"\nScan complete: {total_matches} match(es) in {len(findings)} file(s).")
    return 1


if __name__ == "__main__":
    sys.exit(main())
