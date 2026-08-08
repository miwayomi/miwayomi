package miwayomi.api

fun inferContentType(url: String, upstream: String?): String {
    if (upstream != null && !upstream.equals("application/octet-stream", ignoreCase = true)) {
        return upstream
    }
    val lower = url.substringBefore('?').substringBefore('#').lowercase()

    val videoTypes = mapOf(
        "mp4" to "video/mp4", "m4v" to "video/mp4", "m4p" to "video/mp4",
        "m4b" to "video/mp4", "m4s" to "video/mp4", "mov" to "video/quicktime",
        "qt" to "video/quicktime", "webm" to "video/webm", "mkv" to "video/x-matroska",
        "avi" to "video/x-msvideo", "mpg" to "video/mpeg", "mpeg" to "video/mpeg",
        "mpe" to "video/mpeg", "m2v" to "video/mpeg", "mpv" to "video/mpeg",
        "m1v" to "video/mpeg", "ts" to "video/mp2t", "m2ts" to "video/mp2t",
        "mts" to "video/mp2t", "flv" to "video/x-flv", "wmv" to "video/x-ms-wmv",
        "asf" to "video/x-ms-asf", "3gp" to "video/3gpp", "3g2" to "video/3gpp2",
        "ogv" to "video/ogg", "ogg" to "video/ogg", "rm" to "application/vnd.rn-realmedia",
        "rmvb" to "application/vnd.rn-realmedia-vbr", "vob" to "video/dvd",
        "f4v" to "video/mp4", "mxf" to "application/mxf", "mtsv" to "video/mp2t",
        "av1" to "video/av1", "vp9" to "video/vp9", "divx" to "video/x-msvideo",
        "hevc" to "video/hevc", "265" to "video/hevc", "mk3d" to "video/x-matroska",
        "nut" to "video/x-nut", "ogm" to "video/ogg", "tsa" to "video/mp2t",
        "tsv" to "video/mp2t",
    )

    val audioTypes = mapOf(
        "mp3" to "audio/mpeg", "mp2" to "audio/mpeg", "m4a" to "audio/mp4",
        "aac" to "audio/aac", "wav" to "audio/wav", "oga" to "audio/ogg",
        "opus" to "audio/opus", "flac" to "audio/flac", "wma" to "audio/x-ms-wma",
        "ac3" to "audio/ac3", "eac3" to "audio/eac3", "dts" to "audio/vnd.dts",
        "alac" to "audio/mp4", "aiff" to "audio/aiff", "aif" to "audio/aiff",
        "ape" to "audio/ape", "mka" to "audio/x-matroska", "mpa" to "audio/mpeg",
        "mid" to "audio/midi", "midi" to "audio/midi", "weba" to "audio/webm",
        "amr" to "audio/amr", "ra" to "audio/x-pn-realaudio", "caf" to "audio/x-caf",
        "m4r" to "audio/mp4",
    )

    val subTypes = mapOf(
        "srt" to "application/x-subrip", "vtt" to "text/vtt", "webvtt" to "text/vtt",
        "ass" to "text/x-ssa", "ssa" to "text/x-ssa", "sub" to "text/plain",
        "idx" to "text/plain", "sup" to "application/octet-stream",
        "ttml" to "application/ttml+xml", "dfxp" to "application/ttml+xml",
        "smi" to "application/smil", "sami" to "application/smil",
        "sbv" to "text/plain", "mpsub" to "text/plain", "jss" to "text/plain",
        "usf" to "text/plain", "rt" to "text/plain", "stl" to "text/plain",
        "pgs" to "application/octet-stream", "vtts" to "text/vtt",
    )

    val playlistTypes = mapOf(
        "m3u8" to "application/vnd.apple.mpegurl", "m3u" to "application/vnd.apple.mpegurl",
        "mpd" to "application/dash+xml", "m4s" to "video/mp4",
        "ism" to "application/vnd.ms-sstr+xml", "ismc" to "application/vnd.ms-sstr+xml",
        "ismv" to "video/mp4", "isma" to "audio/mp4", "f4m" to "application/f4m+xml",
        "hls" to "application/vnd.apple.mpegurl", "vtt" to "text/vtt",
    )

    val imageTypes = mapOf(
        "jpg" to "image/jpeg", "jpeg" to "image/jpeg", "png" to "image/png",
        "webp" to "image/webp", "gif" to "image/gif", "avif" to "image/avif",
        "bmp" to "image/bmp", "svg" to "image/svg+xml",
    )

    val ext = lower.substringAfterLast('.')
        .takeIf { it.length in 1..6 && it.all { c -> c.isLetterOrDigit() } }
        ?: return upstream ?: "application/octet-stream"

    return videoTypes[ext]
        ?: audioTypes[ext]
        ?: subTypes[ext]
        ?: playlistTypes[ext]
        ?: imageTypes[ext]
        ?: upstream ?: "application/octet-stream"
}
