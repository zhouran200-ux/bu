import json
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
UI = ROOT / "app/src/main/java/com/lucent/app/ui"
BASELINE = ROOT / "tools/ui_boundary_baseline.json"
FORBIDDEN = ["AppDatabase", "Dao()", "FontStore.import", "FontStore.delete", "LlmClient.fetchModels", "LlmClient.testConnection"]


class UiLayerBoundaryTest(unittest.TestCase):
    def test_ui_layer_holds_no_persistence_or_network_owners(self):
        baseline = set(json.loads(BASELINE.read_text(encoding="utf-8"))["offenders"])
        offenders = set()
        for path in UI.rglob("*.kt"):
            text = path.read_text(encoding="utf-8")
            if any(token in text for token in FORBIDDEN):
                offenders.add(path.name)
        self.assertEqual(baseline, offenders, "update tools/ui_boundary_baseline.json only by removing entries")

    def test_baseline_only_shrinks(self):
        baseline = json.loads(BASELINE.read_text(encoding="utf-8"))["offenders"]
        self.assertEqual(sorted(set(baseline)), sorted(baseline))
        self.assertLessEqual(len(baseline), 6)
