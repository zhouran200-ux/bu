package com.lucent.app.data

object TypographyScopes {
    const val GLOBAL = "global"
    const val TEXT = "text"

    fun normalize(value: String?): String = if (value == TEXT) TEXT else GLOBAL
}
