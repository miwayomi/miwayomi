package miwayomi.di

import android.app.Application
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.NetworkPreferences
import kotlinx.serialization.json.Json
import miwayomi.extension.ExtensionManager
import miwayomi.source.AnimeSourceManager
import miwayomi.source.MangaSourceManager
import tachiyomi.core.common.preference.InMemoryPreferenceStore
import tachiyomi.core.common.preference.PreferenceStore
import uy.kohesive.injekt.api.InjektModule
import uy.kohesive.injekt.api.InjektRegistrar
import uy.kohesive.injekt.api.addSingletonFactory
import uy.kohesive.injekt.api.get

object AppModule : InjektModule {
    override fun InjektRegistrar.registerInjectables() {
        addSingletonFactory { Application.create() }
        addSingletonFactory<PreferenceStore> { InMemoryPreferenceStore() }
        addSingletonFactory { NetworkPreferences(get()) }
        addSingletonFactory {
            NetworkHelper(get<Application>(), get(), ConfigHolder.config.flareSolverrUrl)
        }
        addSingletonFactory {
            Json {
                ignoreUnknownKeys = true
                explicitNulls = false
            }
        }
        addSingletonFactory { ExtensionManager(get(), get()) }
        addSingletonFactory { MangaSourceManager() }
        addSingletonFactory { AnimeSourceManager() }
    }
}
