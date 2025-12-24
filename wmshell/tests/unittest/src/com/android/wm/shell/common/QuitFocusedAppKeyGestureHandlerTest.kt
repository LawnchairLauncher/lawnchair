/*
 * Copyright 2025 The Android Open Source Project
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

package com.android.wm.shell.common

import android.app.IActivityTaskManager
import android.hardware.input.InputManager
import android.hardware.input.InputManager.KeyGestureEventHandler
import android.hardware.input.KeyGestureEvent
import android.testing.AndroidTestingRunner
import android.view.KeyEvent
import androidx.test.filters.SmallTest
import com.android.dx.mockito.inline.extended.ExtendedMockito.doAnswer
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.desktopmode.DesktopModeKeyGestureHandler
import com.android.wm.shell.transition.FocusTransitionObserver
import java.util.Optional
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

/**
 * Test class for [QuitFocusedAppKeyGestureHandler]
 *
 * Usage: atest WMShellUnitTests:QuitFocusedAppKeyGestureHandler
 */
@SmallTest
@RunWith(AndroidTestingRunner::class)
class QuitFocusedAppKeyGestureHandlerTest : ShellTestCase() {

    private val inputManager = mock<InputManager>()
    private val displayController = mock<DisplayController>()
    private val lockTaskChangeListener = mock<LockTaskChangeListener>()
    private val desktopModeKeyHandler = mock<DesktopModeKeyGestureHandler>()
    private val activityTaskService = mock<IActivityTaskManager>()
    private val focusTransitionObserver = mock<FocusTransitionObserver>()
    private val mainExecutor = mock<ShellExecutor>()

    private lateinit var quitFocusedAppKeyGestureHandler: QuitFocusedAppKeyGestureHandler
    private lateinit var keyGestureEventHandler: KeyGestureEventHandler

    @Before
    fun setUp() {
        doAnswer {
            keyGestureEventHandler = (it.arguments[1] as KeyGestureEventHandler)
            null
        }.whenever(inputManager).registerKeyGestureEventHandler(any(), any())
        whenever(focusTransitionObserver.globallyFocusedTaskId).thenReturn(TASK_ID)
        quitFocusedAppKeyGestureHandler =
            QuitFocusedAppKeyGestureHandler(
                context,
                inputManager,
                displayController,
                lockTaskChangeListener,
                Optional.of(desktopModeKeyHandler),
                activityTaskService,
                focusTransitionObserver,
                mainExecutor
            )
    }

    @Test
    fun quitAppGesture_whenInDesktopMode_closesDesktopTask() {
        whenever(desktopModeKeyHandler.quitFocusedDesktopTask()).thenReturn(true)

        sendQuitAppGesture()

        verifyNoInteractions(lockTaskChangeListener)
        verifyNoInteractions(focusTransitionObserver)
        verifyNoInteractions(activityTaskService)
    }

    @Test
    fun quitAppGesture_whenInLockTaskMode_doesNothing() {
        whenever(desktopModeKeyHandler.quitFocusedDesktopTask()).thenReturn(false)
        whenever(lockTaskChangeListener.isTaskLocked).thenReturn(true)

        sendQuitAppGesture()

        verifyNoInteractions(focusTransitionObserver)
        verifyNoInteractions(activityTaskService)
    }

    @Test
    fun quitAppGesture_whenNotInDesktopMode_fallsBackToActivityManagerToCloseFocusedTask() {
        whenever(desktopModeKeyHandler.quitFocusedDesktopTask()).thenReturn(false)
        whenever(lockTaskChangeListener.isTaskLocked).thenReturn(false)

        sendQuitAppGesture()

        verify(activityTaskService).removeTask(eq(TASK_ID))
    }

    private fun sendQuitAppGesture() {
        keyGestureEventHandler.handleKeyGestureEvent(
            KeyGestureEvent.Builder()
                .setKeyGestureType(KeyGestureEvent.KEY_GESTURE_TYPE_QUIT_FOCUSED_TASK)
                .setKeycodes(intArrayOf(KeyEvent.KEYCODE_ESCAPE))
                .setAction(KeyGestureEvent.ACTION_GESTURE_START)
                .build(), /* focusedToken =*/null
        )
        keyGestureEventHandler.handleKeyGestureEvent(
            KeyGestureEvent.Builder()
                .setKeyGestureType(KeyGestureEvent.KEY_GESTURE_TYPE_QUIT_FOCUSED_TASK)
                .setKeycodes(intArrayOf(KeyEvent.KEYCODE_ESCAPE))
                .setAction(KeyGestureEvent.ACTION_GESTURE_COMPLETE)
                .build(), /* focusedToken =*/null
        )
    }

    companion object {
        const val TASK_ID = 123
    }
}