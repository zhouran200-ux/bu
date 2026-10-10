package com.lucent.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiProfileTest {

    @Test
    fun testUserAgentPresetsValues() {
        assertEquals("", UserAgentPresets.DEFAULT)
        assertEquals("node-fetch (+https://github.com/node-fetch/node-fetch)", UserAgentPresets.SILLY_TAVERN_NODE_FETCH)
    }

    @Test
    fun testApiProfileCustomUserAgentRoundTrip() {
        val original = listOf(
            ApiProfile(
                name = "SillyTavern Profile",
                spec = "openai",
                baseUrl = "https://api.example.com/v1",
                apiKey = "test-key-123",
                model = "gpt-4o",
                customUserAgent = UserAgentPresets.SILLY_TAVERN_NODE_FETCH
            ),
            ApiProfile(
                name = "Default Profile",
                spec = "openai",
                baseUrl = "https://api.openai.com/v1",
                apiKey = "sk-default",
                model = "gpt-3.5-turbo",
                customUserAgent = ""
            )
        )

        val serialized = ApiProfiles.serialize(original, encryptKeys = false)
        val parsed = ApiProfiles.parse(serialized)

        assertEquals(2, parsed.size)
        assertEquals("SillyTavern Profile", parsed[0].name)
        assertEquals(UserAgentPresets.SILLY_TAVERN_NODE_FETCH, parsed[0].customUserAgent)
        assertEquals("Default Profile", parsed[1].name)
        assertEquals("", parsed[1].customUserAgent)
    }

    @Test
    fun testLegacyJsonWithoutCustomUserAgentParsesGracefully() {
        val legacyJson = """
            [
                {
                    "name": "Legacy Profile",
                    "spec": "openai",
                    "baseUrl": "https://legacy.example.com",
                    "model": "model-1",
                    "provider": "custom",
                    "selectedModels": ["model-1"]
                }
            ]
        """.trimIndent()

        val parsed = ApiProfiles.parse(legacyJson)
        assertEquals(1, parsed.size)
        assertEquals("Legacy Profile", parsed[0].name)
        assertEquals("", parsed[0].customUserAgent)
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
}
