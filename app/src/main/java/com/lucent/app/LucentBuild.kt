package com.lucent.app

object LucentBuild {

    const val VERSION = "3.1.2"

    const val SECONDARY_SUFFIX = ".secondary"

    val isSecondary: Boolean get() = BuildConfig.APPLICATION_ID.endsWith(SECONDARY_SUFFIX)
}
