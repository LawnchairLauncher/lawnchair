package app.lawnchair.ui.preferences.about

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.lawnchair.util.getApkVersionComparison
import com.android.launcher3.R
import java.io.File

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun UpdateSection(
    updateState: UpdateState,
    onViewChanges: () -> Unit,
    onInstall: (File) -> Unit,
    onForceInstall: (File) -> Unit,
    onDismissMajorUpdate: (File) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var showMajorDialog by remember(updateState) { mutableStateOf(updateState is UpdateState.MajorUpdate) }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (updateState) {
            UpdateState.Hidden -> { /* Render nothing */ }

            UpdateState.Checking -> {
                CircularProgressIndicator(modifier = Modifier.padding(top = 8.dp))
            }

            UpdateState.UpToDate -> {
                Text(
                    text = stringResource(R.string.pro_updated),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            is UpdateState.Available -> {
                Button(
                    onClick = onViewChanges,
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(text = stringResource(R.string.download_update))
                }
            }

            is UpdateState.Downloading -> {
                LinearProgressIndicator(
                    progress = { updateState.progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                )
                Text(
                    text = "${(updateState.progress * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            is UpdateState.Downloaded -> {
                Button(
                    onClick = {
                        onInstall(updateState.file)
                    },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(text = stringResource(R.string.install_update))
                }
            }

            is UpdateState.Failed -> {
                Text(
                    text = stringResource(R.string.update_check_failed),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            is UpdateState.MajorUpdate -> if (showMajorDialog) {
                val comparison = context.getApkVersionComparison(updateState.file)
                val currentMajor = comparison?.first?.getOrNull(0) ?: 0
                val apkMajor = comparison?.second?.getOrNull(0) ?: 0
                AlertDialog(
                    onDismissRequest = { showMajorDialog = false },
                    title = { Text(stringResource(R.string.major_update_label)) },
                    text = { Text(stringResource(R.string.major_update_description, "$currentMajor", "$apkMajor")) },
                    icon = { Icon(Icons.Rounded.Warning, null) },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                showMajorDialog = false
                                onForceInstall(updateState.file)
                            },
                            shapes = ButtonDefaults.shapes(),
                        ) {
                            Text(stringResource(R.string.install_update))
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = {
                                showMajorDialog = false
                                onDismissMajorUpdate(updateState.file)
                            },
                            shapes = ButtonDefaults.shapes(),
                        ) {
                            Text(stringResource(android.R.string.cancel))
                        }
                    },
                )
            }

            is UpdateState.Disabled -> {
                var reason = stringResource(R.string.pro_disabled_by_unknown)
                if (updateState.reason == UpdateDisabledReason.MAJOR_IS_NEWER) {
                    reason = stringResource(R.string.pro_disabled_by_major_is_newer)
                }
                Text(
                    text = reason,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .padding(horizontal = 12.dp),
                )
            }
        }
    }
}
