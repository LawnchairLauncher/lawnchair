/*
 * Copyright (C) 2020 The Android Open Source Project
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

import static android.app.ActivityManager.START_SUCCESS;
import static android.app.ActivityManager.START_TASK_TO_FRONT;
import static android.app.ActivityTaskManager.INVALID_TASK_ID;
import static android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN;
import static android.content.Intent.FLAG_ACTIVITY_MULTIPLE_TASK;
import static android.content.Intent.FLAG_ACTIVITY_NO_USER_ACTION;
import static android.view.Display.DEFAULT_DISPLAY;

import static com.android.wm.shell.Flags.enableFlexibleSplit;
import static com.android.wm.shell.common.MultiInstanceHelper.getComponent;
import static com.android.wm.shell.common.MultiInstanceHelper.getShortcutComponent;
import static com.android.wm.shell.common.MultiInstanceHelper.samePackage;
import static com.android.wm.shell.common.split.SplitScreenUtils.isValidToSplit;
import static com.android.wm.shell.common.split.SplitScreenUtils.reverseSplitPosition;
import static com.android.wm.shell.common.split.SplitScreenUtils.splitFailureMessage;
import static com.android.wm.shell.protolog.ShellProtoLogGroup.WM_SHELL_SPLIT_SCREEN;
import static com.android.wm.shell.shared.split.SplitScreenConstants.KEY_EXTRA_WIDGET_INTENT;
import static com.android.wm.shell.shared.split.SplitScreenConstants.SPLIT_INDEX_0;
import static com.android.wm.shell.shared.split.SplitScreenConstants.SPLIT_INDEX_1;
import static com.android.wm.shell.shared.split.SplitScreenConstants.SPLIT_INDEX_UNDEFINED;
import static com.android.wm.shell.shared.split.SplitScreenConstants.SPLIT_POSITION_BOTTOM_OR_RIGHT;
import static com.android.wm.shell.shared.split.SplitScreenConstants.SPLIT_POSITION_TOP_OR_LEFT;
import static com.android.wm.shell.shared.split.SplitScreenConstants.SPLIT_POSITION_UNDEFINED;
import static com.android.wm.shell.splitscreen.SplitScreen.STAGE_TYPE_UNDEFINED;

import android.app.ActivityManager;
import android.app.ActivityOptions;
import android.app.ActivityTaskManager;
import android.app.IActivityTaskManager;
import android.app.PendingIntent;
import android.app.TaskInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.LauncherApps;
import android.content.pm.ShortcutInfo;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.os.RemoteException;
import android.os.UserHandle;
import android.util.ArrayMap;
import android.util.Log;
import android.util.Slog;
import android.view.IRemoteAnimationFinishedCallback;
import android.view.IRemoteAnimationRunner;
import android.view.RemoteAnimationAdapter;
import android.view.RemoteAnimationTarget;
import android.view.SurfaceControl;
import android.view.WindowManager;
import android.widget.Toast;
import android.window.RemoteTransition;
import android.window.WindowContainerToken;
import android.window.WindowContainerTransaction;

import androidx.annotation.BinderThread;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.logging.InstanceId;
import com.android.internal.protolog.ProtoLog;
import com.android.launcher3.icons.IconProvider;
import com.android.wm.shell.R;
import com.android.wm.shell.RootDisplayAreaOrganizer;
import com.android.wm.shell.RootTaskDisplayAreaOrganizer;
import com.android.wm.shell.ShellTaskOrganizer;
import com.android.wm.shell.common.ComponentUtils;
import com.android.wm.shell.common.DisplayController;
import com.android.wm.shell.common.DisplayImeController;
import com.android.wm.shell.common.DisplayInsetsController;
import com.android.wm.shell.common.ExternalInterfaceBinder;
import com.android.wm.shell.common.LaunchAdjacentController;
import com.android.wm.shell.common.MultiInstanceHelper;
import com.android.wm.shell.common.RemoteCallable;
import com.android.wm.shell.common.ShellExecutor;
import com.android.wm.shell.common.SingleInstanceRemoteListener;
import com.android.wm.shell.common.SyncTransactionQueue;
import com.android.wm.shell.common.split.SplitScreenUtils;
import com.android.wm.shell.common.split.SplitState;
import com.android.wm.shell.desktopmode.DesktopTasksController;
import com.android.wm.shell.desktopmode.DesktopUserRepositories;
import com.android.wm.shell.draganddrop.DragAndDropController;
import com.android.wm.shell.draganddrop.SplitDragPolicy;
import com.android.wm.shell.protolog.ShellProtoLogGroup;
import com.android.wm.shell.recents.RecentTasksController;
import com.android.wm.shell.shared.TransactionPool;
import com.android.wm.shell.shared.annotations.ExternalThread;
import com.android.wm.shell.shared.desktopmode.DesktopState;
import com.android.wm.shell.shared.split.SplitScreenConstants.PersistentSnapPosition;
import com.android.wm.shell.shared.split.SplitScreenConstants.SplitIndex;
import com.android.wm.shell.shared.split.SplitScreenConstants.SplitPosition;
import com.android.wm.shell.splitscreen.SplitScreen.StageType;
import com.android.wm.shell.sysui.KeyguardChangeListener;
import com.android.wm.shell.sysui.ShellCommandHandler;
import com.android.wm.shell.sysui.ShellController;
import com.android.wm.shell.sysui.ShellInit;
import com.android.wm.shell.transition.Transitions;
import com.android.wm.shell.windowdecor.WindowDecorViewModel;

import com.google.android.msdl.domain.MSDLPlayer;

import java.io.PrintWriter;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Class manages split-screen multitasking mode and implements the main interface
 * {@link SplitScreen}.
 *
 * @see StageCoordinator
 */
// TODO(b/198577848): Implement split screen flicker test to consolidate CUJ of split screen.
public class SplitScreenController implements SplitDragPolicy.Starter,
        RemoteCallable<SplitScreenController>, KeyguardChangeListener {
    private static final String TAG = SplitScreenController.class.getSimpleName();

    public static final int EXIT_REASON_UNKNOWN = 0;
    public static final int EXIT_REASON_APP_DOES_NOT_SUPPORT_MULTIWINDOW = 1;
    public static final int EXIT_REASON_APP_FINISHED = 2;
    public static final int EXIT_REASON_DEVICE_FOLDED = 3;
    public static final int EXIT_REASON_DRAG_DIVIDER = 4;
    public static final int EXIT_REASON_RETURN_HOME = 5;
    public static final int EXIT_REASON_ROOT_TASK_VANISHED = 6;
    public static final int EXIT_REASON_SCREEN_LOCKED = 7;
    public static final int EXIT_REASON_SCREEN_LOCKED_SHOW_ON_TOP = 8;
    public static final int EXIT_REASON_CHILD_TASK_ENTER_PIP = 9;
    public static final int EXIT_REASON_RECREATE_SPLIT = 10;
    public static final int EXIT_REASON_FULLSCREEN_SHORTCUT = 11;
    public static final int EXIT_REASON_DESKTOP_MODE = 12;
    public static final int EXIT_REASON_FULLSCREEN_REQUEST = 13;
    public static final int EXIT_REASON_CHILD_TASK_ENTER_BUBBLE = 14;
    @IntDef(value = {
            EXIT_REASON_UNKNOWN,
            EXIT_REASON_APP_DOES_NOT_SUPPORT_MULTIWINDOW,
            EXIT_REASON_APP_FINISHED,
            EXIT_REASON_DEVICE_FOLDED,
            EXIT_REASON_DRAG_DIVIDER,
            EXIT_REASON_RETURN_HOME,
            EXIT_REASON_ROOT_TASK_VANISHED,
            EXIT_REASON_SCREEN_LOCKED,
            EXIT_REASON_SCREEN_LOCKED_SHOW_ON_TOP,
            EXIT_REASON_CHILD_TASK_ENTER_PIP,
            EXIT_REASON_RECREATE_SPLIT,
            EXIT_REASON_FULLSCREEN_SHORTCUT,
            EXIT_REASON_DESKTOP_MODE,
            EXIT_REASON_FULLSCREEN_REQUEST,
            EXIT_REASON_CHILD_TASK_ENTER_BUBBLE
    })
    @Retention(RetentionPolicy.SOURCE)
    @interface ExitReason{}

    public static final int ENTER_REASON_UNKNOWN = 0;
    public static final int ENTER_REASON_MULTI_INSTANCE = 1;
    public static final int ENTER_REASON_DRAG = 2;
    public static final int ENTER_REASON_LAUNCHER = 3;
    /** Acts as a mapping to the actual EnterReasons as defined in the logging proto */
    @IntDef(value = {
            ENTER_REASON_MULTI_INSTANCE,
            ENTER_REASON_DRAG,
            ENTER_REASON_LAUNCHER,
            ENTER_REASON_UNKNOWN
    })
    public @interface SplitEnterReason {
    }

    private final ShellCommandHandler mShellCommandHandler;
    private final ShellController mShellController;
    private final ShellTaskOrganizer mTaskOrganizer;
    private final SyncTransactionQueue mSyncQueue;
    private final Context mContext;
    private final LauncherApps mLauncherApps;
    private final RootTaskDisplayAreaOrganizer mRootTDAOrganizer;
    private final ShellExecutor mMainExecutor;
    private final Handler mMainHandler;
    private final SplitScreenImpl mImpl = new SplitScreenImpl();
    private final DisplayController mDisplayController;
    private final DisplayImeController mDisplayImeController;
    private final DisplayInsetsController mDisplayInsetsController;
    private final DragAndDropController mDragAndDropController;
    private final Transitions mTransitions;
    private final TransactionPool mTransactionPool;
    private final IconProvider mIconProvider;
    private final Optional<RecentTasksController> mRecentTasksOptional;
    private final LaunchAdjacentController mLaunchAdjacentController;
    private final Optional<WindowDecorViewModel> mWindowDecorViewModel;
    private final Optional<DesktopTasksController> mDesktopTasksController;
    private final Optional<DesktopUserRepositories> mDesktopUserRepositories;
    private final MultiInstanceHelper mMultiInstanceHelpher;
    private final SplitState mSplitState;
    private final RootDisplayAreaOrganizer mRootDisplayAreaOrganizer;
    private final IActivityTaskManager mActivityTaskManager;
    private final SplitScreenShellCommandHandler mSplitScreenShellCommandHandler;
    private final DesktopState mDesktopState;
    private final MSDLPlayer mMSDLPlayer;

    @VisibleForTesting
    StageCoordinator mStageCoordinator;

    /**
     * @param stageCoordinator if null, a stage coordinator will be created when this controller is
     *                         initialized. Can be non-null for testing purposes.
     */
    public SplitScreenController(Context context,
            ShellInit shellInit,
            ShellCommandHandler shellCommandHandler,
            ShellController shellController,
            ShellTaskOrganizer shellTaskOrganizer,
            SyncTransactionQueue syncQueue,
            RootTaskDisplayAreaOrganizer rootTDAOrganizer,
            DisplayController displayController,
            DisplayImeController displayImeController,
            DisplayInsetsController displayInsetsController,
            DragAndDropController dragAndDropController,
            Transitions transitions,
            TransactionPool transactionPool,
            IconProvider iconProvider,
            Optional<RecentTasksController> recentTasks,
            LaunchAdjacentController launchAdjacentController,
            Optional<WindowDecorViewModel> windowDecorViewModel,
            Optional<DesktopTasksController> desktopTasksController,
            Optional<DesktopUserRepositories> desktopUserRepositories,
            @Nullable StageCoordinator stageCoordinator,
            MultiInstanceHelper multiInstanceHelper,
            SplitState splitState,
            ShellExecutor mainExecutor,
            Handler mainHandler,
            RootDisplayAreaOrganizer rootDisplayAreaOrganizer,
            DesktopState desktopState,
            IActivityTaskManager activityTaskManager,
            MSDLPlayer msdlPlayer) {
        mShellCommandHandler = shellCommandHandler;
        mShellController = shellController;
        mTaskOrganizer = shellTaskOrganizer;
        mSyncQueue = syncQueue;
        mContext = context;
        mLauncherApps = context.getSystemService(LauncherApps.class);
        mRootTDAOrganizer = rootTDAOrganizer;
        mMainExecutor = mainExecutor;
        mMainHandler = mainHandler;
        mDisplayController = displayController;
        mDisplayImeController = displayImeController;
        mDisplayInsetsController = displayInsetsController;
        mDragAndDropController = dragAndDropController;
        mTransitions = transitions;
        mTransactionPool = transactionPool;
        mIconProvider = iconProvider;
        mRecentTasksOptional = recentTasks;
        mLaunchAdjacentController = launchAdjacentController;
        mWindowDecorViewModel = windowDecorViewModel;
        mDesktopTasksController = desktopTasksController;
        mDesktopUserRepositories = desktopUserRepositories;
        mStageCoordinator = stageCoordinator;
        mMultiInstanceHelpher = multiInstanceHelper;
        mSplitState = splitState;
        mRootDisplayAreaOrganizer = rootDisplayAreaOrganizer;
        mActivityTaskManager = activityTaskManager;
        mSplitScreenShellCommandHandler = new SplitScreenShellCommandHandler(this);
        mDesktopState = desktopState;
        mMSDLPlayer = msdlPlayer;
        // TODO(b/238217847): Temporarily add this check here until we can remove the dynamic
        //                    override for this controller from the base module
        if (ActivityTaskManager.supportsSplitScreenMultiWindow(context)) {
            shellInit.addInitCallback(this::onInit, this);
        }
    }

    public SplitScreen asSplitScreen() {
        return mImpl;
    }

    private ExternalInterfaceBinder createExternalInterface() {
        return new ISplitScreenImpl(this);
    }

    /**
     * This will be called after ShellTaskOrganizer has initialized/registered because of the
     * dependency order.
     */
    @VisibleForTesting
    void onInit() {
        mShellCommandHandler.addDumpCallback(this::dump, this);
        mShellCommandHandler.addCommandCallback("splitscreen", mSplitScreenShellCommandHandler,
                this);
        mShellController.addKeyguardChangeListener(this);
        mShellController.addExternalInterface(ISplitScreen.DESCRIPTOR,
                this::createExternalInterface, this);
        if (mStageCoordinator == null) {
            // TODO: Multi-display
            mStageCoordinator = createStageCoordinator();
        }
        if (mDragAndDropController != null) {
            mDragAndDropController.setSplitScreenController(this);
        }
        mWindowDecorViewModel.ifPresent(viewModel -> viewModel.setSplitScreenController(this));
        mDesktopTasksController.ifPresent(controller -> controller.setSplitScreenController(this));
    }

    protected StageCoordinator createStageCoordinator() {
        return new StageCoordinator(mContext, DEFAULT_DISPLAY, mSyncQueue,
                mTaskOrganizer, mDisplayController, mDisplayImeController,
                mDisplayInsetsController, mTransitions, mTransactionPool, mIconProvider,
                mMainExecutor, mMainHandler, mRecentTasksOptional, mLaunchAdjacentController,
                mWindowDecorViewModel, mSplitState, mDesktopTasksController,
                mDesktopUserRepositories, mRootTDAOrganizer,
                mRootDisplayAreaOrganizer, mDesktopState, mActivityTaskManager, mMSDLPlayer);
    }

    @Override
    public Context getContext() {
        return mContext;
    }

    protected DesktopState getDesktopState() {
        return mDesktopState;
    }

    @Override
    public ShellExecutor getRemoteCallExecutor() {
        return mMainExecutor;
    }

    public boolean isSplitScreenVisible() {
        return mStageCoordinator.isSplitScreenVisible();
    }

    public StageCoordinator getTransitionHandler() {
        return mStageCoordinator;
    }

    public SplitMultiDisplayProvider getMultiDisplayProvider() {
        return mStageCoordinator;
    }

    @Nullable
    public ActivityManager.RunningTaskInfo getTaskInfo(@SplitPosition int splitPosition) {
        if (!isSplitScreenVisible() || splitPosition == SPLIT_POSITION_UNDEFINED) {
            return null;
        }

        final int taskId = mStageCoordinator.getTaskId(splitPosition);
        return mTaskOrganizer.getRunningTaskInfo(taskId);
    }

    /**
     * @return an Array of RunningTaskInfo's ordered by leftToRight or topTopBottom
     */
    public ActivityManager.RunningTaskInfo[] getAllTaskInfos() {
        // TODO(b/349828130) Add the third stage task info and not rely on positions
        ActivityManager.RunningTaskInfo topLeftTask = getTaskInfo(SPLIT_POSITION_TOP_OR_LEFT);
        ActivityManager.RunningTaskInfo bottomRightTask =
                getTaskInfo(SPLIT_POSITION_BOTTOM_OR_RIGHT);
        if (topLeftTask != null && bottomRightTask != null) {
            return new ActivityManager.RunningTaskInfo[]{topLeftTask, bottomRightTask};
        }

        return new ActivityManager.RunningTaskInfo[0];
    }

    /** Check task is under split or not by taskId. */
    public boolean isTaskInSplitScreen(int taskId) {
        return mStageCoordinator.getStageOfTask(taskId) != STAGE_TYPE_UNDEFINED;
    }

    /** Get the split stage of task is under it. */
    public @StageType int getStageOfTask(int taskId) {
        return mStageCoordinator.getStageOfTask(taskId);
    }

    /**
     * @return {@code true} if we should create a left-right split, {@code false} if we should
     * create a top-bottom split.
     */
    public boolean isLeftRightSplit() {
        return mStageCoordinator.isLeftRightSplit();
    }

    /** Check split is foreground and task is under split or not by taskId. */
    public boolean isTaskInSplitScreenForeground(int taskId) {
        return isTaskInSplitScreen(taskId) && isSplitScreenVisible();
    }

    /** Check whether the task is the single-top root or the root of one of the stages. */
    public boolean isTaskRootOrStageRoot(int taskId) {
        return mStageCoordinator.isRootOrStageRoot(taskId);
    }

    public @SplitPosition int getSplitPosition(int taskId) {
        return mStageCoordinator.getSplitPosition(taskId);
    }

    public boolean moveToSideStage(int taskId, @SplitPosition int sideStagePosition) {
        return moveToStage(taskId, sideStagePosition, new WindowContainerTransaction());
    }

    /**
     * Update surfaces of the split screen layout based on the current state
     * @param transaction to write the updates to
     */
    public void updateSplitScreenSurfaces(SurfaceControl.Transaction transaction) {
        mStageCoordinator.updateSurfaces(transaction);
    }

    private boolean moveToStage(int taskId, @SplitPosition int stagePosition,
            WindowContainerTransaction wct) {
        final ActivityManager.RunningTaskInfo task = mTaskOrganizer.getRunningTaskInfo(taskId);
        if (task == null) {
            throw new IllegalArgumentException("Unknown taskId" + taskId);
        }
        if (isTaskInSplitScreen(taskId)) {
            throw new IllegalArgumentException("taskId is in split" + taskId);
        }
        return mStageCoordinator.moveToStage(task, stagePosition, wct);
    }

    public void setSideStagePosition(@SplitPosition int sideStagePosition) {
        mStageCoordinator.setSideStagePosition(sideStagePosition, null /* wct */);
    }

    /**
     * Doing necessary window transaction for other transition handler need to enter split in
     * transition.
     */
    public void prepareEnterSplitScreen(WindowContainerTransaction wct,
            ActivityManager.RunningTaskInfo taskInfo, int startPosition) {
        mStageCoordinator.prepareEnterSplitScreen(wct, taskInfo, startPosition,
                false /* resizeAnim */, SPLIT_INDEX_UNDEFINED);
    }

    /**
     * Doing necessary surface transaction for other transition handler need to enter split in
     * transition when finished.
     */
    public void finishEnterSplitScreen(SurfaceControl.Transaction finishT) {
        mStageCoordinator.finishEnterSplitScreen(finishT);
    }

    /**
     * Performs previous child eviction and such to prepare for the pip task expending into one of
     * the split stages
     *
     * @param taskInfo TaskInfo of the pip task
     */
    public void onPipExpandToSplit(WindowContainerTransaction wct,
            ActivityManager.RunningTaskInfo taskInfo) {
        mStageCoordinator.onPipExpandToSplit(wct, taskInfo);
    }

    /**
     * Doing necessary window transaction for other transition handler need to exit split in
     * transition.
     */
    public void prepareExitSplitScreen(WindowContainerTransaction wct,
            @StageType int stageToTop, @ExitReason int reason) {
        mStageCoordinator.prepareExitSplitScreen(stageToTop, wct, reason);
        mStageCoordinator.setDividerVisibility(false, null);
        mStageCoordinator.clearSplitPairedInRecents(reason);
    }

    /**
     * Determines which split position a new instance of a task should take.
     * @param callingTask The task requesting a new instance.
     * @return the split position of the new instance
     */
    public int determineNewInstancePosition(@NonNull ActivityManager.RunningTaskInfo callingTask) {
        if (callingTask.getWindowingMode() == WINDOWING_MODE_FULLSCREEN
                || getSplitPosition(callingTask.taskId) == SPLIT_POSITION_TOP_OR_LEFT) {
            return SPLIT_POSITION_BOTTOM_OR_RIGHT;
        } else {
            return SPLIT_POSITION_TOP_OR_LEFT;
        }
    }

    /**
     * Determines which split index a new instance of a task should take.
     * @param callingTask The task requesting a new instance.
     * @return the split index of the new instance
     */
    @SplitIndex
    public int determineNewInstanceIndex(@NonNull ActivityManager.RunningTaskInfo callingTask) {
        if (!enableFlexibleSplit()) {
            throw new IllegalStateException("Use determineNewInstancePosition");
        }
        if (callingTask.getWindowingMode() == WINDOWING_MODE_FULLSCREEN
                || getSplitPosition(callingTask.taskId) == SPLIT_POSITION_TOP_OR_LEFT) {
            return SPLIT_INDEX_1;
        } else {
            return SPLIT_INDEX_0;
        }
    }

    public void enterSplitScreen(int taskId, boolean leftOrTop) {
        enterSplitScreen(taskId, leftOrTop, new WindowContainerTransaction());
    }

    public void enterSplitScreen(int taskId, boolean leftOrTop, WindowContainerTransaction wct) {
        final int stagePosition =
                leftOrTop ? SPLIT_POSITION_TOP_OR_LEFT : SPLIT_POSITION_BOTTOM_OR_RIGHT;
        moveToStage(taskId, stagePosition, wct);
    }

    public void exitSplitScreen(int toTopTaskId, @ExitReason int exitReason) {
        mStageCoordinator.dismissSplitScreen(toTopTaskId, exitReason);
    }

    @Override
    public void onKeyguardVisibilityChanged(boolean visible, boolean occluded,
            boolean animatingDismiss) {
        mStageCoordinator.onKeyguardStateChanged(visible, occluded);
    }

    public void onStartedGoingToSleep() {
        mStageCoordinator.onStartedGoingToSleep();
    }

    public void onStartedWakingUp() {
        mStageCoordinator.onStartedWakingUp();
    }

    public void exitSplitScreenOnHide(boolean exitSplitScreenOnHide) {
        mStageCoordinator.exitSplitScreenOnHide(exitSplitScreenOnHide);
    }

    public void getStageBounds(Rect outTopOrLeftBounds, Rect outBottomOrRightBounds) {
        mStageCoordinator.getStageBounds(outTopOrLeftBounds, outBottomOrRightBounds);
    }

    /** Get the parent-based coordinates for split stages. */
    public void getRefStageBounds(Rect outTopOrLeftBounds, Rect outBottomOrRightBounds) {
        mStageCoordinator.getRefStageBounds(outTopOrLeftBounds, outBottomOrRightBounds);
    }

    public void registerSplitScreenListener(SplitScreen.SplitScreenListener listener) {
        mStageCoordinator.registerSplitScreenListener(listener);
    }

    public void unregisterSplitScreenListener(SplitScreen.SplitScreenListener listener) {
        mStageCoordinator.unregisterSplitScreenListener(listener);
    }

    /** Register a split select listener */
    public void registerSplitSelectListener(SplitScreen.SplitSelectListener listener) {
        mStageCoordinator.registerSplitSelectListener(listener);
    }

    /** Unregister a split select listener */
    public void unregisterSplitSelectListener(SplitScreen.SplitSelectListener listener) {
        mStageCoordinator.unregisterSplitSelectListener(listener);
    }

    public void goToFullscreenFromSplit() {
        if (mStageCoordinator.isSplitActive()) {
            mStageCoordinator.goToFullscreenFromSplit();
        }
    }

    public void setSplitscreenFocus(boolean leftOrTop) {
        if (mStageCoordinator.isSplitActive()) {
            mStageCoordinator.grantFocusToPosition(leftOrTop);
        }
    }

    /** Move the specified task to fullscreen, regardless of focus state. */
    public void moveTaskToFullscreen(int taskId, int exitReason) {
        mStageCoordinator.moveTaskToFullscreen(taskId, exitReason);
    }

    public boolean isLaunchToSplit(TaskInfo taskInfo) {
        return mStageCoordinator.isLaunchToSplit(taskInfo);
    }

    public int getActivateSplitPosition(TaskInfo taskInfo) {
        return mStageCoordinator.getActivateSplitPosition(taskInfo);
    }

    /** Start two tasks in parallel as a splitscreen pair. */
    public void startTasks(int taskId1, @Nullable Bundle options1, int taskId2,
            @Nullable Bundle options2, @SplitPosition int splitPosition,
            @PersistentSnapPosition int snapPosition,
            @Nullable RemoteTransition remoteTransition, InstanceId instanceId) {
        mStageCoordinator.startTasks(taskId1, options1, taskId2, options2, splitPosition,
                snapPosition, remoteTransition, instanceId);
    }

    /**
     * Move a task to split select
     * @param taskInfo the task being moved to split select
     * @param splitPosition the split position this task should move to
     * @param taskBounds current freeform bounds of the task entering split
     * @param startRecents whether this request should start a recents transition
     * @param withRecentsWct a wct so include in the recents transition
     */
    public void requestEnterSplitSelect(ActivityManager.RunningTaskInfo taskInfo,
            int splitPosition, Rect taskBounds, boolean startRecents,
            @Nullable WindowContainerTransaction withRecentsWct) {
        if (!startRecents && withRecentsWct != null) {
            throw new IllegalArgumentException("Must be starting recents to include a wct");
        }
        mStageCoordinator.requestEnterSplitSelect(taskInfo, splitPosition, taskBounds,
                startRecents, withRecentsWct);
    }

    /**
     * Starts an existing task into split.
     * TODO(b/351900580): We should remove this path and use StageCoordinator#startTask() instead
     * @param hideTaskToken is not supported.
     */
    public void startTask(int taskId, @SplitPosition int position, @Nullable Bundle options,
            @Nullable WindowContainerToken hideTaskToken) {
        ProtoLog.v(ShellProtoLogGroup.WM_SHELL_DRAG_AND_DROP,
                "Legacy startTask does not support hide task token");
        if (isTaskInSplitScreenForeground(taskId)) return;
        final int[] result = new int[1];
        IRemoteAnimationRunner wrapper = new IRemoteAnimationRunner.Stub() {
            @Override
            public void onAnimationStart(@WindowManager.TransitionOldType int transit,
                    RemoteAnimationTarget[] apps,
                    RemoteAnimationTarget[] wallpapers,
                    RemoteAnimationTarget[] nonApps,
                    final IRemoteAnimationFinishedCallback finishedCallback) {
                try {
                    finishedCallback.onAnimationFinished();
                } catch (RemoteException e) {
                    Slog.e(TAG, "Failed to invoke onAnimationFinished", e);
                }
                if (result[0] == START_SUCCESS || result[0] == START_TASK_TO_FRONT) {
                    final WindowContainerTransaction evictWct = new WindowContainerTransaction();
                    mStageCoordinator.prepareEvictNonOpeningChildTasks(position, apps, evictWct);
                    mSyncQueue.queue(evictWct);
                }
            }
            @Override
            public void onAnimationCancelled() {
                final WindowContainerTransaction evictWct = new WindowContainerTransaction();
                mStageCoordinator.prepareEvictInvisibleChildTasks(evictWct);
                mSyncQueue.queue(evictWct);
            }
        };
        options = mStageCoordinator.resolveStartStage(STAGE_TYPE_UNDEFINED, position, options,
                null /* wct */);
        RemoteAnimationAdapter wrappedAdapter = new RemoteAnimationAdapter(wrapper,
                0 /* duration */, 0 /* statusBarTransitionDelay */);
        ActivityOptions activityOptions = ActivityOptions.fromBundle(options);
        activityOptions.update(ActivityOptions.makeRemoteAnimation(wrappedAdapter));

        try {
            result[0] = ActivityTaskManager.getService().startActivityFromRecents(taskId,
                    activityOptions.toBundle());
        } catch (RemoteException e) {
            Slog.e(TAG, "Failed to launch task", e);
        }
    }

    /**
     * Starts an existing task via StageCoordinator.
     */
    public void startTask(int taskId, @SplitPosition int position, @Nullable Bundle options,
            @Nullable WindowContainerToken hideTaskToken, @SplitIndex int index) {
        mStageCoordinator.startTask(taskId, position, options, hideTaskToken, index);
    }

    /**
     * See {@link #startShortcut(String, String, int, Bundle, UserHandle)}
     * @param instanceId to be used by {@link SplitscreenEventLogger}
     */
    public void startShortcut(String packageName, String shortcutId, @SplitPosition int position,
            @Nullable Bundle options, UserHandle user, @NonNull InstanceId instanceId) {
        ProtoLog.d(WM_SHELL_SPLIT_SCREEN, "startShortcut: reason=%d", ENTER_REASON_LAUNCHER);
        mStageCoordinator.getLogger().enterRequested(instanceId, ENTER_REASON_LAUNCHER);
        startShortcut(packageName, shortcutId, position, options, user);
    }

    @Override
    public void startShortcut(String packageName, String shortcutId, @SplitPosition int position,
            @Nullable Bundle options, UserHandle user) {
        if (options == null) options = new Bundle();
        final ActivityOptions activityOptions = ActivityOptions.fromBundle(options);
        final int userId = user.getIdentifier();

        if (samePackage(packageName, getPackageName(reverseSplitPosition(position), null),
                userId, getUserId(reverseSplitPosition(position), null))) {
            if (mMultiInstanceHelpher.supportsMultiInstanceSplit(
                    getShortcutComponent(packageName, shortcutId, user, mLauncherApps), userId)) {
                activityOptions.setApplyMultipleTaskFlagForShortcut(true);
                ProtoLog.v(ShellProtoLogGroup.WM_SHELL_SPLIT_SCREEN, "Adding MULTIPLE_TASK");
            } else if (isSplitScreenVisible()) {
                mStageCoordinator.switchSplitPosition("startShortcut");
                return;
            } else {
                ProtoLog.v(ShellProtoLogGroup.WM_SHELL_SPLIT_SCREEN,
                        "Cancel entering split as not supporting multi-instances");
                Log.w(TAG, splitFailureMessage("startShortcut",
                        "app package " + packageName + " does not support multi-instance"));
                Toast.makeText(mContext, R.string.dock_multi_instances_not_supported_text,
                        Toast.LENGTH_SHORT).show();
                return;
            }
        }

        mStageCoordinator.startShortcut(packageName, shortcutId, position,
                activityOptions.toBundle(), user);
    }

    void startShortcutAndTask(@NonNull ShortcutInfo shortcutInfo, @Nullable Bundle options1,
            int taskId, @Nullable Bundle options2, @SplitPosition int splitPosition,
            @PersistentSnapPosition int snapPosition, @Nullable RemoteTransition remoteTransition,
            InstanceId instanceId) {
        if (options1 == null) options1 = new Bundle();
        final ActivityOptions activityOptions = ActivityOptions.fromBundle(options1);
        final String packageName1 = shortcutInfo.getPackage();
        // NOTE: This doesn't correctly pull out packageName2 if taskId is referring to a task in
        //       recents that hasn't launched and is not being organized
        final String packageName2 = ComponentUtils.getPackageName(taskId, mTaskOrganizer);
        final int userId1 = shortcutInfo.getUserId();
        final int userId2 = SplitScreenUtils.getUserId(taskId, mTaskOrganizer);
        if (samePackage(packageName1, packageName2, userId1, userId2)) {
            if (mMultiInstanceHelpher.supportsMultiInstanceSplit(shortcutInfo.getActivity(),
                    userId1)) {
                activityOptions.setApplyMultipleTaskFlagForShortcut(true);
                ProtoLog.v(ShellProtoLogGroup.WM_SHELL_SPLIT_SCREEN, "Adding MULTIPLE_TASK");
            } else {
                if (mRecentTasksOptional.isPresent()) {
                    mRecentTasksOptional.get().removeSplitPair(taskId);
                }
                taskId = INVALID_TASK_ID;
                ProtoLog.v(ShellProtoLogGroup.WM_SHELL_SPLIT_SCREEN,
                        "Cancel entering split as not supporting multi-instances");
                Log.w(TAG, splitFailureMessage("startShortcutAndTask",
                        "app package " + packageName1 + " does not support multi-instance"));
                Toast.makeText(mContext, R.string.dock_multi_instances_not_supported_text,
                        Toast.LENGTH_SHORT).show();
            }
        }
        mStageCoordinator.startShortcutAndTask(shortcutInfo, activityOptions.toBundle(), taskId,
                options2, splitPosition, snapPosition, remoteTransition, instanceId);
    }

    /**
     * See {@link #startIntent(PendingIntent, int, Intent, int, Bundle)}
     * @param instanceId to be used by {@link SplitscreenEventLogger}
     */
    public void startIntentWithInstanceId(PendingIntent intent, int userId,
            @Nullable Intent fillInIntent, @SplitPosition int position, @Nullable Bundle options,
            @NonNull InstanceId instanceId) {
        ProtoLog.d(WM_SHELL_SPLIT_SCREEN, "startIntentWithInstanceId: reason=%d",
                ENTER_REASON_LAUNCHER);
        mStageCoordinator.getLogger().enterRequested(instanceId, ENTER_REASON_LAUNCHER);
        // TODO(b/349828130) currently pass in index_undefined until we can revisit these
        //  specific cases in the future. Only focusing on parity with starting intent/task
        startIntent(intent, userId, fillInIntent, position, options, null /* hideTaskToken */,
                SPLIT_INDEX_UNDEFINED);
    }

    private void startIntentAndTask(PendingIntent pendingIntent, int userId1,
            @Nullable Bundle options1, int taskId, @Nullable Bundle options2,
            @SplitPosition int splitPosition, @PersistentSnapPosition int snapPosition,
            @Nullable RemoteTransition remoteTransition, InstanceId instanceId) {
        Intent fillInIntent = null;
        final String packageName1 = ComponentUtils.getPackageName(pendingIntent);
        // NOTE: This doesn't correctly pull out packageName2 if taskId is referring to a task in
        //       recents that hasn't launched and is not being organized
        final String packageName2 = ComponentUtils.getPackageName(taskId, mTaskOrganizer);
        final int userId2 = SplitScreenUtils.getUserId(taskId, mTaskOrganizer);
        boolean setSecondIntentMultipleTask = false;
        if (samePackage(packageName1, packageName2, userId1, userId2)) {
            if (mMultiInstanceHelpher.supportsMultiInstanceSplit(getComponent(pendingIntent),
                    userId1)) {
                setSecondIntentMultipleTask = true;
                ProtoLog.v(ShellProtoLogGroup.WM_SHELL_SPLIT_SCREEN, "Adding MULTIPLE_TASK");
            } else {
                if (mRecentTasksOptional.isPresent()) {
                    mRecentTasksOptional.get().removeSplitPair(taskId);
                }
                taskId = INVALID_TASK_ID;
                ProtoLog.v(ShellProtoLogGroup.WM_SHELL_SPLIT_SCREEN,
                        "Cancel entering split as not supporting multi-instances");
                Log.w(TAG, splitFailureMessage("startIntentAndTask",
                        "app package " + packageName1 + " does not support multi-instance"));
                Toast.makeText(mContext, R.string.dock_multi_instances_not_supported_text,
                        Toast.LENGTH_SHORT).show();
            }
        }
        if (options2 != null) {
            Intent widgetIntent = options2.getParcelable(KEY_EXTRA_WIDGET_INTENT, Intent.class);
            fillInIntent = resolveWidgetFillinIntent(widgetIntent, setSecondIntentMultipleTask);
        }
        mStageCoordinator.startIntentAndTask(pendingIntent, fillInIntent, options1, taskId,
                options2, splitPosition, snapPosition, remoteTransition, instanceId);
    }

    private void startIntents(PendingIntent pendingIntent1, int userId1,
            @Nullable ShortcutInfo shortcutInfo1, @Nullable Bundle options1,
            PendingIntent pendingIntent2, int userId2, @Nullable ShortcutInfo shortcutInfo2,
            @Nullable Bundle options2, @SplitPosition int splitPosition,
            @PersistentSnapPosition int snapPosition, @Nullable RemoteTransition remoteTransition,
            InstanceId instanceId) {
        Intent fillInIntent1 = null;
        Intent fillInIntent2 = null;
        final String packageName1 = ComponentUtils.getPackageName(pendingIntent1);
        final String packageName2 = ComponentUtils.getPackageName(pendingIntent2);
        final ActivityOptions activityOptions1 = options1 != null
                ? ActivityOptions.fromBundle(options1) : ActivityOptions.makeBasic();
        final ActivityOptions activityOptions2 = options2 != null
                ? ActivityOptions.fromBundle(options2) : ActivityOptions.makeBasic();
        boolean setSecondIntentMultipleTask = false;
        if (samePackage(packageName1, packageName2, userId1, userId2)) {
            if (mMultiInstanceHelpher.supportsMultiInstanceSplit(getComponent(pendingIntent1),
                    userId1)) {
                fillInIntent1 = new Intent();
                fillInIntent1.addFlags(FLAG_ACTIVITY_MULTIPLE_TASK);
                setSecondIntentMultipleTask = true;

                if (shortcutInfo1 != null) {
                    activityOptions1.setApplyMultipleTaskFlagForShortcut(true);
                }
                if (shortcutInfo2 != null) {
                    activityOptions2.setApplyMultipleTaskFlagForShortcut(true);
                }
                ProtoLog.v(ShellProtoLogGroup.WM_SHELL_SPLIT_SCREEN, "Adding MULTIPLE_TASK");
            } else {
                pendingIntent2 = null;
                ProtoLog.v(ShellProtoLogGroup.WM_SHELL_SPLIT_SCREEN,
                        "Cancel entering split as not supporting multi-instances");
                Log.w(TAG, splitFailureMessage("startIntents",
                        "app package " + packageName1 + " does not support multi-instance"));
                Toast.makeText(mContext, R.string.dock_multi_instances_not_supported_text,
                        Toast.LENGTH_SHORT).show();
            }
        }
        if (options2 != null) {
            Intent widgetIntent = options2.getParcelable(KEY_EXTRA_WIDGET_INTENT, Intent.class);
            fillInIntent2 = resolveWidgetFillinIntent(widgetIntent, setSecondIntentMultipleTask);
        }
        mStageCoordinator.startIntents(pendingIntent1, fillInIntent1, shortcutInfo1,
                activityOptions1.toBundle(), pendingIntent2, fillInIntent2, shortcutInfo2,
                activityOptions2.toBundle(), splitPosition, snapPosition, remoteTransition,
                instanceId);
    }

    @Override
    public void startIntent(PendingIntent intent, int userId1, @Nullable Intent fillInIntent,
            @SplitPosition int position, @Nullable Bundle options,
            @Nullable WindowContainerToken hideTaskToken, @SplitIndex int index) {
        startIntent(intent, userId1, fillInIntent, position, options, hideTaskToken,
                false /* forceLaunchNewTask */, index, DEFAULT_DISPLAY);
    }

    /**
     * Starts the given intent into split.
     *
     * @param hideTaskToken If non-null, a task matching this token will be moved to back in the
     *                      same window container transaction as the starting of the intent.
     * @param forceLaunchNewTask If true, this method will skip the check for a background task
     *                           matching the intent and launch a new task.
     */
    public void startIntent(PendingIntent intent, int userId1, @Nullable Intent fillInIntent,
            @SplitPosition int position, @Nullable Bundle options,
            @Nullable WindowContainerToken hideTaskToken, boolean forceLaunchNewTask,
            @SplitIndex int index, int displayId) {
        ProtoLog.v(ShellProtoLogGroup.WM_SHELL_SPLIT_SCREEN,
                "startIntent(): intent=%s user=%d fillInIntent=%s position=%d", intent, userId1,
                fillInIntent, position);
        // Flag this as a no-user-action launch to prevent sending user leaving event to the current
        // top activity since it's going to be put into another side of the split. This prevents the
        // current top activity from going into pip mode due to user leaving event.
        if (fillInIntent == null) fillInIntent = new Intent();
        fillInIntent.addFlags(FLAG_ACTIVITY_NO_USER_ACTION);

        final String packageName1 = ComponentUtils.getPackageName(intent);
        final String packageName2 = getPackageName(reverseSplitPosition(position), hideTaskToken);
        final int userId2 = getUserId(reverseSplitPosition(position), hideTaskToken);
        final ComponentName component = intent.getIntent().getComponent();

        // To prevent accumulating large number of instances in the background, reuse task
        // in the background. If we don't explicitly reuse, new may be created even if the app
        // isn't multi-instance because WM won't automatically remove/reuse the previous instance
        final ActivityManager.RecentTaskInfo taskInfo = forceLaunchNewTask ? null :
                mRecentTasksOptional
                        .map(recentTasks -> recentTasks.findTaskInBackground(component, userId1,
                        hideTaskToken))
                .orElse(null);
        if (taskInfo != null) {
            ProtoLog.v(ShellProtoLogGroup.WM_SHELL_SPLIT_SCREEN,
                    "Found suitable background task=%s", taskInfo);
            mStageCoordinator.startTask(taskInfo.taskId, position, options, hideTaskToken, index);

            ProtoLog.v(ShellProtoLogGroup.WM_SHELL_SPLIT_SCREEN, "Start task in background");
            return;
        }
        if (samePackage(packageName1, packageName2, userId1, userId2)) {
            if (mMultiInstanceHelpher.supportsMultiInstanceSplit(getComponent(intent), userId1)) {
                // Flag with MULTIPLE_TASK if this is launching the same activity into both sides of
                // the split and there is no reusable background task.
                fillInIntent.addFlags(FLAG_ACTIVITY_MULTIPLE_TASK);
                ProtoLog.v(ShellProtoLogGroup.WM_SHELL_SPLIT_SCREEN, "Adding MULTIPLE_TASK");
            } else if (isSplitScreenVisible()) {
                mStageCoordinator.switchSplitPosition("startIntent");
                return;
            } else {
                ProtoLog.v(ShellProtoLogGroup.WM_SHELL_SPLIT_SCREEN,
                        "Cancel entering split as not supporting multi-instances");
                Log.w(TAG, splitFailureMessage("startIntent",
                        "app package " + packageName1 + " does not support multi-instance"));
                Toast.makeText(mContext, R.string.dock_multi_instances_not_supported_text,
                        Toast.LENGTH_SHORT).show();
                return;
            }
        }

        mStageCoordinator.startIntent(intent, fillInIntent, position, options, hideTaskToken,
                index, displayId);
    }

    /**
     * Retrieve package name of a specific split position if split screen is activated, otherwise
     * returns the package name of the top running task.
     * TODO(b/351900580): Merge this with getUserId() so we don't make multiple binder calls
     */
    @Nullable
    private String getPackageName(@SplitPosition int position,
            @Nullable WindowContainerToken ignoreTaskToken) {
        ActivityManager.RunningTaskInfo taskInfo;
        if (isSplitScreenVisible()) {
            taskInfo = getTaskInfo(position);
        } else {
            taskInfo = mRecentTasksOptional
                    .map(recentTasks -> recentTasks.getTopRunningTask(ignoreTaskToken))
                    .orElse(null);
            if (!isValidToSplit(taskInfo)) {
                return null;
            }
        }

        return taskInfo != null ? ComponentUtils.getPackageName(taskInfo.baseIntent) : null;
    }

    /**
     * Retrieve user id of a specific split position if split screen is activated, otherwise
     * returns the user id of the top running task.
     * TODO: Merge this with getPackageName() so we don't make multiple binder calls
     */
    private int getUserId(@SplitPosition int position,
            @Nullable WindowContainerToken ignoreTaskToken) {
        ActivityManager.RunningTaskInfo taskInfo;
        if (isSplitScreenVisible()) {
            taskInfo = getTaskInfo(position);
        } else {
            taskInfo = mRecentTasksOptional
                    .map(recentTasks -> recentTasks.getTopRunningTask(ignoreTaskToken))
                    .orElse(null);
            if (!isValidToSplit(taskInfo)) {
                return -1;
            }
        }

        return taskInfo != null ? taskInfo.userId : -1;
    }

    /**
     * Determines whether the widgetIntent needs to be modified if multiple tasks of its
     * corresponding package/app are supported. There are 4 possible paths:
     *  <li> We select a widget for second app which is the same as the first app </li>
     *  <li> We select a widget for second app which is different from the first app </li>
     *  <li> No widgets involved, we select a second app that is the same as first app </li>
     *  <li> No widgets involved, we select a second app that is different from the first app
     *       (returns null) </li>
     *
     * @return an {@link Intent} with the appropriate {@link Intent#FLAG_ACTIVITY_MULTIPLE_TASK}
     *         added on or not depending on {@param launchMultipleTasks}.
     */
    @Nullable
    private Intent resolveWidgetFillinIntent(@Nullable Intent widgetIntent,
            boolean launchMultipleTasks) {
        Intent fillInIntent2 = null;
        if (launchMultipleTasks && widgetIntent != null) {
            fillInIntent2 = widgetIntent;
            fillInIntent2.addFlags(FLAG_ACTIVITY_MULTIPLE_TASK);
        } else if (widgetIntent != null) {
            fillInIntent2 = widgetIntent;
        } else if (launchMultipleTasks) {
            fillInIntent2 = new Intent();
            fillInIntent2.addFlags(FLAG_ACTIVITY_MULTIPLE_TASK);
        }
        return fillInIntent2;
    }

    private SurfaceControl reparentSplitTasksForAnimation(RemoteAnimationTarget[] apps,
            SurfaceControl.Transaction t, String callsite) {
        final SurfaceControl.Builder builder = new SurfaceControl.Builder()
                .setContainerLayer()
                .setName("RecentsAnimationSplitTasks")
                .setHidden(false)
                .setCallsite(callsite);
        mRootTDAOrganizer.attachToDisplayArea(DEFAULT_DISPLAY, builder);
        final SurfaceControl splitTasksLayer = builder.build();

        for (int i = 0; i < apps.length; ++i) {
            final RemoteAnimationTarget appTarget = apps[i];
            t.reparent(appTarget.leash, splitTasksLayer);
            t.setPosition(appTarget.leash, appTarget.screenSpaceBounds.left,
                    appTarget.screenSpaceBounds.top);
        }
        return splitTasksLayer;
    }
    /**
     * Drop callback when splitscreen is entered.
     */
    public void onDroppedToSplit(@SplitPosition int position, InstanceId dragSessionId) {
        mStageCoordinator.onDroppedToSplit(position, dragSessionId);
    }

    void switchSplitPosition(String reason) {
        if (isSplitScreenVisible()) {
            mStageCoordinator.switchSplitPosition(reason);
        }
    }

    /**
     * Return the {@param exitReason} as a string.
     */
    public static String exitReasonToString(int exitReason) {
        switch (exitReason) {
            case EXIT_REASON_UNKNOWN:
                return "UNKNOWN_EXIT";
            case EXIT_REASON_DRAG_DIVIDER:
                return "DRAG_DIVIDER";
            case EXIT_REASON_RETURN_HOME:
                return "RETURN_HOME";
            case EXIT_REASON_SCREEN_LOCKED:
                return "SCREEN_LOCKED";
            case EXIT_REASON_SCREEN_LOCKED_SHOW_ON_TOP:
                return "SCREEN_LOCKED_SHOW_ON_TOP";
            case EXIT_REASON_DEVICE_FOLDED:
                return "DEVICE_FOLDED";
            case EXIT_REASON_ROOT_TASK_VANISHED:
                return "ROOT_TASK_VANISHED";
            case EXIT_REASON_APP_FINISHED:
                return "APP_FINISHED";
            case EXIT_REASON_APP_DOES_NOT_SUPPORT_MULTIWINDOW:
                return "APP_DOES_NOT_SUPPORT_MULTIWINDOW";
            case EXIT_REASON_CHILD_TASK_ENTER_PIP:
                return "CHILD_TASK_ENTER_PIP";
            case EXIT_REASON_RECREATE_SPLIT:
                return "RECREATE_SPLIT";
            case EXIT_REASON_DESKTOP_MODE:
                return "DESKTOP_MODE";
            case EXIT_REASON_FULLSCREEN_REQUEST:
                return "FULLSCREEN_REQUEST";
            default:
                return "unknown reason, reason int = " + exitReason;
        }
    }

    public void dump(@NonNull PrintWriter pw, String prefix) {
        pw.println(prefix + TAG);
        if (mStageCoordinator != null) {
            mStageCoordinator.dump(pw, prefix);
        }
    }

    /**
     * The interface for calls from outside the Shell, within the host process.
     */
    @ExternalThread
    private class SplitScreenImpl implements SplitScreen {
        private final ArrayMap<SplitScreenListener, Executor> mExecutors = new ArrayMap<>();
        private final SplitScreen.SplitScreenListener mListener = new SplitScreenListener() {
            @Override
            public void onStagePositionChanged(int stage, int position) {
                for (int i = 0; i < mExecutors.size(); i++) {
                    final int index = i;
                    mExecutors.valueAt(index).execute(() -> {
                        mExecutors.keyAt(index).onStagePositionChanged(stage, position);
                    });
                }
            }

            @Override
            public void onTaskStageChanged(int taskId, int stage, boolean visible) {
                for (int i = 0; i < mExecutors.size(); i++) {
                    final int index = i;
                    mExecutors.valueAt(index).execute(() -> {
                        mExecutors.keyAt(index).onTaskStageChanged(taskId, stage, visible);
                    });
                }
            }

            @Override
            public void onSplitBoundsChanged(Rect rootBounds, Rect mainBounds, Rect sideBounds) {
                for (int i = 0; i < mExecutors.size(); i++) {
                    final int index = i;
                    mExecutors.valueAt(index).execute(() -> {
                        mExecutors.keyAt(index).onSplitBoundsChanged(rootBounds, mainBounds,
                                sideBounds);
                    });
                }
            }

            @Override
            public void onSplitVisibilityChanged(boolean visible) {
                for (int i = 0; i < mExecutors.size(); i++) {
                    final int index = i;
                    mExecutors.valueAt(index).execute(() -> {
                        mExecutors.keyAt(index).onSplitVisibilityChanged(visible);
                    });
                }
            }
        };

        @Override
        public void startTasks(int taskId1, @Nullable Bundle options1, int taskId2,
                @Nullable Bundle options2, int splitPosition, int snapPosition,
                @Nullable RemoteTransition remoteTransition, InstanceId instanceId) {
            mMainExecutor.execute(() -> SplitScreenController.this.startTasks(
                    taskId1, options1, taskId2, options2, splitPosition, snapPosition,
                    remoteTransition, instanceId));
        }

        @Override
        public void registerSplitScreenListener(SplitScreenListener listener, Executor executor) {
            if (mExecutors.containsKey(listener)) return;

            mMainExecutor.execute(() -> {
                if (mExecutors.size() == 0) {
                    SplitScreenController.this.registerSplitScreenListener(mListener);
                }

                mExecutors.put(listener, executor);
            });

            executor.execute(() -> {
                mStageCoordinator.sendStatusToListener(listener);
            });
        }

        @Override
        public void unregisterSplitScreenListener(SplitScreenListener listener) {
            mMainExecutor.execute(() -> {
                mExecutors.remove(listener);

                if (mExecutors.size() == 0) {
                    SplitScreenController.this.unregisterSplitScreenListener(mListener);
                }
            });
        }

        @Override
        public void registerSplitAnimationListener(@NonNull SplitInvocationListener listener,
                @NonNull Executor executor) {
            mStageCoordinator.registerSplitAnimationListener(listener, executor);
        }

        @Override
        public void onStartedGoingToSleep() {
            mMainExecutor.execute(SplitScreenController.this::onStartedGoingToSleep);
        }

        @Override
        public void onStartedWakingUp() {
            mMainExecutor.execute(SplitScreenController.this::onStartedWakingUp);
        }

        @Override
        public void goToFullscreenFromSplit() {
            mMainExecutor.execute(SplitScreenController.this::goToFullscreenFromSplit);
        }

        @Override
        public void setSplitscreenFocus(boolean leftOrTop) {
            mMainExecutor.execute(
                    () -> SplitScreenController.this.setSplitscreenFocus(leftOrTop));
        }
    }

    /**
     * The interface for calls from outside the host process.
     */
    @BinderThread
    private static class ISplitScreenImpl extends ISplitScreen.Stub
            implements ExternalInterfaceBinder {
        private SplitScreenController mController;
        private final SingleInstanceRemoteListener<SplitScreenController,
                ISplitScreenListener> mListener;
        private final SingleInstanceRemoteListener<SplitScreenController,
                ISplitSelectListener> mSelectListener;
        private final SplitScreen.SplitScreenListener mSplitScreenListener =
                new SplitScreen.SplitScreenListener() {
                    @Override
                    public void onStagePositionChanged(int stage, int position) {
                        mListener.call(l -> l.onStagePositionChanged(stage, position));
                    }

                    @Override
                    public void onTaskStageChanged(int taskId, int stage, boolean visible) {
                        mListener.call(l -> l.onTaskStageChanged(taskId, stage, visible));
                    }
                };

        private final SplitScreen.SplitSelectListener mSplitSelectListener =
                new SplitScreen.SplitSelectListener() {
                    @Override
                    public boolean onRequestEnterSplitSelect(
                            ActivityManager.RunningTaskInfo taskInfo, int splitPosition,
                            Rect taskBounds, boolean startRecents,
                            @Nullable WindowContainerTransaction withRecentsWct) {
                        AtomicBoolean result = new AtomicBoolean(false);
                        mSelectListener.call(l -> result.set(l.onRequestSplitSelect(taskInfo,
                                splitPosition, taskBounds, startRecents, withRecentsWct)));
                        return result.get();
                    }
                };

        public ISplitScreenImpl(SplitScreenController controller) {
            mController = controller;
            mListener = new SingleInstanceRemoteListener<>(controller,
                    c -> c.registerSplitScreenListener(mSplitScreenListener),
                    c -> c.unregisterSplitScreenListener(mSplitScreenListener));
            mSelectListener = new SingleInstanceRemoteListener<>(controller,
                    c -> c.registerSplitSelectListener(mSplitSelectListener),
                    c -> c.unregisterSplitSelectListener(mSplitSelectListener));
        }

        /**
         * Invalidates this instance, preventing future calls from updating the controller.
         */
        @Override
        public void invalidate() {
            mController = null;
            // Unregister the listeners to ensure any binder death recipients are unlinked
            mListener.unregister();
            mSelectListener.unregister();
        }

        @Override
        public void registerSplitScreenListener(ISplitScreenListener listener) {
            executeRemoteCallWithTaskPermission(mController, "registerSplitScreenListener",
                    (controller) -> mListener.register(listener));
        }

        @Override
        public void unregisterSplitScreenListener(ISplitScreenListener listener) {
            executeRemoteCallWithTaskPermission(mController, "unregisterSplitScreenListener",
                    (controller) -> mListener.unregister());
        }

        @Override
        public void registerSplitSelectListener(ISplitSelectListener listener) {
            executeRemoteCallWithTaskPermission(mController, "registerSplitSelectListener",
                    (controller) -> mSelectListener.register(listener));
        }

        @Override
        public void unregisterSplitSelectListener(ISplitSelectListener listener) {
            executeRemoteCallWithTaskPermission(mController, "unregisterSplitSelectListener",
                    (controller) -> mSelectListener.unregister());
        }

        @Override
        public void exitSplitScreen(int toTopTaskId) {
            executeRemoteCallWithTaskPermission(mController, "exitSplitScreen",
                    (controller) -> controller.exitSplitScreen(toTopTaskId, EXIT_REASON_UNKNOWN));
        }

        @Override
        public void exitSplitScreenOnHide(boolean exitSplitScreenOnHide) {
            executeRemoteCallWithTaskPermission(mController, "exitSplitScreenOnHide",
                    (controller) -> controller.exitSplitScreenOnHide(exitSplitScreenOnHide));
        }

        @Override
        public void startTask(int taskId, int position, @Nullable Bundle options) {
            executeRemoteCallWithTaskPermission(mController, "startTask",
                    (controller) -> controller.startTask(taskId, position, options,
                            null /* hideTaskToken */));
        }

        @Override
        public void startTasks(int taskId1, @Nullable Bundle options1, int taskId2,
                @Nullable Bundle options2, @SplitPosition int splitPosition,
                @PersistentSnapPosition int snapPosition,
                @Nullable RemoteTransition remoteTransition, InstanceId instanceId) {
            executeRemoteCallWithTaskPermission(mController, "startTasks",
                    (controller) -> controller.mStageCoordinator.startTasks(taskId1, options1,
                            taskId2, options2, splitPosition, snapPosition, remoteTransition,
                            instanceId));
        }

        @Override
        public void startIntentAndTask(PendingIntent pendingIntent, int userId1,
                @Nullable Bundle options1, int taskId, @Nullable Bundle options2,
                @SplitPosition int splitPosition, @PersistentSnapPosition int snapPosition,
                @Nullable RemoteTransition remoteTransition, InstanceId instanceId) {
            executeRemoteCallWithTaskPermission(mController, "startIntentAndTask",
                    (controller) -> controller.startIntentAndTask(pendingIntent, userId1, options1,
                            taskId, options2, splitPosition, snapPosition, remoteTransition,
                            instanceId));
        }

        @Override
        public void startShortcutAndTask(ShortcutInfo shortcutInfo, @Nullable Bundle options1,
                int taskId, @Nullable Bundle options2, @SplitPosition int splitPosition,
                @PersistentSnapPosition int snapPosition,
                @Nullable RemoteTransition remoteTransition, InstanceId instanceId) {
            executeRemoteCallWithTaskPermission(mController, "startShortcutAndTask",
                    (controller) -> controller.startShortcutAndTask(shortcutInfo, options1, taskId,
                            options2, splitPosition, snapPosition, remoteTransition, instanceId));
        }

        @Override
        public void startIntents(PendingIntent pendingIntent1, int userId1,
                @Nullable ShortcutInfo shortcutInfo1, @Nullable Bundle options1,
                PendingIntent pendingIntent2, int userId2, @Nullable ShortcutInfo shortcutInfo2,
                @Nullable Bundle options2, @SplitPosition int splitPosition,
                @PersistentSnapPosition int snapPosition,
                @Nullable RemoteTransition remoteTransition, InstanceId instanceId) {
            executeRemoteCallWithTaskPermission(mController, "startIntents",
                    (controller) ->
                            controller.startIntents(pendingIntent1, userId1, shortcutInfo1,
                                    options1, pendingIntent2, userId2, shortcutInfo2, options2,
                                    splitPosition, snapPosition, remoteTransition, instanceId)
            );
        }

        @Override
        public void startShortcut(String packageName, String shortcutId, int position,
                @Nullable Bundle options, UserHandle user, InstanceId instanceId) {
            executeRemoteCallWithTaskPermission(mController, "startShortcut",
                    (controller) -> controller.startShortcut(packageName, shortcutId, position,
                            options, user, instanceId));
        }

        @Override
        public void startIntent(PendingIntent intent, int userId, Intent fillInIntent, int position,
                @Nullable Bundle options, InstanceId instanceId) {
            executeRemoteCallWithTaskPermission(mController, "startIntent",
                    (controller) -> controller.startIntentWithInstanceId(intent, userId,
                            fillInIntent, position, options, instanceId));
        }

        @Override
        public void switchSplitPosition() {
            executeRemoteCallWithTaskPermission(mController, "switchSplitPosition",
                    (controller) -> controller.switchSplitPosition("remoteCall"));
        }
    }
}
