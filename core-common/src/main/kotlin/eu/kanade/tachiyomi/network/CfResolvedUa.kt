package eu.kanade.tachiyomi.network

import java.util.concurrent.ConcurrentHashMap

object CfResolvedUa {
    private const val PREFIX = "cf_ua_"

    private val resolved = ConcurrentHashMap<String, String>()

    @Volatile
    private var store: SqliteStore? = null

    fun init(store: SqliteStore?) {
        this.store = store
        resolved.clear()
        store?.kvAll(PREFIX)?.forEach { (k, v) -> resolved[k.removePrefix(PREFIX)] = v }
    }

    fun set(host: String, userAgent: String) {
        val h = host.trim().trimStart('.').lowercase()
        resolved[h] = userAgent
        runCatching { store?.kvSet(PREFIX + h, userAgent) }
    }

    fun get(host: String): String? = resolved[host.lowercase()]

    fun remove(host: String) {
        val h = host.lowercase()
        resolved.remove(h)
        runCatching { store?.kvDelete(PREFIX + h) }
    }
}
