package com.pleospostbox.car.data

import android.content.SharedPreferences

/** 테스트에서 바꿔 끼울 수 있게 SharedPreferences를 감싼다. */
interface KeyValueStore {
    fun getString(key: String): String?
    fun putString(key: String, value: String)
    fun getLong(key: String): Long?
    fun putLong(key: String, value: Long)
    fun keys(): Set<String>
    fun remove(key: String)
}

class SharedPrefsStore(private val prefs: SharedPreferences) : KeyValueStore {
    override fun getString(key: String) = prefs.getString(key, null)
    override fun putString(key: String, value: String) = prefs.edit().putString(key, value).apply()
    override fun getLong(key: String) = if (prefs.contains(key)) prefs.getLong(key, 0) else null
    override fun putLong(key: String, value: Long) = prefs.edit().putLong(key, value).apply()
    override fun keys(): Set<String> = prefs.all.keys
    override fun remove(key: String) = prefs.edit().remove(key).apply()
}

class MemoryStore : KeyValueStore {
    private val map = mutableMapOf<String, Any>()
    override fun getString(key: String) = map[key] as? String
    override fun putString(key: String, value: String) { map[key] = value }
    override fun getLong(key: String) = map[key] as? Long
    override fun putLong(key: String, value: Long) { map[key] = value }
    override fun keys(): Set<String> = map.keys.toSet()
    override fun remove(key: String) { map.remove(key) }
}
