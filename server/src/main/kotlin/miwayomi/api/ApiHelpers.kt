package miwayomi.api

import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.source.CatalogueSource
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytesWriter
import io.ktor.utils.io.writeFully
import miwayomi.source.AnimeSourceManager
import miwayomi.source.MangaSourceManager

internal fun ApplicationCall.sourceId(): Long? =
    parameters["sourceId"]?.toLongOrNull()

internal fun ApplicationCall.requiredUrl(): String? =
    request.queryParameters["url"]?.takeIf { it.isNotBlank() }

internal fun Long?.manga(manager: MangaSourceManager): CatalogueSource? =
    this?.let { manager.get(it) as? CatalogueSource }

internal fun Long?.anime(manager: AnimeSourceManager): AnimeCatalogueSource? =
    this?.let { manager.get(it) as? AnimeCatalogueSource }

internal suspend fun ApplicationCall.respondNotFound() =
    respond(HttpStatusCode.NotFound, ErrorDto("Fuente no encontrada"))

internal suspend fun ApplicationCall.respondMissingUrl() =
    respond(HttpStatusCode.BadRequest, ErrorDto("url requerido"))

internal suspend fun ApplicationCall.proxyStream(
    upstream: okhttp3.Response,
    contentType: String,
    status: HttpStatusCode,
) {
    val body = upstream.body
    if (body == null) {
        upstream.close()
        respond(status, ErrorDto("sin cuerpo"))
        return
    }
    upstream.header("Content-Range")?.let { response.headers.append(HttpHeaders.ContentRange, it) }
    upstream.header("Accept-Ranges")?.let { response.headers.append(HttpHeaders.AcceptRanges, it) }
    upstream.header("Content-Length")?.let { response.headers.append(HttpHeaders.ContentLength, it) }
    try {
        respondBytesWriter(ContentType.parse(contentType), status) {
            val input = body.byteStream()
            try {
                val buf = ByteArray(64 * 1024)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    writeFully(buf, 0, n)
                }
            } finally {
                runCatching { input.close() }
            }
        }
    } finally {
        upstream.close()
    }
}
