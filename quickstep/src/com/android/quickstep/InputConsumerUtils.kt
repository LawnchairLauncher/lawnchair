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
package com.android.quickstep

import android.content.Context
import android.view.MotionEvent
import androidx.annotation.VisibleForTesting
import com.android.launcher3.anim.AnimatedFloat
import com.android.launcher3.statemanager.BaseState
import com.android.launcher3.statemanager.StatefulContainer
import com.android.launcher3.taskbar.TaskbarManager
import com.android.launcher3.util.LockedUserState.Companion.get
import com.android.quickstep.inputconsumers.AccessibilityInputConsumer
import com.android.quickstep.inputconsumers.AssistantInputConsumer
import com.android.quickstep.inputconsumers.BubbleBarInputConsumer
import com.android.quickstep.inputconsumers.DeviceLockedInputConsumer
import com.android.quickstep.inputconsumers.NavHandleLongPressInputConsumer
import com.android.quickstep.inputconsumers.OneHandedModeInputConsumer
import com.android.quickstep.inputconsumers.OtherActivityInputConsumer
import com.android.quickstep.inputconsumers.OverviewInputConsumer
import com.android.quickstep.inputconsumers.OverviewWithoutFocusInputConsumer
import com.android.quickstep.inputconsumers.ProgressDelegateInputConsumer
import com.android.quickstep.inputconsumers.ResetGestureInputConsumer
import com.android.quickstep.inputconsumers.ScreenPinnedInputConsumer
import com.android.quickstep.inputconsumers.SysUiOverlayInputConsumer
import com.android.quickstep.inputconsumers.TaskbarUnstashInputConsumer
import com.android.quickstep.inputconsumers.TrackpadStatusBarInputConsumer
import com.android.quickstep.util.ActiveGestureErrorDetector
import com.android.quickstep.util.ActiveGestureLog
import com.android.quickstep.util.ActiveGestureLog.CompoundString
import com.android.quickstep.util.ActiveGestureProtoLogProxy
import com.android.quickstep.views.RecentsViewContainer
import com.android.systemui.shared.system.InputChannelCompat
import com.android.systemui.shared.system.InputMonitorCompat
import com.android.wm.shell.Flags
import java.util.function.Consumer
import java.util.function.Function

/** Utility class for creating input consumers. */
object InputConsumerUtils {
    private const val SUBSTRING_PREFIX = "; "
    private const val NEWLINE_PREFIX = "\n\t\t\t-> "

    @JvmStatic
    fun <S : BaseState<S>, T> newConsumer(
        context: Context,
        userUnlocked: Boolean,
        overviewComponentObserver: OverviewComponentObserver,
        deviceState: RecentsAnimationDeviceState,
        previousGestureState: GestureState,
        gestureState: GestureState,
        taskAnimationManager: TaskAnimationManager,
        inputMonitorCompat: InputMonitorCompat,
        swipeUpHandlerFactory: AbsSwipeUpHandler.Factory,
        onCompleteCallback: Consumer<OtherActivityInputConsumer>,
        inputEventReceiver: InputChannelCompat.InputEventReceiver,
        taskbarManager: TaskbarManager,
        swipeUpProxyProvider: Function<GestureState?, AnimatedFloat?>,
        overviewCommandHelper: OverviewCommandHelper,
        event: MotionEvent,
    ): InputConsumer where T : RecentsViewContainer, T : StatefulContainer<S> {
        val tac = taskbarManager.currentActivityContext
        val bubbleControllers = tac?.bubbleControllers
        if (bubbleControllers != null && BubbleBarInputConsumer.isEventOnBubbles(tac, event)) {
            val consumer: InputConsumer =
                BubbleBarInputConsumer(
                    context,
                    gestureState.displayId,
                    bubbleControllers,
                    inputMonitorCompat,
                )
            logInputConsumerSelectionReason(
                consumer,
                newCompoundString("event is on bubbles, creating new input consumer"),
            )
            return consumer
        }
        val progressProxy = swipeUpProxyProvider.apply(gestureState)
        if (progressProxy != null) {
            val consumer: InputConsumer =
                ProgressDelegateInputConsumer(
                    context,
                    taskAnimationManager,
                    gestureState,
                    inputMonitorCompat,
                    progressProxy,
                )

            logInputConsumerSelectionReason(
                consumer,
                newCompoundString(
                    "mSwipeUpProxyProvider has been set, using ProgressDelegateInputConsumer"
                ),
            )

            return consumer
        }

        val canStartSystemGesture =
            if (gestureState.isTrackpadGesture) deviceState.canStartTrackpadGesture()
            else deviceState.canStartSystemGesture()

        if (!get(context).isUserUnlocked) {
            val reasonString = newCompoundString("device locked")
            val consumer =
                if (canStartSystemGesture) {
                    // This handles apps launched in direct boot mode (e.g. dialer) as well as apps
                    // launched while device is locked even after exiting direct boot mode (e.g.
                    // camera).
                    createDeviceLockedInputConsumer(
                        context,
                        userUnlocked,
                        taskbarManager,
                        deviceState,
                        gestureState,
                        taskAnimationManager,
                        inputMonitorCompat,
                        reasonString.append("%scan start system gesture", SUBSTRING_PREFIX),
                    )
                } else {
                    getDefaultInputConsumer(
                        gestureState.displayId,
                        userUnlocked,
                        taskAnimationManager,
                        taskbarManager,
                        reasonString.append("%scannot start system gesture", SUBSTRING_PREFIX),
                    )
                }
            logInputConsumerSelectionReason(consumer, reasonString)
            return consumer
        }

        var reasonString: CompoundString
        var base: InputConsumer
        // When there is an existing recents animation running, bypass systemState check as this is
        // a followup gesture and the first gesture started in a valid system state.
        if (canStartSystemGesture || previousGestureState.isRecentsAnimationRunning) {
            reasonString =
                newCompoundString(
                    if (canStartSystemGesture)
                        "can start system gesture, trying to use base consumer"
                    else "recents animation was running, trying to use base consumer"
                )
            base =
                newBaseConsumer<S, T>(
                    context,
                    userUnlocked,
                    taskbarManager,
                    overviewComponentObserver,
                    deviceState,
                    previousGestureState,
                    gestureState,
                    taskAnimationManager,
                    inputMonitorCompat,
                    swipeUpHandlerFactory,
                    onCompleteCallback,
                    inputEventReceiver,
                    event,
                    reasonString,
                )
        } else {
            reasonString =
                newCompoundString(
                    "cannot start system gesture and recents " +
                        "animation was not running, trying to use default input consumer"
                )
            base =
                getDefaultInputConsumer(
                    gestureState.displayId,
                    userUnlocked,
                    taskAnimationManager,
                    taskbarManager,
                    reasonString,
                )
        }
        if (deviceState.isGesturalNavMode || gestureState.isTrackpadGesture) {
            handleOrientationSetup(base)
        }
        if (deviceState.isFullyGesturalNavMode || gestureState.isTrackpadGesture) {
            val reasonPrefix =
                "device is in gesture navigation mode or 3-button mode with a trackpad gesture"
            if (deviceState.canTriggerAssistantAction(event)) {
                reasonString.append(
                    "%s%s%sgesture can trigger the assistant, " +
                        "trying to use assistant input consumer",
                    NEWLINE_PREFIX,
                    reasonPrefix,
                    SUBSTRING_PREFIX,
                )
                base =
                    tryCreateAssistantInputConsumer(
                        context,
                        deviceState,
                        inputMonitorCompat,
                        base,
                        gestureState,
                        event,
                        reasonString,
                    )
            }

            // If Taskbar is present, we listen for swipe or cursor hover events to unstash it.
            if (tac != null && base !is AssistantInputConsumer) {
                // Present always on large screen or on small screen w/ flag
                val useTaskbarConsumer =
                    (tac.deviceProfile.isTaskbarPresent &&
                        !tac.isPhoneMode &&
                        !tac.isInStashedLauncherState)
                if (canStartSystemGesture && useTaskbarConsumer) {
                    reasonString.append(
                        "%s%s%sTaskbarActivityContext != null, " +
                            "using TaskbarUnstashInputConsumer",
                        NEWLINE_PREFIX,
                        reasonPrefix,
                        SUBSTRING_PREFIX,
                    )
                    base =
                        TaskbarUnstashInputConsumer(
                            context,
                            base,
                            inputMonitorCompat,
                            tac,
                            overviewCommandHelper,
                            gestureState,
                        )
                }
            }
            if (Flags.enableBubblesLongPressNavHandle()) {
                // Create bubbles input consumer before NavHandleLongPressInputConsumer.
                // This allows for nav handle to fall back to bubbles.
                if (deviceState.isBubblesExpanded) {
                    reasonString =
                        newCompoundString(reasonPrefix)
                            .append(
                                "%sbubbles expanded, trying to use default input consumer",
                                SUBSTRING_PREFIX,
                            )
                    // Bubbles can handle home gesture itself.
                    base =
                        getDefaultInputConsumer(
                            gestureState.displayId,
                            userUnlocked,
                            taskAnimationManager,
                            taskbarManager,
                            reasonString,
                        )
                }
            }

            val navHandle = tac?.navHandle ?: SystemUiProxy.INSTANCE[context]
            if (
                canStartSystemGesture &&
                    !previousGestureState.isRecentsAnimationRunning &&
                    navHandle.canNavHandleBeLongPressed() &&
                    !ignoreThreeFingerTrackpadForNavHandleLongPress(gestureState)
            ) {
                reasonString.append(
                    "%s%s%sNot running recents animation, ",
                    NEWLINE_PREFIX,
                    reasonPrefix,
                    SUBSTRING_PREFIX,
                )
                if (tac != null && tac.navHandle.canNavHandleBeLongPressed()) {
                    reasonString.append("stashed handle is long-pressable, ")
                }
                reasonString.append("using NavHandleLongPressInputConsumer")
                base =
                    NavHandleLongPressInputConsumer(
                        context,
                        base,
                        inputMonitorCompat,
                        deviceState,
                        navHandle,
                        gestureState,
                    )
            }

            if (!Flags.enableBubblesLongPressNavHandle()) {
                // Continue overriding nav handle input consumer with bubbles
                if (deviceState.isBubblesExpanded) {
                    reasonString =
                        newCompoundString(reasonPrefix)
                            .append(
                                "%sbubbles expanded, trying to use default input consumer",
                                SUBSTRING_PREFIX,
                            )
                    // Bubbles can handle home gesture itself.
                    base =
                        getDefaultInputConsumer(
                            gestureState.displayId,
                            userUnlocked,
                            taskAnimationManager,
                            taskbarManager,
                            reasonString,
                        )
                }
            }

            if (deviceState.isSystemUiDialogShowing) {
                reasonString =
                    newCompoundString(reasonPrefix)
                        .append(
                            "%ssystem dialog is showing, using SysUiOverlayInputConsumer",
                            SUBSTRING_PREFIX,
                        )
                base =
                    SysUiOverlayInputConsumer(
                        context,
                        gestureState.displayId,
                        deviceState,
                        inputMonitorCompat,
                    )
            }

            if (
                gestureState.isTrackpadGesture &&
                    canStartSystemGesture &&
                    !previousGestureState.isRecentsAnimationRunning
            ) {
                reasonString =
                    newCompoundString(reasonPrefix)
                        .append(
                            "%sTrackpad 3-finger gesture, using TrackpadStatusBarInputConsumer",
                            SUBSTRING_PREFIX,
                        )
                base =
                    TrackpadStatusBarInputConsumer(
                        context,
                        gestureState.displayId,
                        base,
                        inputMonitorCompat,
                    )
            }

            if (deviceState.isScreenPinningActive) {
                reasonString =
                    newCompoundString(reasonPrefix)
                        .append(
                            "%sscreen pinning is active, using ScreenPinnedInputConsumer",
                            SUBSTRING_PREFIX,
                        )
                // Note: we only allow accessibility to wrap this, and it replaces the previous
                // base input consumer (which should be NO_OP anyway since topTaskLocked == true).
                base = ScreenPinnedInputConsumer(context, gestureState)
            }

            if (deviceState.canTriggerOneHandedAction(event)) {
                reasonString.append(
                    "%s%s%sgesture can trigger one handed mode, " +
                        "using OneHandedModeInputConsumer",
                    NEWLINE_PREFIX,
                    reasonPrefix,
                    SUBSTRING_PREFIX,
                )
                base =
                    OneHandedModeInputConsumer(
                        context,
                        gestureState.displayId,
                        deviceState,
                        base,
                        inputMonitorCompat,
                    )
            }

            if (deviceState.isAccessibilityMenuAvailable) {
                reasonString.append(
                    "%s%s%saccessibility menu is available, using AccessibilityInputConsumer",
                    NEWLINE_PREFIX,
                    reasonPrefix,
                    SUBSTRING_PREFIX,
                )
                base =
                    AccessibilityInputConsumer(
                        context,
                        gestureState.displayId,
                        deviceState,
                        base,
                        inputMonitorCompat,
                    )
            }
        } else {
            val reasonPrefix = "device is not in gesture navigation mode"
            if (deviceState.isScreenPinningActive) {
                reasonString =
                    newCompoundString(reasonPrefix)
                        .append(
                            "%sscreen pinning is active, trying to use default input consumer",
                            SUBSTRING_PREFIX,
                        )
                base =
                    getDefaultInputConsumer(
                        gestureState.displayId,
                        userUnlocked,
                        taskAnimationManager,
                        taskbarManager,
                        reasonString,
                    )
            }

            if (deviceState.canTriggerOneHandedAction(event)) {
                reasonString.append(
                    "%s%s%sgesture can trigger one handed mode, " +
                        "using OneHandedModeInputConsumer",
                    NEWLINE_PREFIX,
                    reasonPrefix,
                    SUBSTRING_PREFIX,
                )
                base =
                    OneHandedModeInputConsumer(
                        context,
                        gestureState.displayId,
                        deviceState,
                        base,
                        inputMonitorCompat,
                    )
            }
        }
        logInputConsumerSelectionReason(base, reasonString)
        return base
    }

    @JvmStatic
    fun tryCreateAssistantInputConsumer(
        context: Context,
        deviceState: RecentsAnimationDeviceState,
        inputMonitorCompat: InputMonitorCompat,
        gestureState: GestureState,
        motionEvent: MotionEvent,
    ): InputConsumer {
        return tryCreateAssistantInputConsumer(
            context,
            deviceState,
            inputMonitorCompat,
            InputConsumer.createNoOpInputConsumer(gestureState.displayId),
            gestureState,
            motionEvent,
            CompoundString.NO_OP,
        )
    }

    private fun tryCreateAssistantInputConsumer(
        context: Context,
        deviceState: RecentsAnimationDeviceState,
        inputMonitorCompat: InputMonitorCompat,
        base: InputConsumer,
        gestureState: GestureState,
        motionEvent: MotionEvent,
        reasonString: CompoundString,
    ): InputConsumer {
        return if (deviceState.isGestureBlockedTask(gestureState.runningTask)) {
            reasonString.append(
                "%sis gesture-blocked task, using base input consumer",
                SUBSTRING_PREFIX,
            )
            base
        } else {
            reasonString.append("%susing AssistantInputConsumer", SUBSTRING_PREFIX)
            AssistantInputConsumer(
                context,
                gestureState,
                base,
                inputMonitorCompat,
                deviceState,
                motionEvent,
            )
        }
    }

    @VisibleForTesting
    @JvmStatic
    fun <S : BaseState<S>, T> newBaseConsumer(
        context: Context,
        userUnlocked: Boolean,
        taskbarManager: TaskbarManager,
        overviewComponentObserver: OverviewComponentObserver,
        deviceState: RecentsAnimationDeviceState,
        previousGestureState: GestureState,
        gestureState: GestureState,
        taskAnimationManager: TaskAnimationManager,
        inputMonitorCompat: InputMonitorCompat,
        swipeUpHandlerFactory: AbsSwipeUpHandler.Factory,
        onCompleteCallback: Consumer<OtherActivityInputConsumer>,
        inputEventReceiver: InputChannelCompat.InputEventReceiver,
        event: MotionEvent,
        reasonString: CompoundString,
    ): InputConsumer where T : RecentsViewContainer, T : StatefulContainer<S> {
        if (deviceState.isKeyguardShowingOccluded) {
            // This handles apps showing over the lockscreen (e.g. camera)
            return createDeviceLockedInputConsumer(
                context,
                userUnlocked,
                taskbarManager,
                deviceState,
                gestureState,
                taskAnimationManager,
                inputMonitorCompat,
                reasonString.append(
                    "%skeyguard is showing occluded, " +
                        "trying to use device locked input consumer",
                    SUBSTRING_PREFIX,
                ),
            )
        }

        reasonString.append("%skeyguard is not showing occluded", SUBSTRING_PREFIX)

        val runningTask = gestureState.runningTask
        // Use overview input consumer for sharesheets on top of home.
        val forceOverviewInputConsumer =
            gestureState.getContainerInterface<S, T>().isStarted() &&
                runningTask != null &&
                runningTask.isRootChooseActivity

        if (!Flags.enableShellTopTaskTracking()) {
            // In the case where we are in an excluded, translucent overlay, ignore it and treat the
            // running activity as the task behind the overlay.
            val otherVisibleTask = runningTask?.visibleNonExcludedTask
            if (otherVisibleTask != null) {
                ActiveGestureProtoLogProxy.logUpdateGestureStateRunningTask(
                    otherVisibleTask.packageName ?: "MISSING",
                    runningTask.packageName ?: "MISSING",
                )
                gestureState.updateRunningTask(otherVisibleTask)
            }
        }

        val previousGestureAnimatedToLauncher =
            (previousGestureState.isRunningAnimationToLauncher ||
                deviceState.isPredictiveBackToHomeInProgress)
        // with shell-transitions, home is resumed during recents animation, so
        // explicitly check against recents animation too.
        val launcherResumedThroughShellTransition =
            (gestureState.getContainerInterface<S, T>().isResumed() &&
                !previousGestureState.isRecentsAnimationRunning)
        // If a task fragment within Launcher is resumed
        val launcherChildActivityResumed =
            (com.android.launcher3.Flags.useActivityOverlay() &&
                runningTask != null &&
                runningTask.isHomeTask &&
                overviewComponentObserver.isHomeAndOverviewSameActivity &&
                !launcherResumedThroughShellTransition &&
                !previousGestureState.isRecentsAnimationRunning)

        return if (gestureState.getContainerInterface<S, T>().isInLiveTileMode()) {
            createOverviewInputConsumer<S, T>(
                userUnlocked,
                taskAnimationManager,
                taskbarManager,
                deviceState,
                inputMonitorCompat,
                previousGestureState,
                gestureState,
                event,
                reasonString.append(
                    "%sis in live tile mode, trying to use overview input consumer",
                    SUBSTRING_PREFIX,
                ),
            )
        } else if (runningTask == null) {
            getDefaultInputConsumer(
                gestureState.displayId,
                userUnlocked,
                taskAnimationManager,
                taskbarManager,
                reasonString.append("%srunning task == null", SUBSTRING_PREFIX),
            )
        } else if (
            previousGestureAnimatedToLauncher ||
                launcherResumedThroughShellTransition ||
                forceOverviewInputConsumer
        ) {
            createOverviewInputConsumer<S, T>(
                userUnlocked,
                taskAnimationManager,
                taskbarManager,
                deviceState,
                inputMonitorCompat,
                previousGestureState,
                gestureState,
                event,
                reasonString.append(
                    if (previousGestureAnimatedToLauncher)
                        ("%sprevious gesture animated to launcher, " +
                            "trying to use overview input consumer")
                    else
                        (if (launcherResumedThroughShellTransition)
                            ("%slauncher resumed through a shell transition, " +
                                "trying to use overview input consumer")
                        else
                            ("%sforceOverviewInputConsumer == true, " +
                                "trying to use overview input consumer")),
                    SUBSTRING_PREFIX,
                ),
            )
        } else if (deviceState.isGestureBlockedTask(runningTask) || launcherChildActivityResumed) {
            getDefaultInputConsumer(
                gestureState.displayId,
                userUnlocked,
                taskAnimationManager,
                taskbarManager,
                reasonString.append(
                    if (launcherChildActivityResumed)
                        "%sis launcher child-task, trying to use default input consumer"
                    else "%sis gesture-blocked task, trying to use default input consumer",
                    SUBSTRING_PREFIX,
                ),
            )
        } else {
            reasonString.append("%susing OtherActivityInputConsumer", SUBSTRING_PREFIX)
            createOtherActivityInputConsumer<S, T>(
                context,
                swipeUpHandlerFactory,
                overviewComponentObserver,
                deviceState,
                taskAnimationManager,
                inputMonitorCompat,
                onCompleteCallback,
                inputEventReceiver,
                gestureState,
                event,
            )
        }
    }

    private fun createDeviceLockedInputConsumer(
        context: Context,
        userUnlocked: Boolean,
        taskbarManager: TaskbarManager,
        deviceState: RecentsAnimationDeviceState,
        gestureState: GestureState,
        taskAnimationManager: TaskAnimationManager,
        inputMonitorCompat: InputMonitorCompat,
        reasonString: CompoundString,
    ): InputConsumer {
        return if (
            (deviceState.isFullyGesturalNavMode || gestureState.isTrackpadGesture) &&
                gestureState.runningTask != null
        ) {
            reasonString.append(
                "%sdevice is in gesture nav mode or 3-button mode with a trackpad " +
                    "gesture and running task != null, using DeviceLockedInputConsumer",
                SUBSTRING_PREFIX,
            )
            DeviceLockedInputConsumer(
                context,
                deviceState,
                taskAnimationManager,
                gestureState,
                inputMonitorCompat,
            )
        } else {
            getDefaultInputConsumer(
                gestureState.displayId,
                userUnlocked,
                taskAnimationManager,
                taskbarManager,
                reasonString.append(
                    if (deviceState.isFullyGesturalNavMode || gestureState.isTrackpadGesture)
                        "%srunning task == null, trying to use default input consumer"
                    else
                        ("%sdevice is not in gesture nav mode and it's not a trackpad gesture," +
                            " trying to use default input consumer"),
                    SUBSTRING_PREFIX,
                ),
            )
        }
    }

    private fun <S : BaseState<S>, T> createOverviewInputConsumer(
        userUnlocked: Boolean,
        taskAnimationManager: TaskAnimationManager,
        taskbarManager: TaskbarManager,
        deviceState: RecentsAnimationDeviceState,
        inputMonitorCompat: InputMonitorCompat,
        previousGestureState: GestureState,
        gestureState: GestureState,
        event: MotionEvent,
        reasonString: CompoundString,
    ): InputConsumer where T : RecentsViewContainer, T : StatefulContainer<S> {
        val container: T =
            gestureState.getContainerInterface<S, T>().getCreatedContainer()
                ?: return getDefaultInputConsumer(
                    gestureState.displayId,
                    userUnlocked,
                    taskAnimationManager,
                    taskbarManager,
                    reasonString.append(
                        "%sactivity == null, trying to use default input consumer",
                        SUBSTRING_PREFIX,
                    ),
                )

        val rootView = container.rootView
        val hasWindowFocus = rootView?.hasWindowFocus() ?: false
        val isPreviousGestureAnimatingToLauncher =
            (previousGestureState.isRunningAnimationToLauncher ||
                deviceState.isPredictiveBackToHomeInProgress)
        val isInLiveTileMode: Boolean =
            gestureState.getContainerInterface<S, T>().isInLiveTileMode()

        reasonString.append(
            if (hasWindowFocus) "%sactivity has window focus"
            else
                (if (isPreviousGestureAnimatingToLauncher)
                    "%sprevious gesture is still animating to launcher"
                else if (isInLiveTileMode) "%sdevice is in live mode"
                else "%sall overview focus conditions failed"),
            SUBSTRING_PREFIX,
        )
        return if (hasWindowFocus || isPreviousGestureAnimatingToLauncher || isInLiveTileMode) {
            reasonString.append(
                "%soverview should have focus, using OverviewInputConsumer",
                SUBSTRING_PREFIX,
            )
            OverviewInputConsumer(
                gestureState,
                container,
                inputMonitorCompat,
                /* startingInActivityBounds= */ false,
            )
        } else {
            reasonString.append(
                "%soverview shouldn't have focus, using OverviewWithoutFocusInputConsumer",
                SUBSTRING_PREFIX,
            )
            val disableHorizontalSwipe = deviceState.isInExclusionRegion(event)
            OverviewWithoutFocusInputConsumer(
                container.asContext(),
                deviceState,
                gestureState,
                inputMonitorCompat,
                disableHorizontalSwipe,
            )
        }
    }

    /** Returns the [ResetGestureInputConsumer] if user is unlocked, else NO_OP. */
    @JvmStatic
    fun getDefaultInputConsumer(
        displayId: Int,
        userUnlocked: Boolean,
        taskAnimationManager: TaskAnimationManager?,
        taskbarManager: TaskbarManager?,
        reasonString: CompoundString,
    ): InputConsumer {
        return if (userUnlocked && taskAnimationManager != null && taskbarManager != null) {
            reasonString.append(
                "%sResetGestureInputConsumer available, using ResetGestureInputConsumer",
                SUBSTRING_PREFIX,
            )
            ResetGestureInputConsumer(displayId, taskAnimationManager) {
                taskbarManager.getTaskbarForDisplay(displayId)
            }
        } else {
            reasonString.append(
                "%s${
                    if (userUnlocked) "user is locked"
                    else if (taskAnimationManager == null) "taskAnimationManager is null"
                    else "taskbarManager is null"
                }, using no-op input consumer",
                SUBSTRING_PREFIX,
            )
            // ResetGestureInputConsumer isn't available until onUserUnlocked(), so reset to
            // NO_OP until then (we never want these to be null).
            InputConsumer.createNoOpInputConsumer(displayId)
        }
    }

    private fun <S : BaseState<S>, T> createOtherActivityInputConsumer(
        context: Context,
        swipeUpHandlerFactory: AbsSwipeUpHandler.Factory,
        overviewComponentObserver: OverviewComponentObserver,
        deviceState: RecentsAnimationDeviceState,
        taskAnimationManager: TaskAnimationManager,
        inputMonitorCompat: InputMonitorCompat,
        onCompleteCallback: Consumer<OtherActivityInputConsumer>,
        inputEventReceiver: InputChannelCompat.InputEventReceiver,
        gestureState: GestureState,
        event: MotionEvent,
    ): InputConsumer where T : RecentsViewContainer, T : StatefulContainer<S> {
        val shouldDefer =
            (!overviewComponentObserver.isHomeAndOverviewSame ||
                gestureState
                    .getContainerInterface<S, T>()
                    .deferStartingActivity(deviceState, event))
        val disableHorizontalSwipe = deviceState.isInExclusionRegion(event)
        return OtherActivityInputConsumer(
            /* base= */ context,
            deviceState,
            taskAnimationManager,
            gestureState,
            /* isDeferredDownTarget= */ shouldDefer,
            onCompleteCallback,
            inputMonitorCompat,
            inputEventReceiver,
            disableHorizontalSwipe,
            swipeUpHandlerFactory,
        )
    }

    private fun newCompoundString(substring: String): CompoundString {
        return CompoundString("%s%s", NEWLINE_PREFIX, substring)
    }

    private fun logInputConsumerSelectionReason(
        consumer: InputConsumer,
        reasonString: CompoundString,
    ) {
        ActiveGestureProtoLogProxy.logSetInputConsumer(consumer.name, reasonString.toString())
        if ((consumer.type and InputConsumer.TYPE_OTHER_ACTIVITY) != 0) {
            ActiveGestureLog.INSTANCE.trackEvent(
                ActiveGestureErrorDetector.GestureEvent.FLAG_USING_OTHER_ACTIVITY_INPUT_CONSUMER
            )
        }
    }

    private fun ignoreThreeFingerTrackpadForNavHandleLongPress(
        gestureState: GestureState
    ): Boolean {
        return (com.android.launcher3.Flags.ignoreThreeFingerTrackpadForNavHandleLongPress() &&
            gestureState.isThreeFingerTrackpadGesture)
    }

    private fun handleOrientationSetup(baseInputConsumer: InputConsumer) {
        baseInputConsumer.notifyOrientationSetup()
    }
}
