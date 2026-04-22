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

package com.android.wm.shell.common

import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Insets
import android.graphics.Rect
import android.testing.AndroidTestingRunner
import android.view.DisplayCutout
import android.view.DisplayInfo
import android.view.InsetsSource
import android.view.InsetsSource.ID_IME
import android.view.InsetsState
import android.view.Surface
import android.view.WindowInsets.Type
import androidx.test.filters.SmallTest
import com.android.internal.R
import com.android.wm.shell.ShellTestCase
import junit.framework.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.kotlin.whenever


@SmallTest
@RunWith(AndroidTestingRunner::class)
class NavigationBarsListenerTest : ShellTestCase() {
    private lateinit var navigationBarsListener: TestingNavigationBarsListener
    private lateinit var displayLayout: DisplayLayout

    @Mock private lateinit var displayController: DisplayController
    @Before
    fun setUp() {
        val resources = createResources(40, 50, false)
        val displayInfo = createDisplayInfo(1000, 1500, 0, Surface.ROTATION_0)
        displayLayout = DisplayLayout(displayInfo, resources, false, false)
        whenever(displayController.getDisplayLayout(DEFAULT_DISPLAY_ID)).thenReturn(displayLayout)
        navigationBarsListener = TestingNavigationBarsListener(displayController, DEFAULT_DISPLAY_ID)
    }

    @Test
    fun insetsChanged_bottomNavigationBars_callsOnBottomNavigationBarsAppears() {
        val insets = Insets.of(0, 0, 0, DEFAULT_NAVIGATION_BARS_SIZE)
        val insetsState = createBottomNavigationBars(insets)
        navigationBarsListener.insetsChanged(insetsState)
        assertEquals(insets, navigationBarsListener.testInsets)
    }

    @Test
    fun insetsChanged_rightNavigationBars_callsOnRightNavigationBarsAppears() {
        val insets = Insets.of(0, 0, DEFAULT_NAVIGATION_BARS_SIZE, 0)
        val insetsStateWithNavigationBars = createRightNavigationBars(insets)
        navigationBarsListener.insetsChanged(insetsStateWithNavigationBars)
        assertEquals(insets, navigationBarsListener.testInsets)
    }

    @Test
    fun insetsChanged_zeroNavigationBars_callsOnNavigationBarsDisappears() {
        val zeroInsets = Insets.of(0, 0, 0, 0)
        val insetsStateWithoutNavigationBars = createBottomNavigationBars(zeroInsets)
        navigationBarsListener.insetsChanged(insetsStateWithoutNavigationBars)
        assertEquals(Insets.NONE, navigationBarsListener.testInsets)
    }

    @Test
    fun insetsChanged_imeAppears_doesNotCallOnNavigationBarsInsetsChanged() {
        val insets = Insets.of(0, 0, 0, DEFAULT_IME_HEIGHT)
        // Send insetsState with an IME.
        val insetsState = createIme(insets)
        navigationBarsListener.insetsChanged(insetsState)
        assertEquals(Insets.NONE, navigationBarsListener.testInsets)
    }

    private fun createBottomNavigationBars(insets: Insets): InsetsState {
        val stableBounds = Rect()
        displayLayout.getStableBounds(stableBounds)
        val displayRect = Rect()
        displayRect.set(0, 0, displayLayout.width(), displayLayout.height())

        val insetsState = InsetsState()
        val insetsSource = insetsState.getOrCreateSource(
            InsetsSource.createId(
                null, 0, Type.navigationBars()), Type.navigationBars()
        )

        val frame = Rect(0, displayLayout.height() - insets.bottom,
            displayLayout.width(), displayLayout.height())
        insetsSource.setFrame(frame)
        return insetsState
    }

    private fun createRightNavigationBars(insets: Insets): InsetsState {
        val stableBounds = Rect()
        displayLayout.getStableBounds(stableBounds)
        val displayRect = Rect()
        displayRect.set(0, 0, displayLayout.width(), displayLayout.height())

        val insetsState = InsetsState()
        val insetsSource = insetsState.getOrCreateSource(
            InsetsSource.createId(
                null, 0, Type.navigationBars()), Type.navigationBars()
        )

        val frame = Rect(displayLayout.width() - insets.right, 0,
                displayLayout.width(), displayLayout.height())
        insetsSource.setFrame(frame)
        return insetsState
    }

    private fun createIme(insets: Insets): InsetsState {
        val stableBounds = Rect()
        displayLayout.getStableBounds(stableBounds)
        val displayRect = Rect()
        displayRect.set(0, 0, displayLayout.width(), displayLayout.height())

        val insetsState = InsetsState()
        val insetsSource = insetsState.getOrCreateSource(ID_IME, Type.ime())
        insetsSource.setVisible(true)
        insetsSource.setFrame(stableBounds.left, stableBounds.bottom - insets.bottom,
            stableBounds.right, stableBounds.bottom)
        return insetsState
    }

    private fun createDisplayInfo(width: Int, height: Int, cutoutHeight: Int,
        rotation: Int): DisplayInfo {
        val info = DisplayInfo()
        info.logicalWidth = width
        info.logicalHeight = height
        info.rotation = rotation
        if (cutoutHeight > 0) {
            info.displayCutout = DisplayCutout(
                Insets.of(0, cutoutHeight, 0, 0) /* safeInsets */,
                null /* boundLeft */,
                Rect(width / 2 - cutoutHeight, 0, width / 2 + cutoutHeight,
                    cutoutHeight) /* boundTop */, null /* boundRight */,
                null /* boundBottom */)
        } else {
            info.displayCutout = DisplayCutout.NO_CUTOUT
        }
        info.logicalDensityDpi = 300
        return info
    }

    private fun createResources(navLand: Int, navPort: Int, navMoves: Boolean): Resources {
        val cfg = Configuration()
        cfg.uiMode = Configuration.UI_MODE_TYPE_NORMAL
        val res = Mockito.mock(Resources::class.java)
        Mockito.doReturn(navLand).whenever(res).getDimensionPixelSize(
            R.dimen.navigation_bar_height_landscape_car_mode)
        Mockito.doReturn(navPort).whenever(res).getDimensionPixelSize(
            R.dimen.navigation_bar_height_car_mode)
        Mockito.doReturn(navLand).whenever(res).getDimensionPixelSize(
            R.dimen.navigation_bar_width_car_mode)
        Mockito.doReturn(navLand).whenever(res).getDimensionPixelSize(
            R.dimen.navigation_bar_height_landscape)
        Mockito.doReturn(navPort).whenever(res).getDimensionPixelSize(
            R.dimen.navigation_bar_height)
        Mockito.doReturn(navLand).whenever(res).getDimensionPixelSize(
            R.dimen.navigation_bar_width)
        Mockito.doReturn(navMoves).whenever(res).getBoolean(R.bool.config_navBarCanMove)
        Mockito.doReturn(cfg).whenever(res).configuration
        return res
    }

    private class TestingNavigationBarsListener(
        displayController: DisplayController,
        displayId: Int
    ) : NavigationBarsListener(displayController, displayId) {
        var testInsets = Insets.NONE

        override fun onNavigationBarsVisibilityChanged(insets: Insets) {
            testInsets = insets
        }
    }

    companion object {
        private const val DEFAULT_DISPLAY_ID = 0
        private const val DEFAULT_NAVIGATION_BARS_SIZE = 100
        private const val DEFAULT_IME_HEIGHT = 500
    }
}