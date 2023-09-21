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

package com.android.app.viewcapture

import android.content.Context
import android.content.Intent
import android.media.permission.SafeCloseable
import android.provider.Settings
import android.testing.AndroidTestingRunner
import android.view.Choreographer
import android.view.View
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import com.android.app.viewcapture.SettingsAwareViewCapture.Companion.VIEW_CAPTURE_ENABLED
import com.android.app.viewcapture.ViewCapture.MAIN_EXECUTOR
import junit.framework.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidTestingRunner::class)
class SettingsAwareViewCaptureTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().context
    private val activityIntent = Intent(context, TestActivity::class.java)

    @get:Rule val activityScenarioRule = ActivityScenarioRule<TestActivity>(activityIntent)

    @Test
    fun do_not_capture_view_hierarchies_if_setting_is_disabled() {
        Settings.Global.putInt(context.contentResolver, VIEW_CAPTURE_ENABLED, 0)

        activityScenarioRule.scenario.onActivity { activity ->
            val viewCapture: ViewCapture =
                SettingsAwareViewCapture(context, Choreographer.getInstance(), MAIN_EXECUTOR)
            val rootView: View = activity.findViewById(android.R.id.content)

            val closeable: SafeCloseable = viewCapture.startCapture(rootView, "rootViewId")
            Choreographer.getInstance().postFrameCallback {
                rootView.viewTreeObserver.dispatchOnDraw()

                assertEquals(0, viewCapture.getDumpTask(
                        activity.findViewById(android.R.id.content)).get().get().frameDataList.size)
                closeable.close()
            }
        }
    }

    @Test
    fun capture_view_hierarchies_if_setting_is_enabled() {
        Settings.Global.putInt(context.contentResolver, VIEW_CAPTURE_ENABLED, 1)

        activityScenarioRule.scenario.onActivity { activity ->
            val viewCapture: ViewCapture =
                SettingsAwareViewCapture(context, Choreographer.getInstance(), MAIN_EXECUTOR)
            val rootView: View = activity.findViewById(android.R.id.content)

            val closeable: SafeCloseable = viewCapture.startCapture(rootView, "rootViewId")
            Choreographer.getInstance().postFrameCallback {
                rootView.viewTreeObserver.dispatchOnDraw()

                assertEquals(1, viewCapture.getDumpTask(activity.findViewById(
                        android.R.id.content)).get().get().frameDataList.size)

                closeable.close()
            }
        }
    }

    @Test
    fun getInstance_calledTwiceInARow_returnsSameObject() {
        assertEquals(
            SettingsAwareViewCapture.getInstance(context).hashCode(),
            SettingsAwareViewCapture.getInstance(context).hashCode()
        )
    }
}
