package cn.edu.qau.timetable.data.qz

import org.json.JSONObject

/** JS 侧返回的 JSON 契约。 */
data class QzPayload(
    val ok: Boolean,
    val kind: String = "",
    val url: String = "",
    val headers: List<String> = emptyList(),
    val rows: List<List<String>> = emptyList(),
    /** 与 [rows] 同形；锚点处为该格纵向跨的行数（占几小节），其余为 0。 */
    val rowspans: List<List<Int>> = emptyList(),
    val error: String = "",
)

object QzJson {

    fun parse(raw: String?): QzPayload {
        if (raw.isNullOrBlank()) {
            return QzPayload(ok = false, error = "抓取结果为空（页面可能还没加载完，或登录已失效）")
        }
        return try {
            val o = JSONObject(raw)
            val ok = o.optBoolean("ok", false)
            val headersArr = o.optJSONArray("headers")
            val headers = ArrayList<String>()
            if (headersArr != null) {
                for (i in 0 until headersArr.length()) headers += headersArr.optString(i, "")
            }
            val rowsArr = o.optJSONArray("rows")
            val rows = ArrayList<List<String>>()
            if (rowsArr != null) {
                for (i in 0 until rowsArr.length()) {
                    val r = rowsArr.optJSONArray(i) ?: continue
                    val line = ArrayList<String>(r.length())
                    for (j in 0 until r.length()) line += r.optString(j, "")
                    rows += line
                }
            }

            val spanArr = o.optJSONArray("rowspans")
            val spans = ArrayList<List<Int>>()
            if (spanArr != null) {
                for (i in 0 until spanArr.length()) {
                    val r = spanArr.optJSONArray(i) ?: continue
                    val line = ArrayList<Int>(r.length())
                    for (j in 0 until r.length()) line += r.optInt(j, 0)
                    spans += line
                }
            }

            QzPayload(
                ok = ok,
                kind = o.optString("kind", ""),
                url = o.optString("url", ""),
                headers = headers,
                rows = rows,
                rowspans = spans,
                error = o.optString("error", ""),
            )
        } catch (t: Throwable) {
            QzPayload(ok = false, error = "解析抓取结果失败：${t.message}")
        }
    }
}
