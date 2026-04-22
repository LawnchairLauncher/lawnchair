package app.lawnchair.ui.preferences.components.reorderable

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.lawnchair.ui.OverflowMenu
import app.lawnchair.ui.OverflowMenuScope
import app.lawnchair.ui.preferences.components.layout.ExpandAndShrink
import app.lawnchair.ui.preferences.components.layout.PreferenceDivider
import app.lawnchair.ui.preferences.components.layout.PreferenceGroup
import app.lawnchair.ui.preferences.components.layout.PreferenceGroupHeading
import app.lawnchair.ui.preferences.components.layout.PreferenceLazyColumn
import app.lawnchair.ui.preferences.components.layout.PreferenceTemplate
import app.lawnchair.ui.theme.preferenceGroupColor
import com.android.launcher3.R
import sh.calvin.reorderable.ReorderableCollectionItemScope
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

data class PositionalListItem<T>(
    val data: T,
    val id: String,
)

object PositionalMapper {
    /**
     * Converts raw data into a single list for the Reorderable UI.
     * @param allItems All available items (e.g., all apps)
     * @param enabledIds IDs of items that are currently "Enabled"
     */
    fun <T> prepareCategorizedItems(
        allItems: List<T>,
        enabledIds: List<String>,
        idSelector: (T) -> String,
    ): Pair<List<PositionalListItem<T>>, Int> {
        val enabledItems = allItems.filter { idSelector(it) in enabledIds }
            // Ensure enabled items follow the order defined in enabledIds
            .sortedBy { enabledIds.indexOf(idSelector(it)) }
            .map { PositionalListItem(it, idSelector(it)) }

        val disabledItems = allItems.filter { idSelector(it) !in enabledIds }
            .map { PositionalListItem(it, idSelector(it)) }

        return (enabledItems + disabledItems) to enabledItems.size
    }

    /**
     * Converts the UI state back to the enabled IDs list for saving.
     */
    fun <T> getEnabledKeys(
        uiItems: List<PositionalListItem<T>>,
        enabledCount: Int,
    ): List<String> {
        return uiItems.take(enabledCount).map { it.id }
    }

    /**
     * Re-sorts the disabled section alphabetically while maintaining the active section's order.
     */
    fun <T> sortInactiveItems(
        items: List<PositionalListItem<T>>,
        activeCount: Int,
        labelSelector: (T) -> String,
    ): List<PositionalListItem<T>> {
        val active = items.take(activeCount)
        val inactive = items.drop(activeCount).sortedBy { labelSelector(it.data) }
        return active + inactive
    }

    fun <T> swapCategories(
        items: List<PositionalListItem<T>>,
        activeCount: Int,
    ): Pair<List<PositionalListItem<T>>, Int> {
        val newActive = items.drop(activeCount)
        val newInactive = items.take(activeCount)
        return (newActive + newInactive) to newActive.size
    }

    fun <T> toggleItemStatus(
        items: List<PositionalListItem<T>>,
        activeCount: Int,
        itemId: String,
        makeActive: Boolean,
        labelSelector: (T) -> String,
    ): Pair<List<PositionalListItem<T>>, Int> {
        val currentIndex = items.indexOfFirst { it.id == itemId }
        if (currentIndex == -1) return items to activeCount

        val mutable = items.toMutableList()
        val item = mutable.removeAt(currentIndex)

        return if (makeActive) {
            // Move to the very top of the active list
            mutable.add(0, item)
            mutable to activeCount + 1
        } else {
            mutable.add(item)
            val newActiveCount = activeCount - 1
            val result = sortInactiveItems(
                mutable,
                newActiveCount,
                labelSelector,
            )
            result to newActiveCount
        }
    }
}

/**
 * A UI component for managing a list where items can be toggled between "Enabled" and "Disabled"
 * and reordered via drag-and-drop within and across those categories.
 *
 * Instead of using two separate lists (which causes "jumping" animations when moving items),
 * this component treats all items as a single flat list. A "divider" is virtually placed
 * at the index defined by [activeCount].
 * - Indices `< activeCount`: "Enabled" items (e.g., Apps in folder).
 * - Indices `>= activeCount`: "Disabled" items (e.g., Other apps).
 *
 * Moving an item across this boundary automatically increments or decrements the [activeCount],
 * effectively changing the item's status while maintaining a smooth animation.
 *
 * @param T The type of data being displayed.
 * @param items The flattened list of items, where enabled items must come first.
 * @param activeCount The number of items at the start of the list that are considered "active/enabled".
 * @param onOrderChange Callback triggered when an item is moved or toggled. Provides the new
 * full list and the updated count of active items.
 * @param itemContent The UI for an individual item. Provides a `dragHandle` for reordering
 * and a `toggle` for switching status without dragging.
 * @param labelSelector Used to sort the inactive section alphabetically when an item is disabled.
 * @param contentPadding Padding applied to the inner [PreferenceLazyColumn].
 */
@Composable
fun <T> PositionalReorderer(
    items: List<PositionalListItem<T>>,
    activeCount: Int,
    onOrderChange: (newList: List<PositionalListItem<T>>, newEnabledCount: Int) -> Unit,
    itemContent: @Composable ReorderableCollectionItemScope.(
        item: T,
        dragHandle: @Composable () -> Unit,
        toggle: @Composable () -> Unit,
    ) -> Unit,
    labelSelector: (T) -> String,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    var localItems by remember { mutableStateOf(items) }
    var localActiveCount by remember { mutableIntStateOf(activeCount) }

    val itemIndices by remember {
        derivedStateOf {
            buildMap {
                var uiIndex = 0
                if (localActiveCount == 0) put("ghost_active", uiIndex++)

                localItems.forEachIndexed { _, item ->
                    put(item.id, uiIndex++)
                }

                if (localActiveCount == localItems.size) {
                    put("ghost_disabled", uiIndex)
                }
            }
        }
    }

    LaunchedEffect(items, activeCount) {
        localItems = items
        localActiveCount = activeCount
    }

    val lazyListState = rememberLazyListState()
    val haptic = rememberReorderHapticFeedback()

    val updateState: (List<PositionalListItem<T>>, Int) -> Unit = { list, count ->
        localItems = list
        localActiveCount = count
        onOrderChange(list, count)
        haptic.performHapticFeedback(ReorderHapticFeedbackType.MOVE)
    }

    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        val fromKey = from.key as String
        val toKey = to.key as String

        // 1. Safety early exits
        if (fromKey == "ghost_active" || fromKey == "ghost_disabled") return@rememberReorderableLazyListState
        if (toKey == "header_enabled" || toKey == "header_disabled") return@rememberReorderableLazyListState

        val fromUiIndex = itemIndices[fromKey] ?: return@rememberReorderableLazyListState
        val toUiIndex = itemIndices[toKey] ?: return@rememberReorderableLazyListState
        if (fromUiIndex == toUiIndex) return@rememberReorderableLazyListState

        // 2. Map UI indices back to Data indices correctly
        // We subtract 1 ONLY if the item we are looking at is AFTER the active ghost
        val fromDataIndex = if (localActiveCount == 0) {
            fromUiIndex - 1
        } else {
            fromUiIndex
        }

        val toDataIndex = when (toKey) {
            "ghost_active" -> 0

            "ghost_disabled" -> localItems.size - 1

            else -> {
                // If there's an active ghost at the start, all real items are shifted by 1
                if (localActiveCount == 0) (toUiIndex - 1) else toUiIndex
            }
        }.coerceIn(0, localItems.size - 1)

        // 3. Perform the Move
        val newList = localItems.toMutableList().apply {
            if (fromDataIndex in indices) {
                add(toDataIndex, removeAt(fromDataIndex))
            }
        }

        // 4. Boundary Logic
        // The threshold is the physical boundary in the UI list
        val uiThreshold = if (localActiveCount == 0) 1 else localActiveCount

        var newCount = localActiveCount
        if (uiThreshold in (fromUiIndex + 1)..toUiIndex) {
            newCount--
        } else if (uiThreshold in (toUiIndex + 1)..fromUiIndex) {
            newCount++
        }

        updateState(newList, newCount.coerceIn(0, newList.size))
    }

    PreferenceLazyColumn(
        state = lazyListState,
        contentPadding = contentPadding,
        modifier = modifier,
    ) {
        item(key = "header_enabled") {
            PreferenceGroupHeading(heading = stringResource(R.string.reorderable_active_items))
        }

        if (localActiveCount == 0) {
            item(key = "ghost_active") {
                // We treat this hint as a REORDERABLE ITEM so it can be swapped with
                ReorderableItem(reorderableState, key = "ghost_active") {
                    PreferenceTemplate(
                        title = { Text(text = stringResource(R.string.reorderable_add_hint_items)) },
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }
        }

        itemsIndexed(
            items = localItems,
            key = { _, item -> item.id },
        ) { index, item ->
            val isActive = index < localActiveCount

            ExpandAndShrink(visible = index == localActiveCount) {
                PreferenceGroupHeading(
                    stringResource(R.string.reorderable_disabled_items),
                )
            }

            ReorderableItem(
                state = reorderableState,
                key = item.id,
                modifier = Modifier.semanticReorderActions(index, localItems, localActiveCount, onOrderChange),
            ) {
                ReorderableItemContainer(
                    item = item,
                    active = isActive,
                    onActiveChange = { makeActive ->
                        val (newList, newCount) = PositionalMapper.toggleItemStatus(
                            localItems,
                            localActiveCount,
                            item.id,
                            makeActive,
                            labelSelector,
                        )
                        onOrderChange(newList, newCount)
                    },
                    isFirst = index == if (isActive) 0 else localActiveCount,
                    isLast = index == (if (isActive) localActiveCount else localItems.size) - 1,
                    isAnyDragging = reorderableState.isAnyItemDragging,
                    content = itemContent,
                )
            }
        }

        // Handle trailing header if all apps are enabled
        if (localActiveCount == localItems.size) {
            item(key = "ghost_disabled") {
                ReorderableItem(reorderableState, key = "ghost_disabled") {
                    PreferenceGroup(heading = stringResource(R.string.reorderable_disabled_items)) {
                        PreferenceTemplate(title = { Text(text = stringResource(R.string.reorderable_disabled_hint)) })
                    }
                }
            }
        }
    }
}

private fun <T> Modifier.semanticReorderActions(
    index: Int,
    items: List<PositionalListItem<T>>,
    activeCount: Int,
    onUpdate: (newList: List<PositionalListItem<T>>, newEnabledCount: Int) -> Unit,
) = this.semantics {
    customActions = listOfNotNull(
        if (index > 0) {
            CustomAccessibilityAction("Move up") {
                val newList =
                    items.toMutableList().apply { add(index - 1, removeAt(index)) }
                // If it was the first disabled item moving up, it becomes enabled
                val newCount = if (index == activeCount) activeCount + 1 else activeCount
                onUpdate(newList, newCount)
                true
            }
        } else {
            null
        },
        if (index < items.size - 1) {
            CustomAccessibilityAction("Move down") {
                val newList =
                    items.toMutableList().apply { add(index + 1, removeAt(index)) }
                // If it was the last enabled item moving down, it becomes disabled
                val newCount = if (index == activeCount - 1) activeCount - 1 else activeCount
                onUpdate(newList, newCount)
                true
            }
        } else {
            null
        },
    )
}

@Composable
private fun <T> ReorderableCollectionItemScope.ReorderableItemContainer(
    item: PositionalListItem<T>,
    active: Boolean,
    onActiveChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    isFirst: Boolean = true,
    isLast: Boolean = true,
    isAnyDragging: Boolean = false,
    content: @Composable ReorderableCollectionItemScope.(
        item: T,
        dragHandle: @Composable () -> Unit,
        toggle: @Composable () -> Unit,
    ) -> Unit,
) {
    var isSelfDragging by remember { mutableStateOf(false) }

    val shape = remember(isFirst, isLast) {
        val top = if (!isFirst) 0.dp else 12.dp
        val bottom = if (!isLast) 0.dp else 12.dp
        if (isSelfDragging) RoundedCornerShape(12.dp) else RoundedCornerShape(top, top, bottom, bottom)
    }

    val color by animateColorAsState(
        when {
            isSelfDragging -> MaterialTheme.colorScheme.surfaceContainer
            isAnyDragging -> MaterialTheme.colorScheme.surface
            else -> preferenceGroupColor()
        },
    )

    Surface(
        color = color,
        modifier = modifier
            .padding(horizontal = 16.dp)
            .clip(shape),
    ) {
        Column {
            content(
                item.data,
                {
                    ReorderableDragHandle(
                        scope = this@ReorderableItemContainer,
                        onDragStart = { isSelfDragging = true },
                        onDragStop = { isSelfDragging = false },
                    )
                },
                {
                    IconButton(
                        onClick = { onActiveChange(!active) },
                    ) {
                        Icon(
                            imageVector = if (active) Icons.Rounded.Remove else Icons.Rounded.Add,
                            contentDescription = stringResource(if (active) R.string.dialog_remove else R.string.add_label),
                        )
                    }
                },
            )
            if (!isSelfDragging && !isLast) PreferenceDivider(startIndent = 40.dp)
        }
    }
}

@Composable
fun <T> PositionalOrderMenu(
    items: List<PositionalListItem<T>>,
    activeCount: Int,
    onUpdate: (newList: List<PositionalListItem<T>>, newCount: Int) -> Unit,
    modifier: Modifier = Modifier,
    additionalContent: @Composable OverflowMenuScope.(hideMenu: () -> Unit) -> Unit = {},
) {
    OverflowMenu(modifier) {
        // Inverse Selection
        DropdownMenuItem(
            text = { Text(stringResource(R.string.inverse_selection)) },
            onClick = {
                val (newList, newCount) = PositionalMapper.swapCategories(items, activeCount)
                onUpdate(newList, newCount)
                hideMenu()
            },
        )

        // Select / Deselect All
        val allSelected = activeCount == items.size
        DropdownMenuItem(
            text = {
                Text(stringResource(if (allSelected) R.string.deselect_all else R.string.select_all))
            },
            onClick = {
                val newCount = if (allSelected) 0 else items.size
                onUpdate(items, newCount)
                hideMenu()
            },
        )

        // Custom Slots (e.g., Filter Duplicates toggle)
        additionalContent(::hideMenu)

        PreferenceDivider(modifier = Modifier.padding(vertical = 8.dp))

        // Reset (Same as Deselect All in this context)
        DropdownMenuItem(
            text = { Text(stringResource(R.string.action_reset)) },
            onClick = {
                onUpdate(items, 0)
                hideMenu()
            },
        )
    }
}
