package miwayomi.update

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.atomic.AtomicReference

const val MIWAYOMI_VERSION = "0.2.0"
const val MIWAYOMI_REPO = "miwayomi/miwayomi"

@Serializable
data class UpdateInfo(
    val currentVersion: String = MIWAYOMI_VERSION,
    val latestVersion: String? = null,
    val available: Boolean = false,
    val downloaded: Boolean = false,
    val url: String? = null,
)

object UpdateManager {
    private val json = Json { ignoreUnknownKeys = true }
    private val client: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(8))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()

    private val state = AtomicReference(UpdateInfo())
    private var dataDir: File = File("data")

    fun configure(dir: File) {
        dataDir = dir
        try {
            val saved = File(dir, "update/latest.json")
            if (saved.isFile) {
                val info = json.decodeFromString(UpdateInfo.serializer(), saved.readText())
                if (info.downloaded) state.set(info)
            }
        } catch (e: Exception) {
            // guardado corrupto: se ignora
        }
    }

    fun info(): UpdateInfo = state.get()

    fun start() {
        val t = Thread({ check() }, "miwayomi-update")
        t.isDaemon = true
        t.start()
    }

    private fun check() {
        try {
            val latest = fetchLatest()
            val avail = latest != null && compareVersions(latest.tag, MIWAYOMI_VERSION) > 0
            if (!avail || latest == null) {
                state.set(UpdateInfo(currentVersion = MIWAYOMI_VERSION, latestVersion = latest?.tag, available = false))
                return
            }
            val downloaded = downloadJar(latest.jarUrl)
            state.set(
                UpdateInfo(
                    currentVersion = MIWAYOMI_VERSION,
                    latestVersion = latest.tag,
                    available = true,
                    downloaded = downloaded,
                    url = latest.jarUrl,
                ),
            )
            try {
                val f = File(dataDir, "update/latest.json")
                f.parentFile?.mkdirs()
                f.writeText(json.encodeToString(UpdateInfo.serializer(), state.get()))
            } catch (e: Exception) {
                // no crítico
            }
        } catch (e: Exception) {
            // sin red / API caída: se deja el estado por defecto
        }
    }

    private data class Latest(val tag: String, val jarUrl: String)

    private fun fetchLatest(): Latest? {
        return try {
            val req = HttpRequest.newBuilder(URI.create("https://api.github.com/repos/$MIWAYOMI_REPO/releases/latest"))
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "miwayomi-updater")
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build()
            val res = client.send(req, HttpResponse.BodyHandlers.ofString())
            if (res.statusCode() !in 200..299) return null
            val obj = json.parseToJsonElement(res.body()).jsonObject
            val tag = obj["tag_name"]?.jsonPrimitive?.content?.removePrefix("v") ?: return null
            val jarUrl = obj["assets"]?.jsonArray?.firstOrNull { a ->
                a.jsonObject["name"]?.jsonPrimitive?.content == "miwayomi-all.jar"
            }?.jsonObject?.get("browser_download_url")?.jsonPrimitive?.content
            if (jarUrl == null) null else Latest(tag, jarUrl)
        } catch (e: Exception) {
            null
        }
    }

    private fun downloadJar(url: String): Boolean {
        return try {
            val dir = File(dataDir, "update")
            dir.mkdirs()
            val dest = File(dir, "miwayomi-all.jar.new")
            val req = HttpRequest.newBuilder(URI.create(url))
                .header("User-Agent", "miwayomi-updater")
                .timeout(Duration.ofSeconds(120))
                .GET()
                .build()
            val res = client.send(req, HttpResponse.BodyHandlers.ofInputStream())
            if (res.statusCode() !in 200..299) return false
            res.body().use { input -> dest.outputStream().use { output -> input.copyTo(output) } }
            dest.length() > 1_000_000
        } catch (e: Exception) {
            false
        }
    }

    private fun compareVersions(a: String, b: String): Int {
        val pa = a.split('.', '-').mapNotNull { it.toIntOrNull() }
        val pb = b.split('.', '-').mapNotNull { it.toIntOrNull() }
        val n = maxOf(pa.size, pb.size)
        for (i in 0 until n) {
            val x = pa.getOrElse(i) { 0 }
            val y = pb.getOrElse(i) { 0 }
            if (x != y) return x - y
        }
        return 0
    }
}
