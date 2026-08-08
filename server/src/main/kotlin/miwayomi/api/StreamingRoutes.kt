package miwayomi.api

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytesWriter
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.utils.io.writeFully
import miwayomi.source.AnimeSourceManager
import miwayomi.source.MangaSourceManager
import okhttp3.CacheControl
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

private const val MAX_CACHED_IMAGE_BYTES = 20L * 1024 * 1024

fun Application.registerStreamingApi() {
    val mangaSources = Injekt.get<MangaSourceManager>()
    val animeSources = Injekt.get<AnimeSourceManager>()
    val network = Injekt.get<NetworkHelper>()

    routing {

        get("/api/v1/proxy") {
            val url = call.request.queryParameters["url"]
                ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorDto("url requerido"))
            val sourceId = call.request.queryParameters["sourceId"]?.toLongOrNull()
            val custom = parseHeadersParam(call.request.queryParameters["headers"])
            val headersParam = call.request.queryParameters["headers"] ?: ""

            val (client, headers) = buildClientAndHeaders(sourceId, custom, mangaSources, animeSources, network)

            val range = call.request.headers["Range"]

            val cached = ImageCache.cachedFile(url, headersParam)
            if (cached != null && range == null) {
                val cType = inferContentType(url, null)
                call.response.headers.append(HttpHeaders.CacheControl, "public, max-age=31536000, immutable")
                call.respondBytesWriter(ContentType.parse(cType), HttpStatusCode.OK) {
                    writeFully(cached.readBytes())
                }
                return@get
            }

            val request = if (range != null) {
                GET(url, headers, CacheControl.FORCE_NETWORK).newBuilder().header("Range", range).build()
            } else {
                GET(url, headers, CacheControl.FORCE_NETWORK)
            }

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                val code = response.code
                response.close()
                return@get call.respond(HttpStatusCode.fromValue(code), ErrorDto("proxy error $code"))
            }
            val contentType = inferContentType(url, response.header("Content-Type"))

            if (range == null && contentType.startsWith("image/")) {
                val len = response.header("Content-Length")?.toLongOrNull() ?: -1L
                if (len in 1..MAX_CACHED_IMAGE_BYTES) {
                    val bytes = response.body?.bytes()
                    response.close()
                    if (bytes != null) {
                        ImageCache.cacheImage(url, headersParam, bytes)
                        call.response.headers.append(HttpHeaders.CacheControl, "public, max-age=31536000, immutable")
                        call.respondBytesWriter(ContentType.parse(contentType), HttpStatusCode.OK) {
                            writeFully(bytes)
                        }
                        return@get
                    }
                }
            }

            val status = if (response.code == 206) HttpStatusCode.PartialContent else HttpStatusCode.OK
            call.proxyStream(response, contentType, status)
        }

        get("/api/v1/hls") {
            val url = call.request.queryParameters["url"]
                ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorDto("url requerido"))
            val sourceId = call.request.queryParameters["sourceId"]?.toLongOrNull()
            val headersParam = call.request.queryParameters["headers"].orEmpty()
            val custom = parseHeadersParam(headersParam)
            val (client, headers) = buildClientAndHeaders(sourceId, custom, mangaSources, animeSources, network)

            val range = call.request.headers["Range"]
            val request = if (range != null) {
                GET(url, headers, CacheControl.FORCE_NETWORK).newBuilder().header("Range", range).build()
            } else {
                GET(url, headers, CacheControl.FORCE_NETWORK)
            }

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                val code = response.code
                response.close()
                return@get call.respond(HttpStatusCode.fromValue(code), ErrorDto("hls error $code"))
            }
            val contentType = response.header("Content-Type") ?: "application/octet-stream"
            val isPlaylist = contentType.contains("mpegurl") ||
                contentType.contains("mpeg-url") ||
                url.contains(".m3u8")

            if (isPlaylist) {
                val text = response.body.string()
                response.close()
                val local = call.request.local
                val base = proxyBase(local.scheme, local.localHost, local.localPort)
                val rewritten = rewritePlaylist(text, sourceId, url, headersParam, base)
                call.respondText(
                    text = rewritten,
                    contentType = ContentType.parse("application/vnd.apple.mpegurl"),
                )
            } else {
                val status = if (response.code == 206) HttpStatusCode.PartialContent else HttpStatusCode.OK
                call.proxyStream(response, inferContentType(url, contentType), status)
            }
        }

        get("/api/v1/dash") {
            val url = call.request.queryParameters["url"]
                ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorDto("url requerido"))
            val sourceId = call.request.queryParameters["sourceId"]?.toLongOrNull()
            val headersParam = call.request.queryParameters["headers"].orEmpty()
            val custom = parseHeadersParam(headersParam)
            val (client, headers) = buildClientAndHeaders(sourceId, custom, mangaSources, animeSources, network)

            val response = client.newCall(GET(url, headers, CacheControl.FORCE_NETWORK)).execute()
            if (!response.isSuccessful) {
                val code = response.code
                response.close()
                return@get call.respond(HttpStatusCode.fromValue(code), ErrorDto("dash error $code"))
            }
            val contentType = response.header("Content-Type") ?: "application/octet-stream"
            val isDash = contentType.contains("dash") || url.contains(".mpd")
            if (isDash) {
                val text = response.body.string()
                response.close()
                val local = call.request.local
                val base = proxyBase(local.scheme, local.localHost, local.localPort)
                val rewritten = rewriteDashManifest(text, sourceId, url, headersParam, base)
                call.respondText(
                    text = rewritten,
                    contentType = ContentType.parse("application/dash+xml; charset=utf-8"),
                )
            } else {
                call.proxyStream(response, inferContentType(url, contentType), HttpStatusCode.OK)
            }
        }

        get("/api/v1/dashseg") {
            val base = call.request.queryParameters["base"]
                ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorDto("base requerido"))
            val rel = call.request.queryParameters["rel"]
                ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorDto("rel requerido"))
            val sourceId = call.request.queryParameters["sourceId"]?.toLongOrNull()
            val custom = parseHeadersParam(call.request.queryParameters["headers"])
            val (client, headers) = buildClientAndHeaders(sourceId, custom, mangaSources, animeSources, network)
            val url = resolve(base, rel)

            val range = call.request.headers["Range"]
            val request = if (range != null) {
                GET(url, headers, CacheControl.FORCE_NETWORK).newBuilder().header("Range", range).build()
            } else {
                GET(url, headers, CacheControl.FORCE_NETWORK)
            }
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                val code = response.code
                response.close()
                return@get call.respond(HttpStatusCode.fromValue(code), ErrorDto("dashseg error $code"))
            }
            val status = if (response.code == 206) HttpStatusCode.PartialContent else HttpStatusCode.OK
            call.proxyStream(response, inferContentType(url, response.header("Content-Type")), status)
        }
    }
}
