package com.lucent.app.data

import org.junit.Assert.*
import org.junit.Test

class ApiProfileMutationTest {
    private val a = ApiProfile(id = "a", name = "A")
    private val b = ApiProfile(id = "b", name = "B")

    @Test fun staleOrderDoesNotOverwriteFreshlySavedProfileFields() {
        val saved = a.copy(apiKey = "new-key", selectedModels = listOf("new-model"))
        val current = ApiProfileSnapshot(listOf(saved, b), "a")
        val next = current.mutate(ApiProfileMutation.Reorder(listOf("b", "a"), "b"))
        assertEquals(listOf("b", "a"), next.profiles.map { it.id })
        assertEquals(saved, next.profiles.last())
        assertEquals("a", next.selectedId)
    }

    @Test fun updateForDeletedIdentityCannotResurrectIt() {
        val current = ApiProfileSnapshot(listOf(a, b), "b").mutate(ApiProfileMutation.Delete("a"))
        assertEquals(current, current.mutate(ApiProfileMutation.Update(a.copy(name = "old edit"))))
        assertEquals("b", current.selectedId)
    }

    @Test fun reorderDoesNotLoseProfilesAddedAfterUiSnapshot() {
        val current = ApiProfileSnapshot(listOf(a, b), "a").mutate(ApiProfileMutation.Add(ApiProfile(id = "c", name = "C")))
        val next = current.mutate(ApiProfileMutation.Reorder(listOf("b", "a"), "a"))
        assertEquals(listOf("b", "a", "c"), next.profiles.map { it.id })
        assertEquals("c", next.selectedId)
    }

    @Test fun selectingMissingIdentityLeavesCurrentSelectionIntact() {
        val current = ApiProfileSnapshot(listOf(a, b), "b")
        assertEquals(current, current.mutate(ApiProfileMutation.Select("deleted")))
    }
}
