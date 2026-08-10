package miwayomi.api

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import miwayomi.update.UpdateManager

fun Application.registerUpdateApi() {
    routing {
        get("/api/v1/update") {
            call.respond(UpdateManager.info())
        }

        // Asks the server to relaunch itself (used by the update gate page). The
        // new process is spawned shortly after, and this one stops and exits.
        post("/api/v1/update/relaunch") {
            if (UpdateManager.relaunch()) {
                call.respond(mapOf("ok" to true))
            } else {
                call.respond(HttpStatusCode.InternalServerError, ErrorDto("relaunch failed"))
            }
        }
    }
}
