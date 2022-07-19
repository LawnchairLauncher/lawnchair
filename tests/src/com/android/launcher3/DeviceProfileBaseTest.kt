/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.launcher3

import android.content.Context
import android.graphics.PointF
import androidx.test.core.app.ApplicationProvider
import com.android.launcher3.util.DisplayController.Info
import com.android.launcher3.util.WindowBounds
import org.junit.Before
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.mock
import java.io.PrintWriter
import java.io.StringWriter
import org.mockito.Mockito.`when` as whenever

abstract class DeviceProfileBaseTest {

    protected var context: Context? = null
    protected var inv: InvariantDeviceProfile? = null
    protected var info: Info = mock(Info::class.java)
    protected var windowBounds: WindowBounds? = null
    protected var isMultiWindowMode: Boolean = false
    protected var transposeLayoutWithOrientation: Boolean = false
    protected var useTwoPanels: Boolean = false
    protected var isGestureMode: Boolean = true
    protected var isRecentsRtl: Boolean = true

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        // make sure to reset values
        useTwoPanels = false
        isGestureMode = true
    }

    protected fun newDP(): DeviceProfile = DeviceProfile(
        context,
        inv,
        info,
        windowBounds,
        isMultiWindowMode,
        transposeLayoutWithOrientation,
        useTwoPanels,
        isGestureMode
    )

    protected fun initializeVarsForPhone(isGestureMode: Boolean = true,
                                         isVerticalBar: Boolean = false) {
        val (x, y) = if (isVerticalBar)
            Pair(3120, 1440)
        else
            Pair(1440, 3120)

        windowBounds = WindowBounds(x, y, x, y - 100, 0)

        whenever(info.isTablet(any())).thenReturn(false)
        whenever(info.getDensityDpi()).thenReturn(560)
        whenever(info.smallestSizeDp(any())).thenReturn(411f)

        this.isGestureMode = isGestureMode

        inv = newScalableInvariantDeviceProfile().apply {
            deviceType = InvariantDeviceProfile.TYPE_PHONE
            transposeLayoutWithOrientation = isVerticalBar
        }
    }

    protected fun initializeVarsForTablet(isLandscape: Boolean = false,
                                          isTwoPanel: Boolean = false,
                                          isGestureMode: Boolean = true) {
        val (x, y) = if (isLandscape)
            Pair(2560, 1600)
        else
            Pair(1600, 2560)

        windowBounds = WindowBounds(x, y, x, y - 100, 0)

        whenever(info.isTablet(any())).thenReturn(true)
        whenever(info.getDensityDpi()).thenReturn(320)
        whenever(info.smallestSizeDp(any())).thenReturn(800f)

        this.isGestureMode = isGestureMode
        useTwoPanels = isTwoPanel

        inv = newScalableInvariantDeviceProfile().apply {
            deviceType = if (isTwoPanel)
                InvariantDeviceProfile.TYPE_MULTI_DISPLAY else InvariantDeviceProfile.TYPE_TABLET
            inlineQsb = booleanArrayOf(
                    false,
                    isLandscape,
                    false,
                    false
            )
        }
    }

    /**
     * A very generic grid, just to make qsb tests work. For real calculations, make sure to use
     * values that better represent a real grid.
     */
    protected fun newScalableInvariantDeviceProfile(): InvariantDeviceProfile =
        InvariantDeviceProfile().apply {
            isScalable = true
            numColumns = 4
            numRows = 4
            numShownHotseatIcons = 4
            numDatabaseHotseatIcons = 6
            numShrunkenHotseatIcons = 5
            horizontalMargin = FloatArray(4) { 22f }
            borderSpaces = listOf(
                PointF(16f, 16f),
                PointF(16f, 16f),
                PointF(16f, 16f),
                PointF(16f, 16f)
            ).toTypedArray()
            allAppsBorderSpaces = listOf(
                PointF(16f, 16f),
                PointF(16f, 16f),
                PointF(16f, 16f),
                PointF(16f, 16f)
            ).toTypedArray()
            hotseatBorderSpaces = FloatArray(4) { 16f }
            hotseatColumnSpan = IntArray(4) { 4 }
            hotseatBarBottomSpace = FloatArray(4) { 48f }
            hotseatQsbSpace = FloatArray(4) { 36f }
            iconSize = FloatArray(4) { 56f }
            allAppsIconSize = FloatArray(4) { 56f }
            iconTextSize = FloatArray(4) { 14f }
            allAppsIconTextSize = FloatArray(4) { 14f }
            minCellSize = listOf(
                PointF(64f, 83f),
                PointF(64f, 83f),
                PointF(64f, 83f),
                PointF(64f, 83f)
            ).toTypedArray()
            allAppsCellSize = listOf(
                PointF(64f, 83f),
                PointF(64f, 83f),
                PointF(64f, 83f),
                PointF(64f, 83f)
            ).toTypedArray()
            inlineQsb = booleanArrayOf(
                false,
                false,
                false,
                false
            )
        }

    fun dump(dp: DeviceProfile): StringWriter {
        val stringWriter = StringWriter()
        val printWriter = PrintWriter(stringWriter)
        dp.dump(context, "", printWriter)
        printWriter.flush()
        return stringWriter
    }
}