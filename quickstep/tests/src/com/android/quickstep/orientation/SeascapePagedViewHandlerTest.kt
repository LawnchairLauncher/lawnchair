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

package com.android.quickstep.orientation

import android.platform.test.flag.junit.SetFlagsRule
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.launcher3.Flags
import com.android.quickstep.orientation.LandscapePagedViewHandler.SplitIconPositions
import com.android.quickstep.views.IconAppChipView
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.`when`
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

@RunWith(AndroidJUnit4::class)
class SeascapePagedViewHandlerTest {

    @get:Rule val setFlagsRule = SetFlagsRule()

    private val sut = SeascapePagedViewHandler()

    private fun enableGridOnlyOverview(isEnabled: Boolean) {
        if (isEnabled) {
            setFlagsRule.enableFlags(
                Flags.FLAG_ENABLE_GRID_ONLY_OVERVIEW,
                Flags.FLAG_ENABLE_OVERVIEW_ICON_MENU
            )
        } else {
            setFlagsRule.disableFlags(
                Flags.FLAG_ENABLE_GRID_ONLY_OVERVIEW,
                Flags.FLAG_ENABLE_OVERVIEW_ICON_MENU
            )
        }
    }

    /** [ Test getSplitIconsPosition ] */
    private fun getSplitIconsPosition(isRTL: Boolean): SplitIconPositions {
        return sut.getSplitIconsPosition(
            TASK_ICON_HEIGHT_PX,
            PRIMARY_SNAPSHOT,
            TOTAL_THUMBNAIL_HEIGHT,
            isRTL,
            OVERVIEW_TASK_MARGIN_PX,
            DIVIDER_SIZE_PX,
        )
    }

    @Test
    fun testIcon_getSplitIconsPositions() {
        enableGridOnlyOverview(false)

        val (topLeftY, bottomRightY) = getSplitIconsPosition(isRTL = false)

        // The top-left icon is translated from the bottom of the screen to the end of
        // the primary snapshot minus the icon size.
        assertThat(topLeftY).isEqualTo(142)
        // The bottom-right icon is placed at the end of the primary snapshot plus the divider.
        assertThat(bottomRightY).isEqualTo(266)
    }

    @Test
    fun testIcon_getSplitIconsPositions_isRTL() {
        enableGridOnlyOverview(false)

        val (topLeftY, bottomRightY) = getSplitIconsPosition(isRTL = true)

        // The top-left icon is translated from the bottom of the screen to the end of
        // the primary snapshot minus the icon size.
        assertThat(topLeftY).isEqualTo(142)
        // The bottom-right icon is placed at the end of the primary snapshot plus the divider.
        assertThat(bottomRightY).isEqualTo(266)
    }

    @Test
    fun testChip_getSplitIconsPositions() {
        enableGridOnlyOverview(true)

        val (topLeftY, bottomRightY) = getSplitIconsPosition(isRTL = false)

        // Top-Left app chip should always be at the initial position of the first snapshot
        assertThat(topLeftY).isEqualTo(0)
        // Bottom-Right app chip should be at the end of the primary height + divider
        assertThat(bottomRightY).isEqualTo(-266)
    }

    @Test
    fun testChip_getSplitIconsPositions_isRTL() {
        enableGridOnlyOverview(true)

        val (topLeftY, bottomRightY) = getSplitIconsPosition(isRTL = true)

        // TODO(b/326377497): When started in fake seascape and rotated to landscape,
        //  the icon chips are in RTL and wrongly positioned at the right side of the snapshot.
        //  Top-Left app chip should be placed at the top left of the first snapshot, but because
        //  this issue, it's displayed at the top-right of the second snapshot.
        //  The Bottom-Right app chip is displayed at the top-right of the first snapshot because
        //  of this issue.
        assertThat(topLeftY).isEqualTo(316)
        assertThat(bottomRightY).isEqualTo(0)
    }

    /** Test updateSplitIconsPosition */
    @Test
    fun testIcon_updateSplitIconsPosition() {
        enableGridOnlyOverview(false)

        val expectedTranslationY = 250
        val expectedGravity = Gravity.BOTTOM or Gravity.LEFT

        val iconView = mock<View>()
        val frameLayout = FrameLayout.LayoutParams(100, 100)
        `when`(iconView.layoutParams).thenReturn(frameLayout)

        sut.updateSplitIconsPosition(iconView, expectedTranslationY, false)
        assertThat(frameLayout.gravity).isEqualTo(expectedGravity)
        assertThat(frameLayout.bottomMargin).isEqualTo(expectedTranslationY)
        verify(iconView).translationX = 0f
        verify(iconView).translationY = 0f
    }

    @Test
    fun testIcon_updateSplitIconsPosition_isRTL() {
        enableGridOnlyOverview(false)

        val expectedTranslationY = 250
        val expectedGravity = Gravity.BOTTOM or Gravity.LEFT

        val iconView = mock<View>()
        val frameLayout = FrameLayout.LayoutParams(100, 100)
        `when`(iconView.layoutParams).thenReturn(frameLayout)

        sut.updateSplitIconsPosition(iconView, expectedTranslationY, true)
        assertThat(frameLayout.gravity).isEqualTo(expectedGravity)
        assertThat(frameLayout.bottomMargin).isEqualTo(expectedTranslationY)
        verify(iconView).translationX = 0f
        verify(iconView).translationY = 0f
    }

    @Test
    fun testChip_updateSplitIconsPosition() {
        enableGridOnlyOverview(true)

        val expectedTranslationY = 250
        val frameLayout = FrameLayout.LayoutParams(100, 100)
        val iconView = mock<IconAppChipView>()
        `when`(iconView.layoutParams).thenReturn(frameLayout)

        sut.updateSplitIconsPosition(iconView, expectedTranslationY, false)
        assertThat(frameLayout.gravity).isEqualTo(Gravity.BOTTOM or Gravity.START)
        verify(iconView).setSplitTranslationX(0f)
        verify(iconView).setSplitTranslationY(expectedTranslationY.toFloat())
    }

    @Test
    fun testChip_updateSplitIconsPosition_isRTL() {
        enableGridOnlyOverview(true)

        val expectedTranslationY = 250
        val frameLayout = FrameLayout.LayoutParams(100, 100)
        val iconView = mock<IconAppChipView>()
        `when`(iconView.layoutParams).thenReturn(frameLayout)

        sut.updateSplitIconsPosition(iconView, expectedTranslationY, true)
        assertThat(frameLayout.gravity).isEqualTo(Gravity.TOP or Gravity.END)
        verify(iconView).setSplitTranslationX(0f)
        verify(iconView).setSplitTranslationY(expectedTranslationY.toFloat())
    }

    private companion object {
        const val TASK_ICON_HEIGHT_PX = 108
        const val OVERVIEW_TASK_MARGIN_PX = 0
        const val DIVIDER_SIZE_PX = 16
        const val PRIMARY_SNAPSHOT = 250
        const val SECONDARY_SNAPSHOT = 300
        const val TOTAL_THUMBNAIL_HEIGHT = PRIMARY_SNAPSHOT + SECONDARY_SNAPSHOT + DIVIDER_SIZE_PX
    }
}
