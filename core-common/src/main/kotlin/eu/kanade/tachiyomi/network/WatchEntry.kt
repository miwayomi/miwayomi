package eu.kanade.tachiyomi.network

/** A single anime episode's watch progress, persisted across restarts. */
data class WatchEntry(
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
