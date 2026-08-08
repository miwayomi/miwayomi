package miwayomi.api

import java.net.URI
import java.net.URLEncoder

fun proxyBase(scheme: String, host: String, port: Int): String {
    val portStr = if (port == 80 || port == 443 || port < 0) "" else ":$port"
    return "$scheme://$host$portStr"
}

internal fun resolve(base: String, uri: String): String {
    if (uri.startsWith("http://") || uri.startsWith("https://")) return uri
    return try {
        val b = URI(base)
        if (uri.startsWith("/")) {
            URI(b.scheme, b.authority, uri, null, null).toString()
        } else {
            URI(base.substringBeforeLast('/') + "/" + uri).normalize().toString()
        }
    } catch (e: Exception) {
        base + uri
    }
}

fun rewritePlaylist(
    text: String,
    sourceId: Long?,
    currentUrl: String,
    headersParam: String,
    proxyBase: String,
): String {
    return text.lineSequence().joinToString("\n") { line ->
        val trimmed = line.trim()
        when {

            trimmed.isNotEmpty() && !trimmed.startsWith("#") -> {
                proxyUrlFor(resolve(currentUrl, trimmed), sourceId, headersParam, proxyBase)
            }

            else -> {
                val match = Regex("""URI="([^"]*)"""").find(line)
                if (match != null) {
                    val uri = match.groupValues[1]
                    val newUri = proxyUrlFor(resolve(currentUrl, uri), sourceId, headersParam, proxyBase)
                    line.replace("URI=\"$uri\"", "URI=\"$newUri\"")
                } else {
                    line
                }
            }
        }
    }
}

private fun proxyUrlFor(url: String, sourceId: Long?, headersParam: String, proxyBase: String): String {
    val sb = StringBuilder("$proxyBase/api/v1/hls?url=")
        .append(URLEncoder.encode(url, "UTF-8"))
    if (sourceId != null) sb.append("&sourceId=").append(sourceId)
    if (headersParam.isNotBlank()) sb.append("&headers=").append(URLEncoder.encode(headersParam, "UTF-8"))
    return sb.toString()
}

fun rewriteDashManifest(
    text: String,
    sourceId: Long?,
    currentUrl: String,
    headersParam: String,
    proxyBase: String,
): String {
    val baseDir = currentUrl.substringBeforeLast('/') + "/"

    return text.replace(Regex("""(media|initialization)="([^"]*)"""")) { m ->
        val attr = m.groupValues[1]
        val raw = m.groupValues[2]
        if (raw.startsWith("http://") || raw.startsWith("https://")) {
            m.value
        } else {
            val proxied = dashSegUrl(baseDir, raw, sourceId, headersParam, proxyBase)
            """$attr="$proxied""""
        }
    }
}

private fun dashSegUrl(
    baseDir: String,
    rel: String,
    sourceId: Long?,
    headersParam: String,
    proxyBase: String,
): String {
    val sb = StringBuilder("$proxyBase/api/v1/dashseg?base=")
        .append(URLEncoder.encode(baseDir, "UTF-8"))
        .append("&rel=")
        .append(rel)
    if (sourceId != null) sb.append("&sourceId=").append(sourceId)
    if (headersParam.isNotBlank()) sb.append("&headers=").append(URLEncoder.encode(headersParam, "UTF-8"))
    return sb.toString()
}
