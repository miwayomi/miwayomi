package miwayomi.desktop

import miwayomi.ServerConfig
import miwayomi.buildServer
import miwayomi.freePort
import miwayomi.openBrowser
import miwayomi.parseArgs
import java.io.File
import java.util.concurrent.CountDownLatch

fun main(args: Array<String>) {
    var config = parseArgs(args)
    if (config.port <= 0) config = config.copy(port = freePort())
    if (!args.any { it == "--data" || it == "-d" }) {
        config = config.copy(dataDir = File(System.getProperty("user.home"), ".miwayomi/data"))
    }
    config = config.copy(host = "127.0.0.1", openBrowser = false)

    val server = buildServer(config)
    server.start(wait = false)

    val url = "http://127.0.0.1:${config.port}/"
    println("[miwayomi] ventana propia -> $url")

    val proc: Process? = findBrowser()?.let { browser ->
        val profile = File(System.getProperty("user.home"), ".cache/miwayomi-app")
        profile.mkdirs()
        ProcessBuilder(browser, "--app=$url", "--user-data-dir=${profile.absolutePath}")
            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
            .redirectError(ProcessBuilder.Redirect.DISCARD)
            .start()
    }

    if (proc != null) {
        proc.waitFor()
        runCatching { server.stop(100, 3000) }
    } else {
        openBrowser(config.port)
        CountDownLatch(1).await()
    }
}

fun findBrowser(): String? {
    val pf = System.getenv("ProgramFiles") ?: "C:\\Program Files"
    val pf86 = System.getenv("ProgramFiles(x86)") ?: "C:\\Program Files (x86)"
    val windows = listOf(
        "$pf86\\Microsoft\\Edge\\Application\\msedge.exe",
        "$pf\\Microsoft\\Edge\\Application\\msedge.exe",
        "$pf\\Google\\Chrome\\Application\\chrome.exe",
        "$pf86\\Google\\Chrome\\Application\\chrome.exe",
    )
    for (p in windows) if (File(p).isFile) return p

    val mac = listOf(
        "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome",
        "/Applications/Microsoft Edge.app/Contents/MacOS/Microsoft Edge",
        "/Applications/Chromium.app/Contents/MacOS/Chromium",
    )
    for (p in mac) if (File(p).isFile) return p

    for (name in listOf("google-chrome", "google-chrome-stable", "chromium", "chromium-browser", "microsoft-edge")) {
        try {
            val p = ProcessBuilder("which", name).redirectErrorStream(true).start()
            val out = p.inputStream.bufferedReader().readText().trim()
            if (p.waitFor() == 0 && out.isNotBlank()) return out
        } catch (e: Exception) {
        }
    }
    return null
}
