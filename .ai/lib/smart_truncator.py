"""
smart_truncator.py — Intelligent tool-output truncation by command type.
Stdlib only. Principle: Context is the bottleneck.
"""

import re
import json
import hashlib
from typing import Optional


class SmartTruncator:
    """Extracts the signal from noisy command output, discarding boilerplate."""

    # -----------------------------------------------------------------------
    # Public API
    # -----------------------------------------------------------------------

    def truncate(self, output: str, command_hint: str = "", max_chars: int = 4000) -> str:
        if len(output) <= max_chars:
            return output
        cmd_type = self.detect_command_type(command_hint, output)
        handler = {
            "test":    self._truncate_test,
            "build":   self._truncate_build,
            "lint":    self._truncate_lint,
            "git":     self._truncate_git,
            "docker":  self._truncate_docker,
            "json":    self._truncate_json,
        }.get(cmd_type, self._truncate_generic)
        result = handler(output, max_chars)
        if len(result) > max_chars:
            result = result[:max_chars] + f"\n... [truncated — original {len(output)} chars]"
        return result

    def detect_command_type(self, hint: str, output: str) -> str:
        combined = (hint + " " + output[:200]).lower()
        if re.search(r"pytest|jest|junit|./gradlew test|go test|npm test|cargo test", combined):
            return "test"
        if re.search(r"\b./gradlew\b.*build|gradle|npm run build|cargo build|go build|make\b", combined):
            return "build"
        if re.search(r"eslint|ruff|shellcheck|pylint|flake8|golangci", combined):
            return "lint"
        if re.search(r"git (status|log|diff|show|blame)", combined):
            return "git"
        if re.search(r"docker (compose|build|run|ps|logs)|docker-compose", combined):
            return "docker"
        # Try JSON detect
        stripped = output.strip()
        if stripped.startswith(("{", "[")):
            try:
                json.loads(stripped)
                return "json"
            except (json.JSONDecodeError, ValueError):
                pass
        return "generic"

    # -----------------------------------------------------------------------
    # Per-type handlers
    # -----------------------------------------------------------------------

    def _truncate_test(self, output: str, max_chars: int) -> str:
        lines = output.splitlines()
        summary_lines = []
        failure_blocks = []
        current_failure: list[str] = []
        in_failure = False

        for line in lines:
            # Capture summary stats
            if re.search(r"passed|failed|error|warnings|collected", line, re.I):
                summary_lines.append(line)
            # Detect failure block start
            if re.search(r"^(FAILED|ERROR|FAIL)\b|AssertionError|Exception:|Error:", line):
                if current_failure and len(failure_blocks) < 5:
                    failure_blocks.append(self._abbreviate_stack(current_failure))
                current_failure = [line]
                in_failure = True
            elif in_failure:
                current_failure.append(line)
                if len(current_failure) > 20:
                    in_failure = False
        if current_failure and len(failure_blocks) < 5:
            failure_blocks.append(self._abbreviate_stack(current_failure))

        parts = ["=== TEST SUMMARY ==="]
        parts.extend(summary_lines[-10:])
        if failure_blocks:
            parts.append(f"\n=== FIRST {len(failure_blocks)} FAILURES ===")
            for i, block in enumerate(failure_blocks, 1):
                parts.append(f"\n--- Failure {i} ---")
                parts.append(block)
        return "\n".join(parts)

    def _truncate_build(self, output: str, max_chars: int) -> str:
        lines = output.splitlines()
        errors, warnings, tail = [], [], []
        for line in lines:
            if re.search(r"\berror\b|\[ERROR\]|error:", line, re.I):
                errors.append(line)
            elif re.search(r"\bwarning\b|\[WARNING\]|warn:", line, re.I):
                warnings.append(line)
        tail = lines[-20:]
        parts = []
        if errors:
            parts.append(f"=== ERRORS ({len(errors)}) ===")
            parts.extend(errors[:30])
        if warnings:
            parts.append(f"\n=== WARNINGS ({len(warnings)}) ===")
            parts.extend(warnings[:10])
        parts.append("\n=== LAST 20 LINES ===")
        parts.extend(tail)
        return "\n".join(parts)

    def _truncate_lint(self, output: str, max_chars: int) -> str:
        lines = output.splitlines()
        issue_lines = [l for l in lines if re.search(r"error|warning|note|E\d{3,}|W\d{3,}", l, re.I)]
        total = len(issue_lines)
        parts = [f"=== LINT: {total} issues found ==="]
        parts.extend(issue_lines[:10])
        if total > 10:
            parts.append(f"... and {total - 10} more issues")
        return "\n".join(parts)

    def _truncate_git(self, output: str, max_chars: int) -> str:
        lines = output.splitlines()
        changed = [l for l in lines if re.search(r"^\s*(modified|new file|deleted|renamed|M |A |D |\?\?)", l)]
        commits = [l for l in lines if re.search(r"^commit [0-9a-f]{7,}|^[0-9a-f]{7} ", l)]
        hunk_heads = [l for l in lines if l.startswith("@@")]
        parts = []
        if changed:
            parts.append(f"=== FILES CHANGED ({len(changed)}) ===")
            parts.extend(changed[:20])
        if commits:
            parts.append(f"\n=== RECENT COMMITS ({len(commits)}) ===")
            parts.extend(commits[:10])
        if hunk_heads:
            parts.append(f"\n=== DIFF HUNKS ({len(hunk_heads)}) ===")
            parts.extend(hunk_heads[:10])
        if not parts:
            # fallback: first+last
            return self._truncate_generic(output, max_chars)
        return "\n".join(parts)

    def _truncate_docker(self, output: str, max_chars: int) -> str:
        lines = output.splitlines()
        status_lines = [l for l in lines if re.search(r"(Up|Exited|healthy|unhealthy|starting|Running|Error|error)", l, re.I)]
        parts = [f"=== CONTAINER STATUS ({len(status_lines)}) ==="]
        parts.extend(status_lines[:20])
        error_lines = [l for l in lines if re.search(r"error|Error|ERROR|failed|Failed", l)]
        if error_lines:
            parts.append(f"\n=== ERRORS ({len(error_lines)}) ===")
            parts.extend(error_lines[:10])
        return "\n".join(parts)

    def _truncate_json(self, output: str, max_chars: int) -> str:
        try:
            data = json.loads(output.strip())
        except (json.JSONDecodeError, ValueError):
            return self._truncate_generic(output, max_chars)
        if isinstance(data, dict):
            parts = ["=== JSON (top-level keys) ==="]
            for k, v in list(data.items())[:15]:
                sample = repr(v)[:80]
                parts.append(f"  {k}: {sample}")
            return "\n".join(parts)
        if isinstance(data, list):
            parts = [f"=== JSON array ({len(data)} items) ==="]
            for item in data[:5]:
                parts.append("  " + repr(item)[:120])
            if len(data) > 5:
                parts.append(f"  ... and {len(data) - 5} more items")
            return "\n".join(parts)
        return str(data)[:max_chars]

    def _truncate_generic(self, output: str, max_chars: int) -> str:
        lines = output.splitlines()
        total = len(lines)
        important_pat = re.compile(r"FAIL|ERROR|panic|CRITICAL|WARN|PASS|coverage|Exception", re.I)
        important = [l for l in lines if important_pat.search(l)]

        # Reserve space for key lines first — they are the highest signal
        key_section = ""
        if important:
            key_section = f"\n=== KEY LINES ({len(important)}) ===\n" + "\n".join(important[:20])

        # Fill remaining budget with head + tail
        budget = max_chars - len(key_section) - 60  # 60 for omission marker
        if budget < 100:
            budget = 100
        head_n = max(1, int(total * 0.40))
        tail_n = max(1, int(total * 0.20))

        head_text = "\n".join(lines[:head_n])
        tail_text = "\n".join(lines[-tail_n:])

        # Trim head/tail to fit budget
        half = budget // 2
        if len(head_text) > half:
            head_text = head_text[:half] + " ..."
        if len(tail_text) > half:
            tail_text = "... " + tail_text[-half:]

        omitted = total - head_n - tail_n
        result = head_text + f"\n\n... [{omitted} lines omitted] ...\n\n" + tail_text
        if key_section:
            result += key_section
        return result

    # -----------------------------------------------------------------------
    # Helpers
    # -----------------------------------------------------------------------

    def _abbreviate_stack(self, lines: list[str]) -> str:
        """Keep first 3 + last 3 lines of a failure block."""
        if len(lines) <= 6:
            return "\n".join(lines)
        return "\n".join(lines[:3] + ["  ..."] + lines[-3:])
