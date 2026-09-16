package com.nuvio.app.features.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

@Composable
actual fun rememberFontPickerLauncher(
    onFontSelected: (name: String, path: String) -> Unit,
): () -> Unit {
    return remember { {} }
}

@Composable
actual fun rememberSubtitleFilePickerLauncher(
    onSubtitleSelected: (name: String, fileUri: String) -> Unit,
): () -> Unit {
    return remember { {} }
}
