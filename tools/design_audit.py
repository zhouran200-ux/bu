import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.abspath(os.path.join(HERE, ".."))

UI_ROOT = "app/src/main/java/com/lucent/app/ui"

PALETTE_FILES = {
    "app/src/main/java/com/lucent/app/ui/Glass.kt",
    "app/src/main/java/com/lucent/app/ui/PaletteCatalog.kt",
    "app/src/main/java/com/lucent/app/ui/AppThemeColors.kt",
    "app/src/main/java/com/lucent/app/ui/ThemeModes.kt",
    "app/src/main/java/com/lucent/app/ui/BackgroundPaletteMotion.kt",
    "app/src/main/java/com/lucent/app/ui/FluidGlassBackground.kt",
    "app/src/main/java/com/lucent/app/ui/PlatformDiffuseBackground.kt",
}

ALLOWED_LITERAL_COLORS = {
    "Color.Transparent",
    "Color.Unspecified",
    "Color.White",
    "Color.Black",
}

HEX_COLOR = re.compile(r"Color\(0x[0-9A-Fa-f]{6,8}\)")
NAMED_COLOR = re.compile(r"\bColor\.(Red|Green|Blue|Yellow|Cyan|Magenta|Gray|LightGray|DarkGray)\b")
TOUCH_SIZE = re.compile(r"\.size\((\d+)\.dp\)")
CLICKABLE = re.compile(r"\.clickable\s*[({]")


def ui_files():
    base = os.path.join(REPO, UI_ROOT)
    for folder, _, names in os.walk(base):
        for name in names:
            if name.endswith(".kt"):
                path = os.path.join(folder, name)
                yield os.path.relpath(path, REPO).replace(os.sep, "/"), path


def line_is_allowed_literal(line):
    for allowed in ALLOWED_LITERAL_COLORS:
        if allowed in line:
            return True
    return False


def main():
    hard_colors = []
    named_colors = []
    small_touch = []
    files = 0

    for rel, path in sorted(ui_files()):
        files += 1
        if rel in PALETTE_FILES:
            continue
        with open(path, encoding="utf-8") as handle:
            lines = handle.readlines()
        for n, line in enumerate(lines, start=1):
            if "LucentIcons" in line or "lineIcon" in line:
                continue
            hex_hits = HEX_COLOR.findall(line)
            if len(hex_hits) == 1:
                match = HEX_COLOR.search(line)
                hard_colors.append(f"{rel}:{n}: hard-coded {match.group(0)} (prefer MaterialTheme.colorScheme or Local ink)")
            for match in NAMED_COLOR.finditer(line):
                if line_is_allowed_literal(line):
                    continue
                named_colors.append(f"{rel}:{n}: named literal Color.{match.group(1)} does not follow the palette")
            if CLICKABLE.search(line):
                window = "".join(lines[max(0, n - 4):n])
                sizes = [int(s) for s in TOUCH_SIZE.findall(window)]
                if sizes and max(sizes) < 44:
                    small_touch.append(f"{rel}:{n}: clickable target near {max(sizes)}dp is below the 44dp touch minimum")

    findings = hard_colors + named_colors + small_touch
    penalty = len(hard_colors) * 6 + len(named_colors) * 6 + len(small_touch) * 3
    score = max(0, 100 - penalty)

    print(f"Design audit over {files} UI files")
    print(f"  hard-coded hex colors : {len(hard_colors)}")
    print(f"  off-palette named     : {len(named_colors)}")
    print(f"  small touch targets   : {len(small_touch)}")
    print(f"Design conformance score: {score}/100")
    for item in findings:
        print(f"::warning::{item}")

    if "--strict" in sys.argv and findings:
        print(f"::error::{len(findings)} design-conformance issue(s) under --strict")
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
