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

        /** Calculates start and end values for revealing [Folder] background and content */
        fun Folder.getClipRevealData(
            shapeDelegate: ShapeDelegate,
            folderAnimationData: FolderAnimationData,
        ): ClipRevealData {
            val folderBackground = background as GradientDrawable
            val deviceProfile = mActivityContext.deviceProfile

            with(folderAnimationData) {
                // Setup start and end area for revealing Folder background
                val backgroundStartRect =
                    Rect(
                        previewOffsetX,
                        contentOffsetY,
                        Math.round((previewOffsetX + initialFolderSize)),
                        Math.round((contentOffsetY + initialFolderSize)),
                    )
                val backgroundEndRect = Rect(0, 0, layoutParams.width, layoutParams.height)
                val finalBackgroundRadius = folderBackground.cornerRadius

                // Get page for revealing Folder Content
                var page = if (isOpening) content.currentPage else content.destinationPage
                if (Utilities.isRtl(context.resources)) {
                    page = (content.pageCount - 1) - page
                }
                val pageStart = page * layoutParams.width

                // Setup start and end area for revealing Folder Content
                val extraRadius =
                    ((deviceProfile.folderIconSizePx / initialFolderScale) *
                            EXTRA_FOLDER_REVEAL_RADIUS_PERCENTAGE)
                        .toInt()
                val contentStart =
                    Rect(
                        (pageStart + (backgroundStartRect.left / initialFolderScale)).toInt() -
                            extraRadius,
                        (backgroundStartRect.top / initialFolderScale).toInt() - extraRadius,
                        (pageStart + (backgroundStartRect.right / initialFolderScale)).toInt() +
                            extraRadius,
                        (backgroundStartRect.bottom / initialFolderScale).toInt() + extraRadius,
                    )
                val contentEnd =
                    Rect(pageStart, 0, pageStart + layoutParams.width, layoutParams.height)
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
