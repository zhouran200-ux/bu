package com.lucent.app.ui

import com.lucent.app.AppNavigation
import com.lucent.app.Screen
import com.lucent.app.i18n.L
import com.lucent.app.i18n.S
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HomeNavigationTest {

    @BeforeTest
    fun setUp() {
        L.apply("en")
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
        AppNavigation.consumeEditNoteId()
        AppNavigation.consumeNoteId()
        AppNavigation.consumeReturnScreen()
        AppNavigation.consumeComposeNote()
    }

    @Test
    fun editNoteRoutesToNotebooksWithThePendingId() {
        AppNavigation.editNote(42L)
        assertEquals(Screen.Notebooks, AppNavigation.consumeScreen())
        assertEquals(42L, AppNavigation.consumeEditNoteId())
        assertNull(AppNavigation.consumeEditNoteId())
    }

    @Test
    fun openNoteRemembersWhereToReturn() {
        AppNavigation.openNote(5L, from = Screen.Settings)
        assertEquals(Screen.Notebooks, AppNavigation.consumeScreen())
        assertEquals(5L, AppNavigation.consumeNoteId())
        assertEquals(Screen.Settings, AppNavigation.consumeReturnScreen())
        assertNull(AppNavigation.consumeReturnScreen())
    }

    @Test
    fun composeRequestsLandOnNotebooks() {
        AppNavigation.requestComposeNote()
        assertEquals(Screen.Notebooks, AppNavigation.consumeScreen())
        assertTrue(AppNavigation.consumeComposeNote())
        assertFalse(AppNavigation.consumeComposeNote())
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
    fun hydrateRestoresScreenAndIgnoresUnknownNames() {
        LastScreen.hydrate("Settings")
        assertEquals(Screen.Settings, LastScreen.current)
        LastScreen.hydrate("NoSuchScreen")
        assertEquals(Screen.Notebooks, LastScreen.current)
        LastScreen.hydrate("")
        assertEquals(Screen.Notebooks, LastScreen.current)
        assertEquals(Screen.Notebooks.name, LastScreen.persistedName())
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
