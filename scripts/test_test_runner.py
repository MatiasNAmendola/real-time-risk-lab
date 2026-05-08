#!/usr/bin/env python3
"""
test_test_runner.py -- Unit tests for test-runner.py.

Run with:
    python3 -m unittest scripts/test_test_runner.py
    python3 -m unittest scripts/test_test_runner.py -v
"""
import importlib.util
import os
import sys
import tempfile
import unittest
from pathlib import Path

# Load test-runner.py by file path (hyphen in name prevents normal import)
_runner_path = Path(__file__).parent / "test-runner.py"
_spec = importlib.util.spec_from_file_location("test_runner_module", _runner_path)
_mod = importlib.util.module_from_spec(_spec)
sys.modules["test_runner_module"] = _mod
_spec.loader.exec_module(_mod)

_parse_yaml_testgroups = _mod._parse_yaml_testgroups
_topological_levels = _mod._topological_levels
TestGroup = _mod.TestGroup
TestRunner = _mod.TestRunner
JobResult = _mod.JobResult
get_cpu_count = _mod.get_cpu_count
get_load_avg = _mod.get_load_avg
get_total_ram_mb = _mod.get_total_ram_mb
load_test_groups = _mod.load_test_groups
_build_parser = _mod._build_parser
_resolve_jobs = _mod._resolve_jobs

REPO_ROOT = Path(__file__).resolve().parent.parent

# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

MINIMAL_YAML = """
groups:
  unit:
    cmd: "./gradlew test"
    needs_infra: false
    cost_cpu: medium
    cost_ram_mb: 800
    duration_estimate_sec: 60
    exclusive: false

  arch:
    cmd: "./gradlew :tests:architecture:test"
    needs_infra: false
    cost_cpu: low
    cost_ram_mb: 400
    duration_estimate_sec: 15

  integration:
    cmd: "./gradlew :tests:integration:test"
    needs_infra: compose
    cost_ram_mb: 2000
    duration_estimate_sec: 90

  bench-inproc:
    cmd: "./gradlew :bench:inprocess-bench:run"
    needs_infra: false
    cost_cpu: high
    cost_ram_mb: 1200
    exclusive: true

composites:
  quick:
    - unit
    - arch
  ci-fast:
    - unit
    - arch
    - integration
"""


# ---------------------------------------------------------------------------
# 1. YAML parser: basic groups parsed correctly
# ---------------------------------------------------------------------------

class TestYamlParser(unittest.TestCase):
    def setUp(self):
        self.parsed = _parse_yaml_testgroups(MINIMAL_YAML)

    def test_groups_key_present(self):
        self.assertIn("groups", self.parsed)

    def test_composites_key_present(self):
        self.assertIn("composites", self.parsed)

    def test_group_cmd_parsed(self):
        # cmd value may or may not be quoted depending on YAML; strip outer quotes
        raw = self.parsed["groups"]["unit"]["cmd"]
        stripped = raw.strip('"').strip("'")
        self.assertEqual(stripped, "./gradlew test")

    def test_group_scalar_coercion_bool(self):
        """needs_infra: false should be Python False (or string 'false')."""
        val = self.parsed["groups"]["unit"].get("needs_infra", "false")
        # Accept both string "false" and Python False
        self.assertIn(str(val).lower(), ("false",))

    def test_group_scalar_coercion_int(self):
        self.assertEqual(self.parsed["groups"]["unit"]["cost_ram_mb"], 800)

    def test_group_exclusive_true(self):
        self.assertTrue(self.parsed["groups"]["bench-inproc"]["exclusive"])

    def test_composite_quick_members(self):
        self.assertEqual(self.parsed["composites"]["quick"], ["unit", "arch"])

    def test_composite_ci_fast_members(self):
        self.assertEqual(
            self.parsed["composites"]["ci-fast"], ["unit", "arch", "integration"]
        )


# ---------------------------------------------------------------------------
# 2. TestGroup.from_dict
# ---------------------------------------------------------------------------

class TestGroupFromDict(unittest.TestCase):
    def test_defaults_applied(self):
        g = TestGroup.from_dict("mytest", {"cmd": "echo hi"})
        self.assertEqual(g.name, "mytest")
        self.assertEqual(g.cmd, "echo hi")
        self.assertEqual(g.cost_cpu, "medium")
        self.assertEqual(g.cost_ram_mb, 500)
        self.assertFalse(g.exclusive)

    def test_cost_cpu_pct_low(self):
        g = TestGroup.from_dict("t", {"cmd": "x", "cost_cpu": "low"})
        self.assertEqual(g.cost_cpu_pct, 15)

    def test_cost_cpu_pct_high(self):
        g = TestGroup.from_dict("t", {"cmd": "x", "cost_cpu": "high"})
        self.assertEqual(g.cost_cpu_pct, 70)


# ---------------------------------------------------------------------------
# 3. Topological sort
# ---------------------------------------------------------------------------

class TestTopologicalSort(unittest.TestCase):
    def _make_jobs(self, specs: list[tuple[str, str, bool]]) -> list[TestGroup]:
        """specs: [(name, needs_infra, exclusive), ...]"""
        return [
            TestGroup(name=n, cmd="x", needs_infra=inf, exclusive=excl)
            for n, inf, excl in specs
        ]

    def test_no_infra_jobs_level_zero(self):
        jobs = self._make_jobs([("unit", "false", False), ("arch", "false", False)])
        levels = _topological_levels(jobs)
        level0_names = {j.name for j in levels[0]}
        self.assertIn("unit", level0_names)
        self.assertIn("arch", level0_names)

    def test_infra_jobs_after_no_infra(self):
        jobs = self._make_jobs([
            ("unit", "false", False),
            ("smoke", "compose", False),
        ])
        levels = _topological_levels(jobs)
        level_names = [[j.name for j in lvl] for lvl in levels]
        # unit should appear before smoke
        unit_level = next(i for i, lvl in enumerate(level_names) if "unit" in lvl)
        smoke_level = next(i for i, lvl in enumerate(level_names) if "smoke" in lvl)
        self.assertLess(unit_level, smoke_level)

    def test_exclusive_job_isolated(self):
        jobs = self._make_jobs([
            ("unit", "false", False),
            ("arch", "false", False),
            ("bench", "false", True),
        ])
        levels = _topological_levels(jobs)
        # bench must be in a level by itself
        for lvl in levels:
            names = [j.name for j in lvl]
            if "bench" in names:
                self.assertEqual(len(lvl), 1, "exclusive job must be alone in its level")


# ---------------------------------------------------------------------------
# 4. can_dispatch throttle logic
# ---------------------------------------------------------------------------

class TestCanDispatch(unittest.TestCase):
    def _make_runner(self, max_cpu: int = 80, max_ram: int = 8000) -> TestRunner:
        return TestRunner(
            parallel=4,
            max_cpu_pct=max_cpu,
            max_ram_mb=max_ram,
            dry_run=False,
            out_dir=Path(tempfile.mkdtemp()),
            auto_infra=False,
            watch=False,
            json_output=True,
        )

    def test_can_dispatch_under_limits(self):
        runner = self._make_runner(max_cpu=80, max_ram=8000)
        job = TestGroup(name="t", cmd="x", cost_cpu="medium", cost_ram_mb=500)
        self.assertTrue(runner._can_dispatch(job))

    def test_cannot_dispatch_cpu_over(self):
        runner = self._make_runner(max_cpu=30)
        runner._reserved_cpu_pct = 25
        job = TestGroup(name="t", cmd="x", cost_cpu="medium", cost_ram_mb=100)
        # 25 + 35 = 60 > 30
        self.assertFalse(runner._can_dispatch(job))

    def test_cannot_dispatch_ram_over(self):
        runner = self._make_runner(max_ram=1000)
        runner._reserved_ram_mb = 900
        job = TestGroup(name="t", cmd="x", cost_cpu="low", cost_ram_mb=200)
        # 900 + 200 = 1100 > 1000
        self.assertFalse(runner._can_dispatch(job))

    def test_cannot_dispatch_exclusive_while_others_running(self):
        runner = self._make_runner()
        runner._reserved_cpu_pct = 35  # something running
        runner._reserved_ram_mb = 500
        job = TestGroup(name="bench", cmd="x", exclusive=True, cost_cpu="high", cost_ram_mb=100)
        self.assertFalse(runner._can_dispatch(job))

    def test_cannot_dispatch_while_exclusive_running(self):
        runner = self._make_runner()
        runner._exclusive_running = True
        job = TestGroup(name="t", cmd="x", cost_cpu="low", cost_ram_mb=100)
        self.assertFalse(runner._can_dispatch(job))


# ---------------------------------------------------------------------------
# 5. CLI argument parsing
# ---------------------------------------------------------------------------

class TestCLIParsing(unittest.TestCase):
    def _parse(self, args: list[str]) -> object:
        return _build_parser().parse_args(args)

    def test_list_flag(self):
        args = self._parse(["--list"])
        self.assertTrue(args.list)

    def test_group_flag(self):
        args = self._parse(["--group", "unit"])
        self.assertEqual(args.group, "unit")

    def test_composite_flag(self):
        args = self._parse(["--composite", "ci-fast"])
        self.assertEqual(args.composite, "ci-fast")

    def test_dry_run_flag(self):
        args = self._parse(["--group", "unit", "--dry-run"])
        self.assertTrue(args.dry_run)

    def test_auto_parallel_flag(self):
        args = self._parse(["--composite", "quick", "--auto-parallel"])
        self.assertTrue(args.auto_parallel)

    def test_max_cpu_default(self):
        args = self._parse(["--group", "unit"])
        self.assertEqual(args.max_cpu, 80)

    def test_parallel_custom(self):
        args = self._parse(["--group", "unit", "--parallel", "3"])
        self.assertEqual(args.parallel, 3)


# ---------------------------------------------------------------------------
# 6. _resolve_jobs
# ---------------------------------------------------------------------------

class TestResolveJobs(unittest.TestCase):
    def setUp(self):
        self.parsed = _parse_yaml_testgroups(MINIMAL_YAML)
        self.groups = self.parsed["groups"]
        self.composites = self.parsed["composites"]

    def test_resolve_single_group(self):
        parser = _build_parser()
        args = parser.parse_args(["--group", "unit"])
        jobs = _resolve_jobs(args, self.groups, self.composites)
        self.assertEqual(len(jobs), 1)
        self.assertEqual(jobs[0].name, "unit")

    def test_resolve_composite(self):
        parser = _build_parser()
        args = parser.parse_args(["--composite", "quick"])
        jobs = _resolve_jobs(args, self.groups, self.composites)
        names = [j.name for j in jobs]
        self.assertEqual(names, ["unit", "arch"])

    def test_resolve_invalid_group_exits(self):
        parser = _build_parser()
        args = parser.parse_args(["--group", "nonexistent"])
        with self.assertRaises(SystemExit):
            _resolve_jobs(args, self.groups, self.composites)


# ---------------------------------------------------------------------------
# 7. Cross-platform stat functions
# ---------------------------------------------------------------------------

class TestCrossPlatformStats(unittest.TestCase):
    def test_cpu_count_positive(self):
        self.assertGreater(get_cpu_count(), 0)

    def test_load_avg_non_negative(self):
        # load avg is 0.0 on Windows (no os.getloadavg), otherwise >= 0
        self.assertGreaterEqual(get_load_avg(), 0.0)

    def test_total_ram_reasonable(self):
        ram = get_total_ram_mb()
        # Should be between 512MB and 2TB
        self.assertGreater(ram, 512)
        self.assertLess(ram, 2 * 1024 * 1024)

    def test_load_groups_reads_file(self):
        """load_test_groups should read .ai/test-groups.yaml without error."""
        groups, composites = load_test_groups()
        self.assertIsInstance(groups, dict)
        self.assertIsInstance(composites, dict)
        self.assertIn("quick-check", groups)
        self.assertIn("quick", composites)

    def test_real_quick_is_live_demo_safe(self):
        """
        Regression guard for the live-demo quick composite.

        `quick` must stay genuinely quick: no Gradle/JUnit invocations. Full
        unit + ArchUnit validation belongs in ci-fast.
        """
        groups, composites = load_test_groups()
        jobs = [TestGroup.from_dict(name, groups[name]) for name in composites["quick"]]
        levels = _topological_levels(jobs)
        level_names = [[job.name for job in level] for level in levels]
        self.assertEqual(level_names, [["quick-check"]])
        self.assertLessEqual(sum(job.duration_estimate_sec for job in jobs), 5)
        self.assertTrue(all("./gradlew" not in job.cmd for job in jobs))

    def test_ci_fast_keeps_real_unit_and_archunit(self):
        """ci-fast remains the real verification composite beyond live quick."""
        groups, composites = load_test_groups()
        jobs = [TestGroup.from_dict(name, groups[name]) for name in composites["ci-fast"]]
        names = [job.name for job in jobs]
        self.assertIn("unit-java-fast", names)
        self.assertIn("arch", names)
        self.assertTrue(next(job for job in jobs if job.name == "arch").exclusive)


if __name__ == "__main__":
    unittest.main(verbosity=2)
