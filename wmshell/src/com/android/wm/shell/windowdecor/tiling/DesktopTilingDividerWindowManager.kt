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

package com.android.wm.shell.windowdecor.tiling

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.content.res.Configuration
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.Region
import android.os.Binder
import android.util.Size
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.SurfaceControl
import android.view.SurfaceControlViewHost
import android.view.View
import android.view.WindowManager
import android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
import android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
import android.view.WindowManager.LayoutParams.FLAG_SLIPPERY
import android.view.WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
import android.view.WindowManager.LayoutParams.PRIVATE_FLAG_NO_MOVE_ANIMATION
import android.view.WindowManager.LayoutParams.PRIVATE_FLAG_TRUSTED_OVERLAY
import android.view.WindowManager.LayoutParams.TYPE_DOCK_DIVIDER
import android.view.WindowlessWindowManager
import com.android.internal.jank.Cuj.CUJ_DESKTOP_MODE_TILE_RESIZING
import com.android.internal.jank.InteractionJankMonitor
import com.android.internal.protolog.ProtoLog
import com.android.wm.shell.R
import com.android.wm.shell.protolog.ShellProtoLogGroup.WM_SHELL_DESKTOP_MODE
import java.util.concurrent.TimeUnit
import java.util.function.Supplier

/**
 * a [WindowlessWindowManaer] responsible for hosting the [TilingDividerView] on the display root
 * when two tasks are tiled on left and right to resize them simultaneously.
 */
class DesktopTilingDividerWindowManager(
    config: Configuration,
    private val windowName: String,
    private val leash: SurfaceControl,
    private val transitionHandler: DesktopTilingWindowDecoration,
    private val transactionSupplier: Supplier<SurfaceControl.Transaction>,
    private var dividerBounds: Rect,
    private val displayContext: Context,
    private val isDarkMode: Boolean,
    private val interactionJankMonitor: InteractionJankMonitor,
) : WindowlessWindowManager(config, leash, null), DividerMoveCallback, View.OnLayoutChangeListener {
    private lateinit var viewHost: SurfaceControlViewHost
    private var tilingDividerView: TilingDividerView? = null
    private var dividerShown = false
    private var handleRegionSize: Size =
        Size(
            displayContext.resources.getDimensionPixelSize(
                R.dimen.split_divider_handle_region_height
            ),
            displayContext.resources.getDimensionPixelSize(
                R.dimen.split_divider_handle_region_width
            ),
        )
    private var setTouchRegion = true
    private val maxRoundedCornerRadius = getMaxRoundedCornerRadius()
    private var runningAnimator: ValueAnimator? = null

    /**
     * Gets bounds of divider window with screen based coordinate on the param Rect.
     *
     * @param rect bounds for the [TilingDividerView]
     */
    fun getDividerBounds(rect: Rect) {
        rect.set(dividerBounds)
    }

    /**
     * Sets the touch region for the SurfaceControlViewHost.
     *
     * The region includes the area around the handle (for accessibility), the divider itself and
     * the rounded corners (to prevent click reaching windows behind).
     */
    fun setTouchRegion(handle: Rect, divider: Rect, cornerRadius: Float) {
        val path = Path()
        path.fillType = Path.FillType.WINDING
        // The UI starts on the top-left corner leaving a radius gap, the region will be:
        //
        // dividerTop      +--+
        //                 |  |
        //       handleLeft|  |  handleRight
        // handleTop  +----+  +----+
        //            |  handle    |
        // handleBot  +----+  +----+
        //                 |  |
        //                 |  |
        // dividerBottom   +--+

        val centerX = cornerRadius + divider.width() / 2f
        val centerY = divider.height() / 2f
        val handleLeft = centerX - handle.width() / 2f
        val handleRight = handleLeft + handle.width()
        val dividerLeft = centerX - divider.width() / 2f
        val dividerRight = dividerLeft + divider.width()

        val dividerTop = cornerRadius
        val handleTop = centerY - handle.height() / 2f
        val handleBottom = handleTop + handle.height()
        val dividerBottom = divider.height() - cornerRadius

        path.addRect(handleLeft, handleTop, handleRight, handleBottom, Path.Direction.CCW)
        // Divider
        path.addRect(dividerLeft, dividerTop, dividerRight, dividerBottom, Path.Direction.CCW)

        val clip =
            Rect(handleLeft.toInt(), dividerTop.toInt(), handleRight.toInt(), dividerBottom.toInt())

        val region = Region()
        region.setPath(path, Region(clip))

        setTouchRegion(viewHost.windowToken.asBinder(), region)
    }

    /**
     * Builds a view host upon tiling two tasks left and right, and shows the divider view in the
     * middle of the screen between both tasks.
     *
     * @param relativeLeash the task leash that the TilingDividerView should be shown on top of.
     */
    fun generateViewHost(relativeLeash: SurfaceControl) {
        logV("Generating tiling view host.")
        val surfaceControlViewHost =
            SurfaceControlViewHost(
                displayContext,
                displayContext.display,
                this,
                "DesktopTilingManager",
            )
        val dividerView =
            LayoutInflater.from(displayContext)
                .inflate(R.layout.tiling_split_divider, /* root= */ null) as TilingDividerView
        val lp = getWindowManagerParams()
        surfaceControlViewHost.setView(dividerView, lp)
        val tmpDividerBounds = Rect()
        getDividerBounds(tmpDividerBounds)
        dividerView.setup(this, tmpDividerBounds, handleRegionSize, isDarkMode)
        val dividerAnimatorT = transactionSupplier.get()
        runningAnimator =
            ValueAnimator.ofFloat(0f, 1f).apply {
                duration = DIVIDER_FADE_IN_ALPHA_DURATION
                addUpdateListener {
                    dividerAnimatorT.setAlpha(leash, animatedValue as Float).apply()
                }
                addListener(
                    object : AnimatorListenerAdapter() {
                        override fun onAnimationStart(animation: Animator) {
                            dividerAnimatorT
                                .setRelativeLayer(leash, relativeLeash, 1)
                                .setPosition(
                                    leash,
                                    dividerBounds.left.toFloat() - maxRoundedCornerRadius,
                                    dividerBounds.top.toFloat(),
                                )
                                .setAlpha(leash, 0f)
                                .show(leash)
                                .apply()
                        }

                        override fun onAnimationEnd(animation: Animator) {
                            dividerAnimatorT.setAlpha(leash, 1f).apply()
                            runningAnimator = null
                        }
                    }
                )
            }
        runningAnimator?.start()
        dividerShown = true
        viewHost = surfaceControlViewHost
        tilingDividerView = dividerView
        updateTouchRegion()
        dividerView.addOnLayoutChangeListener(this)
    }

    /** Changes divider colour if dark/light mode is toggled. */
    fun onUiModeChange(isDarkMode: Boolean) {
        tilingDividerView?.onUiModeChange(isDarkMode)
    }

    /** Notifies the divider view of task info change and possible color change. */
    fun onThemeChange() {
        tilingDividerView?.onThemeChanged()
    }

    /** Hides the divider bar. */
    fun hideDividerBar() {
        if (!dividerShown) {
            return
        }
        logD("Hiding tiling divider bar.")
        cancelAnimation()
        val t = transactionSupplier.get()
        t.hide(leash)
        t.apply()
        dividerShown = false
    }

    fun cancelAnimation() {
        runningAnimator?.removeAllUpdateListeners()
        runningAnimator?.cancel()
        runningAnimator = null
    }

    /** Shows the divider bar. */
    fun showDividerBar(isTilingVisibleAfterRecents: Boolean) {
        if (dividerShown || runningAnimator != null) return
        logD("Showing tiling divider bar.")
        val dividerAnimatorT = transactionSupplier.get()
        val dividerAnimDuration =
            if (isTilingVisibleAfterRecents) {
                DIVIDER_FADE_IN_ALPHA_SLOW_DURATION
            } else {
                DIVIDER_FADE_IN_ALPHA_DURATION
            }
        runningAnimator =
            ValueAnimator.ofFloat(0f, 1f).apply {
                duration = dividerAnimDuration
                addUpdateListener {
                    dividerAnimatorT.setAlpha(leash, animatedValue as Float).apply()
                }
                addListener(
                    object : AnimatorListenerAdapter() {
                        override fun onAnimationStart(animation: Animator) {
                            dividerAnimatorT.setAlpha(leash, 0f).show(leash).apply()
                        }

                        override fun onAnimationEnd(animation: Animator) {
                            dividerAnimatorT.setAlpha(leash, 1f).apply()
                            runningAnimator = null
                        }
                    }
                )
            }
        runningAnimator?.start()
        dividerShown = true
    }

    /**
     * When the tiled task on top changes, the divider bar's Z access should change to be on top of
     * the latest focused task.
     */
    fun onRelativeLeashChanged(relativeLeash: SurfaceControl, t: SurfaceControl.Transaction) {
        t.setRelativeLayer(leash, relativeLeash, 1)
    }

    override fun onDividerMoveStart(pos: Int, motionEvent: MotionEvent) {
        logD("Tiling divider move start.")
        setSlippery(false)
        beginJankMonitoring()
        transitionHandler.onDividerHandleDragStart(motionEvent)
    }

    private fun beginJankMonitoring() {
        val dividerView =
            tilingDividerView
                ?: run {
                    logE(
                        "Attempting to monitor tiling jank without a tiling divider is not possible."
                    )
                    return
                }
        interactionJankMonitor.begin(
            InteractionJankMonitor.Configuration.Builder.withView(
                    CUJ_DESKTOP_MODE_TILE_RESIZING,
                    dividerView,
                )
                .setTimeout(LONG_CUJ_TIMEOUT_MS)
        )
    }

    private fun endJankMonitoring() {
        interactionJankMonitor.end(CUJ_DESKTOP_MODE_TILE_RESIZING)
    }

    /**
     * Moves the divider view to a new position after touch, gets called from the
     * [TilingDividerView] onTouch function.
     */
    override fun onDividerMove(pos: Int): Boolean {
        val t = transactionSupplier.get()
        t.setPosition(leash, pos.toFloat() - maxRoundedCornerRadius, dividerBounds.top.toFloat())
        val dividerWidth = dividerBounds.width()
        dividerBounds.set(pos, dividerBounds.top, pos + dividerWidth, dividerBounds.bottom)
        return transitionHandler.onDividerHandleMoved(dividerBounds, t)
    }

    /**
     * Notifies the transition handler of tiling operations ending, which might result in resizing
     * WindowContainerTransactions if the sizes of the tiled tasks changed.
     */
    override fun onDividerMovedEnd(pos: Int, motionEvent: MotionEvent) {
        logD("Tiling divider move end.")
        setSlippery(true)
        endJankMonitoring()
        val t = transactionSupplier.get()
        t.setPosition(leash, pos.toFloat() - maxRoundedCornerRadius, dividerBounds.top.toFloat())
        val dividerWidth = dividerBounds.width()
        dividerBounds.set(pos, dividerBounds.top, pos + dividerWidth, dividerBounds.bottom)
        transitionHandler.onDividerHandleDragEnd(dividerBounds, t, motionEvent)
    }

    private fun getWindowManagerParams(): WindowManager.LayoutParams {
        val lp =
            WindowManager.LayoutParams(
                /* w= */ dividerBounds.width() + 2 * maxRoundedCornerRadius,
                /* h= */ dividerBounds.height(),
                TYPE_DOCK_DIVIDER,
                FLAG_NOT_FOCUSABLE or
                    FLAG_NOT_TOUCH_MODAL or
                    FLAG_WATCH_OUTSIDE_TOUCH or
                    FLAG_SLIPPERY,
                PixelFormat.TRANSLUCENT,
            )
        lp.token = Binder()
        lp.title = windowName
        lp.privateFlags =
            lp.privateFlags or (PRIVATE_FLAG_NO_MOVE_ANIMATION or PRIVATE_FLAG_TRUSTED_OVERLAY)
        return lp
    }

    /**
     * Releases the surface control of the current [TilingDividerView] and tear down the view
     * hierarchy.y.
     */
    fun release() {
        cancelAnimation()
        tilingDividerView = null
        viewHost.release()
        transactionSupplier.get().hide(leash).remove(leash).apply()
    }

    override fun onLayoutChange(
        v: View?,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        oldLeft: Int,
        oldTop: Int,
        oldRight: Int,
        oldBottom: Int,
    ) {
        if (!setTouchRegion) return

        updateTouchRegion()
        setTouchRegion = false
    }

    private fun updateTouchRegion() {
        val startX = -handleRegionSize.width / 2
        val handle = Rect(startX, 0, startX + handleRegionSize.width, handleRegionSize.height)
        setTouchRegion(handle, dividerBounds, maxRoundedCornerRadius.toFloat())
    }

    private fun setSlippery(slippery: Boolean) {
        val lp = tilingDividerView?.layoutParams as WindowManager.LayoutParams
        val isSlippery = (lp.flags and FLAG_SLIPPERY) != 0
        if (isSlippery == slippery) return

        if (slippery) {
            lp.flags = lp.flags or FLAG_SLIPPERY
        } else {
            lp.flags = lp.flags and FLAG_SLIPPERY.inv()
        }
        viewHost.relayout(lp)
    }

    private fun getMaxRoundedCornerRadius(): Int {
        return displayContext.resources.getDimensionPixelSize(
            com.android.wm.shell.shared.R.dimen.desktop_windowing_freeform_rounded_corner_radius
        )
    }

    companion object {
        private const val DIVIDER_FADE_IN_ALPHA_DURATION = 300L
        private const val DIVIDER_FADE_IN_ALPHA_SLOW_DURATION = 900L
        // Timeout used for resize and drag CUJs, this is longer than the default timeout to avoid
        // timing out in the middle of a resize or drag action.
        private val LONG_CUJ_TIMEOUT_MS = TimeUnit.SECONDS.toMillis(10L)
        private val TAG = DesktopTilingDividerWindowManager::class.java.simpleName
    }

    private fun logD(msg: String, vararg arguments: Any?) {
        ProtoLog.d(WM_SHELL_DESKTOP_MODE, "%s: $msg", TAG, *arguments)
    }

    private fun logV(msg: String, vararg arguments: Any?) {
        ProtoLog.v(WM_SHELL_DESKTOP_MODE, "%s: $msg", TAG, *arguments)
    }

    private fun logE(msg: String, vararg arguments: Any?) {
        ProtoLog.e(WM_SHELL_DESKTOP_MODE, "%s: $msg", TAG, *arguments)
    }
}
