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

package com.android.launcher3.statehandlers

import android.content.Context
import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.SetFlagsRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import com.android.launcher3.LauncherState
import com.android.launcher3.util.DaggerSingletonTracker
import com.android.quickstep.SystemUiProxy
import com.android.window.flags2.Flags.FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND
import com.android.window.flags2.Flags.FLAG_ENABLE_MULTIPLE_DESKTOPS_FRONTEND
import com.android.wm.shell.desktopmode.DisplayDeskState
import com.android.wm.shell.desktopmode.IDesktopTaskListener
import com.android.wm.shell.shared.desktopmode.DesktopModeStatus
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.nullableArgumentCaptor
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Tests the behavior of [DesktopVisibilityController] in regards to multiple desktops and multiple
 * displays.
 */
@SmallTest
@RunWith(AndroidJUnit4::class)
class DesktopVisibilityControllerTest {

    @get:Rule val setFlagsRule = SetFlagsRule()

    private val context = mock<Context>()
    private val systemUiProxy = mock<SystemUiProxy>()
    private val lifeCycleTracker = mock<DaggerSingletonTracker>()
    private lateinit var desktopVisibilityController: DesktopVisibilityController
    private val listenerCaptor = nullableArgumentCaptor<IDesktopTaskListener>()

    @Before
    fun setUp() {
        whenever(context.resources).thenReturn(mock())
        whenever(DesktopModeStatus.enableMultipleDesktops(context)).thenReturn(true)
        desktopVisibilityController =
            DesktopVisibilityController(context, systemUiProxy, lifeCycleTracker)
        verify(systemUiProxy).setDesktopTaskListener(listenerCaptor.capture())
    }

    @Test
    @EnableFlags(FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND, FLAG_ENABLE_MULTIPLE_DESKTOPS_FRONTEND)
    fun noCrashWhenCheckingNonExistentDisplay() {
        assertThat(desktopVisibilityController.isInDesktopMode(displayId = 500)).isFalse()
        assertThat(desktopVisibilityController.isInDesktopModeAndNotInOverview(displayId = 300))
            .isFalse()
    }

    @Test
    @EnableFlags(FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND, FLAG_ENABLE_MULTIPLE_DESKTOPS_FRONTEND)
    fun taskListenerConnects() {
        connectTaskListener()
    }

    @Test
    @EnableFlags(FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND, FLAG_ENABLE_MULTIPLE_DESKTOPS_FRONTEND)
    fun getActiveDeskIdIsAreCorrect() {
        connectTaskListener()

        assertThat(desktopVisibilityController.getActiveDeskId(FIRST_DISPLAY_ID))
            .isEqualTo(FIRST_DISPLAY_ACTIVE_DESK_ID)
        assertThat(desktopVisibilityController.getActiveDeskId(SECOND_DISPLAY_ID))
            .isEqualTo(SECOND_DISPLAY_ACTIVE_DESK_ID)
    }

    @Test
    @EnableFlags(FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND, FLAG_ENABLE_MULTIPLE_DESKTOPS_FRONTEND)
    fun isInDesktopModeIsCorrect() {
        connectTaskListener()

        assertThat(desktopVisibilityController.isInDesktopMode(FIRST_DISPLAY_ID)).isTrue()
        assertThat(desktopVisibilityController.isInDesktopMode(SECOND_DISPLAY_ID)).isTrue()
        assertThat(desktopVisibilityController.isInDesktopMode(NON_DESKTOP_DISPLAY_ID)).isFalse()
    }

    @Test
    @EnableFlags(FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND, FLAG_ENABLE_MULTIPLE_DESKTOPS_FRONTEND)
    fun launcherStateChangeUpdatesState() {
        connectTaskListener()

        assertThat(desktopVisibilityController.isInDesktopModeAndNotInOverview(FIRST_DISPLAY_ID))
            .isTrue()

        desktopVisibilityController.onLauncherStateChanged(FIRST_DISPLAY_ID, LauncherState.OVERVIEW)

        assertThat(desktopVisibilityController.isInDesktopModeAndNotInOverview(FIRST_DISPLAY_ID))
            .isFalse()

        desktopVisibilityController.onLauncherStateChanged(
            FIRST_DISPLAY_ID,
            LauncherState.BACKGROUND_APP,
        )

        assertThat(desktopVisibilityController.isInDesktopModeAndNotInOverview(FIRST_DISPLAY_ID))
            .isTrue()
    }

    private fun connectTaskListener() {
        val firstDisplay =
            DisplayDeskState().apply {
                displayId = FIRST_DISPLAY_ID
                activeDeskId = FIRST_DISPLAY_ACTIVE_DESK_ID
                deskIds = FIRST_DISPLAY_DESK_IDS
            }
        val secondDisplay =
            DisplayDeskState().apply {
                displayId = SECOND_DISPLAY_ID
                activeDeskId = SECOND_DISPLAY_ACTIVE_DESK_ID
                deskIds = SECOND_DISPLAY_DESK_IDS
            }
        val states = arrayOf(firstDisplay, secondDisplay)
        val listener = listenerCaptor.lastValue
        assertThat(listener).isNotNull()
        listener!!.onListenerConnected(states, /* canCreateDesks= */ true)
        getInstrumentation().waitForIdleSync()
        assertThat(desktopVisibilityController.canCreateDesks).isTrue()
    }

    companion object {
        private const val FIRST_DISPLAY_ID = 0
        private const val FIRST_DISPLAY_ACTIVE_DESK_ID = 0
        private val FIRST_DISPLAY_DESK_IDS = intArrayOf(0, 1, 2, 3, 4)
        private const val SECOND_DISPLAY_ID = 1
        private const val SECOND_DISPLAY_ACTIVE_DESK_ID = 5
        private val SECOND_DISPLAY_DESK_IDS = intArrayOf(5, 6)
        private const val NON_DESKTOP_DISPLAY_ID = 2
    }
}
