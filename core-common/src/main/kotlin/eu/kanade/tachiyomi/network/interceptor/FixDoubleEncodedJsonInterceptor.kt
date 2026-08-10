package eu.kanade.tachiyomi.network.interceptor

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.Interceptor
import okhttp3.MediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.Buffer

class FixDoubleEncodedJsonInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val body = original.body

        if (body == null || !body.contentType().isJson()) {
            return chain.proceed(original)
        }

        val buf = Buffer()
        body.writeTo(buf)
        val bodyString = buf.readUtf8()
        val fixed = unwrapDoubleEncodedJson(bodyString)

        if (fixed != bodyString) {
            println("[miwayomi] fixed double-encoded JSON body (${bodyString.length} -> ${fixed.length} bytes)")
        }

        val newBody = fixed.toRequestBody(body.contentType())

        val request = original.newBuilder()
            .removeHeader("Content-Length")
            .method(original.method, newBody)
            .build()
        return chain.proceed(request)
    }

    private fun unwrapDoubleEncodedJson(body: String): String {
        if (!body.startsWith("\"")) return body
        return try {
            val element = Json.parseToJsonElement(body)
            val inner = (element as? JsonPrimitive)?.contentOrNull ?: return body

            Json.parseToJsonElement(inner)
            inner
        } catch (_: Exception) {
            body
        }
    }

    private fun MediaType?.isJson(): Boolean = this?.subtype?.contains("json") == true
}
