#!/usr/bin/env python3
"""
test_workflow_runner.py — Unit tests for workflow-runner.py

Run with:
    python3 -m unittest .ai/scripts/test_workflow_runner.py
    # or from repo root:
    python3 -m unittest discover -s .ai/scripts -p "test_workflow_runner.py"
"""
import json
import os
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch, MagicMock

# Add scripts directory to path and import via importlib (file uses hyphen in name)
SCRIPT_DIR = Path(__file__).resolve().parent
sys.path.insert(0, str(SCRIPT_DIR))

import importlib.util as _ilu
_spec = _ilu.spec_from_file_location("workflow_runner", SCRIPT_DIR / "workflow-runner.py")
_mod = _ilu.module_from_spec(_spec)
_spec.loader.exec_module(_mod)
import sys as _sys
_sys.modules["workflow_runner"] = _mod
wr = _mod


SAMPLE_WORKFLOW_HEADING = """\
---
name: test-workflow
description: A test workflow
---

# Workflow: test-workflow

## 1. Choose pattern

Choose the right communication pattern.

## 2. Implement

Write the code following the skill.

## 3. Test

Run ATDD tests and verify GREEN.
"""

SAMPLE_WORKFLOW_NUMBERED = """\
---
name: test-numbered
---

# Workflow: test-numbered

Some intro text.

1. First step title
   Content of first step.

2. Second step title
   Content of second step.

3. Third step
"""

SAMPLE_WORKFLOW_NO_STEPS = """\
---
name: empty-workflow
---

# Workflow: empty-workflow

No steps defined here yet.
"""


class TestParseFrontmatter(unittest.TestCase):
    def test_with_frontmatter(self):
        meta, body = wr._parse_frontmatter(SAMPLE_WORKFLOW_HEADING)
        self.assertEqual(meta.get("name"), "test-workflow")
        self.assertIn("# Workflow", body)

    def test_without_frontmatter(self):
        text = "# Just a heading\n\nSome content."
        meta, body = wr._parse_frontmatter(text)
        self.assertEqual(meta, {})
        self.assertEqual(body, text)

    def test_empty_string(self):
        meta, body = wr._parse_frontmatter("")
        self.assertEqual(meta, {})
        self.assertEqual(body, "")


class TestParseSteps(unittest.TestCase):
    def test_heading_style(self):
        _, body = wr._parse_frontmatter(SAMPLE_WORKFLOW_HEADING)
        steps = wr.parse_steps(body)
        self.assertEqual(len(steps), 3)
        self.assertEqual(steps[0]["number"], 1)
        self.assertEqual(steps[0]["title"], "Choose pattern")
        self.assertEqual(steps[1]["number"], 2)
        self.assertEqual(steps[1]["title"], "Implement")
        self.assertEqual(steps[2]["number"], 3)
        self.assertEqual(steps[2]["title"], "Test")

    def test_numbered_list_style(self):
        _, body = wr._parse_frontmatter(SAMPLE_WORKFLOW_NUMBERED)
        steps = wr.parse_steps(body)
        self.assertEqual(len(steps), 3)
        self.assertEqual(steps[0]["title"], "First step title")
        self.assertEqual(steps[1]["title"], "Second step title")
        self.assertEqual(steps[2]["title"], "Third step")

    def test_no_steps(self):
        _, body = wr._parse_frontmatter(SAMPLE_WORKFLOW_NO_STEPS)
        steps = wr.parse_steps(body)
        self.assertEqual(steps, [])

    def test_step_content_preserved(self):
        _, body = wr._parse_frontmatter(SAMPLE_WORKFLOW_HEADING)
        steps = wr.parse_steps(body)
        # Step 1 content should contain the description text
        self.assertIn("pattern", steps[0]["content"].lower())

    def test_heading_with_step_prefix(self):
        text = "## Step 1. Init\nContent here.\n## Step 2. Build\nMore content."
        steps = wr.parse_steps(text)
        self.assertEqual(len(steps), 2)
        self.assertEqual(steps[0]["title"], "Init")
        self.assertEqual(steps[1]["title"], "Build")


class TestRouteSkill(unittest.TestCase):
    def test_returns_none_on_subprocess_failure(self):
        with patch("workflow_runner.subprocess.run") as mock_run:
            mock_run.side_effect = Exception("subprocess failed")
            result = wr.route_skill_for_step("some title", "some content")
            self.assertIsNone(result)

    def test_returns_none_on_empty_results(self):
        with patch("workflow_runner.subprocess.run") as mock_run:
            mock_result = MagicMock()
            mock_result.returncode = 0
            mock_result.stdout = json.dumps({"query": "test", "results": []})
            mock_run.return_value = mock_result
            result = wr.route_skill_for_step("some title", "")
            self.assertIsNone(result)

    def test_returns_top_result(self):
        fake_result = {"name": "add-kafka-consumer", "score": 0.82, "intent": "kafka stuff"}
        with patch("workflow_runner.subprocess.run") as mock_run:
            mock_result = MagicMock()
            mock_result.returncode = 0
            mock_result.stdout = json.dumps({"query": "test", "results": [fake_result]})
            mock_run.return_value = mock_result
            result = wr.route_skill_for_step("add kafka listener", "")
            self.assertIsNotNone(result)
            self.assertEqual(result["name"], "add-kafka-consumer")
            self.assertAlmostEqual(result["score"], 0.82)


class TestLogStep(unittest.TestCase):
    def test_creates_log_file(self):
        with tempfile.TemporaryDirectory() as tmpdir:
            log_path = Path(tmpdir) / "test-run.jsonl"
            # Patch LOGS_DIR so mkdir doesn't need the real path
            with patch.object(wr, "LOGS_DIR", Path(tmpdir)):
                wr.log_step(log_path, {"ts": "2026-01-01T00:00:00Z", "step": 1})
            self.assertTrue(log_path.exists())
            with log_path.open() as f:
                line = json.loads(f.readline())
            self.assertEqual(line["step"], 1)

    def test_appends_multiple_events(self):
        with tempfile.TemporaryDirectory() as tmpdir:
            log_path = Path(tmpdir) / "multi-run.jsonl"
            with patch.object(wr, "LOGS_DIR", Path(tmpdir)):
                wr.log_step(log_path, {"step": 1})
                wr.log_step(log_path, {"step": 2})
            with log_path.open() as f:
                lines = f.readlines()
            self.assertEqual(len(lines), 2)
            self.assertEqual(json.loads(lines[0])["step"], 1)
            self.assertEqual(json.loads(lines[1])["step"], 2)


class TestLoadWorkflow(unittest.TestCase):
    def test_load_real_workflow(self):
        """Test with the actual add-comm-pattern.md from the project."""
        repo_root = SCRIPT_DIR.parent.parent
        workflows_dir = repo_root / ".ai" / "primitives" / "workflows"
        if not (workflows_dir / "add-comm-pattern.md").exists():
            self.skipTest("add-comm-pattern.md not found")
        meta, steps = wr.load_workflow("add-comm-pattern")
        self.assertIsInstance(steps, list)
        # Workflow has at least one step
        self.assertGreater(len(steps), 0)

    def test_load_nonexistent_raises(self):
        with self.assertRaises(FileNotFoundError):
            wr.load_workflow("nonexistent-workflow-xyz")

    def test_load_with_md_extension(self):
        """load_workflow should accept name with .md suffix."""
        repo_root = SCRIPT_DIR.parent.parent
        workflows_dir = repo_root / ".ai" / "primitives" / "workflows"
        if not (workflows_dir / "add-comm-pattern.md").exists():
            self.skipTest("add-comm-pattern.md not found")
        meta, steps = wr.load_workflow("add-comm-pattern.md")
        self.assertIsInstance(steps, list)


class TestPrintPlan(unittest.TestCase):
    def test_print_plan_no_crash(self):
        """print_plan should run without raising exceptions."""
        steps = [
            {"number": 1, "title": "Step One", "content": "Do something."},
            {"number": 2, "title": "Step Two", "content": ""},
        ]
        skill_map = {
            1: {"name": "add-kafka-consumer", "score": 0.82},
            2: None,
        }
        import io
        from contextlib import redirect_stdout
        buf = io.StringIO()
        with redirect_stdout(buf):
            wr.print_plan("test-workflow", steps, skill_map)
        output = buf.getvalue()
        self.assertIn("Step 1", output)
        self.assertIn("Step 2", output)
        self.assertIn("add-kafka-consumer", output)
        self.assertIn("no skill match", output)


if __name__ == "__main__":
    unittest.main()
