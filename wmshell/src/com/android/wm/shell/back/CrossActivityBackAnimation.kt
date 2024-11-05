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

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.RectF
import android.os.RemoteException
import android.util.TimeUtils
import android.view.Choreographer
import android.view.Display
import android.view.IRemoteAnimationFinishedCallback
import android.view.IRemoteAnimationRunner
import android.view.RemoteAnimationTarget
import android.view.SurfaceControl
import android.view.animation.DecelerateInterpolator
import android.view.animation.Interpolator
import android.view.animation.Transformation
import android.window.BackEvent
import android.window.BackMotionEvent
import android.window.BackNavigationInfo
import android.window.BackProgressAnimator
import android.window.IOnBackInvokedCallback
import com.android.internal.dynamicanimation.animation.FloatValueHolder
import com.android.internal.dynamicanimation.animation.SpringAnimation
import com.android.internal.dynamicanimation.animation.SpringForce
import com.android.internal.jank.Cuj
import com.android.internal.policy.ScreenDecorationsUtils
import com.android.internal.policy.SystemBarUtils
import com.android.internal.protolog.common.ProtoLog
import com.android.wm.shell.R
import com.android.wm.shell.RootTaskDisplayAreaOrganizer
import com.android.wm.shell.animation.Interpolators
import com.android.wm.shell.protolog.ShellProtoLogGroup
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

abstract class CrossActivityBackAnimation(
    private val context: Context,
    private val background: BackAnimationBackground,
    private val rootTaskDisplayAreaOrganizer: RootTaskDisplayAreaOrganizer,
    protected val transaction: SurfaceControl.Transaction
) : ShellBackAnimation() {

    protected val startClosingRect = RectF()
    protected val targetClosingRect = RectF()
    protected val currentClosingRect = RectF()

    protected val startEnteringRect = RectF()
    protected val targetEnteringRect = RectF()
    protected val currentEnteringRect = RectF()

    protected val backAnimRect = Rect()
    private val cropRect = Rect()
    private val tempRectF = RectF()

    private var cornerRadius = ScreenDecorationsUtils.getWindowCornerRadius(context)
    private var statusbarHeight = SystemBarUtils.getStatusBarHeight(context)

    private val backAnimationRunner =
        BackAnimationRunner(Callback(), Runner(), context, Cuj.CUJ_PREDICTIVE_BACK_CROSS_ACTIVITY)
    private val initialTouchPos = PointF()
    private val transformMatrix = Matrix()
    private val tmpFloat9 = FloatArray(9)
    protected var enteringTarget: RemoteAnimationTarget? = null
    protected var closingTarget: RemoteAnimationTarget? = null
    private var triggerBack = false
    private var finishCallback: IRemoteAnimationFinishedCallback? = null
    private val progressAnimator = BackProgressAnimator()
    protected val displayBoundsMargin =
        context.resources.getDimension(R.dimen.cross_task_back_vertical_margin)

    private val gestureInterpolator = Interpolators.BACK_GESTURE
    private val verticalMoveInterpolator: Interpolator = DecelerateInterpolator()

    private var scrimLayer: SurfaceControl? = null
    private var maxScrimAlpha: Float = 0f

    private var isLetterboxed = false
    private var enteringHasSameLetterbox = false
    private var leftLetterboxLayer: SurfaceControl? = null
    private var rightLetterboxLayer: SurfaceControl? = null
    private var letterboxColor: Int = 0

    private val postCommitFlingScale = FloatValueHolder(SPRING_SCALE)
    private var lastPostCommitFlingScale = SPRING_SCALE
    private val postCommitFlingSpring = SpringForce(SPRING_SCALE)
            .setStiffness(SpringForce.STIFFNESS_LOW)
            .setDampingRatio(SpringForce.DAMPING_RATIO_LOW_BOUNCY)
    protected var gestureProgress = 0f

    /** Background color to be used during the animation, also see [getBackgroundColor] */
    protected var customizedBackgroundColor = 0

    /**
     * Whether the entering target should be shifted vertically with the user gesture in pre-commit
     */
    abstract val allowEnteringYShift: Boolean

    /**
     * Subclasses must set the [startClosingRect] and [targetClosingRect] to define the movement
     * of the closingTarget during pre-commit phase.
     */
    abstract fun preparePreCommitClosingRectMovement(@BackEvent.SwipeEdge swipeEdge: Int)

    /**
     * Subclasses must set the [startEnteringRect] and [targetEnteringRect] to define the movement
     * of the enteringTarget during pre-commit phase.
     */
    abstract fun preparePreCommitEnteringRectMovement()

    /**
     * Subclasses must provide a duration (in ms) for the post-commit part of the animation
     */
    abstract fun getPostCommitAnimationDuration(): Long

    /**
     * Returns a base transformation to apply to the entering target during pre-commit. The system
     * will apply the default animation on top of it.
     */
    protected open fun getPreCommitEnteringBaseTransformation(progress: Float): Transformation? =
        null

    override fun onConfigurationChanged(newConfiguration: Configuration) {
        cornerRadius = ScreenDecorationsUtils.getWindowCornerRadius(context)
        statusbarHeight = SystemBarUtils.getStatusBarHeight(context)
    }

    override fun getRunner() = backAnimationRunner

    private fun getBackgroundColor(): Int =
        when {
            customizedBackgroundColor != 0 -> customizedBackgroundColor
            isLetterboxed -> letterboxColor
            enteringTarget != null -> enteringTarget!!.taskInfo.taskDescription!!.backgroundColor
            else -> 0
        }

    protected open fun startBackAnimation(backMotionEvent: BackMotionEvent) {
        if (enteringTarget == null || closingTarget == null) {
            ProtoLog.d(
                ShellProtoLogGroup.WM_SHELL_BACK_PREVIEW,
                "Entering target or closing target is null."
            )
            return
        }
        triggerBack = backMotionEvent.triggerBack
        initialTouchPos.set(backMotionEvent.touchX, backMotionEvent.touchY)

        transaction.setAnimationTransaction()
        isLetterboxed = closingTarget!!.taskInfo.appCompatTaskInfo.topActivityBoundsLetterboxed
        enteringHasSameLetterbox =
            isLetterboxed && closingTarget!!.localBounds.equals(enteringTarget!!.localBounds)

        if (isLetterboxed && !enteringHasSameLetterbox) {
            // Play animation with letterboxes, if closing and entering target have mismatching
            // letterboxes
            backAnimRect.set(closingTarget!!.windowConfiguration.bounds)
        } else {
            // otherwise play animation on localBounds only
            backAnimRect.set(closingTarget!!.localBounds)
        }
        // Offset start rectangle to align task bounds.
        backAnimRect.offsetTo(0, 0)

        preparePreCommitClosingRectMovement(backMotionEvent.swipeEdge)
        preparePreCommitEnteringRectMovement()

        background.ensureBackground(
            closingTarget!!.windowConfiguration.bounds,
            getBackgroundColor(),
            transaction,
            statusbarHeight
        )
        ensureScrimLayer()
        if (isLetterboxed && enteringHasSameLetterbox) {
            // crop left and right letterboxes
            cropRect.set(
                closingTarget!!.localBounds.left,
                0,
                closingTarget!!.localBounds.right,
                closingTarget!!.windowConfiguration.bounds.height()
            )
            // and add fake letterbox square surfaces instead
            ensureLetterboxes()
        } else {
            cropRect.set(backAnimRect)
        }
        applyTransaction()
    }

    private fun onGestureProgress(backEvent: BackEvent) {
        val progress = gestureInterpolator.getInterpolation(backEvent.progress)
        gestureProgress = progress
        currentClosingRect.setInterpolatedRectF(startClosingRect, targetClosingRect, progress)
        val yOffset = getYOffset(currentClosingRect, backEvent.touchY)
        currentClosingRect.offset(0f, yOffset)
        applyTransform(closingTarget?.leash, currentClosingRect, 1f)
        currentEnteringRect.setInterpolatedRectF(startEnteringRect, targetEnteringRect, progress)
        if (allowEnteringYShift) currentEnteringRect.offset(0f, yOffset)
        val enteringTransformation = getPreCommitEnteringBaseTransformation(progress)
        applyTransform(
            enteringTarget?.leash,
            currentEnteringRect,
            enteringTransformation?.alpha ?: 1f,
            enteringTransformation
        )
        applyTransaction()
        background.customizeStatusBarAppearance(currentClosingRect.top.toInt())
    }

    private fun getYOffset(centeredRect: RectF, touchY: Float): Float {
        val screenHeight = backAnimRect.height()
        // Base the window movement in the Y axis on the touch movement in the Y axis.
        val rawYDelta = touchY - initialTouchPos.y
        val yDirection = (if (rawYDelta < 0) -1 else 1)
        // limit yDelta interpretation to 1/2 of screen height in either direction
        val deltaYRatio = min(screenHeight / 2f, abs(rawYDelta)) / (screenHeight / 2f)
        val interpolatedYRatio: Float = verticalMoveInterpolator.getInterpolation(deltaYRatio)
        // limit y-shift so surface never passes 8dp screen margin
        val deltaY =
            max(0f, (screenHeight - centeredRect.height()) / 2f - displayBoundsMargin) *
                interpolatedYRatio *
                yDirection
        return deltaY
    }

    protected open fun onGestureCommitted(velocity: Float) {
        if (
            closingTarget?.leash == null ||
                enteringTarget?.leash == null ||
                !enteringTarget!!.leash.isValid ||
                !closingTarget!!.leash.isValid
        ) {
            finishAnimation()
            return
        }

        // kick off spring animation with the current velocity from the pre-commit phase, this
        // affects the scaling of the closing and/or opening activity during post-commit
        val startVelocity =
            if (gestureProgress < 0.1f) -DEFAULT_FLING_VELOCITY else -velocity * SPRING_SCALE
        val flingAnimation = SpringAnimation(postCommitFlingScale, SPRING_SCALE)
            .setStartVelocity(startVelocity.coerceIn(-MAX_FLING_VELOCITY, 0f))
            .setStartValue(SPRING_SCALE)
            .setSpring(postCommitFlingSpring)
        flingAnimation.start()
        // do an animation-frame immediately to prevent idle frame
        flingAnimation.doAnimationFrame(
            Choreographer.getInstance().lastFrameTimeNanos / TimeUtils.NANOS_PER_MS
        )

        val valueAnimator =
            ValueAnimator.ofFloat(1f, 0f).setDuration(getPostCommitAnimationDuration())
        valueAnimator.addUpdateListener { animation: ValueAnimator ->
            val progress = animation.animatedFraction
            onPostCommitProgress(progress)
            if (progress > 1 - BackAnimationConstants.UPDATE_SYSUI_FLAGS_THRESHOLD) {
                background.resetStatusBarCustomization()
            }
        }
        valueAnimator.addListener(
            object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    background.resetStatusBarCustomization()
                    finishAnimation()
                }
            }
        )
        valueAnimator.start()
    }

    protected open fun onPostCommitProgress(linearProgress: Float) {
        scrimLayer?.let { transaction.setAlpha(it, maxScrimAlpha * (1f - linearProgress)) }
    }

    protected open fun finishAnimation() {
        enteringTarget?.let {
            if (it.leash != null && it.leash.isValid) {
                transaction.setCornerRadius(it.leash, 0f)
                if (!triggerBack) transaction.setAlpha(it.leash, 0f)
                it.leash.release()
            }
            enteringTarget = null
        }

        closingTarget?.leash?.release()
        closingTarget = null

        background.removeBackground(transaction)
        applyTransaction()
        transformMatrix.reset()
        initialTouchPos.set(0f, 0f)
        try {
            finishCallback?.onAnimationFinished()
        } catch (e: RemoteException) {
            e.printStackTrace()
        }
        finishCallback = null
        removeScrimLayer()
        removeLetterbox()
        isLetterboxed = false
        enteringHasSameLetterbox = false
        lastPostCommitFlingScale = SPRING_SCALE
        gestureProgress = 0f
        triggerBack = false
    }

    protected fun applyTransform(
        leash: SurfaceControl?,
        rect: RectF,
        alpha: Float,
        baseTransformation: Transformation? = null,
        flingMode: FlingMode = FlingMode.NO_FLING
    ) {
        if (leash == null || !leash.isValid) return
        tempRectF.set(rect)
        if (flingMode != FlingMode.NO_FLING) {
            lastPostCommitFlingScale = min(
                postCommitFlingScale.value / SPRING_SCALE,
                if (flingMode == FlingMode.FLING_BOUNCE) 1f else lastPostCommitFlingScale
            )
            // apply an additional scale to the closing target to account for fling velocity
            tempRectF.scaleCentered(lastPostCommitFlingScale)
        }
        val scale = tempRectF.width() / backAnimRect.width()
        val matrix = baseTransformation?.matrix ?: transformMatrix.apply { reset() }
        val scalePivotX =
            if (isLetterboxed && enteringHasSameLetterbox) {
                closingTarget!!.localBounds.left.toFloat()
            } else {
                0f
            }
        matrix.postScale(scale, scale, scalePivotX, 0f)
        matrix.postTranslate(tempRectF.left, tempRectF.top)
        transaction
            .setAlpha(leash, alpha)
            .setMatrix(leash, matrix, tmpFloat9)
            .setCrop(leash, cropRect)
            .setCornerRadius(leash, cornerRadius)
    }

    protected fun applyTransaction() {
        transaction.setFrameTimelineVsync(Choreographer.getInstance().vsyncId)
        transaction.apply()
    }

    private fun ensureScrimLayer() {
        if (scrimLayer != null) return
        val isDarkTheme: Boolean = isDarkMode(context)
        val scrimBuilder =
            SurfaceControl.Builder()
                .setName("Cross-Activity back animation scrim")
                .setCallsite("CrossActivityBackAnimation")
                .setColorLayer()
                .setOpaque(false)
                .setHidden(false)

        rootTaskDisplayAreaOrganizer.attachToDisplayArea(Display.DEFAULT_DISPLAY, scrimBuilder)
        scrimLayer = scrimBuilder.build()
        val colorComponents = floatArrayOf(0f, 0f, 0f)
        maxScrimAlpha = if (isDarkTheme) MAX_SCRIM_ALPHA_DARK else MAX_SCRIM_ALPHA_LIGHT
        val scrimCrop =
            if (isLetterboxed) {
                closingTarget!!.windowConfiguration.bounds
            } else {
                closingTarget!!.localBounds
            }
        transaction
            .setColor(scrimLayer, colorComponents)
            .setAlpha(scrimLayer!!, maxScrimAlpha)
            .setCrop(scrimLayer!!, scrimCrop)
            .setRelativeLayer(scrimLayer!!, closingTarget!!.leash, -1)
            .show(scrimLayer)
    }

    private fun removeScrimLayer() {
        if (removeLayer(scrimLayer)) applyTransaction()
        scrimLayer = null
    }

    /**
     * Adds two "fake" letterbox square surfaces to the left and right of the localBounds of the
     * closing target
     */
    private fun ensureLetterboxes() {
        closingTarget?.let { t ->
            if (t.localBounds.left != 0 && leftLetterboxLayer == null) {
                val bounds =
                    Rect(
                        0,
                        t.windowConfiguration.bounds.top,
                        t.localBounds.left,
                        t.windowConfiguration.bounds.bottom
                    )
                leftLetterboxLayer = ensureLetterbox(bounds)
            }
            if (
                t.localBounds.right != t.windowConfiguration.bounds.right &&
                    rightLetterboxLayer == null
            ) {
                val bounds =
                    Rect(
                        t.localBounds.right,
                        t.windowConfiguration.bounds.top,
                        t.windowConfiguration.bounds.right,
                        t.windowConfiguration.bounds.bottom
                    )
                rightLetterboxLayer = ensureLetterbox(bounds)
            }
        }
    }

    private fun ensureLetterbox(bounds: Rect): SurfaceControl {
        val letterboxBuilder =
            SurfaceControl.Builder()
                .setName("Cross-Activity back animation letterbox")
                .setCallsite("CrossActivityBackAnimation")
                .setColorLayer()
                .setOpaque(true)
                .setHidden(false)

        rootTaskDisplayAreaOrganizer.attachToDisplayArea(Display.DEFAULT_DISPLAY, letterboxBuilder)
        val layer = letterboxBuilder.build()
        val colorComponents =
            floatArrayOf(
                Color.red(letterboxColor) / 255f,
                Color.green(letterboxColor) / 255f,
                Color.blue(letterboxColor) / 255f
            )
        transaction
            .setColor(layer, colorComponents)
            .setCrop(layer, bounds)
            .setRelativeLayer(layer, closingTarget!!.leash, 1)
            .show(layer)
        return layer
    }

    private fun removeLetterbox() {
        if (removeLayer(leftLetterboxLayer) || removeLayer(rightLetterboxLayer)) applyTransaction()
        leftLetterboxLayer = null
        rightLetterboxLayer = null
    }

    private fun removeLayer(layer: SurfaceControl?): Boolean {
        layer?.let {
            if (it.isValid) {
                transaction.remove(it)
                return true
            }
        }
        return false
    }

    override fun prepareNextAnimation(
        animationInfo: BackNavigationInfo.CustomAnimationInfo?,
        letterboxColor: Int
    ): Boolean {
        this.letterboxColor = letterboxColor
        return false
    }

    private inner class Callback : IOnBackInvokedCallback.Default() {
        override fun onBackStarted(backMotionEvent: BackMotionEvent) {
            // in case we're still animating an onBackCancelled event, let's remove the finish-
            // callback from the progress animator to prevent calling finishAnimation() before
            // restarting a new animation
            progressAnimator.removeOnBackCancelledFinishCallback()

            startBackAnimation(backMotionEvent)
            progressAnimator.onBackStarted(backMotionEvent) { backEvent: BackEvent ->
                onGestureProgress(backEvent)
            }
        }

        override fun onBackProgressed(backEvent: BackMotionEvent) {
            triggerBack = backEvent.triggerBack
            progressAnimator.onBackProgressed(backEvent)
        }

        override fun onBackCancelled() {
            triggerBack = false
            progressAnimator.onBackCancelled { finishAnimation() }
        }

        override fun onBackInvoked() {
            triggerBack = true
            progressAnimator.reset()
            onGestureCommitted(progressAnimator.velocity)
        }
    }

    private inner class Runner : IRemoteAnimationRunner.Default() {
        override fun onAnimationStart(
            transit: Int,
            apps: Array<RemoteAnimationTarget>,
            wallpapers: Array<RemoteAnimationTarget>?,
            nonApps: Array<RemoteAnimationTarget>?,
            finishedCallback: IRemoteAnimationFinishedCallback
        ) {
            ProtoLog.d(
                ShellProtoLogGroup.WM_SHELL_BACK_PREVIEW,
                "Start back to activity animation."
            )
            for (a in apps) {
                when (a.mode) {
                    RemoteAnimationTarget.MODE_CLOSING -> closingTarget = a
                    RemoteAnimationTarget.MODE_OPENING -> enteringTarget = a
                }
            }
            finishCallback = finishedCallback
        }

        override fun onAnimationCancelled() {
            finishAnimation()
        }
    }

    companion object {
        /** Max scale of the closing window. */
        internal const val MAX_SCALE = 0.9f
        private const val MAX_SCRIM_ALPHA_DARK = 0.8f
        private const val MAX_SCRIM_ALPHA_LIGHT = 0.2f
        private const val SPRING_SCALE = 100f
        private const val MAX_FLING_VELOCITY = 1000f
        private const val DEFAULT_FLING_VELOCITY = 120f
    }

    enum class FlingMode {
        NO_FLING,

        /**
         * This is used for the closing target in custom cross-activity back animations. When the
         * back gesture is flung, the closing target shrinks a bit further with a spring motion.
         */
        FLING_SHRINK,

        /**
         * This is used for the closing and opening target in the default cross-activity back
         * animation. When the back gesture is flung, the closing and opening targets shrink a
         * bit further and then bounce back with a spring motion.
         */
        FLING_BOUNCE
    }
}

private fun isDarkMode(context: Context): Boolean {
    return context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
        Configuration.UI_MODE_NIGHT_YES
}

internal fun RectF.setInterpolatedRectF(start: RectF, target: RectF, progress: Float) {
    require(!(progress < 0 || progress > 1)) { "Progress value must be between 0 and 1" }
    left = start.left + (target.left - start.left) * progress
    top = start.top + (target.top - start.top) * progress
    right = start.right + (target.right - start.right) * progress
    bottom = start.bottom + (target.bottom - start.bottom) * progress
}

internal fun RectF.scaleCentered(
    scale: Float,
    pivotX: Float = left + width() / 2,
    pivotY: Float = top + height() / 2
) {
    offset(-pivotX, -pivotY) // move pivot to origin
    scale(scale)
    offset(pivotX, pivotY) // Move back to the original position
}
