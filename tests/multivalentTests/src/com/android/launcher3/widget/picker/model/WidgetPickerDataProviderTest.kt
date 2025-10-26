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

package com.android.launcher3.widget.picker.model

import android.content.ComponentName
import android.content.Context
import android.os.UserHandle
import android.platform.test.rule.AllowedDevices
import android.platform.test.rule.DeviceProduct
import android.platform.test.rule.LimitDevicesRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.LauncherAppState
import com.android.launcher3.LauncherSettings
import com.android.launcher3.icons.IconCache
import com.android.launcher3.icons.cache.CachedObject
import com.android.launcher3.model.WidgetItem
import com.android.launcher3.model.data.PackageItemInfo
import com.android.launcher3.util.ActivityContextWrapper
import com.android.launcher3.util.WidgetUtils
import com.android.launcher3.widget.LauncherAppWidgetProviderInfo
import com.android.launcher3.widget.PendingAddWidgetInfo
import com.android.launcher3.widget.model.WidgetsListBaseEntry
import com.android.launcher3.widget.model.WidgetsListContentEntry
import com.android.launcher3.widget.model.WidgetsListHeaderEntry
import com.android.launcher3.widget.picker.model.WidgetPickerDataProvider.WidgetPickerDataChangeListener
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.invocation.InvocationOnMock
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions

// Tests for the WidgetPickerDataProvider class

@RunWith(AndroidJUnit4::class)
@AllowedDevices(allowed = [DeviceProduct.ROBOLECTRIC])
class WidgetPickerDataProviderTest {
    @Rule @JvmField val limitDevicesRule = LimitDevicesRule()
    @Rule @JvmField val mockitoRule: MockitoRule = MockitoJUnit.rule()

    @Mock private lateinit var changeListener: WidgetPickerDataChangeListener

    @Mock private lateinit var iconCache: IconCache

    private lateinit var userHandle: UserHandle
    private lateinit var context: Context
    private lateinit var testInvariantProfile: InvariantDeviceProfile

    private lateinit var appWidgetItem: WidgetItem

    private lateinit var underTest: WidgetPickerDataProvider

    @Before
    fun setUp() {
        userHandle = UserHandle.CURRENT
        context = ActivityContextWrapper(ApplicationProvider.getApplicationContext())
        testInvariantProfile = LauncherAppState.getIDP(context)
        underTest = WidgetPickerDataProvider(context)
        doAnswer { invocation: InvocationOnMock ->
                val componentWithLabel = invocation.getArgument<Any>(0) as CachedObject
                componentWithLabel.getComponent().shortClassName
            }
            .`when`(iconCache)
            .getTitleNoCache(any<CachedObject>())

        appWidgetItem = createWidgetItem()
    }

    @After
    fun tearDown() {
        underTest.destroy()
    }

    @Test
    fun setWidgets_invokesTheListener_andUpdatedWidgetsAvailable() {
        assertThat(underTest.get().allWidgets).isEmpty()

        underTest.setChangeListener(changeListener)
        val allWidgets = appWidgetListBaseEntries()
        underTest.setWidgets(allWidgets = allWidgets)

        assertThat(underTest.get().allWidgets).containsExactlyElementsIn(allWidgets)
        verify(changeListener, times(1)).onWidgetsBound()
        verifyNoMoreInteractions(changeListener)
    }

    @Test
    fun setWidgetRecommendations_callsBackTheListener_andUpdatedRecommendationsAvailable() {
        underTest.setWidgets(allWidgets = appWidgetListBaseEntries())
        assertThat(underTest.get().recommendations).isEmpty()

        underTest.setChangeListener(changeListener)
        val recommendations =
            listOf(
                PendingAddWidgetInfo(
                    appWidgetItem.widgetInfo,
                    LauncherSettings.Favorites.CONTAINER_WIDGETS_PREDICTION,
                )
            )
        underTest.setWidgetRecommendations(recommendations)

        assertThat(underTest.get().recommendations).hasSize(1)
        verify(changeListener, times(1)).onRecommendedWidgetsBound()
        verifyNoMoreInteractions(changeListener)
    }

    @Test
    fun setChangeListener_null_noCallback() {
        underTest.setChangeListener(changeListener)
        underTest.setChangeListener(null) // reset

        underTest.setWidgets(allWidgets = appWidgetListBaseEntries())
        val recommendations =
            listOf(
                PendingAddWidgetInfo(
                    appWidgetItem.widgetInfo,
                    LauncherSettings.Favorites.CONTAINER_WIDGETS_PREDICTION,
                )
            )
        underTest.setWidgetRecommendations(recommendations)

        verifyNoMoreInteractions(changeListener)
    }

    private fun createWidgetItem(): WidgetItem {
        val providerInfo =
            WidgetUtils.createAppWidgetProviderInfo(
                ComponentName.createRelative(APP_PACKAGE_NAME, APP_PROVIDER_1_CLASS_NAME)
            )
        val widgetInfo = LauncherAppWidgetProviderInfo.fromProviderInfo(context, providerInfo)
        return WidgetItem(widgetInfo, testInvariantProfile, iconCache, context)
    }

    private fun appWidgetListBaseEntries(): List<WidgetsListBaseEntry> {
        val packageItemInfo = PackageItemInfo(APP_PACKAGE_NAME, userHandle)
        packageItemInfo.title = APP_PACKAGE_TITLE
        val widgets = listOf(appWidgetItem)

        return buildList {
            add(WidgetsListHeaderEntry.create(packageItemInfo, APP_SECTION_NAME, widgets))
            add(WidgetsListContentEntry(packageItemInfo, APP_SECTION_NAME, widgets))
        }
    }

    companion object {
        const val APP_PACKAGE_NAME = "com.example.app"
        const val APP_PACKAGE_TITLE = "SomeApp"
        const val APP_SECTION_NAME = "S" // for fast popup
        const val APP_PROVIDER_1_CLASS_NAME = "appProvider1"
    }
}
