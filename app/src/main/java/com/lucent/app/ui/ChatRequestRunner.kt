package com.lucent.app.ui

import com.lucent.app.chat.ChatSession
import com.lucent.app.chat.UiChatMessage
import com.lucent.app.data.ApiProfile
import com.lucent.app.data.Note
import com.lucent.app.data.NovelPromptBuilder
import com.lucent.app.data.NovelSourceIndex
import com.lucent.app.data.ReasoningPreset
import com.lucent.app.data.ReasoningResolver
import com.lucent.app.data.ResolvedReasoningConfig
import com.lucent.app.network.ApiSpec
import com.lucent.app.network.ChatMessagePayload
import com.lucent.app.network.LlmClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class ChatEndpoint(
    val spec: ApiSpec,
    val baseUrl: String,
    val apiKey: String,
    val rawEndpoint: Boolean
)

internal fun resolveChatEndpoint(profile: ApiProfile?): ChatEndpoint {
    val specText = profile?.spec?.lowercase().orEmpty()
    val spec = when {
        specText.contains("google") || specText.contains("gemini") -> ApiSpec.GOOGLE
        specText.contains("anthropic") || specText.contains("claude") -> ApiSpec.ANTHROPIC
        else -> ApiSpec.OPENAI
    }
    return ChatEndpoint(
        spec = spec,
        baseUrl = profile?.baseUrl.orEmpty(),
        apiKey = profile?.apiKey.orEmpty(),
        rawEndpoint = specText == "custom"
    )
}

internal fun resolveChatReasoning(
    preset: ReasoningPreset,
    profile: ApiProfile?,
    model: String
): ResolvedReasoningConfig = ReasoningResolver.resolve(
    preset = preset,
    provider = profile?.spec?.lowercase().orEmpty(),
    model = model
)

internal fun buildChatHistory(
    messages: List<UiChatMessage>,
    answerId: Long,
    rounds: Int
): List<ChatMessagePayload> = messages
    .takeWhile { it.id != answerId }
    .takeLast(rounds * 2)
    .map {
        ChatMessagePayload(
            role = if (it.role == "assistant") "assistant" else "user",
            content = it.content
        )
    }

internal fun startChatStream(
    session: ChatSession,
    answerId: Long,
    effortLabel: String?,
    sourceNote: Note?,
    customPrompt: String,
    endpoint: ChatEndpoint,
    model: String,
    historyPayload: List<ChatMessagePayload>,
    resolvedReasoning: ResolvedReasoningConfig
) {
    var knowledgeBasePrompt = ""
    session.start(answerId, effortLabel, sourceBinding = {
        val index = withContext(Dispatchers.Default) { sourceNote?.let { NovelSourceIndex.get(it) } }
        knowledgeBasePrompt = withContext(Dispatchers.Default) {
            NovelPromptBuilder.system(index, customPrompt)
        }
        index?.noteId to index?.revision
    }) { handle, onChunk ->
        LlmClient.chatStream(
            baseUrl = endpoint.baseUrl,
            spec = endpoint.spec,
            apiKey = endpoint.apiKey,
            model = model,
            messages = historyPayload.mapIndexed { index, message ->
                if (index == historyPayload.lastIndex && message.role == "user")
                    message.copy(content = NovelPromptBuilder.user(message.content, sourceNote != null))
                else message
            },
            systemInstruction = knowledgeBasePrompt,
            reasoningConfig = resolvedReasoning,
            handle = handle,
            rawEndpoint = endpoint.rawEndpoint,
            onChunk = onChunk
        )
    }
}
