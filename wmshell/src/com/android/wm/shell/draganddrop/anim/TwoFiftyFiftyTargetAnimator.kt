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

package com.android.wm.shell.draganddrop.anim

import android.content.res.Resources
import android.graphics.Insets
import android.graphics.Rect
import com.android.wm.shell.R
import com.android.wm.shell.common.DisplayLayout
import com.android.wm.shell.draganddrop.SplitDragPolicy.Target
import com.android.wm.shell.shared.split.SplitScreenConstants.SPLIT_INDEX_0
import com.android.wm.shell.shared.split.SplitScreenConstants.SPLIT_INDEX_1
import com.android.wm.shell.shared.split.SplitScreenConstants.SPLIT_INDEX_2
import com.android.wm.shell.shared.split.SplitScreenConstants.SPLIT_INDEX_3

/**
 * Represents Drop Zone targets and animations for when the system is currently in a 2 app 50/50
 * split.
 * SnapPosition = 2_50_50
 *
 * NOTE: Naming convention for many variables is done as "hXtYZ"
 * This means that variable is a transformation on the Z property for target index Y while the user
 * is hovering over target index X
 * Ex: h1t2scaleX=2 => User is hovering over target index 1, target index 2 should scaleX by 2
 *
 * TODO(b/349828130): Everything in this class is temporary, none of this is up to spec.
 */
class TwoFiftyFiftyTargetAnimator : DropTargetAnimSupplier {
    /**
     * TODO: Could we transpose all the horizontal rects by 90 degrees and have that suffice for
     *  top bottom split?? Hmmm... Doubt it.
     */
    override fun getTargets(
        displayLayout: DisplayLayout,
        insets: Insets,
        isLeftRightSplit: Boolean,
        resources: Resources
    ): Pair<List<Target>, List<List<HoverAnimProps>>> {
        val targets : ArrayList<Target> = ArrayList()
        val w: Int = displayLayout.width()
        val h: Int = displayLayout.height()
        val iw = w - insets.left - insets.right
        val ih = h - insets.top - insets.bottom
        val l = insets.left
        val t = insets.top
        val displayRegion = Rect(l, t, l + iw, t + ih)
        val fullscreenDrawRegion = Rect(displayRegion)
        val dividerWidth: Float = resources.getDimensionPixelSize(
            R.dimen.split_divider_bar_width
        ).toFloat()

        val farStartBounds = Rect()
        farStartBounds.set(displayRegion)
        val startBounds = Rect()
        startBounds.set(displayRegion)
        val endBounds = Rect()
        endBounds.set(displayRegion)
        val farEndBounds = Rect()
        farEndBounds.set(displayRegion)
        val endsPercent = 0.10f
        val visibleStagePercent = 0.45f
        val halfDividerWidth = dividerWidth.toInt() / 2
        val endsWidth = Math.round(displayRegion.width() * endsPercent)
        val stageWidth = Math.round(displayRegion.width() * visibleStagePercent)


        // Place the farStart and farEnds outside of the display, and then
        // animate them in once the hover starts
        // | = divider; || = display boundary
        // farStart || start | end || farEnd
        farStartBounds.left = -endsWidth
        farStartBounds.right = 0
        startBounds.left = farStartBounds.right + dividerWidth.toInt()
        startBounds.right = startBounds.left + stageWidth
        endBounds.left = startBounds.right + dividerWidth.toInt()
        endBounds.right = endBounds.left + stageWidth
        farEndBounds.left = fullscreenDrawRegion.right
        farEndBounds.right = farEndBounds.left + endsWidth


        // For the hit rect, trim the divider space we've added between the
        // rects
        targets.add(
            Target(
                Target.TYPE_SPLIT_LEFT,
                Rect(
                    farStartBounds.left, farStartBounds.top,
                    farStartBounds.right + halfDividerWidth,
                    farStartBounds.bottom
                ),
                farStartBounds, SPLIT_INDEX_0
            )
        )
        targets.add(
            Target(
                Target.TYPE_SPLIT_LEFT,
                Rect(
                    startBounds.left - halfDividerWidth,
                    startBounds.top,
                    startBounds.right + halfDividerWidth,
                    startBounds.bottom
                ),
                startBounds, SPLIT_INDEX_1
            )
        )
        targets.add(
            Target(
                Target.TYPE_SPLIT_LEFT,
                Rect(
                    endBounds.left - halfDividerWidth,
                    endBounds.top, endBounds.right, endBounds.bottom
                ),
                endBounds, SPLIT_INDEX_2
            )
        )
        targets.add(
            Target(
                Target.TYPE_SPLIT_LEFT,
                Rect(
                    farEndBounds.left - halfDividerWidth,
                    farEndBounds.top, farEndBounds.right, farEndBounds.bottom
                ),
                farEndBounds, SPLIT_INDEX_3
            )
        )


        // Hovering over target 0,
        // * increase scaleX of target 0
        // * decrease scaleX of target 1, 2
        // * ensure target 3 offscreen

        // bring target 0 in from offscreen and expand
        val h0t0ScaleX = stageWidth.toFloat() / endsWidth
        val h0t0TransX: Float = stageWidth / h0t0ScaleX + dividerWidth
        val h0t0HoverProps = HoverAnimProps(
            targets.get(0),
            h0t0TransX, farStartBounds.top.toFloat(), h0t0ScaleX, 1f,
            Rect(
                0, 0, (stageWidth + dividerWidth).toInt(),
                farStartBounds.bottom
            )
        )


        // move target 1 over to the middle/end
        val h0t1TransX = stageWidth.toFloat()
        val h0t1ScaleX = 1f
        val h0t1HoverProps = HoverAnimProps(
            targets.get(1),
            h0t1TransX, startBounds.top.toFloat(), h0t1ScaleX, 1f,
            Rect(
                stageWidth, 0, (stageWidth + h0t1TransX).toInt(),
                farStartBounds.bottom
            )
        )


        // move target 2 to the very end
        val h0t2TransX = endBounds.left + stageWidth / 2f
        val h0t2ScaleX = endsWidth.toFloat() / stageWidth
        val h0t2HoverProps = HoverAnimProps(
            targets.get(2),
            h0t2TransX, endBounds.top.toFloat(), h0t2ScaleX, 1f,
            Rect(
                displayRegion.right as Int - endsWidth, 0,
                displayRegion.right as Int,
                farStartBounds.bottom
            )
        )


        // move target 3 off-screen
        val h0t3TransX = farEndBounds.right.toFloat()
        val h0t3ScaleX = 1f
        val h0t3HoverProps = HoverAnimProps(
            targets.get(3),
            h0t3TransX, farEndBounds.top.toFloat(), h0t3ScaleX, 1f,
            null
        )
        val animPropsForHoverTarget0 =
            listOf(h0t0HoverProps, h0t1HoverProps, h0t2HoverProps, h0t3HoverProps)


        // Hovering over target 1,
        // * Bring in target 0 from offscreen start
        // * Shift over target 1
        // * Slightly lower scale of target 2
        // * Ensure target 4 offscreen
        // bring target 0 in from offscreen
        val h1t0TransX = 0f
        val h1t0ScaleX = 1f
        val h1t0HoverProps = HoverAnimProps(
            targets.get(0),
            h1t0TransX, farStartBounds.top.toFloat(), h1t0ScaleX, 1f,
            Rect(
                0, 0, (farStartBounds.width() + dividerWidth).toInt(),
                farStartBounds.bottom
            )
        )


        // move target 1 over a tiny bit by same amount and make it smaller
        val h1t1TransX: Float = endsWidth + dividerWidth
        val h1t1ScaleX = 1f
        val h1t1HoverProps = HoverAnimProps(
            targets.get(1),
            h1t1TransX, startBounds.top.toFloat(), h1t1ScaleX, 1f,
            Rect(
                h1t1TransX.toInt(), 0, (h1t1TransX + stageWidth).toInt(),
                farStartBounds.bottom
            )
        )


        // move target 2 to the very end
        val h1t2TransX = (endBounds.left + farStartBounds.width()).toFloat()
        val h1t2ScaleX = h1t1ScaleX
        val h1t2HoverProps = HoverAnimProps(
            targets.get(2),
            h1t2TransX, endBounds.top.toFloat(), h1t2ScaleX, 1f,
            Rect(
                endBounds.left + farStartBounds.width(),
                0,
                (endBounds.left + farStartBounds.width() + stageWidth),
                farStartBounds.bottom
            )
        )


        // move target 3 off-screen, default laid out is off-screen
        val h1t3TransX = farEndBounds.right.toFloat()
        val h1t3ScaleX = 1f
        val h1t3HoverProps = HoverAnimProps(
            targets.get(3),
            h1t3TransX, farEndBounds.top.toFloat(), h1t3ScaleX, 1f,
            null
        )
        val animPropsForHoverTarget1 =
            listOf(h1t0HoverProps, h1t1HoverProps, h1t2HoverProps, h1t3HoverProps)


        // Hovering over target 2,
        // * Ensure Target 0 offscreen
        // * Ensure target 1 back to start, slightly smaller scale
        // * Slightly lower scale of target 2
        // * Bring target 4 on screen
        // reset target 0
        val h2t0TransX = farStartBounds.left.toFloat()
        val h2t0ScaleX = 1f
        val h2t0HoverProps = HoverAnimProps(
            targets.get(0),
            h2t0TransX, farStartBounds.top.toFloat(), h2t0ScaleX, 1f,
            null
        )


        // move target 1 over a tiny bit by same amount and make it smaller
        val h2t1TransX = startBounds.left.toFloat()
        val h2t1ScaleX = 1f
        val h2t1HoverProps = HoverAnimProps(
            targets.get(1),
            h2t1TransX, startBounds.top.toFloat(), h2t1ScaleX, 1f,
            Rect(
                startBounds.left, 0,
                (startBounds.left + stageWidth),
                farStartBounds.bottom
            )
        )


        // move target 2 to the very end
        val h2t2TransX = endBounds.left.toFloat()
        val h2t2ScaleX = h2t1ScaleX
        val h2t2HoverProps = HoverAnimProps(
            targets.get(2),
            h2t2TransX, endBounds.top.toFloat(), h2t2ScaleX, 1f,
            Rect(
                (startBounds.right + dividerWidth).toInt(),
                0,
                endBounds.left + stageWidth,
                farStartBounds.bottom
            )
        )


        // bring target 3 on-screen
        val h2t3TransX = (farEndBounds.left - farEndBounds.width()).toFloat()
        val h2t3ScaleX = 1f
        val h2t3HoverProps = HoverAnimProps(
            targets.get(3),
            h2t3TransX, farEndBounds.top.toFloat(), h2t3ScaleX, 1f,
            Rect(
                endBounds.right,
                0,
                displayRegion.right,
                farStartBounds.bottom
            )
        )
        val animPropsForHoverTarget2 =
            listOf(h2t0HoverProps, h2t1HoverProps, h2t2HoverProps, h2t3HoverProps)


        // Hovering over target 3,
        // * Ensure Target 0 offscreen
        // * Ensure target 1 back to start, slightly smaller scale
        // * Slightly lower scale of target 2
        // * Bring target 4 on screen and scale up
        // reset target 0
        val h3t0TransX = farStartBounds.left.toFloat()
        val h3t0ScaleX = 1f
        val h3t0HoverProps = HoverAnimProps(
            targets.get(0),
            h3t0TransX, farStartBounds.top.toFloat(), h3t0ScaleX, 1f,
            null
        )


        // move target 1 over a tiny bit by same amount and make it smaller
        val h3t1ScaleX = endsWidth.toFloat() / stageWidth
        val h3t1TransX = 0 - (stageWidth / (1 / h3t1ScaleX))
        val h3t1HoverProps = HoverAnimProps(
            targets.get(1),
            h3t1TransX, startBounds.top.toFloat(), h3t1ScaleX, 1f,
            Rect(
                0, 0,
                endsWidth,
                farStartBounds.bottom
            )
        )


        // move target 2 towards the start
        val h3t2TransX: Float = endsWidth + dividerWidth
        val h3t2ScaleX = 1f
        val h3t2HoverProps = HoverAnimProps(
            targets.get(2),
            h3t2TransX, endBounds.top.toFloat(), h3t2ScaleX, 1f,
            Rect(
                endsWidth, 0,
                (endsWidth + stageWidth + dividerWidth).toInt(),
                farStartBounds.bottom
            )
        )


        // bring target 3 on-screen and expand
        val h3t3ScaleX = stageWidth.toFloat() / endsWidth
        val h3t3TransX = endBounds.right - stageWidth / 2f
        val h3t3HoverProps = HoverAnimProps(
            targets.get(3),
            h3t3TransX, farEndBounds.top.toFloat(), h3t3ScaleX, 1f,
            Rect(
                displayRegion.right - stageWidth, 0,
                displayRegion.right,
                farStartBounds.bottom
            )
        )
        val animPropsForHoverTarget3 =
            listOf(h3t0HoverProps, h3t1HoverProps, h3t2HoverProps, h3t3HoverProps)

        return Pair(targets, listOf(animPropsForHoverTarget0, animPropsForHoverTarget1,
            animPropsForHoverTarget2, animPropsForHoverTarget3))

    }
}