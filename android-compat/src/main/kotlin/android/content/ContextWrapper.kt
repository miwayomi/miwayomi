package android.content

open class ContextWrapper(
    base: Context? = null,
) : Context() {

    protected var mBase: Context = base ?: RuntimeContext()

    fun getBaseContext(): Context = mBase

    override fun getApplicationContext(): Context = mBase.getApplicationContext()

    override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
        mBase.getSharedPreferences(name, mode)

    override fun getFilesDir(): java.io.File = mBase.getFilesDir()

    override fun getCacheDir(): java.io.File = mBase.getCacheDir()

    override fun getExternalFilesDir(type: String?): java.io.File? = mBase.getExternalFilesDir(type)

    override fun getExternalCacheDir(): java.io.File? = mBase.getExternalCacheDir()

    override fun getResources(): android.content.res.Resources = mBase.getResources()

    override fun getPackageName(): String = mBase.getPackageName()

    override fun getPackageManager(): android.content.pm.PackageManager = mBase.getPackageManager()

    override fun getString(resId: Int): String = mBase.getString(resId)

    override fun getClassLoader(): ClassLoader = mBase.getClassLoader()

    override fun getSystemService(name: String): Any? = mBase.getSystemService(name)

    override fun getAssets(): Any? = mBase.getAssets()

    override fun getContentResolver(): Any? = mBase.getContentResolver()

    override fun getMainLooper(): Any? = mBase.getMainLooper()

    override fun getTheme(): Any? = mBase.getTheme()

    override fun openFileInput(name: String): java.io.InputStream = mBase.openFileInput(name)

    override fun openFileOutput(name: String, mode: Int): java.io.OutputStream = mBase.openFileOutput(name, mode)

    override fun deleteFile(name: String): Boolean = mBase.deleteFile(name)

    override fun getDatabasePath(name: String): java.io.File = mBase.getDatabasePath(name)

    override fun getApplicationInfo(): android.content.pm.ApplicationInfo =
        mBase.getApplicationInfo()
}

internal class RuntimeContext : Context()
