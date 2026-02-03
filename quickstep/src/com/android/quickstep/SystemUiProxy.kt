/*
 * Copyright (C) 2019 The Android Open Source Project
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

import android.app.ActivityManager
import android.app.ActivityManager.RunningTaskInfo
import android.app.ActivityOptions
import android.app.ActivityTaskManager.INVALID_TASK_ID
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.graphics.Point
import android.graphics.Rect
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Message
import android.os.RemoteException
import android.os.Trace
import android.os.Trace.traceBegin
import android.os.Trace.traceEnd
import android.os.UserHandle
import android.util.Log
import android.view.IRemoteAnimationRunner
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.RemoteAnimationTarget
import android.view.SurfaceControl
import android.view.SurfaceControl.Transaction
import android.window.DesktopModeFlags.ENABLE_DESKTOP_WINDOWING_TASKBAR_RUNNING_APPS
import android.window.IOnBackInvokedCallback
import android.window.RemoteTransition
import android.window.TaskSnapshot
import android.window.TransitionFilter
import android.window.TransitionInfo
import android.window.WindowContainerTransaction
import androidx.annotation.MainThread
import androidx.annotation.VisibleForTesting
import androidx.annotation.WorkerThread
//import app.lawnchair.gestures.type.GestureType
import com.android.internal.logging.InstanceId
import com.android.internal.util.ScreenshotRequest
import com.android.internal.view.AppearanceRegion
import com.android.launcher3.Flags
import com.android.launcher3.Utilities.ATLEAST_BAKLAVA
import com.android.launcher3.dagger.ApplicationContext
import com.android.launcher3.dagger.LauncherAppComponent
import com.android.launcher3.dagger.LauncherAppSingleton
import com.android.launcher3.util.DaggerSingletonObject
import com.android.launcher3.util.Executors
import com.android.launcher3.util.Preconditions
import com.android.launcher3.util.SplitConfigurationOptions.StagePosition
import com.android.quickstep.util.ActiveGestureProtoLogProxy
import com.android.quickstep.util.ContextualSearchInvoker
import com.android.quickstep.util.unfold.ProxyUnfoldTransitionProvider
import com.android.systemui.contextualeducation.GestureType
import com.android.systemui.shared.recents.ISystemUiProxy
import com.android.systemui.shared.recents.model.ThumbnailData.Companion.wrap
import com.android.systemui.shared.system.QuickStepContract
import com.android.systemui.shared.system.QuickStepContract.SystemUiStateFlags
import com.android.systemui.shared.system.RecentsAnimationControllerCompat
import com.android.systemui.shared.system.RecentsAnimationListener
import com.android.systemui.shared.system.smartspace.ILauncherUnlockAnimationController
import com.android.systemui.shared.system.smartspace.ISysuiUnlockAnimationController
import com.android.systemui.shared.system.smartspace.SmartspaceState
import com.android.systemui.unfold.config.ResourceUnfoldTransitionConfig
import com.android.systemui.unfold.progress.IUnfoldAnimation
import com.android.systemui.unfold.progress.IUnfoldTransitionListener
import com.android.wm.shell.back.IBackAnimation
import com.android.wm.shell.bubbles.IBubbles
import com.android.wm.shell.bubbles.IBubblesListener
import com.android.wm.shell.common.pip.IPip
import com.android.wm.shell.common.pip.IPipAnimationListener
import com.android.wm.shell.desktopmode.IDesktopMode
import com.android.wm.shell.desktopmode.IDesktopTaskListener
import com.android.wm.shell.desktopmode.IMoveToDesktopCallback
import com.android.wm.shell.draganddrop.IDragAndDrop
import com.android.wm.shell.onehanded.IOneHanded
import com.android.wm.shell.recents.IRecentTasks
import com.android.wm.shell.recents.IRecentTasksListener
import com.android.wm.shell.recents.IRecentsAnimationController
import com.android.wm.shell.recents.IRecentsAnimationRunner
import com.android.wm.shell.shared.GroupedTaskInfo
import com.android.wm.shell.shared.IShellTransitions
import com.android.wm.shell.shared.bubbles.BubbleBarLocation
import com.android.wm.shell.shared.bubbles.BubbleBarLocation.UpdateSource
import com.android.wm.shell.shared.desktopmode.DesktopModeStatus
import com.android.wm.shell.shared.desktopmode.DesktopModeTransitionSource
import com.android.wm.shell.shared.desktopmode.DesktopTaskToFrontReason
import com.android.wm.shell.shared.split.SplitBounds
import com.android.wm.shell.shared.split.SplitScreenConstants.PersistentSnapPosition
import com.android.wm.shell.splitscreen.ISplitScreen
import com.android.wm.shell.splitscreen.ISplitScreenListener
import com.android.wm.shell.splitscreen.ISplitSelectListener
import com.android.wm.shell.startingsurface.IStartingWindow
import com.android.wm.shell.startingsurface.IStartingWindowListener
import java.io.PrintWriter
import javax.inject.Inject

/** Holds the reference to SystemUI. */
@LauncherAppSingleton
class SystemUiProxy @Inject constructor(@ApplicationContext private val context: Context) :
    NavHandle {

    private var systemUiProxy: ISystemUiProxy? = null
    private var pip: IPip? = null
    private var bubbles: IBubbles? = null
    private var sysuiUnlockAnimationController: ISysuiUnlockAnimationController? = null
    private var splitScreen: ISplitScreen? = null
    private var oneHanded: IOneHanded? = null
    private var shellTransitions: IShellTransitions? = null
    private var startingWindow: IStartingWindow? = null
    private var recentTasks: IRecentTasks? = null
    private var backAnimation: IBackAnimation? = null
    private var desktopMode: IDesktopMode? = null
    private var unfoldAnimation: IUnfoldAnimation? = null

    private val systemUiProxyDeathRecipient =
        IBinder.DeathRecipient { Executors.MAIN_EXECUTOR.execute { clearProxy() } }

    // Save the listeners passed into the proxy since LauncherProxyService may not have been bound
    // yet, and we'll need to set/register these listeners with SysUI when they do.  Note that it is
    // up to the caller to clear the listeners to prevent leaks as these can be held indefinitely
    // in case SysUI needs to rebind.
    private var pipAnimationListener: IPipAnimationListener? = null
    private var bubblesListener: IBubblesListener? = null
    private var splitScreenListener: ISplitScreenListener? = null
    private var splitSelectListener: ISplitSelectListener? = null
    private var startingWindowListener: IStartingWindowListener? = null
    private var launcherUnlockAnimationController: ILauncherUnlockAnimationController? = null
    private var launcherActivityClass: String? = null
    private var recentTasksListener: IRecentTasksListener? = null
    private var unfoldAnimationListener: IUnfoldTransitionListener? = null
    private var desktopTaskListener: IDesktopTaskListener? = null
    private val remoteTransitions = LinkedHashMap<RemoteTransition, TransitionFilter>()

    // Save bubble bar state in case service is not bound yet when it is updated. SysUI relies on
    // this to suppress the floating bubbles UI.
    private var hasBubbleBar = false

    private val stateChangeCallbacks: MutableList<Runnable> = ArrayList()

    private var originalTransactionToken: IBinder? = null
    private var backToLauncherCallback: IOnBackInvokedCallback? = null
    private var backToLauncherRunner: IRemoteAnimationRunner? = null
    private var dragAndDrop: IDragAndDrop? = null
    val homeVisibilityState = HomeVisibilityState()
    val focusState = FocusState()

    // Used to dedupe calls to SystemUI
    private var lastShelfHeight = 0
    private var lastShelfVisible = false

    // Used to dedupe calls to SystemUI
    private var lastLauncherKeepClearAreaHeight = 0
    private var lastLauncherKeepClearAreaHeightVisible = false

    private val asyncHandler =
        Handler(Executors.UI_HELPER_EXECUTOR.looper) { handleMessageAsync(it) }

    // TODO(141886704): Find a way to remove this
    @SystemUiStateFlags var lastSystemUiStateFlags: Long = 0

    /**
     * This returns a pending intent that is used to start recents via Shell (which is a different
     * process). It is bare-bones, so it's expected that the component and options will be provided
     * via fill-in intent.
     */
    private fun getRecentsPendingIntent(displayId: Int) =
        PendingIntent.getActivity(
            context,
            0,
            Intent().setPackage(context.packageName),
            PendingIntent.FLAG_MUTABLE or
                PendingIntent.FLAG_ALLOW_UNSAFE_IMPLICIT_INTENT or
                Intent.FILL_IN_COMPONENT,
            ActivityOptions.makeBasic()
                .setPendingIntentCreatorBackgroundActivityStartMode(
                    ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                )
                .setLaunchDisplayId(displayId)
                .toBundle(),
        )

    val unfoldTransitionProvider: ProxyUnfoldTransitionProvider? =
        if ((Flags.enableUnfoldStateAnimation() && ResourceUnfoldTransitionConfig().isEnabled))
            ProxyUnfoldTransitionProvider()
        else null

    private inline fun executeWithErrorLog(
        errorMsg: () -> String,
        tag: String = TAG,
        callback: () -> Any?,
    ) {
        try {
            callback.invoke()
        } catch (e: RemoteException) {
            Log.w(tag, errorMsg.invoke(), e)
        }
    }

    fun onBackEvent(backEvent: KeyEvent?) =
        executeWithErrorLog({ "Failed call onBackPressed" }) {
            systemUiProxy?.onBackEvent(backEvent)
        }

    fun onImeSwitcherPressed() =
        executeWithErrorLog({ "Failed call onImeSwitcherPressed" }) {
            systemUiProxy?.onImeSwitcherPressed()
        }

    fun onImeSwitcherLongPress() =
        executeWithErrorLog({ "Failed call onImeSwitcherLongPress" }) {
            systemUiProxy?.onImeSwitcherLongPress()
        }

    fun updateContextualEduStats(isTrackpadGesture: Boolean, gestureType: GestureType) =
        executeWithErrorLog({ "Failed call updateContextualEduStats" }) {
            systemUiProxy?.updateContextualEduStats(isTrackpadGesture, gestureType.name)
        }

    fun setHomeRotationEnabled(enabled: Boolean) =
        executeWithErrorLog({ "Failed call setHomeRotationEnabled" }) {
            systemUiProxy?.setHomeRotationEnabled(enabled)
        }

    /**
     * Sets proxy state, including death linkage, various listeners, and other configuration objects
     */
    @MainThread
    fun setProxy(
        proxy: ISystemUiProxy?,
        pip: IPip?,
        bubbles: IBubbles?,
        splitScreen: ISplitScreen?,
        oneHanded: IOneHanded?,
        shellTransitions: IShellTransitions?,
        startingWindow: IStartingWindow?,
        recentTasks: IRecentTasks?,
        sysuiUnlockAnimationController: ISysuiUnlockAnimationController?,
        backAnimation: IBackAnimation?,
        desktopMode: IDesktopMode?,
        unfoldAnimation: IUnfoldAnimation?,
        dragAndDrop: IDragAndDrop?,
    ) {
        Preconditions.assertUIThread()
        unlinkToDeath()
        systemUiProxy = proxy
        this.pip = pip
        this.bubbles = bubbles
        this.splitScreen = splitScreen
        this.oneHanded = oneHanded
        this.shellTransitions = shellTransitions
        this.startingWindow = startingWindow
        this.sysuiUnlockAnimationController = sysuiUnlockAnimationController
        this.recentTasks = recentTasks
        this.backAnimation = backAnimation
        this.desktopMode = desktopMode
        this.unfoldAnimation = if (Flags.enableUnfoldStateAnimation()) null else unfoldAnimation
        this.dragAndDrop = dragAndDrop
        linkToDeath()
        setHasBubbleBar(hasBubbleBar)
        // re-attach the listeners once missing due to setProxy has not been initialized yet.
        setPipAnimationListener(pipAnimationListener)
        setBubblesListener(bubblesListener)
        registerSplitScreenListener(splitScreenListener)
        registerSplitSelectListener(splitSelectListener)
        homeVisibilityState.init(this.shellTransitions)
        focusState.init(this.shellTransitions)
        setStartingWindowListener(startingWindowListener)
        setLauncherUnlockAnimationController(
            launcherActivityClass,
            launcherUnlockAnimationController,
        )
        LinkedHashMap(remoteTransitions).forEach { (remoteTransition, filter) ->
            registerRemoteTransition(remoteTransition, filter)
        }
        setupTransactionQueue()
        registerRecentTasksListener(recentTasksListener)
        setBackToLauncherCallback(backToLauncherCallback, backToLauncherRunner)
        setUnfoldAnimationListener(unfoldAnimationListener)
        setDesktopTaskListener(desktopTaskListener)
        setAssistantOverridesRequested(
            ContextualSearchInvoker(context).getSysUiAssistOverrideInvocationTypes()
        )
        stateChangeCallbacks.forEach { it.run() }

        if (unfoldTransitionProvider != null) {
            if (unfoldAnimation != null) {
                try {
                    unfoldAnimation.setListener(unfoldTransitionProvider)
                    unfoldTransitionProvider.isActive = true
                } catch (e: RemoteException) {
                    // Ignore
                }
            } else {
                unfoldTransitionProvider.isActive = false
            }
        }
    }

    /**
     * Clear the proxy to release held resources and turn the majority of its operations into no-ops
     */
    @MainThread
    fun clearProxy() =
        setProxy(null, null, null, null, null, null, null, null, null, null, null, null, null)

    /** Adds a callback to be notified whenever the active state changes */
    fun addOnStateChangeListener(callback: Runnable) = stateChangeCallbacks.add(callback)

    /** Removes a previously added state change callback */
    fun removeOnStateChangeListener(callback: Runnable) = stateChangeCallbacks.remove(callback)

    fun isActive() = systemUiProxy != null

    private fun linkToDeath() =
        executeWithErrorLog({ "Failed to link sysui proxy death recipient" }) {
            systemUiProxy?.asBinder()?.linkToDeath(systemUiProxyDeathRecipient, 0 /* flags */)
        }

    private fun unlinkToDeath() =
        systemUiProxy?.asBinder()?.unlinkToDeath(systemUiProxyDeathRecipient, 0 /* flags */)

    fun startScreenPinning(taskId: Int) =
        executeWithErrorLog({ "Failed call startScreenPinning" }) {
            systemUiProxy?.startScreenPinning(taskId)
        }

    fun onOverviewShown(fromHome: Boolean, tag: String = TAG) =
        executeWithErrorLog(
            { "Failed call onOverviewShown from: ${(if (fromHome) "home" else "app")}" },
            tag = tag,
        ) {
            systemUiProxy?.onOverviewShown(fromHome)
        }

    @MainThread
    fun onStatusBarTouchEvent(event: MotionEvent) {
        Preconditions.assertUIThread()
        executeWithErrorLog({ "Failed call onStatusBarTouchEvent with arg: $event" }) {
            systemUiProxy?.onStatusBarTouchEvent(event)
        }
    }

    fun onStatusBarTrackpadEvent(event: MotionEvent) =
        executeWithErrorLog({ "Failed call onStatusBarTrackpadEvent with arg: $event" }) {
            systemUiProxy?.onStatusBarTrackpadEvent(event)
        }

    fun onAssistantProgress(progress: Float) =
        executeWithErrorLog({ "Failed call onAssistantProgress with progress: $progress" }) {
            systemUiProxy?.onAssistantProgress(progress)
        }

    fun onAssistantGestureCompletion(velocity: Float) =
        executeWithErrorLog({ "Failed call onAssistantGestureCompletion" }) {
            systemUiProxy?.onAssistantGestureCompletion(velocity)
        }

    fun startAssistant(args: Bundle) =
        executeWithErrorLog({ "Failed call startAssistant" }) {
            systemUiProxy?.startAssistant(args)
        }

    fun setAssistantOverridesRequested(invocationTypes: IntArray) =
        executeWithErrorLog({ "Failed call setAssistantOverridesRequested" }) {
            systemUiProxy?.setAssistantOverridesRequested(invocationTypes)
        }

    override fun animateNavBarLongPress(isTouchDown: Boolean, shrink: Boolean, durationMs: Long) =
        executeWithErrorLog({ "Failed call animateNavBarLongPress" }) {
            systemUiProxy?.animateNavBarLongPress(isTouchDown, shrink, durationMs)
        }

    fun setOverrideHomeButtonLongPress(duration: Long, slopMultiplier: Float, haptic: Boolean) =
        executeWithErrorLog({ "Failed call setOverrideHomeButtonLongPress" }) {
            systemUiProxy?.setOverrideHomeButtonLongPress(duration, slopMultiplier, haptic)
        }

    fun notifyAccessibilityButtonClicked(displayId: Int) =
        executeWithErrorLog({ "Failed call notifyAccessibilityButtonClicked" }) {
            systemUiProxy?.notifyAccessibilityButtonClicked(displayId)
        }

    fun notifyAccessibilityButtonLongClicked() =
        executeWithErrorLog({ "Failed call notifyAccessibilityButtonLongClicked" }) {
            systemUiProxy?.notifyAccessibilityButtonLongClicked()
        }

    fun stopScreenPinning() =
        executeWithErrorLog({ "Failed call stopScreenPinning" }) {
            systemUiProxy?.stopScreenPinning()
        }

    fun notifyPrioritizedRotation(rotation: Int) =
        executeWithErrorLog({ "Failed call notifyPrioritizedRotation with arg: $rotation" }) {
            systemUiProxy?.notifyPrioritizedRotation(rotation)
        }

    fun notifyTaskbarStatus(visible: Boolean, stashed: Boolean) =
        executeWithErrorLog({ "Failed call notifyTaskbarStatus with arg: $visible, $stashed" }) {
            systemUiProxy?.notifyTaskbarStatus(visible, stashed)
        }

    /**
     * NOTE: If called to suspend, caller MUST call this method to also un-suspend. [suspend] should
     * be `true` to stop auto-hide, `false` to resume normal behavior
     */
    fun notifyTaskbarAutohideSuspend(suspend: Boolean) =
        executeWithErrorLog({ "Failed call notifyTaskbarAutohideSuspend with arg: $suspend" }) {
            // LC-Ignored
            //systemUiProxy?.notifyTaskbarAutohideSuspend(suspend)
        }

    fun notifyRecentsButtonPositionChanged(bounds: Rect) {
        executeWithErrorLog({
            "Failed call notifyRecentsButtonPositionChanged with arg: $bounds"
        }) {
            systemUiProxy?.notifyRecentsButtonPositionChanged(bounds)
        }
    }

    fun takeScreenshot(request: ScreenshotRequest) =
        executeWithErrorLog({ "Failed call takeScreenshot" }) {
            // LC-Ignored
            //systemUiProxy?.takeScreenshot(request)
        }

    fun expandNotificationPanel() =
        executeWithErrorLog({ "Failed call expandNotificationPanel" }) {
            // LC-Ignored
            //systemUiProxy?.expandNotificationPanel()
        }

    fun toggleNotificationPanel() =
        executeWithErrorLog({ "Failed call toggleNotificationPanel" }) {
            // LC-Ignored
            //systemUiProxy?.toggleNotificationPanel()
        }

    fun toggleQuickSettingsPanel() =
        executeWithErrorLog({ "Failed call toggleQuickSettingsPanel" }) {
            // LC-Ignored
            //systemUiProxy?.toggleQuickSettingsPanel()
        }

    //
    // Pip
    //
    /** Sets the shelf height. */
    fun setShelfHeight(visible: Boolean, shelfHeight: Int) =
        Message.obtain(asyncHandler, MSG_SET_SHELF_HEIGHT, if (visible) 1 else 0, shelfHeight)
            .sendToTarget()

    @WorkerThread
    private fun setShelfHeightAsync(visibleInt: Int, shelfHeight: Int) {
        val visible = visibleInt != 0
        val changed = visible != lastShelfVisible || shelfHeight != lastShelfHeight
        val pip = pip
        if (pip != null && changed) {
            lastShelfVisible = visible
            lastShelfHeight = shelfHeight
            executeWithErrorLog({
                "Failed call setShelfHeight visible: $visible height: $shelfHeight"
            }) {
                pip.setShelfHeight(visible, shelfHeight)
            }
        }
    }

    /**
     * Sets the height of the keep clear area that is going to be reported by the Launcher for the
     * Hotseat.
     */
    fun setLauncherKeepClearAreaHeight(visible: Boolean, height: Int) =
        Message.obtain(
                asyncHandler,
                MSG_SET_LAUNCHER_KEEP_CLEAR_AREA_HEIGHT,
                if (visible) 1 else 0,
                height,
            )
            .sendToTarget()

    @WorkerThread
    private fun setLauncherKeepClearAreaHeight(visibleInt: Int, height: Int) {
        val visible = visibleInt != 0
        val changed =
            visible != lastLauncherKeepClearAreaHeightVisible ||
                height != lastLauncherKeepClearAreaHeight
        val pip = pip
        if (pip != null && changed) {
            lastLauncherKeepClearAreaHeightVisible = visible
            lastLauncherKeepClearAreaHeight = height
            executeWithErrorLog({
                "Failed call setLauncherKeepClearAreaHeight visible: $visible height: $height"
            }) {
                pip.setLauncherKeepClearAreaHeight(visible, height)
            }
        }
    }

    /** Sets listener to get pip animation callbacks. */
    fun setPipAnimationListener(listener: IPipAnimationListener?) {
        executeWithErrorLog({ "Failed call setPinnedStackAnimationListener" }) {
            pip?.setPipAnimationListener(listener)
        }
        pipAnimationListener = listener
    }

    /** @return Destination bounds of auto-pip animation, `null` if the animation is not ready. */
    fun startSwipePipToHome(
        taskInfo: RunningTaskInfo,
        launcherRotation: Int,
        hotseatKeepClearArea: Rect?,
    ): Rect? {
        executeWithErrorLog({ "Failed call startSwipePipToHome" }) {
            return pip?.startSwipePipToHome(taskInfo, launcherRotation, hotseatKeepClearArea)
        }
        return null
    }

    /**
     * Notifies WM Shell that launcher has finished the preparation of the animation for swipe to
     * home. WM Shell can choose to fade out the overlay when entering PIP is finished, and WM Shell
     * should be responsible for cleaning up the overlay.
     */
    fun stopSwipePipToHome(
        taskId: Int,
        componentName: ComponentName?,
        destinationBounds: Rect?,
        overlay: SurfaceControl?,
        appBounds: Rect?,
        sourceRectHint: Rect?,
    ) =
        executeWithErrorLog({ "Failed call stopSwipePipToHome" }) {
            pip?.stopSwipePipToHome(
                taskId,
                componentName,
                destinationBounds,
                overlay,
                appBounds,
                sourceRectHint,
            )
        }

    /**
     * Notifies WM Shell that launcher has aborted all the animation for swipe to home. WM Shell can
     * use this callback to clean up its internal states.
     */
    fun abortSwipePipToHome(taskId: Int, componentName: ComponentName?) =
        executeWithErrorLog({ "Failed call abortSwipePipToHome" }) {
            pip?.abortSwipePipToHome(taskId, componentName)
        }

    /** Sets the next pip animation type to be the alpha animation. */
    fun setPipAnimationTypeToAlpha() =
        executeWithErrorLog({ "Failed call setPipAnimationTypeToAlpha" }) {
            pip?.setPipAnimationTypeToAlpha()
        }

    /** Sets the app icon size in pixel used by Launcher all apps. */
    fun setLauncherAppIconSize(iconSizePx: Int) =
        executeWithErrorLog({ "Failed call setLauncherAppIconSize" }) {
            pip?.setLauncherAppIconSize(iconSizePx)
        }

    //
    // Bubbles
    //
    /** Tells SysUI whether bubble bar is used or not. */
    fun setHasBubbleBar(hasBubbleBar: Boolean) {
        executeWithErrorLog({ "Failed call setHasBubbleBar" }) {
            bubbles?.setHasBubbleBar(hasBubbleBar)
        }
        this.hasBubbleBar = hasBubbleBar
    }

    /** Sets the listener to be notified of bubble state changes. */
    fun setBubblesListener(listener: IBubblesListener?) {
        executeWithErrorLog({ "Failed call registerBubblesListener" }) {
            bubbles?.apply {
                bubblesListener?.let { unregisterBubbleListener(it) }
                listener?.let { registerBubbleListener(it) }
            }
        }
        bubblesListener = listener
    }

    /**
     * Tells SysUI to show the bubble with the provided key.
     *
     * @param key the key of the bubble to show.
     * @param bubbleBarTopToScreenBottom distance between the top coordinate of bubble bar and the
     *   bottom of the screen
     */
    fun showBubble(key: String?, bubbleBarTopToScreenBottom: Int) =
        executeWithErrorLog({ "Failed call showBubble" }) {
            bubbles?.showBubble(key, bubbleBarTopToScreenBottom)
        }

    /** Tells SysUI to remove all bubbles. */
    fun removeAllBubbles() =
        executeWithErrorLog({ "Failed call removeAllBubbles" }) { bubbles?.removeAllBubbles() }

    /** Tells SysUI to collapse the bubbles. */
    fun collapseBubbles() =
        executeWithErrorLog({ "Failed call collapseBubbles" }) { bubbles?.collapseBubbles() }

    /**
     * Tells SysUI when the bubble is being dragged. Should be called only when the bubble bar is
     * expanded.
     *
     * @param bubbleKey key of the bubble being dragged
     */
    fun startBubbleDrag(bubbleKey: String?) =
        executeWithErrorLog({ "Failed call startBubbleDrag" }) {
            bubbles?.startBubbleDrag(bubbleKey)
        }

    /**
     * Tells SysUI when the bubble stops being dragged. Should be called only when the bubble bar is
     * expanded.
     *
     * @param location location of the bubble bar
     * @param bubbleBarTopToScreenBottom distance between the new top coordinate for bubble bar and
     *   the bottom of the screen
     */
    fun stopBubbleDrag(location: BubbleBarLocation?, bubbleBarTopToScreenBottom: Int) =
        executeWithErrorLog({ "Failed call stopBubbleDrag" }) {
            bubbles?.stopBubbleDrag(location, bubbleBarTopToScreenBottom)
        }

    /**
     * Tells SysUI to dismiss the bubble with the provided key.
     *
     * @param key the key of the bubble to dismiss.
     * @param timestamp the timestamp when the removal happened.
     */
    fun dragBubbleToDismiss(key: String?, timestamp: Long) =
        executeWithErrorLog({ "Failed call dragBubbleToDismiss" }) {
            bubbles?.dragBubbleToDismiss(key, timestamp)
        }

    /**
     * Tells SysUI to show user education relative to the reference point provided.
     *
     * @param position the bubble bar top center position in Screen coordinates.
     */
    fun showUserEducation(position: Point) =
        executeWithErrorLog({ "Failed call showUserEducation" }) {
            bubbles?.showUserEducation(position.x, position.y)
        }

    /**
     * Tells SysUI to update the bubble bar location to the new location.
     *
     * @param location new location for the bubble bar
     * @param source what triggered the location update
     */
    fun setBubbleBarLocation(location: BubbleBarLocation?, @UpdateSource source: Int) =
        executeWithErrorLog({ "Failed call setBubbleBarLocation" }) {
            bubbles?.setBubbleBarLocation(location, source)
        }

    /**
     * Tells SysUI the distance between the top coordinate of the bubble bar and the bottom of the
     * screen
     */
    fun updateBubbleBarTopToScreenBottom(bubbleBarTopToScreenBottom: Int) =
        executeWithErrorLog({ "Failed call updateBubbleBarTopToScreenBottom" }) {
            bubbles?.updateBubbleBarTopToScreenBottom(bubbleBarTopToScreenBottom)
        }

    /**
     * Tells SysUI to show a shortcut bubble.
     *
     * @param info the shortcut info used to create or identify the bubble.
     * @param bubbleBarLocation the optional location of the bubble bar.
     */
    @JvmOverloads
    fun showShortcutBubble(info: ShortcutInfo?, bubbleBarLocation: BubbleBarLocation? = null) =
        executeWithErrorLog({ "Failed call showShortcutBubble" }) {
            bubbles?.showShortcutBubble(info, bubbleBarLocation)
        }

    /**
     * Tells SysUI to show a bubble of an app.
     *
     * @param intent the intent used to create the bubble.
     * @param bubbleBarLocation the optional location of the bubble bar.
     */
    @JvmOverloads
    fun showAppBubble(
        intent: Intent?,
        user: UserHandle,
        bubbleBarLocation: BubbleBarLocation? = null,
    ) =
        executeWithErrorLog({ "Failed call showAppBubble" }) {
            bubbles?.showAppBubble(intent, user, bubbleBarLocation)
        }

    /** Tells SysUI to show the expanded view. */
    fun showExpandedView() =
        executeWithErrorLog({ "Failed call showExpandedView" }) { bubbles?.showExpandedView() }

    /** Tells SysUI to move the dragged bubble to full screen. */
    fun moveDraggedBubbleToFullscreen(key: String, dropLocation: Point) {
        executeWithErrorLog({ "Failed to call moveDraggedBubbleToFullscreen" }) {
            bubbles?.moveDraggedBubbleToFullscreen(key, dropLocation)
        }
    }

    //
    // Splitscreen
    //
    fun registerSplitScreenListener(listener: ISplitScreenListener?) {
        executeWithErrorLog({ "Failed call registerSplitScreenListener" }) {
            splitScreen?.registerSplitScreenListener(listener)
        }
        splitScreenListener = listener
    }

    fun unregisterSplitScreenListener(listener: ISplitScreenListener?) {
        executeWithErrorLog({ "Failed call unregisterSplitScreenListener" }) {
            splitScreen?.unregisterSplitScreenListener(listener)
        }
        splitScreenListener = null
    }

    fun registerSplitSelectListener(listener: ISplitSelectListener?) {
        executeWithErrorLog({ "Failed call registerSplitSelectListener" }) {
            splitScreen?.registerSplitSelectListener(listener)
        }
        splitSelectListener = listener
    }

    fun unregisterSplitSelectListener(listener: ISplitSelectListener?) {
        executeWithErrorLog({ "Failed call unregisterSplitSelectListener" }) {
            splitScreen?.unregisterSplitSelectListener(listener)
        }
        splitSelectListener = null
    }

    /** Start multiple tasks in split-screen simultaneously. */
    fun startTasks(
        taskId1: Int,
        options1: Bundle?,
        taskId2: Int,
        options2: Bundle?,
        @StagePosition splitPosition: Int,
        @PersistentSnapPosition snapPosition: Int,
        remoteTransition: RemoteTransition?,
        instanceId: InstanceId?,
    ) =
        executeWithErrorLog({ "Failed call startTasks" }) {
            splitScreen?.startTasks(
                taskId1,
                options1,
                taskId2,
                options2,
                splitPosition,
                snapPosition,
                remoteTransition,
                instanceId,
            )
        }

    fun startIntentAndTask(
        pendingIntent: PendingIntent?,
        userId1: Int,
        options1: Bundle?,
        taskId: Int,
        options2: Bundle?,
        @StagePosition splitPosition: Int,
        @PersistentSnapPosition snapPosition: Int,
        remoteTransition: RemoteTransition?,
        instanceId: InstanceId?,
    ) =
        executeWithErrorLog({ "Failed call startIntentAndTask" }) {
            splitScreen?.startIntentAndTask(
                pendingIntent,
                userId1,
                options1,
                taskId,
                options2,
                splitPosition,
                snapPosition,
                remoteTransition,
                instanceId,
            )
        }

    fun startIntents(
        pendingIntent1: PendingIntent?,
        userId1: Int,
        shortcutInfo1: ShortcutInfo?,
        options1: Bundle?,
        pendingIntent2: PendingIntent?,
        userId2: Int,
        shortcutInfo2: ShortcutInfo?,
        options2: Bundle?,
        @StagePosition splitPosition: Int,
        @PersistentSnapPosition snapPosition: Int,
        remoteTransition: RemoteTransition?,
        instanceId: InstanceId?,
    ) =
        executeWithErrorLog({ "Failed call startIntents" }) {
            splitScreen?.startIntents(
                pendingIntent1,
                userId1,
                shortcutInfo1,
                options1,
                pendingIntent2,
                userId2,
                shortcutInfo2,
                options2,
                splitPosition,
                snapPosition,
                remoteTransition,
                instanceId,
            )
        }

    fun startShortcutAndTask(
        shortcutInfo: ShortcutInfo?,
        options1: Bundle?,
        taskId: Int,
        options2: Bundle?,
        @StagePosition splitPosition: Int,
        @PersistentSnapPosition snapPosition: Int,
        remoteTransition: RemoteTransition?,
        instanceId: InstanceId?,
    ) =
        executeWithErrorLog({ "Failed call startShortcutAndTask" }) {
            splitScreen?.startShortcutAndTask(
                shortcutInfo,
                options1,
                taskId,
                options2,
                splitPosition,
                snapPosition,
                remoteTransition,
                instanceId,
            )
        }

    fun startShortcut(
        packageName: String?,
        shortcutId: String?,
        position: Int,
        options: Bundle?,
        user: UserHandle?,
        instanceId: InstanceId?,
    ) =
        executeWithErrorLog({ "Failed call startShortcut" }) {
            splitScreen?.startShortcut(packageName, shortcutId, position, options, user, instanceId)
        }

    fun startIntent(
        intent: PendingIntent?,
        userId: Int,
        fillInIntent: Intent?,
        position: Int,
        options: Bundle?,
        instanceId: InstanceId?,
    ) =
        executeWithErrorLog({ "Failed call startIntent" }) {
            splitScreen?.startIntent(intent, userId, fillInIntent, position, options, instanceId)
        }

    /**
     * Call the desktop mode interface to start a TRANSIT_OPEN transition when launching an intent
     * from the taskbar so that it can be handled in desktop mode.
     */
    fun startLaunchIntentTransition(intent: Intent, options: Bundle, displayId: Int) =
        executeWithErrorLog({ "Failed call startLaunchIntentTransition" }) {
            desktopMode?.startLaunchIntentTransition(intent, options, displayId)
        }

    //
    // One handed
    //
    fun startOneHandedMode() =
        executeWithErrorLog({ "Failed call startOneHandedMode" }) { oneHanded?.startOneHanded() }

    fun stopOneHandedMode() =
        executeWithErrorLog({ "Failed call stopOneHandedMode" }) { oneHanded?.stopOneHanded() }

    //
    // Remote transitions
    //
    fun registerRemoteTransition(remoteTransition: RemoteTransition?, filter: TransitionFilter) {
        remoteTransition ?: return
        executeWithErrorLog({ "Failed call registerRemoteTransition" }) {
            shellTransitions?.registerRemote(filter, remoteTransition)
        }
        remoteTransitions.putIfAbsent(remoteTransition, filter)
    }

    fun unregisterRemoteTransition(remoteTransition: RemoteTransition?) {
        executeWithErrorLog({ "Failed call unregisterRemoteTransition" }) {
            shellTransitions?.unregisterRemote(remoteTransition)
        }
        remoteTransitions.remove(remoteTransition)
    }

    /**
     * Returns a surface which can be used to attach overlays to home task or null if the task
     * doesn't exist or sysui is not connected
     */
    fun getHomeTaskOverlayContainer(): SurfaceControl? {
        executeWithErrorLog({ "Failed call getHomeTaskOverlayContainer" }) {
            return shellTransitions?.homeTaskOverlayContainer
        }
        return null
    }

    /**
     * Use SystemUI's transaction-queue instead of Launcher's independent one. This is necessary if
     * Launcher and SystemUI need to coordinate transactions (eg. for shell transitions).
     */
    fun shareTransactionQueue() {
        if (originalTransactionToken == null) {
            originalTransactionToken = Transaction.getDefaultApplyToken()
        }
        setupTransactionQueue()
    }

    /** Switch back to using Launcher's independent transaction queue. */
    fun unshareTransactionQueue() {
        if (originalTransactionToken == null) {
            return
        }
        Transaction.setDefaultApplyToken(originalTransactionToken)
        originalTransactionToken = null
    }

    private fun setupTransactionQueue() =
        executeWithErrorLog({ "Error getting Shell's apply token" }) {
            val token: IBinder =
                shellTransitions?.shellApplyToken ?: originalTransactionToken ?: return
            Transaction.setDefaultApplyToken(token)
        }

    //
    // Starting window
    //
    /** Sets listener to get callbacks when launching a task. */
    fun setStartingWindowListener(listener: IStartingWindowListener?) {
        executeWithErrorLog({ "Failed call setStartingWindowListener" }) {
            startingWindow?.setStartingWindowListener(listener)
        }
        startingWindowListener = listener
    }

    //
    // SmartSpace transitions
    //
    /**
     * Sets the instance of [ILauncherUnlockAnimationController] that System UI should use to
     * control the launcher side of the unlock animation. This will also cause us to dispatch the
     * current state of the smartspace to System UI (this will subsequently happen if the state
     * changes).
     */
    fun setLauncherUnlockAnimationController(
        activityClass: String?,
        controller: ILauncherUnlockAnimationController?,
    ) {
        executeWithErrorLog({ "Failed call setLauncherUnlockAnimationController" }) {
            // LC-Ignored
//            sysuiUnlockAnimationController?.apply {
//                setLauncherUnlockController(activityClass, controller)
//                controller?.dispatchSmartspaceStateToSysui()
//            }
        }
        launcherActivityClass = activityClass
        launcherUnlockAnimationController = controller
    }

    /**
     * Tells System UI that the Launcher's smartspace state has been updated, so that it can prepare
     * the unlock animation accordingly.
     */
    fun notifySysuiSmartspaceStateUpdated(state: SmartspaceState?) =
        executeWithErrorLog({ "Failed call notifySysuiSmartspaceStateUpdated" }) {
            // LC-Ignored
            //sysuiUnlockAnimationController?.onLauncherSmartspaceStateUpdated(state)
        }

    //
    // Recents
    //
    fun registerRecentTasksListener(listener: IRecentTasksListener?) {
        executeWithErrorLog({ "Failed call registerRecentTasksListener" }) {
            recentTasks?.registerRecentTasksListener(listener)
        }
        recentTasksListener = listener
    }

    fun unregisterRecentTasksListener(listener: IRecentTasksListener?) {
        executeWithErrorLog({ "Failed call unregisterRecentTasksListener" }) {
            recentTasks?.unregisterRecentTasksListener(listener)
        }
        recentTasksListener = null
    }

    //
    // Back navigation transitions
    //
    /** Sets the launcher [android.window.IOnBackInvokedCallback] to shell */
    fun setBackToLauncherCallback(
        callback: IOnBackInvokedCallback?,
        runner: IRemoteAnimationRunner?,
    ) {
        backToLauncherCallback = callback
        backToLauncherRunner = runner
        if (callback == null) return
        try {
            backAnimation?.setBackToLauncherCallback(callback, runner)
        } catch (e: RemoteException) {
            Log.e(TAG, "Failed call setBackToLauncherCallback", e)
        } catch (e: SecurityException) {
            Log.e(TAG, "Failed call setBackToLauncherCallback", e)
        }
    }

    /**
     * Clears the previously registered [IOnBackInvokedCallback].
     *
     * @param callback The previously registered callback instance.
     */
    fun clearBackToLauncherCallback(callback: IOnBackInvokedCallback) {
        if (backToLauncherCallback !== callback) {
            return
        }
        backToLauncherCallback = null
        backToLauncherRunner = null
        executeWithErrorLog({ "Failed call clearBackToLauncherCallback" }) {
            backAnimation?.clearBackToLauncherCallback()
        }
    }

    /** Called when the status bar color needs to be customized when back navigation. */
    fun customizeStatusBarAppearance(appearance: AppearanceRegion?) =
        executeWithErrorLog({ "Failed call customizeStatusBarAppearance" }) {
            backAnimation?.customizeStatusBarAppearance(appearance)
        }

    class GetRecentTasksException : Exception {
        constructor(message: String?) : super(message)

        constructor(message: String?, cause: Throwable?) : super(message, cause)
    }

    /**
     * Retrieves a list of Recent tasks from ActivityManager.
     *
     * @throws GetRecentTasksException if IRecentTasks is not initialized, or when we get
     *   RemoteException from server side
     */
    @Throws(GetRecentTasksException::class)
    fun getRecentTasks(numTasks: Int, userId: Int): ArrayList<GroupedTaskInfo> {
        if (recentTasks == null) {
            Log.e(TAG, "getRecentTasks() failed due to null mRecentTasks")
            throw GetRecentTasksException("null mRecentTasks")
        }
        try {
            traceBegin(Trace.TRACE_TAG_APP, "getRecentTasks")
            val rawTasks =
                recentTasks?.getRecentTasks(
                    numTasks,
                    ActivityManager.RECENT_IGNORE_UNAVAILABLE,
                    userId,
                ) ?: return ArrayList()
            return ArrayList(rawTasks.asList())
        } catch (e: RemoteException) {
            Log.e(TAG, "Failed call getRecentTasks", e)
            throw GetRecentTasksException("Failed call getRecentTasks", e)
        } finally {
            traceEnd(Trace.TRACE_TAG_APP)
        }
    }

    /** Gets the set of running tasks. */
    fun getRunningTasks(numTasks: Int): List<RunningTaskInfo> {
        if (!shouldEnableRunningTasksForDesktopMode()) return emptyList()
        executeWithErrorLog({ "Failed call getRunningTasks" }) {
            return recentTasks?.getRunningTasks(numTasks)?.asList() ?: emptyList()
        }
        return emptyList()
    }

    private fun shouldEnableRunningTasksForDesktopMode(): Boolean =
        DesktopModeStatus.canEnterDesktopMode(context) &&
            if (ATLEAST_BAKLAVA) {
                ENABLE_DESKTOP_WINDOWING_TASKBAR_RUNNING_APPS.isTrue
            } else {
                false
            }

    private fun handleMessageAsync(msg: Message): Boolean {
        return when (msg.what) {
            MSG_SET_SHELF_HEIGHT -> {
                setShelfHeightAsync(msg.arg1, msg.arg2)
                true
            }

            MSG_SET_LAUNCHER_KEEP_CLEAR_AREA_HEIGHT -> {
                setLauncherKeepClearAreaHeight(msg.arg1, msg.arg2)
                true
            }

            else -> false
        }
    }

    //
    // Desktop Mode
    //
    /** Calls shell to create a new desk (if possible) on the display whose ID is `displayId`. */
    fun createDesk(displayId: Int) =
        executeWithErrorLog({ "Failed call createDesk" }) { desktopMode?.createDesk(displayId) }

    /**
     * Calls shell to activate the desk whose ID is `deskId` on whatever display it exists on. This
     * will show all tasks on this desk and bring [taskIdToReorderToFront] to the front if it's
     * provided and already on the given desk. If the provided [taskIdToReorderToFront]'s value is
     * null, do not change the windows' activation on the desk.
     */
    @JvmOverloads
    fun activateDesk(
        deskId: Int,
        transition: RemoteTransition?,
        taskIdToReorderToFront: Int? = null,
    ) =
        executeWithErrorLog({ "Failed call activateDesk" }) {
            desktopMode?.activateDesk(deskId, transition, taskIdToReorderToFront ?: INVALID_TASK_ID)
        }

    /** Calls shell to remove the desk whose ID is `deskId`. */
    fun removeDesk(deskId: Int) =
        executeWithErrorLog({ "Failed call removeDesk" }) { desktopMode?.removeDesk(deskId) }

    /** Calls shell to remove all the available desks on all displays. */
    fun removeAllDesks() =
        executeWithErrorLog({ "Failed call removeAllDesks" }) { desktopMode?.removeAllDesks() }

    /**
     * Call shell to show all apps active on the desktop and bring [taskIdToReorderToFront] to front
     * if it's valid on the default desk on the given display. If the provided
     * [taskIdToReorderToFront]'s value is null, do not change the windows' activation on the desk.
     */
    @JvmOverloads
    fun showDesktopApps(
        displayId: Int,
        transition: RemoteTransition? = null,
        taskIdToReorderToFront: Int? = null,
    ) =
        executeWithErrorLog({ "Failed call showDesktopApps" }) {
            desktopMode?.showDesktopApps(
                displayId,
                transition,
                taskIdToReorderToFront ?: INVALID_TASK_ID,
            )
        }

    /** If task with the given id is on the desktop, bring it to front */
    fun showDesktopApp(
        taskId: Int,
        transition: RemoteTransition?,
        toFrontReason: DesktopTaskToFrontReason,
    ) =
        executeWithErrorLog({ "Failed call showDesktopApp" }) {
            desktopMode?.showDesktopApp(taskId, transition, toFrontReason)
        }

    /** Call shell to move to an existing fullscreen task (given by [taskId]) from desktop. */
    @JvmOverloads
    fun moveToFullscreen(
        taskId: Int,
        desktopModeTransitionSource: DesktopModeTransitionSource,
        remoteTransition: RemoteTransition? = null,
    ) =
        executeWithErrorLog({ "Failed call moveToFullscreen" }) {
            desktopMode?.moveToFullscreen(taskId, desktopModeTransitionSource, remoteTransition)
        }

    /** Set a listener on shell to get updates about desktop task state */
    fun setDesktopTaskListener(listener: IDesktopTaskListener?) {
        desktopTaskListener = listener
        executeWithErrorLog({ "Failed call setDesktopTaskListener" }) {
            desktopMode?.setTaskListener(listener)
        }
    }

    /** Perform cleanup transactions after animation to split select is complete */
    fun onDesktopSplitSelectAnimComplete(taskInfo: RunningTaskInfo?) =
        executeWithErrorLog({ "Failed call onDesktopSplitSelectAnimComplete" }) {
            desktopMode?.onDesktopSplitSelectAnimComplete(taskInfo)
        }

    /** Call shell to move a task with given `taskId` to desktop */
    fun moveToDesktop(
        taskId: Int,
        transitionSource: DesktopModeTransitionSource?,
        transition: RemoteTransition?,
        successCallback: Runnable,
    ) =
        executeWithErrorLog({ "Failed call moveToDesktop" }) {
            desktopMode?.moveToDesktop(
                taskId,
                transitionSource,
                transition,
                object : IMoveToDesktopCallback.Stub() {
                    override fun onTaskMovedToDesktop() {
                        successCallback.run()
                    }
                },
            )
        }

    /** Call shell to remove the desktop that is on given `displayId` */
    fun removeDefaultDeskInDisplay(displayId: Int) =
        executeWithErrorLog({ "Failed call removeDefaultDeskInDisplay" }) {
            desktopMode?.removeDefaultDeskInDisplay(displayId)
        }

    /** Call shell to move a task with given `taskId` to external display. */
    fun moveToExternalDisplay(taskId: Int) =
        executeWithErrorLog({ "Failed call moveToExternalDisplay" }) {
            desktopMode?.moveToExternalDisplay(taskId)
        }

    //
    // Unfold transition
    //
    /** Sets the unfold animation lister to sysui. */
    fun setUnfoldAnimationListener(callback: IUnfoldTransitionListener?) {
        unfoldAnimationListener = callback
        executeWithErrorLog({ "Failed call setUnfoldAnimationListener" }) {
            unfoldAnimation?.setListener(callback)
        }
    }

    //
    // Recents
    //
    /** Starts the recents activity. The caller should manage the thread on which this is called. */
    fun startRecentsActivity(
        intent: Intent?,
        options: ActivityOptions,
        listener: RecentsAnimationListener,
        useSyntheticRecentsTransition: Boolean,
        wct: WindowContainerTransaction? = null,
        displayId: Int,
    ): Boolean {
        executeWithErrorLog({ "Error starting recents via shell" }) {
            recentTasks?.startRecentsTransition(
                getRecentsPendingIntent(displayId),
                intent,
                options.toBundle().apply {
                    if (useSyntheticRecentsTransition) {
                        putBoolean("is_synthetic_recents_transition", true)
                    }
                },
                wct,
                context.iApplicationThread,
                RecentsAnimationListenerStub(listener),
            )
                ?: run {
                    // LC-Ignored
                    //ActiveGestureProtoLogProxy.logRecentTasksMissing()
                    return false
                }
            return true
        }
        return false
    }

    private class RecentsAnimationListenerStub(val listener: RecentsAnimationListener) :
        IRecentsAnimationRunner.Stub() {
        override fun onAnimationStart(
            controller: IRecentsAnimationController,
            apps: Array<RemoteAnimationTarget>?,
            wallpapers: Array<RemoteAnimationTarget>?,
            homeContentInsets: Rect?,
            minimizedHomeBounds: Rect?,
            extras: Bundle?,
            transitionInfo: TransitionInfo?,
        ) =
            listener.onAnimationStart(
                RecentsAnimationControllerCompat(controller),
                apps,
                wallpapers,
                homeContentInsets,
                minimizedHomeBounds,
                extras?.apply {
                    // Aidl bundles need to explicitly set class loader
                    // https://developer.android.com/guide/components/aidl#Bundles
                    classLoader = SplitBounds::class.java.classLoader
                },
                transitionInfo,
            )

        override fun onAnimationCanceled(taskIds: IntArray?, taskSnapshots: Array<TaskSnapshot?>?) {
            listener.onAnimationCanceled(wrap(taskIds, taskSnapshots))
        }

        override fun onTasksAppeared(
            apps: Array<RemoteAnimationTarget>?,
            transitionInfo: TransitionInfo?,
        ) {
            listener.onTasksAppeared(apps, transitionInfo)
        }
    }

    //
    // Drag and drop
    //
    /**
     * For testing purposes. Returns `true` only if the shell drop target has shown and drawn and is
     * ready to handle drag events and the subsequent drop.
     */
    fun isDragAndDropReady(): Boolean {
        executeWithErrorLog({ "Error querying drag state" }) {
            return dragAndDrop?.isReadyToHandleDrag ?: false
        }
        return false
    }

    fun dump(pw: PrintWriter) {
        pw.println("$TAG:")

        pw.println("\tmSystemUiProxy=$systemUiProxy")
        pw.println("\tmPip=$pip")
        pw.println("\tmPipAnimationListener=$pipAnimationListener")
        pw.println("\tmBubbles=$bubbles")
        pw.println("\tmBubblesListener=$bubblesListener")
        pw.println("\tmSplitScreen=$splitScreen")
        pw.println("\tmSplitScreenListener=$splitScreenListener")
        pw.println("\tmSplitSelectListener=$splitSelectListener")
        pw.println("\tmOneHanded=$oneHanded")
        pw.println("\tmShellTransitions=$shellTransitions")
        pw.println("\tmHomeVisibilityState=" + homeVisibilityState)
        pw.println("\tmFocusState=" + focusState)
        pw.println("\tmStartingWindow=$startingWindow")
        pw.println("\tmStartingWindowListener=$startingWindowListener")
        pw.println("\tmSysuiUnlockAnimationController=$sysuiUnlockAnimationController")
        pw.println("\tmLauncherActivityClass=$launcherActivityClass")
        pw.println("\tmLauncherUnlockAnimationController=$launcherUnlockAnimationController")
        pw.println("\tmRecentTasks=$recentTasks")
        pw.println("\tmRecentTasksListener=$recentTasksListener")
        pw.println("\tmBackAnimation=$backAnimation")
        pw.println("\tmBackToLauncherCallback=$backToLauncherCallback")
        pw.println("\tmBackToLauncherRunner=$backToLauncherRunner")
        pw.println("\tmDesktopMode=$desktopMode")
        pw.println("\tmDesktopTaskListener=$desktopTaskListener")
        pw.println("\tmUnfoldAnimation=$unfoldAnimation")
        pw.println("\tmUnfoldAnimationListener=$unfoldAnimationListener")
        pw.println("\tmDragAndDrop=$dragAndDrop")
    }

    /** Adds all interfaces held by this proxy to the bundle */
    @VisibleForTesting
    fun addAllInterfaces(out: Bundle) {
        QuickStepContract.addInterface(systemUiProxy, out)
        QuickStepContract.addInterface(pip, out)
        QuickStepContract.addInterface(bubbles, out)
        QuickStepContract.addInterface(sysuiUnlockAnimationController, out)
        QuickStepContract.addInterface(splitScreen, out)
        QuickStepContract.addInterface(oneHanded, out)
        QuickStepContract.addInterface(shellTransitions, out)
        QuickStepContract.addInterface(startingWindow, out)
        QuickStepContract.addInterface(recentTasks, out)
        QuickStepContract.addInterface(backAnimation, out)
        QuickStepContract.addInterface(desktopMode, out)
        QuickStepContract.addInterface(unfoldAnimation, out)
        QuickStepContract.addInterface(dragAndDrop, out)
    }

    companion object {
        private const val TAG = "SystemUiProxy"

        @JvmField val INSTANCE = DaggerSingletonObject(LauncherAppComponent::getSystemUiProxy)

        private const val MSG_SET_SHELF_HEIGHT = 1
        private const val MSG_SET_LAUNCHER_KEEP_CLEAR_AREA_HEIGHT = 2
    }
}
