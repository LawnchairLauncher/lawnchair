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
package com.android.wm.shell

import android.view.Display.DEFAULT_DISPLAY
import android.view.SurfaceControl
import android.window.DisplayAreaInfo
import android.window.DisplayAreaOrganizer
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.wm.shell.RootTaskDisplayAreaOrganizer.RootTaskDisplayAreaListener
import com.android.wm.shell.sysui.ShellInit
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests for [RootTaskDisplayAreaOrganizerTest].
 *
 * Build/Install/Run:
 *  atest WMShellUnitTests:RootTaskDisplayAreaOrganizerTest
 */
@SmallTest
@RunWith(AndroidJUnit4::class)
class RootTaskDisplayAreaOrganizerTest : ShellTestCase() {

    private val executor = TestShellExecutor()
    private val shellInit = ShellInit(executor)

    private lateinit var organizer: RootTaskDisplayAreaOrganizer

    @Before
    fun setUp() {
        organizer = RootTaskDisplayAreaOrganizer(executor, context, shellInit)
    }

    @Test
    fun registerListener_callsBackWithExistingRootTDA() {
        organizer.onDisplayAreaAppeared(createDisplayAreaInfo(FIRST_DISPLAY), SurfaceControl())
        organizer.onDisplayAreaAppeared(createDisplayAreaInfo(SECOND_DISPLAY), SurfaceControl())

        val listener = FakeRootTaskDisplayAreaListener()
        organizer.registerListener(FIRST_DISPLAY, listener)

        assertThat(listener.displayAreas).containsExactly(FIRST_DISPLAY)
    }

    @Test
    fun registerListener_otherForSameDisplay_callsBothBackWithExistingRootTDAs() {
        organizer.onDisplayAreaAppeared(createDisplayAreaInfo(FIRST_DISPLAY), SurfaceControl())
        organizer.onDisplayAreaAppeared(createDisplayAreaInfo(SECOND_DISPLAY), SurfaceControl())

        val listener1 = FakeRootTaskDisplayAreaListener()
        val listener2 = FakeRootTaskDisplayAreaListener()
        organizer.registerListener(SECOND_DISPLAY, listener1)
        organizer.registerListener(SECOND_DISPLAY, listener2)

        assertThat(listener1.displayAreas).containsExactly(SECOND_DISPLAY)
        assertThat(listener2.displayAreas).containsExactly(SECOND_DISPLAY)
    }

    @Test
    fun unregisterListener() {
        val listener = FakeRootTaskDisplayAreaListener()

        organizer.unregisterListener(FIRST_DISPLAY, listener)
        organizer.onDisplayAreaAppeared(createDisplayAreaInfo(FIRST_DISPLAY), SurfaceControl())

        assertThat(listener.displayAreas).doesNotContain(FIRST_DISPLAY)
    }

    private fun createDisplayAreaInfo(displayId: Int) = DisplayAreaInfo(
        MockToken().token(), displayId, DisplayAreaOrganizer.FEATURE_DEFAULT_TASK_CONTAINER
    )

    private class FakeRootTaskDisplayAreaListener : RootTaskDisplayAreaListener {
        val displayAreas = mutableListOf<Int>()

        override fun onDisplayAreaAppeared(displayAreaInfo: DisplayAreaInfo) {
            displayAreas.add(displayAreaInfo.displayId)
        }
    }

    companion object {
        private const val FIRST_DISPLAY = DEFAULT_DISPLAY
        private const val SECOND_DISPLAY = 2
        private const val THIRD_DISPLAY = 3
    }
}