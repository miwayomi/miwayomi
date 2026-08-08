package android.os

class Looper private constructor() {

    companion object {
        @Volatile
        private var mainLooper: Looper? = null

        @JvmStatic
        fun getMainLooper(): Looper {
            val existing = mainLooper
            if (existing != null) return existing
            synchronized(this) {
                return mainLooper ?: Looper().also { mainLooper = it }
            }
        }

        @JvmStatic
        fun myLooper(): Looper? {
            val name = Thread.currentThread().name
            return if (name == "main" || name.startsWith("main-")) getMainLooper() else null
        }
    }
}
