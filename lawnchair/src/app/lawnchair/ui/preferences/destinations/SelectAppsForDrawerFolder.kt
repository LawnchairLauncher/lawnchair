package app.lawnchair.ui.preferences.destinations

import android.content.Context
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.lawnchair.data.folder.model.FolderViewModel
import app.lawnchair.ui.OverflowMenu
import app.lawnchair.ui.preferences.LocalIsExpandedScreen
import app.lawnchair.ui.preferences.components.AppItem
import app.lawnchair.ui.preferences.components.AppItemPlaceholder
import app.lawnchair.ui.preferences.components.layout.PreferenceDivider
import app.lawnchair.ui.preferences.components.layout.PreferenceLazyColumn
import app.lawnchair.ui.preferences.components.layout.PreferenceScaffold
import app.lawnchair.ui.preferences.components.layout.preferenceGroupItems
import app.lawnchair.util.App
import app.lawnchair.util.appsState
import com.android.launcher3.R
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.model.data.ItemInfo

@Composable
fun SelectAppsForDrawerFolder(
    folderInfoId: Int?,
    modifier: Modifier = Modifier,
    viewModel: FolderViewModel = viewModel(),
) {
    if (folderInfoId == null) {
        val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
        backDispatcher?.onBackPressed()
        return
    }

    val context = LocalContext.current

    val folders by viewModel.folders.collectAsStateWithLifecycle()
    val folderInfo by viewModel.folderInfo.collectAsStateWithLifecycle()
    var allFolderPackages by remember { mutableStateOf(emptySet<String>()) }
    var selectedAppsInFolder by remember { mutableStateOf(setOf<ItemInfo>()) }
    var filterNonUniqueItems by remember { mutableStateOf(true) }

    LaunchedEffect(folders) {
        allFolderPackages = folders.flatMap { it.getContents() }
            .mapNotNull { it.targetPackage }
            .toSet()
    }

    LaunchedEffect(folderInfo) {
        selectedAppsInFolder = folderInfo?.getContents()?.toMutableSet() ?: emptySet()
    }

    LaunchedEffect(folderInfoId) {
        viewModel.setFolderInfo(folderInfoId, false)
    }

    val apps by appsState()
    val filteredApps = apps.filter { app ->
        if (filterNonUniqueItems) {
            !allFolderPackages.contains(app.key.componentName.packageName) || selectedAppsInFolder.map { it.targetPackage }.contains(app.key.componentName.packageName)
        } else {
            true
        }
    }

    val loading = folderInfo == null && apps.isEmpty()

    PreferenceScaffold(
        label = if (loading) {
            stringResource(R.string.loading)
        } else {
            stringResource(R.string.x_with_y_count, folderInfo?.title.toString(), selectedAppsInFolder.size)
        },
        modifier = modifier,
        actions = {
            if (!loading) {
                ListSortingOptions(
                    originalList = apps,
                    filteredList = selectedAppsInFolder,
                    onUpdateList = { newSet ->
                        selectedAppsInFolder = newSet

                        viewModel.updateFolderItems(
                            folderInfoId,
                            folderInfo?.title.toString(),
                            newSet.toList(),
                        )
                    },
                    filterUniqueItems = filterNonUniqueItems,
                    onToggleFilterUniqueItems = {
                        filterNonUniqueItems = it
                    },
                )
            }
        },
        isExpandedScreen = LocalIsExpandedScreen.current,
    ) {
        Crossfade(targetState = loading, label = "") { isLoading ->
            if (isLoading) {
                PreferenceLazyColumn(it, enabled = false, state = rememberLazyListState()) {
                    preferenceGroupItems(
                        count = 20,
                        isFirstChild = true,
                        dividerStartIndent = 40.dp,
                    ) {
                        AppItemPlaceholder {
                            Spacer(Modifier.width(24.dp))
                        }
                    }
                }
            } else {
                PreferenceLazyColumn(it, state = rememberLazyListState()) {
                    preferenceGroupItems(
                        filteredApps,
                        isFirstChild = true,
                        dividerStartIndent = 40.dp,
                    ) { _, app ->
                        key(app.toString()) {
                            AppItem(
                                app,
                                onClick = {
                                    updateFolderItems(
                                        app = it,
                                        items = selectedAppsInFolder,
                                        context = context,
                                        onSetChange = { newSet ->
                                            selectedAppsInFolder = newSet

                                            viewModel.updateFolderItems(
                                                folderInfoId,
                                                folderInfo?.title.toString(),
                                                newSet.filterIsInstance<AppInfo>().toList(),
                                            )
                                        },
                                    )
                                },
                            ) {
                                Checkbox(
                                    checked = selectedAppsInFolder.any {
                                        val appInfo = it as? AppInfo
                                        appInfo?.targetPackage == app.key.componentName.packageName && appInfo.user == app.key.user
                                    },
                                    onCheckedChange = null,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ListSortingOptions(
    originalList: List<App>,
    filteredList: Set<ItemInfo>,
    onUpdateList: (Set<AppInfo>) -> Unit,
    filterUniqueItems: Boolean,
    onToggleFilterUniqueItems: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    OverflowMenu(modifier) {
        val originalListPackageNames = originalList
            .map { it.key.componentName.packageName }
        DropdownMenuItem(
            onClick = {
                val inverseSelectionPackageNames = originalListPackageNames
                    .filter { items ->
                        !filteredList.map { it.targetPackage }.contains(items)
                    }
                    .toSet()

                val inverseSelection = originalList
                    .filter {
                        inverseSelectionPackageNames.contains(it.key.componentName.packageName)
                    }
                    .map {
                        it.toAppInfo(context)
                    }
                    .toSet()

                onUpdateList(inverseSelection)
                hideMenu()
            },
            text = {
                Text(stringResource(R.string.inverse_selection))
            },
        )

        val selectedAll = originalListPackageNames == filteredList.map { it.targetPackage }
        DropdownMenuItem(
            onClick = {
                onUpdateList(
                    if (selectedAll) {
                        emptySet()
                    } else {
                        originalList.map { app ->
                            app.toAppInfo(context)
                        }.toSet()
                    },
                )
                hideMenu()
            },
            text = {
                Text(
                    stringResource(if (selectedAll) R.string.deselect_all else R.string.select_all),
                )
            },
        )
        DropdownMenuItem(
            onClick = {
                onToggleFilterUniqueItems(!filterUniqueItems)
                hideMenu()
            },
            trailingIcon = {
                if (filterUniqueItems) {
                    Icon(Icons.Rounded.Check, contentDescription = null)
                }
            },
            text = {
                Text(stringResource(R.string.folders_filter_duplicates))
            },
        )
        PreferenceDivider(modifier = Modifier.padding(vertical = 8.dp))
        DropdownMenuItem(
            onClick = {
                onUpdateList(
                    emptySet(),
                )
            },
            text = {
                Text(stringResource(R.string.action_reset))
            },
        )
    }
}

fun updateFolderItems(
    app: App,
    items: Set<ItemInfo>,
    context: Context,
    onSetChange: (Set<ItemInfo>) -> Unit,
) {
    val newSet = items.toMutableSet().apply {
        val isChecked = any { it is AppInfo && it.targetPackage == app.key.componentName.packageName && it.user == app.key.user }
        if (isChecked) {
            removeIf { it is AppInfo && it.targetPackage == app.key.componentName.packageName && it.user == app.key.user }
        } else {
            add(
                app.toAppInfo(context),
            )
        }
    }

    onSetChange(newSet)
}
