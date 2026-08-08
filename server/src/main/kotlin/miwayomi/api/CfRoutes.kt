package miwayomi.api

import eu.kanade.tachiyomi.network.CfResolvedUa
import eu.kanade.tachiyomi.network.NetworkHelper
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.serialization.Serializable
import miwayomi.cf.CfBrowser
import miwayomi.di.ConfigHolder
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

@Serializable
data class CfClickDto(val x: Int, val y: Int)

@Serializable
data class CfKeyDto(val key: String)

@Serializable
data class CfOkDto(val ok: Boolean)

@Serializable
data class CfUrlDto(val url: String)

@Serializable
data class CfFinishDto(val count: Int)

fun Application.registerCfApi() {
    val network = Injekt.get<NetworkHelper>()

    val browser = CfBrowser(
        chromePath = ConfigHolder.config.chromePath,
        dataDir = ConfigHolder.config.dataDir,
    )

    routing {
        get("/api/v1/cf/start") {
            val url = call.request.queryParameters["url"]
                ?: return@get call.respond(HttpStatusCode.BadRequest, CfOkDto(false))
            runCatching { browser.start() }
                .onFailure { e -> return@get call.respond(HttpStatusCode.InternalServerError, ErrorDto(e.message ?: "error")) }
            browser.open(url)
            call.respond(CfOkDto(true))
        }

        get("/api/v1/cf/shot") {
            try {
                call.respondBytes(browser.screenshot(), ContentType.Image.PNG)
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, ErrorDto(e.message ?: "error"))
            }
        }

        get("/api/v1/cf/url") {
            call.respond(CfUrlDto(runCatching { browser.currentUrl() }.getOrDefault("")))
        }

        post("/api/v1/cf/click") {
            val dto = call.receive<CfClickDto>()
            runCatching { browser.click(dto.x, dto.y) }
                .onFailure { e -> return@post call.respond(HttpStatusCode.InternalServerError, ErrorDto(e.message ?: "error")) }
            call.respond(CfOkDto(true))
        }

        post("/api/v1/cf/key") {
            val dto = call.receive<CfKeyDto>()
            runCatching { browser.key(dto.key) }
                .onFailure { e -> return@post call.respond(HttpStatusCode.InternalServerError, ErrorDto(e.message ?: "error")) }
            call.respond(CfOkDto(true))
        }

        post("/api/v1/cf/finish") {
            val cookies = runCatching { browser.cookies() }.getOrDefault(emptyList())
            var saved = 0
            for (c in cookies) {
                try {
                    val okCookie = Cookie.Builder()
                        .name(c.name)
                        .value(c.value)
                        .domain(c.domain)
                        .path(c.path)
                        .apply {
                            if (c.httpOnly) httpOnly()
                            if (c.secure) secure()
                            if (c.expires > 0) expiresAt(c.expires * 1000) else expiresAt(Long.MAX_VALUE)
                        }
                        .build()
                    network.cookieJar.saveFromResponse(
                        "https://${c.domain}".toHttpUrl(),
                        listOf(okCookie),
                    )
                    CfResolvedUa.set(c.domain, browser.resolvedUserAgent)
                    saved++
                } catch (_: Exception) {

                }
            }
            runCatching { browser.stop() }
            call.respond(CfFinishDto(saved))
        }
    }
}
