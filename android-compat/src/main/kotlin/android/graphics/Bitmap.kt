package android.graphics

class Bitmap(
    val width: Int,
    val height: Int,
    val config: Config?,
) {
    enum class Config { ALPHA_8, RGB_565, ARGB_8888, RGBA_F16, HARDWARE }

    fun recycle() {}

    companion object {
        @JvmStatic
        fun createBitmap(width: Int, height: Int, config: Config?): Bitmap = Bitmap(width, height, config)
    }
}
