package eu.kanade.tachiyomi.network

data class Favorite(
    val sourceId: String,
    val url: String,
    val title: String,
    val thumbnailUrl: String?,
    val type: String,
    val addedAt: Long,
    val lastReadUrl: String? = null,
    val lastReadName: String? = null,
)
