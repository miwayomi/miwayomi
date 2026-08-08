package android.os

class Message {
    var what: Int = 0
    var arg1: Int = 0
    var arg2: Int = 0
    var obj: Any? = null

    private var target: Handler? = null

    fun getTarget(): Handler? = target
    fun setTarget(handler: Handler?) {
        target = handler
    }

    fun sendToTarget() {
        target?.sendMessage(this)
    }

    companion object {
        fun obtain(): Message = Message()
        fun obtain(what: Int): Message = Message().apply { this.what = what }
    }
}
