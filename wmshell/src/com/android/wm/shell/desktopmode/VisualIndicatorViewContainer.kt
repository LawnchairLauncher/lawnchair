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

package com.android.wm.shell.desktopmode

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.RectEvaluator
import android.animation.ValueAnimator
import android.app.ActivityManager
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.LayerDrawable
import android.view.Display
import android.view.SurfaceControl
import android.view.SurfaceControlViewHost
import android.view.View
import android.view.WindowManager
import android.view.WindowlessWindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.window.DesktopExperienceFlags
import androidx.core.animation.doOnEnd
import com.android.internal.annotations.VisibleForTesting
import com.android.wm.shell.R
import com.android.wm.shell.common.DisplayController
import com.android.wm.shell.common.DisplayLayout
import com.android.wm.shell.common.ShellExecutor
import com.android.wm.shell.common.SyncTransactionQueue
import com.android.wm.shell.desktopmode.DesktopModeVisualIndicator.IndicatorType
import com.android.wm.shell.shared.annotations.ShellDesktopThread
import com.android.wm.shell.shared.annotations.ShellMainThread
import com.android.wm.shell.shared.bubbles.BubbleAnythingFlagHelper
import com.android.wm.shell.shared.bubbles.BubbleDropTargetBoundsProvider
import com.android.wm.shell.windowdecor.WindowDecoration.SurfaceControlViewHostFactory
import com.android.wm.shell.windowdecor.tiling.SnapEventHandler

/**
 * Container for the view / viewhost of the indicator, ensuring it is created / animated off the
 * main thread.
 */
@VisibleForTesting
class VisualIndicatorViewContainer
@JvmOverloads
constructor(
    @ShellDesktopThread private val desktopExecutor: ShellExecutor,
    @ShellMainThread private val mainExecutor: ShellExecutor,
    private val indicatorBuilder: SurfaceControl.Builder,
    private val syncQueue: SyncTransactionQueue,
    private val surfaceControlViewHostFactory: SurfaceControlViewHostFactory =
        object : SurfaceControlViewHostFactory {},
    private val bubbleBoundsProvider: BubbleDropTargetBoundsProvider?,
    private val snapEventHandler: SnapEventHandler,
) {
    @VisibleForTesting var indicatorView: View? = null
    // Optional extra indicator showing the outline of the bubble bar
    private var barIndicatorView: View? = null
    private var indicatorViewHost: SurfaceControlViewHost? = null
    // Below variables and the SyncTransactionQueue are the only variables that should
    // be accessed from shell main thread. Everything else should be used exclusively
    // from the desktop thread.
    private var indicatorLeash: SurfaceControl? = null
    private var isReleased = false

    /** Create a fullscreen indicator with no animation */
    @ShellMainThread
    fun createView(
        context: Context,
        display: Display,
        layout: DisplayLayout,
        taskInfo: ActivityManager.RunningTaskInfo,
        taskSurface: SurfaceControl,
    ) {
        if (isReleased) return
        desktopExecutor.execute {
            val resources = context.resources
            val metrics = resources.displayMetrics
            val screenWidth: Int
            val screenHeight: Int
            if (DesktopExperienceFlags.ENABLE_BUG_FIXES_FOR_SECONDARY_DISPLAY.isTrue) {
                screenWidth = layout.width()
                screenHeight = layout.height()
            } else {
                screenWidth = metrics.widthPixels
                screenHeight = metrics.heightPixels
            }
            indicatorView =
                if (BubbleAnythingFlagHelper.enableBubbleToFullscreen()) {
                    FrameLayout(context)
                } else {
                    View(context)
                }
            val leash =
                indicatorBuilder
                    .setName("Desktop Mode Visual Indicator")
                    .setContainerLayer()
                    .setCallsite("DesktopModeVisualIndicator.createView")
                    .build()
            val lp =
                WindowManager.LayoutParams(
                    screenWidth,
                    screenHeight,
                    WindowManager.LayoutParams.TYPE_APPLICATION,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    PixelFormat.TRANSPARENT,
                )
            lp.title = "Desktop Mode Visual Indicator"
            lp.setTrustedOverlay()
            lp.inputFeatures =
                lp.inputFeatures or WindowManager.LayoutParams.INPUT_FEATURE_NO_INPUT_CHANNEL
            val windowManager =
                WindowlessWindowManager(
                    taskInfo.configuration,
                    leash,
                    /* hostInputTransferToken= */ null,
                )
            indicatorViewHost =
                surfaceControlViewHostFactory.create(
                    context,
                    display,
                    windowManager,
                    "VisualIndicatorViewContainer",
                )
            indicatorView?.let { indicatorViewHost?.setView(it, lp) }
            showIndicator(taskSurface, leash)
        }
    }

    /** Reparent the indicator to {@code newParent}. */
    fun reparentLeash(t: SurfaceControl.Transaction, newParent: SurfaceControl) {
        val leash = indicatorLeash ?: return
        t.reparent(leash, newParent)
    }

    private fun showIndicator(taskSurface: SurfaceControl, leash: SurfaceControl) {
        mainExecutor.execute {
            indicatorLeash = leash
            val t = SurfaceControl.Transaction()
            t.show(indicatorLeash)
            // We want this indicator to be behind the dragged task, but in front of all others.
            t.setRelativeLayer(indicatorLeash, taskSurface, -1)
            syncQueue.runInSync { transaction: SurfaceControl.Transaction ->
                transaction.merge(t)
                t.close()
            }
        }
    }

    @VisibleForTesting
    fun getIndicatorBounds(): Rect {
        return indicatorView?.background?.getBounds() ?: Rect()
    }

    /**
     * Takes existing indicator and animates it to bounds reflecting a new indicator type. Should
     * only be called from the main thread.
     */
    @ShellMainThread
    fun transitionIndicator(
        taskInfo: ActivityManager.RunningTaskInfo,
        displayController: DisplayController,
        currentType: IndicatorType,
        newType: IndicatorType,
    ) {
        if (currentType == newType || isReleased) return
        desktopExecutor.execute {
            val layout =
                displayController.getDisplayLayout(taskInfo.displayId)
                    ?: error("Expected to find DisplayLayout for taskId${taskInfo.taskId}.")
            if (currentType == IndicatorType.NO_INDICATOR) {
                fadeInIndicatorInternal(layout, newType, taskInfo.displayId, snapEventHandler)
            } else if (newType == IndicatorType.NO_INDICATOR) {
                fadeOutIndicator(
                    layout,
                    currentType,
                    /* finishCallback= */ null,
                    taskInfo.displayId,
                    snapEventHandler,
                )
            } else {
                val animStartType = IndicatorType.valueOf(currentType.name)
                val indicator = indicatorView ?: return@execute
                var animator: Animator =
                    VisualIndicatorAnimator.animateIndicatorType(
                        indicator,
                        layout,
                        animStartType,
                        newType,
                        bubbleBoundsProvider,
                        taskInfo.displayId,
                        snapEventHandler,
                    )
                if (BubbleAnythingFlagHelper.enableBubbleToFullscreen()) {
                    if (currentType.isBubbleType() || newType.isBubbleType()) {
                        animator = addBarIndicatorAnimation(animator, currentType, newType)
                    }
                }
                animator.start()
            }
        }
    }

    private fun addBarIndicatorAnimation(
        visualIndicatorAnimator: Animator,
        currentType: IndicatorType,
        newType: IndicatorType,
    ): Animator {
        if (newType.isBubbleType()) {
            getOrCreateBubbleBarIndicator(newType)?.let { bar ->
                return AnimatorSet().apply {
                    playTogether(visualIndicatorAnimator, fadeBarIndicatorIn(bar))
                }
            }
        }
        if (currentType.isBubbleType()) {
            barIndicatorView?.let { bar ->
                barIndicatorView = null
                return AnimatorSet().apply {
                    playTogether(visualIndicatorAnimator, fadeBarIndicatorOut(bar))
                }
            }
        }
        return visualIndicatorAnimator
    }

    /**
     * Fade indicator in as provided type.
     *
     * Animator fades the indicator in while expanding the bounds outwards.
     */
    fun fadeInIndicator(layout: DisplayLayout, type: IndicatorType, displayId: Int) {
        if (isReleased) return
        desktopExecutor.execute {
            fadeInIndicatorInternal(layout, type, displayId, snapEventHandler)
        }
    }

    /**
     * Fade indicator in as provided type. Animator fades it in while expanding the bounds outwards.
     */
    @VisibleForTesting
    fun fadeInIndicatorInternal(
        layout: DisplayLayout,
        type: IndicatorType,
        displayId: Int,
        snapEventHandler: SnapEventHandler,
    ) {
        desktopExecutor.assertCurrentThread()
        indicatorView?.let { indicator ->
            indicator.setBackgroundResource(R.drawable.desktop_windowing_transition_background)
            var animator: Animator =
                VisualIndicatorAnimator.fadeBoundsIn(
                    indicator,
                    type,
                    layout,
                    bubbleBoundsProvider,
                    displayId,
                    snapEventHandler,
                )
            if (BubbleAnythingFlagHelper.enableBubbleToFullscreen()) {
                animator = addBarIndicatorAnimation(animator, IndicatorType.NO_INDICATOR, type)
            }
            animator.start()
        }
    }

    /**
     * Fade out indicator without fully releasing it. Animator fades it out while shrinking bounds.
     *
     * @param finishCallback called when animation ends or gets cancelled
     */
    fun fadeOutIndicator(
        layout: DisplayLayout,
        currentType: IndicatorType,
        finishCallback: Runnable?,
        displayId: Int,
        snapEventHandler: SnapEventHandler,
    ) {
        if (currentType == IndicatorType.NO_INDICATOR) {
            // In rare cases, fade out can be requested before the indicator has determined its
            // initial type and started animating in. In this case, no animator is needed.
            finishCallback?.run()
            return
        }
        desktopExecutor.execute {
            indicatorView?.let {
                val animStartType = IndicatorType.valueOf(currentType.name)
                var animator: Animator =
                    VisualIndicatorAnimator.fadeBoundsOut(
                        it,
                        animStartType,
                        layout,
                        bubbleBoundsProvider,
                        displayId,
                        snapEventHandler,
                    )
                if (BubbleAnythingFlagHelper.enableBubbleToFullscreen()) {
                    animator =
                        addBarIndicatorAnimation(animator, currentType, IndicatorType.NO_INDICATOR)
                }
                animator.addListener(
                    object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator) {
                            if (finishCallback != null) {
                                mainExecutor.execute(finishCallback)
                            }
                        }
                    }
                )
                animator.start()
            }
        }
    }

    /** Release the indicator and its components when it is no longer needed. */
    @ShellMainThread
    fun releaseVisualIndicator() {
        if (isReleased) return
        desktopExecutor.execute {
            indicatorViewHost?.release()
            indicatorViewHost = null
        }
        indicatorLeash?.let {
            val tx = SurfaceControl.Transaction()
            tx.remove(it)
            indicatorLeash = null
            syncQueue.runInSync { transaction: SurfaceControl.Transaction ->
                transaction.merge(tx)
                tx.close()
            }
        }
        isReleased = true
    }

    private fun getOrCreateBubbleBarIndicator(type: IndicatorType): View? {
        val container = indicatorView as? FrameLayout ?: return null
        val onLeft = type == IndicatorType.TO_BUBBLE_LEFT_INDICATOR
        val bounds = bubbleBoundsProvider?.getBarDropTargetBounds(onLeft) ?: return null
        val lp = FrameLayout.LayoutParams(bounds.width(), bounds.height())
        lp.leftMargin = bounds.left
        lp.topMargin = bounds.top
        if (barIndicatorView == null) {
            val indicator = View(container.context)
            indicator.setBackgroundResource(R.drawable.desktop_windowing_transition_background)
            container.addView(indicator, lp)
            barIndicatorView = indicator
        } else {
            barIndicatorView?.layoutParams = lp
        }
        return barIndicatorView
    }

    private fun fadeBarIndicatorIn(barIndicator: View): Animator {
        // Use layout bounds as the end bounds in case the view has not been laid out yet
        val lp = barIndicator.layoutParams
        val endBounds = Rect(0, 0, lp.width, lp.height)
        return VisualIndicatorAnimator.fadeBoundsIn(barIndicator, endBounds)
    }

    private fun fadeBarIndicatorOut(barIndicator: View): Animator {
        val startBounds = Rect(0, 0, barIndicator.width, barIndicator.height)
        val barAnimator = VisualIndicatorAnimator.fadeBoundsOut(barIndicator, startBounds)
        barAnimator.doOnEnd { (indicatorView as? FrameLayout)?.removeView(barIndicator) }
        return barAnimator
    }

    /**
     * Animator for Desktop Mode transitions which supports bounds and alpha animation. Functions
     * should only be called from the desktop executor.
     */
    @VisibleForTesting
    class VisualIndicatorAnimator(view: View, startBounds: Rect, endBounds: Rect) :
        ValueAnimator() {
        /**
         * Determines how this animator will interact with the view's alpha: Fade in, fade out, or
         * no change to alpha
         */
        private enum class AlphaAnimType {
            ALPHA_FADE_IN_ANIM,
            ALPHA_FADE_OUT_ANIM,
            ALPHA_NO_CHANGE_ANIM,
        }

        private val indicatorView: View = view
        @VisibleForTesting val indicatorStartBounds = Rect(startBounds)
        @VisibleForTesting val indicatorEndBounds = endBounds
        private val mRectEvaluator: RectEvaluator

        init {
            setFloatValues(0f, 1f)
            mRectEvaluator = RectEvaluator(Rect())
        }

        /**
         * Update bounds of view based on current animation fraction. Use of delta is to animate
         * bounds independently, in case we need to run multiple animations simultaneously.
         *
         * @param fraction fraction to use, compared against previous fraction
         * @param view the view to update
         */
        @ShellDesktopThread
        private fun updateBounds(fraction: Float, view: View?) {
            if (indicatorStartBounds == indicatorEndBounds) {
                return
            }
            val currentBounds =
                mRectEvaluator.evaluate(fraction, indicatorStartBounds, indicatorEndBounds)
            view?.background?.bounds = currentBounds
        }

        /**
         * Fade in the fullscreen indicator
         *
         * @param fraction current animation fraction
         */
        @ShellDesktopThread
        private fun updateIndicatorAlpha(fraction: Float, view: View?) {
            val drawable = view?.background as LayerDrawable
            drawable.findDrawableByLayerId(R.id.indicator_stroke).alpha =
                (MAXIMUM_OPACITY * fraction).toInt()
            drawable.findDrawableByLayerId(R.id.indicator_solid).alpha =
                (MAXIMUM_OPACITY * fraction * INDICATOR_FINAL_OPACITY).toInt()
        }

        companion object {
            private const val FULLSCREEN_INDICATOR_DURATION = 200
            private const val FULLSCREEN_SCALE_ADJUSTMENT_PERCENT = 0.015f
            private const val INDICATOR_FINAL_OPACITY = 0.35f
            private const val MAXIMUM_OPACITY = 255

            @ShellDesktopThread
            fun fadeBoundsIn(
                view: View,
                type: IndicatorType,
                displayLayout: DisplayLayout,
                bubbleBoundsProvider: BubbleDropTargetBoundsProvider?,
                displayId: Int,
                snapEventHandler: SnapEventHandler,
            ): VisualIndicatorAnimator {
                val endBounds =
                    getIndicatorBounds(
                        displayLayout,
                        type,
                        bubbleBoundsProvider,
                        displayId,
                        snapEventHandler,
                    )
                return fadeBoundsIn(view, endBounds)
            }

            @ShellDesktopThread
            fun fadeBoundsIn(view: View, endBounds: Rect): VisualIndicatorAnimator {
                val startBounds = getMinBounds(endBounds)
                view.background.bounds = startBounds
                val animator = VisualIndicatorAnimator(view, startBounds, endBounds)
                animator.interpolator = DecelerateInterpolator()
                setupIndicatorAnimation(animator, AlphaAnimType.ALPHA_FADE_IN_ANIM)
                return animator
            }

            @ShellDesktopThread
            fun fadeBoundsOut(
                view: View,
                type: IndicatorType,
                displayLayout: DisplayLayout,
                bubbleBoundsProvider: BubbleDropTargetBoundsProvider?,
                displayId: Int,
                snapEventHandler: SnapEventHandler,
            ): VisualIndicatorAnimator {
                val startBounds =
                    getIndicatorBounds(
                        displayLayout,
                        type,
                        bubbleBoundsProvider,
                        displayId,
                        snapEventHandler,
                    )
                return fadeBoundsOut(view, startBounds)
            }

            @ShellDesktopThread
            fun fadeBoundsOut(view: View, startBounds: Rect): VisualIndicatorAnimator {
                val endBounds = getMinBounds(startBounds)
                view.background.bounds = startBounds
                val animator = VisualIndicatorAnimator(view, startBounds, endBounds)
                animator.interpolator = DecelerateInterpolator()
                setupIndicatorAnimation(animator, AlphaAnimType.ALPHA_FADE_OUT_ANIM)
                return animator
            }

            /**
             * Create animator for visual indicator changing type (i.e., fullscreen to freeform,
             * freeform to split, etc.)
             *
             * @param view the view for this indicator
             * @param displayLayout information about the display the transitioning task is
             *   currently on
             * @param origType the original indicator type
             * @param newType the new indicator type
             * @param desktopExecutor: the executor for the ShellDesktopThread; should be the only
             *   thread this function runs on
             */
            @ShellDesktopThread
            fun animateIndicatorType(
                view: View,
                displayLayout: DisplayLayout,
                origType: IndicatorType,
                newType: IndicatorType,
                bubbleBoundsProvider: BubbleDropTargetBoundsProvider?,
                displayId: Int,
                snapEventHandler: SnapEventHandler,
            ): VisualIndicatorAnimator {
                val startBounds =
                    getIndicatorBounds(
                        displayLayout,
                        origType,
                        bubbleBoundsProvider,
                        displayId,
                        snapEventHandler,
                    )
                val endBounds =
                    getIndicatorBounds(
                        displayLayout,
                        newType,
                        bubbleBoundsProvider,
                        displayId,
                        snapEventHandler,
                    )
                val animator = VisualIndicatorAnimator(view, startBounds, endBounds)
                animator.interpolator = DecelerateInterpolator()
                setupIndicatorAnimation(animator, AlphaAnimType.ALPHA_NO_CHANGE_ANIM)
                return animator
            }

            /** Calculates the bounds the indicator should have when fully faded in. */
            private fun getIndicatorBounds(
                layout: DisplayLayout,
                type: IndicatorType,
                bubbleBoundsProvider: BubbleDropTargetBoundsProvider?,
                displayId: Int,
                snapEventHandler: SnapEventHandler,
            ): Rect {
                val desktopStableBounds = Rect()
                layout.getStableBounds(desktopStableBounds)
                val padding = desktopStableBounds.top
                when (type) {
                    IndicatorType.TO_FULLSCREEN_INDICATOR -> {
                        desktopStableBounds.top += padding
                        desktopStableBounds.bottom -= padding
                        desktopStableBounds.left += padding
                        desktopStableBounds.right -= padding
                        return desktopStableBounds
                    }

                    IndicatorType.TO_DESKTOP_INDICATOR -> {
                        val adjustmentPercentage =
                            (1f - DESKTOP_MODE_INITIAL_BOUNDS_SCALE)
                        return Rect(
                            (adjustmentPercentage * desktopStableBounds.width() / 2).toInt(),
                            (adjustmentPercentage * desktopStableBounds.height() / 2).toInt(),
                            (desktopStableBounds.width() -
                                    (adjustmentPercentage * desktopStableBounds.width() / 2))
                                .toInt(),
                            (desktopStableBounds.height() -
                                    (adjustmentPercentage * desktopStableBounds.height() / 2))
                                .toInt(),
                        )
                    }

                    IndicatorType.TO_SPLIT_LEFT_INDICATOR -> {
                        val currentLeftBounds = snapEventHandler.getLeftSnapBoundsIfTiled(displayId)
                        return Rect(
                            padding,
                            padding,
                            currentLeftBounds.right - padding,
                            desktopStableBounds.height(),
                        )
                    }
                    IndicatorType.TO_SPLIT_RIGHT_INDICATOR -> {
                        val currentRightBounds =
                            snapEventHandler.getRightSnapBoundsIfTiled(displayId)
                        return Rect(
                            currentRightBounds.left + padding,
                            padding,
                            desktopStableBounds.width() - padding,
                            desktopStableBounds.height(),
                        )
                    }
                    IndicatorType.TO_BUBBLE_LEFT_INDICATOR ->
                        return bubbleBoundsProvider?.getBubbleBarExpandedViewDropTargetBounds(
                            /* onLeft= */ true
                        ) ?: Rect()
                    IndicatorType.TO_BUBBLE_RIGHT_INDICATOR ->
                        return bubbleBoundsProvider?.getBubbleBarExpandedViewDropTargetBounds(
                            /* onLeft= */ false
                        ) ?: Rect()
                    else -> throw IllegalArgumentException("Invalid indicator type provided.")
                }
            }

            /** Add necessary listener for animation of indicator */
            private fun setupIndicatorAnimation(
                animator: VisualIndicatorAnimator,
                animType: AlphaAnimType,
            ) {
                animator.addUpdateListener { a: ValueAnimator ->
                    animator.updateBounds(a.animatedFraction, animator.indicatorView)
                    if (animType == AlphaAnimType.ALPHA_FADE_IN_ANIM) {
                        animator.updateIndicatorAlpha(a.animatedFraction, animator.indicatorView)
                    } else if (animType == AlphaAnimType.ALPHA_FADE_OUT_ANIM) {
                        animator.updateIndicatorAlpha(
                            1 - a.animatedFraction,
                            animator.indicatorView,
                        )
                    }
                }
                animator.addListener(
                    object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator) {
                            animator.indicatorView.background.bounds = animator.indicatorEndBounds
                        }
                    }
                )
                animator.setDuration(FULLSCREEN_INDICATOR_DURATION.toLong())
            }

            /**
             * Return the minimum bounds of a visual indicator, to be used at the end of fading out
             * and the start of fading in.
             */
            private fun getMinBounds(maxBounds: Rect): Rect {
                return Rect(
                    (maxBounds.left + (FULLSCREEN_SCALE_ADJUSTMENT_PERCENT * maxBounds.width()))
                        .toInt(),
                    (maxBounds.top + (FULLSCREEN_SCALE_ADJUSTMENT_PERCENT * maxBounds.height()))
                        .toInt(),
                    (maxBounds.right - (FULLSCREEN_SCALE_ADJUSTMENT_PERCENT * maxBounds.width()))
                        .toInt(),
                    (maxBounds.bottom - (FULLSCREEN_SCALE_ADJUSTMENT_PERCENT * maxBounds.height()))
                        .toInt(),
                )
            }
        }
    }

    private fun IndicatorType.isBubbleType(): Boolean {
        return this == IndicatorType.TO_BUBBLE_LEFT_INDICATOR ||
            this == IndicatorType.TO_BUBBLE_RIGHT_INDICATOR
    }
}
