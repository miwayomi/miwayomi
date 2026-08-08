package android.view

import android.content.Context
import android.util.AttributeSet

open class View(context: Context?) {

    constructor(context: Context?, attrs: AttributeSet?) : this(context)
    private var visibility: Int = VISIBLE

    companion object {
        const val VISIBLE = 0
        const val INVISIBLE = 4
        const val GONE = 8
    }

    open fun setVisibility(v: Int) {
        visibility = v
    }

    fun getVisibility(): Int = visibility

    open fun setLayoutParams(params: Any?) {}

    open fun measure(widthMeasureSpec: Int, heightMeasureSpec: Int) {}
}
