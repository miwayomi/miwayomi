package miwayomi

import android.app.Application
import android.compat.CompatRuntime
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import miwayomi.api.registerApi
import miwayomi.builtin.DemoSource
import miwayomi.builtin.MockCfSource
import miwayomi.di.AppModule
import miwayomi.di.ConfigHolder
import miwayomi.extension.ExtensionManager
import miwayomi.source.MangaSourceManager
import miwayomi.update.UpdateManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.awt.Desktop
import java.net.URI
import java.util.concurrent.CountDownLatch

fun buildServer(config: ServerConfig): EmbeddedServer<*, *> {
    CompatRuntime.setup(config.dataDir)
    Application.current = Application.create()

    ConfigHolder.config = config
    Injekt.importModule(AppModule)

    UpdateManager.configure(config.dataDir)
    UpdateManager.start()

    if (config.flareSolverrUrl != null) {
        println("[miwayomi] FlareSolverr enabled at ${config.flareSolverrUrl}")
    }

    val extensionManager = Injekt.get<ExtensionManager>()
    val loaded = extensionManager.loadAll()
    println("[miwayomi] $loaded extension(s) loaded")

    Injekt.get<MangaSourceManager>().register(DemoSource())
    Injekt.get<MangaSourceManager>().register(MockCfSource())

    return embeddedServer(Netty, port = config.port, host = config.host) {
        registerApi()
    }
}

fun openBrowser(port: Int) {
    try {
        if (!Desktop.isDesktopSupported()) return
        val desktop = Desktop.getDesktop()
        if (!desktop.isSupported(Desktop.Action.BROWSE)) return
        desktop.browse(URI("http://127.0.0.1:$port/"))
    } catch (e: Exception) {
        // headless or no browser: ignore, the URL is still printed
    }
}

fun main(args: Array<String>) {
    val config = parseArgs(args)
    val server = buildServer(config)

    println("""
        |
        |  miwayomi running at http://${config.host}:${config.port}
        |  data in: ${config.dataDir.absolutePath}
        |  API:      http://${config.host}:${config.port}/api/v1
        |  WebUI:    http://${config.host}:${config.port}/
        |
    """.trimMargin())

    server.start(wait = false)
    if (config.openBrowser) openBrowser(config.port)
    CountDownLatch(1).await()
}
