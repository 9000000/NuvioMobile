package com.nuvio.app.features.torrserver

import com.nuvio.app.features.addons.AddonHttpClientProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.IOException

internal actual suspend fun runTorrServerPreloadStream(
    url: String,
    headers: Map<String, String>,
) = withContext(Dispatchers.IO) {
    val requestBuilder = Request.Builder().url(url)
    headers.forEach { (key, value) ->
        requestBuilder.header(key, value)
    }
    val call = AddonHttpClientProvider.get().newCall(requestBuilder.build())
    try {
        call.execute().use { response ->
            if (response.isSuccessful) {
                val byteStream = response.body?.byteStream()
                val buffer = ByteArray(16384)
                while (isActive) {
                    val read = byteStream?.read(buffer) ?: -1
                    if (read == -1) break
                }
            }
        }
    } catch (_: IOException) {
        // Normal when cancelled or closed
    } finally {
        call.cancel()
    }
}
