package com.nuvio.app.features.torrserver

/**
 * Runs a streaming GET request against TorrServer's &preload URL in the background,
 * reading chunks until the coroutine is cancelled or the server closes the stream.
 */
internal expect suspend fun runTorrServerPreloadStream(
    url: String,
    headers: Map<String, String>,
)
