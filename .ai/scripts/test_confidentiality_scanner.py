#!/usr/bin/env python3
"""
test_confidentiality_scanner.py — Unit tests for confidentiality-scanner.py.

All "prohibited" terms used in tests are generic placeholders
(e.g., "PLACEHOLDER_TERM", "DEMO_BLOCK_001"). No real company
names, project names, or client names appear anywhere in this file.
"""
from __future__ import annotations

import hashlib
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

# ---------------------------------------------------------------------------
# Resolve module under test
# ---------------------------------------------------------------------------

SCRIPT_DIR = Path(__file__).resolve().parent
SCANNER_PATH = SCRIPT_DIR / "confidentiality-scanner.py"

# Import module directly so we can unit-test functions without subprocess.
import importlib.util as _ilu

_spec = _ilu.spec_from_file_location("confidentiality_scanner", SCANNER_PATH)
_mod = _ilu.module_from_spec(_spec)  # type: ignore[arg-type]
_spec.loader.exec_module(_mod)  # type: ignore[union-attr]

normalize = _mod.normalize
sha256_hex = _mod.sha256_hex
load_blocklist = _mod.load_blocklist
extract_candidate_words = _mod.extract_candidate_words
scan = _mod.scan
add_term = _mod.add_term
remove_term = _mod.remove_term
check_word = _mod.check_word
main = _mod.main


# ---------------------------------------------------------------------------
# Helper
# ---------------------------------------------------------------------------

def _sha(word: str) -> str:
    return hashlib.sha256(word.lower().strip().encode()).hexdigest()


# ---------------------------------------------------------------------------
# Tests
# ---------------------------------------------------------------------------

class TestNormalize(unittest.TestCase):
    def test_lowercase(self) -> None:
        self.assertEqual(normalize("PLACEHOLDER_TERM"), "placeholderterm")

    def test_strips_dashes_underscores(self) -> None:
        self.assertEqual(normalize("demo-block_001"), "demoblock001")

    def test_strips_whitespace(self) -> None:
        self.assertEqual(normalize("  word  "), "word")

    def test_idempotent(self) -> None:
        w = "DEMO_BLOCK_001"
        self.assertEqual(normalize(normalize(w)), normalize(w))


class TestSha256Hex(unittest.TestCase):
    def test_deterministic(self) -> None:
        self.assertEqual(sha256_hex("PLACEHOLDER_TERM"), sha256_hex("PLACEHOLDER_TERM"))

    def test_normalized_before_hashing(self) -> None:
        # "DEMO_BLOCK_001" and "demo-block-001" normalise to same canonical form.
        self.assertEqual(sha256_hex("DEMO_BLOCK_001"), sha256_hex("DEMO_BLOCK_001"))

    def test_returns_hex_string(self) -> None:
        h = sha256_hex("PLACEHOLDER_TERM")
        self.assertRegex(h, r"^[0-9a-f]{64}$")


class TestLoadBlocklist(unittest.TestCase):
    def test_missing_file_returns_empty_set(self) -> None:
        with tempfile.TemporaryDirectory() as td:
            path = Path(td) / "nonexistent.sha256"
            self.assertEqual(load_blocklist(path), set())

    def test_loads_hashes(self) -> None:
        with tempfile.TemporaryDirectory() as td:
            path = Path(td) / "test.sha256"
            h = _sha("placeholderterm")
            path.write_text(f"{h}\n")
            result = load_blocklist(path)
            self.assertIn(h, result)

    def test_ignores_comments(self) -> None:
        with tempfile.TemporaryDirectory() as td:
            path = Path(td) / "test.sha256"
            path.write_text("# this is a comment\n")
            self.assertEqual(load_blocklist(path), set())

    def test_ignores_blank_lines(self) -> None:
        with tempfile.TemporaryDirectory() as td:
            path = Path(td) / "test.sha256"
            h = _sha("demoblock001")
            path.write_text(f"\n{h}\n\n")
            self.assertEqual(load_blocklist(path), {h})


class TestExtractCandidateWords(unittest.TestCase):
    def test_extracts_simple_words(self) -> None:
        words = extract_candidate_words("hello world test")
        self.assertIn("hello", words)
        self.assertIn("world", words)

    def test_minimum_length_4(self) -> None:
        # Words shorter than 4 chars should not be extracted.
        # "word" is exactly 4 chars and should be included.
        words = extract_candidate_words("hi ok no word")
        self.assertNotIn("hi", words)
        self.assertNotIn("ok", words)
        self.assertNotIn("no", words)
        self.assertIn("word", words)

    def test_max_length_30(self) -> None:
        long_word = "A" + "b" * 30  # 31 chars
        words = extract_candidate_words(long_word)
        self.assertFalse(any(len(w) > 30 for w in words))

    def test_must_start_with_letter(self) -> None:
        words = extract_candidate_words("1invalid 2block")
        self.assertNotIn("1invalid", words)
        self.assertNotIn("2block", words)


class TestScan(unittest.TestCase):
    def _make_blocklist(self, *terms: str) -> set[str]:
        return {_sha(normalize(t)) for t in terms}

    def test_detects_blocked_term(self) -> None:
        blocklist = self._make_blocklist("placeholderterm")
        matches = scan("This text contains PLACEHOLDER_TERM inside.", blocklist)
        self.assertTrue(len(matches) > 0)

    def test_no_match_on_clean_text(self) -> None:
        blocklist = self._make_blocklist("demoblock001")
        matches = scan("This text is completely clean.", blocklist)
        self.assertEqual(matches, [])

    def test_empty_blocklist_returns_empty(self) -> None:
        matches = scan("PLACEHOLDER_TERM DEMO_BLOCK_001", set())
        self.assertEqual(matches, [])

    def test_returns_hash_prefix_not_plaintext(self) -> None:
        blocklist = self._make_blocklist("placeholderterm")
        matches = scan("PLACEHOLDER_TERM", blocklist)
        # The match should be a 12-char hex prefix, not the plaintext.
        for m in matches:
            self.assertRegex(m, r"^[0-9a-f]{12}$")
            self.assertNotIn("placeholder", m.lower())

    def test_no_duplicates_for_same_term(self) -> None:
        blocklist = self._make_blocklist("placeholderterm")
        matches = scan("PLACEHOLDER_TERM placeholder-term placeholder_term", blocklist)
        # All three normalise to the same hash — should appear only once.
        self.assertEqual(len(matches), 1)


class TestBlocklistManagement(unittest.TestCase):
    def test_add_and_check(self) -> None:
        with tempfile.TemporaryDirectory() as td:
            path = Path(td) / "test.sha256"
            add_term("PLACEHOLDER_TERM", path)
            self.assertTrue(check_word("PLACEHOLDER_TERM", path))

    def test_remove_term(self) -> None:
        with tempfile.TemporaryDirectory() as td:
            path = Path(td) / "test.sha256"
            add_term("DEMO_BLOCK_001", path)
            self.assertTrue(check_word("DEMO_BLOCK_001", path))
            removed = remove_term("DEMO_BLOCK_001", path)
            self.assertTrue(removed)
            self.assertFalse(check_word("DEMO_BLOCK_001", path))

    def test_remove_nonexistent_returns_false(self) -> None:
        with tempfile.TemporaryDirectory() as td:
            path = Path(td) / "test.sha256"
            result = remove_term("DEMO_BLOCK_999", path)
            self.assertFalse(result)

    def test_add_idempotent(self) -> None:
        with tempfile.TemporaryDirectory() as td:
            path = Path(td) / "test.sha256"
            add_term("PLACEHOLDER_TERM", path)
            add_term("PLACEHOLDER_TERM", path)
            hashes = load_blocklist(path)
            self.assertEqual(len(hashes), 1)


class TestCLIIntegration(unittest.TestCase):
    """Integration tests via subprocess to validate CLI exit codes and output."""

    def _run(self, *args: str) -> tuple[int, str, str]:
        result = subprocess.run(
            [sys.executable, str(SCANNER_PATH), *args],
            capture_output=True,
            text=True,
        )
        return result.returncode, result.stdout, result.stderr

    def test_add_then_check_blocked(self) -> None:
        with tempfile.TemporaryDirectory() as td:
            bl = str(Path(td) / "test.sha256")
            rc, out, _ = self._run("--blocklist", bl, "--add", "PLACEHOLDER_TERM")
            self.assertEqual(rc, 0)
            rc2, out2, _ = self._run("--blocklist", bl, "--check-word", "PLACEHOLDER_TERM")
            self.assertEqual(rc2, 1)
            self.assertIn("BLOCKED", out2)

    def test_check_word_not_blocked(self) -> None:
        with tempfile.TemporaryDirectory() as td:
            bl = str(Path(td) / "empty.sha256")
            rc, out, _ = self._run("--blocklist", bl, "--check-word", "PLACEHOLDER_TERM")
            self.assertEqual(rc, 0)
            self.assertIn("OK", out)

    def test_empty_blocklist_exits_zero(self) -> None:
        with tempfile.TemporaryDirectory() as td:
            bl = str(Path(td) / "empty.sha256")
            rc, _, err = self._run("--blocklist", bl, td)
            self.assertEqual(rc, 0)
            self.assertIn("WARNING", err)

    def test_scan_detects_term_in_file(self) -> None:
        with tempfile.TemporaryDirectory() as td:
            bl = Path(td) / "test.sha256"
            add_term("DEMO_BLOCK_001", bl)
            target = Path(td) / "sample.txt"
            target.write_text("This file mentions DEMO_BLOCK_001 inside.\n")
            rc, out, _ = self._run("--blocklist", str(bl), str(target))
            self.assertEqual(rc, 1)
            self.assertIn("prohibited term(s) detected", out)
            # Plaintext must NOT appear in output.
            self.assertNotIn("DEMO_BLOCK_001", out)

    def test_help_flag(self) -> None:
        rc, out, _ = self._run("--help")
        self.assertEqual(rc, 0)
        self.assertIn("confidentiality-scanner", out.lower() + _.lower())


if __name__ == "__main__":
    unittest.main()
