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
            (path / "TEST-db.xml").write_text('<testsuite tests="4" failures="0" errors="0" skipped="0">' + '<testcase name="ok"/>' * 4 + '</testsuite>')
            totals, problems = inventory.inspect_results(folder)
            self.assertEqual(4, totals["tests"])
            self.assertEqual([], problems)

    def test_all_skipped_is_not_a_pass(self):
        with tempfile.TemporaryDirectory() as folder:
            (Path(folder) / "TEST-skip.xml").write_text('<testsuite tests="3" skipped="3">' + '<testcase><skipped/></testcase>' * 3 + '</testsuite>')
            self.assertTrue(inventory.inspect_results(folder)[1])

    def test_failure_is_not_a_pass(self):
        with tempfile.TemporaryDirectory() as folder:
            (Path(folder) / "TEST-fail.xml").write_text('<testsuite tests="1" failures="1"><testcase><failure>broken</failure></testcase></testsuite>')
            self.assertTrue(inventory.inspect_results(folder)[1])

    def test_malformed_xml_is_not_ignored(self):
        with tempfile.TemporaryDirectory() as folder:
            (Path(folder) / "TEST-bad.xml").write_text('<invalid')
            self.assertTrue(inventory.inspect_results(folder)[1])

    def test_each_required_directory_is_enforced(self):
        with tempfile.TemporaryDirectory() as folder:
            good = Path(folder) / "good"
            good.mkdir()
            (good / "TEST-ok.xml").write_text('<testsuite tests="2"><testcase/><testcase/></testsuite>')
            with patch.object(inventory.sys, "argv", ["inventory", str(good), str(Path(folder) / "missing")]):
                self.assertEqual(1, inventory.main())


class InventoryBehaviorTest(unittest.TestCase):
    def setUp(self):
        folder = tempfile.TemporaryDirectory()
        self.addCleanup(folder.cleanup)
        self.root = Path(folder.name)
        self.report = self.root / "TEST-example.xml"
        self.baseline = self.root / "baseline.json"
        self.output = io.StringIO()
        output = contextlib.redirect_stdout(self.output)
        output.__enter__()
        self.addCleanup(output.__exit__, None, None, None)
        environment = patch.dict(os.environ, {"GITHUB_STEP_SUMMARY": ""})
        environment.start()
        self.addCleanup(environment.stop)

    def write_report(self, cases='<testcase name="first"/><testcase name="second"/>', counts='tests="2"'):
        self.report.write_text(f'<testsuite {counts}>{cases}</testsuite>', encoding="utf-8")

    def run_main(self, baseline):
        import json
        self.baseline.write_text(json.dumps(baseline), encoding="utf-8")
        with patch.object(inventory, "BASELINE_FILE", self.baseline), patch.object(inventory.sys, "argv", ["inventory", str(self.root)]):
            return inventory.main()

    def test_real_testcases_satisfy_the_baseline(self):
        self.write_report()
        self.assertEqual(0, self.run_main({str(self.root): {"tests": 2, "suites": 1}}))
        totals, problems = inventory.inspect_results(self.root)
        self.assertEqual((2, 2, 2), (totals["tests"], totals["executed"], totals["passed"]))
        self.assertEqual([], problems)

    def test_missing_and_malformed_baseline_fail_with_annotations(self):
        self.write_report()
        for content in (None, "not JSON", "[]", "{}"):
            with self.subTest(content=content):
                if content is None:
                    self.baseline.unlink(missing_ok=True)
                else:
                    self.baseline.write_text(content, encoding="utf-8")
                with patch.object(inventory, "BASELINE_FILE", self.baseline), patch.object(inventory.sys, "argv", ["inventory", str(self.root)]):
                    self.assertEqual(1, inventory.main())
        self.assertIn("::error::", self.output.getvalue())

    def test_missing_baseline_key_is_a_main_failure(self):
        self.write_report()
        self.assertEqual(1, self.run_main({"different/root": {"tests": 2, "suites": 1}}))
        self.assertIn("Missing test baseline entry", self.output.getvalue())

    def test_invalid_baseline_entry_is_a_main_failure(self):
        self.write_report()
        for entry in ({}, {"tests": 2}, {"tests": -1, "suites": 1}, {"tests": "2", "suites": 1}, {"tests": 2, "suites": True}):
            with self.subTest(entry=entry):
                self.assertEqual(1, self.run_main({str(self.root): entry}))

    def test_partial_skip_cannot_satisfy_existing_test_baseline(self):
        self.write_report('<testcase/><testcase><skipped/></testcase>', 'tests="2" skipped="1"')
        totals, problems = inventory.inspect_results(self.root)
        self.assertEqual([], problems)
        self.assertEqual((2, 1, 1), (totals["tests"], totals["executed"], totals["passed"]))
        self.assertEqual(1, self.run_main({str(self.root): {"tests": 2, "suites": 1}}))
        self.assertIn("Executed count fell below baseline", self.output.getvalue())
        self.assertIn("Passed count fell below baseline", self.output.getvalue())

    def test_explicit_executed_baseline_is_enforced(self):
        self.write_report('<testcase/><testcase><skipped/></testcase>', 'tests="2" skipped="1"')
        self.assertEqual(0, self.run_main({str(self.root): {"tests": 2, "suites": 1, "executed": 1}}))
        self.assertEqual(1, self.run_main({str(self.root): {"tests": 2, "suites": 1, "executed": 2}}))

    def test_aggregate_only_xml_cannot_invent_test_execution(self):
        self.write_report("", 'tests="100"')
        totals, problems = inventory.inspect_results(self.root)
        self.assertEqual(0, totals["executed"])
        self.assertTrue(any("summary mismatch" in problem for problem in problems))

    def test_every_summary_counter_must_match_testcases(self):
        for counts, cases in (
            ('tests="3"', '<testcase/><testcase/>'),
            ('tests="1"', '<testcase/><testcase/>'),
            ('tests="2" failures="1"', '<testcase/><testcase/>'),
            ('tests="2" errors="1"', '<testcase/><testcase/>'),
            ('tests="2" skipped="1"', '<testcase/><testcase/>'),
            ('tests="2"', '<testcase/><testcase><skipped/></testcase>'),
            ('tests="-1"', '<testcase/>'),
            ('tests="not a number"', '<testcase/>'),
            ('', '<testcase/>'),
        ):
            with self.subTest(counts=counts, cases=cases):
                self.write_report(cases, counts)
                self.assertTrue(inventory.inspect_results(self.root)[1])

    def test_nested_suite_containers_do_not_double_count(self):
        self.report.write_text('<testsuites tests="2"><testsuite tests="2"><testsuite tests="1"><testcase/></testsuite><testsuite tests="1"><testcase/></testsuite></testsuite></testsuites>', encoding="utf-8")
        totals, problems = inventory.inspect_results(self.root)
        self.assertEqual([], problems)
        self.assertEqual((2, 2, 2), (totals["tests"], totals["executed"], totals["suites"]))
        self.report.write_text('<testsuites tests="3"><testsuite tests="1"><testcase/></testsuite></testsuites>', encoding="utf-8")
        self.assertTrue(inventory.inspect_results(self.root)[1])

    def test_failure_annotation_survives_bad_summary(self):
        self.write_report('<testcase classname="Example" name="broken"><failure>first line\nsecond line</failure></testcase>', 'tests="9"')
        self.assertEqual(1, self.run_main({str(self.root): {"tests": 1, "suites": 1}}))
        self.assertIn("::error::", self.output.getvalue())
        self.assertIn("FAILED Example.broken: first line", self.output.getvalue())
        self.assertIn("summary mismatch", self.output.getvalue())

    def test_failure_error_and_skip_outcomes_are_counted(self):
        self.write_report('<testcase/><testcase><failure/></testcase><testcase><error/></testcase><testcase><skipped/></testcase>', 'tests="4" failures="1" errors="1" skipped="1"')
        totals, problems = inventory.inspect_results(self.root)
        self.assertEqual((4, 3, 1, 1, 1, 1), tuple(totals[key] for key in ("tests", "executed", "passed", "failures", "errors", "skipped")))
        self.assertIn("Tests reported failures or errors", problems)

    def test_conflicting_case_outcomes_are_invalid(self):
        self.write_report('<testcase><skipped/><failure/></testcase>', 'tests="1" skipped="1" failures="1"')
        self.assertTrue(any("conflicting" in problem for problem in inventory.inspect_results(self.root)[1]))


class LocalGuardTest(unittest.TestCase):
    def setUp(self):
        self.output = contextlib.redirect_stdout(io.StringIO())
        self.output.__enter__()
        self.addCleanup(self.output.__exit__, None, None, None)

    def test_default_plan_does_not_start_gradle(self):
        with patch.object(verify.sys, "argv", ["verify"]), patch.object(verify, "run_step", return_value=True) as run:
            self.assertEqual(0, verify.main())
            self.assertFalse(any("gradlew" in str(call) for call in run.call_args_list))

    def test_local_logic_and_full_allow_checks_but_never_package(self):
        for level in ("logic", "full"):
            with patch.dict(os.environ, {"GITHUB_ACTIONS": "false"}), patch.object(verify.sys, "argv", ["verify", level]), patch.object(verify, "run_step", return_value=True) as run:
                self.assertEqual(0, verify.main())
                self.assertTrue(any("gradlew" in str(call) for call in run.call_args_list))
                for call in run.call_args_list:
                    for argument in call.args[1]:
                        self.assertFalse(any(word in argument.lower() for word in ("assemble", "package", "bundle", "connected")))

    def test_cloud_full_includes_instrumentation_compile(self):
        with patch.dict(os.environ, {"GITHUB_ACTIONS": "true"}), patch.object(verify.sys, "argv", ["verify", "full"]), patch.object(verify, "run_step", return_value=True) as run:
            self.assertEqual(0, verify.main())
            self.assertTrue(any(":app:compileDebugAndroidTestKotlin" in str(call) for call in run.call_args_list))


class DeviceMigrationGateContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.root = TOOLS.parent
        cls.workflow = (cls.root / ".github/workflows/check.yml").read_text(encoding="utf-8")

    def job(self, name):
        import re
        match = re.search(rf"^  {name}:\n(.*?)(?=^  [a-z][a-z-]*:|\Z)", self.workflow, re.M | re.S)
        self.assertIsNotNone(match, f"Missing workflow job {name}")
        return match.group(1)

    def test_both_sqlite_generations_run_the_complete_device_suite(self):
        device = self.job("android-device-check")
        self.assertRegex(device, r"(?m)^      fail-fast: false$")
        self.assertRegex(device, r"(?m)^        api-level: \[33, 35\]$")
        self.assertIn("api-level: ${{ matrix.api-level }}", device)
        self.assertIn(":app:connectedDebugAndroidTest --no-daemon --console=plain", device)
        self.assertNotIn("android.testInstrumentationRunnerArguments.class", device)
        self.assertNotIn("continue-on-error:", device)
        self.assertNotIn("continue-on-error:", self.workflow)
        self.assertIn("python3 tools/test_inventory.py app/build/outputs/androidTest-results/connected", device)
        self.assertIn("name: android-device-reports-api-${{ matrix.api-level }}", device)
        self.assertIn('"Android device API ${{ matrix.api-level }}"', device)

    def test_check_gate_requires_all_matrix_children_and_other_jobs(self):
        gate = self.job("check")
        self.assertIn("if: always()", gate)
        self.assertIn("needs: [source-hygiene, android-jvm-check, android-device-check]", gate)
        for prefix, job in (("SOURCE", "source-hygiene"), ("JVM", "android-jvm-check"), ("DEVICE", "android-device-check")):
            self.assertIn(f"{prefix}_RESULT: ${{{{ needs.{job}.result }}}}", gate)
            self.assertIn(f'test "${prefix}_RESULT" = success', gate)
        self.assertNotIn("continue-on-error:", gate)

    def test_preview_wait_covers_device_matrix_and_fails_closed(self):
        text = (self.root / ".github/workflows/build-apk.yml").read_text(encoding="utf-8")
        preview = text.split("  publish-preview:", 1)[1]
        self.assertIn("timeout-minutes: 50", preview)
        self.assertIn("seq 1 120", preview)
        self.assertIn("sleep 20", preview)
        self.assertIn("curl --fail --connect-timeout 10 --max-time 30", preview)
        self.assertLess(preview.index("Require a green check workflow"), preview.index("Download APK"))
        self.assertIn('if [ "$CONCLUSION" = "success" ]; then exit 0; fi', preview)
        self.assertIn("refusing to publish a preview", preview)

    def test_manual_v25_fixture_reaches_current_schema_through_v26(self):
        import json
        import re
        source = (self.root / "app/src/androidTest/kotlin/com/lucent/app/data/AppDatabaseMigrationTest.kt").read_text(encoding="utf-8")
        chain = source.split("fun fullChain25To36_preservesNotesAndChatAndValidatesCurrentRoomSchema()", 1)[1]
        chain = chain.split("private fun readRows", 1)[0]
        self.assertLessEqual(len(source.splitlines()), 1200)
        self.assertIn("createLegacyDatabase(25)", chain)
        self.assertIn("runLegacyMigrations(26, MIGRATION_25_26)", chain)
        self.assertNotIn("helper.createDatabase", chain)
        self.assertRegex(chain, r"helper\.runMigrationsAndValidate\(\s*legacyDatabaseName, 36, true,")
        self.assertEqual(
            [f"MIGRATION_{version}_{version + 1}" for version in range(25, 36)],
            re.findall(r"MIGRATION_\d+_\d+", chain),
        )
        self.assertIn('assertEquals(originalNotes, readRows(db, "notes", noteColumns))', chain)
        self.assertIn('assertEquals(originalChat, readRows(db, "chat_messages", chatColumns))', chain)
        for index in ("index_notes_isDraft", "index_notes_hidden", "index_tasks_isDraft", "index_tasks_hidden", "index_notes_title", "index_chat_messages_conversationId_timestamp", "index_notebooks_manualOrder_updatedAt", "index_notebook_items_notebookId_addedAt"):
            self.assertIn(index, chain)
        schema = json.loads((self.root / "app/schemas/com.lucent.app.data.AppDatabase/36.json").read_text(encoding="utf-8"))
        self.assertEqual(36, schema["database"]["version"])


if __name__ == "__main__":
    unittest.main()
