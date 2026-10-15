package com.lucent.app.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class NovelPromptBuilderTest {
    @Test fun promptUsesRealShortMarkersAndStableSourceBeforeCustomInstructions() {
        val note = Note(id = 42, title = "书", body = "# 第一章\n\n原文。")
        val source = NovelSourceIndex.get(note)
        val prompt = NovelPromptBuilder.system(source, "用户设定")
        assertTrue(prompt.contains("〔S：1-1〕"))
        assertTrue(prompt.indexOf(source.tagged.text) < prompt.indexOf("用户设定"))
        assertFalse(prompt.contains(source.revision))
        assertFalse(prompt.contains("〔REF:"))
        assertTrue(NovelPromptBuilder.user("问题", true).startsWith("问题"))
        assertTrue(NovelPromptBuilder.user("问题", true).contains("reply_contract"))
        assertEquals("问题", NovelPromptBuilder.user("问题", false))
    }
    @Test fun sourceCacheReusesVersionAndInvalidatesChangedBody() {
        val note = Note(id = 43, title = "书", body = "正文。")
        val first = NovelSourceIndex.get(note)
        assertTrue(first === NovelSourceIndex.get(note))
        val changed = NovelSourceIndex.get(note.copy(body = "新正文。"))
        assertFalse(first.revision == changed.revision)
        NovelSourceIndex.evict(note.id)
        assertFalse(changed === NovelSourceIndex.get(note.copy(body = "新正文。")))
    }
}
