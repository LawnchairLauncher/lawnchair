package app.lawnchair.ui.preferences.destinations

import android.content.Intent
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.lawnchair.backup.AutoBackup
import app.lawnchair.backup.ui.restoreBackupOpener
import app.lawnchair.backup.ui.restoreNovaBackupOpener
import app.lawnchair.preferences.getAdapter
import app.lawnchair.preferences2.preferenceManager2
import app.lawnchair.ui.preferences.LocalIsExpandedScreen
import app.lawnchair.ui.preferences.components.NavigationActionPreference
import app.lawnchair.ui.preferences.components.controls.ClickablePreference
import app.lawnchair.ui.preferences.components.controls.ListPreference
import app.lawnchair.ui.preferences.components.controls.ListPreferenceEntry
import app.lawnchair.ui.preferences.components.controls.SliderPreference
import app.lawnchair.ui.preferences.components.controls.SwitchPreference
import app.lawnchair.ui.preferences.components.layout.ExpandAndShrink
import app.lawnchair.ui.preferences.components.layout.PreferenceGroup
import app.lawnchair.ui.preferences.components.layout.PreferenceLayout
import app.lawnchair.ui.preferences.navigation.CreateBackup
import com.android.launcher3.R
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.launch

@Composable
fun BackupAndRestorePreference(
    modifier: Modifier = Modifier,
) {
    PreferenceLayout(
        label = stringResource(R.string.backup_and_restore_label),
        backArrowVisible = !LocalIsExpandedScreen.current,
        modifier = modifier,
    ) {
        PreferenceGroup {
            NavigationActionPreference(
                label = stringResource(R.string.create_backup),
                subtitle = stringResource(R.string.create_backup_description),
                destination = CreateBackup,
            )
            ClickablePreference(
                label = stringResource(R.string.restore_backup),
                subtitle = stringResource(R.string.restore_backup_description),
                onClick = restoreBackupOpener(),
            )
        }
        AutoBackupPreferences()
        PreferenceGroup {
            ClickablePreference(
                label = stringResource(R.string.restore_nova_backup),
                subtitle = stringResource(R.string.restore_nova_backup_description),
                onClick = restoreNovaBackupOpener(),
            )
        }
    }
}

/** LC: settings of the automatic backup (see [AutoBackup]). */
@Composable
private fun AutoBackupPreferences(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val prefs = preferenceManager2()
    val scope = rememberCoroutineScope()
    val enabled = prefs.autoBackupEnabled.getAdapter()
    val folder by prefs.autoBackupFolder.getAdapter().state
    val status by remember { AutoBackup.status(context) }.collectAsStateWithLifecycle()

    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        runCatching { context.contentResolver.takePersistableUriPermission(uri, flags) }
        scope.launch {
            prefs.autoBackupFolder.set(uri.toString())
            prefs.autoBackupEnabled.set(true)
        }
    }

    PreferenceGroup(
        heading = stringResource(R.string.auto_backup_label),
        modifier = modifier,
    ) {
        SwitchPreference(
            checked = enabled.state.value,
            onCheckedChange = { checked ->
                if (checked && folder.isEmpty()) {
                    pickFolder.launch(null)
                } else {
                    enabled.onChange(checked)
                }
            },
            label = stringResource(R.string.auto_backup_label),
            description = stringResource(R.string.auto_backup_description),
        )
        ExpandAndShrink(visible = enabled.state.value) {
            Column {
                ClickablePreference(
                    label = stringResource(R.string.auto_backup_folder),
                    subtitle = folderName(folder) ?: stringResource(R.string.auto_backup_folder_none),
                    onClick = { pickFolder.launch(null) },
                )
                ListPreference(
                    adapter = prefs.autoBackupIntervalDays.getAdapter(),
                    entries = listOf(1, 3, 7).map { days ->
                        ListPreferenceEntry(days) {
                            if (days == 1) {
                                stringResource(R.string.auto_backup_daily)
                            } else {
                                stringResource(R.string.auto_backup_every_days, days)
                            }
                        }
                    },
                    label = stringResource(R.string.auto_backup_interval),
                )
                SliderPreference(
                    label = stringResource(R.string.auto_backup_keep),
                    adapter = prefs.autoBackupKeep.getAdapter(),
                    valueRange = 1..10,
                    step = 1,
                )
                val current = status
                ClickablePreference(
                    label = stringResource(R.string.auto_backup_now),
                    subtitle = when {
                        current?.lastError != null -> stringResource(R.string.auto_backup_failed, current.lastError)

                        current != null && current.lastTime > 0 -> stringResource(
                            R.string.auto_backup_last,
                            DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(current.lastTime)),
                        )

                        else -> stringResource(R.string.auto_backup_never)
                    },
                    onClick = { AutoBackup.backUpNow(context.applicationContext) },
                )
            }
        }
    }
}

/** Shows e.g. "Download/Backups" for a picked folder. */
private fun folderName(uri: String): String? {
    if (uri.isEmpty()) return null
    return runCatching {
        val id = DocumentsContract.getTreeDocumentId(uri.toUri())
        id.substringAfter(':').ifEmpty { id }
    }.getOrNull()
}
