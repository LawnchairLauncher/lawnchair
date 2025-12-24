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
import android.os.Process.myUserHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import com.android.launcher3.BubbleTextView
import com.android.launcher3.Launcher
import com.android.launcher3.LauncherSettings.Favorites
import com.android.launcher3.integration.util.LauncherActivityScenarioRule
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.WorkspaceItemInfo
import com.android.launcher3.popup.SystemShortcut
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class PopupAddToHomeScreenTest {
    private val targetContext: Context = getInstrumentation().targetContext
    private val launcherActivity = LauncherActivityScenarioRule<Launcher>(targetContext)
    private val userHandle = myUserHandle()
    private val appPackage = "appPackage"
    private val componentName = ComponentName(appPackage, "class")
    private val appIntent =
        Intent().apply {
            component = componentName
            setPackage(appPackage)
        }

    private val appItemInfo =
        WorkspaceItemInfo().apply {
            itemType = Favorites.ITEM_TYPE_APPLICATION
            container = Favorites.CONTAINER_ALL_APPS
            user = userHandle
            screenId = 0
            intent = appIntent
        }

    @Before
    fun setUp() {
        launcherActivity.executeOnLauncher { l: Launcher ->
            val container = l.workspace.getScreenWithId(0)
            container.removeAllViews()
        }
    }

    @Test
    fun testAddToHomeScreenShortcut() {
        launcherActivity.executeOnLauncher { l: Launcher ->
            // Find where we'll be adding the app.
            val coordinates = IntArray(2)
            val container = l.workspace.getScreenWithId(0)
            container.findCellForSpan(coordinates, 1, 1)

            // Add to home screen.
            val appView = BubbleTextView(l)
            appView.tag = appItemInfo
            val systemShortcut: SystemShortcut<*>? =
                SystemShortcut.ADD_TO_HOME_SCREEN.getShortcut(l, appItemInfo, appView)
            systemShortcut?.onClick(appView)
            val itemInfo = container.getChildAt(coordinates[0], coordinates[1]).tag as ItemInfo

            // Verify we added item to home screen.
            assert(itemInfo.targetComponent == appItemInfo.targetComponent)

            // Clean-up.
            container.removeAllViews()
        }
    }
}
