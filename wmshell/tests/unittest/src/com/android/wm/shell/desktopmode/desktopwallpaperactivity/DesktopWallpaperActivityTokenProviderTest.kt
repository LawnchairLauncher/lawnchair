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

package com.android.wm.shell.desktopmode.desktopwallpaperactivity

import android.testing.AndroidTestingRunner
import android.testing.TestableLooper.RunWithLooper
import android.view.Display.DEFAULT_DISPLAY
import androidx.test.filters.SmallTest
import com.android.wm.shell.MockToken
import com.android.wm.shell.ShellTestCase
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Test class for [DesktopWallpaperActivityTokenProvider]
 *
 * Usage: atest WMShellUnitTests:DesktopWallpaperActivityTokenProviderTest
 */
@SmallTest
@RunWithLooper
@RunWith(AndroidTestingRunner::class)
class DesktopWallpaperActivityTokenProviderTest : ShellTestCase() {

    private lateinit var provider: DesktopWallpaperActivityTokenProvider
    private val DEFAULT_DISPLAY = 0
    private val SECONDARY_DISPLAY = 1

    @Before
    fun setUp() {
        provider = DesktopWallpaperActivityTokenProvider()
    }

    @Test
    fun setToken_setsTokenForDisplay() {
        val token = MockToken().token()

        provider.setToken(token, DEFAULT_DISPLAY)

        assertThat(provider.getToken(DEFAULT_DISPLAY)).isEqualTo(token)
    }

    @Test
    fun setToken_overwritesExistingTokenForDisplay() {
        val token1 = MockToken().token()
        val token2 = MockToken().token()

        provider.setToken(token1, DEFAULT_DISPLAY)
        provider.setToken(token2, DEFAULT_DISPLAY)

        assertThat(provider.getToken(DEFAULT_DISPLAY)).isEqualTo(token2)
    }

    @Test
    fun getToken_returnsNullForNonExistentDisplay() {
        assertThat(provider.getToken(SECONDARY_DISPLAY)).isNull()
    }

    @Test
    fun removeToken_removesTokenForDisplay() {
        val token = MockToken().token()

        provider.setToken(token, DEFAULT_DISPLAY)
        provider.removeToken(DEFAULT_DISPLAY)

        assertThat(provider.getToken(DEFAULT_DISPLAY)).isNull()
    }

    @Test
    fun removeToken_withToken_removesTokenForDisplay() {
        val token = MockToken().token()

        provider.setToken(token, DEFAULT_DISPLAY)
        provider.removeToken(token)

        assertThat(provider.getToken(DEFAULT_DISPLAY)).isNull()
    }

    @Test
    fun removeToken_doesNothingForNonExistentDisplay() {
        provider.removeToken(SECONDARY_DISPLAY)

        assertThat(provider.getToken(SECONDARY_DISPLAY)).isNull()
    }

    @Test
    fun removeToken_withNonExistentToken_doesNothing() {
        val token1 = MockToken().token()
        val token2 = MockToken().token()

        provider.setToken(token1, DEFAULT_DISPLAY)
        provider.removeToken(token2)

        assertThat(provider.getToken(DEFAULT_DISPLAY)).isEqualTo(token1)
    }

    @Test
    fun multipleDisplays_tokensAreIndependent() {
        val token1 = MockToken().token()
        val token2 = MockToken().token()

        provider.setToken(token1, DEFAULT_DISPLAY)
        provider.setToken(token2, SECONDARY_DISPLAY)

        assertThat(provider.getToken(DEFAULT_DISPLAY)).isEqualTo(token1)
        assertThat(provider.getToken(SECONDARY_DISPLAY)).isEqualTo(token2)

        provider.removeToken(DEFAULT_DISPLAY)

        assertThat(provider.getToken(DEFAULT_DISPLAY)).isNull()
        assertThat(provider.getToken(SECONDARY_DISPLAY)).isEqualTo(token2)
    }
}
