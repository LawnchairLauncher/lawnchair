/*
 * Copyright (C) 2025 Lawnchair
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package app.lawnchair.widget

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.lawnchair.views.ComposeBottomSheet
import com.android.launcher3.Launcher
import com.android.launcher3.LauncherSettings
import com.android.launcher3.R
import com.android.launcher3.model.WidgetItem
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.LauncherAppWidgetInfo
import com.android.launcher3.widget.PendingAddWidgetInfo
import com.android.launcher3.widget.WidgetManagerHelper
import com.android.launcher3.widget.model.WidgetsListContentEntry

/**
 * Shows the widget stack dialog using Compose
 */
fun showWidgetStackDialog(
    launcher: Launcher,
    widgetInfo: LauncherAppWidgetInfo,
) {
    val isEditing = widgetInfo.widgetStackId != null

    // Ensure we're using valid container values
    val validContainer = if (widgetInfo.container == LauncherSettings.Favorites.CONTAINER_DESKTOP ||
        widgetInfo.container == LauncherSettings.Favorites.CONTAINER_HOTSEAT
    ) {
        widgetInfo.container
    } else {
        // Fallback to CONTAINER_DESKTOP if invalid
        LauncherSettings.Favorites.CONTAINER_DESKTOP
    }

    // Initialize stack info
    val initialStackInfo = if (isEditing) {
        val stackId = widgetInfo.widgetStackId!!

        // Strategy 1: load from DB
        val db = launcher.model.modelDbController.db
        var result: WidgetStackInfo? = WidgetStackManager.loadStack(db, stackId)

        // Strategy 2: read live state from the WidgetStackView on the workspace
        if (result == null) {
            launcher.workspace?.let { workspace ->
                workspace.mapOverItems(
                    object : com.android.launcher3.util.LauncherBindableItemsContainer.ItemOperator {
                        override fun evaluate(info: ItemInfo, v: android.view.View): Boolean {
                            if (v is WidgetStackView && info is LauncherAppWidgetInfo &&
                                info.widgetStackId == stackId
                            ) {
                                result = v.getStackInfo()
                                return true
                            }
                            return false
                        }
                    },
                )
            }
        }

        // Strategy 3: query DB for every widget that references this stackId
        if (result == null) {
            val ids = mutableListOf<Int>()
            try {
                db.query(
                    LauncherSettings.Favorites.TABLE_NAME,
                    arrayOf(LauncherSettings.Favorites.APPWIDGET_ID),
                    "${LauncherSettings.Favorites.WIDGET_STACK_ID} = ?",
                    arrayOf(stackId.toString()),
                    null,
                    null,
                    null,
                ).use { cursor ->
                    while (cursor.moveToNext()) {
                        ids.add(cursor.getInt(0))
                    }
                }
            } catch (_: Exception) { }

            if (ids.isEmpty()) ids.add(widgetInfo.appWidgetId)

            result = WidgetStackInfo(
                stackId = stackId,
                widgetIds = ids,
                container = validContainer,
                screenId = widgetInfo.screenId,
                cellX = widgetInfo.cellX,
                cellY = widgetInfo.cellY,
                spanX = widgetInfo.spanX,
                spanY = widgetInfo.spanY,
            )
        }

        // Filter out widget IDs that no longer exist in the model
        // so the dialog only shows live, valid widgets.
        val bgDataModel = launcher.model.getBgDataModel()
        val liveIds = result?.let { info ->
            synchronized(bgDataModel) {
                info.widgetIds.filter { wid ->
                    bgDataModel.itemsIdMap.any { it is LauncherAppWidgetInfo && it.appWidgetId == wid }
                }
            }
        } ?: emptyList()

        if (liveIds.isNotEmpty() && liveIds.size != result!!.widgetIds.size) {
            result = result!!.copy(widgetIds = liveIds)
        }

        result
    } else {
        // New stack — just wrap the current widget
        WidgetStackInfo(
            stackId = widgetInfo.id.toLong(),
            widgetIds = listOf(widgetInfo.appWidgetId),
            container = validContainer,
            screenId = widgetInfo.screenId,
            cellX = widgetInfo.cellX,
            cellY = widgetInfo.cellY,
            spanX = widgetInfo.spanX,
            spanY = widgetInfo.spanY,
        )
    }

    ComposeBottomSheet.show(
        context = launcher,
        contentPaddings = androidx.compose.foundation.layout.PaddingValues(bottom = 64.dp),
    ) {
        this.WidgetStackDialogContent(
            isEditing = isEditing,
            initialStackInfo = initialStackInfo,
            launcher = launcher,
            onSave = { stackInfo: WidgetStackInfo ->
                // Determine which widgets were removed (if editing)
                val removedWidgets = if (isEditing && initialStackInfo != null) {
                    initialStackInfo.widgetIds.filter { widgetId: Int -> widgetId !in stackInfo.widgetIds }
                } else {
                    emptyList()
                }

                // Delete removed widgets from database
                val modelWriter = launcher.modelWriter
                if (removedWidgets.isNotEmpty() && modelWriter != null) {
                    val bgDataModel = launcher.model.getBgDataModel()
                    removedWidgets.forEach { widgetId: Int ->
                        synchronized(bgDataModel) {
                            // Find and delete the widget
                            val widgetInfo = bgDataModel.itemsIdMap.firstOrNull { itemInfo: ItemInfo ->
                                itemInfo is LauncherAppWidgetInfo && itemInfo.appWidgetId == widgetId
                            } as? LauncherAppWidgetInfo

                            if (widgetInfo != null) {
                                modelWriter.deleteItemFromDatabase(widgetInfo, "removed from stack")
                            }
                        }
                    }
                }

                val validContainer = if (stackInfo.container == LauncherSettings.Favorites.CONTAINER_DESKTOP ||
                    stackInfo.container == LauncherSettings.Favorites.CONTAINER_HOTSEAT
                ) {
                    stackInfo.container
                } else {
                    LauncherSettings.Favorites.CONTAINER_DESKTOP
                }

                val finalStackInfo = stackInfo.copy(container = validContainer)
                val bgDataModel = launcher.model.getBgDataModel()

                // Collect LauncherAppWidgetInfo objects for all widgets in the stack
                val widgetsToUpdate = synchronized(bgDataModel) {
                    finalStackInfo.widgetIds.mapNotNull { widgetId: Int ->
                        bgDataModel.itemsIdMap.firstOrNull { itemInfo: ItemInfo ->
                            itemInfo is LauncherAppWidgetInfo && itemInfo.appWidgetId == widgetId
                        } as? LauncherAppWidgetInfo
                    }
                }

                if (isEditing) {
                    // --- Edit existing stack ---
                    // Find the WidgetStackView on the workspace
                    var existingStackView: WidgetStackView? = null
                    launcher.workspace?.let { workspace ->
                        workspace.mapOverItems(object : com.android.launcher3.util.LauncherBindableItemsContainer.ItemOperator {
                            override fun evaluate(info: ItemInfo, v: android.view.View): Boolean {
                                if (v is WidgetStackView && info is LauncherAppWidgetInfo &&
                                    info.widgetStackId == finalStackInfo.stackId
                                ) {
                                    existingStackView = v
                                    return true
                                }
                                return false
                            }
                        })
                    }

                    existingStackView?.setStackInfo(finalStackInfo, widgetsToUpdate)
                    modelWriter?.saveWidgetStack(finalStackInfo)
                } else {
                    // --- Create new stack ---
                    widgetsToUpdate.forEach { wi ->
                        wi.widgetStackId = finalStackInfo.stackId
                        wi.container = validContainer
                        wi.sourceContainer = validContainer
                        modelWriter?.updateItemInDatabase(wi)
                    }

                    launcher.workspace?.let { workspace ->
                        val targetCellLayout = workspace.getScreenWithId(finalStackInfo.screenId)
                        targetCellLayout?.let { layout ->
                            val container = layout.getShortcutsAndWidgets()

                            // Find the old standalone widget view to replace
                            var oldWidgetView: android.view.View? = null
                            val firstWidget = widgetsToUpdate.firstOrNull()
                            if (firstWidget != null) {
                                for (i in 0 until container.childCount) {
                                    val child = container.getChildAt(i)
                                    val childInfo = child.tag as? LauncherAppWidgetInfo
                                    if (childInfo?.appWidgetId == firstWidget.appWidgetId &&
                                        child !is WidgetStackView
                                    ) {
                                        oldWidgetView = child
                                        break
                                    }
                                }
                            }

                            // Remove the old view first so the cell is free
                            oldWidgetView?.let { container.removeView(it) }

                            val stackView = WidgetStackView(launcher)
                            stackView.tag = firstWidget ?: widgetsToUpdate.firstOrNull()
                            workspace.addInScreen(
                                stackView,
                                finalStackInfo.container,
                                finalStackInfo.screenId,
                                finalStackInfo.cellX,
                                finalStackInfo.cellY,
                                finalStackInfo.spanX,
                                finalStackInfo.spanY,
                            )
                            stackView.setStackInfo(finalStackInfo, widgetsToUpdate)
                        }
                    }
                    modelWriter?.saveWidgetStack(finalStackInfo)
                }

                close(true)
            },
            onCancel = {
                close(true)
            },
        )
    }
}

@Composable
private fun ComposeBottomSheet<*>.WidgetStackDialogContent(
    isEditing: Boolean,
    initialStackInfo: WidgetStackInfo?,
    launcher: Launcher,
    onSave: (WidgetStackInfo) -> Unit,
    onCancel: () -> Unit,
) {
    // Ensure we have valid stack info
    if (initialStackInfo == null) {
        // This shouldn't happen, but handle gracefully
        Text("Error: Could not load stack information")
        return@WidgetStackDialogContent
    }

    var currentStackInfo by remember(initialStackInfo) {
        mutableStateOf(initialStackInfo)
    }
    var autoRotate by remember(currentStackInfo) {
        mutableStateOf(currentStackInfo.autoRotate)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
    ) {
        // Title
        Text(
            text = stringResource(
                if (isEditing) R.string.edit_stack else R.string.create_stack,
            ),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Widget list
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(
                items = currentStackInfo.widgetIds,
                key = { widgetId: Int -> widgetId },
            ) { widgetId: Int ->
                WidgetStackItem(
                    widgetId = widgetId,
                    launcher = launcher,
                    canRemove = currentStackInfo.widgetIds.size > 1,
                    onRemove = {
                        if (currentStackInfo.widgetIds.size > 1) {
                            currentStackInfo = currentStackInfo.copy(
                                widgetIds = currentStackInfo.widgetIds.filter { id: Int -> id != widgetId },
                                container = currentStackInfo.container,
                                screenId = currentStackInfo.screenId,
                                cellX = currentStackInfo.cellX,
                                cellY = currentStackInfo.cellY,
                                spanX = currentStackInfo.spanX,
                                spanY = currentStackInfo.spanY,
                            )
                        }
                    },
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Auto-rotate checkbox
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = autoRotate,
                onCheckedChange = { checked: Boolean ->
                    autoRotate = checked
                    currentStackInfo = currentStackInfo.copy(autoRotate = checked)
                },
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.auto_rotate_widgets),
                style = MaterialTheme.typography.bodyLarge,
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Add widget button
        Button(
            onClick = {
                // Close current dialog and show widget picker
                close(true)
                showWidgetPickerDialog(launcher, currentStackInfo) { widgetItem: WidgetItem ->
                    val providerInfo = widgetItem.widgetInfo ?: return@showWidgetPickerDialog

                    val validContainer = if (currentStackInfo.container == LauncherSettings.Favorites.CONTAINER_DESKTOP ||
                        currentStackInfo.container == LauncherSettings.Favorites.CONTAINER_HOTSEAT
                    ) {
                        currentStackInfo.container
                    } else {
                        LauncherSettings.Favorites.CONTAINER_DESKTOP
                    }

                    val pendingInfo = PendingAddWidgetInfo(providerInfo, validContainer).apply {
                        spanX = currentStackInfo.spanX
                        spanY = currentStackInfo.spanY
                        minSpanX = widgetItem.spanX
                        minSpanY = widgetItem.spanY
                    }

                    val provider = providerInfo.getComponent()
                    if (provider != null) {
                        WidgetStackManager.storePendingStackInfoByProvider(provider, currentStackInfo)
                    }

                    // Create target cell array for addPendingItem
                    val targetCell = intArrayOf(currentStackInfo.cellX, currentStackInfo.cellY)

                    // Call addPendingItem - this will:
                    // 1. Allocate widget ID (inside addAppWidgetFromDrop)
                    // 2. Try to bind the widget
                    // 3. If binding fails (no permission), call startBindFlow to show permission dialog
                    // 4. The permission dialog will be shown to the user (this is what was missing!)
                    launcher.addPendingItem(
                        pendingInfo,
                        validContainer,
                        currentStackInfo.screenId,
                        targetCell,
                        currentStackInfo.spanX,
                        currentStackInfo.spanY,
                    )

                    // Note: After permission is granted, Launcher.onActivityResult will:
                    // 1. Call addAppWidgetImpl to add the widget
                    // 2. Check for pending stack info and add widget to stack
                    // The widget ID will be available in onActivityResult
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.add_widget_to_stack))
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.padding(end = 8.dp),
            ) {
                Text(stringResource(android.R.string.cancel))
            }
            Button(
                onClick = { onSave(currentStackInfo) },
            ) {
                Text(stringResource(R.string.save_stack))
            }
        }
    }
}

@Composable
private fun WidgetStackItem(
    widgetId: Int,
    launcher: Launcher,
    canRemove: Boolean,
    onRemove: () -> Unit,
) {
    val widgetName = remember(widgetId, launcher) {
        // Try BgDataModel first, then AppWidgetManager as fallback
        val bgDataModel = launcher.model.getBgDataModel()
        val fromModel = synchronized(bgDataModel) {
            var info: LauncherAppWidgetInfo? = null
            for (item in bgDataModel.itemsIdMap) {
                if (item is LauncherAppWidgetInfo && item.appWidgetId == widgetId) {
                    info = item
                    break
                }
            }
            info?.let { wi ->
                val wmHelper = WidgetManagerHelper(launcher)
                wmHelper.getLauncherAppWidgetInfo(widgetId, wi.providerName)?.label
                    ?: wi.providerName?.className?.substringAfterLast('.')
            }
        }
        fromModel ?: try {
            val awm = android.appwidget.AppWidgetManager.getInstance(launcher)
            val providerInfo = awm.getAppWidgetInfo(widgetId)
            providerInfo?.loadLabel(launcher.packageManager) ?: "Widget"
        } catch (_: Exception) {
            "Widget"
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = widgetName,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )

        if (canRemove) {
            IconButton(
                onClick = onRemove,
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = stringResource(R.string.remove_widget),
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}

/**
 * Shows a widget picker as a separate bottom sheet
 */
private fun showWidgetPickerDialog(
    launcher: Launcher,
    stackInfo: WidgetStackInfo,
    onSelectWidget: (WidgetItem) -> Unit,
) {
    ComposeBottomSheet.show(
        context = launcher,
        contentPaddings = androidx.compose.foundation.layout.PaddingValues(bottom = 64.dp),
    ) {
        this@show.WidgetPickerDialogContent(
            launcher = launcher,
            stackInfo = stackInfo,
            onSelectWidget = { widgetItem: WidgetItem ->
                onSelectWidget(widgetItem)
                close(true)
            },
            onDismiss = {
                close(true)
            },
        )
    }
}

/**
 * Widget picker dialog content
 */
@Composable
private fun ComposeBottomSheet<*>.WidgetPickerDialogContent(
    launcher: Launcher,
    stackInfo: WidgetStackInfo,
    onSelectWidget: (WidgetItem) -> Unit,
    onDismiss: () -> Unit,
) {
    val availableWidgets = remember(launcher, stackInfo) {
        val popupDataProvider = launcher.popupDataProvider
        val allEntries = popupDataProvider.allWidgets
        val filteredWidgets = mutableListOf<WidgetItem>()

        for (entry in allEntries) {
            if (entry is WidgetsListContentEntry) {
                for (widget in entry.mWidgets) {
                    if (widget.widgetInfo != null && widget.label != null) {
                        filteredWidgets.add(widget)
                    }
                }
            }
        }
        filteredWidgets
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
    ) {
        Text(
            text = "Select Widget",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Widgets will be scaled to fit the stack",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(24.dp))

        if (availableWidgets.isEmpty()) {
            Text(
                text = "No widgets available",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 48.dp),
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(400.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(
                    items = availableWidgets,
                    key = { widgetItem: WidgetItem -> widgetItem.componentName?.flattenToString() ?: widgetItem.hashCode() },
                ) { widgetItem: WidgetItem ->
                    WidgetPickerItem(
                        widgetItem = widgetItem,
                        onClick = {
                            onSelectWidget(widgetItem)
                        },
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedButton(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(android.R.string.cancel))
        }
    }
}

@Composable
private fun WidgetPickerItem(
    widgetItem: WidgetItem,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = widgetItem.label ?: widgetItem.componentName?.shortClassName ?: "Widget",
                style = MaterialTheme.typography.bodyLarge,
            )
            val desc = widgetItem.description
            if (desc != null && desc.isNotEmpty()) {
                Text(
                    text = desc.toString(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = "${widgetItem.spanX}x${widgetItem.spanY}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
