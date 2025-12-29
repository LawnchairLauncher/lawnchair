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

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.android.launcher3.widget.WidgetInflater
import com.android.launcher3.widget.WidgetManagerHelper
import com.android.launcher3.widget.model.WidgetsListBaseEntry
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
        // Load existing stack info from database
        val db = launcher.model.modelDbController.db
        val loadedStackInfo = WidgetStackManager.loadStack(db, widgetInfo.widgetStackId!!)

        // Load stack info - don't aggressively validate/remove widgets here
        // Widgets might not be fully loaded yet, or provider checks might fail temporarily
        // Only remove widgets that are truly invalid (e.g., app uninstalled)
        // This prevents the cycle of widgets being marked invalid -> restart -> valid -> invalid
        loadedStackInfo?.let { stack ->
            // Just return the loaded stack info as-is
            // Don't validate/remove widgets here - let the save logic handle it if needed
            // This prevents widgets from being incorrectly marked as invalid
            stack
        }
    } else {
        // Create new stack with current widget
        // For new stacks, be more lenient - widget might not be fully loaded yet
        // We'll validate it exists in the model, but don't fail if provider check fails
        // The widget will be validated when the stack is saved
        val bgDataModel = launcher.model.getBgDataModel()
        val widgetInModel = synchronized(bgDataModel) {
            bgDataModel.itemsIdMap.firstOrNull {
                it is LauncherAppWidgetInfo && it.appWidgetId == widgetInfo.appWidgetId
            } as? LauncherAppWidgetInfo
        }

        if (widgetInModel == null) {
            android.util.Log.w(
                "WidgetStackDialog",
                "Widget ${widgetInfo.appWidgetId} not found in model when creating stack, will use widgetInfo directly",
            )
            // Widget not in model yet - use widgetInfo directly
            // This can happen if the widget was just added and model hasn't updated yet
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
        } else {
            // Widget exists in model - create stack
            // Don't validate provider here as it might not be loaded yet
            // The provider will be validated when widgets are loaded in WidgetStackContentView
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

                // Determine valid container
                val validContainer = if (stackInfo.container == LauncherSettings.Favorites.CONTAINER_DESKTOP ||
                    stackInfo.container == LauncherSettings.Favorites.CONTAINER_HOTSEAT
                ) {
                    stackInfo.container
                } else {
                    LauncherSettings.Favorites.CONTAINER_DESKTOP
                }

                // Validate widgets before saving to ensure stack integrity
                // Filter out invalid widgets that can't be loaded
                val bgDataModel = launcher.model.getBgDataModel()
                val widgetInflater = WidgetInflater(launcher)
                val widgetManagerHelper = WidgetManagerHelper(launcher)

                // Validate all widgets in the stack
                val validatedStackInfo = synchronized(bgDataModel) {
                    val validWidgetIds = mutableListOf<Int>()
                    val invalidWidgetIds = mutableListOf<Int>()

                    stackInfo.widgetIds.forEach { widgetId ->
                        val widgetInfo = bgDataModel.itemsIdMap.firstOrNull { itemInfo: ItemInfo ->
                            itemInfo is LauncherAppWidgetInfo && itemInfo.appWidgetId == widgetId
                        } as? LauncherAppWidgetInfo

                        if (widgetInfo != null) {
                            // Validate widget using WidgetInflater
                            val validationResult = widgetInflater.inflateAppWidget(widgetInfo)
                            if (validationResult.type == WidgetInflater.TYPE_DELETE) {
                                // Widget is invalid - mark for removal
                                android.util.Log.w("WidgetStackDialog", "Widget $widgetId is invalid, removing from stack. Reason: ${validationResult.reason}")
                                invalidWidgetIds.add(widgetId)
                            } else {
                                // Widget is valid (TYPE_REAL or TYPE_PENDING)
                                validWidgetIds.add(widgetId)
                            }
                        } else {
                            // Widget not in model - might be loading or deleted
                            // Check if widget exists in AppWidgetManager
                            val appWidgetManager = android.appwidget.AppWidgetManager.getInstance(launcher)
                            try {
                                val appWidgetInfo = appWidgetManager.getAppWidgetInfo(widgetId)
                                if (appWidgetInfo != null) {
                                    // Widget exists in AppWidgetManager but not in model yet - keep it
                                    validWidgetIds.add(widgetId)
                                    android.util.Log.d("WidgetStackDialog", "Widget $widgetId exists in AppWidgetManager but not in model yet, keeping in stack")
                                } else {
                                    // Widget doesn't exist - mark for removal
                                    android.util.Log.w("WidgetStackDialog", "Widget $widgetId not found in AppWidgetManager, removing from stack")
                                    invalidWidgetIds.add(widgetId)
                                }
                            } catch (e: Exception) {
                                // Error checking widget - assume invalid
                                android.util.Log.w("WidgetStackDialog", "Error checking widget $widgetId, removing from stack", e)
                                invalidWidgetIds.add(widgetId)
                            }
                        }
                    }

                    // Clear widgetStackId from invalid widgets
                    // Don't call updateItemInDatabase here - it triggers bindItemsModified
                    // which might try to rebind widgets and remove the stack view
                    // Instead, just update in-memory and let the stack save handle it
                    if (invalidWidgetIds.isNotEmpty()) {
                        android.util.Log.w("WidgetStackDialog", "Removing ${invalidWidgetIds.size} invalid widgets from stack: $invalidWidgetIds")
                        invalidWidgetIds.forEach { widgetId ->
                            val invalidWidgetInfo = bgDataModel.itemsIdMap.firstOrNull {
                                it is LauncherAppWidgetInfo && it.appWidgetId == widgetId
                            } as? LauncherAppWidgetInfo
                            invalidWidgetInfo?.let {
                                // Just clear in-memory - don't update database yet
                                // The widget will be removed from the stack when we save the stack
                                it.widgetStackId = null
                                // Don't call updateItemInDatabase here - it triggers callbacks
                                // that might remove the stack view before we can update it
                                // The widget will be cleaned up later if needed
                            }
                        }
                    }

                    // Return validated stack info with only valid widgets
                    if (validWidgetIds.isEmpty()) {
                        android.util.Log.e("WidgetStackDialog", "All widgets invalid in stack ${stackInfo.stackId}, cannot save")
                        // Return original stack info but caller should handle empty stack case
                        stackInfo.copy(widgetIds = emptyList())
                    } else {
                        stackInfo.copy(
                            widgetIds = validWidgetIds,
                            currentIndex = stackInfo.currentIndex.coerceIn(0, (validWidgetIds.size - 1).coerceAtLeast(0)),
                        )
                    }
                }

                // Use validated stack info for the rest of the operation
                val finalValidatedStackInfo = validatedStackInfo

                // Don't save if all widgets are invalid
                if (finalValidatedStackInfo.widgetIds.isEmpty()) {
                    android.util.Log.e("WidgetStackDialog", "Cannot save stack: all widgets are invalid")
                    close(true)
                    return@WidgetStackDialogContent
                }

                // Get widgets to update (only valid ones) - do this once
                val widgetsToUpdate = synchronized(bgDataModel) {
                    finalValidatedStackInfo.widgetIds.mapNotNull { widgetId: Int ->
                        bgDataModel.itemsIdMap.firstOrNull { itemInfo: ItemInfo ->
                            itemInfo is LauncherAppWidgetInfo && itemInfo.appWidgetId == widgetId
                        } as? LauncherAppWidgetInfo
                    }
                }

                // Find WidgetStackView FIRST and update it SYNCHRONOUSLY on main thread
                // BEFORE any database operations that might trigger callbacks
                // This ensures the view stays attached and visible during editing
                var existingStackView: WidgetStackView? = null
                if (isEditing) {
                    // Find the view on the main thread (we're already on main thread in Compose)
                    // Try multiple methods to find the view for robustness
                    launcher.workspace?.let { workspace ->
                        // Method 1: Search by stackId in the target cell layout
                        val targetCellLayout = workspace.getScreenWithId(finalValidatedStackInfo.screenId)
                        targetCellLayout?.let { layout ->
                            val container = layout.getShortcutsAndWidgets()
                            for (i in 0 until container.childCount) {
                                val child = container.getChildAt(i)
                                if (child is WidgetStackView) {
                                    val childInfo = child.tag as? LauncherAppWidgetInfo
                                    if (childInfo?.widgetStackId == finalValidatedStackInfo.stackId) {
                                        existingStackView = child
                                        android.util.Log.d("WidgetStackDialog", "Found WidgetStackView by stackId ${finalValidatedStackInfo.stackId}")
                                        break
                                    }
                                }
                            }
                        }

                        // Method 2: If not found in target layout, search ALL workspace pages
                        // Use mapOverItems to search all pages efficiently
                        if (existingStackView == null) {
                            workspace.mapOverItems(object : com.android.launcher3.util.LauncherBindableItemsContainer.ItemOperator {
                                override fun evaluate(info: ItemInfo, v: android.view.View): Boolean {
                                    if (v is WidgetStackView && info is LauncherAppWidgetInfo) {
                                        if (info.widgetStackId == finalValidatedStackInfo.stackId) {
                                            existingStackView = v
                                            android.util.Log.d("WidgetStackDialog", "Found WidgetStackView by stackId ${finalValidatedStackInfo.stackId} via mapOverItems")
                                            return true // Stop searching
                                        }
                                    }
                                    return false // Continue searching
                                }
                            })
                        }

                        // Method 3: If still not found, try finding by first widget ID
                        if (existingStackView == null && widgetsToUpdate.isNotEmpty()) {
                            val firstWidget = widgetsToUpdate.first()
                            val foundView = workspace.getHomescreenIconByItemId(firstWidget.id)
                            if (foundView is WidgetStackView) {
                                existingStackView = foundView
                                android.util.Log.d("WidgetStackDialog", "Found WidgetStackView by first widget ID ${firstWidget.id}")
                            }
                        }
                    }

                    // Update the view IMMEDIATELY on main thread BEFORE database operations
                    // This ensures the view is updated before any callbacks can remove it
                    if (existingStackView != null) {
                        // Verify view is still attached to workspace before updating
                        val isAttached = existingStackView.parent != null
                        if (!isAttached) {
                            android.util.Log.w("WidgetStackDialog", "WidgetStackView is not attached, re-adding to workspace")
                            // Re-add the view to workspace
                            launcher.workspace?.let { workspace ->
                                val targetCellLayout = workspace.getScreenWithId(finalValidatedStackInfo.screenId)
                                targetCellLayout?.let { layout ->
                                    val container = layout.getShortcutsAndWidgets()
                                    // Check if view is already in container
                                    if (container.indexOfChild(existingStackView) < 0) {
                                        // View not in container - add it
                                        val firstWidget = widgetsToUpdate.firstOrNull()
                                        if (firstWidget != null) {
                                            workspace.addInScreen(
                                                existingStackView,
                                                finalValidatedStackInfo.container,
                                                finalValidatedStackInfo.screenId,
                                                finalValidatedStackInfo.cellX,
                                                finalValidatedStackInfo.cellY,
                                                finalValidatedStackInfo.spanX,
                                                finalValidatedStackInfo.spanY,
                                            )
                                            android.util.Log.d("WidgetStackDialog", "Re-added WidgetStackView to workspace")
                                        }
                                    }
                                }
                            }
                        }

                        // Update in-memory widget info (for view update)
                        widgetsToUpdate.forEach { widgetInfo ->
                            widgetInfo.widgetStackId = finalValidatedStackInfo.stackId
                            widgetInfo.container = validContainer
                            widgetInfo.sourceContainer = validContainer
                        }

                        // Update view SYNCHRONOUSLY on main thread BEFORE database operations
                        // This prevents the view from being removed by callbacks triggered by database updates
                        android.util.Log.d("WidgetStackDialog", "Updating WidgetStackView synchronously before database operations")
                        existingStackView.setStackInfo(finalValidatedStackInfo)
                        android.util.Log.d("WidgetStackDialog", "WidgetStackView updated successfully")

                        // Verify view is still attached after update
                        val stillAttached = existingStackView.parent != null
                        if (!stillAttached) {
                            android.util.Log.e("WidgetStackDialog", "WidgetStackView was removed during update! Attempting to re-add")
                            // Re-add the view
                            launcher.workspace?.let { workspace ->
                                val targetCellLayout = workspace.getScreenWithId(finalValidatedStackInfo.screenId)
                                targetCellLayout?.let { layout ->
                                    val firstWidget = widgetsToUpdate.firstOrNull()
                                    if (firstWidget != null) {
                                        workspace.addInScreen(
                                            existingStackView,
                                            finalValidatedStackInfo.container,
                                            finalValidatedStackInfo.screenId,
                                            finalValidatedStackInfo.cellX,
                                            finalValidatedStackInfo.cellY,
                                            finalValidatedStackInfo.spanX,
                                            finalValidatedStackInfo.spanY,
                                        )
                                        android.util.Log.d("WidgetStackDialog", "Re-added WidgetStackView after update")
                                    }
                                }
                            }
                        }
                    } else {
                        android.util.Log.w("WidgetStackDialog", "Could not find WidgetStackView for stack ${finalValidatedStackInfo.stackId} - view may have been removed")
                    }
                }

                // For editing, only update in-memory and save stack - DON'T update widget DB entries
                // Updating widget DB entries triggers bindItemsModified which removes/recreates views
                // Since stack widgets share the same position, this causes conflicts
                // NOTE: View is already updated above, so we just need to save to database
                if (isEditing) {
                    // View is already updated above, just save to database
                    // No need to update widget DB entries - they're already correct
                    modelWriter?.saveWidgetStack(finalValidatedStackInfo)
                } else {
                    // New stack: Update widget info and save everything
                    widgetsToUpdate.forEach { widgetInfo ->
                        widgetInfo.widgetStackId = finalValidatedStackInfo.stackId
                        widgetInfo.container = validContainer
                        widgetInfo.sourceContainer = validContainer
                    }
                    // Save stack to database
                    modelWriter?.saveWidgetStack(finalValidatedStackInfo)
                    // For new stacks, we need to update widget DB entries to set stackId
                    widgetsToUpdate.forEach { widgetInfo ->
                        modelWriter?.updateItemInDatabase(widgetInfo)
                    }

                    // Create WidgetStackView for new stack
                    if (widgetsToUpdate.isNotEmpty()) {
                        val firstWidget = widgetsToUpdate.first()
                        launcher.workspace?.let { workspace ->
                            val targetCellLayout = workspace.getScreenWithId(finalValidatedStackInfo.screenId)
                            targetCellLayout?.let { layout ->
                                val container = layout.getShortcutsAndWidgets()

                                // Find old widget view
                                var oldWidgetView: android.view.View? = null
                                for (i in 0 until container.childCount) {
                                    val child = container.getChildAt(i)
                                    val childInfo = child.tag as? LauncherAppWidgetInfo
                                    if (childInfo?.appWidgetId == firstWidget.appWidgetId && child !is WidgetStackView) {
                                        oldWidgetView = child
                                        break
                                    }
                                }

                                // Create and add WidgetStackView
                                val stackView = WidgetStackView(launcher)
                                stackView.tag = firstWidget
                                stackView.setStackInfo(finalValidatedStackInfo)

                                workspace.addInScreen(
                                    stackView,
                                    finalValidatedStackInfo.container,
                                    finalValidatedStackInfo.screenId,
                                    finalValidatedStackInfo.cellX,
                                    finalValidatedStackInfo.cellY,
                                    finalValidatedStackInfo.spanX,
                                    finalValidatedStackInfo.spanY,
                                )

                                // Remove old widget view
                                oldWidgetView?.let { layout.removeView(it) }
                            }
                        }
                    }
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
                    // Use the normal widget addition flow which handles permission requests
                    // This ensures the permission dialog shows up when needed
                    val widgetHolder = launcher.appWidgetHolder
                    val widgetManagerHelper = WidgetManagerHelper(launcher)

                    // Ensure we're using valid container values
                    val validContainer = if (currentStackInfo.container == LauncherSettings.Favorites.CONTAINER_DESKTOP ||
                        currentStackInfo.container == LauncherSettings.Favorites.CONTAINER_HOTSEAT
                    ) {
                        currentStackInfo.container
                    } else {
                        LauncherSettings.Favorites.CONTAINER_DESKTOP
                    }

                    // Create PendingAddWidgetInfo - this will be used by addPendingItem
                    // Don't allocate widget ID yet - let addAppWidgetFromDrop handle it
                    val pendingInfo = PendingAddWidgetInfo(widgetItem.widgetInfo, validContainer).apply {
                        spanX = currentStackInfo.spanX
                        spanY = currentStackInfo.spanY
                        minSpanX = widgetItem.spanX
                        minSpanY = widgetItem.spanY
                    }

                    // Store the stack info by provider component name
                    // This will be transferred to widget ID when the widget is allocated
                    val provider = widgetItem.widgetInfo.getComponent()
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
    // Get widget name from PopupDataProvider
    val widgetName = remember(widgetId, launcher) {
        val bgDataModel = launcher.model.getBgDataModel()
        synchronized(bgDataModel) {
            // Find the widget info by appWidgetId
            var widgetInfo: LauncherAppWidgetInfo? = null
            for (itemInfo in bgDataModel.itemsIdMap) {
                if (itemInfo is LauncherAppWidgetInfo && itemInfo.appWidgetId == widgetId) {
                    widgetInfo = itemInfo
                    break
                }
            }

            widgetInfo?.let { info ->
                // Try to get label from widget provider
                val widgetManagerHelper = WidgetManagerHelper(launcher)
                widgetManagerHelper.getLauncherAppWidgetInfo(widgetId, info.providerName)?.label
                    ?: info.providerName?.className?.substringAfterLast('.')
                    ?: "Widget"
            } ?: "Widget $widgetId"
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
    // Get all available widgets from PopupDataProvider and filter by size
    val availableWidgets = remember(launcher, stackInfo) {
        val popupDataProvider = launcher.popupDataProvider
        val allEntries = popupDataProvider.allWidgets
        val filteredWidgets = mutableListOf<WidgetItem>()

        for (entry in allEntries) {
            if (entry is WidgetsListContentEntry) {
                for (widget in entry.mWidgets) {
                    // Filter widgets that match the stack size
                    if (widget.spanX == stackInfo.spanX && widget.spanY == stackInfo.spanY) {
                        // Note: We can't check actual widget IDs here because widgets
                        // haven't been created yet. The widget picker will handle
                        // creating new widget instances when selected.
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
            text = "Widgets matching size ${stackInfo.spanX}x${stackInfo.spanY}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(24.dp))

        if (availableWidgets.isEmpty()) {
            Text(
                text = "No widgets available matching this size",
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
                    key = { widgetItem: WidgetItem -> widgetItem.componentName.toString() },
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
                text = widgetItem.label,
                style = MaterialTheme.typography.bodyLarge,
            )
            if (widgetItem.description.isNotEmpty()) {
                Text(
                    text = widgetItem.description.toString(),
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
