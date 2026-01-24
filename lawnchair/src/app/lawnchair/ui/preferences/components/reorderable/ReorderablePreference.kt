package app.lawnchair.ui.preferences.components.reorderable

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.lawnchair.ui.preferences.components.controls.ClickablePreference
import app.lawnchair.ui.preferences.components.layout.ExpandAndShrink
import app.lawnchair.ui.preferences.components.layout.PreferenceGroup
import app.lawnchair.ui.preferences.components.layout.PreferenceGroupHeading
import app.lawnchair.ui.theme.preferenceGroupColor
import com.android.launcher3.R
import com.android.launcher3.Utilities
import sh.calvin.reorderable.ReorderableColumn
import sh.calvin.reorderable.ReorderableListItemScope

@Composable
fun <T> ReorderablePreferenceGroup(
    label: String?,
    items: List<T>,
    defaultList: List<T>,
    onOrderChange: (List<T>) -> Unit,
    modifier: Modifier = Modifier,
    onSettle: ((List<T>) -> Unit)? = null,
    itemContent: @Composable ReorderableListItemScope.(
        item: T,
        index: Int,
        isDragging: Boolean,
        onDraggingChange: (Boolean) -> Unit,
    ) -> Unit,
) {
    var localItems by remember { mutableStateOf(items) }

    LaunchedEffect(items) {
        if (localItems != items) {
            localItems = items
        }
    }

    var isAnyDragging by remember { mutableStateOf(false) }

    LaunchedEffect(items) {
        if (localItems != items) {
            localItems = items
        }
    }

    val view = LocalView.current

    val color by animateColorAsState(
        targetValue = if (!isAnyDragging) preferenceGroupColor() else MaterialTheme.colorScheme.surface,
        label = "card background animation",
    )

    Column(modifier) {
        PreferenceGroupHeading(
            label,
        )
        Surface(
            modifier = Modifier.padding(horizontal = 16.dp),
            shape = MaterialTheme.shapes.large,
            color = color,
        ) {
            ReorderableColumn(
                list = localItems,
                onSettle = { fromIndex, toIndex ->
                    val newItems = localItems.toMutableList().apply {
                        add(toIndex, removeAt(fromIndex))
                    }.toList()
                    localItems = newItems
                    onOrderChange(newItems)
                    if (onSettle != null) {
                        onSettle(newItems)
                    }
                    isAnyDragging = false
                },
                onMove = {
                    isAnyDragging = true
                    if (Utilities.ATLEAST_U) {
                        view.performHapticFeedback(HapticFeedbackConstants.SEGMENT_FREQUENT_TICK)
                    }
                },
            ) { index, item, isDragging ->
                key(item.hashCode()) {
                    ReorderableItem {
                        Column {
                            ReorderablePreferenceItem(
                                isDragging = isDragging,
                                modifier = Modifier
                                    .a11yDrag(
                                        index = index,
                                        items = items,
                                        onMoveUp = {
                                            localItems = it
                                            onOrderChange(it)
                                            if (onSettle != null) {
                                                onSettle(it)
                                            }
                                        },
                                        onMoveDown = {
                                            localItems = it
                                            onOrderChange(it)
                                            if (onSettle != null) {
                                                onSettle(it)
                                            }
                                        },
                                    ),
                            ) {
                                itemContent(
                                    item,
                                    index,
                                    isDragging,
                                ) { isAnyDragging = it }
                            }

                            AnimatedVisibility(!isAnyDragging && index != localItems.lastIndex) {
                                HorizontalDivider(
                                    Modifier.padding(start = 50.dp, end = 16.dp),
                                )
                            }
                        }
                    }
                }
            }
        }

        ExpandAndShrink(visible = localItems != defaultList) {
            PreferenceGroup {
                Item {
                    ClickablePreference(label = stringResource(id = R.string.action_reset)) {
                        val resetList = defaultList
                        onOrderChange(resetList)
                        if (onSettle != null) {
                            onSettle(resetList)
                        }
                    }
                }
            }
        }
    }
}

fun <T> Modifier.a11yDrag(
    index: Int,
    items: List<T>,
    onMoveUp: (List<T>) -> Unit,
    onMoveDown: (List<T>) -> Unit,
) = this.semantics {
    customActions = listOf(
        CustomAccessibilityAction(
            label = "Move up",
            action = {
                if (index > 0) {
                    onMoveUp(
                        items
                            .toMutableList()
                            .apply {
                                add(index - 1, removeAt(index))
                            },
                    )
                    true
                } else {
                    false
                }
            },
        ),
        CustomAccessibilityAction(
            label = "Move down",
            action = {
                if (index < items.size - 1) {
                    onMoveDown(
                        items.toMutableList()
                            .apply {
                                add(index + 1, removeAt(index))
                            },
                    )
                    true
                } else {
                    false
                }
            },
        ),
    )
}
