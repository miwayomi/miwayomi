package eu.kanade.tachiyomi.animesource.online

import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.Hoster
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

@Suppress("unused")
@Deprecated(
    message = "In most cases sources only require a subset of the methods from this class. " +
        "Source developers should make their own implementation according to their needs.",
)
abstract class ParsedAnimeHttpSource : AnimeHttpSource() {

    @Deprecated(
        "The helper functions are inherently limiting and hides the underlying implementation. Source developers should make their own implementation according to their needs.",
    )
    override fun popularAnimeParse(response: Response): AnimesPage {
        val document = response.asJsoup()

        val animes = document.select(popularAnimeSelector()).map { element ->
            popularAnimeFromElement(element)
        }

        val hasNextPage = popularAnimeNextPageSelector()?.let { selector ->
            document.select(selector).first()
        } != null

        return AnimesPage(animes, hasNextPage)
    }

    protected abstract fun popularAnimeSelector(): String

    protected abstract fun popularAnimeFromElement(element: Element): SAnime

    protected abstract fun popularAnimeNextPageSelector(): String?

    @Deprecated(
        "The helper functions are inherently limiting and hides the underlying implementation. Source developers should make their own implementation according to their needs.",
    )
    override fun searchAnimeParse(response: Response): AnimesPage {
        val document = response.asJsoup()

        val animes = document.select(searchAnimeSelector()).map { element ->
            searchAnimeFromElement(element)
        }

        val hasNextPage = searchAnimeNextPageSelector()?.let { selector ->
            document.select(selector).first()
        } != null

        return AnimesPage(animes, hasNextPage)
    }

    protected abstract fun searchAnimeSelector(): String

    protected abstract fun searchAnimeFromElement(element: Element): SAnime

    protected abstract fun searchAnimeNextPageSelector(): String?

    @Deprecated(
        "The helper functions are inherently limiting and hides the underlying implementation. Source developers should make their own implementation according to their needs.",
    )
    override fun latestUpdatesParse(response: Response): AnimesPage {
        val document = response.asJsoup()

        val animes = document.select(latestUpdatesSelector()).map { element ->
            latestUpdatesFromElement(element)
        }

        val hasNextPage = latestUpdatesNextPageSelector()?.let { selector ->
            document.select(selector).first()
        } != null

        return AnimesPage(animes, hasNextPage)
    }

    protected abstract fun latestUpdatesSelector(): String

    protected abstract fun latestUpdatesFromElement(element: Element): SAnime

    protected abstract fun latestUpdatesNextPageSelector(): String?

    @Deprecated(
        "The helper functions are inherently limiting and hides the underlying implementation. Source developers should make their own implementation according to their needs.",
    )
    override fun animeDetailsParse(response: Response): SAnime {
        return animeDetailsParse(response.asJsoup())
    }

    protected abstract fun animeDetailsParse(document: Document): SAnime

    @Deprecated(
        "The helper functions are inherently limiting and hides the underlying implementation. Source developers should make their own implementation according to their needs.",
    )
    override fun episodeListParse(response: Response): List<SEpisode> {
        val document = response.asJsoup()
        return document.select(episodeListSelector()).map { episodeFromElement(it) }
    }

    protected abstract fun episodeListSelector(): String

    protected abstract fun episodeFromElement(element: Element): SEpisode

    @Deprecated(
        "The helper functions are inherently limiting and hides the underlying implementation. Source developers should make their own implementation according to their needs.",
    )
    override fun seasonListParse(response: Response): List<SAnime> {
        val document = response.asJsoup()
        return document.select(seasonListSelector()).map { seasonFromElement(it) }
    }

    protected abstract fun seasonListSelector(): String

    protected abstract fun seasonFromElement(element: Element): SAnime

    @Deprecated(
        "The helper functions are inherently limiting and hides the underlying implementation. Source developers should make their own implementation according to their needs.",
    )
    override fun hosterListParse(response: Response): List<Hoster> {
        val document = response.asJsoup()
        return document.select(hosterListSelector()).map(::hosterFromElement)
    }

    protected abstract fun hosterListSelector(): String

    protected abstract fun hosterFromElement(element: Element): Hoster

    @Deprecated(
        "The helper functions are inherently limiting and hides the underlying implementation. Source developers should make their own implementation according to their needs.",
    )
    override fun videoListParse(response: Response): List<Video> {
        val document = response.asJsoup()
        return document.select(videoListSelector()).map { videoFromElement(it) }
    }

    protected abstract fun videoListSelector(): String

    protected abstract fun videoFromElement(element: Element): Video

    @Deprecated(
        "The helper functions are inherently limiting and hides the underlying implementation. Source developers should make their own implementation according to their needs.",
    )
    override fun videoUrlParse(response: Response): String {
        return videoUrlParse(response.asJsoup())
    }

    protected abstract fun videoUrlParse(document: Document): String
}
