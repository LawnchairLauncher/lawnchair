package app.lawnchair.backup.ui

import android.app.Activity
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.navArgument
import app.lawnchair.backup.LawnchairBackup
import app.lawnchair.ui.preferences.LocalNavController
import app.lawnchair.ui.preferences.Routes
import app.lawnchair.ui.preferences.components.*
import app.lawnchair.ui.preferences.preferenceGraph
import app.lawnchair.util.BackHandler
import app.lawnchair.util.hasFlag
import app.lawnchair.util.restartLauncher
import com.android.launcher3.R
import com.google.accompanist.navigation.animation.composable
import kotlinx.coroutines.launch
import java.util.Base64

@OptIn(ExperimentalAnimationApi::class)
fun NavGraphBuilder.restoreBackupGraph(route: String) {
    preferenceGraph(route, {}) { subRoute ->
        composable(
            route = subRoute("{base64Uri}"),
            arguments = listOf(
                navArgument("base64Uri") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val args = backStackEntry.arguments!!
            val backupUri = remember {
                val base64Uri = args.getString("base64Uri")!!
                val backupUriString = String(Base64.getDecoder().decode(base64Uri))
                Uri.parse(backupUriString)
            }
            val viewModel: RestoreBackupViewModel = viewModel()
            DisposableEffect(key1 = null) {
                viewModel.init(backupUri)
                onDispose {  }
            }
            RestoreBackupScreen(viewModel)
        }
    }
}

@Composable
fun RestoreBackupScreen(viewModel: RestoreBackupViewModel) {
    val isPortrait = LocalConfiguration.current.orientation == Configuration.ORIENTATION_PORTRAIT
    val scrollState = rememberScrollState()
    val uiState = viewModel.uiState.collectAsState().value

    PreferenceLayout(
        label = stringResource(id = R.string.restore_backup),
        scrollState = if (isPortrait) null else scrollState
    ) {
        when (uiState) {
            is RestoreBackupUiState.Success -> RestoreBackupOptions(isPortrait, uiState.backup, viewModel = viewModel)
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
                    onDispose {  }
                }
            }
        }
    }
}

@Composable
fun ColumnScope.RestoreBackupOptions(
    isPortrait: Boolean,
    backup: LawnchairBackup,
    viewModel: RestoreBackupViewModel
) {
    val backupContents = backup.info.contents
    val contents by viewModel.backupContents.collectAsState()

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
                    contentScale = ContentScale.FillHeight
                )
            }
            val screenshot = backup.screenshot
            if (contents.hasFlag(LawnchairBackup.INCLUDE_LAYOUT_AND_SETTINGS) && screenshot != null) {
                Image(
                    bitmap = screenshot.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.FillHeight
                )
            }
        }
    }

    PreferenceGroup(heading = stringResource(id = R.string.what_to_restore)) {
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
            .padding(horizontal = 16.dp)
    ) {
        Button(
            onClick = { restoreBackup() },
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxWidth(),
            enabled = contents != 0 && !restoringBackup
        ) {
            Text(text = stringResource(id = R.string.restore_backup_action))
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
        navController.navigate("/${Routes.RESTORE_BACKUP}/${base64Uri}/")
    }

    return {
        Intent(Intent.ACTION_OPEN_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType(LawnchairBackup.MIME_TYPE)
            .putExtra(Intent.EXTRA_MIME_TYPES, LawnchairBackup.EXTRA_MIME_TYPES)
            .let { request.launch(it) }
    }
}
