package com.nuvio.app.features.torrserver

import com.nuvio.app.features.addons.httpRequestRaw
import io.ktor.util.encodeBase64

object TorrServerRemoteApi {

    private fun authHeaders(username: String, password: String): Map<String, String> {
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
