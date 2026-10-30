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

    @Test fun decodeAcceptsEqualsSeparatorWhenValuesContainColons() {
        val text = "[方案]\nname = 端口\nbaseUrl=https://host:8443/v1\napiKey=sk:secret\n"
        val restored = ApiProfileTxt.decode(text).getOrThrow().profiles.single()
        assertEquals("端口", restored.name)
        assertEquals("https://host:8443/v1", restored.baseUrl)
        assertEquals("sk:secret", restored.apiKey)
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

    @Test fun unknownSectionDoesNotLeakFieldsIntoThePreviousProfile() {
        val text = "[方案]\nname: 第一\nbaseUrl: https://first.test/v1\n[说明]\nbaseUrl: https://ignored.test\napiKey: ignored\n[方案]\nname: 第二\n"
        val parsed = ApiProfileTxt.decode(text).getOrThrow()
        assertEquals(2, parsed.profiles.size)
        assertEquals(1, parsed.invalidLines)
        assertEquals("https://first.test/v1", parsed.profiles[0].baseUrl)
        assertEquals("", parsed.profiles[0].apiKey)
        assertEquals("第二", parsed.profiles[1].name)
    }

    @Test fun preparationMergesModelsForDuplicateImportedKeys() {
        val existingProfile = ApiProfile(name = "现有", apiKey = "other", id = "existing")
        val existing = listOf(existingProfile)
        val imported = listOf(
            ApiProfile(name = "A", apiKey = "same-key", selectedModels = listOf("1", "2"), id = "a"),
            ApiProfile(name = "B", apiKey = "same-key", selectedModels = listOf("2", "3", "4"), id = "b")
        )
        val prepared = prepareApiProfileImport(existing, imported)
        assertEquals(1, prepared.profiles.size)
        assertEquals(listOf("1", "2", "3", "4"), prepared.profiles.single().selectedModels)
        assertEquals(1, prepared.mergedDuplicateCount)
        assertEquals(0, prepared.conflicts.size)
        assertEquals(listOf(existingProfile), existing)
    }

    @Test fun preparationReportsExistingKeyConflictsWithoutChangingExisting() {
        val existingProfile = ApiProfile(name = "现有", apiKey = "same-key", selectedModels = listOf("0"))
        val prepared = prepareApiProfileImport(
            listOf(existingProfile),
            listOf(ApiProfile(name = "导入", apiKey = " same-key ", selectedModels = listOf("1")))
        )
        assertEquals(1, prepared.profiles.size)
        assertEquals(listOf("1"), prepared.profiles.single().selectedModels)
        assertEquals(listOf(ApiProfileKeyConflict("导入", "现有")), prepared.conflicts)
        assertEquals(listOf("0"), existingProfile.selectedModels)
    }

    @Test fun mergeAddsWithoutUpdatingExistingProfiles() {
        val existing = sample()
        val incoming = listOf(
            ApiProfile(name = "商汤", spec = "openai", baseUrl = "https://token.sensenova.cn/v1/", apiKey = "new", selectedModels = listOf("M2")),
            ApiProfile(name = "商汤", spec = "openai", baseUrl = "https://other.test/v1", apiKey = "k2", selectedModels = listOf("M3"))
        )
        val plan = planApiProfileImport(existing, "fixed-2", incoming, ApiProfileImportMode.Merge)
        assertEquals(2, plan.added)
        assertEquals(0, plan.skipped)
        assertEquals(4, plan.profiles.size)
        assertEquals(existing, plan.profiles.take(2))
        assertEquals("fixed-2", plan.selectedId)
        assertEquals("商汤 (导入)", plan.profiles[2].name)
        assertEquals("商汤 (导入 2)", plan.profiles[3].name)
    }

    @Test fun mergeSkipsProfilesWhoseKeyAlreadyExists() {
        val existing = listOf(ApiProfile(name = "现有", apiKey = "same-key", selectedModels = listOf("0")))
        val incoming = listOf(ApiProfile(name = "导入", apiKey = "same-key", selectedModels = listOf("1", "2")))
        val plan = planApiProfileImport(existing, existing.single().id, incoming, ApiProfileImportMode.Merge)
        assertEquals(existing, plan.profiles)
        assertEquals(0, plan.added)
        assertEquals(1, plan.skipped)
        assertEquals(existing.single().id, plan.selectedId)
    }

    @Test fun mergeSkipsAnExactDuplicateWithABlankKey() {
        val profile = ApiProfile(name = "无密钥", selectedModels = listOf("m"))
        val plan = planApiProfileImport(listOf(profile), profile.id, listOf(profile.copy(id = "another")), ApiProfileImportMode.Merge)
        assertEquals(listOf(profile), plan.profiles)
        assertEquals(0, plan.added)
        assertEquals(1, plan.skipped)
    }

    @Test fun mergeAddsBeyondFiftyProfiles() {
        val existing = (1..50).map { ApiProfile(name = "旧 " + it, spec = "openai", baseUrl = "https://x.test/" + it) }
        val incoming = listOf(ApiProfile(name = "新增", spec = "openai", baseUrl = "https://y.test"))
        val plan = planApiProfileImport(existing, null, incoming, ApiProfileImportMode.Merge)
        assertEquals(1, plan.added)
        assertEquals(51, plan.profiles.size)
    }

    @Test fun replaceSwapsEverythingWithoutTrimming() {
        val existing = sample()
        val incoming = (1..53).map {
            ApiProfile(name = "新 " + it, spec = "google", baseUrl = "https://z.test/" + it)
        }
        val plan = planApiProfileImport(existing, "fixed-1", incoming, ApiProfileImportMode.Replace)
        assertEquals(53, plan.profiles.size)
        assertEquals(53, plan.added)
        assertEquals(0, plan.skipped)
        assertTrue(plan.profiles.none { it.id == "fixed-1" })
        assertEquals(plan.profiles.first().id, plan.selectedId)
    }

    @Test fun replaceMergesDuplicateImportedKeysBeforeApplying() {
        val incoming = listOf(
            ApiProfile(name = "A", apiKey = "same", selectedModels = listOf("1", "2")),
            ApiProfile(name = "B", apiKey = "same", selectedModels = listOf("3", "4"))
        )
        val plan = planApiProfileImport(emptyList(), null, incoming, ApiProfileImportMode.Replace)
        assertEquals(1, plan.profiles.size)
        assertEquals(listOf("1", "2", "3", "4"), plan.profiles.single().selectedModels)
    }

    @Test fun decodeKeepsEveryRecordAndThePlannerKeepsAll() {
        val builder = StringBuilder()
        repeat(52) { builder.append("[方案]\nname: n").append(it).append("\nbaseUrl: https://x.test/").append(it).append("\n") }
        val parsed = ApiProfileTxt.decode(builder.toString()).getOrThrow()
        assertEquals(52, parsed.profiles.size)
        val plan = planApiProfileImport(emptyList(), null, parsed.profiles, ApiProfileImportMode.Replace)
        assertEquals(52, plan.profiles.size)
    }

    @Test fun identicalBackupMergeDoesNotChangeAnything() {
        val existing = sample()
        val imported = ApiProfileTxt.decode(ApiProfileTxt.encode(existing, 0L, zone)).getOrThrow().profiles
        val plan = planApiProfileImport(existing, "fixed-1", imported, ApiProfileImportMode.Merge)
        assertEquals(existing, plan.profiles)
        assertEquals(0, plan.added)
        assertEquals(2, plan.skipped)
    }

    @Test fun emptyFileProducesNoPlanChange() {
        assertNull(ApiProfileTxt.decode("nothing here").getOrNull())
    }
}
