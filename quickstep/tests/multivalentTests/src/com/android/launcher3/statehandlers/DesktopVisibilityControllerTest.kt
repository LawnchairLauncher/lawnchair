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
import com.android.launcher3.util.DaggerSingletonTracker
import com.android.quickstep.SystemUiProxy
import com.android.window.flags2.Flags.FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND
import com.android.window.flags2.Flags.FLAG_ENABLE_MULTIPLE_DESKTOPS_FRONTEND
import com.android.wm.shell.shared.desktopmode.DesktopModeStatus
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
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

    @Before
    fun setUp() {
        whenever(context.resources).thenReturn(mock())
        whenever(DesktopModeStatus.enableMultipleDesktops(context)).thenReturn(true)
        desktopVisibilityController =
            DesktopVisibilityController(context, systemUiProxy, lifeCycleTracker)
    }

    @Test
    @EnableFlags(FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND, FLAG_ENABLE_MULTIPLE_DESKTOPS_FRONTEND)
    fun noCrashWhenCheckingNonExistentDisplay() {
        assertFalse(desktopVisibilityController.isInDesktopMode(displayId = 500))
        assertFalse(desktopVisibilityController.isInDesktopModeAndNotInOverview(displayId = 300))
    }
}
