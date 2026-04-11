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

import android.util.TypedValue
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import com.android.launcher3.LauncherAppState
import com.android.launcher3.LauncherSettings
import com.android.launcher3.R
import com.android.launcher3.model.WidgetItem
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.LauncherAppWidgetInfo
import com.android.launcher3.util.Executors.MODEL_EXECUTOR
import com.android.launcher3.widget.PendingAddWidgetInfo
import com.android.launcher3.widget.WidgetCell
import com.android.launcher3.widget.WidgetManagerHelper
import com.android.launcher3.widget.picker.WidgetsFullSheet
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * [widget_cell] uses `?attr/widgetCellTitleColor` etc., which live on [R.style.WidgetContainerTheme]
 * (see `widgets_full_sheet.xml` `android:theme="?attr/widgetsTheme"`), not on the activity theme.
 */
private fun widgetCellLayoutInflater(launcher: Launcher): LayoutInflater {
    val tv = TypedValue()
    return if (launcher.theme.resolveAttribute(R.attr.widgetsTheme, tv, true)) {
        LayoutInflater.from(ContextThemeWrapper(launcher, tv.resourceId))
    } else {
        LayoutInflater.from(launcher)
    }
}

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

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = stringResource(R.string.widget_stack_members_title, currentStackInfo.widgetIds.size),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Column (not LazyColumn): AndroidView+WidgetCell measures reliably; stacks are small.
        for (widgetId in currentStackInfo.widgetIds) {
            key(widgetId) {
                WidgetStackMemberRow(
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
            Spacer(modifier = Modifier.height(12.dp))
        }

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

        Spacer(modifier = Modifier.height(16.dp))

        // Add widget button
        Button(
            onClick = {
                // Close current dialog and show widget picker
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
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp),
        ) {
            Text(
                text = stringResource(R.string.add_widget_to_stack),
                style = MaterialTheme.typography.labelLarge,
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
 * Builds a [WidgetItem] for a bound workspace widget so [WidgetCell] can show the same preview
 * pipeline as [WidgetsFullSheet].
 *
 * Must run on [MODEL_EXECUTOR] — [WidgetItem]'s constructor uses [com.android.launcher3.icons.IconCache]
 * which asserts the model worker looper.
 */
private fun widgetItemForStackMember(launcher: Launcher, widgetId: Int): WidgetItem? {
    val wi = synchronized(launcher.model.bgDataModel) {
        launcher.model.bgDataModel.itemsIdMap
            .firstOrNull { it is LauncherAppWidgetInfo && it.appWidgetId == widgetId } as? LauncherAppWidgetInfo
    } ?: return null
    val helper = WidgetManagerHelper(launcher)
    val providerInfo = helper.getLauncherAppWidgetInfo(wi.appWidgetId, wi.providerName) ?: return null
    val idp = LauncherAppState.getIDP(launcher)
    val iconCache = LauncherAppState.getInstance(launcher).iconCache
    return WidgetItem(providerInfo, idp, iconCache, launcher, helper)
}

@Composable
private fun WidgetStackMemberRow(
    widgetId: Int,
    launcher: Launcher,
    canRemove: Boolean,
    onRemove: () -> Unit,
) {
    var widgetItem by remember(widgetId, launcher) {
        mutableStateOf<WidgetItem?>(null)
    }
    var loadFinished by remember(widgetId, launcher) {
        mutableStateOf(false)
    }
    LaunchedEffect(widgetId, launcher) {
        widgetItem = null
        loadFinished = false
        widgetItem = withContext(MODEL_EXECUTOR.asCoroutineDispatcher()) {
            widgetItemForStackMember(launcher, widgetId)
        }
        loadFinished = true
    }

    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        border = CardDefaults.outlinedCardBorder(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            // Do not pass WidgetItem? into key() — null hits ComponentKey.equals(null) and NPEs
            // (ComponentKey casts the argument without a null check).
            val loadedItem = widgetItem
            val previewKey = loadedItem?.hashCode() ?: 0
            key(widgetId, previewKey) {
                when {
                    loadedItem != null -> {
                        AndroidView(
                            factory = { _ ->
                                val cell = widgetCellLayoutInflater(launcher).inflate(
                                    R.layout.widget_cell,
                                    null,
                                    false,
                                ) as WidgetCell
                                cell.setSourceContainer(LauncherSettings.Favorites.CONTAINER_WIDGETS_TRAY)
                                cell.isClickable = false
                                cell.isFocusable = false
                                cell.isLongClickable = false
                                cell.applyFromCellItem(loadedItem)
                                cell.hideAddButton(false)
                                cell
                            },
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .heightIn(min = 200.dp, max = 280.dp),
                            update = { },
                        )
                    }

                    !loadFinished -> {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .heightIn(min = 140.dp, max = 200.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(28.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Text(
                                    text = stringResource(R.string.loading),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    else -> {
                        WidgetStackMemberFallback(
                            widgetId = widgetId,
                            launcher = launcher,
                            modifier = Modifier
                                .weight(1f)
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                        )
                    }
                }
            }

            if (canRemove) {
                IconButton(
                    onClick = onRemove,
                    modifier = Modifier.padding(top = 4.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = stringResource(R.string.remove_widget),
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }
    }
}

private data class StackMemberFallbackMeta(
    val label: String,
    val spanX: Int,
    val spanY: Int,
)

@Composable
private fun WidgetStackMemberFallback(
    widgetId: Int,
    launcher: Launcher,
    modifier: Modifier = Modifier,
) {
    val meta = remember(widgetId, launcher) {
        val wi = synchronized(launcher.model.bgDataModel) {
            launcher.model.bgDataModel.itemsIdMap
                .firstOrNull { it is LauncherAppWidgetInfo && it.appWidgetId == widgetId } as? LauncherAppWidgetInfo
        }
        if (wi != null) {
            val wmHelper = WidgetManagerHelper(launcher)
            val label = wmHelper.getLauncherAppWidgetInfo(widgetId, wi.providerName)?.label
                ?: wi.providerName?.className?.substringAfterLast('.')
                ?: "Widget"
            StackMemberFallbackMeta(
                label,
                wi.spanX.coerceAtLeast(1),
                wi.spanY.coerceAtLeast(1),
            )
        } else {
            val label = try {
                val awm = android.appwidget.AppWidgetManager.getInstance(launcher)
                awm.getAppWidgetInfo(widgetId)?.loadLabel(launcher.packageManager)
            } catch (_: Exception) {
                null
            } ?: "Widget"
            StackMemberFallbackMeta(label, 1, 1)
        }
    }

    Column(modifier = modifier) {
        Text(
            text = meta.label,
            style = MaterialTheme.typography.titleSmall,
        )
        Text(
            text = stringResource(R.string.widget_stack_member_id, widgetId) + " · " +
                stringResource(R.string.widget_dims_format, meta.spanX, meta.spanY),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
