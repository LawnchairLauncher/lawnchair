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

package com.android.wm.shell.flicker.pip.nonmatchparent

import android.platform.test.annotations.Presubmit
import android.tools.flicker.FlickerTest
import com.android.server.wm.flicker.helpers.BottomHalfPipAppHelper
import com.android.server.wm.flicker.helpers.PipAppHelper
import com.android.wm.shell.flicker.pip.common.EnterPipTransition
import org.junit.Test

/**
 * The base class to test enter PIP animation on bottom half activity.
 */
abstract class BottomHalfEnterPipTransition(flicker: FlickerTest) :
    EnterPipTransition(flicker)
{
    override val pipApp: PipAppHelper = BottomHalfPipAppHelper(
        instrumentation,
        useLaunchingActivity = true
    )

    @Presubmit
    @Test
    override fun pipWindowRemainInsideVisibleBounds() {
        // We only verify the start and end because we update the layout when
        // the BottomHalfPipActivity goes to PIP mode, which may lead to intermediate state that
        // the window frame may be out of the display frame.
        flicker.assertWmStart { visibleRegion(pipApp).coversAtMost(displayBounds) }
        flicker.assertLayersEnd { visibleRegion(pipApp).coversAtMost(displayBounds) }
    }
}