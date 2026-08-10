package miwayomi.api

import android.compat.CompatRuntime
import eu.kanade.tachiyomi.network.Favorite
import eu.kanade.tachiyomi.network.SqliteStore
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.serialization.Serializable
import java.io.File

@Serializable
data class FavoriteDto(
    val sourceId: String,
    val url: String,
    val title: String,
    val thumbnailUrl: String? = null,
    val type: String,
    val addedAt: Long,
    val lastReadUrl: String? = null,
    val lastReadName: String? = null,
)

@Serializable
data class FavoriteReqDto(
    val sourceId: String,
    val url: String,
    val title: String,
    val thumbnailUrl: String? = null,
    val type: String = "manga",
)

@Serializable
data class FavoriteResultDto(
    val ok: Boolean,
    val error: String? = null,
    val favorite: FavoriteDto? = null,
)

@Serializable
data class ProgressReqDto(
    val sourceId: String,
    val url: String,
    val lastReadUrl: String? = null,
    val lastReadName: String? = null,
)

fun Favorite.toDto() = FavoriteDto(
    sourceId = sourceId,
    url = url,
    title = title,
    thumbnailUrl = thumbnailUrl,
    type = type,
    addedAt = addedAt,
    lastReadUrl = lastReadUrl,
    lastReadName = lastReadName,
)

fun Application.registerFavoritesApi() {
    val store = SqliteStore(File(CompatRuntime.cacheDir, "miwayomi.db"))

    routing {
        get("/api/v1/favorites") {
            call.respond(store.favoriteAll().map { it.toDto() })
        }

        get("/api/v1/favorites/check") {
            val sourceId = call.request.queryParameters["sourceId"]
            val url = call.request.queryParameters["url"]
            if (sourceId.isNullOrBlank() || url.isNullOrBlank()) {
                return@get call.respond(HttpStatusCode.BadRequest, FavoriteResultDto(false, "sourceId and url are required"))
            }
            call.respond(FavoriteResultDto(ok = store.favoriteGet(sourceId, url) != null))
        }

        post("/api/v1/favorites") {
            val req = runCatching { call.receive<FavoriteReqDto>() }.getOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, FavoriteResultDto(false, "invalid body"))
            if (req.sourceId.isBlank() || req.url.isBlank()) {
                return@post call.respond(HttpStatusCode.BadRequest, FavoriteResultDto(false, "sourceId and url are required"))
            }
            store.favoriteUpsert(
                Favorite(
                    sourceId = req.sourceId,
                    url = req.url,
                    title = req.title,
                    thumbnailUrl = req.thumbnailUrl,
                    type = req.type,
                    addedAt = System.currentTimeMillis(),
                ),
            )
            call.respond(FavoriteResultDto(true, favorite = store.favoriteGet(req.sourceId, req.url)?.toDto()))
        }

        delete("/api/v1/favorites") {
            val sourceId = call.request.queryParameters["sourceId"]
            val url = call.request.queryParameters["url"]
            if (sourceId.isNullOrBlank() || url.isNullOrBlank()) {
                return@delete call.respond(HttpStatusCode.BadRequest, FavoriteResultDto(false, "sourceId and url are required"))
            }
            store.favoriteDelete(sourceId, url)
            call.respond(FavoriteResultDto(true))
        }

        post("/api/v1/favorites/progress") {
            val req = runCatching { call.receive<ProgressReqDto>() }.getOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, FavoriteResultDto(false, "invalid body"))
            store.favoriteSetProgress(req.sourceId, req.url, req.lastReadUrl, req.lastReadName)
            call.respond(FavoriteResultDto(true))
        }
    }
}
