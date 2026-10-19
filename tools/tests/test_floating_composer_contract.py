from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
UI = ROOT / "app/src/main/java/com/lucent/app/ui"


class FloatingComposerContractTest(unittest.TestCase):
    def test_composer_is_measured_before_full_viewport_messages(self):
        layout = (UI / "FloatingChatLayout.kt").read_text(encoding="utf-8")
        self.assertLess(layout.index("subcompose(ChatLayer.Composer)"),
                        layout.index("subcompose(ChatLayer.Messages)"))
        self.assertIn("val bottomInset = composerPlaceable.height.toDp()", layout)
        self.assertIn("messages(bottomInset)", layout)
        self.assertIn("controls(bottomInset)", layout)
        self.assertIn("constraints.maxHeight - composerPlaceable.height", layout)
        self.assertNotIn("onSizeChanged", layout)
        chat = (UI / "NotebookChatTab.kt").read_text(encoding="utf-8")
        self.assertIn("FloatingChatLayout(", chat)
        self.assertIn("contentPadding = PaddingValues(bottom = bottomInset)", chat)
        self.assertNotIn("Box(Modifier.weight(1f)", chat)

    def test_only_messages_are_captured_and_pure_white_remains_translucent(self):
        layout = (UI / "FloatingChatLayout.kt").read_text(encoding="utf-8")
        source = layout.split("subcompose(ChatLayer.Messages)", 1)[1].split(
            "subcompose(ChatLayer.Controls)", 1)[0]
        self.assertIn(".hazeSource(state = chatBackdrop)", source)
        self.assertIn(".chatBottomFade(bottomInset)", source)
        self.assertNotIn("composer()", source)
        material = (UI / "ChatComposerGlass.kt").read_text(encoding="utf-8")
        self.assertIn("LocalChatComposerBackdrop.current", material)
        self.assertIn("Modifier.hazeEffect(source", material)
        self.assertIn("fallbackTint", material)
        self.assertNotIn("return@composed", material)
        self.assertNotIn(".background(LucentGlass.PureWhiteSurface)", material)
        chat = (UI / "NotebookChatTab.kt").read_text(encoding="utf-8")
        self.assertIn("LocalLiveGlassEnabled provides false", chat)

    def test_editor_line_limits_exclude_the_fixed_controls_row(self):
        composer = (UI / "ChatOverlayComponents.kt").read_text(encoding="utf-8")
        editor = composer.split("BasicTextField(", 1)[1].split(
            "Spacer(modifier = Modifier.height(10.dp))", 1)[0]
        self.assertIn("minLines = 2", editor)
        self.assertIn("maxLines = 5", editor)
        self.assertNotIn("heightIn(", editor)
        self.assertIn(".chatComposerGlass()", composer)
        for callback in ("onOpenHistory()", "onOpenDialogueSettings()", "onOpenModelPicker()",
                         "onSelectReasoning(preset)", "if (streaming) onStop() else onSend()"):
            self.assertIn(callback, composer)

    def test_scroll_and_regeneration_use_the_uncovered_viewport(self):
        for name in ("NotebookChatTab.kt", "ChatScrollRail.kt", "chat/ChatActionsCoordinator.kt"):
            text = (UI / name).read_text(encoding="utf-8")
            self.assertIn("layout.afterContentPadding", text, name)
        overlays = (UI / "ChatScrollOverlays.kt").read_text(encoding="utf-8")
        self.assertEqual(2, overlays.count("bottom = bottomInset + 8.dp"))
        self.assertNotIn("112.dp", overlays)
        self.assertNotIn("118.dp", overlays)
        self.assertNotIn("imePadding()", overlays)
        self.assertNotIn("navigationBarsPadding()", overlays)


if __name__ == "__main__":
    unittest.main()
