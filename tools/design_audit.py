"""Heuristic Kotlin UI audit; reviewed definitions are not whole-file exemptions."""

import json
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.abspath(os.path.join(HERE, ".."))
UI_ROOT = "app/src/main/java/com/lucent/app/ui"
BASELINE = os.path.join(HERE, "design_whitelist.json")

# These neutral/sentinel primitives are allowed per occurrence, never per line.
ALLOWED_LITERAL_COLORS = {"Transparent", "Unspecified", "White", "Black"}
HEX_COLOR = re.compile(r"\bColor\s*\(\s*0[xX][0-9A-Fa-f_]{6,}(?:[uU]?[lL]?)?\s*\)")
NAMED_COLOR = re.compile(r"\bColor\s*\.\s*(Transparent|Unspecified|White|Black|Red|Green|Blue|Yellow|Cyan|Magenta|Gray|LightGray|DarkGray)\b")
TOUCH_SIZE = re.compile(r"\.size\((\d+)\.dp\)")
CLICKABLE = re.compile(r"\.clickable\s*[({]")


def ui_files(repo):
    for folder, _, names in os.walk(os.path.join(repo, UI_ROOT)):
        for name in names:
            if name.endswith(".kt"):
                path = os.path.join(folder, name)
                yield os.path.relpath(path, repo).replace(os.sep, "/"), path


def normalize(line):
    return " ".join(line.split())


def load_baseline(path):
    """Fail closed on obsolete file lists, broad paths or malformed definitions."""
    with open(path, encoding="utf-8") as handle:
        config = json.load(handle)
    if set(config) != {"schema_version", "policy", "definitions"} or config["schema_version"] != 2:
        raise ValueError("expected schema_version 2 and explicit definitions (no file exemptions)")
    if not isinstance(config["policy"], str) or not config["policy"].strip():
        raise ValueError("baseline policy is required")
    if not isinstance(config["definitions"], list):
        raise ValueError("definitions must be a list")
    seen = set()
    for group in config["definitions"]:
        if set(group) != {"file", "scope", "purpose", "declarations"}:
            raise ValueError("each definition needs file, scope, purpose and declarations")
        relative = group["file"]
        if (not isinstance(relative, str) or not relative.startswith(UI_ROOT + "/")
                or not relative.endswith(".kt") or ".." in relative.split("/") or "\\" in relative):
            raise ValueError("definition file must be a literal UI Kotlin path")
        if any(not isinstance(group[key], str) or not group[key].strip() for key in ("scope", "purpose")):
            raise ValueError("definition scope and purpose must be explicit")
        declarations = group["declarations"]
        if not isinstance(declarations, list) or not declarations:
            raise ValueError("definition declarations must be a nonempty list")
        for declaration in declarations:
            if (not isinstance(declaration, str) or "\n" in declaration
                    or normalize(declaration) != declaration or not HEX_COLOR.search(declaration)):
                raise ValueError("each declaration must be a normalized single-line hex color definition")
            key = (relative, group["scope"], declaration)
            if key in seen:
                raise ValueError("duplicate reviewed definition")
            seen.add(key)
    return config["definitions"]


def reviewed_lines(relative, lines, groups):
    """Allow exact declarations once, only inside their reviewed owner block.

    Scope anchors are unique source lines; braces delimit the owner. This is a
    textual baseline, not a Kotlin parser. Ambiguous/stale entries fail closed.
    """
    approved = set()
    issues = []
    normalized = [normalize(line) for line in lines]
    for group in groups:
        if group["file"] != relative:
            continue
        scope = normalize(group["scope"])
        starts = [i for i, line in enumerate(normalized) if line == scope]
        if len(starts) != 1:
            issues.append(f"{relative}: baseline scope missing or ambiguous: {scope}")
            continue
        start = starts[0]
        end = start
        # A single declaration may itself be the scope (e.g. a CompositionLocal).
        if scope not in group["declarations"]:
            depth = 0
            opened = False
            for end in range(start, len(lines)):
                depth += lines[end].count("{") - lines[end].count("}")
                opened = opened or "{" in lines[end]
                if opened and depth == 0:
                    break
            else:
                issues.append(f"{relative}: baseline scope is not a balanced block: {scope}")
                continue
        for declaration in group["declarations"]:
            hits = [i for i in range(start, end + 1) if normalized[i] == declaration]
            if len(hits) != 1:
                issues.append(f"{relative}: reviewed definition missing or duplicated in {scope}: {declaration}")
                continue
            approved.add(hits[0])
    return approved, issues


def main(argv=None, *, repo=None, baseline_path=None):
    argv = sys.argv[1:] if argv is None else argv
    repo = REPO if repo is None else repo
    try:
        groups = load_baseline(BASELINE if baseline_path is None else baseline_path)
    except (OSError, ValueError, TypeError, KeyError) as exc:
        print(f"::error::Invalid design definition baseline: {exc}")
        return 1
    hard_colors = []
    named_colors = []
    small_touch = []
    baseline_issues = []
    scanned = set()
    reviewed_count = 0
    for rel, path in sorted(ui_files(repo)):
        scanned.add(rel)
        with open(path, encoding="utf-8") as handle:
            lines = handle.readlines()
        approved, issues = reviewed_lines(rel, lines, groups)
        baseline_issues.extend(issues)
        for i, line in enumerate(lines):
            n = i + 1
            for match in HEX_COLOR.finditer(line):
                if i in approved:
                    reviewed_count += 1
                else:
                    hard_colors.append(f"{rel}:{n}: hard-coded {match.group(0)} (prefer MaterialTheme.colorScheme or Local ink)")
            for match in NAMED_COLOR.finditer(line):
                if match.group(1) not in ALLOWED_LITERAL_COLORS:
                    named_colors.append(f"{rel}:{n}: named literal Color.{match.group(1)} does not follow the palette")
            if CLICKABLE.search(line):
                window = "".join(lines[max(0, n - 4):n])
                sizes = [int(s) for s in TOUCH_SIZE.findall(window)]
                if sizes and max(sizes) < 44:
                    small_touch.append(f"{rel}:{n}: clickable target near {max(sizes)}dp is below the 44dp touch minimum")
    for missing in sorted({group["file"] for group in groups} - scanned):
        baseline_issues.append(f"{missing}: reviewed definition file is missing")
    findings = hard_colors + named_colors + small_touch + baseline_issues
    penalty = len(hard_colors) * 6 + len(named_colors) * 6 + len(small_touch) * 3 + len(baseline_issues) * 6
    score = max(0, 100 - penalty)
    print(f"Design audit over {len(scanned)} UI files")
    print(f"  reviewed hex definitions: {reviewed_count}")
    print(f"  hard-coded hex colors : {len(hard_colors)}")
    print(f"  off-palette named     : {len(named_colors)}")
    print(f"  small touch targets   : {len(small_touch)}")
    print(f"  stale baseline entries: {len(baseline_issues)}")
    print(f"Design conformance score: {score}/100 (heuristic checks only)")
    for item in findings:
        print(f"::warning::{item}")
    if "--strict" in argv and findings:
        print(f"::error::{len(findings)} design-conformance issue(s) under --strict")
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
