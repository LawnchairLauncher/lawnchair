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

package com.android.launcher3.util

import android.content.ComponentName
import android.content.pm.LauncherApps
import android.graphics.Bitmap
import android.graphics.Bitmap.Config.ARGB_8888
import android.os.Process.myUserHandle
import android.platform.uiautomatorhelpers.DeviceHelpers.context
import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.launcher3.BubbleTextView
import com.android.launcher3.graphics.PreloadIconDrawable
import com.android.launcher3.icons.BitmapInfo
import com.android.launcher3.icons.FastBitmapDrawable
import com.android.launcher3.icons.PlaceHolderIconDrawable
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.model.data.AppInfo.makeLaunchIntent
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.WorkspaceItemInfo
import com.android.launcher3.pm.PackageInstallInfo
import com.android.launcher3.util.Executors.MAIN_EXECUTOR
import com.android.launcher3.util.LauncherBindableItemsContainer.ItemOperator
import com.android.launcher3.util.LauncherModelHelper.TEST_ACTIVITY
import com.android.launcher3.util.LauncherModelHelper.TEST_ACTIVITY2
import com.android.launcher3.util.LauncherModelHelper.TEST_ACTIVITY3
import com.android.launcher3.util.LauncherModelHelper.TEST_PACKAGE
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class LauncherBindableItemsContainerTest {

    private val icon1 by lazy { getLAI(TEST_ACTIVITY) }
    private val icon2 by lazy { getLAI(TEST_ACTIVITY2) }
    private val icon3 by lazy { getLAI(TEST_ACTIVITY3) }

    private val container = TestContainer()

    @Test
    fun `icon bitmap is updated`() {
        container.addIcon(icon1)
        container.addIcon(icon2)
        container.addIcon(icon3)

        assertThat(container.getAppIcon(icon1).icon)
            .isInstanceOf(PlaceHolderIconDrawable::class.java)
        assertThat(container.getAppIcon(icon2).icon)
            .isInstanceOf(PlaceHolderIconDrawable::class.java)
        assertThat(container.getAppIcon(icon3).icon)
            .isInstanceOf(PlaceHolderIconDrawable::class.java)

        icon2.bitmap = BitmapInfo.fromBitmap(Bitmap.createBitmap(200, 200, ARGB_8888))
        TestUtil.runOnExecutorSync(MAIN_EXECUTOR) {
            container.updateContainerItems(setOf(icon2), container)
        }

        assertThat(container.getAppIcon(icon1).icon)
            .isInstanceOf(PlaceHolderIconDrawable::class.java)
        assertThat(container.getAppIcon(icon3).icon)
            .isInstanceOf(PlaceHolderIconDrawable::class.java)
        assertThat(container.getAppIcon(icon2).icon)
            .isNotInstanceOf(PlaceHolderIconDrawable::class.java)
        assertThat(container.getAppIcon(icon2).icon).isInstanceOf(FastBitmapDrawable::class.java)
    }

    @Test
    fun `icon download progress updated`() {
        container.addIcon(icon1)
        container.addIcon(icon2)
        assertThat(container.getAppIcon(icon1).icon)
            .isInstanceOf(PlaceHolderIconDrawable::class.java)
        assertThat(container.getAppIcon(icon2).icon)
            .isInstanceOf(PlaceHolderIconDrawable::class.java)

        icon1.status = WorkspaceItemInfo.FLAG_RESTORED_ICON
        icon1.bitmap = BitmapInfo.fromBitmap(Bitmap.createBitmap(200, 200, ARGB_8888))
        icon1.setProgressLevel(30, PackageInstallInfo.STATUS_INSTALLING)
        TestUtil.runOnExecutorSync(MAIN_EXECUTOR) {
            container.updateContainerItems(setOf(icon1), container)
        }

        assertThat(container.getAppIcon(icon2).icon)
            .isInstanceOf(PlaceHolderIconDrawable::class.java)
        assertThat(container.getAppIcon(icon1).icon).isInstanceOf(PreloadIconDrawable::class.java)
        val oldIcon = container.getAppIcon(icon1).icon as PreloadIconDrawable
        assertThat(oldIcon.level).isEqualTo(30)
    }

    private fun getLAI(className: String): WorkspaceItemInfo =
        AppInfo(
                context,
                context
                    .getSystemService(LauncherApps::class.java)!!
                    .resolveActivity(
                        makeLaunchIntent(ComponentName(TEST_PACKAGE, className)),
                        myUserHandle(),
                    )!!,
                myUserHandle(),
            )
            .makeWorkspaceItem(context)

    class TestContainer : ActivityContextWrapper(context), LauncherBindableItemsContainer {

        val items = mutableMapOf<ItemInfo, View>()

        override fun mapOverItems(op: ItemOperator): View? =
            items.firstNotNullOfOrNull { (item, view) ->
                if (op.evaluate(item, view)) view else null
            }

        fun addIcon(info: WorkspaceItemInfo) {
            val btv = BubbleTextView(this)
            btv.applyFromWorkspaceItem(info)
            items[info] = btv
        }

        fun getAppIcon(info: WorkspaceItemInfo) = items[info] as BubbleTextView
    }
}
