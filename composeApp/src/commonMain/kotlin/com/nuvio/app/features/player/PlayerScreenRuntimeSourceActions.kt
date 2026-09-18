package com.nuvio.app.features.player

import com.nuvio.app.core.ui.NuvioToastController
import com.nuvio.app.features.debrid.DirectDebridPlayableResult
import com.nuvio.app.features.debrid.DirectDebridPlaybackResolver
import com.nuvio.app.features.debrid.toastMessage
import com.nuvio.app.features.details.MetaDetailsRepository
import com.nuvio.app.features.details.MetaVideo
import com.nuvio.app.features.downloads.DownloadItem
import com.nuvio.app.features.downloads.DownloadSubtitles
import com.nuvio.app.features.downloads.DownloadsRepository
import com.nuvio.app.features.p2p.P2pSettingsRepository
import com.nuvio.app.features.p2p.P2pStreamingEngine
import com.nuvio.app.features.torrserver.TorrServerConfigRepository
import com.nuvio.app.features.torrserver.TorrServerRemoteApi
import com.nuvio.app.features.torrserver.TorrServerService
import com.nuvio.app.features.torrserver.buildTorrServerMagnet
import com.nuvio.app.features.streams.StreamItem
import com.nuvio.app.features.streams.StreamLinkCacheRepository
import com.nuvio.app.features.livetv.LiveTvChannel
import com.nuvio.app.features.livetv.LiveTvRepository
import com.nuvio.app.features.watchprogress.WatchProgressRepository
import com.nuvio.app.features.watchprogress.buildPlaybackVideoId
import kotlinx.coroutines.launch

internal fun PlayerScreenRuntime.resolveDebridForPlayer(
    stream: StreamItem,
    season: Int?,
    episode: Int?,
    onResolved: (StreamItem) -> Unit,
    onStale: () -> Unit,
): Boolean {
    if (!DirectDebridPlaybackResolver.shouldResolveToPlayableStream(stream)) return false
    scope.launch {
        val resolved = DirectDebridPlaybackResolver.resolveToPlayableStream(
            stream = stream,
            season = season,
            episode = episode,
        )
        when (resolved) {
            is DirectDebridPlayableResult.Success -> onResolved(resolved.stream)
            else -> {
                resolved.toastMessage()?.let { NuvioToastController.show(it) }
                if (resolved == DirectDebridPlayableResult.Stale) {
                    onStale()
                }
            }
        }
    }
    return true
}

internal fun PlayerScreenRuntime.p2pSentinelUrl(infoHash: String, fileIdx: Int?): String =
    "torrent://$infoHash${fileIdx?.let { "?index=$it" }.orEmpty()}"

internal fun PlayerScreenRuntime.isP2pStream(stream: StreamItem): Boolean {
    TorrServerConfigRepository.ensureLoaded()
    val isTorr = stream.addonName == "TorrServer" ||
        (TorrServerConfigRepository.uiState.value.enabled &&
            (stream.p2pInfoHash != null || stream.isTorrentStream))
    return isTorr || (stream.needsLocalDebridResolve && stream.p2pInfoHash != null)
}

internal fun PlayerScreenRuntime.openExternalSourceUrl(stream: StreamItem): Boolean {
    if (!stream.shouldOpenExternally) return false
    val url = stream.externalOpenUrl ?: return false
    val openExternalUrl = args.onOpenExternalUrl ?: return false
    openExternalUrl(url)
    showSourcesPanel = false
    showEpisodesPanel = false
    controlsVisible = true
    PlayerStreamsRepository.pauseSearchForPlayback()
    return true
}

internal fun StreamItem.playerSourceIdentityKey(): String? {
    p2pInfoHash?.trim()?.lowercase()?.takeIf { it.isNotBlank() }?.let { hash ->
        return "torrent:$hash:${p2pFileIdx ?: -1}"
    }

    clientResolve?.let { resolve ->
        val raw = resolve.stream?.raw
        val keyParts = listOf(
            addonId,
            resolve.service,
            resolve.serviceIndex?.toString(),
            resolve.infoHash?.trim()?.lowercase(),
            resolve.fileIdx?.toString(),
            resolve.magnetUri,
            resolve.torrentName,
            resolve.filename,
            raw?.torrentName,
            raw?.filename,
            raw?.size?.toString(),
            behaviorHints.filename,
            behaviorHints.videoSize?.toString(),
            streamLabel,
            streamSubtitle,
        ).map { it.orEmpty().trim() }
        if (keyParts.any { it.isNotBlank() }) {
            return "resolve:${keyParts.joinToString("|")}"
        }
    }

    behaviorHints.videoHash?.trim()?.takeIf { it.isNotBlank() }?.let { hash ->
        return "hash:$addonId:$hash:${behaviorHints.videoSize ?: ""}:${behaviorHints.filename.orEmpty()}"
    }

    playableDirectUrl?.trim()?.takeIf { it.isNotBlank() }?.let { url ->
        return "url:$url"
    }

    val fallbackParts = listOf(
        addonId,
        addonName,
        streamLabel,
        streamSubtitle.orEmpty(),
        behaviorHints.filename.orEmpty(),
        behaviorHints.videoSize?.toString().orEmpty(),
        sourceName.orEmpty(),
        sources.joinToString(","),
    ).map { it.trim() }
    return fallbackParts
        .takeIf { parts -> parts.any { it.isNotBlank() } }
        ?.joinToString(separator = "|", prefix = "meta:")
}

internal fun PlayerScreenRuntime.stopActiveP2pStream() {
    if (activeTorrentInfoHash != null || p2pResolvedSourceUrl != null) {
        P2pStreamingEngine.stopStream()
        com.nuvio.app.features.torrserver.TorrServerService.stopStream()
    }
    activeTorrentInfoHash = null
    activeTorrentFileIdx = null
    activeTorrentFilename = null
    activeTorrentTrackers = emptyList()
    p2pResolvedSourceUrl = null
}

internal fun PlayerScreenRuntime.saveP2pStreamForReuse(
    stream: StreamItem,
    videoId: String?,
    season: Int?,
    episode: Int?,
) {
    if (!playerSettingsUiState.streamReuseLastLinkEnabled || videoId == null) return
    val infoHash = stream.p2pInfoHash ?: return
    val cacheKey = StreamLinkCacheRepository.contentKey(
        type = contentType ?: parentMetaType,
        videoId = videoId,
        parentMetaId = parentMetaId,
        season = season,
        episode = episode,
    )
    StreamLinkCacheRepository.save(
        contentKey = cacheKey,
        url = "",
        streamName = stream.streamLabel,
        addonName = stream.addonName,
        addonId = stream.addonId,
        requestHeaders = emptyMap(),
        responseHeaders = emptyMap(),
        filename = stream.behaviorHints.filename,
        videoSize = stream.behaviorHints.videoSize,
        infoHash = infoHash,
        fileIdx = stream.p2pFileIdx,
        sources = stream.sources,
        bingeGroup = stream.behaviorHints.bingeGroup,
    )
}

internal fun PlayerScreenRuntime.switchToP2pSourceStream(stream: StreamItem) {
    val infoHash = stream.p2pInfoHash ?: return
    TorrServerConfigRepository.ensureLoaded()
    val isTorr = stream.addonName == "TorrServer" ||
        TorrServerConfigRepository.uiState.value.enabled
    if (!isTorr && !P2pSettingsRepository.isVisible) return
    if (!isTorr && !P2pSettingsRepository.uiState.value.p2pEnabled) {
        P2pSettingsRepository.setP2pEnabled(true)
    }
    val currentPositionMs = playbackSnapshot.positionMs.coerceAtLeast(0L)
    flushWatchProgress()
    stopActiveP2pStream()
    saveP2pStreamForReuse(
        stream = stream,
        videoId = activeVideoId,
        season = activeSeasonNumber,
        episode = activeEpisodeNumber,
    )
    externalSubtitles = stream.externalSubtitles
    activeSourceUrl = p2pSentinelUrl(infoHash, stream.p2pFileIdx)
    activeSourceAudioUrl = null
    activeSourceHeaders = emptyMap()
    activeSourceResponseHeaders = emptyMap()
    activeStreamType = null
    activeDrmType = null
    activeDrmKey = null
    activeTorrentInfoHash = infoHash
    activeTorrentFileIdx = stream.p2pFileIdx
    activeTorrentFilename = stream.behaviorHints.filename
    activeTorrentTrackers = stream.p2pTrackers
    activeSourceIdentityKey = stream.playerSourceIdentityKey()
    activeStreamTitle = stream.streamLabel
    activeStreamSubtitle = stream.streamSubtitle
    activeProviderName = stream.addonName
    activeProviderAddonId = stream.addonId
    currentStreamBingeGroup = stream.behaviorHints.bingeGroup
    activeInitialPositionMs = currentPositionMs
    activeInitialProgressFraction = null
    showSourcesPanel = false
    controlsVisible = true
    PlayerStreamsRepository.pauseSearchForPlayback()
}

internal fun PlayerScreenRuntime.switchToP2pEpisodeStream(
    stream: StreamItem,
    episode: MetaVideo,
    isAutoPlay: Boolean = false,
) {
    val infoHash = stream.p2pInfoHash ?: return
    TorrServerConfigRepository.ensureLoaded()
    val isTorr = stream.addonName == "TorrServer" ||
        TorrServerConfigRepository.uiState.value.enabled
    if (!isTorr && !P2pSettingsRepository.isVisible) return
    if (!isTorr && !P2pSettingsRepository.uiState.value.p2pEnabled) {
        P2pSettingsRepository.setP2pEnabled(true)
    }
    resetEpisodePanelAndNextEpisodeState()
    flushWatchProgress()
    stopActiveP2pStream()
    val epVideoId = episode.id
    val resume = resolveEpisodeResume(epVideoId, episode)
    saveP2pStreamForReuse(
        stream = stream,
        videoId = epVideoId,
        season = episode.season,
        episode = episode.episode,
    )
    externalSubtitles = stream.externalSubtitles
    activeSourceUrl = p2pSentinelUrl(infoHash, stream.p2pFileIdx)
    activeSourceAudioUrl = null
    activeSourceHeaders = emptyMap()
    activeSourceResponseHeaders = emptyMap()
    activeStreamType = null
    activeDrmType = null
    activeDrmKey = null
    activeTorrentInfoHash = infoHash
    activeTorrentFileIdx = stream.p2pFileIdx
    activeTorrentFilename = stream.behaviorHints.filename
    activeTorrentTrackers = stream.p2pTrackers
    applyEpisodeStreamMetadata(stream, episode, resume)
}

internal fun PlayerScreenRuntime.switchToSource(stream: StreamItem) {
    if (
        resolveDebridForPlayer(
            stream = stream,
            season = activeSeasonNumber,
            episode = activeEpisodeNumber,
            onResolved = { switchToSource(it) },
            onStale = {
                val vid = activeVideoId
                if (vid != null) {
                    PlayerStreamsRepository.loadSources(
                        type = contentType ?: parentMetaType,
                        videoId = vid,
                        season = activeSeasonNumber,
                        episode = activeEpisodeNumber,
                        forceRefresh = true,
                    )
                }
            },
        )
    ) return
    if (isP2pStream(stream)) {
        if (TorrServerConfigRepository.uiState.value.enabled) {
            prepareTorrServerFilePicker(stream)
            return
        }
        switchToP2pSourceStream(stream)
        return
    }
    if (openExternalSourceUrl(stream)) return
    val url = stream.playableDirectUrl ?: return
    val sourceIdentityKey = stream.playerSourceIdentityKey()
    if (url == activeSourceUrl) {
        activeSourceIdentityKey = sourceIdentityKey ?: activeSourceIdentityKey
        return
    }
    val currentPositionMs = playbackSnapshot.positionMs.coerceAtLeast(0L)
    flushWatchProgress()
    stopActiveP2pStream()
    val currentVideoId = activeVideoId
    if (playerSettingsUiState.streamReuseLastLinkEnabled && currentVideoId != null) {
        saveDirectStreamForReuse(stream, url, currentVideoId, activeSeasonNumber, activeEpisodeNumber)
    }
    externalSubtitles = stream.externalSubtitles
    activeSourceUrl = url
    activeSourceAudioUrl = null
    activeSourceHeaders = sanitizePlaybackHeaders(stream.behaviorHints.proxyHeaders?.request)
    activeSourceResponseHeaders = sanitizePlaybackResponseHeaders(stream.behaviorHints.proxyHeaders?.response)
    activeStreamType = stream.streamType
    activeDrmType = null
    activeDrmKey = null
    activeSourceIdentityKey = sourceIdentityKey
    activeStreamTitle = stream.streamLabel
    activeStreamSubtitle = stream.streamSubtitle
    activeProviderName = stream.addonName
    activeProviderAddonId = stream.addonId
    currentStreamBingeGroup = stream.behaviorHints.bingeGroup
    activeInitialPositionMs = currentPositionMs
    activeInitialProgressFraction = null
    showSourcesPanel = false
    controlsVisible = true
    PlayerStreamsRepository.pauseSearchForPlayback()
}

internal fun PlayerScreenRuntime.switchToEpisodeStream(stream: StreamItem, episode: MetaVideo) {
    if (
        resolveDebridForPlayer(
            stream = stream,
            season = episode.season,
            episode = episode.episode,
            onResolved = { resolvedStream -> switchToEpisodeStream(resolvedStream, episode) },
            onStale = {
                PlayerStreamsRepository.loadEpisodeStreams(
                    type = contentType ?: parentMetaType,
                    videoId = episode.id,
                    season = episode.season,
                    episode = episode.episode,
                    forceRefresh = true,
                )
            },
        )
    ) return
    if (isP2pStream(stream)) {
        if (TorrServerConfigRepository.uiState.value.enabled) {
            prepareTorrServerFilePicker(stream, episode)
            return
        }
        switchToP2pEpisodeStream(stream, episode)
        return
    }
    if (openExternalSourceUrl(stream)) return
    val url = stream.playableDirectUrl ?: return
    resetEpisodePanelAndNextEpisodeState()
    flushWatchProgress()
    stopActiveP2pStream()
    val epVideoId = episode.id
    val resume = resolveEpisodeResume(epVideoId, episode)
    if (playerSettingsUiState.streamReuseLastLinkEnabled) {
        saveDirectStreamForReuse(stream, url, epVideoId, episode.season, episode.episode)
    }
    externalSubtitles = stream.externalSubtitles
    activeSourceUrl = url
    activeSourceAudioUrl = null
    activeSourceHeaders = sanitizePlaybackHeaders(stream.behaviorHints.proxyHeaders?.request)
    activeSourceResponseHeaders = sanitizePlaybackResponseHeaders(stream.behaviorHints.proxyHeaders?.response)
    activeStreamType = stream.streamType
    activeDrmType = null
    activeDrmKey = null
    applyEpisodeStreamMetadata(stream, episode, resume)
}

internal fun PlayerScreenRuntime.switchToDownloadedEpisode(downloadItem: DownloadItem, episode: MetaVideo) {
    val localFileUri = DownloadsRepository.playableLocalFileUri(downloadItem) ?: return
    resetEpisodePanelAndNextEpisodeState()
    flushWatchProgress()
    stopActiveP2pStream()

    val fallbackVideoId = buildPlaybackVideoId(
        parentMetaId = parentMetaId,
        seasonNumber = episode.season,
        episodeNumber = episode.episode,
        fallbackVideoId = episode.id,
    )
    val resolvedVideoId = episode.id.takeIf { it.isNotBlank() } ?: fallbackVideoId
    val epEntry = WatchProgressRepository.progressForVideo(
        videoId = resolvedVideoId,
        parentMetaId = parentMetaId,
        seasonNumber = episode.season,
        episodeNumber = episode.episode,
    )
        ?.takeIf { !it.isCompleted }
    val epResumeFraction = epEntry?.progressPercent
        ?.takeIf { it > 0f }
        ?.let { (it / 100f).coerceIn(0f, 1f) }
    val epResumePositionMs = epEntry?.lastPositionMs?.takeIf { it > 0L } ?: 0L

    externalSubtitles = DownloadSubtitles.localSubtitles(localFileUri)
    activeSourceUrl = localFileUri
    activeSourceAudioUrl = null
    activeSourceHeaders = emptyMap()
    activeSourceResponseHeaders = emptyMap()
    activeStreamType = null
    activeDrmType = null
    activeDrmKey = null
    activeSourceIdentityKey = null
    activeStreamTitle = downloadItem.streamTitle.ifBlank {
        episode.title.ifBlank { title }
    }
    activeStreamSubtitle = downloadItem.streamSubtitle
    activeProviderName = downloadItem.providerName.ifBlank { downloadedLabel }
    activeProviderAddonId = downloadItem.providerAddonId
    currentStreamBingeGroup = null
    activeSeasonNumber = episode.season
    activeEpisodeNumber = episode.episode
    activeEpisodeTitle = episode.title
    activeEpisodeThumbnail = episode.thumbnail
    activePauseDescription = episode.overview
    activeVideoId = resolvedVideoId
    activeInitialPositionMs = epResumePositionMs
    activeInitialProgressFraction = epResumeFraction
    controlsVisible = true
}

internal fun PlayerScreenRuntime.playNextEpisode() {
    scope.launchPlayerNextEpisodeAutoPlay(
        previousJob = nextEpisodeAutoPlayJob,
        nextEpisodeInfo = nextEpisodeInfo,
        allEpisodes = playerMetaVideos,
        parentMetaId = parentMetaId,
        parentMetaType = parentMetaType,
        contentType = contentType,
        settings = playerSettingsUiState,
        currentStreamBingeGroup = currentStreamBingeGroup,
        onDownloadedEpisodeSelected = { item, episode -> switchToDownloadedEpisode(item, episode) },
        onEpisodeStreamSelected = { stream, episode -> switchToEpisodeStream(stream, episode) },
        onManualSelectionRequired = { nextVideo ->
            episodeStreamsPanelState = EpisodeStreamsPanelState(
                showStreams = true,
                selectedEpisode = nextVideo,
            )
            showEpisodesPanel = true
        },
        onSearchingChanged = { nextEpisodeAutoPlaySearching = it },
        onSourceNameChanged = { nextEpisodeAutoPlaySourceName = it },
        onCountdownChanged = { nextEpisodeAutoPlayCountdown = it },
        onNextEpisodeCardVisibleChanged = { showNextEpisodeCard = it },
    )?.let { job ->
        nextEpisodeAutoPlayJob = job
    }
}

internal fun PlayerScreenRuntime.switchToLiveChannel(channel: LiveTvChannel) {
    scope.launch {
        val playableChannel = runCatching { LiveTvRepository.prepareForPlayback(channel) }.getOrDefault(channel)
        switchToPreparedLiveChannel(playableChannel)
    }
}

private fun PlayerScreenRuntime.switchToPreparedLiveChannel(channel: LiveTvChannel) {
    LiveTvRepository.markChannelWatched(channel)

    if (channel.streamUrl == activeSourceUrl) {
        activeStreamTitle = channel.name
        activeStreamSubtitle = channel.group
        activeLogo = channel.logoUrl
        activeVideoId = channel.id
        showLiveChannelsPanel = false
        controlsVisible = true
        return
    }
    if (shouldTrackWatchProgress) {
        flushWatchProgress()
    }
    stopActiveP2pStream()
    activeSourceUrl = channel.streamUrl
    activeSourceAudioUrl = null
    activeSourceHeaders = sanitizePlaybackHeaders(channel.headers)
    activeSourceResponseHeaders = emptyMap()
    activeStreamTitle = channel.name
    activeStreamSubtitle = channel.group
    activeProviderName = "Live TV"
    activeProviderAddonId = null
    activeLogo = channel.logoUrl
    currentStreamBingeGroup = null
    activeSeasonNumber = null
    activeEpisodeNumber = null
    activeEpisodeTitle = null
    activeStreamType = channel.streamType
    activeDrmType = channel.drmType
    activeDrmKey = channel.drmKey
    activeEpisodeThumbnail = null
    activeVideoId = channel.id
    activeInitialPositionMs = 0L
    activeInitialProgressFraction = null
    initialSeekApplied = true
    showSourcesPanel = false
    showEpisodesPanel = false
    showLiveChannelsPanel = false
    controlsVisible = true
    initialLoadCompleted = false
    playbackSnapshot = playbackSnapshot.copy(
        isLoading = true,
        videoWidth = 0,
        videoHeight = 0,
    )
    errorMessage = null
    shouldPlay = true
}

internal fun PlayerScreenRuntime.openSourcesPanel() {
    val vid = activeVideoId ?: return
    PlayerStreamsRepository.loadSources(
        type = contentType ?: parentMetaType,
        videoId = vid,
        season = activeSeasonNumber,
        episode = activeEpisodeNumber,
    )
    showSourcesPanel = true
    showEpisodesPanel = false
    showLiveChannelsPanel = false
    controlsVisible = false
}

internal fun PlayerScreenRuntime.openEpisodesPanel() {
    if (playerMetaVideos.isEmpty()) {
        scope.launch {
            playerMetaVideos = MetaDetailsRepository.fetch(parentMetaType, parentMetaId)?.videos ?: emptyList()
        }
    }
    showEpisodesPanel = true
    showSourcesPanel = false
    showLiveChannelsPanel = false
    controlsVisible = false
}

private data class EpisodeResume(val positionMs: Long, val fraction: Float?)

private fun PlayerScreenRuntime.resetEpisodePanelAndNextEpisodeState() {
    showNextEpisodeCard = false
    showSourcesPanel = false
    showEpisodesPanel = false
    showLiveChannelsPanel = false
    episodeStreamsPanelState = EpisodeStreamsPanelState()
    nextEpisodeAutoPlayJob?.cancel()
    nextEpisodeAutoPlaySearching = false
    nextEpisodeAutoPlaySourceName = null
    nextEpisodeAutoPlayCountdown = null
    PlayerStreamsRepository.clearEpisodeStreams()
}

private fun PlayerScreenRuntime.resolveEpisodeResume(epVideoId: String, episode: MetaVideo): EpisodeResume {
    val epResumeVideoId = buildPlaybackVideoId(
        parentMetaId = parentMetaId,
        seasonNumber = episode.season,
        episodeNumber = episode.episode,
        fallbackVideoId = epVideoId,
    )
    val epEntry = WatchProgressRepository.progressForVideo(
        videoId = epVideoId.takeIf { it.isNotBlank() } ?: epResumeVideoId,
        parentMetaId = parentMetaId,
        seasonNumber = episode.season,
        episodeNumber = episode.episode,
    )?.takeIf { !it.isCompleted }
    val epResumeFraction = epEntry?.progressPercent
        ?.takeIf { it > 0f }
        ?.let { (it / 100f).coerceIn(0f, 1f) }
    val epResumePositionMs = epEntry?.lastPositionMs?.takeIf { it > 0L } ?: 0L
    return EpisodeResume(positionMs = epResumePositionMs, fraction = epResumeFraction)
}

private fun PlayerScreenRuntime.applyEpisodeStreamMetadata(
    stream: StreamItem,
    episode: MetaVideo,
    resume: EpisodeResume,
) {
    activeSourceIdentityKey = stream.playerSourceIdentityKey()
    activeStreamTitle = stream.streamLabel
    activeStreamSubtitle = stream.streamSubtitle
    activeProviderName = stream.addonName
    activeProviderAddonId = stream.addonId
    currentStreamBingeGroup = stream.behaviorHints.bingeGroup
    activeSeasonNumber = episode.season
    activeEpisodeNumber = episode.episode
    activeEpisodeTitle = episode.title
    activeEpisodeThumbnail = episode.thumbnail
    activePauseDescription = episode.overview
    activeVideoId = episode.id
    activeInitialPositionMs = resume.positionMs
    activeInitialProgressFraction = resume.fraction
    controlsVisible = true
}

private fun PlayerScreenRuntime.saveDirectStreamForReuse(
    stream: StreamItem,
    url: String,
    videoId: String,
    season: Int?,
    episode: Int?,
) {
    val cacheKey = StreamLinkCacheRepository.contentKey(
        type = contentType ?: parentMetaType,
        videoId = videoId,
        parentMetaId = parentMetaId,
        season = season,
        episode = episode,
    )
    StreamLinkCacheRepository.save(
        contentKey = cacheKey,
        url = url,
        streamName = stream.streamLabel,
        addonName = stream.addonName,
        addonId = stream.addonId,
        requestHeaders = sanitizePlaybackHeaders(stream.behaviorHints.proxyHeaders?.request),
        responseHeaders = sanitizePlaybackResponseHeaders(stream.behaviorHints.proxyHeaders?.response),
        filename = stream.behaviorHints.filename,
        videoSize = stream.behaviorHints.videoSize,
        bingeGroup = stream.behaviorHints.bingeGroup,
        streamType = stream.streamType,
        contentLanguage = contentLanguage,
    )
}


internal fun PlayerScreenRuntime.switchToTorrServerStream(stream: StreamItem) {
    val infoHash = stream.p2pInfoHash ?: return
    val fileIdx = stream.p2pFileIdx ?: 0
    val currentPositionMs = playbackSnapshot.positionMs.coerceAtLeast(0L)
    flushWatchProgress()
    stopActiveP2pStream()
    saveP2pStreamForReuse(
        stream = stream,
        videoId = activeVideoId,
        season = activeSeasonNumber,
        episode = activeEpisodeNumber,
    )

    externalSubtitles = stream.externalSubtitles
    activeSourceUrl = p2pSentinelUrl(infoHash, fileIdx)
    activeSourceAudioUrl = null
    activeSourceHeaders = emptyMap()
    activeSourceResponseHeaders = emptyMap()
    activeStreamType = null
    activeDrmType = null
    activeDrmKey = null
    activeTorrentInfoHash = infoHash
    activeTorrentFileIdx = fileIdx
    activeTorrentFilename = stream.behaviorHints.filename
    activeTorrentTrackers = stream.p2pTrackers
    activeSourceIdentityKey = stream.playerSourceIdentityKey()
    activeStreamTitle = stream.streamLabel
    activeStreamSubtitle = stream.streamSubtitle
    activeProviderName = stream.addonName ?: "TorrServer"
    activeProviderAddonId = stream.addonId
    currentStreamBingeGroup = stream.behaviorHints.bingeGroup
    activeInitialPositionMs = currentPositionMs
    activeInitialProgressFraction = null
    showSourcesPanel = false
    controlsVisible = true
    PlayerStreamsRepository.pauseSearchForPlayback()
}

internal fun PlayerScreenRuntime.switchToTorrServerEpisodeStream(
    stream: StreamItem,
    episode: MetaVideo,
) {
    val infoHash = stream.p2pInfoHash ?: return
    val fileIdx = stream.p2pFileIdx ?: 0
    resetEpisodePanelAndNextEpisodeState()
    flushWatchProgress()
    stopActiveP2pStream()
    val epVideoId = episode.id
    val resume = resolveEpisodeResume(epVideoId, episode)
    saveP2pStreamForReuse(
        stream = stream,
        videoId = epVideoId,
        season = episode.season,
        episode = episode.episode,
    )

    externalSubtitles = stream.externalSubtitles
    activeSourceUrl = p2pSentinelUrl(infoHash, fileIdx)
    activeSourceAudioUrl = null
    activeSourceHeaders = emptyMap()
    activeSourceResponseHeaders = emptyMap()
    activeStreamType = null
    activeDrmType = null
    activeDrmKey = null
    activeTorrentInfoHash = infoHash
    activeTorrentFileIdx = fileIdx
    activeTorrentFilename = stream.behaviorHints.filename
    activeTorrentTrackers = stream.p2pTrackers
    applyEpisodeStreamMetadata(stream, episode, resume)
}

internal fun PlayerScreenRuntime.prepareTorrServerFilePicker(
    stream: StreamItem,
    episode: MetaVideo? = null,
) {
    val infoHash = stream.p2pInfoHash ?: run {
        if (episode != null) {
            switchToTorrServerEpisodeStream(stream, episode)
        } else {
            switchToTorrServerStream(stream)
        }
        return
    }
    torrentPickerStream = stream
    isTorrentPickerLoading = true
    torrentPickerError = null
    torrentPickerFiles = emptyList()
    torrentPickerJob?.cancel()
    torrentPickerJob = scope.launch {
        try {
            val magnetOverride = buildTorrServerMagnet(stream, infoHash)
            val files = TorrServerService.fetchTorrentFiles(
                infoHash = infoHash,
                title = title,
                poster = poster,
                trackers = stream.p2pTrackers,
                magnetOverride = magnetOverride,
            )
            torrentPickerFiles = files
            isTorrentPickerLoading = false
            if (files.isEmpty()) {
                torrentPickerError = "Không tìm thấy tệp nào trong torrent này"
            }
        } catch (e: Exception) {
            torrentPickerError = e.message ?: "Could not load torrent files"
            isTorrentPickerLoading = false
        }
    }
}
