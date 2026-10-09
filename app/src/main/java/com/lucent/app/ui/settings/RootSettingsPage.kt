package com.lucent.app.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.lucent.app.i18n.S
import com.lucent.app.ui.LocalOnGradientMuted
import com.lucent.app.ui.NavCard
import com.lucent.app.ui.SettingsRoute

@Composable
internal fun RootSettingsPage(onRoute: (SettingsRoute) -> Unit) {
    val context = LocalContext.current
    val downloadUrl = "https://mail.10086.cn/default.html"

    NavCard(S.settingsAppearanceTitle, S.settingsAppearanceSub) { onRoute(SettingsRoute.Appearance) }
    Spacer(modifier = Modifier.height(12.dp))
    NavCard(S.settingsEditorTitle, S.settingsEditorSub) { onRoute(SettingsRoute.Editor) }
    Spacer(modifier = Modifier.height(12.dp))
    NavCard(S.settingsCloudModelTitle, "选择、连接与记忆") { onRoute(SettingsRoute.CloudModel) }
    Spacer(modifier = Modifier.height(12.dp))
    NavCard(
        title = "明细",
        subtitle = downloadUrl,
        trailing = {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                contentDescription = null,
                tint = LocalOnGradientMuted.current
            )
        }
    ) {
        runCatching {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }
}
