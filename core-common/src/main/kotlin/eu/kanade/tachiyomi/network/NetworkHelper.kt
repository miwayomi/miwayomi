package eu.kanade.tachiyomi.network

import android.content.Context
import eu.kanade.tachiyomi.network.interceptor.CloudflareInterceptor
import eu.kanade.tachiyomi.network.interceptor.FixDoubleEncodedJsonInterceptor
import eu.kanade.tachiyomi.network.interceptor.IgnoreGzipInterceptor
import eu.kanade.tachiyomi.network.interceptor.UncaughtExceptionInterceptor
import eu.kanade.tachiyomi.network.interceptor.UserAgentInterceptor
import kotlinx.serialization.json.Json
import okhttp3.Cache
import okhttp3.CookieJar
import okhttp3.OkHttpClient
import okhttp3.brotli.BrotliInterceptor
import okhttp3.logging.HttpLoggingInterceptor
import java.io.File
import java.util.concurrent.TimeUnit

class NetworkHelper(
    private val context: Context,
    private val preferences: NetworkPreferences,
    private val flareSolverrUrl: String? = null,
) {

    val cookieJar: CookieJar = run {

        val store = SqliteStore(File(context.getCacheDir(), "miwayomi.db"))
        CfResolvedUa.init(store)
        JvmCookieJar(store)
    }

    private val clientBuilder: OkHttpClient.Builder = run {
        val builder = OkHttpClient.Builder()
            .cookieJar(cookieJar)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .callTimeout(2, TimeUnit.MINUTES)
            .cache(
                Cache(
                    directory = File(context.getCacheDir(), "network_cache"),
                    maxSize = 5L * 1024 * 1024,
                ),
            )
            .addInterceptor(UncaughtExceptionInterceptor())
            .addInterceptor(UserAgentInterceptor(::defaultUserAgentProvider))
            .addNetworkInterceptor(IgnoreGzipInterceptor())
            .addNetworkInterceptor(BrotliInterceptor)

            .addNetworkInterceptor(FixDoubleEncodedJsonInterceptor())

        if (preferences.verboseLogging().get()) {
            val httpLoggingInterceptor = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.HEADERS
            }
            builder.addNetworkInterceptor(httpLoggingInterceptor)
        }

        builder
    }

    val client: OkHttpClient = clientBuilder
        .apply {
            val flareSolverr = flareSolverrUrl?.takeIf { it.isNotBlank() }?.let { url ->
                val flareClient = OkHttpClient.Builder()
                    .connectTimeout(10, TimeUnit.SECONDS)

                    .readTimeout(15, TimeUnit.SECONDS)
                    .callTimeout(25, TimeUnit.SECONDS)
                    .build()
                FlareSolverr(flareClient, Json { ignoreUnknownKeys = true }, url)
            }

            addInterceptor(CloudflareInterceptor(flareSolverr, cookieJar))
        }
        .build()

    fun defaultUserAgentProvider(): String = preferences.defaultUserAgent().get().trim()
}
