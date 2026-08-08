package android.os

import java.io.Serializable

class Bundle : Serializable {

    private val map = mutableMapOf<String, Any?>()

    constructor()

    constructor(other: Bundle?) {
        other?.let { map.putAll(it.map) }
    }

    fun containsKey(key: String): Boolean = map.containsKey(key)

    fun getString(key: String): String? = map[key] as? String

    fun getString(key: String, defaultValue: String): String = map[key] as? String ?: defaultValue

    fun getInt(key: String): Int = map[key] as? Int ?: 0

    fun getInt(key: String, defaultValue: Int): Int = map[key] as? Int ?: defaultValue

    fun getLong(key: String): Long = map[key] as? Long ?: 0L

    fun getLong(key: String, defaultValue: Long): Long = map[key] as? Long ?: defaultValue

    fun getFloat(key: String): Float = map[key] as? Float ?: 0f

    fun getBoolean(key: String): Boolean = map[key] as? Boolean ?: false

    fun getBoolean(key: String, defaultValue: Boolean): Boolean = map[key] as? Boolean ?: defaultValue

    fun getStringArrayList(key: String): ArrayList<String>? = map[key] as? ArrayList<String>

    fun getStringArray(key: String): Array<String>? = map[key] as? Array<String>

    fun getSerializable(key: String): Serializable? = map[key] as? Serializable

    fun getBundle(key: String): Bundle? = map[key] as? Bundle

    fun putString(key: String, value: String?) { map[key] = value }

    fun putInt(key: String, value: Int) { map[key] = value }

    fun putLong(key: String, value: Long) { map[key] = value }

    fun putFloat(key: String, value: Float) { map[key] = value }

    fun putBoolean(key: String, value: Boolean) { map[key] = value }

    fun putStringArrayList(key: String, value: ArrayList<String>?) { map[key] = value }

    fun putStringArray(key: String, value: Array<String>?) { map[key] = value }

    fun putSerializable(key: String, value: Serializable?) { map[key] = value }

    fun putBundle(key: String, value: Bundle?) { map[key] = value }

    fun remove(key: String) { map.remove(key) }

    fun size(): Int = map.size

    fun isEmpty(): Boolean = map.isEmpty()

    fun clear() { map.clear() }

    fun keySet(): Set<String> = map.keys

    override fun toString(): String = map.toString()
}
