package eu.kanade.tachiyomi.network

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody

class FlareSolverr(
    private val client: OkHttpClient,
    private val json: Json,
    val baseUrl: String,
) {

    data class Solution(
        val response: String,
        val cookies: List<CookieData>,
        val userAgent: String,
        val status: Int,
        val headers: Map<String, String>,
    )

    data class CookieData(
        val name: String,
        val value: String,
        val domain: String?,
        val path: String?,
        val expires: Long?,
        val httpOnly: Boolean,
        val secure: Boolean,
    )

    private val lock = Any()
    private var sessionId: String? = null

    private fun endpoint(): String = baseUrl.trimEnd('/') + "/v1"

    private fun post(body: JsonObject): JsonObject {
        val req = POST(
            endpoint(),
            body = body.toString().toRequestBody("application/json".toMediaType()),
        )
        val response = client.newCall(req).execute()
        response.use {
            if (!it.isSuccessful) {
                throw RuntimeException("FlareSolverr error ${it.code}: ${it.body.string().take(300)}")
            }
            return json.parseToJsonElement(it.body.string()).jsonObject
        }
    }

    private fun ensureSession(): String? {
        synchronized(lock) {
            if (sessionId == null) {
                val r = post(buildJsonObject { put("cmd", "sessions.create") })
                sessionId = r["session"]?.jsonPrimitive?.contentOrNull
            }
            return sessionId
        }
    }

    fun destroySession() {
        synchronized(lock) {
            val s = sessionId ?: return
            try {
                post(buildJsonObject {
                    put("cmd", "sessions.destroy")
                    put("session", s)
                })
            } catch (_: Exception) {
            }
            sessionId = null
        }
    }

    fun solve(
        url: String,
        method: String = "GET",
        headers: Map<String, String> = emptyMap(),
        body: String? = null,
    ): Solution {
        val session = ensureSession()
        val cmd = if (method.equals("POST", ignoreCase = true)) "request.post" else "request.get"

        val requestBody = buildJsonObject {
            put("cmd", cmd)
            put("url", url)
            put("maxTimeout", 10000)
            session?.let { put("session", it) }
            if (body != null) put("postData", body)
            val h = buildJsonObject {
                headers.forEach { (k, v) -> put(k, v) }
            }
            put("headers", h)
        }

        val r = post(requestBody)
        val sol = r["solution"]?.jsonObject
            ?: throw RuntimeException("FlareSolverr no devolvió solución: ${r.toString().take(300)}")

        val cookies = sol["cookies"]?.jsonArray?.mapNotNull { c ->
            val o = c.jsonObject
            CookieData(
                name = o["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null,
                value = o["value"]?.jsonPrimitive?.contentOrNull ?: "",
                domain = o["domain"]?.jsonPrimitive?.contentOrNull,
                path = o["path"]?.jsonPrimitive?.contentOrNull,
                expires = o["expires"]?.jsonPrimitive?.contentOrNull?.toLongOrNull(),
                httpOnly = o["httpOnly"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: false,
                secure = o["secure"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: false,
            )
        } ?: emptyList()

        val headersOut = sol["headers"]?.jsonObject
            ?.mapNotNull { (k, v) -> (v.jsonPrimitive.contentOrNull)?.let { k to it } }
            ?.toMap()
            .orEmpty()

        return Solution(
            response = sol["response"]?.jsonPrimitive?.contentOrNull ?: "",
            cookies = cookies,
            userAgent = sol["userAgent"]?.jsonPrimitive?.contentOrNull ?: "",
            status = sol["status"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 200,
            headers = headersOut,
        )
    }
}
