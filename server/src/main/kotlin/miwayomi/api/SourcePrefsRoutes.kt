package miwayomi.api

import android.app.Application as AndroidApp
import android.content.SharedPreferences
import androidx.preference.CheckBoxPreference
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.MultiSelectListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceGroup
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import androidx.preference.TwoStatePreference
import eu.kanade.tachiyomi.animesource.ConfigurableAnimeSource
import eu.kanade.tachiyomi.animesource.sourcePreferences
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.sourcePreferences
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import miwayomi.source.AnimeSourceManager
import miwayomi.source.MangaSourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

@Serializable
data class SourcePrefDto(
    val key: String?,
    val title: String?,
    val summary: String?,
    val type: String,
    val value: String? = null,
    val values: List<String>? = null,
    val labels: List<String>? = null,
)

@Serializable
data class SourcePrefsListDto(
    val sourceId: String,
    val name: String,
    val configurable: Boolean,
    val prefs: List<SourcePrefDto> = emptyList(),
    val error: String? = null,
)

@Serializable
data class SourcePrefsSetDto(val ok: Boolean, val error: String? = null)

fun Application.registerSourcePrefsApi() {
    val mangaManager = Injekt.get<MangaSourceManager>()
    val animeManager = Injekt.get<AnimeSourceManager>()
    val app = Injekt.get<AndroidApp>()

    routing {
        get("/api/v1/sources/{sourceId}/prefs") {
            val id = call.parameters["sourceId"]?.toLongOrNull()
            val source = id?.let { mangaManager.get(it) ?: animeManager.get(it) }
            if (source == null) {
                return@get call.respond(HttpStatusCode.NotFound, ErrorDto("Fuente no encontrada"))
            }

            val dto = buildPrefsDto(source, id, app)
            call.respond(dto)
        }

        post("/api/v1/sources/{sourceId}/prefs") {
            val id = call.parameters["sourceId"]?.toLongOrNull()
            val source = id?.let { mangaManager.get(it) ?: animeManager.get(it) }
            val prefs = source?.sourcePrefs()
            if (prefs == null) {
                return@post call.respond(HttpStatusCode.NotFound, SourcePrefsSetDto(ok = false, error = "fuente no configurable o no encontrada"))
            }

            val body = runCatching { call.receive<Map<String, JsonPrimitive>>() }.getOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, SourcePrefsSetDto(ok = false, error = "cuerpo JSON inválido"))

            try {
                val editor = prefs.edit()
                for ((k, v) in body) {
                    val bool = v.booleanOrNull
                    if (bool != null) {
                        editor.putBoolean(k, bool)
                    } else {
                        editor.putString(k, v.content)
                    }
                }
                editor.apply()
                call.respond(SourcePrefsSetDto(ok = true))
            } catch (e: Throwable) {
                call.respond(HttpStatusCode.InternalServerError, SourcePrefsSetDto(ok = false, error = e.message ?: "error al guardar"))
            }
        }
    }
}

private fun Any?.sourcePrefs(): SharedPreferences? = when (this) {
    is ConfigurableSource -> sourcePreferences()
    is ConfigurableAnimeSource -> sourcePreferences()
    else -> null
}

private fun buildPrefsDto(source: Any, id: Long, app: AndroidApp): SourcePrefsListDto {
    if (source is ConfigurableSource || source is ConfigurableAnimeSource) {
        val screen = PreferenceScreen(app)
        return try {
            when (source) {
                is ConfigurableSource -> source.setupPreferenceScreen(screen)
                is ConfigurableAnimeSource -> source.setupPreferenceScreen(screen)
            }
            val prefs = source.sourcePrefs()
            val list = flatten(screen).mapNotNull { it.toPrefDto(prefs) }
            SourcePrefsListDto(
                sourceId = id.toString(),
                name = sourceName(source),
                configurable = true,
                prefs = list,
            )
        } catch (e: Throwable) {
            val root = generateSequence(e) { it.cause }.lastOrNull() ?: e
            println("[miwayomi] prefs de $id no disponibles: ${root.javaClass.simpleName}: ${root.message}")
            SourcePrefsListDto(
                sourceId = id.toString(),
                name = sourceName(source),
                configurable = true,
                error = root.message ?: "no se pudieron cargar las preferencias",
            )
        }
    }
    return SourcePrefsListDto(sourceId = id.toString(), name = sourceName(source), configurable = false)
}

private fun sourceName(source: Any): String = when (source) {
    is eu.kanade.tachiyomi.source.MangaSource -> source.name
    is eu.kanade.tachiyomi.animesource.AnimeSource -> source.name
    else -> ""
}

private fun flatten(group: PreferenceGroup): List<Preference> {
    val out = mutableListOf<Preference>()
    for (i in 0 until group.getPreferenceCount()) {
        val p = group.getPreference(i) ?: continue
        when (p) {
            is PreferenceGroup -> out.addAll(flatten(p))
            else -> out.add(p)
        }
    }
    return out
}

private fun Preference.toPrefDto(prefs: SharedPreferences?): SourcePrefDto? {
    val type = when (this) {
        is SwitchPreferenceCompat, is CheckBoxPreference -> "switch"
        is EditTextPreference -> "text"
        is ListPreference -> "list"
        is MultiSelectListPreference -> "multi"
        else -> "unknown"
    }
    val k = key
    return SourcePrefDto(
        key = k,
        title = title?.toString(),
        summary = summary?.toString(),
        type = type,
        value = k?.let { currentValue(it, prefs) },
        values = (this as? ListPreference)?.entryValues?.map { it.toString() },
        labels = (this as? ListPreference)?.entries?.map { it.toString() },
    )
}

private fun Preference.currentValue(key: String, prefs: SharedPreferences?): String? {
    return when (this) {
        is TwoStatePreference ->
            (prefs?.getBoolean(key, (defaultValue as? Boolean) ?: isChecked) ?: isChecked).toString()
        is EditTextPreference ->
            prefs?.getString(key, (defaultValue as? String) ?: text) ?: text ?: defaultValue?.toString()
        is ListPreference ->
            prefs?.getString(key, (defaultValue as? String) ?: value) ?: value ?: defaultValue?.toString()
        is MultiSelectListPreference -> {
            val def = (defaultValue as? Set<*>)?.map { it.toString() }?.toSet() ?: values
            (prefs?.getStringSet(key, def) ?: def).sorted().joinToString(",")
        }
        else -> prefs?.all()?.get(key)?.toString() ?: defaultValue?.toString()
    }
}
