package com.lucent.app.network

enum class ConnectionProbeKind { Generation, ModelCatalog, Endpoint }

internal fun configurationProbeMessage(kind: ConnectionProbeKind, success: Boolean, code: Int, millis: Long): String {
    val subject = if (kind == ConnectionProbeKind.Endpoint) "地址探测" else "模型列表探测"
    return when {
        code == 0 -> "$subject 未完成：请检查网络、地址或响应体积限制；未验证模型调用。"
        success -> "$subject 收到成功响应（HTTP $code，${millis}ms）；未验证模型调用。"
        code == 401 || code == 403 -> "$subject 收到鉴权拒绝（HTTP $code）；请核对密钥与权限。"
        kind == ConnectionProbeKind.Endpoint && code in listOf(404, 405, 501) ->
            "服务器已响应（HTTP $code），但不支持或未找到 HEAD 探测；不能据此判定生成接口无效。请在对话中验证模型调用。"
        else -> "$subject 收到 HTTP $code；此结果不代表生成接口一定不可用，请查看详情。"
    }
}
