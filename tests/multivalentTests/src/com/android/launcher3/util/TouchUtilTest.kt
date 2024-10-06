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

package com.android.launcher3.util

import android.view.InputDevice
import android.view.MotionEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

/** Unit tests for [TouchUtil] */
@SmallTest
@RunWith(AndroidJUnit4::class)
class TouchUtilTest {

    @Test
    fun isMouseRightClickDownOrMove_onMouseRightButton_returnsTrue() {
        val ev = MotionEvent.obtain(200, 300, MotionEvent.ACTION_MOVE, 1.0f, 0.0f, 0)
        ev.source = InputDevice.SOURCE_MOUSE
        ev.buttonState = MotionEvent.BUTTON_SECONDARY

        assertThat(TouchUtil.isMouseRightClickDownOrMove(ev)).isTrue()
    }

    @Test
    fun isMouseRightClickDownOrMove_onMouseLeftButton_returnsFalse() {
        val ev = MotionEvent.obtain(200, 300, MotionEvent.ACTION_MOVE, 1.0f, 0.0f, 0)
        ev.source = InputDevice.SOURCE_MOUSE
        ev.buttonState = MotionEvent.BUTTON_PRIMARY

        assertThat(TouchUtil.isMouseRightClickDownOrMove(ev)).isFalse()
    }

    @Test
    fun isMouseRightClickDownOrMove_onMouseTertiaryButton_returnsFalse() {
        val ev = MotionEvent.obtain(200, 300, MotionEvent.ACTION_MOVE, 1.0f, 0.0f, 0)
        ev.source = InputDevice.SOURCE_MOUSE
        ev.buttonState = MotionEvent.BUTTON_TERTIARY

        assertThat(TouchUtil.isMouseRightClickDownOrMove(ev)).isFalse()
    }

    @Test
    fun isMouseRightClickDownOrMove_onDpadRightButton_returnsFalse() {
        val ev = MotionEvent.obtain(200, 300, MotionEvent.ACTION_MOVE, 1.0f, 0.0f, 0)
        ev.source = InputDevice.SOURCE_DPAD
        ev.buttonState = MotionEvent.BUTTON_SECONDARY

        assertThat(TouchUtil.isMouseRightClickDownOrMove(ev)).isFalse()
    }
}
