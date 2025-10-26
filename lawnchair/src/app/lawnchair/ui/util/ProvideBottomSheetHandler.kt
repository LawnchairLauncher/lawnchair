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

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
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
    modifier: Modifier = Modifier,
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
        val windowInsets = if (bottomSheetState.isVisible) WindowInsets.navigationBars else WindowInsets(0.dp)

        var maxOffsetPx by remember(showBottomSheet) { mutableStateOf<Float?>(null) }

        // Live Edit doesn't like that we call requireOffset(), rebuild it instead of update.
        val currentOffsetPx = try {
            bottomSheetState.requireOffset()
        } catch (_: IllegalStateException) {
            null
        }
        if (showBottomSheet && currentOffsetPx != null) {
            maxOffsetPx = maxOf(maxOffsetPx ?: 0f, currentOffsetPx)
        }

        val rawFraction = when {
            !showBottomSheet -> 0f
            currentOffsetPx == null || maxOffsetPx == null || maxOffsetPx == 0f -> 1f
            else -> 1f - (currentOffsetPx / maxOffsetPx!!)
        }.coerceIn(0f, 1f)

        val animatedFraction by animateFloatAsState(
            targetValue = rawFraction,
            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
            label = "BottomSheetBlurFraction",
        )

        // See R.dimen.max_depth_blur_radius_enhanced
        val blur = (34f * animatedFraction).dp
        val scrimAlpha = 0.32f * animatedFraction

        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .blur(blur),
            ) {
                content()
            }

            if (showBottomSheet) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = scrimAlpha)),
                )
            }
        }

        if (showBottomSheet) {
            ModalBottomSheet(
                sheetState = bottomSheetState,
                onDismissRequest = {
                    showBottomSheet = false
                },
                contentWindowInsets = {
                    windowInsets
                },
                // We render our own scrim to control the scrim's blur and alpha
                scrimColor = Color.Transparent,
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
