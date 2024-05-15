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

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

internal val LocalBottomSheetHandler = staticCompositionLocalOf { BottomSheetHandler() }

val bottomSheetHandler: BottomSheetHandler
    @Composable
    @ReadOnlyComposable
    get() = LocalBottomSheetHandler.current

/**
 * Provides the handler for managing the bottom sheets in preferences.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProvideBottomSheetHandler(
    content: @Composable () -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()
    var onDismiss by remember { mutableStateOf({}) }
    val bottomSheetState = rememberModalBottomSheetState(
        confirmValueChange = {
            if (it == SheetValue.Hidden) onDismiss()
            true
        },
    )
    var bottomSheetContent by remember { mutableStateOf(emptyBottomSheetContent) }
    var showBottomSheet by remember { mutableStateOf(false) }
    val bottomSheetHandler = remember {
        BottomSheetHandler(
            show = { sheetContent ->
                showBottomSheet = true
                bottomSheetContent = BottomSheetContent(content = sheetContent)
            },
            hide = {
                coroutineScope.launch { bottomSheetState.hide() }.invokeOnCompletion {
                    if (!bottomSheetState.isVisible) {
                        showBottomSheet = false
                    }
                }
            },
        ) {
            onDismiss = it
        }
    }

    CompositionLocalProvider(LocalBottomSheetHandler provides bottomSheetHandler) {
        content()

        val windowInsets = if (bottomSheetState.isVisible) WindowInsets.navigationBars else WindowInsets(0.dp)

        if (showBottomSheet) {
            ModalBottomSheet(
                sheetState = bottomSheetState,
                onDismissRequest = {
                    showBottomSheet = false
                },
                contentWindowInsets = {
                    windowInsets
                },
            ) {
                bottomSheetContent.content()
            }
        }
    }
}

class BottomSheetHandler(
    val show: (@Composable () -> Unit) -> Unit = {},
    val hide: () -> Unit = {},
    val onDismiss: (() -> Unit) -> Unit = {},
)
