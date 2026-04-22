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

package com.android.launcher3.taskbar.bubbles.stashing

import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Rect
import android.view.MotionEvent
import android.view.View
import androidx.annotation.VisibleForTesting
import androidx.core.animation.doOnEnd
import androidx.core.animation.doOnStart
import androidx.dynamicanimation.animation.SpringForce
import com.android.app.animation.Interpolators.EMPHASIZED
import com.android.app.animation.Interpolators.LINEAR
import com.android.launcher3.R
import com.android.launcher3.anim.AnimatedFloat
import com.android.launcher3.anim.SpringAnimationBuilder
import com.android.launcher3.taskbar.BarsLocationAnimatorHelper.FADE_IN_ANIM_ALPHA_DURATION_MS
import com.android.launcher3.taskbar.BarsLocationAnimatorHelper.FADE_OUT_ANIM_ALPHA_DELAY_MS
import com.android.launcher3.taskbar.BarsLocationAnimatorHelper.FADE_OUT_ANIM_ALPHA_DURATION_MS
import com.android.launcher3.taskbar.BarsLocationAnimatorHelper.FADE_OUT_ANIM_POSITION_DURATION_MS
import com.android.launcher3.taskbar.BarsLocationAnimatorHelper.inShiftX
import com.android.launcher3.taskbar.BarsLocationAnimatorHelper.outShift
import com.android.launcher3.taskbar.TaskbarInsetsController
import com.android.launcher3.taskbar.TaskbarStashController.TASKBAR_STASH_ALPHA_START_DELAY
import com.android.launcher3.taskbar.TaskbarStashController.TRANSIENT_TASKBAR_STASH_ALPHA_DURATION
import com.android.launcher3.taskbar.bubbles.BubbleBarViewController
import com.android.launcher3.taskbar.bubbles.BubbleStashedHandleViewController
import com.android.launcher3.taskbar.bubbles.stashing.BubbleStashController.BubbleLauncherState
import com.android.launcher3.taskbar.bubbles.stashing.BubbleStashController.Companion.BAR_STASH_DURATION
import com.android.launcher3.taskbar.bubbles.stashing.BubbleStashController.Companion.BAR_TRANSLATION_DURATION
import com.android.launcher3.taskbar.bubbles.stashing.BubbleStashController.ControllersAfterInitAction
import com.android.launcher3.taskbar.bubbles.stashing.BubbleStashController.TaskbarHotseatDimensionsProvider
import com.android.launcher3.util.MultiPropertyFactory
import com.android.wm.shell.shared.animation.PhysicsAnimator
import com.android.wm.shell.shared.bubbles.BubbleBarLocation
import com.android.wm.shell.shared.bubbles.ContextUtils.isRtl
import kotlin.math.max

class TransientBubbleStashController(
    private val taskbarHotseatDimensionsProvider: TaskbarHotseatDimensionsProvider,
    private val context: Context,
) : BubbleStashController {

    private lateinit var bubbleBarViewController: BubbleBarViewController
    private lateinit var taskbarInsetsController: TaskbarInsetsController
    private lateinit var controllersAfterInitAction: ControllersAfterInitAction

    // stash view properties
    private var bubbleStashedHandleViewController: BubbleStashedHandleViewController? = null
    private var stashHandleViewAlpha: MultiPropertyFactory<View>.MultiProperty? = null
    private var translationYDuringStash = AnimatedFloat { transY ->
        bubbleStashedHandleViewController?.setTranslationYForStash(transY)
        bubbleBarViewController.setTranslationYForStash(transY)
    }
    private val stashHandleStashVelocity =
        context.resources.getDimension(R.dimen.bubblebar_stashed_handle_spring_velocity_dp_per_s)
    private var stashedHeight: Int = 0

    // bubble bar properties
    private lateinit var bubbleBarAlpha: MultiPropertyFactory<View>.MultiProperty
    private lateinit var bubbleBarBubbleAlpha: AnimatedFloat
    private lateinit var bubbleBarBackgroundAlpha: AnimatedFloat
    private lateinit var bubbleBarTranslationYAnimator: AnimatedFloat
    private lateinit var bubbleBarBubbleTranslationY: AnimatedFloat
    private lateinit var bubbleBarBackgroundScaleX: AnimatedFloat
    private lateinit var bubbleBarBackgroundScaleY: AnimatedFloat
    private val handleCenterFromScreenBottom =
        context.resources.getDimensionPixelSize(R.dimen.bubblebar_stashed_size) / 2f

    private var animator: AnimatorSet? = null
    override var bubbleBarVerticalCenterForHome: Int = 0

    override var isStashed: Boolean = false
        @VisibleForTesting set

    override var launcherState: BubbleLauncherState = BubbleLauncherState.IN_APP
        set(state) {
            if (field == state) return
            field = state
            val hasBubbles = bubbleBarViewController.hasBubbles()
            bubbleBarViewController.onBubbleBarConfigurationChanged(hasBubbles)
            if (!hasBubbles) {
                // if there are no bubbles, there's no need to update the bubble bar, just keep the
                // isStashed state up to date so that we can process state changes when bubbles are
                // created.
                isStashed = launcherState == BubbleLauncherState.IN_APP
                return
            }
            if (field == BubbleLauncherState.HOME) {
                // When to home we need to animate the bubble bar
                // here to align with hotseat center.
                animateBubbleBarYToHotseat()
            } else if (field == BubbleLauncherState.OVERVIEW) {
                // When transitioning to overview we need to animate the bubble bar to align with
                // the taskbar bottom.
                animateBubbleBarYToTaskbar()
            }
            // Only stash if we're in an app, otherwise we're in home or overview where we should
            // be un-stashed
            val stash = field == BubbleLauncherState.IN_APP
            val expand =
                if (stash) {
                    // Always collapse when we are stashing
                    false
                } else {
                    // If unstashing, keep the current state
                    bubbleBarViewController.isExpanded
                }
            updateStashedAndExpandedState(stash, expand)
        }

    override var isSysuiLocked: Boolean = false
        set(isLocked) {
            if (field == isLocked) return
            field = isLocked
            if (!isLocked && bubbleBarViewController.hasBubbles()) {
                animateAfterUnlock()
            }
        }

    override val isTransientTaskBar: Boolean = true

    override val bubbleBarTranslationYForHotseat: Float
        get() {
            val bubbleBarHeight = bubbleBarViewController.bubbleBarCollapsedHeight
            return -bubbleBarVerticalCenterForHome + bubbleBarHeight / 2
        }

    override val bubbleBarTranslationYForTaskbar: Float =
        -taskbarHotseatDimensionsProvider.getTaskbarBottomSpace().toFloat()

    /** Not supported in transient mode */
    override var inAppDisplayOverrideProgress: Float = 0f

    /** Check if we have handle view controller */
    override val hasHandleView: Boolean
        get() = bubbleStashedHandleViewController != null

    override fun init(
        taskbarInsetsController: TaskbarInsetsController,
        bubbleBarViewController: BubbleBarViewController,
        bubbleStashedHandleViewController: BubbleStashedHandleViewController?,
        controllersAfterInitAction: ControllersAfterInitAction,
    ) {
        this.taskbarInsetsController = taskbarInsetsController
        this.bubbleBarViewController = bubbleBarViewController
        this.bubbleStashedHandleViewController = bubbleStashedHandleViewController
        this.controllersAfterInitAction = controllersAfterInitAction
        bubbleBarTranslationYAnimator = bubbleBarViewController.bubbleBarTranslationY
        bubbleBarBubbleTranslationY = bubbleBarViewController.bubbleOffsetY
        // bubble bar has only alpha property, getting it at index 0
        bubbleBarAlpha = bubbleBarViewController.bubbleBarAlpha.get(/* index= */ 0)
        bubbleBarBubbleAlpha = bubbleBarViewController.bubbleBarBubbleAlpha
        bubbleBarBackgroundAlpha = bubbleBarViewController.bubbleBarBackgroundAlpha
        bubbleBarBackgroundScaleX = bubbleBarViewController.bubbleBarBackgroundScaleX
        bubbleBarBackgroundScaleY = bubbleBarViewController.bubbleBarBackgroundScaleY
        stashedHeight = bubbleStashedHandleViewController?.stashedHeight ?: 0
        stashHandleViewAlpha = bubbleStashedHandleViewController?.stashedHandleAlpha?.get(0)
    }

    private fun animateAfterUnlock() {
        val animatorSet = AnimatorSet()
        if (isBubblesShowingOnHome || isBubblesShowingOnOverview) {
            isStashed = false
            animatorSet.playTogether(
                bubbleBarBackgroundScaleX.animateToValue(1f),
                bubbleBarBackgroundScaleY.animateToValue(1f),
                bubbleBarTranslationYAnimator.animateToValue(bubbleBarTranslationY),
                bubbleBarAlpha.animateToValue(1f),
                bubbleBarBubbleAlpha.animateToValue(1f),
                bubbleBarBackgroundAlpha.animateToValue(1f),
            )
        } else {
            isStashed = true
            stashHandleViewAlpha?.let { animatorSet.playTogether(it.animateToValue(1f)) }
        }
        animatorSet
            .updateBarVisibility(isStashed)
            .updateTouchRegionOnAnimationEnd()
            .setDuration(BAR_STASH_DURATION)
            .start()
    }

    override fun showBubbleBarImmediate() {
        showBubbleBarImmediate(bubbleBarTranslationY)
    }

    override fun showBubbleBarImmediate(bubbleBarTranslationY: Float) {
        showBubbleBarImmediateVisually(bubbleBarTranslationY)
        onIsStashedChanged()
    }

    private fun showBubbleBarImmediateVisually(bubbleBarTranslationY: Float) {
        bubbleStashedHandleViewController?.setTranslationYForSwipe(0f)
        stashHandleViewAlpha?.value = 0f
        this.bubbleBarTranslationYAnimator.updateValue(bubbleBarTranslationY)
        bubbleBarAlpha.setValue(1f)
        bubbleBarBubbleAlpha.updateValue(1f)
        bubbleBarBackgroundAlpha.updateValue(1f)
        bubbleBarBackgroundScaleX.updateValue(1f)
        bubbleBarBackgroundScaleY.updateValue(1f)
        isStashed = false
        bubbleBarViewController.setHiddenForStashed(false)
    }

    override fun stashBubbleBarImmediate() {
        stashBubbleBarImmediateVisually()
        onIsStashedChanged()
    }

    private fun stashBubbleBarImmediateVisually() {
        bubbleStashedHandleViewController?.setTranslationYForSwipe(0f)
        stashHandleViewAlpha?.value = 1f
        this.bubbleBarTranslationYAnimator.updateValue(getStashTranslation())
        bubbleBarAlpha.setValue(0f)
        // Reset bubble and background alpha to 1 and only keep the bubble bar alpha at 0
        bubbleBarBubbleAlpha.updateValue(1f)
        bubbleBarBackgroundAlpha.updateValue(1f)
        bubbleBarBackgroundScaleX.updateValue(getStashScaleX())
        bubbleBarBackgroundScaleY.updateValue(getStashScaleY())
        isStashed = true
        bubbleBarViewController.setHiddenForStashed(true)
    }

    override fun getTouchableHeight(): Int =
        when {
            isStashed -> stashedHeight
            isBubbleBarVisible() -> bubbleBarViewController.bubbleBarCollapsedHeight.toInt()
            else -> 0
        }

    override fun isBubbleBarVisible(): Boolean = bubbleBarViewController.hasBubbles() && !isStashed

    override fun onNewBubbleAnimationInterrupted(isStashed: Boolean, bubbleBarTranslationY: Float) {
        if (isStashed) {
            stashBubbleBarImmediate()
        } else {
            showBubbleBarImmediate(bubbleBarTranslationY)
        }
    }

    /** Check if [ev] belongs to the stash handle or the bubble bar views. */
    override fun isEventOverBubbleBarViews(ev: MotionEvent): Boolean {
        val isOverHandle = bubbleStashedHandleViewController?.isEventOverHandle(ev) ?: false
        return isOverHandle || bubbleBarViewController.isEventOverAnyItem(ev)
    }

    /** Set the bubble bar stash handle location . */
    override fun setBubbleBarLocation(bubbleBarLocation: BubbleBarLocation) {
        bubbleStashedHandleViewController?.setBubbleBarLocation(bubbleBarLocation)
    }

    override fun stashBubbleBar() {
        updateStashedAndExpandedState(stash = true, expand = false)
    }

    override fun stashBubbleBarToLocation(
        fromLocation: BubbleBarLocation,
        toLocation: BubbleBarLocation,
    ) {
        if (fromLocation.isSameSideWith(toLocation)) {
            updateStashedAndExpandedState(
                stash = true,
                expand = false,
                updateTouchRegionOnEnd = false,
            )
            return
        }
        cancelAnimation()
        animator =
            AnimatorSet().apply {
                playSequentially(
                    bubbleBarViewController.animateBubbleBarLocationOut(toLocation),
                    createHandleInAnimator(location = toLocation),
                )
                start()
            }
    }

    override fun showBubbleBar(expandBubbles: Boolean, bubbleBarGesture: Boolean) {
        updateStashedAndExpandedState(
            stash = false,
            expand = expandBubbles,
            bubbleBarGesture = bubbleBarGesture,
        )
    }

    override fun showBubbleBarAtLocation(
        fromLocation: BubbleBarLocation,
        toLocation: BubbleBarLocation,
    ) {
        if (fromLocation.isSameSideWith(toLocation)) {
            updateStashedAndExpandedState(
                stash = false,
                expand = false,
                updateTouchRegionOnEnd = false,
            )
            return
        }
        cancelAnimation()
        val bubbleBarInAnimation =
            bubbleBarViewController.animateBubbleBarLocationIn(fromLocation, toLocation).apply {
                doOnStart { showBubbleBarImmediateVisually(bubbleBarTranslationY) }
            }
        animator =
            AnimatorSet().apply {
                playSequentially(
                    createHandleOutAnimator(location = toLocation),
                    bubbleBarInAnimation,
                )
                start()
            }
    }

    override fun getDiffBetweenHandleAndBarCenters(): Float {
        // the difference between the centers of the handle and the bubble bar is the difference
        // between their distance from the bottom of the screen.
        val barCenter: Float = bubbleBarViewController.bubbleBarCollapsedHeight / 2f
        return handleCenterFromScreenBottom - barCenter
    }

    override fun getStashedHandleTranslationForNewBubbleAnimation(): Float {
        return -handleCenterFromScreenBottom
    }

    override fun getStashedHandlePhysicsAnimator(): PhysicsAnimator<View>? {
        return bubbleStashedHandleViewController?.physicsAnimator
    }

    override fun updateTaskbarTouchRegion() {
        taskbarInsetsController.onTaskbarOrBubblebarWindowHeightOrInsetsChanged()
    }

    override fun setHandleTranslationY(translationY: Float) {
        bubbleStashedHandleViewController?.setTranslationYForSwipe(translationY)
    }

    override fun getHandleTranslationY(): Float? = bubbleStashedHandleViewController?.translationY

    override fun getHandleBounds(bounds: Rect) {
        bubbleStashedHandleViewController?.getBounds(bounds)
    }

    private fun getStashTranslation(): Float {
        return (bubbleBarTranslationY - stashedHeight) / 2f
    }

    @VisibleForTesting
    fun getStashScaleX(): Float {
        val handleWidth = bubbleStashedHandleViewController?.stashedWidth ?: 0
        return handleWidth / bubbleBarViewController.bubbleBarCollapsedWidth
    }

    @VisibleForTesting
    fun getStashScaleY(): Float {
        val handleHeight = bubbleStashedHandleViewController?.stashedHeight ?: 0
        return handleHeight / bubbleBarViewController.bubbleBarCollapsedHeight
    }

    /**
     * Create a stash animation.
     *
     * @param isStashed whether it's a stash animation or an unstash animation
     * @param duration duration of the animation
     * @return the animation
     */
    @Suppress("SameParameterValue")
    private fun createStashAnimator(isStashed: Boolean, duration: Long): AnimatorSet {
        val animatorSet = AnimatorSet()

        animatorSet.play(
            createBackgroundAlphaAnimator(isStashed).apply {
                val alphaDuration =
                    if (isStashed) duration else TRANSIENT_TASKBAR_STASH_ALPHA_DURATION
                val alphaDelay = if (isStashed) TASKBAR_STASH_ALPHA_START_DELAY else 0L
                this.duration = max(0L, alphaDuration - alphaDelay)
                this.startDelay = alphaDelay
                this.interpolator = LINEAR
            }
        )

        animatorSet.play(
            bubbleBarBubbleAlpha
                .animateToValue(getBarAlphaStart(isStashed), getBarAlphaEnd(isStashed))
                .apply {
                    this.duration = TRANSIENT_TASKBAR_STASH_ALPHA_DURATION
                    this.startDelay = TASKBAR_STASH_ALPHA_START_DELAY
                    this.interpolator = LINEAR
                }
        )

        animatorSet.play(
            createSpringOnStashAnimator(isStashed).apply {
                this.duration = duration
                this.interpolator = LINEAR
            }
        )

        animatorSet.play(
            bubbleBarViewController.createRevealAnimatorForStashChange(isStashed).apply {
                this.duration = duration
                this.interpolator = EMPHASIZED
            }
        )

        // Animate bubble translation to keep reveal animation in the bounds of the bar
        val bubbleTyStart = if (isStashed) 0f else -bubbleBarTranslationY
        val bubbleTyEnd = if (isStashed) -bubbleBarTranslationY else 0f
        animatorSet.play(
            bubbleBarBubbleTranslationY.animateToValue(bubbleTyStart, bubbleTyEnd).apply {
                this.duration = duration
                this.interpolator = EMPHASIZED
            }
        )

        animatorSet.play(
            bubbleStashedHandleViewController?.createRevealAnimToIsStashed(isStashed)?.apply {
                this.duration = duration
                this.interpolator = EMPHASIZED
            }
        )

        val pivotX = if (bubbleBarViewController.isBubbleBarOnLeft) 0f else 1f
        animatorSet.play(
            createScaleAnimator(isStashed).apply {
                this.duration = duration
                this.interpolator = EMPHASIZED
                this.setBubbleBarPivotDuringAnim(pivotX, 1f)
            }
        )

        val translationYTarget = if (isStashed) getStashTranslation() else bubbleBarTranslationY
        animatorSet.play(
            bubbleBarTranslationYAnimator.animateToValue(translationYTarget).apply {
                this.duration = duration
                this.interpolator = EMPHASIZED
            }
        )

        animatorSet.doOnStart {
            // Update the start value for bubble view and background alpha when the entire animation
            // begins.
            // Alpha animation has a delay, and if we set the initial values at the start of the
            // alpha animation, it will cause flickers.
            bubbleBarBubbleAlpha.updateValue(getBarAlphaStart(isStashed))
            bubbleBarBackgroundAlpha.updateValue(getBarAlphaStart(isStashed))
            // We animate alpha for background and bubble views separately. Make sure the container
            // is always visible.
            bubbleBarAlpha.value = 1f
        }
        animatorSet.doOnEnd {
            cancelAnimation()
            controllersAfterInitAction.runAfterInit {
                if (isStashed) {
                    bubbleBarAlpha.value = 0f
                    // reset bubble view alpha
                    bubbleBarBubbleAlpha.updateValue(1f)
                    bubbleBarBackgroundAlpha.updateValue(1f)
                    // reset stash translation
                    translationYDuringStash.updateValue(0f)
                    bubbleBarBubbleTranslationY.updateValue(0f)
                    bubbleBarViewController.animateExpanded(false)
                }
                taskbarInsetsController.onTaskbarOrBubblebarWindowHeightOrInsetsChanged()
            }
        }
        return animatorSet
    }

    private fun createBackgroundAlphaAnimator(isStashed: Boolean): AnimatorSet {
        return AnimatorSet().apply {
            play(
                bubbleBarBackgroundAlpha.animateToValue(
                    getBarAlphaStart(isStashed),
                    getBarAlphaEnd(isStashed),
                )
            )
            play(stashHandleViewAlpha?.animateToValue(getHandleAlphaEnd(isStashed)))
        }
    }

    private fun getBarAlphaStart(isStashed: Boolean): Float {
        return if (isStashed) 1f else 0f
    }

    private fun getBarAlphaEnd(isStashed: Boolean): Float {
        return if (isStashed) 0f else 1f
    }

    private fun getHandleAlphaEnd(isStashed: Boolean): Float {
        return if (isStashed) 1f else 0f
    }

    private fun createSpringOnStashAnimator(isStashed: Boolean): Animator {
        if (!isStashed) {
            // Animate the stash translation back to 0
            return translationYDuringStash.animateToValue(0f)
        }
        // Apply a spring to the handle
        return SpringAnimationBuilder(context)
            .setStartValue(translationYDuringStash.value)
            .setEndValue(0f)
            .setDampingRatio(SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY)
            .setStiffness(SpringForce.STIFFNESS_LOW)
            .setStartVelocity(stashHandleStashVelocity)
            .build(translationYDuringStash, AnimatedFloat.VALUE)
    }

    private fun createScaleAnimator(isStashed: Boolean): AnimatorSet {
        val scaleXTarget = if (isStashed) getStashScaleX() else 1f
        val scaleYTarget = if (isStashed) getStashScaleY() else 1f
        return AnimatorSet().apply {
            play(bubbleBarBackgroundScaleX.animateToValue(scaleXTarget))
            play(bubbleBarBackgroundScaleY.animateToValue(scaleYTarget))
        }
    }

    private fun onIsStashedChanged() {
        controllersAfterInitAction.runAfterInit {
            taskbarInsetsController.onTaskbarOrBubblebarWindowHeightOrInsetsChanged()
            bubbleStashedHandleViewController?.onIsStashedChanged()
        }
    }

    private fun animateBubbleBarYToHotseat() {
        translateBubbleBarYUpdateTouchRegionOnCompletion(bubbleBarTranslationYForHotseat)
    }

    private fun animateBubbleBarYToTaskbar() {
        translateBubbleBarYUpdateTouchRegionOnCompletion(bubbleBarTranslationYForTaskbar)
    }

    private fun translateBubbleBarYUpdateTouchRegionOnCompletion(toY: Float) {
        bubbleBarViewController.bubbleBarTranslationY
            .animateToValue(toY)
            .updateTouchRegionOnAnimationEnd()
            .setDuration(BAR_TRANSLATION_DURATION)
            .start()
    }

    @VisibleForTesting
    fun updateStashedAndExpandedState(
        stash: Boolean,
        expand: Boolean,
        bubbleBarGesture: Boolean = false,
        updateTouchRegionOnEnd: Boolean = true,
    ) {
        if (bubbleBarViewController.isHiddenForNoBubbles) {
            // If there are no bubbles the bar and handle are invisible, nothing to do here.
            cancelAnimation()
            return
        }
        val isStashed = stash && !isBubblesShowingOnHome && !isBubblesShowingOnOverview
        if (this.isStashed != isStashed) {
            this.isStashed = isStashed

            // notify the view controller that the stash state is about to change so that it can
            // cancel an ongoing animation if there is one.
            bubbleBarViewController.onStashStateChanging()
            cancelAnimation()
            animator =
                createStashAnimator(isStashed, BAR_STASH_DURATION).apply {
                    updateBarVisibility(isStashed)
                    if (updateTouchRegionOnEnd) {
                        updateTouchRegionOnAnimationEnd()
                    }
                    start()
                }
        }
        if (bubbleBarViewController.isExpanded != expand) {
            val maybeShowEdu = expand && bubbleBarGesture
            bubbleBarViewController.animateExpanded(expand, maybeShowEdu)
        }
    }

    private fun cancelAnimation() {
        animator?.cancel()
        animator = null
    }

    override fun getHandleViewAlpha(): MultiPropertyFactory<View>.MultiProperty? =
        // only return handle alpha if the bubble bar is stashed and has bubbles
        if (isStashed && bubbleBarViewController.hasBubbles()) {
            stashHandleViewAlpha
        } else {
            null
        }

    private fun Animator.updateTouchRegionOnAnimationEnd(): Animator {
        doOnEnd { onIsStashedChanged() }
        return this
    }

    private fun <T : Animator> T.updateBarVisibility(stashed: Boolean): T {
        if (stashed) {
            doOnEnd { bubbleBarViewController.setHiddenForStashed(true) }
        } else {
            doOnStart { bubbleBarViewController.setHiddenForStashed(false) }
        }
        return this
    }

    // TODO(b/399678274) add tests
    private fun createHandleInAnimator(location: BubbleBarLocation): Animator? {
        val stashHandleViewController = bubbleStashedHandleViewController ?: return null
        val handleAlpha = stashHandleViewController.stashedHandleAlpha.get(0)
        val shift = context.inShiftX
        val startX = if (location.isOnLeft(context.isRtl)) shift else -shift
        val handleTranslationX =
            ValueAnimator.ofFloat(startX, 0f).apply {
                addUpdateListener { v ->
                    stashHandleViewController.setTranslationX(v.animatedValue as Float)
                }
                duration = FADE_IN_ANIM_ALPHA_DURATION_MS
            }
        val handleAlphaAnimation =
            handleAlpha.animateToValue(1f).apply { duration = FADE_IN_ANIM_ALPHA_DURATION_MS }
        return AnimatorSet().apply {
            playTogether(handleTranslationX, handleAlphaAnimation)
            doOnStart { stashBubbleBarImmediateVisually() }
        }
    }

    private fun createHandleOutAnimator(location: BubbleBarLocation): Animator? {
        val stashHandleViewController = bubbleStashedHandleViewController ?: return null
        val handleAlpha = stashHandleViewController.stashedHandleAlpha.get(0)
        val shift = context.outShift
        val endX = if (location.isOnLeft(context.isRtl)) -shift else shift
        val handleTranslationX =
            ValueAnimator.ofFloat(0f, endX).apply {
                addUpdateListener { v ->
                    stashHandleViewController.setTranslationX(v.animatedValue as Float)
                }
                duration = FADE_OUT_ANIM_POSITION_DURATION_MS
                // in case item dropped to the opposite side - need to clear translation
                doOnEnd { stashHandleViewController.setTranslationX(0f) }
            }
        val handleAlphaAnimation =
            handleAlpha.animateToValue(0f).apply {
                duration = FADE_OUT_ANIM_ALPHA_DURATION_MS
                startDelay = FADE_OUT_ANIM_ALPHA_DELAY_MS
            }
        return AnimatorSet().apply { playTogether(handleTranslationX, handleAlphaAnimation) }
    }

    private fun Animator.setBubbleBarPivotDuringAnim(pivotX: Float, pivotY: Float): Animator {
        var initialPivotX = Float.NaN
        var initialPivotY = Float.NaN
        doOnStart {
            initialPivotX = bubbleBarViewController.bubbleBarRelativePivotX
            initialPivotY = bubbleBarViewController.bubbleBarRelativePivotY
            bubbleBarViewController.setBubbleBarRelativePivot(pivotX, pivotY)
        }
        doOnEnd {
            if (!initialPivotX.isNaN() && !initialPivotY.isNaN()) {
                bubbleBarViewController.setBubbleBarRelativePivot(initialPivotX, initialPivotY)
            }
        }
        return this
    }

    private fun BubbleBarLocation.isSameSideWith(anotherLocation: BubbleBarLocation): Boolean {
        val isRtl = context.isRtl
        return this.isOnLeft(isRtl) == anotherLocation.isOnLeft(isRtl)
    }
}
