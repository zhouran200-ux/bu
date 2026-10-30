package com.lucent.app.ui.settings

import com.lucent.app.data.ApiProfile
import com.lucent.app.data.ApiProfileImportMode
import com.lucent.app.data.ApiProfileMutation
import com.lucent.app.data.ApiProfileSnapshot
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.*
import org.junit.Test

class ApiSettingsStateTest {
    @Test fun identityDraftSurvivesReorderAndFailedDelete() = runBlocking {
        val a = ApiProfile(id = "a", name = "A")
        val b = ApiProfile(id = "b", name = "B")
        val notifications = mutableListOf<String>()
        val state = ApiSettingsState(ApiProfileSnapshot(listOf(a, b), a.id), ApiProfileCommandQueue(this), this,
            { error("disk") }, { _, _ -> }, { notifications += it }, { "saved" })
        state.draft.name.value = "unsaved"
        state.acceptCommitted(ApiProfileSnapshot(listOf(b, a.copy(name = "late")), a.id))
        assertEquals("unsaved", state.draft.name.value)
        state.deleteProfile(1)
        repeat(4) { yield() }
        assertEquals("unsaved", state.draft.name.value)
        assertEquals(1, notifications.size)
    }

    @Test fun saveCommandCapturesIdentityAndFieldsBeforeSelectionChanges() = runBlocking {
        val a = ApiProfile(id = "a", name = "A")
        val b = ApiProfile(id = "b", name = "B")
        val commands = mutableListOf<ApiProfileMutation>()
        val state = ApiSettingsState(ApiProfileSnapshot(listOf(a, b), a.id), ApiProfileCommandQueue(this), this,
            { commands += it }, { _, _ -> }, {}, { "saved" })
        state.draft.name.value = "edited A"
        state.draft.models.value = listOf("model-A")
        state.saveActiveProfile()
        state.acceptCommitted(ApiProfileSnapshot(listOf(a, b), b.id))
        repeat(4) { yield() }
        val command = commands.single() as ApiProfileMutation.Update
        assertEquals("a", command.profile.id)
        assertEquals("edited A", command.profile.name)
        assertEquals(listOf("model-A"), command.profile.selectedModels)
        assertEquals("B", state.draft.name.value)
    }

    @Test fun customModelInputBecomesAnAvailableModelWithoutSelectingIt() = runBlocking {
        val a = ApiProfile(id = "a", name = "A")
        val commands = mutableListOf<ApiProfileMutation>()
        val state = ApiSettingsState(ApiProfileSnapshot(listOf(a), a.id), ApiProfileCommandQueue(this), this,
            { commands += it }, { _, _ -> }, {}, { "saved" })
        state.draft.customModelInput.value = "manual-model"
        assertTrue(state.hasUnsavedChanges())
        state.saveActiveProfile()
        repeat(4) { yield() }
        val command = commands.single() as ApiProfileMutation.Update
        assertEquals(listOf("manual-model"), command.profile.selectedModels)
        assertTrue(state.draft.customModelInput.value.isEmpty())
        state.acceptCommitted(ApiProfileSnapshot(listOf(command.profile), a.id))
        assertFalse(state.hasUnsavedChanges())
    }

    @Test fun failedSaveDoesNotRunSuccessfulContinuationAndCanRetry() = runBlocking {
        val a = ApiProfile(id = "a", name = "A")
        var failSave = true
        var continued = 0
        val outcomes = mutableListOf<Boolean>()
        val state = ApiSettingsState(ApiProfileSnapshot(listOf(a), a.id), ApiProfileCommandQueue(this), this,
            { if (failSave) error("disk full") }, { _, _ -> }, {}, { "saved" })
        state.draft.name.value = "edited"
        state.saveActiveProfile { result ->
            outcomes += result.isSuccess
            if (result.isSuccess) continued++
        }
        assertTrue(state.saving)
        repeat(4) { yield() }
        assertFalse(state.saving)
        assertEquals(listOf(false), outcomes)
        assertEquals(0, continued)
        assertEquals("edited", state.draft.name.value)
        assertTrue(state.hasUnsavedChanges())
        failSave = false
        state.saveActiveProfile { if (it.isSuccess) continued++ }
        repeat(4) { yield() }
        assertEquals(1, continued)
        assertFalse(state.hasUnsavedChanges())
    }

    @Test fun duplicateNonBlankKeyBlocksSaveAndNamesTheExistingProfile() = runBlocking {
        val a = ApiProfile(id = "a", name = "A", apiKey = "same-key")
        val b = ApiProfile(id = "b", name = "B", apiKey = "other-key")
        val commands = mutableListOf<ApiProfileMutation>()
        val notifications = mutableListOf<String>()
        val state = ApiSettingsState(ApiProfileSnapshot(listOf(a, b), b.id), ApiProfileCommandQueue(this), this,
            { commands += it }, { _, _ -> }, { notifications += it }, { "saved" })
        state.draft.key.value = "same-key"
        state.saveActiveProfile()
        repeat(4) { yield() }
        assertTrue(commands.isEmpty())
        assertTrue(notifications.single().contains("A"))
        assertFalse(state.saving)
        assertTrue(state.hasUnsavedChanges())
    }

    @Test fun importRejectsUnsavedDraftAndPreservesIt() = runBlocking {
        val a = ApiProfile(id = "a", name = "A")
        var writes = 0
        val state = ApiSettingsState(ApiProfileSnapshot(listOf(a), a.id), ApiProfileCommandQueue(this), this,
            {}, { _, _ -> writes++ }, {}, { "saved" })
        state.draft.key.value = "unsaved secret"
        state.importProfiles(listOf(a.copy(name = "replacement")), ApiProfileImportMode.Replace)
        repeat(4) { yield() }
        assertEquals(0, writes)
        assertEquals("unsaved secret", state.draft.key.value)
        assertTrue(state.hasUnsavedChanges())
    }

    @Test fun savedDraftIsIncludedWhenImportPlanIsBuiltAfterSave() = runBlocking {
        val a = ApiProfile(id = "a", name = "A")
        var result: List<ApiProfile>? = null
        val state = ApiSettingsState(ApiProfileSnapshot(listOf(a), a.id), ApiProfileCommandQueue(this), this,
            {}, { profiles, _ -> result = profiles }, {}, { "saved" })
        state.draft.key.value = "new secret"
        state.saveActiveProfile { outcome ->
            if (outcome.isSuccess) state.importProfiles(listOf(ApiProfile(id = "b", name = "B")), ApiProfileImportMode.Merge)
        }
        repeat(8) { yield() }
        assertEquals("new secret", result!!.first().apiKey)
        assertEquals(2, result!!.size)
        assertFalse(state.saving)
    }

    @Test fun reorderCompletionIsSingleAndOnlyAfterPersistence() = runBlocking {
        val a = ApiProfile(id = "a", name = "A")
        val b = ApiProfile(id = "b", name = "B")
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        var count = 0
        val state = ApiSettingsState(ApiProfileSnapshot(listOf(a, b), a.id), ApiProfileCommandQueue(this), this,
            { gate.await() }, { _, _ -> }, {}, { "saved" })
        state.reorderProfiles(listOf(b, a), 1) { assertTrue(it.isSuccess); count++ }
        yield()
        assertEquals(0, count)
        gate.complete(Unit)
        repeat(4) { yield() }
        assertEquals(1, count)
    }

    @Test fun importPersistsTheWholePlanAndDropsStaleDrafts() = runBlocking {
        val a = ApiProfile(id = "a", name = "A")
        val notifications = mutableListOf<String>()
        var savedNames: List<String>? = null
        var savedIndex = -1
        val state = ApiSettingsState(ApiProfileSnapshot(listOf(a), a.id), ApiProfileCommandQueue(this), this,
            {}, { list, index -> savedNames = list.map { it.name }; savedIndex = index },
            { notifications += it }, { "saved" })
        state.importProfiles(listOf(a.copy(name = "A2")), ApiProfileImportMode.Replace)
        repeat(4) { yield() }
        assertEquals(listOf("A2"), savedNames)
        assertEquals(0, savedIndex)
        assertEquals(1, notifications.size)
        state.acceptCommitted(ApiProfileSnapshot(listOf(a.copy(name = "A2")), a.id))
        assertEquals("A2", state.draft.name.value)
    }
}
