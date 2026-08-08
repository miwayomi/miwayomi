package miwayomi.api

import miwayomi.di.ConfigHolder
import java.io.File
import java.security.MessageDigest

object ImageCache {

    private val dir: File by lazy { File(ConfigHolder.config.dataDir, "cache/images") }

    fun cachedFile(url: String, headers: String): File? {
        val f = File(dir, key(url, headers))
        return f.takeIf { it.isFile && it.length() > 0 }
    }

    fun cacheImage(url: String, headers: String, bytes: ByteArray) {
        runCatching {
            if (!dir.exists()) dir.mkdirs()
            File(dir, key(url, headers)).writeBytes(bytes)
        }
    }

    private fun key(url: String, headers: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest("$url|$headers".toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
