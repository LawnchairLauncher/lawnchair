package app.lawnchair.ui.preferences.destinations

import android.content.Context
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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
import app.lawnchair.ui.preferences.LocalIsExpandedScreen
import app.lawnchair.ui.preferences.components.AppItem
import app.lawnchair.ui.preferences.components.AppItemPlaceholder
import app.lawnchair.ui.preferences.components.DragHandle
import app.lawnchair.ui.preferences.components.DraggablePreferenceGroup
import app.lawnchair.ui.preferences.components.layout.PreferenceLayoutLazyColumn
import app.lawnchair.ui.preferences.components.layout.preferenceGroupItems
import app.lawnchair.util.App
import app.lawnchair.util.appsState
import com.android.launcher3.R
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.util.ComponentKey

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
    val selectedAppsInFolder = remember { mutableStateListOf<App>() }

    LaunchedEffect(folderInfoId) {
        viewModel.setFolderInfo(folderInfoId, false)
    }

    val apps by appsState()
    var isInitialLoad by remember { mutableStateOf(true) }

    LaunchedEffect(folderInfo, apps) {
        if (isInitialLoad && folderInfo != null && apps.isNotEmpty()) {
            val currentContent = folderInfo!!.getContents()
            val orderedApps = currentContent.sortedBy { it.rank }.mapNotNull { item ->
                val key = ComponentKey(item.targetComponent, item.user)
                apps.find { it.key == key }
            }
            selectedAppsInFolder.clear()
            selectedAppsInFolder.addAll(orderedApps)
            isInitialLoad = false
        }
    }

    val loading = folderInfo == null && apps.isEmpty()

    PreferenceLayoutLazyColumn(
        label = if (loading) {
            stringResource(R.string.loading)
        } else {
            stringResource(R.string.x_with_y_count, folderInfo?.title.toString(), selectedAppsInFolder.size)
        },
        modifier = modifier,
        isExpandedScreen = LocalIsExpandedScreen.current,
    ) {
        if (loading) {
            preferenceGroupItems(
                count = 20,
                isFirstChild = true,
                dividerStartIndent = 40.dp,
            ) {
                AppItemPlaceholder()
            }
        } else {
            item {
                if (selectedAppsInFolder.isNotEmpty()) {
                    DraggablePreferenceGroup<App>(
                        label = stringResource(R.string.selected_apps),
                        items = selectedAppsInFolder,
                        defaultList = selectedAppsInFolder,
                        onOrderChange = { newOrder ->
                            selectedAppsInFolder.clear()
                            selectedAppsInFolder.addAll(newOrder)
                        },
                        onSettle = { newOrder ->
                            // Update the database when drag settles
                            viewModel.updateFolderItems(
                                folderInfoId,
                                folderInfo?.title.toString(),
                                newOrder.map { it.toAppInfo(context) },
                            )
                        },
                    ) { app, _, _, onDraggingChange ->
                        val interactionSource = remember { MutableInteractionSource() }
                        AppItem(
                            app = app,
                            onClick = { },
                            widget = {
                                DragHandle(
                                    scope = this,
                                    interactionSource = interactionSource,
                                    onDragStop = {
                                        onDraggingChange(false)
                                    },
                                )
                            },
                            endWidget = {
                                IconButton(onClick = {
                                    val newList = selectedAppsInFolder.toMutableList()
                                    newList.remove(app)
                                    selectedAppsInFolder.clear()
                                    selectedAppsInFolder.addAll(newList)

                                    viewModel.updateFolderItems(
                                        folderInfoId,
                                        folderInfo?.title.toString(),
                                        newList.map { it.toAppInfo(context) },
                                    )
                                }) {
                                    Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.delete_label))
                                }
                            },
                        )
                    }
                }
            }

            val unselectedApps = apps.filter { !selectedAppsInFolder.contains(it) }

            preferenceGroupItems(
                items = unselectedApps,
                heading = if (selectedAppsInFolder.isNotEmpty()) {
                    { stringResource(R.string.add_apps) }
                } else {
                    null
                },
                isFirstChild = selectedAppsInFolder.isEmpty(),
                dividerStartIndent = 40.dp,
            ) { _, app ->
                AppItem(
                    app = app,
                    onClick = {
                        selectedAppsInFolder.add(app)
                        viewModel.updateFolderItems(
                            folderInfoId,
                            folderInfo?.title.toString(),
                            selectedAppsInFolder.map { it.toAppInfo(context) },
                        )
                    },
                    widget = {
                        Icon(Icons.Rounded.Add, contentDescription = null)
                    },
                )
            }
        }
    }
}
