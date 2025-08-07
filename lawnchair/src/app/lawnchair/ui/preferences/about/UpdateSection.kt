package app.lawnchair.ui.preferences.about

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.android.launcher3.R

@Composable
fun UpdateSection(
    updateState: UpdateState,
    showChangesDialog: Boolean,
    onEvent: (AboutEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
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
                Row {
                    Button(
                        onClick = { onEvent(AboutEvent.OnDownloadClicked) },
                    ) {
                        Text(text = stringResource(R.string.download_update))
                    }
                    if (showChangesDialog) {
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedButton(
                            onClick = { onEvent(AboutEvent.OnViewChangesClicked) },
                        ) {
                            Text(text = stringResource(R.string.view_changes))
                        }
                    }
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
                    onClick = { onEvent(AboutEvent.OnInstallClicked(updateState.file)) },
                ) {
                    Text(text = stringResource(R.string.install_update))
                }
            }
            UpdateState.Failed -> {
                Text(
                    text = stringResource(R.string.update_check_failed),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}
