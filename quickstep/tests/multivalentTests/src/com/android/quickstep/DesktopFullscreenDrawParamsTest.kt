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

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock

/** Test for [DesktopFullscreenDrawParams] class. */
@SmallTest
@RunWith(AndroidJUnit4::class)
class DesktopFullscreenDrawParamsTest() {
    private val params =
        DesktopFullscreenDrawParams(mock<Context>(), cornerRadiusProvider = { CORNER_RADIUS })

    @Test
    fun setMiddleProgress_invariantCornerRadiusForDesktop() {
        params.setProgress(fullscreenProgress = 0f, parentScale = 1f, taskViewScale = 1f)
        assertThat(params.currentCornerRadius).isEqualTo(CORNER_RADIUS)

        params.setProgress(fullscreenProgress = 0.67f, parentScale = 1f, taskViewScale = 1f)
        assertThat(params.currentCornerRadius).isEqualTo(CORNER_RADIUS)

        params.setProgress(fullscreenProgress = 1f, parentScale = 1f, taskViewScale = 1f)
        assertThat(params.currentCornerRadius).isEqualTo(CORNER_RADIUS)
    }

    companion object {
        const val CORNER_RADIUS = 32f
    }
}
