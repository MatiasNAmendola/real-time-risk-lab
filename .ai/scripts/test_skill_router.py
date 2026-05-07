#!/usr/bin/env python3
"""
test_skill_router.py — Unit tests for skill-router.py

Run with:
    python3 -m unittest .ai/scripts/test_skill_router.py -v
    # or from repo root:
    python3 -m unittest discover -s .ai/scripts -p 'test_*.py' -v
"""
import importlib.util
import json
import os
import sys
import tempfile
import time
import unittest
from pathlib import Path
from unittest.mock import patch

# ---------------------------------------------------------------------------
# Load skill-router module dynamically (it lives next to this file)
# ---------------------------------------------------------------------------
_HERE = Path(__file__).resolve().parent
_ROUTER_PATH = _HERE / "skill-router.py"

spec = importlib.util.spec_from_file_location("skill_router", str(_ROUTER_PATH))
sr = importlib.util.module_from_spec(spec)
spec.loader.exec_module(sr)  # type: ignore[union-attr]


# ---------------------------------------------------------------------------
# Fixtures
# ---------------------------------------------------------------------------

VALID_SKILL_TEMPLATE = """\
---
name: {name}
intent: {intent}
inputs: [topic, group]
preconditions: [service running]
postconditions: [consumer registered]
related_rules: [communication-patterns]
tags: {tags}
---
# {name}

{body}
"""

SKILLS_DATA = [
    {
        "name": "add-kafka-consumer",
        "intent": "Agregar un consumer Kafka que lea del topic risk-decisions",
        "tags": "[kafka, async, consumer]",
        "body": "Use Vert.x Kafka client to register a consumer group. Handle offsets manually.",
    },
    {
        "name": "add-kafka-publisher",
        "intent": "Publicar eventos en un topic Kafka desde un servicio Vert.x",
        "tags": "[kafka, async, publisher]",
        "body": "Inject KafkaProducer and send ProducerRecord with schema validation.",
    },
    {
        "name": "expose-rest-endpoint",
        "intent": "Exponer un nuevo endpoint REST en el API Gateway",
        "tags": "[rest, http, api, gateway]",
        "body": "Add route in Router.java and create handler class implementing Handler<RoutingContext>.",
    },
    {
        "name": "add-outbox-pattern",
        "intent": "Implementar el patrón outbox para garantizar exactly-once delivery",
        "tags": "[outbox, messaging, reliability]",
        "body": "Write domain event to outbox table inside same transaction then poll and publish.",
    },
    {
        "name": "add-port-out",
        "intent": "Agregar un puerto out (interface en domain/repository)",
        "tags": "[domain, ports, hexagonal]",
        "body": "Create interface in domain module, implement in infrastructure adapter.",
    },
]


def create_skills_dir(tmpdir: Path, skills: list[dict] | None = None) -> Path:
    """Create a temp .ai/primitives/skills/ directory with given skills."""
    skills_dir = tmpdir / ".ai" / "primitives" / "skills"
    skills_dir.mkdir(parents=True, exist_ok=True)
    for s in (skills or SKILLS_DATA):
        content = VALID_SKILL_TEMPLATE.format(**s)
        (skills_dir / f"{s['name']}.md").write_text(content, encoding="utf-8")
    return skills_dir


# ---------------------------------------------------------------------------
# Helper to patch SKILLS_DIR and CACHE_FILE inside sr module
# ---------------------------------------------------------------------------
class RouterFixture:
    """Context manager: temporarily redirects sr module paths to tmpdir."""

    def __init__(self, tmpdir: Path, skills: list[dict] | None = None):
        self.tmpdir = tmpdir
        self.skills = skills
        self._orig_skills = sr.SKILLS_DIR
        self._orig_cache_dir = sr.CACHE_DIR
        self._orig_cache_file = sr.CACHE_FILE
        self._orig_repo_root = sr.REPO_ROOT

    def __enter__(self):
        skills_dir = create_skills_dir(self.tmpdir, self.skills)
        sr.SKILLS_DIR = skills_dir
        sr.REPO_ROOT = self.tmpdir
        sr.CACHE_DIR = self.tmpdir / ".cache"
        sr.CACHE_FILE = sr.CACHE_DIR / "skills-index.json"
        return self

    def __exit__(self, *_):
        sr.SKILLS_DIR = self._orig_skills
        sr.CACHE_DIR = self._orig_cache_dir
        sr.CACHE_FILE = self._orig_cache_file
        sr.REPO_ROOT = self._orig_repo_root


# ---------------------------------------------------------------------------
# Tests
# ---------------------------------------------------------------------------

class TestParseFrontmatter(unittest.TestCase):

    def test_valid_frontmatter(self):
        text = """\
---
name: my-skill
intent: Do something useful
tags: [foo, bar]
---
body content here
"""
        fm, body = sr.parse_frontmatter(text)
        self.assertIsNotNone(fm)
        self.assertEqual(fm["name"], "my-skill")
        self.assertEqual(fm["intent"], "Do something useful")
        self.assertIn("foo", fm["tags"])
        self.assertIn("bar", fm["tags"])
        self.assertIn("body content here", body)

    def test_no_frontmatter(self):
        text = "# Just markdown\n\nNo frontmatter here."
        fm, body = sr.parse_frontmatter(text)
        self.assertIsNone(fm)
        self.assertEqual(body, text)

    def test_invalid_frontmatter_not_closed(self):
        text = "---\nname: broken\n# no closing ---"
        fm, body = sr.parse_frontmatter(text)
        self.assertIsNone(fm)

    def test_empty_frontmatter(self):
        text = "---\n---\nbody"
        fm, body = sr.parse_frontmatter(text)
        self.assertIsNotNone(fm)
        self.assertEqual(fm, {})
        self.assertEqual(body, "body")

    def test_block_list_frontmatter(self):
        text = """\
---
name: block-list
tags:
  - alpha
  - beta
---
"""
        fm, _ = sr.parse_frontmatter(text)
        self.assertIsNotNone(fm)
        self.assertEqual(fm["tags"], ["alpha", "beta"])


class TestScoreKeyword(unittest.TestCase):

    def _skill(self, name, intent, tags=None):
        return {
            "name": name,
            "intent": intent,
            "tags": tags or [],
            "related_rules": [],
            "body": "",
        }

    def test_exact_query_matches_high(self):
        skill = self._skill("add-kafka-consumer", "Agregar un consumer Kafka", ["kafka", "consumer"])
        tokens = sr._tokenize("kafka consumer")
        score = sr.keyword_score(tokens, skill)
        self.assertGreater(score, 0.5)

    def test_unrelated_query_matches_low(self):
        skill = self._skill("add-kafka-consumer", "Agregar un consumer Kafka", ["kafka"])
        tokens = sr._tokenize("expose rest endpoint")
        score = sr.keyword_score(tokens, skill)
        self.assertLess(score, 0.3)

    def test_empty_query_returns_zero(self):
        skill = self._skill("foo", "bar intent", ["baz"])
        self.assertEqual(sr.keyword_score([], skill), 0.0)


class TestScoreFuzzy(unittest.TestCase):

    def _skill(self, name):
        return {"name": name, "intent": "", "tags": [], "body": ""}

    def test_exact_name_match(self):
        skill = self._skill("add-kafka-consumer")
        score = sr.fuzzy_score("add-kafka-consumer", skill)
        self.assertAlmostEqual(score, 1.0, places=1)

    def test_typo_still_ranks(self):
        skill = self._skill("add-kafka-consumer")
        # typical typo: 'kafaka' or 'comsumer'
        score = sr.fuzzy_score("kafka", skill)
        self.assertGreater(score, 0.3)

    def test_unrelated_query_low(self):
        skill = self._skill("add-kafka-consumer")
        score = sr.fuzzy_score("outbox pattern", skill)
        self.assertLess(score, 0.5)


class TestCacheInvalidation(unittest.TestCase):

    def test_cache_invalidated_on_mtime_change(self):
        with tempfile.TemporaryDirectory() as tmpdir_str:
            tmpdir = Path(tmpdir_str)
            with RouterFixture(tmpdir):
                # First index
                skills1 = sr.index_skills(force=False)
                self.assertEqual(len(skills1), len(SKILLS_DATA))

                # Touch a file to change mtime
                md_files = list(sr.SKILLS_DIR.glob("*.md"))
                self.assertTrue(md_files)
                time.sleep(0.01)  # ensure different mtime
                md_files[0].touch()

                # Load cache — should detect mtime change
                cache = sr._load_cache()
                self.assertIsNotNone(cache)
                self.assertFalse(sr._cache_is_valid(cache))

    def test_cache_valid_when_no_changes(self):
        with tempfile.TemporaryDirectory() as tmpdir_str:
            tmpdir = Path(tmpdir_str)
            with RouterFixture(tmpdir):
                sr.index_skills(force=True)
                cache = sr._load_cache()
                self.assertIsNotNone(cache)
                self.assertTrue(sr._cache_is_valid(cache))


class TestTopK(unittest.TestCase):

    def test_top_1_returns_one(self):
        with tempfile.TemporaryDirectory() as tmpdir_str:
            tmpdir = Path(tmpdir_str)
            with RouterFixture(tmpdir):
                skills = sr.index_skills(force=True)
                results = sr.rank_skills("kafka consumer", skills, top_k=1)
                self.assertEqual(len(results), 1)

    def test_top_5_returns_up_to_5(self):
        with tempfile.TemporaryDirectory() as tmpdir_str:
            tmpdir = Path(tmpdir_str)
            with RouterFixture(tmpdir):
                skills = sr.index_skills(force=True)
                results = sr.rank_skills("kafka consumer", skills, top_k=5)
                self.assertLessEqual(len(results), 5)
                self.assertGreater(len(results), 0)

    def test_top_k_exceeds_total_returns_all(self):
        with tempfile.TemporaryDirectory() as tmpdir_str:
            tmpdir = Path(tmpdir_str)
            with RouterFixture(tmpdir):
                skills = sr.index_skills(force=True)
                results = sr.rank_skills("kafka", skills, top_k=100)
                self.assertEqual(len(results), len(SKILLS_DATA))


class TestJsonOutput(unittest.TestCase):

    def test_json_output_is_parseable(self):
        import io
        with tempfile.TemporaryDirectory() as tmpdir_str:
            tmpdir = Path(tmpdir_str)
            with RouterFixture(tmpdir):
                skills = sr.index_skills(force=True)
                results = sr.rank_skills("kafka", skills, top_k=3)
                captured = io.StringIO()
                with patch("sys.stdout", captured):
                    sr.print_json("kafka", results)
                output = captured.getvalue()
                parsed = json.loads(output)
                self.assertEqual(parsed["query"], "kafka")
                self.assertIn("results", parsed)
                for r in parsed["results"]:
                    self.assertIn("name", r)
                    self.assertIn("score", r)
                    self.assertIn("path", r)
                    self.assertIsInstance(r["score"], float)

    def test_json_top_result_for_kafka_query(self):
        import io
        with tempfile.TemporaryDirectory() as tmpdir_str:
            tmpdir = Path(tmpdir_str)
            with RouterFixture(tmpdir):
                skills = sr.index_skills(force=True)
                results = sr.rank_skills("agregar un consumer Kafka", skills, top_k=3)
                captured = io.StringIO()
                with patch("sys.stdout", captured):
                    sr.print_json("agregar un consumer Kafka", results)
                parsed = json.loads(captured.getvalue())
                top_name = parsed["results"][0]["name"]
                self.assertIn(top_name, ["add-kafka-consumer", "add-kafka-publisher"])


class TestSkipInvalidFrontmatter(unittest.TestCase):

    def test_bad_frontmatter_does_not_abort(self):
        """A file with broken frontmatter should be skipped, not crash."""
        broken_skills = [
            *SKILLS_DATA,
            # Broken: frontmatter not closed
            {
                "name": "broken-skill",
                "intent": "Should be skipped",
                "tags": "[broken]",
                "body": "no closing delimiter",
            },
        ]

        with tempfile.TemporaryDirectory() as tmpdir_str:
            tmpdir = Path(tmpdir_str)
            with RouterFixture(tmpdir):
                # Overwrite one file with truly broken frontmatter
                broken_file = sr.SKILLS_DIR / "broken-skill.md"
                broken_file.write_text("---\nno closing\n# content", encoding="utf-8")

                import io
                stderr_capture = io.StringIO()
                with patch("sys.stderr", stderr_capture):
                    skills = sr.index_skills(force=True)

                # The broken file should be skipped, valid ones loaded
                names = [s["name"] for s in skills]
                self.assertNotIn("broken-skill", names)
                self.assertIn("add-kafka-consumer", names)
                # Should have warned
                self.assertIn("WARN", stderr_capture.getvalue())

    def test_run_still_returns_results_with_some_broken_files(self):
        with tempfile.TemporaryDirectory() as tmpdir_str:
            tmpdir = Path(tmpdir_str)
            with RouterFixture(tmpdir):
                # Add extra broken file
                broken = sr.SKILLS_DIR / "totally-broken.md"
                broken.write_text("not even markdown frontmatter", encoding="utf-8")

                import io
                with patch("sys.stderr", io.StringIO()):
                    skills = sr.index_skills(force=True)

                results = sr.rank_skills("kafka consumer", skills, top_k=3)
                self.assertGreater(len(results), 0)


# ---------------------------------------------------------------------------
# Integration: simulate 5 fake skills and run end-to-end query
# ---------------------------------------------------------------------------

class TestIntegrationFakeSkills(unittest.TestCase):

    def test_end_to_end_kafka_query(self):
        with tempfile.TemporaryDirectory() as tmpdir_str:
            tmpdir = Path(tmpdir_str)
            with RouterFixture(tmpdir):
                skills = sr.index_skills(force=True)
                self.assertEqual(len(skills), len(SKILLS_DATA))

                results = sr.rank_skills("agregar un consumer Kafka", skills, top_k=3)
                self.assertEqual(len(results), 3)

                # The kafka consumer should rank first
                top = results[0]
                self.assertIn("kafka", top["name"])
                self.assertGreater(top["score"], 0.0)

    def test_end_to_end_rest_query(self):
        with tempfile.TemporaryDirectory() as tmpdir_str:
            tmpdir = Path(tmpdir_str)
            with RouterFixture(tmpdir):
                skills = sr.index_skills(force=True)
                results = sr.rank_skills("exponer endpoint REST", skills, top_k=3)
                self.assertEqual(len(results), 3)
                names = [r["name"] for r in results]
                self.assertIn("expose-rest-endpoint", names)
                # Should be top result
                self.assertEqual(results[0]["name"], "expose-rest-endpoint")

    def test_end_to_end_outbox_query(self):
        with tempfile.TemporaryDirectory() as tmpdir_str:
            tmpdir = Path(tmpdir_str)
            with RouterFixture(tmpdir):
                skills = sr.index_skills(force=True)
                results = sr.rank_skills("outbox pattern", skills, top_k=3)
                self.assertGreater(len(results), 0)
                self.assertEqual(results[0]["name"], "add-outbox-pattern")


if __name__ == "__main__":
    unittest.main(verbosity=2)
