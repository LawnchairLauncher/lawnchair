package app.lawnchair.ui.preferences.components.reorderable

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.lawnchair.ui.preferences.components.layout.PreferenceTemplate
import sh.calvin.reorderable.ReorderableCollectionItemScope
import sh.calvin.reorderable.ReorderableListItemScope

@Composable
fun ReorderablePreferenceItem(
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
fun ReorderableSwitchPreference(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    dragHandle: @Composable () -> Unit,
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
            dragHandle()
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
fun ReorderableDragHandle(
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    isDraggable: Boolean = true,
) {
    IconButton(
        modifier = modifier,
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

@Composable
fun ReorderableDragHandle(
    scope: ReorderableListItemScope,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    isDraggable: Boolean = true,
    onDragStart: () -> Unit = {},
    onDragStop: () -> Unit = {},
) {
    val haptic = rememberReorderHapticFeedback()

    ReorderableDragHandle(
        modifier = with(scope) {
            modifier.longPressDraggableHandle(
                interactionSource = interactionSource,
                onDragStarted = {
                    haptic.performHapticFeedback(ReorderHapticFeedbackType.START)
                    onDragStart()
                },
                onDragStopped = {
                    haptic.performHapticFeedback(ReorderHapticFeedbackType.END)
                    onDragStop()
                },
            )
        },
        interactionSource = interactionSource,
        isDraggable = isDraggable,
    )
}

@Composable
fun ReorderableDragHandle(
    scope: ReorderableCollectionItemScope,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    isDraggable: Boolean = true,
    onDragStart: () -> Unit = {},
    onDragStop: () -> Unit = {},
) {
    val haptic = rememberReorderHapticFeedback()

    ReorderableDragHandle(
        modifier = with(scope) {
            modifier.longPressDraggableHandle(
                interactionSource = interactionSource,
                onDragStarted = {
                    haptic.performHapticFeedback(ReorderHapticFeedbackType.START)
                    onDragStart()
                },
                onDragStopped = {
                    haptic.performHapticFeedback(ReorderHapticFeedbackType.END)
                    onDragStop()
                },
            )
        },
        interactionSource = interactionSource,
        isDraggable = isDraggable,
    )
}
