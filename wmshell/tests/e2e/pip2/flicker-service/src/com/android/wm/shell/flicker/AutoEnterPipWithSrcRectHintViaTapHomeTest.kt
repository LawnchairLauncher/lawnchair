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

package com.android.wm.shell.flicker

import android.app.Instrumentation
import android.platform.test.annotations.Postsubmit
import android.tools.NavBar
import android.tools.Rotation
import android.tools.flicker.AssertionInvocationGroup
import android.tools.flicker.FlickerConfig
import android.tools.flicker.annotation.ExpectedScenarios
import android.tools.flicker.annotation.FlickerConfigProvider
import android.tools.flicker.assertors.ComponentTemplate
import android.tools.flicker.assertors.assertions.LayerReduces
import android.tools.flicker.config.AssertionTemplates
import android.tools.flicker.config.FlickerConfig
import android.tools.flicker.config.FlickerConfigEntry
import android.tools.flicker.config.FlickerServiceConfig
import android.tools.flicker.config.ScenarioId
import android.tools.flicker.extractors.TaggedScenarioExtractorBuilder
import android.tools.flicker.junit.FlickerServiceJUnit4ClassRunner
import android.tools.traces.events.CujType
import android.tools.traces.parsers.WindowManagerStateHelper
import androidx.test.filters.RequiresDevice
import androidx.test.platform.app.InstrumentationRegistry
import com.android.launcher3.tapl.LauncherInstrumentation
import com.android.server.wm.flicker.helpers.PipAppHelper
import com.android.wm.shell.Utils
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RequiresDevice
@Postsubmit
@RunWith(FlickerServiceJUnit4ClassRunner::class)
class AutoEnterPipWithSrcRectHintViaTapHomeTest {
    private val tapl: LauncherInstrumentation = LauncherInstrumentation()

    @Rule
    @JvmField
    val testSetupRule = Utils.testSetupRule(NavBar.MODE_3BUTTON, Rotation.ROTATION_0)

    @Before
    fun setup() {
        pipApp.launchViaIntent(wmHelper)
        pipApp.setSourceRectHint()
        pipApp.enableAutoEnterForPipActivity()
    }

    @ExpectedScenarios(["PIP_ENTER_IN_BTN_NAV"])
    @Test
    fun tapHomeToAutoEnterPipWithSrcRectHint() {
        tapl.goHome()
    }

    @After
    fun teardown() {
        pipApp.exit(wmHelper)
    }

    companion object {
        val instrumentation: Instrumentation = InstrumentationRegistry.getInstrumentation()
        val wmHelper = WindowManagerStateHelper(instrumentation)
        val pipApp: PipAppHelper = PipAppHelper(instrumentation)
        private val pipAppComponentTemplate = ComponentTemplate("PIP_APP") { _ -> pipApp }

        private val PIP_ENTER_CUJ_EXTRACTOR = TaggedScenarioExtractorBuilder()
            .setTargetTag(CujType.CUJ_PIP_TRANSITION)
            .setAdditionalCujFilter {
                it.tag == "ENTER_PIP"
            }.build()

        private val PIP_ENTER_CUJ_CONFIG = FlickerConfigEntry(
            scenarioId = ScenarioId("PIP_ENTER_IN_BTN_NAV"),
            extractor = PIP_ENTER_CUJ_EXTRACTOR,
            assertions = AssertionTemplates.COMMON_ASSERTIONS + mapOf(
                LayerReduces(pipAppComponentTemplate) to AssertionInvocationGroup.BLOCKING
            ),
            enabled = true
        )

        @JvmStatic
        @FlickerConfigProvider
        fun flickerConfigProvider(): FlickerConfig =
            FlickerConfig().use(FlickerServiceConfig.DEFAULT).use(PIP_ENTER_CUJ_CONFIG)
    }
}