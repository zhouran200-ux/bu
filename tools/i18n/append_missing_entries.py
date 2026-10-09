import re, sys, os, importlib.util

_HERE = os.path.dirname(os.path.abspath(__file__))
_REPO = os.path.abspath(os.path.join(_HERE, "..", ".."))
KT = os.path.join(_REPO, "app", "src", "main", "java", "com", "lucent", "app", "i18n", "I18n.kt")
CATALOG = os.path.join(_HERE, "catalog.py")

def q(v):
    if v is None:
        return "None"
    esc = v.replace("\\", "\\\\").replace('"', '\\"').replace("\n", "\\n")
    return '"' + esc + '"'

def decode_kt(s):
    out = []
    i = 0
    while i < len(s):
        c = s[i]
        if c == "\\" and i + 1 < len(s):
            n = s[i + 1]
            if n == "n": out.append("\n"); i += 2
            elif n == "t": out.append("\t"); i += 2
            elif n == '"': out.append('"'); i += 2
            elif n == "\\": out.append("\\"); i += 2
            elif n == "$": out.append("$"); i += 2
            else: out.append(n); i += 2
        else:
            out.append(c)
            i += 1
    return "".join(out)

def to_catalog_template(s):
    return re.sub(r'\$\{(\w+)\}', r'{\1}', s)

def main():
    kt = open(KT, encoding="utf-8").read()
    lines = kt.split("\n")

    tr_start = next(i for i, l in enumerate(lines) if l.strip() == "open class Tr {")
    tr_end = next(i for i in range(tr_start + 1, len(lines)) if lines[i].strip().startswith("object Zh"))
    tr_lines = lines[tr_start + 1:tr_end]

    tr_sigs = {}
    tr_if = {}
    for l in tr_lines:
        m = re.match(r'\s*open fun\s+(\w+)(\([^)]*\))?\s*:\s*String\s*=\s*(if\s.*)$', l)
        if m:
            sig = m.group(1) + (m.group(2) or "")
            tr_if[sig] = m.group(3)
            continue
        m = re.match(r'\s*open (?:val|fun)\s+(\w+)(\([^)]*\))?\s*:\s*String\s*=\s*"((?:[^"\\]|\\.)*)"\s*$', l)
        if m:
            tr_sigs[m.group(1) + (m.group(2) or "")] = decode_kt(m.group(3))

    spec = importlib.util.spec_from_file_location("cat", CATALOG)
    cat = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(cat)

    existing = set()
    for e in cat.ENTRIES:
        if not isinstance(e, str):
            existing.add(e[0])

    missing = []
    conditional = {}
    all_sigs = set(tr_sigs) | set(tr_if)
    for sig in sorted(all_sigs):
        if sig in existing:
            continue
        if sig in tr_if:
            conditional[sig] = tr_if.get(sig)
            missing.append((sig, None))
            continue
        z = to_catalog_template(tr_sigs.get(sig, ""))
        missing.append((sig, z))

    print(f"existing: {len(existing)}, missing found: {len(missing)}, conditional: {len(conditional)}")
    if not missing and not conditional:
        print("nothing to append")
        return

    if "--write" not in sys.argv:
        print("dry run — pass --write to append")
        print("first 5 missing:", [m[0] for m in missing[:5]])
        print("last 5 missing:", [m[0] for m in missing[-5:]])
        return

    cat_text = open(CATALOG, encoding="utf-8").read()
    marker = "\n]"
    last_close = cat_text.rfind(marker)
    if last_close < 0:
        sys.exit("cannot find ENTRIES closing bracket")

    new_entries = []
    for m in missing:
        new_entries.append(f"    ({q(m[0])}, {q(m[1])}),")
    cat_text = cat_text[:last_close] + "\n" + "\n".join(new_entries) + "\n" + cat_text[last_close:]

    with open(CATALOG, "w", encoding="utf-8") as f:
        f.write(cat_text)
    print(f"appended {len(missing)} entries")

if __name__ == "__main__":
    main()
