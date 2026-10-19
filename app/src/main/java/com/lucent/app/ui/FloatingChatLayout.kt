package com.lucent.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

private enum class ChatLayer { Composer, Messages, Controls }

@Composable
internal fun FloatingChatLayout(
    modifier: Modifier = Modifier,
    messages: @Composable (bottomInset: Dp) -> Unit,
    composer: @Composable () -> Unit,
    controls: @Composable BoxScope.(bottomInset: Dp) -> Unit
) {
    val chatBackdrop = rememberHazeState()
    SubcomposeLayout(modifier = modifier) { constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val composerPlaceable = subcompose(ChatLayer.Composer) {
            CompositionLocalProvider(LocalChatComposerBackdrop provides chatBackdrop) {
                Box(Modifier.fillMaxWidth().navigationBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 6.dp)) {
                    composer()
                }
            }
        }.single().measure(loose)
        val bottomInset = composerPlaceable.height.toDp()
        val messagePlaceable = subcompose(ChatLayer.Messages) {
            Box(Modifier.fillMaxSize()
                .testTag("chat_message_viewport")
                .hazeSource(state = chatBackdrop)
                .chatBottomFade(bottomInset)) {
                messages(bottomInset)
            }
        }.single().measure(loose)
        val controlsPlaceable = subcompose(ChatLayer.Controls) {
            Box(Modifier.fillMaxSize()) { controls(bottomInset) }
        }.single().measure(loose)
        layout(constraints.maxWidth, constraints.maxHeight) {
            messagePlaceable.placeRelative(0, 0)
            composerPlaceable.placeRelative(0, constraints.maxHeight - composerPlaceable.height)
            controlsPlaceable.placeRelative(0, 0)
        }
    }
}
