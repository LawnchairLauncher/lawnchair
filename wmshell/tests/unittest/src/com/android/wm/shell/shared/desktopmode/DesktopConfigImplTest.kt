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

package com.android.wm.shell.shared.desktopmode

import android.app.WindowConfiguration.WINDOWING_MODE_FREEFORM
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.platform.test.annotations.Presubmit
import androidx.test.filters.SmallTest
import com.android.window.flags.Flags
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.util.createTaskInfo
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.spy
import org.mockito.kotlin.whenever

/**
 * Test class for [DesktopConfigImpl].
 */
@SmallTest
@Presubmit
class DesktopConfigImplTest : ShellTestCase() {
    private lateinit var desktopConfig: DesktopConfig

    @Before
    fun setUp() {
        desktopConfig = spy(DesktopConfigImpl(context))
    }

    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_MODE)
    @DisableFlags(Flags.FLAG_ENABLE_OPAQUE_BACKGROUND_FOR_TRANSPARENT_WINDOWS)
    @Test
    fun shouldSetBackground_BTWFlagDisabled_freeformTaskAndFluid_returnsTrue() {
        val freeFormTaskInfo = createTaskInfo(deviceWindowingMode = WINDOWING_MODE_FREEFORM)

        setIsVeiledResizeEnabled(false)

        assertThat(desktopConfig.shouldSetBackground(freeFormTaskInfo)).isTrue()
    }

    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_MODE)
    @DisableFlags(Flags.FLAG_ENABLE_OPAQUE_BACKGROUND_FOR_TRANSPARENT_WINDOWS)
    @Test
    fun shouldSetBackground_BTWFlagDisabled_freeformTaskAndVeiled_returnsFalse() {
        val freeFormTaskInfo = createTaskInfo(deviceWindowingMode = WINDOWING_MODE_FREEFORM)

        setIsVeiledResizeEnabled(true)

        assertThat(desktopConfig.shouldSetBackground(freeFormTaskInfo)).isFalse()
    }

    @EnableFlags(
        Flags.FLAG_ENABLE_DESKTOP_WINDOWING_MODE,
        Flags.FLAG_ENABLE_OPAQUE_BACKGROUND_FOR_TRANSPARENT_WINDOWS,
    )
    @Test
    fun shouldSetBackground_BTWFlagEnabled_freeformTaskAndFluid_returnsTrue() {
        val freeFormTaskInfo = createTaskInfo(deviceWindowingMode = WINDOWING_MODE_FREEFORM)

        setIsVeiledResizeEnabled(false)

        assertThat(desktopConfig.shouldSetBackground(freeFormTaskInfo)).isTrue()
    }

    @EnableFlags(
        Flags.FLAG_ENABLE_DESKTOP_WINDOWING_MODE,
        Flags.FLAG_ENABLE_OPAQUE_BACKGROUND_FOR_TRANSPARENT_WINDOWS,
    )
    @Test
    fun shouldSetBackground_BTWFlagEnabled_windowModesTask_freeformTaskAndVeiled_returnsTrue() {
        val freeFormTaskInfo = createTaskInfo(deviceWindowingMode = WINDOWING_MODE_FREEFORM)

        setIsVeiledResizeEnabled(true)

        assertThat(desktopConfig.shouldSetBackground(freeFormTaskInfo)).isTrue()
    }

    private fun setIsVeiledResizeEnabled(enabled: Boolean) {
        whenever(desktopConfig.isVeiledResizeEnabled).thenReturn(enabled)
    }
}
