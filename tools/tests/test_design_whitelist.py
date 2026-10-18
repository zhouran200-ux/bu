import contextlib
import importlib.util
import io
import json
from pathlib import Path
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location("design_audit", ROOT / "tools/design_audit.py")
audit = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(audit)


class DesignWhitelistTest(unittest.TestCase):
    def run_audit(self, text, definitions=None, strict=True):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            relative = audit.UI_ROOT + "/Palette.kt"
            source = root / relative
            source.parent.mkdir(parents=True)
            source.write_text(text, encoding="utf-8")
            baseline = root / "baseline.json"
            baseline.write_text(json.dumps({
                "schema_version": 2, "policy": "Review exact color owners", "definitions": definitions or []
            }), encoding="utf-8")
            output = io.StringIO()
            with contextlib.redirect_stdout(output):
                result = audit.main(["--strict"] if strict else [], repo=str(root), baseline_path=str(baseline))
            return result, output.getvalue()

    def definition(self, declarations):
        return [{"file": audit.UI_ROOT + "/Palette.kt", "scope": "object Palette {",
                 "purpose": "Reviewed palette definition", "declarations": declarations}]

    def test_repository_definitions_are_current_and_strict_audit_passes(self):
        with contextlib.redirect_stdout(io.StringIO()):
            self.assertEqual(0, audit.main(["--strict"]))
        groups = audit.load_baseline(str(ROOT / "tools/design_whitelist.json"))
        self.assertTrue(groups)
        for group in groups:
            self.assertTrue((ROOT / group["file"]).is_file())
            self.assertTrue(group["purpose"].strip())

    def test_two_hex_colors_on_one_line_are_both_reported(self):
        status, output = self.run_audit("val colors = listOf(Color(0xFF123456), Color(0xFF234567))")
        self.assertEqual(1, status)
        self.assertIn("hard-coded hex colors : 2", output)

    def test_neutral_literal_does_not_hide_a_named_color_on_the_same_line(self):
        status, output = self.run_audit("val colors = listOf(Color.Transparent, Color.Red, Color.Blue)")
        self.assertEqual(1, status)
        self.assertIn("off-palette named     : 2", output)

    def test_exact_declaration_is_allowed_only_in_its_owner(self):
        declaration = "val ink = Color(0xFF123456)"
        status, output = self.run_audit("object Palette {\n" + declaration + "\n}\n", self.definition([declaration]))
        self.assertEqual(0, status, output)
        status, output = self.run_audit("object Palette {\n}\nobject Other {\n" + declaration + "\n}\n", self.definition([declaration]))
        self.assertEqual(1, status)
        self.assertIn("hard-coded", output)
        self.assertIn("stale baseline", output)

    def test_reviewed_file_is_not_exempt_from_new_colors_or_small_touch_targets(self):
        declaration = "val ink = Color(0xFF123456)"
        text = "object Palette {\n" + declaration + "\nval newColor = Color(0xFF234567)\nModifier.size(12.dp).clickable { }\n}\n"
        status, output = self.run_audit(text, self.definition([declaration]))
        self.assertEqual(1, status)
        self.assertIn("hard-coded hex colors : 1", output)
        self.assertIn("small touch targets   : 1", output)

    def test_duplicate_or_modified_declaration_does_not_expand_its_budget(self):
        declaration = "val ink = Color(0xFF123456)"
        for text in [declaration + "\n" + declaration, declaration + "; val extra = Color(0xFF234567)"]:
            status, output = self.run_audit("object Palette {\n" + text + "\n}\n", self.definition([declaration]))
            self.assertEqual(1, status)
            self.assertIn("hard-coded", output)

    def test_icon_names_never_exempt_a_whole_line(self):
        for name in ["LucentIcons", "lineIcon"]:
            status, output = self.run_audit(f"val {name} = Color(0xFF123456)")
            self.assertEqual(1, status)
            self.assertIn("hard-coded", output)

    def test_obsolete_whole_file_whitelist_is_rejected(self):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / "baseline.json"
            path.write_text(json.dumps({"files": [audit.UI_ROOT + "/Palette.kt"]}), encoding="utf-8")
            with contextlib.redirect_stdout(io.StringIO()):
                self.assertEqual(1, audit.main(["--strict"], repo=folder, baseline_path=str(path)))

    def test_non_strict_reports_findings_but_does_not_hide_them(self):
        status, output = self.run_audit("val ink = Color.Red", strict=False)
        self.assertEqual(0, status)
        self.assertIn("off-palette named     : 1", output)


if __name__ == "__main__":
    unittest.main()
