package com.nuvio.app.features.torrserver

import com.nuvio.app.core.network.IPv4FirstDns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

private val preloadHttpClient by lazy {
    OkHttpClient.Builder()
        .dns(IPv4FirstDns())
        .connectionPool(ConnectionPool(5, 5, TimeUnit.MINUTES))
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()
}

internal actual suspend fun runTorrServerPreloadStream(
    url: String,
    headers: Map<String, String>,
) = withContext(Dispatchers.IO) {
    val requestBuilder = Request.Builder().url(url)
    headers.forEach { (key, value) ->
        requestBuilder.header(key, value)
    }
    val call = preloadHttpClient.newCall(requestBuilder.build())
    try {
        call.execute().use { response ->
            if (response.isSuccessful) {
                co.touchlab.kermit.Logger.withTag("TorrServerPreload").d { "TorrServer preload stream connected successfully" }
                val byteStream = response.body?.byteStream()
                val buffer = ByteArray(16384)
                while (isActive) {
                    val read = byteStream?.read(buffer) ?: -1
                    if (read == -1) break
                }
            } else {
                co.touchlab.kermit.Logger.withTag("TorrServerPreload").w { "TorrServer preload request failed: ${response.code} ${response.message}" }
            }
        }
    } catch (_: IOException) {
        // Normal when cancelled or closed
    } catch (e: Throwable) {
        co.touchlab.kermit.Logger.withTag("TorrServerPreload").w { "TorrServer preload stream error: ${e.message}" }
    } finally {
        call.cancel()
    }
}
