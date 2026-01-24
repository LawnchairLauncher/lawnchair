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

package com.android.wm.shell.transition

import android.app.ActivityManager.RunningTaskInfo
import android.app.WindowConfiguration
import android.view.WindowManager
import android.window.TransitionInfo
import android.window.TransitionInfo.FLAG_TRANSLUCENT
import com.android.internal.R
import com.android.internal.policy.TransitionAnimation
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.TestRunningTaskInfoBuilder
import org.junit.Test
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mock
import org.mockito.Mockito.verify

class TransitionAnimationHelperTest : ShellTestCase() {

    @Mock
    lateinit var transitionAnimation: TransitionAnimation

    @Test
    fun loadAttributeAnimation_freeform_taskOpen_taskToBackChange_returnsMinimizeAnim() {
        val openChange = ChangeBuilder(WindowManager.TRANSIT_OPEN)
            .setTask(createTaskInfo(WindowConfiguration.WINDOWING_MODE_FREEFORM))
            .build()
        val toBackChange = ChangeBuilder(WindowManager.TRANSIT_TO_BACK)
            .setTask(createTaskInfo(WindowConfiguration.WINDOWING_MODE_FREEFORM))
            .build()
        val info = TransitionInfoBuilder(WindowManager.TRANSIT_OPEN)
            .addChange(openChange)
            .addChange(toBackChange)
            .build()

        loadAttributeAnimation(WindowManager.TRANSIT_OPEN, info, toBackChange)

        verify(transitionAnimation).loadDefaultAnimationAttr(
            eq(R.styleable.WindowAnimation_activityCloseExitAnimation), anyBoolean())
    }

    @Test
    fun loadAttributeAnimation_freeform_taskToFront_taskToFrontChange_returnsUnminimizeAnim() {
        val toFrontChange = ChangeBuilder(WindowManager.TRANSIT_TO_FRONT)
            .setTask(createTaskInfo(WindowConfiguration.WINDOWING_MODE_FREEFORM))
            .build()
        val info = TransitionInfoBuilder(WindowManager.TRANSIT_TO_FRONT)
            .addChange(toFrontChange)
            .build()

        loadAttributeAnimation(WindowManager.TRANSIT_TO_FRONT, info, toFrontChange)

        verify(transitionAnimation).loadDefaultAnimationAttr(
            eq(R.styleable.WindowAnimation_activityOpenEnterAnimation),
            /* translucent= */ anyBoolean())
    }

    @Test
    fun loadAttributeAnimation_fullscreen_taskOpen_returnsTaskOpenEnterAnim() {
        val openChange = ChangeBuilder(WindowManager.TRANSIT_OPEN)
            .setTask(createTaskInfo(WindowConfiguration.WINDOWING_MODE_FULLSCREEN))
            .build()
        val info = TransitionInfoBuilder(WindowManager.TRANSIT_OPEN).addChange(openChange).build()

        loadAttributeAnimation(WindowManager.TRANSIT_OPEN, info, openChange)

        verify(transitionAnimation).loadDefaultAnimationAttr(
            eq(R.styleable.WindowAnimation_taskOpenEnterAnimation),
            /* translucent= */ anyBoolean())
    }

    @Test
    fun loadAttributeAnimation_freeform_taskOpen_taskToBackChange_passesTranslucent() {
        val openChange = ChangeBuilder(WindowManager.TRANSIT_OPEN)
            .setTask(createTaskInfo(WindowConfiguration.WINDOWING_MODE_FREEFORM))
            .build()
        val toBackChange = ChangeBuilder(WindowManager.TRANSIT_TO_BACK)
            .setTask(createTaskInfo(WindowConfiguration.WINDOWING_MODE_FREEFORM))
            .setFlags(FLAG_TRANSLUCENT)
            .build()
        val info = TransitionInfoBuilder(WindowManager.TRANSIT_OPEN)
            .addChange(openChange)
            .addChange(toBackChange)
            .build()

        loadAttributeAnimation(WindowManager.TRANSIT_OPEN, info, toBackChange)

        verify(transitionAnimation).loadDefaultAnimationAttr(
            eq(R.styleable.WindowAnimation_activityCloseExitAnimation),
            /* translucent= */ eq(true))
    }

    private fun loadAttributeAnimation(
        @WindowManager.TransitionType type: Int,
        info: TransitionInfo,
        change: TransitionInfo.Change,
        wallpaperTransit: Int = TransitionAnimation.WALLPAPER_TRANSITION_NONE,
        isDreamTransition: Boolean = false,
    ) {
        TransitionAnimationHelper.loadAttributeAnimation(
            type, info, change, wallpaperTransit, transitionAnimation, isDreamTransition)
    }

    private fun createTaskInfo(windowingMode: Int): RunningTaskInfo {
        val taskInfo = TestRunningTaskInfoBuilder()
            .setWindowingMode(windowingMode)
            .build()
        return taskInfo
    }
}