package com.lucent.app.data

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class ModelRegistryConcurrencyTest {
    @Test fun parallelRegistrationAndSnapshotsAreSafeAndFindable() {
        val pool = Executors.newFixedThreadPool(4)
        val prefix = "registry-test-" + java.util.UUID.randomUUID()
        try {
            val tasks = (1..100).map { index -> pool.submit(Callable {
                val id = prefix + index
                ModelCapabilityRegistry.register(ModelDefinition(id, ReasoningCapability.NONE))
                ModelCapabilityRegistry.getAll()
                assertEquals(id, ModelCapabilityRegistry.find(id)?.id)
                id
            }) }
            val ids = tasks.map { it.get(10, TimeUnit.SECONDS) }
            val snapshot = ModelCapabilityRegistry.getAll().map { it.id }.toSet()
            assertTrue(snapshot.containsAll(ids))
        } finally { pool.shutdownNow() }
    }
}
