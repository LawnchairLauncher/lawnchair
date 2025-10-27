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

package com.android.launcher3.model

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.os.UserHandle
import android.platform.test.rule.AllowedDevices
import android.platform.test.rule.DeviceProduct
import android.platform.test.rule.LimitDevicesRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.launcher3.AppFilter
import com.android.launcher3.DeviceProfile
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.icons.IconCache
import com.android.launcher3.model.data.PackageItemInfo
import com.android.launcher3.pm.UserCache
import com.android.launcher3.util.ActivityContextWrapper
import com.android.launcher3.util.ComponentKey
import com.android.launcher3.util.Executors
import com.android.launcher3.util.IntSet
import com.android.launcher3.util.PackageUserKey
import com.android.launcher3.util.WidgetUtils.createAppWidgetProviderInfo
import com.android.launcher3.widget.LauncherAppWidgetProviderInfo
import com.android.launcher3.widget.WidgetSections
import com.android.launcher3.widget.WidgetSections.NO_CATEGORY
import com.google.common.truth.Truth.assertThat
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.spy
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever

@AllowedDevices(allowed = [DeviceProduct.ROBOLECTRIC])
@RunWith(AndroidJUnit4::class)
class WidgetsModelTest {
    @Rule @JvmField val limitDevicesRule = LimitDevicesRule()
    @Rule @JvmField val mockitoRule: MockitoRule = MockitoJUnit.rule()

    @Mock private lateinit var appWidgetManager: AppWidgetManager
    @Mock private lateinit var iconCacheMock: IconCache

    private lateinit var context: Context
    private lateinit var idp: InvariantDeviceProfile
    private lateinit var underTest: WidgetsModel

    private var widgetSectionCategory: Int = 0
    private lateinit var appAPackage: String

    @Before
    fun setUp() {
        val appContext: Context = ApplicationProvider.getApplicationContext()
        idp = InvariantDeviceProfile.INSTANCE[appContext]

        context =
            object : ActivityContextWrapper(ApplicationProvider.getApplicationContext()) {
                override fun getSystemService(name: String): Any? {
                    if (name == "appwidget") {
                        return appWidgetManager
                    }
                    return super.getSystemService(name)
                }

                override fun getDeviceProfile(): DeviceProfile {
                    return idp.getDeviceProfile(applicationContext).copy(applicationContext)
                }
            }

        whenever(iconCacheMock.getTitleNoCache(any<LauncherAppWidgetProviderInfo>()))
            .thenReturn("title")

        val widgetToCategoryEntry: Map.Entry<ComponentName, IntSet> =
            WidgetSections.getWidgetsToCategory(context).entries.first()
        widgetSectionCategory = widgetToCategoryEntry.value.first()
        val appAWidgetComponent = widgetToCategoryEntry.key
        appAPackage = appAWidgetComponent.packageName

        whenever(appWidgetManager.getInstalledProvidersForProfile(any()))
            .thenReturn(
                listOf(
                    // First widget from widget sections xml
                    createAppWidgetProviderInfo(appAWidgetComponent),
                    // A widget that belongs to same package as the widget from widget sections
                    // xml, but, because it's not mentioned in xml, it would be included in its
                    // own package section.
                    createAppWidgetProviderInfo(
                        ComponentName.createRelative(appAPackage, APP_A_TEST_WIDGET_NAME)
                    ),
                    // A widget in different package (none of that app's widgets are in widget
                    // sections xml)
                    createAppWidgetProviderInfo(AppBTestWidgetComponent),
                    // A widget in different app that is meant to be hidden from picker
                    createAppWidgetProviderInfo(
                        AppCPinOnlyTestWidgetComponent,
                        /*hideFromPicker=*/ true,
                    ),
                )
            )

        val userCache = spy(UserCache.INSTANCE.get(context))
        whenever(userCache.userProfiles).thenReturn(listOf(UserHandle.CURRENT))

        underTest = WidgetsModel(context, idp, iconCacheMock, AppFilter(context))
    }

    @Test
    fun widgetsByPackageForPicker_treatsWidgetSectionsAsSeparatePackageItems() {
        loadWidgets()

        val packages: Map<PackageItemInfo, List<WidgetItem>> =
            underTest.widgetsByPackageItemForPicker

        // expect 3 package items (no app C as its widget is hidden from picker)
        // one for the custom section with widget from appA
        // one for package section for second widget from appA (that wasn't listed in xml)
        // and one for package section for appB
        assertThat(packages).hasSize(3)

        // Each package item when used as a key is distinct (i.e. even if appA is split into custom
        // package and owner package section, each of them is a distinct key). This ensures that
        // clicking on a custom widget section doesn't take user to app package section.
        val distinctPackageUserKeys =
            packages.map { PackageUserKey.fromPackageItemInfo(it.key) }.distinct()
        assertThat(distinctPackageUserKeys).hasSize(3)

        val customSections = packages.filter { it.key.widgetCategory == widgetSectionCategory }
        assertThat(customSections).hasSize(1)
        val widgetsInCustomSection = customSections.entries.first().value
        assertThat(widgetsInCustomSection).hasSize(1)

        val packageSections = packages.filter { it.key.widgetCategory == NO_CATEGORY }
        assertThat(packageSections).hasSize(2)

        // App A's package section
        val appAPackageSection = packageSections.filter { it.key.packageName == appAPackage }
        assertThat(appAPackageSection).hasSize(1)
        val widgetsInAppASection = appAPackageSection.entries.first().value
        assertThat(widgetsInAppASection).hasSize(1)

        // App B's package section
        val appBPackageSection =
            packageSections.filter { it.key.packageName == AppBTestWidgetComponent.packageName }
        assertThat(appBPackageSection).hasSize(1)
        val widgetsInAppBSection = appBPackageSection.entries.first().value
        assertThat(widgetsInAppBSection).hasSize(1)

        // No App C's package section - as the only widget hosted by it is hidden in picker
        val appCPackageSection =
            packageSections.filter {
                it.key.packageName == AppCPinOnlyTestWidgetComponent.packageName
            }
        assertThat(appCPackageSection).isEmpty()
    }

    @Test
    fun widgetComponentMap_returnsWidgets() {
        loadWidgets()

        val widgetsByComponentKey: Map<ComponentKey, WidgetItem> = underTest.widgetsByComponentKey

        // Has all widgets including ones not visible in picker
        assertThat(widgetsByComponentKey).hasSize(4)
        widgetsByComponentKey.forEach { entry ->
            assertThat(entry.key).isEqualTo(entry.value as ComponentKey)
        }
    }

    @Test
    fun widgetComponentMapForPicker_excludesWidgetsHiddenInPicker() {
        loadWidgets()

        val widgetsByComponentKey: Map<ComponentKey, WidgetItem> =
            underTest.widgetsByComponentKeyForPicker

        // Has all widgets excluding the appC's widget.
        assertThat(widgetsByComponentKey).hasSize(3)
        assertThat(
                widgetsByComponentKey.filter {
                    it.key.componentName == AppCPinOnlyTestWidgetComponent
                }
            )
            .isEmpty()
        // widgets mapped correctly
        widgetsByComponentKey.forEach { entry ->
            assertThat(entry.key).isEqualTo(entry.value as ComponentKey)
        }
    }

    @Test
    fun widgets_noData_returnsEmpty() {
        // no loadWidgets()

        assertThat(underTest.widgetsByComponentKey).isEmpty()
    }

    @Test
    fun getWidgetsByPackageItemForPicker_returnsACopyOfMap() {
        loadWidgets()

        val latch = CountDownLatch(1)
        Executors.MODEL_EXECUTOR.execute {
            var update = true

            // each "widgetsByPackageItem" read returns a different copy of the map held internally.
            // Modifying one shouldn't impact another.
            for ((_, _) in underTest.widgetsByPackageItemForPicker.entries) {
                underTest.widgetsByPackageItemForPicker.clear()
                if (update) { // trigger update
                    update = false
                    // Similarly, model could update its code independently while a client is
                    // iterating on the list.
                    underTest.update(/* packageUser= */ null)
                }
            }

            latch.countDown()
        }
        if (!latch.await(LOAD_WIDGETS_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            fail("Timed out waiting for test")
        }

        // No exception
    }

    private fun loadWidgets() {
        val latch = CountDownLatch(1)
        Executors.MODEL_EXECUTOR.execute {
            underTest.update(/* packageUser= */ null)
            latch.countDown()
        }
        if (!latch.await(LOAD_WIDGETS_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            fail("Timed out waiting widgets to load")
        }
    }

    companion object {
        // Another widget within app A
        private const val APP_A_TEST_WIDGET_NAME = "MyProvider"

        private val AppBTestWidgetComponent: ComponentName =
            ComponentName.createRelative("com.test.package", "TestProvider")

        private val AppCPinOnlyTestWidgetComponent: ComponentName =
            ComponentName.createRelative("com.testC.package", "PinOnlyTestProvider")

        private const val LOAD_WIDGETS_TIMEOUT_SECONDS = 2L
    }
}
