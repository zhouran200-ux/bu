import json
import os
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
BASELINE_FILE = REPO / "tools/test-baseline.json"


def load_baseline():
    try:
        baseline = json.loads(BASELINE_FILE.read_text(encoding="utf-8"))
    except (OSError, ValueError) as error:
        raise ValueError(f"Cannot load test baseline {BASELINE_FILE}: {error}") from error
    if not isinstance(baseline, dict) or not baseline:
        raise ValueError(f"Test baseline {BASELINE_FILE} must be a non-empty object")
    return baseline


def check_baseline(relative, totals, baseline):
    expected = baseline.get(relative)
    if not isinstance(expected, dict) or not expected:
        return [f"Missing test baseline entry for {relative}"]
    # Existing 'tests' baselines now require that many successful, non-skipped
    # testcases. An explicit executed minimum may be supplied for newer baselines.
    minima = {"tests": expected.get("tests"), "suites": expected.get("suites"),
              "executed": expected.get("executed", expected.get("tests"))}
    if any(type(value) is not int or value <= 0 for value in minima.values()):
        return [f"Invalid test baseline entry for {relative}: positive integer tests/suites/executed required"]
    problems = []
    for key, minimum in minima.items():
        if totals[key] < minimum:
            problems.append(f"{key.capitalize()} count fell below baseline ({totals[key]} < {minimum}); a test or suite disappeared or was skipped")
    if totals["passed"] < minima["executed"]:
        problems.append(f"Passed count fell below baseline ({totals['passed']} < {minima['executed']}); skipped or failed tests cannot satisfy the baseline")
    return problems


def case_totals(cases):
    totals = dict(tests=len(cases), failures=0, errors=0, skipped=0, executed=0, passed=0)
    for case in cases:
        failure = case.find("failure") is not None
        error = case.find("error") is not None
        skipped = case.find("skipped") is not None
        if sum((failure, error, skipped)) > 1:
            raise ValueError("testcase has conflicting failure/error/skipped outcomes")
        totals["failures"] += int(failure)
        totals["errors"] += int(error)
        totals["skipped"] += int(skipped)
        totals["executed"] += int(not skipped)
        totals["passed"] += int(not (failure or error or skipped))
    return totals


def validate_summary(element, actual, required_tests=True):
    for key in ("tests", "failures", "errors", "skipped"):
        raw = element.get(key)
        if raw is None and key == "tests" and required_tests:
            raise ValueError("testsuite is missing tests count")
        if raw is None and not required_tests:
            continue
        declared = int(raw or "0")
        if declared < 0 or declared != actual[key]:
            raise ValueError(f"{key} summary mismatch: declared {declared}, actual testcases {actual[key]}")


def inspect_results(root):
    totals = dict(tests=0, failures=0, errors=0, skipped=0, suites=0, executed=0, passed=0)
    problems = []
    files = sorted(Path(root).rglob("TEST-*.xml"))
    if not files:
        problems.append(f"No test XML reports at {root}")
    for path in files:
        try:
            report = ET.parse(path).getroot()
            if report.tag not in ("testsuite", "testsuites"):
                raise ValueError("root must be testsuite or testsuites")
            suites = list(report.iter("testsuite"))
            if not suites:
                raise ValueError("no testsuite element")
            cases = list(report.iter("testcase"))
            # Emit real failures even when the enclosing report has bad totals.
            for case in cases:
                if case.find("failure") is None and case.find("error") is None:
                    continue
                name = case.get("name", "?")
                klass = case.get("classname", "?")
                detail = (case.findtext("failure") or case.findtext("error") or "").strip()
                first = detail.splitlines()[0] if detail else ""
                problems.append(f"FAILED {klass}.{name}: {first[:180]}")
            actual = case_totals(cases)
            for key, value in actual.items():
                totals[key] += value
            # Nested container suites summarize descendants; count their cases once.
            totals["suites"] += sum(bool(suite.findall("testcase")) or not suite.findall("testsuite") for suite in suites)
            for suite in suites:
                validate_summary(suite, case_totals(list(suite.iter("testcase"))))
            if report.tag == "testsuites":
                validate_summary(report, actual, required_tests=False)
            if len(cases) != sum(len(suite.findall("testcase")) for suite in suites):
                raise ValueError("testcase outside testsuite")
        except (ET.ParseError, ValueError, OSError) as error:
            problems.append(f"Invalid report {path}: {error}")
    if totals["executed"] <= 0:
        problems.append("No non-skipped tests executed")
    if totals["passed"] <= 0:
        problems.append("No passed tests executed")
    if totals["failures"] or totals["errors"]:
        problems.append("Tests reported failures or errors")
    return totals, problems


def main():
    roots = sys.argv[1:] or ["app/build/test-results/testDebugUnitTest"]
    try:
        baseline = load_baseline()
    except ValueError as error:
        print(f"::error::{error}")
        return 1
    failed = False
    for relative in roots:
        root = Path(relative)
        if not root.is_absolute():
            root = REPO / root
        totals, problems = inspect_results(root)
        problems.extend(check_baseline(relative, totals, baseline))
        text = f"{relative}: {totals}"
        print(text)
        summary = os.environ.get("GITHUB_STEP_SUMMARY")
        if summary:
            with open(summary, "a", encoding="utf-8") as handle:
                handle.write(f"- {text}\n")
        for problem in problems:
            print(f"::error::{relative}: {problem}")
        failed |= bool(problems)
    return int(failed)


if __name__ == "__main__":
    sys.exit(main())
