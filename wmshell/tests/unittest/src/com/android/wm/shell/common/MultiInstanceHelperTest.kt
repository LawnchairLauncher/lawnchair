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

package com.android.wm.shell.common

import android.app.ActivityTaskManager
import android.content.ComponentName
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.content.pm.ShortcutInfo
import android.os.UserHandle
import android.view.WindowManager.PROPERTY_SUPPORTS_MULTI_INSTANCE_SYSTEM_UI
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.wm.shell.ShellTestCase
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.eq
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class MultiInstanceHelperTest : ShellTestCase() {

    @Before
    fun setup() {
        assumeTrue(ActivityTaskManager.supportsSplitScreenMultiWindow(mContext))
    }

    @Test
    fun getShortcutComponent_nullShortcuts() {
        val launcherApps = mock<LauncherApps>()
        whenever(launcherApps.getShortcuts(ArgumentMatchers.any(), ArgumentMatchers.any()))
                .thenReturn(null)
        assertEquals(null, MultiInstanceHelper.getShortcutComponent(TEST_PACKAGE,
            TEST_SHORTCUT_ID, UserHandle.CURRENT, launcherApps))
    }

    @Test
    fun getShortcutComponent_noShortcuts() {
        val launcherApps = mock<LauncherApps>()
        whenever(launcherApps.getShortcuts(ArgumentMatchers.any(), ArgumentMatchers.any()))
                .thenReturn(ArrayList<ShortcutInfo>())
        assertEquals(null, MultiInstanceHelper.getShortcutComponent(TEST_PACKAGE,
            TEST_SHORTCUT_ID, UserHandle.CURRENT, launcherApps))
    }

    @Test
    fun getShortcutComponent_validShortcut() {
        val component = ComponentName(TEST_PACKAGE, TEST_ACTIVITY)
        val shortcutInfo = ShortcutInfo.Builder(context, "id").setActivity(component).build()
        val launcherApps = mock<LauncherApps>()
        whenever(launcherApps.getShortcuts(ArgumentMatchers.any(), ArgumentMatchers.any()))
                .thenReturn(arrayListOf(shortcutInfo))
        assertEquals(component, MultiInstanceHelper.getShortcutComponent(TEST_PACKAGE,
            TEST_SHORTCUT_ID, UserHandle.CURRENT, launcherApps))
    }

    @Test
    fun supportsMultiInstanceSplit_inStaticAllowList() {
        val allowList = arrayOf(TEST_PACKAGE)
        val helper = MultiInstanceHelper(mContext, context.packageManager, allowList, true)
        val component = ComponentName(TEST_PACKAGE, TEST_ACTIVITY)
        assertEquals(true, helper.supportsMultiInstanceSplit(component))
    }

    @Test
    fun supportsMultiInstanceSplit_notInStaticAllowList() {
        val allowList = arrayOf(TEST_PACKAGE)
        val helper = MultiInstanceHelper(mContext, context.packageManager, allowList, true)
        val component = ComponentName(TEST_NOT_ALLOWED_PACKAGE, TEST_ACTIVITY)
        assertEquals(false, helper.supportsMultiInstanceSplit(component))
    }

    @Test
    @Throws(PackageManager.NameNotFoundException::class)
    fun supportsMultiInstanceSplit_activityPropertyTrue() {
        val component = ComponentName(TEST_PACKAGE, TEST_ACTIVITY)
        val pm = mock<PackageManager>()
        val activityProp = PackageManager.Property("", true, "", "")
        whenever(pm.getProperty(eq(PROPERTY_SUPPORTS_MULTI_INSTANCE_SYSTEM_UI),
            eq(component)))
                .thenReturn(activityProp)
        val appProp = PackageManager.Property("", false, "", "")
        whenever(pm.getProperty(eq(PROPERTY_SUPPORTS_MULTI_INSTANCE_SYSTEM_UI),
            eq(component.packageName)))
                .thenReturn(appProp)

        val helper = MultiInstanceHelper(mContext, pm, emptyArray(), true)
        // Expect activity property to override application property
        assertEquals(true, helper.supportsMultiInstanceSplit(component))
    }

    @Test
    @Throws(PackageManager.NameNotFoundException::class)
    fun supportsMultiInstanceSplit_activityPropertyFalseApplicationPropertyTrue() {
        val component = ComponentName(TEST_PACKAGE, TEST_ACTIVITY)
        val pm = mock<PackageManager>()
        val activityProp = PackageManager.Property("", false, "", "")
        whenever(pm.getProperty(eq(PROPERTY_SUPPORTS_MULTI_INSTANCE_SYSTEM_UI),
            eq(component)))
                .thenReturn(activityProp)
        val appProp = PackageManager.Property("", true, "", "")
        whenever(pm.getProperty(eq(PROPERTY_SUPPORTS_MULTI_INSTANCE_SYSTEM_UI),
            eq(component.packageName)))
                .thenReturn(appProp)

        val helper = MultiInstanceHelper(mContext, pm, emptyArray(), true)
        // Expect activity property to override application property
        assertEquals(false, helper.supportsMultiInstanceSplit(component))
    }

    @Test
    @Throws(PackageManager.NameNotFoundException::class)
    fun supportsMultiInstanceSplit_noActivityPropertyApplicationPropertyTrue() {
        val component = ComponentName(TEST_PACKAGE, TEST_ACTIVITY)
        val pm = mock<PackageManager>()
        whenever(pm.getProperty(eq(PROPERTY_SUPPORTS_MULTI_INSTANCE_SYSTEM_UI),
            eq(component)))
                .thenThrow(PackageManager.NameNotFoundException())
        val appProp = PackageManager.Property("", true, "", "")
        whenever(pm.getProperty(eq(PROPERTY_SUPPORTS_MULTI_INSTANCE_SYSTEM_UI),
            eq(component.packageName)))
                .thenReturn(appProp)

        val helper = MultiInstanceHelper(mContext, pm, emptyArray(), true)
        // Expect fall through to app property
        assertEquals(true, helper.supportsMultiInstanceSplit(component))
    }

    @Test
    @Throws(PackageManager.NameNotFoundException::class)
    fun supportsMultiInstanceSplit_noActivityOrAppProperty() {
        val component = ComponentName(TEST_PACKAGE, TEST_ACTIVITY)
        val pm = mock<PackageManager>()
        whenever(pm.getProperty(eq(PROPERTY_SUPPORTS_MULTI_INSTANCE_SYSTEM_UI),
            eq(component)))
                .thenThrow(PackageManager.NameNotFoundException())
        whenever(pm.getProperty(eq(PROPERTY_SUPPORTS_MULTI_INSTANCE_SYSTEM_UI),
            eq(component.packageName)))
                .thenThrow(PackageManager.NameNotFoundException())

        val helper = MultiInstanceHelper(mContext, pm, emptyArray(), true)
        assertEquals(false, helper.supportsMultiInstanceSplit(component))
    }

    @Test
    @Throws(PackageManager.NameNotFoundException::class)
    fun checkNoMultiInstancePropertyFlag_ignoreProperty() {
        val component = ComponentName(TEST_PACKAGE, TEST_ACTIVITY)
        val pm = mock<PackageManager>()
        val activityProp = PackageManager.Property("", true, "", "")
        whenever(pm.getProperty(eq(PROPERTY_SUPPORTS_MULTI_INSTANCE_SYSTEM_UI),
            eq(component)))
            .thenReturn(activityProp)
        val appProp = PackageManager.Property("", true, "", "")
        whenever(pm.getProperty(eq(PROPERTY_SUPPORTS_MULTI_INSTANCE_SYSTEM_UI),
            eq(component.packageName)))
            .thenReturn(appProp)

        val helper = MultiInstanceHelper(mContext, pm, emptyArray(), false)
        // Expect we only check the static list and not the property
        assertEquals(false, helper.supportsMultiInstanceSplit(component))
        verify(pm, never()).getProperty(any(), any<ComponentName>())
    }

    companion object {
        val TEST_PACKAGE = "com.android.wm.shell.common"
        val TEST_NOT_ALLOWED_PACKAGE = "com.android.wm.shell.common.fake";
        val TEST_ACTIVITY = "TestActivity";
        val TEST_SHORTCUT_ID = "test_shortcut_1"
    }
}