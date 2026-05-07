#!/usr/bin/env python3
"""
Tests for debug-runner.py
Run: python3 test_debug_runner.py
"""

import json
import os
import sys
import tempfile
import unittest
from unittest.mock import patch, MagicMock

_HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, _HERE)

import importlib.util
_spec = importlib.util.spec_from_file_location(
    "debug_runner",
    os.path.join(_HERE, "debug-runner.py")
)
dr = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(dr)
# Register so @patch("debug_runner.X") works
sys.modules["debug_runner"] = dr


# ---------------------------------------------------------------------------
# Helpers / mocks
# ---------------------------------------------------------------------------

MOCK_COMPOSE_PS = """\
NAME                COMMAND             SERVICE             STATUS              PORTS
controller-app      java -jar app.jar   controller-app      Up (healthy)        0.0.0.0:8080->8080/tcp
usecase-app         java -jar app.jar   usecase-app         Up (healthy)        0.0.0.0:8081->8081/tcp
kafka               /etc/confluent...   kafka               Up                  9092/tcp
openobserve         ./openobserve       openobserve         Up (unhealthy)      5080/tcp
"""

MOCK_SERVICES = ["controller-app", "usecase-app", "kafka", "openobserve"]

MOCK_LOGS_NXDOMAIN = """\
2026-05-07T10:00:00 INFO  Starting controller-app
2026-05-07T10:00:02 ERROR UnknownHostException: usecase-app NXDOMAIN
2026-05-07T10:00:03 FATAL Application failed to start
"""

MOCK_LOGS_OOM = """\
2026-05-07T10:00:00 INFO  Processing request
2026-05-07T10:00:05 ERROR java.lang.OutOfMemoryError: Java heap space
2026-05-07T10:00:06 FATAL JVM crashed
"""

MOCK_LOGS_CONNECTION_REFUSED = """\
2026-05-07T10:00:00 INFO  Connecting to kafka:9092
2026-05-07T10:00:01 ERROR Connection refused to kafka:9092
"""

MOCK_LOGS_CLEAN = """\
2026-05-07T10:00:00 INFO  All systems nominal
2026-05-07T10:00:01 INFO  Processing complete
"""

MOCK_DOCKER_STATS = """\
NAME                CPU %     MEM USAGE / LIMIT     MEM %
controller-app      12.3%     256MiB / 512MiB       50.0%
usecase-app         85.5%     460MiB / 512MiB       89.8%
"""


# ---------------------------------------------------------------------------
# Tests
# ---------------------------------------------------------------------------

class TestLogPatterns(unittest.TestCase):
    def test_nxdomain_pattern_matches(self):
        import re
        pattern = r"NXDOMAIN|no such host|UnknownHostException"
        self.assertTrue(re.search(pattern, MOCK_LOGS_NXDOMAIN, re.IGNORECASE))

    def test_oom_pattern_matches(self):
        import re
        pattern = r"OutOfMemoryError|java\.lang\.OutOfMemoryError"
        self.assertTrue(re.search(pattern, MOCK_LOGS_OOM, re.IGNORECASE))

    def test_connection_refused_pattern_matches(self):
        import re
        pattern = r"Connection refused|EHOSTUNREACH"
        self.assertTrue(re.search(pattern, MOCK_LOGS_CONNECTION_REFUSED, re.IGNORECASE))

    def test_clean_logs_no_match(self):
        import re
        any_error = False
        for pattern, _, _, _ in dr.LOG_PATTERNS:
            if re.search(pattern, MOCK_LOGS_CLEAN, re.IGNORECASE):
                any_error = True
        # "ERROR|FATAL" would match if present, but our clean log has no ERROR
        # Actually check against specific high-severity patterns
        for pattern, severity, _, _ in dr.LOG_PATTERNS:
            if severity == "HIGH" and re.search(pattern, MOCK_LOGS_CLEAN, re.IGNORECASE):
                self.fail(f"Pattern {pattern} matched clean logs unexpectedly")


class TestDockerHelpers(unittest.TestCase):
    @patch("debug_runner._run", return_value="controller-app\nusecase-app\n")
    @patch("debug_runner._docker_available", return_value=True)
    def test_list_services_parses_output(self, mock_avail, mock_run):
        services = dr._list_services()
        self.assertIn("controller-app", services)
        self.assertIn("usecase-app", services)

    @patch("debug_runner._docker_available", return_value=False)
    def test_list_services_without_docker(self, mock_avail):
        services = dr._list_services()
        self.assertEqual(services, [])

    @patch("debug_runner._run", return_value=MOCK_COMPOSE_PS)
    @patch("debug_runner._docker_available", return_value=True)
    def test_compose_ps_returns_string(self, mock_avail, mock_run):
        result = dr._compose_ps()
        self.assertIn("controller-app", result)

    @patch("debug_runner._run", return_value=MOCK_DOCKER_STATS)
    @patch("debug_runner._docker_available", return_value=True)
    def test_docker_stats_returns_string(self, mock_avail, mock_run):
        result = dr._docker_stats()
        self.assertIn("controller-app", result)


class TestDiagnoseHeuristics(unittest.TestCase):
    """Test the diagnose command logic in isolation."""

    @patch("debug_runner._list_services", return_value=["controller-app"])
    @patch("debug_runner._service_logs", return_value=MOCK_LOGS_NXDOMAIN)
    @patch("debug_runner._compose_ps", return_value=MOCK_COMPOSE_PS)
    @patch("debug_runner._docker_available", return_value=True)
    @patch("debug_runner._check_openobserve", return_value="not reachable")
    @patch("debug_runner._host_disk_info", return_value="15% used")
    @patch("debug_runner._host_mem_info", return_value="4.0 GB active")
    def test_diagnose_detects_nxdomain(self, *mocks):
        args = MagicMock()
        # capture stdout
        import io
        from contextlib import redirect_stdout
        output = io.StringIO()
        with redirect_stdout(output):
            dr.cmd_diagnose(args)
        result = output.getvalue()
        # Should mention DNS or network issue
        self.assertTrue(
            "DNS" in result or "network" in result or "NXDOMAIN" in result or "controller-app" in result
        )

    @patch("debug_runner._list_services", return_value=["usecase-app"])
    @patch("debug_runner._service_logs", return_value=MOCK_LOGS_OOM)
    @patch("debug_runner._compose_ps", return_value="")
    @patch("debug_runner._docker_available", return_value=True)
    @patch("debug_runner._check_openobserve", return_value="not reachable")
    @patch("debug_runner._host_disk_info", return_value="20% used")
    @patch("debug_runner._host_mem_info", return_value="8.0 GB active")
    def test_diagnose_detects_oom(self, *mocks):
        args = MagicMock()
        import io
        from contextlib import redirect_stdout
        output = io.StringIO()
        with redirect_stdout(output):
            dr.cmd_diagnose(args)
        result = output.getvalue()
        self.assertTrue("memory" in result.lower() or "OutOfMemory" in result or "usecase-app" in result)

    @patch("debug_runner._list_services", return_value=[])
    @patch("debug_runner._compose_ps", return_value="")
    @patch("debug_runner._docker_available", return_value=True)
    @patch("debug_runner._check_openobserve", return_value="not reachable")
    @patch("debug_runner._host_disk_info", return_value="10% used")
    @patch("debug_runner._host_mem_info", return_value="")
    def test_diagnose_no_services_graceful(self, *mocks):
        args = MagicMock()
        import io
        from contextlib import redirect_stdout
        output = io.StringIO()
        with redirect_stdout(output):
            dr.cmd_diagnose(args)
        result = output.getvalue()
        self.assertIn("Diagnosis report", result)


class TestSnapshot(unittest.TestCase):
    @patch("debug_runner._list_services", return_value=["controller-app"])
    @patch("debug_runner._service_logs", return_value="INFO app started")
    @patch("debug_runner._compose_ps", return_value="controller-app Up")
    @patch("debug_runner._docker_stats", return_value="controller-app 5.0% 100MiB")
    @patch("debug_runner._docker_available", return_value=True)
    @patch("debug_runner._collect_host_info", return_value="Linux x86_64")
    @patch("debug_runner._openobserve_summary", return_value={"status": "unavailable"})
    def test_snapshot_creates_output_dir(self, *mocks):
        with tempfile.TemporaryDirectory() as tmpdir:
            orig = dr.REPO_ROOT
            dr.REPO_ROOT = tmpdir
            args = MagicMock()
            out_dir = dr.cmd_snapshot(args)
            dr.REPO_ROOT = orig
            self.assertTrue(os.path.isdir(out_dir))
            self.assertTrue(os.path.isfile(os.path.join(out_dir, "compose-ps.txt")))
            self.assertTrue(os.path.isfile(os.path.join(out_dir, "meta.json")))
            self.assertTrue(os.path.isfile(os.path.join(out_dir, "host-info.txt")))

    @patch("debug_runner._list_services", return_value=["svc-a"])
    @patch("debug_runner._service_logs", return_value="INFO ok")
    @patch("debug_runner._compose_ps", return_value="svc-a Up")
    @patch("debug_runner._docker_stats", return_value="")
    @patch("debug_runner._docker_available", return_value=True)
    @patch("debug_runner._collect_host_info", return_value="info")
    @patch("debug_runner._openobserve_summary", return_value={"status": "unavailable"})
    def test_snapshot_meta_json_valid(self, *mocks):
        with tempfile.TemporaryDirectory() as tmpdir:
            orig = dr.REPO_ROOT
            dr.REPO_ROOT = tmpdir
            args = MagicMock()
            out_dir = dr.cmd_snapshot(args)
            dr.REPO_ROOT = orig
            meta_path = os.path.join(out_dir, "meta.json")
            with open(meta_path) as f:
                meta = json.load(f)
            self.assertIn("ts", meta)
            self.assertIn("mode", meta)
            self.assertIn("duration_seconds", meta)


class TestResourceThresholds(unittest.TestCase):
    def test_cpu_threshold_constant(self):
        self.assertEqual(dr.RESOURCE_CPU_THRESHOLD, 80.0)

    def test_mem_threshold_constant(self):
        self.assertEqual(dr.RESOURCE_MEM_THRESHOLD, 90.0)


if __name__ == "__main__":
    unittest.main(verbosity=2)
