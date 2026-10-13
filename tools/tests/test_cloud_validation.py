import importlib.util
import contextlib
import io
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

TOOLS = Path(__file__).resolve().parents[1]


def load(name):
    spec = importlib.util.spec_from_file_location(name, TOOLS / f"{name}.py")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


inventory = load("test_inventory")
verify = load("verify")


class ReportValidationTest(unittest.TestCase):
    def setUp(self):
        environment = patch.dict(os.environ, {"GITHUB_STEP_SUMMARY": ""})
        environment.start()
        self.addCleanup(environment.stop)
        self.output = contextlib.redirect_stdout(io.StringIO())
        self.output.__enter__()
        self.addCleanup(self.output.__exit__, None, None, None)

    def test_missing_reports_fail(self):
        with tempfile.TemporaryDirectory() as folder:
            totals, problems = inventory.inspect_results(folder)
            self.assertEqual(0, totals["tests"])
            self.assertTrue(problems)

    def test_nested_instrumentation_reports_count(self):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / "debug" / "device"
            path.mkdir(parents=True)
            (path / "TEST-db.xml").write_text('<testsuite tests="4" failures="0" errors="0" skipped="0"/>')
            totals, problems = inventory.inspect_results(folder)
            self.assertEqual(4, totals["tests"])
            self.assertEqual([], problems)

    def test_all_skipped_is_not_a_pass(self):
        with tempfile.TemporaryDirectory() as folder:
            (Path(folder) / "TEST-skip.xml").write_text('<testsuite tests="3" skipped="3"/>')
            self.assertTrue(inventory.inspect_results(folder)[1])

    def test_failure_is_not_a_pass(self):
        with tempfile.TemporaryDirectory() as folder:
            (Path(folder) / "TEST-fail.xml").write_text('<testsuite tests="3" failures="1"/>')
            self.assertTrue(inventory.inspect_results(folder)[1])

    def test_malformed_xml_is_not_ignored(self):
        with tempfile.TemporaryDirectory() as folder:
            (Path(folder) / "TEST-bad.xml").write_text('<invalid')
            self.assertTrue(inventory.inspect_results(folder)[1])

    def test_each_required_directory_is_enforced(self):
        with tempfile.TemporaryDirectory() as folder:
            good = Path(folder) / "good"
            good.mkdir()
            (good / "TEST-ok.xml").write_text('<testsuite tests="2"/>')
            with patch.object(inventory.sys, "argv", ["inventory", str(good), str(Path(folder) / "missing")]):
                self.assertEqual(1, inventory.main())


class LocalGuardTest(unittest.TestCase):
    def setUp(self):
        self.output = contextlib.redirect_stdout(io.StringIO())
        self.output.__enter__()
        self.addCleanup(self.output.__exit__, None, None, None)

    def test_default_plan_does_not_start_gradle(self):
        with patch.object(verify.sys, "argv", ["verify"]), patch.object(verify, "run_step", return_value=True) as run:
            self.assertEqual(0, verify.main())
            self.assertFalse(any("gradlew" in str(call) for call in run.call_args_list))

    def test_local_logic_and_full_are_rejected_before_execution(self):
        for level in ("logic", "full"):
            with patch.dict(os.environ, {"GITHUB_ACTIONS": "false"}), patch.object(verify.sys, "argv", ["verify", level]), patch.object(verify, "run_step") as run:
                self.assertEqual(2, verify.main())
                run.assert_not_called()

    def test_cloud_full_includes_instrumentation_compile(self):
        with patch.dict(os.environ, {"GITHUB_ACTIONS": "true"}), patch.object(verify.sys, "argv", ["verify", "full"]), patch.object(verify, "run_step", return_value=True) as run:
            self.assertEqual(0, verify.main())
            self.assertTrue(any(":app:compileDebugAndroidTestKotlin" in str(call) for call in run.call_args_list))


if __name__ == "__main__":
    unittest.main()
