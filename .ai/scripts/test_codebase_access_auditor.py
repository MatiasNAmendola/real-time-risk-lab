import importlib.util
import json
import sys
import tempfile
import unittest
from datetime import datetime, timedelta
from pathlib import Path

# Load module with hyphenated filename
_spec = importlib.util.spec_from_file_location(
    "codebase_access_auditor",
    Path(__file__).parent / "codebase-access-auditor.py",
)
_mod = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(_mod)

CATEGORIES = _mod.CATEGORIES
categorize = _mod.categorize
categorize_events = _mod.categorize_events
load_reads = _mod.load_reads
primitive_coverage_ratio = _mod.primitive_coverage_ratio
render_table = _mod.render_table


class TestCategorize(unittest.TestCase):
    def test_primitive_category(self):
        self.assertEqual(categorize(".ai/primitives/skill-router.md"), "PRIMITIVE")

    def test_vault_category(self):
        self.assertEqual(categorize("vault/secret.yaml"), "VAULT")

    def test_docs_category(self):
        self.assertEqual(categorize("docs/01-design.md"), "DOCS")

    def test_codebase_poc(self):
        self.assertEqual(categorize("poc/risk-engine/Main.java"), "CODEBASE")

    def test_codebase_pkg(self):
        self.assertEqual(categorize("pkg/router/router.go"), "CODEBASE")

    def test_codebase_sdks(self):
        self.assertEqual(categorize("sdks/java/client.java"), "CODEBASE")

    def test_codebase_cli(self):
        self.assertEqual(categorize("cli/cmd/root.go"), "CODEBASE")

    def test_infra_compose(self):
        self.assertEqual(categorize("compose/docker-compose.yml"), "INFRA")

    def test_config_ai_scripts(self):
        self.assertEqual(categorize(".ai/scripts/skill-router.py"), "CONFIG")

    def test_config_nx(self):
        self.assertEqual(categorize("nx"), "CONFIG")

    def test_other_catchall(self):
        self.assertEqual(categorize("some/random/unknown/path.txt"), "OTHER")


class TestLoadReads(unittest.TestCase):
    def _make_log(self, tmpdir: Path, entries: list) -> Path:
        log_file = tmpdir / "reads-2026-05-07.jsonl"
        lines = [json.dumps(e) for e in entries]
        log_file.write_text("\n".join(lines) + "\n")
        return log_file

    def test_load_reads_basic(self):
        with tempfile.TemporaryDirectory() as td:
            log_dir = Path(td)
            now = datetime.utcnow()
            entries = [
                {"ts": now.strftime("%Y-%m-%dT%H:%M:%SZ"), "tool": "Read", "path": ".ai/primitives/x.md"},
                {"ts": now.strftime("%Y-%m-%dT%H:%M:%SZ"), "tool": "Read", "path": "poc/Main.java"},
            ]
            self._make_log(log_dir, entries)
            events = load_reads(log_dir, since_days=1)
            self.assertEqual(len(events), 2)

    def test_load_reads_since_filter(self):
        with tempfile.TemporaryDirectory() as td:
            log_dir = Path(td)
            old_ts = (datetime.utcnow() - timedelta(days=10)).strftime("%Y-%m-%dT%H:%M:%SZ")
            new_ts = datetime.utcnow().strftime("%Y-%m-%dT%H:%M:%SZ")
            entries = [
                {"ts": old_ts, "tool": "Read", "path": "poc/Old.java"},
                {"ts": new_ts, "tool": "Read", "path": ".ai/primitives/new.md"},
            ]
            self._make_log(log_dir, entries)
            events = load_reads(log_dir, since_days=5)
            self.assertEqual(len(events), 1)
            self.assertEqual(events[0]["path"], ".ai/primitives/new.md")

    def test_load_reads_empty_dir(self):
        with tempfile.TemporaryDirectory() as td:
            events = load_reads(Path(td), since_days=7)
            self.assertEqual(events, [])

    def test_load_reads_skips_blank_lines(self):
        with tempfile.TemporaryDirectory() as td:
            log_dir = Path(td)
            now = datetime.utcnow().strftime("%Y-%m-%dT%H:%M:%SZ")
            content = f'{{"ts":"{now}","tool":"Read","path":"docs/x.md"}}\n\n\n'
            (log_dir / "reads-2026-05-07.jsonl").write_text(content)
            events = load_reads(log_dir, since_days=1)
            self.assertEqual(len(events), 1)


class TestPrimitiveCoverageRatio(unittest.TestCase):
    def test_zero_events(self):
        self.assertEqual(primitive_coverage_ratio({}), 0.0)

    def test_all_primitive(self):
        ratio = primitive_coverage_ratio({"PRIMITIVE": 10, "CODEBASE": 0})
        self.assertAlmostEqual(ratio, 100.0)

    def test_all_codebase(self):
        ratio = primitive_coverage_ratio({"PRIMITIVE": 0, "CODEBASE": 10})
        self.assertAlmostEqual(ratio, 0.0)

    def test_mixed(self):
        ratio = primitive_coverage_ratio({"PRIMITIVE": 3, "VAULT": 1, "CODEBASE": 4})
        # (3+1) / (4+4) = 50%
        self.assertAlmostEqual(ratio, 50.0)

    def test_vault_counts_as_primitive(self):
        ratio = primitive_coverage_ratio({"VAULT": 5, "CODEBASE": 5})
        self.assertAlmostEqual(ratio, 50.0)


class TestCategorizeEvents(unittest.TestCase):
    def test_totals(self):
        events = [
            {"path": ".ai/primitives/x.md"},
            {"path": "poc/Main.java"},
            {"path": "docs/01.md"},
        ]
        stats = categorize_events(events)
        self.assertEqual(stats["total"], 3)
        self.assertEqual(stats["by_category"]["PRIMITIVE"], 1)
        self.assertEqual(stats["by_category"]["CODEBASE"], 1)
        self.assertEqual(stats["by_category"]["DOCS"], 1)

    def test_top_files(self):
        events = [{"path": "poc/Main.java"}] * 5 + [{"path": "docs/x.md"}] * 2
        stats = categorize_events(events)
        top = dict(stats["top_files"])
        self.assertEqual(top["poc/Main.java"], 5)
        self.assertEqual(top["docs/x.md"], 2)


class TestRenderTable(unittest.TestCase):
    def test_zero_reads_output(self):
        stats = {"total": 0, "by_category": {}, "top_files": []}
        output = render_table(stats)
        self.assertIn("Total reads tracked: 0", output)
        self.assertIn("Primitive coverage ratio: 0.0%", output)
        self.assertIn("Verdict: LOW", output)

    def test_high_verdict(self):
        stats = {
            "total": 10,
            "by_category": {"PRIMITIVE": 6, "CODEBASE": 2},
            "top_files": [],
        }
        output = render_table(stats)
        self.assertIn("Verdict: HIGH", output)

    def test_medium_verdict(self):
        stats = {
            "total": 10,
            "by_category": {"PRIMITIVE": 3, "CODEBASE": 9},
            "top_files": [],
        }
        output = render_table(stats)
        self.assertIn("Verdict: MEDIUM", output)

    def test_all_categories_present(self):
        stats = {"total": 0, "by_category": {}, "top_files": []}
        output = render_table(stats)
        for cat, _ in CATEGORIES:
            self.assertIn(cat, output)


class TestJsonOutput(unittest.TestCase):
    def test_json_output_valid(self):
        events = [
            {"path": ".ai/primitives/x.md"},
            {"path": "poc/Main.java"},
        ]
        stats = categorize_events(events)
        serialized = json.dumps(stats, default=str)
        parsed = json.loads(serialized)
        self.assertEqual(parsed["total"], 2)
        self.assertIn("by_category", parsed)
        self.assertIn("top_files", parsed)


class TestThreshold(unittest.TestCase):
    def test_above_threshold(self):
        by_cat = {"PRIMITIVE": 5, "CODEBASE": 5}
        ratio = primitive_coverage_ratio(by_cat)
        self.assertGreaterEqual(ratio, 25.0)

    def test_below_threshold(self):
        by_cat = {"PRIMITIVE": 1, "CODEBASE": 20}
        ratio = primitive_coverage_ratio(by_cat)
        self.assertLess(ratio, 25.0)


if __name__ == "__main__":
    unittest.main()
