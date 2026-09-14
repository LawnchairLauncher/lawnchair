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

package com.android.launcher3.folder

import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.view.ViewGroup
import app.lawnchair.ui.liquid.LiquidGlassDrawable
import com.android.launcher3.Utilities
import com.android.launcher3.graphics.ShapeDelegate

/**
 * Start and End values for revealing [Folder] content and background via Clip Animation. Reveal
 * Animator defined in [ShapeDelegate].
 */
data class ClipRevealData(
    /** is folder opening or closing */
    val isOpening: Boolean,
    /** shape to clip folder to */
    val shapeDelegate: ShapeDelegate,
    /** area to clip background to when folder is closed */
    val backgroundStartRect: Rect,
    /** area to clip background to when folder is open */
    val backgroundEndRect: Rect,
    /** area to clip content to when folder is closed */
    val contentStart: Rect,
    /** area to clip content to when folder is open */
    val contentEnd: Rect,
    /** radius of folder when open */
    val finalRadius: Float,
) {
    companion object Factory {
        const val EXTRA_FOLDER_REVEAL_RADIUS_PERCENTAGE = 0.125f
        /** Half the short side is the deepest a corner reaches; 1 - 1/sqrt2 of it is the sweep. */
        private const val CORNER_CLEARANCE = 0.15f

        /** Calculates start and end values for revealing [Folder] background and content */
        fun Folder.getClipRevealData(
            shapeDelegate: ShapeDelegate,
            folderAnimationData: FolderAnimationData,
        ): ClipRevealData {
            // Sora: the folder background is liquid glass, not a GradientDrawable.
            // Only the corner radius is needed here, so read it from whichever it is.
            val folderBackground = background
            val deviceProfile = mActivityContext.deviceProfile

            with(folderAnimationData) {
                // Setup start and end area for revealing Folder background
                val cardTop = cardTop
                val cardHeight = cardHeight
                val backgroundStartRect = Rect(
                    previewOffsetX,
                    cardTop + contentOffsetY,
                    Math.round(previewOffsetX + initialFolderWidth),
                    Math.round(cardTop + contentOffsetY + initialFolderHeight),
                )
                val backgroundEndRect = Rect(0, cardTop, layoutParams.width, cardTop + cardHeight)
                val finalBackgroundRadius = when (folderBackground) {
                    is LiquidGlassDrawable -> folderBackground.cornerRadiusPx
                    is GradientDrawable -> folderBackground.cornerRadius
                    else -> Folder.FOLDER_GLASS_CORNER_RADIUS_DP * context.resources.displayMetrics.density
                }

                // Get page for revealing Folder Content
                var page = if (isOpening) content.currentPage else content.destinationPage
                if (Utilities.isRtl(context.resources)) {
                    page = (content.pageCount - 1) - page
                }
                val pageStart = page * layoutParams.width

                // Setup start and end area for revealing Folder Content
                // Room for the reveal's corner, not just for the app.
                //
                // What the content is cut to is the folder shape inscribed in
                // this rectangle, so on a narrow one the corner sweeps in by
                // nearly a seventh of the short side. A folder two cells tall
                // is 254 wide against 638 high here, and its last preview slot
                // sits right down in that sweep -- which is why a folder of
                // three apps came out with its third clipped while one of four,
                // whose last slot holds the small block instead, did not.
                //
                // A shape inscribed in a box differs from the box most on the
                // diagonal, by r * (1 - 1/sqrt2) of a corner that can reach half
                // the short side. Clearing that is what the second term buys.
                val shortSide =
                    minOf(backgroundStartRect.width(), backgroundStartRect.height()) /
                        initialFolderScale
                val extraRadius =
                    ((deviceProfile.folderIconSizePx / initialFolderScale) *
                            EXTRA_FOLDER_REVEAL_RADIUS_PERCENTAGE +
                            shortSide * CORNER_CLEARANCE)
                        .toInt()
                val contentStart =
                    Rect(
                        (pageStart + (backgroundStartRect.left / initialFolderScale)).toInt() -
                            extraRadius,
                        (contentOffsetY / initialFolderScale).toInt() - extraRadius,
                        (pageStart + (backgroundStartRect.right / initialFolderScale)).toInt() +
                            extraRadius,
                        ((contentOffsetY + initialFolderHeight) / initialFolderScale).toInt() +
                            extraRadius,
                    )
                val contentEnd =
                    Rect(pageStart, 0, pageStart + layoutParams.width, if (isCentered()) contentAreaHeight else layoutParams.height)
                return ClipRevealData(
                    isOpening = folderAnimationData.isOpening,
                    shapeDelegate = shapeDelegate,
                    backgroundStartRect = backgroundStartRect,
                    backgroundEndRect = backgroundEndRect,
                    contentStart = contentStart,
                    contentEnd = contentEnd,
                    finalRadius = finalBackgroundRadius,
                )
            }
        }
    }
}
