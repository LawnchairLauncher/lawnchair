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
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.ModalBottomSheetLayout
import androidx.compose.material.ModalBottomSheetValue
import androidx.compose.material.rememberModalBottomSheetState
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import kotlinx.coroutines.launch

internal val LocalBottomSheetHandler = staticCompositionLocalOf { BottomSheetHandler() }

val bottomSheetHandler: BottomSheetHandler
    @Composable
    @ReadOnlyComposable
    get() = LocalBottomSheetHandler.current

@ExperimentalMaterialApi
@Composable
fun ProvideBottomSheetHandler(
    content: @Composable () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val bottomSheetState = rememberModalBottomSheetState(initialValue = ModalBottomSheetValue.Hidden)
    var bottomSheetContent by remember { mutableStateOf(emptyBottomSheetContent) }
    val bottomSheetHandler = BottomSheetHandler(
        show = { sheetContent ->
            bottomSheetContent = BottomSheetContent(content = sheetContent)
            coroutineScope.launch { bottomSheetState.show() }
        },
        hide = {
            coroutineScope.launch { bottomSheetState.hide() }
        }
    )

    ModalBottomSheetLayout(
        sheetContent = {
            val isSheetShown = bottomSheetState.isAnimationRunning || bottomSheetState.isVisible
            BackHandler(enabled = isSheetShown) {
                bottomSheetHandler.hide()
            }
            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
                bottomSheetContent.content()
            }
        },
        sheetState = bottomSheetState
    ) {
        CompositionLocalProvider(LocalBottomSheetHandler provides bottomSheetHandler) {
            content()
        }
    }
}

data class BottomSheetHandler(
    val show: (@Composable () -> Unit) -> Unit = {},
    val hide: () -> Unit = {}
)
