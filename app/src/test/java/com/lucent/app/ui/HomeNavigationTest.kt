package com.lucent.app.ui

import com.lucent.app.AppNavigation
import com.lucent.app.Screen
import com.lucent.app.i18n.L
import com.lucent.app.i18n.S
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HomeNavigationTest {

    @BeforeTest
    fun setUp() {
        L.apply("zh")
        drain()
        LastScreen.current = Screen.Notebooks
        LastScreen.home = Screen.Notebooks
    }

    @AfterTest
    fun tearDown() {
        drain()
    }

    private fun drain() {
        AppNavigation.consumeScreen()
    }

    @Test
    fun createNotebookRequestLandsOnNotebooksAndIsConsumedOnce() {
        AppNavigation.requestCreateNotebook()
        assertEquals(Screen.Notebooks, AppNavigation.consumeScreen())
        assertTrue(AppNavigation.consumeCreateNotebook())
        assertEquals(false, AppNavigation.consumeCreateNotebook())
    }

    @Test
    fun requestedScreenIsDeliveredExactlyOnce() {
        AppNavigation.requestScreen(Screen.Settings)
        assertEquals(Screen.Settings, AppNavigation.consumeScreen())
        assertNull(AppNavigation.consumeScreen())
    }

    @Test
    fun lastScreenTracksTheHomeScreen() {
        LastScreen.remember(Screen.Notebooks)
        assertEquals(Screen.Notebooks, LastScreen.home)
        LastScreen.remember(Screen.Settings)
        assertEquals(Screen.Notebooks, LastScreen.home)
        assertEquals(Screen.Settings, LastScreen.current)
    }

    @Test
    fun newStringsExistInEveryLanguage() {
        L.apply("zh")
        assertTrue(S.screenNotebooks.isNotBlank())
        assertTrue(S.notebooksTotal(3).contains("3"))
        assertTrue(S.notebooksTotal(1).contains("1"))
        assertTrue(S.notebooksTotal(2).contains("2"))
    }
}
