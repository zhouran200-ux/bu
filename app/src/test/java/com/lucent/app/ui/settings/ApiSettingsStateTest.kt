package com.lucent.app.ui.settings

import com.lucent.app.data.ApiProfile
import com.lucent.app.data.ApiProfileImportPlan
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
        state.draft.selectedModel.value = "model-A"
        state.saveActiveProfile()
        state.acceptCommitted(ApiProfileSnapshot(listOf(a, b), b.id))
        repeat(4) { yield() }
        val command = commands.single() as ApiProfileMutation.Update
        assertEquals("a", command.profile.id)
        assertEquals("edited A", command.profile.name)
        assertEquals(listOf("model-A"), command.profile.selectedModels)
        assertEquals("B", state.draft.name.value)
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
        state.draft.name.value = "unsaved"
        state.importProfiles(ApiProfileImportPlan(listOf(a.copy(name = "A2")), a.id, added = 1, updated = 0, dropped = 0))
        repeat(4) { yield() }
        assertEquals(listOf("A2"), savedNames)
        assertEquals(0, savedIndex)
        assertEquals(1, notifications.size)
        state.acceptCommitted(ApiProfileSnapshot(listOf(a.copy(name = "A2")), a.id))
        assertEquals("A2", state.draft.name.value)
    }
}
