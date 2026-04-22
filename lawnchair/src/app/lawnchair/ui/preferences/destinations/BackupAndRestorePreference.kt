package app.lawnchair.ui.preferences.destinations

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.lawnchair.backup.ui.restoreBackupOpener
import app.lawnchair.ui.preferences.LocalIsExpandedScreen
import app.lawnchair.ui.preferences.components.NavigationActionPreference
import app.lawnchair.ui.preferences.components.controls.ClickablePreference
import app.lawnchair.ui.preferences.components.layout.PreferenceGroup
import app.lawnchair.ui.preferences.components.layout.PreferenceLayout
import app.lawnchair.ui.preferences.navigation.CreateBackup
import com.android.launcher3.R

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
            Item {
                NavigationActionPreference(
                    label = stringResource(R.string.create_backup),
                    subtitle = stringResource(R.string.create_backup_description),
                    destination = CreateBackup,
                )
            }
            Item {
                ClickablePreference(
                    label = stringResource(R.string.restore_backup),
                    subtitle = stringResource(R.string.restore_backup_description),
                    onClick = restoreBackupOpener(),
                )
            }
        }
    }
}
