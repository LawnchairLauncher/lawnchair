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

package com.android.launcher3.integration.popup

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.os.Process.myUserHandle
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import com.android.launcher3.BubbleTextView
import com.android.launcher3.Launcher
import com.android.launcher3.LauncherSettings.Favorites.CONTAINER_DESKTOP
import com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_APPLICATION
import com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_DEEP_SHORTCUT
import com.android.launcher3.R
import com.android.launcher3.integration.util.LauncherActivityScenarioRule
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.WorkspaceItemInfo
import com.android.launcher3.popup.PopupContainerWithArrow
import com.android.launcher3.popup.PopupController.PopupControllerFactory.createPopupController
import com.android.launcher3.shortcuts.DeepShortcutView
import com.android.launcher3.shortcuts.ShortcutKey
import com.android.launcher3.util.WorkspaceDragHelper.Companion.className
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class PopupAddDeepShortcutTest {
    private val targetContext: Context = getInstrumentation().targetContext
    private val launcherActivity = LauncherActivityScenarioRule<Launcher>(targetContext)
    private val userHandle = myUserHandle()
    private val appPackage = "appPackage"
    private val appClassName = "class"
    private val shortcutId = "fake_id"
    private val componentName = ComponentName(appPackage, appClassName)
    private val appIntent =
        Intent().apply {
            component = componentName
            `package` = appPackage
            action = "fake_action"
            putExtra(ShortcutKey.EXTRA_SHORTCUT_ID, shortcutId)
        }
    private val shortcutInfo =
        ShortcutInfo.Builder(getApplicationContext(), "fake_id")
            .setIntent(appIntent)
            .setActivity(componentName)
            .setShortLabel("testShortcut")
            .build()
    private val appItemInfo =
        WorkspaceItemInfo().apply {
            itemType = ITEM_TYPE_APPLICATION
            container = CONTAINER_DESKTOP
            user = userHandle
            screenId = 0
            intent = appIntent
        }
    private val shortcutItemInfo =
        WorkspaceItemInfo().apply {
            itemType = ITEM_TYPE_DEEP_SHORTCUT
            container = CONTAINER_DESKTOP
            user = userHandle
            screenId = 0
            intent = appIntent
        }

    @Before
    fun setUp() {
        removeItemsFromScreen()
    }

    @After
    fun cleanup() {
        removeItemsFromScreen()
    }

    private fun removeItemsFromScreen() {
        launcherActivity.executeOnLauncher { l: Launcher ->
            val container = l.workspace.getScreenWithId(0)
            container.removeAllViews()
        }
    }

    @Test
    fun testAddDeepShortcutToHomeScreen() {
        launcherActivity.executeOnLauncher { l: Launcher ->
            // Find where we'll be adding the deep shortcut.
            val coordinates = IntArray(2)
            val container = l.workspace.getScreenWithId(0)
            container.findCellForSpan(coordinates, 1, 1)

            // Add to home screen.
            val popupControllerForAppIcons = createPopupController<Launcher>()
            launcherActivity.executeOnLauncher { l: Launcher ->
                val appView = BubbleTextView(l)
                appView.tag = appItemInfo
                val popup =
                    popupControllerForAppIcons.show(appView) as PopupContainerWithArrow<Launcher>
                val themedContext = ContextThemeWrapper(l, R.style.PopupItem)
                val deepShortcutView =
                    LayoutInflater.from(themedContext).inflate(R.layout.deep_shortcut, null, false)
                        as DeepShortcutView
                deepShortcutView.applyShortcutInfo(shortcutItemInfo, shortcutInfo, popup, l)

                deepShortcutView
                    .findViewById<FrameLayout>(R.id.deep_shortcut_add_button)
                    .callOnClick()
                val itemInfo = container.getChildAt(coordinates[0], coordinates[1]).tag as ItemInfo

                // Verify we added item to home screen.
                assert(itemInfo.targetComponent == shortcutItemInfo.targetComponent)
                assert(itemInfo.className() == shortcutItemInfo.className())
                assert(itemInfo.itemType == ITEM_TYPE_DEEP_SHORTCUT)
            }
        }
    }
}
