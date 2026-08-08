package eu.kanade.tachiyomi.source.online

import eu.kanade.tachiyomi.source.MangaSource
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga

interface ResolvableSource : MangaSource {

    fun getUriType(uri: String): UriType

    suspend fun getManga(uri: String): SManga?

    suspend fun getChapter(uri: String): SChapter?
}

sealed interface UriType {
    data object Manga : UriType
    data object Chapter : UriType
    data object Unknown : UriType
}
