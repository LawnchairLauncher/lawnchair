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

import android.tools.Rotation
import org.junit.After
import org.junit.Ignore
import org.junit.Test

@Ignore("Test Base Class")
abstract class EnterDesktopWithDrag
constructor(
    val rotation: Rotation = Rotation.ROTATION_0,
    isResizeable: Boolean = true,
    isLandscapeApp: Boolean = true,
) : DesktopScenarioCustomAppTestBase(isResizeable, isLandscapeApp, rotation) {
    @Test
    open fun enterDesktopWithDrag() {
        // By default this method uses drag to desktop
        testApp.enterDesktopMode(wmHelper, device, shouldUseDragToDesktop = true)
    }

    @After
    fun teardown() {
        testApp.exit(wmHelper)
    }
}
