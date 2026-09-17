package com.nuvio.app.features.torrserver

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import platform.Foundation.NSMutableURLRequest
import platform.Foundation.NSURL
import platform.Foundation.NSURLSession
import platform.Foundation.NSURLSessionConfiguration
import platform.Foundation.setValue

internal actual suspend fun runTorrServerPreloadStream(
    url: String,
    headers: Map<String, String>,
) = withContext(Dispatchers.IO) {
    val nsUrl = NSURL.URLWithString(url) ?: return@withContext
    val request = NSMutableURLRequest.requestWithURL(nsUrl)
    headers.forEach { (key, value) ->
        request.setValue(value, forHTTPHeaderField = key)
    }
    val session = NSURLSession.sessionWithConfiguration(
        NSURLSessionConfiguration.defaultSessionConfiguration
    )
    val task = session.dataTaskWithRequest(request)
    task.resume()
    try {
        while (isActive) {
            delay(500L)
        }
    } finally {
        task.cancel()
        session.finishTasksAndInvalidate()
    }
}
