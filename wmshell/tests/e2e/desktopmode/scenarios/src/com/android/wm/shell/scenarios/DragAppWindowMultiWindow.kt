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

package com.android.wm.shell.scenarios

import android.tools.PlatformConsts.DEFAULT_DISPLAY
import com.android.server.wm.flicker.helpers.DesktopModeAppHelper
import com.android.server.wm.flicker.helpers.MailAppHelper
import com.android.wm.shell.shared.desktopmode.DesktopConfig
import com.android.wm.shell.shared.desktopmode.DesktopState
import org.junit.After
import org.junit.Assume
import org.junit.Before
import org.junit.Ignore
import org.junit.Test

@Ignore("Test Base Class")
abstract class DragAppWindowMultiWindow : DragAppWindowScenarioTestBase()
{
    private val mailAppHelper = MailAppHelper(instrumentation)
    private val mailAppDesktopHelper = DesktopModeAppHelper(mailAppHelper)

    private val desktopConfig = DesktopConfig.fromContext(instrumentation.context)
    private val maxNum = desktopConfig.maxTaskLimit

    @Before
    fun setup() {
        Assume.assumeTrue(
            DesktopState.fromContext(instrumentation.context)
                .isDesktopModeSupportedOnDisplay(DEFAULT_DISPLAY)
        )
        mailAppDesktopHelper.enterDesktopMode(wmHelper, device)
        mailAppDesktopHelper.openTasks(wmHelper, numTasks = maxNum - 1)
    }

    @Test
    override fun dragAppWindow() {
        val (startX, startY) = getWindowDragStartCoordinate(mailAppHelper)

        mailAppDesktopHelper.dragWindow(
            startX,
            startY,
            endX = startX + 150,
            endY = startY + 150,
            wmHelper,
            device
        )
    }

    @After
    fun teardown() {
        mailAppDesktopHelper.exit(wmHelper)
    }
}
