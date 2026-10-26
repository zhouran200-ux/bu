from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
MAIN = ROOT / "app/src/main/java/com/lucent/app"
UI = MAIN / "ui"
SETTINGS = UI / "settings"


class ApiConfigContractTest(unittest.TestCase):
    def test_api_config_manages_profiles_and_available_models_with_a_transient_test_choice(self):
        page = (SETTINGS / "ApiSettingsPage.kt").read_text(encoding="utf-8")
        section = (SETTINGS / "ApiModelsSection.kt").read_text(encoding="utf-8")
        self.assertNotIn("selectedModel", page)
        self.assertNotIn("onSelectedModelChange", page)
        self.assertNotIn("ApiReasoningSummarySection", page)
        self.assertNotIn("selectedModel", section)
        self.assertNotIn("selectedCard", section)
        self.assertIn("S.apiAvailableModels", section)
        self.assertIn("ApiModelPickerDialog(", section)
        self.assertIn("testModel: String", section)
        self.assertIn("onTestModelChange: (String) -> Unit", section)
        self.assertIn("selectable(", section)
        self.assertIn("testModel = testModel", page)
        self.assertIn("onTestModelChange = { testModel = it }", page)

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

    def test_transfer_actions_sit_beside_the_profile_count_without_a_card(self):
        page = (SETTINGS / "ApiSettingsPage.kt").read_text(encoding="utf-8")
        actions = (SETTINGS / "ApiProfileTransferActions.kt").read_text(encoding="utf-8")
        list_section = (SETTINGS / "ApiProfileListSection.kt").read_text(encoding="utf-8")
        self.assertNotIn("ApiProfileTransferSection", page)
        self.assertIn("headerActions = {", page)
        self.assertIn("ApiProfileTransferActions(", page)
        self.assertIn("headerActions: @Composable () -> Unit", list_section)
        self.assertIn("headerActions()", list_section)
        self.assertIn("S.apiTransferImport", actions)
        self.assertIn("S.apiTransferExport", actions)
        self.assertIn("ApiProfileImportMode.Merge", actions)
        self.assertIn("ApiProfileImportMode.Replace", actions)
        self.assertFalse((SETTINGS / "ApiProfileTransferSection.kt").exists())

    def test_connection_test_targets_a_selected_model_without_persisting_it(self):
        diagnostics = (SETTINGS / "ApiConnectionDiagnosticsSection.kt").read_text(encoding="utf-8")
        self.assertNotIn("currentTestTarget", diagnostics)
        self.assertNotIn("apiTestConnectionTarget", diagnostics)
        self.assertNotIn("onTestModelChange", diagnostics)
        self.assertNotIn("DropdownMenu", diagnostics)
        self.assertIn("S.apiTestGeneration", diagnostics)
        self.assertIn("S.apiProbeScopeHintModel(testModel)", diagnostics)
        self.assertIn("S.apiProbeScopeHint", diagnostics)
        page = (SETTINGS / "ApiSettingsPage.kt").read_text(encoding="utf-8")
        self.assertIn("hasModels = models.isNotEmpty()", page)
        self.assertIn("ApiProbe.testConnection(url, spec, key, targetModel)", page)
        self.assertIn("latestTestModel.value == targetModel", page)
        self.assertNotIn("selectedModel", page)
        self.assertNotIn("if (res.success) {\n                            commitAndSaveProfile()", page)
        self.assertIn("onRequestTransfer", page)

    def test_generation_probe_sends_hello_world_once(self):
        payload = (MAIN / "network/ConnectionProbePayload.kt").read_text(encoding="utf-8")
        connection = (MAIN / "network/LlmClientConnection.kt").read_text(encoding="utf-8")
        self.assertIn('CONNECTION_PROBE_PROMPT = "hello world"', payload)
        self.assertNotIn('"ping"', payload)
        self.assertIn("connectionProbePayload(spec, probeModel)", connection)
        self.assertNotIn('"ping"', connection)


if __name__ == "__main__":
    unittest.main()
