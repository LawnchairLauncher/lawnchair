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
import app.lawnchair.ui.preferences.components.reorderable.PositionalList
import app.lawnchair.ui.preferences.components.reorderable.PositionalListOverflowMenu
import app.lawnchair.ui.preferences.components.reorderable.PositionalListState
import app.lawnchair.ui.preferences.components.reorderable.rememberPositionalListState
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
        PositionalListState.prepareCategorizedItems(
            allItems = filtered,
            enabledIds = activeIds,
            idSelector = { it.key.toString() },
        )
    }

    val loading = folderEntry == null || apps.isEmpty()

    val state = key(loading) {
        rememberPositionalListState(
            items = positionalItems,
            activeCount = activeCount,
            onOrderChange = { newList, newCount ->
                val sorted = PositionalListState.sortInactiveItems(newList, newCount) { it.label }
                val activeKeys = PositionalListState.getEnabledKeys(sorted, newCount)
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
                )
            }
        }
    }
}

@Composable
private fun PositionalAppListPreference(
    state: PositionalListState<App, String>,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    PositionalList(
        state = state,
        itemContent = { app, dragHandle, toggle ->
            AppItem(
                app = app,
                onClick = {},
                widget = dragHandle,
                endWidget = toggle,
            )
        },
        contentPadding = contentPadding,
        modifier = modifier,
    )
}
