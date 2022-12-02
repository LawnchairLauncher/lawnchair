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
import com.android.launcher3.util.SplitConfigurationOptions.STAGE_POSITION_BOTTOM_OR_RIGHT
import com.android.launcher3.util.SplitConfigurationOptions.STAGE_POSITION_TOP_OR_LEFT
import com.android.quickstep.views.TaskView.FullscreenDrawParams
import com.android.systemui.shared.recents.model.ThumbnailData
import com.android.systemui.shared.recents.utilities.PreviewPositionHelper
import com.android.wm.shell.util.SplitBounds
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import kotlin.math.roundToInt

/**
 * Test for FullscreenDrawParams class.
 */
@SmallTest
@RunWith(AndroidJUnit4::class)
class FullscreenDrawParamsTest : DeviceProfileBaseTest() {

    private val TASK_SCALE = 0.7f
    private var mThumbnailData: ThumbnailData = mock(ThumbnailData::class.java)

    private val mPreviewPositionHelper = PreviewPositionHelper()
    private lateinit var params: FullscreenDrawParams

    @Before
    fun setup() {
        params = FullscreenDrawParams(context)
    }

    @Test
    fun setFullProgress_currentDrawnInsets_clipTaskbarSizeFromBottomForTablets() {
        initializeVarsForTablet()
        val dp = newDP()
        val previewRect = Rect(0, 0, 100, 100)
        val canvasWidth = (dp.widthPx * TASK_SCALE).roundToInt()
        val canvasHeight = (dp.heightPx * TASK_SCALE).roundToInt()
        val currentRotation = 0
        val isRtl = false

        mPreviewPositionHelper.updateThumbnailMatrix(previewRect, mThumbnailData, canvasWidth,
                canvasHeight, dp.widthPx, dp.heightPx, dp.taskbarSize, dp.isTablet, currentRotation,
                isRtl)
        params.setProgress(/* fullscreenProgress= */ 1.0f, /* parentScale= */ 1.0f,
                /* taskViewScale= */ 1.0f,  /* previewWidth= */ 0, dp, mPreviewPositionHelper)

        val expectedClippedInsets = RectF(0f, 0f, 0f, dp.taskbarSize * TASK_SCALE)
        assertThat(params.mCurrentDrawnInsets)
                .isEqualTo(expectedClippedInsets)
    }

    @Test
    fun setFullProgress_currentDrawnInsets_clipTaskbarSizeFromBottomForTablets_splitPortrait() {
        initializeVarsForTablet()
        val dp = newDP()
        val previewRect = Rect(0, 0, 100, 100)
        val canvasWidth = (dp.widthPx * TASK_SCALE).roundToInt()
        val canvasHeight = (dp.heightPx * TASK_SCALE / 2).roundToInt()
        val currentRotation = 0
        val isRtl = false
        // portrait/vertical split apps
        val dividerSize = 10
        val splitBounds = SplitBounds(
                Rect(0, 0, dp.widthPx, (dp.heightPx - dividerSize) / 2),
                Rect(0, (dp.heightPx + dividerSize) / 2, dp.widthPx, dp.heightPx),
                0 /*lefTopTaskId*/, 0 /*rightBottomTaskId*/)
        mPreviewPositionHelper.setSplitBounds(splitBounds, STAGE_POSITION_BOTTOM_OR_RIGHT)

        mPreviewPositionHelper.updateThumbnailMatrix(previewRect, mThumbnailData, canvasWidth,
                canvasHeight, dp.widthPx, dp.heightPx, dp.taskbarSize, dp.isTablet, currentRotation,
                isRtl)
        params.setProgress(/* fullscreenProgress= */ 1.0f, /* parentScale= */ 1.0f,
                /* taskViewScale= */ 1.0f,  /* previewWidth= */ 0, dp, mPreviewPositionHelper)

        // Probably unhelpful, but also unclear how to test otherwise ¯\_(ツ)_/¯
        val fullscreenTaskHeight = dp.heightPx *
                (1 - (splitBounds.topTaskPercent + splitBounds.dividerHeightPercent))
        val canvasScreenRatio = canvasHeight / fullscreenTaskHeight
        val expectedBottomHint = dp.taskbarSize * canvasScreenRatio
        assertThat(params.mCurrentDrawnInsets.bottom)
                .isWithin(1f).of(expectedBottomHint)
    }

    @Test
    fun setFullProgress_currentDrawnInsets_clipTaskbarSizeFromTopForTablets_splitPortrait() {
        initializeVarsForTablet()
        val dp = newDP()
        val previewRect = Rect(0, 0, 100, 100)
        val canvasWidth = (dp.widthPx * TASK_SCALE).roundToInt()
        val canvasHeight = (dp.heightPx * TASK_SCALE / 2).roundToInt()
        val currentRotation = 0
        val isRtl = false
        // portrait/vertical split apps
        val dividerSize = 10
        val splitBounds = SplitBounds(
                Rect(0, 0, dp.widthPx, (dp.heightPx - dividerSize) / 2),
                Rect(0, (dp.heightPx + dividerSize) / 2, dp.widthPx, dp.heightPx),
                0 /*lefTopTaskId*/, 0 /*rightBottomTaskId*/)
        mPreviewPositionHelper.setSplitBounds(splitBounds, STAGE_POSITION_TOP_OR_LEFT)

        mPreviewPositionHelper.updateThumbnailMatrix(previewRect, mThumbnailData, canvasWidth,
                canvasHeight, dp.widthPx, dp.heightPx, dp.taskbarSize, dp.isTablet, currentRotation,
                isRtl)
        params.setProgress(/* fullscreenProgress= */ 1.0f, /* parentScale= */ 1.0f,
                /* taskViewScale= */ 1.0f,  /* previewWidth= */ 0, dp, mPreviewPositionHelper)

        assertThat(params.mCurrentDrawnInsets.bottom)
                .isWithin(1f).of((0f))
    }

    @Test
    fun setFullProgress_currentDrawnInsets_clipTaskbarSizeFromBottomForTablets_splitLandscape() {
        initializeVarsForTablet(isLandscape = true)
        val dp = newDP()
        val previewRect = Rect(0, 0, 100, 100)
        val canvasWidth = (dp.widthPx * TASK_SCALE / 2).roundToInt()
        val canvasHeight = (dp.heightPx * TASK_SCALE).roundToInt()
        val currentRotation = 0
        val isRtl = false
        // portrait/vertical split apps
        val dividerSize = 10
        val splitBounds = SplitBounds(
                Rect(0, 0, (dp.widthPx - dividerSize) / 2, dp.heightPx),
                Rect((dp.widthPx + dividerSize) / 2, 0, dp.widthPx, dp.heightPx),
                0 /*lefTopTaskId*/, 0 /*rightBottomTaskId*/)
        mPreviewPositionHelper.setSplitBounds(splitBounds, STAGE_POSITION_BOTTOM_OR_RIGHT)

        mPreviewPositionHelper.updateThumbnailMatrix(previewRect, mThumbnailData, canvasWidth,
                canvasHeight, dp.widthPx, dp.heightPx, dp.taskbarSize, dp.isTablet, currentRotation,
                isRtl)
        params.setProgress(/* fullscreenProgress= */ 1.0f, /* parentScale= */ 1.0f,
                /* taskViewScale= */ 1.0f,  /* previewWidth= */ 0, dp, mPreviewPositionHelper)

        assertThat(params.mCurrentDrawnInsets.bottom)
                .isWithin(1f).of((dp.taskbarSize * TASK_SCALE))
    }

    @Test
    fun setFullProgress_currentDrawnInsets_doNotClipTaskbarSizeFromBottomForPhones() {
        initializeVarsForPhone()
        val dp = newDP()
        val previewRect = Rect(0, 0, 100, 100)
        val canvasWidth = (dp.widthPx * TASK_SCALE).roundToInt()
        val canvasHeight = (dp.heightPx * TASK_SCALE).roundToInt()
        val currentRotation = 0
        val isRtl = false

        mPreviewPositionHelper.updateThumbnailMatrix(previewRect, mThumbnailData, canvasWidth,
                canvasHeight, dp.widthPx, dp.heightPx, dp.taskbarSize, dp.isTablet, currentRotation,
                isRtl)
        params.setProgress(/* fullscreenProgress= */ 1.0f, /* parentScale= */ 1.0f,
                /* taskViewScale= */ 1.0f,  /* previewWidth= */ 0, dp, mPreviewPositionHelper)

        val expectedClippedInsets = RectF(0f, 0f, 0f, 0f)
        assertThat(params.mCurrentDrawnInsets)
                .isEqualTo(expectedClippedInsets)
    }
}