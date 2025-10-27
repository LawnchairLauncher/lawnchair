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

package com.android.launcher3.widget.model

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
import com.android.launcher3.icons.IconCache
import com.android.launcher3.icons.cache.CachedObject
import com.android.launcher3.model.WidgetItem
import com.android.launcher3.model.data.PackageItemInfo
import com.android.launcher3.util.ActivityContextWrapper
import com.android.launcher3.util.WidgetUtils
import com.android.launcher3.widget.LauncherAppWidgetProviderInfo
import com.google.common.truth.Truth.assertThat
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

@RunWith(AndroidJUnit4::class)
@AllowedDevices(allowed = [DeviceProduct.ROBOLECTRIC])
class WidgetsListBaseEntriesBuilderTest {
    @Rule @JvmField val limitDevicesRule = LimitDevicesRule()
    @Rule @JvmField val mockitoRule: MockitoRule = MockitoJUnit.rule()

    @Mock private lateinit var iconCache: IconCache

    private lateinit var userHandle: UserHandle
    private lateinit var context: Context
    private lateinit var testInvariantProfile: InvariantDeviceProfile
    private lateinit var allWidgets: Map<PackageItemInfo, List<WidgetItem>>
    private lateinit var underTest: WidgetsListBaseEntriesBuilder

    @Before
    fun setUp() {
        userHandle = UserHandle.CURRENT
        context = ActivityContextWrapper(ApplicationProvider.getApplicationContext())
        testInvariantProfile = LauncherAppState.getIDP(context)

        doAnswer { invocation: InvocationOnMock ->
                val componentWithLabel = invocation.getArgument<Any>(0) as CachedObject
                componentWithLabel.getComponent().shortClassName
            }
            .`when`(iconCache)
            .getTitleNoCache(any<CachedObject>())
        underTest = WidgetsListBaseEntriesBuilder(context)

        allWidgets =
            mapOf(
                // app 1
                packageItemInfoWithTitle(APP_1_PACKAGE_NAME, APP_1_PACKAGE_TITLE) to
                    listOf(
                        createWidgetItem(APP_1_PACKAGE_NAME, APP_1_PROVIDER_1_CLASS_NAME),
                        createWidgetItem(APP_1_PACKAGE_NAME, APP_1_PROVIDER_2_CLASS_NAME),
                    ),
                // app 2
                packageItemInfoWithTitle(APP_2_PACKAGE_NAME, APP_2_PACKAGE_TITLE) to
                    listOf(createWidgetItem(APP_2_PACKAGE_NAME, APP_2_PROVIDER_1_CLASS_NAME)),
                // app 3
                packageItemInfoWithTitle(APP_3_PACKAGE_NAME, APP_3_PACKAGE_TITLE) to
                    listOf(createWidgetItem(APP_3_PACKAGE_NAME, APP_3_PROVIDER_1_CLASS_NAME)),
            )
    }

    @Test
    fun widgetsListBaseEntriesBuilder_addsHeaderAndContentEntries_withCorrectSectionName() {
        val expectedWidgetsCountBySection =
            listOf(
                APP_1_EXPECTED_SECTION_NAME to 2,
                APP_2_EXPECTED_SECTION_NAME to 1,
                APP_3_EXPECTED_SECTION_NAME to 1,
            )

        val entries = underTest.build(allWidgets)

        assertThat(entries).hasSize(6)
        val headerEntrySectionAndWidgetSizes =
            entries.filterIsInstance<WidgetsListHeaderEntry>().map {
                it.mTitleSectionName to it.mWidgets.size
            }
        val contentEntrySectionAndWidgetSizes =
            entries.filterIsInstance<WidgetsListContentEntry>().map {
                it.mTitleSectionName to it.mWidgets.size
            }
        assertThat(headerEntrySectionAndWidgetSizes)
            .containsExactlyElementsIn(expectedWidgetsCountBySection)
        assertThat(contentEntrySectionAndWidgetSizes)
            .containsExactlyElementsIn(expectedWidgetsCountBySection)
    }

    @Test
    fun widgetsListBaseEntriesBuilder_withFilter_addsFilteredHeaderAndContentEntries() {
        val allowList = listOf(APP_1_PROVIDER_1_CLASS_NAME, APP_3_PROVIDER_1_CLASS_NAME)
        val expectedWidgetsCountBySection =
            listOf(
                APP_1_EXPECTED_SECTION_NAME to 1, // one widget filtered out
                APP_3_EXPECTED_SECTION_NAME to 1,
            )

        val entries =
            underTest.build(allWidgets) { w -> allowList.contains(w.componentName.shortClassName) }

        assertThat(entries).hasSize(4) // app 2 filtered out
        val headerEntrySectionAndWidgetSizes =
            entries.filterIsInstance<WidgetsListHeaderEntry>().map {
                it.mTitleSectionName to it.mWidgets.size
            }
        val contentEntrySectionAndWidgetSizes =
            entries.filterIsInstance<WidgetsListContentEntry>().map {
                it.mTitleSectionName to it.mWidgets.size
            }
        assertThat(headerEntrySectionAndWidgetSizes)
            .containsExactlyElementsIn(expectedWidgetsCountBySection)
        assertThat(contentEntrySectionAndWidgetSizes)
            .containsExactlyElementsIn(expectedWidgetsCountBySection)
    }

    private fun packageItemInfoWithTitle(packageName: String, title: String): PackageItemInfo {
        val packageItemInfo = PackageItemInfo(packageName, userHandle)
        packageItemInfo.title = title
        return packageItemInfo
    }

    private fun createWidgetItem(packageName: String, widgetProviderName: String): WidgetItem {
        val providerInfo =
            WidgetUtils.createAppWidgetProviderInfo(
                ComponentName.createRelative(packageName, widgetProviderName)
            )
        val widgetInfo = LauncherAppWidgetProviderInfo.fromProviderInfo(context, providerInfo)
        return WidgetItem(widgetInfo, testInvariantProfile, iconCache, context)
    }

    companion object {
        const val APP_1_PACKAGE_NAME = "com.example.app1"
        const val APP_1_PACKAGE_TITLE = "App1"
        const val APP_1_EXPECTED_SECTION_NAME = "A" // for fast popup
        const val APP_1_PROVIDER_1_CLASS_NAME = "app1Provider1"
        const val APP_1_PROVIDER_2_CLASS_NAME = "app1Provider2"

        const val APP_2_PACKAGE_NAME = "com.example.app2"
        const val APP_2_PACKAGE_TITLE = "SomeApp2"
        const val APP_2_EXPECTED_SECTION_NAME = "S" // for fast popup
        const val APP_2_PROVIDER_1_CLASS_NAME = "app2Provider1"

        const val APP_3_PACKAGE_NAME = "com.example.app3"
        const val APP_3_PACKAGE_TITLE = "OtherApp3"
        const val APP_3_EXPECTED_SECTION_NAME = "O" // for fast popup
        const val APP_3_PROVIDER_1_CLASS_NAME = "app3Provider1"
    }
}
