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

package com.android.launcher3.desktop

import android.app.WindowConfiguration.ACTIVITY_TYPE_STANDARD
import android.app.WindowConfiguration.WINDOWING_MODE_FREEFORM
import android.content.Context
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.SetFlagsRule
import android.view.WindowManager.TRANSIT_OPEN
import android.view.WindowManager.TRANSIT_TO_FRONT
import android.window.TransitionFilter
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.quickstep.SystemUiProxy
import com.android.window.flags.Flags.FLAG_ENABLE_DESKTOP_APP_LAUNCH_TRANSITIONS_BUGFIX
import com.android.wm.shell.shared.desktopmode.DesktopModeStatus
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@SmallTest
@RunWith(AndroidJUnit4::class)
class DesktopAppLaunchTransitionManagerTest {

    @get:Rule val mSetFlagsRule = SetFlagsRule()

    private val context = mock<Context>()
    private val systemUiProxy = mock<SystemUiProxy>()
    private lateinit var transitionManager: DesktopAppLaunchTransitionManager

    @Before
    fun setUp() {
        whenever(context.resources).thenReturn(mock())
        whenever(DesktopModeStatus.canEnterDesktopMode(context)).thenReturn(true)
        transitionManager = DesktopAppLaunchTransitionManager(context, systemUiProxy)
    }

    @Test
    @EnableFlags(FLAG_ENABLE_DESKTOP_APP_LAUNCH_TRANSITIONS_BUGFIX)
    fun registerTransitions_appLaunchFlagEnabled_registersTransition() {
        transitionManager.registerTransitions()

        verify(systemUiProxy, times(1)).registerRemoteTransition(any(), any())
    }

    @Test
    @DisableFlags(FLAG_ENABLE_DESKTOP_APP_LAUNCH_TRANSITIONS_BUGFIX)
    fun registerTransitions_appLaunchFlagDisabled_doesntRegisterTransition() {
        transitionManager.registerTransitions()

        verify(systemUiProxy, times(0)).registerRemoteTransition(any(), any())
    }

    @Test
    @EnableFlags(FLAG_ENABLE_DESKTOP_APP_LAUNCH_TRANSITIONS_BUGFIX)
    fun registerTransitions_usesCorrectFilter() {
        transitionManager.registerTransitions()
        val filterArgumentCaptor = argumentCaptor<TransitionFilter>()

        verify(systemUiProxy, times(1))
            .registerRemoteTransition(any(), filterArgumentCaptor.capture())

        assertThat(filterArgumentCaptor.lastValue).isNotNull()
        assertThat(filterArgumentCaptor.lastValue.mTypeSet)
            .isEqualTo(intArrayOf(TRANSIT_OPEN, TRANSIT_TO_FRONT))
        assertThat(filterArgumentCaptor.lastValue.mRequirements).hasLength(1)
        val launchRequirement = filterArgumentCaptor.lastValue.mRequirements!![0]
        assertThat(launchRequirement.mModes).isEqualTo(intArrayOf(TRANSIT_OPEN, TRANSIT_TO_FRONT))
        assertThat(launchRequirement.mActivityType).isEqualTo(ACTIVITY_TYPE_STANDARD)
        assertThat(launchRequirement.mWindowingMode).isEqualTo(WINDOWING_MODE_FREEFORM)
    }
}
