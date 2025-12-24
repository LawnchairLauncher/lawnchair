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
package com.android.wm.shell.windowdecor

import android.graphics.Rect
import android.os.Binder
import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.SetFlagsRule
import android.testing.AndroidTestingRunner
import android.window.WindowContainerToken
import androidx.test.filters.SmallTest
import com.android.window.flags2.Flags
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock

/**
 * Tests for [WindowDecorationInsets].
 *
 * Build/Install/Run: atest WMShellUnitTests:WindowDecorationInsetsTest
 */
@SmallTest
@RunWith(AndroidTestingRunner::class)
class WindowDecorationInsetsTest {
    @JvmField @Rule val setFlagsRule = SetFlagsRule()

    private val token: WindowContainerToken = mock()
    private val owner = Binder()

    @Test
    fun `equals`() {
        val frame = Rect(0, 0, 1000, 80)
        val taskFrame = Rect(0, 0, 1000, 600)
        val insets1 =
            WindowDecorationInsets(
                token = token,
                owner = owner,
                frame = frame,
                taskFrame = taskFrame,
                boundingRects = emptyList(),
                flags = 0,
                shouldAddCaptionInset = true,
                excludedFromAppBounds = false,
            )
        val insets2 =
            WindowDecorationInsets(
                token = token,
                owner = owner,
                frame = frame,
                taskFrame = taskFrame,
                boundingRects = emptyList(),
                flags = 0,
                shouldAddCaptionInset = true,
                excludedFromAppBounds = false,
            )

        assertThat(insets1).isEqualTo(insets2)
    }

    @Test
    fun `equals with bounding rects`() {
        val frame = Rect(0, 0, 1000, 80)
        val taskFrame = Rect(0, 0, 1000, 600)
        val rects = listOf(Rect(0, 0, 300, 80), Rect(800, 0, 1000, 80))
        val insets1 =
            WindowDecorationInsets(
                token = token,
                owner = owner,
                frame = frame,
                taskFrame = taskFrame,
                boundingRects = rects,
                flags = 0,
                shouldAddCaptionInset = true,
                excludedFromAppBounds = false,
            )
        val insets2 =
            WindowDecorationInsets(
                token = token,
                owner = owner,
                frame = frame,
                taskFrame = taskFrame,
                boundingRects = rects,
                flags = 0,
                shouldAddCaptionInset = true,
                excludedFromAppBounds = false,
            )

        assertThat(insets1).isEqualTo(insets2)
    }

    @Test
    @EnableFlags(Flags.FLAG_RELATIVE_INSETS)
    fun `equals with different frame but same height`() {
        val taskFrame = Rect(0, 0, 1000, 600)
        val insets1 =
            WindowDecorationInsets(
                token = token,
                owner = owner,
                frame = Rect(0, 0, 1000, 80),
                taskFrame = taskFrame,
                boundingRects = emptyList(),
                flags = 0,
                shouldAddCaptionInset = true,
                excludedFromAppBounds = false,
            )
        val insets2 =
            WindowDecorationInsets(
                token = token,
                owner = owner,
                frame = Rect(100, 0, 1000, 80),
                taskFrame = taskFrame,
                boundingRects = emptyList(),
                flags = 0,
                shouldAddCaptionInset = true,
                excludedFromAppBounds = false,
            )

        assertThat(insets1).isEqualTo(insets2)
    }

    @Test
    fun `equals with different task frame but no app bounds exclusion`() {
        val frame = Rect(0, 0, 1000, 80)
        val insets1 =
            WindowDecorationInsets(
                token = token,
                owner = owner,
                frame = frame,
                taskFrame = Rect(0, 0, 1000, 600),
                boundingRects = emptyList(),
                flags = 0,
                shouldAddCaptionInset = true,
                excludedFromAppBounds = false,
            )
        val insets2 =
            WindowDecorationInsets(
                token = token,
                owner = owner,
                frame = frame,
                taskFrame = Rect(10, 0, 1010, 600),
                boundingRects = emptyList(),
                flags = 0,
                shouldAddCaptionInset = true,
                excludedFromAppBounds = false,
            )

        assertThat(insets1).isEqualTo(insets2)
    }
}
