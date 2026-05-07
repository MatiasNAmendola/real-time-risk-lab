#!/usr/bin/env python3
"""
Tests for auto-fix-dispatcher.py
Run: python3 test_auto_fix_dispatcher.py
"""

import os
import sys
import unittest

_HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, _HERE)

import importlib.util
_spec = importlib.util.spec_from_file_location(
    "auto_fix_dispatcher",
    os.path.join(_HERE, "auto-fix-dispatcher.py")
)
afd = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(afd)


# ---------------------------------------------------------------------------
# Fixtures
# ---------------------------------------------------------------------------

def _make_failure(suite="unit", location="FooTest:10", name="testFoo",
                  message="Connection refused", root_cause="service down"):
    return {
        "suite": suite,
        "location": location,
        "name": name,
        "message": message,
        "root_cause": root_cause,
        "suggested_fix": "check service is running",
    }


# ---------------------------------------------------------------------------
# Tests
# ---------------------------------------------------------------------------

class TestClustering(unittest.TestCase):
    def test_clusters_by_root_cause(self):
        failures = [
            _make_failure(root_cause="service down"),
            _make_failure(root_cause="service down"),
            _make_failure(root_cause="slow service"),
        ]
        clusters = afd.cluster_failures(failures)
        causes = {c.root_cause for c in clusters}
        self.assertIn("service down", causes)
        self.assertIn("slow service", causes)
        # service down cluster has 2 failures
        sd_cluster = next(c for c in clusters if c.root_cause == "service down")
        self.assertEqual(len(sd_cluster.failures), 2)

    def test_empty_failures_returns_empty_clusters(self):
        clusters = afd.cluster_failures([])
        self.assertEqual(len(clusters), 0)

    def test_all_unknown_clustered_together(self):
        failures = [_make_failure(root_cause="unknown") for _ in range(3)]
        clusters = afd.cluster_failures(failures)
        self.assertEqual(len(clusters), 1)
        self.assertEqual(clusters[0].root_cause, "unknown")
        self.assertEqual(len(clusters[0].failures), 3)


class TestPromptGeneration(unittest.TestCase):
    def test_service_down_prompt_contains_context(self):
        cluster = afd.Cluster("service down", [
            _make_failure(suite="atdd-karate", location="features/foo.feature:10",
                          name="webhook test", root_cause="service down")
        ])
        prompt = cluster.build_prompt()
        self.assertIn("Service Down", prompt)
        self.assertIn("atdd-karate", prompt)
        self.assertNotIn("@Disabled", prompt)

    def test_test_logic_prompt_blocks_auto_fix(self):
        cluster = afd.Cluster("test logic issue", [_make_failure(root_cause="test logic issue")])
        prompt = cluster.build_prompt()
        self.assertIn("AUTO-FIX BLOCKED", prompt)
        self.assertIn("HUMAN REVIEW", prompt)

    def test_unknown_cause_prompt_generated(self):
        cluster = afd.Cluster("unknown", [_make_failure(root_cause="unknown")])
        prompt = cluster.build_prompt()
        self.assertIn("Human investigation required", prompt)


class TestForbiddenPatterns(unittest.TestCase):
    def test_disabled_annotation_detected(self):
        hits = afd._check_forbidden("add @Disabled to this test")
        self.assertTrue(len(hits) > 0)

    def test_skip_call_detected(self):
        hits = afd._check_forbidden("call .skip( on test method")
        self.assertTrue(len(hits) > 0)

    def test_todo_reenable_detected(self):
        hits = afd._check_forbidden("// TODO: re-enable this test later")
        self.assertTrue(len(hits) > 0)

    def test_comment_assert_detected(self):
        hits = afd._check_forbidden("// assert something = true")
        self.assertTrue(len(hits) > 0)

    def test_coverage_lowering_detected(self):
        hits = afd._check_forbidden("coverage = 50 // lowered for now")
        self.assertTrue(len(hits) > 0)

    def test_clean_prompt_passes(self):
        hits = afd._check_forbidden("Fix docker compose healthcheck configuration")
        self.assertEqual(hits, [])


class TestFileCountHeuristic(unittest.TestCase):
    def test_few_files_under_limit(self):
        prompt = "Change compose/docker-compose.yml and app.yaml"
        count = afd._estimate_file_count(prompt)
        self.assertLessEqual(count, afd.MAX_FILES_PER_FIX)

    def test_many_files_over_limit(self):
        prompt = " ".join([f"file{i}.java" for i in range(10)])
        count = afd._estimate_file_count(prompt)
        self.assertGreater(count, afd.MAX_FILES_PER_FIX)


class TestSafetyCheck(unittest.TestCase):
    def test_safe_cluster_returns_true(self):
        cluster = afd.Cluster("service down", [_make_failure(root_cause="service down")])
        result = afd._safety_check_and_warn(cluster)
        self.assertTrue(result)

    def test_cluster_failure_list_text(self):
        f = _make_failure(suite="smoke", location="health_check:5", name="healthCheck",
                          root_cause="service down")
        cluster = afd.Cluster("service down", [f])
        text = cluster.failure_list_text
        self.assertIn("smoke", text)
        self.assertIn("health_check", text)


class TestLoadFailures(unittest.TestCase):
    def test_load_from_json_file(self):
        import json
        import tempfile
        data = {
            "totals": {
                "failures": [_make_failure()]
            }
        }
        with tempfile.NamedTemporaryFile(suffix=".json", mode="w", delete=False) as f:
            json.dump(data, f)
            fname = f.name
        failures = afd.load_failures(fname)
        os.unlink(fname)
        self.assertEqual(len(failures), 1)
        self.assertEqual(failures[0]["root_cause"], "service down")


if __name__ == "__main__":
    unittest.main(verbosity=2)
