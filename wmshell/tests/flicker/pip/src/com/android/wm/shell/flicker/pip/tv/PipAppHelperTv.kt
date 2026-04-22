/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.wm.shell.flicker.pip.tv

import android.app.Instrumentation
import android.tools.traces.parsers.WindowManagerStateHelper
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import com.android.server.wm.flicker.helpers.PipAppHelper

/** Helper class for PIP app on AndroidTV */
open class PipAppHelperTv(instrumentation: Instrumentation) : PipAppHelper(instrumentation) {
    private val appSelector = By.pkg(packageName).depth(0)

    val ui: UiObject2?
        get() = uiDevice.findObject(appSelector)

    private fun focusOnObject(selector: BySelector): Boolean {
        // We expect all the focusable UI elements to be arranged in a way so that it is possible
        // to "cycle" over all them by clicking the D-Pad DOWN button, going back up to "the top"
        // from "the bottom".
        repeat(FOCUS_ATTEMPTS) {
            uiDevice.findObject(selector)?.apply { if (isFocusedOrHasFocusedChild) return true }
                ?: error("The object we try to focus on is gone.")

            uiDevice.pressDPadDown()
            uiDevice.waitForIdle()
        }
        return false
    }

    override fun clickObject(resId: String) {
        val selector = By.res(packageName, resId)
        focusOnObject(selector) || error("Could not focus on `$resId` object")
        uiDevice.pressDPadCenter()
    }

    @Deprecated(
        "Use PipAppHelper.closePipWindow(wmHelper) instead",
        ReplaceWith("closePipWindow(wmHelper)")
    )
    override fun closePipWindow() {
        uiDevice.closeTvPipWindow()
    }

    /** Taps the pip window and dismisses it by clicking on the X button. */
    override fun closePipWindow(wmHelper: WindowManagerStateHelper) {
        uiDevice.closeTvPipWindow()

        // Wait for animation to complete.
        wmHelper.StateSyncBuilder().withPipGone().withHomeActivityVisible().waitForAndVerify()
    }

    fun waitUntilClosed(): Boolean {
        val appSelector = By.pkg(packageName).depth(0)
        return uiDevice.wait(Until.gone(appSelector), APP_CLOSE_WAIT_TIME_MS)
    }

    companion object {
        private const val FOCUS_ATTEMPTS = 20
        private const val APP_CLOSE_WAIT_TIME_MS = 3_000L
    }
}
