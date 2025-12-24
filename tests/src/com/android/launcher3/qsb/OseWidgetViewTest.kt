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

import android.appwidget.AppWidgetManager.INVALID_APPWIDGET_ID
import android.appwidget.AppWidgetProviderInfo
import android.widget.RemoteViews
import androidx.test.annotation.UiThreadTest
import com.android.dx.mockito.inline.extended.ExtendedMockito.spyOn
import com.android.launcher3.dagger.LauncherAppComponent
import com.android.launcher3.dagger.LauncherAppModule
import com.android.launcher3.dagger.LauncherAppSingleton
import com.android.launcher3.util.LauncherMultivalentJUnit
import com.android.launcher3.util.MutableListenableRef
import com.android.launcher3.util.SandboxApplication
import com.android.launcher3.util.TestActivityContext
import com.android.launcher3.util.ui.TestViewHelpers
import com.android.launcher3.views.OptionsPopupView.OptionItem
import dagger.BindsInstance
import dagger.Component
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnit
import org.mockito.kotlin.any
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@UiThreadTest
@RunWith(LauncherMultivalentJUnit::class)
class OseWidgetViewTest {

    @get:Rule val sandboxContext = SandboxApplication()
    @get:Rule val mockitoRule = MockitoJUnit.rule()
    @get:Rule val context = TestActivityContext(sandboxContext)

    @Mock lateinit var oseWidgetManager: OseWidgetManager
    @Mock lateinit var oseWidgetOptionsProvider: OseWidgetOptionsProvider
    @Mock lateinit var optionItem: OptionItem

    private lateinit var mVut: OseWidgetView

    private val widgetInfo = TestViewHelpers.findWidgetProvider(false)
    private val remoteView = RemoteViews(widgetInfo.provider.packageName, 0)
    private val mockProviderInfo = MutableListenableRef<AppWidgetProviderInfo>(widgetInfo)
    private val mockRemoteViews = MutableListenableRef(remoteView)

    @Before
    fun setUp() {
        sandboxContext.initDaggerComponent(
            DaggerOseWidgetViewTest_TestComponent.builder().bindOseWidgetManager(oseWidgetManager)
        )
        val activityContextComponent = context.activityComponent
        spyOn(activityContextComponent)
        doReturn(oseWidgetOptionsProvider)
            .whenever(activityContextComponent)
            .getOseWidgetOptionsProvider()
        mVut = OseWidgetView(context)
        spyOn(mVut)
        spyOn(mVut.closeActions)
        doNothing().whenever(mVut).setAppWidget(any(), any())

        doReturn(mockProviderInfo).whenever(oseWidgetManager).providerInfo
        doReturn(mockRemoteViews).whenever(oseWidgetManager).views
    }

    @Test
    fun when_view_attachedToWindow() {
        mVut.attachedToWindow()
        verify(mVut).setAppWidget(INVALID_APPWIDGET_ID, widgetInfo)
        verify(mVut).updateAppWidget(remoteView)
        verify(mVut.closeActions).executeAllAndClear()
        verify(mVut.closeActions, times(2)).add(any())
    }

    @Test
    fun when_providerInfo_changes() {
        mVut.attachedToWindow()
        verify(mVut).setAppWidget(INVALID_APPWIDGET_ID, widgetInfo)

        val newWidgetInfo = TestViewHelpers.findWidgetProvider(false)
        mockProviderInfo.dispatchValue(newWidgetInfo)

        verify(mVut).setAppWidget(INVALID_APPWIDGET_ID, newWidgetInfo)
        verify(mVut, times(1)).updateAppWidget(remoteView)
    }

    @Test
    fun when_remoteView_changes() {
        mVut.attachedToWindow()
        verify(mVut).updateAppWidget(remoteView)

        val newWidgetInfo = TestViewHelpers.findWidgetProvider(false)
        val newRemoteView = RemoteViews(newWidgetInfo.provider.packageName, 0)
        mockRemoteViews.dispatchValue(newRemoteView)

        verify(mVut, times(1)).setAppWidget(INVALID_APPWIDGET_ID, widgetInfo)
        verify(mVut).updateAppWidget(newRemoteView)
    }

    @Test
    fun when_providerInfo_changes_after_view_detachedFromWindow() {
        mVut.attachedToWindow()
        verify(mVut, times(1)).setAppWidget(INVALID_APPWIDGET_ID, widgetInfo)
        mVut.detachedFromWindow()
        verify(mVut.closeActions, times(2)).executeAllAndClear()

        val newWidgetInfo = TestViewHelpers.findWidgetProvider(false)
        mockProviderInfo.dispatchValue(newWidgetInfo)
        // setAppWidget is not called since view is detached even though providerInfo changes
        verify(mVut, times(1)).setAppWidget(any(), any())

        val anotherWidgetInfo = TestViewHelpers.findWidgetProvider(false)
        mockProviderInfo.dispatchValue(anotherWidgetInfo)
        // setAppWidget is not called since view is detached even though providerInfo changes
        verify(mVut, times(1)).setAppWidget(any(), any())
    }

    @Test
    fun when_remoteView_changes_after_view_detachedFromWindow() {
        mVut.attachedToWindow()
        verify(mVut, times(1)).updateAppWidget(remoteView)
        mVut.detachedFromWindow()
        verify(mVut.closeActions, times(2)).executeAllAndClear()

        val newWidgetInfo = TestViewHelpers.findWidgetProvider(false)
        val newRemoteView = RemoteViews(newWidgetInfo.provider.packageName, 0)
        mockRemoteViews.dispatchValue(newRemoteView)
        // updateAppWidget is not called since view is detached even though remoteView changes
        verify(mVut, times(1)).updateAppWidget(any())

        val anotherWidgetInfo = TestViewHelpers.findWidgetProvider(false)
        val anotherRemoteView = RemoteViews(anotherWidgetInfo.provider.packageName, 0)
        mockRemoteViews.dispatchValue(anotherRemoteView)
        // updateAppWidget is not called since view is detached even though remoteView changes
        verify(mVut, times(1)).updateAppWidget(any())
    }

    @Test
    fun when_view_longClicked_noOptionItems_returnsFalse() {
        doReturn(emptyList<OptionItem>()).whenever(oseWidgetOptionsProvider).getOptionItems()
        doNothing().whenever(mVut).showOptionsPopup(any(), any())
        assertFalse { mVut.onLongClick(mVut) }
    }

    @Test
    fun when_view_longClicked_optionItemsExist_returnsTrue() {
        doReturn(listOf(optionItem)).whenever(oseWidgetOptionsProvider).getOptionItems()
        doNothing().whenever(mVut).showOptionsPopup(any(), any())
        assertTrue { mVut.onLongClick(mVut) }
    }

    @LauncherAppSingleton
    @Component(modules = [LauncherAppModule::class])
    interface TestComponent : LauncherAppComponent {

        @Component.Builder
        interface Builder : LauncherAppComponent.Builder {
            @BindsInstance fun bindOseWidgetManager(manager: OseWidgetManager): Builder

            override fun build(): TestComponent
        }
    }
}
