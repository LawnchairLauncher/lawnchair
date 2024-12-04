/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.wm.shell.common.bubbles

import android.testing.AndroidTestingRunner
import androidx.test.filters.SmallTest
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.common.bubbles.BubbleBarLocation.DEFAULT
import com.android.wm.shell.common.bubbles.BubbleBarLocation.LEFT
import com.android.wm.shell.common.bubbles.BubbleBarLocation.RIGHT
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidTestingRunner::class)
class BubbleBarLocationTest : ShellTestCase() {

    @Test
    fun isOnLeft_rtlEnabled_defaultsToLeft() {
        assertThat(DEFAULT.isOnLeft(isRtl = true)).isTrue()
    }

    @Test
    fun isOnLeft_rtlDisabled_defaultsToRight() {
        assertThat(DEFAULT.isOnLeft(isRtl = false)).isFalse()
    }

    @Test
    fun isOnLeft_left_trueForAllLanguageDirections() {
        assertThat(LEFT.isOnLeft(isRtl = false)).isTrue()
        assertThat(LEFT.isOnLeft(isRtl = true)).isTrue()
    }

    @Test
    fun isOnLeft_right_falseForAllLanguageDirections() {
        assertThat(RIGHT.isOnLeft(isRtl = false)).isFalse()
        assertThat(RIGHT.isOnLeft(isRtl = true)).isFalse()
    }
}