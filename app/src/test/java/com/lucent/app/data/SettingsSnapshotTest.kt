package com.lucent.app.data

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class SettingsSnapshotTest {
    @Test fun groupedApiUpdateNeverPublishesMixedPair() {
        val before = SettingsCache.snapshot()
        val pool = Executors.newFixedThreadPool(2)
        val start = CountDownLatch(1)
        try {
            SettingsCache.updateApiProfiles("0", 0)
            val writer = pool.submit { start.await(); repeat(10000) { SettingsCache.updateApiProfiles(it.toString(), it) } }
            val reader = pool.submit { start.await(); repeat(10000) { val value = SettingsCache.snapshot(); assertEquals(value.apiProfilesJson.toInt(), value.apiProfileSelected) } }
            start.countDown()
            writer.get(10, TimeUnit.SECONDS)
            reader.get(10, TimeUnit.SECONDS)
        } finally {
            SettingsCache.updateApiProfiles(before.apiProfilesJson, before.apiProfileSelected)
            pool.shutdownNow()
        }
    }

    @Test fun unrelatedPropertyUpdatePreservesApiPair() {
        val before = SettingsCache.snapshot()
        try {
            SettingsCache.updateApiProfiles("saved", 2)
            SettingsCache.palette = "OCEAN"
            val snapshot = SettingsCache.snapshot()
            assertEquals("saved", snapshot.apiProfilesJson)
            assertEquals(2, snapshot.apiProfileSelected)
        } finally {
            SettingsCache.updateApiProfiles(before.apiProfilesJson, before.apiProfileSelected)
            SettingsCache.palette = before.palette
        }
    }
}
