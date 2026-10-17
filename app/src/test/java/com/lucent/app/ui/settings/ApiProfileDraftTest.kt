package com.lucent.app.ui.settings

import com.lucent.app.data.ApiProfile
import org.junit.Assert.*
import org.junit.Test

class ApiProfileDraftTest {
    @Test fun persistenceEchoAndSwitchingDoNotOverwriteUnsavedFields() {
        val store = ApiProfileDraftStore()
        val a = ApiProfile(id = "a", name = "saved")
        val first = store.forProfile(a)
        first.name.value = "unsaved"
        store.forProfile(ApiProfile(id = "b", name = "other"))
        val echoed = store.forProfile(a.copy(name = "late echo"))
        assertSame(first, echoed)
        assertEquals("unsaved", echoed.name.value)
    }

    @Test fun onlySuccessfulDeleteDiscardsDraft() {
        val store = ApiProfileDraftStore()
        val a = ApiProfile(id = "a", name = "saved")
        val first = store.forProfile(a)
        first.key.value = "draft-key"
        assertEquals("draft-key", store.forProfile(a).key.value)
        store.removeAfterSuccessfulDelete(a.id)
        assertNotSame(first, store.forProfile(a))
    }
}
