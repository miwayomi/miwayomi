package miwayomi.api

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.protobuf.ProtoNumber
import kotlinx.serialization.protobuf.ProtoBuf
import java.io.ByteArrayInputStream
import java.util.zip.GZIPInputStream

@Serializable
data class KeiMetadata(
    @ProtoNumber(1) val name: String? = null,
    @ProtoNumber(2) val lang: String? = null,
    @ProtoNumber(101) val extensions: KeiExtensionList? = null,
)

@Serializable
data class KeiExtensionList(
    @ProtoNumber(1) val extension: List<KeiExtension> = emptyList(),
)

@Serializable
data class KeiExtension(
    @ProtoNumber(1) val name: String? = null,
    @ProtoNumber(2) val pkg: String? = null,
    @ProtoNumber(3) val apk: KeiApk? = null,
    @ProtoNumber(5) val versionCode: Int = 0,
    @ProtoNumber(6) val versionName: String? = null,
    @ProtoNumber(8) val sources: List<KeiSource> = emptyList(),
)

@Serializable
data class KeiApk(
    @ProtoNumber(1) val url: String? = null,
    @ProtoNumber(2) val icon: String? = null,
    @ProtoNumber(501) val jarUrl: String? = null,
)

@Serializable
data class KeiSource(
    @ProtoNumber(1) val id: Long = 0,
    @ProtoNumber(2) val name: String? = null,
    @ProtoNumber(3) val lang: String? = null,
    @ProtoNumber(4) val baseUrl: String? = null,
)

fun isKeiProto(url: String, bytes: ByteArray): Boolean =
    url.lowercase().endsWith(".pb") ||
        (bytes.size >= 2 && bytes[0] == 0x1f.toByte() && bytes[1] == 0x8b.toByte())

fun parseKeiProto(bytes: ByteArray): List<RepoEntryDto> {
    val raw = if (bytes.size >= 2 && bytes[0] == 0x1f.toByte() && bytes[1] == 0x8b.toByte()) {
        GZIPInputStream(ByteArrayInputStream(bytes)).use { it.readBytes() }
    } else {
        bytes
    }
    val meta = ProtoBuf.decodeFromByteArray<KeiMetadata>(raw)
    return meta.extensions?.extension.orEmpty().map { e ->
        val pkg = e.pkg.orEmpty()
        val apkUrl = e.apk?.url.orEmpty()
        val sources = e.sources.map { RepoSourceDto(it.name.orEmpty(), it.lang.orEmpty()) }
        RepoEntryDto(
            name = e.name.orEmpty(),
            pkg = pkg,
            lang = sources.firstOrNull()?.lang ?: "",
            nsfw = false,
            apk = apkUrl,
            version = e.versionName.orEmpty(),
            installed = false,
            sources = sources,
        )
    }
}
