import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parents[1]
THEME_MODES = ROOT / "app/src/main/java/com/lucent/app/ui/ThemeModes.kt"
DAY_COLORS = ROOT / "app/src/main/res/values/colors.xml"
NIGHT_COLORS = ROOT / "app/src/main/res/values-night/colors.xml"
NAME = "lucent_window_background"


def kotlin_color(text, constant):
    match = re.search("val " + constant + " = Color\\(0x([0-9A-Fa-f]{8})\\)", text)
    return match.group(1).upper() if match else None


def xml_color(text):
    match = re.search("<color name=\"" + NAME + "\">#([0-9A-Fa-f]{6,8})</color>", text)
    return match.group(1).upper()[-6:] if match else None


def main():
    modes = THEME_MODES.read_text(encoding="utf-8")
    expected = {
        "LIGHT_BACKDROP": kotlin_color(modes, "LIGHT_BACKDROP"),
        "DARK_BACKDROP": kotlin_color(modes, "DARK_BACKDROP"),
    }
    for label, value in expected.items():
        if not value:
            print("::error::Could not read " + label + " from ThemeModes.kt")
            return 1
    day = xml_color(DAY_COLORS.read_text(encoding="utf-8"))
    night = xml_color(NIGHT_COLORS.read_text(encoding="utf-8"))
    failures = 0
    if day is None:
        print("::error::values/colors.xml is missing " + NAME)
        failures += 1
    elif day != expected["LIGHT_BACKDROP"][-6:]:
        print("::error::day window background disagrees with ThemeModes.LIGHT_BACKDROP")
        failures += 1
    if night is None:
        print("::error::values-night/colors.xml is missing " + NAME)
        failures += 1
    elif night != expected["DARK_BACKDROP"][-6:]:
        print("::error::night window background disagrees with ThemeModes.DARK_BACKDROP")
        failures += 1
    print("window background: day=#" + str(day) + " night=#" + str(night))
    if failures:
        return 1
    print("XML window background matches the Compose backdrop constants.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
