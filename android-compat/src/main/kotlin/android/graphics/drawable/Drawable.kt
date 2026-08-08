package android.graphics.drawable

abstract class Drawable {
    var bounds: android.graphics.Rect? = null

    open fun setBounds(left: Int, top: Int, right: Int, bottom: Int) {
        bounds = android.graphics.Rect(left, top, right, bottom)
    }

    abstract fun draw(canvas: Any?)

    open fun getIntrinsicWidth(): Int = 0
    open fun getIntrinsicHeight(): Int = 0

    interface Callback {
        fun invalidateDrawable(who: Drawable)
        fun scheduleDrawable(who: Drawable, what: Runnable, `when`: Long)
        fun unscheduleDrawable(who: Drawable, what: Runnable)
    }
}
