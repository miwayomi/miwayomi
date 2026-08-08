package tachiyomi.core.common.util.system

import java.io.InputStream

object ImageUtil {

    fun isImage(name: String?, openStream: (() -> InputStream)? = null): Boolean {
        if (name == null) return false
        val extension = name.substringAfterLast('.').lowercase()
        return ImageType.entries.any { it.extension == extension }
    }

    fun getExtensionFromMimeType(mime: String?, openStream: () -> InputStream): String {
        val type = mime?.let { m -> ImageType.entries.find { it.mime == m } }
        return type?.extension ?: "jpg"
    }

    enum class ImageType(val mime: String, val extension: String) {
        AVIF("image/avif", "avif"),
        GIF("image/gif", "gif"),
        HEIF("image/heif", "heif"),
        JPEG("image/jpeg", "jpg"),
        JXL("image/jxl", "jxl"),
        PNG("image/png", "png"),
        WEBP("image/webp", "webp"),
    }
}
