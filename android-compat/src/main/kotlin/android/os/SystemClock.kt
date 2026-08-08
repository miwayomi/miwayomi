package android.os

object SystemClock {

    @JvmStatic
    fun elapsedRealtime(): Long = System.nanoTime() / 1_000_000L

    @JvmStatic
    fun uptimeMillis(): Long = System.nanoTime() / 1_000_000L

    @JvmStatic
    fun currentThreadTimeMillis(): Long = System.currentTimeMillis()

    @JvmStatic
    fun sleep(ms: Long) = Thread.sleep(ms)
}
