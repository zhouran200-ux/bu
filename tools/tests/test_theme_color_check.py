import importlib.util
import io
from pathlib import Path
import tempfile
import unittest
from contextlib import redirect_stdout

TOOLS = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location("theme_color_check", TOOLS / "theme_color_check.py")
audit = importlib.util.module_from_spec(spec)
spec.loader.exec_module(audit)


class ThemeColorCheckTest(unittest.TestCase):
    def run_check(self, modes_text, day_text, night_text):
        with tempfile.TemporaryDirectory() as folder:
            base = Path(folder)
            (base / "values").mkdir()
            (base / "values-night").mkdir()
            modes = base / "ThemeModes.kt"
            day = base / "values/colors.xml"
            night = base / "values-night/colors.xml"
            modes.write_text(modes_text, encoding="utf-8")
            day.write_text(day_text, encoding="utf-8")
            night.write_text(night_text, encoding="utf-8")
            original = (audit.THEME_MODES, audit.DAY_COLORS, audit.NIGHT_COLORS)
            audit.THEME_MODES, audit.DAY_COLORS, audit.NIGHT_COLORS = modes, day, night
            try:
                with redirect_stdout(io.StringIO()) as output:
                    result = audit.main()
                return result, output.getvalue()
            finally:
                audit.THEME_MODES, audit.DAY_COLORS, audit.NIGHT_COLORS = original

    def test_repository_background_colors_agree(self):
        with redirect_stdout(io.StringIO()) as output:
            self.assertEqual(0, audit.main())
        self.assertIn("matches the Compose backdrop constants", output.getvalue())

    def test_mismatched_night_color_fails(self):
        modes = "val LIGHT_BACKDROP = Color(0xFFF4F3F8)\nval DARK_BACKDROP = Color(0xFF0E0E14)"
        day = "<resources><color name=\"lucent_window_background\">#FFF4F3F8</color></resources>"
        night = "<resources><color name=\"lucent_window_background\">#FF000000</color></resources>"
        result, output = self.run_check(modes, day, night)
        self.assertEqual(1, result)
        self.assertIn("DARK_BACKDROP", output)

    def test_missing_color_fails(self):
        modes = "val LIGHT_BACKDROP = Color(0xFFF4F3F8)\nval DARK_BACKDROP = Color(0xFF0E0E14)"
        day = "<resources></resources>"
        night = "<resources><color name=\"lucent_window_background\">#FF0E0E14</color></resources>"
        result, output = self.run_check(modes, day, night)
        self.assertEqual(1, result)
        self.assertIn("missing", output)


if __name__ == "__main__":
    unittest.main()
