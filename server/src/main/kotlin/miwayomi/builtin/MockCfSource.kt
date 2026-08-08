package miwayomi.builtin

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import okhttp3.Request
import okhttp3.Response

class MockCfSource : HttpSource() {

    override val name = "MockCF"
    override val lang = "test"
    override val baseUrl = "http://127.0.0.1:9999"
    override val supportsLatest = false

    override fun getFilterList() = FilterList()

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/challenge", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        response.use {
            val body = it.body.string()
            return MangasPage(
                listOf(
                    SManga.create().apply {
                        url = "/m/1"
                        title = if (body.contains("SOLUCIONADO")) "OK: reto superado" else "FALLO: ${body.take(120)}"
                    },
                ),
                false,
            )
        }
    }

    override fun latestUpdatesRequest(page: Int): Request = popularMangaRequest(page)
    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request =
        popularMangaRequest(page)
    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    override fun mangaDetailsParse(response: Response): SManga = SManga.create()
    override fun chapterListParse(response: Response): List<SChapter> = emptyList()
    override fun chapterPageParse(response: Response): SChapter = SChapter.create()
    override fun pageListParse(response: Response): List<Page> = emptyList()
    override fun imageUrlParse(response: Response): String = ""
}
