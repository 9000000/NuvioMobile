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

        val readyByStat = TorrServerRemoteStatus(
            hash = "123456",
            title = "Test",
            stat = 3, // TorrentWorking
            statString = "Working",
            torrentSize = 1_000_000_000L,
            preloadedBytes = 10_000_000L,
            preloadSize = 50_000_000L,
        )
        assertTrue(readyByStat.isPreloadReady)

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
}
