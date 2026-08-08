package android.content

import android.compat.CompatRuntime
import android.content.pm.PackageManager
import android.content.res.Resources
import java.io.File
import java.io.InputStream
import java.io.OutputStream

abstract class Context {

    open fun getApplicationContext(): Context = this

    open fun getFilesDir(): File = CompatRuntime.filesDir

    open fun getCacheDir(): File = CompatRuntime.cacheDir

    open fun getExternalFilesDir(type: String? = null): File? =
        File(CompatRuntime.baseDir, "external")

    open fun getExternalCacheDir(): File? =
        File(CompatRuntime.cacheDir, "external")

    open fun getResources(): Resources = Resources.getSystem()

    open fun getPackageName(): String = CompatRuntime.packageName

    open fun getPackageManager(): PackageManager = CompatRuntime.packageManager

    open fun getApplicationInfo(): android.content.pm.ApplicationInfo =
        CompatRuntime.packageManager.getApplicationInfo(getPackageName(), 0)

    open fun getString(resId: Int): String = ""

    open fun getClassLoader(): ClassLoader = javaClass.classLoader!!

    open fun getSystemService(name: String): Any? = null

    open fun getAssets(): Any? = null

    open fun getContentResolver(): Any? = null

    open fun getMainLooper(): Any? = null

    open fun getTheme(): Any? = null

    open fun openFileInput(name: String): InputStream = File(CompatRuntime.filesDir, name).inputStream()

    open fun openFileOutput(name: String, mode: Int): OutputStream = File(CompatRuntime.filesDir, name).outputStream()

    open fun deleteFile(name: String): Boolean = File(CompatRuntime.filesDir, name).delete()

    open fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
        CompatRuntime.getSharedPreferences(name)

    open fun getDatabasePath(name: String): File = File(CompatRuntime.filesDir, name)

    companion object {
        const val MODE_PRIVATE = 0
        const val MODE_WORLD_READABLE = 1
        const val MODE_MULTI_PROCESS = 4
        const val BIND_AUTO_CREATE = 1
    }
}
