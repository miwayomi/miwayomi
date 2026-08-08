package eu.kanade.tachiyomi.animesource.online

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.Hoster
import eu.kanade.tachiyomi.animesource.model.HttpServer
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.ThumbnailInfo
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.network.awaitSuccess
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import tachiyomi.core.common.util.lang.awaitSingle
import uy.kohesive.injekt.injectLazy
import java.net.URI
import java.net.URISyntaxException
import java.security.MessageDigest

abstract class AnimeHttpSource : AnimeCatalogueSource {

    protected val network: NetworkHelper by injectLazy()

    abstract val baseUrl: String

    open fun getHomeUrl(): String = baseUrl

    open val versionId: Int = 1

    override val id: Long by lazy { generateId(name, lang, versionId) }

    val headers: Headers by lazy { headersBuilder().build() }

    open val client: OkHttpClient get() = network.client

    @Suppress("MemberVisibilityCanBePrivate")
    protected fun generateId(name: String, lang: String, versionId: Int): Long {
        val key = "${name.lowercase()}/$lang/$versionId"
        val bytes = MessageDigest.getInstance("MD5").digest(key.toByteArray())
        return (0..7).map { bytes[it].toLong() and 0xff shl 8 * (7 - it) }.reduce(Long::or) and Long.MAX_VALUE
    }

    protected open fun headersBuilder(): Headers.Builder = Headers.Builder().apply {
        add("User-Agent", network.defaultUserAgentProvider())
    }

    override fun toString(): String = "$name (${lang.uppercase()})"

    @Suppress("DEPRECATION")
    @Deprecated(
        "Use the suspend API instead",
        ReplaceWith("getPopularAnime"),
    )
    override fun fetchPopularAnime(page: Int): Observable<AnimesPage> {
        return client.newCall(popularAnimeRequest(page))
            .asObservableSuccess()
            .map { response ->
                popularAnimeParse(response)
            }
    }

    @Deprecated(
        message = "The helper functions are inherently limiting and hides the underlying implementation. " +
            "Source developers should make their own implementation according to their needs.",
    )
    protected open fun popularAnimeRequest(page: Int): Request = throw UnsupportedOperationException()

    @Deprecated(
        message = "The helper functions are inherently limiting and hides the underlying implementation. " +
            "Source developers should make their own implementation according to their needs.",
    )
    protected open fun popularAnimeParse(response: Response): AnimesPage = throw UnsupportedOperationException()

    @Suppress("DEPRECATION")
    @Deprecated(
        "Use the suspend API instead",
        ReplaceWith("getSearchAnime"),
    )
    override fun fetchSearchAnime(page: Int, query: String, filters: AnimeFilterList): Observable<AnimesPage> {
        return client.newCall(searchAnimeRequest(page, query, filters))
            .asObservableSuccess()
            .map { response ->
                searchAnimeParse(response)
            }
    }

    @Deprecated(
        message = "The helper functions are inherently limiting and hides the underlying implementation. " +
            "Source developers should make their own implementation according to their needs.",
    )
    protected open fun searchAnimeRequest(
        page: Int,
        query: String,
        filters: AnimeFilterList,
    ): Request = throw UnsupportedOperationException()

    @Deprecated(
        message = "The helper functions are inherently limiting and hides the underlying implementation. " +
            "Source developers should make their own implementation according to their needs.",
    )
    protected open fun searchAnimeParse(response: Response): AnimesPage = throw UnsupportedOperationException()

    @Suppress("DEPRECATION")
    @Deprecated(
        "Use the suspend API instead",
        ReplaceWith("getLatestUpdates"),
    )
    override fun fetchLatestUpdates(page: Int): Observable<AnimesPage> {
        return client.newCall(latestUpdatesRequest(page))
            .asObservableSuccess()
            .map { response ->
                latestUpdatesParse(response)
            }
    }

    @Deprecated(
        message = "The helper functions are inherently limiting and hides the underlying implementation. " +
            "Source developers should make their own implementation according to their needs.",
    )
    protected open fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException()

    @Deprecated(
        message = "The helper functions are inherently limiting and hides the underlying implementation. " +
            "Source developers should make their own implementation according to their needs.",
    )
    protected open fun latestUpdatesParse(response: Response): AnimesPage = throw UnsupportedOperationException()

    @Suppress("DEPRECATION")
    override suspend fun getAnimeDetails(anime: SAnime): SAnime {
        return fetchAnimeDetails(anime).awaitSingle()
    }

    @Suppress("DEPRECATION")
    @Deprecated("Use the combined suspend API instead", replaceWith = ReplaceWith("getAnimeEpisodeUpdate"))
    override fun fetchAnimeDetails(anime: SAnime): Observable<SAnime> {
        return client.newCall(animeDetailsRequest(anime))
            .asObservableSuccess()
            .map { response ->
                animeDetailsParse(response).apply { initialized = true }
            }
    }

    @Deprecated(
        message = "The helper functions are inherently limiting and hides the underlying implementation. " +
            "Source developers should make their own implementation according to their needs.",
    )
    open fun animeDetailsRequest(anime: SAnime): Request {
        return GET(baseUrl + anime.url, headers)
    }

    @Deprecated(
        message = "The helper functions are inherently limiting and hides the underlying implementation. " +
            "Source developers should make their own implementation according to their needs.",
    )
    protected open fun animeDetailsParse(response: Response): SAnime = throw UnsupportedOperationException()

    @Suppress("DEPRECATION")
    override suspend fun getEpisodeList(anime: SAnime): List<SEpisode> {
        return fetchEpisodeList(anime).awaitSingle()
    }

    @Suppress("DEPRECATION")
    @Deprecated("Use the non-RxJava API instead", replaceWith = ReplaceWith("getEpisodeList"))
    override fun fetchEpisodeList(anime: SAnime): Observable<List<SEpisode>> {
        return client.newCall(episodeListRequest(anime))
            .asObservableSuccess()
            .map { response ->
                episodeListParse(response)
            }
    }

    @Deprecated(
        message = "The helper functions are inherently limiting and hides the underlying implementation. " +
            "Source developers should make their own implementation according to their needs.",
    )
    protected open fun episodeListRequest(anime: SAnime): Request {
        return GET(baseUrl + anime.url, headers)
    }

    @Deprecated(
        message = "The helper functions are inherently limiting and hides the underlying implementation. " +
            "Source developers should make their own implementation according to their needs.",
    )
    protected open fun episodeListParse(response: Response): List<SEpisode> = throw UnsupportedOperationException()

    @Deprecated(
        message = "The helper functions are inherently limiting and hides the underlying implementation. " +
            "Source developers should make their own implementation according to their needs.",
    )
    protected open fun episodeVideoParse(response: Response): SEpisode = throw UnsupportedOperationException()

    @Suppress("DEPRECATION")
    @Deprecated(
        message = "Use the combined API instead",
        ReplaceWith("getAnimeSeasonUpdate"),
    )
    override suspend fun getSeasonList(anime: SAnime): List<SAnime> {
        return client.newCall(seasonListRequest(anime))
            .awaitSuccess()
            .let { response ->
                seasonListParse(response)
            }
    }

    @Deprecated(
        message = "The helper functions are inherently limiting and hides the underlying implementation. " +
            "Source developers should make their own implementation according to their needs.",
    )
    protected open fun seasonListRequest(anime: SAnime): Request {
        return GET(baseUrl + anime.url, headers)
    }

    @Deprecated(
        message = "The helper functions are inherently limiting and hides the underlying implementation. " +
            "Source developers should make their own implementation according to their needs.",
    )
    protected open fun seasonListParse(response: Response): List<SAnime> = throw UnsupportedOperationException()

    @Suppress("DEPRECATION")
    override suspend fun getHosterList(episode: SEpisode): List<Hoster> {
        return client.newCall(hosterListRequest(episode))
            .awaitSuccess()
            .let { response ->
                hosterListParse(response)
            }
    }

    @Deprecated(
        message = "The helper functions are inherently limiting and hides the underlying implementation. " +
            "Source developers should make their own implementation according to their needs.",
    )
    protected open fun hosterListRequest(episode: SEpisode): Request {
        return GET(baseUrl + episode.url, headers)
    }

    @Deprecated(
        message = "The helper functions are inherently limiting and hides the underlying implementation. " +
            "Source developers should make their own implementation according to their needs.",
    )
    protected open fun hosterListParse(response: Response): List<Hoster> = throw UnsupportedOperationException()

    @Suppress("DEPRECATION")
    override suspend fun getVideoList(hoster: Hoster): List<Video> {
        return client.newCall(videoListRequest(hoster))
            .awaitSuccess()
            .let { response ->
                videoListParse(response, hoster)
            }
    }

    @Deprecated(
        message = "The helper functions are inherently limiting and hides the underlying implementation. " +
            "Source developers should make their own implementation according to their needs.",
    )
    protected open fun videoListRequest(hoster: Hoster): Request {
        return GET(hoster.hosterUrl, headers)
    }

    @Deprecated(
        message = "The helper functions are inherently limiting and hides the underlying implementation. " +
            "Source developers should make their own implementation according to their needs.",
    )
    protected open fun videoListParse(
        response: Response,
        hoster: Hoster,
    ): List<Video> = throw UnsupportedOperationException()

    open suspend fun resolveVideo(video: Video): Video? {
        return video
    }

    open fun createHttpServer(): HttpServer? {
        return null
    }

    open suspend fun getVideoThumbnails(video: Video): ThumbnailInfo? {
        return null
    }

    open suspend fun getImageTile(url: String): Bitmap? {
        return client.newCall(GET(url, headers)).execute().body.byteStream().use {
            BitmapFactory.decodeStream(it)
        }
    }

    override suspend fun getVideoList(episode: SEpisode): List<Video> {
        @Suppress("DEPRECATION")
        return fetchVideoList(episode).awaitSingle()
    }

    @Suppress("DEPRECATION")
    @Deprecated("Use the suspend API instead", replaceWith = ReplaceWith("getVideoList"))
    override fun fetchVideoList(episode: SEpisode): Observable<List<Video>> {
        return client.newCall(videoListRequest(episode))
            .asObservableSuccess()
            .map { response ->
                videoListParse(response)
            }
    }

    @Deprecated(
        message = "The helper functions are inherently limiting and hides the underlying implementation. " +
            "Source developers should make their own implementation according to their needs.",
    )
    protected open fun videoListRequest(episode: SEpisode): Request {
        return GET(baseUrl + episode.url, headers)
    }

    @Deprecated(
        message = "The helper functions are inherently limiting and hides the underlying implementation. " +
            "Source developers should make their own implementation according to their needs.",
    )
    protected open fun videoListParse(response: Response): List<Video> = throw UnsupportedOperationException()

    open fun List<Hoster>.sortHosters(): List<Hoster> {
        return this
    }

    open fun List<Video>.sortVideos(): List<Video> {
        @Suppress("DEPRECATION")
        return sort()
    }

    @Deprecated("Use .sortVideos() instead", replaceWith = ReplaceWith("sortVideos"))
    protected open fun List<Video>.sort(): List<Video> {
        return this
    }

    @Suppress("DEPRECATION")
    open suspend fun getVideoUrl(video: Video): String {
        return fetchVideoUrl(video).awaitSingle()
    }

    @Suppress("DEPRECATION")
    @Deprecated("Use resolveVideo for lazy loading instead", replaceWith = ReplaceWith("resolveVideo"))
    open fun fetchVideoUrl(video: Video): Observable<String> {
        return client.newCall(videoUrlRequest(video))
            .asObservableSuccess()
            .map { videoUrlParse(it) }
    }

    @Deprecated(
        message = "The helper functions are inherently limiting and hides the underlying implementation. " +
            "Source developers should make their own implementation according to their needs.",
    )
    protected open fun videoUrlRequest(video: Video): Request {
        return GET(video.url, headers)
    }

    @Deprecated(
        message = "The helper functions are inherently limiting and hides the underlying implementation. " +
            "Source developers should make their own implementation according to their needs.",
    )
    protected open fun videoUrlParse(response: Response): String = throw UnsupportedOperationException()

    @Suppress("Unused")
    fun SEpisode.setUrlWithoutDomain(url: String) {
        this.url = getUrlWithoutDomain(url)
    }

    @Suppress("Unused")
    fun SAnime.setUrlWithoutDomain(url: String) {
        this.url = getUrlWithoutDomain(url)
    }

    private fun getUrlWithoutDomain(orig: String): String {
        return try {
            val uri = URI(orig)
            var out = uri.path
            if (uri.query != null) {
                out += "?" + uri.query
            }
            if (uri.fragment != null) {
                out += "#" + uri.fragment
            }
            out
        } catch (_: URISyntaxException) {
            orig
        }
    }

    @Suppress("DEPRECATION")
    open fun getAnimeUrl(anime: SAnime): String {
        return animeDetailsRequest(anime).url.toString()
    }

    @Suppress("Unused")
    open fun getEpisodeUrl(episode: SEpisode): String {
        return episode.url
    }

    @Deprecated("All modifications should be done when constructing the episode")
    open fun prepareNewEpisode(episode: SEpisode, anime: SAnime) {}
}
