package com.lucent.app.ui

import com.lucent.app.AppNavigation
import com.lucent.app.Screen
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
        drain()
        LastScreen.current = Screen.Notebooks
    }

    @AfterTest
    fun tearDown() {
        drain()
    }

    private fun drain() {
        AppNavigation.consumeScreen()
        AppNavigation.resetSettingsRoute()
        com.lucent.app.SettingsNav.handler = null
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
    fun apiManagementRouteSurvivesSettingsPageNotYetMounted() {
        com.lucent.app.SettingsNav.go(SettingsRoute.Api)
        assertEquals(SettingsRoute.Api, AppNavigation.settingsRoute)
    }

    @Test
    fun settingsRouteStillNotifiesMountedHandler() {
        var delivered: SettingsRoute? = null
        com.lucent.app.SettingsNav.handler = { delivered = it }
        com.lucent.app.SettingsNav.go(SettingsRoute.Api)
        assertEquals(SettingsRoute.Api, delivered)
        assertEquals(SettingsRoute.Api, AppNavigation.settingsRoute)
    }

    @Test
    fun lastScreenTracksTheVisibleScreen() {
        LastScreen.remember(Screen.Notebooks)
        assertEquals(Screen.Notebooks, LastScreen.current)
        LastScreen.remember(Screen.Settings)
        assertEquals(Screen.Settings, LastScreen.current)
    }

    @Test
    fun newStringsExistInEveryLanguage() {
        assertTrue(S.screenNotebooks.isNotBlank())
        assertTrue(S.notebookNew.isNotBlank())
        assertTrue(S.actionCancel.isNotBlank())
    }
}
