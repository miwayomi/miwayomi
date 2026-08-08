package eu.kanade.tachiyomi.source

interface SourceFactory {

    fun createSources(): List<MangaSource>
}
