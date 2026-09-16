package com.nuvio.app.features.player

import androidx.compose.runtime.Composable

@Composable
expect fun rememberFontPickerLauncher(
    onFontSelected: (name: String, path: String) -> Unit,
): () -> Unit

@Composable
expect fun rememberSubtitleFilePickerLauncher(
    onSubtitleSelected: (name: String, fileUri: String) -> Unit,
): () -> Unit
