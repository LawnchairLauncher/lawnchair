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

package com.android.launcher3.widget

import android.content.Context
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import com.android.launcher3.util.ActivityContextWrapper
import com.google.common.truth.Truth
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class ListenableHostViewTest {

    private val context: Context
        get() = ActivityContextWrapper(InstrumentationRegistry.getInstrumentation().targetContext)

    @Test
    fun updateAppWidget_notifiesListeners() {
        val hostView = ListenableHostView(context)
        var wasNotifiedOfUpdate = false
        val updateListener = Runnable { wasNotifiedOfUpdate = true }
        hostView.addUpdateListener(updateListener)
        hostView.beginDeferringUpdates()
        hostView.updateAppWidget(null)
        Truth.assertThat(wasNotifiedOfUpdate).isTrue()
    }

    @Test
    fun onInitializeAccessibilityNodeInfo_correctlySetsClassName() {
        val hostView = ListenableHostView(context)
        val nodeInfo = AccessibilityNodeInfo()
        hostView.onInitializeAccessibilityNodeInfo(nodeInfo)
        Truth.assertThat(nodeInfo.className).isEqualTo(LauncherAppWidgetHostView::class.java.name)
    }
}
