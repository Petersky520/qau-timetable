package cn.edu.qau.timetable.data.kaoyan

import cn.edu.qau.timetable.core.kaoyan.AiConfig
import cn.edu.qau.timetable.core.kaoyan.AiMessage
import cn.edu.qau.timetable.core.kaoyan.AiResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL

/**
 * OpenAI 兼容的 `/chat/completions` 客户端。
 *
 * 三条设计约束，都是踩过坑之后定下来的：
 *
 * 1. **绝不抛异常**。调用点在 ViewModel 的 `safeLaunch` 里，异常抛出去就是闪退；
 *    失败一律变成 [AiResult.Failed]，并且文案是给用户看的中文，不是堆栈。
 * 2. **密钥不进日志**。这个类里没有任何日志调用，报错文案只带 HTTP 状态码和
 *    响应体摘要 —— 请求头里的 `Authorization` 永远不外泄。
 * 3. **阻塞 IO 必须切 IO 线程**。`HttpURLConnection` 是同步的，主线程调用会 ANR。
 */
class KaoyanAiClient {

    /** 阻塞调用，必须在 Dispatchers.IO 里调。失败走 [AiResult.Failed]。 */
    suspend fun ask(config: AiConfig, messages: List<AiMessage>): AiResult {
        if (!config.isReady) {
            return AiResult.Failed("还没有配置 AI 服务。到「设置 → AI 搜索」里填 base URL、密钥和模型名。")
        }
        return withContext(Dispatchers.IO) {
            try {
                call(config, messages)
            } catch (cancel: CancellationException) {
                // 协程被取消不是"调用失败"，不能吞掉 —— 否则 withContext 收不了尾。
                throw cancel
            } catch (timeout: SocketTimeoutException) {
                AiResult.Failed("请求超时，检查网络或调大超时时间")
            } catch (t: Throwable) {
                AiResult.Failed("网络错误：${t.message ?: t.javaClass.simpleName}")
            }
        }
    }

    private fun call(config: AiConfig, messages: List<AiMessage>): AiResult {
        val endpoint = config.baseUrl.trimEnd('/') + "/chat/completions"
        val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = CONNECT_TIMEOUT_MS
            // 配置 0 表示"用默认 60 秒"，不是"无限等"。
            readTimeout = (if (config.timeoutSeconds > 0) config.timeoutSeconds else DEFAULT_READ_TIMEOUT_S) * 1000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            // 只在这里出现一次，之后不再被读取、拼接或打印。
            setRequestProperty("Authorization", "Bearer ${config.apiKey}")
            setRequestProperty("Accept", "application/json")
        }

        return try {
            val body = buildRequestBody(config, messages)
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

            val code = conn.responseCode
            when {
                code in 200..299 -> {
                    val text = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                    parseAnswer(text)
                }
                code == 401 || code == 403 -> AiResult.Failed("密钥无效或没有权限（HTTP $code）")
                code == 429 -> AiResult.Failed("请求太频繁或额度用完了（HTTP 429）")
                else -> AiResult.Failed("服务返回 HTTP $code：${excerpt(conn)}")
            }
        } finally {
            conn.disconnect()
        }
    }

    /**
     * 拼请求体。
     *
     * 手拼而不是用 `org.json`：Android 的 `org.json` 在 JVM 单测里是抛
     * `RuntimeException("Stub!")` 的空壳，用上它这个类就没法在纯 JVM 里做任何断言。
     * 消息里的引号、换行、制表符由 [MiniJson.quote] 转义。
     */
    private fun buildRequestBody(config: AiConfig, messages: List<AiMessage>): String = buildString {
        append('{')
        append("\"model\":").append(MiniJson.quote(config.model))
        append(",\"messages\":[")
        messages.forEachIndexed { index, m ->
            if (index > 0) append(',')
            append("{\"role\":").append(MiniJson.quote(m.role))
            append(",\"content\":").append(MiniJson.quote(m.content))
            append('}')
        }
        append(']')
        append(",\"temperature\":0.3")
        append(",\"stream\":false")
        append('}')
    }

    /** 取 `choices[0].message.content`；格式不对就报"没返回内容"，而不是返回空串。 */
    private fun parseAnswer(raw: String): AiResult {
        val root = MiniJson.parse(raw)
            ?: return AiResult.Failed("服务返回的不是合法 JSON：${preview(raw)}")
        val content = root.optChoiceContent()
            ?: return AiResult.Failed("服务没有返回回答内容：${preview(raw)}")
        if (content.isBlank()) return AiResult.Failed("服务返回了空回答，换个问法试试")
        return AiResult.Ok(content.trim())
    }

    private fun MiniJson.Value.optChoiceContent(): String? {
        val choices = (this as? MiniJson.Value.Obj)?.fields?.get("choices") as? MiniJson.Value.Arr
            ?: return null
        val first = choices.items.firstOrNull() as? MiniJson.Value.Obj ?: return null
        val message = first.fields["message"] as? MiniJson.Value.Obj ?: return null
        return (message.fields["content"] as? MiniJson.Value.Str)?.value
    }

    /** 错误响应体只截前 200 字 —— 有些网关会回一整页 HTML。 */
    private fun excerpt(conn: HttpURLConnection): String {
        val text = try {
            conn.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
        } catch (_: Throwable) {
            ""
        }.trim()
        return preview(text)
    }

    private fun preview(text: String): String {
        val flat = text.replace(Regex("\\s+"), " ").trim()
        return if (flat.length > MAX_EXCERPT) flat.take(MAX_EXCERPT) + "…" else flat
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 15_000
        const val DEFAULT_READ_TIMEOUT_S = 60
        const val MAX_EXCERPT = 200
    }
}
