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
package com.android.launcher3.uioverrides.touchcontrollers

import android.view.MotionEvent
import android.view.WindowManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.launcher3.Launcher
import com.android.launcher3.ui.AbstractLauncherUiTest
import junit.framework.Assert.assertEquals
import junit.framework.Assert.assertFalse
import junit.framework.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class StatusBarTouchControllerTest : AbstractLauncherUiTest() {
    @Before
    @Throws(Exception::class)
    fun setup() {
        super.setUp()
        initialize(this)
    }

    @Test
    fun interceptActionDown_canIntercept() {
        executeOnLauncher { launcher ->
            val underTest = StatusBarTouchController(launcher)
            assertFalse(underTest.mCanIntercept)
            val downEvent = MotionEvent.obtain(1, 1, MotionEvent.ACTION_DOWN, 0f, 0f, 0)

            underTest.onControllerInterceptTouchEvent(downEvent)

            assertTrue(underTest.mCanIntercept)
        }
    }

    @Test
    fun interceptVerticalActionMove_handledAndSetSlippery() {
        executeOnLauncher { launcher ->
            val underTest = StatusBarTouchController(launcher)
            val downEvent = MotionEvent.obtain(1, 1, MotionEvent.ACTION_DOWN, 0f, 0f, 0)
            underTest.onControllerInterceptTouchEvent(downEvent)
            val w = launcher.window
            assertEquals(0, w.attributes.flags and WindowManager.LayoutParams.FLAG_SLIPPERY)
            val moveEvent =
                MotionEvent.obtain(
                    2,
                    2,
                    MotionEvent.ACTION_MOVE,
                    underTest.mTouchSlop,
                    underTest.mTouchSlop + 10,
                    0
                )

            val handled = underTest.onControllerInterceptTouchEvent(moveEvent)

            assertTrue(handled)
            assertEquals(
                WindowManager.LayoutParams.FLAG_SLIPPERY,
                w.attributes.flags and WindowManager.LayoutParams.FLAG_SLIPPERY
            )
        }
    }

    @Test
    fun interceptHorizontalActionMove_not_handled() {
        executeOnLauncher { launcher ->
            val underTest = StatusBarTouchController(launcher)
            val downEvent = MotionEvent.obtain(1, 1, MotionEvent.ACTION_DOWN, 0f, 0f, 0)
            underTest.onControllerInterceptTouchEvent(downEvent)
            val moveEvent =
                MotionEvent.obtain(
                    2,
                    2,
                    MotionEvent.ACTION_MOVE,
                    underTest.mTouchSlop + 10,
                    underTest.mTouchSlop,
                    0
                )

            val handled = underTest.onControllerInterceptTouchEvent(moveEvent)

            assertFalse(handled)
        }
    }

    @Test
    fun interceptActionMoveAsFirstGestureEvent_notCrashedNorHandled() {
        executeOnLauncher { launcher ->
            val underTest = StatusBarTouchController(launcher)
            underTest.mCanIntercept = true
            val moveEvent = MotionEvent.obtain(2, 2, MotionEvent.ACTION_MOVE, 10f, 10f, 0)

            val handled = underTest.onControllerInterceptTouchEvent(moveEvent)

            assertFalse(handled)
        }
    }

    @Test
    fun handleActionUp_setNotSlippery() {
        executeOnLauncher { launcher: Launcher ->
            val underTest = StatusBarTouchController(launcher)
            underTest.mCanIntercept = true
            underTest.setWindowSlippery(true)
            val moveEvent = MotionEvent.obtain(2, 2, MotionEvent.ACTION_UP, 10f, 10f, 0)

            val handled = underTest.onControllerTouchEvent(moveEvent)

            assertTrue(handled)
            assertEquals(
                0,
                launcher.window.attributes.flags and WindowManager.LayoutParams.FLAG_SLIPPERY
            )
        }
    }

    @Test
    fun handleActionCancel_setNotSlippery() {
        executeOnLauncher { launcher ->
            val underTest = StatusBarTouchController(launcher)
            underTest.mCanIntercept = true
            underTest.setWindowSlippery(true)
            val moveEvent = MotionEvent.obtain(2, 2, MotionEvent.ACTION_CANCEL, 10f, 10f, 0)

            val handled = underTest.onControllerTouchEvent(moveEvent)

            assertTrue(handled)
            assertEquals(
                0,
                launcher.window.attributes.flags and WindowManager.LayoutParams.FLAG_SLIPPERY
            )
        }
    }
}
