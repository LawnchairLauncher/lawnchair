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
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
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

/**
 * A wrapper class used by [PositionalList] to represent an item in a reorderable list.
 *
 * @param T The type of the actual data object.
 * @param K The type of the unique identifier for the item.
 * @property data The underlying data object being displayed.
 * @property id The unique key used to track this item's identity during reordering and animations.
 */
data class PositionalListItem<T, K>(
    val data: T,
    val id: K,
)

object PositionalListMapper {
    /**
     * Converts raw data into a single list for the Reorderable UI.
     * @param allItems All available items
     * @param enabledIds IDs of items that are currently "Enabled"
     */
    fun <T, K> prepareCategorizedItems(
        allItems: List<T>,
        enabledIds: List<K>,
        idSelector: (T) -> K,
    ): Pair<List<PositionalListItem<T, K>>, Int> {
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
    fun <T, K> getEnabledKeys(
        uiItems: List<PositionalListItem<T, K>>,
        enabledCount: Int,
    ): List<K> {
        return uiItems.take(enabledCount).map { it.id }
    }

    /**
     * Re-sorts the disabled section alphabetically while maintaining the active section's order.
     */
    fun <T, K> sortInactiveItems(
        items: List<PositionalListItem<T, K>>,
        activeCount: Int,
        labelSelector: (T) -> String,
    ): List<PositionalListItem<T, K>> {
        val active = items.take(activeCount)
        val inactive = items.drop(activeCount).sortedBy { labelSelector(it.data) }
        return active + inactive
    }

    /**
     * Re-sorts the active section alphabetically while maintaining the inactive section's order.
     */
    fun <T, K> sortActiveItems(
        items: List<PositionalListItem<T, K>>,
        activeCount: Int,
        labelSelector: (T) -> String,
    ): List<PositionalListItem<T, K>> {
        val active = items.take(activeCount).sortedBy { labelSelector(it.data) }
        val inactive = items.drop(activeCount)
        return active + inactive
    }

    /**
     * Swaps the positions of the active and inactive sections.
     */
    fun <T, K> swapCategories(
        items: List<PositionalListItem<T, K>>,
        activeCount: Int,
    ): Pair<List<PositionalListItem<T, K>>, Int> {
        val newActive = items.drop(activeCount)
        val newInactive = items.take(activeCount)
        return (newActive + newInactive) to newActive.size
    }

    /**
     * Toggles the status of an item between active (enabled) and inactive (disabled).
     *
     * When an item is made active, it is moved to the top of the active list (index 0).
     * When an item is made inactive, it is moved to the inactive section and the section is re-sorted
     * alphabetically using the provided [labelSelector].
     */
    internal fun <T, K> toggleItemStatus(
        items: List<PositionalListItem<T, K>>,
        activeCount: Int,
        itemId: K,
        makeActive: Boolean,
        labelSelector: (T) -> String,
    ): Pair<List<PositionalListItem<T, K>>, Int> {
        val currentIndex = items.indexOfFirst { it.id == itemId }
        if (currentIndex == -1) return items to activeCount

        val mutable = items.toMutableList()
        val item = mutable.removeAt(currentIndex)

        return if (makeActive) {
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

    /**
     * Calculates the mapping of item keys to their visual UI indices, including ghost/hint items.
     *
     * Ghost items are inserted when a section (active or disabled) is empty to provide
     * a visual drop target or hint.
     */
    internal fun <K> calculateIndices(
        items: List<PositionalListItem<*, K>>,
        activeCount: Int,
    ): Map<Any, Int> = buildMap {
        var uiIndex = 0
        if (activeCount == 0) put("ghost_active", uiIndex++)

        items.forEach { item ->
            put(item.id as Any, uiIndex++)
        }

        if (activeCount == items.size) {
            put("ghost_disabled", uiIndex)
        }
    }

    /**
     * Computes the new state of the list and active count after a reorder drag-and-drop event.
     *
     * This logic handles the transformation from UI-level indices (which may include ghost/header items)
     * back to data-level indices, and updates [activeCount] if an item is dragged across the
     * boundary between the active and inactive sections.
     *
     * @return A [Pair] containing the updated list and the new active count, or `null` if the move is invalid.
     */
    internal fun <T, K> calculateReorder(
        items: List<PositionalListItem<T, K>>,
        activeCount: Int,
        fromKey: Any,
        toKey: Any,
        itemIndices: Map<Any, Int>,
    ): Pair<List<PositionalListItem<T, K>>, Int>? {
        // 1. Safety early exits
        if (fromKey == "ghost_active" || fromKey == "ghost_disabled") return null
        if (toKey == "header_enabled" || toKey == "header_disabled") return null

        val fromUiIndex = itemIndices[fromKey] ?: return null
        val toUiIndex = itemIndices[toKey] ?: return null
        if (fromUiIndex == toUiIndex) return null

        // 2. Map UI indices back to Data indices correctly
        val fromDataIndex = if (activeCount == 0) {
            fromUiIndex - 1
        } else {
            fromUiIndex
        }

        val toDataIndex = when (toKey) {
            "ghost_active" -> 0
            "ghost_disabled" -> items.size - 1
            else -> {
                if (activeCount == 0) (toUiIndex - 1) else toUiIndex
            }
        }.coerceIn(0, items.size - 1)

        // 3. Perform the Move
        val newList = items.toMutableList().apply {
            if (fromDataIndex in indices) {
                add(toDataIndex, removeAt(fromDataIndex))
            }
        }

        // 4. Boundary Logic
        val uiThreshold = if (activeCount == 0) 1 else activeCount

        var newCount = activeCount
        if (uiThreshold in (fromUiIndex + 1)..toUiIndex) {
            newCount--
        } else if (uiThreshold in (toUiIndex + 1)..fromUiIndex) {
            newCount++
        }
        return newList to newCount.coerceIn(0, newList.size)
    }
}

object PositionalListDefaults {
    @Composable
    fun containerColor(isSelfDragging: Boolean, isAnyDragging: Boolean): Color {
        return when {
            isSelfDragging -> MaterialTheme.colorScheme.surfaceContainer
            isAnyDragging -> MaterialTheme.colorScheme.surface
            else -> preferenceGroupColor()
        }
    }

    fun containerShape(isFirst: Boolean, isLast: Boolean, isSelfDragging: Boolean): Shape {
        val top = if (!isFirst) 0.dp else 12.dp
        val bottom = if (!isLast) 0.dp else 12.dp
        return if (isSelfDragging) RoundedCornerShape(12.dp) else RoundedCornerShape(top, top, bottom, bottom)
    }
}


/**
 * A UI component for managing a list of items toggled between "Enabled" and "Disabled" states.
 * Uses a single flat list to prevent animation jumping during state transitions.
 *
 * - Indices `< activeCount` represent "Enabled" items.
 * - Indices `>= activeCount` represent "Disabled" items.
 *
 * Moving an item across this boundary updates [activeCount] and changes the item's status.
 *
 * @param items The flattened list of items, with enabled items positioned first.
 * @param activeCount The number of enabled items at the start of the list.
 * @param onOrderChange Callback triggered on item move or toggle. Returns the updated list and active count.
 * @param itemContent The UI builder for an individual item, exposing a drag handle and status toggle.
 * @param labelSelector Property selector used to sort the inactive section alphabetically.
 * @param contentPadding Padding applied to the underlying scrollable container.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun <T, K> PositionalList(
    items: List<PositionalListItem<T, K>>,
    activeCount: Int,
    onOrderChange: (newList: List<PositionalListItem<T, K>>, newEnabledCount: Int) -> Unit,
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
            PositionalListMapper.calculateIndices(localItems, localActiveCount)
        }
    }

    LaunchedEffect(items, activeCount) {
        localItems = items
        localActiveCount = activeCount
    }

    val lazyListState = rememberLazyListState()
    val haptic = rememberReorderHapticFeedback()

    val updateState: (List<PositionalListItem<T, K>>, Int) -> Unit = { list, count ->
        localItems = list
        localActiveCount = count
        onOrderChange(list, count)
        haptic.performHapticFeedback(ReorderHapticFeedbackType.MOVE)
    }

    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        PositionalListMapper.calculateReorder(
            localItems,
            localActiveCount,
            from.key,
            to.key,
            itemIndices,
        )?.let { (newList, newCount) ->
            updateState(newList, newCount)
        }
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
            key = { _, item -> item.id as Any },
        ) { index, item ->
            val isActive = index < localActiveCount

            ExpandAndShrink(visible = index == localActiveCount) {
                PreferenceGroupHeading(
                    stringResource(R.string.reorderable_disabled_items),
                )
            }

            ReorderableItem(
                state = reorderableState,
                key = item.id as Any,
                modifier = Modifier.semanticReorderActions(index, localItems, localActiveCount, onOrderChange),
            ) {
                ReorderableItemContainer(
                    item = item,
                    active = isActive,
                    onActiveChange = { makeActive ->
                        val (newList, newCount) = PositionalListMapper.toggleItemStatus(
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

        // Handle trailing header if all items are enabled
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

private fun <T, K> Modifier.semanticReorderActions(
    index: Int,
    items: List<PositionalListItem<T, K>>,
    activeCount: Int,
    onUpdate: (newList: List<PositionalListItem<T, K>>, newEnabledCount: Int) -> Unit,
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
private fun <T, K> ReorderableCollectionItemScope.ReorderableItemContainer(
    item: PositionalListItem<T, K>,
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

    val shape = PositionalListDefaults.containerShape(isFirst, isLast, isSelfDragging)
    val color by animateColorAsState(
        PositionalListDefaults.containerColor(isSelfDragging, isAnyDragging),
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
fun <T, K> PositionalListOveflowMenu(
    items: List<PositionalListItem<T, K>>,
    activeCount: Int,
    onUpdate: (newList: List<PositionalListItem<T, K>>, newCount: Int) -> Unit,
    labelSelector: (T) -> String,
    modifier: Modifier = Modifier,
    extraItems: @Composable OverflowMenuScope.(hideMenu: () -> Unit) -> Unit = {},
) {
    OverflowMenu(modifier) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.inverse_selection)) },
            onClick = {
                val (newList, newCount) = PositionalListMapper.swapCategories(items, activeCount)
                onUpdate(newList, newCount)
                hideMenu()
            },
        )

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

        DropdownMenuItem(
            text = { Text(stringResource(R.string.sort_active_items_action)) },
            onClick = {
                val newList = PositionalListMapper.sortActiveItems(items, activeCount, labelSelector)
                onUpdate(newList, activeCount)
                hideMenu()
            },
        )

        extraItems(::hideMenu)

        PreferenceDivider(modifier = Modifier.padding(vertical = 8.dp))

        DropdownMenuItem(
            text = { Text(stringResource(R.string.action_reset)) },
            onClick = {
                onUpdate(items, 0)
                hideMenu()
            },
        )
    }
}
