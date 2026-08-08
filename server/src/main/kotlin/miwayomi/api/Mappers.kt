package miwayomi.api

import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.model.Hoster
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.source.MangaSource
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import okhttp3.Headers

fun MangaSource.toDto(pkg: String? = null): SourceDto = SourceDto(id = id.toString(), name = name, lang = lang, type = "manga", pkg = pkg)

fun AnimeSource.toDto(pkg: String? = null): SourceDto = SourceDto(id = id.toString(), name = name, lang = lang, type = "anime", pkg = pkg)

fun SManga.toDto(): MangaDto = MangaDto(
    url = url,
    title = title,
    author = author,
    artist = artist,
    description = description,
    genre = genre,
    status = status,
    thumbnail_url = thumbnail_url,
    initialized = initialized,
)

fun SChapter.toDto(): ChapterDto = ChapterDto(
    url = url,
    name = name,
    date_upload = date_upload,
    chapter_number = chapter_number,
    scanlator = scanlator,
)

fun Page.toDto(): PageDto = PageDto(
    index = index,
    number = number,
    url = url,
    imageUrl = imageUrl,
)

fun SAnime.toDto(): AnimeDto = AnimeDto(
    url = url,
    title = title,
    thumbnail_url = thumbnail_url,
    background_url = background_url,
    author = author,
    artist = artist,
    status = status,
    description = description,
    genre = genre,
    season_number = season_number,
    initialized = initialized,
)

fun SEpisode.toDto(): EpisodeDto = EpisodeDto(
    url = url,
    name = name,
    episode_number = episode_number,
    fillermark = fillermark,
    scanlator = scanlator,
    date_upload = date_upload,
    summary = summary,
    preview_url = preview_url,
)

fun Hoster.toDto(): HosterDto = HosterDto(
    hosterUrl = hosterUrl,
    hosterName = hosterName,
    lazy = lazy,
    videoList = videoList?.map { it.toDto() },
)

fun Video.toDto(): VideoDto = VideoDto(
    videoUrl = videoUrl,
    videoTitle = videoTitle,
    resolution = resolution,
    bitrate = bitrate,
    preferred = preferred,
    initialized = initialized,
    headers = headers?.toHeaderMap(),
    subtitleTracks = subtitleTracks.map { TrackDto(it.url, it.lang) },
    audioTracks = audioTracks.map { TrackDto(it.url, it.lang) },
    timestamps = timestamps.map { TimestampDto(it.start, it.end, it.name, it.type.name) },
)

fun Headers.toHeaderMap(): Map<String, String> =
    toMultimap().mapValues { it.value.firstOrNull() ?: "" }

fun Video.toDtoNormalized(): VideoDto {
    val dto = toDto()
    return dto.copy(videoUrl = normalizeVideoUrl(dto.videoUrl) ?: dto.videoUrl)
}
