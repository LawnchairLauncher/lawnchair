/*
 * Copyright (C) 2022 The Android Open Source Project
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

import android.content.Intent
import android.media.permission.SafeCloseable
import android.testing.AndroidTestingRunner
import android.view.Choreographer
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import com.android.app.viewcapture.TestActivity.Companion.TEXT_VIEW_COUNT
import com.android.app.viewcapture.data.ExportedData
import junit.framework.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidTestingRunner::class)
class ViewCaptureTest {

    private val memorySize = 100
    private val initPoolSize = 15
    private val viewCapture by lazy {
        object :
            ViewCapture(memorySize, initPoolSize, Choreographer.getInstance(), MAIN_EXECUTOR) {}
    }

    private val activityIntent =
        Intent(InstrumentationRegistry.getInstrumentation().context, TestActivity::class.java)

    @get:Rule val activityScenarioRule = ActivityScenarioRule<TestActivity>(activityIntent)

    @Test
    fun testViewCaptureDumpsOneFrameAfterInvalidate() {
        activityScenarioRule.scenario.onActivity { activity ->
            Choreographer.getInstance().postFrameCallback {
                val closeable = startViewCaptureAndInvalidateNTimes(1, activity)
                val rootView = activity.findViewById<View>(android.R.id.content)
                val exportedData = viewCapture.getDumpTask(rootView).get().get()

                assertEquals(1, exportedData.frameDataList.size)
                verifyTestActivityViewHierarchy(exportedData)
                closeable.close()
            }
        }
    }

    @Test
    fun testViewCaptureDumpsCorrectlyAfterRecyclingStarted() {
        activityScenarioRule.scenario.onActivity { activity ->
            Choreographer.getInstance().postFrameCallback {
                val closeable = startViewCaptureAndInvalidateNTimes(memorySize + 5, activity)
                val rootView = activity.findViewById<View>(android.R.id.content)
                val exportedData = viewCapture.getDumpTask(rootView).get().get()

                // since ViewCapture MEMORY_SIZE is [viewCaptureMemorySize], only
                // [viewCaptureMemorySize] frames are exported, although the view is invalidated
                // [viewCaptureMemorySize + 5] times
                assertEquals(memorySize, exportedData.frameDataList.size)
                verifyTestActivityViewHierarchy(exportedData)
                closeable.close()
            }
        }
    }

    private fun startViewCaptureAndInvalidateNTimes(n: Int, activity: TestActivity): SafeCloseable {
        val rootView: View = activity.findViewById(android.R.id.content)
        val closeable: SafeCloseable = viewCapture.startCapture(rootView, "rootViewId")
        dispatchOnDraw(rootView, times = n)
        return closeable
    }

    private fun dispatchOnDraw(view: View, times: Int) {
        if (times > 0) {
            view.viewTreeObserver.dispatchOnDraw()
            dispatchOnDraw(view, times - 1)
        }
    }

    private fun verifyTestActivityViewHierarchy(exportedData: ExportedData) {
        for (frame in exportedData.frameDataList) {
            val testActivityRoot =
                frame.node // FrameLayout (android.R.id.content)
                    .childrenList
                    .first() // LinearLayout (set by setContentView())
            assertEquals(TEXT_VIEW_COUNT, testActivityRoot.childrenList.size)
            assertEquals(
                LinearLayout::class.qualifiedName,
                exportedData.getClassname(testActivityRoot.classnameIndex)
            )
            assertEquals(
                TextView::class.qualifiedName,
                exportedData.getClassname(testActivityRoot.childrenList.first().classnameIndex)
            )
        }
    }
}
