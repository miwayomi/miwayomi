package miwayomi

import android.app.Application
import android.compat.CompatRuntime
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import miwayomi.api.registerApi
import miwayomi.builtin.DemoSource
import miwayomi.builtin.MockCfSource
import miwayomi.di.AppModule
import miwayomi.di.ConfigHolder
import miwayomi.extension.ExtensionManager
import miwayomi.source.MangaSourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

fun main(args: Array<String>) {
    val config = parseArgs(args)

    CompatRuntime.setup(config.dataDir)
    Application.current = Application.create()

    ConfigHolder.config = config
    Injekt.importModule(AppModule)

    if (config.flareSolverrUrl != null) {
        println("[miwayomi] FlareSolverr habilitado en ${config.flareSolverrUrl}")
    }

    val extensionManager = Injekt.get<ExtensionManager>()
    val loaded = extensionManager.loadAll()
    println("[miwayomi] $loaded extensión(es) cargada(s)")

    Injekt.get<MangaSourceManager>().register(DemoSource())
    Injekt.get<MangaSourceManager>().register(MockCfSource())

    val server = embeddedServer(Netty, port = config.port, host = config.host) {
        registerApi()
    }

    println("""
        |
        |  miwayomi corriendo en http://${config.host}:${config.port}
        |  datos en: ${config.dataDir.absolutePath}
        |  API:      http://${config.host}:${config.port}/api/v1
        |  WebUI:    http://${config.host}:${config.port}/
        |
    """.trimMargin())

    server.start(wait = true)
}
