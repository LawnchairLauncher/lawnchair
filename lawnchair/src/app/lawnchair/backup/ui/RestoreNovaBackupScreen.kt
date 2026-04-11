package app.lawnchair.backup.ui

import android.app.Activity
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import app.lawnchair.backup.NovaBackupConverter.NovaBackupInfo
import app.lawnchair.backup.ui.RestoreNovaBackupViewModel.Event
import app.lawnchair.backup.ui.RestoreNovaBackupViewModel.State
import app.lawnchair.preferences.getAdapter
import app.lawnchair.preferences2.preferenceManager2
import app.lawnchair.ui.preferences.LocalIsExpandedScreen
import app.lawnchair.ui.preferences.LocalNavController
import app.lawnchair.ui.preferences.components.controls.SwitchPreference
import app.lawnchair.ui.preferences.components.controls.WarningPreference
import app.lawnchair.ui.preferences.components.layout.PreferenceGroup
import app.lawnchair.ui.preferences.components.layout.PreferenceLayout
import app.lawnchair.ui.preferences.components.layout.PreferenceTemplate
import app.lawnchair.ui.preferences.navigation.RestoreNovaBackup
import app.lawnchair.util.BackHandler
import app.lawnchair.util.restartLauncher
import com.android.launcher3.R
import java.util.Base64
import kotlinx.coroutines.flow.Flow

fun NavGraphBuilder.restoreNovaBackupGraph() {
    composable<RestoreNovaBackup> { backStackEntry ->
        val route: RestoreNovaBackup = backStackEntry.toRoute()
        val backupUri = remember {
            String(Base64.getDecoder().decode(route.base64Uri)).toUri()
        }
        val viewModel: RestoreNovaBackupViewModel = viewModel()
        DisposableEffect(key1 = null) {
            viewModel.init(backupUri)
            onDispose { }
        }
        RestoreNovaBackupScreen()
    }
}

@Composable
internal fun RestoreNovaBackupScreen(
    viewModel: RestoreNovaBackupViewModel = viewModel(),
) {
    val state = viewModel.state.collectAsStateWithLifecycle()

    CollectEvents(viewModel.events)

    PreferenceLayout(
        label = stringResource(id = R.string.restore_nova_backup),
        backArrowVisible = !LocalIsExpandedScreen.current,
    ) {
        when (val stateValue = state.value) {
            is State.Success -> {
                RestoreNovaBackupContent(
                    state = stateValue,
                    onRestore = viewModel::restore,
                )
            }

            is State.Loading -> RestoreNovaBackupLoading()

            is State.Error -> RestoreNovaBackupError()
        }
    }
}

@Composable
internal fun ColumnScope.RestoreNovaBackupContent(
    state: State.Success,
    onRestore: () -> Unit,
) {
    if (state.isRestoring) {
        BackHandler {}
    }

    BackupInfoGroup(state.info)

    if (state.info.isSubgrid) {
        SubgridWarning()
    }

    BackupInfoOptions()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .padding(horizontal = 16.dp),
    ) {
        Button(
            onClick = onRestore,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxWidth(),
            enabled = !state.isRestoring,
        ) {
            Text(text = stringResource(id = R.string.action_restore))
        }
    }
}

@Composable
private fun BackupInfoGroup(info: NovaBackupInfo) {
    PreferenceGroup {
        if (info.columns != null && info.rows != null) {
            BackupInfoStringPreference(
                R.string.nova_grid_label,
                stringResource(R.string.nova_grid_value, info.columns, info.rows),
            )
        }
        BackupInfoIntPreference(R.string.nova_dock_label, info.hotseatCount)
        BackupInfoIntPreference(R.string.nova_app_label, info.appCount)
        BackupInfoIntPreference(R.string.nova_widget_label, info.widgetCount)
        BackupInfoIntPreference(R.string.nova_folder_label, info.folderCount)
        BackupInfoIntPreference(R.string.nova_shortcut_label, info.shortcutCount)
        BackupInfoStringPreference(R.string.nova_icon_pack_label, info.iconPackLabel)
    }
}

@Composable
private fun SubgridWarning() {
    PreferenceGroup {
        WarningPreference(
            text = stringResource(R.string.restore_nova_subgrid_warning),
        )
    }
}

@Composable
private fun BackupInfoOptions() {
    PreferenceGroup {
        val prefs2 = preferenceManager2()
        val smartspaceAdapter = prefs2.enableSmartspace.getAdapter()
        SwitchPreference(
            adapter = smartspaceAdapter,
            label = stringResource(R.string.restore_nova_smartspace_conflict_toggle),
        )
    }
}

@Composable
private fun BackupInfoIntPreference(@StringRes labelRes: Int, value: Int?) {
    if (value != null && value > 0) {
        PreferenceTemplate(
            title = { Text(text = stringResource(labelRes)) },
            description = { Text(text = value.toString()) },
        )
    }
}

@Composable
private fun BackupInfoStringPreference(@StringRes labelRes: Int, value: String?) {
    if (!value.isNullOrEmpty()) {
        PreferenceTemplate(
            title = { Text(text = stringResource(labelRes)) },
            description = { Text(text = value) },
        )
    }
}

@Composable
private fun RestoreNovaBackupLoading() {
    Box(modifier = Modifier.fillMaxSize()) {
        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
    }
}

@Composable
private fun RestoreNovaBackupError() {
    val context = LocalContext.current
    val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher

    LaunchedEffect(Unit) {
        Toast.makeText(context, R.string.invalid_nova_backup_file, Toast.LENGTH_SHORT).show()
        backDispatcher?.onBackPressed()
    }
}

@Composable
private fun CollectEvents(events: Flow<Event>) {
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        events.collect { event ->
            when (event) {
                is Event.RestoreSuccess -> {
                    Toast.makeText(context, R.string.backup_restore_success, Toast.LENGTH_SHORT)
                        .show()
                    restartLauncher(context)
                }

                is Event.RestoreError -> {
                    Toast.makeText(context, R.string.backup_restore_error, Toast.LENGTH_SHORT)
                        .show()
                }
            }
        }
    }
}

@Composable
fun restoreNovaBackupOpener(): () -> Unit {
    val navController = LocalNavController.current

    val request =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
            val uri = it.data?.data ?: return@rememberLauncherForActivityResult

            val base64Uri = Base64.getEncoder().encodeToString(uri.toString().toByteArray())
            navController.navigate(RestoreNovaBackup(base64Uri))
        }

    return {
        Intent(Intent.ACTION_OPEN_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType("*/*")
            .let { request.launch(it) }
    }
}
