package eu.kanade.tachiyomi.network

/**
 * Metadata of an installed extension, persisted in the SQLite registry
 * (`extensions` table). Keeps track of what the user installed so it can be
 * shown again later without needing to query a remote repository.
 */
data class StoredExtension(
    val pkg: String,
    val name: String,
    val versionName: String,
    val versionCode: Long,
    val isNsfw: Boolean,
    val isAnime: Boolean,
    val apkFile: String?,
    val jarFile: String?,
    val sourceCount: Int,
    val installedAt: Long,
)
