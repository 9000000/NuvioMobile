package com.nuvio.app.features.torrserver

import com.nuvio.app.core.storage.ProfileScopedKey
import platform.Foundation.NSUserDefaults

internal actual object TorrServerSettingsStorage {
    private const val enabledKey = "torrserver_enabled"
    private const val serverUrlKey = "torrserver_server_url"
    private const val authUsernameKey = "torrserver_auth_username"
    private const val authPasswordKey = "torrserver_auth_password"
    private const val preloadKey = "torrserver_preload"
    private const val saveToDbKey = "torrserver_save_to_db"
    private const val gstKey = "torrserver_gst"

    actual fun isInitialized(): Boolean = true

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

    private fun loadBoolean(keyBase: String): Boolean? {
        val defaults = NSUserDefaults.standardUserDefaults
        val key = ProfileScopedKey.of(keyBase)
        return if (defaults.objectForKey(key) != null) {
            defaults.boolForKey(key)
        } else {
            null
        }
    }

    private fun saveBoolean(keyBase: String, value: Boolean) {
        NSUserDefaults.standardUserDefaults.setBool(value, forKey = ProfileScopedKey.of(keyBase))
    }

    private fun loadString(keyBase: String): String? {
        val defaults = NSUserDefaults.standardUserDefaults
        val key = ProfileScopedKey.of(keyBase)
        return defaults.stringForKey(key)
    }

    private fun saveString(keyBase: String, value: String) {
        NSUserDefaults.standardUserDefaults.setObject(value, forKey = ProfileScopedKey.of(keyBase))
    }
}
