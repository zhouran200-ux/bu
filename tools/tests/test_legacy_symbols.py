import json
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
MANIFEST = ROOT / "tools/legacy_symbols.json"
SOURCE_ROOTS = ["app/src", "baselineprofile/src"]
SKIP_DIRS = {"build"}


def sources():
    for root in SOURCE_ROOTS:
        base = ROOT / root
        for path in base.rglob("*"):
            if path.is_file() and path.suffix in {".kt", ".kts"}:
                if SKIP_DIRS.intersection(path.parts):
                    continue
                yield path


class LegacySymbolTest(unittest.TestCase):
    def test_retired_symbols_never_come_back(self):
        retired = json.loads(MANIFEST.read_text(encoding="utf-8"))["retired"]
        self.assertTrue(retired, "the retired symbol list must not be empty")
        for symbol in retired:
            hits = []
            for path in sources():
                text = path.read_text(encoding="utf-8")
                if symbol in text:
                    hits.append(str(path.relative_to(ROOT)).replace("\\", "/"))
            self.assertEqual([], hits, f"retired symbol {symbol} is still referenced")

    def test_manifest_stays_sorted_and_unique(self):
        retired = json.loads(MANIFEST.read_text(encoding="utf-8"))["retired"]
        self.assertEqual(sorted(set(retired)), sorted(retired))
