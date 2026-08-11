package miwayomi.update

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import io.ktor.server.engine.EmbeddedServer
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Duration
import java.util.concurrent.atomic.AtomicReference

const val MIWAYOMI_VERSION = "0.2.5"
const val MIWAYOMI_REPO = "miwayomi/miwayomi"

@Serializable
data class UpdateInfo(
    val currentVersion: String = MIWAYOMI_VERSION,
    val latestVersion: String? = null,
    val available: Boolean = false,
    val downloaded: Boolean = false,
    val url: String? = null,
    val applied: Boolean = false,
)

object UpdateManager {
    private val json = Json { ignoreUnknownKeys = true }
    private val client: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(8))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()

    private val state = AtomicReference(UpdateInfo())
    private var dataDir: File = File("data")
    private var launchArgs: Array<String> = emptyArray()
    private var server: EmbeddedServer<*, *>? = null

    /** Original command-line arguments, used to relaunch the server after an update. */
    fun setLaunchArgs(args: Array<String>) {
        launchArgs = args
    }

    /** Registers the running server so it can be stopped (and the port released) on relaunch. */
    fun attachServer(s: EmbeddedServer<*, *>) {
        server = s
    }

    fun configure(dir: File) {
        dataDir = dir
        try {
            val saved = File(dir, "update/latest.json")
            if (saved.isFile) {
                val info = json.decodeFromString(UpdateInfo.serializer(), saved.readText())
                if (info.downloaded) state.set(info)
            }
        } catch (e: Exception) {
            // corrupted save: ignore
        }
    }

    fun info(): UpdateInfo = state.get()

    fun start() {
        val t = Thread({ check() }, "miwayomi-update")
        t.isDaemon = true
        t.start()
    }

    /**
     * Applies a previously downloaded update (data/update/miwayomi-all.jar.new) by
     * replacing the running jar. Works on macOS/Linux even while the JVM is running;
     * on Windows the running jar is locked, so the swap is left pending for the
     * launcher script. Returns true when the jar on disk was replaced.
     */
    fun applyPendingUpdate(dir: File): Boolean {
        val upd = File(dir, "update")
        val pending = File(upd, "miwayomi-all.jar.new")
        if (!pending.isFile) return false

        if (pending.length() < 1_000_000) {
            println("[miwayomi] pending update looks invalid, ignoring it")
            runCatching { pending.delete() }
            return false
        }

        val runningJar = runningJarFile()
        if (runningJar == null || !runningJar.isFile) {
            println("[miwayomi] pending update found but the running jar could not be located")
            return false
        }

        val replaced = try {
            runCatching {
                Files.move(pending.toPath(), runningJar.toPath(), StandardCopyOption.REPLACE_EXISTING)
                true
            }.getOrElse {
                try {
                    pending.copyTo(runningJar, overwrite = true)
                    true
                } catch (e: Exception) {
                    false
                }
            }
        } catch (e: Exception) {
            false
        }

        return if (replaced) {
            println("[miwayomi] update applied: replaced ${runningJar.name} (relaunch to activate)")
            runCatching { pending.delete() }
            runCatching { File(upd, "latest.json").delete() }
            state.set(UpdateInfo(currentVersion = MIWAYOMI_VERSION, latestVersion = null, available = false, downloaded = false, applied = true))
            true
        } else {
            println("[miwayomi] update is pending but the running jar could not be replaced (Windows keeps it locked); the launcher will apply it on next launch")
            false
        }
    }

    private fun runningJarFile(): File? {
        return try {
            val loc = UpdateManager::class.java.protectionDomain?.codeSource?.location ?: return null
            val f = runCatching { File(loc.toURI()) }.getOrNull() ?: File(loc.path)
            if (f.isFile && f.name.endsWith(".jar")) f else null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Relaunches the server with the same java command and original arguments. In a
     * background thread it lets the HTTP response flush, stops the running server to
     * release the port, spawns the new process, and then exits this JVM. Best-effort;
     * returns false when the command line cannot be rebuilt.
     */
    fun relaunch(): Boolean {
        return try {
            val jar = runningJarFile() ?: return false
            val isWin = System.getProperty("os.name").lowercase().contains("win")
            val javaExe = File(System.getProperty("java.home"), "bin/java" + if (isWin) ".exe" else "")
            if (!javaExe.isFile) {
                println("[miwayomi] java executable not found at ${javaExe}")
                return false
            }
            val cmd = mutableListOf(javaExe.absolutePath, "-jar", jar.absolutePath)
            cmd.addAll(launchArgs)
            val log = File(File(dataDir, "update"), "relaunch.log")
            log.parentFile?.mkdirs()
            Thread({
                try {
                    // Let the HTTP response reach the client first.
                    Thread.sleep(1500)
                    // Stop the current server so the new process can bind the port.
                    server?.let { runCatching { it.stop(100, 2000) } }
                    ProcessBuilder(cmd)
                        .redirectOutput(ProcessBuilder.Redirect.appendTo(log))
                        .redirectErrorStream(true)
                        .start()
                    println("[miwayomi] relaunched: ${cmd.joinToString(" ")}")
                    Thread.sleep(500)
                    Runtime.getRuntime().halt(0)
                } catch (e: Exception) {
                    println("[miwayomi] relaunch failed: $e")
                    runCatching { Runtime.getRuntime().halt(0) }
                }
            }, "miwayomi-relaunch").start()
            true
        } catch (e: Exception) {
            println("[miwayomi] relaunch failed: $e")
            false
        }
    }

    private fun check() {
        try {
            val latest = fetchLatest()

            // If we just applied the new jar at startup, the running process is still the
            // previous version: don't re-download, just surface that a relaunch is needed.
            if (state.get().applied) {
                state.set(UpdateInfo(currentVersion = MIWAYOMI_VERSION, latestVersion = latest?.tag, available = false, downloaded = false, applied = true))
                return
            }

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
                // not critical
            }
        } catch (e: Exception) {
            // no network / API down: keep the default state
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
