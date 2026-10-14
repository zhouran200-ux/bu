package com.lucent.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class ApiProfileTest {

    @Test
    fun testUserAgentPresetsValues() {
        assertEquals("node-fetch (+https://github.com/node-fetch/node-fetch)", UserAgentPresets.SILLY_TAVERN_NODE_FETCH)
    }

    @Test
    fun testApiProfileRoundTripKeepsEveryField() {
        val original = listOf(
            ApiProfile(
                name = "SillyTavern Profile",
                spec = "openai",
                baseUrl = "https://api.example.com/v1",
                apiKey = "test-key-123",
                model = "gpt-4o",
                selectedModels = listOf("gpt-4o", "gpt-4o-mini")
            ),
            ApiProfile(
                name = "Default Profile",
                spec = "openai",
                baseUrl = "https://api.openai.com/v1",
                apiKey = "sk-default",
                model = "gpt-3.5-turbo"
            )
        )

        val serialized = ApiProfiles.serialize(original)
        val parsed = ApiProfiles.parse(serialized)

        assertEquals(original, parsed)
        assertEquals(2, parsed.size)
        assertEquals("SillyTavern Profile", parsed[0].name)
        assertEquals(listOf("gpt-4o", "gpt-4o-mini"), parsed[0].selectedModels)
        assertEquals("Default Profile", parsed[1].name)
        assertEquals("sk-default", parsed[1].apiKey)
    }

    @Test
    fun testStoredJsonWithRemovedUserAgentFieldStillParses() {
        val storedJson = """
            [
                {
                    "name": "Legacy Profile",
                    "spec": "openai",
                    "baseUrl": "https://legacy.example.com",
                    "apiKey": "k",
                    "model": "model-1",
                    "provider": "custom",
                    "selectedModels": ["model-1"],
                    "customUserAgent": "node-fetch (+https://github.com/node-fetch/node-fetch)"
                }
            ]
        """.trimIndent()

        val parsed = ApiProfiles.parse(storedJson)
        assertEquals(1, parsed.size)
        assertEquals("Legacy Profile", parsed[0].name)
        assertEquals(listOf("model-1"), parsed[0].selectedModels)
        assertFalse(parsed[0].toString().contains("node-fetch"))
    }

    @Test
    fun testNewProfilesHaveUniqueUuidIds() {
        val first = ApiProfile(name = "same")
        val second = ApiProfile(name = "same")
        assertEquals(first.id, UUID.fromString(first.id).toString())
        assertNotEquals(first.id, second.id)
        assertEquals(first.id, first.copy(name = "renamed").id)
    }

    @Test
    fun testLegacyIdsAreStableDistinctAndIndependentOfSecrets() {
        val json = """[
            {"name":"重复", "spec":"openai", "baseUrl":"https://example.com", "apiKey":"secret-one"},
            {"name":"重复", "spec":"openai", "baseUrl":"https://example.com", "apiKey":"secret-two"}
        ]""".trimIndent()
        val first = ApiProfiles.parse(json)
        assertEquals(first, ApiProfiles.parse(json))
        assertNotEquals(first[0].id, first[1].id)
        assertEquals(first.map { it.id }, ApiProfiles.parse(json.replace("secret", "changed")).map { it.id })
        first.forEach { assertEquals(it.id, UUID.fromString(it.id).toString()) }
        assertEquals(first, ApiProfiles.parse(ApiProfiles.serialize(first)))
    }

    @Test
    fun testBlankAndDuplicateIdsAreRepairedDeterministically() {
        val json = """[
            {"id":"saved", "name":"A"},
            {"id":"saved", "name":"A"},
            {"id":"   ", "name":"A"},
            {"name":"A"}
        ]""".trimIndent()
        val profiles = ApiProfiles.parse(json)
        assertEquals("saved", profiles[0].id)
        assertEquals(4, profiles.map { it.id }.toSet().size)
        assertFalse(profiles.any { it.id.isBlank() })
        assertEquals(profiles, ApiProfiles.parse(json))
        assertEquals(profiles, ApiProfiles.parse(ApiProfiles.serialize(profiles)))
    }

    @Test
    fun testDuplicateRepairDoesNotStealALaterStoredId() {
        val duplicateJson = """[{"id":"same", "name":"A"}, {"id":"same", "name":"B"}]"""
        val repairedId = ApiProfiles.parse(duplicateJson)[1].id
        val json = """[
            {"id":"same", "name":"A"},
            {"id":"same", "name":"B"},
            {"id":"$repairedId", "name":"C"}
        ]""".trimIndent()
        val profiles = ApiProfiles.parse(json)
        assertEquals(repairedId, profiles[2].id)
        assertEquals(3, profiles.map { it.id }.toSet().size)
        assertEquals(profiles, ApiProfiles.parse(json))
    }

    @Test
    fun testParseNormalizesSelectedModelsAndKeepsLegacyPrimaryAvailable() {
        val profiles = ApiProfiles.parse("""[
            {"name":"A", "model":" primary ", "selectedModels":[" one ", "", "one", "  ", " two "]},
            {"name":"B", "model":" legacy "}
        ]""".trimIndent())
        assertEquals(listOf("one", "two"), profiles[0].selectedModels)
        assertEquals(listOf("one", "two", "primary"), ApiProfiles.availableModels(profiles[0]))
        assertEquals(listOf("legacy"), profiles[1].selectedModels)
        assertEquals(listOf("legacy"), ApiProfiles.availableModels(profiles[1]))
    }

    private fun named(vararg names: String): List<ApiProfile> = names.map { ApiProfile(name = it) }

    private fun namesOf(profiles: List<ApiProfile>): List<String> = profiles.map { it.name }

    @Test
    fun testMovedReordersUpAndDown() {
        val list = named("A", "B", "C", "D")
        assertEquals(listOf("B", "A", "C", "D"), namesOf(ApiProfiles.moved(list, 0, 1)))
        assertEquals(listOf("A", "D", "B", "C"), namesOf(ApiProfiles.moved(list, 3, 1)))
        assertEquals(listOf("D", "A", "B", "C"), namesOf(ApiProfiles.moved(list, 3, 0)))
        assertEquals(listOf("B", "C", "D", "A"), namesOf(ApiProfiles.moved(list, 0, 3)))
    }

    @Test
    fun testMovedKeepsTheListUntouchedForImpossibleRequests() {
        val list = named("A", "B", "C")
        assertEquals(list, ApiProfiles.moved(list, 1, 1))
        assertEquals(list, ApiProfiles.moved(list, -1, 0))
        assertEquals(list, ApiProfiles.moved(list, 0, 3))
        assertEquals(list, ApiProfiles.moved(list, 4, 0))
        assertEquals(emptyList<ApiProfile>(), ApiProfiles.moved(emptyList<ApiProfile>(), 0, 1))
    }

    @Test
    fun testMovedNeverLosesOrDuplicatesProfiles() {
        val list = named("A", "B", "C", "D", "E")
        for (from in list.indices) {
            for (to in list.indices) {
                val moved = ApiProfiles.moved(list, from, to)
                assertEquals(list.size, moved.size)
                assertEquals(list.toSet(), moved.toSet())
            }
        }
    }

    @Test
    fun testOrderKeysStayPairedWithTheirProfileThroughMoves() {
        val list = named("A", "B", "A", "A", "C")
        val pairsAtStart = ApiProfiles.orderKeys(list).mapIndexed { i, key -> key to list[i] }
        var order = list
        var keys = ApiProfiles.orderKeys(list)
        for ((from, to) in listOf(0 to 1, 3 to 1, 4 to 0, 2 to 4, 1 to 0)) {
            order = ApiProfiles.moved(order, from, to)
            keys = ApiProfiles.moved(keys, from, to)
        }
        assertEquals(order.size, keys.size)
        assertEquals(pairsAtStart.toSet(), keys.mapIndexed { i, key -> key to order[i] }.toSet())
        assertEquals(pairsAtStart.size, keys.toSet().size)
    }

    @Test
    fun testMovedWorksForAnyList() {
        assertEquals(listOf("b", "a", "c"), ApiProfiles.moved(listOf("a", "b", "c"), 0, 1))
        assertEquals(listOf(2, 3, 1), ApiProfiles.moved(listOf(1, 2, 3), 0, 2))
    }

    @Test
    fun testSelectionFollowsTheActiveProfileThroughEveryMove() {
        val list = named("A", "B", "C", "D")
        for (selected in list.indices) {
            for (from in list.indices) {
                for (to in list.indices) {
                    val after = ApiProfiles.moved(list, from, to)
                    val newSelected = ApiProfiles.selectionAfterMove(selected, from, to)
                    assertEquals(
                        "active profile $selected moving $from->$to",
                        list[selected],
                        after[newSelected]
                    )
                }
            }
        }
    }

    @Test
    fun testSelectionIsUnchangedWhenNothingActuallyMoves() {
        assertEquals(2, ApiProfiles.selectionAfterMove(selected = 2, from = 0, to = 1))
        assertEquals(0, ApiProfiles.selectionAfterMove(selected = 0, from = 2, to = 3))
        assertEquals(1, ApiProfiles.selectionAfterMove(selected = 1, from = 1, to = 1))
    }

    @Test
    fun testSteppedDragKeepsSelectionOnTheHeldProfile() {
        var order = named("A", "B", "C", "D", "E")
        var selected = 2
        for (step in listOf(1, 1, -1, 1, 1, -1, -1, 1)) {
            val to = selected + step
            if (to in order.indices) {
                order = ApiProfiles.moved(order, selected, to)
                selected = ApiProfiles.selectionAfterMove(selected, selected, to)
            }
        }
        assertEquals("C", order[selected].name)
        assertEquals(listOf("A", "B", "D", "C", "E"), namesOf(order))
    }

    @Test
    fun testOrderKeysStayUniqueWhenNamesRepeat() {
        val list = named("A", "B", "A", "A")
        val keys = ApiProfiles.orderKeys(list)
        assertEquals(listOf("A", "B", "A#1", "A#2"), keys)
        assertEquals(keys.size, keys.toSet().size)
        assertEquals(keys.toSet(), ApiProfiles.orderKeys(list.reversed()).toSet())
    }

    @Test
    fun testOrderKeysMatchTheListLength() {
        assertEquals(0, ApiProfiles.orderKeys(emptyList()).size)
        assertEquals(1, ApiProfiles.orderKeys(named("only")).size)
        assertEquals(3, ApiProfiles.orderKeys(named("x", "x", "x")).size)
    }

    private fun profileWithModels(name: String, vararg models: String) =
        ApiProfile(name = name, model = models.firstOrNull().orEmpty(), selectedModels = models.toList())

    @Test
    fun testResolveSelectionDistinguishesDuplicateNamesById() {
        val first = ApiProfile(name = "same", baseUrl = "https://first.example", apiKey = "first-key", model = "shared")
        val second = ApiProfile(name = "same", baseUrl = "https://second.example", apiKey = "second-key", model = "shared")
        val profiles = listOf(first, second)
        assertEquals(second to "shared", ApiProfiles.resolveSelection(profiles, second.id, "same", "shared"))
        assertEquals(first to "shared", ApiProfiles.resolveSelection(profiles, "", "same", "shared"))
    }

    @Test
    fun testResolveSelectionSurvivesRenameReorderAndSerialization() {
        val first = profileWithModels("first", "a")
        val second = profileWithModels("second", "b", "c")
        val renamed = second.copy(name = "renamed")
        val profiles = ApiProfiles.parse(ApiProfiles.serialize(listOf(renamed, first)))
        assertEquals(renamed to "c", ApiProfiles.resolveSelection(profiles, second.id, "second", "c"))
        assertEquals(renamed to "c", ApiProfiles.resolveSelection(profiles.reversed(), second.id, "second", "c"))
    }

    @Test
    fun testResolveSelectionMissingIdDoesNotAliasLegacyName() {
        val first = profileWithModels("first", "a")
        val second = profileWithModels("second", "b")
        val profiles = listOf(first, second)
        assertEquals(first to "a", ApiProfiles.resolveSelection(profiles, "deleted-id", "second", "b"))
        assertEquals(second to "b", ApiProfiles.resolveSelection(profiles, "", "second", "b"))
        assertEquals(second to "b", ApiProfiles.resolveSelection(profiles, "  ", "second", "b"))
    }

    @Test
    fun testAvailableModelsTrimsDeduplicatesAndAppendsMissingPrimary() {
        val profile = ApiProfile(model = " primary ", selectedModels = listOf(" one ", "", "one", "  ", " two "))
        assertEquals(listOf("one", "two", "primary"), ApiProfiles.availableModels(profile))
        assertEquals(listOf("one", "two"), ApiProfiles.availableModels(profile.copy(model = " one ")))
        assertEquals(listOf("primary"), ApiProfiles.availableModels(profile.copy(selectedModels = emptyList())))
    }

    @Test
    fun testResolveSelectionUsesNormalizedSavedThenPrimaryThenFirstModel() {
        val profile = ApiProfile(model = " primary ", selectedModels = listOf(" one ", "two", " two "))
        assertEquals(profile to "two", ApiProfiles.resolveSelection(listOf(profile), profile.id, "", " two "))
        assertEquals(profile to "primary", ApiProfiles.resolveSelection(listOf(profile), profile.id, "", "gone"))
        assertEquals(profile to "primary", ApiProfiles.resolveSelection(listOf(profile), profile.id, "", " primary "))
        val withoutPrimary = profile.copy(model = "  ")
        assertEquals(withoutPrimary to "one", ApiProfiles.resolveSelection(listOf(withoutPrimary), profile.id, "", "gone"))
        assertEquals(profile.name to "two", ApiProfiles.selectionFor(listOf(profile), profile.name, " two "))
    }

    @Test
    fun testResolveSelectionHandlesEmptyProfilesAndModels() {
        assertNull(ApiProfiles.resolveSelection(emptyList(), "id", "name", "model"))
        assertEquals("" to "", ApiProfiles.selectionFor(emptyList(), "name", "model"))
        val profile = ApiProfile(model = "  ", selectedModels = listOf("", "  "))
        assertEquals(emptyList<String>(), ApiProfiles.availableModels(profile))
        assertEquals(profile to "", ApiProfiles.resolveSelection(listOf(profile), profile.id, "", "missing"))
    }

    @Test
    fun testSelectionForKeepsAStillValidChoice() {
        val profiles = listOf(
            profileWithModels("甲", "m-a1", "m-a2"),
            profileWithModels("乙", "m-b1")
        )
        assertEquals("乙" to "m-b1", ApiProfiles.selectionFor(profiles, "乙", "m-b1"))
        assertEquals("甲" to "m-a2", ApiProfiles.selectionFor(profiles, "甲", "m-a2"))
    }

    @Test
    fun testSelectionForFallsBackToTheFirstSavedProfile() {
        val profiles = listOf(
            profileWithModels("甲", "m-a1", "m-a2"),
            profileWithModels("乙", "m-b1")
        )
        assertEquals("甲" to "m-a1", ApiProfiles.selectionFor(profiles, "已删除的方案", "已删除的模型"))
        assertEquals("甲" to "m-a1", ApiProfiles.selectionFor(profiles, "", ""))
        assertEquals("", ApiProfiles.selectionFor(emptyList(), "甲", "m-a1").first)
    }

    @Test
    fun testSelectionForNeverReturnsAModelTheProfileDoesNotOffer() {
        val profiles = listOf(
            ApiProfile(name = "甲", model = "m-a1", selectedModels = listOf("m-a2")),
            ApiProfile(name = "乙", model = "", selectedModels = emptyList())
        )
        assertEquals("甲" to "m-a1", ApiProfiles.selectionFor(profiles, "甲", "m-a1"))
        assertEquals("乙" to "", ApiProfiles.selectionFor(profiles, "乙", "别的方案里的模型"))
        assertEquals("乙" to "", ApiProfiles.selectionFor(profiles, "乙", ""))
    }

    @Test
    fun profileCountIsCappedOnBothWriteAndRead() {
        val many = (1..(ApiProfiles.MAX + 5)).map { profileWithModels("方案 $it", "m$it") }
        assertEquals(ApiProfiles.MAX, ApiProfiles.parse(ApiProfiles.serialize(many)).size)
        assertEquals(ApiProfiles.MAX, ApiProfiles.parse(ApiProfiles.serialize(many.take(ApiProfiles.MAX))).size)

        val oversizedJson = ApiProfiles.serialize(many)
        assertEquals(ApiProfiles.MAX, ApiProfiles.parse(oversizedJson).size)
    }

    @Test
    fun nextDefaultNameSkipsNamesAlreadyInUse() {
        assertEquals("方案 1", ApiProfiles.nextDefaultName(emptyList()))
        assertEquals("方案 2", ApiProfiles.nextDefaultName(listOf(ApiProfile(name = "方案 1"))))
        assertEquals("方案 4", ApiProfiles.nextDefaultName(
            listOf(ApiProfile(name = "方案 1"), ApiProfile(name = "方案 2"), ApiProfile(name = "方案 3"))
        ))
        assertEquals("方案 1", ApiProfiles.nextDefaultName(listOf(ApiProfile(name = "商汤"))))
        assertEquals("方案 3", ApiProfiles.nextDefaultName(
            listOf(ApiProfile(name = "API 1"), ApiProfile(name = "方案 2"), ApiProfile(name = "自定义"))
        ))
        assertEquals("方案 2", ApiProfiles.nextDefaultName(
            listOf(ApiProfile(name = "方案 1"), ApiProfile(name = "方案 1"))
        ))
    }

    @Test
    fun parseSkipsNonObjectEntriesAndKeepsValidOnes() {
        val json = """[1,"text",{"name":"有效","spec":"openai","model":"m"},{"spec":"anthropic"}]"""
        val parsed = ApiProfiles.parse(json)
        assertEquals(2, parsed.size)
        assertEquals("有效", parsed[0].name)
        assertEquals("openai", parsed[0].spec)
        assertEquals("anthropic", parsed[1].spec)
        assertEquals(listOf("m"), parsed[0].selectedModels)
        assertTrue(parsed.none { it.name.isBlank() })
        assertEquals(parsed.size, parsed.map { it.name }.toSet().size)
    }

    @Test
    fun parseDistinctsAndTrimsStoredModels() {
        val json = """[{"name":"p","selectedModels":[" a ","a","","  ","b"]}]"""
        assertEquals(listOf("a", "b"), ApiProfiles.parse(json)[0].selectedModels)
    }

    @Test
    fun blankOrBrokenJsonFallsBackToTheDefaultProfile() {
        assertEquals(ApiProfiles.DEFAULT_PROFILES, ApiProfiles.parse(""))
        assertEquals(ApiProfiles.DEFAULT_PROFILES, ApiProfiles.parse(null))
        assertEquals(ApiProfiles.DEFAULT_PROFILES, ApiProfiles.parse("not json at all"))
        assertEquals(ApiProfiles.DEFAULT_PROFILES, ApiProfiles.parse("[]"))
    }

    @Test
    fun storedParsingDistinguishesNeverSavedFromDeliberatelyEmptied() {
        assertNull(ApiProfiles.parseStored(null))
        assertNull(ApiProfiles.parseStored(""))
        assertNull(ApiProfiles.parseStored("   "))
        assertEquals(emptyList<ApiProfile>(), ApiProfiles.parseStored("[]"))
        assertNull(ApiProfiles.parseStored("not json at all"))
        assertEquals(1, ApiProfiles.parseStored("""[{"name":"甲","model":"m"}]""")!!.size)
    }

    @Test
    fun deletingEveryStoredProfileStaysDeleted() {
        val emptied = ApiProfiles.serialize(emptyList())
        assertEquals("[]", emptied)
        assertEquals(emptyList<ApiProfile>(), ApiProfiles.parseStored(emptied))
        assertEquals(ApiProfiles.DEFAULT_PROFILES, ApiProfiles.parse(emptied))
    }
}
