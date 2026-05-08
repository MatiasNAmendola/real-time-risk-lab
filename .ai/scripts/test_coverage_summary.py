#!/usr/bin/env python3
"""
test_coverage_summary.py — stdlib unittest for coverage-summary.py.

Run:
  python3 -m unittest .ai/scripts/test_coverage_summary.py
  python3 -m unittest test_coverage_summary  (from .ai/scripts/)
"""

import importlib.util
import io
import json
import os
import sys
import unittest

# Load coverage-summary.py by file path (hyphen in name prevents normal import)
_HERE = os.path.dirname(os.path.abspath(__file__))
_SCRIPT = os.path.join(_HERE, "coverage-summary.py")
_spec = importlib.util.spec_from_file_location("coverage_summary", _SCRIPT)
cs = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(cs)

_FIXTURE = os.path.join(_HERE, "test_data", "sample-jacoco.xml")


class TestParseJacocoXml(unittest.TestCase):
    """parse_jacoco_xml returns well-structured dict per package."""

    def setUp(self):
        self.data = cs.parse_jacoco_xml(_FIXTURE)

    def test_returns_packages_key(self):
        self.assertIn("packages", self.data)
        self.assertIn("totals", self.data)

    def test_packages_not_empty(self):
        self.assertGreater(len(self.data["packages"]), 0)

    def test_package_names_use_dot_separator(self):
        for name in self.data["packages"]:
            self.assertNotIn("/", name, msg=f"Package name contains slash: {name}")

    def test_known_package_present(self):
        self.assertIn("io.riskplatform.poc.pkg.risk.rule", self.data["packages"])


class TestCounterParsing(unittest.TestCase):
    """INSTRUCTION, BRANCH, LINE, METHOD, CLASS counters parsed correctly."""

    def setUp(self):
        self.data = cs.parse_jacoco_xml(_FIXTURE)

    def test_instruction_counter(self):
        pkg = self.data["packages"]["io.riskplatform.poc.pkg.risk.rule"]
        c = pkg["INSTRUCTION"]
        self.assertEqual(c["missed"], 0)
        self.assertEqual(c["covered"], 200)

    def test_branch_counter(self):
        pkg = self.data["packages"]["io.riskplatform.poc.pkg.risk.rule"]
        c = pkg["BRANCH"]
        self.assertEqual(c["missed"], 0)
        self.assertEqual(c["covered"], 40)

    def test_line_counter(self):
        pkg = self.data["packages"]["io.riskplatform.poc.pkg.risk.rule"]
        c = pkg["LINE"]
        self.assertEqual(c["missed"], 0)
        self.assertEqual(c["covered"], 50)

    def test_method_counter(self):
        pkg = self.data["packages"]["io.riskplatform.poc.pkg.risk.rule"]
        c = pkg["METHOD"]
        self.assertEqual(c["missed"], 0)
        self.assertEqual(c["covered"], 20)

    def test_class_counter(self):
        pkg = self.data["packages"]["io.riskplatform.poc.pkg.risk.rule"]
        c = pkg["CLASS"]
        self.assertEqual(c["missed"], 0)
        self.assertEqual(c["covered"], 5)


class TestPctCalc(unittest.TestCase):
    """_pct computes percentage correctly."""

    def test_full_coverage(self):
        self.assertEqual(cs._pct(0, 100), 100)

    def test_zero_coverage(self):
        self.assertEqual(cs._pct(50, 0), 0)

    def test_partial_coverage(self):
        # 80 covered / 100 total = 80%
        self.assertEqual(cs._pct(20, 80), 80)

    def test_rounding(self):
        # 93 covered / (10+130) = 92.8... -> rounds to 93
        self.assertEqual(cs._pct(10, 130), 93)

    def test_no_data_returns_none(self):
        self.assertIsNone(cs._pct(0, 0))


class TestNABranches(unittest.TestCase):
    """Packages without BRANCH counter report n/a."""

    def setUp(self):
        self.data = cs.parse_jacoco_xml(_FIXTURE)

    def test_config_package_has_no_branch(self):
        # config package in fixture has no BRANCH counter -> missed=0,covered=0
        pkg = self.data["packages"]["io.riskplatform.engine.config"]
        branch_pct = cs._pct(pkg["BRANCH"]["missed"], pkg["BRANCH"]["covered"])
        self.assertIsNone(branch_pct)

    def test_fmt_pct_none_is_na(self):
        result = cs._fmt_pct(None).strip()
        self.assertEqual(result, "n/a")


class TestMarkdownOutput(unittest.TestCase):
    """--markdown output has correct table structure."""

    def setUp(self):
        self.data = cs.parse_jacoco_xml(_FIXTURE)

    def test_has_header_row(self):
        buf = io.StringIO()
        cs.print_markdown(self.data, out=buf)
        output = buf.getvalue()
        self.assertIn("| Package |", output)
        self.assertIn("Lines", output)
        self.assertIn("Branches", output)
        self.assertIn("Methods", output)

    def test_has_separator_row(self):
        buf = io.StringIO()
        cs.print_markdown(self.data, out=buf)
        output = buf.getvalue()
        self.assertIn("|---------|", output)

    def test_has_total_row(self):
        buf = io.StringIO()
        cs.print_markdown(self.data, out=buf)
        output = buf.getvalue()
        self.assertIn("TOTAL", output)

    def test_known_package_in_output(self):
        buf = io.StringIO()
        cs.print_markdown(self.data, out=buf)
        output = buf.getvalue()
        self.assertIn("io.riskplatform.poc.pkg.risk.rule", output)


class TestJsonOutput(unittest.TestCase):
    """--json output is valid JSON with expected structure."""

    def setUp(self):
        self.data = cs.parse_jacoco_xml(_FIXTURE)

    def test_valid_json(self):
        result = cs.build_json(self.data)
        # Should be serialisable
        serialised = json.dumps(result)
        parsed = json.loads(serialised)
        self.assertIsInstance(parsed, dict)

    def test_json_has_total(self):
        result = cs.build_json(self.data)
        self.assertIn("total", result)
        self.assertIn("lines", result["total"])

    def test_json_has_packages(self):
        result = cs.build_json(self.data)
        self.assertIn("packages", result)
        self.assertIsInstance(result["packages"], list)

    def test_json_has_generated_timestamp(self):
        result = cs.build_json(self.data)
        self.assertIn("generated", result)
        self.assertIn("T", result["generated"])


class TestThresholdCheck(unittest.TestCase):
    """--threshold exits 1 when TOTAL lines < threshold."""

    def setUp(self):
        self.data = cs.parse_jacoco_xml(_FIXTURE)

    def test_passes_when_above_threshold(self):
        # TOTAL lines in fixture: 204/(204+75) ~73%
        self.assertTrue(cs.check_threshold(self.data, 50))

    def test_fails_when_below_threshold(self):
        self.assertFalse(cs.check_threshold(self.data, 99))

    def test_main_exits_1_on_threshold_fail(self):
        with self.assertRaises(SystemExit) as ctx:
            cs.main([_FIXTURE, "--threshold", "99", "--no-color"])
        self.assertEqual(ctx.exception.code, 1)

    def test_main_exits_0_on_threshold_pass(self):
        # Should not raise SystemExit for a low threshold
        try:
            cs.main([_FIXTURE, "--threshold", "1", "--no-color"])
        except SystemExit as e:
            self.fail(f"main() raised SystemExit({e.code}) unexpectedly")


class TestColorOutput(unittest.TestCase):
    """Color/no-color output behaves correctly."""

    def setUp(self):
        self.data = cs.parse_jacoco_xml(_FIXTURE)

    def test_no_color_has_no_ansi(self):
        buf = io.StringIO()
        cs.print_table(self.data, use_color=False, out=buf)
        output = buf.getvalue()
        self.assertNotIn("\033[", output)

    def test_color_contains_ansi_for_green(self):
        buf = io.StringIO()
        cs.print_table(self.data, use_color=True, out=buf)
        output = buf.getvalue()
        # fixture has 100% packages -> GREEN should appear
        self.assertIn("\033[", output)

    def test_fmt_pct_returns_right_aligned(self):
        result = cs._fmt_pct(80, width=7)
        self.assertEqual(result, "    80%")


if __name__ == "__main__":
    unittest.main()
