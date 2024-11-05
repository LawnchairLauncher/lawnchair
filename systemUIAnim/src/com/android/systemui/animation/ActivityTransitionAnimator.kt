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
import android.app.WindowConfiguration
import android.content.ComponentName
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.RectF
import android.os.Binder
import android.os.Build
import android.os.Handler
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
import android.view.WindowManager.TRANSIT_CLOSE
import android.view.WindowManager.TRANSIT_OPEN
import android.view.WindowManager.TRANSIT_TO_BACK
import android.view.WindowManager.TRANSIT_TO_FRONT
import android.view.animation.PathInterpolator
import android.window.RemoteTransition
import android.window.TransitionFilter
import androidx.annotation.AnyThread
import androidx.annotation.BinderThread
import androidx.annotation.UiThread
import com.android.app.animation.Interpolators
import com.android.internal.annotations.VisibleForTesting
import com.android.internal.policy.ScreenDecorationsUtils
import com.android.systemui.Flags.activityTransitionUseLargestWindow
import com.android.systemui.Flags.translucentOccludingActivityFix
import com.android.systemui.shared.Flags.returnAnimationFrameworkLibrary
import com.android.wm.shell.shared.IShellTransitions
import com.android.wm.shell.shared.ShellTransitions
import java.util.concurrent.Executor
import kotlin.math.roundToInt

private const val TAG = "ActivityTransitionAnimator"

/**
 * A class that allows activities to be started in a seamless way from a view that is transforming
 * nicely into the starting window.
 */
class ActivityTransitionAnimator
@JvmOverloads
constructor(
    /** The executor that runs on the main thread. */
    private val mainExecutor: Executor,

    /** The object used to register ephemeral returns and long-lived transitions. */
    private val transitionRegister: TransitionRegister? = null,

    /** The animator used when animating a View into an app. */
    private val transitionAnimator: TransitionAnimator = defaultTransitionAnimator(mainExecutor),

    /** The animator used when animating a Dialog into an app. */
    // TODO(b/218989950): Remove this animator and instead set the duration of the dim fade out to
    // TIMINGS.contentBeforeFadeOutDuration.
    private val dialogToAppAnimator: TransitionAnimator = defaultDialogToAppAnimator(mainExecutor),

    /**
     * Whether we should disable the WindowManager timeout. This should be set to true in tests
     * only.
     */
    // TODO(b/301385865): Remove this flag.
    private val disableWmTimeout: Boolean = false,
) {
    @JvmOverloads
    constructor(
        mainExecutor: Executor,
        shellTransitions: ShellTransitions,
        transitionAnimator: TransitionAnimator = defaultTransitionAnimator(mainExecutor),
        dialogToAppAnimator: TransitionAnimator = defaultDialogToAppAnimator(mainExecutor),
        disableWmTimeout: Boolean = false,
    ) : this(
        mainExecutor,
        TransitionRegister.fromShellTransitions(shellTransitions),
        transitionAnimator,
        dialogToAppAnimator,
        disableWmTimeout,
    )

    @JvmOverloads
    constructor(
        mainExecutor: Executor,
        iShellTransitions: IShellTransitions,
        transitionAnimator: TransitionAnimator = defaultTransitionAnimator(mainExecutor),
        dialogToAppAnimator: TransitionAnimator = defaultDialogToAppAnimator(mainExecutor),
        disableWmTimeout: Boolean = false,
    ) : this(
        mainExecutor,
        TransitionRegister.fromIShellTransitions(iShellTransitions),
        transitionAnimator,
        dialogToAppAnimator,
        disableWmTimeout,
    )

    companion object {
        /** The timings when animating a View into an app. */
        @JvmField
        val TIMINGS =
            TransitionAnimator.Timings(
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
            TransitionAnimator.Interpolators(
                positionInterpolator = Interpolators.EMPHASIZED,
                positionXInterpolator = Interpolators.EMPHASIZED_COMPLEMENT,
                contentBeforeFadeOutInterpolator = Interpolators.LINEAR_OUT_SLOW_IN,
                contentAfterFadeInInterpolator = PathInterpolator(0f, 0f, 0.6f, 1f)
            )

        // TODO(b/288507023): Remove this flag.
        @JvmField val DEBUG_TRANSITION_ANIMATION = Build.IS_DEBUGGABLE

        /** Durations & interpolators for the navigation bar fading in & out. */
        private const val ANIMATION_DURATION_NAV_FADE_IN = 266L
        private const val ANIMATION_DURATION_NAV_FADE_OUT = 133L
        private val ANIMATION_DELAY_NAV_FADE_IN =
            TIMINGS.totalDuration - ANIMATION_DURATION_NAV_FADE_IN

        private val NAV_FADE_IN_INTERPOLATOR = Interpolators.STANDARD_DECELERATE
        private val NAV_FADE_OUT_INTERPOLATOR = PathInterpolator(0.2f, 0f, 1f, 1f)

        /** The time we wait before timing out the remote animation after starting the intent. */
        private const val TRANSITION_TIMEOUT = 1_000L

        /**
         * The time we wait before we Log.wtf because the remote animation was neither started or
         * cancelled by WM.
         */
        private const val LONG_TRANSITION_TIMEOUT = 5_000L

        private fun defaultTransitionAnimator(mainExecutor: Executor): TransitionAnimator {
            return TransitionAnimator(mainExecutor, TIMINGS, INTERPOLATORS)
        }

        private fun defaultDialogToAppAnimator(mainExecutor: Executor): TransitionAnimator {
            return TransitionAnimator(mainExecutor, DIALOG_TIMINGS, INTERPOLATORS)
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
            override fun onTransitionAnimationStart() {
                listeners.forEach { it.onTransitionAnimationStart() }
            }

            override fun onTransitionAnimationEnd() {
                listeners.forEach { it.onTransitionAnimationEnd() }
            }

            override fun onTransitionAnimationProgress(linearProgress: Float) {
                listeners.forEach { it.onTransitionAnimationProgress(linearProgress) }
            }

            override fun onTransitionAnimationCancelled() {
                listeners.forEach { it.onTransitionAnimationCancelled() }
            }
        }

    /** Book-keeping for long-lived transitions that are currently registered. */
    private val longLivedTransitions =
        HashMap<TransitionCookie, Pair<RemoteTransition, RemoteTransition>>()

    /**
     * Start an intent and animate the opening window. The intent will be started by running
     * [intentStarter], which should use the provided [RemoteAnimationAdapter] and return the launch
     * result. [controller] is responsible from animating the view from which the intent was started
     * in [Controller.onTransitionAnimationProgress]. No animation will start if there is no window
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
                    "ActivityTransitionAnimator.callback must be set before using this animator"
                )
        val runner = createRunner(controller)
        val runnerDelegate = runner.delegate!!
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

        if (animationAdapter != null && controller.transitionCookie != null) {
            registerEphemeralReturnAnimation(controller, transitionRegister)
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
            runnerDelegate.postTimeouts()

            // Hide the keyguard using the launch animation instead of the default unlock animation.
            if (hideKeyguardWithAnimation) {
                callback.hideKeyguardWithAnimation(runner)
            }
        } else {
            // We need to make sure delegate references are dropped to avoid memory leaks.
            runner.dispose()
        }
    }

    private fun Controller.callOnIntentStartedOnMainThread(willAnimate: Boolean) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainExecutor.execute { callOnIntentStartedOnMainThread(willAnimate) }
        } else {
            if (DEBUG_TRANSITION_ANIMATION) {
                Log.d(
                    TAG,
                    "Calling controller.onIntentStarted(willAnimate=$willAnimate) " +
                        "[controller=$this]"
                )
            }
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
        showOverLockscreen: Boolean = false,
        intentStarter: PendingIntentStarter
    ) {
        startIntentWithAnimation(controller, animate, packageName, showOverLockscreen) {
            intentStarter.startPendingIntent(it)
        }
    }

    /**
     * Uses [transitionRegister] to set up the return animation for the given [launchController].
     *
     * De-registration is set up automatically once the return animation is run.
     *
     * TODO(b/339194555): automatically de-register when the launchable is detached.
     */
    private fun registerEphemeralReturnAnimation(
        launchController: Controller,
        transitionRegister: TransitionRegister?
    ) {
        if (!returnAnimationFrameworkLibrary()) return

        var cleanUpRunnable: Runnable? = null
        val returnRunner =
            createRunner(
                object : DelegateTransitionAnimatorController(launchController) {
                    override val isLaunching = false

                    override fun onTransitionAnimationCancelled(
                        newKeyguardOccludedState: Boolean?
                    ) {
                        super.onTransitionAnimationCancelled(newKeyguardOccludedState)
                        cleanUp()
                    }

                    override fun onTransitionAnimationEnd(isExpandingFullyAbove: Boolean) {
                        super.onTransitionAnimationEnd(isExpandingFullyAbove)
                        cleanUp()
                    }

                    private fun cleanUp() {
                        cleanUpRunnable?.run()
                    }
                }
            )

        // mTypeSet and mModes match back signals only, and not home. This is on purpose, because
        // we only want ephemeral return animations triggered in these scenarios.
        val filter =
            TransitionFilter().apply {
                mTypeSet = intArrayOf(TRANSIT_CLOSE, TRANSIT_TO_BACK)
                mRequirements =
                    arrayOf(
                        TransitionFilter.Requirement().apply {
                            mLaunchCookie = launchController.transitionCookie
                            mModes = intArrayOf(TRANSIT_CLOSE, TRANSIT_TO_BACK)
                        }
                    )
            }
        val transition =
            RemoteTransition(
                RemoteAnimationRunnerCompat.wrap(returnRunner),
                "${launchController.transitionCookie}_returnTransition"
            )

        transitionRegister?.register(filter, transition)
        cleanUpRunnable = Runnable { transitionRegister?.unregister(transition) }
    }

    /** Add a [Listener] that can listen to transition animations. */
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
        val transitionAnimator =
            if (controller.isDialogLaunch) {
                dialogToAppAnimator
            } else {
                transitionAnimator
            }

        return Runner(controller, callback!!, transitionAnimator, lifecycleListener)
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
        fun isOnKeyguard(): Boolean = false

        /** Hide the keyguard and animate using [runner]. */
        fun hideKeyguardWithAnimation(runner: IRemoteAnimationRunner) {
            throw UnsupportedOperationException()
        }

        /* Get the background color of [task]. */
        fun getBackgroundColor(task: TaskInfo): Int
    }

    interface Listener {
        /** Called when an activity transition animation started. */
        fun onTransitionAnimationStart() {}

        /**
         * Called when an activity transition animation is finished. This will be called if and only
         * if [onTransitionAnimationStart] was called earlier.
         */
        fun onTransitionAnimationEnd() {}

        /**
         * The animation was cancelled. Note that [onTransitionAnimationEnd] will still be called
         * after this if the animation was already started, i.e. if [onTransitionAnimationStart] was
         * called before the cancellation.
         */
        fun onTransitionAnimationCancelled() {}

        /** Called when an activity transition animation made progress. */
        fun onTransitionAnimationProgress(linearProgress: Float) {}
    }

    /**
     * A controller that takes care of applying the animation to an expanding view.
     *
     * Note that all callbacks (onXXX methods) are all called on the main thread.
     */
    interface Controller : TransitionAnimator.Controller {
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
            @JvmOverloads
            @JvmStatic
            fun fromView(
                view: View,
                cujType: Int? = null,
                cookie: TransitionCookie? = null,
                component: ComponentName? = null,
                returnCujType: Int? = null
            ): Controller? {
                // Make sure the View we launch from implements LaunchableView to avoid visibility
                // issues.
                if (view !is LaunchableView) {
                    throw IllegalArgumentException(
                        "An ActivityTransitionAnimator.Controller was created from a View that " +
                            "does not implement LaunchableView. This can lead to subtle bugs " +
                            "where the visibility of the View we are launching from is not what " +
                            "we expected."
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

                return GhostedViewTransitionAnimatorController(
                    view,
                    cujType,
                    cookie,
                    component,
                    returnCujType
                )
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
         * Whether the expandable controller by this [Controller] is below the window that is going
         * to be animated.
         *
         * This should be `false` when animating an app from or to the shade or status bar, given
         * that they are drawn above all apps. This is usually `true` when using this animator in a
         * normal app or a launcher, that are drawn below the animating activity/window.
         */
        val isBelowAnimatingWindow: Boolean
            get() = false

        /**
         * The cookie associated with the transition controlled by this [Controller].
         *
         * This should be defined for all return [Controller] (when [isLaunching] is false) and for
         * their associated launch [Controller]s.
         *
         * For the recommended format, see [TransitionCookie].
         */
        val transitionCookie: TransitionCookie?
            get() = null

        /**
         * The [ComponentName] of the activity whose window is tied to this [Controller].
         *
         * This is used as a fallback when a cookie is defined but there is no match (e.g. when a
         * matching activity was launched by a mean different from the launchable in this
         * [Controller]), and should be defined for all long-lived registered [Controller]s.
         */
        val component: ComponentName?
            get() = null

        /**
         * The intent was started. If [willAnimate] is false, nothing else will happen and the
         * animation will not be started.
         */
        fun onIntentStarted(willAnimate: Boolean) {}

        /**
         * The animation was cancelled. Note that [onTransitionAnimationEnd] will still be called
         * after this if the animation was already started, i.e. if [onTransitionAnimationStart] was
         * called before the cancellation.
         *
         * If this transition animation affected the occlusion state of the keyguard, WM will
         * provide us with [newKeyguardOccludedState] so that we can set the occluded state
         * appropriately.
         */
        fun onTransitionAnimationCancelled(newKeyguardOccludedState: Boolean? = null) {}
    }

    /**
     * Registers [controller] as a long-lived transition handler for launch and return animations.
     *
     * The [controller] will only be used for transitions matching the [TransitionCookie] defined
     * within it, or the [ComponentName] if the cookie matching fails. Both fields are mandatory for
     * this registration.
     */
    fun register(controller: Controller) {
        check(returnAnimationFrameworkLibrary()) {
            "Long-lived registrations cannot be used when the returnAnimationFrameworkLibrary " +
                "flag is disabled"
        }

        if (transitionRegister == null) {
            throw IllegalStateException(
                "A RemoteTransitionRegister must be provided when creating this animator in " +
                    "order to use long-lived animations"
            )
        }

        val cookie =
            controller.transitionCookie
                ?: throw IllegalStateException(
                    "A cookie must be defined in order to use long-lived animations"
                )
        val component =
            controller.component
                ?: throw IllegalStateException(
                    "A component must be defined in order to use long-lived animations"
                )

        // Make sure that any previous registrations linked to the same cookie are gone.
        unregister(cookie)

        val launchFilter =
            TransitionFilter().apply {
                mRequirements =
                    arrayOf(
                        TransitionFilter.Requirement().apply {
                            mActivityType = WindowConfiguration.ACTIVITY_TYPE_STANDARD
                            mModes = intArrayOf(TRANSIT_OPEN, TRANSIT_TO_FRONT)
                            mTopActivity = component
                        }
                    )
            }
        val launchRemoteTransition =
            RemoteTransition(
                RemoteAnimationRunnerCompat.wrap(createRunner(controller)),
                "${cookie}_launchTransition"
            )
        transitionRegister.register(launchFilter, launchRemoteTransition)

        val returnController =
            object : Controller by controller {
                override val isLaunching: Boolean = false
            }
        val returnFilter =
            TransitionFilter().apply {
                mRequirements =
                    arrayOf(
                        TransitionFilter.Requirement().apply {
                            mActivityType = WindowConfiguration.ACTIVITY_TYPE_STANDARD
                            mModes = intArrayOf(TRANSIT_CLOSE, TRANSIT_TO_BACK)
                            mTopActivity = component
                        }
                    )
            }
        val returnRemoteTransition =
            RemoteTransition(
                RemoteAnimationRunnerCompat.wrap(createRunner(returnController)),
                "${cookie}_returnTransition"
            )
        transitionRegister.register(returnFilter, returnRemoteTransition)

        longLivedTransitions[cookie] = Pair(launchRemoteTransition, returnRemoteTransition)
    }

    /** Unregisters all controllers previously registered that contain [cookie]. */
    fun unregister(cookie: TransitionCookie) {
        val transitions = longLivedTransitions[cookie] ?: return
        transitionRegister?.unregister(transitions.first)
        transitionRegister?.unregister(transitions.second)
        longLivedTransitions.remove(cookie)
    }

    /**
     * Invokes [onAnimationComplete] when animation is either cancelled or completed. Delegates all
     * events to the passed [delegate].
     */
    @VisibleForTesting
    inner class DelegatingAnimationCompletionListener(
        private val delegate: Listener?,
        private val onAnimationComplete: () -> Unit
    ) : Listener {
        var cancelled = false

        override fun onTransitionAnimationStart() {
            delegate?.onTransitionAnimationStart()
        }

        override fun onTransitionAnimationProgress(linearProgress: Float) {
            delegate?.onTransitionAnimationProgress(linearProgress)
        }

        override fun onTransitionAnimationEnd() {
            delegate?.onTransitionAnimationEnd()
            if (!cancelled) {
                onAnimationComplete.invoke()
            }
        }

        override fun onTransitionAnimationCancelled() {
            cancelled = true
            delegate?.onTransitionAnimationCancelled()
            onAnimationComplete.invoke()
        }
    }

    @VisibleForTesting
    inner class Runner(
        controller: Controller,
        callback: Callback,
        /** The animator to use to animate the window transition. */
        transitionAnimator: TransitionAnimator,
        /** Listener for animation lifecycle events. */
        listener: Listener? = null
    ) : IRemoteAnimationRunner.Stub() {
        // This is being passed across IPC boundaries and cycles (through PendingIntentRecords,
        // etc.) are possible. So we need to make sure we drop any references that might
        // transitively cause leaks when we're done with animation.
        @VisibleForTesting var delegate: AnimationDelegate?

        init {
            delegate =
                AnimationDelegate(
                    mainExecutor,
                    controller,
                    callback,
                    DelegatingAnimationCompletionListener(listener, this::dispose),
                    transitionAnimator,
                    disableWmTimeout,
                )
        }

        @BinderThread
        override fun onAnimationStart(
            transit: Int,
            apps: Array<out RemoteAnimationTarget>?,
            wallpapers: Array<out RemoteAnimationTarget>?,
            nonApps: Array<out RemoteAnimationTarget>?,
            finishedCallback: IRemoteAnimationFinishedCallback?
        ) {
            val delegate = delegate
            mainExecutor.execute {
                if (delegate == null) {
                    Log.i(TAG, "onAnimationStart called after completion")
                    // Animation started too late and timed out already. We need to still
                    // signal back that we're done with it.
                    finishedCallback?.onAnimationFinished()
                } else {
                    delegate.onAnimationStart(transit, apps, wallpapers, nonApps, finishedCallback)
                }
            }
        }

        @BinderThread
        override fun onAnimationCancelled() {
            val delegate = delegate
            mainExecutor.execute {
                delegate ?: Log.wtf(TAG, "onAnimationCancelled called after completion")
                delegate?.onAnimationCancelled()
            }
        }

        @AnyThread
        fun dispose() {
            // Drop references to animation controller once we're done with the animation
            // to avoid leaking.
            mainExecutor.execute { delegate = null }
        }
    }

    class AnimationDelegate
    @JvmOverloads
    constructor(
        private val mainExecutor: Executor,
        private val controller: Controller,
        private val callback: Callback,
        /** Listener for animation lifecycle events. */
        private val listener: Listener? = null,
        /** The animator to use to animate the window transition. */
        private val transitionAnimator: TransitionAnimator =
            defaultTransitionAnimator(mainExecutor),

        /**
         * Whether we should disable the WindowManager timeout. This should be set to true in tests
         * only.
         */
        // TODO(b/301385865): Remove this flag.
        disableWmTimeout: Boolean = false,
    ) : RemoteAnimationDelegate<IRemoteAnimationFinishedCallback> {
        private val transitionContainer = controller.transitionContainer
        private val context = transitionContainer.context
        private val transactionApplierView =
            controller.openingWindowSyncView ?: controller.transitionContainer
        private val transactionApplier = SyncRtSurfaceTransactionApplier(transactionApplierView)
        private val timeoutHandler =
            if (!disableWmTimeout) {
                Handler(Looper.getMainLooper())
            } else {
                null
            }

        private val matrix = Matrix()
        private val invertMatrix = Matrix()
        private var windowCrop = Rect()
        private var windowCropF = RectF()
        private var timedOut = false
        private var cancelled = false
        private var animation: TransitionAnimator.Animation? = null

        /**
         * A timeout to cancel the transition animation if the remote animation is not started or
         * cancelled within [TRANSITION_TIMEOUT] milliseconds after the intent was started.
         *
         * Note that this is important to keep this a Runnable (and not a Kotlin lambda), otherwise
         * it will be automatically converted when posted and we wouldn't be able to remove it after
         * posting it.
         */
        private var onTimeout = Runnable { onAnimationTimedOut() }

        /**
         * A long timeout to Log.wtf (signaling a bug in WM) when the remote animation wasn't
         * started or cancelled within [LONG_TRANSITION_TIMEOUT] milliseconds after the intent was
         * started.
         */
        private var onLongTimeout = Runnable {
            Log.wtf(
                TAG,
                "The remote animation was neither cancelled or started within " +
                    "$LONG_TRANSITION_TIMEOUT"
            )
        }

        init {
            // We do this check here to cover all entry points, including Launcher which doesn't
            // call startIntentWithAnimation()
            if (!controller.isLaunching) TransitionAnimator.checkReturnAnimationFrameworkFlag()
        }

        @UiThread
        internal fun postTimeouts() {
            if (timeoutHandler != null) {
                timeoutHandler.postDelayed(onTimeout, TRANSITION_TIMEOUT)
                timeoutHandler.postDelayed(onLongTimeout, LONG_TRANSITION_TIMEOUT)
            }
        }

        private fun removeTimeouts() {
            if (timeoutHandler != null) {
                timeoutHandler.removeCallbacks(onTimeout)
                timeoutHandler.removeCallbacks(onLongTimeout)
            }
        }

        @UiThread
        override fun onAnimationStart(
            @WindowManager.TransitionOldType transit: Int,
            apps: Array<out RemoteAnimationTarget>?,
            wallpapers: Array<out RemoteAnimationTarget>?,
            nonApps: Array<out RemoteAnimationTarget>?,
            callback: IRemoteAnimationFinishedCallback?
        ) {
            removeTimeouts()

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

            val window = findTargetWindowIfPossible(apps)
            if (window == null) {
                Log.i(TAG, "Aborting the animation as no window is opening")
                callback?.invoke()

                if (DEBUG_TRANSITION_ANIMATION) {
                    Log.d(
                        TAG,
                        "Calling controller.onTransitionAnimationCancelled() [no window opening]"
                    )
                }
                controller.onTransitionAnimationCancelled()
                listener?.onTransitionAnimationCancelled()
                return
            }

            val navigationBar =
                nonApps?.firstOrNull {
                    it.windowType == WindowManager.LayoutParams.TYPE_NAVIGATION_BAR
                }

            startAnimation(window, navigationBar, callback)
        }

        private fun findTargetWindowIfPossible(
            apps: Array<out RemoteAnimationTarget>?
        ): RemoteAnimationTarget? {
            if (apps == null) {
                return null
            }

            val targetMode =
                if (controller.isLaunching) {
                    RemoteAnimationTarget.MODE_OPENING
                } else {
                    RemoteAnimationTarget.MODE_CLOSING
                }
            var candidate: RemoteAnimationTarget? = null

            for (it in apps) {
                if (it.mode == targetMode) {
                    if (activityTransitionUseLargestWindow()) {
                        if (returnAnimationFrameworkLibrary()) {
                            // If the controller contains a cookie, _only_ match if either the
                            // candidate contains the matching cookie, or a component is also
                            // defined and is a match.
                            if (
                                controller.transitionCookie != null &&
                                    it.taskInfo
                                        ?.launchCookies
                                        ?.contains(controller.transitionCookie) != true &&
                                    (controller.component == null ||
                                        it.taskInfo?.topActivity != controller.component)
                            ) {
                                continue
                            }
                        }

                        if (
                            candidate == null ||
                                !it.hasAnimatingParent && candidate.hasAnimatingParent
                        ) {
                            candidate = it
                            continue
                        }
                        if (
                            !it.hasAnimatingParent &&
                                it.screenSpaceBounds.hasGreaterAreaThan(candidate.screenSpaceBounds)
                        ) {
                            candidate = it
                        }
                    } else {
                        if (!it.hasAnimatingParent) {
                            return it
                        }
                        if (candidate == null) {
                            candidate = it
                        }
                    }
                }
            }

            return candidate
        }

        private fun startAnimation(
            window: RemoteAnimationTarget,
            navigationBar: RemoteAnimationTarget?,
            iCallback: IRemoteAnimationFinishedCallback?
        ) {
            if (TransitionAnimator.DEBUG) {
                Log.d(TAG, "Remote animation started")
            }

            val windowBounds = window.screenSpaceBounds
            val endState =
                if (controller.isLaunching) {
                    TransitionAnimator.State(
                        top = windowBounds.top,
                        bottom = windowBounds.bottom,
                        left = windowBounds.left,
                        right = windowBounds.right
                    )
                } else {
                    controller.createAnimatorState()
                }
            val windowBackgroundColor =
                if (translucentOccludingActivityFix() && window.isTranslucent) {
                    Color.TRANSPARENT
                } else {
                    window.taskInfo?.let { callback.getBackgroundColor(it) }
                        ?: window.backgroundColor
                }

            // TODO(b/184121838): We should somehow get the top and bottom radius of the window
            // instead of recomputing isExpandingFullyAbove here.
            val isExpandingFullyAbove =
                transitionAnimator.isExpandingFullyAbove(controller.transitionContainer, endState)
            if (controller.isLaunching) {
                val endRadius = getWindowRadius(isExpandingFullyAbove)
                endState.topCornerRadius = endRadius
                endState.bottomCornerRadius = endRadius
            }

            // We animate the opening window and delegate the view expansion to [this.controller].
            val delegate = this.controller
            val controller =
                object : Controller by delegate {
                    override fun createAnimatorState(): TransitionAnimator.State {
                        if (isLaunching) return delegate.createAnimatorState()
                        val windowRadius = getWindowRadius(isExpandingFullyAbove)
                        return TransitionAnimator.State(
                            top = windowBounds.top,
                            bottom = windowBounds.bottom,
                            left = windowBounds.left,
                            right = windowBounds.right,
                            topCornerRadius = windowRadius,
                            bottomCornerRadius = windowRadius
                        )
                    }

                    override fun onTransitionAnimationStart(isExpandingFullyAbove: Boolean) {
                        listener?.onTransitionAnimationStart()

                        if (DEBUG_TRANSITION_ANIMATION) {
                            Log.d(
                                TAG,
                                "Calling controller.onTransitionAnimationStart(" +
                                    "isExpandingFullyAbove=$isExpandingFullyAbove) " +
                                    "[controller=$delegate]"
                            )
                        }
                        delegate.onTransitionAnimationStart(isExpandingFullyAbove)
                    }

                    override fun onTransitionAnimationEnd(isExpandingFullyAbove: Boolean) {
                        listener?.onTransitionAnimationEnd()
                        iCallback?.invoke()

                        if (DEBUG_TRANSITION_ANIMATION) {
                            Log.d(
                                TAG,
                                "Calling controller.onTransitionAnimationEnd(" +
                                    "isExpandingFullyAbove=$isExpandingFullyAbove) " +
                                    "[controller=$delegate]"
                            )
                        }
                        delegate.onTransitionAnimationEnd(isExpandingFullyAbove)
                    }

                    override fun onTransitionAnimationProgress(
                        state: TransitionAnimator.State,
                        progress: Float,
                        linearProgress: Float
                    ) {
                        applyStateToWindow(window, state, linearProgress)
                        navigationBar?.let { applyStateToNavigationBar(it, state, linearProgress) }

                        listener?.onTransitionAnimationProgress(linearProgress)
                        delegate.onTransitionAnimationProgress(state, progress, linearProgress)
                    }
                }

            animation =
                transitionAnimator.startAnimation(
                    controller,
                    endState,
                    windowBackgroundColor,
                    fadeWindowBackgroundLayer = !controller.isBelowAnimatingWindow,
                    drawHole = !controller.isBelowAnimatingWindow,
                )
        }

        private fun getWindowRadius(isExpandingFullyAbove: Boolean): Float {
            return if (isExpandingFullyAbove) {
                // Most of the time, expanding fully above the root view means
                // expanding in full screen.
                ScreenDecorationsUtils.getWindowCornerRadius(context)
            } else {
                // This usually means we are in split screen mode, so 2 out of 4
                // corners will have a radius of 0.
                0f
            }
        }

        private fun applyStateToWindow(
            window: RemoteAnimationTarget,
            state: TransitionAnimator.State,
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

            val windowAnimationDelay =
                if (controller.isLaunching) {
                    TIMINGS.contentAfterFadeInDelay
                } else {
                    TIMINGS.contentBeforeFadeOutDelay
                }
            val windowAnimationDuration =
                if (controller.isLaunching) {
                    TIMINGS.contentAfterFadeInDuration
                } else {
                    TIMINGS.contentBeforeFadeOutDuration
                }
            val windowProgress =
                TransitionAnimator.getProgress(
                    TIMINGS,
                    linearProgress,
                    windowAnimationDelay,
                    windowAnimationDuration
                )

            // The alpha of the opening window. If it opens above the expandable, then it should
            // fade in progressively. Otherwise, it should be fully opaque and will be progressively
            // revealed as the window background color layer above the window fades out.
            val alpha =
                if (controller.isBelowAnimatingWindow) {
                    if (controller.isLaunching) {
                        INTERPOLATORS.contentAfterFadeInInterpolator.getInterpolation(
                            windowProgress
                        )
                    } else {
                        1 -
                            INTERPOLATORS.contentBeforeFadeOutInterpolator.getInterpolation(
                                windowProgress
                            )
                    }
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
            state: TransitionAnimator.State,
            linearProgress: Float
        ) {
            if (transactionApplierView.viewRootImpl == null || !navigationBar.leash.isValid) {
                // Don't apply any transaction if the view root we synchronize with was detached or
                // if the SurfaceControl associated with [navigationBar] is not valid, as
                // [SyncRtSurfaceTransactionApplier.scheduleApply] would otherwise throw.
                return
            }

            val fadeInProgress =
                TransitionAnimator.getProgress(
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
                    TransitionAnimator.getProgress(
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
            // The remote animation was cancelled by WM, so we already cancelled the transition
            // animation.
            if (cancelled) {
                return
            }

            Log.w(TAG, "Remote animation timed out")
            timedOut = true

            if (DEBUG_TRANSITION_ANIMATION) {
                Log.d(
                    TAG,
                    "Calling controller.onTransitionAnimationCancelled() [animation timed out]"
                )
            }
            controller.onTransitionAnimationCancelled()
            listener?.onTransitionAnimationCancelled()
        }

        @UiThread
        override fun onAnimationCancelled() {
            removeTimeouts()

            // The short timeout happened, so we already cancelled the transition animation.
            if (timedOut) {
                return
            }

            Log.i(TAG, "Remote animation was cancelled")
            cancelled = true

            animation?.cancel()

            if (DEBUG_TRANSITION_ANIMATION) {
                Log.d(
                    TAG,
                    "Calling controller.onTransitionAnimationCancelled() [remote animation " +
                        "cancelled]",
                )
            }
            controller.onTransitionAnimationCancelled()
            listener?.onTransitionAnimationCancelled()
        }

        private fun IRemoteAnimationFinishedCallback.invoke() {
            try {
                onAnimationFinished()
            } catch (e: RemoteException) {
                e.printStackTrace()
            }
        }

        private fun Rect.hasGreaterAreaThan(other: Rect): Boolean {
            return (this.width() * this.height()) > (other.width() * other.height())
        }
    }

    /**
     * Wraps one of the two methods we have to register remote transitions with WM Shell:
     * - for in-process registrations (e.g. System UI) we use [ShellTransitions]
     * - for cross-process registrations (e.g. Launcher) we use [IShellTransitions]
     *
     * Important: each instance of this class must wrap exactly one of the two.
     */
    class TransitionRegister
    private constructor(
        private val shellTransitions: ShellTransitions? = null,
        private val iShellTransitions: IShellTransitions? = null,
    ) {
        init {
            assert((shellTransitions != null).xor(iShellTransitions != null))
        }

        companion object {
            /** Provides a [TransitionRegister] instance wrapping [ShellTransitions]. */
            fun fromShellTransitions(shellTransitions: ShellTransitions): TransitionRegister {
                return TransitionRegister(shellTransitions = shellTransitions)
            }

            /** Provides a [TransitionRegister] instance wrapping [IShellTransitions]. */
            fun fromIShellTransitions(iShellTransitions: IShellTransitions): TransitionRegister {
                return TransitionRegister(iShellTransitions = iShellTransitions)
            }
        }

        /** Register [remoteTransition] with WM Shell using the given [filter]. */
        internal fun register(
            filter: TransitionFilter,
            remoteTransition: RemoteTransition,
        ) {
            shellTransitions?.registerRemote(filter, remoteTransition)
            iShellTransitions?.registerRemote(filter, remoteTransition)
        }

        /** Unregister [remoteTransition] from WM Shell. */
        internal fun unregister(remoteTransition: RemoteTransition) {
            shellTransitions?.unregisterRemote(remoteTransition)
            iShellTransitions?.unregisterRemote(remoteTransition)
        }
    }

    /**
     * A cookie used to uniquely identify a task launched using an
     * [ActivityTransitionAnimator.Controller].
     *
     * The [String] encapsulated by this class should be formatted in such a way to be unique across
     * the system, but reliably constant for the same associated launchable.
     *
     * Recommended naming scheme:
     * - DO use the fully qualified name of the class that owns the instance of the launchable,
     *   along with a concise and precise description of the purpose of the launchable in question.
     * - DO NOT introduce uniqueness through the use of timestamps or other runtime variables that
     *   will change if the instance is destroyed and re-created.
     *
     * Example: "com.not.the.real.class.name.ShadeController_openSettingsButton"
     *
     * Note that sometimes (e.g. in recycler views) there could be multiple instances of the same
     * launchable, and no static knowledge to adequately differentiate between them using a single
     * description. In this case, the recommendation is to append a unique identifier related to the
     * contents of the launchable.
     *
     * Example: “com.not.the.real.class.name.ToastWebResult_launchAga_id143256”
     */
    data class TransitionCookie(private val cookie: String) : Binder()
}
