package app.cash.quickjs

import org.graalvm.polyglot.Context as GraalContext

class QuickJs private constructor(
    private val context: GraalContext,
) : AutoCloseable {

    fun evaluate(script: String): Any? {
        return evaluate(script, "")
    }

    fun evaluate(script: String, ignoredFileName: String): Any? {
        val value = context.eval("js", script)
        return when {
            value.isString -> value.asString()
            value.isBoolean -> value.asBoolean()
            value.isNumber -> value.asDouble()
            value.isNull -> null
            else -> value.toString()
        }
    }

    fun compile(sourceCode: String, ignoredFileName: String): ByteArray = sourceCode.toByteArray()

    fun execute(bytecode: ByteArray): Any? = evaluate(String(bytecode))

    fun <T> set(name: String, ignoredType: Class<T>, obj: T) {
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
        fun create(): QuickJs {
            val ctx = GraalContext.newBuilder("js")
                .allowAllAccess(true)
                .allowExperimentalOptions(true)
                .build()
            return QuickJs(ctx)
        }
    }
}
