/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.quickstep

import android.graphics.Rect
import android.graphics.RectF
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.launcher3.DeviceProfileBaseTest
import com.android.quickstep.views.TaskThumbnailView.PreviewPositionHelper
import com.android.systemui.shared.recents.model.ThumbnailData
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock

/**
 * Test for TaskThumbnailView class.
 */
@SmallTest
@RunWith(AndroidJUnit4::class)
class TaskThumbnailViewTest : DeviceProfileBaseTest() {

    private var mThumbnailData: ThumbnailData = mock(ThumbnailData::class.java)

    private val mPreviewPositionHelper = PreviewPositionHelper()

    @Test
    fun getInsetsToDrawInFullscreen_clipTaskbarSizeFromBottomForTablets() {
        initializeVarsForTablet()
        val dp = newDP()
        val previewRect = Rect(0, 0, 100, 100)
        val canvasWidth = dp.widthPx / 2
        val canvasHeight = dp.heightPx / 2
        val currentRotation = 0
        val isRtl = false

        mPreviewPositionHelper.updateThumbnailMatrix(previewRect, mThumbnailData, canvasWidth,
                canvasHeight, dp, currentRotation, isRtl)

        val expectedClippedInsets = RectF(0f, 0f, 0f, dp.taskbarSize / 2f)
        assertThat(mPreviewPositionHelper.getInsetsToDrawInFullscreen(dp))
                .isEqualTo(expectedClippedInsets)
    }

    @Test
    fun getInsetsToDrawInFullscreen_doNotClipTaskbarSizeFromBottomForPhones() {
        initializeVarsForPhone()
        val dp = newDP()
        val previewRect = Rect(0, 0, 100, 100)
        val canvasWidth = dp.widthPx / 2
        val canvasHeight = dp.heightPx / 2
        val currentRotation = 0
        val isRtl = false

        mPreviewPositionHelper.updateThumbnailMatrix(previewRect, mThumbnailData, canvasWidth,
                canvasHeight, dp, currentRotation, isRtl)

        val expectedClippedInsets = RectF(0f, 0f, 0f, 0f)
        assertThat(mPreviewPositionHelper.getInsetsToDrawInFullscreen(dp))
                .isEqualTo(expectedClippedInsets)
    }
}