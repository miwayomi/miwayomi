package miwayomi.api

import io.ktor.server.application.Application
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import miwayomi.update.UpdateManager

fun Application.registerUpdateApi() {
    routing {
        get("/api/v1/update") {
            call.respond(UpdateManager.info())
        }
    }
}
