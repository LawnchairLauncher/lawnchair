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

package com.android.launcher3.widgetpicker.repository

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.appwidget.AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN
import android.content.ComponentName
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.os.UserHandle
import android.widget.RemoteViews
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import com.android.dx.mockito.inline.extended.ExtendedMockito.spyOn
import com.android.launcher3.AppFilter
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.LauncherAppState
import com.android.launcher3.R
import com.android.launcher3.icons.IconCache
import com.android.launcher3.icons.cache.CachedObject
import com.android.launcher3.model.WidgetsModel
import com.android.launcher3.model.data.PackageItemInfo
import com.android.launcher3.pm.ShortcutConfigActivityInfo
import com.android.launcher3.util.Executors
import com.android.launcher3.util.SandboxApplication
import com.android.launcher3.util.TestActivityContext
import com.android.launcher3.util.TestUtil
import com.android.launcher3.util.UserIconInfo
import com.android.launcher3.util.WidgetUtils
import com.android.launcher3.util.rule.MockUsersRule
import com.android.launcher3.util.rule.MockUsersRule.MockUser
import com.android.launcher3.widget.LauncherAppWidgetProviderInfo
import com.android.launcher3.widgetpicker.data.repository.WidgetsRepository
import com.android.launcher3.widgetpicker.datasource.FeaturedWidgetsDataSource
import com.android.launcher3.widgetpicker.datasource.InMemoryWidgetSearchAlgorithm
import com.android.launcher3.widgetpicker.shared.model.PickableWidget
import com.android.launcher3.widgetpicker.shared.model.WidgetApp
import com.android.launcher3.widgetpicker.shared.model.WidgetAppId
import com.android.launcher3.widgetpicker.shared.model.WidgetId
import com.android.launcher3.widgetpicker.shared.model.WidgetPreview
import com.android.launcher3.widgetpicker.shared.model.isAppWidget
import com.android.launcher3.widgetpicker.shared.model.isShortcut
import com.google.common.truth.Truth.assertThat
import kotlin.test.assertNotNull
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever

@SmallTest
@RunWith(AndroidJUnit4::class)
@MockUser(userType = UserIconInfo.TYPE_MAIN)
@OptIn(ExperimentalCoroutinesApi::class)
class WidgetsRepositoryImplTest {
    @get:Rule val mockitoRule: MockitoRule = MockitoJUnit.rule()

    @get:Rule val context = SandboxApplication()

    @get:Rule val uiContext = TestActivityContext(context, R.style.WidgetContainerTheme)

    @get:Rule var mMockUsersRule: MockUsersRule = MockUsersRule(context)

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    @Mock private lateinit var iconCacheMock: IconCache

    @Mock private lateinit var appFilterMock: AppFilter

    @Mock lateinit var mockShortcutLauncherActivityInfo: LauncherActivityInfo

    @Mock lateinit var mockActivityInfo: ActivityInfo

    @Mock lateinit var mockApplicationInfo: ApplicationInfo

    private lateinit var user: UserHandle
    private lateinit var idp: InvariantDeviceProfile
    private lateinit var appWidgetManager: AppWidgetManager
    private lateinit var launcherApps: LauncherApps
    private lateinit var generatedPreview: RemoteViews

    private lateinit var underTest: WidgetsRepository

    @Before
    fun setup() {
        idp = InvariantDeviceProfile.INSTANCE[uiContext]

        underTest =
            WidgetsRepositoryImpl(
                appContext = context,
                idp = idp,
                widgetsModel = WidgetsModel(context, idp, iconCacheMock, appFilterMock),
                featuredWidgetsDataSource = FakeFeaturedDataSource,
                searchAlgorithm = InMemoryWidgetSearchAlgorithm(testDispatcher),
                backgroundContext = testDispatcher,
            )

        generatedPreview = RemoteViews(context.packageName, GENERATED_PREVIEW_LAYOUT_ID)
        appWidgetManager = context.spyService(AppWidgetManager::class.java)
        launcherApps = context.spyService(LauncherApps::class.java)

        whenever(appFilterMock.shouldShowApp(any())).thenReturn(true)
        seedAppTitles()
        seedWidgetAndShortcutTitles()

        user = mMockUsersRule.findUser { it.isMain }
        seedShortcutInLauncherApps(user)
        seedWidgetsInAppWidgetManager(user)
    }

    @After
    fun tearDown() {
        underTest.cleanUp()
    }

    private fun seedAppTitles() {
        whenever(iconCacheMock.getTitleAndIconForApp(any(), any())).thenAnswer {
            (it.arguments[0] as PackageItemInfo).apply { title = packageName.simpleName() }
        }
    }

    private fun seedWidgetAndShortcutTitles() {
        whenever(iconCacheMock.getTitleNoCache(any())).thenAnswer {
            (it.arguments[0] as CachedObject).let { info ->
                when (info) {
                    is LauncherAppWidgetProviderInfo -> {
                        info.component.className
                    }

                    is ShortcutConfigActivityInfo -> {
                        info.component.className
                    }

                    else -> "title"
                }
            }
        }
    }

    private fun seedWidgetsInAppWidgetManager(user: UserHandle) {
        whenever(appWidgetManager.getInstalledProvidersForProfile(user))
            .thenReturn(listOf(App1Widget1ProviderInfo, App2Widget1ProviderInfo))
        whenever(appWidgetManager.getInstalledProvidersForPackage(APP_1_PACKAGE_NAME, user))
            .thenReturn(listOf(App1Widget1ProviderInfo))
        whenever(appWidgetManager.getInstalledProvidersForPackage(APP_2_PACKAGE_NAME, user))
            .thenReturn(listOf(App2Widget1ProviderInfo))

        doAnswer { i ->
                generatedPreview.takeIf {
                    i.arguments[0] == App2Widget1ProviderName &&
                        i.arguments[1] == user &&
                        i.arguments[2] == WIDGET_CATEGORY_HOME_SCREEN
                }
            }
            .whenever(appWidgetManager)
            .getWidgetPreview(any(), any(), any())
    }

    private fun seedShortcutInLauncherApps(user: UserHandle) {
        mockApplicationInfo.targetSdkVersion = Build.VERSION_CODES.BAKLAVA
        mockActivityInfo.apply { whenever(componentName).thenReturn(App1Shortcut1Name) }

        mockShortcutLauncherActivityInfo.apply {
            whenever(componentName).thenReturn(App1Shortcut1Name)
            whenever(applicationInfo).thenReturn(mockApplicationInfo)
            whenever(activityInfo).thenReturn(mockActivityInfo)
            whenever(getUser()).thenReturn(user)
        }

        whenever(launcherApps.getShortcutConfigActivityList(eq(null), eq(user)))
            .thenReturn(listOf(mockShortcutLauncherActivityInfo))
        whenever(launcherApps.getShortcutConfigActivityList(eq(APP_1_PACKAGE_NAME), eq(user)))
            .thenReturn(listOf(mockShortcutLauncherActivityInfo))
        whenever(launcherApps.getShortcutConfigActivityList(eq(APP_2_PACKAGE_NAME), eq(user)))
            .thenReturn(listOf())

        val iconCache = LauncherAppState.INSTANCE[context].iconCache
        spyOn(iconCache)
        doReturn(ShortcutPreviewDrawable).whenever(iconCache).getFullResIcon(any())
    }

    @Test
    @MockUser(userType = UserIconInfo.TYPE_MAIN)
    fun initializeAndObserveWidgets_widgetsAndShortcutsMappedCorrectly() =
        testScope.runTest {
            underTest.initialize()
            TestUtil.runOnExecutorSync(Executors.MODEL_EXECUTOR) {}

            val widgetApps = underTest.observeWidgets().first()

            // 2 apps; first with a widget an shortcut and second with just a widget
            assertThat(widgetApps).hasSize(2)
            val expectedWidgetApp1Id =
                WidgetAppId(packageName = APP_1_PACKAGE_NAME, userHandle = user, category = -1)
            val expectedWidgetApp2Id =
                WidgetAppId(packageName = APP_2_PACKAGE_NAME, userHandle = user, category = -1)
            val expectedWidget1AId = WidgetId(App1Widget1ProviderName, user)
            val expectedShortcut1BId = WidgetId(App1Shortcut1Name, user)
            val expectedWidget2AId = WidgetId(App2Widget1ProviderName, user)

            val app1 = assertNotNull(widgetApps.find { it.id == expectedWidgetApp1Id })
            app1.apply {
                assertThat(title).isEqualTo(APP_1)
                assertThat(widgets).hasSize(2)

                val widget1A = assertNotNull(app1.widgets.find { it.id == expectedWidget1AId })
                assertThat(widget1A.widgetInfo.isAppWidget()).isTrue()
                assertThat(widget1A.label).isEqualTo(WIDGET_1A_NAME)

                val shortcut1B = assertNotNull(app1.widgets.find { it.id == expectedShortcut1BId })
                assertThat(shortcut1B.widgetInfo.isShortcut()).isTrue()
                assertThat(shortcut1B.label).isEqualTo(SHORTCUT_1B_NAME)
            }

            val app2 = assertNotNull(widgetApps.find { it.id == expectedWidgetApp2Id })
            app2.apply {
                assertThat(app2.title).isEqualTo(APP_2)
                assertThat(app2.widgets).hasSize(1)

                val widget2A = assertNotNull(app2.widgets.find { it.id == expectedWidget2AId })
                assertThat(widget2A.widgetInfo.isAppWidget()).isTrue()
                assertThat(widget2A.label).isEqualTo(WIDGET_2A_NAME)
            }
        }

    @Test
    fun initializeWidgetAppAndObserve_appOne_hasAWidgetAndAShortcut() =
        testScope.runTest {
            val widgetAppId =
                WidgetAppId(packageName = APP_1_PACKAGE_NAME, userHandle = user, category = -1)

            underTest.initialize(
                WidgetsRepository.InitializationOptions(
                    widgetAppId = widgetAppId,
                    loadFeaturedWidgets = false,
                )
            )
            TestUtil.runOnExecutorSync(Executors.MODEL_EXECUTOR) {}

            val widgetApp = underTest.observeWidgetApp(widgetAppId = widgetAppId).first()

            assertThat(widgetApp).isNotNull()
            widgetApp?.apply {
                assertThat(title).isEqualTo(APP_1)
                assertThat(widgets).hasSize(2)

                val expectedWidget1AId = WidgetId(App1Widget1ProviderName, user)
                val widget1A = assertNotNull(widgets.find { it.id == expectedWidget1AId })
                assertThat(widget1A.widgetInfo.isAppWidget()).isTrue()
                assertThat(widget1A.label).isEqualTo(WIDGET_1A_NAME)

                val expectedShortcut1BId = WidgetId(App1Shortcut1Name, user)
                val shortcut1B = assertNotNull(widgets.find { it.id == expectedShortcut1BId })
                assertThat(shortcut1B.widgetInfo.isShortcut()).isTrue()
                assertThat(shortcut1B.label).isEqualTo(SHORTCUT_1B_NAME)
            }
        }

    @Test
    fun initializeWidgetAppAndObserve_appTwo_hasOnlyAWidget() =
        testScope.runTest {
            val widgetAppId =
                WidgetAppId(packageName = APP_2_PACKAGE_NAME, userHandle = user, category = -1)

            underTest.initialize(
                WidgetsRepository.InitializationOptions(
                    widgetAppId = widgetAppId,
                    loadFeaturedWidgets = false,
                )
            )
            TestUtil.runOnExecutorSync(Executors.MODEL_EXECUTOR) {}

            val widgetApp = underTest.observeWidgetApp(widgetAppId = widgetAppId).first()

            assertThat(widgetApp).isNotNull()
            widgetApp?.apply {
                assertThat(title).isEqualTo(APP_2)
                assertThat(widgets).hasSize(1)

                val expectedWidget2AId = WidgetId(App2Widget1ProviderName, user)
                val widget2A = assertNotNull(widgets.find { it.id == expectedWidget2AId })
                assertThat(widget2A.widgetInfo.isAppWidget()).isTrue()
                assertThat(widget2A.label).isEqualTo(WIDGET_2A_NAME)
            }
        }

    @Test
    fun searchWidgets_returnsMatchedWidgetsAndShortcuts() =
        testScope.runTest {
            val query = APP_1
            underTest.initialize()
            TestUtil.runOnExecutorSync(Executors.MODEL_EXECUTOR) {}

            val result = underTest.searchWidgets(query)

            assertThat(result).hasSize(1)
            assertThat(result[0].title).isEqualTo(APP_1)
            // the widget and the shortcut from app1
            assertThat(result[0].widgets).hasSize(2)
        }

    @Test
    fun getWidgetPreview_widgetInfoNotAvailable_returnsPlaceholder() =
        testScope.runTest {
            underTest.initialize()
            TestUtil.runOnExecutorSync(Executors.MODEL_EXECUTOR) {}

            // If for some reason, widget's info wasn't available, maybe package updated, a
            // placeholder
            // will show up
            val preview =
                underTest.getWidgetPreview(
                    WidgetId(
                        componentName = ComponentName("unknownPackage", "unknownWidget"),
                        userHandle = user,
                    )
                )

            assertThat(preview).isInstanceOf(WidgetPreview.PlaceholderWidgetPreview::class.java)
        }

    @Test
    fun getWidgetPreview_layoutXml_returnsProviderInfoPreview() =
        testScope.runTest {
            underTest.initialize()
            TestUtil.runOnExecutorSync(Executors.MODEL_EXECUTOR) {}

            // in the seeded data, widget 1 from app1 uses layout xml
            val preview = underTest.getWidgetPreview(WidgetId(App1Widget1ProviderName, user))

            assertThat(preview).isInstanceOf(WidgetPreview.ProviderInfoWidgetPreview::class.java)
            // Initial layout set as the preview layout.
            assertThat(
                    (preview as WidgetPreview.ProviderInfoWidgetPreview).providerInfo.initialLayout
                )
                .isEqualTo(PREVIEW_LAYOUT_ID)
        }

    @Test
    fun getWidgetPreview_generatedPreview_returnsRemoteViewsPreview() =
        testScope.runTest {
            underTest.initialize()
            TestUtil.runOnExecutorSync(Executors.MODEL_EXECUTOR) {}

            // in the seeded data, widget 1 from app2 uses generated preview
            val preview = underTest.getWidgetPreview(WidgetId(App2Widget1ProviderName, user))

            assertThat(preview).isInstanceOf(WidgetPreview.RemoteViewsWidgetPreview::class.java)
            assertThat((preview as WidgetPreview.RemoteViewsWidgetPreview).remoteViews.layoutId)
                .isEqualTo(GENERATED_PREVIEW_LAYOUT_ID)
        }

    @Test
    fun getWidgetPreview_shortcut_returnsBitmapPreview() =
        testScope.runTest {
            underTest.initialize()
            TestUtil.runOnExecutorSync(Executors.MODEL_EXECUTOR) {}

            val preview = underTest.getWidgetPreview(WidgetId(App1Shortcut1Name, user))

            assertThat(preview).isInstanceOf(WidgetPreview.BitmapWidgetPreview::class.java)
        }

    @Test
    fun cleanUp_clearsState() =
        testScope.runTest {
            underTest.initialize()
            TestUtil.runOnExecutorSync(Executors.MODEL_EXECUTOR) {}

            assertThat(underTest.observeWidgets().first()).isNotEmpty()
            assertThat(underTest.getFeaturedWidgets().first()).isNotEmpty()

            underTest.cleanUp()

            assertThat(underTest.observeWidgets().first()).isEmpty()
            assertThat(underTest.getFeaturedWidgets().first()).isEmpty()
        }

    @Test
    fun getFeaturedWidgets_returnsFromDataSource() =
        testScope.runTest {
            underTest.initialize()
            TestUtil.runOnExecutorSync(Executors.MODEL_EXECUTOR) {}

            val result = underTest.getFeaturedWidgets().first()

            assertThat(result).hasSize(1)
        }

    companion object {
        const val APP_1 = "app1"
        const val APP_1_PACKAGE_NAME = "com.test.app1"
        const val WIDGET_1A_NAME = "TestWidgetProvider1A"
        const val SHORTCUT_1B_NAME = "TestShortcut1B"

        const val APP_2 = "app2"
        const val APP_2_PACKAGE_NAME = "com.test.app2"
        const val WIDGET_2A_NAME = "TestWidgetProvider2A"

        private val PREVIEW_LAYOUT_ID =
            getInstrumentation().context.run {
                resources.getIdentifier("test_layout_appwidget_view", "layout", packageName)
            }
        private val GENERATED_PREVIEW_LAYOUT_ID =
            getInstrumentation().context.run {
                resources.getIdentifier("test_layout_appwidget_blue", "layout", packageName)
            }
        private val ShortcutPreviewDrawable =
            BitmapDrawable(
                getInstrumentation().context.resources,
                Bitmap.createBitmap(50, 50, Bitmap.Config.ARGB_8888),
            )

        val App1Widget1ProviderName = ComponentName(APP_1_PACKAGE_NAME, WIDGET_1A_NAME)
        val App1Widget1ProviderInfo: AppWidgetProviderInfo =
            WidgetUtils.createAppWidgetProviderInfo(App1Widget1ProviderName).apply {
                previewLayout = PREVIEW_LAYOUT_ID
            }

        val App1Shortcut1Name = ComponentName(APP_1_PACKAGE_NAME, SHORTCUT_1B_NAME)

        val App2Widget1ProviderName = ComponentName(APP_2_PACKAGE_NAME, WIDGET_2A_NAME)
        val App2Widget1ProviderInfo: AppWidgetProviderInfo =
            WidgetUtils.createAppWidgetProviderInfo(App2Widget1ProviderName).apply {
                generatedPreviewCategories = WIDGET_CATEGORY_HOME_SCREEN
            }

        private fun String.simpleName() = split(".").last()

        val FakeFeaturedDataSource =
            object : FeaturedWidgetsDataSource {
                var eligibleApps = emptyList<String>()

                override suspend fun initialize() {
                    eligibleApps = listOf(APP_2_PACKAGE_NAME)
                }

                override suspend fun getFeaturedWidgets(
                    widgetApps: List<WidgetApp>
                ): List<PickableWidget> =
                    widgetApps
                        .filter { eligibleApps.contains(it.id.packageName) }
                        .map { it.widgets[0] }

                override fun cleanup() {
                    eligibleApps = emptyList()
                }
            }
    }
}
