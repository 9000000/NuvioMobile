package com.nuvio.app.features.torrserver

import com.nuvio.app.features.addons.httpRequestRaw
import io.ktor.util.encodeBase64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

object TorrServerRemoteApi {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun authHeaders(username: String, password: String): Map<String, String> {
        return if (username.isNotBlank() || password.isNotBlank()) {
            val credentials = "$username:$password"
            mapOf("Authorization" to "Basic ${credentials.encodeBase64()}")
        } else {
            emptyMap()
        }
    }

    suspend fun healthCheck(
        serverUrl: String,
        username: String = "",
        password: String = "",
    ): Result<String> {
        val base = serverUrl.trim().trimEnd('/')
        if (base.isBlank()) return Result.failure(IllegalArgumentException("Địa chỉ máy chủ trống"))
        return try {
            val response = httpRequestRaw(
                method = "GET",
                url = "$base/echo",
                headers = authHeaders(username, password),
                body = "",
            )
            if (response.status in 200..299) {
                Result.success(response.body.trim().ifBlank { "TorrServer OK" })
            } else {
                Result.failure(Exception("HTTP ${response.status}: ${response.statusText}"))
            }
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    suspend fun checkGStreamerSupport(
        serverUrl: String,
        username: String = "",
        password: String = "",
    ): Result<Boolean> {
        val base = serverUrl.trim().trimEnd('/')
        if (base.isBlank()) return Result.failure(IllegalArgumentException("Địa chỉ máy chủ trống"))
        return try {
            val response = httpRequestRaw(
                method = "GET",
                url = "$base/gst/settings",
                headers = authHeaders(username, password),
                body = "",
            )
            if (response.status in 200..299) {
                val hasGst = response.body.contains("\"built_in\":true") ||
                    response.body.contains("\"config\":")
                Result.success(hasGst)
            } else {
                Result.success(false)
            }
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    suspend fun addTorrent(
        serverUrl: String,
        magnetLink: String,
        title: String? = null,
        poster: String? = null,
        saveToDb: Boolean = false,
        username: String = "",
        password: String = "",
    ): String? {
        val base = serverUrl.trim().trimEnd('/')
        if (base.isBlank() || magnetLink.isBlank()) return null

        val body = buildJsonObject {
            put("action", "add")
            put("link", magnetLink)
            put("save_to_db", saveToDb)
            if (!title.isNullOrBlank()) {
                put("title", title)
            }
            if (!poster.isNullOrBlank()) {
                put("poster", poster)
            }
        }.toString()

        val headers = authHeaders(username, password) + mapOf(
            "Content-Type" to "application/json",
            "Accept" to "application/json",
        )

        return try {
            val response = httpRequestRaw(
                method = "POST",
                url = "$base/torrents",
                headers = headers,
                body = body,
            )
            if (response.status in 200..299 && response.body.isNotBlank()) {
                val elem = json.parseToJsonElement(response.body).jsonObject
                elem["hash"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
            } else {
                null
            }
        } catch (_: Throwable) {
            null
        }
    }

    suspend fun getTorrentDetails(
        serverUrl: String,
        hash: String,
        username: String = "",
        password: String = "",
    ): TorrServerRemoteStatus? {
        val base = serverUrl.trim().trimEnd('/')
        if (base.isBlank() || hash.isBlank()) return null

        val body = buildJsonObject {
            put("action", "get")
            put("hash", hash)
        }.toString()

        val headers = authHeaders(username, password) + mapOf(
            "Content-Type" to "application/json",
            "Accept" to "application/json",
        )

        return try {
            val response = httpRequestRaw(
                method = "POST",
                url = "$base/torrents",
                headers = headers,
                body = body,
            )
            if (response.status !in 200..299 || response.body.isBlank()) return null
            val root = json.parseToJsonElement(response.body).jsonObject

            val fileStatsArray = root["file_stats"]?.jsonArray
            val files = fileStatsArray?.mapIndexedNotNull { index, item ->
                val f = item.jsonObject
                TorrServerRemoteFile(
                    id = f["id"]?.jsonPrimitive?.intOrNull ?: (index + 1),
                    path = f["path"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    length = f["length"]?.jsonPrimitive?.longOrNull ?: 0L,
                )
            } ?: emptyList()

            TorrServerRemoteStatus(
                hash = root["hash"]?.jsonPrimitive?.contentOrNull ?: hash,
                title = root["title"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() },
                stat = root["stat"]?.jsonPrimitive?.intOrNull ?: 0,
                statString = root["stat_string"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() },
                downloadSpeed = (root["download_speed"]?.jsonPrimitive?.doubleOrNull ?: 0.0).toLong(),
                uploadSpeed = (root["upload_speed"]?.jsonPrimitive?.doubleOrNull ?: 0.0).toLong(),
                preloadedBytes = root["preloaded_bytes"]?.jsonPrimitive?.longOrNull ?: 0L,
                preloadSize = root["preload_size"]?.jsonPrimitive?.longOrNull ?: 0L,
                loadedSize = root["loaded_size"]?.jsonPrimitive?.longOrNull ?: 0L,
                torrentSize = root["torrent_size"]?.jsonPrimitive?.longOrNull ?: 0L,
                activePeers = root["active_peers"]?.jsonPrimitive?.intOrNull ?: 0,
                connectedSeeders = root["connected_seeders"]?.jsonPrimitive?.intOrNull ?: 0,
                totalPeers = root["total_peers"]?.jsonPrimitive?.intOrNull ?: 0,
                files = files,
            )
        } catch (_: Throwable) {
            null
        }
    }

    suspend fun dropTorrent(
        serverUrl: String,
        hash: String,
        username: String = "",
        password: String = "",
    ) {
        val base = serverUrl.trim().trimEnd('/')
        if (base.isBlank() || hash.isBlank()) return

        val body = buildJsonObject {
            put("action", "drop")
            put("hash", hash)
        }.toString()

        val headers = authHeaders(username, password) + mapOf(
            "Content-Type" to "application/json",
            "Accept" to "application/json",
        )

        try {
            httpRequestRaw(
                method = "POST",
                url = "$base/torrents",
                headers = headers,
                body = body,
            )
        } catch (_: Throwable) {
        }
    }

    suspend fun runPreload(
        preloadUrl: String,
        username: String = "",
        password: String = "",
    ) {
        runTorrServerPreloadStream(preloadUrl, authHeaders(username, password))
    }

    fun buildStreamUrl(
        serverUrl: String,
        magnetLink: String,
        fileIdx: Int = 0,
        preload: Boolean = false,
        save: Boolean = false,
        gst: Boolean = false,
        hash: String? = null,
    ): String {
        val base = serverUrl.trim().trimEnd('/')
        if (gst && !hash.isNullOrBlank()) {
            return "$base/gst/$hash/master.m3u8?index=$fileIdx"
        }
        val encodedLink = urlEncode(magnetLink)
        val sb = StringBuilder("$base/stream?link=$encodedLink&index=$fileIdx")
        if (preload) {
            sb.append("&preload")
        } else {
            sb.append("&play")
        }
        if (save) {
            sb.append("&save")
        }
        return sb.toString()
    }

    fun buildDirectStreamUrl(serverUrl: String, hash: String, fileIdx: Int): String {
        val base = serverUrl.trim().trimEnd('/')
        return "$base/play/$hash/$fileIdx"
    }

    fun buildGstStreamUrl(serverUrl: String, hash: String, fileIdx: Int): String {
        val base = serverUrl.trim().trimEnd('/')
        return "$base/gst/$hash/master.m3u8?index=$fileIdx"
    }

    private fun urlEncode(value: String): String {
        val allowedChars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_.~"
        val out = StringBuilder()
        for (char in value) {
            if (char in allowedChars) {
                out.append(char)
            } else {
                val bytes = char.toString().encodeToByteArray()
                for (b in bytes) {
                    val hex = (b.toInt() and 0xFF).toString(16).uppercase()
                    out.append('%')
                    if (hex.length == 1) out.append('0')
                    out.append(hex)
                }
            }
        }
        return out.toString()
    }
}
