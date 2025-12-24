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

package com.android.wm.shell.splitscreen;

import static com.android.wm.shell.Flags.enable2x1Split;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.ActivityManager.RunningTaskInfo;
import android.app.IActivityTaskManager;
import android.app.PendingIntent;
import android.app.TaskInfo;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ShortcutInfo;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.UserHandle;
import android.view.RemoteAnimationTarget;
import android.view.SurfaceControl;
import android.window.RemoteTransition;
import android.window.TransitionInfo;
import android.window.TransitionRequestInfo;
import android.window.WindowContainerToken;
import android.window.WindowContainerTransaction;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.logging.InstanceId;
import com.android.launcher3.icons.IconProvider;
import com.android.wm.shell.RootDisplayAreaOrganizer;
import com.android.wm.shell.RootTaskDisplayAreaOrganizer;
import com.android.wm.shell.ShellTaskOrganizer;
import com.android.wm.shell.bubbles.BubbleController;
import com.android.wm.shell.common.DisplayChangeController;
import com.android.wm.shell.common.DisplayController;
import com.android.wm.shell.common.DisplayImeController;
import com.android.wm.shell.common.DisplayInsetsController;
import com.android.wm.shell.common.LaunchAdjacentController;
import com.android.wm.shell.common.ShellExecutor;
import com.android.wm.shell.common.SyncTransactionQueue;
import com.android.wm.shell.common.split.SplitLayout;
import com.android.wm.shell.common.split.SplitState;
import com.android.wm.shell.desktopmode.DesktopTasksController;
import com.android.wm.shell.desktopmode.DesktopUserRepositories;
import com.android.wm.shell.recents.RecentTasksController;
import com.android.wm.shell.shared.TransactionPool;
import com.android.wm.shell.shared.desktopmode.DesktopState;
import com.android.wm.shell.shared.split.SplitScreenConstants.PersistentSnapPosition;
import com.android.wm.shell.shared.split.SplitScreenConstants.SplitIndex;
import com.android.wm.shell.shared.split.SplitScreenConstants.SplitPosition;
import com.android.wm.shell.splitscreen.SplitScreen.StageType;
import com.android.wm.shell.transition.DefaultMixedHandler;
import com.android.wm.shell.transition.Transitions;
import com.android.wm.shell.windowdecor.WindowDecorViewModel;



import com.google.android.msdl.domain.MSDLPlayer;

import java.io.PrintWriter;
import java.util.Optional;
import java.util.concurrent.Executor;

/**
 * Coordinates the staging (visibility, sizing, ...) of the split-screen stages.
 */
public abstract class StageCoordinatorAbstract implements SplitLayout.SplitLayoutHandler,
        DisplayController.OnDisplaysChangedListener,
        DisplayChangeController.OnDisplayChangingListener, Transitions.TransitionHandler,
        ShellTaskOrganizer.TaskListener, StageTaskListener.StageListenerCallbacks,
        SplitMultiDisplayProvider {

    /**
     * Creates a new StageCoordinator.
     */
    public static StageCoordinator createStageCoordinator(Context context,
            int displayId, SyncTransactionQueue syncQueue,
            ShellTaskOrganizer taskOrganizer, DisplayController displayController,
            DisplayImeController displayImeController,
            DisplayInsetsController displayInsetsController, Transitions transitions,
            TransactionPool transactionPool, IconProvider iconProvider, ShellExecutor mainExecutor,
            Handler mainHandler, Optional<RecentTasksController> recentTasks,
            LaunchAdjacentController launchAdjacentController,
            Optional<WindowDecorViewModel> windowDecorViewModel, SplitState splitState,
            Optional<DesktopTasksController> desktopTasksController,
            Optional<DesktopUserRepositories> desktopUserRepositories,
            RootTaskDisplayAreaOrganizer rootTDAOrganizer,
            RootDisplayAreaOrganizer rootDisplayAreaOrganizer, DesktopState desktopState,
            IActivityTaskManager activityTaskManager, MSDLPlayer msdlPlayer,
            Optional<BubbleController> bubbleController) {
        if (enable2x1Split()) {
            return new StageCoordinator2(context, displayId, syncQueue, taskOrganizer,
                    displayController, displayImeController, displayInsetsController, transitions,
                    transactionPool, iconProvider, mainExecutor, mainHandler, recentTasks,
                    launchAdjacentController, windowDecorViewModel, splitState,
                    desktopTasksController, desktopUserRepositories, rootTDAOrganizer,
                    rootDisplayAreaOrganizer, desktopState, activityTaskManager, msdlPlayer,
                    bubbleController);
        }

        return new StageCoordinator(context, displayId, syncQueue, taskOrganizer,
                displayController, displayImeController, displayInsetsController, transitions,
                transactionPool, iconProvider, mainExecutor, mainHandler, recentTasks,
                launchAdjacentController, windowDecorViewModel, splitState,
                desktopTasksController, desktopUserRepositories, rootTDAOrganizer,
                rootDisplayAreaOrganizer, desktopState, activityTaskManager, msdlPlayer,
                bubbleController);
    }

    /// ////////////////////////////////////////////////////////////////////////////////////////////
    ///
    /// Register
    ///
    /// ////////////////////////////////////////////////////////////////////////////////////////////
    abstract void registerSplitScreenListener(SplitScreen.SplitScreenListener listener);

    abstract void unregisterSplitScreenListener(SplitScreen.SplitScreenListener listener);

    abstract void registerSplitSelectListener(SplitScreen.SplitSelectListener listener);

    abstract void unregisterSplitSelectListener(SplitScreen.SplitSelectListener listener);

    abstract void registerSplitAnimationListener(
            @NonNull SplitScreen.SplitInvocationListener listener, @NonNull Executor executor);

    /// ////////////////////////////////////////////////////////////////////////////////////////////
    ///
    /// Start a task in split.
    ///
    /// ////////////////////////////////////////////////////////////////////////////////////////////
    abstract void startShortcut(String packageName, String shortcutId,
            @SplitPosition int position, Bundle options, UserHandle user);

    /**
     * Use this method to launch an existing Task via a taskId.
     *
     * @param hideTaskToken If non-null, a task matching this token will be moved to back in the
     *                      same window container transaction as the starting of the intent.
     */
    abstract void startTask(int taskId, @SplitPosition int position,
            @Nullable Bundle options, @Nullable WindowContainerToken hideTaskToken,
            @SplitIndex int index);

    /**
     * Launches an activity into split.
     *
     * @param hideTaskToken If non-null, a task matching this token will be moved to back in the
     *                      same window container transaction as the starting of the intent.
     */
    abstract void startIntent(
            PendingIntent intent, Intent fillInIntent, @SplitPosition
            int position, @Nullable Bundle options, @Nullable WindowContainerToken hideTaskToken,
            @Nullable WindowContainerTransaction transaction,
            @SplitIndex int index, int displayId);

    /**
     * Starts 2 tasks in one transition.
     *
     * @param taskId1 starts in the mSideStage
     * @param taskId2 starts in the mainStage #startWithTask()
     */
    abstract void startTasks(int taskId1, @Nullable Bundle options1, int taskId2,
            @Nullable Bundle options2, @SplitPosition int splitPosition,
            @PersistentSnapPosition int snapPosition,
            @Nullable RemoteTransition remoteTransition, InstanceId instanceId);

    /** Start an intent and a task to a split pair in one transition. */
    abstract void startIntentAndTask(PendingIntent pendingIntent, Intent fillInIntent,
            @Nullable Bundle options1, int taskId, @Nullable Bundle options2,
            @SplitPosition int splitPosition,
            @PersistentSnapPosition int snapPosition,
            @Nullable RemoteTransition remoteTransition, InstanceId instanceId);

    /** Starts a shortcut and a task to a split pair in one transition. */
    abstract void startShortcutAndTask(ShortcutInfo shortcutInfo, @Nullable Bundle options1,
            int taskId, @Nullable Bundle options2, @SplitPosition
            int splitPosition, @PersistentSnapPosition int snapPosition,
            @Nullable RemoteTransition remoteTransition, InstanceId instanceId);

    abstract void startIntents(PendingIntent pendingIntent1, Intent fillInIntent1,
            @Nullable ShortcutInfo shortcutInfo1, @Nullable Bundle options1,
            @Nullable PendingIntent pendingIntent2, Intent fillInIntent2,
            @Nullable ShortcutInfo shortcutInfo2, @Nullable Bundle options2,
            @SplitPosition int splitPosition,
            @PersistentSnapPosition int snapPosition,
            @Nullable RemoteTransition remoteTransition, InstanceId instanceId);

    abstract Bundle resolveStartStage(@SplitScreen.StageType int stage,
            @SplitPosition int position, @Nullable Bundle options,
            @Nullable WindowContainerTransaction wct);

    /// ////////////////////////////////////////////////////////////////////////////////////////////
    ///
    /// Split operations.
    ///
    /// ////////////////////////////////////////////////////////////////////////////////////////////
    abstract void requestEnterSplitSelect(RunningTaskInfo taskInfo,
            int splitPosition, Rect taskBounds, boolean startRecents,
            @Nullable WindowContainerTransaction withRecentsWct);

    abstract boolean moveToStage(RunningTaskInfo task, @SplitPosition int stagePosition,
            WindowContainerTransaction wct);

    abstract void switchSplitPosition(String reason);

    abstract void setSideStagePosition(@SplitPosition int sideStagePosition,
            @Nullable WindowContainerTransaction wct);

    /**
     * Prepare transaction to active split screen. If there's a task indicated, the task will be put
     * into side stage.
     */
    abstract void prepareEnterSplitScreen(WindowContainerTransaction wct,
            @Nullable RunningTaskInfo taskInfo, @SplitPosition
            int startPosition, boolean resizeAnim, @SplitIndex int index);

    abstract void finishEnterSplitScreen(SurfaceControl.Transaction finishT);

    /**
     * Unlike exitSplitScreen, this takes a stagetype vs an actual stage-reference and populates
     * an existing WindowContainerTransaction (rather than applying immediately). This is intended
     * to be used when exiting split might be bundled with other window operations.
     *
     * @param stageToTop The stage to move to the top
     */
    abstract void prepareExitSplitScreen(@SplitScreen.StageType int stageToTop,
            @NonNull WindowContainerTransaction wct, @SplitScreenController.ExitReason
            int exitReason);

    abstract void prepareEvictNonOpeningChildTasks(@SplitPosition int position,
            RemoteAnimationTarget[] apps, WindowContainerTransaction wct);

    abstract void prepareEvictInvisibleChildTasks(WindowContainerTransaction wct);

    abstract void dismissSplitScreen(int toTopTaskId,
            @SplitScreenController.ExitReason int exitReason);

    abstract void grantFocusToPosition(boolean leftOrTop);

    abstract void clearSplitPairedInRecents(@SplitScreenController.ExitReason int exitReason);

    /**
     * Dismisses split in the background.
     */
    public abstract void dismissSplitInBackground(@SplitScreenController.ExitReason int exitReason);

    /**
     * Update surfaces of the split screen layout based on the current state
     *
     * @param transaction to write the updates to
     */
    public abstract void updateSurfaces(SurfaceControl.Transaction transaction);

    /**
     * This is used for mixed-transition scenarios (specifically when transitioning one split task
     * into PIP). For such scenarios, just make sure to include exiting split or entering split when
     * appropriate. This is an addition to
     * {@link #addEnterOrExitForPipIfNeeded(TransitionRequestInfo, WindowContainerTransaction)},
     * for PiP2 where PiP-able task can also come in through the pip change request field,
     * and this method is provided to explicitly prepare an exit in that case.
     *
     * This is only called if requestImpliesSplitToPip() returns `true`.
     */
    public abstract void removePipFromSplitIfNeeded(@NonNull TransitionRequestInfo request,
            @NonNull WindowContainerTransaction outWCT);

    /**
     * This is used for mixed-transition scenarios (specifically when transitioning one split task
     * into PIP). For such scenarios, just make sure to include exiting split or entering split when
     * appropriate.
     *
     * This is only called if requestImpliesSplitToPip() returns `true`.
     */
    public abstract void addEnterOrExitForPipIfNeeded(@Nullable TransitionRequestInfo request,
            @NonNull WindowContainerTransaction outWCT);

    /**
     * This is used for mixed-transition scenarios, specifically when transitioning one split task
     * into a bubble. For such scenarios, just make sure to include exiting split or entering split
     * when appropriate.
     *
     * This is only called if requestImpliesSplitToBubble() returns `true`.
     */
    public abstract void addExitForBubblesIfNeeded(@Nullable TransitionRequestInfo request,
            @NonNull WindowContainerTransaction outWCT);

    /**
     * When we launch an app, there are times when the app trampolines itself into another activity
     * and ends up breaking a split pair (because that second activity already existed as part of a
     * pair). This method is used to detect whether that happened, so we can clean up the split
     * state.
     *
     * @return whether the transition implies a split task being launched in fullscreen resulting
     * in splitscreen being broken.
     */
    public abstract boolean transitionImpliesSplitToFullscreen(TransitionInfo info);

    /** Jump the current transition animation to the end. */
    public abstract boolean end();

    /** Starts the pending transition animation. */
    public abstract boolean startPendingAnimation(@NonNull IBinder transition,
            @NonNull TransitionInfo info,
            @NonNull SurfaceControl.Transaction startTransaction,
            @NonNull SurfaceControl.Transaction finishTransaction,
            @NonNull Transitions.TransitionFinishCallback finishCallback);

    /**
     * Move the specified task to fullscreen, regardless of focus state.
     */
    public abstract void goToFullscreenFromSplit();

    /** Move the specified task to fullscreen, regardless of focus state. */
    public abstract void moveTaskToFullscreen(int taskId, int exitReason);

    /**
     * Synchronize split-screen state with transition and make appropriate preparations.
     *
     * @param toStage The stage that will not be dismissed. If set to
     *                {@link SplitScreen#STAGE_TYPE_UNDEFINED} then both stages will be dismissed
     */
    public abstract void prepareDismissAnimation(@SplitScreen.StageType int toStage,
            @SplitScreenController.ExitReason int dismissReason, @NonNull TransitionInfo info,
            @NonNull SurfaceControl.Transaction t, @NonNull SurfaceControl.Transaction finishT);

    abstract void exitSplitScreenOnHide(boolean exitSplitScreenOnHide);

    ////////////////////////////////////////////////////////////////////////////////////////////////
    ///
    /// Split events.
    ///
    ////////////////////////////////////////////////////////////////////////////////////////////////
    /**
     * Runs when keyguard state changes. The booleans here are a bit complicated, so for reference:
     *
     * @param active               {@code true} if we are in a state where the keyguard *should* be
     *                             shown
     *                             -- still true when keyguard is "there" but is behind an app, or
     *                             screen is off.
     * @param occludingTaskRunning {@code true} when there is a running task that has
     *                             FLAG_SHOW_WHEN_LOCKED -- also true when the task is
     *                             just running on its own and keyguard is not active
     *                             at all.
     */
    abstract void onKeyguardStateChanged(boolean active, boolean occludingTaskRunning);

    abstract void onStartedWakingUp();

    abstract void onStartedGoingToSleep();

    /**
     * Sets drag info to be logged when splitscreen is next entered.
     */
    abstract void onDroppedToSplit(@SplitPosition int position,
            InstanceId dragSessionId);

    abstract void onFoldedStateChanged(boolean folded);

    /** Called to clean-up state and do house-keeping after the animation is done. */
    public abstract void onTransitionAnimationComplete();

    /**
     * Performs previous child eviction and such to prepare for the pip task expending into one of
     * the split stages
     *
     * @param taskInfo TaskInfo of the pip task
     */
    public abstract void onPipExpandToSplit(WindowContainerTransaction wct,
            RunningTaskInfo taskInfo);

    /** Call this when starting the open-recents animation while split-screen is active. */
    public abstract void onRecentsInSplitAnimationStart(TransitionInfo info);

    /** Call this when the recents animation canceled during split-screen. */
    public abstract void onRecentsInSplitAnimationCanceled();

    /**
     * Returns whether the given WCT is reordering any of the split tasks to top.
     */
    public abstract boolean wctIsReorderingSplitToTop(
            @NonNull WindowContainerTransaction finishWct);

    /** Called when the recents animation during split-screen finishes. */
    public abstract void onRecentsInSplitAnimationFinishing(boolean returnToApp,
            @NonNull WindowContainerTransaction finishWct,
            @NonNull SurfaceControl.Transaction finishT);

    /** Call this when the animation from split screen to desktop is started. */
    public abstract void onSplitToDesktop();

    /** Call this when the recents animation finishes by doing pair-to-pair switch. */
    public abstract void onRecentsPairToPairAnimationFinish(WindowContainerTransaction finishWct);

    abstract void sendStatusToListener(SplitScreen.SplitScreenListener listener);

    abstract void handleUnsupportedSplitStart();

    /// ////////////////////////////////////////////////////////////////////////////////////////////
    ///
    /// Split properties.
    ///
    /// ////////////////////////////////////////////////////////////////////////////////////////////
    @SplitScreen.StageType
    abstract int getStageOfTask(int taskId);

    abstract boolean isRootOrStageRoot(int taskId);

    abstract int getTaskId(@SplitPosition int splitPosition);

    abstract void getStageBounds(Rect outTopOrLeftBounds, Rect outBottomOrRightBounds);

    abstract void getRefStageBounds(Rect outTopOrLeftBounds, Rect outBottomOrRightBounds);

    @SplitPosition
    abstract int getSplitPosition(int taskId);

    /**
     * Set divider visibility flag and try to apply it, the param transaction is used to apply.
     * See applyDividerVisibility for more detail.
     */
    abstract void setDividerVisibility(boolean visible, @Nullable SurfaceControl.Transaction t);

    /**
     * @return {@code true} if we should create a left-right split, {@code false} if we should
     * create a top-bottom split.
     */
    abstract boolean isLeftRightSplit();

    abstract boolean isLaunchToSplit(TaskInfo taskInfo);

    abstract int getActivateSplitPosition(TaskInfo taskInfo);

    /**
     * Returns whether the status bar is in immersive mode.
     *
     * @return true if the status bar is in immersive mode.
     */
    abstract boolean isStatusBarImmersive();

    /**
     * Returns whether we should sleep on fold.
     *
     * @return true if we should sleep on fold.
     */
    abstract boolean willSleepOnFold();

    /**
     * Returns whether the split screen is active.
     *
     * @return true if the split screen is active.
     */
    public abstract boolean isSplitActive();

    /**
     * Returns whether the split screen is visible.
     *
     * @return true if the split screen is visible.
     */
    public abstract boolean isSplitScreenVisible();

    /** Checks if `transition` is a pending enter-split transition. */
    public abstract boolean isPendingEnter(IBinder transition);

    /**
     * Returns the {@link SplitScreen.StageType} where {@param token} is being used
     * {@link SplitScreen#STAGE_TYPE_UNDEFINED} otherwise
     */
    @SplitScreen.StageType
    public abstract int getSplitItemStage(@Nullable WindowContainerToken token);

    /** @return whether the transition-request implies entering pip from split. */
    public abstract boolean requestImpliesSplitToPip(TransitionRequestInfo request);

    /** @return whether the opening task implies entering bubbles from split. */
    public abstract boolean requestImpliesSplitToBubble(TaskInfo openingTask);

    /**
     * Returns the {@link SplitScreenTransitions} object.
     *
     * @return the {@link SplitScreenTransitions} object.
     */
    @VisibleForTesting
    abstract SplitScreenTransitions getSplitTransitions();

    /**
     * Sets the {@link SplitScreenTransitions} object.
     *
     * @param splitScreenTransitions the {@link SplitScreenTransitions} object.
     */
    @VisibleForTesting
    abstract void setSplitTransitions(SplitScreenTransitions splitScreenTransitions);

    /**
     * Called when the animation status changes.
     *
     * @param animationRunning {@code true} if the animation is running.
     */
    abstract void notifySplitAnimationStatus(boolean animationRunning);

    /**
     * Sets the {@link DefaultMixedHandler} object.
     *
     * @param mixedHandler the {@link DefaultMixedHandler} object.
     */
    public abstract void setMixedHandler(DefaultMixedHandler mixedHandler);

    /**
     * Returns the side stage position.
     *
     * @return the side stage position.
     */
    abstract @SplitPosition int getSideStagePosition();

    /**
     * Returns the main stage position.
     *
     * @return the main stage position.
     */
    abstract @SplitPosition int getMainStagePosition();

    /**
     * Returns the {@link SplitMultiDisplayHelper} object.
     *
     * @return the {@link SplitMultiDisplayHelper} object.
     */
    abstract SplitMultiDisplayHelper getSplitMultiDisplayHelper();

    /**
     * Sets the {@link SplitMultiDisplayHelper} object.
     *
     * @param splitMultiDisplayHelper the {@link SplitMultiDisplayHelper} object.
     */
    abstract void setSplitMultiDisplayHelper(SplitMultiDisplayHelper splitMultiDisplayHelper);

    /**
     * Returns the last active stage.
     *
     * @return the last active stage.
     */
    abstract @StageType int getLastActiveStage();

    ////////////////////////////////////////////////////////////////////////////////////////////////
    ///
    /// Split debug log.
    ///
    ////////////////////////////////////////////////////////////////////////////////////////////////
    /**
     * Dumps the information of the split screen.
     *
     * @param pw     The output stream to dump to
     * @param prefix The prefix to use when dumping
     */

    public abstract void dump(@NonNull PrintWriter pw, String prefix);

    /**
     * Returns the {@link SplitscreenEventLogger} object.
     *
     * @return the {@link SplitscreenEventLogger} object.
     */
    abstract SplitscreenEventLogger getLogger();
}
