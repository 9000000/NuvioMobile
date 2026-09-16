package com.nuvio.app.features.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.features.torrserver.TorrServerConfigRepository
import com.nuvio.app.features.torrserver.TorrServerRemoteApi
import com.nuvio.app.features.torrserver.TorrServerSettingsUiState
import kotlinx.coroutines.launch
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.action_cancel
import nuvio.composeapp.generated.resources.action_save
import nuvio.composeapp.generated.resources.settings_torrserver
import nuvio.composeapp.generated.resources.settings_torrserver_description
import nuvio.composeapp.generated.resources.torrserver_auth
import nuvio.composeapp.generated.resources.torrserver_auth_description
import nuvio.composeapp.generated.resources.torrserver_auth_password
import nuvio.composeapp.generated.resources.torrserver_auth_username
import nuvio.composeapp.generated.resources.torrserver_check_gst
import nuvio.composeapp.generated.resources.torrserver_enable
import nuvio.composeapp.generated.resources.torrserver_enable_description
import nuvio.composeapp.generated.resources.torrserver_gst
import nuvio.composeapp.generated.resources.torrserver_gst_description
import nuvio.composeapp.generated.resources.torrserver_preload
import nuvio.composeapp.generated.resources.torrserver_preload_description
import nuvio.composeapp.generated.resources.torrserver_save_to_db
import nuvio.composeapp.generated.resources.torrserver_save_to_db_description
import nuvio.composeapp.generated.resources.torrserver_server_url
import nuvio.composeapp.generated.resources.torrserver_server_url_hint
import nuvio.composeapp.generated.resources.torrserver_test_connection
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
internal fun LazyListScope.torrServerSettingsContent(
    isTablet: Boolean,
    uiState: TorrServerSettingsUiState,
    onEnabledToggle: (Boolean) -> Unit,
    onServerUrlSave: (String) -> Unit,
    onCredentialsSave: (String, String) -> Unit,
    onPreloadToggle: (Boolean) -> Unit,
    onSaveToDbToggle: (Boolean) -> Unit,
    onGstToggle: (Boolean) -> Unit,
    onTestConnection: () -> Unit,
    onCheckGst: () -> Unit,
    onShowUrlDialog: () -> Unit,
    onShowAuthDialog: () -> Unit,
) {
    item {
        SettingsSection(
            title = stringResource(Res.string.settings_torrserver),
            isTablet = isTablet,
        ) {
            SettingsGroup(isTablet = isTablet) {
                SettingsSwitchRow(
                    title = stringResource(Res.string.torrserver_enable),
                    description = stringResource(Res.string.torrserver_enable_description),
                    checked = uiState.enabled,
                    onCheckedChange = onEnabledToggle,
                    isTablet = isTablet,
                )
            }
        }
    }

    if (uiState.enabled) {
        item {
            SettingsSection(
                title = stringResource(Res.string.torrserver_server_url),
                isTablet = isTablet,
            ) {
                SettingsGroup(isTablet = isTablet) {
                    SettingsNavigationRow(
                        title = stringResource(Res.string.torrserver_server_url),
                        description = uiState.serverUrl.ifBlank { stringResource(Res.string.torrserver_server_url_hint) },
                        icon = Icons.Rounded.Dns,
                        isTablet = isTablet,
                        onClick = onShowUrlDialog,
                    )
                    SettingsGroupDivider(isTablet = isTablet)
                    val authSubtitle = if (uiState.authUsername.isNotBlank()) {
                        "Username: ${uiState.authUsername}"
                    } else {
                        stringResource(Res.string.torrserver_auth_description)
                    }
                    SettingsNavigationRow(
                        title = stringResource(Res.string.torrserver_auth),
                        description = authSubtitle,
                        icon = Icons.Rounded.Lock,
                        isTablet = isTablet,
                        onClick = onShowAuthDialog,
                    )
                }
            }
        }

        item {
            SettingsSection(
                title = "STREAMING",
                isTablet = isTablet,
            ) {
                SettingsGroup(isTablet = isTablet) {
                    SettingsSwitchRow(
                        title = stringResource(Res.string.torrserver_preload),
                        description = stringResource(Res.string.torrserver_preload_description),
                        checked = uiState.preload,
                        onCheckedChange = onPreloadToggle,
                        isTablet = isTablet,
                    )
                    SettingsGroupDivider(isTablet = isTablet)
                    SettingsSwitchRow(
                        title = stringResource(Res.string.torrserver_save_to_db),
                        description = stringResource(Res.string.torrserver_save_to_db_description),
                        checked = uiState.saveToDb,
                        onCheckedChange = onSaveToDbToggle,
                        isTablet = isTablet,
                    )
                    SettingsGroupDivider(isTablet = isTablet)
                    SettingsSwitchRow(
                        title = stringResource(Res.string.torrserver_gst),
                        description = stringResource(Res.string.torrserver_gst_description),
                        checked = uiState.gst,
                        onCheckedChange = onGstToggle,
                        isTablet = isTablet,
                    )
                }
            }
        }

        item {
            SettingsSection(
                title = "DIAGNOSTICS",
                isTablet = isTablet,
            ) {
                SettingsGroup(isTablet = isTablet) {
                    TorrServerActionRow(
                        title = stringResource(Res.string.torrserver_test_connection),
                        statusMessage = uiState.serverStatusMessage,
                        isSuccess = uiState.serverStatusSuccess,
                        isLoading = uiState.isTestingServer,
                        onClick = onTestConnection,
                        isTablet = isTablet,
                    )
                    if (uiState.gst) {
                        SettingsGroupDivider(isTablet = isTablet)
                        TorrServerActionRow(
                            title = stringResource(Res.string.torrserver_check_gst),
                            statusMessage = uiState.gstStatusMessage,
                            isSuccess = uiState.gstStatusSuccess,
                            isLoading = uiState.isCheckingGst,
                            onClick = onCheckGst,
                            isTablet = isTablet,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TorrServerActionRow(
    title: String,
    statusMessage: String?,
    isSuccess: Boolean?,
    isLoading: Boolean,
    onClick: () -> Unit,
    isTablet: Boolean,
) {
    val tokens = MaterialTheme.nuvio
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isLoading, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f, fill = false)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
            )
            if (!statusMessage.isNullOrBlank()) {
                val statusColor = when (isSuccess) {
                    true -> Color(0xFF4CAF50)
                    false -> Color(0xFFEF5350)
                    else -> tokens.colors.textMuted
                }
                Text(
                    text = statusMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = statusColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }

        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = tokens.colors.accent,
            )
        } else if (isSuccess != null) {
            Icon(
                imageVector = if (isSuccess) Icons.Rounded.CheckCircle else Icons.Rounded.Error,
                contentDescription = null,
                tint = if (isSuccess) Color(0xFF4CAF50) else Color(0xFFEF5350),
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TorrServerUrlDialog(
    currentUrl: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var urlText by remember { mutableStateOf(currentUrl) }
    val tokens = MaterialTheme.nuvio

    BasicAlertDialog(
        onDismissRequest = onDismiss,
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = tokens.colors.surfaceElevated,
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    text = stringResource(Res.string.torrserver_server_url),
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                )

                OutlinedTextField(
                    value = urlText,
                    onValueChange = { urlText = it },
                    singleLine = true,
                    placeholder = { Text(stringResource(Res.string.torrserver_server_url_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(Res.string.action_cancel))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { onSave(urlText); onDismiss() },
                        colors = ButtonDefaults.buttonColors(containerColor = tokens.colors.accent),
                    ) {
                        Text(stringResource(Res.string.action_save))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TorrServerAuthDialog(
    currentUsername: String,
    currentPassword: String,
    onSave: (String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var userText by remember { mutableStateOf(currentUsername) }
    var passText by remember { mutableStateOf(currentPassword) }
    val tokens = MaterialTheme.nuvio

    BasicAlertDialog(
        onDismissRequest = onDismiss,
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = tokens.colors.surfaceElevated,
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    text = stringResource(Res.string.torrserver_auth),
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                )

                OutlinedTextField(
                    value = userText,
                    onValueChange = { userText = it },
                    singleLine = true,
                    label = { Text(stringResource(Res.string.torrserver_auth_username)) },
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = passText,
                    onValueChange = { passText = it },
                    singleLine = true,
                    label = { Text(stringResource(Res.string.torrserver_auth_password)) },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(Res.string.action_cancel))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { onSave(userText, passText); onDismiss() },
                        colors = ButtonDefaults.buttonColors(containerColor = tokens.colors.accent),
                    ) {
                        Text(stringResource(Res.string.action_save))
                    }
                }
            }
        }
    }
}
