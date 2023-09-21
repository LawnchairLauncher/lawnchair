/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.systemui.animation

import android.app.ActivityManager
import android.app.ActivityTaskManager
import android.app.PendingIntent
import android.app.TaskInfo
import android.graphics.Matrix
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.os.Looper
import android.os.RemoteException
import android.util.Log
import android.view.IRemoteAnimationFinishedCallback
import android.view.IRemoteAnimationRunner
import android.view.RemoteAnimationAdapter
import android.view.RemoteAnimationTarget
import android.view.SyncRtSurfaceTransactionApplier
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.Interpolator
import android.view.animation.PathInterpolator
import androidx.annotation.BinderThread
import androidx.annotation.UiThread
import com.android.internal.annotations.VisibleForTesting
import com.android.internal.policy.ScreenDecorationsUtils
import java.lang.IllegalArgumentException
import kotlin.math.roundToInt

private const val TAG = "ActivityLaunchAnimator"

/**
 * A class that allows activities to be started in a seamless way from a view that is transforming
 * nicely into the starting window.
 */
class ActivityLaunchAnimator(
    /** The animator used when animating a View into an app. */
    private val launchAnimator: LaunchAnimator = DEFAULT_LAUNCH_ANIMATOR,

    /** The animator used when animating a Dialog into an app. */
    // TODO(b/218989950): Remove this animator and instead set the duration of the dim fade out to
    // TIMINGS.contentBeforeFadeOutDuration.
    private val dialogToAppAnimator: LaunchAnimator = DEFAULT_DIALOG_TO_APP_ANIMATOR
) {
    companion object {
        /** The timings when animating a View into an app. */
        @JvmField
        val TIMINGS =
            LaunchAnimator.Timings(
                totalDuration = 500L,
                contentBeforeFadeOutDelay = 0L,
                contentBeforeFadeOutDuration = 150L,
                contentAfterFadeInDelay = 150L,
                contentAfterFadeInDuration = 183L
            )

        /**
         * The timings when animating a Dialog into an app. We need to wait at least 200ms before
         * showing the app (which is under the dialog window) so that the dialog window dim is fully
         * faded out, to avoid flicker.
         */
        val DIALOG_TIMINGS =
            TIMINGS.copy(contentBeforeFadeOutDuration = 200L, contentAfterFadeInDelay = 200L)

        /** The interpolators when animating a View or a dialog into an app. */
        val INTERPOLATORS =
            LaunchAnimator.Interpolators(
                positionInterpolator = Interpolators.EMPHASIZED,
                positionXInterpolator = createPositionXInterpolator(),
                contentBeforeFadeOutInterpolator = Interpolators.LINEAR_OUT_SLOW_IN,
                contentAfterFadeInInterpolator = PathInterpolator(0f, 0f, 0.6f, 1f)
            )

        private val DEFAULT_LAUNCH_ANIMATOR = LaunchAnimator(TIMINGS, INTERPOLATORS)
        private val DEFAULT_DIALOG_TO_APP_ANIMATOR = LaunchAnimator(DIALOG_TIMINGS, INTERPOLATORS)

        /** Durations & interpolators for the navigation bar fading in & out. */
        private const val ANIMATION_DURATION_NAV_FADE_IN = 266L
        private const val ANIMATION_DURATION_NAV_FADE_OUT = 133L
        private val ANIMATION_DELAY_NAV_FADE_IN =
            TIMINGS.totalDuration - ANIMATION_DURATION_NAV_FADE_IN

        private val NAV_FADE_IN_INTERPOLATOR = Interpolators.STANDARD_DECELERATE
        private val NAV_FADE_OUT_INTERPOLATOR = PathInterpolator(0.2f, 0f, 1f, 1f)

        /** The time we wait before timing out the remote animation after starting the intent. */
        private const val LAUNCH_TIMEOUT = 1000L

        private fun createPositionXInterpolator(): Interpolator {
            val path =
                Path().apply {
                    moveTo(0f, 0f)
                    cubicTo(0.1217f, 0.0462f, 0.15f, 0.4686f, 0.1667f, 0.66f)
                    cubicTo(0.1834f, 0.8878f, 0.1667f, 1f, 1f, 1f)
                }
            return PathInterpolator(path)
        }
    }

    /**
     * The callback of this animator. This should be set before any call to
     * [start(Pending)IntentWithAnimation].
     */
    var callback: Callback? = null

    /** The set of [Listener] that should be notified of any animation started by this animator. */
    private val listeners = LinkedHashSet<Listener>()

    /** Top-level listener that can be used to notify all registered [listeners]. */
    private val lifecycleListener =
        object : Listener {
            override fun onLaunchAnimationStart() {
                listeners.forEach { it.onLaunchAnimationStart() }
            }

            override fun onLaunchAnimationEnd() {
                listeners.forEach { it.onLaunchAnimationEnd() }
            }

            override fun onLaunchAnimationProgress(linearProgress: Float) {
                listeners.forEach { it.onLaunchAnimationProgress(linearProgress) }
            }
        }

    /**
     * Start an intent and animate the opening window. The intent will be started by running
     * [intentStarter], which should use the provided [RemoteAnimationAdapter] and return the launch
     * result. [controller] is responsible from animating the view from which the intent was started
     * in [Controller.onLaunchAnimationProgress]. No animation will start if there is no window
     * opening.
     *
     * If [controller] is null or [animate] is false, then the intent will be started and no
     * animation will run.
     *
     * If possible, you should pass the [packageName] of the intent that will be started so that
     * trampoline activity launches will also be animated.
     *
     * If the device is currently locked, the user will have to unlock it before the intent is
     * started unless [showOverLockscreen] is true. In that case, the activity will be started
     * directly over the lockscreen.
     *
     * This method will throw any exception thrown by [intentStarter].
     */
    @JvmOverloads
    fun startIntentWithAnimation(
        controller: Controller?,
        animate: Boolean = true,
        packageName: String? = null,
        showOverLockscreen: Boolean = false,
        intentStarter: (RemoteAnimationAdapter?) -> Int
    ) {
        if (controller == null || !animate) {
            Log.i(TAG, "Starting intent with no animation")
            intentStarter(null)
            controller?.callOnIntentStartedOnMainThread(willAnimate = false)
            return
        }

        val callback =
            this.callback
                ?: throw IllegalStateException(
                    "ActivityLaunchAnimator.callback must be set before using this animator"
                )
        val runner = createRunner(controller)
        val hideKeyguardWithAnimation = callback.isOnKeyguard() && !showOverLockscreen

        // Pass the RemoteAnimationAdapter to the intent starter only if we are not hiding the
        // keyguard with the animation
        val animationAdapter =
            if (!hideKeyguardWithAnimation) {
                RemoteAnimationAdapter(
                    runner,
                    TIMINGS.totalDuration,
                    TIMINGS.totalDuration - 150 /* statusBarTransitionDelay */
                )
            } else {
                null
            }

        // Register the remote animation for the given package to also animate trampoline
        // activity launches.
        if (packageName != null && animationAdapter != null) {
            try {
                ActivityTaskManager.getService()
                    .registerRemoteAnimationForNextActivityStart(
                        packageName,
                        animationAdapter,
                        null /* launchCookie */
                    )
            } catch (e: RemoteException) {
                Log.w(TAG, "Unable to register the remote animation", e)
            }
        }

        val launchResult = intentStarter(animationAdapter)

        // Only animate if the app is not already on top and will be opened, unless we are on the
        // keyguard.
        val willAnimate =
            launchResult == ActivityManager.START_TASK_TO_FRONT ||
                launchResult == ActivityManager.START_SUCCESS ||
                (launchResult == ActivityManager.START_DELIVERED_TO_TOP &&
                    hideKeyguardWithAnimation)

        Log.i(
            TAG,
            "launchResult=$launchResult willAnimate=$willAnimate " +
                "hideKeyguardWithAnimation=$hideKeyguardWithAnimation"
        )
        controller.callOnIntentStartedOnMainThread(willAnimate)

        // If we expect an animation, post a timeout to cancel it in case the remote animation is
        // never started.
        if (willAnimate) {
            runner.delegate.postTimeout()

            // Hide the keyguard using the launch animation instead of the default unlock animation.
            if (hideKeyguardWithAnimation) {
                callback.hideKeyguardWithAnimation(runner)
            }
        }
    }

    private fun Controller.callOnIntentStartedOnMainThread(willAnimate: Boolean) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            this.launchContainer.context.mainExecutor.execute { this.onIntentStarted(willAnimate) }
        } else {
            this.onIntentStarted(willAnimate)
        }
    }

    /**
     * Same as [startIntentWithAnimation] but allows [intentStarter] to throw a
     * [PendingIntent.CanceledException] which must then be handled by the caller. This is useful
     * for Java caller starting a [PendingIntent].
     *
     * If possible, you should pass the [packageName] of the intent that will be started so that
     * trampoline activity launches will also be animated.
     */
    @Throws(PendingIntent.CanceledException::class)
    @JvmOverloads
    fun startPendingIntentWithAnimation(
        controller: Controller?,
        animate: Boolean = true,
        packageName: String? = null,
        intentStarter: PendingIntentStarter
    ) {
        startIntentWithAnimation(controller, animate, packageName) {
            intentStarter.startPendingIntent(it)
        }
    }

    /** Add a [Listener] that can listen to launch animations. */
    fun addListener(listener: Listener) {
        listeners.add(listener)
    }

    /** Remove a [Listener]. */
    fun removeListener(listener: Listener) {
        listeners.remove(listener)
    }

    /** Create a new animation [Runner] controlled by [controller]. */
    @VisibleForTesting
    fun createRunner(controller: Controller): Runner {
        // Make sure we use the modified timings when animating a dialog into an app.
        val launchAnimator =
            if (controller.isDialogLaunch) {
                dialogToAppAnimator
            } else {
                launchAnimator
            }

        return Runner(controller, callback!!, launchAnimator, lifecycleListener)
    }

    interface PendingIntentStarter {
        /**
         * Start a pending intent using the provided [animationAdapter] and return the launch
         * result.
         */
        @Throws(PendingIntent.CanceledException::class)
        fun startPendingIntent(animationAdapter: RemoteAnimationAdapter?): Int
    }

    interface Callback {
        /** Whether we are currently on the keyguard or not. */
        @JvmDefault
        fun isOnKeyguard(): Boolean = false

        /** Hide the keyguard and animate using [runner]. */
        @JvmDefault
        fun hideKeyguardWithAnimation(runner: IRemoteAnimationRunner) {
            throw UnsupportedOperationException()
        }

        /* Get the background color of [task]. */
        fun getBackgroundColor(task: TaskInfo): Int
    }

    interface Listener {
        /** Called when an activity launch animation started. */
        @JvmDefault fun onLaunchAnimationStart() {}

        /**
         * Called when an activity launch animation is finished. This will be called if and only if
         * [onLaunchAnimationStart] was called earlier.
         */
        @JvmDefault fun onLaunchAnimationEnd() {}

        /** Called when an activity launch animation made progress. */
        @JvmDefault fun onLaunchAnimationProgress(linearProgress: Float) {}
    }

    /**
     * A controller that takes care of applying the animation to an expanding view.
     *
     * Note that all callbacks (onXXX methods) are all called on the main thread.
     */
    interface Controller : LaunchAnimator.Controller {
        companion object {
            /**
             * Return a [Controller] that will animate and expand [view] into the opening window.
             *
             * Important: The view must be attached to a [ViewGroup] when calling this function and
             * during the animation. For safety, this method will return null when it is not. The
             * view must also implement [LaunchableView], otherwise this method will throw.
             *
             * Note: The background of [view] should be a (rounded) rectangle so that it can be
             * properly animated.
             */
            @JvmStatic
            fun fromView(view: View, cujType: Int? = null): Controller? {
                // Make sure the View we launch from implements LaunchableView to avoid visibility
                // issues.
                if (view !is LaunchableView) {
                    throw IllegalArgumentException(
                        "An ActivityLaunchAnimator.Controller was created from a View that does " +
                            "not implement LaunchableView. This can lead to subtle bugs where the" +
                            " visibility of the View we are launching from is not what we expected."
                    )
                }

                if (view.parent !is ViewGroup) {
                    Log.e(
                        TAG,
                        "Skipping animation as view $view is not attached to a ViewGroup",
                        Exception()
                    )
                    return null
                }

                return GhostedViewLaunchAnimatorController(view, cujType)
            }
        }

        /**
         * Whether this controller is controlling a dialog launch. This will be used to adapt the
         * timings, making sure we don't show the app until the dialog dim had the time to fade out.
         */
        // TODO(b/218989950): Remove this.
        val isDialogLaunch: Boolean
            get() = false

        /**
         * Whether the expandable controller by this [Controller] is below the launching window that
         * is going to be animated.
         *
         * This should be `false` when launching an app from the shade or status bar, given that
         * they are drawn above all apps. This is usually `true` when using this launcher in a
         * normal app or a launcher, that are drawn below the animating activity/window.
         */
        val isBelowAnimatingWindow: Boolean
            get() = false

        /**
         * The intent was started. If [willAnimate] is false, nothing else will happen and the
         * animation will not be started.
         */
        fun onIntentStarted(willAnimate: Boolean) {}

        /**
         * The animation was cancelled. Note that [onLaunchAnimationEnd] will still be called after
         * this if the animation was already started, i.e. if [onLaunchAnimationStart] was called
         * before the cancellation.
         *
         * If this launch animation affected the occlusion state of the keyguard, WM will provide us
         * with [newKeyguardOccludedState] so that we can set the occluded state appropriately.
         */
        fun onLaunchAnimationCancelled(newKeyguardOccludedState: Boolean? = null) {}
    }

    @VisibleForTesting
    inner class Runner(
        controller: Controller,
        callback: Callback,
        /** The animator to use to animate the window launch. */
        launchAnimator: LaunchAnimator = DEFAULT_LAUNCH_ANIMATOR,
        /** Listener for animation lifecycle events. */
        listener: Listener? = null
    ) : IRemoteAnimationRunner.Stub() {
        private val context = controller.launchContainer.context
        internal val delegate: AnimationDelegate

        init {
            delegate = AnimationDelegate(controller, callback, listener, launchAnimator)
        }

        @BinderThread
        override fun onAnimationStart(
            transit: Int,
            apps: Array<out RemoteAnimationTarget>?,
            wallpapers: Array<out RemoteAnimationTarget>?,
            nonApps: Array<out RemoteAnimationTarget>?,
            finishedCallback: IRemoteAnimationFinishedCallback?
        ) {
            context.mainExecutor.execute {
                delegate.onAnimationStart(transit, apps, wallpapers, nonApps, finishedCallback)
            }
        }

        @BinderThread
        override fun onAnimationCancelled(isKeyguardOccluded: Boolean) {
            context.mainExecutor.execute { delegate.onAnimationCancelled(isKeyguardOccluded) }
        }
    }

    class AnimationDelegate
    @JvmOverloads
    constructor(
        private val controller: Controller,
        private val callback: Callback,
        /** Listener for animation lifecycle events. */
        private val listener: Listener? = null,
        /** The animator to use to animate the window launch. */
        private val launchAnimator: LaunchAnimator = DEFAULT_LAUNCH_ANIMATOR
    ) : RemoteAnimationDelegate<IRemoteAnimationFinishedCallback> {
        private val launchContainer = controller.launchContainer
        private val context = launchContainer.context
        private val transactionApplierView =
            controller.openingWindowSyncView ?: controller.launchContainer
        private val transactionApplier = SyncRtSurfaceTransactionApplier(transactionApplierView)

        private val matrix = Matrix()
        private val invertMatrix = Matrix()
        private var windowCrop = Rect()
        private var windowCropF = RectF()
        private var timedOut = false
        private var cancelled = false
        private var animation: LaunchAnimator.Animation? = null

        // A timeout to cancel the remote animation if it is not started within X milliseconds after
        // the intent was started.
        //
        // Note that this is important to keep this a Runnable (and not a Kotlin lambda), otherwise
        // it will be automatically converted when posted and we wouldn't be able to remove it after
        // posting it.
        private var onTimeout = Runnable { onAnimationTimedOut() }

        @UiThread
        internal fun postTimeout() {
            launchContainer.postDelayed(onTimeout, LAUNCH_TIMEOUT)
        }

        private fun removeTimeout() {
            launchContainer.removeCallbacks(onTimeout)
        }

        @UiThread
        override fun onAnimationStart(
            @WindowManager.TransitionOldType transit: Int,
            apps: Array<out RemoteAnimationTarget>?,
            wallpapers: Array<out RemoteAnimationTarget>?,
            nonApps: Array<out RemoteAnimationTarget>?,
            callback: IRemoteAnimationFinishedCallback?
        ) {
            removeTimeout()

            // The animation was started too late and we already notified the controller that it
            // timed out.
            if (timedOut) {
                callback?.invoke()
                return
            }

            // This should not happen, but let's make sure we don't start the animation if it was
            // cancelled before and we already notified the controller.
            if (cancelled) {
                return
            }

            startAnimation(apps, nonApps, callback)
        }

        private fun startAnimation(
            apps: Array<out RemoteAnimationTarget>?,
            nonApps: Array<out RemoteAnimationTarget>?,
            iCallback: IRemoteAnimationFinishedCallback?
        ) {
            if (LaunchAnimator.DEBUG) {
                Log.d(TAG, "Remote animation started")
            }

            val window = apps?.firstOrNull { it.mode == RemoteAnimationTarget.MODE_OPENING }

            if (window == null) {
                Log.i(TAG, "Aborting the animation as no window is opening")
                removeTimeout()
                iCallback?.invoke()
                controller.onLaunchAnimationCancelled()
                return
            }

            val navigationBar =
                nonApps?.firstOrNull {
                    it.windowType == WindowManager.LayoutParams.TYPE_NAVIGATION_BAR
                }

            val windowBounds = window.screenSpaceBounds
            val endState =
                LaunchAnimator.State(
                    top = windowBounds.top,
                    bottom = windowBounds.bottom,
                    left = windowBounds.left,
                    right = windowBounds.right
                )
            val windowBackgroundColor =
                window.taskInfo?.let { callback.getBackgroundColor(it) } ?: window.backgroundColor

            // TODO(b/184121838): We should somehow get the top and bottom radius of the window
            // instead of recomputing isExpandingFullyAbove here.
            val isExpandingFullyAbove =
                launchAnimator.isExpandingFullyAbove(controller.launchContainer, endState)
            val endRadius =
                if (isExpandingFullyAbove) {
                    // Most of the time, expanding fully above the root view means expanding in full
                    // screen.
                    ScreenDecorationsUtils.getWindowCornerRadius(context)
                } else {
                    // This usually means we are in split screen mode, so 2 out of 4 corners will
                    // have
                    // a radius of 0.
                    0f
                }
            endState.topCornerRadius = endRadius
            endState.bottomCornerRadius = endRadius

            // We animate the opening window and delegate the view expansion to [this.controller].
            val delegate = this.controller
            val controller =
                object : Controller by delegate {
                    override fun onLaunchAnimationStart(isExpandingFullyAbove: Boolean) {
                        listener?.onLaunchAnimationStart()
                        delegate.onLaunchAnimationStart(isExpandingFullyAbove)
                    }

                    override fun onLaunchAnimationEnd(isExpandingFullyAbove: Boolean) {
                        listener?.onLaunchAnimationEnd()
                        iCallback?.invoke()
                        delegate.onLaunchAnimationEnd(isExpandingFullyAbove)
                    }

                    override fun onLaunchAnimationProgress(
                        state: LaunchAnimator.State,
                        progress: Float,
                        linearProgress: Float
                    ) {
                        // Apply the state to the window only if it is visible, i.e. when the
                        // expanding view is *not* visible.
                        if (!state.visible) {
                            applyStateToWindow(window, state, linearProgress)
                        }
                        navigationBar?.let { applyStateToNavigationBar(it, state, linearProgress) }

                        listener?.onLaunchAnimationProgress(linearProgress)
                        delegate.onLaunchAnimationProgress(state, progress, linearProgress)
                    }
                }

            animation =
                launchAnimator.startAnimation(
                    controller,
                    endState,
                    windowBackgroundColor,
                    fadeOutWindowBackgroundLayer = !controller.isBelowAnimatingWindow,
                    drawHole = !controller.isBelowAnimatingWindow,
                )
        }

        private fun applyStateToWindow(
            window: RemoteAnimationTarget,
            state: LaunchAnimator.State,
            linearProgress: Float,
        ) {
            if (transactionApplierView.viewRootImpl == null || !window.leash.isValid) {
                // Don't apply any transaction if the view root we synchronize with was detached or
                // if the SurfaceControl associated with [window] is not valid, as
                // [SyncRtSurfaceTransactionApplier.scheduleApply] would otherwise throw.
                return
            }

            val screenBounds = window.screenSpaceBounds
            val centerX = (screenBounds.left + screenBounds.right) / 2f
            val centerY = (screenBounds.top + screenBounds.bottom) / 2f
            val width = screenBounds.right - screenBounds.left
            val height = screenBounds.bottom - screenBounds.top

            // Scale the window. We use the max of (widthRatio, heightRatio) so that there is no
            // blank space on any side.
            val widthRatio = state.width.toFloat() / width
            val heightRatio = state.height.toFloat() / height
            val scale = maxOf(widthRatio, heightRatio)
            matrix.reset()
            matrix.setScale(scale, scale, centerX, centerY)

            // Align it to the top and center it in the x-axis.
            val heightChange = height * scale - height
            val translationX = state.centerX - centerX
            val translationY = state.top - screenBounds.top + heightChange / 2f
            matrix.postTranslate(translationX, translationY)

            // Crop it. The matrix will also be applied to the crop, so we apply the inverse
            // operation. Given that we only scale (by factor > 0) then translate, we can assume
            // that the matrix is invertible.
            val cropX = state.left.toFloat() - screenBounds.left
            val cropY = state.top.toFloat() - screenBounds.top
            windowCropF.set(cropX, cropY, cropX + state.width, cropY + state.height)
            matrix.invert(invertMatrix)
            invertMatrix.mapRect(windowCropF)
            windowCrop.set(
                windowCropF.left.roundToInt(),
                windowCropF.top.roundToInt(),
                windowCropF.right.roundToInt(),
                windowCropF.bottom.roundToInt()
            )

            // The alpha of the opening window. If it opens above the expandable, then it should
            // fade in progressively. Otherwise, it should be fully opaque and will be progressively
            // revealed as the window background color layer above the window fades out.
            val alpha =
                if (controller.isBelowAnimatingWindow) {
                    val windowProgress =
                        LaunchAnimator.getProgress(
                            TIMINGS,
                            linearProgress,
                            TIMINGS.contentAfterFadeInDelay,
                            TIMINGS.contentAfterFadeInDuration
                        )

                    INTERPOLATORS.contentAfterFadeInInterpolator.getInterpolation(windowProgress)
                } else {
                    1f
                }

            // The scale will also be applied to the corner radius, so we divide by the scale to
            // keep the original radius. We use the max of (topCornerRadius, bottomCornerRadius) to
            // make sure that the window does not draw itself behind the expanding view. This is
            // especially important for lock screen animations, where the window is not clipped by
            // the shade.
            val cornerRadius = maxOf(state.topCornerRadius, state.bottomCornerRadius) / scale
            val params =
                SyncRtSurfaceTransactionApplier.SurfaceParams.Builder(window.leash)
                    .withAlpha(alpha)
                    .withMatrix(matrix)
                    .withWindowCrop(windowCrop)
                    .withCornerRadius(cornerRadius)
                    .withVisibility(true)
                    .build()

            transactionApplier.scheduleApply(params)
        }

        private fun applyStateToNavigationBar(
            navigationBar: RemoteAnimationTarget,
            state: LaunchAnimator.State,
            linearProgress: Float
        ) {
            if (transactionApplierView.viewRootImpl == null || !navigationBar.leash.isValid) {
                // Don't apply any transaction if the view root we synchronize with was detached or
                // if the SurfaceControl associated with [navigationBar] is not valid, as
                // [SyncRtSurfaceTransactionApplier.scheduleApply] would otherwise throw.
                return
            }

            val fadeInProgress =
                LaunchAnimator.getProgress(
                    TIMINGS,
                    linearProgress,
                    ANIMATION_DELAY_NAV_FADE_IN,
                    ANIMATION_DURATION_NAV_FADE_OUT
                )

            val params = SyncRtSurfaceTransactionApplier.SurfaceParams.Builder(navigationBar.leash)
            if (fadeInProgress > 0) {
                matrix.reset()
                matrix.setTranslate(
                    0f,
                    (state.top - navigationBar.sourceContainerBounds.top).toFloat()
                )
                windowCrop.set(state.left, 0, state.right, state.height)
                params
                    .withAlpha(NAV_FADE_IN_INTERPOLATOR.getInterpolation(fadeInProgress))
                    .withMatrix(matrix)
                    .withWindowCrop(windowCrop)
                    .withVisibility(true)
            } else {
                val fadeOutProgress =
                    LaunchAnimator.getProgress(
                        TIMINGS,
                        linearProgress,
                        0,
                        ANIMATION_DURATION_NAV_FADE_OUT
                    )
                params.withAlpha(1f - NAV_FADE_OUT_INTERPOLATOR.getInterpolation(fadeOutProgress))
            }

            transactionApplier.scheduleApply(params.build())
        }

        private fun onAnimationTimedOut() {
            if (cancelled) {
                return
            }

            Log.i(TAG, "Remote animation timed out")
            timedOut = true
            controller.onLaunchAnimationCancelled()
        }

        @UiThread
        override fun onAnimationCancelled(isKeyguardOccluded: Boolean) {
            if (timedOut) {
                return
            }

            Log.i(TAG, "Remote animation was cancelled")
            cancelled = true
            removeTimeout()

            animation?.cancel()
            controller.onLaunchAnimationCancelled(newKeyguardOccludedState = isKeyguardOccluded)
        }

        private fun IRemoteAnimationFinishedCallback.invoke() {
            try {
                onAnimationFinished()
            } catch (e: RemoteException) {
                e.printStackTrace()
            }
        }
    }
}
