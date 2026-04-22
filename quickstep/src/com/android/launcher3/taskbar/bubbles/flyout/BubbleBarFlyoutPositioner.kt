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

package com.android.launcher3.taskbar.bubbles.flyout

import android.graphics.PointF

/** Provides positioning data to the flyout view. */
interface BubbleBarFlyoutPositioner {

    /** Whether the flyout view should be positioned on left or the right edge. */
    val isOnLeft: Boolean

    /** The target translation Y that the flyout view should have when displayed. */
    val targetTy: Float

    /**
     * The distance between the expanded position of the flyout and the collapsed position.
     *
     * The distance is calculated between the bottom corner which is aligned with the bubble bar.
     */
    val distanceToCollapsedPosition: PointF

    /** The size of the flyout when collapsed. */
    val collapsedSize: Float

    /** The color of the flyout when collapsed. */
    val collapsedColor: Int

    /** The elevation of the flyout when collapsed. */
    val collapsedElevation: Float

    /**
     * The distance the flyout must pass from its collapsed position until it can start revealing
     * the triangle.
     */
    val distanceToRevealTriangle: Float
}
