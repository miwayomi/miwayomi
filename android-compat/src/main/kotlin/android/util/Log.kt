package android.util

import java.io.PrintStream

object Log {

    @JvmStatic
    fun v(tag: String, msg: String): Int = println(PrintStream(System.out), "V", tag, msg)

    @JvmStatic
    fun d(tag: String, msg: String): Int = println(PrintStream(System.out), "D", tag, msg)

    @JvmStatic
    fun i(tag: String, msg: String): Int = println(PrintStream(System.out), "I", tag, msg)

    @JvmStatic
    fun w(tag: String, msg: String): Int = println(PrintStream(System.out), "W", tag, msg)

    @JvmStatic
    fun e(tag: String, msg: String): Int = println(PrintStream(System.err), "E", tag, msg)

    @JvmStatic
    fun v(tag: String, msg: String, tr: Throwable): Int = println(PrintStream(System.out), "V", tag, "$msg\n${tr.stackTraceToString()}")

    @JvmStatic
    fun d(tag: String, msg: String, tr: Throwable): Int = println(PrintStream(System.out), "D", tag, "$msg\n${tr.stackTraceToString()}")

    @JvmStatic
    fun i(tag: String, msg: String, tr: Throwable): Int = println(PrintStream(System.out), "I", tag, "$msg\n${tr.stackTraceToString()}")

    @JvmStatic
    fun w(tag: String, msg: String, tr: Throwable): Int = println(PrintStream(System.err), "W", tag, "$msg\n${tr.stackTraceToString()}")

    @JvmStatic
    fun e(tag: String, msg: String, tr: Throwable): Int = println(PrintStream(System.err), "E", tag, "$msg\n${tr.stackTraceToString()}")

    @JvmStatic
    fun isLoggable(tag: String, level: Int): Boolean = level >= Log.INFO

    @JvmStatic
    fun getStackTraceString(tr: Throwable): String = tr.stackTraceToString()

    const val VERBOSE = 2
    const val DEBUG = 3
    const val INFO = 4
    const val WARN = 5
    const val ERROR = 6
    const val ASSERT = 7

    private fun println(ps: PrintStream, level: String, tag: String, msg: String): Int {
        ps.println("[$level/$tag] $msg")
        return msg.length
    }
}
