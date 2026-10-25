package com.lucent.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone

class ApiProfileTxtTest {
    private val zone = TimeZone.getTimeZone("Asia/Shanghai")

    private fun sample() = listOf(
        ApiProfile(
            name = "商汤",
            spec = "openai",
            baseUrl = "https://token.sensenova.cn/v1",
            apiKey = "sk-abc:123",
            selectedModels = listOf("SenseChat-5", "SenseChat-Turbo"),
            id = "fixed-1"
        ),
        ApiProfile(
            name = "Claude",
            spec = "anthropic",
            baseUrl = "http://localhost:8080/v1",
            apiKey = "",
            selectedModels = emptyList(),
            id = "fixed-2"
        )
    )

    @Test fun roundTripKeepsEveryFieldExceptTheLocalId() {
        val encoded = ApiProfileTxt.encode(sample(), 1_700_000_000_000L, zone)
        val parsed = ApiProfileTxt.decode(encoded).getOrThrow()
        assertTrue(encoded.lines().none { it.startsWith("model:") })
        assertEquals(2, parsed.profiles.size)
        assertEquals(0, parsed.invalidLines)
        parsed.profiles.forEachIndexed { index, restored ->
            val original = sample()[index]
            assertEquals(original.name, restored.name)
            assertEquals(original.spec, restored.spec)
            assertEquals(original.baseUrl, restored.baseUrl)
            assertEquals(original.apiKey, restored.apiKey)
            assertEquals(original.selectedModels, restored.selectedModels)
        }
    }

    @Test fun decodeAcceptsBomCrlfCommentsAndMissingMarkers() {
        val text = "\uFEFF# comment\r\nname: 手写\r\nspec: google\r\nbaseUrl: https://x.test/v1beta\r\napiKey: k\r\nmodels: a\r\nmodels: b\r\n"
        val parsed = ApiProfileTxt.decode(text).getOrThrow()
        assertEquals(1, parsed.profiles.size)
        assertEquals("手写", parsed.profiles.single().name)
        assertEquals("google", parsed.profiles.single().spec)
        assertEquals(listOf("a", "b"), parsed.profiles.single().selectedModels)
    }

    @Test fun decodeKeepsColonsInsideValues() {
        val text = "[方案]\nname: 端口\nbaseUrl: http://host:8080/v1\napiKey: a:b:c\n"
        val restored = ApiProfileTxt.decode(text).getOrThrow().profiles.single()
        assertEquals("http://host:8080/v1", restored.baseUrl)
        assertEquals("a:b:c", restored.apiKey)
    }

    @Test fun decodeKeepsTheLegacySingleModelInTheAvailableList() {
        val text = "[方案]\nname: 旧\nmodels: A\nmodel: Legacy-1\n"
        val restored = ApiProfileTxt.decode(text).getOrThrow().profiles.single()
        assertEquals(listOf("A", "Legacy-1"), restored.selectedModels)
    }

    @Test fun decodeRejectsTextWithoutAnyRecord() {
        assertTrue(ApiProfileTxt.decode("# only comments\n\n").isFailure)
        assertTrue(ApiProfileTxt.decode("").isFailure)
    }

    @Test fun unknownSpecFallsBackAndBlankNameIsFilled() {
        val restored = ApiProfileTxt.decode("[方案]\nspec: unknown\nbaseUrl: https://x.test\n").getOrThrow().profiles.single()
        assertEquals("openai", restored.spec)
        assertEquals("方案 1", restored.name)
    }

    @Test fun mergeUpdatesMatchingProfilesAndAppendsTheRest() {
        val existing = sample()
        val incoming = listOf(
            ApiProfile(name = "商汤", spec = "openai", baseUrl = "https://token.sensenova.cn/v1/", apiKey = "new", selectedModels = listOf("M2")),
            ApiProfile(name = "商汤", spec = "openai", baseUrl = "https://other.test/v1", apiKey = "k2", selectedModels = listOf("M3"))
        )
        val plan = planApiProfileImport(existing, "fixed-2", incoming, ApiProfileImportMode.Merge)
        assertEquals(1, plan.updated)
        assertEquals(1, plan.added)
        assertEquals(0, plan.dropped)
        assertEquals(3, plan.profiles.size)
        assertEquals("fixed-1", plan.profiles[0].id)
        assertEquals("new", plan.profiles[0].apiKey)
        assertEquals(listOf("M2"), plan.profiles[0].selectedModels)
        assertEquals("商汤 (导入)", plan.profiles[2].name)
        assertEquals("fixed-2", plan.selectedId)
    }

    @Test fun mergeKeepsExistingSecretsWhenTheFileLeavesThemBlank() {
        val existing = sample()
        val incoming = listOf(ApiProfile(name = "商汤", spec = "openai", baseUrl = "https://token.sensenova.cn/v1", apiKey = "", selectedModels = emptyList()))
        val plan = planApiProfileImport(existing, "fixed-1", incoming, ApiProfileImportMode.Merge)
        assertEquals(1, plan.updated)
        assertEquals("sk-abc:123", plan.profiles[0].apiKey)
        assertEquals(listOf("SenseChat-5", "SenseChat-Turbo"), plan.profiles[0].selectedModels)
    }

    @Test fun mergeStopsAtTheProfileCapAndCountsTheDroppedOnes() {
        val existing = (1..ApiProfiles.MAX).map { ApiProfile(name = "旧 " + it, spec = "openai", baseUrl = "https://x.test/" + it) }
        val incoming = listOf(ApiProfile(name = "新增", spec = "openai", baseUrl = "https://y.test"))
        val plan = planApiProfileImport(existing, null, incoming, ApiProfileImportMode.Merge)
        assertEquals(0, plan.added)
        assertEquals(1, plan.dropped)
        assertEquals(ApiProfiles.MAX, plan.profiles.size)
    }

    @Test fun replaceSwapsEverythingAndTrimsToTheCap() {
        val existing = sample()
        val incoming = (1..(ApiProfiles.MAX + 3)).map {
            ApiProfile(name = "新 " + it, spec = "google", baseUrl = "https://z.test/" + it)
        }
        val plan = planApiProfileImport(existing, "fixed-1", incoming, ApiProfileImportMode.Replace)
        assertEquals(ApiProfiles.MAX, plan.profiles.size)
        assertEquals(ApiProfiles.MAX, plan.added)
        assertEquals(3, plan.dropped)
        assertEquals(0, plan.updated)
        assertTrue(plan.profiles.none { it.id == "fixed-1" })
        assertEquals(plan.profiles.first().id, plan.selectedId)
    }

    @Test fun decodeKeepsEveryRecordAndThePlannerAppliesTheCap() {
        val builder = StringBuilder()
        repeat(ApiProfiles.MAX + 2) { builder.append("[方案]\nname: n").append(it).append("\nbaseUrl: https://x.test/").append(it).append("\n") }
        val parsed = ApiProfileTxt.decode(builder.toString()).getOrThrow()
        assertEquals(ApiProfiles.MAX + 2, parsed.profiles.size)
        val plan = planApiProfileImport(emptyList(), null, parsed.profiles, ApiProfileImportMode.Replace)
        assertEquals(ApiProfiles.MAX, plan.profiles.size)
        assertEquals(2, plan.dropped)
    }

    @Test fun emptyFileProducesNoPlanChange() {
        assertNull(ApiProfileTxt.decode("nothing here").getOrNull())
    }
}
