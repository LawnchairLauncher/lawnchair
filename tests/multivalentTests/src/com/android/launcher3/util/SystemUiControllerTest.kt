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

package com.android.launcher3.util

import android.view.View
import android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
import android.view.Window
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.launcher3.util.SystemUiController.FLAG_DARK_NAV
import com.android.launcher3.util.SystemUiController.FLAG_DARK_STATUS
import com.android.launcher3.util.SystemUiController.FLAG_LIGHT_NAV
import com.android.launcher3.util.SystemUiController.FLAG_LIGHT_STATUS
import com.android.launcher3.util.SystemUiController.UI_STATE_BASE_WINDOW
import com.android.launcher3.util.SystemUiController.UI_STATE_SCRIM_VIEW
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations

@SmallTest
@RunWith(AndroidJUnit4::class)
class SystemUiControllerTest {

    @Mock private lateinit var window: Window
    @Mock private lateinit var decorView: View

    private lateinit var underTest: SystemUiController

    @Before
    fun setup() {
        MockitoAnnotations.initMocks(this)
        `when`(window.decorView).thenReturn(decorView)
        underTest = SystemUiController(window.decorView)
    }

    @Test
    fun test_default_state() {
        assertThat(underTest.toString()).isEqualTo("mStates=[0, 0, 0, 0, 0]")
    }

    @Test
    fun update_state_base_window_light() {
        underTest.updateUiState(UI_STATE_BASE_WINDOW, /* isLight= */ true)

        val visibility =
            View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        verify(decorView).systemUiVisibility = eq(visibility)
        assertThat(underTest.baseSysuiVisibility).isEqualTo(visibility)
        val flag = FLAG_LIGHT_NAV or FLAG_LIGHT_STATUS
        assertThat(underTest.toString()).isEqualTo("mStates=[$flag, 0, 0, 0, 0]")
    }

    @Test
    fun update_state_scrim_view_light() {
        underTest.updateUiState(UI_STATE_SCRIM_VIEW, /* isLight= */ true)

        verify(decorView).systemUiVisibility =
            eq(View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR)
        assertThat(underTest.baseSysuiVisibility).isEqualTo(0)
        val flag = FLAG_LIGHT_NAV or FLAG_LIGHT_STATUS
        assertThat(underTest.toString()).isEqualTo("mStates=[0, $flag, 0, 0, 0]")
    }

    @Test
    fun update_state_base_window_dark() {
        underTest.updateUiState(UI_STATE_BASE_WINDOW, /* isLight= */ false)

        verify(decorView, never()).systemUiVisibility = anyInt()
        assertThat(underTest.baseSysuiVisibility).isEqualTo(0)
        val flag = FLAG_DARK_NAV or FLAG_DARK_STATUS
        assertThat(underTest.toString()).isEqualTo("mStates=[$flag, 0, 0, 0, 0]")
    }

    @Test
    fun update_state_scrim_view_dark() {
        underTest.updateUiState(UI_STATE_SCRIM_VIEW, /* isLight= */ false)

        verify(decorView, never()).systemUiVisibility = anyInt()
        assertThat(underTest.baseSysuiVisibility).isEqualTo(0)
        val flag = FLAG_DARK_NAV or FLAG_DARK_STATUS
        assertThat(underTest.toString()).isEqualTo("mStates=[0, $flag, 0, 0, 0]")
    }

    @Test
    fun get_base_sysui_visibility() {
        `when`(decorView.systemUiVisibility).thenReturn(SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)

        assertThat(underTest.baseSysuiVisibility).isEqualTo(SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)
    }

    @Test
    fun update_state_base_window_light_with_existing_visibility() {
        `when`(decorView.systemUiVisibility).thenReturn(SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)

        underTest.updateUiState(UI_STATE_BASE_WINDOW, /* isLight= */ true)

        val visibility =
            View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR or
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or
                SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        assertThat(underTest.baseSysuiVisibility).isEqualTo(visibility)
        verify(decorView).systemUiVisibility = eq(visibility)
    }
}
