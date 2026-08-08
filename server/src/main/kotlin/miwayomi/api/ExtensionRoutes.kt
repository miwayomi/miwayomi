package miwayomi.api

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import miwayomi.di.ConfigHolder
import miwayomi.extension.ExtensionManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File

val DEFAULT_REPO_URL = "https://raw.githubusercontent.com/yuzono/anime-repo/repo/index.min.json"

@Serializable
data class RepoSourceDto(val name: String, val lang: String)

@Serializable
data class RepoEntryDto(
    val name: String,
    val pkg: String,
    val lang: String,
    val nsfw: Boolean,
    val apk: String,
    val version: String,
    val installed: Boolean,
    val sources: List<RepoSourceDto> = emptyList(),
)

@Serializable
data class RepoListDto(
    val repo: String,
    val total: Int,
    val installed: Int,
    val extensions: List<RepoEntryDto>,
)

@Serializable
data class InstallRequestDto(
    val repoUrl: String = DEFAULT_REPO_URL,
    val apk: String,
)

@Serializable
data class InstallResultDto(
    val ok: Boolean,
    val name: String? = null,
    val pkg: String? = null,
    val manga: Int = 0,
    val anime: Int = 0,
    val error: String? = null,
)

@Serializable
data class UninstallRequestDto(
    val pkg: String,
)

@Serializable
data class UninstallResultDto(
    val ok: Boolean,
    val name: String? = null,
    val pkg: String? = null,
    val error: String? = null,
)

fun Application.registerExtensionApi() {
    val json = Json { ignoreUnknownKeys = true }
    val extensionManager = Injekt.get<ExtensionManager>()
    val extensionsDir = ConfigHolder.config.extensionsDir

    routing {
        get("/api/v1/extensions/repo") {
            val repo = call.request.queryParameters["url"]?.takeIf { it.isNotBlank() } ?: DEFAULT_REPO_URL
            val client = Injekt.get<NetworkHelper>().client

            val response = client.newCall(GET(repo)).execute()
            if (!response.isSuccessful) {
                val code = response.code
                response.close()
                return@get call.respond(HttpStatusCode.BadGateway, ErrorDto("error al leer el repo ($code)"))
            }
            val bytes = response.body.bytes()
            response.close()

            val installedPkgs = extensionManager.loaded.map { it.meta.pkgName }.toSet()
            val entries = try {
                if (isKeiProto(repo, bytes)) {

                    parseKeiProto(bytes).map {
                        it.copy(installed = it.pkg.isNotEmpty() && installedPkgs.contains(it.pkg))
                    }
                } else {

                    val text = bytes.toString(Charsets.UTF_8)
                    json.parseToJsonElement(text).jsonArray.map { el ->
                        val o = el.jsonObject
                        val apk = o["apk"]?.jsonPrimitive?.contentOrNull.orEmpty()
                        val pkg = o["pkg"]?.jsonPrimitive?.contentOrNull ?: ""
                        RepoEntryDto(
                            name = o["name"]?.jsonPrimitive?.contentOrNull ?: "",
                            pkg = pkg,
                            lang = o["lang"]?.jsonPrimitive?.contentOrNull ?: "",
                            nsfw = (o["nsfw"]?.jsonPrimitive?.contentOrNull ?: "0") == "1",
                            apk = apk,
                            version = o["version"]?.jsonPrimitive?.contentOrNull ?: "",
                            installed = apk.isNotEmpty() &&
                                (installedPkgs.contains(pkg) || (!apk.startsWith("http") && File(extensionsDir, apk).exists())),
                            sources = (o["sources"]?.jsonArray ?: emptyList()).mapNotNull { s ->
                                val so = s.jsonObject
                                RepoSourceDto(
                                    name = so["name"]?.jsonPrimitive?.contentOrNull ?: "",
                                    lang = so["lang"]?.jsonPrimitive?.contentOrNull ?: "",
                                )
                            },
                        )
                    }
                }
            } catch (e: Exception) {
                return@get call.respond(HttpStatusCode.BadGateway, ErrorDto("índice inválido: ${e.message}"))
            }

            call.respond(RepoListDto(repo, entries.size, entries.count { it.installed }, entries))
        }

        post("/api/v1/extensions/install") {
            val req = call.receive<InstallRequestDto>()
            val apkName = req.apk

            val url = if (apkName.startsWith("http://") || apkName.startsWith("https://")) {
                apkName
            } else {
                val repoBase = req.repoUrl.substringBeforeLast('/')
                "$repoBase/apk/$apkName"
            }
            val destName = url.substringAfterLast('/')
            if (!destName.endsWith(".apk", ignoreCase = true)) {
                return@post call.respond(HttpStatusCode.BadRequest, InstallResultDto(ok = false, error = "nombre de apk inválido"))
            }

            val client = Injekt.get<NetworkHelper>().client
            val response = client.newCall(GET(url)).execute()
            if (!response.isSuccessful) {
                val code = response.code
                response.close()
                return@post call.respond(HttpStatusCode.BadGateway, InstallResultDto(ok = false, error = "descarga fallida ($code)"))
            }
            val bytes = response.body.bytes()
            response.close()

            val dest = File(extensionsDir, destName)
            val tmp = File(extensionsDir, destName + ".part")
            runCatching { tmp.parentFile?.mkdirs() }
            tmp.writeBytes(bytes)
            if (!tmp.renameTo(dest)) {
                dest.writeBytes(bytes)
                tmp.delete()
            }

            val loaded = try {
                extensionManager.load(dest)
            } catch (e: Throwable) {
                val root = generateSequence(e) { it.cause }.lastOrNull() ?: e
                return@post call.respond(HttpStatusCode.InternalServerError, InstallResultDto(ok = false, error = root.message ?: e.message ?: "error al cargar"))
            }

            call.respond(
                InstallResultDto(
                    ok = true,
                    name = loaded.meta.name,
                    pkg = loaded.meta.pkgName,
                    manga = loaded.manga,
                    anime = loaded.anime,
                ),
            )
        }

        post("/api/v1/extensions/uninstall") {
            val req = call.receive<UninstallRequestDto>()
            val pkg = req.pkg.trim()
            if (pkg.isEmpty()) {
                return@post call.respond(HttpStatusCode.BadRequest, UninstallResultDto(ok = false, error = "pkg vacío"))
            }
            val removed = extensionManager.uninstall(pkg)
            if (removed == null) {
                return@post call.respond(HttpStatusCode.NotFound, UninstallResultDto(ok = false, error = "extensión no instalada"))
            }
            call.respond(UninstallResultDto(ok = true, name = removed.meta.name, pkg = pkg))
        }
    }
}
