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

import android.app.prediction.AppTarget
import android.app.prediction.AppTargetEvent
import android.app.prediction.AppTargetId
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.os.Process.myUserHandle
import android.os.UserHandle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.launcher3.DeviceProfile
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.LauncherAppState
import com.android.launcher3.icons.IconCache
import com.android.launcher3.model.WidgetPredictionsRequester.buildBundleForPredictionSession
import com.android.launcher3.model.WidgetPredictionsRequester.filterPredictions
import com.android.launcher3.model.WidgetPredictionsRequester.notOnUiSurfaceFilter
import com.android.launcher3.util.ActivityContextWrapper
import com.android.launcher3.util.PackageUserKey
import com.android.launcher3.util.WidgetUtils.createAppWidgetProviderInfo
import com.android.launcher3.widget.LauncherAppWidgetProviderInfo
import com.google.common.truth.Truth.assertThat
import java.util.function.Predicate
import junit.framework.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.MockitoAnnotations

@RunWith(AndroidJUnit4::class)
class WidgetsPredictionsRequesterTest {

    private lateinit var mUserHandle: UserHandle
    private lateinit var context: Context
    private lateinit var deviceProfile: DeviceProfile
    private lateinit var testInvariantProfile: InvariantDeviceProfile

    private lateinit var widget1aInfo: AppWidgetProviderInfo
    private lateinit var widget1bInfo: AppWidgetProviderInfo
    private lateinit var widget2Info: AppWidgetProviderInfo

    private lateinit var widgetItem1a: WidgetItem
    private lateinit var widgetItem1b: WidgetItem
    private lateinit var widgetItem2: WidgetItem

    private lateinit var allWidgets: Map<PackageUserKey, List<WidgetItem>>

    @Mock private lateinit var iconCache: IconCache

    @Before
    fun setUp() {
        MockitoAnnotations.initMocks(this)
        mUserHandle = myUserHandle()
        context = ActivityContextWrapper(ApplicationProvider.getApplicationContext())
        testInvariantProfile = LauncherAppState.getIDP(context)
        deviceProfile = testInvariantProfile.getDeviceProfile(context).copy(context)

        widget1aInfo =
            createAppWidgetProviderInfo(
                ComponentName.createRelative(APP_1_PACKAGE_NAME, APP_1_PROVIDER_A_CLASS_NAME)
            )
        widget1bInfo =
            createAppWidgetProviderInfo(
                ComponentName.createRelative(APP_1_PACKAGE_NAME, APP_1_PROVIDER_B_CLASS_NAME)
            )
        widgetItem1a = createWidgetItem(widget1aInfo)
        widgetItem1b = createWidgetItem(widget1bInfo)

        widget2Info =
            createAppWidgetProviderInfo(
                ComponentName.createRelative(APP_2_PACKAGE_NAME, APP_2_PROVIDER_1_CLASS_NAME)
            )
        widgetItem2 = createWidgetItem(widget2Info)

        allWidgets =
            mapOf(
                PackageUserKey(APP_1_PACKAGE_NAME, mUserHandle) to
                    listOf(widgetItem1a, widgetItem1b),
                PackageUserKey(APP_2_PACKAGE_NAME, mUserHandle) to listOf(widgetItem2),
            )
    }

    @Test
    fun buildBundleForPredictionSession_includesAddedAppWidgets() {
        val existingWidgets = arrayListOf(widget1aInfo, widget1bInfo, widget2Info)

        val bundle = buildBundleForPredictionSession(existingWidgets, TEST_UI_SURFACE)
        val addedWidgetsBundleExtra =
            bundle.getParcelableArrayList(BUNDLE_KEY_ADDED_APP_WIDGETS, AppTarget::class.java)

        assertNotNull(addedWidgetsBundleExtra)
        assertThat(addedWidgetsBundleExtra)
            .containsExactly(
                buildExpectedAppTargetEvent(
                    /*pkg=*/ APP_1_PACKAGE_NAME,
                    /*providerClassName=*/ APP_1_PROVIDER_A_CLASS_NAME,
                    /*user=*/ mUserHandle
                ),
                buildExpectedAppTargetEvent(
                    /*pkg=*/ APP_1_PACKAGE_NAME,
                    /*providerClassName=*/ APP_1_PROVIDER_B_CLASS_NAME,
                    /*user=*/ mUserHandle
                ),
                buildExpectedAppTargetEvent(
                    /*pkg=*/ APP_2_PACKAGE_NAME,
                    /*providerClassName=*/ APP_2_PROVIDER_1_CLASS_NAME,
                    /*user=*/ mUserHandle
                )
            )
    }

    @Test
    fun filterPredictions_notOnUiSurfaceFilter_returnsOnlyEligiblePredictions() {
        val widgetsAlreadyOnSurface = arrayListOf(widget1bInfo)
        val filter: Predicate<WidgetItem> = notOnUiSurfaceFilter(widgetsAlreadyOnSurface)

        val predictions =
            listOf(
                // already on surface
                AppTarget(
                    AppTargetId(APP_1_PACKAGE_NAME),
                    APP_1_PACKAGE_NAME,
                    APP_1_PROVIDER_B_CLASS_NAME,
                    mUserHandle
                ),
                // eligible
                AppTarget(
                    AppTargetId(APP_2_PACKAGE_NAME),
                    APP_2_PACKAGE_NAME,
                    APP_2_PROVIDER_1_CLASS_NAME,
                    mUserHandle
                )
            )

        // only 2 was eligible
        assertThat(filterPredictions(predictions, allWidgets, filter)).containsExactly(widgetItem2)
    }

    @Test
    fun filterPredictions_appPredictions_returnsWidgetFromPackage() {
        val widgetsAlreadyOnSurface = arrayListOf(widget1bInfo)
        val filter: Predicate<WidgetItem> = notOnUiSurfaceFilter(widgetsAlreadyOnSurface)

        val predictions =
            listOf(
                AppTarget(
                    AppTargetId(APP_1_PACKAGE_NAME),
                    APP_1_PACKAGE_NAME,
                    "$APP_1_PACKAGE_NAME.SomeActivity",
                    mUserHandle
                ),
                AppTarget(
                    AppTargetId(APP_2_PACKAGE_NAME),
                    APP_2_PACKAGE_NAME,
                    "$APP_2_PACKAGE_NAME.SomeActivity2",
                    mUserHandle
                ),
            )

        assertThat(filterPredictions(predictions, allWidgets, filter))
            .containsExactly(widgetItem1a, widgetItem2)
    }

    private fun createWidgetItem(
        providerInfo: AppWidgetProviderInfo,
    ): WidgetItem {
        val widgetInfo = LauncherAppWidgetProviderInfo.fromProviderInfo(context, providerInfo)
        return WidgetItem(widgetInfo, testInvariantProfile, iconCache, context)
    }

    companion object {
        const val TEST_UI_SURFACE = "widgets_test"
        const val BUNDLE_KEY_ADDED_APP_WIDGETS = "added_app_widgets"

        const val APP_1_PACKAGE_NAME = "com.example.app1"
        const val APP_1_PROVIDER_A_CLASS_NAME = "app1Provider1"
        const val APP_1_PROVIDER_B_CLASS_NAME = "app1Provider2"

        const val APP_2_PACKAGE_NAME = "com.example.app2"
        const val APP_2_PROVIDER_1_CLASS_NAME = "app2Provider1"

        const val TEST_PACKAGE = "pkg"

        private fun buildExpectedAppTargetEvent(
            pkg: String,
            providerClassName: String,
            userHandle: UserHandle
        ): AppTargetEvent {
            val appTarget =
                AppTarget.Builder(
                        /*id=*/ AppTargetId("widget:$pkg"),
                        /*packageName=*/ pkg,
                        /*user=*/ userHandle
                    )
                    .setClassName(providerClassName)
                    .build()
            return AppTargetEvent.Builder(appTarget, AppTargetEvent.ACTION_PIN)
                .setLaunchLocation(TEST_UI_SURFACE)
                .build()
        }
    }
}
