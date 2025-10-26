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

package com.android.launcher3.allapps

import android.content.Context
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.SetFlagsRule
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.launcher3.Flags
import com.android.launcher3.util.ActivityContextWrapper
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FloatingHeaderViewTest {

    @get:Rule val mSetFlagsRule = SetFlagsRule()

    private lateinit var context: Context
    private lateinit var vut: FloatingHeaderView

    @Before
    fun setUp() {
        context = ActivityContextWrapper(getApplicationContext())
        // TODO(b/352161553): Inflate FloatingHeaderView or R.layout.all_apps_content with proper
        // FloatingHeaderView#setup
        vut = FloatingHeaderView(context)
        vut.onFinishInflate()
    }

    @Test
    @DisableFlags(Flags.FLAG_FLOATING_SEARCH_BAR, Flags.FLAG_MULTILINE_SEARCH_BAR)
    fun onHeightUpdated_whenNotMultiline_thenZeroHeight() {
        vut.setFloatingRowsCollapsed(true)
        val beforeHeight = vut.maxTranslation
        vut.updateSearchBarOffset(HEADER_HEIGHT_OFFSET)

        vut.onHeightUpdated()

        assertThat(vut.maxTranslation).isEqualTo(beforeHeight)
    }

    @Test
    @EnableFlags(Flags.FLAG_MULTILINE_SEARCH_BAR)
    @DisableFlags(Flags.FLAG_FLOATING_SEARCH_BAR)
    fun onHeightUpdated_whenMultiline_thenHeightIsOffset() {
        vut.setFloatingRowsCollapsed(true)
        vut.updateSearchBarOffset(HEADER_HEIGHT_OFFSET)

        vut.onHeightUpdated()

        assertThat(vut.maxTranslation).isEqualTo(HEADER_HEIGHT_OFFSET)
    }

    @Test
    @DisableFlags(Flags.FLAG_MULTILINE_SEARCH_BAR)
    @EnableFlags(Flags.FLAG_FLOATING_SEARCH_BAR)
    fun onHeightUpdated_whenFloatingRowsShownAndNotMultiline_thenAddsOnlyFloatingRow() {
        // Collapse floating rows and expand to trigger header height calculation
        vut.setFloatingRowsCollapsed(true)
        vut.setFloatingRowsCollapsed(false)
        val defaultHeight = vut.maxTranslation
        vut.updateSearchBarOffset(HEADER_HEIGHT_OFFSET)

        vut.onHeightUpdated()

        assertThat(vut.maxTranslation).isEqualTo(defaultHeight)
    }

    companion object {
        private const val HEADER_HEIGHT_OFFSET = 50
    }
}
