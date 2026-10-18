"""UI/chat wording gate, not dataflow or general language detection.

Block CJK string literals and letters at direct Text/BasicText first arguments or
named text/title/subtitle/placeholder/contentDescription arguments/assignments.
Debug labels, protocol strings, punctuation, numbers and template values remain
allowed. English strings through variables/custom APIs and resources are outside
this gate; Android lint and generated i18n sync remain complementary checks.
"""
import argparse
from pathlib import Path
import re
from source_audit import ROOT, lex, source_files

SCOPE = ("app/src/main/java/com/lucent/app/ui/", "app/src/main/java/com/lucent/app/chat/")
CJK = re.compile(r"[\u3400-\u9fff\uf900-\ufaff\U00020000-\U0002ffff]")
DIRECT = re.compile(r"(?:\b(?:Text|BasicText)\s*\(\s*|\b(?:text|title|subtitle|placeholder|contentDescription)\s*=\s*)$")
# Font specimens show glyph shapes rather than translatable words.
SPECIMENS = {("app/src/main/java/com/lucent/app/ui/settings/FontLibrarySection.kt", "Aa")}


def wording_findings(text, rel):
    masked, strings = lex(text)
    findings = []
    for start, _, literal in strings:
        if (rel, literal) in SPECIMENS:
            continue
        if CJK.search(literal) or (DIRECT.search(masked[:start]) and any(ch.isalpha() for ch in literal)):
            findings.append((text.count("\n", 0, start) + 1, "literal-user-wording", literal))
    return findings


def audit(root):
    checked, findings = 0, []
    for path in source_files(root):
        rel = path.relative_to(root).as_posix()
        if path.suffix == ".kt" and rel.startswith(SCOPE):
            checked += 1
            findings.extend((rel, *item) for item in wording_findings(path.read_text(encoding="utf-8-sig"), rel))
    return checked, findings


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=ROOT)
    args = parser.parse_args(argv)
    checked, findings = audit(args.root)
    for rel, line, rule, literal in findings:
        print(f"{rel}:{line}: {rule}: {literal!r}; move wording to the i18n catalog")
    print(f"UI/chat Kotlin files checked: {checked}; literal wording violations: {len(findings)}")
    return int(bool(findings))


if __name__ == "__main__":
    raise SystemExit(main())
