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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.lawnchair.data.folder.FolderEntry
import app.lawnchair.data.folder.model.FolderViewModel
import app.lawnchair.preferences.getAdapter
import app.lawnchair.preferences.preferenceManager
import app.lawnchair.ui.preferences.LocalIsExpandedScreen
import app.lawnchair.ui.preferences.components.AppItem
import app.lawnchair.ui.preferences.components.AppItemPlaceholder
import app.lawnchair.ui.preferences.components.controls.SwitchPreference
import app.lawnchair.ui.preferences.components.layout.PreferenceGroup
import app.lawnchair.ui.preferences.components.layout.PreferenceLazyColumn
import app.lawnchair.ui.preferences.components.layout.PreferenceScaffold
import app.lawnchair.ui.preferences.components.layout.preferenceGroupItems
import app.lawnchair.ui.preferences.components.reorderable.PositionalList
import app.lawnchair.ui.preferences.components.reorderable.PositionalListOverflowMenu
import app.lawnchair.ui.preferences.components.reorderable.PositionalListState
import app.lawnchair.ui.preferences.components.reorderable.rememberPositionalListState
import app.lawnchair.util.App
import app.lawnchair.util.appsState
import com.android.launcher3.R
import com.android.launcher3.util.LabelComparator

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
    val sortItemsAdapter = preferenceManager().sortDrawerFolderItems.getAdapter()
    val sortItems = sortItemsAdapter.state.value

    val activeIds = remember(folderEntry) {
        folderEntry?.itemComponentKeys ?: emptyList()
    }
    // Read by onOrderChange, which the list state keeps from its first composition.
    val currentActiveIds by rememberUpdatedState(activeIds)
    val currentSortItems by rememberUpdatedState(sortItems)

    val (positionalItems, activeCount) = remember(apps, activeIds, filterNonUniqueItems, allFolderPackages, sortItems) {
        val filtered = apps.filter { app ->
            if (filterNonUniqueItems) {
                val packages = allFolderPackages ?: emptySet()
                !packages.contains(app.key.componentName.packageName) ||
                    activeIds.contains(app.key.toString())
            } else {
                true
            }
        }
        val (items, count) = PositionalListState.prepareCategorizedItems(
            allItems = filtered,
            enabledIds = activeIds,
            idSelector = { it.key.toString() },
        )
        if (sortItems) {
            val labelComparator = LabelComparator()
            items.take(count).sortedWith(compareBy(labelComparator) { it.data.label }) + items.drop(count) to count
        } else {
            items to count
        }
    }

    val loading = folderEntry == null || apps.isEmpty()

    val state = key(loading) {
        rememberPositionalListState(
            items = positionalItems,
            activeCount = activeCount,
            onOrderChange = { newList, newCount ->
                val sorted = PositionalListState.sortInactiveItems(newList, newCount) { it.label }
                val newKeys = PositionalListState.getEnabledKeys(sorted, newCount)
                // When sorted, only the selection changes: keep the saved manual order (new apps go
                // last), so it comes back if sorting is turned off.
                val activeKeys = if (currentSortItems) {
                    currentActiveIds.filter { it in newKeys } + newKeys.filter { it !in currentActiveIds }
                } else {
                    newKeys
                }
                onUpdate(folderEntry?.title.toString(), activeKeys)
            },
            labelSelector = { it.label },
        )
    }

    PreferenceScaffold(
        label = if (loading) {
            stringResource(R.string.loading)
        } else {
            stringResource(R.string.x_with_y_count, folderEntry.title, state.activeCount)
        },
        modifier = modifier,
        actions = {
            if (!loading) {
                PositionalListOverflowMenu(
                    state = state,
                ) { hideMenu ->
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
                }
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
                    state = state,
                    contentPadding = contentPadding,
                    sorted = sortItems,
                    headerContent = {
                        PreferenceGroup {
                            SwitchPreference(
                                adapter = sortItemsAdapter,
                                label = stringResource(R.string.sort_drawer_folder_items_label),
                                description = stringResource(R.string.sort_drawer_folder_items_description),
                            )
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun PositionalAppListPreference(
    state: PositionalListState<App, String>,
    contentPadding: PaddingValues,
    sorted: Boolean,
    headerContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    PositionalList(
        state = state,
        itemContent = { app, dragHandle, toggle ->
            AppItem(
                app = app,
                onClick = {},
                widget = if (sorted) null else dragHandle,
                endWidget = toggle,
            )
        },
        contentPadding = contentPadding,
        modifier = modifier,
        reorderEnabled = !sorted,
        headerContent = headerContent,
    )
}
