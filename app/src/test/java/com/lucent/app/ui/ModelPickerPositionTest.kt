package com.lucent.app.ui

import com.lucent.app.data.ApiProfile
import kotlin.test.Test
import kotlin.test.assertEquals

class ModelPickerPositionTest {
    private fun profile(id: String, vararg models: String) =
        ApiProfile(id = id, name = id, selectedModels = models.toList())

    @Test fun singleModelSelectionStillExpandsItsProfile() {
        val profiles = listOf(profile("a", "only"))
        assertEquals(ModelPickerPosition("a", 1), modelPickerPosition(profiles, "a", "only"))
    }

    @Test fun selectsActualModelAfterCollapsedPrecedingProfiles() {
        val profiles = listOf(profile("a", "a1", "a2"), profile("b", "b1", "b2", "b3"))
        assertEquals(ModelPickerPosition("b", 4), modelPickerPosition(profiles, "b", "b3"))
    }

    @Test fun selectedProfileAndModelChangesRecalculatePosition() {
        val profiles = listOf(profile("a", "a1", "a2"), profile("b", "b1", "b2"))
        assertEquals(ModelPickerPosition("a", 2), modelPickerPosition(profiles, "a", "a2"))
        assertEquals(ModelPickerPosition("b", 3), modelPickerPosition(profiles, "b", "b2"))
        assertEquals(ModelPickerPosition("b", 2), modelPickerPosition(profiles.reversed(), "b", "b2"))
    }

    @Test fun missingSelectionAndEmptyProfilesDoNotExpandUnrelatedProfile() {
        assertEquals(ModelPickerPosition(null, 0), modelPickerPosition(emptyList(), "gone", "x"))
        assertEquals(ModelPickerPosition(null, 0), modelPickerPosition(listOf(profile("a", "x")), "gone", "x"))
    }

    @Test fun emptyProfileTargetsItsHeaderWithoutInventingAModel() {
        assertEquals(ModelPickerPosition(null, 1), modelPickerPosition(listOf(profile("a", "x"), profile("b")), "b", "gone"))
    }

    @Test fun normalizedModelsUseTheSameOrderAsRenderedRows() {
        val profiles = listOf(ApiProfile(id = "a", selectedModels = listOf(" x ", "x", "", "y", "z")))
        assertEquals(ModelPickerPosition("a", 3), modelPickerPosition(profiles, "a", "z"))
    }
}
