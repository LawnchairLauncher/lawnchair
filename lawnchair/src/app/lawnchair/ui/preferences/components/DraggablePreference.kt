package app.lawnchair.ui.preferences.components

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import app.lawnchair.ui.preferences.components.layout.PreferenceTemplate
import app.lawnchair.ui.theme.preferenceGroupColor
import app.lawnchair.ui.util.addIf
import com.android.launcher3.R
import com.android.launcher3.Utilities
import sh.calvin.reorderable.ReorderableColumn
import sh.calvin.reorderable.ReorderableScope

@Composable
fun <T> DraggablePreferenceGroup(
    label: String?,
    items: List<T>,
    defaultList: List<T>,
    onOrderChange: (List<T>) -> Unit,
    modifier: Modifier = Modifier,
    onSettle: ((List<T>) -> Unit)? = null,
    itemContent: @Composable ReorderableScope.(
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

    val color by animateColorAsState(
        targetValue = if (!isAnyDragging) preferenceGroupColor() else MaterialTheme.colorScheme.surface,
        label = "card background animation",
    )

    val view = LocalView.current

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
                modifier = Modifier,
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
                key(item) {
                    Column {
                        DraggablePreferenceContainer(
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
                            ) {
                                isAnyDragging = it
                            }
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

        ExpandAndShrink(visible = localItems != defaultList) {
            PreferenceGroup {
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

@Composable
fun DraggablePreferenceContainer(
    isDragging: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Card(
        elevation = if (isDragging) {
            CardDefaults.elevatedCardElevation()
        } else {
            CardDefaults.cardElevation(
                0.dp,
            )
        },
        colors = if (isDragging) {
            CardDefaults.elevatedCardColors()
        } else {
            CardDefaults.cardColors(
                Color.Transparent,
            )
        },
        modifier = modifier,
    ) {
        content()
    }
}

@Composable
fun DraggableSwitchPreference(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    dragIndicator: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    enabled: Boolean = true,
    description: String? = null,
) {
    PreferenceTemplate(
        modifier = modifier.clickable(
            enabled = enabled,
            onClick = {
                onCheckedChange(!checked)
            },
            interactionSource = interactionSource,
            indication = ripple(),
        ),
        contentModifier = Modifier
            .fillMaxHeight()
            .padding(vertical = 16.dp)
            .padding(start = 16.dp),
        title = { Text(text = label) },
        description = { description?.let { Text(text = it) } },
        startWidget = {
            dragIndicator()
        },
        endWidget = {
            Switch(
                modifier = Modifier
                    .padding(all = 16.dp)
                    .height(24.dp),
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
            )
        },
        enabled = enabled,
        applyPaddings = false,
    )
}

@Composable
fun DragHandle(
    scope: ReorderableScope,
    interactionSource: MutableInteractionSource,
    modifier: Modifier = Modifier,
    isDraggable: Boolean = true,
    onDragStop: () -> Unit = {},
) {
    val view = LocalView.current
    IconButton(
        modifier = with(scope) {
            modifier.addIf(isDraggable) {
                longPressDraggableHandle(
                    onDragStarted = {
                        if (Utilities.ATLEAST_U) {
                            view.performHapticFeedback(HapticFeedbackConstants.DRAG_START)
                        }
                    },
                    onDragStopped = {
                        if (Utilities.ATLEAST_R) {
                            view.performHapticFeedback(HapticFeedbackConstants.GESTURE_END)
                        }
                        onDragStop()
                    },
                )
            }
        },
        enabled = isDraggable,
        onClick = {},
        interactionSource = interactionSource,
    ) {
        Icon(
            imageVector = Icons.Rounded.DragHandle,
            contentDescription = "Drag indicator",
            modifier = Modifier.width(24.dp),
        )
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
