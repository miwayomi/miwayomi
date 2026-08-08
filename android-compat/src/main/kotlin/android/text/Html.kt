package android.text

object Html {

    @JvmStatic
    fun fromHtml(source: String?): Spanned = fromHtml(source, 0)

    @JvmStatic
    fun fromHtml(source: String?, flags: Int): Spanned = SimpleHtml(source ?: "")

    @JvmStatic
    fun escapeHtml(text: CharSequence?): String = text?.toString()?.escapeHtml() ?: ""
}

class SimpleHtml(private val text: String) : Spanned, CharSequence by text {

    init {

    }

    private val cleaned: String by lazy { text.htmlToPlainText() }

    override fun toString(): String = cleaned

    override fun getSpanEnd(tag: Any): Int = length
    override fun getSpanStart(tag: Any): Int = 0
    override fun getSpanFlags(tag: Any): Int = 0
    override fun <T : Any> getSpans(start: Int, end: Int, type: Class<T>): Array<T> = arrayOfNulls<Any>(0) as Array<T>
    override fun nextSpanTransition(start: Int, limit: Int, type: Class<*>?): Int = limit
}

internal fun String.htmlToPlainText(): String {
    var s = replace(Regex("(?is)<br\\s*/?>"), "\n")
        .replace(Regex("(?is)<p[^>]*>"), "\n")
        .replace(Regex("(?is)<li[^>]*>"), "\n• ")
        .replace(Regex("(?is)</(p|div|h[1-6]|li)>"), "\n")
        .replace(Regex("(?is)<[^>]+>"), "")
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&apos;", "'")
    s = s.lines().joinToString("\n") { it.trim() }
    return s.trim()
}

internal fun String.escapeHtml(): String = buildString {
    for (ch in this@escapeHtml) {
        when (ch) {
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            '&' -> append("&amp;")
            '"' -> append("&quot;")
            '\'' -> append("&#39;")
            else -> append(ch)
        }
    }
}
