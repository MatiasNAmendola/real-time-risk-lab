"""Tests for smart_truncator.py — 10+ cases, one per command type."""

import unittest
from smart_truncator import SmartTruncator


class TestSmartTruncator(unittest.TestCase):

    def setUp(self):
        self.t = SmartTruncator()

    # --- detect_command_type ---

    def test_detect_pytest(self):
        self.assertEqual(self.t.detect_command_type("pytest -v", ""), "test")

    def test_detect_mvn_test(self):
        self.assertEqual(self.t.detect_command_type("mvn test", ""), "test")

    def test_detect_go_test(self):
        self.assertEqual(self.t.detect_command_type("go test ./...", ""), "test")

    def test_detect_gradle_build(self):
        self.assertEqual(self.t.detect_command_type("gradle build", ""), "build")

    def test_detect_eslint(self):
        self.assertEqual(self.t.detect_command_type("eslint src/", ""), "lint")

    def test_detect_git_status(self):
        self.assertEqual(self.t.detect_command_type("git status", ""), "git")

    def test_detect_docker(self):
        self.assertEqual(self.t.detect_command_type("docker compose up", ""), "docker")

    def test_detect_json_output(self):
        self.assertEqual(self.t.detect_command_type("", '{"key":"value"}'), "json")

    def test_detect_generic_fallback(self):
        self.assertEqual(self.t.detect_command_type("echo hello", "hello world"), "generic")

    # --- truncation: no-op for short output ---

    def test_no_truncation_when_short(self):
        short = "a\nb\nc"
        result = self.t.truncate(short, "pytest", max_chars=4000)
        self.assertEqual(result, short)

    # --- truncation: test output ---

    def test_truncate_test_extracts_failures(self):
        output = "\n".join([
            "collecting ...",
            "FAILED tests/test_foo.py::test_bar - AssertionError: expected 1 got 2",
            "    at line 42",
            "    AssertionError: expected 1 got 2",
            "1 passed, 1 failed in 0.5s",
        ] + ["noise line"] * 200)
        result = self.t.truncate(output, "pytest", max_chars=4000)
        self.assertIn("FAILED", result)
        self.assertIn("passed", result)

    # --- truncation: build output ---

    def test_truncate_build_extracts_errors(self):
        lines = [f"[INFO] processing {i}" for i in range(100)]
        lines.append("[ERROR] Compilation failure: cannot find symbol")
        lines.append("[WARNING] deprecated API used at line 55")
        output = "\n".join(lines)
        result = self.t.truncate(output, "mvn build", max_chars=500)
        self.assertIn("ERROR", result)

    # --- truncation: lint output ---

    def test_truncate_lint_counts_issues(self):
        issues = [f"src/foo.py:line {i}: error E501 line too long" for i in range(50)]
        output = "\n".join(issues)
        result = self.t.truncate(output, "ruff check .", max_chars=500)
        self.assertIn("50", result)

    # --- truncation: git output ---

    def test_truncate_git_extracts_files(self):
        output = "\n".join([
            "On branch main",
            "Changes not staged for commit:",
            "        modified:   src/main/java/Foo.java",
            "        modified:   src/main/java/Bar.java",
            "nothing added to commit",
        ] + ["noise"] * 100)
        result = self.t.truncate(output, "git status", max_chars=500)
        self.assertIn("modified", result)

    # --- truncation: docker output ---

    def test_truncate_docker_extracts_status(self):
        output = "\n".join([
            "NAME     COMMAND   SERVICE   STATUS    PORTS",
            "api      java      api       Up        8080/tcp",
            "db       postgres  db        Exited",
            "Error: container 'db' exited with code 1",
        ] + ["Step 1/20: FROM openjdk"] * 80)
        result = self.t.truncate(output, "docker compose ps", max_chars=500)
        self.assertIn("Exited", result)

    # --- truncation: json output ---

    def test_truncate_json_dict(self):
        import json
        data = {f"key_{i}": f"value_{i}" for i in range(30)}
        output = json.dumps(data)
        result = self.t.truncate(output, "", max_chars=200)
        self.assertIn("key_0", result)

    # --- truncation: generic ---

    def test_truncate_generic_keeps_error_lines(self):
        lines = [f"info line {i}" for i in range(200)]
        lines[100] = "CRITICAL: out of memory"
        output = "\n".join(lines)
        result = self.t.truncate(output, "some random command", max_chars=500)
        self.assertIn("CRITICAL", result)

    # --- max_chars enforcement ---

    def test_result_never_exceeds_max_chars_plus_trailer(self):
        big = "x" * 100_000
        result = self.t.truncate(big, "", max_chars=1000)
        # Allow for the truncation trailer (max ~60 chars)
        self.assertLessEqual(len(result), 1100)


if __name__ == "__main__":
    unittest.main()
