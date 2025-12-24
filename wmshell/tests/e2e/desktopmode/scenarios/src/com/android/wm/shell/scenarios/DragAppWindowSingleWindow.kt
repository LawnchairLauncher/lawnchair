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

import com.android.server.wm.flicker.helpers.DesktopModeAppHelper
import com.android.server.wm.flicker.helpers.SimpleAppHelper
import org.junit.After
import org.junit.Before
import org.junit.Ignore
import org.junit.Test

@Ignore("Test Base Class")
abstract class DragAppWindowSingleWindow : DragAppWindowScenarioTestBase()
{
    private val simpleAppHelper = SimpleAppHelper(instrumentation)
    private val testApp = DesktopModeAppHelper(simpleAppHelper)

    @Before
    fun setup() {
        testApp.enterDesktopMode(wmHelper, device)
    }

    @Test
    override fun dragAppWindow() {
        val (startXTest, startYTest) = getWindowDragStartCoordinate(simpleAppHelper)
        testApp.dragWindow(startXTest, startYTest,
            endX = startXTest + 150, endY = startYTest + 150,
            wmHelper, device)
    }

    @After
    fun teardown() {
        testApp.exit(wmHelper)
    }
}
