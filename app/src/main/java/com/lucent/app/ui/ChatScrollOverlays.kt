package com.lucent.app.ui

import com.lucent.app.i18n.S

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

@Composable
internal fun BoxScope.ChatDeleteUndoLayer(
    pending: PendingChatDelete?,
    motionOn: Boolean,
    imeVisible: Boolean,
    onUndo: () -> Unit,
    onDismiss: () -> Unit
) {
    AnimatedVisibility(
        visible = pending != null,
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .imePadding()
            .then(if (!imeVisible) Modifier.navigationBarsPadding() else Modifier)
            .padding(bottom = 112.dp),
        enter = if (motionOn) slideInVertically(
            animationSpec = LucentMotion.toIntOffset(LucentMotion.Sheet)
        ) { it } + fadeIn(tween(180)) else fadeIn(tween(0)),
        exit = if (motionOn) slideOutVertically(
            animationSpec = LucentMotion.toIntOffset(LucentMotion.Firm)
        ) { it } + fadeOut(tween(150)) else fadeOut(tween(0))
    ) {
        pending?.let { target ->
            DeleteUndoBar(
                messageId = target.message.id,
                label = if (target.message.role == "user") S.deletedQuestion else S.deletedAnswer,
                onUndo = onUndo,
                onDismiss = onDismiss
            )
        }
    }
}

@Composable
internal fun BoxScope.ChatScrollToBottomButton(
    visible: Boolean,
    motionOn: Boolean,
    onClick: () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .navigationBarsPadding()
            .padding(end = 16.dp, bottom = 118.dp),
        enter = if (motionOn) fadeIn(tween(160)) + slideInVertically(tween(160)) { it } else fadeIn(tween(0)),
        exit = if (motionOn) fadeOut(tween(120)) + slideOutVertically(tween(120)) { it } else fadeOut(tween(0))
    ) {
        Surface(
            shape = CircleShape,
            color = glassPanelColor(),
            tonalElevation = 0.dp,
            shadowElevation = 4.dp,
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .clickable(onClick = onClick)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Rounded.ArrowDownward,
                    contentDescription = S.backToBottom,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
