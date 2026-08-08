package android.content

import android.net.Uri
import android.os.Bundle

class Intent {

    var action: String? = null
    var data: Uri? = null
    var type: String? = null
    var component: Any? = null
    var flags: Int = 0
    private val extras = Bundle()
    private val categories = mutableSetOf<String>()

    constructor()

    constructor(action: String?, uri: Uri?) {
        this.action = action
        this.data = uri
    }

    constructor(action: String?, uri: Uri?, context: Any?, cls: Class<*>?) {
        this.action = action
        this.data = uri
    }

    fun setData(uri: Uri?): Intent { data = uri; return this }
    fun setType(type: String?): Intent { this.type = type; return this }
    fun setDataAndType(uri: Uri?, type: String?): Intent { data = uri; this.type = type; return this }
    fun putExtra(name: String, value: String): Intent { extras.putString(name, value); return this }
    fun putExtra(name: String, value: Int): Intent { extras.putInt(name, value); return this }
    fun putExtra(name: String, value: Long): Intent { extras.putLong(name, value); return this }
    fun putExtra(name: String, value: Boolean): Intent { extras.putBoolean(name, value); return this }
    fun putExtra(name: String, value: Bundle): Intent { extras.putBundle(name, value); return this }
    fun getStringExtra(name: String): String? = extras.getString(name)
    fun getIntExtra(name: String, defaultValue: Int): Int = extras.getInt(name, defaultValue)
    fun getLongExtra(name: String, defaultValue: Long): Long = extras.getLong(name, defaultValue)
    fun getBooleanExtra(name: String, defaultValue: Boolean): Boolean = extras.getBoolean(name, defaultValue)
    fun getExtras(): Bundle = extras
    fun hasExtra(name: String): Boolean = extras.containsKey(name)
    fun addCategory(category: String): Intent { categories.add(category); return this }
    fun hasCategory(category: String): Boolean = category in categories
    fun setFlags(flags: Int): Intent { this.flags = flags; return this }
    fun addFlags(flags: Int): Intent { this.flags = this.flags or flags; return this }

    fun toUri(flags: Int): String = buildString {
        append("intent:")
        action?.let { append("#Intent;action=$it") }
        data?.let { append(";S.uri=${it.toString()}") }
        append(";end")
    }

    companion object {
        const val ACTION_VIEW = "android.intent.action.VIEW"
        const val ACTION_SEND = "android.intent.action.SEND"
        const val FLAG_ACTIVITY_NEW_TASK = 0x10000000
        const val FLAG_ACTIVITY_CLEAR_TOP = 0x04000000
        const val FLAG_ACTIVITY_SINGLE_TOP = 0x20000000
    }
}
