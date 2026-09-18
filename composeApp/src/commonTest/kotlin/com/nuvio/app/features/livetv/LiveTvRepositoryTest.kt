package com.nuvio.app.features.livetv

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class LiveTvRepositoryTest {

    @Test
    fun parseM3uWithKodiPropDrmAndExtvlcHeaders() {
        val playlist = """
            #EXTM3U
            #KODIPROP:inputstream.adaptive.license_type=clearkey
            #KODIPROP:inputstream.adaptive.license_key=0123456789abcdef0123456789abcdef:fedcba9876543210fedcba9876543210
            #KODIPROP:inputstream.adaptive.manifest_type=mpd
            #EXTVLCOPT:http-user-agent=TestUserAgent/1.0
            #EXTVLCOPT:http-referrer=https://referrer.example.com
            #EXTINF:-1 tvg-id="vtv1" tvg-name="VTV1 HD" tvg-logo="https://logo.png" group-title="Vietnam",VTV1 HD
            https://stream.example.com/live/vtv1.mpd
        """.trimIndent()

        val channels = parseM3uPlaylist(playlist)
        assertEquals(1, channels.size)

        val channel = channels.first()
        assertEquals("VTV1 HD", channel.name)
        assertEquals("Vietnam", channel.group)
        assertEquals("https://stream.example.com/live/vtv1.mpd", channel.streamUrl)
        assertEquals("clearkey", channel.drmType)
        assertEquals("0123456789abcdef0123456789abcdef:fedcba9876543210fedcba9876543210", channel.drmKey)
        assertEquals("mpd", channel.streamType)
        assertEquals("TestUserAgent/1.0", channel.headers["User-Agent"])
        assertEquals("https://referrer.example.com", channel.headers["Referer"])
    }

    @Test
    fun parseM3uWithPipedHeadersInUrl() {
        val playlist = """
            #EXTM3U
            #EXTINF:-1 tvg-name="HBO HD" group-title="Movies",HBO HD
            https://stream.example.com/hbo.m3u8|User-Agent=CustomAgent&Referer=https://hbo.com&X-Token=secret123
        """.trimIndent()

        val channels = parseM3uPlaylist(playlist)
        assertEquals(1, channels.size)

        val channel = channels.first()
        assertEquals("HBO HD", channel.name)
        assertEquals("https://stream.example.com/hbo.m3u8", channel.streamUrl)
        assertEquals("CustomAgent", channel.headers["User-Agent"])
        assertEquals("https://hbo.com", channel.headers["Referer"])
        assertEquals("secret123", channel.headers["X-Token"])
    }

    @Test
    fun parseM3uWithPlaylistMetadata() {
        val playlistSource = """
            #EXTM3U
            #EXTINF:-1 tvg-name="Discovery Channel" group-title="Documentary",Discovery
            https://stream.example.com/discovery.m3u8
        """.trimIndent()

        val playlist = LiveTvPlaylist(
            id = "playlist-123",
            name = "My IPTV List",
            type = LiveTvPlaylistType.Url,
            source = "https://example.com/list.m3u",
            isEnabled = true,
        )

        val channels = parseM3uPlaylist(playlistSource, playlist)
        assertEquals(1, channels.size)

        val channel = channels.first()
        assertEquals("Discovery", channel.name)
        assertEquals("playlist-123", channel.playlistId)
        assertEquals("My IPTV List", channel.playlistName)
    }
}
