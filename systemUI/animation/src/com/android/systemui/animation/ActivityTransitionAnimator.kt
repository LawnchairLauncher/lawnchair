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
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.RectF
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.RemoteException
import android.util.ArrayMap
import android.util.Log
import android.view.IRemoteAnimationFinishedCallback
import android.view.IRemoteAnimationRunner
import android.view.RemoteAnimationAdapter
import android.view.RemoteAnimationTarget
import android.view.SurfaceControl
import android.view.SyncRtSurfaceTransactionApplier
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.WindowManager.TRANSIT_CLOSE
import android.view.WindowManager.TRANSIT_OPEN
import android.view.WindowManager.TRANSIT_TO_BACK
import android.view.WindowManager.TRANSIT_TO_FRONT
import android.view.animation.PathInterpolator
import android.window.IRemoteTransition
import android.window.IRemoteTransitionFinishedCallback
import android.window.RemoteTransition
import android.window.TransitionFilter
import android.window.TransitionInfo
import android.window.WindowAnimationState
import androidx.annotation.AnyThread
import androidx.annotation.BinderThread
import androidx.annotation.UiThread
import com.android.app.animation.Interpolators
import com.android.internal.annotations.VisibleForTesting
import com.android.internal.policy.ScreenDecorationsUtils
import com.android.systemui.Flags.animationLibraryDelayLeashCleanup
import com.android.systemui.Flags.instantHideShade
import com.android.systemui.Flags.moveTransitionAnimationLayer
import com.android.systemui.animation.TransitionAnimator.Companion.assertLongLivedReturnAnimations
import com.android.systemui.animation.TransitionAnimator.Companion.assertReturnAnimations
import com.android.systemui.animation.TransitionAnimator.Companion.longLivedReturnAnimationsEnabled
import com.android.systemui.animation.TransitionAnimator.Companion.returnAnimationsEnabled
import com.android.systemui.animation.TransitionAnimator.Companion.toTransitionState
import com.android.wm.shell.shared.IShellTransitions
import com.android.wm.shell.shared.ShellTransitions
import com.android.wm.shell.shared.TransitionUtil
import java.util.concurrent.Executor
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

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

    /**
     * Whether we should disable the reparent transaction that puts the opening/closing window above
     * the view's window. This should be set to true in tests only, where we can't currently use a
     * valid leash.
     *
     * TODO(b/397180418): Remove this flag when we don't have the RemoteAnimation wrapper anymore
     *   and we can just inject a fake transaction.
     */
    private val skipReparentTransaction: Boolean = false,
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
                contentAfterFadeInDuration = 183L,
            )

        /**
         * The timings when animating a View into an app using a spring animator. These timings
         * represent fractions of the progress between the spring's initial value and its final
         * value.
         */
        val SPRING_TIMINGS =
            TransitionAnimator.SpringTimings(
                contentBeforeFadeOutDelay = 0f,
                contentBeforeFadeOutDuration = 0.8f,
                contentAfterFadeInDelay = 0.85f,
                contentAfterFadeInDuration = 0.135f,
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
                contentAfterFadeInInterpolator = PathInterpolator(0f, 0f, 0.6f, 1f),
            )

        /** The interpolators when animating a View into an app using a spring animator. */
        val SPRING_INTERPOLATORS =
            INTERPOLATORS.copy(
                contentBeforeFadeOutInterpolator = Interpolators.DECELERATE_1_5,
                contentAfterFadeInInterpolator = Interpolators.SLOW_OUT_LINEAR_IN,
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
            return TransitionAnimator(
                mainExecutor,
                TIMINGS,
                INTERPOLATORS,
                SPRING_TIMINGS,
                SPRING_INTERPOLATORS,
            )
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
                LinkedHashSet(listeners).forEach { it.onTransitionAnimationStart() }
            }

            override fun onTransitionAnimationEnd() {
                LinkedHashSet(listeners).forEach { it.onTransitionAnimationEnd() }
            }

            override fun onTransitionAnimationProgress(linearProgress: Float) {
                LinkedHashSet(listeners).forEach {
                    it.onTransitionAnimationProgress(linearProgress)
                }
            }

            override fun onTransitionAnimationCancelled() {
                LinkedHashSet(listeners).forEach { it.onTransitionAnimationCancelled() }
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
        intentStarter: (RemoteAnimationAdapter?) -> Int,
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
        val runner = createEphemeralRunner(controller)
        val runnerDelegate = runner.delegate
        val hideKeyguardWithAnimation = callback.isOnKeyguard() && !showOverLockscreen

        // Pass the RemoteAnimationAdapter to the intent starter only if we are not hiding the
        // keyguard with the animation
        val animationAdapter =
            if (!hideKeyguardWithAnimation) {
                RemoteAnimationAdapter(
                    runner,
                    TIMINGS.totalDuration,
                    TIMINGS.totalDuration - 150, /* statusBarTransitionDelay */
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
                        null, /* launchCookie */
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
                "hideKeyguardWithAnimation=$hideKeyguardWithAnimation",
        )
        controller.callOnIntentStartedOnMainThread(willAnimate)

        // If we expect an animation, post a timeout to cancel it in case the remote animation is
        // never started.
        if (willAnimate) {
            if (longLivedReturnAnimationsEnabled()) {
                runner.postTimeouts()
            } else {
                runnerDelegate!!.postTimeouts()
            }

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
                        "[controller=$this]",
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
        intentStarter: PendingIntentStarter,
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
        transitionRegister: TransitionRegister?,
    ) {
        if (!returnAnimationsEnabled()) return

        var cleanUpRunnable: Runnable? = null
        val returnRunner =
            createEphemeralRunner(
                object : DelegateTransitionAnimatorController(launchController) {
                    override val isLaunching = false

                    override fun onTransitionAnimationCancelled(
                        newKeyguardOccludedState: Boolean?
                    ) {
                        super.onTransitionAnimationCancelled(newKeyguardOccludedState)
                        onDispose()
                    }

                    override fun onTransitionAnimationEnd(isExpandingFullyAbove: Boolean) {
                        super.onTransitionAnimationEnd(isExpandingFullyAbove)
                        onDispose()
                    }

                    override fun onDispose() {
                        super.onDispose()
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
                "${launchController.transitionCookie}_returnTransition",
            )

        transitionRegister?.register(filter, transition, includeTakeover = false)
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

    /**
     * Create a new animation [Runner] controlled by [controller].
     *
     * This method must only be used for ephemeral (launch or return) transitions. Otherwise, use
     * [createLongLivedRunner].
     */
    @VisibleForTesting
    fun createEphemeralRunner(controller: Controller): Runner {
        // Make sure we use the modified timings when animating a dialog into an app.
        val transitionAnimator =
            if (controller.isDialogLaunch) {
                dialogToAppAnimator
            } else {
                transitionAnimator
            }

        return Runner(controller, callback!!, transitionAnimator, lifecycleListener)
    }

    /**
     * Create a new animation [Runner] controlled by the [Controller] that [controllerFactory] can
     * create based on [forLaunch] and within the given [scope].
     *
     * This method must only be used for long-lived registrations. Otherwise, use
     * [createEphemeralRunner].
     */
    @VisibleForTesting
    fun createLongLivedRunner(
        controllerFactory: ControllerFactory,
        scope: CoroutineScope,
        forLaunch: Boolean,
    ): Runner {
        assertLongLivedReturnAnimations()
        return Runner(scope, callback!!, transitionAnimator, lifecycleListener) {
            controllerFactory.createController(forLaunch)
        }
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
     * A factory used to create instances of [Controller] linked to a specific cookie [cookie] and
     * [component].
     */
    abstract class ControllerFactory(
        val cookie: TransitionCookie,
        val component: ComponentName?,
        val launchCujType: Int? = null,
        val returnCujType: Int? = null,
    ) {
        /**
         * Creates a [Controller] for launching or returning from the activity linked to [cookie]
         * and [component].
         */
        abstract suspend fun createController(forLaunch: Boolean): Controller
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
                returnCujType: Int? = null,
                isEphemeral: Boolean = true,
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
                        Exception(),
                    )
                    return null
                }

                return GhostedViewTransitionAnimatorController(
                    view,
                    cujType,
                    cookie,
                    component,
                    returnCujType,
                    isEphemeral,
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

        /** The controller will not be used again. Clean up the relevant internal state. */
        fun onDispose() {}
    }

    /**
     * Registers [controllerFactory] as a long-lived transition handler for launch and return
     * animations.
     *
     * The [Controller]s created by [controllerFactory] will only be used for transitions matching
     * the [cookie], or the [ComponentName] defined within it if the cookie matching fails. These
     * [Controller]s can only be created within [scope].
     */
    fun register(
        cookie: TransitionCookie,
        controllerFactory: ControllerFactory,
        scope: CoroutineScope,
    ) {
        assertLongLivedReturnAnimations()

        if (transitionRegister == null) {
            throw IllegalStateException(
                "A RemoteTransitionRegister must be provided when creating this animator in " +
                    "order to use long-lived animations"
            )
        }

        val component =
            controllerFactory.component
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
                OriginTransition(createLongLivedRunner(controllerFactory, scope, forLaunch = true)),
                "${cookie}_launchTransition",
            )
        // TODO(b/403529740): re-enable takeovers once we solve the Compose jank issues.
        transitionRegister.register(launchFilter, launchRemoteTransition, includeTakeover = false)

        // Cross-task close transitions should not use this animation, so we only register it for
        // when the opening window is Launcher.
        val returnFilter =
            TransitionFilter().apply {
                mRequirements =
                    arrayOf(
                        TransitionFilter.Requirement().apply {
                            mActivityType = WindowConfiguration.ACTIVITY_TYPE_STANDARD
                            mModes = intArrayOf(TRANSIT_CLOSE, TRANSIT_TO_BACK)
                            mTopActivity = component
                        },
                        TransitionFilter.Requirement().apply {
                            mActivityType = WindowConfiguration.ACTIVITY_TYPE_HOME
                            mModes = intArrayOf(TRANSIT_OPEN, TRANSIT_TO_FRONT)
                        },
                    )
            }
        val returnRemoteTransition =
            RemoteTransition(
                OriginTransition(
                    createLongLivedRunner(controllerFactory, scope, forLaunch = false)
                ),
                "${cookie}_returnTransition",
            )
        // TODO(b/403529740): re-enable takeovers once we solve the Compose jank issues.
        transitionRegister.register(returnFilter, returnRemoteTransition, includeTakeover = false)

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
        private val onAnimationComplete: () -> Unit,
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

    /** [Runner] wrapper that supports animation takeovers. */
    private inner class OriginTransition(private val runner: Runner) : IRemoteTransition {
        private val delegate = RemoteAnimationRunnerCompat.wrap(runner)

        init {
            assertLongLivedReturnAnimations()
        }

        override fun startAnimation(
            token: IBinder?,
            info: TransitionInfo?,
            t: SurfaceControl.Transaction?,
            finishCallback: IRemoteTransitionFinishedCallback?,
        ) {
            delegate.startAnimation(token, info, t, finishCallback)
        }

        override fun mergeAnimation(
            transition: IBinder?,
            info: TransitionInfo?,
            t: SurfaceControl.Transaction?,
            mergeTarget: IBinder?,
            finishCallback: IRemoteTransitionFinishedCallback?,
        ) {
            delegate.mergeAnimation(transition, info, t, mergeTarget, finishCallback)
        }

        override fun onTransitionConsumed(transition: IBinder?, aborted: Boolean) {
            delegate.onTransitionConsumed(transition, aborted)
        }

        override fun takeOverAnimation(
            token: IBinder?,
            info: TransitionInfo?,
            t: SurfaceControl.Transaction?,
            finishCallback: IRemoteTransitionFinishedCallback?,
            states: Array<WindowAnimationState>,
        ) {
            if (info == null || t == null) {
                Log.e(
                    TAG,
                    "Skipping the animation takeover because the required data is missing: " +
                        "info=$info, transaction=$t",
                )
                return
            }

            // The following code converts the contents of the given TransitionInfo into
            // RemoteAnimationTargets. This is necessary because we must currently support both the
            // new (Shell, remote transitions) and old (remote animations) framework to maintain
            // functionality for all users of the library.
            val apps = ArrayList<RemoteAnimationTarget>()
            val filteredStates = ArrayList<WindowAnimationState>()
            val leashMap = ArrayMap<SurfaceControl, SurfaceControl>()
            val leafTaskFilter = TransitionUtil.LeafTaskFilter()

            // About layering: we divide up the "layer space" into 2 regions (each the size of the
            // change count). This lets us categorize things into above and below while
            // maintaining their relative ordering.
            val belowLayers = info.changes.size
            val aboveLayers = info.changes.size * 2
            for (i in info.changes.indices) {
                val change = info.changes[i]
                if (change == null || change.taskInfo == null) {
                    continue
                }

                val taskInfo = change.taskInfo

                if (TransitionUtil.isWallpaper(change)) {
                    val target =
                        TransitionUtil.newTarget(
                            change,
                            belowLayers - i, // wallpapers go into the "below" layer space
                            info,
                            t,
                            leashMap,
                        )

                    // Make all the wallpapers opaque.
                    t.setAlpha(target.leash, 1f)
                } else if (leafTaskFilter.test(change)) {
                    // Start by putting everything into the "below" layer space.
                    val target =
                        TransitionUtil.newTarget(change, belowLayers - i, info, t, leashMap)
                    apps.add(target)
                    filteredStates.add(states[i])

                    // Make all the apps opaque.
                    t.setAlpha(target.leash, 1f)

                    if (
                        TransitionUtil.isClosingType(change.mode) &&
                            taskInfo?.topActivityType != WindowConfiguration.ACTIVITY_TYPE_HOME
                    ) {
                        // Raise closing task to "above" layer so it isn't covered.
                        t.setLayer(target.leash, aboveLayers - i)
                    } else if (TransitionUtil.isOpeningType(change.mode)) {
                        // Put into the "below" layer space.
                        t.setLayer(target.leash, belowLayers - i)
                    }
                } else if (TransitionInfo.isIndependent(change, info)) {
                    // Root tasks
                    if (TransitionUtil.isClosingType(change.mode)) {
                        // Raise closing task to "above" layer so it isn't covered.
                        t.setLayer(change.leash, aboveLayers - i)
                    } else if (TransitionUtil.isOpeningType(change.mode)) {
                        // Put into the "below" layer space.
                        t.setLayer(change.leash, belowLayers - i)
                    }
                } else if (TransitionUtil.isDividerBar(change)) {
                    val target =
                        TransitionUtil.newTarget(change, belowLayers - i, info, t, leashMap)
                    apps.add(target)
                    filteredStates.add(states[i])
                }
            }

            val wrappedCallback: IRemoteAnimationFinishedCallback =
                object : IRemoteAnimationFinishedCallback.Stub() {
                    override fun onAnimationFinished() {
                        leashMap.clear()
                        val finishTransaction = SurfaceControl.Transaction()
                        finishCallback?.onTransitionFinished(null, finishTransaction)
                        finishTransaction.close()
                    }
                }

            runner.takeOverAnimation(
                apps.toTypedArray(),
                filteredStates.toTypedArray(),
                t,
                wrappedCallback,
            )
        }

        override fun asBinder(): IBinder {
            return delegate.asBinder()
        }
    }

    @VisibleForTesting
    inner class Runner
    private constructor(
        /**
         * This can hold a reference to a view, so it needs to be cleaned up and can't be held on to
         * forever. In case of a long-lived [Runner], this must be null and [controllerFactory] must
         * be defined instead.
         */
        private var controller: Controller?,
        /**
         * Reusable factory to generate single-use controllers. In case of an ephemeral [Runner],
         * this must be null and [controller] must be defined instead.
         */
        private val controllerFactory: (suspend () -> Controller)?,
        /** The scope to use when this runner is based on [controllerFactory]. */
        private val scope: CoroutineScope? = null,
        private val callback: Callback,
        /** The animator to use to animate the window transition. */
        private val transitionAnimator: TransitionAnimator,
        /** Listener for animation lifecycle events. */
        private val listener: Listener?,
    ) : IRemoteAnimationRunner.Stub() {
        constructor(
            controller: Controller,
            callback: Callback,
            transitionAnimator: TransitionAnimator,
            listener: Listener? = null,
        ) : this(
            controller = controller,
            controllerFactory = null,
            callback = callback,
            transitionAnimator = transitionAnimator,
            listener = listener,
        )

        constructor(
            scope: CoroutineScope,
            callback: Callback,
            transitionAnimator: TransitionAnimator,
            listener: Listener? = null,
            controllerFactory: suspend () -> Controller,
        ) : this(
            controller = null,
            controllerFactory = controllerFactory,
            scope = scope,
            callback = callback,
            transitionAnimator = transitionAnimator,
            listener = listener,
        )

        // This is being passed across IPC boundaries and cycles (through PendingIntentRecords,
        // etc.) are possible. So we need to make sure we drop any references that might
        // transitively cause leaks when we're done with animation.
        @VisibleForTesting var delegate: AnimationDelegate?

        init {
            assert((controller != null).xor(controllerFactory != null))

            delegate = null
            controller?.let {
                // Ephemeral launches bundle the runner with the launch request (instead of being
                // registered ahead of time for later use). This means that there could be a timeout
                // between creation and invocation, so the delegate needs to exist from the
                // beginning in order to handle such timeout.
                createDelegate(it)
            }
        }

        @BinderThread
        override fun onAnimationStart(
            transit: Int,
            apps: Array<out RemoteAnimationTarget>?,
            wallpapers: Array<out RemoteAnimationTarget>?,
            nonApps: Array<out RemoteAnimationTarget>?,
            finishedCallback: IRemoteAnimationFinishedCallback?,
        ) {
            initAndRun(finishedCallback) { delegate ->
                delegate.onAnimationStart(transit, apps, wallpapers, nonApps, finishedCallback)
            }
        }

        @VisibleForTesting
        @BinderThread
        fun takeOverAnimation(
            apps: Array<RemoteAnimationTarget>?,
            windowAnimationStates: Array<WindowAnimationState>,
            startTransaction: SurfaceControl.Transaction,
            finishedCallback: IRemoteAnimationFinishedCallback?,
        ) {
            assertLongLivedReturnAnimations()
            initAndRun(finishedCallback) { delegate ->
                delegate.takeOverAnimation(
                    apps,
                    windowAnimationStates,
                    startTransaction,
                    finishedCallback,
                )
            }
        }

        @BinderThread
        private fun initAndRun(
            finishedCallback: IRemoteAnimationFinishedCallback?,
            performAnimation: (AnimationDelegate) -> Unit,
        ) {
            val controller = controller
            val controllerFactory = controllerFactory

            if (controller != null) {
                maybeSetUp(controller)
                val success = startAnimation(performAnimation)
                if (!success) finishedCallback?.onAnimationFinished()
            } else if (controllerFactory != null) {
                scope?.launch {
                    val success =
                        withTimeoutOrNull(TRANSITION_TIMEOUT) {
                            setUp(controllerFactory)
                            startAnimation(performAnimation)
                        } ?: false
                    if (!success) finishedCallback?.onAnimationFinished()
                }
            } else {
                // This happens when onDisposed() has already been called due to the animation being
                // cancelled. Only issue the callback.
                finishedCallback?.onAnimationFinished()
            }
        }

        /** Tries to start the animation on the main thread and returns whether it succeeded. */
        @BinderThread
        private fun startAnimation(performAnimation: (AnimationDelegate) -> Unit): Boolean {
            val delegate = delegate
            return if (delegate != null) {
                mainExecutor.execute { performAnimation(delegate) }
                true
            } else {
                // Animation started too late and timed out already.
                Log.i(TAG, "startAnimation called after completion")
                false
            }
        }

        @BinderThread
        override fun onAnimationCancelled() {
            val delegate = delegate
            if (delegate != null) {
                mainExecutor.execute { delegate.onAnimationCancelled() }
            } else {
                Log.wtf(TAG, "onAnimationCancelled called after completion")
            }
        }

        /**
         * Posts the default animation timeouts. Since this only applies to ephemeral launches, this
         * method is a no-op if [controller] is not defined.
         */
        @VisibleForTesting
        @UiThread
        fun postTimeouts() {
            controller?.let { maybeSetUp(it) }
            delegate?.postTimeouts()
        }

        @AnyThread
        private fun maybeSetUp(controller: Controller) {
            if (delegate != null) return
            createDelegate(controller)
        }

        @AnyThread
        private suspend fun setUp(createController: suspend () -> Controller) {
            val controller = createController()
            createDelegate(controller)
        }

        @AnyThread
        private fun createDelegate(controller: Controller) {
            delegate =
                AnimationDelegate(
                    mainExecutor,
                    controller,
                    callback,
                    DelegatingAnimationCompletionListener(listener, this::dispose),
                    transitionAnimator,
                    disableWmTimeout,
                    skipReparentTransaction,
                )
        }

        @AnyThread
        fun dispose() {
            // Drop references to animation controller once we're done with the animation to avoid
            // leaking in case of ephemeral launches. When long-lived, [controllerFactory] will
            // still be around to create new controllers.
            mainExecutor.execute {
                delegate = null
                controller = null
            }
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

        /**
         * Whether we should disable the reparent transaction that puts the opening/closing window
         * above the view's window. This should be set to true in tests only, where we can't
         * currently use a valid leash.
         *
         * TODO(b/397180418): Remove this flag when we don't have the RemoteAnimation wrapper
         *   anymore and we can just inject a fake transaction.
         */
        private val skipReparentTransaction: Boolean = false,
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
         * Whether the opening/closing window needs to reparented to the view's window at the
         * beginning of the animation. Since we don't always do this, we need to keep track of it in
         * order to have the rest of the animation behave correctly.
         */
        var reparent = false

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
                    "$LONG_TRANSITION_TIMEOUT",
            )
        }

        init {
            // We do this check here to cover all entry points, including Launcher which doesn't
            // call startIntentWithAnimation()
            if (!controller.isLaunching) assertReturnAnimations()
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
            callback: IRemoteAnimationFinishedCallback?,
        ) {
            val window = setUpAnimation(apps, callback) ?: return

            if (controller.windowAnimatorState == null || !longLivedReturnAnimationsEnabled()) {
                startAnimation(window, iCallback = callback)
            } else {
                // If a [controller.windowAnimatorState] exists, treat this like a takeover.
                takeOverAnimationInternal(
                    window,
                    startWindowState = null,
                    startTransaction = null,
                    callback,
                )
            }
        }

        @UiThread
        internal fun takeOverAnimation(
            apps: Array<out RemoteAnimationTarget>?,
            startWindowStates: Array<WindowAnimationState>,
            startTransaction: SurfaceControl.Transaction,
            callback: IRemoteAnimationFinishedCallback?,
        ) {
            val window = setUpAnimation(apps, callback) ?: return
            val startWindowState = startWindowStates[apps!!.indexOf(window)]
            takeOverAnimationInternal(window, startWindowState, startTransaction, callback)
        }

        private fun takeOverAnimationInternal(
            window: RemoteAnimationTarget,
            startWindowState: WindowAnimationState?,
            startTransaction: SurfaceControl.Transaction?,
            callback: IRemoteAnimationFinishedCallback?,
        ) {
            val useSpring =
                !controller.isLaunching && startWindowState != null && startTransaction != null
            startAnimation(window, useSpring, startWindowState, startTransaction, callback)
        }

        @UiThread
        private fun setUpAnimation(
            apps: Array<out RemoteAnimationTarget>?,
            callback: IRemoteAnimationFinishedCallback?,
        ): RemoteAnimationTarget? {
            removeTimeouts()

            // The animation was started too late and we already notified the controller that it
            // timed out.
            if (timedOut) {
                callback?.invoke()
                return null
            }

            // This should not happen, but let's make sure we don't start the animation if it was
            // cancelled before and we already notified the controller.
            if (cancelled) {
                return null
            }

            val window = findTargetWindowIfPossible(apps)
            if (window == null) {
                Log.i(TAG, "Aborting the animation as no window is opening")
                callback?.invoke()

                if (DEBUG_TRANSITION_ANIMATION) {
                    Log.d(
                        TAG,
                        "Calling controller.onTransitionAnimationCancelled() [no window opening]",
                    )
                }
                controller.onTransitionAnimationCancelled()
                listener?.onTransitionAnimationCancelled()
                return null
            }

            return window
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
                    if (returnAnimationsEnabled()) {
                        // If the controller contains a cookie, _only_ match if either the
                        // candidate contains the matching cookie, or a component is also
                        // defined and is a match.
                        if (
                            controller.transitionCookie != null &&
                                it.taskInfo?.launchCookies?.contains(controller.transitionCookie) !=
                                    true &&
                                (controller.component == null ||
                                    it.taskInfo?.topActivity != controller.component)
                        ) {
                            continue
                        }
                    }

                    if (
                        candidate == null || !it.hasAnimatingParent && candidate.hasAnimatingParent
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
                }
            }

            return candidate
        }

        private fun startAnimation(
            window: RemoteAnimationTarget,
            useSpring: Boolean = false,
            startingWindowState: WindowAnimationState? = null,
            startTransaction: SurfaceControl.Transaction? = null,
            iCallback: IRemoteAnimationFinishedCallback? = null,
        ) {
            if (TransitionAnimator.DEBUG) {
                Log.d(TAG, "Remote animation started")
            }

            val windowBounds = window.screenSpaceBounds
            val endState =
                if (controller.isLaunching) {
                    controller.windowAnimatorState?.toTransitionState()
                        ?: TransitionAnimator.State(
                                top = windowBounds.top,
                                bottom = windowBounds.bottom,
                                left = windowBounds.left,
                                right = windowBounds.right,
                            )
                            .apply {
                                // TODO(b/184121838): We should somehow get the top and bottom
                                // radius of the window instead of recomputing isExpandingFullyAbove
                                // here.
                                getWindowRadius(
                                        transitionAnimator.isExpandingFullyAbove(
                                            controller.transitionContainer,
                                            this,
                                        )
                                    )
                                    .let {
                                        topCornerRadius = it
                                        bottomCornerRadius = it
                                    }
                            }
                } else {
                    controller.createAnimatorState()
                }
            val windowBackgroundColor =
                if (window.isTranslucent) {
                    Color.TRANSPARENT
                } else {
                    window.taskInfo?.let { callback.getBackgroundColor(it) }
                        ?: window.backgroundColor
                }

            val isExpandingFullyAbove =
                transitionAnimator.isExpandingFullyAbove(controller.transitionContainer, endState)
            val windowState = startingWindowState ?: controller.windowAnimatorState

            // We animate the opening window and delegate the view expansion to [this.controller].
            val delegate = this.controller
            val controller =
                object : Controller by delegate {
                    override fun createAnimatorState(): TransitionAnimator.State {
                        if (isLaunching) {
                            return delegate.createAnimatorState()
                        } else if (!longLivedReturnAnimationsEnabled()) {
                            return delegate.windowAnimatorState?.toTransitionState()
                                ?: getWindowRadius(isExpandingFullyAbove).let {
                                    TransitionAnimator.State(
                                        top = windowBounds.top,
                                        bottom = windowBounds.bottom,
                                        left = windowBounds.left,
                                        right = windowBounds.right,
                                        topCornerRadius = it,
                                        bottomCornerRadius = it,
                                    )
                                }
                        }

                        // TODO(b/323863002): use the timestamp and velocity to update the initial
                        //   position.
                        val bounds = windowState?.bounds
                        val left: Int = bounds?.left?.toInt() ?: windowBounds.left
                        val top: Int = bounds?.top?.toInt() ?: windowBounds.top
                        val right: Int = bounds?.right?.toInt() ?: windowBounds.right
                        val bottom: Int = bounds?.bottom?.toInt() ?: windowBounds.bottom

                        val width = windowBounds.right - windowBounds.left
                        val height = windowBounds.bottom - windowBounds.top
                        // Scale the window. We use the max of (widthRatio, heightRatio) so that
                        // there is no blank space on any side.
                        val widthRatio = (right - left).toFloat() / width
                        val heightRatio = (bottom - top).toFloat() / height
                        val startScale = maxOf(widthRatio, heightRatio)

                        val maybeRadius = windowState?.topLeftRadius
                        val windowRadius =
                            if (maybeRadius != null) {
                                maybeRadius * startScale
                            } else {
                                getWindowRadius(isExpandingFullyAbove)
                            }

                        return TransitionAnimator.State(
                            top = top,
                            bottom = bottom,
                            left = left,
                            right = right,
                            topCornerRadius = windowRadius,
                            bottomCornerRadius = windowRadius,
                        )
                    }

                    override fun onTransitionAnimationStart(isExpandingFullyAbove: Boolean) {
                        listener?.onTransitionAnimationStart()

                        if (DEBUG_TRANSITION_ANIMATION) {
                            Log.d(
                                TAG,
                                "Calling controller.onTransitionAnimationStart(" +
                                    "isExpandingFullyAbove=$isExpandingFullyAbove) " +
                                    "[controller=$delegate]",
                            )
                        }

                        // We only reparent launch animations. In current integrations, returns are
                        // not affected by the issue solved by reparenting, and they present
                        // additional problems when the view lives in the Status Bar.
                        // TODO(b/397646693): remove this exception.
                        val isEligibleForReparenting = controller.isLaunching
                        val viewRoot = controller.transitionContainer.viewRootImpl
                        val skipReparenting =
                            skipReparentTransaction || !window.leash.isValid || viewRoot == null
                        if (
                            moveTransitionAnimationLayer() &&
                                isEligibleForReparenting &&
                                !skipReparenting
                        ) {
                            // Ensure that the launching window is rendered above the view's window,
                            // so it is not obstructed.
                            // Note that it is possible that the leash gets released between the
                            // check above and the call below. For this reason, we still need to
                            // wrap the transaction in a try/catch and set the value of [reparent]
                            // accordingly.
                            // TODO(b/397180418): re-use the start transaction once the
                            //  RemoteAnimation wrapper is cleaned up.
                            try {
                                SurfaceControl.Transaction().use {
                                    it.reparent(window.leash, viewRoot.surfaceControl).apply()
                                }
                                reparent = true
                            } catch (e: Exception) {
                                Log.e(TAG, "Failed to reparent transition leash: already released")
                                reparent = false
                            }
                        }

                        if (startTransaction != null) {
                            // Calling applyStateToWindow() here avoids skipping a frame when taking
                            // over an animation.
                            applyStateToWindow(
                                window,
                                createAnimatorState(),
                                linearProgress = 0f,
                                useSpring,
                                startTransaction,
                            )
                        }

                        delegate.onTransitionAnimationStart(isExpandingFullyAbove)
                    }

                    override fun onTransitionAnimationEnd(isExpandingFullyAbove: Boolean) {
                        listener?.onTransitionAnimationEnd()
                        if (!instantHideShade()) iCallback?.invoke()

                        if (reparent) {
                            val cleanUpTransitionLeash: () -> Unit = {
                                // Relying on this try-catch is not great, but the existence of
                                // RemoteAnimationRunnerCompat means that we can't reliably
                                // assume that the transaction will be executed while the leash
                                // is still valid.
                                // TODO(b/397180418): remove once the RemoteAnimation wrapper is
                                //  cleaned up.
                                try {
                                    // Reparent to null to avoid leaking the transition leash.
                                    // TODO(b/397180418): shouldn't be needed anymore once the
                                    //  RemoteAnimation wrapper is cleaned up.
                                    SurfaceControl.Transaction().use {
                                        it.reparent(window.leash, null)
                                        it.apply(/* sync */ false)
                                    }
                                } catch (e: IllegalStateException) {
                                    Log.e(
                                        TAG,
                                        "Failed to clean up transition leash: already released",
                                    )
                                }
                            }
                            if (animationLibraryDelayLeashCleanup()) {
                                // This cleanup is not time-sensitive as it is just to avoid leaking
                                // leashes. By delaying it, we make (reasonably) sure that the
                                // finish callback above is executed before the reparent
                                // transaction, which avoids flaky flickers. Unfortunately both are
                                // async, so we need to resort to this hacky solution. Fortunately
                                // none of this will be necessary as soon as b/397180418 is done.
                                Handler(Looper.getMainLooper())
                                    .postDelayed(cleanUpTransitionLeash, 500L)
                            } else {
                                cleanUpTransitionLeash()
                            }
                        }

                        if (DEBUG_TRANSITION_ANIMATION) {
                            Log.d(
                                TAG,
                                "Calling controller.onTransitionAnimationEnd(" +
                                    "isExpandingFullyAbove=$isExpandingFullyAbove) " +
                                    "[controller=$delegate]",
                            )
                        }
                        delegate.onTransitionAnimationEnd(isExpandingFullyAbove)

                        if (instantHideShade()) iCallback?.invoke()
                    }

                    override fun onTransitionAnimationProgress(
                        state: TransitionAnimator.State,
                        progress: Float,
                        linearProgress: Float,
                    ) {
                        applyStateToWindow(window, state, linearProgress, useSpring)

                        listener?.onTransitionAnimationProgress(linearProgress)
                        delegate.onTransitionAnimationProgress(state, progress, linearProgress)
                    }
                }
            val velocityPxPerS =
                if (longLivedReturnAnimationsEnabled() && windowState?.velocityPxPerMs != null) {
                    val xVelocityPxPerS = windowState.velocityPxPerMs.x * 1000
                    val yVelocityPxPerS = windowState.velocityPxPerMs.y * 1000
                    PointF(xVelocityPxPerS, yVelocityPxPerS)
                } else if (useSpring) {
                    PointF(0f, 0f)
                } else {
                    null
                }
            val shouldFadeWindowBackgroundLayer = {
                if (reparent) {
                    false
                } else {
                    !controller.isBelowAnimatingWindow
                }
            }
            animation =
                transitionAnimator.startAnimation(
                    controller,
                    endState,
                    windowBackgroundColor,
                    shouldFadeWindowBackgroundLayer = shouldFadeWindowBackgroundLayer,
                    drawHole = !controller.isBelowAnimatingWindow,
                    startVelocity = velocityPxPerS,
                    startFrameTime = windowState?.timestamp ?: -1,
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
            useSpring: Boolean,
            transaction: SurfaceControl.Transaction? = null,
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
                windowCropF.bottom.roundToInt(),
            )

            val interpolators: TransitionAnimator.Interpolators
            val windowProgress: Float

            if (useSpring) {
                val windowAnimationDelay: Float
                val windowAnimationDuration: Float
                if (controller.isLaunching) {
                    windowAnimationDelay = SPRING_TIMINGS.contentAfterFadeInDelay
                    windowAnimationDuration = SPRING_TIMINGS.contentAfterFadeInDuration
                } else {
                    windowAnimationDelay = SPRING_TIMINGS.contentBeforeFadeOutDelay
                    windowAnimationDuration = SPRING_TIMINGS.contentBeforeFadeOutDuration
                }

                interpolators = SPRING_INTERPOLATORS
                windowProgress =
                    TransitionAnimator.getProgress(
                        linearProgress,
                        windowAnimationDelay,
                        windowAnimationDuration,
                    )
            } else {
                val windowAnimationDelay: Long
                val windowAnimationDuration: Long
                if (controller.isLaunching) {
                    windowAnimationDelay = TIMINGS.contentAfterFadeInDelay
                    windowAnimationDuration = TIMINGS.contentAfterFadeInDuration
                } else {
                    windowAnimationDelay = TIMINGS.contentBeforeFadeOutDelay
                    windowAnimationDuration = TIMINGS.contentBeforeFadeOutDuration
                }

                interpolators = INTERPOLATORS
                windowProgress =
                    TransitionAnimator.getProgress(
                        TIMINGS,
                        linearProgress,
                        windowAnimationDelay,
                        windowAnimationDuration,
                    )
            }

            // The alpha of the opening window. If it opens above the expandable, then it should
            // fade in progressively. Otherwise, it should be fully opaque and will be progressively
            // revealed as the window background color layer above the window fades out.
            val alpha =
                if (reparent || controller.isBelowAnimatingWindow) {
                    if (controller.isLaunching) {
                        interpolators.contentAfterFadeInInterpolator.getInterpolation(
                            windowProgress
                        )
                    } else {
                        1 -
                            interpolators.contentBeforeFadeOutInterpolator.getInterpolation(
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
            if (transaction != null) params.withMergeTransaction(transaction)

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
                    "Calling controller.onTransitionAnimationCancelled() [animation timed out]",
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
            includeTakeover: Boolean,
        ) {
            shellTransitions?.registerRemote(filter, remoteTransition)
            iShellTransitions?.registerRemote(filter, remoteTransition)
            if (includeTakeover) {
                shellTransitions?.registerRemoteForTakeover(filter, remoteTransition)
                iShellTransitions?.registerRemoteForTakeover(filter, remoteTransition)
            }
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
