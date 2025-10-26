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
import com.android.launcher3.FakeInvariantDeviceProfileTest
import com.android.quickstep.util.TaskCornerRadius
import com.android.systemui.shared.system.QuickStepContract
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock

/** Test for [FullscreenDrawParams] class. */
@SmallTest
@RunWith(AndroidJUnit4::class)
class FullscreenDrawParamsTest : FakeInvariantDeviceProfileTest() {
    private lateinit var params: FullscreenDrawParams

    @Before
    fun setup() {
        params = FullscreenDrawParams(context)
    }

    @Test
    fun setStartProgress_correctCornerRadiusForTablet() {
        initializeVarsForTablet()

        params.setProgress(fullscreenProgress = 0f, parentScale = 1.0f, taskViewScale = 1.0f)

        val expectedRadius = TaskCornerRadius.get(context)
        assertThat(params.currentCornerRadius).isEqualTo(expectedRadius)
    }

    @Test
    fun setFullProgress_correctCornerRadiusForTablet() {
        initializeVarsForTablet()

        params.setProgress(fullscreenProgress = 1.0f, parentScale = 1f, taskViewScale = 1f)

        val expectedRadius = QuickStepContract.getWindowCornerRadius(context)
        assertThat(params.currentCornerRadius).isEqualTo(expectedRadius)
    }

    @Test
    fun setStartProgress_correctCornerRadiusForPhone() {
        initializeVarsForPhone()

        params.setProgress(fullscreenProgress = 0f, parentScale = 1f, taskViewScale = 1f)

        val expectedRadius = TaskCornerRadius.get(context)
        assertThat(params.currentCornerRadius).isEqualTo(expectedRadius)
    }

    @Test
    fun setFullProgress_correctCornerRadiusForPhone() {
        initializeVarsForPhone()

        params.setProgress(fullscreenProgress = 1.0f, parentScale = 1f, taskViewScale = 1f)

        val expectedRadius = QuickStepContract.getWindowCornerRadius(context)
        assertThat(params.currentCornerRadius).isEqualTo(expectedRadius)
    }

    @Test
    fun setStartProgress_correctCornerRadiusForMultiDisplay() {
        val display1Context = mock<Context>()
        val display2Context = mock<Context>()
        val display1TaskRadius = TASK_CORNER_RADIUS + 1
        val display2TaskRadius = TASK_CORNER_RADIUS + 2

        val params =
            FullscreenDrawParams(
                context,
                taskCornerRadiusProvider = { context ->
                    when (context) {
                        display1Context -> display1TaskRadius
                        display2Context -> display2TaskRadius
                        else -> TASK_CORNER_RADIUS
                    }
                },
                windowCornerRadiusProvider = { 0f },
            )

        params.setProgress(fullscreenProgress = 0f, parentScale = 1f, taskViewScale = 1f)
        assertThat(params.currentCornerRadius).isEqualTo(TASK_CORNER_RADIUS)

        params.updateCornerRadius(display1Context)
        params.setProgress(fullscreenProgress = 0f, parentScale = 1f, taskViewScale = 1f)
        assertThat(params.currentCornerRadius).isEqualTo(display1TaskRadius)

        params.updateCornerRadius(display2Context)
        params.setProgress(fullscreenProgress = 0f, parentScale = 1f, taskViewScale = 1f)
        assertThat(params.currentCornerRadius).isEqualTo(display2TaskRadius)
    }

    @Test
    fun setFullProgress_correctCornerRadiusForMultiDisplay() {
        val display1Context = mock<Context>()
        val display2Context = mock<Context>()
        val display1WindowRadius = WINDOW_CORNER_RADIUS + 1
        val display2WindowRadius = WINDOW_CORNER_RADIUS + 2

        val params =
            FullscreenDrawParams(
                context,
                taskCornerRadiusProvider = { 0f },
                windowCornerRadiusProvider = { context ->
                    when (context) {
                        display1Context -> display1WindowRadius
                        display2Context -> display2WindowRadius
                        else -> WINDOW_CORNER_RADIUS
                    }
                },
            )

        params.setProgress(fullscreenProgress = 1f, parentScale = 1f, taskViewScale = 1f)
        assertThat(params.currentCornerRadius).isEqualTo(WINDOW_CORNER_RADIUS)

        params.updateCornerRadius(display1Context)
        params.setProgress(fullscreenProgress = 1f, parentScale = 1f, taskViewScale = 1f)
        assertThat(params.currentCornerRadius).isEqualTo(display1WindowRadius)

        params.updateCornerRadius(display2Context)
        params.setProgress(fullscreenProgress = 1f, parentScale = 1f, taskViewScale = 1f)
        assertThat(params.currentCornerRadius).isEqualTo(display2WindowRadius)
    }

    companion object {
        const val TASK_CORNER_RADIUS = 56f
        const val WINDOW_CORNER_RADIUS = 32f
    }
}
