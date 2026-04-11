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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import app.lawnchair.views.ComposeBottomSheet
import com.android.launcher3.Launcher
import com.android.launcher3.LauncherSettings
import com.android.launcher3.R
import com.android.launcher3.util.Executors
import com.android.launcher3.model.WidgetItem
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.LauncherAppWidgetInfo
import com.android.launcher3.widget.PendingAddWidgetInfo
import com.android.launcher3.widget.picker.WidgetsFullSheet

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

    fun presentWidgetStackSheet(initialStackInfo: WidgetStackInfo?) {
        ComposeBottomSheet.show(
            context = launcher,
            contentPaddings = PaddingValues(),
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

                    // Delete removed widgets from database (lookups under lock; DB work is async on model thread)
                    val modelWriter = launcher.modelWriter
                    if (removedWidgets.isNotEmpty() && modelWriter != null) {
                        val bgDataModel = launcher.model.getBgDataModel()
                        val toDelete = synchronized(bgDataModel) {
                            removedWidgets.mapNotNull { widgetId: Int ->
                                bgDataModel.itemsIdMap.firstOrNull { itemInfo: ItemInfo ->
                                    itemInfo is LauncherAppWidgetInfo && itemInfo.appWidgetId == widgetId
                                } as? LauncherAppWidgetInfo
                            }
                        }
                        toDelete.forEach { wi: LauncherAppWidgetInfo ->
                            modelWriter.deleteItemFromDatabase(wi, "removed from stack")
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

    if (!isEditing) {
        presentWidgetStackSheet(
            WidgetStackInfo(
                stackId = widgetInfo.id.toLong(),
                widgetIds = listOf(widgetInfo.appWidgetId),
                container = validContainer,
                screenId = widgetInfo.screenId,
                cellX = widgetInfo.cellX,
                cellY = widgetInfo.cellY,
                spanX = widgetInfo.spanX,
                spanY = widgetInfo.spanY,
            ),
        )
        return
    }

    val stackId = widgetInfo.widgetStackId!!
    Executors.MODEL_EXECUTOR.execute {
        val fromDb: WidgetStackInfo? = try {
            val db = launcher.model.modelDbController.db
            WidgetStackManager.loadStack(db, stackId)
        } catch (_: Exception) {
            null
        }

        val strategy3Fallback: WidgetStackInfo? = if (fromDb == null) {
            try {
                val db = launcher.model.modelDbController.db
                val ids = mutableListOf<Int>()
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
                if (ids.isEmpty()) ids.add(widgetInfo.appWidgetId)
                WidgetStackInfo(
                    stackId = stackId,
                    widgetIds = ids,
                    container = validContainer,
                    screenId = widgetInfo.screenId,
                    cellX = widgetInfo.cellX,
                    cellY = widgetInfo.cellY,
                    spanX = widgetInfo.spanX,
                    spanY = widgetInfo.spanY,
                )
            } catch (_: Exception) {
                WidgetStackInfo(
                    stackId = stackId,
                    widgetIds = listOf(widgetInfo.appWidgetId),
                    container = validContainer,
                    screenId = widgetInfo.screenId,
                    cellX = widgetInfo.cellX,
                    cellY = widgetInfo.cellY,
                    spanX = widgetInfo.spanX,
                    spanY = widgetInfo.spanY,
                )
            }
        } else {
            null
        }

        Executors.MAIN_EXECUTOR.execute {
            if (launcher.isFinishing || launcher.isDestroyed) return@execute

            var result = fromDb
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
            if (result == null) {
                result = strategy3Fallback
            }

            val bgDataModel = launcher.model.getBgDataModel()
            val liveIds = result?.let { info ->
                synchronized(bgDataModel) {
                    info.widgetIds.filter { wid ->
                        bgDataModel.itemsIdMap.any { it is LauncherAppWidgetInfo && it.appWidgetId == wid }
                    }
                }
            } ?: emptyList()

            if (liveIds.isNotEmpty() && result != null && liveIds.size != result!!.widgetIds.size) {
                result = result!!.copy(widgetIds = liveIds)
            }

            presentWidgetStackSheet(result)
        }
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

    var localWidgetIds by remember(initialStackInfo) {
        mutableStateOf(initialStackInfo.widgetIds)
    }
    LaunchedEffect(currentStackInfo.widgetIds) {
        if (localWidgetIds != currentStackInfo.widgetIds) {
            localWidgetIds = currentStackInfo.widgetIds
        }
    }

    val scroll = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .verticalScroll(scroll)
            .padding(horizontal = 24.dp)
            .padding(top = 20.dp, bottom = 24.dp),
    ) {
        // Title (match widget sheet typography weight)
        Text(
            text = stringResource(
                if (isEditing) R.string.edit_stack else R.string.create_stack,
            ),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stringResource(
                R.string.widget_stack_sheet_subtitle,
                currentStackInfo.spanX,
                currentStackInfo.spanY,
            ),
            style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(16.dp))

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = stringResource(R.string.widget_stack_reorder_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
        )

        AndroidView(
            modifier = Modifier.fillMaxWidth(),
            factory = { WidgetStackEditMembersHost(launcher) },
            update = { host: WidgetStackEditMembersHost ->
                host.bind(
                    widgetIds = localWidgetIds,
                    currentIndex = currentStackInfo.currentIndex,
                    onRemove = { widgetId: Int ->
                        val oldIds = currentStackInfo.widgetIds
                        if (oldIds.size > 1) {
                            val oldCur = currentStackInfo.currentIndex.coerceIn(
                                0,
                                (oldIds.size - 1).coerceAtLeast(0),
                            )
                            val visibleId = oldIds.getOrNull(oldCur)
                            val newIds = oldIds.filter { id: Int -> id != widgetId }
                            val newIdx = when {
                                visibleId != null && visibleId in newIds ->
                                    newIds.indexOf(visibleId)

                                newIds.isEmpty() -> 0

                                else -> oldCur.coerceIn(0, newIds.lastIndex)
                            }
                            localWidgetIds = newIds
                            currentStackInfo = currentStackInfo.copy(
                                widgetIds = newIds,
                                currentIndex = newIdx,
                            )
                        }
                    },
                    onReorder = { newIds: List<Int> ->
                        val oldCur = currentStackInfo.currentIndex.coerceIn(
                            0,
                            (localWidgetIds.size - 1).coerceAtLeast(0),
                        )
                        val visibleId = localWidgetIds.getOrNull(oldCur)
                        val newIdx = when {
                            visibleId != null && visibleId in newIds ->
                                newIds.indexOf(visibleId)

                            newIds.isEmpty() -> 0

                            else -> oldCur.coerceIn(0, newIds.lastIndex)
                        }
                        localWidgetIds = newIds
                        currentStackInfo = currentStackInfo.copy(
                            widgetIds = newIds,
                            currentIndex = newIdx,
                        )
                    },
                    onAddWidget = {
                        close(true)
                        showWidgetPickerDialog(launcher) { widgetItem: WidgetItem ->
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

                            val targetCell = intArrayOf(currentStackInfo.cellX, currentStackInfo.cellY)

                            launcher.addPendingItem(
                                pendingInfo,
                                validContainer,
                                currentStackInfo.screenId,
                                targetCell,
                                currentStackInfo.spanX,
                                currentStackInfo.spanY,
                            )
                        }
                    },
                    onPageSelected = { pos: Int ->
                        if (currentStackInfo.currentIndex != pos) {
                            currentStackInfo = currentStackInfo.copy(currentIndex = pos)
                        }
                    },
                )
            },
        )

        Spacer(modifier = Modifier.height(8.dp))

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

        Spacer(modifier = Modifier.height(20.dp))

        // Action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier
                    .padding(end = 12.dp)
                    .heightIn(min = 48.dp),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            ) {
                Text(stringResource(android.R.string.cancel))
            }
            Button(
                onClick = { onSave(currentStackInfo) },
                modifier = Modifier.heightIn(min = 48.dp),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            ) {
                Text(stringResource(R.string.save_stack))
            }
        }
    }
}

/**
 * Opens the same full widget tray as the launcher ([WidgetsFullSheet]) so previews, search,
 * work profile tabs, and tap behavior match the stock picker.
 */
private fun showWidgetPickerDialog(
    launcher: Launcher,
    onSelectWidget: (WidgetItem) -> Unit,
) {
    val sheet = WidgetsFullSheet.show(launcher, true)
    sheet.setPickerTitle(launcher.getString(R.string.add_widget_to_stack))
    sheet.setWidgetPickListener { item: WidgetItem ->
        onSelectWidget(item)
    }
    sheet.addOnCloseListener {
        sheet.setWidgetPickListener(null)
    }
}
