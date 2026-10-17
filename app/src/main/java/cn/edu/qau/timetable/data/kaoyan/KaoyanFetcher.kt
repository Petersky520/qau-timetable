package cn.edu.qau.timetable.data.kaoyan

import cn.edu.qau.timetable.core.kaoyan.SchoolItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * 远程院校目录的抓取结果。
 *
 * 和 `AiResult` 一样用 sealed interface：失败必须带一句能直接展示给用户的中文，
 * 不然 UI 只能显示一个不知道哪一步坏掉的「失败」。
 */
sealed interface FetchResult {
    data class Ok(val schools: List<SchoolItem>) : FetchResult
    data class Failed(val message: String) : FetchResult
}

/**
 * 从远端拉一份 JSON 院校目录。
 *
 * 认的格式就是本项目自己导出的 `schools.json`：
 * `{"schools":[{"id":"...","name":"...", ...同 SchoolItem 字段...}]}`。
 * 谁都能拿一份放自己服务器上，改完在设置里填 URL 就能刷新 ——
 * 这样「内置种子数据」和「自己维护的目录」走的是同一条解析链路，
 * 不会出现「内置能读、远端读不了」的分叉。
 */
class KaoyanFetcher {

    suspend fun fetchCatalog(url: String): FetchResult = withContext(Dispatchers.IO) {
        if (url.isBlank()) return@withContext FetchResult.Failed("刷新地址为空")

        val body = try {
            readBody(url.trim())
        } catch (t: Throwable) {
            return@withContext FetchResult.Failed("网络错误：${t.message ?: t.javaClass.simpleName}")
        }

        when (body) {
            is HttpBody.Failed -> FetchResult.Failed(body.message)
            is HttpBody.Ok -> decodeCatalog(body.text)
        }
    }

    /**
     * 把远端返回的文本变成院校列表。
     *
     * 非法 JSON 与「没有条目」是两件事，文案不同 ——
     * 前者说明拉回来的可能是登录页 / 404 页面，用户该检查 URL；
     * 后者说明格式对但目录是空的，用户该检查数据源。
     */
    private fun decodeCatalog(raw: String): FetchResult {
        val schools = catalogOrNull(raw)
            ?: return FetchResult.Failed("返回的不是合法 JSON")
        if (schools.isEmpty()) return FetchResult.Failed("返回的目录里没有院校条目")
        return FetchResult.Ok(schools)
    }

    private fun readBody(url: String): HttpBody {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/json")
        }
        return try {
            val code = conn.responseCode
            if (code !in 200..299) {
                HttpBody.Failed("服务返回 HTTP $code")
            } else {
                val text = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                HttpBody.Ok(text)
            }
        } finally {
            conn.disconnect()
        }
    }

    private sealed interface HttpBody {
        data class Ok(val text: String) : HttpBody
        data class Failed(val message: String) : HttpBody
    }

    private companion object {
        /** 8 秒：刷新目录是用户主动点的，不该让人盯着转圈。 */
        const val TIMEOUT_MS = 8_000
    }
}

/**
 * 解析目录 JSON 成院校条目。
 *
 * 独立成 internal 顶层函数是为了让纯 JVM 单测能直接喂字符串进来，
 * 不必造一个 Android Context 去读 assets。
 *
 * 语义约定：
 *  · 缺字段一律走缺省值（0 / "" / false），不因为少一个 `note` 就整份作废；
 *  · 整段不是合法 JSON → 空列表（调用方据此给「返回的不是合法 JSON」）；
 *  · 合法但没有 `schools` 数组、或数组里全是残条 → 也是空列表。
 */
internal fun parseCatalogJson(raw: String): List<SchoolItem> = catalogOrNull(raw).orEmpty()

/** 非法 JSON 返回 null，合法但没条目返回空列表 —— 只有调用方需要区分这两者。 */
private fun catalogOrNull(raw: String): List<SchoolItem>? {
    val root = MiniJson.parse(raw) ?: return null
    val array = root.optArray("schools") ?: return emptyList()
    return array.mapNotNull { node ->
        val obj = node as? MiniJson.Value.Obj ?: return@mapNotNull null
        val id = obj.optString("id").trim()
        val name = obj.optString("name").trim()
        // 没有 id 或院校名，这条在任何界面里都没法展示，也没法当主键。
        if (id.isEmpty() || name.isEmpty()) return@mapNotNull null
        SchoolItem(
            id = id,
            name = name,
            province = obj.optString("province"),
            city = obj.optString("city"),
            tier = obj.optString("tier", "REGULAR"),
            majorCode = obj.optString("majorCode"),
            majorName = obj.optString("majorName"),
            scoreLast = obj.optInt("scoreLast"),
            scorePrev = obj.optInt("scorePrev"),
            quota = obj.optInt("quota"),
            applicants = obj.optInt("applicants"),
            mathRequired = obj.optBoolean("mathRequired"),
            tags = obj.optString("tags"),
            note = obj.optString("note"),
        )
    }
}

private fun MiniJson.Value.optString(key: String, def: String = ""): String =
    ((this as? MiniJson.Value.Obj)?.fields?.get(key) as? MiniJson.Value.Str)?.value ?: def

private fun MiniJson.Value.optInt(key: String, def: Int = 0): Int = when (val v = opt(key)) {
    is MiniJson.Value.Num -> v.value.toInt()
    is MiniJson.Value.Str -> v.value.trim().toIntOrNull() ?: def
    else -> def
}

private fun MiniJson.Value.optBoolean(key: String, def: Boolean = false): Boolean = when (val v = opt(key)) {
    is MiniJson.Value.Bool -> v.value
    is MiniJson.Value.Str -> v.value.equals("true", ignoreCase = true)
    is MiniJson.Value.Num -> v.value != 0.0
    else -> def
}

private fun MiniJson.Value.optArray(key: String): List<MiniJson.Value>? =
    (opt(key) as? MiniJson.Value.Arr)?.items

private fun MiniJson.Value.opt(key: String): MiniJson.Value? =
    (this as? MiniJson.Value.Obj)?.fields?.get(key)

/**
 * 一个只够本项目用的小 JSON 解析器。
 *
 * 为什么不用 `org.json`：Android 的 `org.json` 在纯 JVM 单测里是个方法体
 * 直接抛 `RuntimeException("Stub!")` 的空壳，数据层一旦依赖它，§7 要求的
 * `SchoolSeedTest`（测 `parseCatalogJson`）就只能靠 Robolectric 或额外依赖。
 * 这里手写一份递归下降解析器，换来「解析逻辑在 JVM 上就能测」，且不引入新依赖。
 */
internal object MiniJson {

    sealed interface Value {
        data class Obj(val fields: Map<String, Value>) : Value
        data class Arr(val items: List<Value>) : Value
        data class Str(val value: String) : Value
        data class Num(val value: Double) : Value
        data class Bool(val value: Boolean) : Value
        data object Null : Value
    }

    /** 不是合法 JSON（或后面拖着垃圾）时返回 null，调用方决定怎么报。 */
    fun parse(raw: String): Value? = try {
        val p = Parser(raw)
        p.skipWs()
        val v = p.readValue()
        p.skipWs()
        if (p.pos == raw.length) v else null
    } catch (_: Throwable) {
        null
    }

    /** 拼 JSON 字符串字面量（含两侧引号），写请求体时用。 */
    fun quote(s: String): String = buildString(s.length + 2) {
        append('"')
        for (c in s) {
            when (c) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                else -> if (c < ' ') append("\\u%04x".format(c.code)) else append(c)
            }
        }
        append('"')
    }

    private class Parser(private val s: String) {
        var pos = 0

        fun skipWs() {
            while (pos < s.length && s[pos].isJsonSpace()) pos++
        }

        fun readValue(): Value {
            skipWs()
            if (pos >= s.length) throw IllegalStateException("意外的结尾")
            return when (val c = s[pos]) {
                '{' -> readObject()
                '[' -> readArray()
                '"' -> Value.Str(readString())
                't' -> readLiteral("true", Value.Bool(true))
                'f' -> readLiteral("false", Value.Bool(false))
                'n' -> readLiteral("null", Value.Null)
                else ->
                    if (c == '-' || c.isDigit()) readNumber() else throw IllegalStateException("非法字符 $c")
            }
        }

        private fun readLiteral(text: String, value: Value): Value {
            if (!s.startsWith(text, pos)) throw IllegalStateException("非法字面量")
            pos += text.length
            return value
        }

        private fun readObject(): Value.Obj {
            pos++ // {
            val map = LinkedHashMap<String, Value>()
            skipWs()
            if (pos < s.length && s[pos] == '}') {
                pos++
                return Value.Obj(map)
            }
            while (true) {
                skipWs()
                if (pos >= s.length || s[pos] != '"') throw IllegalStateException("期待键名")
                val key = readString()
                skipWs()
                if (pos >= s.length || s[pos] != ':') throw IllegalStateException("期待冒号")
                pos++
                map[key] = readValue()
                skipWs()
                if (pos >= s.length) throw IllegalStateException("对象未闭合")
                when (s[pos]) {
                    ',' -> pos++
                    '}' -> {
                        pos++
                        return Value.Obj(map)
                    }
                    else -> throw IllegalStateException("期待逗号或右大括号")
                }
            }
        }

        private fun readArray(): Value.Arr {
            pos++ // [
            val items = ArrayList<Value>()
            skipWs()
            if (pos < s.length && s[pos] == ']') {
                pos++
                return Value.Arr(items)
            }
            while (true) {
                items += readValue()
                skipWs()
                if (pos >= s.length) throw IllegalStateException("数组未闭合")
                when (s[pos]) {
                    ',' -> pos++
                    ']' -> {
                        pos++
                        return Value.Arr(items)
                    }
                    else -> throw IllegalStateException("期待逗号或右方括号")
                }
            }
        }

        private fun readString(): String {
            pos++ // "
            val sb = StringBuilder()
            while (true) {
                if (pos >= s.length) throw IllegalStateException("字符串未闭合")
                when (val c = s[pos]) {
                    '"' -> {
                        pos++
                        return sb.toString()
                    }
                    '\\' -> {
                        pos++
                        if (pos >= s.length) throw IllegalStateException("转义未闭合")
                        when (val e = s[pos]) {
                            '"' -> sb.append('"')
                            '\\' -> sb.append('\\')
                            '/' -> sb.append('/')
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'u' -> {
                                if (pos + 4 >= s.length) throw IllegalStateException("转义越界")
                                val hex = s.substring(pos + 1, pos + 5)
                                sb.append(hex.toInt(16).toChar())
                                pos += 4
                            }
                            else -> throw IllegalStateException("非法转义 $e")
                        }
                        pos++
                    }
                    else -> {
                        sb.append(c)
                        pos++
                    }
                }
            }
        }

        private fun readNumber(): Value.Num {
            val start = pos
            if (pos < s.length && s[pos] == '-') pos++
            while (pos < s.length && (s[pos].isDigit() || s[pos] == '.' || s[pos] == 'e' || s[pos] == 'E' ||
                    s[pos] == '+' || s[pos] == '-')
            ) {
                pos++
            }
            val text = s.substring(start, pos)
            val d = text.toDoubleOrNull() ?: throw IllegalStateException("非法数字 $text")
            return Value.Num(d)
        }

        private fun Char.isJsonSpace(): Boolean =
            this == ' ' || this == '\n' || this == '\r' || this == '\t'
    }
}
