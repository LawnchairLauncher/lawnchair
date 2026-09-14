/*
 * Copyright 2026, Renns Project
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

package app.lawnchair.ui.liquid

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.rememberCanvasBackdrop

/** Matches Material 3's disabled content alpha. */
private const val DISABLED_ALPHA = 0.38f

/**
 * The surface Sora's liquid glass refracts.
 *
 * Preference rows are drawn on `surfaceContainer` (see PreferenceTemplate), so
 * that is what the glass bends. This mirrors the Backdrop catalog, which uses a
 * canvas backdrop for controls sitting on a flat surface. Refracting the real
 * content behind a control instead would mean rendering the whole preference
 * screen into a layer backdrop -- a larger change than swapping the controls.
 */
@Composable
fun rememberSurfaceBackdrop(
    color: Color = MaterialTheme.colorScheme.surfaceContainer,
): Backdrop {
    // rememberCanvasBackdrop keys its cache on the lambda instance, so the lambda
    // is remembered explicitly: an unstable one would rebuild the backdrop on
    // every recomposition, which in a scrolling list is every frame.
    val onDraw: DrawScope.() -> Unit = remember(color) { { drawRect(color) } }
    return rememberCanvasBackdrop(onDraw)
}

/**
 * Swallows touches and dims content while [enabled] is false.
 *
 * The upstream liquid components take no `enabled` flag, and the launcher's
 * preferences rely on one. Handling it here keeps the vendored copies close to
 * upstream so they stay easy to diff against new releases.
 */
private fun Modifier.disabledUnless(enabled: Boolean): Modifier =
    if (enabled) {
        this
    } else {
        this
            .alpha(DISABLED_ALPHA)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent().changes.forEach { it.consume() }
                    }
                }
            }
    }

/**
 * Liquid glass replacement for Material 3's `Switch`.
 *
 * [checked] is a lambda, not a value: the underlying component subscribes to it
 * through `snapshotFlow`, so it has to read the state itself. Passing a plain
 * Boolean would capture one reading and freeze the control.
 */
@Composable
fun SoraLiquidToggle(
    checked: () -> Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    backdrop: Backdrop = rememberSurfaceBackdrop(),
) {
    LiquidToggle(
        selected = checked,
        onSelect = { if (enabled) onCheckedChange(it) },
        backdrop = backdrop,
        modifier = modifier.disabledUnless(enabled),
    )
}

/**
 * Liquid glass replacement for Material 3's `Slider`.
 *
 * [value] is a lambda for the same reason as in [SoraLiquidToggle], and here it
 * matters more: the thumb only moves when `LaunchedEffect` observes the value
 * through `snapshotFlow`, and that effect is keyed on the animation object, so
 * it captures the lambda exactly once. Hand it a plain Float and the slider
 * renders correctly but cannot be dragged at all.
 */
@Composable
fun SoraLiquidSlider(
    value: () -> Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    backdrop: Backdrop = rememberSurfaceBackdrop(),
) {
    LiquidSlider(
        value = value,
        onValueChange = { if (enabled) onValueChange(it) },
        valueRange = valueRange,
        // A hundredth of the range reads as "stopped" without cutting the
        // settle animation short on wide ranges such as 0..1000.
        visibilityThreshold = (valueRange.endInclusive - valueRange.start) / 100f,
        backdrop = backdrop,
        modifier = modifier.disabledUnless(enabled),
        onValueChangeFinished = { if (enabled) onValueChangeFinished(it) },
    )
}

/** Liquid glass replacement for Material 3's `Button`. */
@Composable
fun SoraLiquidButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = MaterialTheme.colorScheme.primary,
    content: @Composable RowScope.() -> Unit,
) {
    LiquidButton(
        onClick = { if (enabled) onClick() },
        backdrop = rememberSurfaceBackdrop(),
        modifier = modifier.disabledUnless(enabled),
        tint = tint,
        content = content,
    )
}
