package app.lawnchair.ui.preferences.components

import android.Manifest
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.lawnchair.ui.util.isPlayStoreFlavor
import app.lawnchair.util.openAppPermissionSettings
import app.lawnchair.util.requestManageAllFilesAccessPermission
import com.android.launcher3.R
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale

/**
 * A dialog that requests a permission.
 *
 * @param title The title of the dialog.
 * @param text The text of the dialog.
 * @param isPermanentlyDenied Whether the permission is permanently denied.
 * @param onConfirm Called when the user confirms the dialog.
 * @param onDismiss Called when the dialog is dismissed.
 * @param onGoToSettings Called when the user clicks the "Go to settings" button.
 * @param modifier The modifier to be applied to the dialog.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PermissionDialog(
    title: String,
    text: String,
    isPermanentlyDenied: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    onGoToSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            Button(
                onClick = {
                    if (isPermanentlyDenied) onGoToSettings() else onConfirm()
                    onDismiss()
                },
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(
                    stringResource(
                        if (isPermanentlyDenied) R.string.open_permission_settings else R.string.grant_requested_permissions,
                    ),
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                shapes = ButtonDefaults.shapes(),
            ) { Text(stringResource(android.R.string.cancel)) }
        },
    )
}

/**
 * A dialog that requests wallpaper access permission.
 *
 * @param onDismiss Called when the dialog is dismissed.
 * @param modifier The modifier to be applied to the dialog.
 * @param onPermissionRequest Called when a permission request is initiated.
 */
@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun WallpaperAccessPermissionDialog(
    managedFilesChecked: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onPermissionRequest: () -> Unit = {},
) {
    val context = LocalContext.current

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        if (isPlayStoreFlavor()) {
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
                    FilledTonalButton(
                        onClick = onDismiss,
                        shapes = ButtonDefaults.shapes(),
                    ) { Text(stringResource(R.string.dismiss)) }
                },
            )
        } else {
            val latestOnDismiss by rememberUpdatedState(onDismiss)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val mediaPermission = rememberMultiplePermissionsState(
                    listOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO),
                )

                AlertDialog(
                    onDismissRequest = onDismiss,
                    modifier = modifier,
                    title = {
                        Text(stringResource(R.string.permission_desc_wallpaper_multiple))
                    },
                    text = {
                        Column {
                            Text(stringResource(R.string.permission_desc_wallpaper_multiple_desc, stringResource(id = R.string.derived_app_name)))

                            Spacer(modifier = Modifier.height(8.dp))
                            PermissionRow(
                                isChecked = managedFilesChecked,
                                onClick = {
                                    onPermissionRequest()
                                    context.requestManageAllFilesAccessPermission()
                                },
                                permissionName = stringResource(R.string.permission_label_manage_all_files),
                            )
                            PermissionRow(
                                isChecked = mediaPermission.allPermissionsGranted,
                                onClick = {
                                    onPermissionRequest()
                                    if (mediaPermission.shouldShowRationale) {
                                        context.openAppPermissionSettings()
                                    } else {
                                        mediaPermission.launchMultiplePermissionRequest()
                                    }
                                },
                                permissionName = stringResource(id = R.string.permission_label_read_photos_videos),
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(
                            onClick = onDismiss,
                            shapes = ButtonDefaults.shapes(),
                        ) { Text(stringResource(android.R.string.cancel)) }
                    },
                )

                LaunchedEffect(managedFilesChecked, mediaPermission.allPermissionsGranted) {
                    if (managedFilesChecked && mediaPermission.allPermissionsGranted) {
                        latestOnDismiss()
                    }
                }
            } else {
                PermissionDialog(
                    title = stringResource(R.string.permissions_manage_storage),
                    modifier = modifier,
                    text = stringResource(
                        R.string.permission_desc_wallpaper_base,
                        stringResource(id = R.string.derived_app_name),
                        stringResource(R.string.permission_desc_ending_manage_all_files),
                    ),
                    isPermanentlyDenied = true,
                    onConfirm = {},
                    onDismiss = onDismiss,
                    onGoToSettings = {
                        context.requestManageAllFilesAccessPermission()
                    },
                )
                LaunchedEffect(managedFilesChecked) {
                    if (managedFilesChecked) {
                        latestOnDismiss()
                    }
                }
            }
        }
    } else {
        val permission = rememberPermissionState(Manifest.permission.READ_EXTERNAL_STORAGE)

        PermissionDialog(
            title = stringResource(R.string.permissions_external_storage),
            modifier = modifier,
            text = stringResource(
                R.string.permission_desc_wallpaper_base,
                stringResource(id = R.string.derived_app_name),
                stringResource(R.string.permission_desc_ending_read_all_files),
            ),
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

@Composable
fun PermissionRow(
    isChecked: Boolean,
    onClick: () -> Unit,
    permissionName: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(MaterialTheme.shapes.large)
            .clickable(onClick = onClick, enabled = !isChecked)
            .fillMaxWidth()
            .padding(vertical = 16.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val color = if (isChecked) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.errorContainer

        Box(
            modifier = Modifier
                .size(36.dp)
                .background(
                    color = color,
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (isChecked) Icons.Rounded.Check else Icons.Rounded.Close,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = contentColorFor(color),
            )
        }

        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(
                permissionName,
                style = MaterialTheme.typography.bodyMedium,
            )
            if (!isChecked) {
                Text(
                    stringResource(R.string.grant_requested_permissions_tap),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
