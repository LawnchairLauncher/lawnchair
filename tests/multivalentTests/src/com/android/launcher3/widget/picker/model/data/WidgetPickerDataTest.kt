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

package com.android.launcher3.widget.picker.model.data

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
import com.android.launcher3.LauncherSettings.Favorites.CONTAINER_WIDGETS_PREDICTION
import com.android.launcher3.icons.IconCache
import com.android.launcher3.icons.cache.CachedObject
import com.android.launcher3.model.WidgetItem
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.PackageItemInfo
import com.android.launcher3.util.ActivityContextWrapper
import com.android.launcher3.util.PackageUserKey
import com.android.launcher3.util.WidgetUtils
import com.android.launcher3.widget.LauncherAppWidgetProviderInfo
import com.android.launcher3.widget.PendingAddWidgetInfo
import com.android.launcher3.widget.model.WidgetsListBaseEntry
import com.android.launcher3.widget.model.WidgetsListContentEntry
import com.android.launcher3.widget.model.WidgetsListHeaderEntry
import com.android.launcher3.widget.picker.WidgetRecommendationCategory
import com.android.launcher3.widget.picker.WidgetRecommendationCategory.DEFAULT_WIDGET_RECOMMENDATION_CATEGORY
import com.android.launcher3.widget.picker.model.data.WidgetPickerDataUtils.findAllWidgetsForPackageUser
import com.android.launcher3.widget.picker.model.data.WidgetPickerDataUtils.findContentEntryForPackageUser
import com.android.launcher3.widget.picker.model.data.WidgetPickerDataUtils.withRecommendedWidgets
import com.android.launcher3.widget.picker.model.data.WidgetPickerDataUtils.withWidgets
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

// Tests for code / classes in WidgetPickerData file.

@RunWith(AndroidJUnit4::class)
@AllowedDevices(allowed = [DeviceProduct.ROBOLECTRIC])
class WidgetPickerDataTest {
    @Rule @JvmField val limitDevicesRule = LimitDevicesRule()
    @Rule @JvmField val mockitoRule: MockitoRule = MockitoJUnit.rule()

    @Mock private lateinit var iconCache: IconCache

    private lateinit var userHandle: UserHandle
    private lateinit var context: Context
    private lateinit var testInvariantProfile: InvariantDeviceProfile

    private lateinit var app1PackageItemInfo: PackageItemInfo
    private lateinit var app2PackageItemInfo: PackageItemInfo

    private lateinit var app1WidgetItem1: WidgetItem
    private lateinit var app1WidgetItem2: WidgetItem
    private lateinit var app2WidgetItem1: WidgetItem

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

        app1PackageItemInfo = packageItemInfoWithTitle(APP_1_PACKAGE_NAME, APP_1_PACKAGE_TITLE)
        app2PackageItemInfo = packageItemInfoWithTitle(APP_2_PACKAGE_NAME, APP_2_PACKAGE_TITLE)

        app1WidgetItem1 = createWidgetItem(APP_1_PACKAGE_NAME, APP_1_PROVIDER_1_CLASS_NAME)
        app1WidgetItem2 = createWidgetItem(APP_1_PACKAGE_NAME, APP_1_PROVIDER_2_CLASS_NAME)
        app2WidgetItem1 = createWidgetItem(APP_2_PACKAGE_NAME, APP_2_PROVIDER_1_CLASS_NAME)
    }

    @Test
    fun withWidgets_returnsACopyWithProvidedWidgets() {
        // only app two
        val widgetPickerData = WidgetPickerData(allWidgets = appTwoWidgetsListBaseEntries())

        // update: only app 1 and default list set
        val newAllWidgets: List<WidgetsListBaseEntry> =
            appOneWidgetsListBaseEntries(includeWidgetTwo = true)
        val newDefaultWidgets: List<WidgetsListBaseEntry> =
            appOneWidgetsListBaseEntries(includeWidgetTwo = false)

        val newWidgetData = widgetPickerData.withWidgets(newAllWidgets, newDefaultWidgets)

        assertThat(newWidgetData.allWidgets).containsExactlyElementsIn(newAllWidgets)
        assertThat(newWidgetData.defaultWidgets).containsExactlyElementsIn(newDefaultWidgets)
    }

    @Test
    fun withWidgets_noExplicitDefaults_unsetsOld() {
        // only app two
        val widgetPickerData =
            WidgetPickerData(
                allWidgets = appTwoWidgetsListBaseEntries(),
                defaultWidgets = appTwoWidgetsListBaseEntries(),
            )

        val newWidgetData =
            widgetPickerData.withWidgets(allWidgets = appOneWidgetsListBaseEntries())

        assertThat(newWidgetData.allWidgets)
            .containsExactlyElementsIn(appOneWidgetsListBaseEntries())
        assertThat(newWidgetData.defaultWidgets).isEmpty() // previous values cleared.
    }

    @Test
    fun withRecommendedWidgets_returnsACopyWithProvidedRecommendedWidgets() {
        val widgetPickerData =
            WidgetPickerData(
                allWidgets =
                    buildList {
                        addAll(appOneWidgetsListBaseEntries())
                        addAll(appTwoWidgetsListBaseEntries())
                    },
                defaultWidgets = buildList { appTwoWidgetsListBaseEntries() },
            )
        val recommendations: List<ItemInfo> =
            listOf(
                PendingAddWidgetInfo(
                    app1WidgetItem1.widgetInfo,
                    CONTAINER_WIDGETS_PREDICTION,
                    CATEGORY_1,
                ),
                PendingAddWidgetInfo(
                    app2WidgetItem1.widgetInfo,
                    CONTAINER_WIDGETS_PREDICTION,
                    CATEGORY_2,
                ),
            )

        val updatedData = widgetPickerData.withRecommendedWidgets(recommendations)

        assertThat(updatedData.recommendations.keys).containsExactly(CATEGORY_1, CATEGORY_2)
        assertThat(updatedData.recommendations[CATEGORY_1]).containsExactly(app1WidgetItem1)
        assertThat(updatedData.recommendations[CATEGORY_2]).containsExactly(app2WidgetItem1)
    }

    @Test
    fun withRecommendedWidgets_noCategory_usesDefault() {
        val widgetPickerData =
            WidgetPickerData(
                allWidgets =
                    buildList {
                        addAll(appOneWidgetsListBaseEntries())
                        addAll(appTwoWidgetsListBaseEntries())
                    },
                defaultWidgets = buildList { appTwoWidgetsListBaseEntries() },
            )
        val recommendations: List<ItemInfo> =
            listOf(
                PendingAddWidgetInfo(app1WidgetItem1.widgetInfo, CONTAINER_WIDGETS_PREDICTION),
                PendingAddWidgetInfo(app2WidgetItem1.widgetInfo, CONTAINER_WIDGETS_PREDICTION),
            )

        val updatedData = widgetPickerData.withRecommendedWidgets(recommendations)

        assertThat(updatedData.recommendations.keys)
            .containsExactly(DEFAULT_WIDGET_RECOMMENDATION_CATEGORY)
        assertThat(updatedData.recommendations[DEFAULT_WIDGET_RECOMMENDATION_CATEGORY])
            .containsExactly(app1WidgetItem1, app2WidgetItem1)
    }

    @Test
    fun withRecommendedWidgets_emptyRecommendations_clearsOld() {
        val widgetPickerData =
            WidgetPickerData(
                allWidgets =
                    buildList {
                        addAll(appOneWidgetsListBaseEntries())
                        addAll(appTwoWidgetsListBaseEntries())
                    },
                defaultWidgets = buildList { appTwoWidgetsListBaseEntries() },
                recommendations = mapOf(CATEGORY_1 to listOf(app1WidgetItem1)),
            )

        val updatedData = widgetPickerData.withRecommendedWidgets(listOf())

        assertThat(updatedData.recommendations).isEmpty()
    }

    @Test
    fun withRecommendedWidgets_widgetNotInAllWidgets_filteredOut() {
        val widgetPickerData =
            WidgetPickerData(
                allWidgets =
                    buildList {
                        addAll(appOneWidgetsListBaseEntries(includeWidgetTwo = false))
                        addAll(appTwoWidgetsListBaseEntries())
                    },
                defaultWidgets = buildList { appTwoWidgetsListBaseEntries() },
            )

        val recommendations: List<ItemInfo> =
            listOf(
                PendingAddWidgetInfo(app1WidgetItem2.widgetInfo, CONTAINER_WIDGETS_PREDICTION),
                PendingAddWidgetInfo(app2WidgetItem1.widgetInfo, CONTAINER_WIDGETS_PREDICTION),
            )
        val updatedData = widgetPickerData.withRecommendedWidgets(recommendations)

        assertThat(updatedData.recommendations).hasSize(1)
        // no app1widget2
        assertThat(updatedData.recommendations.values.first()).containsExactly(app2WidgetItem1)
    }

    @Test
    fun findContentEntryForPackageUser_returnsCorrectEntry() {
        val widgetPickerData =
            WidgetPickerData(
                allWidgets =
                    buildList {
                        addAll(appOneWidgetsListBaseEntries())
                        addAll(appTwoWidgetsListBaseEntries())
                    },
                defaultWidgets = buildList { addAll(appTwoWidgetsListBaseEntries()) },
            )
        val app1PackageUserKey = PackageUserKey.fromPackageItemInfo(app1PackageItemInfo)

        val contentEntry = findContentEntryForPackageUser(widgetPickerData, app1PackageUserKey)

        assertThat(contentEntry).isNotNull()
        assertThat(contentEntry?.mPkgItem).isEqualTo(app1PackageItemInfo)
        assertThat(contentEntry?.mWidgets).hasSize(2)
    }

    @Test
    fun findContentEntryForPackageUser_fromDefaults_returnsEntryFromDefaultWidgets() {
        val widgetPickerData =
            WidgetPickerData(
                allWidgets =
                    buildList {
                        addAll(appOneWidgetsListBaseEntries())
                        addAll(appTwoWidgetsListBaseEntries())
                    },
                defaultWidgets =
                    buildList { addAll(appOneWidgetsListBaseEntries(includeWidgetTwo = false)) },
            )
        val app1PackageUserKey = PackageUserKey.fromPackageItemInfo(app1PackageItemInfo)

        val contentEntry =
            findContentEntryForPackageUser(
                widgetPickerData = widgetPickerData,
                packageUserKey = app1PackageUserKey,
                fromDefaultWidgets = true,
            )

        assertThat(contentEntry).isNotNull()
        assertThat(contentEntry?.mPkgItem).isEqualTo(app1PackageItemInfo)
        // only one widget (since default widgets had only one widget for app A
        assertThat(contentEntry?.mWidgets).hasSize(1)
    }

    @Test
    fun findContentEntryForPackageUser_noMatch_returnsNull() {
        val app2PackageUserKey = PackageUserKey.fromPackageItemInfo(app2PackageItemInfo)
        val widgetPickerData =
            WidgetPickerData(allWidgets = buildList { addAll(appOneWidgetsListBaseEntries()) })

        val contentEntry = findContentEntryForPackageUser(widgetPickerData, app2PackageUserKey)

        assertThat(contentEntry).isNull()
    }

    @Test
    fun findAllWidgetsForPackageUser_returnsListOfWidgets() {
        val app1PackageUserKey = PackageUserKey.fromPackageItemInfo(app1PackageItemInfo)
        val widgetPickerData =
            WidgetPickerData(
                allWidgets =
                    buildList {
                        addAll(appOneWidgetsListBaseEntries())
                        addAll(appTwoWidgetsListBaseEntries())
                    },
                defaultWidgets =
                    buildList { addAll(appOneWidgetsListBaseEntries(includeWidgetTwo = false)) },
            )

        val widgets = findAllWidgetsForPackageUser(widgetPickerData, app1PackageUserKey)

        // both widgets returned irrespective of default widgets list
        assertThat(widgets).hasSize(2)
    }

    @Test
    fun findAllWidgetsForPackageUser_noMatch_returnsEmptyList() {
        val widgetPickerData =
            WidgetPickerData(allWidgets = buildList { addAll(appTwoWidgetsListBaseEntries()) })
        val app1PackageUserKey = PackageUserKey.fromPackageItemInfo(app1PackageItemInfo)

        val widgets = findAllWidgetsForPackageUser(widgetPickerData, app1PackageUserKey)

        assertThat(widgets).isEmpty()
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

    private fun appTwoWidgetsListBaseEntries(): List<WidgetsListBaseEntry> = buildList {
        val widgets = listOf(app2WidgetItem1)
        add(WidgetsListHeaderEntry.create(app2PackageItemInfo, APP_2_SECTION_NAME, widgets))
        add(WidgetsListContentEntry(app2PackageItemInfo, APP_2_SECTION_NAME, widgets))
    }

    private fun appOneWidgetsListBaseEntries(
        includeWidgetTwo: Boolean = true
    ): List<WidgetsListBaseEntry> = buildList {
        val widgets =
            if (includeWidgetTwo) {
                listOf(app1WidgetItem1, app1WidgetItem2)
            } else {
                listOf(app1WidgetItem1)
            }

        add(WidgetsListHeaderEntry.create(app1PackageItemInfo, APP_1_SECTION_NAME, widgets))
        add(WidgetsListContentEntry(app1PackageItemInfo, APP_1_SECTION_NAME, widgets))
    }

    companion object {
        private const val APP_1_PACKAGE_NAME = "com.example.app1"
        private const val APP_1_PACKAGE_TITLE = "App1"
        private const val APP_1_SECTION_NAME = "A" // for fast popup
        private const val APP_1_PROVIDER_1_CLASS_NAME = "app1Provider1"
        private const val APP_1_PROVIDER_2_CLASS_NAME = "app1Provider2"

        private const val APP_2_PACKAGE_NAME = "com.example.app2"
        private const val APP_2_PACKAGE_TITLE = "SomeApp2"
        private const val APP_2_SECTION_NAME = "S" // for fast popup
        private const val APP_2_PROVIDER_1_CLASS_NAME = "app2Provider1"

        private val CATEGORY_1 =
            WidgetRecommendationCategory(/* categoryTitleRes= */ 0, /* order= */ 0)
        private val CATEGORY_2 =
            WidgetRecommendationCategory(/* categoryTitleRes= */ 1, /* order= */ 1)
    }
}
