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

package com.android.quickstep.recents.domain.usecase

import android.graphics.Bitmap
import android.view.Surface
import com.android.launcher3.util.SplitConfigurationOptions.STAGE_POSITION_BOTTOM_OR_RIGHT
import com.android.launcher3.util.SplitConfigurationOptions.STAGE_POSITION_TOP_OR_LEFT
import com.android.quickstep.recents.data.RecentsRotationStateRepository
import com.android.systemui.shared.recents.model.ThumbnailData
import com.android.systemui.shared.recents.utilities.PreviewPositionHelper
import com.android.systemui.shared.recents.utilities.Utilities
import com.android.wm.shell.shared.split.SplitBounds
import com.android.wm.shell.shared.split.SplitScreenConstants.SNAP_TO_2_10_90
import com.android.wm.shell.shared.split.SplitScreenConstants.SNAP_TO_2_90_10

/**
 * Use case responsible for validating the aspect ratio and rotation of a thumbnail against the
 * expected values based on the view's dimensions and the current rotation state.
 *
 * This class checks if the thumbnail's aspect ratio significantly differs from the aspect ratio of
 * the view it is intended to be displayed in, and if the thumbnail's rotation is consistent with
 * the device's current rotation state.
 *
 * @property rotationStateRepository Repository providing the current rotation state of the device.
 */
class IsThumbnailValidUseCase(private val rotationStateRepository: RecentsRotationStateRepository) {
    operator fun invoke(thumbnailData: ThumbnailData?, viewWidth: Int, viewHeight: Int,
                        splitBounds: SplitBounds?, stagePosition: Int): Boolean {
        val thumbnail = thumbnailData?.thumbnail ?: return false
        return !isInaccurateThumbnail(thumbnail, viewWidth, viewHeight, thumbnailData.rotation,
            splitBounds, stagePosition)
    }

    private fun isInaccurateThumbnail(
        thumbnail: Bitmap,
        viewWidth: Int,
        viewHeight: Int,
        rotation: Int,
        splitBounds: SplitBounds?,
        stagePosition: Int,
    ) = isAspectRatioDifferentFromViewAspectRatio(
            thumbnail = thumbnail,
            width = viewWidth.toFloat(),
            height = viewHeight.toFloat(),
            splitBounds = splitBounds,
            stagePosition = stagePosition
        ) || isRotationDifferentFromTask(rotation)

    private fun isAspectRatioDifferentFromViewAspectRatio(
        thumbnail: Bitmap,
        width: Float,
        height: Float,
        splitBounds: SplitBounds?,
        stagePosition: Int,
    ): Boolean {
        // TODO(b/428983119): Pass in a rect for visible bounds and compare that instead of the
        //  thumbnail width/height aspect ratio to avoid special casing flex split checks
        if (splitBounds != null) {
            val isTopLeft10PercentApp =
                (splitBounds.snapPosition == SNAP_TO_2_10_90 &&
                        stagePosition == STAGE_POSITION_TOP_OR_LEFT)
            val isBottomRight10PercentApp =
                        (splitBounds.snapPosition == SNAP_TO_2_90_10 &&
                                stagePosition == STAGE_POSITION_BOTTOM_OR_RIGHT)
            if (isTopLeft10PercentApp || isBottomRight10PercentApp) {
                return false
            }
        }

        return Utilities.isRelativePercentDifferenceGreaterThan(
            /* first = */ width / height,
            /* second = */ thumbnail.width / thumbnail.height.toFloat(),
            /* bound = */ PreviewPositionHelper.MAX_PCT_BEFORE_ASPECT_RATIOS_CONSIDERED_DIFFERENT,
        )
    }

    private fun isRotationDifferentFromTask(thumbnailRotation: Int): Boolean {
        val rotationState = rotationStateRepository.getRecentsRotationState()
        return if (rotationState.orientationHandlerRotation == Surface.ROTATION_0) {
            (rotationState.activityRotation - thumbnailRotation) % 2 != 0
        } else {
            rotationState.orientationHandlerRotation != thumbnailRotation
        }
    }
}
