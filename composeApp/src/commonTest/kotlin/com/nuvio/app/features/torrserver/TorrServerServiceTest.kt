package com.nuvio.app.features.torrserver

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TorrServerServiceTest {

    @Test
    fun testTorrServerDisplayTitle() {
        assertEquals("[NuvioM] Inception (2010)", torrServerDisplayTitle("Inception (2010)"))
        assertEquals(null, torrServerDisplayTitle(null))
        assertEquals(null, torrServerDisplayTitle("   "))
    }

    @Test
    fun testTorrServerRemoteStatusPreloadReady() {
        val notReady = TorrServerRemoteStatus(
            hash = "123456",
            title = "Test",
            stat = 1,
            statString = "Connecting",
            torrentSize = 1_000_000_000L,
            preloadedBytes = 10_000_000L,
            preloadSize = 50_000_000L,
        )
        assertFalse(notReady.isPreloadReady)
        assertEquals(0.2f, notReady.preloadProgress)

        // Khi mới add torrent / lấy danh sách file xong: server ở stat = 3 (active), nhưng chưa buffer xong
        val activeMetadataOnly = TorrServerRemoteStatus(
            hash = "123456",
            title = "Test",
            stat = 3, // TorrentWorking / active
            statString = "Working",
            torrentSize = 1_000_000_000L,
            preloadedBytes = 0L,
            preloadSize = 0L,
        )
        assertFalse(activeMetadataOnly.isPreloadReady)
        assertEquals(0.0f, activeMetadataOnly.preloadProgress)

        val readyBySize = TorrServerRemoteStatus(
            hash = "123456",
            title = "Test",
            stat = 2,
            statString = "Preloading",
            torrentSize = 1_000_000_000L,
            preloadedBytes = 55_000_000L,
            preloadSize = 50_000_000L,
        )
        assertTrue(readyBySize.isPreloadReady)
        assertEquals(1.0f, readyBySize.preloadProgress)

        // 94% (47MB / 50MB) -> chưa sẵn sàng
        val threshold94 = TorrServerRemoteStatus(
            hash = "123456",
            title = "Test",
            stat = 2,
            statString = "Preloading",
            torrentSize = 1_000_000_000L,
            preloadedBytes = 47_000_000L,
            preloadSize = 50_000_000L,
        )
        assertFalse(threshold94.isPreloadReady)

        // 95% (47.5MB / 50MB) -> chuyển sang play ngay lập tức
        val threshold95 = TorrServerRemoteStatus(
            hash = "123456",
            title = "Test",
            stat = 2,
            statString = "Preloading",
            torrentSize = 1_000_000_000L,
            preloadedBytes = 47_500_000L,
            preloadSize = 50_000_000L,
        )
        assertTrue(threshold95.isPreloadReady)
        assertEquals(1.0f, threshold95.preloadProgress)
    }

    @Test
    fun testEvaluatePreloadReadiness_InitialActiveState_NotReady() {
        // Lưu ý quan trọng từ người dùng: Khi check thông tin file lần đầu để có danh sách file,
        // trạng thái của server đang là active (stat = 3), preloadedBytes = 0.
        // Khi vừa bắt đầu preload, KHÔNG ĐƯỢC coi là ready!
        val activeInitialStatus = TorrServerRemoteStatus(
            hash = "123456",
            stat = 3,
            statString = "Torrent working",
            preloadedBytes = 0L,
            preloadSize = 0L,
        )
        val ready = TorrServerService.evaluatePreloadReadiness(
            stats = activeInitialStatus,
            hasObservedPreloadState = false,
            elapsedMs = 200L,
            gracePeriodMs = 5_000L,
        )
        assertFalse(ready)
    }

    @Test
    fun testEvaluatePreloadReadiness_PreloadTransitionToActive_Ready() {
        // Sau khi đã chứng kiến server vào pha preload (stat == 2), server chuyển sang active (stat == 3)
        // -> TorrServer đã nạp buffer xong và báo active -> READY!
        val transitionedStatus = TorrServerRemoteStatus(
            hash = "123456",
            stat = 3,
            statString = "Torrent working",
            preloadedBytes = 33_554_432L,
            preloadSize = 33_554_432L,
        )
        val ready = TorrServerService.evaluatePreloadReadiness(
            stats = transitionedStatus,
            hasObservedPreloadState = true,
            elapsedMs = 3_000L,
            gracePeriodMs = 5_000L,
        )
        assertTrue(ready)
    }

    @Test
    fun testEvaluatePreloadReadiness_Buffer95Percent_Ready() {
        // Đạt >= 95% buffer ngay cả khi server vẫn đang ở stat = 2 (preloading) -> READY!
        val preloadingStatus = TorrServerRemoteStatus(
            hash = "123456",
            stat = 2,
            statString = "Torrent preload",
            preloadedBytes = 48_000_000L,
            preloadSize = 50_000_000L,
        )
        val ready = TorrServerService.evaluatePreloadReadiness(
            stats = preloadingStatus,
            hasObservedPreloadState = true,
            elapsedMs = 2_000L,
            gracePeriodMs = 5_000L,
        )
        assertTrue(ready)
    }

    @Test
    fun testEvaluatePreloadReadiness_CachedGracePeriod_Ready() {
        // Nếu torrent đã được cache từ trước trên server và server giữ nguyên active (stat = 3),
        // sau khi hết grace period (5s) -> READY để không bị treo
        val cachedStatus = TorrServerRemoteStatus(
            hash = "123456",
            stat = 3,
            statString = "active",
            preloadedBytes = 0L,
            preloadSize = 0L,
        )
        val readyBeforeGrace = TorrServerService.evaluatePreloadReadiness(
            stats = cachedStatus,
            hasObservedPreloadState = false,
            elapsedMs = 2_000L,
            gracePeriodMs = 5_000L,
        )
        assertFalse(readyBeforeGrace)

        val readyAfterGrace = TorrServerService.evaluatePreloadReadiness(
            stats = cachedStatus,
            hasObservedPreloadState = false,
            elapsedMs = 5_500L,
            gracePeriodMs = 5_000L,
        )
        assertTrue(readyAfterGrace)
    }

    @Test
    fun testResolveFileIndexBySeasonAndEpisode() {
        val files = listOf(
            TorrServerRemoteFile(id = 1, path = "Sample/sample.mkv", length = 50_000_000L),
            TorrServerRemoteFile(id = 2, path = "Breaking.Bad.S01E01.720p.mkv", length = 800_000_000L),
            TorrServerRemoteFile(id = 3, path = "Breaking.Bad.S01E02.720p.mkv", length = 850_000_000L),
            TorrServerRemoteFile(id = 4, path = "Subtitles/en.srt", length = 50_000L),
        )

        val resolvedEp1 = TorrServerService.resolveFileIndex(files, targetSeason = 1, targetEpisode = 1)
        assertEquals(2, resolvedEp1)

        val resolvedEp2 = TorrServerService.resolveFileIndex(files, targetSeason = 1, targetEpisode = 2)
        assertEquals(3, resolvedEp2)
    }

    @Test
    fun testResolveFileIndexFallbackLargestVideo() {
        val files = listOf(
            TorrServerRemoteFile(id = 1, path = "trailer.mp4", length = 100_000_000L),
            TorrServerRemoteFile(id = 2, path = "Movie.2024.1080p.mkv", length = 4_500_000_000L),
            TorrServerRemoteFile(id = 3, path = "sample.mkv", length = 50_000_000L),
        )

        // Không truyền season/episode -> chọn video lớn nhất
        val resolved = TorrServerService.resolveFileIndex(files, targetSeason = null, targetEpisode = null)
        assertEquals(2, resolved)
    }

    @Test
    fun testBuildTorrServerMagnetWithCustomTrackers() {
        val magnet = TorrServerRemoteApi.buildStreamUrl(
            serverUrl = "http://127.0.0.1:8090",
            magnetLink = "magnet:?xt=urn:btih:0123456789abcdef",
            fileIdx = 1,
            preload = false,
            save = false,
            gst = false,
            hash = "0123456789abcdef",
        )
        assertNotNull(magnet)
        assertTrue(magnet.contains("http://127.0.0.1:8090/stream?"))
        assertTrue(magnet.contains("play"))
        assertFalse(magnet.contains("preload"))
    }

    @Test
    fun testPreloadUrlHelpers() {
        val preloadUrl = "http://127.0.0.1:8090/stream?link=magnet%3A%3Fxt%3Durn%3Abtih%3Aabc&index=1&preload"
        assertTrue(TorrServerRemoteApi.hasPreloadParam(preloadUrl))
        assertTrue(TorrServerRemoteApi.isTorrServerUrl(preloadUrl))

        val playUrl = TorrServerRemoteApi.toPlaybackUrl(preloadUrl)
        assertFalse(TorrServerRemoteApi.hasPreloadParam(playUrl))
        assertTrue(playUrl.contains("play"))
        assertTrue(playUrl.contains("index=1"))

        val alreadyPlayUrl = "http://127.0.0.1:8090/stream?link=magnet%3A%3Fxt%3Durn%3Abtih%3Aabc&index=1&play"
        assertEquals(alreadyPlayUrl, TorrServerRemoteApi.toPlaybackUrl(alreadyPlayUrl))
    }

    @Test
    fun testBuildTorrServerUrlPreloadVsPlay() {
        val preloadUrl = TorrServerRemoteApi.buildStreamUrl(
            serverUrl = "http://127.0.0.1:8090",
            magnetLink = "magnet:?xt=urn:btih:0123456789abcdef",
            fileIdx = 1,
            preload = true,
        )
        assertTrue(preloadUrl.contains("&preload"))
        assertFalse(preloadUrl.contains("play"))

        val playUrl = TorrServerRemoteApi.buildStreamUrl(
            serverUrl = "http://127.0.0.1:8090",
            magnetLink = "magnet:?xt=urn:btih:0123456789abcdef",
            fileIdx = 1,
            preload = false,
        )
        assertTrue(playUrl.contains("&play"))
        assertFalse(playUrl.contains("preload"))
    }
}

