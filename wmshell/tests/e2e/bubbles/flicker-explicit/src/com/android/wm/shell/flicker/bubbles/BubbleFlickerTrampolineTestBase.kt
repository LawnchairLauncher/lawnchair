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

package com.android.wm.shell.flicker.bubbles

import android.tools.device.apphelpers.StandardAppHelper
import android.tools.traces.parsers.toFlickerComponent
import com.android.server.wm.flicker.helpers.SimpleAppHelper
import com.android.server.wm.flicker.testapp.ActivityOptions
import org.junit.Test

/**
 * The base class for Bubble flicker tests that bubble an app that launches via a trampoline task
 */
abstract class BubbleFlickerTrampolineTestBase : BubbleFlickerTestBase() {

    override val testApp = runningApp

    /** Verifies the transition from the trampoline activity to the running activity. */
    @Test
    fun trampolineActivityTransitions() {
        layersTraceSubject
            .skipUntilFirstAssertion()
            .isSplashScreenVisibleFor(trampolineApp)
            .then()
            // Check that trampoline starts the running app, running app can show a splash or not
            .isSplashScreenVisibleFor(runningApp, isOptional = true)
            .then()
            .isVisible(runningApp)
            .forAllEntries()
    }

    companion object {
        /**
         * Entry point for the app that is launching via trampoline
         */
        val trampolineApp: StandardAppHelper =
            SimpleAppHelper(
                instrumentation,
                launcherName = ActivityOptions.TrampolineStartActivity.LABEL,
                component = ActivityOptions.TrampolineStartActivity.COMPONENT.toFlickerComponent(),
            )

        /**
         * App that will actually be running after the trampoline finishes
         */
        val runningApp: StandardAppHelper =
            SimpleAppHelper(
                instrumentation,
                launcherName = ActivityOptions.TrampolineFinishActivity.LABEL,
                component = ActivityOptions.TrampolineFinishActivity.COMPONENT.toFlickerComponent(),
            )
    }
}
