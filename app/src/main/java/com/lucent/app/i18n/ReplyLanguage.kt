package com.lucent.app.i18n

fun currentLanguageKey(): String = "zh"

object ReplyLanguage {

    enum class Lang(val englishName: String, val nativeName: String) {
        ZH("Chinese", "中文")
    }

    fun detect(text: String): Lang = Lang.ZH

    fun instructionFor(userText: String): String =
        "CRITICAL OUTPUT LANGUAGE: Write your entire reply to the user in Chinese (中文)."
}
