/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.launcher3.celllayout.board

import android.content.ComponentName
import android.content.Context
import android.graphics.Rect
import android.os.Process
import android.os.UserHandle
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.LauncherSettings
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.model.data.FolderInfo
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.WorkspaceItemInfo
import com.android.launcher3.util.WidgetUtils
import com.android.launcher3.util.ui.TestViewHelpers
import com.android.launcher3.util.workspace.FavoriteItemsTransaction
import java.util.function.Supplier

class TestWorkspaceBuilder(private val mContext: Context) {

    private var appComponentName =
        ComponentName("com.google.android.calculator", "com.android.calculator2.Calculator")
    private val myUser: UserHandle = Process.myUserHandle()

    /** Fills the given rect in WidgetRect with 1x1 widgets. This is useful to equalize cases. */
    private fun fillWithWidgets(
        widgetRect: WidgetRect,
        transaction: FavoriteItemsTransaction,
        screenId: Int,
    ): FavoriteItemsTransaction {
        val initX = widgetRect.cellX
        val initY = widgetRect.cellY
        for (x in initX until initX + widgetRect.spanX) {
            for (y in initY until initY + widgetRect.spanY) {
                try {
                    // this widgets are filling, we don't care if we can't place them
                    transaction.addItem(
                        createWidgetInCell(WidgetRect(CellType.IGNORE, Rect(x, y, x, y)), screenId)
                    )
                } catch (e: Exception) {
                    Log.d(TAG, "Unable to place filling widget at $x,$y")
                }
            }
        }
        return transaction
    }

    private fun app() =
        AppInfo(appComponentName, "test icon", myUser, AppInfo.makeLaunchIntent(appComponentName))

    /**
     * Helper to set the app to use for the test workspace, using activity-alias from
     * AndroidManifest-common.
     *
     * @param testAppName the android:name field of the test app activity-alias to use
     */
    fun setTestAppActivityAlias(testAppName: String) {
        appComponentName =
            ComponentName(
                InstrumentationRegistry.getInstrumentation().context.packageName,
                TEST_ACTIVITY_PACKAGE_PREFIX + testAppName,
            )
    }

    /**
     * Sets the test app for app icons to the specified Component
     *
     * @param testAppComponent ComponentName to use for app icons
     */
    fun setTestAppComponent(testAppComponent: ComponentName) {
        appComponentName = testAppComponent
    }

    private fun addCorrespondingWidgetRect(
        widgetRect: WidgetRect,
        transaction: FavoriteItemsTransaction,
        screenId: Int,
    ) {
        if (widgetRect.type == 'x') {
            fillWithWidgets(widgetRect, transaction, screenId)
        } else {
            transaction.addItem(createWidgetInCell(widgetRect, screenId))
        }
    }

    /** Builds the given board into the transaction */
    fun buildFromBoard(
        board: CellLayoutBoard,
        transaction: FavoriteItemsTransaction,
        screenId: Int,
    ): FavoriteItemsTransaction {
        board.widgets.forEach { addCorrespondingWidgetRect(it, transaction, screenId) }
        board.icons.forEach { transaction.addItem { createIconInCell(it, screenId) } }
        board.folders.forEach { transaction.addItem { createFolderInCell(it, screenId) } }
        return transaction
    }

    /**
     * Fills the hotseat row with apps instead of suggestions, for this to work the workspace should
     * be clean otherwise this doesn't overrides the existing icons.
     */
    fun fillHotseatIcons(transaction: FavoriteItemsTransaction): FavoriteItemsTransaction {
        for (i in 0..<InvariantDeviceProfile.INSTANCE[mContext].numDatabaseHotseatIcons) {
            transaction.addItem { getHotseatValues(i) }
        }
        return transaction
    }

    private fun createWidgetInCell(widgetRect: WidgetRect, paramScreenId: Int): Supplier<ItemInfo> {
        // Create the widget lazily since the appWidgetId can get lost during setup
        return Supplier<ItemInfo> {
            WidgetUtils.createWidgetInfo(
                    TestViewHelpers.findWidgetProvider(false),
                    ApplicationProvider.getApplicationContext(),
                    true,
                )
                .apply {
                    cellX = widgetRect.cellX
                    cellY = widgetRect.cellY
                    spanX = widgetRect.spanX
                    spanY = widgetRect.spanY
                    screenId = paramScreenId
                }
        }
    }

    fun createFolderInCell(folderPoint: FolderPoint, paramScreenId: Int): FolderInfo =
        FolderInfo().apply {
            screenId = paramScreenId
            container = LauncherSettings.Favorites.CONTAINER_DESKTOP
            cellX = folderPoint.coord.x
            cellY = folderPoint.coord.y
            spanY = 1
            spanX = 1
            minSpanX = 1
            minSpanY = 1
            setOption(FolderInfo.FLAG_MULTI_PAGE_ANIMATION, true, null)
            for (i in 0 until folderPoint.numberIconsInside) {
                add(getDefaultWorkspaceItem(paramScreenId))
            }
        }

    private fun getDefaultWorkspaceItem(paramScreenId: Int): WorkspaceItemInfo =
        WorkspaceItemInfo(app()).apply {
            screenId = paramScreenId
            spanY = 1
            spanX = 1
            minSpanX = 1
            minSpanY = 1
            container = LauncherSettings.Favorites.CONTAINER_DESKTOP
        }

    private fun createIconInCell(iconPoint: IconPoint, paramScreenId: Int) =
        WorkspaceItemInfo(app()).apply {
            screenId = paramScreenId
            cellX = iconPoint.coord.x
            cellY = iconPoint.coord.y
            spanY = 1
            spanX = 1
            minSpanX = 1
            minSpanY = 1
            container = LauncherSettings.Favorites.CONTAINER_DESKTOP
        }

    private fun getHotseatValues(x: Int) =
        WorkspaceItemInfo(app()).apply {
            cellX = x
            cellY = 0
            spanY = 1
            spanX = 1
            minSpanX = 1
            minSpanY = 1
            rank = x
            screenId = x
            container = LauncherSettings.Favorites.CONTAINER_HOTSEAT
        }

    companion object {
        private const val TAG = "CellLayoutBoardBuilder"
        private const val TEST_ACTIVITY_PACKAGE_PREFIX = "com.android.launcher3.tests."
    }
}
