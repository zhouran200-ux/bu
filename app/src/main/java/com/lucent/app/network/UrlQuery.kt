package com.lucent.app.network

import java.net.URLEncoder

internal fun queryValue(raw: String): String = URLEncoder.encode(raw, "UTF-8")
