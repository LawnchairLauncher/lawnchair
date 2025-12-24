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

package com.android.launcher3.model

import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import android.os.Process
import android.platform.test.flag.junit.SetFlagsRule
import android.util.Log
import androidx.core.graphics.drawable.toDrawable
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_DEEP_SHORTCUT
import com.android.launcher3.icons.BitmapRenderer.createSoftwareBitmap
import com.android.launcher3.icons.cache.CacheLookupFlag.Companion.DEFAULT_LOOKUP_FLAG
import com.android.launcher3.model.data.WorkspaceItemInfo
import com.android.launcher3.shortcuts.ShortcutKey
import com.android.launcher3.util.Executors
import com.android.launcher3.util.LauncherLayoutBuilder
import com.android.launcher3.util.LauncherModelHelper.SHORTCUT_ID
import com.android.launcher3.util.LauncherModelHelper.TEST_PACKAGE
import com.android.launcher3.util.LayoutResource
import com.android.launcher3.util.ModelTestExtensions.isPersistedModelItem
import com.android.launcher3.util.ModelTestExtensions.loadModelSync
import com.android.launcher3.util.RoboApiWrapper
import com.android.launcher3.util.SandboxApplication
import com.android.launcher3.util.TestUtil
import com.android.launcher3.util.UserIconInfo
import com.android.launcher3.util.rule.MockUsersRule
import com.android.launcher3.util.rule.MockUsersRule.MockUser
import java.util.concurrent.CountDownLatch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.junit.MockitoJUnit
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class ShortcutIconTest {

    @get:Rule val mockitoRule = MockitoJUnit.rule()
    @get:Rule val setFlagsRule: SetFlagsRule = SetFlagsRule()
    @get:Rule val app = SandboxApplication().withModelDependency()
    @get:Rule val layoutResource = LayoutResource(app)
    @get:Rule val shortcutAccessRule = RoboApiWrapper.grantShortcutsPermissionRule()
    @get:Rule val mockUsers = MockUsersRule(app)

    val state: TestableModelState by lazy { app.appComponent.testableModelState }

    @Test
    @MockUser(userType = UserIconInfo.TYPE_MAIN)
    fun shortcut_icon_retained_even_after_system_error() {
        // Setup mock LauncherApps to return a valid drawable
        val iconBitmap = createSoftwareBitmap(200, 200) { it.drawColor(Color.GREEN) }
        var drawableProvider: () -> Drawable? = { iconBitmap.toDrawable(app.resources) }
        val la = app.spyService(LauncherApps::class.java)
        doAnswer {
                Log.e("Hello", "Getting icon " + it.arguments.get(0))
                drawableProvider.invoke()
            }
            .whenever(la)
            .getShortcutIconDrawable(argThat { id == SHORTCUT_ID }, any())
        doReturn(
                listOf(
                    ShortcutInfo.Builder(getInstrumentation().context, SHORTCUT_ID)
                        .setIntent(Intent(Intent.ACTION_VIEW))
                        .setShortLabel("Test")
                        .setIcon(Icon.createWithBitmap(iconBitmap))
                        .build()
                )
            )
            .whenever(la)
            .getShortcuts(any(), any())

        layoutResource.set(
            LauncherLayoutBuilder().atHotseat(0).putShortcut(TEST_PACKAGE, SHORTCUT_ID)
        )
        state.model.loadModelSync()
        state.model.forceReload()
        state.model.loadModelSync()
        verifyIconHasIcon()

        // Ensure that the icon is saved in the main DB
        TestUtil.runOnExecutorSync(Executors.MODEL_EXECUTOR) {
            state.model.getWriter(false, null, null).updateItemInDatabase(getPinnedInfo())
        }

        // Make the system fail to load shortcut icons
        drawableProvider = { null }

        // Clear cache and reload
        TestUtil.runOnExecutorSync(Executors.MODEL_EXECUTOR) {
            state.iconCache.getUpdateHandler()
            state.iconCache.removeIconsForPkg(TEST_PACKAGE, Process.myUserHandle())
        }
        state.model.forceReload()
        state.model.loadModelSync()
        verifyIconHasIcon()

        // Ensure that updating the icon on UI continues to show valid icon
        TestUtil.runOnExecutorSync(Executors.MODEL_EXECUTOR) {
            state.iconCache.getUpdateHandler()
            state.iconCache.removeIconsForPkg(TEST_PACKAGE, Process.myUserHandle())
        }
        val updateLock = CountDownLatch(1)
        state.iconCache.updateIconInBackground(
            { updateLock.countDown() },
            getPinnedInfo(),
            DEFAULT_LOOKUP_FLAG.withThemeIcon(),
        )
        updateLock.await()
        verifyIconHasIcon()
    }

    private fun verifyIconHasIcon() {
        val info = getPinnedInfo()
        assertEquals(SHORTCUT_ID, ShortcutKey.fromItemInfo(info).id)
        assertFalse("Item has low-res icon", info.bitmap.isLowRes)
        assertFalse("Item has default icon", state.iconCache.isDefaultIcon(info.bitmap, info.user))
    }

    private fun getPinnedInfo() =
        state.homeRepo.workspaceState.value.first {
            it.itemType == ITEM_TYPE_DEEP_SHORTCUT && it.isPersistedModelItem()
        } as WorkspaceItemInfo
}
