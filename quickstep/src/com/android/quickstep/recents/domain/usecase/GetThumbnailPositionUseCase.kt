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

import android.graphics.Matrix
import android.graphics.Rect
import com.android.quickstep.recents.data.RecentsDeviceProfileRepository
import com.android.quickstep.recents.data.RecentsRotationStateRepository
import com.android.systemui.shared.recents.model.ThumbnailData
import com.android.systemui.shared.recents.utilities.PreviewPositionHelper

/** Use case for retrieving [Matrix] for positioning Thumbnail in a View */
class GetThumbnailPositionUseCase(
    private val deviceProfileRepository: RecentsDeviceProfileRepository,
    private val rotationStateRepository: RecentsRotationStateRepository,
    private val previewPositionHelperFactory: PreviewPositionHelper.PreviewPositionHelperFactory,
) {
    operator fun invoke(
        thumbnailData: ThumbnailData?,
        width: Int,
        height: Int,
        isRtl: Boolean,
    ): ThumbnailPosition {
        val thumbnail =
            thumbnailData?.thumbnail ?: return ThumbnailPosition(Matrix.IDENTITY_MATRIX, false)

        val previewPositionHelper = previewPositionHelperFactory.create()
        previewPositionHelper.updateThumbnailMatrix(
            Rect(0, 0, thumbnail.width, thumbnail.height),
            thumbnailData,
            width,
            height,
            deviceProfileRepository.getRecentsDeviceProfile().isLargeScreen,
            rotationStateRepository.getRecentsRotationState().activityRotation,
            isRtl,
        )
        return ThumbnailPosition(
            matrix = previewPositionHelper.matrix,
            isRotated = previewPositionHelper.isOrientationChanged,
        )
    }
}

data class ThumbnailPosition(val matrix: Matrix, val isRotated: Boolean)
