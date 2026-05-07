#!/usr/bin/env python3
"""
Tests for failures-aggregator.py
Run: python3 test_failures_aggregator.py
"""

import json
import os
import sys
import tempfile
import unittest
import xml.etree.ElementTree as ET

# Ensure the script is importable
_HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, _HERE)

# Patch REPO_ROOT before importing to avoid side effects
import importlib.util
_spec = importlib.util.spec_from_file_location(
    "failures_aggregator",
    os.path.join(_HERE, "failures-aggregator.py")
)
agg = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(agg)


# ---------------------------------------------------------------------------
# Helpers to build fixture content
# ---------------------------------------------------------------------------

def _surefire_xml(tests=2, failures=1, skipped=0, error_msg="Connection refused to localhost:8080"):
    root = ET.Element("testsuite", tests=str(tests), failures=str(failures),
                       errors="0", skipped=str(skipped))
    tc_pass = ET.SubElement(root, "testcase", classname="com.example.FooTest",
                             name="testPasses", time="0.1")
    if failures > 0:
        tc_fail = ET.SubElement(root, "testcase", classname="com.example.FooTest",
                                name="testFails", time="0.5")
        fail_el = ET.SubElement(tc_fail, "failure",
                                message=error_msg,
                                type="java.lang.AssertionError")
        fail_el.text = error_msg
    return ET.tostring(root, encoding="unicode")


def _cucumber_json(scenarios=2, failed=1, fail_msg="Expected true but was false"):
    steps_pass = [{"result": {"status": "passed", "duration": 100000}}]
    steps_fail = [
        {"result": {"status": "passed", "duration": 100000}},
        {"result": {"status": "failed", "duration": 200000, "error_message": fail_msg}},
    ]
    data = [
        {
            "name": "Feature One",
            "uri": "features/one.feature",
            "elements": [
                {"name": "Scenario passes", "steps": steps_pass},
                {"name": "Scenario fails", "steps": steps_fail},
            ] if failed > 0 else [
                {"name": "Scenario passes", "steps": steps_pass},
                {"name": "Scenario passes 2", "steps": steps_pass},
            ],
        }
    ]
    return json.dumps(data)


def _karate_summary_json(total=5, failed=2):
    feature_results = []
    if failed > 0:
        feature_results.append({
            "relativeUri": "features/05_webhook.feature",
            "scenarioResults": [
                {"name": "webhook dispatches callback", "line": 23, "failed": True,
                 "errorMessage": "Expected callback received in <3s, but got 0 callbacks"},
                {"name": "webhook succeeds", "line": 10, "failed": False},
            ]
        })
    return json.dumps({
        "scenarioCount": total,
        "scenariosFailed": failed,
        "scenariosPending": 1,
        "featureResults": feature_results,
    })


def _smoke_meta_json(total=5, passed=4, failed=1):
    data = {
        "total": total,
        "passed": passed,
        "failed": failed,
        "skipped": 0,
        "failures": [
            {"check": "health_risk_engine", "name": "risk-engine /health", "message": "Connection refused"}
        ] if failed > 0 else [],
    }
    return json.dumps(data)


# ---------------------------------------------------------------------------
# Test cases
# ---------------------------------------------------------------------------

class TestHeuristics(unittest.TestCase):
    def test_connection_refused_detected(self):
        cause = agg._heuristic_root_cause("Connection refused to localhost:8080")
        self.assertEqual(cause, "service down")

    def test_timeout_detected(self):
        cause = agg._heuristic_root_cause("Timed out after 5000ms")
        self.assertEqual(cause, "slow service")

    def test_nxdomain_detected(self):
        cause = agg._heuristic_root_cause("NXDOMAIN for host 'usecase-app'")
        self.assertEqual(cause, "network/DNS issue")

    def test_assertion_error_detected(self):
        cause = agg._heuristic_root_cause("AssertionError: Expected 200 but got 404")
        self.assertEqual(cause, "test logic issue")

    def test_oom_detected(self):
        cause = agg._heuristic_root_cause("java.lang.OutOfMemoryError: Java heap space")
        self.assertEqual(cause, "mem_limit too low")

    def test_class_not_found_detected(self):
        cause = agg._heuristic_root_cause("ClassNotFoundException: com.example.Foo")
        self.assertEqual(cause, "classpath issue")

    def test_unknown_returns_unknown(self):
        cause = agg._heuristic_root_cause("Some totally unrecognised error")
        self.assertEqual(cause, "unknown")

    def test_empty_message(self):
        cause = agg._heuristic_root_cause("")
        self.assertEqual(cause, "unknown")


class TestSurefireParser(unittest.TestCase):
    def test_parse_surefire_xml_counts(self):
        xml_content = _surefire_xml(tests=3, failures=1, skipped=1)
        with tempfile.NamedTemporaryFile(suffix=".xml", mode="w", delete=False) as f:
            f.write(xml_content)
            f.flush()
            result = agg._parse_surefire_xml(f.name, "unit")
        os.unlink(f.name)
        self.assertEqual(result.total, 3)
        self.assertEqual(result.failed, 1)
        self.assertEqual(result.skipped, 1)

    def test_parse_surefire_xml_failure_message(self):
        xml_content = _surefire_xml(tests=2, failures=1, error_msg="Connection refused")
        with tempfile.NamedTemporaryFile(suffix=".xml", mode="w", delete=False) as f:
            f.write(xml_content)
            f.flush()
            result = agg._parse_surefire_xml(f.name, "unit")
        os.unlink(f.name)
        self.assertEqual(len(result.failures), 1)
        self.assertIn("refused", result.failures[0].message.lower())
        self.assertEqual(result.failures[0].root_cause, "service down")

    def test_parse_surefire_invalid_xml(self):
        with tempfile.NamedTemporaryFile(suffix=".xml", mode="w", delete=False) as f:
            f.write("not valid xml <<>>")
            f.flush()
            result = agg._parse_surefire_xml(f.name, "unit")
        os.unlink(f.name)
        self.assertIn("error", result.note.lower())


class TestCucumberParser(unittest.TestCase):
    def test_parse_cucumber_json_counts(self):
        json_content = _cucumber_json(scenarios=2, failed=1)
        with tempfile.NamedTemporaryFile(suffix=".json", mode="w", delete=False) as f:
            f.write(json_content)
            f.flush()
            result = agg._parse_cucumber_json(f.name, "atdd-cucumber")
        os.unlink(f.name)
        self.assertEqual(result.total, 2)
        self.assertEqual(result.failed, 1)
        self.assertEqual(result.passed, 1)

    def test_parse_cucumber_json_failure_captured(self):
        json_content = _cucumber_json(failed=1, fail_msg="AssertionError: Expected true but was false")
        with tempfile.NamedTemporaryFile(suffix=".json", mode="w", delete=False) as f:
            f.write(json_content)
            f.flush()
            result = agg._parse_cucumber_json(f.name, "atdd-cucumber")
        os.unlink(f.name)
        self.assertEqual(len(result.failures), 1)
        self.assertIn("AssertionError", result.failures[0].message)

    def test_parse_cucumber_json_all_pass(self):
        json_content = _cucumber_json(scenarios=2, failed=0)
        with tempfile.NamedTemporaryFile(suffix=".json", mode="w", delete=False) as f:
            f.write(json_content)
            f.flush()
            result = agg._parse_cucumber_json(f.name, "atdd-cucumber")
        os.unlink(f.name)
        self.assertEqual(result.failed, 0)
        self.assertEqual(len(result.failures), 0)


class TestKarateParser(unittest.TestCase):
    def test_parse_karate_summary_counts(self):
        json_content = _karate_summary_json(total=5, failed=2)
        with tempfile.NamedTemporaryFile(suffix=".json", mode="w", delete=False) as f:
            f.write(json_content)
            f.flush()
            result = agg._parse_karate_summary(f.name, "atdd-karate")
        os.unlink(f.name)
        self.assertEqual(result.total, 5)
        self.assertEqual(result.failed, 2)
        self.assertEqual(result.skipped, 1)

    def test_parse_karate_failure_location(self):
        json_content = _karate_summary_json(total=5, failed=2)
        with tempfile.NamedTemporaryFile(suffix=".json", mode="w", delete=False) as f:
            f.write(json_content)
            f.flush()
            result = agg._parse_karate_summary(f.name, "atdd-karate")
        os.unlink(f.name)
        self.assertTrue(len(result.failures) > 0)
        self.assertIn("webhook", result.failures[0].location)


class TestSmokeParser(unittest.TestCase):
    def test_parse_smoke_meta_json(self):
        json_content = _smoke_meta_json(total=5, passed=4, failed=1)
        with tempfile.TemporaryDirectory() as tmpdir:
            meta_path = os.path.join(tmpdir, "meta.json")
            with open(meta_path, "w") as f:
                f.write(json_content)
            # Temporarily patch REPO_ROOT
            orig = agg.REPO_ROOT
            agg.REPO_ROOT = os.path.dirname(os.path.dirname(tmpdir))
            # Build path structure: out/smoke/latest/meta.json
            smoke_dir = os.path.join(agg.REPO_ROOT, "out", "smoke", "latest")
            os.makedirs(smoke_dir, exist_ok=True)
            dest = os.path.join(smoke_dir, "meta.json")
            with open(dest, "w") as f:
                f.write(json_content)
            result = agg._aggregate_smoke("smoke")
            agg.REPO_ROOT = orig
            self.assertEqual(result.total, 5)
            self.assertEqual(result.failed, 1)


class TestFormatTable(unittest.TestCase):
    def test_format_table_contains_totals(self):
        results = [
            agg.SuiteResult("unit", 10, 9, 1, 0, failures=[
                agg.Failure("unit", "FooTest.testBar", "testBar", "Connection refused")
            ]),
        ]
        output = agg.format_table(results)
        self.assertIn("TOTAL", output)
        self.assertIn("unit", output)
        self.assertIn("Failures (1)", output)
        self.assertIn("service down", output)

    def test_format_table_no_failures(self):
        results = [agg.SuiteResult("unit", 5, 5, 0, 0)]
        output = agg.format_table(results)
        self.assertIn("No failures found.", output)

    def test_format_json_structure(self):
        results = [agg.SuiteResult("unit", 5, 5, 0, 0)]
        output = agg.format_json(results)
        data = json.loads(output)
        self.assertIn("suites", data)
        self.assertIn("totals", data)
        self.assertIn("generated_at", data)


class TestSinceParsing(unittest.TestCase):
    def test_parse_since_hours(self):
        import datetime
        threshold = agg._parse_since("2h")
        now = datetime.datetime.utcnow()
        diff = now - threshold
        self.assertAlmostEqual(diff.total_seconds(), 7200, delta=5)

    def test_parse_since_invalid_raises(self):
        with self.assertRaises(ValueError):
            agg._parse_since("invalid")


if __name__ == "__main__":
    unittest.main(verbosity=2)
