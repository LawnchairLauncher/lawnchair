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
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
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
@Immutable
data class PositionalListItem<T, K : Any>(
    val data: T,
    val id: K,
)

/**
 * This class handles the logic for reordering items in [PositionalList] component via drag-and-drop,
 * toggling items, between sections, and bulk operations like sorting or swapping categories.
 *
 * @param T The type of the actual data object.
 * @param K The type of the unique identifier for the item.
 * @param initialItems The initial list of items.
 * @param initialActiveCount The initial number of items at the start of the list considered "Enabled".
 * @param onOrderChange Callback triggered when the list order or the active count changes.
 * @param labelSelector Function used to retrieve a string label from the data for alphabetical sorting.
 * @param haptic Provider for haptic feedback during reorder operations.
 */
@Stable
class PositionalListState<T, K : Any>(
    initialItems: List<PositionalListItem<T, K>>,
    initialActiveCount: Int,
    internal val onOrderChange: (newList: List<PositionalListItem<T, K>>, newEnabledCount: Int) -> Unit,
    internal val labelSelector: (T) -> String,
    private val haptic: ReorderHapticFeedback,
) {
    /**
     * The flat list of items, partitioned by [activeCount] into enabled and disabled sections.
     */
    var items by mutableStateOf(initialItems)
        internal set

    /**
     * The number of [items] at the start of the list that are currently enabled.
     */
    var activeCount by mutableIntStateOf(initialActiveCount)
        internal set

    /**
     * Swaps the positions of the active and inactive sections.
     */
    fun swapCategories() {
        val newActive = items.drop(activeCount)
        val newInactive = items.take(activeCount)
        notifyChange(newActive + newInactive, newActive.size)
    }

    /**
     * Toggles between selecting all items and deselecting all items.
     */
    fun toggleSelectAll() {
        val allSelected = activeCount == items.size
        val newCount = if (allSelected) 0 else items.size
        notifyChange(items, newCount)
    }

    /**
     * Re-sorts the active section alphabetically while maintaining the inactive section's order.
     */
    fun sortActiveItems() {
        val active = items.take(activeCount).sortedBy { labelSelector(it.data) }
        val inactive = items.drop(activeCount)
        notifyChange(active + inactive, activeCount)
    }

    /**
     * Resets the list by deselecting all items.
     */
    fun reset() {
        notifyChange(items, 0)
    }

    /**
     * Moves the item at [index] up by one position.
     *
     * If an item moves from the start of the disabled section into the enabled section,
     * the [activeCount] is incremented.
     *
     * @return `true` if the move was performed, `false` if already at the top.
     */
    fun moveUp(index: Int): Boolean {
        if (index <= 0) return false
        val newList = items.toMutableList().apply { add(index - 1, removeAt(index)) }
        val newCount = if (index == activeCount) activeCount + 1 else activeCount
        notifyChange(newList, newCount)
        return true
    }

    /**
     * Moves the item at [index] down by one position.
     *
     * If an item moves from the end of the enabled section into the disabled section,
     * the [activeCount] is decremented.
     *
     * @return `true` if the move was performed, `false` if already at the bottom.
     */
    fun moveDown(index: Int): Boolean {
        if (index >= items.size - 1) return false
        val newList = items.toMutableList().apply { add(index + 1, removeAt(index)) }
        val newCount = if (index == activeCount - 1) activeCount - 1 else activeCount
        notifyChange(newList, newCount)
        return true
    }

    internal fun update(newList: List<PositionalListItem<T, K>>, newCount: Int) {
        items = newList
        activeCount = newCount
    }

    internal fun toggleItemStatus(itemId: K, makeActive: Boolean) {
        val currentIndex = items.indexOfFirst { it.id == itemId }
        if (currentIndex == -1) return

        val mutable = items.toMutableList()
        val item = mutable.removeAt(currentIndex)

        if (makeActive) {
            mutable.add(0, item)
            notifyChange(mutable, activeCount + 1)
        } else {
            mutable.add(item)
            val newActiveCount = activeCount - 1
            val result = sortInactiveItems(
                mutable,
                newActiveCount,
                labelSelector,
            )
            notifyChange(result, newActiveCount)
        }
    }

    internal fun reorder(fromKey: Any, toKey: Any): Boolean {
        // Early exits for safety
        if (fromKey == ReorderableKey.GHOST_ACTIVE || fromKey == ReorderableKey.GHOST_DISABLED) return false
        if (toKey == ReorderableKey.HEADER_ENABLED || toKey == ReorderableKey.HEADER_DISABLED) return false

        val fromUiIndex = itemIndices[fromKey] ?: return false
        val toUiIndex = itemIndices[toKey] ?: return false
        if (fromUiIndex == toUiIndex) return false

        // Map UI indices back to data indices correctly
        val fromDataIndex = if (activeCount == 0) {
            fromUiIndex - 1
        } else {
            fromUiIndex
        }

        val toDataIndex = when (toKey) {
            ReorderableKey.GHOST_ACTIVE -> 0

            ReorderableKey.GHOST_DISABLED -> items.size - 1

            else -> {
                if (activeCount == 0) (toUiIndex - 1) else toUiIndex
            }
        }.coerceIn(0, items.size - 1)

        // Perform move
        val newList = items.toMutableList().apply {
            if (fromDataIndex in indices) {
                add(toDataIndex, removeAt(fromDataIndex))
            }
        }

        // Boundary logic
        val uiThreshold = if (activeCount == 0) 1 else activeCount

        var newCount = activeCount
        if (uiThreshold in (fromUiIndex + 1)..toUiIndex) {
            newCount--
        } else if (uiThreshold in (toUiIndex + 1)..fromUiIndex) {
            newCount++
        }
        notifyChange(newList, newCount.coerceIn(0, newList.size))
        haptic.performHapticFeedback(ReorderHapticFeedbackType.MOVE)
        return true
    }

    private val itemIndices by derivedStateOf {
        buildMap {
            var uiIndex = 0
            if (activeCount == 0) put(ReorderableKey.GHOST_ACTIVE, uiIndex++)

            items.forEach { item ->
                put(item.id as Any, uiIndex++)
            }

            if (activeCount == items.size) {
                put(ReorderableKey.GHOST_DISABLED, uiIndex)
            }
        }
    }

    private fun notifyChange(newList: List<PositionalListItem<T, K>>, newCount: Int) {
        items = newList
        activeCount = newCount
        onOrderChange(newList, newCount)
    }

    companion object {
        /**
         * Converts raw data into a single list for the Reorderable UI.
         * @param allItems All available items
         * @param enabledIds IDs of items that are currently "Enabled"
         */
        fun <T, K : Any> prepareCategorizedItems(
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
        fun <T, K : Any> getEnabledKeys(
            uiItems: List<PositionalListItem<T, K>>,
            enabledCount: Int,
        ): List<K> {
            return uiItems.take(enabledCount).map { it.id }
        }

        /**
         * Re-sorts the disabled section alphabetically while maintaining the active section's order.
         */
        fun <T, K : Any> sortInactiveItems(
            items: List<PositionalListItem<T, K>>,
            activeCount: Int,
            labelSelector: (T) -> String,
        ): List<PositionalListItem<T, K>> {
            val active = items.take(activeCount)
            val inactive = items.drop(activeCount).sortedBy { labelSelector(it.data) }
            return active + inactive
        }
    }
}

/**
 * Creates and remembers a [PositionalListState] to manage the state of a reorderable list
 * with active and inactive sections.
 *
 * @param T The type of the data object.
 * @param K The type of the unique identifier for the item.
 * @param items The initial list of items to display.
 * @param activeCount The number of items starting from the top of the list that are considered active.
 * @param onOrderChange Callback invoked when the order or the active status of items changes.
 * @param labelSelector A function to extract a display string from the data, used for sorting.
 * @return A remembered [PositionalListState] instance.
 */
@Composable
fun <T, K : Any> rememberPositionalListState(
    items: List<PositionalListItem<T, K>>,
    activeCount: Int,
    onOrderChange: (newList: List<PositionalListItem<T, K>>, newEnabledCount: Int) -> Unit,
    labelSelector: (T) -> String,
): PositionalListState<T, K> {
    val haptic = rememberReorderHapticFeedback()
    val state = remember {
        PositionalListState(
            initialItems = items,
            initialActiveCount = activeCount,
            onOrderChange = onOrderChange,
            labelSelector = labelSelector,
            haptic = haptic,
        )
    }

    LaunchedEffect(items, activeCount) {
        state.update(items, activeCount)
    }

    return state
}

enum class ReorderableKey {
    GHOST_ACTIVE,
    GHOST_DISABLED,
    HEADER_ENABLED,
    HEADER_DISABLED,
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
 * Moving an item across this boundary updates activeCount and changes the item's status.
 *
 * @param state The state object managing the list's items and their enabled status.
 * @param itemContent The UI builder for an individual item, exposing a drag handle and status toggle.
 * @param contentPadding Padding applied to the underlying scrollable container.
 * @param modifier The modifier to be applied to the container.
 */
@Composable
fun <T, K : Any> PositionalList(
    state: PositionalListState<T, K>,
    itemContent: @Composable ReorderableCollectionItemScope.(
        item: T,
        dragHandle: @Composable () -> Unit,
        toggle: @Composable () -> Unit,
    ) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val lazyListState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        state.reorder(from.key, to.key)
    }

    PreferenceLazyColumn(
        state = lazyListState,
        contentPadding = contentPadding,
        modifier = modifier,
    ) {
        item(key = ReorderableKey.HEADER_ENABLED) {
            PreferenceGroupHeading(heading = stringResource(R.string.reorderable_active_items))
        }

        if (state.activeCount == 0) {
            item(key = ReorderableKey.GHOST_ACTIVE) {
                // We treat this hint as a REORDERABLE ITEM so it can be swapped with
                ReorderableItem(reorderableState, key = ReorderableKey.GHOST_ACTIVE) {
                    PreferenceTemplate(
                        title = { Text(text = stringResource(R.string.reorderable_add_hint_items)) },
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }
        }

        itemsIndexed(
            items = state.items,
            key = { _, item -> item.id as Any },
        ) { index, item ->
            val isActive = index < state.activeCount

            ExpandAndShrink(visible = index == state.activeCount) {
                PreferenceGroupHeading(
                    stringResource(R.string.reorderable_disabled_items),
                )
            }

            ReorderableItem(
                state = reorderableState,
                key = item.id,
                modifier = Modifier.semanticReorderActions(
                    index,
                    state,
                    stringResource(R.string.reorderable_move_up),
                    stringResource(R.string.reorderable_move_down),
                ),
            ) {
                ReorderableItemContainer(
                    item = item,
                    active = isActive,
                    onActiveChange = { makeActive ->
                        state.toggleItemStatus(item.id, makeActive)
                    },
                    isFirst = index == if (isActive) 0 else state.activeCount,
                    isLast = index == (if (isActive) state.activeCount else state.items.size) - 1,
                    isAnyDragging = reorderableState.isAnyItemDragging,
                    content = itemContent,
                )
            }
        }

        // Handle trailing header if all items are enabled
        if (state.activeCount == state.items.size) {
            item(key = ReorderableKey.GHOST_DISABLED) {
                ReorderableItem(reorderableState, key = ReorderableKey.GHOST_DISABLED) {
                    PreferenceGroup(heading = stringResource(R.string.reorderable_disabled_items)) {
                        PreferenceTemplate(title = { Text(text = stringResource(R.string.reorderable_disabled_hint)) })
                    }
                }
            }
        }
    }
}

private fun <T, K : Any> Modifier.semanticReorderActions(
    index: Int,
    state: PositionalListState<T, K>,
    moveUpLabel: String,
    moveDownLabel: String,
) = this.semantics {
    customActions = listOfNotNull(
        if (index > 0) {
            CustomAccessibilityAction(moveUpLabel) {
                state.moveUp(index)
            }
        } else {
            null
        },
        if (index < state.items.size - 1) {
            CustomAccessibilityAction(moveDownLabel) {
                state.moveDown(index)
            }
        } else {
            null
        },
    )
}

@Composable
private fun <T, K : Any> ReorderableCollectionItemScope.ReorderableItemContainer(
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
fun <T, K : Any> PositionalListOverflowMenu(
    state: PositionalListState<T, K>,
    modifier: Modifier = Modifier,
    extraItems: @Composable OverflowMenuScope.(hideMenu: () -> Unit) -> Unit = {},
) {
    OverflowMenu(modifier) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.inverse_selection)) },
            onClick = {
                state.swapCategories()
                hideMenu()
            },
        )

        val allSelected = state.activeCount == state.items.size
        DropdownMenuItem(
            text = {
                Text(stringResource(if (allSelected) R.string.deselect_all else R.string.select_all))
            },
            onClick = {
                state.toggleSelectAll()
                hideMenu()
            },
        )

        DropdownMenuItem(
            text = { Text(stringResource(R.string.sort_active_items_action)) },
            onClick = {
                state.sortActiveItems()
                hideMenu()
            },
        )

        extraItems(::hideMenu)

        PreferenceDivider(modifier = Modifier.padding(vertical = 8.dp))

        DropdownMenuItem(
            text = { Text(stringResource(R.string.action_reset)) },
            onClick = {
                state.reset()
                hideMenu()
            },
        )
    }
}
