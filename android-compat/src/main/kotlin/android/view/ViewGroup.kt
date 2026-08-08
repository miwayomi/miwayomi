package android.view

import android.content.Context
import android.util.AttributeSet

open class ViewGroup(context: Context?) : View(context) {

    constructor(context: Context?, attrs: AttributeSet?) : this(context)

    open fun addView(child: View, index: Int = -1, params: Any? = null) {}

    open fun removeAllViews() {}

    override fun setLayoutParams(params: Any?) {}

    open class LayoutParams {
        var width: Int = -1
        var height: Int = -1

        constructor(width: Int, height: Int) {
            this.width = width
            this.height = height
        }

        constructor()

        companion object {
            const val MATCH_PARENT = -1
            const val WRAP_CONTENT = -2
        }
    }
}
