package com.lucent.app

import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import com.lucent.app.data.SettingsCache
import com.lucent.app.data.SettingsRepository
import com.lucent.app.data.SettingsWriteCoordinator
import com.lucent.app.data.TypographyScopes
import com.lucent.app.i18n.S
import com.lucent.app.ui.LucentToast
import android.content.Context

object AppScope {
    val io = CoroutineScope(
        SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, throwable ->
            android.util.Log.e(TAG, "background write failed", throwable)
        }
    )

    private val settingsWriter = SettingsWriteCoordinator(io)

    private val themeModeSetting = SettingsWriteCoordinator.Setting({ SettingsCache.themeMode }, { SettingsCache.themeMode = it })

    fun setThemeMode(context: Context, repo: SettingsRepository, value: String) =
        save(context, listOf(themeModeSetting.change(value) { repo.themeMode.first() })) { repo.setThemeMode(value) }

    private val paletteSetting = SettingsWriteCoordinator.Setting({ SettingsCache.palette }, { SettingsCache.palette = it })

    fun setPalette(context: Context, repo: SettingsRepository, value: String) =
        save(context, listOf(paletteSetting.change(value) { repo.palette.first() })) { repo.setPalette(value) }

    private val fontSetting = SettingsWriteCoordinator.Setting({ SettingsCache.font }, { SettingsCache.font = it })

    fun setFont(context: Context, repo: SettingsRepository, value: String) =
        save(context, listOf(fontSetting.change(value) { repo.font.first() })) { repo.setFont(value) }

    private val fontScaleSetting = SettingsWriteCoordinator.Setting({ SettingsCache.fontScale }, { SettingsCache.fontScale = it })

    fun setFontScale(context: Context, repo: SettingsRepository, value: Float) =
        save(context, listOf(fontScaleSetting.change(value) { repo.fontScale.first() })) { repo.setFontScale(value) }

    private val lineSpacingSetting = SettingsWriteCoordinator.Setting({ SettingsCache.lineSpacing }, { SettingsCache.lineSpacing = it })

    fun setLineSpacing(context: Context, repo: SettingsRepository, value: Float) =
        save(context, listOf(lineSpacingSetting.change(value) { repo.lineSpacing.first() })) { repo.setLineSpacing(value) }

    private val letterSpacingSetting = SettingsWriteCoordinator.Setting({ SettingsCache.letterSpacing }, { SettingsCache.letterSpacing = it })

    fun setLetterSpacing(context: Context, repo: SettingsRepository, value: Float) =
        save(context, listOf(letterSpacingSetting.change(value) { repo.letterSpacing.first() })) { repo.setLetterSpacing(value) }

    private val paragraphSpacingSetting = SettingsWriteCoordinator.Setting({ SettingsCache.paragraphSpacing }, { SettingsCache.paragraphSpacing = it })

    fun setParagraphSpacing(context: Context, repo: SettingsRepository, value: Float) =
        save(context, listOf(paragraphSpacingSetting.change(value) { repo.paragraphSpacing.first() })) { repo.setParagraphSpacing(value) }

    private val typographyScopeSetting = SettingsWriteCoordinator.Setting({ SettingsCache.typographyScope }, { SettingsCache.typographyScope = it })

    fun setTypographyScope(context: Context, repo: SettingsRepository, value: String) {
        val scope = TypographyScopes.normalize(value)
        save(context, listOf(typographyScopeSetting.change(scope) { repo.typographyScope.first() })) {
            repo.setTypographyScope(scope)
        }
    }

    private val dynamicColorSetting = SettingsWriteCoordinator.Setting({ SettingsCache.dynamicColor }, { SettingsCache.dynamicColor = it })

    fun setDynamicColorEnabled(context: Context, repo: SettingsRepository, value: Boolean) =
        save(context, listOf(dynamicColorSetting.change(value) { repo.dynamicColorEnabled.first() })) { repo.setDynamicColorEnabled(value) }

    private val backgroundAnimationEnabledSetting = SettingsWriteCoordinator.Setting({ SettingsCache.backgroundAnimationEnabled }, { SettingsCache.backgroundAnimationEnabled = it })

    fun setBackgroundAnimationEnabled(context: Context, repo: SettingsRepository, value: Boolean) =
        save(context, listOf(backgroundAnimationEnabledSetting.change(value) { repo.backgroundAnimationEnabled.first() })) { repo.setBackgroundAnimationEnabled(value) }

    fun resetTypographyDefaults(context: Context, repo: SettingsRepository) = save(
        context,
        listOf(
            fontScaleSetting.change(1.0f) { repo.fontScale.first() },
            lineSpacingSetting.change(1.2f) { repo.lineSpacing.first() },
            letterSpacingSetting.change(0.0f) { repo.letterSpacing.first() },
            paragraphSpacingSetting.change(1.0f) { repo.paragraphSpacing.first() }
        )
    ) { repo.resetTypographyDefaults() }

    private fun save(
        context: Context,
        changes: List<SettingsWriteCoordinator.Change>,
        write: suspend () -> Unit
    ): kotlinx.coroutines.Job {
        val appContext = context.applicationContext
        return settingsWriter.submit(changes, write) {
            android.util.Log.e(TAG, "settings write failed")
            withContext(Dispatchers.Main) { LucentToast.show(appContext, S.settingsSaveFailed) }
        }
    }

    private const val TAG = "LucentAppScope"
}
