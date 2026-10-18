import json
import re
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
AUDIT = ROOT / "tools/design_audit.py"
FROZEN = ROOT / "tools/design_whitelist.json"


class DesignWhitelistTest(unittest.TestCase):
    def test_whitelist_matches_the_frozen_review_list(self):
        source = AUDIT.read_text(encoding="utf-8")
        block = source.split("PALETTE_FILES = {", 1)[1].split("}", 1)[0]
        declared = sorted(re.findall(r'"([^"]+\.kt)"', block))
        frozen = sorted(json.loads(FROZEN.read_text(encoding="utf-8"))["files"])
        self.assertEqual(frozen, declared, "design whitelist changed without review")

    def test_whitelisted_files_exist(self):
        for relative in json.loads(FROZEN.read_text(encoding="utf-8"))["files"]:
            self.assertTrue((ROOT / relative).is_file(), relative)
