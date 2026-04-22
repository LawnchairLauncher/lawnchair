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

package com.android.launcher3.icons.cache

import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.SetFlagsRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.launcher3.icons.cache.CacheLookupFlag.Companion.DEFAULT_LOOKUP_FLAG
import com.android.systemui.shared.Flags.FLAG_EXTENDIBLE_THEME_MANAGER
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class CacheLookupFlagTest {

    @get:Rule val flags = SetFlagsRule()

    @Test
    fun `useLowRes preserves lowRes values`() {
        assertFalse(DEFAULT_LOOKUP_FLAG.useLowRes())
        assertTrue(DEFAULT_LOOKUP_FLAG.withUseLowRes().useLowRes())
        assertFalse(DEFAULT_LOOKUP_FLAG.withUseLowRes().withUseLowRes(false).useLowRes())
        assertTrue(
            DEFAULT_LOOKUP_FLAG.withUseLowRes().withUseLowRes(false).withUseLowRes().useLowRes()
        )
    }

    @Test
    fun `usePackageIcon preserves lowRes values`() {
        assertFalse(DEFAULT_LOOKUP_FLAG.usePackageIcon())
        assertTrue(DEFAULT_LOOKUP_FLAG.withUsePackageIcon().usePackageIcon())
        assertFalse(
            DEFAULT_LOOKUP_FLAG.withUsePackageIcon().withUsePackageIcon(false).usePackageIcon()
        )
        assertTrue(
            DEFAULT_LOOKUP_FLAG.withUsePackageIcon()
                .withUsePackageIcon(false)
                .withUsePackageIcon()
                .usePackageIcon()
        )
    }

    @Test
    fun `skipAddToMemCache preserves lowRes values`() {
        assertFalse(DEFAULT_LOOKUP_FLAG.skipAddToMemCache())
        assertTrue(DEFAULT_LOOKUP_FLAG.withSkipAddToMemCache().skipAddToMemCache())
        assertFalse(
            DEFAULT_LOOKUP_FLAG.withSkipAddToMemCache()
                .withSkipAddToMemCache(false)
                .skipAddToMemCache()
        )
        assertTrue(
            DEFAULT_LOOKUP_FLAG.withSkipAddToMemCache()
                .withSkipAddToMemCache(false)
                .withSkipAddToMemCache()
                .skipAddToMemCache()
        )
    }

    @Test
    fun `preserves multiple flags`() {
        val flag = DEFAULT_LOOKUP_FLAG.withSkipAddToMemCache().withUseLowRes()

        assertTrue(flag.skipAddToMemCache())
        assertTrue(flag.useLowRes())
        assertFalse(flag.usePackageIcon())
    }

    @Test
    fun `isVisuallyLessThan does not depend on package icon`() {
        assertFalse(DEFAULT_LOOKUP_FLAG.isVisuallyLessThan(DEFAULT_LOOKUP_FLAG))
        assertFalse(
            DEFAULT_LOOKUP_FLAG.withUsePackageIcon().isVisuallyLessThan(DEFAULT_LOOKUP_FLAG)
        )
        assertFalse(
            DEFAULT_LOOKUP_FLAG.isVisuallyLessThan(DEFAULT_LOOKUP_FLAG.withUsePackageIcon())
        )
    }

    @Test
    fun `isVisuallyLessThan depends on low res`() {
        assertTrue(DEFAULT_LOOKUP_FLAG.withUseLowRes().isVisuallyLessThan(DEFAULT_LOOKUP_FLAG))
        assertFalse(DEFAULT_LOOKUP_FLAG.isVisuallyLessThan(DEFAULT_LOOKUP_FLAG.withUseLowRes()))
        assertTrue(
            DEFAULT_LOOKUP_FLAG.withUseLowRes()
                .isVisuallyLessThan(DEFAULT_LOOKUP_FLAG.withUsePackageIcon())
        )
    }

    @DisableFlags(FLAG_EXTENDIBLE_THEME_MANAGER)
    @Test
    fun `isVisuallyLessThan does not depend on theme with flag off`() {
        assertFalse(DEFAULT_LOOKUP_FLAG.withThemeIcon().isVisuallyLessThan(DEFAULT_LOOKUP_FLAG))
        assertFalse(DEFAULT_LOOKUP_FLAG.isVisuallyLessThan(DEFAULT_LOOKUP_FLAG.withThemeIcon()))
    }

    @EnableFlags(FLAG_EXTENDIBLE_THEME_MANAGER)
    @Test
    fun `isVisuallyLessThan depends on theme with flag on`() {
        assertFalse(DEFAULT_LOOKUP_FLAG.withThemeIcon().isVisuallyLessThan(DEFAULT_LOOKUP_FLAG))
        assertTrue(DEFAULT_LOOKUP_FLAG.isVisuallyLessThan(DEFAULT_LOOKUP_FLAG.withThemeIcon()))
    }
}
