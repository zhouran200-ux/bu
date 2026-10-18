import contextlib
import io
import json
from pathlib import Path
import sys
import tempfile
import unittest

TOOLS = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(TOOLS))
import source_audit as audit
import i18n_source_check as wording
import verify


class SourceAuditTest(unittest.TestCase):
    def test_unused_alias_and_comment_only_reference_are_candidates(self):
        hard, candidates = audit.kotlin_findings("import a.Unused\nimport a.Thing as Alias\n// Unused Alias\nval s = 1")
        self.assertEqual([], hard)
        self.assertEqual(["import a.Unused", "import a.Thing as Alias"], [x[2] for x in candidates])

    def test_extensions_annotations_aliases_templates_and_implicit_operators_are_kept(self):
        text = '''import a.extension
import a.Annotation
import a.Value as Alias
import a.getValue
import a.setValue
import a.component1
import a.plus
import a.invoke
import a.iterator
import a.*
@Annotation
val thing by provider
val out = Alias().extension()
val template = "$out"'''
        self.assertEqual(([], []), audit.kotlin_findings(text))

    def test_live_import_in_nested_string_template_is_not_deleted(self):
        text = 'import a.Live\nval result = "' + '$' + '{Live("x")}"'
        self.assertEqual(([], []), audit.kotlin_findings(text))

    def test_backtick_imports_remain_review_safe(self):
        text = "import a.`not obvious`\nimport a.Live as `some alias`\nval x = 1"
        self.assertEqual(([], []), audit.kotlin_findings(text))

    def test_markdown_fence_does_not_swallow_references(self):
        text = 'private const val FENCE = "' + '`' * 3 + '"\nfun test() = FENCE'
        self.assertEqual(([], []), audit.kotlin_findings(text))

    def test_fake_import_in_raw_string_is_not_an_import(self):
        text = 'val example = """\nimport a.Unused\nimport a.Unused\n"""'
        self.assertEqual(([], []), audit.kotlin_findings(text))

    def test_duplicate_import_is_hard_failure(self):
        hard, _ = audit.kotlin_findings("import a.Live\nimport a.Live\nval x = Live()")
        self.assertEqual([(2, "duplicate-import", "import a.Live")], hard)

    def test_private_unused_is_candidate_not_automatic_failure(self):
        hard, candidates = audit.kotlin_findings("private val stale = 42\nprivate fun live() = 1\nfun call() = live()")
        self.assertEqual([], hard)
        self.assertEqual([(1, "private-declaration-candidate", "stale")], candidates)

    def test_project_walk_covers_new_modules_and_excludes_generated_build(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            for rel in ["newmodule/src/Example.kt", "build/Bad.kt", "newmodule/build/Bad.kt", "generated/Bad.kt", "app/src/main/java/com/lucent/app/i18n/I18n.kt", "proguard-rules.pro", "settings.gradle.kts", "test/java/Example.java"]:
                path = root / rel
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text("", encoding="utf-8")
            self.assertEqual({"newmodule/src/Example.kt", "proguard-rules.pro", "settings.gradle.kts", "test/java/Example.java"}, {p.relative_to(root).as_posix() for p in audit.source_files(root)})

    def test_candidate_gate_fails_without_automatic_allowlist(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            (root / "Example.kt").write_text("import a.Unused\nval x = 1", encoding="utf-8")
            with contextlib.redirect_stdout(io.StringIO()):
                self.assertEqual(1, audit.main(["--root", str(root), "--fail-on-candidates"]))

    def test_candidate_gate_passes_after_manual_removal(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            (root / "Example.kt").write_text("val x = 1", encoding="utf-8")
            with contextlib.redirect_stdout(io.StringIO()):
                self.assertEqual(0, audit.main(["--root", str(root), "--fail-on-candidates"]))

    def test_retired_symbols_match_identifier_boundaries_and_fail_cli(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            (root / "tools").mkdir()
            (root / "tools/legacy_symbols.json").write_text(json.dumps({"retired": ["Retired"]}), encoding="utf-8")
            (root / "Example.java").write_text("class RetiredSuffix {}", encoding="utf-8")
            self.assertEqual([], audit.audit(root)[1])
            (root / "Example.java").write_text("class Retired {}", encoding="utf-8")
            with contextlib.redirect_stdout(io.StringIO()):
                self.assertEqual(1, audit.main(["--root", str(root)]))


class WordingTest(unittest.TestCase):
    REL = "app/src/main/java/com/lucent/app/ui/Example.kt"

    def findings(self, text):
        return wording.wording_findings(text, self.REL)

    def test_actual_cjk_and_english_ui_literals_are_rejected(self):
        for text in ['Text("Hello")', 'Text(text = "Hello")', 'Icon(contentDescription = "Locked")', 'val message = "读取失败"', 'Text("""保存""")', 'Text("' + '$' + '{count} items")']:
            with self.subTest(text=text):
                self.assertTrue(self.findings(text))

    def test_comments_debug_keys_templates_and_punctuation_are_allowed(self):
        text = '''// 中文注释 Text("Fake")
/* nested /* 中文 */ comment */
val key = "user_agent"
animateFloatAsState(label = "debugLabel")
Text("123…")
Text("$title $valueLabel")
val url = "https://example.invalid/path"
val ch = '中'
'''
        self.assertEqual([], self.findings(text))

    def test_unicode_escape_and_nested_template_literals_are_checked(self):
        escaped = 'Text("' + chr(92) + 'u4e2d")'
        nested = 'Text("' + '$' + '{if (ok) "保存" else S.cancel}")'
        self.assertTrue(self.findings(escaped))
        self.assertTrue(self.findings(nested))

    def test_comment_does_not_break_direct_sink_and_line_is_accurate(self):
        self.assertEqual([(3, "literal-user-wording", "Locked")], self.findings('Icon(\ncontentDescription = /* comment */\n"Locked")'))

    def test_specimen_exception_is_path_and_value_specific(self):
        rel = "app/src/main/java/com/lucent/app/ui/settings/FontSettingsPage.kt"
        self.assertEqual([], wording.wording_findings('Text("Aa")', rel))
        self.assertTrue(self.findings('Text("Aa")'))
        self.assertTrue(wording.wording_findings('Text("English wording")', rel))

    def test_cli_scope_only_production_ui_chat_and_failure_exit(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            for rel in [self.REL, "app/src/test/java/com/lucent/app/ui/Test.kt", "app/src/main/java/com/lucent/app/data/Data.kt"]:
                path = root / rel
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text('Text("Hello")', encoding="utf-8")
            checked, findings = wording.audit(root)
            self.assertEqual(1, checked)
            self.assertEqual(1, len(findings))
            with contextlib.redirect_stdout(io.StringIO()):
                self.assertEqual(1, wording.main(["--root", str(root)]))

    def test_every_verify_level_includes_gates(self):
        for level in verify.LEVELS:
            commands = [command for _, command in verify.plan(level)]
            self.assertIn(verify.SOURCE_AUDIT, commands)
            self.assertIn(verify.I18N_SOURCE, commands)


if __name__ == "__main__":
    unittest.main()
