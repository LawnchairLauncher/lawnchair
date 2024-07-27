package app.lawnchair.ui.preferences.components.layout

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.lawnchair.preferences.BasePreferenceManager
import kotlin.math.roundToInt
import kotlinx.coroutines.runBlocking

@Composable
fun DraggablePreferenceGroup(
    pref: BasePreferenceManager.StringPref,
    items: List<@Composable () -> Unit>,
    modifier: Modifier = Modifier,
    heading: String? = null,
    description: String? = null,
    showDescription: Boolean = true,
    showDividers: Boolean = true,
    dividerStartIndent: Dp = 16.dp,
    dividerEndIndent: Dp = 16.dp,
) {
    var order by remember {
        mutableStateOf(
            pref.get().split(",").map { it.toInt() }
                ?: items.indices.toList(),
        )
    }
    var draggingItemIndex by remember { mutableStateOf<Int?>(null) }
    var draggingOffset by remember { mutableFloatStateOf(0f) }

    fun saveOrder(order: List<Int>) {
        runBlocking {
            pref.set(order.joinToString(","))
        }
    }

    Column(
        modifier = modifier,
    ) {
        PreferenceGroupHeading(heading)
        Surface(
            modifier = Modifier.padding(horizontal = 16.dp),
            shape = MaterialTheme.shapes.large,
            tonalElevation = 1.dp,
        ) {
            if (showDividers) {
                Column {
                    order.forEachIndexed { index, itemIndex ->
                        val isDragging = draggingItemIndex == index
                        Box(
                            modifier = Modifier
                                .offset { IntOffset(0, if (isDragging) draggingOffset.roundToInt() else 0) }
                                .draggable(
                                    state = rememberDraggableState { delta ->
                                        if (isDragging) {
                                            draggingOffset += delta
                                        }
                                    },
                                    orientation = Orientation.Vertical,
                                    onDragStarted = {
                                        draggingItemIndex = index
                                        draggingOffset = 0f
                                    },
                                    onDragStopped = {
                                        draggingItemIndex?.let {
                                            val newIndex = (index + (draggingOffset / 100).roundToInt()).coerceIn(0, order.lastIndex)
                                            order = order.toMutableList().apply {
                                                add(newIndex, removeAt(it))
                                            }
                                            saveOrder(order)
                                        }
                                        draggingItemIndex = null
                                        draggingOffset = 0f
                                    },
                                ),
                        ) {
                            items[itemIndex]()
                        }
                        if (index != order.lastIndex) {
                            Divider(
                                Modifier
                                    .padding(start = dividerStartIndent, end = dividerEndIndent),
                            )
                        }
                    }
                }
            } else {
                Column {
                    items.forEach { item ->
                        Box {
                            item()
                        }
                    }
                }
            }
        }
        PreferenceGroupDescription(description = description, showDescription = showDescription)
    }
}
