/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.systemui.surfaceeffects.glowboxeffect

/** Parameters used to play [GlowBoxEffect]. */
data class GlowBoxConfig(
    /** Start center position X in px. */
    val startCenterX: Float,
    /** Start center position Y in px. */
    val startCenterY: Float,
    /** End center position X in px. */
    val endCenterX: Float,
    /** End center position Y in px. */
    val endCenterY: Float,
    /** Width of the box in px. */
    val width: Float,
    /** Height of the box in px. */
    val height: Float,
    /** Color of the box in ARGB, Apply alpha value if needed. */
    val color: Int,
    /** Amount of blur (or glow) of the box. */
    val blurAmount: Float,
    /**
     * Duration of the animation. Note that the full duration of the animation is
     * [duration] + [easeInDuration] + [easeOutDuration].
     */
    val duration: Long,
    /** Ease in duration of the animation. */
    val easeInDuration: Long,
    /** Ease out duration of the animation. */
    val easeOutDuration: Long,
)
