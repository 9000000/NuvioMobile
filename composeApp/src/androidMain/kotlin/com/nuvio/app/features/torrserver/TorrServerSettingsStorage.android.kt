package com.nuvio.app.features.torrserver

import android.content.Context
import android.content.SharedPreferences
import com.nuvio.app.core.storage.ProfileScopedKey

internal actual object TorrServerSettingsStorage {
    private const val preferencesName = "torrserver_settings"
    private const val enabledKey = "torrserver_enabled"
    private const val serverUrlKey = "torrserver_server_url"
    private const val authUsernameKey = "torrserver_auth_username"
    private const val authPasswordKey = "torrserver_auth_password"
    private const val preloadKey = "torrserver_preload"
    private const val saveToDbKey = "torrserver_save_to_db"
    private const val gstKey = "torrserver_gst"

    private var preferences: SharedPreferences? = null

    fun initialize(context: Context) {
        preferences = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
    }

    actual fun isInitialized(): Boolean = preferences != null

    actual fun loadEnabled(): Boolean? = loadBoolean(enabledKey)
    actual fun saveEnabled(enabled: Boolean) = saveBoolean(enabledKey, enabled)

    actual fun loadServerUrl(): String? = loadString(serverUrlKey)
    actual fun saveServerUrl(url: String) = saveString(serverUrlKey, url)

    actual fun loadAuthUsername(): String? = loadString(authUsernameKey)
    actual fun saveAuthUsername(username: String) = saveString(authUsernameKey, username)

    actual fun loadAuthPassword(): String? = loadString(authPasswordKey)
    actual fun saveAuthPassword(password: String) = saveString(authPasswordKey, password)

    actual fun loadPreload(): Boolean? = loadBoolean(preloadKey)
    actual fun savePreload(preload: Boolean) = saveBoolean(preloadKey, preload)

    actual fun loadSaveToDb(): Boolean? = loadBoolean(saveToDbKey)
    actual fun saveSaveToDb(saveToDb: Boolean) = saveBoolean(saveToDbKey, saveToDb)

    actual fun loadGst(): Boolean? = loadBoolean(gstKey)
    actual fun saveGst(gst: Boolean) = saveBoolean(gstKey, gst)

    private fun loadBoolean(keyBase: String): Boolean? =
        preferences?.let { sharedPreferences ->
            val scopedKey = ProfileScopedKey.of(keyBase)
            when {
                sharedPreferences.contains(scopedKey) -> sharedPreferences.getBoolean(scopedKey, false)
                sharedPreferences.contains(keyBase) -> sharedPreferences.getBoolean(keyBase, false)
                else -> null
            }
        }

    private fun saveBoolean(keyBase: String, value: Boolean) {
        preferences?.edit()?.apply {
            putBoolean(ProfileScopedKey.of(keyBase), value)
            putBoolean(keyBase, value)
            apply()
        }
    }

    private fun loadString(keyBase: String): String? =
        preferences?.let { sharedPreferences ->
            val scopedKey = ProfileScopedKey.of(keyBase)
            when {
                sharedPreferences.contains(scopedKey) -> sharedPreferences.getString(scopedKey, null)
                sharedPreferences.contains(keyBase) -> sharedPreferences.getString(keyBase, null)
                else -> null
            }
        }

    private fun saveString(keyBase: String, value: String) {
        preferences?.edit()?.apply {
            putString(ProfileScopedKey.of(keyBase), value)
            putString(keyBase, value)
            apply()
        }
    }
}
