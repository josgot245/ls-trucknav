package de.ls.trucknav.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.ls.trucknav.ui.theme.AppDanger
import de.ls.trucknav.ui.theme.AppPrimary
import de.ls.trucknav.ui.theme.AppSurface
import de.ls.trucknav.ui.theme.AppText
import de.ls.trucknav.ui.theme.AppTextMuted

/** Dialog, der eine neuere Version meldet und die Installation startet. */
@Composable
fun UpdateDialog(
    state: UiState,
    onInstall: () -> Unit,
    onDismiss: () -> Unit,
) {
    val info = state.updateInfo ?: return
    val percent = state.updateProgress?.let { (it * 100).toInt() }

    AlertDialog(
        onDismissRequest = { if (!state.updateDownloading) onDismiss() },
        containerColor = AppSurface,
        titleContentColor = AppText,
        textContentColor = AppTextMuted,
        title = {
            Text(text = "Update verfügbar", fontWeight = FontWeight.Bold)
        },
        text = {
            Column {
                Text(
                    text = "Version ${info.version} ist verfügbar " +
                        "(installiert: ${state.currentVersion}).",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppText,
                )

                if (info.notes.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = info.notes,
                        style = MaterialTheme.typography.bodySmall,
                        color = AppTextMuted,
                        modifier = Modifier
                            .heightIn(max = 220.dp)
                            .verticalScroll(rememberScrollState()),
                    )
                }

                if (state.updateDownloading) {
                    Spacer(Modifier.height(14.dp))
                    LinearProgressIndicator(
                        progress = { state.updateProgress ?: 0f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = if (percent != null) {
                            "Wird heruntergeladen … $percent %"
                        } else {
                            "Wird heruntergeladen …"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = AppTextMuted,
                    )
                }

                state.updateError?.let { error ->
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = AppDanger,
                    )
                }

                state.updateMessage?.let { message ->
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = AppTextMuted,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onInstall,
                enabled = !state.updateDownloading,
            ) {
                Text(
                    text = if (state.updateDownloading) "Bitte warten …" else "Jetzt aktualisieren",
                    color = AppPrimary,
                    fontWeight = FontWeight.Bold,
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !state.updateDownloading,
            ) {
                Text("Später", color = AppTextMuted)
            }
        },
    )
}
