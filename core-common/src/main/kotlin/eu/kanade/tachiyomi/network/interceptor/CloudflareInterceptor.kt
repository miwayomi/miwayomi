package eu.kanade.tachiyomi.network.interceptor

import eu.kanade.tachiyomi.network.CfResolvedUa
import eu.kanade.tachiyomi.network.CloudflareChallengeException
import eu.kanade.tachiyomi.network.FlareSolverr
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.IOException

class CloudflareInterceptor(
    private val flareSolverr: FlareSolverr?,
    private val cookieJar: CookieJar,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        var request = chain.request()

        val resolvedUa = resolvedUaFor(request.url.host)
        if (resolvedUa != null && request.header("User-Agent") != resolvedUa) {
            request = request.newBuilder().header("User-Agent", resolvedUa).build()
        }

        val response = chain.proceed(request)

        if (!isCloudflareChallenge(response)) {
            return response
        }
        response.close()

        val ua = request.header("User-Agent")
        val solver = flareSolverr
        if (solver == null) {
            throw CloudflareChallengeException(request.url.toString(), ua)
        }

        return try {
            val headers = request.headers.toMultimap().mapValues { it.value.firstOrNull() ?: "" }
            val body = if (request.method.equals("POST", ignoreCase = true)) {
                request.body?.let { readBody(it) }
            } else {
                null
            }
            val solution = solver.solve(request.url.toString(), request.method, headers, body)
            persistCookies(solution.cookies, request.url)
            buildResponse(request, solution)
        } catch (e: Exception) {
            if (e is CloudflareChallengeException) throw e
            throw CloudflareChallengeException(request.url.toString(), ua, e)
        }
    }

    private fun isCloudflareChallenge(response: Response): Boolean {
        if (response.code !in setOf(403, 429, 503)) return false
        val server = response.header("Server")?.lowercase() ?: ""
        val hasCfHeaders = response.header("cf-mitigated") != null ||
            response.header("CF-RAY") != null ||
            response.header("cf-chl-out") != null
        if (server.contains("cloudflare") || hasCfHeaders) return true

        val bodyHint = runCatching {
            val peeked = response.body?.source()?.peek()?.readUtf8()?.take(2048)?.lowercase()
            peeked?.contains("just a moment") == true ||
                peeked?.contains("cf-chl") == true ||
                peeked?.contains("cf_browser_challenge") == true
        }.getOrDefault(false)
        return bodyHint
    }

    private fun resolvedUaFor(host: String): String? {
        var h = host
        while (true) {
            CfResolvedUa.get(h)?.let { return it }
            val idx = h.indexOf('.')
            if (idx <= 0 || idx == h.lastIndex) return null
            h = h.substring(idx + 1)
        }
    }

    private fun persistCookies(cookies: List<FlareSolverr.CookieData>, url: HttpUrl) {
        val parsed = cookies.mapNotNull { c ->
            try {
                val domain = c.domain?.trimStart('.')?.takeIf { it.isNotBlank() } ?: url.host
                Cookie.Builder()
                    .name(c.name)
                    .value(c.value)
                    .domain(domain)
                    .path(c.path ?: "/")
                    .apply {
                        if (c.httpOnly) httpOnly()
                        if (c.secure) secure()
                        val exp = c.expires ?: 0L
                        if (exp > 0) expiresAt(exp * 1000) else expiresAt(Long.MAX_VALUE)
                    }
                    .build()
            } catch (e: Exception) {
                null
            }
        }
        if (parsed.isNotEmpty()) {
            runCatching { cookieJar.saveFromResponse(url, parsed) }
        }
    }

    private fun buildResponse(request: Request, solution: FlareSolverr.Solution): Response {
        val contentType = solution.headers["Content-Type"]
            ?: solution.headers["content-type"]
            ?: "text/html; charset=utf-8"
        val body = solution.response.toResponseBody(contentType.toMediaType())
        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(if (solution.status in 200..599) solution.status else 200)
            .message("OK")
            .body(body)
            .addHeader("Content-Type", contentType)
            .build()
    }

    private fun readBody(body: okhttp3.RequestBody): String {
        val buffer = okio.Buffer()
        body.writeTo(buffer)
        return buffer.readUtf8()
    }
}
