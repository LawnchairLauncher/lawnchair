package app.lawnchair.ui.preferences.destinations

import android.util.Log
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.lawnchair.data.folder.FolderEntry
import app.lawnchair.data.folder.model.FolderViewModel
import app.lawnchair.preferences.getAdapter
import app.lawnchair.preferences.preferenceManager
import app.lawnchair.ui.ModalBottomSheetContent
import app.lawnchair.ui.preferences.LocalNavController
import app.lawnchair.ui.preferences.components.controls.ClickablePreference
import app.lawnchair.ui.preferences.components.controls.SwitchPreference
import app.lawnchair.ui.preferences.components.layout.LoadingScreen
import app.lawnchair.ui.preferences.components.layout.PreferenceGroup
import app.lawnchair.ui.preferences.components.layout.PreferenceLayout
import app.lawnchair.ui.preferences.components.layout.PreferenceTemplate
import app.lawnchair.ui.preferences.components.reorderable.ReorderableDragHandle
import app.lawnchair.ui.preferences.components.reorderable.ReorderablePreferenceGroup
import app.lawnchair.ui.preferences.navigation.AppDrawerAppListToFolder
import app.lawnchair.ui.preferences.navigation.AppDrawerFolder
import app.lawnchair.ui.util.bottomSheetHandler
import com.android.launcher3.R
import com.android.launcher3.util.MSDLPlayerWrapper
import com.google.android.msdl.data.model.MSDLToken

@Composable
fun AppDrawerFolderPreferenceItem(
    modifier: Modifier = Modifier,
) {
    val mMSDLPlayerWrapper = MSDLPlayerWrapper.INSTANCE.get(LocalContext.current)
    val navController = LocalNavController.current

    PreferenceGroup(
        modifier = modifier,
    ) {
        ClickablePreference(
            label = stringResource(R.string.app_drawer_folder),
            modifier = Modifier,
            hapticToken = null,
            onClick = {
                mMSDLPlayerWrapper.playToken(MSDLToken.TAP_HIGH_EMPHASIS)
                navController.navigate(route = AppDrawerFolder)
            },
        )
    }
}

@Composable
fun AppDrawerFoldersPreference(
    modifier: Modifier = Modifier,
    viewModel: FolderViewModel = viewModel(),
) {
    val navController = LocalNavController.current
    val folders by viewModel.folders.collectAsStateWithLifecycle()

    AppDrawerFoldersPreference(
        modifier = modifier,
        folders = folders,
        onCreateFolder = { label ->
            viewModel.createFolder(label)
        },
        onEditFolderItems = {
            navController.navigate(AppDrawerAppListToFolder(it))
        },
        onRenameFolder = { folderId, newTitle ->
            viewModel.renameFolder(folderId, newTitle)
        },
        onDeleteFolder = {
            viewModel.deleteFolder(it.id)
        },
        onOrderChange = {
            viewModel.updateFolderOrder(it)
        },
    )
}

@Composable
fun AppDrawerFoldersPreference(
    folders: List<FolderEntry>?,
    onCreateFolder: (String) -> Unit,
    onEditFolderItems: (Int) -> Unit,
    onRenameFolder: (Int, String) -> Unit,
    onDeleteFolder: (FolderEntry) -> Unit,
    onOrderChange: (List<Int>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val mMSDLPlayerWrapper = MSDLPlayerWrapper.INSTANCE.get(LocalContext.current)
    val bottomSheetHandler = bottomSheetHandler
    val prefs = preferenceManager()

    val sortedDisplayList = folders ?: emptyList()

    LoadingScreen(
        isLoading = folders == null,
        modifier = modifier.fillMaxWidth(),
    ) {
        PreferenceLayout(
            label = stringResource(id = R.string.app_drawer_folder),
            backArrowVisible = true,
        ) {
            PreferenceGroup(
                heading = stringResource(R.string.settings),
            ) {
                SwitchPreference(
                    adapter = prefs.folderApps.getAdapter(),
                    label = stringResource(id = R.string.apps_in_folder_label),
                    description = stringResource(id = R.string.apps_in_folder_description),
                )
            }
            PreferenceGroup(heading = stringResource(R.string.folders_label)) {
                PreferenceTemplate(
                    title = {
                        Text(
                            text = stringResource(R.string.add_folder),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    },
                    startWidget = {
                        Icon(Icons.Rounded.Add, contentDescription = null)
                    },
                    onClick = {
                        mMSDLPlayerWrapper.playToken(MSDLToken.TAP_MEDIUM_EMPHASIS)
                        bottomSheetHandler.show {
                            FolderEditSheet(
                                folderId = 0,
                                initialTitle = stringResource(R.string.my_folder_label),
                                itemCount = 0,
                                onRename = { _, title -> onCreateFolder(title) },
                                onNavigate = {},
                                onDismiss = {
                                    bottomSheetHandler.hide()
                                },
                                hideAppPicker = true,
                            )
                        }
                    },
                )
            }
            ReorderablePreferenceGroup(
                label = null,
                items = sortedDisplayList,
                defaultList = sortedDisplayList,
                onOrderChange = { updatedFolders ->
                    onOrderChange(updatedFolders.map { it.id })
                },
            ) { folderEntry, _, _ ->
                val interactionSource = remember { MutableInteractionSource() }
                FolderItem(
                    folderEntry = folderEntry,
                    onItemClick = {
                        mMSDLPlayerWrapper.playToken(MSDLToken.TAP_MEDIUM_EMPHASIS)
                        bottomSheetHandler.show {
                            FolderEditSheet(
                                folderId = folderEntry.id,
                                initialTitle = folderEntry.title,
                                itemCount = folderEntry.itemComponentKeys.size,
                                onRename = onRenameFolder,
                                onNavigate = {
                                    onEditFolderItems(it)
                                    bottomSheetHandler.hide()
                                },
                                onDismiss = {
                                    bottomSheetHandler.hide()
                                },
                            )
                        }
                    },
                    onItemDelete = { folderToDelete ->
                        mMSDLPlayerWrapper.playToken(MSDLToken.SUCCESS)
                        onDeleteFolder(folderToDelete)
                    },
                    dragIndicator = {
                        ReorderableDragHandle(
                            interactionSource = interactionSource,
                            scope = this,
                        )
                    },
                    interactionSource = interactionSource,
                )
            }
        }
    }
}

@Composable
fun FolderEditSheet(
    folderId: Int,
    initialTitle: String,
    itemCount: Int,
    onRename: (Int, String) -> Unit,
    onNavigate: (Int) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    hideAppPicker: Boolean = false,
) {
    val resources = LocalResources.current
    var textFieldValue by remember { mutableStateOf(TextFieldValue(initialTitle)) }

    ModalBottomSheetContent(
        buttons = {
            OutlinedButton(
                onClick = onDismiss,
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(stringResource(android.R.string.cancel))
            }
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = {
                    onRename(folderId, textFieldValue.text)
                    onDismiss()
                },
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(stringResource(android.R.string.ok))
            }
        },
        modifier = modifier,
    ) {
        Column {
            OutlinedTextField(
                value = textFieldValue,
                onValueChange = {
                    textFieldValue = it
                },
                label = { Text(text = stringResource(id = R.string.label)) },
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth(),
                singleLine = true,
                isError = textFieldValue.text.isEmpty(),
            )
            if (!hideAppPicker) {
                ClickablePreference(
                    label = "Manage apps",
                    subtitle = resources.getQuantityString(
                        R.plurals.apps_count,
                        itemCount,
                        itemCount,
                    ),
                    modifier = Modifier
                        .padding(horizontal = 8.dp),
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                ) {
                    onNavigate(folderId)
                }
            }
        }
    }
}

@Composable
fun FolderItem(
    folderEntry: FolderEntry,
    onItemClick: (FolderEntry) -> Unit,
    onItemDelete: (FolderEntry) -> Unit,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    dragIndicator: @Composable () -> Unit,
) {
    val resources = LocalResources.current
    PreferenceTemplate(
        title = {
            Text(
                text = folderEntry.title,
            )
        },
        modifier = modifier,
        description = {
            Text(
                text = resources.getQuantityString(
                    R.plurals.apps_count,
                    folderEntry.itemComponentKeys.size,
                    folderEntry.itemComponentKeys.size,
                ),
            )
        },
        startWidget = {
            dragIndicator()
        },
        endWidget = {
            Row {
                IconButton(
                    onClick = {
                        onItemDelete(folderEntry)
                    },
                    shapes = IconButtonDefaults.shapes(),
                ) {
                    Icon(
                        Icons.Rounded.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        onClick = {
            onItemClick(folderEntry)
        },
        interactionSource = interactionSource,
    )
}
