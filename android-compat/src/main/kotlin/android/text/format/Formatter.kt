package android.text.format

import java.util.Locale

object Formatter {

    @JvmStatic
    fun formatFileSize(context: android.content.Context?, sizeBytes: Long): String =
        formatFileSize(sizeBytes)

    @JvmStatic
    fun formatFileSize(sizeBytes: Long): String {
        if (sizeBytes < 1024) return "$sizeBytes B"
        val units = arrayOf("KB", "MB", "GB", "TB", "PB")
        var size = sizeBytes.toDouble()
        var unit = -1
        while (size >= 1024 && unit < units.size - 1) {
            size /= 1024
            unit++
        }
        return String.format(Locale.US, "%.1f %s", size, units[unit])
    }

    @JvmStatic
    fun formatShortFileSize(context: android.content.Context?, sizeBytes: Long): String =
        formatFileSize(sizeBytes)

    @JvmStatic
    fun formatIpAddress(ipv4Address: Int): String {
        return "${(ipv4Address shr 24) and 0xFF}.${(ipv4Address shr 16) and 0xFF}." +
            "${(ipv4Address shr 8) and 0xFF}.${ipv4Address and 0xFF}"
    }
}
