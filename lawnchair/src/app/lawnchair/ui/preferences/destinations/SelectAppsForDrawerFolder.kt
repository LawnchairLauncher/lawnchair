package app.lawnchair.ui.preferences.destinations

import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.lawnchair.data.folder.FolderEntry
import app.lawnchair.data.folder.model.FolderViewModel
import app.lawnchair.ui.preferences.LocalIsExpandedScreen
import app.lawnchair.ui.preferences.components.AppItem
import app.lawnchair.ui.preferences.components.AppItemPlaceholder
import app.lawnchair.ui.preferences.components.layout.PreferenceLazyColumn
import app.lawnchair.ui.preferences.components.layout.PreferenceScaffold
import app.lawnchair.ui.preferences.components.layout.preferenceGroupItems
import app.lawnchair.ui.preferences.components.reorderable.PositionalListItem
import app.lawnchair.ui.preferences.components.reorderable.PositionalMapper
import app.lawnchair.ui.preferences.components.reorderable.PositionalOrderMenu
import app.lawnchair.ui.preferences.components.reorderable.PositionalReorderer
import app.lawnchair.util.App
import app.lawnchair.util.appsState
import com.android.launcher3.R

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

    val apps by appsState()
    val folderEntry by viewModel.getFolderFlowForId(folderInfoId).collectAsStateWithLifecycle(null)
    val allFolderPackages by viewModel.allFolderPackages.collectAsStateWithLifecycle()

    SelectAppsForDrawerFolder(
        folderEntry = folderEntry,
        apps = apps,
        allFolderPackages = allFolderPackages,
        onUpdate = { title, componentKeys ->
            viewModel.updateFolderItems(folderInfoId, title, componentKeys)
        },
        modifier = modifier,
    )
}

@Composable
fun SelectAppsForDrawerFolder(
    folderEntry: FolderEntry?,
    apps: List<App>,
    allFolderPackages: Set<String>?,
    onUpdate: (title: String, componentKeys: List<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    var filterNonUniqueItems by remember { mutableStateOf(true) }

    val activeIds = remember(folderEntry) {
        folderEntry?.itemComponentKeys ?: emptyList()
    }

    val (positionalItems, activeCount) = remember(apps, activeIds, filterNonUniqueItems, allFolderPackages) {
        val filtered = apps.filter { app ->
            if (filterNonUniqueItems) {
                val packages = allFolderPackages ?: emptySet()
                !packages.contains(app.key.componentName.packageName) ||
                    activeIds.contains(app.key.toString())
            } else {
                true
            }
        }
        PositionalMapper.prepareCategorizedItems(
            allItems = filtered,
            enabledIds = activeIds,
            idSelector = { it.key.toString() },
        )
    }

    val loading = folderEntry == null || apps.isEmpty()

    PreferenceScaffold(
        label = if (loading) {
            stringResource(R.string.loading)
        } else {
            stringResource(R.string.x_with_y_count, folderEntry.title, activeCount)
        },
        modifier = modifier,
        actions = {
            if (!loading) {
                PositionalOrderMenu(
                    items = positionalItems,
                    activeCount = activeCount,
                    onUpdate = { newList, newCount ->
                        val sorted = PositionalMapper.sortInactiveItems(newList, newCount) { it.label }
                        val activeKeys = PositionalMapper.getEnabledKeys(sorted, newCount)
                        onUpdate(folderEntry.title, activeKeys)
                    },
                    labelSelector = { it.label },
                    additionalContent = { hideMenu ->
                        DropdownMenuItem(
                            onClick = {
                                filterNonUniqueItems = !filterNonUniqueItems
                                hideMenu()
                            },
                            trailingIcon = {
                                if (filterNonUniqueItems) Icon(Icons.Rounded.Check, null)
                            },
                            text = { Text(stringResource(R.string.folders_filter_duplicates)) },
                        )
                    },
                )
            }
        },
        isExpandedScreen = LocalIsExpandedScreen.current,
    ) { contentPadding ->
        Crossfade(targetState = loading, label = "") { isLoading ->
            if (isLoading) {
                PreferenceLazyColumn(contentPadding, enabled = false, state = rememberLazyListState()) {
                    preferenceGroupItems(
                        count = 20,
                        isFirstChild = true,
                    ) {
                        AppItemPlaceholder {
                            Spacer(Modifier.width(24.dp))
                        }
                    }
                }
            } else {
                PositionalAppListPreference(
                    items = positionalItems,
                    activeCount = activeCount,
                    onOrderChange = { newList, newCount ->
                        val sorted = PositionalMapper.sortInactiveItems(newList, newCount) { it.label }
                        val activeKeys = PositionalMapper.getEnabledKeys(sorted, newCount)
                        onUpdate(folderEntry?.title.toString(), activeKeys)
                    },
                    contentPadding = contentPadding,
                )
            }
        }
    }
}

@Composable
private fun PositionalAppListPreference(
    items: List<PositionalListItem<App>>,
    activeCount: Int,
    onOrderChange: (newList: List<PositionalListItem<App>>, newEnabledCount: Int) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    PositionalReorderer(
        items = items,
        activeCount = activeCount,
        onOrderChange = onOrderChange,
        itemContent = { app, dragHandle, toggle ->
            AppItem(
                app = app,
                onClick = {},
                widget = dragHandle,
                endWidget = toggle,
            )
        },
        labelSelector = { it.label },
        contentPadding = contentPadding,
        modifier = modifier,
    )
}
