package com.lucent.app.ui.settings

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.*
import org.junit.Test

class ApiRequestOwnerFailureTest {
    @Test fun alreadyCancelledScopeDoesNotLeaveBusySet() = runBlocking {
        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Job().apply { cancel() })
        var busy = false
        ApiRequestOwner().start(scope, { true }, { busy = it }, { Unit }, { fail("Unexpected failure") }, { fail("Cancelled request ran") })
        yield()
        assertFalse(busy)
    }

    @Test fun unexpectedFailureClearsBusyAndReportsOnlyToCurrentTarget() = runBlocking {
        val owner = ApiRequestOwner()
        var busy = false
        var errors = 0
        owner.start<Unit>(this, { true }, { busy = it }, { error("failed") }, { errors++ }, { fail("Unexpected value") })
        yield()
        assertFalse(busy)
        assertEquals(1, errors)
    }
}
