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

package com.android.launcher3.widget

import android.content.Context
import android.content.res.Resources
import android.graphics.Rect
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.SetFlagsRule
import android.view.View
import android.view.ViewGroup
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.launcher3.Flags
import com.android.launcher3.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever

@SmallTest
@RunWith(AndroidJUnit4::class)
class RoundedCornerEnforcementTest {

    @get:Rule val setFlagsRule: SetFlagsRule = SetFlagsRule()

    @Test
    fun `Widget view has one background`() {
        val mockWidgetView = mock(LauncherAppWidgetHostView::class.java)

        doReturn(android.R.id.background).whenever(mockWidgetView).id

        assertSame(RoundedCornerEnforcement.findBackground(mockWidgetView), mockWidgetView)
    }

    @Test
    fun `Widget opted out of rounded corner enforcement`() {
        val mockView = mock(View::class.java)

        doReturn(android.R.id.background).whenever(mockView).id
        doReturn(true).whenever(mockView).clipToOutline

        assertTrue(RoundedCornerEnforcement.hasAppWidgetOptedOut(mockView))
    }

    @Test
    fun `Compute rect based on widget view with background`() {
        val mockBackgroundView = mock(View::class.java)
        val mockWidgetView = mock(ViewGroup::class.java)
        val testRect = Rect(0, 0, 0, 0)

        doReturn(WIDTH).whenever(mockBackgroundView).width
        doReturn(HEIGHT).whenever(mockBackgroundView).height
        doReturn(LEFT).whenever(mockBackgroundView).left
        doReturn(TOP).whenever(mockBackgroundView).top
        doReturn(mockWidgetView).whenever(mockBackgroundView).parent

        RoundedCornerEnforcement.computeRoundedRectangle(
            mockWidgetView,
            mockBackgroundView,
            testRect,
        )

        assertEquals(Rect(50, 75, 250, 275), testRect)
    }

    @Test
    @DisableFlags(Flags.FLAG_USE_SYSTEM_RADIUS_FOR_APP_WIDGETS)
    fun `Compute system radius when smaller`() {
        val mockContext = mock(Context::class.java)
        val mockRes = mock(Resources::class.java)

        doReturn(mockRes).whenever(mockContext).resources
        doReturn(RADIUS)
            .whenever(mockRes)
            .getDimension(eq(android.R.dimen.system_app_widget_background_radius))
        doReturn(LAUNCHER_RADIUS)
            .whenever(mockRes)
            .getDimension(eq(R.dimen.enforced_rounded_corner_max_radius))

        assertEquals(RADIUS, RoundedCornerEnforcement.computeEnforcedRadius(mockContext))
    }

    @Test
    @DisableFlags(Flags.FLAG_USE_SYSTEM_RADIUS_FOR_APP_WIDGETS)
    fun `Compute launcher radius when smaller`() {
        val mockContext = mock(Context::class.java)
        val mockRes = mock(Resources::class.java)

        doReturn(mockRes).whenever(mockContext).resources
        doReturn(LAUNCHER_RADIUS + 8f)
            .whenever(mockRes)
            .getDimension(eq(android.R.dimen.system_app_widget_background_radius))
        doReturn(LAUNCHER_RADIUS)
            .whenever(mockRes)
            .getDimension(eq(R.dimen.enforced_rounded_corner_max_radius))

        assertEquals(LAUNCHER_RADIUS, RoundedCornerEnforcement.computeEnforcedRadius(mockContext))
    }

    @Test
    @EnableFlags(Flags.FLAG_USE_SYSTEM_RADIUS_FOR_APP_WIDGETS)
    fun `Compute system radius ignoring launcher radius`() {
        val mockContext = mock(Context::class.java)
        val mockRes = mock(Resources::class.java)

        doReturn(mockRes).whenever(mockContext).resources
        val systemRadius = LAUNCHER_RADIUS + 8f
        doReturn(systemRadius)
            .whenever(mockRes)
            .getDimension(eq(android.R.dimen.system_app_widget_background_radius))
        doReturn(LAUNCHER_RADIUS)
            .whenever(mockRes)
            .getDimension(eq(R.dimen.enforced_rounded_corner_max_radius))

        assertEquals(systemRadius, RoundedCornerEnforcement.computeEnforcedRadius(mockContext))
    }

    companion object {
        const val WIDTH = 200
        const val HEIGHT = 200
        const val LEFT = 50
        const val TOP = 75

        const val RADIUS = 8f
        const val LAUNCHER_RADIUS = 16f
    }
}
