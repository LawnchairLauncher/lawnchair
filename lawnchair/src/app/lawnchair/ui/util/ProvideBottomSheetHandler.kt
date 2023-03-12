/*
 * Copyright 2021, Lawnchair
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package app.lawnchair.ui.util

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.ModalBottomSheetLayout
import androidx.compose.material.ModalBottomSheetState
import androidx.compose.material.ModalBottomSheetValue
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlinx.coroutines.launch
import androidx.compose.material.MaterialTheme as Material2Theme

internal val LocalBottomSheetHandler = staticCompositionLocalOf { BottomSheetHandler() }

val bottomSheetHandler: BottomSheetHandler
    @Composable
    @ReadOnlyComposable
    get() = LocalBottomSheetHandler.current

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun ProvideBottomSheetHandler(
    content: @Composable () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var onDismiss by remember { mutableStateOf({}) }
    val bottomSheetState = remember {
        ModalBottomSheetState(
            initialValue = ModalBottomSheetValue.Hidden,
            confirmValueChange = {
                if (it == ModalBottomSheetValue.Hidden) onDismiss()
                true
            },
        )
    }
    var bottomSheetContent by remember { mutableStateOf(emptyBottomSheetContent) }
    val bottomSheetHandler = remember {
        BottomSheetHandler(
            show = { sheetContent ->
                bottomSheetContent = BottomSheetContent(content = sheetContent)
                if (bottomSheetState.isVisible.not()) coroutineScope.launch { bottomSheetState.show() }
            },
            hide = {
                onDismiss()
                coroutineScope.launch {
                    bottomSheetState.hide()
                }
            },
            onDismiss = {
                onDismiss = it
            },
        )
    }

    ModalBottomSheetLayout(
        sheetContent = {
            BackHandler(enabled = bottomSheetState.isVisible) {
                bottomSheetHandler.hide()
            }
            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
                StatusBarOffset {
                    bottomSheetContent.content()
                }
            }
        },
        sheetState = bottomSheetState,
        sheetShape = Material2Theme.shapes.large.copy(
            bottomStart = CornerSize(0.dp),
            bottomEnd = CornerSize(0.dp),
        ),
    ) {
        CompositionLocalProvider(LocalBottomSheetHandler provides bottomSheetHandler) {
            content()
        }
    }
}

class BottomSheetHandler(
    val show: (@Composable () -> Unit) -> Unit = {},
    val hide: () -> Unit = {},
    val onDismiss: (() -> Unit) -> Unit = {},
)

@Composable
fun StatusBarOffset(content: @Composable () -> Unit) {
    val statusBar = WindowInsets.statusBars.getTop(LocalDensity.current)
    val displayCutout = WindowInsets.displayCutout.getTop(LocalDensity.current)
    val statusBarHeight = max(statusBar, displayCutout)
    val topOffset = statusBarHeight + with(LocalDensity.current) { 8.dp.roundToPx() }

    Box(
        modifier = Modifier
            .layout { measurable, constraints ->
                val newConstraints = Constraints(
                    minWidth = constraints.minWidth,
                    maxWidth = constraints.maxWidth,
                    minHeight = constraints.minHeight,
                    maxHeight = when (constraints.maxHeight) {
                        Constraints.Infinity -> Constraints.Infinity
                        else -> constraints.maxHeight - topOffset
                    }
                )
                val placeable = measurable.measure(newConstraints)

                layout(placeable.width, placeable.height) {
                    placeable.placeRelative(0, 0)
                }
            }
    ) {
        content()
    }
}
