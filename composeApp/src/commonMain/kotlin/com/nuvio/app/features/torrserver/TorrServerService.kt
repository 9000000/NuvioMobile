package com.nuvio.app.features.torrserver

import co.touchlab.kermit.Logger
import com.nuvio.app.features.p2p.P2pStreamingException
import com.nuvio.app.features.p2p.P2pStreamingState
import com.nuvio.app.features.watchprogress.WatchProgressClock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val VIDEO_EXTENSIONS = setOf("mkv", "mp4", "avi", "webm", "ts", "m4v", "mov", "wmv", "flv")

object TorrServerService {
    private val log = Logger.withTag("TorrServerService")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val DEFAULT_TRACKERS = listOf(
        "udp://tracker.opentrackr.org:1337/announce",
        "udp://open.stealth.si:80/announce",
        "udp://tracker.openbittorrent.com:6969/announce",
        "udp://exodus.desync.com:6969/announce",
        "udp://tracker.torrent.eu.org:451/announce",
    )

    private val _state = MutableStateFlow<P2pStreamingState>(P2pStreamingState.Idle)
    val state: StateFlow<P2pStreamingState> = _state.asStateFlow()

    private var statsJob: Job? = null
    private var preloadJob: Job? = null
    private var currentHash: String? = null
    private var currentServerUrl: String? = null

    suspend fun startStream(
        infoHash: String,
        fileIdx: Int?,
        filename: String? = null,
        title: String? = null,
        poster: String? = null,
        trackers: List<String> = emptyList(),
        season: Int? = null,
        episode: Int? = null,
    ): String = withContext(Dispatchers.IO) {
        stopStream()
        _state.value = P2pStreamingState.Connecting(phase = "starting_engine")

        val config = TorrServerConfigRepository.uiState.value
        val serverUrl = config.serverUrl.trim().trimEnd('/')
        if (serverUrl.isBlank()) {
            val errorMsg = "Chưa cấu hình địa chỉ máy chủ TorrServer"
            _state.value = P2pStreamingState.Error(errorMsg)
            throw P2pStreamingException(errorMsg)
        }

        val magnetLink = buildMagnetUri(infoHash, trackers)
        log.d { "Starting TorrServer stream on $serverUrl: $magnetLink" }

        val hash = TorrServerRemoteApi.addTorrent(
            serverUrl = serverUrl,
            magnetLink = magnetLink,
            title = torrServerDisplayTitle(title),
            poster = poster,
            saveToDb = config.saveToDb,
            username = config.authUsername,
            password = config.authPassword,
        ) ?: infoHash.trim().lowercase()

        currentHash = hash
        currentServerUrl = serverUrl

        _state.value = P2pStreamingState.Connecting(phase = "resolving_metadata")
        val resolvedIdx = resolveFileIndex(
            serverUrl = serverUrl,
            hash = hash,
            requestedIdx = fileIdx,
            filename = filename,
            targetSeason = season,
            targetEpisode = episode,
            user = config.authUsername,
            pass = config.authPassword,
        )

        val isPreload = config.preload

        val preloadUrl = TorrServerRemoteApi.buildStreamUrl(
            serverUrl = serverUrl,
            magnetLink = magnetLink,
            fileIdx = resolvedIdx,
            preload = true,
            save = config.saveToDb,
            gst = config.gst,
            hash = hash,
        )

        val playbackPlayUrl = TorrServerRemoteApi.buildStreamUrl(
            serverUrl = serverUrl,
            magnetLink = magnetLink,
            fileIdx = resolvedIdx,
            preload = false,
            save = config.saveToDb,
            gst = config.gst,
            hash = hash,
        )

        if (isPreload) {
            log.d { "Preload enabled: starting preload call with URL (preload param only): $preloadUrl" }
            startStatsPolling(serverUrl, hash, preloadUrl, config.authUsername, config.authPassword)

            _state.value = P2pStreamingState.Streaming(
                localUrl = preloadUrl,
                downloadSpeed = 0L,
                uploadSpeed = 0L,
                peers = 0,
                seeds = 0,
                bufferProgress = 0f,
                totalProgress = 0f,
                isPreloadActive = true,
                isPreloadReady = false,
                preloadProgress = 0f,
                stat = 2,
                statString = "preloading",
            )

            val preloadCallJob = scope.launch(Dispatchers.IO) {
                try {
                    TorrServerRemoteApi.runPreload(
                        preloadUrl = preloadUrl,
                        username = config.authUsername,
                        password = config.authPassword,
                    )
                } catch (_: CancellationException) {
                } catch (e: Exception) {
                    log.w { "TorrServer preload stream ended: ${e.message}" }
                }
            }
            preloadJob = preloadCallJob

            try {
                awaitPreloadReady(serverUrl, hash, config.authUsername, config.authPassword)
            } finally {
                preloadCallJob.cancel()
                if (preloadJob == preloadCallJob) {
                    preloadJob = null
                }
            }

            // Preload hoàn tất và đã nhận được trạng thái active từ TorrServer:
            // Lúc này mới chuyển sang truyền tham số play để phát
            log.d { "Preload complete and active received; now passing play parameter: $playbackPlayUrl" }
            val cur = _state.value
            if (cur is P2pStreamingState.Streaming) {
                _state.value = cur.copy(
                    localUrl = playbackPlayUrl,
                    isPreloadActive = true,
                    isPreloadReady = true,
                    preloadProgress = 1f,
                )
            }
            playbackPlayUrl
        } else {
            log.d { "Preload disabled: starting directly with play parameter: $playbackPlayUrl" }
            startStatsPolling(serverUrl, hash, playbackPlayUrl, config.authUsername, config.authPassword)

            _state.value = P2pStreamingState.Streaming(
                localUrl = playbackPlayUrl,
                downloadSpeed = 0L,
                uploadSpeed = 0L,
                peers = 0,
                seeds = 0,
                bufferProgress = 0f,
                totalProgress = 0f,
                isPreloadActive = false,
                isPreloadReady = true,
                preloadProgress = 1f,
            )
            playbackPlayUrl
        }
    }

    suspend fun fetchTorrentFiles(
        infoHash: String,
        title: String? = null,
        poster: String? = null,
        trackers: List<String> = emptyList(),
        magnetOverride: String? = null,
    ): List<TorrServerRemoteFile> = withContext(Dispatchers.IO) {
        val config = TorrServerConfigRepository.uiState.value
        val serverUrl = config.serverUrl.trim().trimEnd('/')
        if (serverUrl.isBlank()) return@withContext emptyList()

        val magnetLink = magnetOverride?.takeIf { it.isNotBlank() } ?: buildMagnetUri(infoHash, trackers)
        val hash = TorrServerRemoteApi.addTorrent(
            serverUrl = serverUrl,
            magnetLink = magnetLink,
            title = torrServerDisplayTitle(title),
            poster = poster,
            saveToDb = config.saveToDb,
            username = config.authUsername,
            password = config.authPassword,
        ) ?: infoHash

        val deadline = WatchProgressClock.nowEpochMs() + 15_000L
        var files: List<TorrServerRemoteFile> = emptyList()
        var pollDelay = 300L

        while (isActive && WatchProgressClock.nowEpochMs() < deadline) {
            val details = TorrServerRemoteApi.getTorrentDetails(
                serverUrl = serverUrl,
                hash = hash,
                username = config.authUsername,
                password = config.authPassword,
            )
            if (details != null && details.files.isNotEmpty()) {
                files = details.files
                break
            }
            delay(pollDelay)
            pollDelay = (pollDelay * 2).coerceAtMost(1000L)
        }

        files
    }

    fun stopStream() {
        statsJob?.cancel()
        statsJob = null
        preloadJob?.cancel()
        preloadJob = null

        val hash = currentHash
        val serverUrl = currentServerUrl
        val config = TorrServerConfigRepository.uiState.value

        if (hash != null && serverUrl != null && !config.saveToDb) {
            scope.launch(Dispatchers.IO) {
                try {
                    TorrServerRemoteApi.dropTorrent(
                        serverUrl = serverUrl,
                        hash = hash,
                        username = config.authUsername,
                        password = config.authPassword,
                    )
                } catch (e: Exception) {
                    log.w { "Error dropping torrent: $hash (${e.message})" }
                }
            }
        }

        currentHash = null
        currentServerUrl = null
        _state.value = P2pStreamingState.Idle
    }

    private suspend fun awaitPreloadReady(
        serverUrl: String,
        hash: String,
        user: String,
        pass: String,
    ) {
        val deadline = WatchProgressClock.nowEpochMs() + 60_000L
        while (WatchProgressClock.nowEpochMs() < deadline) {
            val stats = TorrServerRemoteApi.getTorrentDetails(
                serverUrl = serverUrl,
                hash = hash,
                username = user,
                password = pass,
            )
            if (stats != null) {
                val isReady = stats.isPreloadReady || stats.preloadProgress >= 0.95f
                val latest = _state.value
                if (latest is P2pStreamingState.Streaming) {
                    _state.value = latest.copy(
                        downloadSpeed = stats.downloadSpeed,
                        uploadSpeed = stats.uploadSpeed,
                        peers = stats.activePeers,
                        seeds = stats.connectedSeeders,
                        bufferProgress = if (isReady) 1f else stats.preloadProgress,
                        totalProgress = if (isReady) 1f else stats.preloadProgress,
                        downloadedBytes = stats.preloadedBytes,
                        deliveredBytes = stats.loadedSize,
                        preloadedBytes = stats.preloadedBytes,
                        preloadSize = stats.preloadSize,
                        isPreloadActive = true,
                        isPreloadReady = isReady,
                        preloadProgress = if (isReady) 1f else stats.preloadProgress,
                        stat = stats.stat,
                        statString = stats.statString,
                    )
                }
                if (isReady) {
                    log.d { "TorrServer preload reached 95% (or active): handing stream to player" }
                    return
                }
            }
            delay(300L)
        }
        log.w { "TorrServer preload timeout (60s); proceeding with playback" }
        val latest = _state.value
        if (latest is P2pStreamingState.Streaming) {
            _state.value = latest.copy(
                isPreloadActive = true,
                isPreloadReady = true,
                preloadProgress = 1f,
            )
        }
    }

    private fun startStatsPolling(
        serverUrl: String,
        hash: String,
        playbackUrl: String,
        user: String,
        pass: String,
    ) {
        statsJob?.cancel()
        statsJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                try {
                    val stats = TorrServerRemoteApi.getTorrentDetails(
                        serverUrl = serverUrl,
                        hash = hash,
                        username = user,
                        password = pass,
                    )
                    val cur = _state.value
                    if (stats != null && cur is P2pStreamingState.Streaming) {
                        val ready = stats.isPreloadReady || stats.preloadProgress >= 0.95f || cur.isPreloadReady
                        _state.value = cur.copy(
                            downloadSpeed = stats.downloadSpeed,
                            uploadSpeed = stats.uploadSpeed,
                            peers = stats.activePeers,
                            seeds = stats.connectedSeeders,
                            bufferProgress = stats.preloadProgress,
                            totalProgress = stats.preloadProgress,
                            downloadedBytes = stats.preloadedBytes,
                            deliveredBytes = stats.loadedSize,
                            preloadedBytes = stats.preloadedBytes,
                            preloadSize = stats.preloadSize,
                            isPreloadActive = cur.isPreloadActive,
                            isPreloadReady = ready,
                            preloadProgress = if (ready) 1f else stats.preloadProgress,
                            stat = stats.stat,
                            statString = stats.statString,
                        )
                    }
                } catch (_: CancellationException) {
                    break
                } catch (e: Exception) {
                    log.w { "Stats polling error: ${e.message}" }
                }
                delay(1000L)
            }
        }
    }

    fun resolveFileIndex(
        files: List<TorrServerRemoteFile>,
        targetSeason: Int?,
        targetEpisode: Int?,
    ): Int? {
        if (files.isEmpty()) return null
        if (targetSeason != null && targetEpisode != null) {
            val videoFiles = files.filter { f ->
                f.path.substringAfterLast('.', "").lowercase() in VIDEO_EXTENSIONS
            }
            val pool = videoFiles.ifEmpty { files }
            val pattern = Regex("(?i)s0*${targetSeason}[ex]0*${targetEpisode}(?:[^0-9]|$)")
            val match = pool.firstOrNull { pattern.containsMatchIn(it.path) }
            if (match != null) return match.id
        }
        val videoFile = files
            .filter { it.path.substringAfterLast('.', "").lowercase() in VIDEO_EXTENSIONS }
            .maxByOrNull { it.length }
        return videoFile?.id ?: files.maxByOrNull { it.length }?.id
    }

    private suspend fun resolveFileIndex(
        serverUrl: String,
        hash: String,
        requestedIdx: Int?,
        filename: String?,
        targetSeason: Int?,
        targetEpisode: Int?,
        user: String,
        pass: String,
    ): Int {
        val deadline = WatchProgressClock.nowEpochMs() + 15_000L
        var files: List<TorrServerRemoteFile> = emptyList()

        while (WatchProgressClock.nowEpochMs() < deadline) {
            files = TorrServerRemoteApi.getTorrentDetails(serverUrl, hash, user, pass)?.files ?: emptyList()
            if (files.isNotEmpty()) break
            log.d { "Waiting for torrent metadata..." }
            delay(1000L)
        }

        if (files.isEmpty()) {
            val fallback = requestedIdx ?: 1
            log.w { "No files after metadata timeout, using index $fallback" }
            return fallback
        }

        log.d { "Torrent has ${files.size} files" }

        // Strategy 0: Exact match by requested file ID (e.g. from TorrentFilePickerDialog)
        if (requestedIdx != null && files.any { it.id == requestedIdx }) {
            log.d { "File resolved by exact file ID: id=$requestedIdx" }
            return requestedIdx
        }

        // Strategy 1: Match by filename
        if (!filename.isNullOrBlank()) {
            val name = filename.trim()
            val exact = files.firstOrNull { f ->
                f.path.substringAfterLast('/').equals(name, ignoreCase = true)
            }
            if (exact != null) {
                log.d { "File resolved by exact filename: ${exact.path} -> id=${exact.id}" }
                return exact.id
            }
            val contains = files.firstOrNull { f ->
                f.path.contains(name, ignoreCase = true)
            }
            if (contains != null) {
                log.d { "File resolved by filename contains: ${contains.path} -> id=${contains.id}" }
                return contains.id
            }
        }

        // Strategy 2: Match by Season & Episode
        if (targetSeason != null && targetEpisode != null) {
            val videoFiles = files.filter { f ->
                f.path.substringAfterLast('.', "").lowercase() in VIDEO_EXTENSIONS
            }
            val pool = videoFiles.ifEmpty { files }
            val pattern = Regex("(?i)s0*${targetSeason}[ex]0*${targetEpisode}(?:[^0-9]|$)")
            val match = pool.firstOrNull { pattern.containsMatchIn(it.path) }
            if (match != null) {
                log.d { "File resolved by Season/Episode match: ${match.path} -> id=${match.id}" }
                return match.id
            }
        }

        // Strategy 3: Match by ID offset (requestedIdx + 1)
        if (requestedIdx != null) {
            val tsIdx = requestedIdx + 1
            if (files.any { it.id == tsIdx }) {
                log.d { "File resolved by ID offset: id=$tsIdx" }
                return tsIdx
            }
        }

        // Strategy 4: Positional index
        if (requestedIdx != null && requestedIdx in files.indices) {
            val positional = files[requestedIdx]
            log.d { "File resolved by positional index: [$requestedIdx] -> ${positional.path} (id=${positional.id})" }
            return positional.id
        }

        // Strategy 5: Fallback to largest video file
        val videoFile = files
            .filter { it.path.substringAfterLast('.', "").lowercase() in VIDEO_EXTENSIONS }
            .maxByOrNull { it.length }

        val result = videoFile?.id ?: files.maxByOrNull { it.length }?.id ?: 1
        log.d { "File resolved by largest video fallback: id=$result" }
        return result
    }

    private fun buildMagnetUri(infoHash: String, extraTrackers: List<String>): String {
        val trackers = (DEFAULT_TRACKERS + extraTrackers).distinct()
        val trackerParams = trackers.joinToString("") { "&tr=$it" }
        return "magnet:?xt=urn:btih:$infoHash$trackerParams"
    }
}
