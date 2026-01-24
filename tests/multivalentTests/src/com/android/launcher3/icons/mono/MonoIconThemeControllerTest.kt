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

package com.android.launcher3.icons.mono

import android.content.ComponentName
import android.graphics.Color
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.ColorDrawable
import android.os.Process
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.SetFlagsRule
import android.platform.uiautomatorhelpers.DeviceHelpers.context
import android.util.DisplayMetrics
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.launcher3.Flags
import com.android.launcher3.icons.BaseIconFactory
import com.android.launcher3.icons.BitmapInfo
import com.android.launcher3.icons.SourceHint
import com.android.launcher3.icons.ThemedBitmap
import com.android.launcher3.icons.cache.LauncherActivityCachingLogic
import com.android.launcher3.util.ComponentKey
import com.android.launcher3.util.LauncherMultivalentJUnit.Companion.isRunningInRobolectric
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assume.assumeFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class MonoIconThemeControllerTest {

    @get:Rule val mSetFlagsRule = SetFlagsRule()

    private val iconFactory = BaseIconFactory(context, DisplayMetrics.DENSITY_MEDIUM, 30)

    private val sourceHint =
        SourceHint(
            key = ComponentKey(ComponentName("a", "a"), Process.myUserHandle()),
            logic = LauncherActivityCachingLogic,
        )

    @Test
    fun `createThemedBitmap when mono drawable is present`() {
        val icon = AdaptiveIconDrawable(ColorDrawable(Color.BLACK), null, ColorDrawable(Color.RED))
        assertNotNull(
            MonoIconThemeController().createThemedBitmap(icon, BitmapInfo.LOW_RES_INFO, iconFactory)
        )
    }

    @Test
    @DisableFlags(Flags.FLAG_FORCE_MONOCHROME_APP_ICONS)
    fun `createThemedBitmap when mono generation is disabled`() {
        val icon = AdaptiveIconDrawable(ColorDrawable(Color.BLACK), null, null)
        assertNull(
            MonoIconThemeController().createThemedBitmap(icon, BitmapInfo.LOW_RES_INFO, iconFactory)
        )
    }

    @Test
    @EnableFlags(Flags.FLAG_FORCE_MONOCHROME_APP_ICONS)
    fun `createThemedBitmap when mono generation is enabled`() {
        ensureBitmapSerializationSupported()
        val icon = AdaptiveIconDrawable(ColorDrawable(Color.BLACK), null, null)
        assertNotNull(
            MonoIconThemeController(shouldForceThemeIcon = true)
                .createThemedBitmap(icon, BitmapInfo.LOW_RES_INFO, iconFactory)
        )
    }

    @Test
    fun `decode bitmap after serialization valid data`() {
        ensureBitmapSerializationSupported()
        val icon = AdaptiveIconDrawable(ColorDrawable(Color.BLACK), null, ColorDrawable(Color.RED))
        val iconInfo = iconFactory.createBadgedIconBitmap(icon)

        val themeBitmap =
            MonoIconThemeController().createThemedBitmap(icon, iconInfo, iconFactory)!!
        assertNotSame(
            ThemedBitmap.NOT_SUPPORTED,
            MonoIconThemeController()
                .decode(themeBitmap.serialize(), iconInfo, iconFactory, sourceHint),
        )
    }

    @Test
    fun `decode bitmap after serialization invalid data`() {
        ensureBitmapSerializationSupported()
        val icon = AdaptiveIconDrawable(ColorDrawable(Color.BLACK), null, ColorDrawable(Color.RED))
        val iconInfo = iconFactory.createBadgedIconBitmap(icon)
        assertSame(
            ThemedBitmap.NOT_SUPPORTED,
            MonoIconThemeController()
                .decode(byteArrayOf(1, 1, 1, 1), iconInfo, iconFactory, sourceHint),
        )
    }

    @Test
    fun `createThemedAdaptiveIcon with monochrome drawable`() {
        val icon = AdaptiveIconDrawable(ColorDrawable(Color.BLACK), null, ColorDrawable(Color.RED))
        assertNotNull(MonoIconThemeController().createThemedAdaptiveIcon(context, icon, null))
    }

    @Test
    fun `createThemedAdaptiveIcon with bitmap info`() {
        val icon = AdaptiveIconDrawable(ColorDrawable(Color.BLACK), null, ColorDrawable(Color.RED))
        val iconInfo = iconFactory.createBadgedIconBitmap(icon)
        iconInfo.themedBitmap =
            MonoIconThemeController().createThemedBitmap(icon, iconInfo, iconFactory)

        val nonMonoIcon = AdaptiveIconDrawable(ColorDrawable(Color.BLACK), null, null)
        assertNotSame(
            nonMonoIcon,
            MonoIconThemeController().createThemedAdaptiveIcon(context, nonMonoIcon, iconInfo),
        )
    }

    @Test
    fun `createThemedAdaptiveIcon invalid bitmap info`() {
        val nonMonoIcon = AdaptiveIconDrawable(ColorDrawable(Color.BLACK), null, null)
        assertSame(
            nonMonoIcon,
            MonoIconThemeController()
                .createThemedAdaptiveIcon(context, nonMonoIcon, BitmapInfo.LOW_RES_INFO),
        )
    }

    companion object {

        fun ensureBitmapSerializationSupported() {
            // Robolectric doesn't support serializing 8-bit bitmaps
            assumeFalse(isRunningInRobolectric)
        }
    }
}
