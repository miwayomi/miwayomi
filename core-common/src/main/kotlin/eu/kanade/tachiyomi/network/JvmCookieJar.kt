package eu.kanade.tachiyomi.network

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import java.util.concurrent.ConcurrentHashMap

class JvmCookieJar(private val persistent: SqliteStore? = null) : CookieJar {

    private val store = ConcurrentHashMap<String, MutableList<Cookie>>()

    init {
        persistent?.cookieAll()?.forEach { c ->
            c.toCookie()?.let { cookie ->
                store.getOrPut(c.host) { mutableListOf() }.add(cookie)
            }
        }
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (cookies.isEmpty()) return
        val list = store.getOrPut(url.host) { mutableListOf() }
        synchronized(list) {
            for (cookie in cookies) {
                list.removeAll { it.name == cookie.name }
                list.add(cookie)
                persistent?.cookieUpsert(cookie.toStored(url.host))
            }
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val result = ArrayList<Cookie>()
        for (list in store.values) {
            synchronized(list) {
                for (cookie in list) {
                    if (cookie.matches(url)) result.add(cookie)
                }
            }
        }
        return result
    }

    fun get(url: HttpUrl): List<Cookie> = loadForRequest(url)

    fun remove(url: HttpUrl, cookieNames: List<String>? = null, maxAge: Int = -1): Int {
        val list = store[url.host] ?: return 0
        synchronized(list) {
            val toRemove = if (cookieNames == null) list.toList() else list.filter { it.name in cookieNames }
            toRemove.forEach { cookie ->
                list.remove(cookie)
                persistent?.cookieDelete(url.host, cookie.name)
                if (maxAge >= 0) {
                    val expired = Cookie.Builder()
                        .name(cookie.name)
                        .value("")
                        .domain(url.host)
                        .expiresAt(System.currentTimeMillis() - 1)
                        .build()
                    list.add(expired)
                    persistent?.cookieUpsert(expired.toStored(url.host))
                }
            }
            return toRemove.size
        }
    }

    fun removeAll() {
        store.clear()
        persistent?.cookieDeleteAll()
    }
}
