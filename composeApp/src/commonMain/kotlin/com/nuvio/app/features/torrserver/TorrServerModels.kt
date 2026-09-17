package com.nuvio.app.features.torrserver

import com.nuvio.app.features.p2p.P2pSettingsRepository
import com.nuvio.app.features.p2p.P2pStreamingEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class TorrServerConfigData(
    val enabled: Boolean = false,
    val serverUrl: String = "http://127.0.0.1:8090",
    val authUsername: String = "",
    val authPassword: String = "",
    val preload: Boolean = true,
    val saveToDb: Boolean = false,
    val gst: Boolean = false,
)

data class TorrServerSettingsUiState(
    val enabled: Boolean = false,
    val serverUrl: String = "http://127.0.0.1:8090",
    val authUsername: String = "",
    val authPassword: String = "",
    val preload: Boolean = true,
    val saveToDb: Boolean = false,
    val gst: Boolean = false,
    val isTestingServer: Boolean = false,
    val serverStatusMessage: String? = null,
    val serverStatusSuccess: Boolean? = null,
    val isCheckingGst: Boolean = false,
    val gstStatusMessage: String? = null,
    val gstStatusSuccess: Boolean? = null,
)

data class TorrServerRemoteFile(
    val id: Int,
    val path: String,
    val length: Long,
)

data class TorrServerRemoteStatus(
    val hash: String,
    val title: String? = null,
    val stat: Int = 0,
    val statString: String? = null,
    val downloadSpeed: Long = 0L,
    val uploadSpeed: Long = 0L,
    val preloadedBytes: Long = 0L,
    val preloadSize: Long = 0L,
    val loadedSize: Long = 0L,
    val torrentSize: Long = 0L,
    val activePeers: Int = 0,
    val connectedSeeders: Int = 0,
    val totalPeers: Int = 0,
    val files: List<TorrServerRemoteFile> = emptyList(),
) {
    val rawPreloadProgress: Float
        get() {
            val target = if (preloadSize > 0) preloadSize else if (stat == 2 && preloadedBytes > 0) 33_554_432L else 0L
            return if (target > 0) (preloadedBytes.toFloat() / target).coerceIn(0f, 1f) else 0f
        }

    val isPreloadReadyByProgress: Boolean
        get() = (preloadSize > 0 && preloadedBytes >= preloadSize * 95 / 100) ||
            rawPreloadProgress >= 0.95f

    /**
     * Note: [stat == 3] (active) MUST NOT be unconditionally treated as preload ready here,
     * because when resolving torrent metadata / files list, the server is already in stat == 3 (active)
     * before the preload stream even begins.
     */
    val isPreloadReady: Boolean
        get() = isPreloadReadyByProgress

    val preloadProgress: Float
        get() = if (isPreloadReady) 1f else rawPreloadProgress
}

fun torrServerDisplayTitle(title: String?): String? =
    title?.trim()?.takeIf { it.isNotBlank() }?.let { "[NuvioM] $it" }


internal expect object TorrServerSettingsStorage {
    fun isInitialized(): Boolean
    fun loadEnabled(): Boolean?
    fun saveEnabled(enabled: Boolean)
    fun loadServerUrl(): String?
    fun saveServerUrl(url: String)
    fun loadAuthUsername(): String?
    fun saveAuthUsername(username: String)
    fun loadAuthPassword(): String?
    fun saveAuthPassword(password: String)
    fun loadPreload(): Boolean?
    fun savePreload(preload: Boolean)
    fun loadSaveToDb(): Boolean?
    fun saveSaveToDb(saveToDb: Boolean)
    fun loadGst(): Boolean?
    fun saveGst(gst: Boolean)
}

object TorrServerConfigRepository {
    private val _uiState = MutableStateFlow(TorrServerSettingsUiState())
    val uiState: StateFlow<TorrServerSettingsUiState> = _uiState.asStateFlow()

    private var hasLoaded = false
    private var enabled = false
    private var serverUrl = "http://127.0.0.1:8090"
    private var authUsername = ""
    private var authPassword = ""
    private var preload = true
    private var saveToDb = false
    private var gst = false

    fun ensureLoaded() {
        if (hasLoaded) return
        if (!TorrServerSettingsStorage.isInitialized()) return
        loadFromDisk()
    }

    /** Reload all settings from persistent storage (e.g. after a profile switch). */
    fun onProfileChanged() {
        hasLoaded = false
        if (TorrServerSettingsStorage.isInitialized()) {
            loadFromDisk()
        }
    }

    private fun loadFromDisk() {
        if (!TorrServerSettingsStorage.isInitialized()) return
        hasLoaded = true
        enabled = TorrServerSettingsStorage.loadEnabled() ?: false
        serverUrl = TorrServerSettingsStorage.loadServerUrl() ?: "http://127.0.0.1:8090"
        authUsername = TorrServerSettingsStorage.loadAuthUsername() ?: ""
        authPassword = TorrServerSettingsStorage.loadAuthPassword() ?: ""
        preload = TorrServerSettingsStorage.loadPreload() ?: true
        saveToDb = TorrServerSettingsStorage.loadSaveToDb() ?: false
        gst = TorrServerSettingsStorage.loadGst() ?: false
        publish()
    }

    fun setEnabled(enabled: Boolean) {
        ensureLoaded()
        if (this.enabled == enabled) return
        this.enabled = enabled
        TorrServerSettingsStorage.saveEnabled(enabled)
        publish()
        if (enabled) {
            // Tự động tắt P2P và tắt engine P2P giống bản TV
            P2pSettingsRepository.setP2pEnabled(false)
            P2pStreamingEngine.shutdown()
        }
    }

    fun setServerUrl(url: String) {
        ensureLoaded()
        val trimmed = url.trim()
        if (this.serverUrl == trimmed) return
        this.serverUrl = trimmed
        TorrServerSettingsStorage.saveServerUrl(trimmed)
        publish()
    }

    fun setCredentials(user: String, pass: String) {
        ensureLoaded()
        this.authUsername = user.trim()
        this.authPassword = pass
        TorrServerSettingsStorage.saveAuthUsername(this.authUsername)
        TorrServerSettingsStorage.saveAuthPassword(this.authPassword)
        publish()
    }

    fun setPreload(enabled: Boolean) {
        ensureLoaded()
        if (this.preload == enabled) return
        this.preload = enabled
        TorrServerSettingsStorage.savePreload(enabled)
        publish()
    }

    fun setSaveToDb(enabled: Boolean) {
        ensureLoaded()
        if (this.saveToDb == enabled) return
        this.saveToDb = enabled
        TorrServerSettingsStorage.saveSaveToDb(enabled)
        publish()
    }

    fun setGst(enabled: Boolean) {
        ensureLoaded()
        if (this.gst == enabled) return
        this.gst = enabled
        TorrServerSettingsStorage.saveGst(enabled)
        publish()
    }

    fun updateStatus(
        isTesting: Boolean? = null,
        serverMsg: String? = null,
        serverSuccess: Boolean? = null,
        isCheckingGst: Boolean? = null,
        gstMsg: String? = null,
        gstSuccess: Boolean? = null,
    ) {
        val cur = _uiState.value
        _uiState.value = cur.copy(
            isTestingServer = isTesting ?: cur.isTestingServer,
            serverStatusMessage = if (isTesting == true) null else (serverMsg ?: cur.serverStatusMessage),
            serverStatusSuccess = if (isTesting == true) null else (serverSuccess ?: cur.serverStatusSuccess),
            isCheckingGst = isCheckingGst ?: cur.isCheckingGst,
            gstStatusMessage = if (isCheckingGst == true) null else (gstMsg ?: cur.gstStatusMessage),
            gstStatusSuccess = if (isCheckingGst == true) null else (gstSuccess ?: cur.gstStatusSuccess),
        )
    }

    suspend fun testConnection() {
        updateStatus(isTesting = true)
        val result = TorrServerRemoteApi.healthCheck(serverUrl, authUsername, authPassword)
        if (result.isSuccess) {
            updateStatus(isTesting = false, serverMsg = result.getOrNull() ?: "OK", serverSuccess = true)
        } else {
            updateStatus(isTesting = false, serverMsg = result.exceptionOrNull()?.message ?: "Lỗi kết nối", serverSuccess = false)
        }
    }

    suspend fun checkGst() {
        updateStatus(isCheckingGst = true)
        val result = TorrServerRemoteApi.checkGStreamerSupport(serverUrl, authUsername, authPassword)
        if (result.isSuccess) {
            val supported = result.getOrDefault(false)
            updateStatus(
                isCheckingGst = false,
                gstMsg = if (supported) "GStreamer được hỗ trợ" else "GStreamer không khả dụng",
                gstSuccess = supported,
            )
        } else {
            updateStatus(
                isCheckingGst = false,
                gstMsg = result.exceptionOrNull()?.message ?: "Lỗi kiểm tra",
                gstSuccess = false,
            )
        }
    }

    private fun publish() {
        val cur = _uiState.value
        _uiState.value = cur.copy(
            enabled = enabled,
            serverUrl = serverUrl,
            authUsername = authUsername,
            authPassword = authPassword,
            preload = preload,
            saveToDb = saveToDb,
            gst = gst,
        )
    }
}

fun buildTorrServerMagnet(stream: com.nuvio.app.features.streams.StreamItem, infoHash: String): String {
    val existing = stream.torrentMagnetUri
    val trackers = stream.p2pTrackers
    val trParams = if (trackers.isNotEmpty() && (existing == null || !existing.contains("tr="))) {
        trackers.joinToString("") { "&tr=$it" }
    } else {
        ""
    }
    return when {
        existing != null -> "$existing$trParams"
        else -> "magnet:?xt=urn:btih:$infoHash$trParams"
    }
}

