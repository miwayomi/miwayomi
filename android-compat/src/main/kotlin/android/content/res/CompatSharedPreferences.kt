package android.content.res

import android.content.SharedPreferences
import java.io.File
import java.util.Properties
import java.util.concurrent.CopyOnWriteArraySet

class CompatSharedPreferences(
    private val dir: File,
    private val name: String,
) : SharedPreferences {

    private val file = File(dir, "$name.properties")
    private val props = Properties()
    private val listeners = CopyOnWriteArraySet<SharedPreferences.OnSharedPreferenceChangeListener>()
    private val lock = Any()

    init {
        dir.mkdirs()
        if (file.exists()) {
            synchronized(lock) {
                file.inputStream().use { props.load(it) }
            }
        }
    }

    override fun getString(key: String, defValue: String?): String? = synchronized(lock) { props.getProperty(key) ?: defValue }

    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String, defValues: Set<String>?): Set<String>? {
        val v = getString(key, null) ?: return defValues
        return v.split("\n").filter { it.isNotEmpty() }.toSet()
    }

    override fun getInt(key: String, defValue: Int): Int = synchronized(lock) { props.getProperty(key)?.toIntOrNull() ?: defValue }

    override fun getLong(key: String, defValue: Long): Long = synchronized(lock) { props.getProperty(key)?.toLongOrNull() ?: defValue }

    override fun getFloat(key: String, defValue: Float): Float = synchronized(lock) { props.getProperty(key)?.toFloatOrNull() ?: defValue }

    override fun getBoolean(key: String, defValue: Boolean): Boolean = synchronized(lock) { props.getProperty(key)?.toBooleanStrictOrNull() ?: defValue }

    override fun contains(key: String): Boolean = synchronized(lock) { props.containsKey(key) }

    override fun edit(): SharedPreferences.Editor = CompatEditor()

    override fun all(): Map<String, *> = synchronized(lock) {
        props.entries.associate { (k, v) -> k.toString() to v }
    }

    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        listeners.add(listener)
    }

    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        listeners.remove(listener)
    }

    private fun persist() {
        synchronized(lock) {
            file.outputStream().use { props.store(it, "miwayomi prefs: $name") }
        }
    }

    private fun notifyListeners(key: String) {
        for (l in listeners) {
            try {
                l.onSharedPreferenceChanged(this, key)
            } catch (_: Throwable) {
            }
        }
    }

    private inner class CompatEditor : SharedPreferences.Editor {
        private val edits = mutableMapOf<String, Any?>()
        private var clearAll = false

        override fun putString(key: String, value: String?): SharedPreferences.Editor = apply { edits[key] = value }
        override fun putStringSet(key: String, values: Set<String>?): SharedPreferences.Editor = apply { edits[key] = values }
        override fun putInt(key: String, value: Int): SharedPreferences.Editor = apply { edits[key] = value }
        override fun putLong(key: String, value: Long): SharedPreferences.Editor = apply { edits[key] = value }
        override fun putFloat(key: String, value: Float): SharedPreferences.Editor = apply { edits[key] = value }
        override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor = apply { edits[key] = value }
        override fun remove(key: String): SharedPreferences.Editor = apply { edits[key] = null }
        override fun clear(): SharedPreferences.Editor = apply { clearAll = true }

        override fun commit(): Boolean {
            synchronized(lock) {
                if (clearAll) props.clear()
                for ((k, v) in edits) {
                    if (v == null) {
                        props.remove(k)
                    } else {
                        props.setProperty(k, encode(v))
                    }
                }
            }
            persist()
            for (k in edits.keys) notifyListeners(k)
            return true
        }

        override fun apply() {
            commit()
        }

        private fun encode(v: Any): String = when (v) {
            is Set<*> -> (v as Set<*>).joinToString("\n") { it.toString() }
            else -> v.toString()
        }
    }
}
