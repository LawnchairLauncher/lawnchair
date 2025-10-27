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

package com.android.quickstep.recents.domain.usecase

import android.view.WindowInsetsController.APPEARANCE_LIGHT_CAPTION_BARS
import android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
import android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
import com.android.launcher3.util.SystemUiController.FLAG_DARK_NAV
import com.android.launcher3.util.SystemUiController.FLAG_DARK_STATUS
import com.android.launcher3.util.SystemUiController.FLAG_LIGHT_NAV
import com.android.launcher3.util.SystemUiController.FLAG_LIGHT_STATUS
import com.android.systemui.shared.recents.model.ThumbnailData
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class GetSysUiStatusNavFlagsUseCaseTest {
    private val sut: GetSysUiStatusNavFlagsUseCase = GetSysUiStatusNavFlagsUseCase()

    @Test
    fun onLightStatusBarAppearance_returns_LightTheme() {
        val thumbnailData = ThumbnailData(appearance = APPEARANCE_LIGHT_STATUS_BARS)
        val flag = sut.invoke(thumbnailData) // 6
        flag.assertContainsFlag(FLAG_LIGHT_STATUS)
        flag.assertContainsFlag(FLAG_DARK_NAV)
        flag.assertDoesNotContainsFlag(FLAG_DARK_STATUS)
        flag.assertDoesNotContainsFlag(FLAG_LIGHT_NAV)
    }

    @Test
    fun onLightNavBarsAppearance_returns_LightTheme() {
        val thumbnailData = ThumbnailData(appearance = APPEARANCE_LIGHT_NAVIGATION_BARS)
        val flag = sut.invoke(thumbnailData)
        flag.assertContainsFlag(FLAG_DARK_STATUS)
        flag.assertContainsFlag(FLAG_LIGHT_NAV)
        flag.assertDoesNotContainsFlag(FLAG_LIGHT_STATUS)
        flag.assertDoesNotContainsFlag(FLAG_DARK_NAV)
    }

    @Test
    fun onLightStatusBarAndNavBarAppearance_returns_LightTheme() {
        val thumbnailData =
            ThumbnailData(
                appearance = APPEARANCE_LIGHT_STATUS_BARS or APPEARANCE_LIGHT_NAVIGATION_BARS
            )
        val flag = sut.invoke(thumbnailData)
        flag.assertContainsFlag(FLAG_LIGHT_NAV)
        flag.assertContainsFlag(FLAG_LIGHT_STATUS)
        flag.assertDoesNotContainsFlag(FLAG_DARK_STATUS)
        flag.assertDoesNotContainsFlag(FLAG_DARK_NAV)
    }

    @Test
    fun onLightAppearance_returns_LightTheme() {
        val thumbnailData =
            ThumbnailData(
                appearance =
                    APPEARANCE_LIGHT_CAPTION_BARS or
                        APPEARANCE_LIGHT_STATUS_BARS or
                        APPEARANCE_LIGHT_NAVIGATION_BARS
            )
        val flag = sut.invoke(thumbnailData)
        flag.assertContainsFlag(FLAG_LIGHT_NAV)
        flag.assertContainsFlag(FLAG_LIGHT_STATUS)
        flag.assertDoesNotContainsFlag(FLAG_DARK_STATUS)
        flag.assertDoesNotContainsFlag(FLAG_DARK_NAV)
    }

    @Test
    fun onDarkAppearance_returns_DarkTheme() {
        val thumbnailData = ThumbnailData(appearance = 0)
        val flag = sut.invoke(thumbnailData)
        flag.assertContainsFlag(FLAG_DARK_STATUS)
        flag.assertContainsFlag(FLAG_DARK_NAV)
        flag.assertDoesNotContainsFlag(FLAG_LIGHT_NAV)
        flag.assertDoesNotContainsFlag(FLAG_LIGHT_STATUS)
    }

    @Test
    fun onUnrelatedDarkAppearance_returns_DarkTheme() {
        val thumbnailData = ThumbnailData(appearance = 1)
        val flag = sut.invoke(thumbnailData)
        flag.assertContainsFlag(FLAG_DARK_STATUS)
        flag.assertContainsFlag(FLAG_DARK_NAV)
        flag.assertDoesNotContainsFlag(FLAG_LIGHT_NAV)
        flag.assertDoesNotContainsFlag(FLAG_LIGHT_STATUS)
    }

    @Test
    fun whenThumbnailIsNull_returns_default() {
        val flag = sut.invoke(null)
        flag.assertDoesNotContainsFlag(FLAG_DARK_STATUS)
        flag.assertDoesNotContainsFlag(FLAG_LIGHT_NAV)
        flag.assertDoesNotContainsFlag(FLAG_LIGHT_STATUS)
        flag.assertDoesNotContainsFlag(FLAG_DARK_NAV)
    }

    private fun Int.assertContainsFlag(flag: Int) {
        assertThat(this and flag).isNotEqualTo(0)
    }

    private fun Int.assertDoesNotContainsFlag(flag: Int) {
        assertThat(this and flag).isEqualTo(0)
    }
}
