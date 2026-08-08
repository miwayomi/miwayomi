package android.util

import java.util.Base64 as JavaBase64

object Base64 {

    const val DEFAULT = 0
    const val NO_PADDING = 1
    const val NO_WRAP = 2
    const val CRLF = 4
    const val URL_SAFE = 8

    @JvmStatic
    fun encodeToString(input: ByteArray, flags: Int): String {
        return String(encode(input, flags), Charsets.US_ASCII)
    }

    @JvmStatic
    fun encodeToString(input: ByteArray, offset: Int, len: Int, flags: Int): String {
        return encodeToString(input.copyOfRange(offset, offset + len), flags)
    }

    @JvmStatic
    fun encode(input: ByteArray, flags: Int): ByteArray {
        val urlSafe = flags and URL_SAFE != 0
        val noPadding = flags and NO_PADDING != 0
        val enc = if (urlSafe) JavaBase64.getUrlEncoder() else JavaBase64.getEncoder()
        var out = if (noPadding) enc.withoutPadding().encode(input) else enc.encode(input)
        if (flags and NO_WRAP != 0) {
            out = out.filterNot { it == '\n'.code.toByte() || it == '\r'.code.toByte() }.toByteArray()
        }
        return out
    }

    @JvmStatic
    fun decode(str: String, flags: Int): ByteArray {
        val urlSafe = flags and URL_SAFE != 0
        val dec = if (urlSafe) JavaBase64.getUrlDecoder() else JavaBase64.getDecoder()
        return dec.decode(str)
    }

    @JvmStatic
    fun decode(input: ByteArray, flags: Int): ByteArray {
        return decode(String(input, Charsets.US_ASCII), flags)
    }

    @JvmStatic
    fun decode(input: ByteArray, offset: Int, len: Int, flags: Int): ByteArray {
        return decode(input.copyOfRange(offset, offset + len), flags)
    }
}
