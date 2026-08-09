package miwayomi

import java.io.File
import java.net.ServerSocket

data class ServerConfig(
    val host: String = "0.0.0.0",
    val port: Int = 0,
    val dataDir: File = File("data"),
    val flareSolverrUrl: String? = "http://127.0.0.1:8191",
    val chromePath: String? = null,
    val openBrowser: Boolean = true,
) {
    val extensionsDir: File get() = File(dataDir, "extensions")
}

fun freePort(): Int = ServerSocket(0).use { it.localPort }

fun parseArgs(args: Array<String>): ServerConfig {
    var config = ServerConfig()
    var i = 0
    while (i < args.size) {
        when (args[i]) {
            "--port", "-p" -> config = config.copy(port = args.getOrNull(i + 1)?.toIntOrNull() ?: config.port).also { i++ }
            "--host", "-h" -> config = config.copy(host = args.getOrNull(i + 1) ?: config.host).also { i++ }
            "--data", "-d" -> config = config.copy(dataDir = File(args.getOrNull(i + 1) ?: config.dataDir.path)).also { i++ }
            "--flaresolverr", "-f" -> {
                val url = args.getOrNull(i + 1)?.takeIf { it.isNotBlank() }
                config = config.copy(flareSolverrUrl = url).also { i++ }
            }
            "--chrome" -> {
                val path = args.getOrNull(i + 1)?.takeIf { it.isNotBlank() }
                config = config.copy(chromePath = path).also { i++ }
            }
            "--no-open" -> config = config.copy(openBrowser = false)
        }
        i++
    }
    if (config.port <= 0) config = config.copy(port = freePort())
    return config
}
