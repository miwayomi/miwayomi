package eu.kanade.tachiyomi.network

import android.content.Context
import org.graalvm.polyglot.Context as GraalContext
import tachiyomi.core.common.util.lang.withIOContext

class JavaScriptEngine(@Suppress("UNUSED_PARAMETER") context: Context) {

    @Volatile
    private var sharedContext: GraalContext? = null

    private val lock = Any()

    suspend fun <T> evaluate(script: String): T = withIOContext {
        val ctx = getContext()
        val value = synchronized(lock) { ctx.eval("js", script) }
        @Suppress("UNCHECKED_CAST")
        when {
            value.isString -> value.asString() as T
            value.isBoolean -> value.asBoolean() as T
            value.isNumber -> value.asDouble() as T
            value.isNull -> null as T
            else -> value.toString() as T
        }
    }

    private fun getContext(): GraalContext {
        sharedContext?.let { return it }
        synchronized(lock) {
            sharedContext?.let { return it }
            val ctx = GraalContext.newBuilder("js")
                .allowAllAccess(true)
                .allowExperimentalOptions(true)
                .build()
            sharedContext = ctx
            return ctx
        }
    }
}
