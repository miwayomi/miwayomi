package logcat

var minLogPriority: LogPriority = LogPriority.DEBUG

var defaultTag: String = "miwayomi"

inline fun logcat(
    priority: LogPriority = LogPriority.DEBUG,
    tag: String = defaultTag,
    throwable: Throwable? = null,
    crossinline message: () -> String,
) {
    if (priority.ordinal < minLogPriority.ordinal) return
    val msg = message()
    val text = if (throwable != null) "$msg\n${throwable.stackTraceToString()}" else msg
    System.out.println("${priority.name[0]}(${Thread.currentThread().name}) $tag: $text")
}

fun Throwable.asLog(): String = stackTraceToString()
