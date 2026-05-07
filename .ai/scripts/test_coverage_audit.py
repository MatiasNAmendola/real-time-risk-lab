#!/usr/bin/env python3
"""
test_coverage_audit.py — Unit tests for coverage-audit.py

Run with:
    python3 -m unittest .ai/scripts/test_coverage_audit.py
    # or from repo root:
    python3 -m unittest discover -s .ai/scripts -p 'test_coverage_audit.py'
"""
from __future__ import annotations

import json
import os
import sys
import tempfile
import unittest
from pathlib import Path

# Load coverage-audit.py via importlib since the filename has a hyphen
import importlib.util as _ilu

SCRIPT_DIR = Path(__file__).resolve().parent
_spec = _ilu.spec_from_file_location("coverage_audit", SCRIPT_DIR / "coverage-audit.py")
ca = _ilu.module_from_spec(_spec)  # type: ignore[arg-type]
_spec.loader.exec_module(ca)  # type: ignore[union-attr]
sys.modules["coverage_audit"] = ca


def _write(path: Path, content: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content, encoding="utf-8")


class TestParseFrontmatter(unittest.TestCase):
    def test_valid_frontmatter(self):
        text = "---\ntitle: My Doc\ntags: [foo, bar]\n---\n# Body"
        fm = ca.parse_frontmatter(text)
        self.assertEqual(fm["title"], "My Doc")
        self.assertIsInstance(fm["tags"], list)
        self.assertIn("foo", fm["tags"])

    def test_missing_frontmatter(self):
        text = "# No frontmatter\nsome content"
        fm = ca.parse_frontmatter(text)
        self.assertEqual(fm, {})

    def test_partial_frontmatter(self):
        text = "---\nname: my-skill\n---\n"
        fm = ca.parse_frontmatter(text)
        self.assertEqual(fm["name"], "my-skill")
        self.assertNotIn("tags", fm)


class TestWikilinkIntegrity(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.vault = Path(self.tmp.name) / "vault"
        self.vault.mkdir()
        # Patch VAULT_DIR
        self._orig = ca.VAULT_DIR
        ca.VAULT_DIR = self.vault

    def tearDown(self):
        ca.VAULT_DIR = self._orig
        self.tmp.cleanup()

    def test_no_broken_links(self):
        _write(self.vault / "doc-a.md", "---\ntitle: A\ntags: [test]\n---\n[[doc-b]]")
        _write(self.vault / "doc-b.md", "---\ntitle: B\ntags: [test]\n---\ncontent")
        # Minimal docs dir patch
        orig_docs = ca.DOCS_DIR
        ca.DOCS_DIR = Path(self.tmp.name) / "docs"
        result = ca.audit_docs()
        ca.DOCS_DIR = orig_docs
        wi = result["wikilink_integrity"]
        self.assertEqual(wi["broken"], 0)
        self.assertEqual(wi["total"], 1)
        self.assertEqual(wi["pct"], 100)

    def test_broken_link_detected(self):
        _write(self.vault / "doc-a.md", "---\ntitle: A\ntags: [x]\n---\n[[nonexistent-page]]")
        orig_docs = ca.DOCS_DIR
        ca.DOCS_DIR = Path(self.tmp.name) / "docs"
        result = ca.audit_docs()
        ca.DOCS_DIR = orig_docs
        wi = result["wikilink_integrity"]
        self.assertEqual(wi["broken"], 1)
        self.assertEqual(wi["pct"], 0)


class TestReadmePresence(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.root = Path(self.tmp.name)
        self._orig_root = ca.REPO_ROOT
        ca.REPO_ROOT = self.root
        ca.VAULT_DIR = self.root / "vault"
        ca.DOCS_DIR = self.root / "docs"

    def tearDown(self):
        ca.REPO_ROOT = self._orig_root
        ca.VAULT_DIR = self._orig_root / "vault"
        ca.DOCS_DIR = self._orig_root / "docs"
        self.tmp.cleanup()

    def test_readme_present(self):
        poc_dir = self.root / "poc" / "my-poc"
        poc_dir.mkdir(parents=True)
        _write(poc_dir / "README.md", "# My PoC")
        result = ca.audit_docs()
        rp = result["readme_presence"]
        self.assertGreater(rp["present"], 0)
        self.assertEqual(rp["pct"], 100)

    def test_readme_missing(self):
        poc_dir = self.root / "poc" / "my-poc"
        poc_dir.mkdir(parents=True)
        # No README.md created
        result = ca.audit_docs()
        rp = result["readme_presence"]
        self.assertIn("poc/my-poc", rp["missing"])
        self.assertLess(rp["pct"], 100)


class TestAdrCompleteness(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.root = Path(self.tmp.name)
        self._orig_root = ca.REPO_ROOT
        ca.REPO_ROOT = self.root
        ca.VAULT_DIR = self.root / "vault"
        ca.DOCS_DIR = self.root / "docs"

    def tearDown(self):
        ca.REPO_ROOT = self._orig_root
        ca.VAULT_DIR = self._orig_root / "vault"
        ca.DOCS_DIR = self._orig_root / "docs"
        self.tmp.cleanup()

    def test_complete_adr(self):
        adr_dir = self.root / "vault" / "02-Decisions"
        adr_dir.mkdir(parents=True)
        _write(adr_dir / "0001-test.md",
               "# ADR\n## Context\n...\n## Decision\n...\n## Alternatives considered\n...\n## Consequences\n...")
        result = ca.audit_docs()
        ac = result["adr_completeness"]
        self.assertEqual(ac["complete"], 1)
        self.assertEqual(ac["pct"], 100)

    def test_partial_adr(self):
        adr_dir = self.root / "vault" / "02-Decisions"
        adr_dir.mkdir(parents=True)
        _write(adr_dir / "0001-incomplete.md", "# ADR\n## Context\n...\n## Decision\n...")
        result = ca.audit_docs()
        ac = result["adr_completeness"]
        self.assertEqual(ac["complete"], 0)
        self.assertIn("0001-incomplete.md", ac["partial"])


class TestSkillFrontmatterValidity(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.root = Path(self.tmp.name)
        self._orig_prim = ca.PRIMITIVES_DIR
        ca.PRIMITIVES_DIR = self.root / ".ai" / "primitives"
        ca.ADAPTERS_DIR = self.root / ".ai" / "adapters"
        ca.SETTINGS_JSON = self.root / ".claude" / "settings.json"
        ca.LOGS_DIR = self.root / ".ai" / "logs"
        ca.REPO_ROOT = self.root

    def tearDown(self):
        orig = Path(__file__).resolve().parent.parent.parent
        ca.PRIMITIVES_DIR = orig / ".ai" / "primitives"
        ca.ADAPTERS_DIR = orig / ".ai" / "adapters"
        ca.SETTINGS_JSON = orig / ".claude" / "settings.json"
        ca.LOGS_DIR = orig / ".ai" / "logs"
        ca.REPO_ROOT = orig
        self.tmp.cleanup()

    def test_valid_skill(self):
        skills_dir = self.root / ".ai" / "primitives" / "skills"
        skills_dir.mkdir(parents=True)
        _write(skills_dir / "my-skill.md",
               "---\nname: my-skill\nintent: do things\ninputs: [x]\n"
               "preconditions: [y]\npostconditions: [z]\nrelated_rules: []\ntags: [test]\n---\n")
        result = ca.audit_primitives()
        sf = result["skill_frontmatter"]
        self.assertEqual(sf["valid"], 1)
        self.assertEqual(sf["pct"], 100)

    def test_invalid_skill_missing_fields(self):
        skills_dir = self.root / ".ai" / "primitives" / "skills"
        skills_dir.mkdir(parents=True)
        _write(skills_dir / "bad-skill.md", "---\nname: bad-skill\n---\n# missing most fields")
        result = ca.audit_primitives()
        sf = result["skill_frontmatter"]
        self.assertEqual(sf["valid"], 0)
        self.assertIn("bad-skill.md", sf["invalid"])
        self.assertGreater(len(sf["missing_fields"].get("bad-skill.md", [])), 0)


class TestWorkflowSkillLinkage(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.root = Path(self.tmp.name)
        ca.PRIMITIVES_DIR = self.root / ".ai" / "primitives"
        ca.ADAPTERS_DIR = self.root / ".ai" / "adapters"
        ca.SETTINGS_JSON = self.root / ".claude" / "settings.json"
        ca.LOGS_DIR = self.root / ".ai" / "logs"
        ca.REPO_ROOT = self.root

    def tearDown(self):
        orig = Path(__file__).resolve().parent.parent.parent
        ca.PRIMITIVES_DIR = orig / ".ai" / "primitives"
        ca.ADAPTERS_DIR = orig / ".ai" / "adapters"
        ca.SETTINGS_JSON = orig / ".claude" / "settings.json"
        ca.LOGS_DIR = orig / ".ai" / "logs"
        ca.REPO_ROOT = orig
        self.tmp.cleanup()

    def test_workflow_references_existing_skill(self):
        prims = self.root / ".ai" / "primitives"
        (prims / "skills").mkdir(parents=True)
        (prims / "workflows").mkdir(parents=True)
        _write(prims / "skills" / "add-fraud-rule.md",
               "---\nname: add-fraud-rule\nintent: x\ninputs: []\n"
               "preconditions: []\npostconditions: []\nrelated_rules: []\ntags: []\n---\n")
        _write(prims / "workflows" / "fraud-review.md",
               "# Workflow\nUses `add-fraud-rule` to add rules.\n")
        result = ca.audit_primitives()
        wl = result["workflow_skill_linkage"]
        self.assertEqual(wl["ok"], 1)
        self.assertEqual(wl["pct"], 100)


class TestCrossAxisMatrix(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.root = Path(self.tmp.name)
        self._orig_root = ca.REPO_ROOT
        ca.REPO_ROOT = self.root
        ca.VAULT_DIR = self.root / "vault"
        ca.DOCS_DIR = self.root / "docs"
        ca.PRIMITIVES_DIR = self.root / ".ai" / "primitives"

    def tearDown(self):
        ca.REPO_ROOT = self._orig_root
        ca.VAULT_DIR = self._orig_root / "vault"
        ca.DOCS_DIR = self._orig_root / "docs"
        ca.PRIMITIVES_DIR = self._orig_root / ".ai" / "primitives"
        self.tmp.cleanup()

    def test_cross_axis_with_poc(self):
        poc_dir = self.root / "poc" / "my-poc"
        poc_dir.mkdir(parents=True)
        _write(poc_dir / "README.md", "# My PoC")
        _write(poc_dir / "Main.java", "public class Main {}")
        _write(poc_dir / "MainTest.java", "@Test public void test() {}")
        result = ca.audit_cross_axis({}, {}, {})
        matrix = result["matrix"]
        areas = [r["area"] for r in matrix]
        self.assertIn("poc/my-poc", areas)

    def test_cross_axis_has_overall(self):
        result = ca.audit_cross_axis({}, {}, {})
        self.assertIn("project_overall_pct", result)
        self.assertIsInstance(result["project_overall_pct"], int)

    def test_matrix_row_structure(self):
        poc_dir = self.root / "poc" / "sample"
        poc_dir.mkdir(parents=True)
        result = ca.audit_cross_axis({}, {}, {})
        for row in result["matrix"]:
            self.assertIn("area", row)
            self.assertIn("docs", row)
            self.assertIn("overall", row)


class TestHookWiring(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.root = Path(self.tmp.name)
        ca.PRIMITIVES_DIR = self.root / ".ai" / "primitives"
        ca.ADAPTERS_DIR = self.root / ".ai" / "adapters"
        ca.SETTINGS_JSON = self.root / ".claude" / "settings.json"
        ca.LOGS_DIR = self.root / ".ai" / "logs"
        ca.REPO_ROOT = self.root

    def tearDown(self):
        orig = Path(__file__).resolve().parent.parent.parent
        ca.PRIMITIVES_DIR = orig / ".ai" / "primitives"
        ca.ADAPTERS_DIR = orig / ".ai" / "adapters"
        ca.SETTINGS_JSON = orig / ".claude" / "settings.json"
        ca.LOGS_DIR = orig / ".ai" / "logs"
        ca.REPO_ROOT = orig
        self.tmp.cleanup()

    def test_hook_wired_via_settings(self):
        hooks_dir = self.root / ".ai" / "primitives" / "hooks"
        hooks_dir.mkdir(parents=True)
        settings_dir = self.root / ".claude"
        settings_dir.mkdir(parents=True)
        _write(hooks_dir / "pre-tool-use-block-secrets.md",
               "---\nname: pre-tool-use-block-secrets\ntrigger: PreToolUse\n---\n# Hook\n")
        settings_data = {"hooks": {"PreToolUse": [{"command": "echo test"}]}}
        _write(settings_dir / "settings.json", json.dumps(settings_data))
        result = ca.audit_primitives()
        hw = result["hook_wiring"]
        self.assertEqual(hw["wired"], 1)
        self.assertEqual(hw["pct"], 100)


if __name__ == "__main__":
    unittest.main(verbosity=2)
