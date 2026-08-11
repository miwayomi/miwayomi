package miwayomi.api

import android.compat.CompatRuntime
import eu.kanade.tachiyomi.network.SqliteStore
import eu.kanade.tachiyomi.network.WatchEntry
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
data class WatchEntryDto(
    val sourceId: String,
    val animeUrl: String,
    val epUrl: String,
    val animeTitle: String,
    val epName: String,
    val thumb: String? = null,
    val timeSeconds: Double = 0.0,
    val durationSeconds: Double = 0.0,
    val updatedAt: Long,
    val completed: Boolean = false,
    val episodeNumber: Int? = null,
)

@Serializable
data class WatchReqDto(
    val sourceId: String,
    val animeUrl: String,
    val epUrl: String,
    val animeTitle: String = "",
    val epName: String = "",
    val thumb: String? = null,
    val timeSeconds: Double = 0.0,
    val durationSeconds: Double = 0.0,
    val completed: Boolean = false,
    val episodeNumber: Int? = null,
)

@Serializable
data class WatchResultDto(val ok: Boolean, val error: String? = null)

fun WatchEntry.toDto() = WatchEntryDto(
    sourceId = sourceId,
    animeUrl = animeUrl,
    epUrl = epUrl,
    animeTitle = animeTitle,
    epName = epName,
    thumb = thumb,
    timeSeconds = timeSeconds,
    durationSeconds = durationSeconds,
    updatedAt = updatedAt,
    completed = completed,
    episodeNumber = episodeNumber,
)

fun Application.registerWatchApi() {
    val store = SqliteStore(File(CompatRuntime.cacheDir, "miwayomi.db"))

    routing {
        get("/api/v1/watch") {
            call.respond(store.watchAll().map { it.toDto() })
        }

        post("/api/v1/watch") {
            val req = runCatching { call.receive<WatchReqDto>() }.getOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, WatchResultDto(false, "invalid body"))
            if (req.sourceId.isBlank() || req.animeUrl.isBlank() || req.epUrl.isBlank()) {
                return@post call.respond(HttpStatusCode.BadRequest, WatchResultDto(false, "sourceId, animeUrl and epUrl are required"))
            }
            store.watchUpsert(
                WatchEntry(
                    sourceId = req.sourceId,
                    animeUrl = req.animeUrl,
                    epUrl = req.epUrl,
                    animeTitle = req.animeTitle,
                    epName = req.epName,
                    thumb = req.thumb,
                    timeSeconds = req.timeSeconds,
                    durationSeconds = req.durationSeconds,
                    updatedAt = System.currentTimeMillis(),
                    completed = req.completed,
                    episodeNumber = req.episodeNumber,
                ),
            )
            call.respond(WatchResultDto(true))
        }

        delete("/api/v1/watch") {
            val sourceId = call.request.queryParameters["sourceId"]
            val animeUrl = call.request.queryParameters["animeUrl"]
            val epUrl = call.request.queryParameters["epUrl"]
            if (sourceId.isNullOrBlank() || animeUrl.isNullOrBlank() || epUrl.isNullOrBlank()) {
                return@delete call.respond(HttpStatusCode.BadRequest, WatchResultDto(false, "sourceId, animeUrl and epUrl are required"))
            }
            store.watchDelete(sourceId, animeUrl, epUrl)
            call.respond(WatchResultDto(true))
        }
    }
}
