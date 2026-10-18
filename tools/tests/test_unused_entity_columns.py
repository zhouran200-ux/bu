import json
import re
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
ENTITIES = ROOT / "app/src/main/java/com/lucent/app/data/Entities.kt"
BASELINE = ROOT / "tools/unused_columns_baseline.json"
MAIN = ROOT / "app/src/main/java"


def declared_columns():
    names = []
    for line in ENTITIES.read_text(encoding="utf-8").splitlines():
        match = re.match(r"\s+val (\w+):", line)
        if match:
            names.append(match.group(1))
    return names


def used_outside_data(column, sources):
    pattern = re.compile(rf"\b{re.escape(column)}\b")
    for path, text in sources:
        if pattern.search(text):
            return True
    return False


class UnusedEntityColumnTest(unittest.TestCase):
    def test_only_known_unused_columns_remain(self):
        sources = []
        for path in MAIN.rglob("*.kt"):
            if "\\data\\" in str(path):
                continue
            sources.append((path, path.read_text(encoding="utf-8")))
        unused = sorted({
            column for column in declared_columns()
            if not used_outside_data(column, sources)
        })
        baseline = json.loads(BASELINE.read_text(encoding="utf-8"))["columns"]
        self.assertEqual(sorted(baseline), unused, "entity columns changed without review")

    def test_baseline_is_sorted_and_unique(self):
        baseline = json.loads(BASELINE.read_text(encoding="utf-8"))["columns"]
        self.assertEqual(sorted(set(baseline)), sorted(baseline))
