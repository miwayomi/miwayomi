package miwayomi.api

import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.Hoster
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import miwayomi.source.AnimeSourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

fun Application.registerAnimeApi() {
    val animeSources = Injekt.get<AnimeSourceManager>()

    routing {
        get("/api/v1/anime/{sourceId}/popular") {
            val source = call.sourceId().anime(animeSources) ?: return@get call.respondNotFound()
            val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 1
            val res = source.getPopularAnime(page)
            call.respond(AnimesPageDto(res.hasNextPage, res.animes.map { it.toDto() }))
        }

        get("/api/v1/anime/{sourceId}/latest") {
            val source = call.sourceId().anime(animeSources) ?: return@get call.respondNotFound()
            val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 1
            val res = source.getLatestUpdates(page)
            call.respond(AnimesPageDto(res.hasNextPage, res.animes.map { it.toDto() }))
        }

        get("/api/v1/anime/{sourceId}/search") {
            val source = call.sourceId().anime(animeSources) ?: return@get call.respondNotFound()
            val query = call.request.queryParameters["query"].orEmpty()
            val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 1
            // Like Aniyomi, a plain text search does not need the source's filter list.
            // Some extensions' getFilterList() throws (e.g. InstantiationError on minified
            // builds), which would make every search return HTTP 500. Fall back to an empty
            // filter list instead of failing the whole request.
            val filters = runCatching { source.getFilterList() }.getOrElse {
                println("[miwayomi] getFilterList() failed for ${source.name}, using empty filter list: $it")
                AnimeFilterList()
            }
            val res = source.getSearchAnime(page, query, filters)
            call.respond(AnimesPageDto(res.hasNextPage, res.animes.map { it.toDto() }))
        }

        get("/api/v1/anime/{sourceId}/details") {
            val source = call.sourceId().anime(animeSources) ?: return@get call.respondNotFound()
            val url = call.requiredUrl() ?: return@get call.respondMissingUrl()
            val anime = SAnime.create().also { it.url = url }
            val res = source.getAnimeDetails(anime)
            if (runCatching { res.url }.getOrNull().isNullOrBlank()) res.url = url
            call.respond(res.toDto())
        }

        get("/api/v1/anime/{sourceId}/episodes") {
            val source = call.sourceId().anime(animeSources) ?: return@get call.respondNotFound()
            val url = call.requiredUrl() ?: return@get call.respondMissingUrl()
            val anime = SAnime.create().also { it.url = url }
            val episodes = source.getEpisodeList(anime)
            call.respond(EpisodesDto(episodes.map { it.toDto() }))
        }

        get("/api/v1/anime/{sourceId}/seasons") {
            val source = call.sourceId().anime(animeSources) ?: return@get call.respondNotFound()
            val url = call.requiredUrl() ?: return@get call.respondMissingUrl()
            val anime = SAnime.create().also { it.url = url }
            val seasons = source.getSeasonList(anime)
            call.respond(SeasonsDto(seasons.map { it.toDto() }))
        }

        get("/api/v1/anime/{sourceId}/hosters") {
            val source = call.sourceId().anime(animeSources) ?: return@get call.respondNotFound()
            val url = call.requiredUrl() ?: return@get call.respondMissingUrl()
            val episode = SEpisode.create().also { it.url = url }
            val hosters = source.getHosterList(episode)
            call.respond(HostersDto(hosters.map { it.toDto() }))
        }

        get("/api/v1/anime/{sourceId}/videos") {
            val source = call.sourceId().anime(animeSources) ?: return@get call.respondNotFound()
            val url = call.requiredUrl() ?: return@get call.respondMissingUrl()
            val episode = SEpisode.create().also { it.url = url }

            val videos = mutableListOf<VideoDto>()
            val hosters = runCatching { source.getHosterList(episode) }.getOrNull().orEmpty()
            if (hosters.isNotEmpty()) {
                for (hoster in hosters) {
                    val list = hoster.videoList
                        ?: runCatching { source.getVideoList(hoster) }.getOrNull().orEmpty()
                    videos.addAll(list.map { it.toDtoNormalized() })
                }
            } else {
                val legacy = try {
                    source.getVideoList(episode)
                } catch (e: Throwable) {
                    val root = generateSequence(e) { it.cause }.lastOrNull() ?: e
                    println("[miwayomi] videos ${source.name}: ${root.javaClass.simpleName}: ${root.message}")
                    emptyList()
                }
                videos.addAll(legacy.map { it.toDtoNormalized() })
            }
            call.respond(VideosDto(videos))
        }

        get("/api/v1/anime/{sourceId}/hosterVideos") {
            val source = call.sourceId().anime(animeSources) ?: return@get call.respondNotFound()
            val url = call.requiredUrl() ?: return@get call.respondMissingUrl()
            val hoster = Hoster(hosterUrl = url)
            val videos = source.getVideoList(hoster)
            call.respond(VideosDto(videos.map { it.toDtoNormalized() }))
        }
    }
}
