"""Small, conservative source audit; not a Kotlin parser or semantic dead-code analyzer.

Hard failures: identical imports and retired identifiers in source/config files.
Unused imports/private declarations are review candidates only: they are not semantic
dead-code proof. With --fail-on-candidates, every candidate must be manually removed
or explained; this blocks hygiene debt without claiming reachability analysis. There
is no default allowlist: an intentional future exception needs one explicit symbol,
line, rule, and reason rather than a file-wide suppression. No cross-file resolution,
reflection/lifecycle/overloads/reachability guarantees.
Strings count as references deliberately (including templates), preferring misses
rather than suggesting deletion of a live import. Implicit operator/delegate and
wildcard/backtick imports are never classified as unused. Scanned file types are
Kotlin, Java, XML, ProGuard, TOML, properties and Gradle; lexical Kotlin candidates
are not claimed for other languages, documentation or dynamically loaded symbols.
"""
import argparse
from collections import Counter
import json
import os
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
SKIP_DIRS = {".git", ".gradle", ".kotlin", ".idea", "build", "out", "dist", "generated", "node_modules", "__pycache__", ".venv", "venv"}
GENERATED = {"app/src/main/java/com/lucent/app/i18n/I18n.kt"}
SOURCE_SUFFIXES = {".kt", ".kts", ".java", ".xml", ".pro", ".toml", ".properties", ".gradle"}
IDENT = re.compile(r"[^\W\d]\w*", re.UNICODE)
IMPORT = re.compile(r"(?m)^[ \t]*import[ \t]+((?:[\w`]+\.)*[\w`*]+)(?:[ \t]+as[ \t]+([\w`]+))?[ \t]*;?[ \t]*$")
# Kotlin convention calls may have no spelled-out reference in source.
IMPLICIT = {"getValue", "setValue", "provideDelegate", "get", "set", "invoke", "contains", "iterator", "hasNext", "next", "compareTo", "equals", "inc", "dec", "unaryPlus", "unaryMinus", "not", "plus", "minus", "times", "div", "rem", "rangeTo", "rangeUntil", "plusAssign", "minusAssign", "timesAssign", "divAssign", "remAssign"}


def source_files(root):
    for folder, dirs, names in os.walk(root):
        dirs[:] = sorted(d for d in dirs if d not in SKIP_DIRS and not Path(folder, d).is_symlink())
        for name in sorted(names):
            path = Path(folder, name)
            if path.is_symlink() or path.suffix not in SOURCE_SUFFIXES:
                continue
            if path.relative_to(root).as_posix() not in GENERATED:
                yield path


def lex(text):
    """Return comment-masked text and (start, end, literal-parts) strings.

    Keep offsets/newlines. Handles nested block comments and string templates,
    including quoted expressions inside templates. Literal-parts omit template
    expressions, which are scanned recursively for their own string literals.
    This lexer is deliberately limited to Kotlin, not reused for Java syntax.
    """
    masked = list(text)
    strings = []
    n = len(text)

    def blank(start, end):
        for pos in range(start, end):
            if text[pos] != "\n":
                masked[pos] = " "

    def string(start):
        raw = text.startswith('"""', start)
        quote = '"""' if raw else '"'
        i = start + len(quote)
        parts = []
        while i < n:
            if text.startswith(quote, i):
                end = i + len(quote)
                strings.append((start, end, "".join(parts)))
                return end
            if not raw and text[i] == "\\":
                escape = text[i:i + 6]
                if re.fullmatch(r"\\u[0-9a-fA-F]{4}", escape):
                    parts.append(chr(int(escape[2:], 16)))
                    i += 6
                else:
                    parts.append(text[i:i + 2])
                    i += 2
            elif text.startswith("$" + "{", i):
                i = code(i + 2, template=True)
            elif text[i] == "$" and i + 1 < n and (text[i + 1].isalpha() or text[i + 1] == "_"):
                i += 2
                while i < n and (text[i].isalnum() or text[i] == "_"):
                    i += 1
            else:
                parts.append(text[i])
                i += 1
        strings.append((start, n, "".join(parts)))
        return n

    def code(i, template=False):
        depth = 0
        while i < n:
            if text.startswith("//", i):
                end = text.find("\n", i)
                end = n if end < 0 else end
                blank(i, end)
                i = end
            elif text.startswith("/*", i):
                start, level = i, 1
                i += 2
                while i < n and level:
                    if text.startswith("/*", i):
                        level += 1
                        i += 2
                    elif text.startswith("*/", i):
                        level -= 1
                        i += 2
                    else:
                        i += 1
                blank(start, i)
            elif text[i] == '"':
                i = string(i)
            elif text[i] == "'":
                i += 1
                while i < n:
                    if text[i] == "\\":
                        i += 2
                    elif text[i] == "'":
                        i += 1
                        break
                    else:
                        i += 1
            elif text[i] == "{":
                depth += 1
                i += 1
            elif text[i] == "}":
                if template and depth == 0:
                    return i + 1
                depth -= 1
                i += 1
            else:
                i += 1
        return i

    code(0)
    return "".join(masked), sorted(strings)


def kotlin_findings(text):
    masked, strings = lex(text)
    import_code = list(masked)
    for start, end, _ in strings:
        import_code[start:end] = " " * (end - start)
    imports = list(IMPORT.finditer("".join(import_code)))
    body = list(masked)
    for match in imports:
        body[match.start():match.end()] = " " * (match.end() - match.start())
    body = "".join(body)
    names = IDENT.findall(body)
    counts = Counter(names)
    hard, candidates = [], []
    seen = set()
    for match in imports:
        path, alias = match.groups()
        line = text.count("\n", 0, match.start()) + 1
        key = (path, alias)
        if key in seen:
            hard.append((line, "duplicate-import", match.group().strip()))
        seen.add(key)
        name = (alias or path.rsplit(".", 1)[-1]).strip("`")
        original = path.rsplit(".", 1)[-1].strip("`")
        if "`" in path or (alias and "`" in alias):
            continue
        if name == "*" or original in IMPLICIT or re.fullmatch(r"component[0-9]+", original):
            continue
        if name not in counts:
            candidates.append((line, "unused-import-candidate", match.group().strip()))
    # Plain private declarations only. Annotation/reflection and delegated use
    # still require human review; do not use these candidates as deletion proof.
    declaration_body = list(masked)
    for start, end, _ in strings:
        declaration_body[start:end] = " " * (end - start)
    declarations = re.finditer(r"\bprivate\s+(?:(?:const|suspend|tailrec|inline)\s+)*(?:fun|val|var|class|object)\s+([A-Za-z_]\w*)", "".join(declaration_body))
    for match in declarations:
        name = match.group(1)
        suffix = "".join(declaration_body)[match.end():]
        if suffix.lstrip().startswith((".", "<", "?")):
            continue
        if counts.get(name, 0) == 1:
            candidates.append((text.count("\n", 0, match.start()) + 1, "private-declaration-candidate", name))
    return hard, candidates


def audit(root):
    manifest = root / "tools/legacy_symbols.json"
    retired = json.loads(manifest.read_text(encoding="utf-8"))["retired"] if manifest.exists() else []
    hard, candidates, checked = [], [], 0
    for path in source_files(root):
        checked += 1
        rel = path.relative_to(root).as_posix()
        text = path.read_text(encoding="utf-8-sig")
        for symbol in retired:
            for match in re.finditer(r"(?<![\w])" + re.escape(symbol) + r"(?![\w])", text):
                hard.append((rel, text.count("\n", 0, match.start()) + 1, "retired-symbol", symbol))
        if path.suffix in {".kt", ".kts"}:
            failures, review = kotlin_findings(text)
            hard.extend((rel, *item) for item in failures)
            candidates.extend((rel, *item) for item in review)
    return checked, hard, candidates


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=ROOT)
    parser.add_argument("--json", action="store_true")
    parser.add_argument("--fail-on-candidates", action="store_true", help="fail when any review candidate remains; candidates are not semantic dead-code proof")
    args = parser.parse_args(argv)
    checked, hard, candidates = audit(args.root)
    if args.json:
        print(json.dumps({"checked": checked, "errors": hard, "review_candidates": candidates}, ensure_ascii=False, indent=2))
    else:
        for rel, line, rule, detail in hard + candidates:
            print(f"{rel}:{line}: {rule}: {detail}")
        print(f"Source/config files checked: {checked}; errors: {len(hard)}; review candidates: {len(candidates)}")
        if args.fail_on_candidates and candidates:
            print("Candidate gate failed: each candidate requires removal or a human-reviewed explanation.")
        print("Candidates are NOT semantic dead-code proof; review and compiler/lint validation are required. No files changed.")
    return int(bool(hard or (args.fail_on_candidates and candidates)))


if __name__ == "__main__":
    raise SystemExit(main())
