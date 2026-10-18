from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
UI = ROOT / "app/src/main/java/com/lucent/app/ui"

POPUP_FILES = {
    "ChatHistorySheet.kt": UI / "ChatHistorySheet.kt",
    "ProfileModelPickerSheet.kt": UI / "ProfileModelPickerSheet.kt",
    "SettingsComponents.kt": UI / "SettingsComponents.kt",
    "NotebooksScreen.kt": UI / "NotebooksScreen.kt",
    "NotebookChatTab.kt": UI / "NotebookChatTab.kt",
    "ChatOverlayComponents.kt": UI / "ChatOverlayComponents.kt",
    "ChatDialogueSettings.kt": UI / "ChatDialogueSettings.kt",
    "FontSettingsPage.kt": UI / "settings/FontSettingsPage.kt",
}


class ForegroundGlassContractTest(unittest.TestCase):
    def test_cards_have_real_background_sampling_and_pure_white_bypass(self):
        text = (UI / "ForegroundGlass.kt").read_text(encoding="utf-8")
        self.assertIn("LocalGlassBackdrop", text)
        self.assertIn("Modifier.hazeEffect(source", text)
        self.assertIn("if (LocalPureWhite.current)", text)
        self.assertIn("fallbackTint", text)
        self.assertIn("foregroundGlass(cornerRadius)", (UI / "Glass.kt").read_text(encoding="utf-8"))

    def test_source_is_not_registered_on_foreground_pages(self):
        for name in ("NotebooksScreen.kt", "SettingsScreen.kt"):
            self.assertNotIn("hazeSource", (UI / name).read_text(encoding="utf-8"))
        main = (ROOT / "app/src/main/java/com/lucent/app/AppShell.kt").read_text(encoding="utf-8")
        self.assertIn(".hazeSource(state = glassBackdrop)", main)
        self.assertNotIn("HazeMaterials.ultraThin", main)

    def test_popups_let_material_own_position_and_size(self):
        material = (UI / "ForegroundGlass.kt").read_text(encoding="utf-8")
        self.assertNotIn("fun Modifier.glassPanel(", material)
        self.assertIn("fun Modifier.glassPopupPanel(", material)
        for name, path in POPUP_FILES.items():
            text = path.read_text(encoding="utf-8")
            self.assertNotIn("Modifier.glassPanel(", text, name)
        for name in ("ChatHistorySheet.kt", "ProfileModelPickerSheet.kt", "SettingsComponents.kt"):
            text = POPUP_FILES[name].read_text(encoding="utf-8")
            self.assertIn("containerColor = glassPanelColor()", text)
        dialogs = POPUP_FILES["NotebooksScreen.kt"].read_text(encoding="utf-8") + POPUP_FILES["FontSettingsPage.kt"].read_text(encoding="utf-8")
        self.assertIn("glassPopupPanel(", dialogs)
        self.assertIn("containerColor = glassPanelColor()", dialogs)
        chat = POPUP_FILES["ChatOverlayComponents.kt"].read_text(encoding="utf-8")
        self.assertIn("border = glassPopupBorder()", chat)
        self.assertIn("containerColor = glassPanelColor()", chat)

    def test_tall_sheets_stay_within_the_available_height(self):
        for name in ("ProfileModelPickerSheet.kt", "SettingsComponents.kt", "ChatDialogueSettings.kt"):
            text = POPUP_FILES[name].read_text(encoding="utf-8")
            self.assertIn("sheetContentHeightLimit()", text)
        picker = POPUP_FILES["ProfileModelPickerSheet.kt"].read_text(encoding="utf-8")
        self.assertIn("sheetGesturesEnabled = false", picker)
        self.assertIn("dragHandle = null", picker)
        self.assertIn("drawer.handle()", picker)
        notebooks = POPUP_FILES["NotebooksScreen.kt"].read_text(encoding="utf-8")
        self.assertIn("LaunchedEffect(listScrolling) { if (listScrolling) menuOpen = false }", notebooks)


if __name__ == "__main__":
    unittest.main()
