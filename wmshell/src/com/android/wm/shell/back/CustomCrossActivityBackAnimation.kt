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
package com.android.wm.shell.back

import android.content.Context
import android.graphics.Rect
import android.graphics.RectF
import android.os.Handler
import android.util.MathUtils
import android.view.SurfaceControl
import android.view.animation.Animation
import android.view.animation.Transformation
import android.window.BackEvent
import android.window.BackMotionEvent
import android.window.BackNavigationInfo
import com.android.internal.R
import com.android.internal.policy.TransitionAnimation
import com.android.internal.protolog.ProtoLog
import com.android.wm.shell.RootTaskDisplayAreaOrganizer
import com.android.wm.shell.protolog.ShellProtoLogGroup
import com.android.wm.shell.shared.annotations.ShellMainThread
import javax.inject.Inject
import kotlin.math.max
import kotlin.math.min

/** Class that handles customized predictive cross activity back animations. */
class CustomCrossActivityBackAnimation(
    context: Context,
    background: BackAnimationBackground,
    rootTaskDisplayAreaOrganizer: RootTaskDisplayAreaOrganizer,
    transaction: SurfaceControl.Transaction,
    private val customAnimationLoader: CustomAnimationLoader,
    @ShellMainThread handler: Handler,
) :
    CrossActivityBackAnimation(
        context,
        background,
        rootTaskDisplayAreaOrganizer,
        transaction,
        handler
    ) {

    private var enterAnimation: Animation? = null
    private var closeAnimation: Animation? = null
    private val transformation = Transformation()

    override val allowEnteringYShift = false

    @Inject
    constructor(
        context: Context,
        background: BackAnimationBackground,
        rootTaskDisplayAreaOrganizer: RootTaskDisplayAreaOrganizer,
        @ShellMainThread handler: Handler,
    ) : this(
        context,
        background,
        rootTaskDisplayAreaOrganizer,
        SurfaceControl.Transaction(),
        CustomAnimationLoader(
            TransitionAnimation(context, false /* debug */, "CustomCrossActivityBackAnimation")
        ),
        handler,
    )

    override fun preparePreCommitClosingRectMovement(swipeEdge: Int) {
        startClosingRect.set(backAnimRect)

        // scale closing target to the left for right-hand-swipe and to the right for
        // left-hand-swipe
        targetClosingRect.set(startClosingRect)
        targetClosingRect.scaleCentered(MAX_SCALE)
        val offset = if (swipeEdge != BackEvent.EDGE_RIGHT) {
            startClosingRect.right - targetClosingRect.right - displayBoundsMargin
        } else {
            -targetClosingRect.left + displayBoundsMargin
        }
        targetClosingRect.offset(offset, 0f)
    }

    override fun preparePreCommitEnteringRectMovement() {
        // No movement for the entering rect
        startEnteringRect.set(startClosingRect)
        targetEnteringRect.set(startClosingRect)
    }

    override fun getPostCommitAnimationDuration(): Long {
        return min(
            MAX_POST_COMMIT_ANIM_DURATION, max(closeAnimation!!.duration, enterAnimation!!.duration)
        )
    }

    override fun getPreCommitEnteringBaseTransformation(progress: Float): Transformation {
        transformation.clear()
        enterAnimation!!.getTransformationAt(progress * PRE_COMMIT_MAX_PROGRESS, transformation)
        return transformation
    }

    override fun startBackAnimation(backMotionEvent: BackMotionEvent) {
        super.startBackAnimation(backMotionEvent)
        if (
            closeAnimation == null ||
            enterAnimation == null ||
            closingTarget == null ||
            enteringTarget == null
        ) {
            ProtoLog.d(
                ShellProtoLogGroup.WM_SHELL_BACK_PREVIEW,
                "Enter animation or close animation is null."
            )
            return
        }
        initializeAnimation(closeAnimation!!, closingTarget!!.localBounds)
        initializeAnimation(enterAnimation!!, enteringTarget!!.localBounds)
    }

    override fun onPostCommitProgress(linearProgress: Float) {
        super.onPostCommitProgress(linearProgress)
        if (closingTarget == null || enteringTarget == null) return

        val closingProgress = closeAnimation!!.getPostCommitProgress(linearProgress)
        applyTransform(
            closingTarget!!.leash,
            currentClosingRect,
            closingProgress,
            closeAnimation!!,
            FlingMode.FLING_SHRINK
        )
        val enteringProgress = MathUtils.lerp(
            gestureProgress * PRE_COMMIT_MAX_PROGRESS,
            1f,
            enterAnimation!!.getPostCommitProgress(linearProgress)
        )
        applyTransform(
            enteringTarget!!.leash,
            currentEnteringRect,
            enteringProgress,
            enterAnimation!!,
            FlingMode.NO_FLING
        )
        applyTransaction()
    }

    private fun applyTransform(
        leash: SurfaceControl,
        rect: RectF,
        progress: Float,
        animation: Animation,
        flingMode: FlingMode
    ) {
        transformation.clear()
        animation.getTransformationAt(progress, transformation)
        applyTransform(leash, rect, transformation.alpha, transformation, flingMode)
    }

    override fun finishAnimation() {
        closeAnimation?.reset()
        closeAnimation = null
        enterAnimation?.reset()
        enterAnimation = null
        transformation.clear()
        super.finishAnimation()
    }

    /** Load customize animation before animation start. */
    override fun prepareNextAnimation(
        animationInfo: BackNavigationInfo.CustomAnimationInfo?,
        letterboxColor: Int
    ): Boolean {
        super.prepareNextAnimation(animationInfo, letterboxColor)
        if (animationInfo == null) return false
        customAnimationLoader.loadAll(animationInfo)?.let { result ->
            closeAnimation = result.closeAnimation
            enterAnimation = result.enterAnimation
            customizedBackgroundColor = result.backgroundColor
            return true
        }
        return false
    }

    private fun Animation.getPostCommitProgress(linearProgress: Float): Float {
        return when (duration) {
            0L -> 1f
            else -> min(
                1f,
                getPostCommitAnimationDuration() / min(
                    MAX_POST_COMMIT_ANIM_DURATION,
                    duration
                ).toFloat() * linearProgress
            )
        }
    }

    class AnimationLoadResult {
        var closeAnimation: Animation? = null
        var enterAnimation: Animation? = null
        var backgroundColor = 0
    }

    companion object {
        private const val PRE_COMMIT_MAX_PROGRESS = 0.2f
        private const val MAX_POST_COMMIT_ANIM_DURATION = 2000L
    }
}

/** Helper class to load custom animation. */
class CustomAnimationLoader(private val transitionAnimation: TransitionAnimation) {

    /**
     * Load both enter and exit animation for the close activity transition. Note that the result is
     * only valid if the exit animation has set and loaded success. If the entering animation has
     * not set(i.e. 0), here will load the default entering animation for it.
     *
     * @param animationInfo The information of customize animation, which can be set from
     *   [Activity.overrideActivityTransition] and/or [LayoutParams.windowAnimations]
     */
    fun loadAll(
        animationInfo: BackNavigationInfo.CustomAnimationInfo
    ): CustomCrossActivityBackAnimation.AnimationLoadResult? {
        if (animationInfo.packageName.isEmpty()) return null
        val close = loadAnimation(animationInfo, false) ?: return null
        val open = loadAnimation(animationInfo, true)
        val result = CustomCrossActivityBackAnimation.AnimationLoadResult()
        result.closeAnimation = close
        result.enterAnimation = open
        result.backgroundColor = animationInfo.customBackground
        return result
    }

    /**
     * Load enter or exit animation from CustomAnimationInfo
     *
     * @param animationInfo The information for customize animation.
     * @param enterAnimation true when load for enter animation, false for exit animation.
     * @return Loaded animation.
     */
    fun loadAnimation(
        animationInfo: BackNavigationInfo.CustomAnimationInfo,
        enterAnimation: Boolean
    ): Animation? {
        var a: Animation? = null
        // Activity#overrideActivityTransition has higher priority than windowAnimations
        // Try to get animation from Activity#overrideActivityTransition
        if (
            enterAnimation && animationInfo.customEnterAnim != 0 ||
            !enterAnimation && animationInfo.customExitAnim != 0
        ) {
            a =
                transitionAnimation.loadAppTransitionAnimation(
                    animationInfo.packageName,
                    if (enterAnimation) animationInfo.customEnterAnim
                    else animationInfo.customExitAnim
                )
        } else if (animationInfo.windowAnimations != 0) {
            // try to get animation from LayoutParams#windowAnimations
            a =
                transitionAnimation.loadAnimationAttr(
                    animationInfo.packageName,
                    animationInfo.windowAnimations,
                    if (enterAnimation) R.styleable.WindowAnimation_activityCloseEnterAnimation
                    else R.styleable.WindowAnimation_activityCloseExitAnimation,
                    false /* translucent */
                )
        }
        // Only allow to load default animation for opening target.
        if (a == null && enterAnimation) {
            a = loadDefaultOpenAnimation()
        }
        if (a != null) {
            ProtoLog.d(ShellProtoLogGroup.WM_SHELL_BACK_PREVIEW, "custom animation loaded %s", a)
        } else {
            ProtoLog.e(ShellProtoLogGroup.WM_SHELL_BACK_PREVIEW, "No custom animation loaded")
        }
        return a
    }

    private fun loadDefaultOpenAnimation(): Animation? {
        return transitionAnimation.loadDefaultAnimationAttr(
            R.styleable.WindowAnimation_activityCloseEnterAnimation,
            false /* translucent */
        )
    }
}

private fun initializeAnimation(animation: Animation, bounds: Rect) {
    val width = bounds.width()
    val height = bounds.height()
    animation.initialize(width, height, width, height)
}
