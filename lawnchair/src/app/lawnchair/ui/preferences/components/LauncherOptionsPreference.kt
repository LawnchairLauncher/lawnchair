package app.lawnchair.ui.preferences.components

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import app.lawnchair.preferences.getAdapter
import app.lawnchair.preferences2.preferenceManager2
import app.lawnchair.ui.ModalBottomSheetContent
import app.lawnchair.ui.popup.LauncherOptionPopupItem
import app.lawnchair.ui.popup.LauncherOptionsPopup
import app.lawnchair.ui.popup.toLauncherOptions
import app.lawnchair.ui.popup.toOptionOrderString
import app.lawnchair.ui.preferences.components.controls.ClickablePreference
import app.lawnchair.ui.util.addIf
import app.lawnchair.ui.util.bottomSheetHandler
import com.android.launcher3.R
import sh.calvin.reorderable.ReorderableColumn
import com.android.launcher3.Utilities

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.TextButton

@Composable
fun LauncherOptionsPreference(
    modifier: Modifier = Modifier,
) {
    val bottomSheetHandler = bottomSheetHandler
    ClickablePreference(
        modifier = modifier,
        label = "Edit pop-up menu",
        onClick = {
            bottomSheetHandler.show {
                LauncherOptionsPopupEditor {
                    bottomSheetHandler.hide()
                }
            }
        },
    )
}

@Composable
fun LauncherOptionsPopupEditor(
    onDismiss: () -> Unit,
) {
    val prefs2 = preferenceManager2()
    val optionsPref = prefs2.launcherPopupOrder.getAdapter()

    LauncherOptionsPopupEditor(
        list = optionsPref.state.value.toLauncherOptions(),
        onListChange = {
            optionsPref.onChange(it.toOptionOrderString())
        },
        onDismiss = onDismiss,
    )
}

@Composable
fun LauncherOptionsPopupEditor(
    list: List<LauncherOptionPopupItem>,
    onListChange: (List<LauncherOptionPopupItem>) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current
    var localList = list

    val prefs2 = preferenceManager2()
    val isHomeScreenLocked = prefs2.lockHomeScreen.getAdapter().state.value

    val showPreview = remember { mutableStateOf(false) }

    ModalBottomSheetContent(
        modifier = modifier,
        title = {
            Text("Pop-up menu")
        },
        buttons = {
            TextButton(
                onClick = {
                    onListChange(LauncherOptionsPopup.DEFAULT_ORDER.toLauncherOptions())
                    onDismiss()
                },
            ) {
                Text(
                    stringResource(id = R.string.action_reset),
                )
            }
            OutlinedButton(
                onClick = {
                    showPreview.value = !showPreview.value
                },
            ) {
                Text(
                    stringResource(id = R.string.preview_label),
                )
            }
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = onDismiss,
            ) {
                Text(
                    stringResource(id = R.string.action_apply),
                )
            }
        },
    ) {
        var isAnyDragging by remember { mutableStateOf(false) }

        ReorderableColumn(
            modifier = Modifier.verticalScroll(rememberScrollState()),
            list = localList,
            onSettle = { fromIndex, toIndex ->
                localList = localList.toMutableList().apply {
                    add(toIndex, removeAt(fromIndex))
                }.toList().also { newItems ->
                    onListChange(newItems)
                    isAnyDragging = false
                }
            },
            onMove = {
                isAnyDragging = true
                if (Utilities.ATLEAST_U) {
                    view.performHapticFeedback(HapticFeedbackConstants.SEGMENT_FREQUENT_TICK)
                }
            },
        ) { index, item, isDragging ->
            key(item) {
                val metadata = LauncherOptionsPopup.getMetadataForOption(item.identifier)

                val enabled = when (item.identifier) {
                    "edit_mode", "widgets" -> (!isHomeScreenLocked)
                    else -> true
                }

                OptionItemPreview(
                    buttonModifier = Modifier
                        .longPressDraggableHandle(
                            onDragStarted = {
                                view.performHapticFeedback(HapticFeedbackConstants.DRAG_START)
                            },
                            onDragStopped = {
                                view.performHapticFeedback(HapticFeedbackConstants.GESTURE_END)
                            },
                        )
                        .clearAndSetSemantics { },
                    checked = item.isEnabled,
                    onCheckedChange = {
                        localList[index].isEnabled = it
                        onListChange(localList)
                    },
                    label = stringResource(metadata.label),
                    description = if (!enabled) stringResource(R.string.home_screen_locked) else null,
                    icon = painterResource(metadata.icon),
                    isFirst = index == 0,
                    isLast = index == localList.lastIndex,
                    isDragging = isDragging,
                    enabled = enabled,
                    inPreview = showPreview.value,
                )
            }
        }
    }

}

@Composable
fun OptionItemPreview(
    enabled: Boolean = true,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    label: String,
    icon: Painter,
    isFirst: Boolean,
    isLast: Boolean,
    modifier: Modifier = Modifier,
    buttonModifier: Modifier = Modifier,
    description: String? = null,
    isDragging: Boolean = false,
    inPreview: Boolean,
) {
    val clipRadius = MaterialTheme.shapes.large
    val shape = if (isFirst) {
        RoundedCornerShape(
            clipRadius.topStart, clipRadius.topEnd, CornerSize(0), CornerSize(0),
        )
    } else if (isLast) {
        RoundedCornerShape(
            CornerSize(0), CornerSize(0), clipRadius.bottomStart, clipRadius.bottomEnd,
        )
    } else {
        RoundedCornerShape(0)
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth(),
    ) {
        if (!inPreview) {
            IconButton(
                modifier = buttonModifier,
                onClick = {},
            ) {
                Icon(
                    Icons.Rounded.DragHandle,
                    contentDescription = "Reorder",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(8.dp))
        }

        if (!inPreview) {
            Surface(
                shape = shape,
                modifier = Modifier
                    .addIf(!checked) {
                        alpha(0.38f)
                    }
                    .weight(0.8f),
                color = if (isDragging) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainer,
            ) {
                OptionsItemContent(icon, label, description)
            }
        } else {
            if (enabled) {
                Surface(
                    shape = shape,
                    modifier = Modifier
                        .width(360.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                ) {
                    OptionsItemContent(icon, label, null)
                }
            }
        }
        if (!inPreview) {
            Spacer(Modifier.width(16.dp))
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
            )
        }
    }
    if (!isLast) {
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun OptionsItemContent(
    icon: Painter,
    label: String,
    description: String?,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(16.dp),
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.width(16.dp))
        Column {
            Text(label)
            if (description != null) {
                CompositionLocalProvider(
                    LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant,
                    LocalTextStyle provides MaterialTheme.typography.bodyMedium,
                ) {
                    Text(
                        text = description,
                    )
                }
            }
        }
    }
}
