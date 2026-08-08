package android.content

open class ContextThemeWrapper(
    base: Context? = null,
) : ContextWrapper(base) {

    override fun getTheme(): Any? = null

    fun setTheme(resId: Int) {}
}
