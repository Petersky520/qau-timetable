package cn.edu.qau.timetable.core.kaoyan

/**
 * 用户自备的 AI 服务配置（OpenAI 兼容协议）。
 *
 * 刻意保持成纯 Kotlin data class —— 它被 `SettingsStore` 读写、被单测直接构造，
 * 一旦掺进 Android 类型，纯 JVM 单测就再也跑不起来。
 *
 * 三个字段全部留空时 [isReady] 为 false，UI 据此显示「去设置」的引导，
 * 而不是让用户点一下提问再等一个 401。
 */
data class AiConfig(
    /** OpenAI 兼容的 base URL，如 https://api.deepseek.com/v1 */
    val baseUrl: String = "",
    val apiKey: String = "",
    val model: String = "deepseek-chat",
    /** 0 表示用默认 60 秒。 */
    val timeoutSeconds: Int = 60,
) {
    val isReady: Boolean get() = baseUrl.isNotBlank() && apiKey.isNotBlank() && model.isNotBlank()

    /**
     * 打码后的描述。
     *
     * data class 自动生成的 `toString()` 会把 apiKey 原样打出来，而这个对象
     * 迟早会被哪个日志或崩溃上报顺手拼进去。密钥只该存在于内存和请求头里。
     */
    override fun toString(): String =
        "AiConfig(baseUrl='$baseUrl', apiKey=${if (apiKey.isBlank()) "''" else "'***'"}, " +
            "model='$model', timeoutSeconds=$timeoutSeconds)"
}

/** 一条对话消息。role 只取 "system" | "user" | "assistant"。 */
data class AiMessage(val role: String, val content: String)

/**
 * AI 调用的结果。
 *
 * 用 sealed interface 而不是 `String?`：失败必须带一条**给用户看的中文原因**，
 * 调用方没机会把 null 当成「没回答」敷衍过去。
 */
sealed interface AiResult {
    data class Ok(val text: String) : AiResult
    data class Failed(val message: String) : AiResult
}
