package aniyomi.core.common.torrent

import aniyomi.core.common.torrent.model.Torrent
import eu.kanade.tachiyomi.network.NetworkHelper
import kotlinx.serialization.json.Json
import java.io.InputStream

class TorrentServerApi(
    @Suppress("UNUSED_PARAMETER") private val network: NetworkHelper,
    @Suppress("UNUSED_PARAMETER") private val json: Json,
) {
    @Volatile
    private var port: Int = 0

    val hostUrl: String
        get() = "http://127.0.0.1:$port"

    fun setPort(value: Int) {
        port = value
    }

    fun getPort(): Int = port

    suspend fun echo(): String {
        throw DisabledTorrServerException()
    }

    suspend fun addTorrent(
        link: String,
        title: String,
        poster: String = "",
        data: String = "",
        save: Boolean,
    ): Torrent {
        throw DisabledTorrServerException()
    }

    suspend fun uploadTorrent(
        file: InputStream,
        title: String,
        save: Boolean = false,
    ): Torrent {
        throw DisabledTorrServerException()
    }
}
