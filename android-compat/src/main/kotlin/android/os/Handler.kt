package android.os

open class Handler {

    interface Callback {
        fun handleMessage(msg: Message): Boolean
    }

    private val callback: Callback?

    constructor() {
        callback = null
    }

    constructor(looper: Looper) {
        callback = null
    }

    constructor(callback: Callback) {
        this.callback = callback
    }

    constructor(looper: Looper, callback: Callback) {
        this.callback = callback
    }

    open fun post(r: Runnable): Boolean {
        r.run()
        return true
    }

    open fun postDelayed(r: Runnable, delayMillis: Long): Boolean {
        r.run()
        return true
    }

    open fun removeCallbacks(r: Runnable) {

    }

    open fun sendMessage(msg: Message): Boolean = true

    open fun obtainMessage(what: Int): Message = Message().apply { this.what = what }

    open fun dispatchMessage(msg: Message) {
        if (callback != null) {
            callback.handleMessage(msg)
        }
    }

    open fun handleMessage(msg: Message) {

    }
}
