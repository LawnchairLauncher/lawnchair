/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.wm.shell.flicker.pip.common

import android.app.Instrumentation
import android.content.Intent
import android.platform.test.annotations.Presubmit
import android.tools.Rotation
import android.tools.flicker.legacy.FlickerBuilder
import android.tools.flicker.legacy.LegacyFlickerTest
import android.tools.flicker.rules.RemoveAllTasksButHomeRule.Companion.removeAllTasksButHome
import android.tools.helpers.WindowUtils
import android.tools.traces.component.ComponentNameMatcher
import com.android.server.wm.flicker.helpers.PipAppHelper
import com.android.server.wm.flicker.helpers.setRotation
import com.android.server.wm.flicker.testapp.ActivityOptions
import com.android.wm.shell.flicker.BaseTest
import com.google.common.truth.Truth
import org.junit.Test

abstract class PipTransition(flicker: LegacyFlickerTest) : BaseTest(flicker) {
    protected val pipApp = PipAppHelper(instrumentation)
    protected val displayBounds = WindowUtils.getDisplayBounds(flicker.scenario.startRotation)
    protected val broadcastActionTrigger = BroadcastActionTrigger(instrumentation)

    // Helper class to process test actions by broadcast.
    protected class BroadcastActionTrigger(private val instrumentation: Instrumentation) {
        private fun createIntentWithAction(broadcastAction: String): Intent {
            return Intent(broadcastAction).setFlags(Intent.FLAG_RECEIVER_FOREGROUND)
        }

        fun doAction(broadcastAction: String) {
            instrumentation.context.sendBroadcast(createIntentWithAction(broadcastAction))
        }

        companion object {
            // Corresponds to ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            @JvmStatic val ORIENTATION_LANDSCAPE = 0

            // Corresponds to ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            @JvmStatic val ORIENTATION_PORTRAIT = 1
        }
    }

    /** Defines the transition used to run the test */
    protected open val thisTransition: FlickerBuilder.() -> Unit = {}

    override val transition: FlickerBuilder.() -> Unit
        get() = {
            defaultSetup(this)
            defaultEnterPip(this)
            thisTransition(this)
            defaultTeardown(this)
        }

    /** Defines the default setup steps required by the test */
    protected open val defaultSetup: FlickerBuilder.() -> Unit = {
        setup {
            setRotation(Rotation.ROTATION_0)
            removeAllTasksButHome()
        }
    }

    /** Defines the default method of entering PiP */
    protected open val defaultEnterPip: FlickerBuilder.() -> Unit = {
        setup {
            pipApp.launchViaIntentAndWaitForPip(
                wmHelper,
                stringExtras = mapOf(ActivityOptions.Pip.EXTRA_ENTER_PIP to "true")
            )
        }
    }

    /** Defines the default teardown required to clean up after the test */
    protected open val defaultTeardown: FlickerBuilder.() -> Unit = {
        teardown { pipApp.exit(wmHelper) }
    }

    @Presubmit
    @Test
    open fun hasAtMostOnePipDismissOverlayWindow() {
        val matcher = ComponentNameMatcher("", "pip-dismiss-overlay")
        flicker.assertWm {
            val overlaysPerState =
                trace.entries.map { entry ->
                    entry.windowStates.count { window -> matcher.windowMatchesAnyOf(window) } <= 1
                }

            Truth.assertWithMessage("Number of dismiss overlays per state")
                .that(overlaysPerState)
                .doesNotContain(false)
        }
    }
}
