package miwayomi.api

import android.compat.CompatRuntime
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.SqliteStore
import eu.kanade.tachiyomi.network.StoredExtension
import eu.kanade.tachiyomi.source.MangaSource
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

/**
 * Persists the currently loaded extensions in the SQLite registry and prunes
 * entries whose files no longer exist. Called once at startup so the installed
 * list survives restarts even when the release ships empty (no bundled apk/jar).
 */
fun syncExtensionRegistry(extensionManager: ExtensionManager) {
    val store = SqliteStore(File(CompatRuntime.cacheDir, "miwayomi.db"))
    val now = System.currentTimeMillis()
    val loadedPkgs = extensionManager.loaded.map { it.meta.pkgName }.toSet()
    extensionManager.loaded.forEach { ext ->
        store.extensionUpsert(
            StoredExtension(
                pkg = ext.meta.pkgName,
                name = ext.meta.name,
                versionName = ext.meta.versionName,
                versionCode = ext.meta.versionCode,
                isNsfw = ext.meta.isNsfw,
                isAnime = ext.meta.isAnime,
                apkFile = ext.apk.name,
                jarFile = ext.jar.name,
                sourceCount = ext.manga + ext.anime,
                installedAt = now,
            ),
        )
    }
    store.extensionAll().forEach { stored ->
        if (stored.pkg !in loadedPkgs) store.extensionDelete(stored.pkg)
    }
    store.close()
}

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
    val repoUrl: String = "",
    val apk: String,
)

@Serializable
data class ReposDto(val repos: List<String>)

@Serializable
data class ReposSaveDto(val repos: List<String> = emptyList())

@Serializable
data class ReposResultDto(val ok: Boolean)

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
    val store = SqliteStore(File(CompatRuntime.cacheDir, "miwayomi.db"))

    routing {
        get("/api/v1/extensions/repo") {
            val repo = call.request.queryParameters["url"]?.takeIf { it.isNotBlank() }
                ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorDto("url required"))
            val client = Injekt.get<NetworkHelper>().client

            val response = client.newCall(GET(repo)).execute()
            if (!response.isSuccessful) {
                val code = response.code
                response.close()
                return@get call.respond(HttpStatusCode.BadGateway, ErrorDto("error reading repo ($code)"))
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
                return@get call.respond(HttpStatusCode.BadGateway, ErrorDto("invalid index: ${e.message}"))
            }

            call.respond(RepoListDto(repo, entries.size, entries.count { it.installed }, entries))
        }

        // Locally installed extensions only — no remote repository involved.
        // Keeps the registry (SQLite) in sync with what is actually loaded.
        get("/api/v1/extensions/installed") {
            val now = System.currentTimeMillis()
            val entries = extensionManager.loaded.map { ext ->
                store.extensionUpsert(
                    StoredExtension(
                        pkg = ext.meta.pkgName,
                        name = ext.meta.name,
                        versionName = ext.meta.versionName,
                        versionCode = ext.meta.versionCode,
                        isNsfw = ext.meta.isNsfw,
                        isAnime = ext.meta.isAnime,
                        apkFile = ext.apk.name,
                        jarFile = ext.jar.name,
                        sourceCount = ext.manga + ext.anime,
                        installedAt = now,
                    ),
                )
                RepoEntryDto(
                    name = ext.meta.name,
                    pkg = ext.meta.pkgName,
                    lang = "",
                    nsfw = ext.meta.isNsfw,
                    apk = ext.apk.name,
                    version = ext.meta.versionName,
                    installed = true,
                    sources = ext.sources.mapNotNull { s ->
                        val name = when (s) {
                            is MangaSource -> s.name
                            is AnimeSource -> s.name
                            else -> null
                        } ?: return@mapNotNull null
                        RepoSourceDto(name, "")
                    },
                )
            }
            call.respond(RepoListDto("installed", entries.size, entries.size, entries))
        }

        // User-added repository URLs, persisted in the database so they don't
        // have to be re-entered every time (no default repo is loaded).
        get("/api/v1/extensions/repos") {
            call.respond(ReposDto(store.reposGet()))
        }

        post("/api/v1/extensions/repos") {
            val req = runCatching { call.receive<ReposSaveDto>() }.getOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, ErrorDto("invalid body"))
            store.reposSet(req.repos)
            call.respond(ReposResultDto(ok = true))
        }

        post("/api/v1/extensions/install") {
            val req = call.receive<InstallRequestDto>()
            val apkName = req.apk

            val isAbsolute = apkName.startsWith("http://") || apkName.startsWith("https://")
            val repoBase = if (isAbsolute) null else req.repoUrl.substringBeforeLast('/')
            val url = if (isAbsolute) apkName else "$repoBase/apk/$apkName"
            val destName = url.substringAfterLast('/')
            if (!destName.endsWith(".apk", ignoreCase = true)) {
                return@post call.respond(HttpStatusCode.BadRequest, InstallResultDto(ok = false, error = "invalid apk name"))
            }

            val client = Injekt.get<NetworkHelper>().client
            val response = client.newCall(GET(url)).execute()
            if (!response.isSuccessful) {
                val code = response.code
                response.close()
                return@post call.respond(HttpStatusCode.BadGateway, InstallResultDto(ok = false, error = "download failed ($code)"))
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

            // Best-effort: some repos also publish a desktop JVM jar with the
            // same base name. Using it avoids the dex2jar DEX->JVM conversion,
            // which corrupts R8 output (bad `invokespecial <init>` owners) and
            // breaks the extension with a VerifyError. If the repo has no jar,
            // fall back to conversion.
            if (repoBase != null) {
                val jarName = destName.substringBeforeLast('.').plus(".jar")
                val jarUrl = "$repoBase/jar/$jarName"
                val jarResponse = try {
                    client.newCall(GET(jarUrl)).execute()
                } catch (_: Throwable) {
                    null
                }
                if (jarResponse != null) {
                    if (jarResponse.isSuccessful) {
                        val jarBytes = jarResponse.body.bytes()
                        jarResponse.close()
                        val jarDest = File(extensionsDir, jarName)
                        val jarTmp = File(extensionsDir, jarName + ".part")
                        jarTmp.writeBytes(jarBytes)
                        if (!jarTmp.renameTo(jarDest)) {
                            jarDest.writeBytes(jarBytes)
                            jarTmp.delete()
                        }
                        println("[miwayomi] downloaded desktop jar $jarName for $destName")
                    } else {
                        jarResponse.close()
                    }
                }
            }

            val loaded = try {
                extensionManager.load(dest)
            } catch (e: Throwable) {
                val root = generateSequence(e) { it.cause }.lastOrNull() ?: e
                return@post call.respond(HttpStatusCode.InternalServerError, InstallResultDto(ok = false, error = root.message ?: e.message ?: "error loading"))
            }

            store.extensionUpsert(
                StoredExtension(
                    pkg = loaded.meta.pkgName,
                    name = loaded.meta.name,
                    versionName = loaded.meta.versionName,
                    versionCode = loaded.meta.versionCode,
                    isNsfw = loaded.meta.isNsfw,
                    isAnime = loaded.meta.isAnime,
                    apkFile = loaded.apk.name,
                    jarFile = loaded.jar.name,
                    sourceCount = loaded.manga + loaded.anime,
                    installedAt = System.currentTimeMillis(),
                ),
            )

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
                return@post call.respond(HttpStatusCode.BadRequest, UninstallResultDto(ok = false, error = "empty pkg"))
            }
            val removed = extensionManager.uninstall(pkg)
            if (removed == null) {
                return@post call.respond(HttpStatusCode.NotFound, UninstallResultDto(ok = false, error = "extension not installed"))
            }
            store.extensionDelete(pkg)
            call.respond(UninstallResultDto(ok = true, name = removed.meta.name, pkg = pkg))
        }
    }
}
