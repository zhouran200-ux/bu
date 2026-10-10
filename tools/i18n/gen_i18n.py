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

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class AppLanguage(val key: String, val label: String) {
    SYSTEM("system", "系统默认"),
    ZH("zh", "中文");

    companion object {
        fun fromKey(key: String?): AppLanguage = ZH

        fun systemDefault(): AppLanguage = ZH
    }
}

object L {
    var current: Tr by mutableStateOf(Zh)
        private set

    var language: AppLanguage = AppLanguage.ZH
        private set

    fun apply(key: String?) {
        language = AppLanguage.ZH
        current = Zh
    }
}

val S: Tr get() = L.current

fun lucentLocale(): java.util.Locale = java.util.Locale.SIMPLIFIED_CHINESE

object LDates {
    private var cachedFor: Tr? = null
    private val cache = HashMap<String, java.time.format.DateTimeFormatter>()

    fun of(pattern: String): java.time.format.DateTimeFormatter {
        val now = L.current
        if (cachedFor !== now) {
            cache.clear()
            cachedFor = now
        }
        return cache.getOrPut(pattern) { java.time.format.DateTimeFormatter.ofPattern(pattern, lucentLocale()) }
    }
}
'''

CONDITIONAL = getattr(cat, "CONDITIONAL_ENTRIES", {})

def base_decl_for(e):
    key = e[0]
    if key in CONDITIONAL:
        cond_val = CONDITIONAL[key]
        expr = cond_val if isinstance(cond_val, str) else (cond_val[1] if len(cond_val) > 1 and cond_val[1] else cond_val[0])
        return f"    open fun {key}: String = {expr}"
    zh_text = e[1] if len(e) > 1 and e[1] is not None else ""
    return base_decl(key, zh_text)

def emit(entries, path):
    out = [header]
    out.append("open class Tr {")
    for e in entries:
        if isinstance(e, str):
            out.append("    " + e if e else "")
        else:
            out.append(base_decl_for(e))
    out.append("}")
    out.append("")
    out.append("object Zh : Tr()")
    out.append("")
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        f.write("\n".join(out))
    n = sum(1 for e in entries if not isinstance(e, str))
    print(f"Wrote {path}: {n} entries")

all_entries = list(ENTRIES)
emit(all_entries, os.path.join(_REPO, "app", "src", "main", "java", "com", "lucent", "app", "i18n", "I18n.kt"))
