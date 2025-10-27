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

package com.android.app.viewcapture

import android.content.Context
import android.content.Intent
import android.testing.AndroidTestingRunner
import android.view.WindowManager
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidTestingRunner::class)
@SmallTest
class ViewCaptureAwareWindowManagerTest {
    private val mContext: Context = InstrumentationRegistry.getInstrumentation().context
    private lateinit var mViewCaptureAwareWindowManager: ViewCaptureAwareWindowManager

    private val activityIntent = Intent(mContext, TestActivity::class.java)

    @get:Rule val activityScenarioRule = ActivityScenarioRule<TestActivity>(activityIntent)

    @Test
    fun testAddView_verifyStartCaptureCall() {
        activityScenarioRule.scenario.onActivity { activity ->
            mViewCaptureAwareWindowManager = ViewCaptureAwareWindowManager(mContext)

            val activityDecorView = activity.window.decorView
            // removing view since it is already added to view hierarchy on declaration
            mViewCaptureAwareWindowManager.removeView(activityDecorView)
            val viewCapture = ViewCaptureFactory.getInstance(mContext)

            mViewCaptureAwareWindowManager.addView(
                activityDecorView,
                activityDecorView.layoutParams as WindowManager.LayoutParams,
            )
            assertTrue(viewCapture.mIsStarted)
        }
    }
}
