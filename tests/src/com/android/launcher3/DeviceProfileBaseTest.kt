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
import android.graphics.Rect
import android.util.SparseArray
import androidx.test.core.app.ApplicationProvider
import com.android.launcher3.DeviceProfile.DEFAULT_PROVIDER
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
        SparseArray(),
        isMultiWindowMode,
        transposeLayoutWithOrientation,
        useTwoPanels,
        isGestureMode,
        DEFAULT_PROVIDER
    )

    protected fun initializeVarsForPhone(isGestureMode: Boolean = true,
                                         isVerticalBar: Boolean = false) {
        val (x, y) = if (isVerticalBar)
            Pair(2400, 1080)
        else
            Pair(1080, 2400)

        windowBounds = WindowBounds(Rect(0, 0, x, y), Rect(
                if (isVerticalBar) 118 else 0,
                if (isVerticalBar) 74 else 118,
                if (!isGestureMode && isVerticalBar) 126 else 0,
                if (isGestureMode) 63 else if (isVerticalBar) 0 else 126))

        whenever(info.isTablet(any())).thenReturn(false)
        whenever(info.getDensityDpi()).thenReturn(420)
        whenever(info.smallestSizeDp(any())).thenReturn(411f)

        this.isGestureMode = isGestureMode
        transposeLayoutWithOrientation = true

        inv = InvariantDeviceProfile().apply {
            numRows = 5
            numColumns = 4
            numSearchContainerColumns = 4

            iconSize = floatArrayOf(60f, 54f, 60f, 60f)
            iconTextSize = FloatArray(4) { 14f }
            deviceType = InvariantDeviceProfile.TYPE_PHONE

            minCellSize = listOf(
                    PointF(80f, 104f),
                    PointF(80f, 104f),
                    PointF(80f, 104f),
                    PointF(80f, 104f)
            ).toTypedArray()

            borderSpaces = listOf(
                    PointF(16f, 16f),
                    PointF(16f, 16f),
                    PointF(16f, 16f),
                    PointF(16f, 16f)
            ).toTypedArray()

            numFolderRows = 3
            numFolderColumns = 3
            folderStyle = R.style.FolderDefaultStyle

            inlineNavButtonsEndSpacing = R.dimen.taskbar_button_margin_split

            horizontalMargin = FloatArray(4) { 22f }

            allAppsCellSize = listOf(
                    PointF(80f, 104f),
                    PointF(80f, 104f),
                    PointF(80f, 104f),
                    PointF(80f, 104f)
            ).toTypedArray()
            allAppsIconSize = floatArrayOf(60f, 60f, 60f, 60f)
            allAppsIconTextSize = FloatArray(4) { 14f }
            allAppsBorderSpaces = listOf(
                    PointF(16f, 16f),
                    PointF(16f, 16f),
                    PointF(16f, 16f),
                    PointF(16f, 16f)
            ).toTypedArray()

            numShownHotseatIcons = 4

            numDatabaseHotseatIcons = 4

            hotseatColumnSpan = IntArray(4) { 4 }
            hotseatBarBottomSpace = FloatArray(4) { 48f }
            hotseatQsbSpace = FloatArray(4) { 36f }

            numAllAppsColumns = 4

            isScalable = true

            inlineQsb = BooleanArray(4) { false }

            devicePaddingId = R.xml.paddings_handhelds
        }
    }

    protected fun initializeVarsForTablet(isLandscape: Boolean = false,
                                          isGestureMode: Boolean = true) {
        val (x, y) = if (isLandscape)
            Pair(2560, 1600)
        else
            Pair(1600, 2560)

        windowBounds =
                WindowBounds(Rect(0, 0, x, y), Rect(0, 104, 0, 0))

        whenever(info.isTablet(any())).thenReturn(true)
        whenever(info.getDensityDpi()).thenReturn(320)
        whenever(info.smallestSizeDp(any())).thenReturn(800f)

        this.isGestureMode = isGestureMode
        useTwoPanels = false

        inv = InvariantDeviceProfile().apply {
            numRows = 5
            numColumns = 6
            numSearchContainerColumns = 3

            iconSize = FloatArray(4) { 60f }
            iconTextSize = FloatArray(4) { 14f }
            deviceType = InvariantDeviceProfile.TYPE_TABLET

            minCellSize = listOf(
                    PointF(102f, 120f),
                    PointF(120f, 104f),
                    PointF(102f, 120f),
                    PointF(102f, 120f)
            ).toTypedArray()

            borderSpaces = listOf(
                    PointF(16f, 64f),
                    PointF(64f, 16f),
                    PointF(16f, 64f),
                    PointF(16f, 64f)
            ).toTypedArray()

            numFolderRows = 3
            numFolderColumns = 3
            folderStyle = R.style.FolderDefaultStyle

            inlineNavButtonsEndSpacing = R.dimen.taskbar_button_margin_6_5

            horizontalMargin = floatArrayOf(54f, 120f, 54f, 54f)

            allAppsCellSize = listOf(
                    PointF(96f, 142f),
                    PointF(126f, 126f),
                    PointF(96f, 142f),
                    PointF(96f, 142f)
            ).toTypedArray()
            allAppsIconSize = FloatArray(4) { 60f }
            allAppsIconTextSize = FloatArray(4) { 14f }
            allAppsBorderSpaces = listOf(
                    PointF(8f, 16f),
                    PointF(16f, 16f),
                    PointF(8f, 16f),
                    PointF(8f, 16f)
            ).toTypedArray()

            numShownHotseatIcons = 6

            numDatabaseHotseatIcons = 6

            hotseatColumnSpan = intArrayOf(6, 4, 6, 6)
            hotseatBarBottomSpace = floatArrayOf(36f, 40f, 36f, 36f)
            hotseatQsbSpace = FloatArray(4) { 32f }

            numAllAppsColumns = 6

            isScalable = true
            devicePaddingId = R.xml.paddings_6x5

            inlineQsb = booleanArrayOf(
                    false,
                    true,
                    false,
                    false
            )

            devicePaddingId = R.xml.paddings_handhelds
        }
    }

    protected fun initializeVarsForTwoPanel(isLandscape: Boolean = false,
            isGestureMode: Boolean = true) {
        val (x, y) = if (isLandscape)
            Pair(2208, 1840)
        else
            Pair(1840, 2208)

        windowBounds = WindowBounds(Rect(0, 0, x, y),
                Rect(0, 110, 0, 0))

        whenever(info.isTablet(any())).thenReturn(true)
        whenever(info.getDensityDpi()).thenReturn(420)
        whenever(info.smallestSizeDp(any())).thenReturn(700f)

        this.isGestureMode = isGestureMode
        useTwoPanels = true

        inv = InvariantDeviceProfile().apply {
            numRows = 4
            numColumns = 4
            numSearchContainerColumns = 4

            iconSize = floatArrayOf(60f, 52f, 52f, 60f)
            iconTextSize = floatArrayOf(14f, 14f, 12f, 14f)
            deviceType = InvariantDeviceProfile.TYPE_MULTI_DISPLAY

            minCellSize = listOf(
                    PointF(80f, 104f),
                    PointF(80f, 104f),
                    PointF(68f, 116f),
                    PointF(80f, 102f)
            ).toTypedArray()

            borderSpaces = listOf(
                    PointF(16f, 16f),
                    PointF(16f, 16f),
                    PointF(16f, 20f),
                    PointF(20f, 20f)
            ).toTypedArray()

            numFolderRows = 3
            numFolderColumns = 3
            folderStyle = R.style.FolderDefaultStyle

            inlineNavButtonsEndSpacing = R.dimen.taskbar_button_margin_split

            horizontalMargin = floatArrayOf(21.5f, 21.5f, 22.5f, 30.5f)

            allAppsCellSize = listOf(
                    PointF(0f, 0f),
                    PointF(0f, 0f),
                    PointF(68f, 104f),
                    PointF(80f, 104f)
            ).toTypedArray()
            allAppsIconSize = floatArrayOf(60f, 60f, 52f, 60f)
            allAppsIconTextSize = floatArrayOf(14f, 14f, 12f, 14f)
            allAppsBorderSpaces = listOf(
                    PointF(16f, 16f),
                    PointF(16f, 16f),
                    PointF(16f, 28f),
                    PointF(20f, 16f)
            ).toTypedArray()

            numShownHotseatIcons = 6

            numDatabaseHotseatIcons = 6

            hotseatColumnSpan = IntArray(4) { 6 }
            hotseatBarBottomSpace = floatArrayOf(48f, 48f, 36f, 20f)
            hotseatQsbSpace = floatArrayOf(36f, 36f, 36f, 28f)

            numAllAppsColumns = 6
            numDatabaseAllAppsColumns = 6

            isScalable = true

            inlineQsb = booleanArrayOf(
                    false,
                    false,
                    false,
                    false
            )

            devicePaddingId = R.xml.paddings_handhelds
        }
    }

    fun dump(dp: DeviceProfile): String {
        val stringWriter = StringWriter()
        val printWriter = PrintWriter(stringWriter)
        dp.dump(context, "", printWriter)
        printWriter.flush()
        return stringWriter.toString()
    }
}