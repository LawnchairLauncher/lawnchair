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

import androidx.test.annotation.UiThreadTest
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import com.android.launcher3.BuildConfig.WIDGETS_ENABLED
import com.android.launcher3.util.ActivityContextWrapper
import com.android.launcher3.util.LauncherMultivalentJUnit
import com.android.launcher3.widget.LauncherWidgetHolder.FLAG_ACTIVITY_RESUMED
import com.android.launcher3.widget.LauncherWidgetHolder.FLAG_ACTIVITY_STARTED
import com.android.launcher3.widget.LauncherWidgetHolder.FLAG_STATE_IS_NORMAL
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.whenever

@SmallTest
@RunWith(LauncherMultivalentJUnit::class)
class LauncherWidgetHolderTest {
    private lateinit var widgetHolder: LauncherWidgetHolder

    @Before
    fun setUp() {
        assertTrue(WIDGETS_ENABLED)
        widgetHolder =
            LauncherWidgetHolder(ActivityContextWrapper(getInstrumentation().targetContext))
    }

    @After
    fun tearDown() {
        widgetHolder.destroy()
    }

    @Test
    fun widget_holder_start_listening() {
        val testView = mock(PendingAppWidgetHostView::class.java)
        widgetHolder.mViews[0] = testView
        widgetHolder.setListeningFlag(false)
        assertFalse(widgetHolder.isListening)
        widgetHolder.startListening()
        ListenableAppWidgetHost.widgetHolderExecutor.submit {}.get()
        getInstrumentation().waitForIdleSync()
        assertTrue(widgetHolder.isListening)
        verify(testView, times(1)).reInflate()
        widgetHolder.clearWidgetViews()
    }

    @Test
    fun holder_start_listening_after_activity_start() {
        widgetHolder.setShouldListenFlag(FLAG_STATE_IS_NORMAL or FLAG_ACTIVITY_RESUMED, true)
        widgetHolder.setActivityStarted(false)
        ListenableAppWidgetHost.widgetHolderExecutor.submit {}.get()
        assertFalse(widgetHolder.shouldListen(widgetHolder.mFlags.get()))
        widgetHolder.setActivityStarted(true)
        ListenableAppWidgetHost.widgetHolderExecutor.submit {}.get()
        assertTrue(widgetHolder.shouldListen(widgetHolder.mFlags.get()))
    }

    @Test
    fun holder_start_listening_after_activity_resume() {
        widgetHolder.setShouldListenFlag(FLAG_STATE_IS_NORMAL or FLAG_ACTIVITY_STARTED, true)
        widgetHolder.setActivityResumed(false)
        ListenableAppWidgetHost.widgetHolderExecutor.submit {}.get()
        assertFalse(widgetHolder.shouldListen(widgetHolder.mFlags.get()))
        widgetHolder.setActivityResumed(true)
        ListenableAppWidgetHost.widgetHolderExecutor.submit {}.get()
        assertTrue(widgetHolder.shouldListen(widgetHolder.mFlags.get()))
    }

    @Test
    fun holder_start_listening_after_state_normal() {
        widgetHolder.setShouldListenFlag(FLAG_ACTIVITY_RESUMED or FLAG_ACTIVITY_STARTED, true)
        widgetHolder.setStateIsNormal(false)
        ListenableAppWidgetHost.widgetHolderExecutor.submit {}.get()
        assertFalse(widgetHolder.shouldListen(widgetHolder.mFlags.get()))
        widgetHolder.setStateIsNormal(true)
        ListenableAppWidgetHost.widgetHolderExecutor.submit {}.get()
        assertTrue(widgetHolder.shouldListen(widgetHolder.mFlags.get()))
    }

    @Test
    @UiThreadTest
    fun widget_holder_create_view() {
        val mockProviderInfo = mock(LauncherAppWidgetProviderInfo::class.java)
        doReturn(false).whenever(mockProviderInfo).isCustomWidget
        assertEquals(0, widgetHolder.mViews.size())
        widgetHolder.createView(APP_WIDGET_ID, mockProviderInfo)
        assertEquals(1, widgetHolder.mViews.size())
        assertEquals(APP_WIDGET_ID, widgetHolder.mViews.get(0).appWidgetId)
        widgetHolder.deleteAppWidgetId(APP_WIDGET_ID)
        assertEquals(0, widgetHolder.mViews.size())
    }

    @Test
    fun holder_add_provider_change_listener() {
        val listener = ListenableAppWidgetHost.ProviderChangedListener {}
        widgetHolder.addProviderChangeListener(listener)
        getInstrumentation().waitForIdleSync()
        assertEquals(1, widgetHolder.mProviderChangedListeners.size)
        assertSame(widgetHolder.mProviderChangedListeners.first(), listener)
        widgetHolder.removeProviderChangeListener(listener)
    }

    @Test
    fun holder_remove_provider_change_listener() {
        val listener = ListenableAppWidgetHost.ProviderChangedListener {}
        widgetHolder.addProviderChangeListener(listener)
        widgetHolder.removeProviderChangeListener(listener)
        getInstrumentation().waitForIdleSync()
        assertEquals(0, widgetHolder.mProviderChangedListeners.size)
    }

    @Test
    fun widget_holder_stop_listening() {
        widgetHolder.setListeningFlag(true)
        assertTrue(widgetHolder.isListening)
        widgetHolder.stopListening()
        ListenableAppWidgetHost.widgetHolderExecutor.submit {}.get()
        assertFalse(widgetHolder.isListening)
    }

    companion object {
        private const val APP_WIDGET_ID = 0
    }
}
