package miwayomi.api

import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.source.online.HttpSource
import miwayomi.source.AnimeSourceManager
import miwayomi.source.MangaSourceManager
import okhttp3.Headers
import okhttp3.OkHttpClient
import java.net.URI
import java.net.URLDecoder

fun parseHeadersParam(raw: String?): Map<String, String> {
    if (raw.isNullOrBlank()) return emptyMap()
    return raw.lineSequence()
        .mapNotNull { line ->
            val idx = line.indexOf(':')
            if (idx <= 0) null else line.substring(0, idx).trim() to line.substring(idx + 1).trim()
        }
        .toMap()
}

fun buildClientAndHeaders(
    sourceId: Long?,
    custom: Map<String, String>,
    mangaSources: MangaSourceManager,
    animeSources: AnimeSourceManager,
    network: NetworkHelper,
): Pair<OkHttpClient, Headers> {
    val client: OkHttpClient
    val baseHeaders: Headers
    if (sourceId != null) {
        val src = mangaSources.get(sourceId) ?: animeSources.get(sourceId)
        when (src) {
            is HttpSource -> {
                client = src.client
                baseHeaders = src.headers
            }
            is AnimeHttpSource -> {
                client = src.client
                baseHeaders = src.headers
            }
            else -> {
                client = network.client
                baseHeaders = Headers.Builder().build()
            }
        }
    } else {
        client = network.client
        baseHeaders = Headers.Builder().build()
    }
    val headers = Headers.Builder().apply {
        baseHeaders.forEach { (k, v) -> add(k, v) }
        custom.forEach { (k, v) -> set(k, v) }
    }.build()
    return client to headers
}

fun normalizeVideoUrl(url: String?): String? {
    if (url == null) return null
    return try {
        val uri = URI(url)
        if (uri.host == "localhost" && url.contains("url=")) {
            val query = uri.query ?: return url
            query.split("&")
                .firstOrNull { it.startsWith("url=") }
                ?.removePrefix("url=")
                ?.let { URLDecoder.decode(it, "UTF-8") }
                ?: url
        } else {
            url
        }
    } catch (e: Exception) {
        url
    }
}
