package miwayomi.api

import kotlinx.serialization.Serializable

@Serializable
data class SourceDto(
    val id: String,
    val name: String,
    val lang: String,
    val type: String,
    val pkg: String? = null,
)

@Serializable
data class MangaDto(
    val url: String,
    val title: String,
    val author: String?,
    val artist: String?,
    val description: String?,
    val genre: String?,
    val status: Int,
    val thumbnail_url: String?,
    val initialized: Boolean,
)

@Serializable
data class ChapterDto(
    val url: String,
    val name: String,
    val date_upload: Long,
    val chapter_number: Float,
    val scanlator: String?,
)

@Serializable
data class PageDto(
    val index: Int,
    val number: Int,
    val url: String,
    val imageUrl: String?,
)

@Serializable
data class AnimeDto(
    val url: String,
    val title: String,
    val thumbnail_url: String?,
    val background_url: String?,
    val author: String?,
    val artist: String?,
    val status: Int,
    val description: String?,
    val genre: String?,
    val season_number: Double,
    val initialized: Boolean,
)

@Serializable
data class EpisodeDto(
    val url: String,
    val name: String,
    val episode_number: Float,
    val fillermark: Boolean,
    val scanlator: String?,
    val date_upload: Long,
    val summary: String?,
    val preview_url: String?,
)

@Serializable
data class TrackDto(val url: String, val lang: String)

@Serializable
data class TimestampDto(val start: Double, val end: Double, val name: String, val type: String)

@Serializable
data class VideoDto(
    val videoUrl: String,
    val videoTitle: String,
    val resolution: Int?,
    val bitrate: Int?,
    val preferred: Boolean,
    val initialized: Boolean,
    val headers: Map<String, String>?,
    val subtitleTracks: List<TrackDto>,
    val audioTracks: List<TrackDto>,
    val timestamps: List<TimestampDto>,
)

@Serializable
data class HosterDto(
    val hosterUrl: String,
    val hosterName: String,
    val lazy: Boolean,
    val videoList: List<VideoDto>?,
)

@Serializable
data class SourcesListDto(val manga: List<SourceDto>, val anime: List<SourceDto>)

@Serializable
data class MangasPageDto(val hasNextPage: Boolean, val mangas: List<MangaDto>)

@Serializable
data class AnimesPageDto(val hasNextPage: Boolean, val animes: List<AnimeDto>)

@Serializable
data class ChaptersDto(val chapters: List<ChapterDto>)

@Serializable
data class PagesDto(val pages: List<PageDto>)

@Serializable
data class EpisodesDto(val episodes: List<EpisodeDto>)

@Serializable
data class SeasonsDto(val seasons: List<AnimeDto>)

@Serializable
data class HostersDto(val hosters: List<HosterDto>)

@Serializable
data class VideosDto(val videos: List<VideoDto>)

@Serializable
data class HealthDto(val status: String, val service: String, val mangaSources: Int, val animeSources: Int)

@Serializable
data class ErrorDto(
    val error: String,
    val challengeUrl: String? = null,
    val challengeUserAgent: String? = null,
)
