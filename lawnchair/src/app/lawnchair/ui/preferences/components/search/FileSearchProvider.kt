package app.lawnchair.ui.preferences.components.search

import android.Manifest
import android.app.Application
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.lawnchair.preferences.PreferenceAdapter
import app.lawnchair.preferences.getAdapter
import app.lawnchair.preferences.preferenceManager
import app.lawnchair.preferences2.preferenceManager2
import app.lawnchair.ui.preferences.components.PermissionDialog
import app.lawnchair.ui.preferences.components.controls.MainSwitchPreference
import app.lawnchair.ui.preferences.components.controls.SliderPreference
import app.lawnchair.ui.preferences.components.controls.SwitchPreference
import app.lawnchair.ui.preferences.components.layout.ExpandAndShrink
import app.lawnchair.ui.preferences.components.layout.PreferenceGroup
import app.lawnchair.ui.preferences.components.layout.PreferenceTemplate
import app.lawnchair.ui.theme.dividerColor
import app.lawnchair.ui.util.isPlayStoreFlavor
import app.lawnchair.util.FileAccessManager
import app.lawnchair.util.FileAccessState
import app.lawnchair.util.openAppPermissionSettings
import app.lawnchair.util.requestManageAllFilesAccessPermission
import com.android.launcher3.R
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionState
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale

class FileSearchProviderViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val fileAccessManager: FileAccessManager = FileAccessManager.getInstance(application)

    val hasAnyPermissions = fileAccessManager.hasAnyPermission
    val visualMediaAccessState = fileAccessManager.visualMediaAccessState
    val audioAccessState = fileAccessManager.audioAccessState
    val allFilesAccessState = fileAccessManager.allFilesAccessState

    fun refreshAccessStates() = fileAccessManager.refresh()
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun FileSearchProvider(
    modifier: Modifier = Modifier,
    viewModel: FileSearchProviderViewModel = viewModel(),
) {
    val prefs = preferenceManager()
    val prefs2 = preferenceManager2()
    val appName = stringResource(id = R.string.derived_app_name)

    val mainAdapter = prefs.searchResultFilesToggle.getAdapter()
    val hasAnyPermissions by viewModel.hasAnyPermissions.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.refreshAccessStates()
    }

    LifecycleResumeEffect(Unit) {
        viewModel.refreshAccessStates()
        onPauseOrDispose {}
    }

    MainSwitchPreference(
        checked = mainAdapter.state.value,
        onCheckedChange = mainAdapter::onChange,
        label = stringResource(R.string.search_pref_result_files_title),
        enabled = hasAnyPermissions,
        modifier = modifier,
    )
    PreferenceGroup(
        heading = stringResource(R.string.search_pref_files_search_on),
    ) {
        val allFilesAccessState by viewModel.allFilesAccessState.collectAsStateWithLifecycle()
        val allFilesAccessAdapter = prefs.searchResultAllFiles.getAdapter()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            ManageExternalStorageSetting(
                accessState = allFilesAccessState,
                adapter = allFilesAccessAdapter,
                onPermissionRequest = viewModel::refreshAccessStates,
            )
        } else {
            GenericAccessSetting(
                adapter = allFilesAccessAdapter,
                requiredPermission = Manifest.permission.READ_EXTERNAL_STORAGE,
                switchEnabled = { allFilesAccessState != FileAccessState.Denied },
                label = stringResource(R.string.search_pref_result_all_files_title),
                permissionTitle = stringResource(R.string.permissions_external_storage),
                permissionDescription = stringResource(R.string.permissions_external_storage_description, appName),
                onPermissionResult = { viewModel.refreshAccessStates() },
            )
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val visualMediaAccessState by viewModel.visualMediaAccessState.collectAsStateWithLifecycle()
            val audioAccessState by viewModel.audioAccessState.collectAsStateWithLifecycle()

            VisualMediaSetting(
                accessState = visualMediaAccessState,
                adapter = prefs.searchResultVisualMedia.getAdapter(),
                onPermissionRequest = viewModel::refreshAccessStates,
                alwaysEnabled = allFilesAccessAdapter.state.value && allFilesAccessState == FileAccessState.Full,
            )

            GenericAccessSetting(
                adapter = prefs.searchResultAudio.getAdapter(),
                requiredPermission = android.Manifest.permission.READ_MEDIA_AUDIO,
                switchEnabled = { audioAccessState != FileAccessState.Denied },
                label = stringResource(R.string.search_pref_result_audio_media_title),
                permissionTitle = stringResource(R.string.permissions_music_audio),
                permissionDescription = stringResource(R.string.permissions_music_audio_description, appName),
                onPermissionResult = { viewModel.refreshAccessStates() },
                alwaysEnabled = allFilesAccessAdapter.state.value && allFilesAccessState == FileAccessState.Full,
            )
        }
    }

    ExpandAndShrink(hasAnyPermissions) {
        PreferenceGroup {
            SliderPreference(
                label = stringResource(id = R.string.max_file_result_count_title),
                adapter = prefs2.maxFileResultCount.getAdapter(),
                step = 1,
                valueRange = 3..10,
            )
        }
    }
}

@RequiresApi(Build.VERSION_CODES.R)
@Composable
private fun ManageExternalStorageSetting(
    accessState: FileAccessState,
    adapter: PreferenceAdapter<Boolean>,
    onPermissionRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showPermissionDialog by remember { mutableStateOf(false) }

    if (accessState == FileAccessState.Full) {
        SwitchPreference(
            adapter = adapter,
            label = stringResource(R.string.search_pref_result_all_files_title),
            modifier = modifier,
        )
    } else {
        TwoTargetSwitchPreference(
            label = stringResource(R.string.search_pref_result_all_files_title),
            description = stringResource(R.string.permissions_needed),
            checked = false,
            onCheckedChange = {
                if (accessState == FileAccessState.Denied) {
                    showPermissionDialog = true
                } else {
                    adapter.onChange(it)
                }
            },
            onClick = {
                if (accessState == FileAccessState.Denied) {
                    showPermissionDialog = true
                }
            },
            switchEnabled = !isPlayStoreFlavor() && (accessState != FileAccessState.Denied),
            modifier = modifier,
        )
    }

    if (showPermissionDialog) {
        FileAccessPermissionDialog(
            onDismiss = { showPermissionDialog = false },
            onPermissionRequest = onPermissionRequest,
        )
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun VisualMediaSetting(
    accessState: FileAccessState,
    adapter: PreferenceAdapter<Boolean>,
    onPermissionRequest: () -> Unit,
    alwaysEnabled: Boolean = false,
) {
    val context = LocalContext.current
    val permissionState = rememberMultiplePermissionsState(
        listOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
        ),
    ) {
        onPermissionRequest()
    }

    var showPermissionDialog by remember { mutableStateOf(false) }
    var showPartialAccessDialog by remember { mutableStateOf(false) }

    if (alwaysEnabled || accessState == FileAccessState.Full) {
        SwitchPreference(
            checked = alwaysEnabled || adapter.state.value,
            onCheckedChange = adapter::onChange,
            label = stringResource(R.string.search_pref_result_visual_media_title),
            enabled = !alwaysEnabled,
        )
    } else {
        TwoTargetSwitchPreference(
            label = stringResource(R.string.search_pref_result_visual_media_title),
            description = stringResource(R.string.permissions_needed),
            checked = (adapter.state.value && accessState != FileAccessState.Denied),
            onCheckedChange = {
                if (accessState == FileAccessState.Denied) {
                    showPermissionDialog = true
                } else {
                    adapter.onChange(it)
                }
            },
            onClick = {
                if (accessState == FileAccessState.Denied) {
                    showPermissionDialog = true
                } else if (accessState == FileAccessState.Partial) {
                    showPartialAccessDialog = true
                }
            },
            switchEnabled = accessState != FileAccessState.Denied,
        )
    }

    if (showPermissionDialog) {
        PermissionDialog(
            title = stringResource(R.string.permissions_photos_videos),
            text = stringResource(R.string.permissions_photos_videos_description, stringResource(id = R.string.derived_app_name)),
            isPermanentlyDenied = permissionState.allPermissionsGranted,
            onConfirm = { permissionState.launchMultiplePermissionRequest() },
            onDismiss = { showPermissionDialog = false },
            onGoToSettings = { context.openAppPermissionSettings() },
        )
    }

    if (showPartialAccessDialog) {
        AlertDialog(
            onDismissRequest = { showPartialAccessDialog = false },
            title = { Text(stringResource(R.string.permissions_photos_videos_full)) },
            text = { Text(stringResource(R.string.permissions_photos_videos_full_description, stringResource(id = R.string.derived_app_name))) },
            confirmButton = {
                Column {
                    Button(
                        onClick = {
                            context.openAppPermissionSettings()
                            showPartialAccessDialog = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.permissions_photos_videos_grant_full)) }

                    TextButton(
                        onClick = {
                            permissionState.launchMultiplePermissionRequest()
                            showPartialAccessDialog = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.permissions_photos_videos_manage_selected)) }

                    TextButton(
                        onClick = { showPartialAccessDialog = false },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(android.R.string.cancel)) }
                }
            },
        )
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun GenericAccessSetting(
    adapter: PreferenceAdapter<Boolean>,
    requiredPermission: String,
    switchEnabled: (PermissionState) -> Boolean,
    label: String,
    permissionTitle: String,
    permissionDescription: String,
    onPermissionResult: () -> Unit,
    alwaysEnabled: Boolean = false,
) {
    var showPermissionDialog by remember { mutableStateOf(false) }
    val permission = rememberPermissionState(requiredPermission) {
        onPermissionResult()
    }

    val context = LocalContext.current

    if (!alwaysEnabled && !switchEnabled(permission)) {
        TwoTargetSwitchPreference(
            label = label,
            description = stringResource(R.string.permissions_needed),
            switchEnabled = false,
            checked = false,
            onCheckedChange = {},
            onClick = {
                showPermissionDialog = true
            },
        )
    } else {
        SwitchPreference(
            label = label,
            checked = alwaysEnabled || adapter.state.value,
            onCheckedChange = adapter::onChange,
            enabled = !alwaysEnabled,
        )
    }

    if (showPermissionDialog) {
        PermissionDialog(
            title = permissionTitle,
            text = permissionDescription,
            isPermanentlyDenied = permission.status.shouldShowRationale,
            onConfirm = { permission.launchPermissionRequest() },
            onDismiss = { showPermissionDialog = false },
            onGoToSettings = { context.openAppPermissionSettings() },
        )
    }
}

@Composable
internal fun TwoTargetSwitchPreference(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    enabled: Boolean = true,
    switchEnabled: Boolean = enabled,
    onClick: (() -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }

    PreferenceTemplate(
        modifier = modifier.clickable(
            enabled = enabled,
            indication = ripple(),
            interactionSource = interactionSource,
        ) {
            if (onClick != null) {
                onClick()
            } else {
                onCheckedChange(!checked)
            }
        },
        contentModifier = Modifier
            .fillMaxHeight()
            .padding(vertical = 16.dp)
            .padding(start = 16.dp),
        title = { Text(text = label) },
        description = { description?.let { Text(text = it) } },
        endWidget = {
            if (onClick != null) {
                Spacer(
                    modifier = Modifier
                        .height(32.dp)
                        .width(1.dp)
                        .fillMaxHeight()
                        .background(dividerColor()),
                )
            }
            Switch(
                modifier = Modifier
                    .padding(all = 16.dp)
                    .height(24.dp),
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = switchEnabled,
                interactionSource = interactionSource,
            )
        },
        enabled = enabled,
        applyPaddings = false,
    )
}

/**
 * A dialog that requests file access permission.
 *
 * On Android R and above, this requests manage all files access. Otherwise, it requests read
 * external storage permission. For Play Store builds on Android R and above, it shows a dialog
 * explaining that the permission is not available.
 *
 * @param onDismiss Called when the dialog is dismissed.
 * @param modifier The modifier to be applied to the dialog.
 * @param rationale The rationale to show to the user.
 * @param onPermissionRequest Called when the permission is requested.
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun FileAccessPermissionDialog(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    rationale: String = stringResource(R.string.permissions_manage_storage_description, stringResource(id = R.string.derived_app_name)),
    onPermissionRequest: () -> Unit = {},
) {
    val context = LocalContext.current

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        if (!isPlayStoreFlavor()) {
            PermissionDialog(
                title = stringResource(R.string.permissions_manage_storage),
                modifier = modifier,
                text = rationale,
                isPermanentlyDenied = true,
                onConfirm = { },
                onDismiss = onDismiss,
                onGoToSettings = {
                    onPermissionRequest()
                    context.requestManageAllFilesAccessPermission()
                },
            )
        } else {
            AlertDialog(
                onDismissRequest = onDismiss,
                modifier = modifier,
                title = {
                    Text(stringResource(R.string.manage_storage_access_denied_title))
                },
                text = {
                    Text(stringResource(R.string.manage_storage_access_denied_description, stringResource(id = R.string.derived_app_name)))
                },
                confirmButton = {
                    FilledTonalButton(onClick = onDismiss) { Text(stringResource(R.string.dismiss)) }
                },
            )
        }
    } else {
        val permission = rememberPermissionState(Manifest.permission.READ_EXTERNAL_STORAGE)

        PermissionDialog(
            title = stringResource(R.string.permissions_external_storage),
            modifier = modifier,
            text = rationale,
            isPermanentlyDenied = permission.status.shouldShowRationale,
            onConfirm = {
                onPermissionRequest()
                permission.launchPermissionRequest()
            },
            onDismiss = onDismiss,
            onGoToSettings = { context.openAppPermissionSettings() },
        )
    }
}
