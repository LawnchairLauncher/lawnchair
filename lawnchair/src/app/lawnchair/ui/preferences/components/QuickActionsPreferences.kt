package app.lawnchair.ui.preferences.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.lawnchair.preferences.PreferenceAdapter
import app.lawnchair.ui.preferences.components.reorderable.ReorderableDragHandle
import app.lawnchair.ui.preferences.components.reorderable.ReorderablePreferenceGroup
import app.lawnchair.ui.preferences.components.reorderable.ReorderableSwitchPreference
import com.android.launcher3.R

data class RecentsQuickAction(
    val id: Int,
    val label: String,
    val adapter: PreferenceAdapter<Boolean>,
    val description: String? = null,
)

fun sortListByIdOrder(list: List<RecentsQuickAction>, order: String): List<RecentsQuickAction> {
    val orderList = order.split(",").map { it.toInt() }
    return list.sortedBy { orderList.indexOf(it.id) }
}

private const val DEFAULT_ORDER = "0,1,2,3,4"

@Composable
fun QuickActionsPreferences(
    adapter: PreferenceAdapter<String>,
    items: List<RecentsQuickAction>,
    modifier: Modifier = Modifier,
) {
    QuickActionsPreferences(
        order = adapter.state.value,
        onOrderChange = adapter::onChange,
        items = items,
        modifier = modifier,
    )
}

@Composable
fun QuickActionsPreferences(
    order: String,
    onOrderChange: (String) -> Unit,
    items: List<RecentsQuickAction>,
    modifier: Modifier = Modifier,
) {
    val orderedItems = sortListByIdOrder(items, order)

    // TODO migrate from index-based to item (class)-based list sorting
    ReorderablePreferenceGroup(
        label = stringResource(id = R.string.recents_actions_label),
        items = orderedItems,
        defaultList = sortListByIdOrder(items, DEFAULT_ORDER),
        onOrderChange = { newList ->
            onOrderChange(
                newList.map { it.id }.joinToString(separator = ","),
            )
        },
        modifier = modifier,
    ) { item, _, _, onDraggingChange ->
        val interactionSource = remember { MutableInteractionSource() }
        val scope = this

        ReorderableSwitchPreference(
            checked = item.adapter.state.value,
            onCheckedChange = item.adapter::onChange,
            label = item.label,
            description = item.description,
            interactionSource = interactionSource,
            dragHandle = {
                ReorderableDragHandle(
                    interactionSource = interactionSource,
                    scope = scope,
                    onDragStop = {
                        onDraggingChange(false)
                    },
                )
            },
        )
    }
}
