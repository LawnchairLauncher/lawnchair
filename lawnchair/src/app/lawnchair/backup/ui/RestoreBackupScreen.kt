package app.lawnchair.backup.ui

import android.app.Activity
import android.content.Intent
import android.content.res.Configuration
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import app.lawnchair.backup.LawnchairBackup
import app.lawnchair.ui.preferences.LocalIsExpandedScreen
import app.lawnchair.ui.preferences.LocalNavController
import app.lawnchair.ui.preferences.components.DummyLauncherBox
import app.lawnchair.ui.preferences.components.controls.FlagSwitchPreference
import app.lawnchair.ui.preferences.components.layout.PreferenceGroup
import app.lawnchair.ui.preferences.components.layout.PreferenceLayout
import app.lawnchair.ui.preferences.navigation.RestoreBackup
import app.lawnchair.util.BackHandler
import app.lawnchair.util.hasFlag
import app.lawnchair.util.restartLauncher
import com.android.launcher3.R
import java.util.Base64
import kotlinx.coroutines.launch

fun NavGraphBuilder.restoreBackupGraph() {
    composable<RestoreBackup> { backStackEntry ->
        val route: RestoreBackup = backStackEntry.toRoute()
        val backupUri = remember {
            val base64Uri = route.base64Uri
            val backupUriString = String(Base64.getDecoder().decode(base64Uri))
            backupUriString.toUri()
        }
        val viewModel: RestoreBackupViewModel = viewModel()
        DisposableEffect(key1 = null) {
            viewModel.init(backupUri)
            onDispose { }
        }
        RestoreBackupScreen()
    }
}

@Composable
fun RestoreBackupScreen(
    modifier: Modifier = Modifier,
    viewModel: RestoreBackupViewModel = viewModel(),
) {
    val isPortrait = LocalConfiguration.current.orientation == Configuration.ORIENTATION_PORTRAIT
    val scrollState = rememberScrollState()
    val uiState = viewModel.uiState.collectAsStateWithLifecycle().value

    PreferenceLayout(
        label = stringResource(id = R.string.restore_backup),
        modifier = modifier,
        backArrowVisible = !LocalIsExpandedScreen.current,
        scrollState = if (isPortrait) null else scrollState,
    ) {
        when (uiState) {
            is RestoreBackupUiState.Success -> RestoreBackupOptions(isPortrait, uiState.backup)

            is RestoreBackupUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize()) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
            }

            is RestoreBackupUiState.Error -> {
                val context = LocalContext.current
                val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
                DisposableEffect(null) {
                    Toast.makeText(context, R.string.invalid_backup_file, Toast.LENGTH_SHORT).show()
                    backDispatcher?.onBackPressed()
                    onDispose { }
                }
            }
        }
    }
}

@Composable
fun ColumnScope.RestoreBackupOptions(
    isPortrait: Boolean,
    backup: LawnchairBackup,
    modifier: Modifier = Modifier,
    viewModel: RestoreBackupViewModel = viewModel(),
) {
    val backupContents = backup.info.contents
    val contents by viewModel.backupContents.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var restoringBackup by remember { mutableStateOf(false) }
    if (restoringBackup) {
        BackHandler {}
    }

    fun restoreBackup() {
        if (restoringBackup) return
        scope.launch {
            restoringBackup = true
            try {
                backup.restore(contents)
                Toast.makeText(context, R.string.backup_restore_success, Toast.LENGTH_SHORT).show()
                restartLauncher(context)
            } catch (t: Throwable) {
                Log.e("RestoreBackupScreen", "failed to restore backup", t)
                Toast.makeText(context, R.string.backup_restore_error, Toast.LENGTH_SHORT).show()
            }
            restoringBackup = false
        }
    }

    if (isPortrait) {
        DummyLauncherBox(
            modifier = Modifier
                .padding(top = 8.dp)
                .weight(1f)
                .align(Alignment.CenterHorizontally)
                .clip(MaterialTheme.shapes.large),
            darkText = backup.info.previewDarkText,
        ) {
            val wallpaper = backup.wallpaper
            if (contents.hasFlag(LawnchairBackup.INCLUDE_WALLPAPER) && wallpaper != null) {
                Image(
                    bitmap = wallpaper.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.FillHeight,
                )
            }
            val screenshot = backup.screenshot
            if (contents.hasFlag(LawnchairBackup.INCLUDE_LAYOUT_AND_SETTINGS) && screenshot != null) {
                Image(
                    bitmap = screenshot.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.FillHeight,
                )
            }
        }
    }

    PreferenceGroup(
        modifier = modifier,
        heading = stringResource(id = R.string.what_to_restore),
    ) {
        FlagSwitchPreference(
            flags = contents,
            setFlags = viewModel::setBackupContents,
            mask = LawnchairBackup.INCLUDE_LAYOUT_AND_SETTINGS,
            label = stringResource(id = R.string.backup_content_layout_and_settings),
            enabled = backupContents.hasFlag(LawnchairBackup.INCLUDE_LAYOUT_AND_SETTINGS),
        )
        FlagSwitchPreference(
            flags = contents,
            setFlags = viewModel::setBackupContents,
            mask = LawnchairBackup.INCLUDE_WALLPAPER,
            label = stringResource(id = R.string.backup_content_wallpaper),
            enabled = backupContents.hasFlag(LawnchairBackup.INCLUDE_WALLPAPER),
        )
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .padding(horizontal = 16.dp),
    ) {
        Button(
            onClick = { restoreBackup() },
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxWidth(),
            enabled = contents != 0 && !restoringBackup,
        ) {
            Text(text = stringResource(id = R.string.action_restore))
        }
    }
}

@Composable
fun restoreBackupOpener(): () -> Unit {
    val navController = LocalNavController.current

    val request = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val uri = it.data?.data ?: return@rememberLauncherForActivityResult

        val base64Uri = Base64.getEncoder().encodeToString(uri.toString().toByteArray())
        navController.navigate(RestoreBackup(base64Uri))
    }

    return {
        Intent(Intent.ACTION_OPEN_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType(LawnchairBackup.MIME_TYPE)
            .putExtra(Intent.EXTRA_MIME_TYPES, LawnchairBackup.EXTRA_MIME_TYPES)
            .let { request.launch(it) }
    }
}
