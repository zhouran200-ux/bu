package com.lucent.app.ui.settings

import com.lucent.app.data.ApiProfile
import kotlin.test.Test
import kotlin.test.assertEquals

class ApiProfileLogicTest {
    @Test
    fun modelChoicesTrimsDeduplicatesAndKeepsFetchedBeforeSortedSaved() {
        val result = modelChoices(
            fetched = listOf(" gpt-4 ", "", "gpt-4", "claude"),
            saved = listOf(" saved-z ", "claude", "saved-a", " ")
        )
        assertEquals(listOf("gpt-4", "claude", "saved-z", "saved-a"), result)
    }

    @Test
    fun steppedMovesHeldProfileAndSelectionTogether() {
        val profiles = listOf(ApiProfile(name = "A"), ApiProfile(name = "B"), ApiProfile(name = "C"))
        val keys = listOf("A", "B", "C")
        val drag = ProfileDrag(profiles, keys, selected = 1, held = 1, travel = 12f)
        val moved = drag.stepped(1)
        assertEquals(listOf("A", "C", "B"), moved.order.map { it.name })
        assertEquals(2, moved.selected)
        assertEquals(2, moved.held)
        assertEquals(12f, moved.travel)
    }

    @Test
    fun steppedIgnoresOutOfBoundsMoves() {
        val profiles = listOf(ApiProfile(name = "A"))
        val drag = ProfileDrag(profiles, listOf("A"), selected = 0, held = 0, travel = 1f)
        assertEquals(drag, drag.stepped(-1))
        assertEquals(drag, drag.stepped(1))
    }
}
