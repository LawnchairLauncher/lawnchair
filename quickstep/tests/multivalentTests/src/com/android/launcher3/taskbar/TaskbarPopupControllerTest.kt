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

package com.android.launcher3.taskbar

import android.content.ComponentName
import android.content.Intent
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.SetFlagsRule
import android.util.SparseArray
import com.android.launcher3.AbstractFloatingView
import com.android.launcher3.BubbleTextView
import com.android.launcher3.Flags.FLAG_ENABLE_MULTI_INSTANCE_MENU_TASKBAR
import com.android.launcher3.LauncherSettings
import com.android.launcher3.R
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.WorkspaceItemInfo
import com.android.launcher3.statehandlers.DesktopVisibilityController
import com.android.launcher3.taskbar.TaskbarControllerTestUtil.runOnMainSync
import com.android.launcher3.taskbar.TaskbarViewTestUtil.createHotseatWorkspaceItem
import com.android.launcher3.taskbar.TaskbarViewTestUtil.createRecents
import com.android.launcher3.taskbar.TaskbarViewTestUtil.createTestWorkspaceItem
import com.android.launcher3.taskbar.rules.TaskbarUnitTestRule
import com.android.launcher3.taskbar.rules.TaskbarUnitTestRule.InjectController
import com.android.launcher3.taskbar.rules.TaskbarWindowSandboxContext
import com.android.launcher3.util.LauncherMultivalentJUnit
import com.android.launcher3.util.LauncherMultivalentJUnit.EmulatedDevices
import com.android.quickstep.util.GroupTask
import com.android.window.flags2.Flags.FLAG_ENABLE_PINNING_APP_WITH_CONTEXT_MENU
import com.google.common.truth.Truth.assertThat
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.mockito.kotlin.whenever

@RunWith(LauncherMultivalentJUnit::class)
@EmulatedDevices(["pixelFoldable2023", "pixelTablet2023"])
@DisableFlags(FLAG_ENABLE_MULTI_INSTANCE_MENU_TASKBAR)
class TaskbarPopupControllerTest {
    @get:Rule(order = 0) val setFlagsRule = SetFlagsRule()

    @get:Rule(order = 1) val context = TaskbarWindowSandboxContext.create()

    @get:Rule(order = 2) val taskbarUnitTestRule = TaskbarUnitTestRule(this, context)

    @InjectController lateinit var popupController: TaskbarPopupController

    private val taskbarContext: TaskbarActivityContext
        get() = taskbarUnitTestRule.activityContext

    private val desktopVisibilityController: DesktopVisibilityController
        get() = DesktopVisibilityController.INSTANCE[context]

    private lateinit var taskbarView: TaskbarView
    private lateinit var hotseatIcon: BubbleTextView
    private lateinit var recentTaskIcon: BubbleTextView

    @Before
    fun setup() {
        taskbarContext.controllers.uiController.init(taskbarContext.controllers)
        runOnMainSync { taskbarView = taskbarContext.dragLayer.findViewById(R.id.taskbar_view) }

        val hotseatItems = arrayOf(createHotseatWorkspaceItem())
        popupController.setApps(
            hotseatItems
                .map { item -> AppInfo(item.targetComponent, item.title, item.user, item.intent) }
                .toTypedArray()
        )
        popupController.setHotseatInfosList(SparseArray())
        val recentItems = createRecents(2)
        runOnMainSync {
            taskbarView.updateItems(hotseatItems, recentItems)
            hotseatIcon =
                taskbarView.iconViews.filterIsInstance<BubbleTextView>().first {
                    it.tag is WorkspaceItemInfo
                }
            recentTaskIcon =
                taskbarView.iconViews.filterIsInstance<BubbleTextView>().first {
                    it.tag is GroupTask
                }
        }
    }

    @Test
    fun showForIcon_hotseatItem() {
        assertThat(hasPopupMenu()).isFalse()
        runOnMainSync { popupController.showForIcon(hotseatIcon) }
        assertThat(hasPopupMenu()).isTrue()
    }

    @Test
    @EnableFlags(FLAG_ENABLE_PINNING_APP_WITH_CONTEXT_MENU)
    fun showForIcon_recentTask() {
        whenever(desktopVisibilityController.isInDesktopMode(context.displayId)).thenReturn(true)
        assertThat(hasPopupMenu()).isFalse()
        runOnMainSync { popupController.showForIcon(recentTaskIcon) }
        assertThat(hasPopupMenu()).isTrue()
    }

    @Test
    fun createPinShortcut_itemAlreadyPinned_returnsUnpinShortcut() {
        val hotseatItems = SparseArray<ItemInfo>()
        val appUser = android.os.Process.myUserHandle()
        val appAIntent = Intent().setComponent(ComponentName("com.example.app", "AppAActivity"))

        val itemFromAllApps =
            createTestWorkspaceItem(
                0,
                "AppA",
                appAIntent,
                appUser,
                LauncherSettings.Favorites.CONTAINER_ALL_APPS,
            )

        val pinnedItemInHotseat =
            createTestWorkspaceItem(
                1,
                "AppA",
                appAIntent,
                appUser,
                LauncherSettings.Favorites.CONTAINER_HOTSEAT,
            )

        hotseatItems.put(0, pinnedItemInHotseat)
        popupController.setHotseatInfosList(hotseatItems)
        val allAppsAppIcon = Mockito.mock(BubbleTextView::class.java)

        val shortcut =
            popupController.createPinShortcut(taskbarContext, itemFromAllApps, allAppsAppIcon)
        Assert.assertNotNull("Shortcut should not be null", shortcut)
        Assert.assertTrue(
            "Shortcut should be PinToTaskbarShortcut",
            shortcut is PinToTaskbarShortcut<*>,
        )
        Assert.assertFalse((shortcut as PinToTaskbarShortcut<*>).mIsPin)
    }

    private fun hasPopupMenu(): Boolean {
        return AbstractFloatingView.hasOpenView(
            taskbarContext,
            AbstractFloatingView.TYPE_ACTION_POPUP,
        )
    }
}
