package com.nuvio.app.features.player

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import java.io.File
import java.io.FileOutputStream

@Composable
actual fun rememberFontPickerLauncher(
    onFontSelected: (name: String, path: String) -> Unit,
): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val result = copyUriToInternalStorage(context, uri, subDir = "fonts") ?: return@rememberLauncherForActivityResult
        onFontSelected(result.first, result.second.absolutePath)
    }

    return remember(launcher) {
        {
            launcher.launch(arrayOf("font/*", "application/x-font-ttf", "application/x-font-opentype", "application/octet-stream", "*/*"))
        }
    }
}

@Composable
actual fun rememberSubtitleFilePickerLauncher(
    onSubtitleSelected: (name: String, fileUri: String) -> Unit,
): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val result = copyUriToInternalStorage(context, uri, subDir = "subtitles") ?: return@rememberLauncherForActivityResult
        val fileUri = Uri.fromFile(result.second).toString()
        onSubtitleSelected(result.first, fileUri)
    }

    return remember(launcher) {
        {
            launcher.launch(arrayOf("text/*", "application/x-subrip", "application/octet-stream", "*/*"))
        }
    }
}

private fun copyUriToInternalStorage(context: Context, uri: Uri, subDir: String): Pair<String, File>? {
    return try {
        var displayName: String? = null
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) {
                    displayName = cursor.getString(index)
                }
            }
        }
        val safeName = displayName?.takeIf { it.isNotBlank() } ?: "file_${System.currentTimeMillis()}"
        val targetDir = context.filesDir.resolve(subDir).apply { mkdirs() }
        val targetFile = targetDir.resolve(safeName)

        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(targetFile).use { output ->
                input.copyTo(output)
            }
        }
        Pair(safeName, targetFile)
    } catch (e: Throwable) {
        e.printStackTrace()
        null
    }
}
