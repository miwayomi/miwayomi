package eu.kanade.tachiyomi.network

import java.io.IOException

class CloudflareChallengeException(
    val url: String,
    val userAgent: String? = null,
    cause: Throwable? = null,
) : IOException("Reto de Cloudflare pendiente en $url", cause)
