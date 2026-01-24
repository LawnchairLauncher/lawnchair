package app.lawnchair.backup.ui

import android.app.Activity
import android.app.WallpaperManager
import android.content.Intent
import android.content.res.Configuration
import android.provider.DocumentsContract
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.lawnchair.backup.LawnchairBackup
import app.lawnchair.ui.preferences.LocalIsExpandedScreen
import app.lawnchair.ui.preferences.LocalNavController
import app.lawnchair.ui.preferences.components.DummyLauncherBox
import app.lawnchair.ui.preferences.components.WallpaperAccessPermissionDialog
import app.lawnchair.ui.preferences.components.WallpaperPreview
import app.lawnchair.ui.preferences.components.WithWallpaper
import app.lawnchair.ui.preferences.components.controls.ClickablePreference
import app.lawnchair.ui.preferences.components.controls.FlagSwitchPreference
import app.lawnchair.ui.preferences.components.layout.PreferenceGroup
import app.lawnchair.ui.preferences.components.layout.PreferenceLayout
import app.lawnchair.ui.preferences.navigation.AppDrawerFolder
import app.lawnchair.util.BackHandler
import app.lawnchair.util.FileAccessState
import app.lawnchair.util.hasFlag
import app.lawnchair.util.removeFlag
import com.android.launcher3.R
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CreateBackupScreen(
    viewModel: CreateBackupViewModel,
    modifier: Modifier = Modifier,
) {
    val contents by viewModel.backupContents.collectAsStateWithLifecycle()
    val screenshot by viewModel.screenshot.collectAsStateWithLifecycle()
    val screenshotDone by viewModel.screenshotDone.collectAsStateWithLifecycle()

    val isPortrait = LocalConfiguration.current.orientation == Configuration.ORIENTATION_PORTRAIT
    val scrollState = rememberScrollState()

    val context = LocalContext.current
    val hasLiveWallpaper = remember { WallpaperManager.getInstance(context).wallpaperInfo != null }
    val allFilesAccessState by viewModel.allFilesAccessState.collectAsStateWithLifecycle()
    val wallpaperAccessState by viewModel.wallpaperAccessState.collectAsStateWithLifecycle()
    val hasWallpaperPermission = wallpaperAccessState == FileAccessState.Full

    var showPermissionDialog by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    var creatingBackup by remember { mutableStateOf(false) }
    if (creatingBackup) {
        BackHandler {}
    }

    val navController = LocalNavController.current
    val request = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val uri = it.data?.data ?: return@rememberLauncherForActivityResult
        if (creatingBackup) return@rememberLauncherForActivityResult
        scope.launch {
            creatingBackup = true
            try {
                LawnchairBackup.create(context, contents, screenshot, uri)
                navController.popBackStack()
                Toast.makeText(context, R.string.backup_create_success, Toast.LENGTH_SHORT).show()
            } catch (t: Throwable) {
                Log.e("CreateBackupScreen", "failed to create backup", t)
                Toast.makeText(context, R.string.backup_create_error, Toast.LENGTH_SHORT).show()
                runCatching { DocumentsContract.deleteDocument(context.contentResolver, uri) }
            }
            creatingBackup = false
        }
    }

    fun launchPicker() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        intent.type = "*/*"
        intent.putExtra(Intent.EXTRA_TITLE, LawnchairBackup.generateBackupFileName())
        request.launch(intent)
    }

    PreferenceLayout(
        label = stringResource(id = R.string.create_backup),
        modifier = modifier,
        isExpandedScreen = true,
        backArrowVisible = !LocalIsExpandedScreen.current,
        scrollState = if (isPortrait) null else scrollState,
    ) {
        DisposableEffect(contents, hasLiveWallpaper, hasWallpaperPermission) {
            val canBackupWallpaper = hasLiveWallpaper || !hasWallpaperPermission
            if (contents.hasFlag(LawnchairBackup.INCLUDE_WALLPAPER) && canBackupWallpaper) {
                viewModel.setBackupContents(contents.removeFlag(LawnchairBackup.INCLUDE_WALLPAPER))
            }
            onDispose { }
        }

        if (isPortrait) {
            WithWallpaper(
                displayWallpaperButton = false,
            ) { wallpaper ->
                DummyLauncherBox(
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .weight(1f)
                        .align(Alignment.CenterHorizontally)
                        .clip(MaterialTheme.shapes.large),
                ) {
                    if (contents.hasFlag(LawnchairBackup.INCLUDE_WALLPAPER)) {
                        WallpaperPreview(
                            wallpaper = wallpaper,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    if (contents.hasFlag(LawnchairBackup.INCLUDE_LAYOUT_AND_SETTINGS)) {
                        Image(
                            bitmap = screenshot.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.FillHeight,
                        )
                    }
                }
            }
        }

        PreferenceGroup(heading = stringResource(id = R.string.what_to_backup)) {
            Item {
                FlagSwitchPreference(
                    flags = contents,
                    setFlags = viewModel::setBackupContents,
                    mask = LawnchairBackup.INCLUDE_LAYOUT_AND_SETTINGS,
                    label = stringResource(id = R.string.backup_content_layout_and_settings),
                )
            }
            Item {
                FlagSwitchPreference(
                    flags = contents,
                    setFlags = {
                        if (it.hasFlag(LawnchairBackup.INCLUDE_WALLPAPER) && !hasWallpaperPermission) {
                            showPermissionDialog = true
                        } else {
                            viewModel.setBackupContents(it)
                        }
                    },
                    mask = LawnchairBackup.INCLUDE_WALLPAPER,
                    label = stringResource(id = R.string.backup_content_wallpaper),
                    enabled = !hasLiveWallpaper,
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
                onClick = { launchPicker() },
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxWidth(),
                enabled = contents != 0 && screenshotDone && !creatingBackup,
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(text = stringResource(id = R.string.action_create))
            }
        }

        if (showPermissionDialog) {
            WallpaperAccessPermissionDialog(
                managedFilesChecked = allFilesAccessState == FileAccessState.Full,
                onDismiss = {
                    showPermissionDialog = false
                },
                onPermissionRequest = {
                    viewModel.refreshFilePermissionStates()
                },
            )
        }
    }
}
