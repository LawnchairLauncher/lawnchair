/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.launcher3.widgetpicker.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

/**
 * Animates the alpha when composable is first visible using a default spring animation.
 *
 * @param label a label to differentiate this animation from other animations.
 */
@Composable
internal fun Modifier.fadeInWhenVisible(label: String): Modifier {
    var visible by remember { mutableStateOf(false) }

    val animatedAlpha by
        animateFloatAsState(
            label = label,
            targetValue =
                if (visible) {
                    VISIBLE
                } else {
                    INVISIBLE
                },
        )

    LaunchedEffect(Unit) { visible = true }

    return this.graphicsLayer { alpha = animatedAlpha }
}

private const val VISIBLE = 1.0f
private const val INVISIBLE = 0f
