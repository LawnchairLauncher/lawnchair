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
import android.view.Surface.ROTATION_90
import com.android.quickstep.recents.data.FakeRecentsRotationStateRepository
import com.android.systemui.shared.recents.model.ThumbnailData
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class IsThumbnailValidUseCaseTest {
    private val recentsRotationStateRepository = FakeRecentsRotationStateRepository()
    private val systemUnderTest = IsThumbnailValidUseCase(recentsRotationStateRepository)

    @Test
    fun withNullThumbnail_returnsInvalid() = runTest {
        val isThumbnailValid = systemUnderTest(thumbnailData = null, viewWidth = 0, viewHeight = 0)
        assertThat(isThumbnailValid).isEqualTo(false)
    }

    @Test
    fun sameAspectRatio_sameRotation_returnsValid() = runTest {
        val isThumbnailValid =
            systemUnderTest.invoke(
                thumbnailData = createThumbnailData(),
                viewWidth = THUMBNAIL_WIDTH * 2,
                viewHeight = THUMBNAIL_HEIGHT * 2,
            )
        assertThat(isThumbnailValid).isEqualTo(true)
    }

    @Test
    fun differentAspectRatio_sameRotation_returnsInvalid() = runTest {
        val isThumbnailValid =
            systemUnderTest.invoke(
                thumbnailData = createThumbnailData(),
                viewWidth = THUMBNAIL_WIDTH,
                viewHeight = THUMBNAIL_HEIGHT * 2,
            )
        assertThat(isThumbnailValid).isEqualTo(false)
    }

    @Test
    fun sameAspectRatio_differentRotation_returnsInvalid() = runTest {
        val isThumbnailValid =
            systemUnderTest.invoke(
                thumbnailData = createThumbnailData(rotation = ROTATION_90),
                viewWidth = THUMBNAIL_WIDTH * 2,
                viewHeight = THUMBNAIL_HEIGHT * 2,
            )
        assertThat(isThumbnailValid).isEqualTo(false)
    }

    @Test
    fun differentAspectRatio_differentRotation_returnsInvalid() = runTest {
        val isThumbnailValid =
            systemUnderTest.invoke(
                thumbnailData = createThumbnailData(rotation = ROTATION_90),
                viewWidth = THUMBNAIL_WIDTH,
                viewHeight = THUMBNAIL_HEIGHT * 2,
            )
        assertThat(isThumbnailValid).isEqualTo(false)
    }

    private fun createThumbnailData(
        rotation: Int = Surface.ROTATION_0,
        width: Int = THUMBNAIL_WIDTH,
        height: Int = THUMBNAIL_HEIGHT,
    ): ThumbnailData {
        val bitmap = mock<Bitmap>()
        whenever(bitmap.width).thenReturn(width)
        whenever(bitmap.height).thenReturn(height)
        return ThumbnailData(thumbnail = bitmap, rotation = rotation)
    }

    companion object {
        const val THUMBNAIL_WIDTH = 100
        const val THUMBNAIL_HEIGHT = 200
    }
}
