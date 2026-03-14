package app.lawnchair.backup.ui

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import app.lawnchair.ui.preferences.LocalIsExpandedScreen
import app.lawnchair.ui.preferences.LocalNavController
import app.lawnchair.ui.preferences.components.layout.PreferenceGroup
import app.lawnchair.ui.preferences.components.layout.PreferenceLayout
import app.lawnchair.ui.preferences.navigation.RestoreNovaBackup
import app.lawnchair.util.BackHandler
import app.lawnchair.util.restartLauncher
import com.android.launcher3.R
import java.util.Base64
import kotlinx.coroutines.launch

fun NavGraphBuilder.restoreNovaBackupGraph() {
    composable<RestoreNovaBackup> { backStackEntry ->
        val route: RestoreNovaBackup = backStackEntry.toRoute()
        val backupUri = remember {
            val base64Uri = route.base64Uri
            val backupUriString = String(Base64.getDecoder().decode(base64Uri))
            backupUriString.toUri()
        }
        RestoreNovaBackupScreen(backupUri)
    }
}

@Composable
internal fun RestoreNovaBackupScreen(
    backupUri: Uri,
    viewModel: RestoreNovaBackupViewModel = viewModel(),
) {
    LaunchedEffect(Unit) {
        viewModel.loadBackup(backupUri)
    }

    val state = viewModel.uiState.collectAsStateWithLifecycle().value

    PreferenceLayout(
        label = stringResource(id = R.string.restore_nova_backup),
        backArrowVisible = !LocalIsExpandedScreen.current,
    ) {
        when (state) {
            is RestoreNovaBackupUiState.Success -> RestoreNovaBackupContent(state)
            is RestoreNovaBackupUiState.Loading -> RestoreNovaBackupLoading()
            is RestoreNovaBackupUiState.Error -> RestoreNovaBackupError()
        }
    }
}

@Composable
internal fun ColumnScope.RestoreNovaBackupContent(
    state: RestoreNovaBackupUiState.Success,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var restoring by remember { mutableStateOf(false) }
    if (restoring) {
        BackHandler {}
    }

    val countItems = remember(state.info) {
        listOf(
            R.string.nova_app_count to state.info.appCount,
            R.string.nova_widget_count to state.info.widgetCount,
            R.string.nova_folder_count to state.info.folderCount,
            R.string.nova_shortcut_count to state.info.shortcutCount,
        ).filter { (_, count) -> count > 0 }
    }

    fun restore() {
        if (restoring) return
        scope.launch {
            restoring = true
            try {
                state.converter.convertAndRestore(state.info)
                Toast.makeText(context, R.string.backup_restore_success, Toast.LENGTH_SHORT).show()
                restartLauncher(context)
            } catch (t: Throwable) {
                Log.e("RestoreNovaBackup", "failed to restore Nova backup", t)
                Toast.makeText(context, R.string.backup_restore_error, Toast.LENGTH_SHORT).show()
            }
            restoring = false
        }
    }

    PreferenceGroup {
        Text(
            text = stringResource(R.string.nova_grid_info, state.info.columns, state.info.rows),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        Text(
            text = stringResource(R.string.nova_dock_count, state.info.hotseatCount),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        for ((stringRes, count) in countItems) {
            Text(
                text = stringResource(stringRes, count),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        if (!state.info.iconPackPackage.isNullOrEmpty()) {
            val packageManager = LocalContext.current.packageManager
            val iconPackLabel = remember(state.info.iconPackPackage) {
                try {
                    packageManager.getApplicationInfo(state.info.iconPackPackage, 0)
                        .loadLabel(packageManager)
                        .toString()
                } catch (_: PackageManager.NameNotFoundException) {
                    state.info.iconPackPackage
                }
            }
            Text(
                text = stringResource(R.string.nova_icon_pack, iconPackLabel),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .padding(horizontal = 16.dp),
    ) {
        Button(
            onClick = { restore() },
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxWidth(),
            enabled = !restoring,
        ) {
            Text(text = stringResource(id = R.string.action_restore))
        }
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
    DisposableEffect(null) {
        Toast.makeText(context, R.string.invalid_nova_backup_file, Toast.LENGTH_SHORT).show()
        backDispatcher?.onBackPressed()
        onDispose { }
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
