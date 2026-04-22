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

package com.android.wm.shell.compatui.letterbox.lifecycle

import android.app.ActivityManager.RunningTaskInfo
import android.app.WindowConfiguration.WINDOWING_MODE_FREEFORM
import android.content.ComponentName
import android.content.Intent
import android.graphics.Point
import android.graphics.Rect
import android.testing.AndroidTestingRunner
import android.view.Display.DEFAULT_DISPLAY
import android.view.SurfaceControl
import android.view.WindowManager.TRANSIT_CLOSE
import android.view.WindowManager.TRANSIT_NONE
import android.view.WindowManager.TRANSIT_OPEN
import android.view.WindowManager.TRANSIT_PIP
import android.window.IWindowContainerToken
import android.window.TransitionInfo.Change
import android.window.TransitionInfo.ChangeFlags
import android.window.TransitionInfo.FLAG_NONE
import android.window.TransitionInfo.TransitionMode
import android.window.WindowContainerToken
import androidx.test.filters.SmallTest
import com.android.dx.mockito.inline.extended.ExtendedMockito.spyOn
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.compatui.letterbox.LetterboxKey
import com.android.wm.shell.compatui.letterbox.lifecycle.LetterboxLifecycleEventType.CLOSE
import com.android.wm.shell.compatui.letterbox.lifecycle.LetterboxLifecycleEventType.NONE
import com.android.wm.shell.compatui.letterbox.lifecycle.LetterboxLifecycleEventType.OPEN
import java.util.function.Consumer
import kotlin.test.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

/**
 * Tests for [LetterboxLifecycleControllerImpl].
 *
 * Build/Install/Run:
 *  atest WMShellUnitTests:LetterboxLifecycleEventTest
 */
@RunWith(AndroidTestingRunner::class)
@SmallTest
class LetterboxLifecycleEventTest : ShellTestCase() {

    @Test
    fun `Type is OPEN with open transition`() {
        runTestScenario { r ->
            r.configureChange(changeMode = TRANSIT_OPEN)
            r.createLetterboxLifecycleEvent()
            r.checkEventType(expected = OPEN)
        }
    }

    @Test
    fun `Type is CLOSE with close transition`() {
        runTestScenario { r ->
            r.configureChange(changeMode = TRANSIT_CLOSE)
            r.createLetterboxLifecycleEvent()
            r.checkEventType(expected = CLOSE)
        }
    }

    @Test
    fun `Type is NONE with no open or close transition`() {
        runTestScenario { r ->
            r.configureChange(changeMode = TRANSIT_PIP)
            r.createLetterboxLifecycleEvent()
            r.checkEventType(expected = NONE)
        }
    }

    @Test
    fun `Build correct TaskBounds from Change dim fields`() {
        runTestScenario { r ->
            r.configureChange(
                changeMode = TRANSIT_OPEN,
                endRelOffset = Point(20, 30),
                endAbsBounds = Rect(500, 0, 1500, 1000)
            )
            r.createLetterboxLifecycleEvent()
            r.checkEventType(expected = OPEN)
            r.checkTaskBounds(expected = Rect(20, 30, 1000, 1000))
        }
    }

    @Test
    fun `When not letterboxed letterboxBounds are null`() {
        runTestScenario { r ->
            r.configureChange(
                changeMode = TRANSIT_OPEN,
                changeTaskInfo = r.createTaskInfo().apply {
                    appCompatTaskInfo.isTopActivityLetterboxed = false
                    appCompatTaskInfo.topActivityLetterboxBounds = Rect(500, 0, 1500, 800)
                }
            )
            r.createLetterboxLifecycleEvent()
            r.checkLetterboxBounds(expected = null)
        }
    }

    @Test
    fun `When letterboxed gets correct letterboxBounds`() {
        runTestScenario { r ->
            r.configureChange(
                changeMode = TRANSIT_OPEN,
                changeTaskInfo = r.createTaskInfo().apply {
                    appCompatTaskInfo.isTopActivityLetterboxed = true
                    appCompatTaskInfo.topActivityLetterboxBounds = Rect(500, 0, 1500, 800)
                }
            )
            r.createLetterboxLifecycleEvent()
            r.checkLetterboxBounds(expected = Rect(500, 0, 1500, 800))
        }
    }

    @Test
    fun `Use correct token`() {
        runTestScenario { r ->
            val testToken: WindowContainerToken = mock()
            r.configureChange(
                changeMode = TRANSIT_OPEN,
                changeTaskInfo = r.createTaskInfo(
                    windowToken = testToken
                )
            )
            r.createLetterboxLifecycleEvent()
            r.checkLetterboxActivityToken(expected = testToken)
        }
    }

    @Test
    fun `Use correct Leash for creation`() {
        runTestScenario { r ->
            val testLeash: SurfaceControl = mock()
            r.configureChange(
                changeMode = TRANSIT_OPEN,
                leash = testLeash
            )
            r.createLetterboxLifecycleEvent()
            r.checkTaskLeash(expected = testLeash)
        }
    }

    @Test
    fun `Use correct letterboxKey`() {
        runTestScenario { r ->
            r.configureChange(
                changeMode = TRANSIT_OPEN,
                changeTaskInfo = r.createTaskInfo(
                    id = 37,
                    taskDisplayId = 28
                )
            )
            r.createLetterboxLifecycleEvent()
            r.checkDisplayId(expected = 28)
            r.checkTaskIdId(expected = 37)
            r.checkLetterboxKey(expected = LetterboxKey(displayId = 28, taskId = 37))
        }
    }

    /**
     * Runs a test scenario providing a Robot.
     */
    fun runTestScenario(consumer: Consumer<LetterboxLifecycleControllerImplRobotTest>) {
        val robot = LetterboxLifecycleControllerImplRobotTest()
        consumer.accept(robot)
    }

    class LetterboxLifecycleControllerImplRobotTest {

        private var currentChange: Change? = null
        private var event: LetterboxLifecycleEvent? = null

        fun configureChange(
            token: WindowContainerToken? = mock(),
            leash: SurfaceControl = mock(),
            @TransitionMode changeMode: Int = TRANSIT_NONE,
            parentToken: WindowContainerToken? = null,
            changeTaskInfo: RunningTaskInfo? = null,
            @ChangeFlags changeFlags: Int = FLAG_NONE,
            endRelOffset: Point = Point(0, 0),
            endAbsBounds: Rect = Rect()
        ) = Change(token, leash).apply {
            mode = changeMode
            parent = parentToken
            taskInfo = changeTaskInfo
            flags = changeFlags
        }.apply {
            currentChange = this
            spyOn(currentChange)
            doReturn(endRelOffset).`when`(currentChange)?.endRelOffset
            doReturn(endAbsBounds).`when`(currentChange)?.endAbsBounds
        }

        fun createTaskInfo(
            id: Int = 0,
            taskDisplayId: Int = DEFAULT_DISPLAY,
            windowingMode: Int = WINDOWING_MODE_FREEFORM,
            windowToken: WindowContainerToken = WindowContainerToken(mock<IWindowContainerToken>())
        ) =
            RunningTaskInfo().apply {
                taskId = id
                displayId = taskDisplayId
                configuration.windowConfiguration.windowingMode = windowingMode
                token = windowToken
                baseIntent = Intent().apply {
                    component = ComponentName("package", "component.name")
                }
            }

        fun createLetterboxLifecycleEvent() {
            event = currentChange?.toLetterboxLifecycleEvent()
        }

        fun checkEventType(expected: LetterboxLifecycleEventType) {
            assertEquals(expected, event?.type)
        }

        fun checkDisplayId(expected: Int) {
            assertEquals(expected, event?.displayId)
        }

        fun checkTaskIdId(expected: Int) {
            assertEquals(expected, event?.taskId)
        }

        fun checkTaskBounds(expected: Rect?) {
            assertEquals(expected, event?.taskBounds)
        }

        fun checkLetterboxBounds(expected: Rect?) {
            assertEquals(expected, event?.letterboxBounds)
        }

        fun checkLetterboxActivityToken(expected: WindowContainerToken?) {
            assertEquals(expected, event?.containerToken)
        }

        fun checkTaskLeash(expected: SurfaceControl?) {
            assertEquals(expected, event?.taskLeash)
        }

        fun checkLetterboxKey(expected: LetterboxKey) {
            assertEquals(expected, event?.letterboxKey())
        }
    }
}
