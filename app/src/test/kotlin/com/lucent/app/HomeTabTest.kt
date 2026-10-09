package com.lucent.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HomeTabTest {

    @Test
    fun bottomBarReadsNotebooksSettings() {
        assertEquals(
            listOf(HomeTab.Notebooks, HomeTab.Settings),
            HomeTab.entries.toList()
        )
    }

    @Test
    fun screenMatchesTabDirectly() {
        assertEquals(HomeTab.Notebooks, HomeTab.of(Screen.Notebooks))
        assertEquals(HomeTab.Settings, HomeTab.of(Screen.Settings))
    }

    @Test
    fun tabReturnsCorrespondingScreen() {
        assertEquals(Screen.Notebooks, HomeTab.Notebooks.screen())
        assertEquals(Screen.Settings, HomeTab.Settings.screen())
    }

    @Test
    fun everyTabAndScreenHasALabel() {
        HomeTab.entries.forEach { assertTrue(it.label.isNotBlank()) }
        Screen.entries.forEach { assertTrue(it.label.isNotBlank()) }
    }
}
