package android.app

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.compat.CompatRuntime

open class Application : ContextWrapper() {

    override fun getApplicationContext(): Context = this

    override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
        CompatRuntime.getSharedPreferences(name)

    override fun getApplicationInfo(): android.content.pm.ApplicationInfo =
        CompatRuntime.packageManager.getApplicationInfo(getPackageName(), 0)

    open fun onCreate() {}

    companion object {

        @Volatile
        var current: Application? = null

        @JvmStatic
        fun create(): Application = Application()
    }
}
