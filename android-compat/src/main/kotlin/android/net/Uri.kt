package android.net

import java.net.URI
import java.nio.charset.StandardCharsets

class Uri private constructor(
    private val scheme: String?,
    private val ssp: String?,
    private val path: String?,
    private val query: String?,
    private val fragment: String?,
    private val rawString: String,
) {

    fun isAbsolute(): Boolean = scheme != null

    fun getScheme(): String? = scheme

    fun getSchemeSpecificPart(): String? = ssp

    fun getAuthority(): String? {
        val s = ssp ?: return null
        val end = s.indexOfFirst { it == '/' || it == '?' }
        val endIdx = if (end == -1) s.length else end
        return s.substring(0, endIdx)
    }

    fun getHost(): String? {
        val a = getAuthority() ?: return null
        var h = a
        val at = h.lastIndexOf('@')
        if (at != -1) h = h.substring(at + 1)
        val colon = h.indexOf(':')
        if (colon != -1) h = h.substring(0, colon)
        return h.ifEmpty { null }
    }

    fun getPort(): Int? {
        val a = getAuthority() ?: return null
        val at = a.lastIndexOf('@')
        var h = if (at != -1) a.substring(at + 1) else a
        val colon = h.lastIndexOf(':')
        if (colon == -1 || colon == h.length - 1) return null
        val portStr = h.substring(colon + 1)
        return portStr.toIntOrNull()
    }

    fun getUserInfo(): String? {
        val a = getAuthority() ?: return null
        val at = a.lastIndexOf('@')
        return if (at != -1) a.substring(0, at) else null
    }

    fun getPath(): String? = path

    fun getLastPathSegment(): String? {
        val p = path ?: return null
        if (p.isEmpty() || p == "/") return null
        val trimmed = p.removeSuffix("/")
        return trimmed.substringAfterLast('/')
    }

    fun getQuery(): String? = query

    fun getFragment(): String? = fragment

    fun getQueryParameter(key: String): String? {
        val q = query ?: return null
        for (pair in q.split("&")) {
            val idx = pair.indexOf('=')
            val k = if (idx == -1) pair else pair.substring(0, idx)
            if (k == key) {
                val v = if (idx == -1) "" else pair.substring(idx + 1)
                return Uri.decode(v)
            }
        }
        return null
    }

    fun getQueryParameterNames(): Set<String> {
        val q = query ?: return emptySet()
        return q.split("&").mapNotNull { p ->
            val idx = p.indexOf('=')
            if (idx == -1) p else p.substring(0, idx)
        }.toSet()
    }

    fun getQueryParameters(key: String): List<String> {
        val q = query ?: return emptyList()
        return q.split("&").mapNotNull { p ->
            val idx = p.indexOf('=')
            val k = if (idx == -1) p else p.substring(0, idx)
            if (k == key) {
                val v = if (idx == -1) "" else p.substring(idx + 1)
                Uri.decode(v)
            } else {
                null
            }
        }
    }

    fun buildUpon(): Builder = Builder().also {
        it.scheme = scheme
        it.ssp = ssp
        it.path = path
        it.query = query
        it.fragment = fragment
    }

    fun withQueryParameter(key: String, value: String?): Uri =
        buildUpon().appendQueryParameter(key, value).build()

    fun toStringEncoded(): String = rawString

    override fun toString(): String = rawString

    override fun equals(other: Any?): Boolean = other is Uri && other.toString() == toString()

    override fun hashCode(): Int = rawString.hashCode()

    class Builder {
        var scheme: String? = null
        var ssp: String? = null
        var path: String? = null
        var query: String? = null
        var fragment: String? = null
        private val queryPairs = mutableListOf<Pair<String, String?>>()

        fun scheme(s: String?): Builder = apply { scheme = s }
        fun schemeSpecificPart(s: String?): Builder = apply { ssp = s }
        fun path(p: String?): Builder = apply { path = p }
        fun appendPath(p: String?): Builder = apply {
            path = if (path.isNullOrEmpty()) p else {
                val left = path!!.trimEnd('/')
                val right = p?.trimStart('/')
                if (left.isEmpty()) "/$right" else "$left/$right"
            }
        }
        fun appendQueryParameter(k: String, v: String?): Builder = apply {
            queryPairs.add(k to v)
            query = queryPairs.joinToString("&") { (key, value) ->
                val encK = Uri.encode(key)
                if (value == null) encK else "$encK=${Uri.encode(value)}"
            }
        }
        fun query(q: String?): Builder = apply { query = q }
        fun fragment(f: String?): Builder = apply { fragment = f }
        fun build(): Uri {
            val sb = StringBuilder()
            if (scheme != null) {
                sb.append(scheme).append(':')
                if (ssp != null) {
                    sb.append(ssp)
                } else {
                    if (path != null) {
                        sb.append("//").append(path!!.trimStart('/'))
                    }
                    if (query != null) sb.append('?').append(query)
                }
            } else {
                if (path != null) sb.append(path)
                if (query != null) sb.append('?').append(query)
            }
            if (fragment != null) sb.append('#').append(fragment)
            return Uri(scheme, ssp, path, query, fragment, sb.toString())
        }
    }

    companion object {
        @JvmStatic
        fun parse(string: String): Uri {
            return try {
                val uri = URI(string)
                val path = uri.rawPath
                Uri(
                    scheme = uri.scheme,
                    ssp = if (uri.scheme != null) string.substringAfter(':') else null,
                    path = path,
                    query = uri.rawQuery,
                    fragment = uri.rawFragment,
                    rawString = string,
                )
            } catch (_: Exception) {

                val qIdx = string.indexOf('?')
                val fIdx = string.indexOf('#')
                val frag = if (fIdx != -1) string.substring(fIdx + 1) else null
                val beforeFrag = if (fIdx != -1) string.substring(0, fIdx) else string
                val queryPart = if (qIdx != -1) beforeFrag.substring(qIdx + 1) else null
                val pathPart = if (qIdx != -1) beforeFrag.substring(0, qIdx) else beforeFrag
                Uri(null, null, pathPart, queryPart, frag, string)
            }
        }

        @JvmStatic
        fun fromParts(scheme: String, ssp: String, fragment: String?): Uri {
            return Uri(scheme, ssp, null, null, fragment, "$scheme:$ssp${fragment?.let { "#$it" } ?: ""}")
        }

        @JvmStatic
        fun fromEncoded(uriString: String): Uri = parse(uriString)

        @JvmStatic
        fun encode(s: String, allow: String = "!#$&'()*+,/:;=?@[]~.-_"): String {
            val sb = StringBuilder()
            for (ch in s) {
                if (ch.isLetterOrDigit() || ch in allow) sb.append(ch) else {
                    val bytes = ch.toString().toByteArray(StandardCharsets.UTF_8)
                    for (b in bytes) sb.append('%').append("%02X".format(b))
                }
            }
            return sb.toString()
        }

        @JvmStatic
        fun decode(s: String): String {

            return java.net.URLDecoder.decode(s.replace("+", "%2B"), "UTF-8")
        }
    }
}
