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
package com.android.launcher3.taskbar

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Insets
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Region
import android.inputmethodservice.InputMethodService.ENABLE_HIDE_IME_CAPTION_BAR
import android.os.Binder
import android.os.IBinder
import android.view.DisplayInfo
import android.view.Gravity
import android.view.InsetsFrameProvider
import android.view.InsetsFrameProvider.SOURCE_DISPLAY
import android.view.InsetsSource.FLAG_ANIMATE_RESIZING
import android.view.InsetsSource.FLAG_INSETS_ROUNDED_CORNER
import android.view.InsetsSource.FLAG_SUPPRESS_SCRIM
import android.view.Surface
import android.view.ViewTreeObserver
import android.view.ViewTreeObserver.InternalInsetsInfo.TOUCHABLE_INSETS_FRAME
import android.view.ViewTreeObserver.InternalInsetsInfo.TOUCHABLE_INSETS_REGION
import android.view.WindowInsets
import android.view.WindowInsets.Type.mandatorySystemGestures
import android.view.WindowInsets.Type.navigationBars
import android.view.WindowInsets.Type.systemGestures
import android.view.WindowInsets.Type.tappableElement
import android.view.WindowManager
import android.view.WindowManager.LayoutParams.TYPE_INPUT_METHOD
import android.view.WindowManager.LayoutParams.TYPE_VOICE_INTERACTION
import androidx.core.graphics.toRegion
import com.android.internal.policy.GestureNavigationSettingsObserver
import com.android.launcher3.DeviceProfile
import com.android.launcher3.R
import com.android.launcher3.anim.AlphaUpdateListener
import com.android.launcher3.config.FeatureFlags.ENABLE_TASKBAR_NAVBAR_UNIFICATION
import com.android.launcher3.config.FeatureFlags.enableTaskbarNoRecreate
import com.android.launcher3.taskbar.TaskbarControllers.LoggableTaskbarController
import com.android.launcher3.util.DisplayController
import java.io.PrintWriter
import kotlin.jvm.optionals.getOrNull
import kotlin.math.max

/** Handles the insets that Taskbar provides to underlying apps and the IME. */
class TaskbarInsetsController(val context: TaskbarActivityContext) : LoggableTaskbarController {

    companion object {
        private const val INDEX_LEFT = 0
        private const val INDEX_RIGHT = 1
    }

    /** The bottom insets taskbar provides to the IME when IME is visible. */
    val taskbarHeightForIme: Int = context.resources.getDimensionPixelSize(R.dimen.taskbar_ime_size)
    // The touchableRegion we will set unless some other state takes precedence.
    private val defaultTouchableRegion: Region = Region()
    private val insetsOwner: IBinder = Binder()
    private val deviceProfileChangeListener = { _: DeviceProfile ->
        onTaskbarOrBubblebarWindowHeightOrInsetsChanged()
    }
    private val gestureNavSettingsObserver =
        GestureNavigationSettingsObserver(
            context.mainThreadHandler,
            context,
            this::onTaskbarOrBubblebarWindowHeightOrInsetsChanged
        )
    private val debugTouchableRegion = DebugTouchableRegion()

    // Initialized in init.
    private lateinit var controllers: TaskbarControllers
    private lateinit var windowLayoutParams: WindowManager.LayoutParams

    fun init(controllers: TaskbarControllers) {
        this.controllers = controllers
        windowLayoutParams = context.windowLayoutParams
        onTaskbarOrBubblebarWindowHeightOrInsetsChanged()

        context.addOnDeviceProfileChangeListener(deviceProfileChangeListener)
        gestureNavSettingsObserver.registerForCallingUser()
    }

    fun onDestroy() {
        context.removeOnDeviceProfileChangeListener(deviceProfileChangeListener)
        gestureNavSettingsObserver.unregister()
    }

    fun onTaskbarOrBubblebarWindowHeightOrInsetsChanged() {
        val tappableHeight = controllers.taskbarStashController.tappableHeightToReportToApps
        // We only report tappableElement height for unstashed, persistent taskbar,
        // which is also when we draw the rounded corners above taskbar.
        val insetsRoundedCornerFlag =
            if (tappableHeight > 0) {
                FLAG_INSETS_ROUNDED_CORNER
            } else {
                0
            }

        windowLayoutParams.providedInsets =
            if (enableTaskbarNoRecreate() && controllers.sharedState != null) {
                getProvidedInsets(
                    controllers.sharedState!!.insetsFrameProviders,
                    insetsRoundedCornerFlag
                )
            } else {
                getProvidedInsets(insetsRoundedCornerFlag)
            }

        if (windowLayoutParams.paramsForRotation != null) {
            for (layoutParams in windowLayoutParams.paramsForRotation) {
                layoutParams.providedInsets = getProvidedInsets(insetsRoundedCornerFlag)
            }
        }

        val taskbarTouchableHeight = controllers.taskbarStashController.touchableHeight
        val bubblesTouchableHeight =
            if (controllers.bubbleControllers.isPresent) {
                controllers.bubbleControllers.get().bubbleStashController.touchableHeight
            } else {
                0
            }
        val touchableHeight = max(taskbarTouchableHeight, bubblesTouchableHeight)

        if (
            controllers.bubbleControllers.isPresent &&
                controllers.bubbleControllers.get().bubbleStashController.isBubblesShowingOnHome
        ) {
            val iconBounds =
                controllers.bubbleControllers.get().bubbleBarViewController.bubbleBarBounds
            defaultTouchableRegion.set(
                iconBounds.left,
                iconBounds.top,
                iconBounds.right,
                iconBounds.bottom
            )
        } else {
            defaultTouchableRegion.set(
                0,
                windowLayoutParams.height - touchableHeight,
                context.deviceProfile.widthPx,
                windowLayoutParams.height
            )

            // if there's an animating bubble add it to the touch region so that it's clickable
            val isAnimatingNewBubble =
                controllers.bubbleControllers
                    .getOrNull()
                    ?.bubbleBarViewController
                    ?.isAnimatingNewBubble
                    ?: false
            if (isAnimatingNewBubble) {
                val iconBounds =
                    controllers.bubbleControllers.get().bubbleBarViewController.bubbleBarBounds
                defaultTouchableRegion.op(iconBounds, Region.Op.UNION)
            }
        }

        // Pre-calculate insets for different providers across different rotations for this gravity
        for (rotation in Surface.ROTATION_0..Surface.ROTATION_270) {
            // Add insets for navbar rotated params
            val layoutParams = windowLayoutParams.paramsForRotation[rotation]
            for (provider in layoutParams.providedInsets) {
                setProviderInsets(provider, layoutParams.gravity, rotation)
            }
        }
        // Also set the parent providers (i.e. not in paramsForRotation).
        for (provider in windowLayoutParams.providedInsets) {
            setProviderInsets(provider, windowLayoutParams.gravity, context.display.rotation)
        }
        context.notifyUpdateLayoutParams()
    }

    /**
     * This is for when ENABLE_TASKBAR_NO_RECREATION is enabled. We generate one instance of
     * providedInsets and use it across the entire lifecycle of TaskbarManager. The only thing we
     * need to reset is nav bar flags based on insetsRoundedCornerFlag.
     */
    private fun getProvidedInsets(
        providedInsets: Array<InsetsFrameProvider>,
        insetsRoundedCornerFlag: Int
    ): Array<InsetsFrameProvider> {
        val navBarsFlag =
            (if (context.isGestureNav) FLAG_SUPPRESS_SCRIM else 0) or insetsRoundedCornerFlag
        for (provider in providedInsets) {
            if (provider.type == navigationBars()) {
                provider.setFlags(navBarsFlag, FLAG_SUPPRESS_SCRIM or FLAG_INSETS_ROUNDED_CORNER)
            }
        }
        return providedInsets
    }

    /**
     * The inset types and number of insets provided have to match for both gesture nav and button
     * nav. The values and the order of the elements in array are allowed to differ. Reason being WM
     * does not allow types and number of insets changing for a given window once it is added into
     * the hierarchy for performance reasons.
     */
    private fun getProvidedInsets(insetsRoundedCornerFlag: Int): Array<InsetsFrameProvider> {
        val navBarsFlag =
            (if (context.isGestureNav) FLAG_SUPPRESS_SCRIM or FLAG_ANIMATE_RESIZING else 0) or
                insetsRoundedCornerFlag
        return arrayOf(
            InsetsFrameProvider(insetsOwner, 0, navigationBars())
                .setFlags(
                    navBarsFlag,
                    FLAG_SUPPRESS_SCRIM or FLAG_ANIMATE_RESIZING or FLAG_INSETS_ROUNDED_CORNER
                ),
            InsetsFrameProvider(insetsOwner, 0, tappableElement()),
            InsetsFrameProvider(insetsOwner, 0, mandatorySystemGestures()),
            InsetsFrameProvider(insetsOwner, INDEX_LEFT, systemGestures())
                .setSource(SOURCE_DISPLAY),
            InsetsFrameProvider(insetsOwner, INDEX_RIGHT, systemGestures())
                .setSource(SOURCE_DISPLAY)
        )
    }

    private fun setProviderInsets(provider: InsetsFrameProvider, gravity: Int, endRotation: Int) {
        val contentHeight = controllers.taskbarStashController.contentHeightToReportToApps
        val tappableHeight = controllers.taskbarStashController.tappableHeightToReportToApps
        val res = context.resources
        if (provider.type == navigationBars() || provider.type == mandatorySystemGestures()) {
            provider.insetsSize = getInsetsForGravityWithCutout(contentHeight, gravity, endRotation)
        } else if (provider.type == tappableElement()) {
            provider.insetsSize = getInsetsForGravity(tappableHeight, gravity)
        } else if (provider.type == systemGestures() && provider.index == INDEX_LEFT) {
            val leftIndexInset =
                if (context.isThreeButtonNav) 0
                else gestureNavSettingsObserver.getLeftSensitivityForCallingUser(res)
            provider.insetsSize = Insets.of(leftIndexInset, 0, 0, 0)
        } else if (provider.type == systemGestures() && provider.index == INDEX_RIGHT) {
            val rightIndexInset =
                if (context.isThreeButtonNav) 0
                else gestureNavSettingsObserver.getRightSensitivityForCallingUser(res)
            provider.insetsSize = Insets.of(0, 0, rightIndexInset, 0)
        }

        // When in gesture nav, report the stashed height to the IME, to allow hiding the
        // IME navigation bar.
        val imeInsetsSize =
            if (ENABLE_HIDE_IME_CAPTION_BAR && context.isGestureNav) {
                getInsetsForGravity(controllers.taskbarStashController.stashedHeight, gravity)
            } else {
                getInsetsForGravity(taskbarHeightForIme, gravity)
            }
        val imeInsetsSizeOverride =
            arrayOf(
                InsetsFrameProvider.InsetsSizeOverride(TYPE_INPUT_METHOD, imeInsetsSize),
                InsetsFrameProvider.InsetsSizeOverride(
                    TYPE_VOICE_INTERACTION,
                    // No-op override to keep the size and types in sync with the
                    // override below (insetsSizeOverrides must have the same length and
                    // types after the window is added according to
                    // WindowManagerService#relayoutWindow)
                    provider.insetsSize
                )
            )
        // Use 0 tappableElement insets for the VoiceInteractionWindow when gesture nav is enabled.
        val visInsetsSizeForTappableElement =
            if (context.isGestureNav) getInsetsForGravity(0, gravity)
            else getInsetsForGravity(tappableHeight, gravity)
        val insetsSizeOverrideForTappableElement =
            arrayOf(
                InsetsFrameProvider.InsetsSizeOverride(TYPE_INPUT_METHOD, imeInsetsSize),
                InsetsFrameProvider.InsetsSizeOverride(
                    TYPE_VOICE_INTERACTION,
                    visInsetsSizeForTappableElement
                ),
            )
        if (
            (context.isGestureNav || ENABLE_TASKBAR_NAVBAR_UNIFICATION) &&
                provider.type == tappableElement()
        ) {
            provider.insetsSizeOverrides = insetsSizeOverrideForTappableElement
        } else if (provider.type != systemGestures()) {
            // We only override insets at the bottom of the screen
            provider.insetsSizeOverrides = imeInsetsSizeOverride
        }
    }

    /**
     * Calculate the [Insets] for taskbar after a rotation, specifically for any potential cutouts
     * in the screen that can come from the camera.
     */
    private fun getInsetsForGravityWithCutout(inset: Int, gravity: Int, rot: Int): Insets {
        val display = context.display
        // If there is no cutout, fall back to the original method of calculating insets
        val cutout = display.cutout ?: return getInsetsForGravity(inset, gravity)
        val rotation = display.rotation
        val info = DisplayInfo()
        display.getDisplayInfo(info)
        val rotatedCutout = cutout.getRotated(info.logicalWidth, info.logicalHeight, rotation, rot)

        if ((gravity and Gravity.BOTTOM) == Gravity.BOTTOM) {
            return Insets.of(0, 0, 0, maxOf(inset, rotatedCutout.safeInsetBottom))
        }

        // TODO(b/230394142): seascape
        val isSeascape = (gravity and Gravity.START) == Gravity.START
        val leftInset = if (isSeascape) maxOf(inset, rotatedCutout.safeInsetLeft) else 0
        val rightInset = if (isSeascape) 0 else maxOf(inset, rotatedCutout.safeInsetRight)
        return Insets.of(leftInset, 0, rightInset, 0)
    }

    /**
     * @return [Insets] where the [inset] is either used as a bottom inset or right/left inset if
     *   using 3 button nav
     */
    private fun getInsetsForGravity(inset: Int, gravity: Int): Insets {
        if ((gravity and Gravity.BOTTOM) == Gravity.BOTTOM) {
            // Taskbar or portrait phone mode
            return Insets.of(0, 0, 0, inset)
        }

        // TODO(b/230394142): seascape
        val isSeascape = (gravity and Gravity.START) == Gravity.START
        val leftInset = if (isSeascape) inset else 0
        val rightInset = if (isSeascape) 0 else inset
        return Insets.of(leftInset, 0, rightInset, 0)
    }

    /**
     * Called to update the touchable insets.
     *
     * @see ViewTreeObserver.InternalInsetsInfo.setTouchableInsets
     */
    fun updateInsetsTouchability(insetsInfo: ViewTreeObserver.InternalInsetsInfo) {
        insetsInfo.touchableRegion.setEmpty()
        // Always have nav buttons be touchable
        controllers.navbarButtonsViewController.addVisibleButtonsRegion(
            context.dragLayer,
            insetsInfo.touchableRegion
        )
        debugTouchableRegion.lastSetTouchableBounds.set(insetsInfo.touchableRegion.bounds)

        val bubbleBarVisible =
            controllers.bubbleControllers.isPresent &&
                controllers.bubbleControllers.get().bubbleBarViewController.isBubbleBarVisible()
        var insetsIsTouchableRegion = true
        if (
            context.isPhoneButtonNavMode &&
                (!controllers.navbarButtonsViewController.isImeVisible ||
                    !controllers.navbarButtonsViewController.isImeRenderingNavButtons)
        ) {
            insetsInfo.setTouchableInsets(TOUCHABLE_INSETS_FRAME)
            insetsIsTouchableRegion = false
        } else if (context.dragLayer.alpha < AlphaUpdateListener.ALPHA_CUTOFF_THRESHOLD) {
            // Let touches pass through us.
            insetsInfo.setTouchableInsets(TOUCHABLE_INSETS_REGION)
            debugTouchableRegion.lastSetTouchableReason = "Taskbar is invisible"
        } else if (
            controllers.navbarButtonsViewController.isImeVisible &&
                controllers.taskbarStashController.isStashed
        ) {
            // Let touches pass through us.
            insetsInfo.setTouchableInsets(TOUCHABLE_INSETS_REGION)
            debugTouchableRegion.lastSetTouchableReason = "Stashed over IME"
        } else if (!controllers.uiController.isTaskbarTouchable) {
            // Let touches pass through us.
            insetsInfo.setTouchableInsets(TOUCHABLE_INSETS_REGION)
            debugTouchableRegion.lastSetTouchableReason = "Taskbar is not touchable"
        } else if (controllers.taskbarDragController.isSystemDragInProgress) {
            // Let touches pass through us.
            insetsInfo.setTouchableInsets(TOUCHABLE_INSETS_REGION)
            debugTouchableRegion.lastSetTouchableReason = "System drag is in progress"
        } else if (context.isTaskbarWindowFullscreen) {
            // Intercept entire fullscreen window.
            insetsInfo.setTouchableInsets(TOUCHABLE_INSETS_FRAME)
            insetsIsTouchableRegion = false
            debugTouchableRegion.lastSetTouchableReason = "Taskbar is fullscreen"
            context.dragLayer.getBoundsInWindow(debugTouchableRegion.lastSetTouchableBounds, false)
        } else if (
            controllers.taskbarViewController.areIconsVisible() ||
                context.isNavBarKidsModeActive ||
                bubbleBarVisible
        ) {
            // Taskbar has some touchable elements, take over the full taskbar area
            if (
                controllers.uiController.isInOverviewUi &&
                    DisplayController.isTransientTaskbar(context)
            ) {
                val region =
                    controllers.taskbarActivityContext.dragLayer.lastDrawnTransientRect.toRegion()
                val bubbleBarBounds =
                    controllers.bubbleControllers.getOrNull()?.let { bubbleControllers ->
                        if (!bubbleControllers.bubbleStashController.isBubblesShowingOnOverview) {
                            return@let null
                        }
                        if (!bubbleControllers.bubbleBarViewController.isBubbleBarVisible) {
                            return@let null
                        }
                        bubbleControllers.bubbleBarViewController.bubbleBarBounds
                    }

                // Include the bounds of the bubble bar in the touchable region if they exist.
                if (bubbleBarBounds != null) {
                    region.op(bubbleBarBounds, Region.Op.UNION)
                }
                insetsInfo.touchableRegion.set(region)
                debugTouchableRegion.lastSetTouchableReason = "Transient Taskbar is in Overview"
                debugTouchableRegion.lastSetTouchableBounds.set(region.bounds)
            } else {
                insetsInfo.touchableRegion.set(defaultTouchableRegion)
                debugTouchableRegion.lastSetTouchableReason = "Using default touchable region"
                debugTouchableRegion.lastSetTouchableBounds.set(defaultTouchableRegion.bounds)
            }
            insetsInfo.setTouchableInsets(TOUCHABLE_INSETS_REGION)
            insetsIsTouchableRegion = false
        } else {
            insetsInfo.setTouchableInsets(TOUCHABLE_INSETS_REGION)
            debugTouchableRegion.lastSetTouchableReason =
                "Icons are not visible, but other components such as 3 buttons might be"
        }
        context.excludeFromMagnificationRegion(insetsIsTouchableRegion)
    }

    /** Draws the last set touchableRegion as a red rectangle onto the given Canvas. */
    fun drawDebugTouchableRegionBounds(canvas: Canvas) {
        val paint = Paint()
        paint.color = Color.RED
        paint.style = Paint.Style.STROKE
        canvas.drawRect(debugTouchableRegion.lastSetTouchableBounds, paint)
    }

    override fun dumpLogs(prefix: String, pw: PrintWriter) {
        pw.println("${prefix}TaskbarInsetsController:")
        pw.println("$prefix\twindowHeight=${windowLayoutParams.height}")
        for (provider in windowLayoutParams.providedInsets) {
            pw.print(
                "$prefix\tprovidedInsets: (type=" +
                    WindowInsets.Type.toString(provider.type) +
                    " insetsSize=" +
                    provider.insetsSize
            )
            if (provider.insetsSizeOverrides != null) {
                pw.print(" insetsSizeOverrides={")
                for ((i, overrideSize) in provider.insetsSizeOverrides.withIndex()) {
                    if (i > 0) pw.print(", ")
                    pw.print(overrideSize)
                }
                pw.print("})")
            }
            pw.println()
        }
        pw.println("$prefix\tlastSetTouchableBounds=${debugTouchableRegion.lastSetTouchableBounds}")
        pw.println("$prefix\tlastSetTouchableReason=${debugTouchableRegion.lastSetTouchableReason}")
    }

    class DebugTouchableRegion {
        val lastSetTouchableBounds = Rect()
        var lastSetTouchableReason = ""
    }
}
