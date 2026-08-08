package eu.kanade.tachiyomi.network

import okhttp3.Cookie

data class StoredCookie(
    val host: String,
    val name: String,
    val value: String,
    val domain: String,
    val path: String,
    val expiresAt: Long,
    val secure: Boolean,
    val httpOnly: Boolean,
    val hostOnly: Boolean,
)

fun StoredCookie.toCookie(): Cookie? = try {
    val b = Cookie.Builder().name(name).value(value).path(path)

    if (expiresAt > 0) b.expiresAt(expiresAt)
    if (hostOnly) b.hostOnlyDomain(domain) else b.domain(domain)
    if (secure) b.secure()
    if (httpOnly) b.httpOnly()
    b.build()
} catch (e: Exception) {
    null
}

fun Cookie.toStored(host: String) = StoredCookie(
    host = host,
    name = name,
    value = value,
    domain = domain,
    path = path,
    expiresAt = expiresAt,
    secure = secure,
    httpOnly = httpOnly,
    hostOnly = hostOnly,
)
