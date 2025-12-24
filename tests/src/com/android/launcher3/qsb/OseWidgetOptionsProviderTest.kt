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

package com.android.launcher3.qsb

import android.appwidget.AppWidgetProviderInfo
import android.appwidget.AppWidgetProviderInfo.WIDGET_FEATURE_RECONFIGURABLE
import android.content.ComponentName
import com.android.dx.mockito.inline.extended.ExtendedMockito.spyOn
import com.android.launcher3.util.LauncherMultivalentJUnit
import com.android.launcher3.util.MutableListenableRef
import com.android.launcher3.util.SandboxApplication
import com.android.launcher3.util.TestActivityContext
import com.android.launcher3.util.ui.TestViewHelpers
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnit
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.whenever

@RunWith(LauncherMultivalentJUnit::class)
class OseWidgetOptionsProviderTest {

    @get:Rule val sandboxContext = SandboxApplication()
    @get:Rule val mockitoRule = MockitoJUnit.rule()
    @get:Rule val context = TestActivityContext(sandboxContext)
    @Mock private lateinit var oseWidgetManager: OseWidgetManager

    private lateinit var underTest: OseWidgetOptionsProvider

    val infoWithoutConfig = TestViewHelpers.findWidgetProvider(false).apply { configure = null }
    private val testProviderInfo = MutableListenableRef<AppWidgetProviderInfo>(infoWithoutConfig)

    @Before
    fun setup() {
        doReturn(testProviderInfo).whenever(oseWidgetManager).providerInfo
        underTest = OseWidgetOptionsProvider(oseWidgetManager, context)
        spyOn(underTest)
    }

    @Test
    fun `appWidgetSupportsReconfigure returns false when providerInfo value is null`() {
        val nullValueProviderInfo = MutableListenableRef<AppWidgetProviderInfo?>(null)
        doReturn(nullValueProviderInfo).whenever(oseWidgetManager).providerInfo

        assertFalse(underTest.appWidgetSupportsReconfigure())
    }

    @Test
    fun `appWidgetSupportsReconfigure returns false when configure is null`() {
        doReturn(testProviderInfo).whenever(oseWidgetManager).providerInfo

        assertFalse(underTest.appWidgetSupportsReconfigure())
    }

    @Test
    fun `appWidgetSupportsReconfigure returns false when not reconfigurable`() {
        val infoNotReconfigurableConfig =
            TestViewHelpers.findWidgetProvider(true).apply {
                configure = ComponentName("test", "test")
                widgetFeatures = 0 // Not reconfigurable
            }
        testProviderInfo.dispatchValue(infoNotReconfigurableConfig)

        assertFalse(underTest.appWidgetSupportsReconfigure())
    }

    @Test
    fun `appWidgetSupportsReconfigure returns true when reconfigurable and configure is not null`() {
        val infoWithReconfigurableConfig =
            TestViewHelpers.findWidgetProvider(true).apply {
                configure = ComponentName("test", "test")
                widgetFeatures = WIDGET_FEATURE_RECONFIGURABLE
            }
        testProviderInfo.dispatchValue(infoWithReconfigurableConfig)

        assertTrue(underTest.appWidgetSupportsReconfigure())
    }

    @Test
    fun `appWidgetSupportsReconfigure handles other widget features correctly`() {
        val infoWithoutReconfigurableConfig =
            TestViewHelpers.findWidgetProvider(true).apply {
                configure = ComponentName("test", "test")
                // Other features present, but not reconfigurable
                widgetFeatures = 2 // Some other feature flag
            }
        testProviderInfo.dispatchValue(infoWithoutReconfigurableConfig)

        assertFalse(underTest.appWidgetSupportsReconfigure())
    }

    @Test
    fun `getOptionItems returns empty list when reconfigure is NOT supported`() {
        doReturn(false).`when`(underTest).appWidgetSupportsReconfigure()

        assertTrue(underTest.getOptionItems().isEmpty())
    }
}
