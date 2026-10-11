import os, re, sys, importlib.util

_HERE = os.path.dirname(os.path.abspath(__file__))
_REPO = os.path.abspath(os.path.join(_HERE, "..", ".."))

spec = importlib.util.spec_from_file_location("catalog", os.path.join(_HERE, "catalog.py"))
cat = importlib.util.module_from_spec(spec)
spec.loader.exec_module(cat)

ENTRIES = cat.ENTRIES

def esc(s: str) -> str:
    s = s.replace("\\", "\\\\").replace('"', '\\"').replace("$", "\\$")
    s = s.replace("\n", "\\n")
    return re.sub(r"\\?\{(\w+)\}", r"${\1}", s)

def is_fn(key: str) -> bool:
    return "(" in key

def fn_name(key: str) -> str:
    return key.split("(")[0]

seen = set()
for e in ENTRIES:
    if isinstance(e, str):
        continue
    k = fn_name(e[0]) if is_fn(e[0]) else e[0]
    if k in seen:
        sys.exit(f"DUPLICATE KEY: {k}")
    seen.add(k)

def base_decl(key, text):
    if is_fn(key):
        return f'    open fun {key}: String = "{esc(text)}"'
    return f'    open val {key}: String = "{esc(text)}"'

header = '''package com.lucent.app.i18n
'''

def emit(entries, path):
    out = [header]
    out.append("open class Tr {")
    for e in entries:
        if isinstance(e, str):
            out.append("    " + e if e else "")
        else:
            out.append(base_decl(e[0], e[1] if len(e) > 1 and e[1] is not None else ""))
    out.append("}")
    out.append("")
    out.append("object Zh : Tr()")
    out.append("")
    out.append("val S: Tr = Zh")
    out.append("")
    with open(path, "wb") as f:
        f.write("\n".join(out).encode("utf-8"))
    n = sum(1 for e in entries if not isinstance(e, str))
    print(f"Wrote {path}: {n} entries")

emit(list(ENTRIES), os.path.join(_REPO, "app", "src", "main", "java", "com", "lucent", "app", "i18n", "I18n.kt"))
