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

package com.android.launcher3.util

import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherApps
import android.os.Bundle
import android.os.Process
import android.platform.test.flag.junit.SetFlagsRule
import android.view.View.OnClickListener
import android.view.View.OnFocusChangeListener
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import com.android.launcher3.BubbleTextView
import com.android.launcher3.Flags
import com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_APP_PAIR
import com.android.launcher3.apppairs.AppPairIcon
import com.android.launcher3.folder.FolderIcon
import com.android.launcher3.model.ModelWriter
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.model.data.AppPairInfo
import com.android.launcher3.model.data.FolderInfo
import com.android.launcher3.model.data.LauncherAppWidgetInfo
import com.android.launcher3.model.data.LauncherAppWidgetInfo.FLAG_ID_NOT_VALID
import com.android.launcher3.model.data.LauncherAppWidgetInfo.FLAG_UI_NOT_READY
import com.android.launcher3.model.data.LauncherAppWidgetInfo.RESTORE_COMPLETED
import com.android.launcher3.ui.TestViewHelpers
import com.android.launcher3.util.Executors.MAIN_EXECUTOR
import com.android.launcher3.util.Executors.VIEW_PREINFLATION_EXECUTOR
import com.android.launcher3.util.rule.ShellCommandRule
import com.android.launcher3.widget.LauncherAppWidgetHostView
import com.android.launcher3.widget.LauncherWidgetHolder
import com.android.launcher3.widget.PendingAppWidgetHostView
import com.android.launcher3.widget.WidgetManagerHelper
import java.util.concurrent.Callable
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.same
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions

/** Tests for ItemInflater */
@SmallTest
@RunWith(AndroidJUnit4::class)
class ItemInflaterTest {

    @get:Rule val setFlagsRule = SetFlagsRule()
    @get:Rule val grantWidgetRule = ShellCommandRule.grantWidgetBind()

    private val clickListener = OnClickListener {}
    private val focusListener = OnFocusChangeListener { _, _ -> }

    @Mock private lateinit var modelWriter: ModelWriter

    private lateinit var testContext: Context
    private lateinit var uiContext: ActivityContextWrapper

    private lateinit var widgetHolder: LauncherWidgetHolder
    private lateinit var underTest: ItemInflater<*>

    @Before
    fun setUp() {
        MockitoAnnotations.initMocks(this)
        testContext = InstrumentationRegistry.getInstrumentation().context

        uiContext = ActivityContextWrapper(getApplicationContext())
        uiContext.setTheme(Themes.getActivityThemeRes(uiContext, 0))

        widgetHolder = LauncherWidgetHolder.newInstance(uiContext)
        widgetHolder.startListening()
        underTest =
            ItemInflater(
                uiContext,
                widgetHolder,
                clickListener,
                focusListener,
                FrameLayout(uiContext)
            )
    }

    @After
    fun tearDown() {
        widgetHolder.destroy()
    }

    @Test
    fun test_workspace_item_inflated_on_UI() {
        val itemInfo = workspaceItemInfo()
        val view =
            MAIN_EXECUTOR.submit(Callable { underTest.inflateItem(itemInfo, modelWriter) }).get()

        assertTrue(view is BubbleTextView)
        assertEquals(itemInfo, view!!.tag)
    }

    @Test
    fun test_workspace_item_inflated_on_BG() {
        setFlagsRule.enableFlags(Flags.FLAG_ENABLE_WORKSPACE_INFLATION)

        val itemInfo = workspaceItemInfo()
        val view =
            VIEW_PREINFLATION_EXECUTOR.submit(
                    Callable { underTest.inflateItem(itemInfo, modelWriter) }
                )
                .get()

        assertTrue(view is BubbleTextView)
        assertEquals(itemInfo, view!!.tag)
    }

    @Test
    fun test_folder_inflated_on_UI() {
        val itemInfo = FolderInfo()
        itemInfo.add(workspaceItemInfo())
        itemInfo.add(workspaceItemInfo())
        itemInfo.add(workspaceItemInfo())

        val view =
            MAIN_EXECUTOR.submit(Callable { underTest.inflateItem(itemInfo, modelWriter) }).get()

        assertTrue(view is FolderIcon)
        assertEquals(itemInfo, view!!.tag)
    }

    @Test
    fun test_folder_inflated_on_BG() {
        setFlagsRule.enableFlags(Flags.FLAG_ENABLE_WORKSPACE_INFLATION)

        val itemInfo = FolderInfo()
        itemInfo.add(workspaceItemInfo())
        itemInfo.add(workspaceItemInfo())
        itemInfo.add(workspaceItemInfo())

        val view =
            VIEW_PREINFLATION_EXECUTOR.submit(
                    Callable { underTest.inflateItem(itemInfo, modelWriter) }
                )
                .get()

        assertTrue(view is FolderIcon)
        assertEquals(itemInfo, view!!.tag)
    }

    @Test
    fun test_app_pair_inflated_on_UI() {
        val itemInfo = AppPairInfo()
        itemInfo.itemType = ITEM_TYPE_APP_PAIR
        itemInfo.add(workspaceItemInfo())
        itemInfo.add(workspaceItemInfo())

        val view =
            MAIN_EXECUTOR.submit(Callable { underTest.inflateItem(itemInfo, modelWriter) }).get()

        assertTrue(view is AppPairIcon)
        assertEquals(itemInfo, view!!.tag)
    }

    @Test
    fun test_app_pair_inflated_on_BG() {
        setFlagsRule.enableFlags(Flags.FLAG_ENABLE_WORKSPACE_INFLATION)

        val itemInfo = AppPairInfo()
        itemInfo.itemType = ITEM_TYPE_APP_PAIR
        itemInfo.add(workspaceItemInfo())
        itemInfo.add(workspaceItemInfo())

        val view =
            VIEW_PREINFLATION_EXECUTOR.submit(
                    Callable { underTest.inflateItem(itemInfo, modelWriter) }
                )
                .get()

        assertTrue(view is AppPairIcon)
        assertEquals(itemInfo, view!!.tag)
    }

    @Test
    fun test_pending_widget_inflated_on_UI() {
        val itemInfo = widgetItemInfo(true)

        val view =
            MAIN_EXECUTOR.submit(Callable { underTest.inflateItem(itemInfo, modelWriter) }).get()

        assertTrue(view is PendingAppWidgetHostView)
        assertEquals(itemInfo, view!!.tag)
    }

    @Test
    fun test_pending_widget_inflated_on_BG() {
        setFlagsRule.enableFlags(Flags.FLAG_ENABLE_WORKSPACE_INFLATION)

        val itemInfo = widgetItemInfo(true)
        val view =
            VIEW_PREINFLATION_EXECUTOR.submit(
                    Callable { underTest.inflateItem(itemInfo, modelWriter) }
                )
                .get()

        assertTrue(view is PendingAppWidgetHostView)
        assertEquals(itemInfo, view!!.tag)
    }

    @Test
    fun test_widget_restored_and_inflated_on_UI() {
        val itemInfo = widgetItemInfo(false)

        val view =
            MAIN_EXECUTOR.submit(Callable { underTest.inflateItem(itemInfo, modelWriter) }).get()

        // Verify that the widget is automatically restored and a final widget is returned
        assertTrue(view is LauncherAppWidgetHostView)
        assertFalse(view is PendingAppWidgetHostView)
        assertEquals(itemInfo, view!!.tag)
        assertEquals(RESTORE_COMPLETED, itemInfo.restoreStatus)
        verify(modelWriter).updateItemInDatabase(same(itemInfo))
    }

    @Test
    fun test_widget_restored_and_inflated_on_BG() {
        setFlagsRule.enableFlags(Flags.FLAG_ENABLE_WORKSPACE_INFLATION)
        val itemInfo = widgetItemInfo(false)

        val view =
            VIEW_PREINFLATION_EXECUTOR.submit(
                    Callable { underTest.inflateItem(itemInfo, modelWriter) }
                )
                .get()

        // Verify that the widget is automatically restored and a final widget is returned
        assertTrue(view is LauncherAppWidgetHostView)
        assertFalse(view is PendingAppWidgetHostView)
        assertEquals(itemInfo, view!!.tag)
        assertEquals(RESTORE_COMPLETED, itemInfo.restoreStatus)
        verify(modelWriter).updateItemInDatabase(same(itemInfo))
    }

    @Test
    fun test_invalid_widget_deleted() {
        val itemInfo =
            widgetItemInfo(false).apply {
                providerName = ComponentName(providerName.packageName, "invalid_provider_name")
            }
        val view =
            MAIN_EXECUTOR.submit(Callable { underTest.inflateItem(itemInfo, modelWriter) }).get()
        assertNull(view)
        verify(modelWriter).deleteItemFromDatabase(same(itemInfo), any())
    }

    @Test
    fun test_normal_widget_inflated_UI() {
        val providerInfo = TestViewHelpers.findWidgetProvider(false)
        val id = widgetHolder.allocateAppWidgetId()
        assertTrue(
            WidgetManagerHelper(uiContext).bindAppWidgetIdIfAllowed(id, providerInfo, Bundle())
        )
        val itemInfo = LauncherAppWidgetInfo(id, providerInfo.provider)
        itemInfo.spanX = 2
        itemInfo.spanY = 2

        val view =
            MAIN_EXECUTOR.submit(Callable { underTest.inflateItem(itemInfo, modelWriter) }).get()

        // Verify that the widget is automatically restored and a final widget is returned
        assertTrue(view is LauncherAppWidgetHostView)
        assertFalse(view is PendingAppWidgetHostView)
        assertEquals(itemInfo, view!!.tag)
        verifyNoMoreInteractions(modelWriter)
    }

    private fun workspaceItemInfo() =
        AppInfo(
                uiContext,
                uiContext
                    .getSystemService(LauncherApps::class.java)!!
                    .getActivityList(testContext.packageName, Process.myUserHandle())[0],
                Process.myUserHandle()
            )
            .makeWorkspaceItem(uiContext)

    private fun widgetItemInfo(hasConfig: Boolean) =
        LauncherAppWidgetInfo(0, TestViewHelpers.findWidgetProvider(hasConfig).component).apply {
            spanX = 2
            spanY = 2
            restoreStatus = FLAG_ID_NOT_VALID or FLAG_UI_NOT_READY
        }
}
