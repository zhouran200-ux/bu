import os
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent


def inspect_results(root):
    totals = dict(tests=0, failures=0, errors=0, skipped=0, suites=0)
    problems = []
    files = sorted(Path(root).rglob("TEST-*.xml"))
    if not files:
        problems.append(f"No test XML reports at {root}")
    for path in files:
        try:
            report = ET.parse(path).getroot()
            suites = [report] if report.tag == "testsuite" else report.findall(".//testsuite")
            if not suites:
                raise ValueError("no testsuite element")
            for suite in suites:
                for key in ("tests", "failures", "errors", "skipped"):
                    value = int(suite.get(key, "0"))
                    if value < 0:
                        raise ValueError(f"negative {key}")
                    totals[key] += value
                totals["suites"] += 1
        except (ET.ParseError, ValueError, OSError) as error:
            problems.append(f"Invalid report {path}: {error}")
    if totals["tests"] - totals["skipped"] <= 0:
        problems.append("No non-skipped tests executed")
    if totals["failures"] or totals["errors"]:
        problems.append("Tests reported failures or errors")
    return totals, problems


def main():
    roots = sys.argv[1:] or ["app/build/test-results/testDebugUnitTest"]
    failed = False
    for relative in roots:
        root = Path(relative)
        if not root.is_absolute():
            root = REPO / root
        totals, problems = inspect_results(root)
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
