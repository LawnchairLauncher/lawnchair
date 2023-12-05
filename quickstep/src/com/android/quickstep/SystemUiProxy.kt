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
import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.pm.ShortcutInfo
import android.graphics.Point
import android.graphics.Rect
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Message
import android.os.RemoteException
import android.os.UserHandle
import android.util.Log
import android.view.IRecentsAnimationController
import android.view.IRecentsAnimationRunner
import android.view.IRemoteAnimationRunner
import android.view.MotionEvent
import android.view.RemoteAnimationAdapter
import android.view.RemoteAnimationTarget
import android.view.SurfaceControl
import android.window.IOnBackInvokedCallback
import android.window.RemoteTransition
import android.window.TaskSnapshot
import android.window.TransitionFilter
import androidx.annotation.MainThread
import androidx.annotation.WorkerThread
import com.android.internal.logging.InstanceId
import com.android.internal.util.ScreenshotRequest
import com.android.internal.view.AppearanceRegion
import com.android.launcher3.config.FeatureFlags
import com.android.launcher3.util.Executors
import com.android.launcher3.util.Executors.UI_HELPER_EXECUTOR
import com.android.launcher3.util.MainThreadInitializedObject
import com.android.launcher3.util.Preconditions
import com.android.launcher3.util.SplitConfigurationOptions.StagePosition
import com.android.quickstep.util.ActiveGestureErrorDetector
import com.android.quickstep.util.ActiveGestureLog
import com.android.quickstep.util.AssistUtils
import com.android.quickstep.util.LogUtils.splitFailureMessage
import com.android.systemui.shared.recents.ISystemUiProxy
import com.android.systemui.shared.recents.model.ThumbnailData
import com.android.systemui.shared.system.RecentsAnimationControllerCompat
import com.android.systemui.shared.system.RecentsAnimationListener
import com.android.systemui.shared.system.smartspace.ILauncherUnlockAnimationController
import com.android.systemui.shared.system.smartspace.ISysuiUnlockAnimationController
import com.android.systemui.shared.system.smartspace.SmartspaceState
import com.android.systemui.unfold.progress.IUnfoldAnimation
import com.android.systemui.unfold.progress.IUnfoldTransitionListener
import com.android.wm.shell.back.IBackAnimation
import com.android.wm.shell.bubbles.IBubbles
import com.android.wm.shell.bubbles.IBubblesListener
import com.android.wm.shell.common.split.SplitScreenConstants.PersistentSnapPosition
import com.android.wm.shell.desktopmode.IDesktopMode
import com.android.wm.shell.desktopmode.IDesktopTaskListener
import com.android.wm.shell.draganddrop.IDragAndDrop
import com.android.wm.shell.onehanded.IOneHanded
import com.android.wm.shell.pip.IPip
import com.android.wm.shell.pip.IPipAnimationListener
import com.android.wm.shell.recents.IRecentTasks
import com.android.wm.shell.recents.IRecentTasksListener
import com.android.wm.shell.splitscreen.ISplitScreen
import com.android.wm.shell.splitscreen.ISplitScreenListener
import com.android.wm.shell.splitscreen.ISplitSelectListener
import com.android.wm.shell.startingsurface.IStartingWindow
import com.android.wm.shell.startingsurface.IStartingWindowListener
import com.android.wm.shell.transition.IHomeTransitionListener
import com.android.wm.shell.transition.IShellTransitions
import com.android.wm.shell.util.GroupedRecentTaskInfo
import java.io.PrintWriter

/** Holds the reference to SystemUI. */
class SystemUiProxy(private val context: Context) {

    private val asyncHandler = Handler(UI_HELPER_EXECUTOR.looper, this::handleMessageAsync)

    /**
     * This is a singleton pending intent that is used to start recents via Shell (which is a
     * different process). It is bare-bones, so it's expected that the component and options will be
     * provided via fill-in intent.
     */
    private val recentsPendingIntent =
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
                .toBundle()
        )

    private val systemUiProxyDeathRecipient =
        IBinder.DeathRecipient { Executors.MAIN_EXECUTOR.execute { clearProxy() } }

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

    // Save the listeners passed into the proxy since OverviewProxyService may not have been bound
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
    private var originalTransactionToken: IBinder? = null
    private var backToLauncherCallback: IOnBackInvokedCallback? = null
    private var backToLauncherRunner: IRemoteAnimationRunner? = null
    private var dragAndDrop: IDragAndDrop? = null
    private var homeTransitionListener: IHomeTransitionListener? = null

    // Used to dedupe calls to SystemUI
    private var lastShelfHeight = 0
    private var lastShelfVisible = false

    // Used to dedupe calls to SystemUI
    private var lastLauncherKeepClearAreaHeight = 0
    private var lastLauncherKeepClearAreaHeightVisible = false

    // TODO(141886704): Find a way to remove this
    var lastSystemUiStateFlags = 0

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
        dragAndDrop: IDragAndDrop?
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
        this.unfoldAnimation = unfoldAnimation
        this.dragAndDrop = dragAndDrop
        linkToDeath()
        // re-attach the listeners once missing due to setProxy has not been initialized yet.
        setPipAnimationListener(pipAnimationListener)
        setBubblesListener(bubblesListener)
        registerSplitScreenListener(splitScreenListener)
        registerSplitSelectListener(splitSelectListener)
        setHomeTransitionListener(homeTransitionListener)
        setStartingWindowListener(startingWindowListener)
        setLauncherUnlockAnimationController(
            launcherActivityClass,
            launcherUnlockAnimationController
        )
        LinkedHashMap(remoteTransitions).forEach(this::registerRemoteTransition)
        setupTransactionQueue()
        registerRecentTasksListener(recentTasksListener)
        setBackToLauncherCallback(backToLauncherCallback, backToLauncherRunner)
        setUnfoldAnimationListener(unfoldAnimationListener)
        setDesktopTaskListener(desktopTaskListener)
        setAssistantOverridesRequested(
            AssistUtils.newInstance(context).sysUiAssistOverrideInvocationTypes
        )
    }

    /**
     * Clear the proxy to release held resources and turn the majority of its operations into no-ops
     */
    @MainThread
    fun clearProxy() {
        setProxy(null, null, null, null, null, null, null, null, null, null, null, null, null)
    }

    val isActive: Boolean
        get() = systemUiProxy != null

    private fun linkToDeath() {
        tryOrLog("Failed to link sysui proxy death recipient") {
            systemUiProxy?.asBinder()?.linkToDeath(systemUiProxyDeathRecipient, 0 /* flags */)
        }
    }

    private fun unlinkToDeath() {
        systemUiProxy?.asBinder()?.unlinkToDeath(systemUiProxyDeathRecipient, 0 /* flags */)
    }

    fun onBackPressed() {
        tryOrLog("Failed call onBackPressed") { systemUiProxy?.onBackPressed() }
    }

    fun onImeSwitcherPressed() {
        tryOrLog("Failed call onImeSwitcherPressed") { systemUiProxy?.onImeSwitcherPressed() }
    }

    fun setHomeRotationEnabled(enabled: Boolean) {
        tryOrLog("Failed call setHomeRotationEnabled") {
            systemUiProxy?.setHomeRotationEnabled(enabled)
        }
    }

    fun startScreenPinning(taskId: Int) {
        tryOrLog("Failed call startScreenPinning") { systemUiProxy?.startScreenPinning(taskId) }
    }

    fun onOverviewShown(fromHome: Boolean, tag: String?) {
        try {
            systemUiProxy?.onOverviewShown(fromHome)
        } catch (e: RemoteException) {
            Log.w(tag, "Failed call onOverviewShown from: ${if (fromHome) "home" else "app"}", e)
        }
    }

    @MainThread
    fun onStatusBarTouchEvent(event: MotionEvent) {
        Preconditions.assertUIThread()
        tryOrLog("Failed call onStatusBarTouchEvent with arg: $event") {
            systemUiProxy?.onStatusBarTouchEvent(event)
        }
    }

    fun onStatusBarTrackpadEvent(event: MotionEvent) {
        tryOrLog("Failed call onStatusBarTrackpadEvent with arg: $event") {
            systemUiProxy?.onStatusBarTrackpadEvent(event)
        }
    }

    fun onAssistantProgress(progress: Float) {
        tryOrLog("Failed call onAssistantProgress with progress: $progress") {
            systemUiProxy?.onAssistantProgress(progress)
        }
    }

    fun onAssistantGestureCompletion(velocity: Float) {
        tryOrLog("Failed call onAssistantGestureCompletion") {
            systemUiProxy?.onAssistantGestureCompletion(velocity)
        }
    }

    fun startAssistant(args: Bundle?) {
        tryOrLog("Failed call startAssistant") { systemUiProxy?.startAssistant(args) }
    }

    fun setAssistantOverridesRequested(invocationTypes: IntArray?) {
        tryOrLog("Failed call setAssistantOverridesRequested") {
            systemUiProxy?.setAssistantOverridesRequested(invocationTypes)
        }
    }

    fun animateNavBarLongPress(isTouchDown: Boolean, shrink:Boolean, durationMs: Long) {
        tryOrLog("Failed call animateNavBarLongPress") {
            systemUiProxy?.animateNavBarLongPress(isTouchDown, shrink, durationMs)
        }
    }

    fun notifyAccessibilityButtonClicked(displayId: Int) {
        tryOrLog("Failed call notifyAccessibilityButtonClicked") {
            systemUiProxy?.notifyAccessibilityButtonClicked(displayId)
        }
    }

    fun notifyAccessibilityButtonLongClicked() {
        tryOrLog("Failed call notifyAccessibilityButtonLongClicked") {
            systemUiProxy?.notifyAccessibilityButtonLongClicked()
        }
    }

    fun stopScreenPinning() {
        tryOrLog("Failed call stopScreenPinning") { systemUiProxy?.stopScreenPinning() }
    }

    fun notifyPrioritizedRotation(rotation: Int) {
        tryOrLog("Failed call notifyPrioritizedRotation with arg: $rotation") {
            systemUiProxy?.notifyPrioritizedRotation(rotation)
        }
    }

    fun notifyTaskbarStatus(visible: Boolean, stashed: Boolean) {
        tryOrLog("Failed call notifyTaskbarStatus with arg: $visible, $stashed") {
            systemUiProxy?.notifyTaskbarStatus(visible, stashed)
        }
    }

    /**
     * NOTE: If called to suspend, caller MUST call this method to also un-suspend
     *
     * @param suspend should be true to stop auto-hide, false to resume normal behavior
     */
    fun notifyTaskbarAutohideSuspend(suspend: Boolean) {
        tryOrLog("Failed call notifyTaskbarAutohideSuspend with arg: $suspend") {
            systemUiProxy?.notifyTaskbarAutohideSuspend(suspend)
        }
    }

    fun takeScreenshot(request: ScreenshotRequest?) {
        tryOrLog("Failed call takeScreenshot") { systemUiProxy?.takeScreenshot(request) }
    }

    fun expandNotificationPanel() {
        tryOrLog("Failed call expandNotificationPanel") { systemUiProxy?.expandNotificationPanel() }
    }

    fun toggleNotificationPanel() {
        tryOrLog("Failed call toggleNotificationPanel") { systemUiProxy?.toggleNotificationPanel() }
    }

    //
    // Pip
    //
    /** Sets the shelf height. */
    fun setShelfHeight(visible: Boolean, shelfHeight: Int) {
        Message.obtain(asyncHandler, MSG_SET_SHELF_HEIGHT, if (visible) 1 else 0, shelfHeight)
            .sendToTarget()
    }

    @WorkerThread
    private fun setShelfHeightAsync(visibleInt: Int, shelfHeight: Int) {
        val visible = visibleInt != 0
        if (visible == lastShelfVisible && shelfHeight == lastShelfHeight) return

        pip?.let {
            tryOrLog("Failed call setShelfHeight visible: $visible height: $shelfHeight") {
                it.setShelfHeight(visible, shelfHeight)
                lastShelfVisible = visible
                lastShelfHeight = shelfHeight
            }
        }
    }

    /**
     * Sets the height of the keep clear area that is going to be reported by the Launcher for the
     * Hotseat.
     */
    fun setLauncherKeepClearAreaHeight(visible: Boolean, height: Int) {
        Message.obtain(
                asyncHandler,
                MSG_SET_LAUNCHER_KEEP_CLEAR_AREA_HEIGHT,
                if (visible) 1 else 0,
                height
            )
            .sendToTarget()
    }

    @WorkerThread
    private fun setLauncherKeepClearAreaHeight(visibleInt: Int, height: Int) {
        val visible = visibleInt != 0
        if (
            visible == lastLauncherKeepClearAreaHeightVisible &&
                height == lastLauncherKeepClearAreaHeight
        )
            return

        pip?.let {
            tryOrLog("Failed call setLauncherKeepClearAreaHeight vis: $visible height: $height") {
                it.setLauncherKeepClearAreaHeight(visible, height)
                lastLauncherKeepClearAreaHeightVisible = visible
                lastLauncherKeepClearAreaHeight = height
            }
        }
    }

    /** Sets listener to get pip animation callbacks. */
    fun setPipAnimationListener(listener: IPipAnimationListener?) {
        tryOrLog("Failed call setPinnedStackAnimationListener") {
            pip?.setPipAnimationListener(listener)
        }
        pipAnimationListener = listener
    }

    /** @return Destination bounds of auto-pip animation, `null` if the animation is not ready. */
    fun startSwipePipToHome(
        componentName: ComponentName?,
        activityInfo: ActivityInfo?,
        pictureInPictureParams: PictureInPictureParams?,
        launcherRotation: Int,
        hotseatKeepClearArea: Rect?
    ): Rect? {
        return tryOrElse("Failed call startSwipePipToHome", null) {
            return pip?.startSwipePipToHome(
                componentName,
                activityInfo,
                pictureInPictureParams,
                launcherRotation,
                hotseatKeepClearArea
            )
        }
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
        overlay: SurfaceControl?
    ) {
        tryOrLog("Failed call stopSwipePipToHome") {
            pip?.stopSwipePipToHome(taskId, componentName, destinationBounds, overlay)
        }
    }

    /**
     * Notifies WM Shell that launcher has aborted all the animation for swipe to home. WM Shell can
     * use this callback to clean up its internal states.
     */
    fun abortSwipePipToHome(taskId: Int, componentName: ComponentName?) {
        tryOrLog("Failed call abortSwipePipToHome") {
            pip?.abortSwipePipToHome(taskId, componentName)
        }
    }

    /** Sets the next pip animation type to be the alpha animation. */
    fun setPipAnimationTypeToAlpha() {
        tryOrLog("Failed call setPipAnimationTypeToAlpha") { pip?.setPipAnimationTypeToAlpha() }
    }

    /** Sets the app icon size in pixel used by Launcher all apps. */
    fun setLauncherAppIconSize(iconSizePx: Int) {
        tryOrLog("Failed call setLauncherAppIconSize") { pip?.setLauncherAppIconSize(iconSizePx) }
    }

    //
    // Bubbles
    //
    /** Sets the listener to be notified of bubble state changes. */
    fun setBubblesListener(listener: IBubblesListener?) {
        bubbles?.let {
            tryOrLog("Failed call setLauncherAppIconSize") {
                bubblesListener?.let(it::unregisterBubbleListener)
                listener?.let(it::registerBubbleListener)
            }
        }
        bubblesListener = listener
    }

    /**
     * Tells SysUI to show the bubble with the provided key.
     *
     * @param key the key of the bubble to show.
     * @param bubbleBarOffsetX the offset of the bubble bar from the edge of the screen on the X
     *   axis.
     * @param bubbleBarOffsetY the offset of the bubble bar from the edge of the screen on the Y
     *   axis.
     */
    fun showBubble(key: String?, bubbleBarOffsetX: Int, bubbleBarOffsetY: Int) {
        tryOrLog("Failed call showBubble") {
            bubbles?.showBubble(key, bubbleBarOffsetX, bubbleBarOffsetY)
        }
    }

    /**
     * Tells SysUI to remove the bubble with the provided key.
     *
     * @param key the key of the bubble to show.
     */
    fun removeBubble(key: String?) {
        tryOrLog("Failed call removeBubble") { bubbles?.removeBubble(key) }
    }

    /** Tells SysUI to remove all bubbles. */
    fun removeAllBubbles() {
        tryOrLog("Failed call removeAllBubbles") { bubbles?.removeAllBubbles() }
    }

    /** Tells SysUI to collapse the bubbles. */
    fun collapseBubbles() {
        tryOrLog("Failed call collapseBubbles") { bubbles?.collapseBubbles() }
    }

    /**
     * Tells SysUI when the bubble is being dragged. Should be called only when the bubble bar is
     * expanded.
     *
     * @param bubbleKey the key of the bubble to collapse/expand
     * @param isBeingDragged whether the bubble is being dragged
     */
    fun onBubbleDrag(bubbleKey: String?, isBeingDragged: Boolean) {
        tryOrLog("Failed call onBubbleDrag") { bubbles?.onBubbleDrag(bubbleKey, isBeingDragged) }
    }

    /**
     * Tells SysUI to show user education relative to the reference point provided.
     *
     * @param position the bubble bar top center position in Screen coordinates.
     */
    fun showUserEducation(position: Point) {
        tryOrLog("Failed call showUserEducation") {
            bubbles?.showUserEducation(position.x, position.y)
        }
    }

    //
    // Splitscreen
    //
    fun registerSplitScreenListener(listener: ISplitScreenListener?) {
        tryOrLog("Failed call registerSplitScreenListener") {
            splitScreen?.registerSplitScreenListener(listener)
        }
        splitScreenListener = listener
    }

    fun registerSplitSelectListener(listener: ISplitSelectListener?) {
        tryOrLog("Failed call registerSplitScreenListener") {
            splitScreen?.registerSplitSelectListener(listener)
        }
        splitSelectListener = listener
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
        instanceId: InstanceId?
    ) {
        tryOrLog(splitFailureMessage("startTasks", "RemoteException")) {
            splitScreen?.startTasks(
                taskId1,
                options1,
                taskId2,
                options2,
                splitPosition,
                snapPosition,
                remoteTransition,
                instanceId
            )
        }
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
        instanceId: InstanceId?
    ) {
        tryOrLog(splitFailureMessage("startIntentAndTask", "RemoteException")) {
            splitScreen?.startIntentAndTask(
                pendingIntent,
                userId1,
                options1,
                taskId,
                options2,
                splitPosition,
                snapPosition,
                remoteTransition,
                instanceId
            )
        }
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
        instanceId: InstanceId?
    ) {
        tryOrLog(splitFailureMessage("startIntents", "RemoteException")) {
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
                instanceId
            )
        }
    }

    fun startShortcutAndTask(
        shortcutInfo: ShortcutInfo?,
        options1: Bundle?,
        taskId: Int,
        options2: Bundle?,
        @StagePosition splitPosition: Int,
        @PersistentSnapPosition snapPosition: Int,
        remoteTransition: RemoteTransition?,
        instanceId: InstanceId?
    ) {
        tryOrLog(splitFailureMessage("startShortcutAndTask", "RemoteException")) {
            splitScreen?.startShortcutAndTask(
                shortcutInfo,
                options1,
                taskId,
                options2,
                splitPosition,
                snapPosition,
                remoteTransition,
                instanceId
            )
        }
    }

    /** Start multiple tasks in split-screen simultaneously. */
    fun startTasksWithLegacyTransition(
        taskId1: Int,
        options1: Bundle?,
        taskId2: Int,
        options2: Bundle?,
        @StagePosition splitPosition: Int,
        @PersistentSnapPosition snapPosition: Int,
        adapter: RemoteAnimationAdapter?,
        instanceId: InstanceId?
    ) {
        tryOrLog(splitFailureMessage("startTasksWithLegacyTransition", "RemoteException")) {
            splitScreen?.startTasksWithLegacyTransition(
                taskId1,
                options1,
                taskId2,
                options2,
                splitPosition,
                snapPosition,
                adapter,
                instanceId
            )
        }
    }

    fun startIntentAndTaskWithLegacyTransition(
        pendingIntent: PendingIntent?,
        userId1: Int,
        options1: Bundle?,
        taskId: Int,
        options2: Bundle?,
        @StagePosition splitPosition: Int,
        @PersistentSnapPosition snapPosition: Int,
        adapter: RemoteAnimationAdapter?,
        instanceId: InstanceId?
    ) {
        tryOrLog(splitFailureMessage("startIntentAndTaskWithLegacyTransition", "RemoteException")) {
            splitScreen?.startIntentAndTaskWithLegacyTransition(
                pendingIntent,
                userId1,
                options1,
                taskId,
                options2,
                splitPosition,
                snapPosition,
                adapter,
                instanceId
            )
        }
    }

    fun startShortcutAndTaskWithLegacyTransition(
        shortcutInfo: ShortcutInfo?,
        options1: Bundle?,
        taskId: Int,
        options2: Bundle?,
        @StagePosition splitPosition: Int,
        @PersistentSnapPosition snapPosition: Int,
        adapter: RemoteAnimationAdapter?,
        instanceId: InstanceId?
    ) {
        tryOrLog(
            splitFailureMessage("startShortcutAndTaskWithLegacyTransition", "RemoteException")
        ) {
            splitScreen?.startShortcutAndTaskWithLegacyTransition(
                shortcutInfo,
                options1,
                taskId,
                options2,
                splitPosition,
                snapPosition,
                adapter,
                instanceId
            )
        }
    }

    /**
     * Starts a pair of intents or shortcuts in split-screen using legacy transition. Passing a
     * non-null shortcut info means to start the app as a shortcut.
     */
    fun startIntentsWithLegacyTransition(
        pendingIntent1: PendingIntent?,
        userId1: Int,
        shortcutInfo1: ShortcutInfo?,
        options1: Bundle?,
        pendingIntent2: PendingIntent?,
        userId2: Int,
        shortcutInfo2: ShortcutInfo?,
        options2: Bundle?,
        @StagePosition sidePosition: Int,
        @PersistentSnapPosition snapPosition: Int,
        adapter: RemoteAnimationAdapter?,
        instanceId: InstanceId?
    ) {
        tryOrLog(splitFailureMessage("startIntentsWithLegacyTransition", "RemoteException")) {
            splitScreen?.startIntentsWithLegacyTransition(
                pendingIntent1,
                userId1,
                shortcutInfo1,
                options1,
                pendingIntent2,
                userId2,
                shortcutInfo2,
                options2,
                sidePosition,
                snapPosition,
                adapter,
                instanceId
            )
        }
    }

    fun startShortcut(
        packageName: String?,
        shortcutId: String?,
        position: Int,
        options: Bundle?,
        user: UserHandle?,
        instanceId: InstanceId?
    ) {
        tryOrLog(splitFailureMessage("startShortcut", "RemoteException")) {
            splitScreen?.startShortcut(packageName, shortcutId, position, options, user, instanceId)
        }
    }

    fun startIntent(
        intent: PendingIntent?,
        userId: Int,
        fillInIntent: Intent?,
        position: Int,
        options: Bundle?,
        instanceId: InstanceId?
    ) {
        tryOrLog(splitFailureMessage("startIntent", "RemoteException")) {
            splitScreen?.startIntent(intent, userId, fillInIntent, position, options, instanceId)
        }
    }

    /**
     * Call this when going to recents so that shell can set-up and provide appropriate leashes for
     * animation (eg. DividerBar).
     *
     * @return RemoteAnimationTargets of windows that need to animate but only exist in shell.
     */
    fun onGoingToRecentsLegacy(
        apps: Array<RemoteAnimationTarget?>?
    ): Array<RemoteAnimationTarget>? {
        if (TaskAnimationManager.ENABLE_SHELL_TRANSITIONS) return null
        return tryOrElse("Failed call onGoingToRecentsLegacy", null) {
            return splitScreen?.onGoingToRecentsLegacy(apps)
        }
    }

    fun onStartingSplitLegacy(apps: Array<RemoteAnimationTarget?>?): Array<RemoteAnimationTarget>? {
        return tryOrElse("Failed call onStartingSplitLegacy", null) {
            return splitScreen?.onStartingSplitLegacy(apps)
        }
    }

    //
    // One handed
    //
    fun startOneHandedMode() {
        tryOrLog("Failed call startOneHandedMode") { oneHanded?.startOneHanded() }
    }

    fun stopOneHandedMode() {
        tryOrLog("Failed call startOneHandedstopOneHandedModeMode") { oneHanded?.stopOneHanded() }
    }

    //
    // Remote transitions
    //
    fun registerRemoteTransition(remoteTransition: RemoteTransition, filter: TransitionFilter) {
        tryOrLog("Failed call registerRemoteTransition") {
            shellTransitions?.registerRemote(filter, remoteTransition)
        }
        if (!remoteTransitions.containsKey(remoteTransition)) {
            remoteTransitions[remoteTransition] = filter
        }
    }

    fun unregisterRemoteTransition(remoteTransition: RemoteTransition) {
        tryOrLog("Failed call unregisterRemoteTransition") {
            shellTransitions?.unregisterRemote(remoteTransition)
        }
        remoteTransitions.remove(remoteTransition)
    }

    fun setHomeTransitionListener(listener: IHomeTransitionListener?) {
        if (!FeatureFlags.enableHomeTransitionListener()) return

        homeTransitionListener = listener

        tryOrLog("Failed call unregisterRemoteTransition") {
            shellTransitions?.setHomeTransitionListener(listener)
                ?: Log.w(
                    TAG,
                    "Unable to call setHomeTransitionListener because ShellTransitions is null"
                )
        }
    }

    /**
     * Use SystemUI's transaction-queue instead of Launcher's independent one. This is necessary if
     * Launcher and SystemUI need to coordinate transactions (eg. for shell transitions).
     */
    fun shareTransactionQueue() {
        if (originalTransactionToken == null) {
            originalTransactionToken = SurfaceControl.Transaction.getDefaultApplyToken()
        }
        setupTransactionQueue()
    }

    /** Switch back to using Launcher's independent transaction queue. */
    fun unshareTransactionQueue() {
        originalTransactionToken?.let { SurfaceControl.Transaction.setDefaultApplyToken(it) }
        originalTransactionToken = null
    }

    private fun setupTransactionQueue() {
        originalTransactionToken ?: return

        var transitions = shellTransitions ?: run {
            SurfaceControl.Transaction.setDefaultApplyToken(originalTransactionToken)
            return
        }
        tryOrLog("Error getting Shell's apply token") {
            transitions.getShellApplyToken()?.apply {
                SurfaceControl.Transaction.setDefaultApplyToken(this)
            } ?: Log.e(TAG, "Didn't receive apply token from Shell")
        }
    }

    //
    // Starting window
    //
    /** Sets listener to get callbacks when launching a task. */
    fun setStartingWindowListener(listener: IStartingWindowListener?) {
        tryOrLog("Failed call setStartingWindowListener") {
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
        controller: ILauncherUnlockAnimationController?
    ) {
        tryOrLog("Failed call setLauncherUnlockAnimationController") {
            sysuiUnlockAnimationController?.let {
                it.setLauncherUnlockController(activityClass, controller)
                controller?.dispatchSmartspaceStateToSysui()
            }
        }

        launcherActivityClass = activityClass
        launcherUnlockAnimationController = controller
    }

    /**
     * Tells System UI that the Launcher's smartspace state has been updated, so that it can prepare
     * the unlock animation accordingly.
     */
    fun notifySysuiSmartspaceStateUpdated(state: SmartspaceState?) {
        tryOrLog("Failed call notifySysuiSmartspaceStateUpdated") {
            sysuiUnlockAnimationController?.onLauncherSmartspaceStateUpdated(state)
        }
    }

    //
    // Recents
    //
    fun registerRecentTasksListener(listener: IRecentTasksListener?) {
        tryOrLog("Failed call registerRecentTasksListener") {
            recentTasks?.registerRecentTasksListener(listener)
        }
        recentTasksListener = listener
    }

    //
    // Back navigation transitions
    //
    /** Sets the launcher [android.window.IOnBackInvokedCallback] to shell */
    fun setBackToLauncherCallback(
        callback: IOnBackInvokedCallback?,
        runner: IRemoteAnimationRunner?
    ) {
        backToLauncherCallback = callback
        backToLauncherRunner = runner

        callback ?: return
        tryOrLog("Failed call setBackToLauncherCallback") {
            backAnimation?.setBackToLauncherCallback(callback, runner)
        }
    }

    /**
     * Clears the previously registered [IOnBackInvokedCallback].
     *
     * @param callback The previously registered callback instance.
     */
    fun clearBackToLauncherCallback(callback: IOnBackInvokedCallback) {
        if (backToLauncherCallback != callback) {
            return
        }
        backToLauncherCallback = null
        backToLauncherRunner = null
        tryOrLog("Failed call clearBackToLauncherCallback") {
            backAnimation?.clearBackToLauncherCallback()
        }
    }

    /** Called when the status bar color needs to be customized when back navigation. */
    fun customizeStatusBarAppearance(appearance: AppearanceRegion?) {
        tryOrLog("Failed call customizeStatusBarAppearance") {
            backAnimation?.customizeStatusBarAppearance(appearance)
        }
    }

    fun getRecentTasks(numTasks: Int, userId: Int): ArrayList<GroupedRecentTaskInfo> {
        return tryOrElse("Failed call getRecentTasks", ArrayList()) {
            return recentTasks
                ?.getRecentTasks(numTasks, ActivityManager.RECENT_IGNORE_UNAVAILABLE, userId)
                ?.let { ArrayList(it.asList()) }
                ?: ArrayList()
        }
    }

    /** Gets the set of running tasks. */
    fun getRunningTasks(numTasks: Int): ArrayList<RunningTaskInfo> {
        if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_PC)) {
            return ArrayList()
        }

        return tryOrElse("Failed call getRunningTasks", ArrayList()) {
            recentTasks?.getRunningTasks(numTasks)?.let { ArrayList(it.asList()) } ?: ArrayList()
        }
    }

    private fun handleMessageAsync(msg: Message): Boolean {
        when (msg.what) {
            MSG_SET_SHELF_HEIGHT -> {
                setShelfHeightAsync(msg.arg1, msg.arg2)
                return true
            }
            MSG_SET_LAUNCHER_KEEP_CLEAR_AREA_HEIGHT -> {
                setLauncherKeepClearAreaHeight(msg.arg1, msg.arg2)
                return true
            }
        }
        return false
    }

    //
    // Desktop Mode
    //
    /** Call shell to show all apps active on the desktop */
    fun showDesktopApps(displayId: Int, transition: RemoteTransition?) {
        tryOrLog("Failed call showDesktopApps") {
            desktopMode?.showDesktopApps(displayId, transition)
        }
    }

    /** Call shell to stash desktop apps */
    fun stashDesktopApps(displayId: Int) {
        tryOrLog("Failed call stashDesktopApps") { desktopMode?.stashDesktopApps(displayId) }
    }

    /** Call shell to hide desktop apps that may be stashed */
    fun hideStashedDesktopApps(displayId: Int) {
        tryOrLog("Failed call hideStashedDesktopApps") {
            desktopMode?.hideStashedDesktopApps(displayId)
        }
    }

    /** If task with the given id is on the desktop, bring it to front */
    fun showDesktopApp(taskId: Int) {
        tryOrLog("Failed call showDesktopApp") { desktopMode?.showDesktopApp(taskId) }
    }

    /** Call shell to get number of visible freeform tasks */
    fun getVisibleDesktopTaskCount(displayId: Int): Int {
        return tryOrElse("Failed call getVisibleDesktopTaskCount", 0) {
            return desktopMode?.getVisibleTaskCount(displayId) ?: 0
        }
    }

    /** Set a listener on shell to get updates about desktop task state */
    fun setDesktopTaskListener(listener: IDesktopTaskListener?) {
        desktopTaskListener = listener
        tryOrLog("Failed call setDesktopTaskListener") { desktopMode?.setTaskListener(listener) }
    }

    /** Perform cleanup transactions after animation to split select is complete */
    fun onDesktopSplitSelectAnimComplete(taskInfo: RunningTaskInfo?) {
        tryOrLog("Failed call onDesktopSplitSelectAnimComplete") {
            desktopMode?.onDesktopSplitSelectAnimComplete(taskInfo)
        }
    }
    //
    // Unfold transition
    //
    /** Sets the unfold animation lister to sysui. */
    fun setUnfoldAnimationListener(callback: IUnfoldTransitionListener?) {
        unfoldAnimationListener = callback
        tryOrLog("Failed call setUnfoldAnimationListener") {
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
        listener: RecentsAnimationListener
    ): Boolean {

        val runner: IRecentsAnimationRunner =
            object : IRecentsAnimationRunner.Stub() {
                override fun onAnimationStart(
                    controller: IRecentsAnimationController,
                    apps: Array<RemoteAnimationTarget>,
                    wallpapers: Array<RemoteAnimationTarget>,
                    homeContentInsets: Rect,
                    minimizedHomeBounds: Rect,
                    extras: Bundle?
                ) {
                    // Aidl bundles need to explicitly set class loader
                    // https://developer.android.com/guide/components/aidl#Bundles
                    extras?.classLoader = javaClass.classLoader
                    listener.onAnimationStart(
                        RecentsAnimationControllerCompat(controller),
                        apps,
                        wallpapers,
                        homeContentInsets,
                        minimizedHomeBounds,
                        extras
                    )
                }

                override fun onAnimationCanceled(
                    taskIds: IntArray,
                    taskSnapshots: Array<TaskSnapshot>
                ) {
                    listener.onAnimationCanceled(ThumbnailData.wrap(taskIds, taskSnapshots))
                }

                override fun onTasksAppeared(apps: Array<RemoteAnimationTarget>) {
                    listener.onTasksAppeared(apps)
                }
            }

        return tryOrElse("Error starting recents via shell", false) {
            recentTasks?.let {
                it.startRecentsTransition(
                    recentsPendingIntent,
                    intent,
                    options.toBundle(),
                    context.iApplicationThread,
                    runner
                )
                return true
            }
                ?: run {
                    ActiveGestureLog.INSTANCE.addLog(
                        "Null recentTasks",
                        ActiveGestureErrorDetector.GestureEvent.RECENT_TASKS_MISSING
                    )
                    false
                }
        }
    }

    //
    // Drag and drop
    //

    /**
     * For testing purposes. Returns `true` only if the shell drop target has shown and drawn and is
     * ready to handle drag events and the subsequent drop.
     */
    val isDragAndDropReady: Boolean
        get() =
            tryOrElse("Error querying drag state", false) {
                dragAndDrop?.isReadyToHandleDrag ?: false
            }

    fun dump(pw: PrintWriter) {
        pw.println("$TAG:")
        pw.println("\tsystemUiProxy=$systemUiProxy")
        pw.println("\tpip=$pip")
        pw.println("\tpipAnimationListener=$pipAnimationListener")
        pw.println("\tbubbles=$bubbles")
        pw.println("\tbubblesListener=$bubblesListener")
        pw.println("\tsplitScreen=$splitScreen")
        pw.println("\tsplitScreenListener=$splitScreenListener")
        pw.println("\tsplitSelectListener=$splitSelectListener")
        pw.println("\toneHanded=$oneHanded")
        pw.println("\tshellTransitions=$shellTransitions")
        pw.println("\thomeTransitionListener=$homeTransitionListener")
        pw.println("\tstartingWindow=$startingWindow")
        pw.println("\tstartingWindowListener=$startingWindowListener")
        pw.println("\tsysuiUnlockAnimationController=$sysuiUnlockAnimationController")
        pw.println("\tlauncherActivityClass=$launcherActivityClass")
        pw.println("\tlauncherUnlockAnimationController=$launcherUnlockAnimationController")
        pw.println("\trecentTasks=$recentTasks")
        pw.println("\trecentTasksListener=$recentTasksListener")
        pw.println("\tbackAnimation=$backAnimation")
        pw.println("\tbackToLauncherCallback=$backToLauncherCallback")
        pw.println("\tbackToLauncherRunner=$backToLauncherRunner")
        pw.println("\tdesktopMode=$desktopMode")
        pw.println("\tdesktopTaskListener=$desktopTaskListener")
        pw.println("\tunfoldAnimation=$unfoldAnimation")
        pw.println("\tunfoldAnimationListener=$unfoldAnimationListener")
        pw.println("\tdragAndDrop=$dragAndDrop")
    }

    companion object {
        private const val TAG = "SystemUiProxy"
        private const val MSG_SET_SHELF_HEIGHT = 1
        private const val MSG_SET_LAUNCHER_KEEP_CLEAR_AREA_HEIGHT = 2

        @JvmField
        val INSTANCE = MainThreadInitializedObject { context: Context -> SystemUiProxy(context) }

        private inline fun tryOrLog(msg: String, f: () -> Unit) =
            try {
                f()
            } catch (e: RemoteException) {
                Log.w(TAG, msg, e)
            }

        private inline fun <T> tryOrElse(msg: String, fallback: T, f: () -> T): T =
            try {
                f()
            } catch (e: RemoteException) {
                Log.w(TAG, msg, e)
                fallback
            }
    }
}
