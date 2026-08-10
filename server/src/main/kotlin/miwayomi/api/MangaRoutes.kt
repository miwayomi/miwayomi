package miwayomi.api

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import miwayomi.source.MangaSourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

fun Application.registerMangaApi() {
    val mangaSources = Injekt.get<MangaSourceManager>()

    routing {
        get("/api/v1/manga/{sourceId}/popular") {
            val source = call.sourceId().manga(mangaSources) ?: return@get call.respondNotFound()
            val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 1
            val res = source.getPopularManga(page)
            call.respond(MangasPageDto(res.hasNextPage, res.mangas.map { it.toDto() }))
        }

        get("/api/v1/manga/{sourceId}/latest") {
            val source = call.sourceId().manga(mangaSources) ?: return@get call.respondNotFound()
            val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 1
            val res = source.getLatestUpdates(page)
            call.respond(MangasPageDto(res.hasNextPage, res.mangas.map { it.toDto() }))
        }

        get("/api/v1/manga/{sourceId}/search") {
            val source = call.sourceId().manga(mangaSources) ?: return@get call.respondNotFound()
            val query = call.request.queryParameters["query"].orEmpty()
            val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 1
            // Like Tachiyomi, a plain text search does not need the source's filter list.
            // Some extensions' getFilterList() throws (e.g. InstantiationError on minified
            // builds), which would make every search return HTTP 500. Fall back to an empty
            // filter list instead of failing the whole request.
            val filters = runCatching { source.getFilterList() }.getOrElse {
                println("[miwayomi] getFilterList() failed for ${source.name}, using empty filter list: $it")
                FilterList()
            }
            val res = source.getSearchManga(page, query, filters)
            call.respond(MangasPageDto(res.hasNextPage, res.mangas.map { it.toDto() }))
        }

        get("/api/v1/manga/{sourceId}/details") {
            val source = call.sourceId().manga(mangaSources) ?: return@get call.respondNotFound()
            val url = call.requiredUrl() ?: return@get call.respondMissingUrl()
            val manga = SManga.create().also { it.url = url }
            val res = source.getMangaDetails(manga)
            if (runCatching { res.url }.getOrNull().isNullOrBlank()) res.url = url
            call.respond(res.toDto())
        }

        get("/api/v1/manga/{sourceId}/chapters") {
            val source = call.sourceId().manga(mangaSources) ?: return@get call.respondNotFound()
            val url = call.requiredUrl() ?: return@get call.respondMissingUrl()
            val manga = SManga.create().also { it.url = url }
            val chapters = source.getChapterList(manga)
            call.respond(ChaptersDto(chapters.map { it.toDto() }))
        }

        get("/api/v1/manga/{sourceId}/pages") {
            val source = call.sourceId().manga(mangaSources) ?: return@get call.respondNotFound()
            val url = call.requiredUrl() ?: return@get call.respondMissingUrl()
            val chapter = SChapter.create().also { it.url = url }
            val pages = source.getPageList(chapter)
            val httpSource = source as? HttpSource
            pages.forEach { p ->
                if (p.imageUrl == null && httpSource != null) {
                    runCatching { p.imageUrl = httpSource.getImageUrl(p) }
                }
            }
            call.respond(PagesDto(pages.map { it.toDto() }))
        }
    }
}
