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
import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import com.android.launcher3.Launcher
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.LauncherAppWidgetInfo
import com.android.launcher3.util.ActivityContextWrapper
import com.google.common.truth.Truth
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.any
import org.mockito.Mockito.verify
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@SmallTest
@RunWith(AndroidJUnit4::class)
class WidgetAddFlowHandlerTest {

    private val context: Context
        get() = ActivityContextWrapper(InstrumentationRegistry.getInstrumentation().targetContext)

    private val providerInfo =
        LauncherAppWidgetProviderInfo().apply {
            configure = InstrumentationRegistry.getInstrumentation().componentName
        }
    private val appWidgetHolder: LauncherWidgetHolder = mock<LauncherWidgetHolder>()
    private val launcher: Launcher =
        mock<Launcher>().also { whenever(it.appWidgetHolder).thenReturn(appWidgetHolder) }
    private val appWidgetInfo = LauncherAppWidgetInfo().apply { appWidgetId = 123 }
    private val requestCode = 123
    private val flowHandler = WidgetAddFlowHandler(providerInfo)

    @Test
    fun valuesShouldRemainTheSame_beforeAndAfter_parcelization() {
        with(Bundle()) {
            val testKey = "testKey"
            putParcelable(testKey, flowHandler)
            Truth.assertThat(getParcelable(testKey, WidgetAddFlowHandler::class.java))
                .isEqualTo(flowHandler)
        }
    }

    @Test
    fun describeContents_shouldReturn_0() {
        Truth.assertThat(flowHandler.describeContents()).isEqualTo(0)
    }

    @Test
    fun startBindFlow_shouldCorrectly_startLauncherFlowBinding() {
        flowHandler.startBindFlow(launcher, appWidgetInfo.appWidgetId, appWidgetInfo, requestCode)
        verify(launcher).setWaitingForResult(any())
        verify(appWidgetHolder)
            .startBindFlow(launcher, appWidgetInfo.appWidgetId, providerInfo, requestCode)
    }

    @Test
    fun startConfigActivityWithCustomAppWidgetId_shouldAskLauncherToStartConfigActivity() {
        flowHandler.startConfigActivity(
            launcher,
            appWidgetInfo.appWidgetId,
            ItemInfo(),
            requestCode
        )
        verify(launcher).setWaitingForResult(any())
        verify(appWidgetHolder)
            .startConfigActivity(launcher, appWidgetInfo.appWidgetId, requestCode)
    }

    @Test
    fun startConfigActivity_shouldAskLauncherToStartConfigActivity() {
        flowHandler.startConfigActivity(launcher, appWidgetInfo, requestCode)
        verify(launcher).setWaitingForResult(any())
        verify(appWidgetHolder)
            .startConfigActivity(launcher, appWidgetInfo.appWidgetId, requestCode)
    }

    @Test
    fun needsConfigure_returnsTrue_ifFlagsAndProviderInfoDetermineSo() {
        Truth.assertThat(flowHandler.needsConfigure()).isTrue()
    }

    @Test
    fun getProviderInfo_returnCorrectProviderInfo() {
        Truth.assertThat(flowHandler.getProviderInfo(context)).isSameInstanceAs(providerInfo)
    }
}
