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

    @Test
    fun profilePagesAreTenItemsAndNeverEmpty() {
        assertEquals(1, profilePageCount(0))
        assertEquals(1, profilePageCount(10))
        assertEquals(2, profilePageCount(11))
        assertEquals(5, profilePageCount(50))
        assertEquals(0..9, profilePageRange(0, 49))
        assertEquals(0..9, profilePageRange(9, 49))
        assertEquals(10..19, profilePageRange(10, 49))
        assertEquals(40..49, profilePageRange(40, 49))
        assertEquals(40..40, profilePageRange(40, 40))
    }

    @Test
    fun steppedDoesNotLeaveTheCurrentProfilePage() {
        val profiles = (0..12).map { ApiProfile(name = ('A' + it).toString()) }
        val keys = profiles.map { it.name }
        val drag = ProfileDrag(profiles, keys, selected = 10, held = 10, travel = 0f)
        assertEquals(drag, drag.stepped(-1, 10..12))
        val moved = drag.stepped(1, 10..12)
        assertEquals(11, moved.held)
        assertEquals("K", moved.order[11].name)
        assertEquals(11, moved.selected)
    }
}
