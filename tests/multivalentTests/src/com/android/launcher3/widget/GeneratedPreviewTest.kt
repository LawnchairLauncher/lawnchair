package com.android.launcher3.widget

import android.appwidget.AppWidgetProviderInfo
import android.appwidget.AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN
import android.appwidget.AppWidgetProviderInfo.WIDGET_CATEGORY_KEYGUARD
import android.appwidget.AppWidgetProviderInfo.WIDGET_CATEGORY_SEARCHBOX
import android.content.ComponentName
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.platform.test.annotations.RequiresFlagsDisabled
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.CheckFlagsRule
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.widget.RemoteViews
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import com.android.launcher3.Flags.FLAG_ENABLE_GENERATED_PREVIEWS
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.icons.IconCache
import com.android.launcher3.icons.IconProvider
import com.android.launcher3.model.WidgetItem
import com.android.launcher3.util.ActivityContextWrapper
import com.android.launcher3.util.Executors
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class GeneratedPreviewTest {
    @get:Rule val checkFlagsRule: CheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()
    private val providerName =
        ComponentName(
            "com.android.launcher3.tests",
            "com.android.launcher3.testcomponent.AppWidgetNoConfig"
        )
    private val generatedPreviewLayout =
        getInstrumentation().context.run {
            resources.getIdentifier("test_layout_appwidget_blue", "layout", packageName)
        }
    private lateinit var context: Context
    private lateinit var generatedPreview: RemoteViews
    private lateinit var widgetCell: WidgetCell
    private lateinit var helper: WidgetManagerHelper
    private lateinit var appWidgetProviderInfo: LauncherAppWidgetProviderInfo
    private lateinit var widgetItem: WidgetItem

    @Before
    fun setup() {
        context = getApplicationContext()
        generatedPreview = RemoteViews(context.packageName, generatedPreviewLayout)
        widgetCell =
            LayoutInflater.from(
                    ActivityContextWrapper(
                        ContextThemeWrapper(
                            context,
                            com.android.launcher3.R.style.WidgetContainerTheme
                        )
                    )
                )
                .inflate(com.android.launcher3.R.layout.widget_cell, null) as WidgetCell
        appWidgetProviderInfo =
            AppWidgetProviderInfo()
                .apply {
                    generatedPreviewCategories = WIDGET_CATEGORY_HOME_SCREEN
                    provider = providerName
                    providerInfo = ActivityInfo().apply { applicationInfo = ApplicationInfo() }
                }
                .let { LauncherAppWidgetProviderInfo.fromProviderInfo(context, it) }
        helper =
            object : WidgetManagerHelper(context) {
                override fun loadGeneratedPreview(
                    info: AppWidgetProviderInfo,
                    widgetCategory: Int
                ) =
                    generatedPreview.takeIf {
                        info === appWidgetProviderInfo &&
                            widgetCategory == WIDGET_CATEGORY_HOME_SCREEN
                    }
            }
        createWidgetItem()
    }

    private fun createWidgetItem() {
        Executors.MODEL_EXECUTOR.submit {
                val idp = InvariantDeviceProfile()
                widgetItem =
                    WidgetItem(
                        appWidgetProviderInfo,
                        idp,
                        IconCache(context, idp, null, IconProvider(context)),
                        context,
                        helper,
                    )
            }
            .get()
    }

    @Test
    @RequiresFlagsEnabled(FLAG_ENABLE_GENERATED_PREVIEWS)
    fun widgetItem_hasGeneratedPreview() {
        assertThat(widgetItem.hasGeneratedPreview(WIDGET_CATEGORY_HOME_SCREEN)).isTrue()
        assertThat(widgetItem.hasGeneratedPreview(WIDGET_CATEGORY_KEYGUARD)).isFalse()
        assertThat(widgetItem.hasGeneratedPreview(WIDGET_CATEGORY_SEARCHBOX)).isFalse()
    }

    @Test
    @RequiresFlagsEnabled(FLAG_ENABLE_GENERATED_PREVIEWS)
    fun widgetItem_hasGeneratedPreview_noPreview() {
        appWidgetProviderInfo.generatedPreviewCategories = 0
        createWidgetItem()
        assertThat(widgetItem.hasGeneratedPreview(WIDGET_CATEGORY_HOME_SCREEN)).isFalse()
        assertThat(widgetItem.hasGeneratedPreview(WIDGET_CATEGORY_KEYGUARD)).isFalse()
        assertThat(widgetItem.hasGeneratedPreview(WIDGET_CATEGORY_SEARCHBOX)).isFalse()
    }

    @Test
    @RequiresFlagsEnabled(FLAG_ENABLE_GENERATED_PREVIEWS)
    fun widgetItem_hasGeneratedPreview_nullPreview() {
        appWidgetProviderInfo.generatedPreviewCategories =
            WIDGET_CATEGORY_HOME_SCREEN or WIDGET_CATEGORY_KEYGUARD
        createWidgetItem()
        assertThat(widgetItem.hasGeneratedPreview(WIDGET_CATEGORY_HOME_SCREEN)).isTrue()
        // loadGeneratedPreview returns null for KEYGUARD, so this should still be false.
        assertThat(widgetItem.hasGeneratedPreview(WIDGET_CATEGORY_KEYGUARD)).isFalse()
        assertThat(widgetItem.hasGeneratedPreview(WIDGET_CATEGORY_SEARCHBOX)).isFalse()
    }

    @Test
    @RequiresFlagsDisabled(FLAG_ENABLE_GENERATED_PREVIEWS)
    fun widgetItem_hasGeneratedPreview_flagDisabled() {
        assertThat(widgetItem.hasGeneratedPreview(WIDGET_CATEGORY_HOME_SCREEN)).isFalse()
        assertThat(widgetItem.hasGeneratedPreview(WIDGET_CATEGORY_KEYGUARD)).isFalse()
        assertThat(widgetItem.hasGeneratedPreview(WIDGET_CATEGORY_SEARCHBOX)).isFalse()
    }

    @Test
    @RequiresFlagsEnabled(FLAG_ENABLE_GENERATED_PREVIEWS)
    fun widgetItem_getGeneratedPreview() {
        val preview = widgetItem.generatedPreviews.get(WIDGET_CATEGORY_HOME_SCREEN)
        assertThat(preview).isEqualTo(generatedPreview)
    }

    @Test
    @RequiresFlagsEnabled(FLAG_ENABLE_GENERATED_PREVIEWS)
    fun widgetCell_showGeneratedPreview() {
        widgetCell.applyFromCellItem(widgetItem)
        DatabaseWidgetPreviewLoader.getLoaderExecutor().submit {}.get()
        assertThat(widgetCell.appWidgetHostViewPreview).isNotNull()
        assertThat(widgetCell.appWidgetHostViewPreview?.appWidgetInfo)
            .isEqualTo(appWidgetProviderInfo)
    }

    @Test
    @RequiresFlagsDisabled(FLAG_ENABLE_GENERATED_PREVIEWS)
    fun widgetCell_showGeneratedPreview_flagDisabled() {
        widgetCell.applyFromCellItem(widgetItem)
        DatabaseWidgetPreviewLoader.getLoaderExecutor().submit {}.get()
        assertThat(widgetCell.appWidgetHostViewPreview).isNull()
    }
}
