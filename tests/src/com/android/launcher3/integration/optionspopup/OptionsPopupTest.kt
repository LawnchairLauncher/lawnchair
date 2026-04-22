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

package com.google.android.apps.nexuslauncher.integration.optionspopup

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import com.android.launcher3.Launcher
import com.android.launcher3.LauncherState
import com.android.launcher3.integration.util.LauncherActivityScenarioRule
import com.android.launcher3.integration.util.events.ActivityTestEvents.createStateWaiter
import com.android.launcher3.views.OptionsPopupView
import com.google.common.truth.Truth.assertWithMessage
import java.util.concurrent.TimeUnit
import org.junit.Test
import org.junit.runner.RunWith

@MediumTest
@RunWith(AndroidJUnit4::class)
class OptionsPopupTest {

    companion object {
        val APPS_OPTION_LABEL = "Apps list"
    }

    var targetContext: Context = getInstrumentation().targetContext

    var launcherActivity = LauncherActivityScenarioRule<Launcher>(targetContext)

    @Test
    fun `test long press Apps option`() {
        val allAppsStateWaiter = launcherActivity.createStateWaiter(LauncherState.ALL_APPS)
        launcherActivity.executeOnLauncher {
            val appsOption = OptionsPopupView.getOptions(it).find { it.label == APPS_OPTION_LABEL }
            assertWithMessage("No option for apps found").that(appsOption).isNotNull()
            appsOption!!.clickListener.onLongClick(it.rootView)
        }
        allAppsStateWaiter.waitForSignal(TimeUnit.SECONDS.toMillis(10))
    }
}
