package miwayomi.source

import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.source.MangaSource
import java.util.concurrent.ConcurrentHashMap

class MangaSourceManager {

    private val sources = ConcurrentHashMap<Long, MangaSource>()

    fun register(source: MangaSource) {
        sources[source.id] = source
    }

    fun unregister(source: MangaSource) {
        sources.remove(source.id)
    }

    fun get(id: Long): MangaSource? = sources[id]

    fun all(): List<MangaSource> = sources.values.sortedBy { it.name }
}

class AnimeSourceManager {

    private val sources = ConcurrentHashMap<Long, AnimeSource>()

    fun register(source: AnimeSource) {
        sources[source.id] = source
    }

    fun unregister(source: AnimeSource) {
        sources.remove(source.id)
    }

    fun get(id: Long): AnimeSource? = sources[id]

    fun all(): List<AnimeSource> = sources.values.sortedBy { it.name }
}
