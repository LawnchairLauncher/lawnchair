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
import android.graphics.Matrix
import android.graphics.Rect
import android.view.Surface.ROTATION_90
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.quickstep.recents.data.FakeRecentsDeviceProfileRepository
import com.android.quickstep.recents.data.FakeRecentsRotationStateRepository
import com.android.systemui.shared.recents.model.ThumbnailData
import com.android.systemui.shared.recents.utilities.PreviewPositionHelper
import com.android.systemui.shared.recents.utilities.PreviewPositionHelper.PreviewPositionHelperFactory
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/** Test for [GetThumbnailPositionUseCase] */
@RunWith(AndroidJUnit4::class)
class GetThumbnailPositionUseCaseTest {
    private val deviceProfileRepository = FakeRecentsDeviceProfileRepository()
    private val rotationStateRepository = FakeRecentsRotationStateRepository()
    private val previewPositionHelperFactoryMock = mock<PreviewPositionHelperFactory>()
    private val previewPositionHelper = mock<PreviewPositionHelper>()

    private val systemUnderTest =
        GetThumbnailPositionUseCase(
            deviceProfileRepository = deviceProfileRepository,
            rotationStateRepository = rotationStateRepository,
            previewPositionHelperFactory = previewPositionHelperFactoryMock,
        )

    @Before
    fun setUp() {
        whenever(previewPositionHelperFactoryMock.create()).thenReturn(previewPositionHelper)
    }

    @Test
    fun nullThumbnailData_returnsIdentityMatrix() = runTest {
        val expectedResult = ThumbnailPosition(Matrix.IDENTITY_MATRIX, false)
        val result = systemUnderTest.invoke(null, CANVAS_WIDTH, CANVAS_HEIGHT, isRtl = true)
        assertThat(result).isEqualTo(expectedResult)
    }

    @Test
    fun withoutThumbnail_returnsIdentityMatrix() = runTest {
        val expectedResult = ThumbnailPosition(Matrix.IDENTITY_MATRIX, false)
        val result =
            systemUnderTest.invoke(ThumbnailData(), CANVAS_WIDTH, CANVAS_HEIGHT, isRtl = true)
        assertThat(result).isEqualTo(expectedResult)
    }

    @Test
    fun visibleTaskWithThumbnailData_returnsTransformedMatrix() = runTest {
        val isLargeScreen = true
        deviceProfileRepository.setRecentsDeviceProfile(
            deviceProfileRepository.getRecentsDeviceProfile().copy(isLargeScreen = isLargeScreen)
        )
        val activityRotation = ROTATION_90
        rotationStateRepository.setRecentsRotationState(
            rotationStateRepository
                .getRecentsRotationState()
                .copy(activityRotation = activityRotation)
        )
        val isRtl = true
        val isRotated = true

        whenever(previewPositionHelper.matrix).thenReturn(MATRIX)
        whenever(previewPositionHelper.isOrientationChanged).thenReturn(isRotated)

        val result = systemUnderTest.invoke(THUMBNAIL_DATA, CANVAS_WIDTH, CANVAS_HEIGHT, isRtl)
        val expectedResult = ThumbnailPosition(MATRIX, isRotated)
        assertThat(result).isEqualTo(expectedResult)

        verify(previewPositionHelper)
            .updateThumbnailMatrix(
                Rect(0, 0, THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT),
                THUMBNAIL_DATA,
                CANVAS_WIDTH,
                CANVAS_HEIGHT,
                isLargeScreen,
                activityRotation,
                isRtl,
            )
    }

    @Test
    fun multipleInvocations_usesPreviewPositionHelperFactoryEachTime() = runTest {
        whenever(previewPositionHelper.matrix).thenReturn(MATRIX)

        val sut =
            GetThumbnailPositionUseCase(
                deviceProfileRepository = deviceProfileRepository,
                rotationStateRepository = rotationStateRepository,
                previewPositionHelperFactory = previewPositionHelperFactoryMock,
            )
        verify(previewPositionHelperFactoryMock, times(0)).create()

        sut.invoke(THUMBNAIL_DATA, CANVAS_WIDTH, CANVAS_HEIGHT, /* isRtl= */ true)
        sut.invoke(THUMBNAIL_DATA, CANVAS_WIDTH, CANVAS_HEIGHT, /* isRtl= */ false)

        // Each invocation of use case should use a fresh position helper acquired by the factory.
        verify(previewPositionHelperFactoryMock, times(2)).create()
    }

    private companion object {
        const val THUMBNAIL_WIDTH = 100
        const val THUMBNAIL_HEIGHT = 200
        const val CANVAS_WIDTH = 300
        const val CANVAS_HEIGHT = 600
        val MATRIX =
            Matrix().apply {
                setValues(floatArrayOf(2.3f, 4.5f, 2.6f, 7.4f, 3.4f, 2.3f, 2.5f, 6.0f, 3.4f))
            }

        val THUMBNAIL_DATA =
            ThumbnailData(
                thumbnail =
                    mock<Bitmap>().apply {
                        whenever(width).thenReturn(THUMBNAIL_WIDTH)
                        whenever(height).thenReturn(THUMBNAIL_HEIGHT)
                    }
            )
    }
}
