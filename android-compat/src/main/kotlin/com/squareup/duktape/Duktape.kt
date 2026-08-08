package com.squareup.duktape

import org.graalvm.polyglot.Context as GraalContext

class Duktape private constructor(
    private val context: GraalContext,
) : AutoCloseable {

    fun evaluate(script: String): Any? {
        val value = context.eval("js", script)
        return when {
            value.isString -> value.asString()
            value.isBoolean -> value.asBoolean()
            value.isNumber -> value.asDouble()
            value.isNull -> null
            else -> value.toString()
        }
    }

    fun <T> set(name: String, type: Class<T>, obj: T) {
        context.getBindings("js").putMember(name, obj)
    }

    override fun close() {
        try {
            context.close()
        } catch (_: Throwable) {
        }
    }

    companion object {
        @JvmStatic
        fun create(): Duktape {
            val ctx = GraalContext.newBuilder("js")
                .allowAllAccess(true)
                .allowExperimentalOptions(true)
                .build()
            return Duktape(ctx)
        }
    }
}
