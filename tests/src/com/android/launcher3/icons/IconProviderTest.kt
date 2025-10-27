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

package com.android.launcher3.icons

import android.content.ComponentName
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageItemInfo
import android.content.pm.PackageManager
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Parcel
import android.os.Parcelable.Creator
import android.os.Process
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.widget.WidgetManagerHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Tests for IconProvider */
@LargeTest
@RunWith(AndroidJUnit4::class)
class IconProviderTest {

    lateinit var context: Context
    lateinit var pm: PackageManager
    lateinit var iconProvider: IconProvider

    lateinit var testContext: Context

    @Before
    fun setup() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        pm = context.packageManager
        iconProvider = IconProvider(context)

        testContext = InstrumentationRegistry.getInstrumentation().context
    }

    @Test
    fun launcherActivityInfo_activity_icon() {
        val icon = iconProvider.getIcon(getLauncherActivityInfo(DiffIconActivity).activityInfo)
        assertNotNull(icon)
        verifyIconResName(icon, ICON_DIFFERENT_ACTIVITY)
    }

    @Test
    fun packageActivityInfo_activity_icon() {
        val icon = iconProvider.getIcon(getPackageActivityInfo(DiffIconActivity))
        assertNotNull(icon)
        verifyIconResName(icon, ICON_DIFFERENT_ACTIVITY)
    }

    @Test
    fun launcherActivityInfo_wrong_icon() {
        val ai =
            getLauncherActivityInfo(WrongIconActivity)
                .activityInfo
                .overrideAppIcon(ActivityInfo.CREATOR)
        assertEquals(ai.icon.toResourceName(), ICON_WRONG_DRAWABLE)
        val icon = iconProvider.getIcon(ai)
        assertNotNull(icon)
        // App icon is loaded if the drawable is not found
        verifyIconResName(icon, ICON_APP_INFO)
    }

    @Test
    fun packageActivityInfo_wrong_icon() {
        val ai = getPackageActivityInfo(WrongIconActivity)
        assertEquals(ai.icon.toResourceName(), ICON_WRONG_DRAWABLE)
        assertNotEquals(ai.icon, 0)
        val icon = iconProvider.getIcon(ai)
        assertNotNull(icon)
        // App icon is loaded if the drawable is not found
        verifyIconResName(icon, ICON_APP_INFO)
    }

    @Test
    fun launcherActivityInfo_fallback_to_icon() {
        val ai =
            getLauncherActivityInfo(AppIconActivity)
                .activityInfo
                .overrideAppIcon(ActivityInfo.CREATOR)
        assertEquals(ai.icon, 0)
        val icon = iconProvider.getIcon(ai)
        assertNotNull(icon)
        // App icon is loaded if component icon is not defined
        verifyIconResName(icon, ICON_APP_INFO)
    }

    @Test
    fun packageActivityInfo_fallback_to_icon() {
        val ai = getPackageActivityInfo(AppIconActivity)
        assertEquals(ai.icon, 0)
        val icon = iconProvider.getIcon(ai)
        assertNotNull(icon)
        // App icon is loaded if component icon is not defined
        verifyIconResName(icon, ICON_APP_INFO)
    }

    @Test
    fun applicationInfo_icon() {
        val appInfo =
            getLauncherActivityInfo(AppIconActivity)
                .applicationInfo
                .overrideAppIcon(ApplicationInfo.CREATOR)
        val icon = iconProvider.getIcon(appInfo)
        assertNotNull(icon)
        verifyIconResName(icon, ICON_APP_INFO)
    }

    @Test
    fun applicationInfo_wrong_icon() {
        val appInfo =
            getLauncherActivityInfo(AppIconActivity)
                .applicationInfo
                .overrideAppIcon(ApplicationInfo.CREATOR)
        appInfo.icon = 0

        val icon = iconProvider.getIcon(appInfo)
        assertNotNull(icon)
        // Fallback is loaded if the drawable is defined
        assertTrue(pm.isDefaultApplicationIcon(icon))
    }

    @Test
    fun appwidgetProviderInfo_icon() {
        val widgetInfo =
            WidgetManagerHelper(context)
                .findProvider(ComponentName(testContext, AppWidgetNoConfig), Process.myUserHandle())
        assertNotNull(widgetInfo)

        val icon = iconProvider.getIcon(widgetInfo.activityInfo)
        assertNotNull(icon)
        verifyIconResName(icon, ICON_WIDGET_NO_CONFIG)
    }

    private fun verifyIconResName(icon: Drawable, resName: String) {
        assertTrue(icon is AdaptiveIconDrawable)
        assertEquals(resName, (icon as AdaptiveIconDrawable).sourceDrawableResId.toResourceName())
    }

    private fun Int.toResourceName() = testContext.resources.getResourceEntryName(this)

    private fun getLauncherActivityInfo(className: String): LauncherActivityInfo =
        context
            .getSystemService(LauncherApps::class.java)!!
            .resolveActivity(getActivityIntent(className), Process.myUserHandle())

    private fun getPackageActivityInfo(className: String): ActivityInfo =
        pm.resolveActivity(getActivityIntent(className), 0)!!
            .activityInfo
            .overrideAppIcon(ActivityInfo.CREATOR)

    private fun <T : PackageItemInfo> PackageItemInfo.overrideAppIcon(creator: Creator<T>): T {
        // Clone the obj since it may have been cached by the system
        val p = Parcel.obtain()
        writeToParcel(p, 0)
        p.setDataPosition(0)
        val result = creator.createFromParcel(p)
        p.recycle()
        result.applicationInfo.icon =
            testContext.resources.getIdentifier(ICON_APP_INFO, "drawable", testContext.packageName)
        return result
    }

    private fun getActivityIntent(className: String) =
        AppInfo.makeLaunchIntent(ComponentName(testContext, className))

    companion object {
        private const val AppIconActivity = "com.android.launcher3.tests.AppIconActivity"
        private const val DiffIconActivity = "com.android.launcher3.tests.DiffIconActivity"
        private const val WrongIconActivity = "com.android.launcher3.tests.WrongIconActivity"
        private const val AppWidgetNoConfig =
            "com.android.launcher3.testcomponent.AppWidgetNoConfig"

        private const val ICON_DIFFERENT_ACTIVITY = "test_different_activity_icon"
        private const val ICON_APP_INFO = "test_app_info_icon"
        private const val ICON_WRONG_DRAWABLE = "test_wrong_activity_icon"
        private const val ICON_WIDGET_NO_CONFIG = "test_widget_no_config_icon"
    }
}
