package tachiyomi.core.common.util.system

import logcat.LogPriority
import logcat.asLog

inline fun Any.logcat(
    priority: LogPriority = LogPriority.DEBUG,
    throwable: Throwable? = null,
    crossinline message: () -> String = { "" },
) = logcat.logcat(priority = priority) {
    var msg = message()
    if (throwable != null) {
        if (msg.isNotBlank()) msg += "\n"
        msg += throwable.asLog()
    }
    msg
}
