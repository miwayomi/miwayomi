package android.compat

import android.content.res.CompatSharedPreferences
import java.io.File
import java.util.concurrent.ConcurrentHashMap

object CompatRuntime {

    @Volatile
    var baseDir: File = File("data")

    val filesDir: File get() = baseDir
    val cacheDir: File get() = File(baseDir, "cache")

    @Volatile
    var packageName: String = "eu.kanade.tachiyomi"

    @Volatile
    var versionCode: Int = 1

    @Volatile
    var versionName: String = "1.0"

    val packageManager: android.content.pm.PackageManager = android.content.pm.PackageManager()

    private val prefs = ConcurrentHashMap<String, CompatSharedPreferences>()

    fun getSharedPreferences(name: String): CompatSharedPreferences {
        return prefs.getOrPut(name) {
            CompatSharedPreferences(File(baseDir, "prefs"), name)
        }
    }

    fun setup(dataDir: File, pkg: String = "eu.kanade.tachiyomi") {
        baseDir = dataDir
        packageName = pkg
        dataDir.mkdirs()
        File(dataDir, "prefs").mkdirs()
        File(dataDir, "cache").mkdirs()
    }
}
