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

import android.app.prediction.AppPredictionManager
import android.app.prediction.AppPredictor
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
import com.android.launcher3.model.WidgetPredictionsRequester.LAUNCH_LOCATION
import com.android.launcher3.model.WidgetPredictionsRequester.buildBundleForPredictionSession
import com.android.launcher3.model.WidgetPredictionsRequester.filterPredictions
import com.android.launcher3.model.WidgetPredictionsRequester.notOnUiSurfaceFilter
import com.android.launcher3.util.ActivityContextWrapper
import com.android.launcher3.util.ComponentKey
import com.android.launcher3.util.Executors
import com.android.launcher3.util.Executors.MODEL_EXECUTOR
import com.android.launcher3.util.TestUtil
import com.android.launcher3.util.WidgetUtils.createAppWidgetProviderInfo
import com.android.launcher3.widget.LauncherAppWidgetProviderInfo
import com.android.launcher3.widget.PendingAddWidgetInfo
import com.google.common.truth.Truth.assertThat
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.function.Predicate
import junit.framework.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.whenever

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

    private lateinit var allWidgets: Map<ComponentKey, WidgetItem>

    @Mock private lateinit var iconCache: IconCache

    @Mock private lateinit var apmMock: AppPredictionManager

    @Mock private lateinit var predictorMock: AppPredictor

    @Before
    fun setUp() {
        MockitoAnnotations.initMocks(this)
        mUserHandle = myUserHandle()

        whenever(apmMock.createAppPredictionSession(any())).thenReturn(predictorMock)

        context =
            object : ActivityContextWrapper(ApplicationProvider.getApplicationContext()) {
                override fun getSystemService(name: String): Any? {
                    if (name == "app_prediction") {
                        return apmMock
                    }
                    return super.getSystemService(name)
                }
            }
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
                ComponentKey(widgetItem1a.componentName, widgetItem1a.user) to widgetItem1a,
                ComponentKey(widgetItem1b.componentName, widgetItem1b.user) to widgetItem1b,
                ComponentKey(widgetItem2.componentName, widgetItem2.user) to widgetItem2,
            )
    }

    @Test
    fun buildBundleForPredictionSession_includesAddedAppWidgets() {
        val existingWidgets = arrayListOf(widget1aInfo, widget1bInfo, widget2Info)

        val bundle = buildBundleForPredictionSession(existingWidgets)
        val addedWidgetsBundleExtra =
            bundle.getParcelableArrayList(BUNDLE_KEY_ADDED_APP_WIDGETS, AppTarget::class.java)

        assertNotNull(addedWidgetsBundleExtra)
        assertThat(addedWidgetsBundleExtra)
            .containsExactly(
                buildExpectedAppTargetEvent(
                    /*pkg=*/ APP_1_PACKAGE_NAME,
                    /*providerClassName=*/ APP_1_PROVIDER_A_CLASS_NAME,
                    /*user=*/ mUserHandle,
                ),
                buildExpectedAppTargetEvent(
                    /*pkg=*/ APP_1_PACKAGE_NAME,
                    /*providerClassName=*/ APP_1_PROVIDER_B_CLASS_NAME,
                    /*user=*/ mUserHandle,
                ),
                buildExpectedAppTargetEvent(
                    /*pkg=*/ APP_2_PACKAGE_NAME,
                    /*providerClassName=*/ APP_2_PROVIDER_1_CLASS_NAME,
                    /*user=*/ mUserHandle,
                ),
            )
    }

    @Test
    fun request_invokesCallbackWithPredictedItems() {
        TestUtil.runOnExecutorSync(MODEL_EXECUTOR) {
            val underTest = WidgetPredictionsRequester(context, TEST_UI_SURFACE, allWidgets)
            val existingWidgets = arrayListOf(widget1aInfo, widget1bInfo)
            val predictions =
                listOf(
                    // (existing) already on surface
                    AppTarget(
                        AppTargetId(APP_1_PACKAGE_NAME),
                        APP_1_PACKAGE_NAME,
                        APP_1_PROVIDER_B_CLASS_NAME,
                        mUserHandle,
                    ),
                    // eligible
                    AppTarget(
                        AppTargetId(APP_2_PACKAGE_NAME),
                        APP_2_PACKAGE_NAME,
                        APP_2_PROVIDER_1_CLASS_NAME,
                        mUserHandle,
                    ),
                )
            doAnswer {
                    underTest.onTargetsAvailable(predictions)
                    null
                }
                .whenever(predictorMock)
                .requestPredictionUpdate()
            val testCountDownLatch = CountDownLatch(1)
            val listener =
                WidgetPredictionsRequester.WidgetPredictionsListener { itemInfos ->
                    if (itemInfos.size == 1 && itemInfos[0] is PendingAddWidgetInfo) {
                        // only one item was eligible.
                        testCountDownLatch.countDown()
                    } else {
                        println("Unexpected prediction items found: ${itemInfos.size}")
                    }
                }

            underTest.request(existingWidgets, listener)
            TestUtil.runOnExecutorSync(Executors.MAIN_EXECUTOR) {}

            assertThat(testCountDownLatch.await(TEST_TIMEOUT, TimeUnit.SECONDS)).isTrue()
        }
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
                    mUserHandle,
                ),
                // eligible
                AppTarget(
                    AppTargetId(APP_2_PACKAGE_NAME),
                    APP_2_PACKAGE_NAME,
                    APP_2_PROVIDER_1_CLASS_NAME,
                    mUserHandle,
                ),
            )

        // only 2 was eligible
        assertThat(filterPredictions(predictions, allWidgets, filter)).containsExactly(widgetItem2)
    }

    @Test
    fun filterPredictions_appPredictions_returnsEmptyList() {
        val widgetsAlreadyOnSurface = arrayListOf(widget1bInfo)
        val filter: Predicate<WidgetItem> = notOnUiSurfaceFilter(widgetsAlreadyOnSurface)

        val predictions =
            listOf(
                AppTarget(
                    AppTargetId(APP_1_PACKAGE_NAME),
                    APP_1_PACKAGE_NAME,
                    "$APP_1_PACKAGE_NAME.SomeActivity",
                    mUserHandle,
                ),
                AppTarget(
                    AppTargetId(APP_2_PACKAGE_NAME),
                    APP_2_PACKAGE_NAME,
                    "$APP_2_PACKAGE_NAME.SomeActivity2",
                    mUserHandle,
                ),
            )

        assertThat(filterPredictions(predictions, allWidgets, filter)).isEmpty()
    }

    private fun createWidgetItem(providerInfo: AppWidgetProviderInfo): WidgetItem {
        val widgetInfo = LauncherAppWidgetProviderInfo.fromProviderInfo(context, providerInfo)
        return WidgetItem(widgetInfo, testInvariantProfile, iconCache, context)
    }

    companion object {
        const val TEST_TIMEOUT = 3L

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
            userHandle: UserHandle,
        ): AppTargetEvent {
            val appTarget =
                AppTarget.Builder(
                        /*id=*/ AppTargetId("widget:$pkg"),
                        /*packageName=*/ pkg,
                        /*user=*/ userHandle,
                    )
                    .setClassName(providerClassName)
                    .build()
            return AppTargetEvent.Builder(appTarget, AppTargetEvent.ACTION_PIN)
                .setLaunchLocation(LAUNCH_LOCATION)
                .build()
        }
    }
}
