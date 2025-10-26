package com.android.launcher3.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.appwidget.AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN
import android.content.ComponentName
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.CheckFlagsRule
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.widget.RemoteViews
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import com.android.launcher3.Flags.FLAG_ENABLE_GENERATED_PREVIEWS
import com.android.launcher3.R
import com.android.launcher3.icons.IconCache
import com.android.launcher3.model.WidgetItem
import com.android.launcher3.util.ActivityContextWrapper
import com.android.launcher3.util.Executors
import com.android.launcher3.util.Executors.MAIN_EXECUTOR
import com.android.launcher3.util.LauncherModelHelper.SandboxModelContext
import com.android.launcher3.util.TestUtil
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.whenever

@SmallTest
@RunWith(AndroidJUnit4::class)
@RequiresFlagsEnabled(FLAG_ENABLE_GENERATED_PREVIEWS)
class GeneratedPreviewTest {
    @get:Rule val checkFlagsRule: CheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()
    private val providerName =
        ComponentName(
            getInstrumentation().context.packageName,
            "com.android.launcher3.testcomponent.AppWidgetNoConfig",
        )
    private val generatedPreviewLayout =
        getInstrumentation().context.run {
            resources.getIdentifier("test_layout_appwidget_blue", "layout", packageName)
        }

    private lateinit var context: SandboxModelContext
    private lateinit var uiContext: Context
    private lateinit var generatedPreview: RemoteViews
    private lateinit var widgetCell: WidgetCell
    private lateinit var appWidgetProviderInfo: LauncherAppWidgetProviderInfo
    private lateinit var widgetItem: WidgetItem

    @Mock lateinit var iconCache: IconCache

    @Before
    fun setup() {
        MockitoAnnotations.initMocks(this)
        context = SandboxModelContext()
        generatedPreview = RemoteViews(context.packageName, generatedPreviewLayout)
        uiContext =
            ActivityContextWrapper(ContextThemeWrapper(context, R.style.WidgetContainerTheme))
        widgetCell =
            LayoutInflater.from(uiContext).inflate(R.layout.widget_cell, null) as WidgetCell
        appWidgetProviderInfo =
            AppWidgetProviderInfo()
                .apply {
                    generatedPreviewCategories = WIDGET_CATEGORY_HOME_SCREEN
                    provider = providerName
                    providerInfo = ActivityInfo().apply { applicationInfo = ApplicationInfo() }
                }
                .let { LauncherAppWidgetProviderInfo.fromProviderInfo(context, it) }

        val widgetManager = context.spyService(AppWidgetManager::class.java)
        doAnswer { i ->
                generatedPreview.takeIf {
                    i.arguments[0] == appWidgetProviderInfo.provider &&
                        i.arguments[1] == appWidgetProviderInfo.user &&
                        i.arguments[2] == WIDGET_CATEGORY_HOME_SCREEN
                }
            }
            .whenever(widgetManager)
            .getWidgetPreview(any(), any(), any())
        createWidgetItem()
    }

    @After
    fun tearDown() {
        context.destroy()
    }

    private fun createWidgetItem() {
        Executors.MODEL_EXECUTOR.submit {
                val idp = context.appComponent.idp
                widgetItem = WidgetItem(appWidgetProviderInfo, idp, iconCache, context)
            }
            .get()
    }

    @Test
    fun widgetItem_hasGeneratedPreview_noPreview() {
        appWidgetProviderInfo.generatedPreviewCategories = 0
        createWidgetItem()
        val preview = DatabaseWidgetPreviewLoader(uiContext).generatePreviewInfoBg(widgetItem, 1, 1)
        assertThat(preview.remoteViews).isNull()
    }

    @Test
    fun widgetItem_getGeneratedPreview() {
        val preview = DatabaseWidgetPreviewLoader(uiContext).generatePreviewInfoBg(widgetItem, 1, 1)
        assertThat(preview.remoteViews).isEqualTo(generatedPreview)
    }

    @Test
    fun widgetCell_showGeneratedPreview() {
        widgetCell.applyFromCellItem(widgetItem)
        TestUtil.runOnExecutorSync(DatabaseWidgetPreviewLoader.getLoaderExecutor()) {}
        TestUtil.runOnExecutorSync(MAIN_EXECUTOR) {}
        assertThat(widgetCell.appWidgetHostViewPreview).isNotNull()
        assertThat(widgetCell.appWidgetHostViewPreview?.appWidgetInfo)
            .isEqualTo(appWidgetProviderInfo)
    }
}
