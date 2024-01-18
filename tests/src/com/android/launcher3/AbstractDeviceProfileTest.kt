/*
 * Copyright (C) 2023 The Android Open Source Project
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
import android.content.res.Configuration
import android.graphics.Point
import android.graphics.Rect
import android.util.DisplayMetrics
import android.view.Surface
import androidx.test.core.app.ApplicationProvider
import com.android.launcher3.testing.shared.ResourceUtils
import com.android.launcher3.util.DisplayController
import com.android.launcher3.util.MainThreadInitializedObject.SandboxContext
import com.android.launcher3.util.NavigationMode
import com.android.launcher3.util.WindowBounds
import com.android.launcher3.util.rule.TestStabilityRule
import com.android.launcher3.util.window.CachedDisplayInfo
import com.android.launcher3.util.window.WindowManagerProxy
import java.io.BufferedReader
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import kotlin.math.max
import kotlin.math.min
import org.junit.Rule
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.spy
import org.mockito.kotlin.whenever

/**
 * This is an abstract class for DeviceProfile tests that create an InvariantDeviceProfile based on
 * a real device spec.
 *
 * For an implementation that mocks InvariantDeviceProfile, use [FakeInvariantDeviceProfileTest]
 */
abstract class AbstractDeviceProfileTest {
    protected lateinit var context: SandboxContext
    protected open val runningContext: Context = ApplicationProvider.getApplicationContext()
    private val displayController: DisplayController = mock()
    private val windowManagerProxy: WindowManagerProxy = mock()
    private val launcherPrefs: LauncherPrefs = mock()

    @Rule @JvmField val testStabilityRule = TestStabilityRule()

    class DeviceSpec(
        val naturalSize: Pair<Int, Int>,
        var densityDpi: Int,
        val statusBarNaturalPx: Int,
        val statusBarRotatedPx: Int,
        val gesturePx: Int,
        val cutoutPx: Int
    )

    open val deviceSpecs =
        mapOf(
            "phone" to
                DeviceSpec(
                    Pair(1080, 2400),
                    densityDpi = 420,
                    statusBarNaturalPx = 118,
                    statusBarRotatedPx = 74,
                    gesturePx = 63,
                    cutoutPx = 118
                ),
            "tablet" to
                DeviceSpec(
                    Pair(2560, 1600),
                    densityDpi = 320,
                    statusBarNaturalPx = 104,
                    statusBarRotatedPx = 104,
                    gesturePx = 0,
                    cutoutPx = 0
                ),
            "twopanel-phone" to
                DeviceSpec(
                    Pair(1080, 2092),
                    densityDpi = 420,
                    statusBarNaturalPx = 133,
                    statusBarRotatedPx = 110,
                    gesturePx = 63,
                    cutoutPx = 133
                ),
            "twopanel-tablet" to
                DeviceSpec(
                    Pair(2208, 1840),
                    densityDpi = 420,
                    statusBarNaturalPx = 110,
                    statusBarRotatedPx = 133,
                    gesturePx = 0,
                    cutoutPx = 0
                )
        )

    protected fun initializeVarsForPhone(
        deviceSpec: DeviceSpec,
        isGestureMode: Boolean = true,
        isVerticalBar: Boolean = false
    ) {
        val (naturalX, naturalY) = deviceSpec.naturalSize
        val windowsBounds = phoneWindowsBounds(deviceSpec, isGestureMode, naturalX, naturalY)
        val displayInfo =
            CachedDisplayInfo(Point(naturalX, naturalY), Surface.ROTATION_0, Rect(0, 0, 0, 0))
        val perDisplayBoundsCache = mapOf(displayInfo to windowsBounds)

        initializeCommonVars(
            perDisplayBoundsCache,
            displayInfo,
            rotation = if (isVerticalBar) Surface.ROTATION_90 else Surface.ROTATION_0,
            isGestureMode,
            densityDpi = deviceSpec.densityDpi
        )
    }

    protected fun initializeVarsForTablet(
        deviceSpec: DeviceSpec,
        isLandscape: Boolean = false,
        isGestureMode: Boolean = true
    ) {
        val (naturalX, naturalY) = deviceSpec.naturalSize
        val windowsBounds = tabletWindowsBounds(deviceSpec, naturalX, naturalY)
        val displayInfo =
            CachedDisplayInfo(Point(naturalX, naturalY), Surface.ROTATION_0, Rect(0, 0, 0, 0))
        val perDisplayBoundsCache = mapOf(displayInfo to windowsBounds)

        initializeCommonVars(
            perDisplayBoundsCache,
            displayInfo,
            rotation = if (isLandscape) Surface.ROTATION_0 else Surface.ROTATION_90,
            isGestureMode,
            densityDpi = deviceSpec.densityDpi
        )
    }

    protected fun initializeVarsForTwoPanel(
        deviceSpecUnfolded: DeviceSpec,
        deviceSpecFolded: DeviceSpec,
        isLandscape: Boolean = false,
        isGestureMode: Boolean = true,
        isFolded: Boolean = false
    ) {
        val (unfoldedNaturalX, unfoldedNaturalY) = deviceSpecUnfolded.naturalSize
        val unfoldedWindowsBounds =
            tabletWindowsBounds(deviceSpecUnfolded, unfoldedNaturalX, unfoldedNaturalY)
        val unfoldedDisplayInfo =
            CachedDisplayInfo(
                Point(unfoldedNaturalX, unfoldedNaturalY),
                Surface.ROTATION_0,
                Rect(0, 0, 0, 0)
            )

        val (foldedNaturalX, foldedNaturalY) = deviceSpecFolded.naturalSize
        val foldedWindowsBounds =
            phoneWindowsBounds(deviceSpecFolded, isGestureMode, foldedNaturalX, foldedNaturalY)
        val foldedDisplayInfo =
            CachedDisplayInfo(
                Point(foldedNaturalX, foldedNaturalY),
                Surface.ROTATION_0,
                Rect(0, 0, 0, 0)
            )

        val perDisplayBoundsCache =
            mapOf(
                unfoldedDisplayInfo to unfoldedWindowsBounds,
                foldedDisplayInfo to foldedWindowsBounds
            )

        if (isFolded) {
            initializeCommonVars(
                perDisplayBoundsCache = perDisplayBoundsCache,
                displayInfo = foldedDisplayInfo,
                rotation = if (isLandscape) Surface.ROTATION_90 else Surface.ROTATION_0,
                isGestureMode = isGestureMode,
                densityDpi = deviceSpecFolded.densityDpi
            )
        } else {
            initializeCommonVars(
                perDisplayBoundsCache = perDisplayBoundsCache,
                displayInfo = unfoldedDisplayInfo,
                rotation = if (isLandscape) Surface.ROTATION_0 else Surface.ROTATION_90,
                isGestureMode = isGestureMode,
                densityDpi = deviceSpecUnfolded.densityDpi
            )
        }
    }

    private fun phoneWindowsBounds(
        deviceSpec: DeviceSpec,
        isGestureMode: Boolean,
        naturalX: Int,
        naturalY: Int
    ): List<WindowBounds> {
        val buttonsNavHeight = Utilities.dpToPx(48f, deviceSpec.densityDpi)

        val rotation0Insets =
            Rect(
                0,
                max(deviceSpec.statusBarNaturalPx, deviceSpec.cutoutPx),
                0,
                if (isGestureMode) deviceSpec.gesturePx else buttonsNavHeight
            )
        val rotation90Insets =
            Rect(
                deviceSpec.cutoutPx,
                deviceSpec.statusBarRotatedPx,
                if (isGestureMode) 0 else buttonsNavHeight,
                if (isGestureMode) deviceSpec.gesturePx else 0
            )
        val rotation180Insets =
            Rect(
                0,
                deviceSpec.statusBarNaturalPx,
                0,
                max(
                    if (isGestureMode) deviceSpec.gesturePx else buttonsNavHeight,
                    deviceSpec.cutoutPx
                )
            )
        val rotation270Insets =
            Rect(
                if (isGestureMode) 0 else buttonsNavHeight,
                deviceSpec.statusBarRotatedPx,
                deviceSpec.cutoutPx,
                if (isGestureMode) deviceSpec.gesturePx else 0
            )

        return listOf(
            WindowBounds(Rect(0, 0, naturalX, naturalY), rotation0Insets, Surface.ROTATION_0),
            WindowBounds(Rect(0, 0, naturalY, naturalX), rotation90Insets, Surface.ROTATION_90),
            WindowBounds(Rect(0, 0, naturalX, naturalY), rotation180Insets, Surface.ROTATION_180),
            WindowBounds(Rect(0, 0, naturalY, naturalX), rotation270Insets, Surface.ROTATION_270)
        )
    }

    private fun tabletWindowsBounds(
        deviceSpec: DeviceSpec,
        naturalX: Int,
        naturalY: Int
    ): List<WindowBounds> {
        val naturalInsets = Rect(0, deviceSpec.statusBarNaturalPx, 0, 0)
        val rotatedInsets = Rect(0, deviceSpec.statusBarRotatedPx, 0, 0)

        return listOf(
            WindowBounds(Rect(0, 0, naturalX, naturalY), naturalInsets, Surface.ROTATION_0),
            WindowBounds(Rect(0, 0, naturalY, naturalX), rotatedInsets, Surface.ROTATION_90),
            WindowBounds(Rect(0, 0, naturalX, naturalY), naturalInsets, Surface.ROTATION_180),
            WindowBounds(Rect(0, 0, naturalY, naturalX), rotatedInsets, Surface.ROTATION_270)
        )
    }

    private fun initializeCommonVars(
        perDisplayBoundsCache: Map<CachedDisplayInfo, List<WindowBounds>>,
        displayInfo: CachedDisplayInfo,
        rotation: Int,
        isGestureMode: Boolean = true,
        densityDpi: Int
    ) {
        val windowsBounds = perDisplayBoundsCache[displayInfo]!!
        val realBounds = windowsBounds[rotation]
        whenever(windowManagerProxy.getDisplayInfo(any())).thenReturn(displayInfo)
        whenever(windowManagerProxy.getRealBounds(any(), any())).thenReturn(realBounds)
        whenever(windowManagerProxy.getCurrentBounds(any())).thenReturn(realBounds.bounds)
        whenever(windowManagerProxy.getRotation(any())).thenReturn(rotation)
        whenever(windowManagerProxy.getNavigationMode(any()))
            .thenReturn(
                if (isGestureMode) NavigationMode.NO_BUTTON else NavigationMode.THREE_BUTTONS
            )

        val density = densityDpi / DisplayMetrics.DENSITY_DEFAULT.toFloat()
        val config =
            Configuration(runningContext.resources.configuration).apply {
                this.densityDpi = densityDpi
                screenWidthDp = (realBounds.bounds.width() / density).toInt()
                screenHeightDp = (realBounds.bounds.height() / density).toInt()
                smallestScreenWidthDp = min(screenWidthDp, screenHeightDp)
            }
        val configurationContext = runningContext.createConfigurationContext(config)
        context =
            SandboxContext(
                configurationContext,
                DisplayController.INSTANCE,
                WindowManagerProxy.INSTANCE,
                LauncherPrefs.INSTANCE
            )
        context.putObject(DisplayController.INSTANCE, displayController)
        context.putObject(WindowManagerProxy.INSTANCE, windowManagerProxy)
        context.putObject(LauncherPrefs.INSTANCE, launcherPrefs)

        whenever(launcherPrefs.get(LauncherPrefs.TASKBAR_PINNING)).thenReturn(false)
        val info = spy(DisplayController.Info(context, windowManagerProxy, perDisplayBoundsCache))
        whenever(displayController.info).thenReturn(info)
        whenever(info.isTransientTaskbar).thenReturn(isGestureMode)
    }

    /** Create a new dump of DeviceProfile, saves to a file in the device and returns it */
    protected fun dump(context: Context, dp: DeviceProfile, fileName: String): String {
        val stringWriter = StringWriter()
        PrintWriter(stringWriter).use { dp.dump(context, "", it) }
        return stringWriter.toString().also { content -> writeToDevice(context, fileName, content) }
    }

    /** Read a file from assets/ and return it as a string */
    protected fun readDumpFromAssets(context: Context, fileName: String): String =
        context.assets.open("dumpTests/$fileName").bufferedReader().use(BufferedReader::readText)

    private fun writeToDevice(context: Context, fileName: String, content: String) {
        File(context.getDir("dumpTests", Context.MODE_PRIVATE), fileName).writeText(content)
    }

    protected fun Float.dpToPx(): Float {
        return ResourceUtils.pxFromDp(this, context!!.resources.displayMetrics).toFloat()
    }

    protected fun Int.dpToPx(): Int {
        return ResourceUtils.pxFromDp(this.toFloat(), context!!.resources.displayMetrics)
    }
}
