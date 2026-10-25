from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
MAIN = ROOT / "app/src/main/java/com/lucent/app"
UI = MAIN / "ui"
SETTINGS = UI / "settings"


class ApiConfigContractTest(unittest.TestCase):
    def test_api_config_only_manages_profiles_and_available_models(self):
        page = (SETTINGS / "ApiSettingsPage.kt").read_text(encoding="utf-8")
        section = (SETTINGS / "ApiModelsSection.kt").read_text(encoding="utf-8")
        self.assertNotIn("selectedModel", page)
        self.assertNotIn("onSelectedModelChange", page)
        self.assertNotIn("ApiReasoningSummarySection", page)
        self.assertNotIn("selectedModel", section)
        self.assertNotIn("selectedCard", section)
        self.assertIn("S.apiAvailableModels", section)
        self.assertIn("ApiModelPickerDialog(", section)

    def test_chat_picker_remains_the_only_chat_model_selector(self):
        picker = (UI / "ProfileModelPickerSheet.kt").read_text(encoding="utf-8")
        self.assertIn("onSelect(profile, model)", picker)
        coordinator = (UI / "chat/ChatActionsCoordinator.kt").read_text(encoding="utf-8")
        self.assertIn('prefs.getString("model_$notebookId"', coordinator)
        profile = (MAIN / "data/ApiProfile.kt").read_text(encoding="utf-8")
        self.assertNotIn("val model:", profile)
        self.assertIn('optString("model"', profile)

    def test_selected_profile_card_has_border_bold_name_and_stronger_wash(self):
        list_section = (SETTINGS / "ApiProfileListSection.kt").read_text(encoding="utf-8")
        self.assertIn("borderWidth = 1.5.dp, washStrength = 1.35f", list_section)
        self.assertIn("fontWeight = if (isActive) FontWeight.Bold", list_section)
        self.assertIn("selected = isActive", list_section)

    def test_connection_test_no_longer_targets_a_chat_model(self):
        diagnostics = (SETTINGS / "ApiConnectionDiagnosticsSection.kt").read_text(encoding="utf-8")
        self.assertNotIn("currentTestTarget", diagnostics)
        self.assertNotIn("apiTestConnectionTarget", diagnostics)
        self.assertIn("S.apiTestConnection", diagnostics)


if __name__ == "__main__":
    unittest.main()
