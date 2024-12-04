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

import static android.app.ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED;
import static android.app.ActivityTaskManager.INVALID_TASK_ID;
import static android.app.WindowConfiguration.ACTIVITY_TYPE_HOME;
import static android.app.WindowConfiguration.ACTIVITY_TYPE_RECENTS;
import static android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN;
import static android.content.Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT;
import static android.content.res.Configuration.SMALLEST_SCREEN_WIDTH_DP_UNDEFINED;
import static android.view.Display.DEFAULT_DISPLAY;
import static android.view.RemoteAnimationTarget.MODE_OPENING;
import static android.view.WindowManager.LayoutParams.TYPE_DOCK_DIVIDER;
import static android.view.WindowManager.TRANSIT_CHANGE;
import static android.view.WindowManager.TRANSIT_CLOSE;
import static android.view.WindowManager.TRANSIT_KEYGUARD_OCCLUDE;
import static android.view.WindowManager.TRANSIT_TO_BACK;
import static android.view.WindowManager.TRANSIT_TO_FRONT;
import static android.view.WindowManager.transitTypeToString;
import static android.window.TransitionInfo.FLAG_IS_DISPLAY;
import static android.window.WindowContainerTransaction.HierarchyOp.HIERARCHY_OP_TYPE_REORDER;

import static com.android.wm.shell.common.split.SplitLayout.PARALLAX_ALIGN_CENTER;
import static com.android.wm.shell.common.split.SplitScreenConstants.FLAG_IS_DIVIDER_BAR;
import static com.android.wm.shell.common.split.SplitScreenConstants.SPLIT_POSITION_BOTTOM_OR_RIGHT;
import static com.android.wm.shell.common.split.SplitScreenConstants.SPLIT_POSITION_TOP_OR_LEFT;
import static com.android.wm.shell.common.split.SplitScreenConstants.SPLIT_POSITION_UNDEFINED;
import static com.android.wm.shell.common.split.SplitScreenConstants.splitPositionToString;
import static com.android.wm.shell.common.split.SplitScreenUtils.getResizingBackgroundColor;
import static com.android.wm.shell.common.split.SplitScreenUtils.reverseSplitPosition;
import static com.android.wm.shell.common.split.SplitScreenUtils.splitFailureMessage;
import static com.android.wm.shell.protolog.ShellProtoLogGroup.WM_SHELL_SPLIT_SCREEN;
import static com.android.wm.shell.shared.TransitionUtil.isClosingType;
import static com.android.wm.shell.shared.TransitionUtil.isOpeningType;
import static com.android.wm.shell.splitscreen.SplitScreen.STAGE_TYPE_MAIN;
import static com.android.wm.shell.splitscreen.SplitScreen.STAGE_TYPE_SIDE;
import static com.android.wm.shell.splitscreen.SplitScreen.STAGE_TYPE_UNDEFINED;
import static com.android.wm.shell.splitscreen.SplitScreenController.ENTER_REASON_LAUNCHER;
import static com.android.wm.shell.splitscreen.SplitScreenController.ENTER_REASON_MULTI_INSTANCE;
import static com.android.wm.shell.splitscreen.SplitScreenController.EXIT_REASON_APP_DOES_NOT_SUPPORT_MULTIWINDOW;
import static com.android.wm.shell.splitscreen.SplitScreenController.EXIT_REASON_APP_FINISHED;
import static com.android.wm.shell.splitscreen.SplitScreenController.EXIT_REASON_CHILD_TASK_ENTER_PIP;
import static com.android.wm.shell.splitscreen.SplitScreenController.EXIT_REASON_DESKTOP_MODE;
import static com.android.wm.shell.splitscreen.SplitScreenController.EXIT_REASON_DEVICE_FOLDED;
import static com.android.wm.shell.splitscreen.SplitScreenController.EXIT_REASON_DRAG_DIVIDER;
import static com.android.wm.shell.splitscreen.SplitScreenController.EXIT_REASON_FULLSCREEN_REQUEST;
import static com.android.wm.shell.splitscreen.SplitScreenController.EXIT_REASON_FULLSCREEN_SHORTCUT;
import static com.android.wm.shell.splitscreen.SplitScreenController.EXIT_REASON_RECREATE_SPLIT;
import static com.android.wm.shell.splitscreen.SplitScreenController.EXIT_REASON_RETURN_HOME;
import static com.android.wm.shell.splitscreen.SplitScreenController.EXIT_REASON_ROOT_TASK_VANISHED;
import static com.android.wm.shell.splitscreen.SplitScreenController.EXIT_REASON_SCREEN_LOCKED_SHOW_ON_TOP;
import static com.android.wm.shell.splitscreen.SplitScreenController.EXIT_REASON_UNKNOWN;
import static com.android.wm.shell.splitscreen.SplitScreenController.exitReasonToString;
import static com.android.wm.shell.transition.MixedTransitionHelper.getPipReplacingChange;
import static com.android.wm.shell.transition.Transitions.ENABLE_SHELL_TRANSITIONS;
import static com.android.wm.shell.transition.Transitions.TRANSIT_SPLIT_SCREEN_OPEN_TO_SIDE;
import static com.android.wm.shell.transition.Transitions.TRANSIT_SPLIT_SCREEN_PAIR_OPEN;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.annotation.CallSuper;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.ActivityManager;
import android.app.ActivityOptions;
import android.app.IActivityTaskManager;
import android.app.PendingIntent;
import android.app.TaskInfo;
import android.app.WindowConfiguration;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.LauncherApps;
import android.content.pm.ShortcutInfo;
import android.graphics.Rect;
import android.hardware.devicestate.DeviceStateManager;
import android.os.Bundle;
import android.os.Debug;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.UserHandle;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.IntArray;
import android.util.Log;
import android.util.Slog;
import android.view.Choreographer;
import android.view.IRemoteAnimationFinishedCallback;
import android.view.IRemoteAnimationRunner;
import android.view.RemoteAnimationAdapter;
import android.view.RemoteAnimationTarget;
import android.view.SurfaceControl;
import android.view.SurfaceSession;
import android.view.WindowManager;
import android.widget.Toast;
import android.window.DisplayAreaInfo;
import android.window.RemoteTransition;
import android.window.TransitionInfo;
import android.window.TransitionRequestInfo;
import android.window.WindowContainerToken;
import android.window.WindowContainerTransaction;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.logging.InstanceId;
import com.android.internal.protolog.common.ProtoLog;
import com.android.internal.util.ArrayUtils;
import com.android.launcher3.icons.IconProvider;
import com.android.wm.shell.R;
import com.android.wm.shell.ShellTaskOrganizer;
import com.android.wm.shell.common.DisplayController;
import com.android.wm.shell.common.DisplayImeController;
import com.android.wm.shell.common.DisplayInsetsController;
import com.android.wm.shell.common.LaunchAdjacentController;
import com.android.wm.shell.common.ScreenshotUtils;
import com.android.wm.shell.common.ShellExecutor;
import com.android.wm.shell.common.SyncTransactionQueue;
import com.android.wm.shell.common.TransactionPool;
import com.android.wm.shell.common.split.SplitLayout;
import com.android.wm.shell.common.split.SplitScreenConstants.PersistentSnapPosition;
import com.android.wm.shell.common.split.SplitScreenConstants.SplitPosition;
import com.android.wm.shell.common.split.SplitScreenUtils;
import com.android.wm.shell.common.split.SplitWindowManager;
import com.android.wm.shell.protolog.ShellProtoLogGroup;
import com.android.wm.shell.recents.RecentTasksController;
import com.android.wm.shell.shared.TransitionUtil;
import com.android.wm.shell.splitscreen.SplitScreen.StageType;
import com.android.wm.shell.splitscreen.SplitScreenController.ExitReason;
import com.android.wm.shell.splitscreen.SplitScreenController.SplitEnterReason;
import com.android.wm.shell.transition.DefaultMixedHandler;
import com.android.wm.shell.transition.LegacyTransitions;
import com.android.wm.shell.transition.Transitions;
import com.android.wm.shell.util.SplitBounds;
import com.android.wm.shell.windowdecor.WindowDecorViewModel;

import dalvik.annotation.optimization.NeverCompile;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executor;

/**
 * Coordinates the staging (visibility, sizing, ...) of the split-screen {@link MainStage} and
 * {@link SideStage} stages.
 * Some high-level rules:
 * - The {@link StageCoordinator} is only considered active if the {@link SideStage} contains at
 * least one child task.
 * - The {@link MainStage} should only have children if the coordinator is active.
 * - The {@link SplitLayout} divider is only visible if both the {@link MainStage}
 * and {@link SideStage} are visible.
 * - Both stages are put under a single-top root task.
 * This rules are mostly implemented in {@link #onStageVisibilityChanged(StageListenerImpl)} and
 * {@link #onStageHasChildrenChanged(StageListenerImpl).}
 */
public class StageCoordinator implements SplitLayout.SplitLayoutHandler,
        DisplayController.OnDisplaysChangedListener, Transitions.TransitionHandler,
        ShellTaskOrganizer.TaskListener {

    private static final String TAG = StageCoordinator.class.getSimpleName();

    private final SurfaceSession mSurfaceSession = new SurfaceSession();

    private final MainStage mMainStage;
    private final StageListenerImpl mMainStageListener = new StageListenerImpl();
    private final SideStage mSideStage;
    private final StageListenerImpl mSideStageListener = new StageListenerImpl();
    @SplitPosition
    private int mSideStagePosition = SPLIT_POSITION_BOTTOM_OR_RIGHT;

    private final int mDisplayId;
    private SplitLayout mSplitLayout;
    private ValueAnimator mDividerFadeInAnimator;
    private boolean mDividerVisible;
    private boolean mKeyguardShowing;
    private boolean mShowDecorImmediately;
    private final SyncTransactionQueue mSyncQueue;
    private final ShellTaskOrganizer mTaskOrganizer;
    private final Context mContext;
    private final List<SplitScreen.SplitScreenListener> mListeners = new ArrayList<>();
    private final Set<SplitScreen.SplitSelectListener> mSelectListeners = new HashSet<>();
    private final DisplayController mDisplayController;
    private final DisplayImeController mDisplayImeController;
    private final DisplayInsetsController mDisplayInsetsController;
    private final TransactionPool mTransactionPool;
    private SplitScreenTransitions mSplitTransitions;
    private final SplitscreenEventLogger mLogger;
    private final ShellExecutor mMainExecutor;
    // Cache live tile tasks while entering recents, evict them from stages in finish transaction
    // if user is opening another task(s).
    private final ArrayList<Integer> mPausingTasks = new ArrayList<>();
    private final Optional<RecentTasksController> mRecentTasks;
    private final LaunchAdjacentController mLaunchAdjacentController;
    private final Optional<WindowDecorViewModel> mWindowDecorViewModel;

    private final Rect mTempRect1 = new Rect();
    private final Rect mTempRect2 = new Rect();

    /**
     * A single-top root task which the split divider attached to.
     */
    @VisibleForTesting
    ActivityManager.RunningTaskInfo mRootTaskInfo;

    private SurfaceControl mRootTaskLeash;

    // Tracks whether we should update the recent tasks.  Only allow this to happen in between enter
    // and exit, since exit itself can trigger a number of changes that update the stages.
    private boolean mShouldUpdateRecents;
    private boolean mExitSplitScreenOnHide;
    private boolean mIsDividerRemoteAnimating;
    private boolean mIsDropEntering;
    private boolean mSkipEvictingMainStageChildren;
    private boolean mIsExiting;
    private boolean mIsRootTranslucent;
    @VisibleForTesting
    int mTopStageAfterFoldDismiss;

    private DefaultMixedHandler mMixedHandler;
    private final Toast mSplitUnsupportedToast;
    private SplitRequest mSplitRequest;
    /** Used to notify others of when shell is animating into split screen */
    private SplitScreen.SplitInvocationListener mSplitInvocationListener;
    private Executor mSplitInvocationListenerExecutor;

    /**
     * Since StageCoordinator only coordinates MainStage and SideStage, it shouldn't support
     * CompatUI layouts. CompatUI is handled separately by MainStage and SideStage.
     */
    @Override
    public boolean supportCompatUI() {
        return false;
    }

    /** NOTE: Will overwrite any previously set {@link #mSplitInvocationListener} */
    public void registerSplitAnimationListener(
            @NonNull SplitScreen.SplitInvocationListener listener, @NonNull Executor executor) {
        mSplitInvocationListener = listener;
        mSplitInvocationListenerExecutor = executor;
        mSplitTransitions.registerSplitAnimListener(listener, executor);
    }

    class SplitRequest {
        @SplitPosition
        int mActivatePosition;
        int mActivateTaskId;
        int mActivateTaskId2;
        Intent mStartIntent;
        Intent mStartIntent2;

        SplitRequest(int taskId, Intent startIntent, int position) {
            mActivateTaskId = taskId;
            mStartIntent = startIntent;
            mActivatePosition = position;
        }
        SplitRequest(Intent startIntent, int position) {
            mStartIntent = startIntent;
            mActivatePosition = position;
        }
        SplitRequest(Intent startIntent, Intent startIntent2, int position) {
            mStartIntent = startIntent;
            mStartIntent2 = startIntent2;
            mActivatePosition = position;
        }
        SplitRequest(int taskId1, int position) {
            mActivateTaskId = taskId1;
            mActivatePosition = position;
        }
        SplitRequest(int taskId1, int taskId2, int position) {
            mActivateTaskId = taskId1;
            mActivateTaskId2 = taskId2;
            mActivatePosition = position;
        }
    }

    private final SplitWindowManager.ParentContainerCallbacks mParentContainerCallbacks =
            new SplitWindowManager.ParentContainerCallbacks() {
                @Override
                public void attachToParentSurface(SurfaceControl.Builder b) {
                    b.setParent(mRootTaskLeash);
                }

                @Override
                public void onLeashReady(SurfaceControl leash) {
                    // This is for avoiding divider invisible due to delay of creating so only need
                    // to do when divider should visible case.
                    if (mDividerVisible) {
                        mSyncQueue.runInSync(t -> applyDividerVisibility(t));
                    }
                }
            };

    protected StageCoordinator(Context context, int displayId, SyncTransactionQueue syncQueue,
            ShellTaskOrganizer taskOrganizer, DisplayController displayController,
            DisplayImeController displayImeController,
            DisplayInsetsController displayInsetsController, Transitions transitions,
            TransactionPool transactionPool,
            IconProvider iconProvider, ShellExecutor mainExecutor,
            Optional<RecentTasksController> recentTasks,
            LaunchAdjacentController launchAdjacentController,
            Optional<WindowDecorViewModel> windowDecorViewModel) {
        mContext = context;
        mDisplayId = displayId;
        mSyncQueue = syncQueue;
        mTaskOrganizer = taskOrganizer;
        mLogger = new SplitscreenEventLogger();
        mMainExecutor = mainExecutor;
        mRecentTasks = recentTasks;
        mLaunchAdjacentController = launchAdjacentController;
        mWindowDecorViewModel = windowDecorViewModel;

        taskOrganizer.createRootTask(displayId, WINDOWING_MODE_FULLSCREEN, this /* listener */);

        ProtoLog.d(WM_SHELL_SPLIT_SCREEN, "Creating main/side root task");
        mMainStage = new MainStage(
                mContext,
                mTaskOrganizer,
                mDisplayId,
                mMainStageListener,
                mSyncQueue,
                mSurfaceSession,
                iconProvider,
                mWindowDecorViewModel);
        mSideStage = new SideStage(
                mContext,
                mTaskOrganizer,
                mDisplayId,
                mSideStageListener,
                mSyncQueue,
                mSurfaceSession,
                iconProvider,
                mWindowDecorViewModel);
        mDisplayController = displayController;
        mDisplayImeController = displayImeController;
        mDisplayInsetsController = displayInsetsController;
        mTransactionPool = transactionPool;
        final DeviceStateManager deviceStateManager =
                mContext.getSystemService(DeviceStateManager.class);
        deviceStateManager.registerCallback(taskOrganizer.getExecutor(),
                new DeviceStateManager.FoldStateListener(mContext, this::onFoldedStateChanged));
        mSplitTransitions = new SplitScreenTransitions(transactionPool, transitions,
                this::onTransitionAnimationComplete, this);
        mDisplayController.addDisplayWindowListener(this);
        transitions.addHandler(this);
        mSplitUnsupportedToast = Toast.makeText(mContext,
                R.string.dock_non_resizeble_failed_to_dock_text, Toast.LENGTH_SHORT);
        // With shell transition, we should update recents tile each callback so set this to true by
        // default.
        mShouldUpdateRecents = ENABLE_SHELL_TRANSITIONS;
    }

    @VisibleForTesting
    StageCoordinator(Context context, int displayId, SyncTransactionQueue syncQueue,
            ShellTaskOrganizer taskOrganizer, MainStage mainStage, SideStage sideStage,
            DisplayController displayController, DisplayImeController displayImeController,
            DisplayInsetsController displayInsetsController, SplitLayout splitLayout,
            Transitions transitions, TransactionPool transactionPool,
            ShellExecutor mainExecutor,
            Optional<RecentTasksController> recentTasks,
            LaunchAdjacentController launchAdjacentController,
            Optional<WindowDecorViewModel> windowDecorViewModel) {
        mContext = context;
        mDisplayId = displayId;
        mSyncQueue = syncQueue;
        mTaskOrganizer = taskOrganizer;
        mMainStage = mainStage;
        mSideStage = sideStage;
        mDisplayController = displayController;
        mDisplayImeController = displayImeController;
        mDisplayInsetsController = displayInsetsController;
        mTransactionPool = transactionPool;
        mSplitLayout = splitLayout;
        mSplitTransitions = new SplitScreenTransitions(transactionPool, transitions,
                this::onTransitionAnimationComplete, this);
        mLogger = new SplitscreenEventLogger();
        mMainExecutor = mainExecutor;
        mRecentTasks = recentTasks;
        mLaunchAdjacentController = launchAdjacentController;
        mWindowDecorViewModel = windowDecorViewModel;
        mDisplayController.addDisplayWindowListener(this);
        transitions.addHandler(this);
        mSplitUnsupportedToast = Toast.makeText(mContext,
                R.string.dock_non_resizeble_failed_to_dock_text, Toast.LENGTH_SHORT);
    }

    public void setMixedHandler(DefaultMixedHandler mixedHandler) {
        mMixedHandler = mixedHandler;
    }

    @VisibleForTesting
    SplitScreenTransitions getSplitTransitions() {
        return mSplitTransitions;
    }

    @VisibleForTesting
    void setSplitTransitions(SplitScreenTransitions splitScreenTransitions) {
        mSplitTransitions = splitScreenTransitions;
    }

    public boolean isSplitScreenVisible() {
        return mSideStageListener.mVisible && mMainStageListener.mVisible;
    }

    public boolean isSplitActive() {
        return mMainStage.isActive();
    }

    /** @return whether this transition-request has the launch-adjacent flag. */
    public boolean requestHasLaunchAdjacentFlag(TransitionRequestInfo request) {
        final ActivityManager.RunningTaskInfo triggerTask = request.getTriggerTask();
        return triggerTask != null && triggerTask.baseIntent != null
                && (triggerTask.baseIntent.getFlags() & FLAG_ACTIVITY_LAUNCH_ADJACENT) != 0;
    }

    /** @return whether the transition-request implies entering pip from split. */
    public boolean requestImpliesSplitToPip(TransitionRequestInfo request) {
        if (!isSplitActive() || !mMixedHandler.requestHasPipEnter(request)) {
            return false;
        }

        if (request.getTriggerTask() != null && getSplitPosition(
                request.getTriggerTask().taskId) != SPLIT_POSITION_UNDEFINED) {
            return true;
        }

        // If one of the splitting tasks support auto-pip, wm-core might reparent the task to TDA
        // and file a TRANSIT_PIP transition when finishing transitions.
        // @see com.android.server.wm.RootWindowContainer#moveActivityToPinnedRootTask
        if (mMainStage.getChildCount() == 0 || mSideStage.getChildCount() == 0) {
            return true;
        }

        return false;
    }

    /** Checks if `transition` is a pending enter-split transition. */
    public boolean isPendingEnter(IBinder transition) {
        return mSplitTransitions.isPendingEnter(transition);
    }

    @StageType
    int getStageOfTask(int taskId) {
        if (mMainStage.containsTask(taskId)) {
            return STAGE_TYPE_MAIN;
        } else if (mSideStage.containsTask(taskId)) {
            return STAGE_TYPE_SIDE;
        }

        return STAGE_TYPE_UNDEFINED;
    }

    boolean isRootOrStageRoot(int taskId) {
        if (mRootTaskInfo != null && mRootTaskInfo.taskId == taskId) {
            return true;
        }
        return mMainStage.isRootTaskId(taskId) || mSideStage.isRootTaskId(taskId);
    }

    boolean moveToStage(ActivityManager.RunningTaskInfo task, @SplitPosition int stagePosition,
            WindowContainerTransaction wct) {
        ProtoLog.d(WM_SHELL_SPLIT_SCREEN, "moveToStage: task=%d position=%d", task.taskId,
                stagePosition);
        prepareEnterSplitScreen(wct, task, stagePosition, false /* resizeAnim */);
        if (ENABLE_SHELL_TRANSITIONS) {
            mSplitTransitions.startEnterTransition(TRANSIT_TO_FRONT, wct,
                    null, this,
                    isSplitScreenVisible()
                            ? TRANSIT_SPLIT_SCREEN_OPEN_TO_SIDE : TRANSIT_SPLIT_SCREEN_PAIR_OPEN,
                    !mIsDropEntering);
        } else {
            mSyncQueue.queue(wct);
            mSyncQueue.runInSync(t -> {
                updateSurfaceBounds(mSplitLayout, t, false /* applyResizingOffset */);
            });
        }
        // Due to drag already pip task entering split by this method so need to reset flag here.
        mIsDropEntering = false;
        mSkipEvictingMainStageChildren = false;
        return true;
    }

    boolean removeFromSideStage(int taskId) {
        ProtoLog.d(WM_SHELL_SPLIT_SCREEN, "removeFromSideStage: task=%d", taskId);
        final WindowContainerTransaction wct = new WindowContainerTransaction();

        /**
         * {@link MainStage} will be deactivated in {@link #onStageHasChildrenChanged} if the
         * {@link SideStage} no longer has children.
         */
        final boolean result = mSideStage.removeTask(taskId,
                mMainStage.isActive() ? mMainStage.mRootTaskInfo.token : null,
                wct);
        mTaskOrganizer.applyTransaction(wct);
        return result;
    }

    SplitscreenEventLogger getLogger() {
        return mLogger;
    }

    void requestEnterSplitSelect(ActivityManager.RunningTaskInfo taskInfo,
            WindowContainerTransaction wct, int splitPosition, Rect taskBounds) {
        boolean enteredSplitSelect = false;
        for (SplitScreen.SplitSelectListener listener : mSelectListeners) {
            enteredSplitSelect |= listener.onRequestEnterSplitSelect(taskInfo, splitPosition,
                    taskBounds);
        }
        if (enteredSplitSelect) {
            mTaskOrganizer.applyTransaction(wct);
        }
    }

    void startShortcut(String packageName, String shortcutId, @SplitPosition int position,
            Bundle options, UserHandle user) {
        ProtoLog.d(WM_SHELL_SPLIT_SCREEN, "startShortcut: pkg=%s id=%s position=%d user=%d",
                packageName, shortcutId, position, user.getIdentifier());
        final boolean isEnteringSplit = !isSplitActive();

        IRemoteAnimationRunner wrapper = new IRemoteAnimationRunner.Stub() {
            @Override
            public void onAnimationStart(@WindowManager.TransitionOldType int transit,
                    RemoteAnimationTarget[] apps,
                    RemoteAnimationTarget[] wallpapers,
                    RemoteAnimationTarget[] nonApps,
                    final IRemoteAnimationFinishedCallback finishedCallback) {
                if (isEnteringSplit && mSideStage.getChildCount() == 0) {
                    mMainExecutor.execute(() -> exitSplitScreen(
                            null /* childrenToTop */, EXIT_REASON_UNKNOWN));
                    Log.w(TAG, splitFailureMessage("startShortcut",
                            "side stage was not populated"));
                    handleUnsupportedSplitStart();
                }

                if (finishedCallback != null) {
                    try {
                        finishedCallback.onAnimationFinished();
                    } catch (RemoteException e) {
                        Slog.e(TAG, "Error finishing legacy transition: ", e);
                    }
                }

                if (!isEnteringSplit && apps != null) {
                    final WindowContainerTransaction evictWct = new WindowContainerTransaction();
                    prepareEvictNonOpeningChildTasks(position, apps, evictWct);
                    mSyncQueue.queue(evictWct);
                }
            }
            @Override
            public void onAnimationCancelled() {
                if (isEnteringSplit) {
                    mMainExecutor.execute(() -> exitSplitScreen(
                            mSideStage.getChildCount() == 0 ? mMainStage : mSideStage,
                            EXIT_REASON_UNKNOWN));
                }
            }
        };
        options = resolveStartStage(STAGE_TYPE_UNDEFINED, position, options,
                null /* wct */);
        RemoteAnimationAdapter wrappedAdapter = new RemoteAnimationAdapter(wrapper,
                0 /* duration */, 0 /* statusBarTransitionDelay */);
        ActivityOptions activityOptions = ActivityOptions.fromBundle(options);
        // Flag this as a no-user-action launch to prevent sending user leaving event to the current
        // top activity since it's going to be put into another side of the split. This prevents the
        // current top activity from going into pip mode due to user leaving event.
        activityOptions.setApplyNoUserActionFlagForShortcut(true);
        activityOptions.update(ActivityOptions.makeRemoteAnimation(wrappedAdapter));
        try {
            LauncherApps launcherApps = mContext.getSystemService(LauncherApps.class);
            launcherApps.startShortcut(packageName, shortcutId, null /* sourceBounds */,
                    activityOptions.toBundle(), user);
        } catch (ActivityNotFoundException e) {
            Slog.e(TAG, "Failed to launch shortcut", e);
        }
    }

    /** Use this method to launch an existing Task via a taskId */
    void startTask(int taskId, @SplitPosition int position, @Nullable Bundle options) {
        ProtoLog.d(WM_SHELL_SPLIT_SCREEN, "startTask: task=%d position=%d", taskId, position);
        mSplitRequest = new SplitRequest(taskId, position);
        final WindowContainerTransaction wct = new WindowContainerTransaction();
        options = resolveStartStage(STAGE_TYPE_UNDEFINED, position, options, null /* wct */);
        wct.startTask(taskId, options);
        // If this should be mixed, send the task to avoid split handle transition directly.
        if (mMixedHandler != null && mMixedHandler.isTaskInPip(taskId, mTaskOrganizer)) {
            mTaskOrganizer.applyTransaction(wct);
            return;
        }

        // Don't evict the main stage children as this can race and happen after the activity is
        // started into that stage
        if (!isSplitScreenVisible()) {
            mSkipEvictingMainStageChildren = true;
            // Starting the split task without evicting children will bring the single root task
            // container forward, so ensure that we hide the divider before we start animate it
            setDividerVisibility(false, null);
        }

        // If split screen is not activated, we're expecting to open a pair of apps to split.
        final int extraTransitType = mMainStage.isActive()
                ? TRANSIT_SPLIT_SCREEN_OPEN_TO_SIDE : TRANSIT_SPLIT_SCREEN_PAIR_OPEN;
        prepareEnterSplitScreen(wct, null /* taskInfo */, position, !mIsDropEntering);

        mSplitTransitions.startEnterTransition(TRANSIT_TO_FRONT, wct, null, this,
                extraTransitType, !mIsDropEntering);
    }

    /** Launches an activity into split. */
    void startIntent(PendingIntent intent, Intent fillInIntent, @SplitPosition int position,
            @Nullable Bundle options) {
        ProtoLog.d(WM_SHELL_SPLIT_SCREEN, "startIntent: intent=%s position=%d", intent.getIntent(),
                position);
        mSplitRequest = new SplitRequest(intent.getIntent(), position);
        if (!ENABLE_SHELL_TRANSITIONS) {
            startIntentLegacy(intent, fillInIntent, position, options);
            return;
        }

        final WindowContainerTransaction wct = new WindowContainerTransaction();
        options = resolveStartStage(STAGE_TYPE_UNDEFINED, position, options, null /* wct */);
        wct.sendPendingIntent(intent, fillInIntent, options);

        // If this should be mixed, just send the intent to avoid split handle transition directly.
        if (mMixedHandler != null && mMixedHandler.isIntentInPip(intent)) {
            mTaskOrganizer.applyTransaction(wct);
            return;
        }

        // Don't evict the main stage children as this can race and happen after the activity is
        // started into that stage
        if (!isSplitScreenVisible()) {
            mSkipEvictingMainStageChildren = true;
            // Starting the split task without evicting children will bring the single root task
            // container forward, so ensure that we hide the divider before we start animate it
            setDividerVisibility(false, null);
        }

        // If split screen is not activated, we're expecting to open a pair of apps to split.
        final int extraTransitType = mMainStage.isActive()
                ? TRANSIT_SPLIT_SCREEN_OPEN_TO_SIDE : TRANSIT_SPLIT_SCREEN_PAIR_OPEN;
        prepareEnterSplitScreen(wct, null /* taskInfo */, position, !mIsDropEntering);

        mSplitTransitions.startEnterTransition(TRANSIT_TO_FRONT, wct, null, this,
                extraTransitType, !mIsDropEntering);
    }

    /** Launches an activity into split by legacy transition. */
    void startIntentLegacy(PendingIntent intent, Intent fillInIntent, @SplitPosition int position,
            @Nullable Bundle options) {
        final boolean isEnteringSplit = !isSplitActive();

        LegacyTransitions.ILegacyTransition transition = new LegacyTransitions.ILegacyTransition() {
            @Override
            public void onAnimationStart(int transit, RemoteAnimationTarget[] apps,
                    RemoteAnimationTarget[] wallpapers, RemoteAnimationTarget[] nonApps,
                    IRemoteAnimationFinishedCallback finishedCallback,
                    SurfaceControl.Transaction t) {
                if (isEnteringSplit && mSideStage.getChildCount() == 0) {
                    mMainExecutor.execute(() -> exitSplitScreen(
                            null /* childrenToTop */, EXIT_REASON_UNKNOWN));
                    Log.w(TAG, splitFailureMessage("startIntentLegacy",
                            "side stage was not populated"));
                    handleUnsupportedSplitStart();
                }

                if (apps != null) {
                    for (int i = 0; i < apps.length; ++i) {
                        if (apps[i].mode == MODE_OPENING) {
                            t.show(apps[i].leash);
                        }
                    }
                }
                t.apply();

                if (finishedCallback != null) {
                    try {
                        finishedCallback.onAnimationFinished();
                    } catch (RemoteException e) {
                        Slog.e(TAG, "Error finishing legacy transition: ", e);
                    }
                }


                if (!isEnteringSplit && apps != null) {
                    final WindowContainerTransaction evictWct = new WindowContainerTransaction();
                    prepareEvictNonOpeningChildTasks(position, apps, evictWct);
                    mSyncQueue.queue(evictWct);
                }
            }
        };

        final WindowContainerTransaction wct = new WindowContainerTransaction();
        options = resolveStartStage(STAGE_TYPE_UNDEFINED, position, options, wct);

        // If split still not active, apply windows bounds first to avoid surface reset to
        // wrong pos by SurfaceAnimator from wms.
        if (isEnteringSplit && mLogger.isEnterRequestedByDrag()) {
            updateWindowBounds(mSplitLayout, wct);
        }
        wct.sendPendingIntent(intent, fillInIntent, options);
        mSyncQueue.queue(transition, WindowManager.TRANSIT_OPEN, wct);
    }

    /** Starts 2 tasks in one transition. */
    void startTasks(int taskId1, @Nullable Bundle options1, int taskId2, @Nullable Bundle options2,
            @SplitPosition int splitPosition, @PersistentSnapPosition int snapPosition,
            @Nullable RemoteTransition remoteTransition, InstanceId instanceId) {
        ProtoLog.d(WM_SHELL_SPLIT_SCREEN,
                "startTasks: task1=%d task2=%d position=%d snapPosition=%d",
                taskId1, taskId2, splitPosition, snapPosition);
        final WindowContainerTransaction wct = new WindowContainerTransaction();
        if (taskId2 == INVALID_TASK_ID) {
            startSingleTask(taskId1, options1, wct, remoteTransition);
            return;
        }

        setSideStagePosition(splitPosition, wct);
        options1 = options1 != null ? options1 : new Bundle();
        addActivityOptions(options1, mSideStage);
        wct.startTask(taskId1, options1);

        startWithTask(wct, taskId2, options2, snapPosition, remoteTransition, instanceId);
    }

    /** Start an intent and a task to a split pair in one transition. */
    void startIntentAndTask(PendingIntent pendingIntent, Intent fillInIntent,
            @Nullable Bundle options1, int taskId, @Nullable Bundle options2,
            @SplitPosition int splitPosition, @PersistentSnapPosition int snapPosition,
            @Nullable RemoteTransition remoteTransition, InstanceId instanceId) {
        ProtoLog.d(WM_SHELL_SPLIT_SCREEN,
                "startIntentAndTask: intent=%s task1=%d position=%d snapPosition=%d",
                pendingIntent.getIntent(), taskId, splitPosition, snapPosition);
        final WindowContainerTransaction wct = new WindowContainerTransaction();
        boolean firstIntentPipped = mMixedHandler.isIntentInPip(pendingIntent);
        boolean secondTaskPipped = mMixedHandler.isTaskInPip(taskId, mTaskOrganizer);
        if (taskId == INVALID_TASK_ID || secondTaskPipped) {
            startSingleIntent(pendingIntent, fillInIntent, options1, wct, remoteTransition);
            return;
        }

        if (firstIntentPipped) {
            startSingleTask(taskId, options2, wct, remoteTransition);
            return;
        }

        setSideStagePosition(splitPosition, wct);
        options1 = options1 != null ? options1 : new Bundle();
        addActivityOptions(options1, mSideStage);
        wct.sendPendingIntent(pendingIntent, fillInIntent, options1);

        startWithTask(wct, taskId, options2, snapPosition, remoteTransition, instanceId);
    }

    /**
     * @param taskId Starts this task in fullscreen, removing it from existing pairs if it was part
     *               of one.
     */
    private void startSingleTask(int taskId, Bundle options, WindowContainerTransaction wct,
            RemoteTransition remoteTransition) {
        if (mMainStage.containsTask(taskId) || mSideStage.containsTask(taskId)) {
            prepareExitSplitScreen(STAGE_TYPE_UNDEFINED, wct);
        }
        if (mRecentTasks.isPresent()) {
            mRecentTasks.get().removeSplitPair(taskId);
        }
        options = options != null ? options : new Bundle();
        addActivityOptions(options, null);
        wct.startTask(taskId, options);
        mSplitTransitions.startFullscreenTransition(wct, remoteTransition);
    }

    /** Starts a shortcut and a task to a split pair in one transition. */
    void startShortcutAndTask(ShortcutInfo shortcutInfo, @Nullable Bundle options1,
            int taskId, @Nullable Bundle options2, @SplitPosition int splitPosition,
            @PersistentSnapPosition int snapPosition, @Nullable RemoteTransition remoteTransition,
            InstanceId instanceId) {
        ProtoLog.d(WM_SHELL_SPLIT_SCREEN,
                "startShortcutAndTask: shortcut=%s task1=%d position=%d snapPosition=%d",
                shortcutInfo, taskId, splitPosition, snapPosition);
        final WindowContainerTransaction wct = new WindowContainerTransaction();
        if (taskId == INVALID_TASK_ID) {
            options1 = options1 != null ? options1 : new Bundle();
            addActivityOptions(options1, null);
            wct.startShortcut(mContext.getPackageName(), shortcutInfo, options1);
            mSplitTransitions.startFullscreenTransition(wct, remoteTransition);
            return;
        }

        setSideStagePosition(splitPosition, wct);
        options1 = options1 != null ? options1 : new Bundle();
        addActivityOptions(options1, mSideStage);
        wct.startShortcut(mContext.getPackageName(), shortcutInfo, options1);

        startWithTask(wct, taskId, options2, snapPosition, remoteTransition, instanceId);
    }

    /**
     * Starts with the second task to a split pair in one transition.
     *
     * @param wct        transaction to start the first task
     * @param instanceId if {@code null}, will not log. Otherwise it will be used in
     *                   {@link SplitscreenEventLogger#logEnter(float, int, int, int, int, boolean)}
     */
    private void startWithTask(WindowContainerTransaction wct, int mainTaskId,
            @Nullable Bundle mainOptions, @PersistentSnapPosition int snapPosition,
            @Nullable RemoteTransition remoteTransition, InstanceId instanceId) {
        if (!mMainStage.isActive()) {
            // Build a request WCT that will launch both apps such that task 0 is on the main stage
            // while task 1 is on the side stage.
            mMainStage.activate(wct, false /* reparent */);
        }
        mSplitLayout.setDivideRatio(snapPosition);
        updateWindowBounds(mSplitLayout, wct);
        wct.reorder(mRootTaskInfo.token, true);
        wct.setReparentLeafTaskIfRelaunch(mRootTaskInfo.token,
                false /* reparentLeafTaskIfRelaunch */);
        setRootForceTranslucent(false, wct);

        // Make sure the launch options will put tasks in the corresponding split roots
        mainOptions = mainOptions != null ? mainOptions : new Bundle();
        addActivityOptions(mainOptions, mMainStage);

        // Add task launch requests
        wct.startTask(mainTaskId, mainOptions);

        // leave recents animation by re-start pausing tasks
        if (mPausingTasks.contains(mainTaskId)) {
            mPausingTasks.clear();
        }
        mSplitTransitions.startEnterTransition(TRANSIT_TO_FRONT, wct, remoteTransition, this,
                TRANSIT_SPLIT_SCREEN_PAIR_OPEN, false);
        setEnterInstanceId(instanceId);
    }

    void startIntents(PendingIntent pendingIntent1, Intent fillInIntent1,
            @Nullable ShortcutInfo shortcutInfo1, @Nullable Bundle options1,
            @Nullable PendingIntent pendingIntent2, Intent fillInIntent2,
            @Nullable ShortcutInfo shortcutInfo2, @Nullable Bundle options2,
            @SplitPosition int splitPosition, @PersistentSnapPosition int snapPosition,
            @Nullable RemoteTransition remoteTransition, InstanceId instanceId) {
        ProtoLog.d(WM_SHELL_SPLIT_SCREEN,
                "startIntents: intent1=%s intent2=%s position=%d snapPosition=%d",
                pendingIntent1.getIntent(),
                (pendingIntent2 != null ? pendingIntent2.getIntent() : "null"),
                splitPosition, snapPosition);
        final WindowContainerTransaction wct = new WindowContainerTransaction();
        if (pendingIntent2 == null) {
            options1 = options1 != null ? options1 : new Bundle();
            addActivityOptions(options1, null);
            if (shortcutInfo1 != null) {
                wct.startShortcut(mContext.getPackageName(), shortcutInfo1, options1);
            } else {
                wct.sendPendingIntent(pendingIntent1, fillInIntent1, options1);
            }
            mSplitTransitions.startFullscreenTransition(wct, remoteTransition);
            return;
        }

        boolean handledForPipSplitLaunch = handlePippedSplitIntentsLaunch(
                pendingIntent1,
                pendingIntent2,
                options1,
                options2,
                shortcutInfo1,
                shortcutInfo2,
                wct,
                fillInIntent1,
                fillInIntent2,
                remoteTransition);
        if (handledForPipSplitLaunch) {
            return;
        }

        if (!mMainStage.isActive()) {
            // Build a request WCT that will launch both apps such that task 0 is on the main stage
            // while task 1 is on the side stage.
            mMainStage.activate(wct, false /* reparent */);
        }

        setSideStagePosition(splitPosition, wct);
        mSplitLayout.setDivideRatio(snapPosition);
        updateWindowBounds(mSplitLayout, wct);
        wct.reorder(mRootTaskInfo.token, true);
        wct.setReparentLeafTaskIfRelaunch(mRootTaskInfo.token,
                false /* reparentLeafTaskIfRelaunch */);
        setRootForceTranslucent(false, wct);

        options1 = options1 != null ? options1 : new Bundle();
        addActivityOptions(options1, mSideStage);
        if (shortcutInfo1 != null) {
            wct.startShortcut(mContext.getPackageName(), shortcutInfo1, options1);
        } else {
            wct.sendPendingIntent(pendingIntent1, fillInIntent1, options1);
        }
        options2 = options2 != null ? options2 : new Bundle();
        addActivityOptions(options2, mMainStage);
        if (shortcutInfo2 != null) {
            wct.startShortcut(mContext.getPackageName(), shortcutInfo2, options2);
        } else {
            wct.sendPendingIntent(pendingIntent2, fillInIntent2, options2);
        }

        mSplitTransitions.startEnterTransition(TRANSIT_TO_FRONT, wct, remoteTransition, this,
                TRANSIT_SPLIT_SCREEN_PAIR_OPEN, false);
        setEnterInstanceId(instanceId);
    }

    /**
     * Checks if either of the apps in the desired split launch is currently in Pip. If so, it will
     * launch the non-pipped app as a fullscreen app, otherwise no-op.
     */
    private boolean handlePippedSplitIntentsLaunch(PendingIntent pendingIntent1,
            PendingIntent pendingIntent2, Bundle options1, Bundle options2,
            ShortcutInfo shortcutInfo1, ShortcutInfo shortcutInfo2, WindowContainerTransaction wct,
            Intent fillInIntent1, Intent fillInIntent2, RemoteTransition remoteTransition) {
        // If one of the split apps to start is in Pip, only launch the non-pip app in fullscreen
        boolean firstIntentPipped = mMixedHandler.isIntentInPip(pendingIntent1);
        boolean secondIntentPipped = mMixedHandler.isIntentInPip(pendingIntent2);
        if (firstIntentPipped || secondIntentPipped) {
            Bundle options = secondIntentPipped ? options1 : options2;
            options = options == null ? new Bundle() : options;
            addActivityOptions(options, null);
            if (shortcutInfo1 != null || shortcutInfo2 != null) {
                ShortcutInfo infoToLaunch = secondIntentPipped ? shortcutInfo1 : shortcutInfo2;
                wct.startShortcut(mContext.getPackageName(), infoToLaunch, options);
                mSplitTransitions.startFullscreenTransition(wct, remoteTransition);
            } else {
                PendingIntent intentToLaunch = secondIntentPipped ? pendingIntent1 : pendingIntent2;
                Intent fillInIntentToLaunch = secondIntentPipped ? fillInIntent1 : fillInIntent2;
                startSingleIntent(intentToLaunch, fillInIntentToLaunch, options, wct,
                        remoteTransition);
            }
            return true;
        }
        return false;
    }

    /** @param pendingIntent Starts this intent in fullscreen */
    private void startSingleIntent(PendingIntent pendingIntent, Intent fillInIntent, Bundle options,
            WindowContainerTransaction wct,
            RemoteTransition remoteTransition) {
        Bundle optionsToLaunch = options != null ? options : new Bundle();
        addActivityOptions(optionsToLaunch, null);
        wct.sendPendingIntent(pendingIntent, fillInIntent, optionsToLaunch);
        mSplitTransitions.startFullscreenTransition(wct, remoteTransition);
    }

    /** Starts a pair of tasks using legacy transition. */
    void startTasksWithLegacyTransition(int taskId1, @Nullable Bundle options1,
            int taskId2, @Nullable Bundle options2, @SplitPosition int splitPosition,
            @PersistentSnapPosition int snapPosition, RemoteAnimationAdapter adapter,
            InstanceId instanceId) {
        final WindowContainerTransaction wct = new WindowContainerTransaction();
        if (options1 == null) options1 = new Bundle();
        if (taskId2 == INVALID_TASK_ID) {
            // Launching a solo task.
            // Exit split first if this task under split roots.
            if (mMainStage.containsTask(taskId1) || mSideStage.containsTask(taskId1)) {
                exitSplitScreen(null /* childrenToTop */, EXIT_REASON_RECREATE_SPLIT);
            }
            ActivityOptions activityOptions = ActivityOptions.fromBundle(options1);
            activityOptions.update(ActivityOptions.makeRemoteAnimation(adapter));
            options1 = activityOptions.toBundle();
            addActivityOptions(options1, null /* launchTarget */);
            wct.startTask(taskId1, options1);
            mSyncQueue.queue(wct);
            return;
        }

        addActivityOptions(options1, mSideStage);
        wct.startTask(taskId1, options1);
        mSplitRequest = new SplitRequest(taskId1, taskId2, splitPosition);
        startWithLegacyTransition(wct, taskId2, options2, splitPosition, snapPosition, adapter,
                instanceId);
    }

    /** Starts a pair of intents using legacy transition. */
    void startIntentsWithLegacyTransition(PendingIntent pendingIntent1, Intent fillInIntent1,
            @Nullable ShortcutInfo shortcutInfo1, @Nullable Bundle options1,
            @Nullable PendingIntent pendingIntent2, Intent fillInIntent2,
            @Nullable ShortcutInfo shortcutInfo2, @Nullable Bundle options2,
            @SplitPosition int splitPosition, @PersistentSnapPosition int snapPosition,
            RemoteAnimationAdapter adapter, InstanceId instanceId) {
        final WindowContainerTransaction wct = new WindowContainerTransaction();
        if (options1 == null) options1 = new Bundle();
        if (pendingIntent2 == null) {
            // Launching a solo intent or shortcut as fullscreen.
            launchAsFullscreenWithRemoteAnimation(pendingIntent1, fillInIntent1, shortcutInfo1,
                    options1, adapter, wct);
            return;
        }

        addActivityOptions(options1, mSideStage);
        if (shortcutInfo1 != null) {
            wct.startShortcut(mContext.getPackageName(), shortcutInfo1, options1);
        } else {
            wct.sendPendingIntent(pendingIntent1, fillInIntent1, options1);
            mSplitRequest = new SplitRequest(pendingIntent1.getIntent(),
                    pendingIntent2 != null ? pendingIntent2.getIntent() : null, splitPosition);
        }
        startWithLegacyTransition(wct, pendingIntent2, fillInIntent2, shortcutInfo2, options2,
                splitPosition, snapPosition, adapter, instanceId);
    }

    void startIntentAndTaskWithLegacyTransition(PendingIntent pendingIntent, Intent fillInIntent,
            @Nullable Bundle options1, int taskId, @Nullable Bundle options2,
            @SplitPosition int splitPosition, @PersistentSnapPosition int snapPosition,
            RemoteAnimationAdapter adapter, InstanceId instanceId) {
        final WindowContainerTransaction wct = new WindowContainerTransaction();
        if (options1 == null) options1 = new Bundle();
        if (taskId == INVALID_TASK_ID) {
            // Launching a solo intent as fullscreen.
            launchAsFullscreenWithRemoteAnimation(pendingIntent, fillInIntent, null, options1,
                    adapter, wct);
            return;
        }

        addActivityOptions(options1, mSideStage);
        wct.sendPendingIntent(pendingIntent, fillInIntent, options1);
        mSplitRequest = new SplitRequest(taskId, pendingIntent.getIntent(), splitPosition);
        startWithLegacyTransition(wct, taskId, options2, splitPosition, snapPosition, adapter,
                instanceId);
    }

    /** Starts a pair of shortcut and task using legacy transition. */
    void startShortcutAndTaskWithLegacyTransition(ShortcutInfo shortcutInfo,
            @Nullable Bundle options1, int taskId, @Nullable Bundle options2,
            @SplitPosition int splitPosition, @PersistentSnapPosition int snapPosition,
            RemoteAnimationAdapter adapter, InstanceId instanceId) {
        final WindowContainerTransaction wct = new WindowContainerTransaction();
        if (options1 == null) options1 = new Bundle();
        if (taskId == INVALID_TASK_ID) {
            // Launching a solo shortcut as fullscreen.
            launchAsFullscreenWithRemoteAnimation(null, null, shortcutInfo, options1, adapter, wct);
            return;
        }

        addActivityOptions(options1, mSideStage);
        wct.startShortcut(mContext.getPackageName(), shortcutInfo, options1);
        startWithLegacyTransition(wct, taskId, options2, splitPosition, snapPosition, adapter,
                instanceId);
    }

    private void launchAsFullscreenWithRemoteAnimation(@Nullable PendingIntent pendingIntent,
            @Nullable Intent fillInIntent, @Nullable ShortcutInfo shortcutInfo,
            @Nullable Bundle options, RemoteAnimationAdapter adapter,
            WindowContainerTransaction wct) {
        LegacyTransitions.ILegacyTransition transition =
                (transit, apps, wallpapers, nonApps, finishedCallback, t) -> {
                    if (apps == null || apps.length == 0) {
                        onRemoteAnimationFinished(apps);
                        t.apply();
                        try {
                            adapter.getRunner().onAnimationCancelled();
                        } catch (RemoteException e) {
                            Slog.e(TAG, "Error starting remote animation", e);
                        }
                        return;
                    }

                    for (int i = 0; i < apps.length; ++i) {
                        if (apps[i].mode == MODE_OPENING) {
                            t.show(apps[i].leash);
                        }
                    }
                    t.apply();

                    try {
                        adapter.getRunner().onAnimationStart(
                                transit, apps, wallpapers, nonApps, finishedCallback);
                    } catch (RemoteException e) {
                        Slog.e(TAG, "Error starting remote animation", e);
                    }
                };

        addActivityOptions(options, null /* launchTarget */);
        if (shortcutInfo != null) {
            wct.startShortcut(mContext.getPackageName(), shortcutInfo, options);
        } else if (pendingIntent != null) {
            wct.sendPendingIntent(pendingIntent, fillInIntent, options);
        } else {
            Slog.e(TAG, "Pending intent and shortcut are null is invalid case.");
        }
        mSyncQueue.queue(transition, WindowManager.TRANSIT_OPEN, wct);
    }

    private void startWithLegacyTransition(WindowContainerTransaction wct,
            @Nullable PendingIntent mainPendingIntent, @Nullable Intent mainFillInIntent,
            @Nullable ShortcutInfo mainShortcutInfo, @Nullable Bundle mainOptions,
            @SplitPosition int sidePosition, @PersistentSnapPosition int snapPosition,
            RemoteAnimationAdapter adapter, InstanceId instanceId) {
        startWithLegacyTransition(wct, INVALID_TASK_ID, mainPendingIntent, mainFillInIntent,
                mainShortcutInfo, mainOptions, sidePosition, snapPosition, adapter, instanceId);
    }

    private void startWithLegacyTransition(WindowContainerTransaction wct, int mainTaskId,
            @Nullable Bundle mainOptions, @SplitPosition int sidePosition,
            @PersistentSnapPosition int snapPosition, RemoteAnimationAdapter adapter,
            InstanceId instanceId) {
        startWithLegacyTransition(wct, mainTaskId, null /* mainPendingIntent */,
                null /* mainFillInIntent */, null /* mainShortcutInfo */, mainOptions, sidePosition,
                snapPosition, adapter, instanceId);
    }

    /**
     * @param wct        transaction to start the first task
     * @param instanceId if {@code null}, will not log. Otherwise it will be used in
     *                   {@link SplitscreenEventLogger#logEnter(float, int, int, int, int, boolean)}
     */
    private void startWithLegacyTransition(WindowContainerTransaction wct, int mainTaskId,
            @Nullable PendingIntent mainPendingIntent, @Nullable Intent mainFillInIntent,
            @Nullable ShortcutInfo mainShortcutInfo, @Nullable Bundle options,
            @SplitPosition int sidePosition, @PersistentSnapPosition int snapPosition,
            RemoteAnimationAdapter adapter, InstanceId instanceId) {
        if (!isSplitScreenVisible()) {
            exitSplitScreen(null /* childrenToTop */, EXIT_REASON_RECREATE_SPLIT);
        }

        // Init divider first to make divider leash for remote animation target.
        mSplitLayout.init();
        mSplitLayout.setDivideRatio(snapPosition);

        // Apply surface bounds before animation start.
        SurfaceControl.Transaction startT = mTransactionPool.acquire();
        updateSurfaceBounds(mSplitLayout, startT, false /* applyResizingOffset */);
        startT.apply();
        mTransactionPool.release(startT);

        // Set false to avoid record new bounds with old task still on top;
        mShouldUpdateRecents = false;
        mIsDividerRemoteAnimating = true;
        if (mSplitRequest == null) {
            mSplitRequest = new SplitRequest(mainTaskId,
                    mainPendingIntent != null ? mainPendingIntent.getIntent() : null,
                    sidePosition);
        }
        setSideStagePosition(sidePosition, wct);
        if (!mMainStage.isActive()) {
            mMainStage.activate(wct, false /* reparent */);
        }

        if (options == null) options = new Bundle();
        addActivityOptions(options, mMainStage);

        updateWindowBounds(mSplitLayout, wct);
        wct.reorder(mRootTaskInfo.token, true);
        setRootForceTranslucent(false, wct);

        // TODO(b/268008375): Merge APIs to start a split pair into one.
        if (mainTaskId != INVALID_TASK_ID) {
            options = wrapAsSplitRemoteAnimation(adapter, options);
            wct.startTask(mainTaskId, options);
            mSyncQueue.queue(wct);
        } else {
            if (mainShortcutInfo != null) {
                wct.startShortcut(mContext.getPackageName(), mainShortcutInfo, options);
            } else {
                wct.sendPendingIntent(mainPendingIntent, mainFillInIntent, options);
            }
            mSyncQueue.queue(wrapAsSplitRemoteAnimation(adapter), WindowManager.TRANSIT_OPEN, wct);
        }

        setEnterInstanceId(instanceId);
    }

    private Bundle wrapAsSplitRemoteAnimation(RemoteAnimationAdapter adapter, Bundle options) {
        final WindowContainerTransaction evictWct = new WindowContainerTransaction();
        if (isSplitScreenVisible()) {
            mMainStage.evictAllChildren(evictWct);
            mSideStage.evictAllChildren(evictWct);
        }

        IRemoteAnimationRunner wrapper = new IRemoteAnimationRunner.Stub() {
            @Override
            public void onAnimationStart(@WindowManager.TransitionOldType int transit,
                    RemoteAnimationTarget[] apps,
                    RemoteAnimationTarget[] wallpapers,
                    RemoteAnimationTarget[] nonApps,
                    final IRemoteAnimationFinishedCallback finishedCallback) {
                IRemoteAnimationFinishedCallback wrapCallback =
                        new IRemoteAnimationFinishedCallback.Stub() {
                            @Override
                            public void onAnimationFinished() throws RemoteException {
                                onRemoteAnimationFinishedOrCancelled(evictWct);
                                finishedCallback.onAnimationFinished();
                            }
                        };
                Transitions.setRunningRemoteTransitionDelegate(adapter.getCallingApplication());
                try {
                    adapter.getRunner().onAnimationStart(transit, apps, wallpapers,
                            ArrayUtils.appendElement(RemoteAnimationTarget.class, nonApps,
                                    getDividerBarLegacyTarget()), wrapCallback);
                } catch (RemoteException e) {
                    Slog.e(TAG, "Error starting remote animation", e);
                }
            }

            @Override
            public void onAnimationCancelled() {
                onRemoteAnimationFinishedOrCancelled(evictWct);
                setDividerVisibility(true, null);
                try {
                    adapter.getRunner().onAnimationCancelled();
                } catch (RemoteException e) {
                    Slog.e(TAG, "Error starting remote animation", e);
                }
            }
        };
        RemoteAnimationAdapter wrappedAdapter = new RemoteAnimationAdapter(
                wrapper, adapter.getDuration(), adapter.getStatusBarTransitionDelay());
        ActivityOptions activityOptions = ActivityOptions.fromBundle(options);
        activityOptions.update(ActivityOptions.makeRemoteAnimation(wrappedAdapter));
        return activityOptions.toBundle();
    }

    private LegacyTransitions.ILegacyTransition wrapAsSplitRemoteAnimation(
            RemoteAnimationAdapter adapter) {
        LegacyTransitions.ILegacyTransition transition =
                (transit, apps, wallpapers, nonApps, finishedCallback, t) -> {
                    if (apps == null || apps.length == 0) {
                        onRemoteAnimationFinished(apps);
                        t.apply();
                        try {
                            adapter.getRunner().onAnimationCancelled();
                        } catch (RemoteException e) {
                            Slog.e(TAG, "Error starting remote animation", e);
                        }
                        return;
                    }

                    // Wrap the divider bar into non-apps target to animate together.
                    nonApps = ArrayUtils.appendElement(RemoteAnimationTarget.class, nonApps,
                            getDividerBarLegacyTarget());

                    for (int i = 0; i < apps.length; ++i) {
                        if (apps[i].mode == MODE_OPENING) {
                            t.show(apps[i].leash);
                            // Reset the surface position of the opening app to prevent offset.
                            t.setPosition(apps[i].leash, 0, 0);
                        }
                    }
                    setDividerVisibility(true, t);
                    t.apply();

                    IRemoteAnimationFinishedCallback wrapCallback =
                            new IRemoteAnimationFinishedCallback.Stub() {
                                @Override
                                public void onAnimationFinished() throws RemoteException {
                                    onRemoteAnimationFinished(apps);
                                    finishedCallback.onAnimationFinished();
                                }
                            };
                    Transitions.setRunningRemoteTransitionDelegate(adapter.getCallingApplication());
                    try {
                        adapter.getRunner().onAnimationStart(
                                transit, apps, wallpapers, nonApps, wrapCallback);
                    } catch (RemoteException e) {
                        Slog.e(TAG, "Error starting remote animation", e);
                    }
                };

        return transition;
    }

    private void setEnterInstanceId(InstanceId instanceId) {
        if (instanceId != null) {
            mLogger.enterRequested(instanceId, ENTER_REASON_LAUNCHER);
        }
    }

    private void onRemoteAnimationFinishedOrCancelled(WindowContainerTransaction evictWct) {
        mIsDividerRemoteAnimating = false;
        mShouldUpdateRecents = true;
        clearRequestIfPresented();
        // If any stage has no child after animation finished, it means that split will display
        // nothing, such status will happen if task and intent is same app but not support
        // multi-instance, we should exit split and expand that app as full screen.
        if (mMainStage.getChildCount() == 0 || mSideStage.getChildCount() == 0) {
            mMainExecutor.execute(() ->
                    exitSplitScreen(mMainStage.getChildCount() == 0
                            ? mSideStage : mMainStage, EXIT_REASON_UNKNOWN));
            Log.w(TAG, splitFailureMessage("onRemoteAnimationFinishedOrCancelled",
                    "main or side stage was not populated."));
            handleUnsupportedSplitStart();
        } else {
            mSyncQueue.queue(evictWct);
            mSyncQueue.runInSync(t -> {
                updateSurfaceBounds(mSplitLayout, t, false /* applyResizingOffset */);
            });
        }
    }

    private void onRemoteAnimationFinished(RemoteAnimationTarget[] apps) {
        mIsDividerRemoteAnimating = false;
        mShouldUpdateRecents = true;
        clearRequestIfPresented();
        // If any stage has no child after finished animation, that side of the split will display
        // nothing. This might happen if starting the same app on the both sides while not
        // supporting multi-instance. Exit the split screen and expand that app to full screen.
        if (mMainStage.getChildCount() == 0 || mSideStage.getChildCount() == 0) {
            mMainExecutor.execute(() -> exitSplitScreen(mMainStage.getChildCount() == 0
                    ? mSideStage : mMainStage, EXIT_REASON_UNKNOWN));
            Log.w(TAG, splitFailureMessage("onRemoteAnimationFinished",
                    "main or side stage was not populated"));
            handleUnsupportedSplitStart();
            return;
        }

        final WindowContainerTransaction evictWct = new WindowContainerTransaction();
        mMainStage.evictNonOpeningChildren(apps, evictWct);
        mSideStage.evictNonOpeningChildren(apps, evictWct);
        mSyncQueue.queue(evictWct);
    }

    void prepareEvictNonOpeningChildTasks(@SplitPosition int position, RemoteAnimationTarget[] apps,
            WindowContainerTransaction wct) {
        if (position == mSideStagePosition) {
            mSideStage.evictNonOpeningChildren(apps, wct);
        } else {
            mMainStage.evictNonOpeningChildren(apps, wct);
        }
    }

    void prepareEvictInvisibleChildTasks(WindowContainerTransaction wct) {
        mMainStage.evictInvisibleChildren(wct);
        mSideStage.evictInvisibleChildren(wct);
    }

    Bundle resolveStartStage(@StageType int stage, @SplitPosition int position,
            @Nullable Bundle options, @Nullable WindowContainerTransaction wct) {
        switch (stage) {
            case STAGE_TYPE_UNDEFINED: {
                if (position != SPLIT_POSITION_UNDEFINED) {
                    if (isSplitScreenVisible()) {
                        // Use the stage of the specified position
                        options = resolveStartStage(
                                position == mSideStagePosition ? STAGE_TYPE_SIDE : STAGE_TYPE_MAIN,
                                position, options, wct);
                    } else {
                        // Use the side stage as default to active split screen
                        options = resolveStartStage(STAGE_TYPE_SIDE, position, options, wct);
                    }
                } else {
                    Slog.w(TAG,
                            "No stage type nor split position specified to resolve start stage");
                }
                break;
            }
            case STAGE_TYPE_SIDE: {
                if (position != SPLIT_POSITION_UNDEFINED) {
                    setSideStagePosition(position, wct);
                } else {
                    position = getSideStagePosition();
                }
                if (options == null) {
                    options = new Bundle();
                }
                updateActivityOptions(options, position);
                break;
            }
            case STAGE_TYPE_MAIN: {
                if (position != SPLIT_POSITION_UNDEFINED) {
                    // Set the side stage opposite of what we want to the main stage.
                    setSideStagePosition(reverseSplitPosition(position), wct);
                } else {
                    position = getMainStagePosition();
                }
                if (options == null) {
                    options = new Bundle();
                }
                updateActivityOptions(options, position);
                break;
            }
            default:
                throw new IllegalArgumentException("Unknown stage=" + stage);
        }

        return options;
    }

    @SplitPosition
    int getSideStagePosition() {
        return mSideStagePosition;
    }

    @SplitPosition
    int getMainStagePosition() {
        return reverseSplitPosition(mSideStagePosition);
    }

    int getTaskId(@SplitPosition int splitPosition) {
        if (splitPosition == SPLIT_POSITION_UNDEFINED) {
            return INVALID_TASK_ID;
        }

        return mSideStagePosition == splitPosition
                ? mSideStage.getTopVisibleChildTaskId()
                : mMainStage.getTopVisibleChildTaskId();
    }

    void switchSplitPosition(String reason) {
        ProtoLog.d(WM_SHELL_SPLIT_SCREEN, "switchSplitPosition");
        final SurfaceControl.Transaction t = mTransactionPool.acquire();
        mTempRect1.setEmpty();
        final StageTaskListener topLeftStage =
                mSideStagePosition == SPLIT_POSITION_TOP_OR_LEFT ? mSideStage : mMainStage;
        final SurfaceControl topLeftScreenshot = ScreenshotUtils.takeScreenshot(t,
                topLeftStage.mRootLeash, mTempRect1, Integer.MAX_VALUE - 1);
        final StageTaskListener bottomRightStage =
                mSideStagePosition == SPLIT_POSITION_TOP_OR_LEFT ? mMainStage : mSideStage;
        final SurfaceControl bottomRightScreenshot = ScreenshotUtils.takeScreenshot(t,
                bottomRightStage.mRootLeash, mTempRect1, Integer.MAX_VALUE - 1);
        mSplitLayout.splitSwitching(t, topLeftStage.mRootLeash, bottomRightStage.mRootLeash,
                insets -> {
                    WindowContainerTransaction wct = new WindowContainerTransaction();
                    setSideStagePosition(reverseSplitPosition(mSideStagePosition), wct);
                    mSyncQueue.queue(wct);
                    mSyncQueue.runInSync(st -> {
                        updateSurfaceBounds(mSplitLayout, st, false /* applyResizingOffset */);
                        st.setPosition(topLeftScreenshot, -insets.left, -insets.top);
                        st.setPosition(bottomRightScreenshot, insets.left, insets.top);

                        final ValueAnimator va = ValueAnimator.ofFloat(1, 0);
                        va.addUpdateListener(valueAnimator-> {
                            final float progress = (float) valueAnimator.getAnimatedValue();
                            t.setAlpha(topLeftScreenshot, progress);
                            t.setAlpha(bottomRightScreenshot, progress);
                            t.apply();
                        });
                        va.addListener(new AnimatorListenerAdapter() {
                            @Override
                            public void onAnimationEnd(
                                    @androidx.annotation.NonNull Animator animation) {
                                t.remove(topLeftScreenshot);
                                t.remove(bottomRightScreenshot);
                                t.apply();
                                mTransactionPool.release(t);
                            }
                        });
                        va.start();
                    });
                });

        ProtoLog.v(WM_SHELL_SPLIT_SCREEN, "Switch split position: %s", reason);
        mLogger.logSwap(getMainStagePosition(), mMainStage.getTopChildTaskUid(),
                getSideStagePosition(), mSideStage.getTopChildTaskUid(),
                mSplitLayout.isLeftRightSplit());
    }

    void setSideStagePosition(@SplitPosition int sideStagePosition,
            @Nullable WindowContainerTransaction wct) {
        setSideStagePosition(sideStagePosition, true /* updateBounds */, wct);
    }

    private void setSideStagePosition(@SplitPosition int sideStagePosition, boolean updateBounds,
            @Nullable WindowContainerTransaction wct) {
        if (mSideStagePosition == sideStagePosition) return;
        mSideStagePosition = sideStagePosition;
        sendOnStagePositionChanged();

        if (mSideStageListener.mVisible && updateBounds) {
            if (wct == null) {
                // onLayoutChanged builds/applies a wct with the contents of updateWindowBounds.
                onLayoutSizeChanged(mSplitLayout);
            } else {
                updateWindowBounds(mSplitLayout, wct);
                sendOnBoundsChanged();
            }
        }
    }

    void onKeyguardVisibilityChanged(boolean showing) {
        mKeyguardShowing = showing;
        if (!mMainStage.isActive()) {
            return;
        }
        ProtoLog.d(WM_SHELL_SPLIT_SCREEN, "onKeyguardVisibilityChanged: showing=%b", showing);
        setDividerVisibility(!mKeyguardShowing, null);
    }

    void onFinishedWakingUp() {
        ProtoLog.d(WM_SHELL_SPLIT_SCREEN, "onFinishedWakingUp");
        if (!mMainStage.isActive()) {
            return;
        }

        // Check if there's only one stage visible while keyguard occluded.
        final boolean mainStageVisible = mMainStage.mRootTaskInfo.isVisible;
        final boolean oneStageVisible =
                mMainStage.mRootTaskInfo.isVisible != mSideStage.mRootTaskInfo.isVisible;
        if (oneStageVisible && !ENABLE_SHELL_TRANSITIONS) {
            // Dismiss split because there's show-when-locked activity showing on top of keyguard.
            // Also make sure the task contains show-when-locked activity remains on top after split
            // dismissed.
            final StageTaskListener toTop = mainStageVisible ? mMainStage : mSideStage;
            exitSplitScreen(toTop, EXIT_REASON_SCREEN_LOCKED_SHOW_ON_TOP);
        }

        // Dismiss split if the flag record any side of stages.
        if (mTopStageAfterFoldDismiss != STAGE_TYPE_UNDEFINED) {
            if (ENABLE_SHELL_TRANSITIONS) {
                // Need manually clear here due to this transition might be aborted due to keyguard
                // on top and lead to no visible change.
                clearSplitPairedInRecents(EXIT_REASON_DEVICE_FOLDED);
                final WindowContainerTransaction wct = new WindowContainerTransaction();
                prepareExitSplitScreen(mTopStageAfterFoldDismiss, wct);
                mSplitTransitions.startDismissTransition(wct, this,
                        mTopStageAfterFoldDismiss, EXIT_REASON_DEVICE_FOLDED);
                setSplitsVisible(false);
            } else {
                exitSplitScreen(
                        mTopStageAfterFoldDismiss == STAGE_TYPE_MAIN ? mMainStage : mSideStage,
                        EXIT_REASON_DEVICE_FOLDED);
            }
            mTopStageAfterFoldDismiss = STAGE_TYPE_UNDEFINED;
        }
    }

    void exitSplitScreenOnHide(boolean exitSplitScreenOnHide) {
        mExitSplitScreenOnHide = exitSplitScreenOnHide;
    }

    /** Exits split screen with legacy transition */
    void exitSplitScreen(int toTopTaskId, @ExitReason int exitReason) {
        ProtoLog.d(WM_SHELL_SPLIT_SCREEN, "exitSplitScreen: topTaskId=%d reason=%s active=%b",
                toTopTaskId, exitReasonToString(exitReason), mMainStage.isActive());
        if (!mMainStage.isActive()) return;

        StageTaskListener childrenToTop = null;
        if (mMainStage.containsTask(toTopTaskId)) {
            childrenToTop = mMainStage;
        } else if (mSideStage.containsTask(toTopTaskId)) {
            childrenToTop = mSideStage;
        }

        final WindowContainerTransaction wct = new WindowContainerTransaction();
        if (childrenToTop != null) {
            childrenToTop.reorderChild(toTopTaskId, true /* onTop */, wct);
        }
        applyExitSplitScreen(childrenToTop, wct, exitReason);
    }

    /** Exits split screen with legacy transition */
    private void exitSplitScreen(@Nullable StageTaskListener childrenToTop,
            @ExitReason int exitReason) {
        ProtoLog.d(WM_SHELL_SPLIT_SCREEN, "exitSplitScreen: mainStageToTop=%b reason=%s active=%b",
                childrenToTop == mMainStage, exitReasonToString(exitReason), mMainStage.isActive());
        if (!mMainStage.isActive()) return;

        final WindowContainerTransaction wct = new WindowContainerTransaction();
        applyExitSplitScreen(childrenToTop, wct, exitReason);
    }

    private void applyExitSplitScreen(@Nullable StageTaskListener childrenToTop,
            WindowContainerTransaction wct, @ExitReason int exitReason) {
        ProtoLog.d(WM_SHELL_SPLIT_SCREEN, "applyExitSplitScreen: reason=%s",
                exitReasonToString(exitReason));
        if (!mMainStage.isActive() || mIsExiting) return;

        onSplitScreenExit();
        clearSplitPairedInRecents(exitReason);

        mShouldUpdateRecents = false;
        mIsDividerRemoteAnimating = false;
        mSplitRequest = null;

        mSplitLayout.getInvisibleBounds(mTempRect1);
        if (childrenToTop == null || childrenToTop.getTopVisibleChildTaskId() == INVALID_TASK_ID) {
            mSideStage.removeAllTasks(wct, false /* toTop */);
            mMainStage.deactivate(wct, false /* toTop */);
            wct.reorder(mRootTaskInfo.token, false /* onTop */);
            setRootForceTranslucent(true, wct);
            wct.setBounds(mSideStage.mRootTaskInfo.token, mTempRect1);
            onTransitionAnimationComplete();
        } else {
            // Expand to top side split as full screen for fading out decor animation and dismiss
            // another side split(Moving its children to bottom).
            mIsExiting = true;
            childrenToTop.resetBounds(wct);
            wct.reorder(childrenToTop.mRootTaskInfo.token, true);
        }
        wct.setReparentLeafTaskIfRelaunch(mRootTaskInfo.token,
                false /* reparentLeafTaskIfRelaunch */);
        mSyncQueue.queue(wct);
        mSyncQueue.runInSync(t -> {
            t.setWindowCrop(mMainStage.mRootLeash, null)
                    .setWindowCrop(mSideStage.mRootLeash, null);
            t.hide(mMainStage.mDimLayer).hide(mSideStage.mDimLayer);
            setDividerVisibility(false, t);

            if (childrenToTop == null) {
                t.setPosition(mSideStage.mRootLeash, mTempRect1.left, mTempRect1.right);
            } else {
                // In this case, exit still under progress, fade out the split decor after first WCT
                // done and do remaining WCT after animation finished.
                childrenToTop.fadeOutDecor(() -> {
                    WindowContainerTransaction finishedWCT = new WindowContainerTransaction();
                    mIsExiting = false;
                    mMainStage.deactivate(finishedWCT, childrenToTop == mMainStage /* toTop */);
                    mSideStage.removeAllTasks(finishedWCT, childrenToTop == mSideStage /* toTop */);
                    finishedWCT.reorder(mRootTaskInfo.token, false /* toTop */);
                    setRootForceTranslucent(true, finishedWCT);
                    finishedWCT.setBounds(mSideStage.mRootTaskInfo.token, mTempRect1);
                    mSyncQueue.queue(finishedWCT);
                    mSyncQueue.runInSync(at -> {
                        at.setPosition(mSideStage.mRootLeash, mTempRect1.left, mTempRect1.right);
                    });
                    onTransitionAnimationComplete();
                });
            }
        });

        // Log the exit
        if (childrenToTop != null) {
            logExitToStage(exitReason, childrenToTop == mMainStage);
        } else {
            logExit(exitReason);
        }
    }

    void dismissSplitScreen(int toTopTaskId, @ExitReason int exitReason) {
        if (!mMainStage.isActive()) return;
        final int stage = getStageOfTask(toTopTaskId);
        final WindowContainerTransaction wct = new WindowContainerTransaction();
        prepareExitSplitScreen(stage, wct);
        mSplitTransitions.startDismissTransition(wct, this, stage, exitReason);
    }

    /**
     * Overridden by child classes.
     */
    protected void onSplitScreenEnter() {
    }

    /**
     * Overridden by child classes.
     */
    protected void onSplitScreenExit() {
    }

    /**
     * Exits the split screen by finishing one of the tasks.
     */
    protected void exitStage(@SplitPosition int stageToClose) {
        ProtoLog.d(WM_SHELL_SPLIT_SCREEN, "exitStage: stageToClose=%d", stageToClose);
        mSplitLayout.flingDividerToDismiss(stageToClose == SPLIT_POSITION_BOTTOM_OR_RIGHT,
                EXIT_REASON_APP_FINISHED);
    }

    /**
     * Grants focus to the main or the side stages.
     */
    protected void grantFocusToStage(@SplitPosition int stageToFocus) {
        IActivityTaskManager activityTaskManagerService = IActivityTaskManager.Stub.asInterface(
                ServiceManager.getService(Context.ACTIVITY_TASK_SERVICE));
        try {
            activityTaskManagerService.setFocusedTask(getTaskId(stageToFocus));
        } catch (RemoteException | NullPointerException e) {
            ProtoLog.e(WM_SHELL_SPLIT_SCREEN,
                    "Unable to update focus on the chosen stage: %s", e.getMessage());
        }
    }

    protected void grantFocusToPosition(boolean leftOrTop) {
        int stageToFocus;
        if (mSideStagePosition == SPLIT_POSITION_BOTTOM_OR_RIGHT) {
            stageToFocus = leftOrTop ? getMainStagePosition() : getSideStagePosition();
        } else {
            stageToFocus = leftOrTop ? getSideStagePosition() : getMainStagePosition();
        }
        grantFocusToStage(stageToFocus);
    }

    private void clearRequestIfPresented() {
        ProtoLog.d(WM_SHELL_SPLIT_SCREEN, "clearRequestIfPresented");
        if (mSideStageListener.mVisible && mSideStageListener.mHasChildren
                && mMainStageListener.mVisible && mSideStageListener.mHasChildren) {
            mSplitRequest = null;
        }
    }

    /**
     * Returns whether the split pair in the recent tasks list should be broken.
     */
    private boolean shouldBreakPairedTaskInRecents(@ExitReason int exitReason) {
        switch (exitReason) {
            // One of the apps doesn't support MW
            case EXIT_REASON_APP_DOES_NOT_SUPPORT_MULTIWINDOW:
                // User has explicitly dragged the divider to dismiss split
            case EXIT_REASON_DRAG_DIVIDER:
                // Either of the split apps have finished
            case EXIT_REASON_APP_FINISHED:
                // One of the children enters PiP
            case EXIT_REASON_CHILD_TASK_ENTER_PIP:
                // One of the apps occludes lock screen.
            case EXIT_REASON_SCREEN_LOCKED_SHOW_ON_TOP:
                // User has unlocked the device after folded
            case EXIT_REASON_DEVICE_FOLDED:
                // The device is folded
            case EXIT_REASON_FULLSCREEN_SHORTCUT:
                // User has used a keyboard shortcut to go back to fullscreen from split
            case EXIT_REASON_DESKTOP_MODE:
                // One of the children enters desktop mode
            case EXIT_REASON_UNKNOWN:
                // Unknown reason
                return true;
            default:
                return false;
        }
    }

    void clearSplitPairedInRecents(@ExitReason int exitReason) {
        if (!shouldBreakPairedTaskInRecents(exitReason) || !mShouldUpdateRecents) return;

        ProtoLog.d(WM_SHELL_SPLIT_SCREEN, "clearSplitPairedInRecents: reason=%s",
                exitReasonToString(exitReason));
        mRecentTasks.ifPresent(recentTasks -> {
            // Notify recents if we are exiting in a way that breaks the pair, and disable further
            // updates to splits in the recents until we enter split again
            mMainStage.doForAllChildTasks(taskId -> recentTasks.removeSplitPair(taskId));
            mSideStage.doForAllChildTasks(taskId -> recentTasks.removeSplitPair(taskId));
        });
    }

    /**
     * Unlike exitSplitScreen, this takes a stagetype vs an actual stage-reference and populates
     * an existing WindowContainerTransaction (rather than applying immediately). This is intended
     * to be used when exiting split might be bundled with other window operations.
     */
    void prepareExitSplitScreen(@StageType int stageToTop,
            @NonNull WindowContainerTransaction wct) {
        if (!mMainStage.isActive()) return;
        ProtoLog.d(WM_SHELL_SPLIT_SCREEN, "prepareExitSplitScreen: stageToTop=%d", stageToTop);
        mSideStage.removeAllTasks(wct, stageToTop == STAGE_TYPE_SIDE);
        mMainStage.deactivate(wct, stageToTop == STAGE_TYPE_MAIN);
    }

    private void prepareEnterSplitScreen(WindowContainerTransaction wct) {
        ProtoLog.d(WM_SHELL_SPLIT_SCREEN, "prepareEnterSplitScreen");
        prepareEnterSplitScreen(wct, null /* taskInfo */, SPLIT_POSITION_UNDEFINED,
                !mIsDropEntering);
    }

    /**
     * Prepare transaction to active split screen. If there's a task indicated, the task will be put
     * into side stage.
     */
    void prepareEnterSplitScreen(WindowContainerTransaction wct,
            @Nullable ActivityManager.RunningTaskInfo taskInfo, @SplitPosition int startPosition,
            boolean resizeAnim) {
        ProtoLog.d(WM_SHELL_SPLIT_SCREEN, "prepareEnterSplitScreen: position=%d resize=%b",
                startPosition, resizeAnim);
        onSplitScreenEnter();
        // Preemptively reset the reparenting behavior if we know that we are entering, as starting
        // split tasks with activity trampolines can inadvertently trigger the task to be
        // reparented out of the split root mid-launch
        wct.setReparentLeafTaskIfRelaunch(mRootTaskInfo.token,
                false /* setReparentLeafTaskIfRelaunch */);
        if (isSplitActive()) {
            prepareBringSplit(wct, taskInfo, startPosition, resizeAnim);
        } else {
            prepareActiveSplit(wct, taskInfo, startPosition, resizeAnim);
        }
    }

    private void prepareBringSplit(WindowContainerTransaction wct,
            @Nullable ActivityManager.RunningTaskInfo taskInfo, @SplitPosition int startPosition,
            boolean resizeAnim) {
        ProtoLog.d(WM_SHELL_SPLIT_SCREEN, "prepareBringSplit: task=%d isSplitVisible=%b",
                taskInfo != null ? taskInfo.taskId : -1, isSplitScreenVisible());
        if (taskInfo != null) {
            wct.startTask(taskInfo.taskId,
                    resolveStartStage(STAGE_TYPE_UNDEFINED, startPosition, null, wct));
        }
        // If running background, we need to reparent current top visible task to main stage.
        if (!isSplitScreenVisible()) {
            // Ensure to evict old splitting tasks because the new split pair might be composed by
            // one of the splitting tasks, evicting the task when finishing entering transition
            // won't guarantee to put the task to the indicated new position.
            if (!mSkipEvictingMainStageChildren) {
                mMainStage.evictAllChildren(wct);
            }
            mMainStage.reparentTopTask(wct);
            prepareSplitLayout(wct, resizeAnim);
        }
    }

    private void prepareActiveSplit(WindowContainerTransaction wct,
            @Nullable ActivityManager.RunningTaskInfo taskInfo, @SplitPosition int startPosition,
            boolean resizeAnim) {
        ProtoLog.d(WM_SHELL_SPLIT_SCREEN, "prepareActiveSplit: task=%d isSplitVisible=%b",
                taskInfo != null ? taskInfo.taskId : -1, isSplitScreenVisible());
        if (!ENABLE_SHELL_TRANSITIONS) {
            // Legacy transition we need to create divider here, shell transition case we will
            // create it on #finishEnterSplitScreen
            mSplitLayout.init();
        } else {
            // We handle split visibility itself on shell transition, but sometimes we didn't
            // reset it correctly after dismiss by some reason, so just set invisible before active.
            setSplitsVisible(false);
        }
        if (taskInfo != null) {
            setSideStagePosition(startPosition, wct);
            mSideStage.addTask(taskInfo, wct);
        }
        mMainStage.activate(wct, true /* includingTopTask */);
        prepareSplitLayout(wct, resizeAnim);
    }

    private void prepareSplitLayout(WindowContainerTransaction wct, boolean resizeAnim) {
        ProtoLog.d(WM_SHELL_SPLIT_SCREEN, "prepareSplitLayout: resize=%b", resizeAnim);
        if (resizeAnim) {
            mSplitLayout.setDividerAtBorder(mSideStagePosition == SPLIT_POSITION_TOP_OR_LEFT);
        } else {
            mSplitLayout.resetDividerPosition();
        }
        updateWindowBounds(mSplitLayout, wct);
        if (resizeAnim) {
            // Reset its smallest width dp to avoid is change layout before it actually resized to
            // split bounds.
            wct.setSmallestScreenWidthDp(mMainStage.mRootTaskInfo.token,
                    SMALLEST_SCREEN_WIDTH_DP_UNDEFINED);
            mSplitLayout.getInvisibleBounds(mTempRect1);
            mSplitLayout.setTaskBounds(wct, mSideStage.mRootTaskInfo, mTempRect1);
        }
        wct.reorder(mRootTaskInfo.token, true);
        setRootForceTranslucent(false, wct);
    }

    void finishEnterSplitScreen(SurfaceControl.Transaction finishT) {
        ProtoLog.d(WM_SHELL_SPLIT_SCREEN, "finishEnterSplitScreen");
        mSplitLayout.update(null, true /* resetImePosition */);
        mMainStage.getSplitDecorManager().inflate(mContext, mMainStage.mRootLeash);
        mSideStage.getSplitDecorManager().inflate(mContext, mSideStage.mRootLeash);
        setDividerVisibility(true, finishT);
        // Ensure divider surface are re-parented back into the hierarchy at the end of the
        // transition. See Transition#buildFinishTransaction for more detail.
        finishT.reparent(mSplitLayout.getDividerLeash(), mRootTaskLeash);

        updateSurfaceBounds(mSplitLayout, finishT, false /* applyResizingOffset */);
        finishT.show(mRootTaskLeash);
        setSplitsVisible(true);
        mIsDropEntering = false;
        mSkipEvictingMainStageChildren = false;
        mSplitRequest = null;
        updateRecentTasksSplitPair();
        if (!mLogger.hasStartedSession()) {
            mLogger.logEnter(mSplitLayout.getDividerPositionAsFraction(),
                    getMainStagePosition(), mMainStage.getTopChildTaskUid(),
                    getSideStagePosition(), mSideStage.getTopChildTaskUid(),
                    mSplitLayout.isLeftRightSplit());
        }
    }

    void getStageBounds(Rect outTopOrLeftBounds, Rect outBottomOrRightBounds) {
        outTopOrLeftBounds.set(mSplitLayout.getBounds1());
        outBottomOrRightBounds.set(mSplitLayout.getBounds2());
    }

    @SplitPosition
    int getSplitPosition(int taskId) {
        if (mSideStage.getTopVisibleChildTaskId() == taskId) {
            return getSideStagePosition();
        } else if (mMainStage.getTopVisibleChildTaskId() == taskId) {
            return getMainStagePosition();
        }
        return SPLIT_POSITION_UNDEFINED;
    }

    private void addActivityOptions(Bundle opts, @Nullable StageTaskListener launchTarget) {
        ActivityOptions options = ActivityOptions.fromBundle(opts);
        if (launchTarget != null) {
            options.setLaunchRootTask(launchTarget.mRootTaskInfo.token);
        }
        // Put BAL flags to avoid activity start aborted. Otherwise, flows like shortcut to split
        // will be canceled.
        options.setPendingIntentBackgroundActivityStartMode(MODE_BACKGROUND_ACTIVITY_START_ALLOWED);
        options.setPendingIntentBackgroundActivityLaunchAllowedByPermission(true);

        // TODO (b/336477473): Disallow enter PiP when launching a task in split by default;
        //                     this might have to be changed as more split-to-pip cujs are defined.
        options.setDisallowEnterPictureInPictureWhileLaunching(true);
        opts.putAll(options.toBundle());
    }

    void updateActivityOptions(Bundle opts, @SplitPosition int position) {
        addActivityOptions(opts, position == mSideStagePosition ? mSideStage : mMainStage);
    }

    void registerSplitScreenListener(SplitScreen.SplitScreenListener listener) {
        if (mListeners.contains(listener)) return;
        mListeners.add(listener);
        sendStatusToListener(listener);
    }

    void unregisterSplitScreenListener(SplitScreen.SplitScreenListener listener) {
        mListeners.remove(listener);
    }

    void registerSplitSelectListener(SplitScreen.SplitSelectListener listener) {
        mSelectListeners.add(listener);
    }

    void unregisterSplitSelectListener(SplitScreen.SplitSelectListener listener) {
        mSelectListeners.remove(listener);
    }

    void sendStatusToListener(SplitScreen.SplitScreenListener listener) {
        listener.onStagePositionChanged(STAGE_TYPE_MAIN, getMainStagePosition());
        listener.onStagePositionChanged(STAGE_TYPE_SIDE, getSideStagePosition());
        listener.onSplitVisibilityChanged(isSplitScreenVisible());
        if (mSplitLayout != null) {
            listener.onSplitBoundsChanged(mSplitLayout.getRootBounds(), getMainStageBounds(),
                    getSideStageBounds());
        }
        mSideStage.onSplitScreenListenerRegistered(listener, STAGE_TYPE_SIDE);
        mMainStage.onSplitScreenListenerRegistered(listener, STAGE_TYPE_MAIN);
    }

    private void sendOnStagePositionChanged() {
        for (int i = mListeners.size() - 1; i >= 0; --i) {
            final SplitScreen.SplitScreenListener l = mListeners.get(i);
            l.onStagePositionChanged(STAGE_TYPE_MAIN, getMainStagePosition());
            l.onStagePositionChanged(STAGE_TYPE_SIDE, getSideStagePosition());
        }
    }

    private void sendOnBoundsChanged() {
        if (mSplitLayout == null) return;
        for (int i = mListeners.size() - 1; i >= 0; --i) {
            mListeners.get(i).onSplitBoundsChanged(mSplitLayout.getRootBounds(),
                    getMainStageBounds(), getSideStageBounds());
        }
    }

    private void onStageChildTaskStatusChanged(StageListenerImpl stageListener, int taskId,
            boolean present, boolean visible) {
        int stage;
        if (present) {
            stage = stageListener == mSideStageListener ? STAGE_TYPE_SIDE : STAGE_TYPE_MAIN;
        } else {
            // No longer on any stage
            stage = STAGE_TYPE_UNDEFINED;
        }
        if (stage == STAGE_TYPE_MAIN) {
            mLogger.logMainStageAppChange(getMainStagePosition(), mMainStage.getTopChildTaskUid(),
                    mSplitLayout.isLeftRightSplit());
        } else {
            mLogger.logSideStageAppChange(getSideStagePosition(), mSideStage.getTopChildTaskUid(),
                    mSplitLayout.isLeftRightSplit());
        }
        if (present) {
            updateRecentTasksSplitPair();
        }

        for (int i = mListeners.size() - 1; i >= 0; --i) {
            mListeners.get(i).onTaskStageChanged(taskId, stage, visible);
        }
    }

    private void updateRecentTasksSplitPair() {
        // Preventing from single task update while processing recents.
        if (!mShouldUpdateRecents || !mPausingTasks.isEmpty()) {
            return;
        }
        mRecentTasks.ifPresent(recentTasks -> {
            Rect topLeftBounds = mSplitLayout.getBounds1();
            Rect bottomRightBounds = mSplitLayout.getBounds2();
            int mainStageTopTaskId = mMainStage.getTopVisibleChildTaskId();
            int sideStageTopTaskId = mSideStage.getTopVisibleChildTaskId();
            boolean sideStageTopLeft = mSideStagePosition == SPLIT_POSITION_TOP_OR_LEFT;
            int leftTopTaskId;
            int rightBottomTaskId;
            if (sideStageTopLeft) {
                leftTopTaskId = sideStageTopTaskId;
                rightBottomTaskId = mainStageTopTaskId;
            } else {
                leftTopTaskId = mainStageTopTaskId;
                rightBottomTaskId = sideStageTopTaskId;
            }
            SplitBounds splitBounds = new SplitBounds(topLeftBounds, bottomRightBounds,
                    leftTopTaskId, rightBottomTaskId, mSplitLayout.calculateCurrentSnapPosition());
            if (mainStageTopTaskId != INVALID_TASK_ID && sideStageTopTaskId != INVALID_TASK_ID) {
                // Update the pair for the top tasks
                boolean added = recentTasks.addSplitPair(mainStageTopTaskId, sideStageTopTaskId,
                        splitBounds);
                if (added) {
                    ProtoLog.d(WM_SHELL_SPLIT_SCREEN,
                            "updateRecentTasksSplitPair: adding split pair ltTask=%d rbTask=%d",
                            leftTopTaskId, rightBottomTaskId);
                }
            }
        });
    }

    private void sendSplitVisibilityChanged() {
        ProtoLog.d(WM_SHELL_SPLIT_SCREEN, "sendSplitVisibilityChanged: dividerVisible=%b",
                mDividerVisible);
        for (int i = mListeners.size() - 1; i >= 0; --i) {
            final SplitScreen.SplitScreenListener l = mListeners.get(i);
            l.onSplitVisibilityChanged(mDividerVisible);
        }
        sendOnBoundsChanged();
    }

    @Override
    @CallSuper
    public void onTaskAppeared(ActivityManager.RunningTaskInfo taskInfo, SurfaceControl leash) {
        if (mRootTaskInfo != null || taskInfo.hasParentTask()) {
            throw new IllegalArgumentException(this + "\n Unknown task appeared: " + taskInfo);
        }

        ProtoLog.d(WM_SHELL_SPLIT_SCREEN, "onTaskAppeared: task=%s", taskInfo);
        mRootTaskInfo = taskInfo;
        mRootTaskLeash = leash;

        if (mSplitLayout == null) {
            mSplitLayout = new SplitLayout(TAG + "SplitDivider", mContext,
                    mRootTaskInfo.configuration, this, mParentContainerCallbacks,
                    mDisplayController, mDisplayImeController, mTaskOrganizer,
                    PARALLAX_ALIGN_CENTER /* parallaxType */);
            mDisplayInsetsController.addInsetsChangedListener(mDisplayId, mSplitLayout);
        }

        onRootTaskAppeared();
    }

    @Override
    @CallSuper
    public void onTaskInfoChanged(ActivityManager.RunningTaskInfo taskInfo) {
        if (mRootTaskInfo == null || mRootTaskInfo.taskId != taskInfo.taskId) {
            throw new IllegalArgumentException(this + "\n Unknown task info changed: " + taskInfo);
        }
        mWindowDecorViewModel.ifPresent(viewModel -> viewModel.onTaskInfoChanged(taskInfo));
        mRootTaskInfo = taskInfo;
        if (mSplitLayout != null
                && mSplitLayout.updateConfiguration(mRootTaskInfo.configuration)
                && mMainStage.isActive()) {
            ProtoLog.d(WM_SHELL_SPLIT_SCREEN, "onTaskInfoChanged: task=%d updating",
                    taskInfo.taskId);
            // Clear the divider remote animating flag as the divider will be re-rendered to apply
            // the new rotation config.  Don't reset the IME state since those updates are not in
            // sync with task info changes.
            mIsDividerRemoteAnimating = false;
            mSplitLayout.update(null /* t */, false /* resetImePosition */);
            onLayoutSizeChanged(mSplitLayout);
        }
    }

    @Override
    @CallSuper
    public void onTaskVanished(ActivityManager.RunningTaskInfo taskInfo) {
        ProtoLog.d(WM_SHELL_SPLIT_SCREEN, "onTaskVanished: task=%s", taskInfo);
        if (mRootTaskInfo == null) {
            throw new IllegalArgumentException(this + "\n Unknown task vanished: " + taskInfo);
        }

        onRootTaskVanished();

        if (mSplitLayout != null) {
            mSplitLayout.release();
            mSplitLayout = null;
        }

        mRootTaskInfo = null;
        mRootTaskLeash = null;
        mIsRootTranslucent = false;
    }


    @VisibleForTesting
    void onRootTaskAppeared() {
        ProtoLog.d(WM_SHELL_SPLIT_SCREEN, "onRootTaskAppeared: rootTask=%s mainRoot=%b sideRoot=%b",
                mRootTaskInfo, mMainStageListener.mHasRootTask, mSideStageListener.mHasRootTask);
        // Wait unit all root tasks appeared.
        if (mRootTaskInfo == null
                || !mMainStageListener.mHasRootTask
                || !mSideStageListener.mHasRootTask) {
            return;
        }

        final WindowContainerTransaction wct = new WindowContainerTransaction();
        wct.reparent(mMainStage.mRootTaskInfo.token, mRootTaskInfo.token, true);
        wct.reparent(mSideStage.mRootTaskInfo.token, mRootTaskInfo.token, true);
        // Make the stages adjacent to each other so they occlude what's behind them.
        wct.setAdjacentRoots(mMainStage.mRootTaskInfo.token, mSideStage.mRootTaskInfo.token);
        setRootForceTranslucent(true, wct);
        mSplitLayout.getInvisibleBounds(mTempRect1);
        wct.setBounds(mSideStage.mRootTaskInfo.token, mTempRect1);
        mSyncQueue.queue(wct);
        mSyncQueue.runInSync(t -> {
            t.setPosition(mSideStage.mRootLeash, mTempRect1.left, mTempRect1.top);
        });
        mLaunchAdjacentController.setLaunchAdjacentRoot(mSideStage.mRootTaskInfo.token);
    }

    /** Callback when split roots have child task appeared under it, this is a little different from
     * #onStageHasChildrenChanged because this would be called every time child task appeared.
     * NOTICE: This only be called on legacy transition. */
    private void onChildTaskAppeared(StageListenerImpl stageListener, int taskId) {
        ProtoLog.d(WM_SHELL_SPLIT_SCREEN, "onChildTaskAppeared: isMainStage=%b task=%d",
                stageListener == mMainStageListener, taskId);
        // Handle entering split screen while there is a split pair running in the background.
        if (stageListener == mSideStageListener && !isSplitScreenVisible() && isSplitActive()
                && mSplitRequest == null) {
            final WindowContainerTransaction wct = new WindowContainerTransaction();
            prepareEnterSplitScreen(wct);
            mMainStage.evictAllChildren(wct);
            mSideStage.evictOtherChildren(wct, taskId);

            mSyncQueue.queue(wct);
            mSyncQueue.runInSync(t -> {
                if (mIsDropEntering) {
                    updateSurfaceBounds(mSplitLayout, t, false /* applyResizingOffset */);
                    mIsDropEntering = false;
                    mSkipEvictingMainStageChildren = false;
                } else {
                    mShowDecorImmediately = true;
                    mSplitLayout.flingDividerToCenter(/*finishCallback*/ null);
                }
            });
        }
    }

    private void onRootTaskVanished() {
        ProtoLog.d(WM_SHELL_SPLIT_SCREEN, "onRootTaskVanished");
        final WindowContainerTransaction wct = new WindowContainerTransaction();
        mLaunchAdjacentController.clearLaunchAdjacentRoot();
        applyExitSplitScreen(null /* childrenToTop */, wct, EXIT_REASON_ROOT_TASK_VANISHED);
        mDisplayInsetsController.removeInsetsChangedListener(mDisplayId, mSplitLayout);
    }

    private void setRootForceTranslucent(boolean translucent, WindowContainerTransaction wct) {
        if (mIsRootTranslucent == translucent) return;

        mIsRootTranslucent = translucent;
        wct.setForceTranslucent(mRootTaskInfo.token, translucent);
    }

    /** Callback when split roots visiblility changed.
     * NOTICE: This only be called on legacy transition. */
    private void onStageVisibilityChanged(StageListenerImpl stageListener) {
        // If split didn't active, just ignore this callback because we should already did these
        // on #applyExitSplitScreen.
        if (!isSplitActive()) {
            return;
        }

        final boolean sideStageVisible = mSideStageListener.mVisible;
        final boolean mainStageVisible = mMainStageListener.mVisible;

        // Wait for both stages having the same visibility to prevent causing flicker.
        if (mainStageVisible != sideStageVisible) {
            return;
        }

        // TODO Protolog

        // Check if it needs to dismiss split screen when both stage invisible.
        if (!mainStageVisible && mExitSplitScreenOnHide) {
            exitSplitScreen(null /* childrenToTop */, EXIT_REASON_RETURN_HOME);
            return;
        }

        final WindowContainerTransaction wct = new WindowContainerTransaction();
        if (!mainStageVisible) {
            // Split entering background.
            wct.setReparentLeafTaskIfRelaunch(mRootTaskInfo.token,
                    true /* setReparentLeafTaskIfRelaunch */);
            setRootForceTranslucent(true, wct);
        } else {
            clearRequestIfPresented();
            wct.setReparentLeafTaskIfRelaunch(mRootTaskInfo.token,
                    false /* setReparentLeafTaskIfRelaunch */);
            setRootForceTranslucent(false, wct);
        }

        mSyncQueue.queue(wct);
        setDividerVisibility(mainStageVisible, null);
    }

    // Set divider visibility flag and try to apply it, the param transaction is used to apply.
    // See applyDividerVisibility for more detail.
    private void setDividerVisibility(boolean visible, @Nullable SurfaceControl.Transaction t) {
        if (visible == mDividerVisible) {
            return;
        }

        ProtoLog.d(WM_SHELL_SPLIT_SCREEN,
                "setDividerVisibility: visible=%b keyguardShowing=%b dividerAnimating=%b caller=%s",
                visible, mKeyguardShowing, mIsDividerRemoteAnimating, Debug.getCaller());

        // Defer showing divider bar after keyguard dismissed, so it won't interfere with keyguard
        // dismissing animation.
        if (visible && mKeyguardShowing) {
            ProtoLog.d(WM_SHELL_SPLIT_SCREEN,
                    "   Defer showing divider bar due to keyguard showing.");
            return;
        }

        mDividerVisible = visible;
        sendSplitVisibilityChanged();

        if (mIsDividerRemoteAnimating) {
            ProtoLog.d(WM_SHELL_SPLIT_SCREEN,
                    "   Skip animating divider bar due to it's remote animating.");
            return;
        }

        applyDividerVisibility(t);
    }

    // Apply divider visibility by current visibility flag. If param transaction is non-null, it
    // will apply by that transaction, if it is null and visible, it will run a fade-in animation,
    // otherwise hide immediately.
    private void applyDividerVisibility(@Nullable SurfaceControl.Transaction t) {
        final SurfaceControl dividerLeash = mSplitLayout.getDividerLeash();
        if (dividerLeash == null) {
            ProtoLog.d(WM_SHELL_SPLIT_SCREEN,
                    "   Skip animating divider bar due to divider leash not ready.");
            return;
        }
        if (mIsDividerRemoteAnimating) {
            ProtoLog.d(WM_SHELL_SPLIT_SCREEN,
                    "   Skip animating divider bar due to it's remote animating.");
            return;
        }

        if (mDividerFadeInAnimator != null && mDividerFadeInAnimator.isRunning()) {
            mDividerFadeInAnimator.cancel();
        }

        mSplitLayout.getRefDividerBounds(mTempRect1);
        if (t != null) {
            t.setVisibility(dividerLeash, mDividerVisible);
            t.setLayer(dividerLeash, Integer.MAX_VALUE);
            t.setPosition(dividerLeash, mTempRect1.left, mTempRect1.top);
        } else if (mDividerVisible) {
            final SurfaceControl.Transaction transaction = mTransactionPool.acquire();
            mDividerFadeInAnimator = ValueAnimator.ofFloat(0f, 1f);
            mDividerFadeInAnimator.addUpdateListener(animation -> {
                if (dividerLeash == null || !dividerLeash.isValid()) {
                    mDividerFadeInAnimator.cancel();
                    return;
                }
                transaction.setFrameTimelineVsync(Choreographer.getInstance().getVsyncId());
                transaction.setAlpha(dividerLeash, (float) animation.getAnimatedValue());
                transaction.apply();
            });
            mDividerFadeInAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationStart(Animator animation) {
                    if (dividerLeash == null || !dividerLeash.isValid()) {
                        mDividerFadeInAnimator.cancel();
                        return;
                    }
                    mSplitLayout.getRefDividerBounds(mTempRect1);
                    transaction.show(dividerLeash);
                    transaction.setAlpha(dividerLeash, 0);
                    transaction.setLayer(dividerLeash, Integer.MAX_VALUE);
                    transaction.setPosition(dividerLeash, mTempRect1.left, mTempRect1.top);
                    transaction.apply();
                }

                @Override
                public void onAnimationEnd(Animator animation) {
                    if (dividerLeash != null && dividerLeash.isValid()) {
                        transaction.setAlpha(dividerLeash, 1);
                        transaction.apply();
                    }
                    mTransactionPool.release(transaction);
                    mDividerFadeInAnimator = null;
                }
            });

            mDividerFadeInAnimator.start();
        } else {
            final SurfaceControl.Transaction transaction = mTransactionPool.acquire();
            transaction.hide(dividerLeash);
            transaction.apply();
            mTransactionPool.release(transaction);
        }
    }

    /** Callback when split roots have child or haven't under it.
     * NOTICE: This only be called on legacy transition. */
    private void onStageHasChildrenChanged(StageListenerImpl stageListener) {
        ProtoLog.d(WM_SHELL_SPLIT_SCREEN, "onStageHasChildrenChanged: isMainStage=%b",
                stageListener == mMainStageListener);
        final boolean hasChildren = stageListener.mHasChildren;
        final boolean isSideStage = stageListener == mSideStageListener;
        if (!hasChildren && !mIsExiting && mMainStage.isActive()) {
            if (isSideStage && mMainStageListener.mVisible) {
                // Exit to main stage if side stage no longer has children.
                mSplitLayout.flingDividerToDismiss(
                        mSideStagePosition == SPLIT_POSITION_BOTTOM_OR_RIGHT,
                        EXIT_REASON_APP_FINISHED);
            } else if (!isSideStage && mSideStageListener.mVisible) {
                // Exit to side stage if main stage no longer has children.
                mSplitLayout.flingDividerToDismiss(
                        mSideStagePosition != SPLIT_POSITION_BOTTOM_OR_RIGHT,
                        EXIT_REASON_APP_FINISHED);
            } else if (!isSplitScreenVisible() && mSplitRequest == null) {
                // Dismiss split screen in the background once any sides of the split become empty.
                exitSplitScreen(null /* childrenToTop */, EXIT_REASON_APP_FINISHED);
            }
        } else if (isSideStage && hasChildren && !mMainStage.isActive()) {
            final WindowContainerTransaction wct = new WindowContainerTransaction();
            prepareEnterSplitScreen(wct);

            mSyncQueue.queue(wct);
            mSyncQueue.runInSync(t -> {
                if (mIsDropEntering) {
                    updateSurfaceBounds(mSplitLayout, t, false /* applyResizingOffset */);
                    mIsDropEntering = false;
                    mSkipEvictingMainStageChildren = false;
                } else {
                    mShowDecorImmediately = true;
                    mSplitLayout.flingDividerToCenter(/*finishCallback*/ null);
                }
            });
        }
        if (mMainStageListener.mHasChildren && mSideStageListener.mHasChildren) {
            mShouldUpdateRecents = true;
            clearRequestIfPresented();
            updateRecentTasksSplitPair();

            if (!mLogger.hasStartedSession()) {
                if (!mLogger.hasValidEnterSessionId()) {
                    mLogger.enterRequested(null /*enterSessionId*/, ENTER_REASON_MULTI_INSTANCE);
                }
                mLogger.logEnter(mSplitLayout.getDividerPositionAsFraction(),
                        getMainStagePosition(), mMainStage.getTopChildTaskUid(),
                        getSideStagePosition(), mSideStage.getTopChildTaskUid(),
                        mSplitLayout.isLeftRightSplit());
            }
        }
    }

    @Override
    public void onSnappedToDismiss(boolean bottomOrRight, @ExitReason int exitReason) {
        ProtoLog.d(WM_SHELL_SPLIT_SCREEN, "onSnappedToDismiss: bottomOrRight=%b reason=%s",
                bottomOrRight, exitReasonToString(exitReason));
        final boolean mainStageToTop =
                bottomOrRight ? mSideStagePosition == SPLIT_POSITION_BOTTOM_OR_RIGHT
                        : mSideStagePosition == SPLIT_POSITION_TOP_OR_LEFT;
        final StageTaskListener toTopStage = mainStageToTop ? mMainStage : mSideStage;
        if (!ENABLE_SHELL_TRANSITIONS) {
            exitSplitScreen(toTopStage, exitReason);
            return;
        }

        final int dismissTop = mainStageToTop ? STAGE_TYPE_MAIN : STAGE_TYPE_SIDE;
        final WindowContainerTransaction wct = new WindowContainerTransaction();
        toTopStage.resetBounds(wct);
        prepareExitSplitScreen(dismissTop, wct);
        if (mRootTaskInfo != null) {
            wct.setDoNotPip(mRootTaskInfo.token);
        }
        mSplitTransitions.startDismissTransition(wct, this, dismissTop, EXIT_REASON_DRAG_DIVIDER);
    }

    @Override
    public void onDoubleTappedDivider() {
        switchSplitPosition("double tap");
    }

    @Override
    public void onLayoutPositionChanging(SplitLayout layout) {
        final SurfaceControl.Transaction t = mTransactionPool.acquire();
        t.setFrameTimelineVsync(Choreographer.getInstance().getVsyncId());
        updateSurfaceBounds(layout, t, false /* applyResizingOffset */);
        t.apply();
        mTransactionPool.release(t);
    }

    @Override
    public void onLayoutSizeChanging(SplitLayout layout, int offsetX, int offsetY,
            boolean shouldUseParallaxEffect) {
        final SurfaceControl.Transaction t = mTransactionPool.acquire();
        t.setFrameTimelineVsync(Choreographer.getInstance().getVsyncId());
        updateSurfaceBounds(layout, t, shouldUseParallaxEffect);
        getMainStageBounds(mTempRect1);
        getSideStageBounds(mTempRect2);
        // TODO (b/307490004): "commonColor" below is a temporary fix to ensure the colors on both
        //  sides match. When b/307490004 is fixed, this code can be reverted.
        float[] commonColor = getResizingBackgroundColor(mSideStage.mRootTaskInfo).getComponents();
        mMainStage.onResizing(
                mTempRect1, mTempRect2, t, offsetX, offsetY, mShowDecorImmediately, commonColor);
        mSideStage.onResizing(
                mTempRect2, mTempRect1, t, offsetX, offsetY, mShowDecorImmediately, commonColor);
        t.apply();
        mTransactionPool.release(t);
    }

    @Override
    public void onLayoutSizeChanged(SplitLayout layout) {
        ProtoLog.d(WM_SHELL_SPLIT_SCREEN, "onLayoutSizeChanged");
        // Reset this flag every time onLayoutSizeChanged.
        mShowDecorImmediately = false;

        final WindowContainerTransaction wct = new WindowContainerTransaction();
        boolean sizeChanged = updateWindowBounds(layout, wct);
        if (!sizeChanged) {
            // We still need to resize on decor for ensure all current status clear.
            final SurfaceControl.Transaction t = mTransactionPool.acquire();
            mMainStage.onResized(t);
            mSideStage.onResized(t);
            mTransactionPool.release(t);
            return;
        }

        sendOnBoundsChanged();
        if (ENABLE_SHELL_TRANSITIONS) {
            mSplitLayout.setDividerInteractive(false, false, "onSplitResizeStart");
            mSplitTransitions.startResizeTransition(wct, this, (aborted) -> {
                        mSplitLayout.setDividerInteractive(true, false, "onSplitResizeConsumed");
                    }, (finishWct, t) -> {
                        mSplitLayout.setDividerInteractive(true, false, "onSplitResizeFinish");
                    }, mMainStage.getSplitDecorManager(), mSideStage.getSplitDecorManager());
        } else {
            // Only need screenshot for legacy case because shell transition should screenshot
            // itself during transition.
            final SurfaceControl.Transaction startT = mTransactionPool.acquire();
            mMainStage.screenshotIfNeeded(startT);
            mSideStage.screenshotIfNeeded(startT);
            mTransactionPool.release(startT);

            mSyncQueue.queue(wct);
            mSyncQueue.runInSync(t -> {
                updateSurfaceBounds(layout, t, false /* applyResizingOffset */);
                mMainStage.onResized(t);
                mSideStage.onResized(t);
            });
        }
        mLogger.logResize(mSplitLayout.getDividerPositionAsFraction());
    }

    /**
     * @return {@code true} if we should create a left-right split, {@code false} if we should
     * create a top-bottom split.
     */
    boolean isLeftRightSplit() {
        return mSplitLayout != null && mSplitLayout.isLeftRightSplit();
    }

    /**
     * Populates `wct` with operations that match the split windows to the current layout.
     * To match relevant surfaces, make sure to call updateSurfaceBounds after `wct` is applied
     *
     * @return true if stage bounds actually .
     */
    private boolean updateWindowBounds(SplitLayout layout, WindowContainerTransaction wct) {
        final StageTaskListener topLeftStage =
                mSideStagePosition == SPLIT_POSITION_TOP_OR_LEFT ? mSideStage : mMainStage;
        final StageTaskListener bottomRightStage =
                mSideStagePosition == SPLIT_POSITION_TOP_OR_LEFT ? mMainStage : mSideStage;
        boolean updated = layout.applyTaskChanges(wct, topLeftStage.mRootTaskInfo,
                bottomRightStage.mRootTaskInfo);
        ProtoLog.d(WM_SHELL_SPLIT_SCREEN, "updateWindowBounds: topLeftStage=%s bottomRightStage=%s",
                layout.getBounds1(), layout.getBounds2());
        return updated;
    }

    void updateSurfaceBounds(@Nullable SplitLayout layout, @NonNull SurfaceControl.Transaction t,
            boolean applyResizingOffset) {
        final StageTaskListener topLeftStage =
                mSideStagePosition == SPLIT_POSITION_TOP_OR_LEFT ? mSideStage : mMainStage;
        final StageTaskListener bottomRightStage =
                mSideStagePosition == SPLIT_POSITION_TOP_OR_LEFT ? mMainStage : mSideStage;
        (layout != null ? layout : mSplitLayout).applySurfaceChanges(t, topLeftStage.mRootLeash,
                bottomRightStage.mRootLeash, topLeftStage.mDimLayer, bottomRightStage.mDimLayer,
                applyResizingOffset);
        ProtoLog.d(WM_SHELL_SPLIT_SCREEN,
                "updateSurfaceBounds: topLeftStage=%s bottomRightStage=%s",
                layout.getBounds1(), layout.getBounds2());
    }

    @Override
    public int getSplitItemPosition(WindowContainerToken token) {
        if (token == null) {
            return SPLIT_POSITION_UNDEFINED;
        }

        if (mMainStage.containsToken(token)) {
            return getMainStagePosition();
        } else if (mSideStage.containsToken(token)) {
            return getSideStagePosition();
        }

        return SPLIT_POSITION_UNDEFINED;
    }

    /**
     * Returns the {@link StageType} where {@param token} is being used
     * {@link SplitScreen#STAGE_TYPE_UNDEFINED} otherwise
     */
    @StageType
    public int getSplitItemStage(@Nullable WindowContainerToken token) {
        if (token == null) {
            return STAGE_TYPE_UNDEFINED;
        }

        if (mMainStage.containsToken(token)) {
            return STAGE_TYPE_MAIN;
        } else if (mSideStage.containsToken(token)) {
            return STAGE_TYPE_SIDE;
        }

        return STAGE_TYPE_UNDEFINED;
    }

    @Override
    public void setLayoutOffsetTarget(int offsetX, int offsetY, SplitLayout layout) {
        ProtoLog.d(WM_SHELL_SPLIT_SCREEN, "setLayoutOffsetTarget: x=%d y=%d",
                offsetX, offsetY);
        final StageTaskListener topLeftStage =
                mSideStagePosition == SPLIT_POSITION_TOP_OR_LEFT ? mSideStage : mMainStage;
        final StageTaskListener bottomRightStage =
                mSideStagePosition == SPLIT_POSITION_TOP_OR_LEFT ? mMainStage : mSideStage;
        final WindowContainerTransaction wct = new WindowContainerTransaction();
        layout.applyLayoutOffsetTarget(wct, offsetX, offsetY, topLeftStage.mRootTaskInfo,
                bottomRightStage.mRootTaskInfo);
        mTaskOrganizer.applyTransaction(wct);
    }

    public void onDisplayAdded(int displayId) {
        if (displayId != DEFAULT_DISPLAY) {
            return;
        }
        ProtoLog.d(WM_SHELL_SPLIT_SCREEN, "onDisplayAdded: display=%d", displayId);
        mDisplayController.addDisplayChangingController(this::onDisplayChange);
    }

    /**
     * Update surfaces of the split screen layout based on the current state
     * @param transaction to write the updates to
     */
    public void updateSurfaces(SurfaceControl.Transaction transaction) {
        updateSurfaceBounds(mSplitLayout, transaction, /* applyResizingOffset */ false);
        mSplitLayout.update(transaction, true /* resetImePosition */);
    }

    private void onDisplayChange(int displayId, int fromRotation, int toRotation,
            @Nullable DisplayAreaInfo newDisplayAreaInfo, WindowContainerTransaction wct) {
        if (displayId != DEFAULT_DISPLAY || !mMainStage.isActive()) {
            return;
        }

        ProtoLog.d(WM_SHELL_SPLIT_SCREEN,
                "onDisplayChange: display=%d fromRot=%d toRot=%d config=%s",
                displayId, fromRotation, toRotation,
                newDisplayAreaInfo != null ? newDisplayAreaInfo.configuration : null);
        mSplitLayout.rotateTo(toRotation);
        if (newDisplayAreaInfo != null) {
            mSplitLayout.updateConfiguration(newDisplayAreaInfo.configuration);
        }
        updateWindowBounds(mSplitLayout, wct);
        sendOnBoundsChanged();
    }

    @VisibleForTesting
    void onFoldedStateChanged(boolean folded) {
        ProtoLog.d(WM_SHELL_SPLIT_SCREEN, "onFoldedStateChanged: folded=%b", folded);
        mTopStageAfterFoldDismiss = STAGE_TYPE_UNDEFINED;
        if (!folded) return;

        if (!isSplitActive() || !isSplitScreenVisible()) return;

        // To avoid split dismiss when user fold the device and unfold to use later, we only
        // record the flag here and try to dismiss on wakeUp callback to ensure split dismiss
        // when user interact on phone folded.
        if (mMainStage.isFocused()) {
            mTopStageAfterFoldDismiss = STAGE_TYPE_MAIN;
        } else if (mSideStage.isFocused()) {
            mTopStageAfterFoldDismiss = STAGE_TYPE_SIDE;
        }
    }

    private Rect getSideStageBounds() {
        return mSideStagePosition == SPLIT_POSITION_TOP_OR_LEFT
                ? mSplitLayout.getBounds1() : mSplitLayout.getBounds2();
    }

    private Rect getMainStageBounds() {
        return mSideStagePosition == SPLIT_POSITION_TOP_OR_LEFT
                ? mSplitLayout.getBounds2() : mSplitLayout.getBounds1();
    }

    private void getSideStageBounds(Rect rect) {
        if (mSideStagePosition == SPLIT_POSITION_TOP_OR_LEFT) {
            mSplitLayout.getBounds1(rect);
        } else {
            mSplitLayout.getBounds2(rect);
        }
    }

    private void getMainStageBounds(Rect rect) {
        if (mSideStagePosition == SPLIT_POSITION_TOP_OR_LEFT) {
            mSplitLayout.getBounds2(rect);
        } else {
            mSplitLayout.getBounds1(rect);
        }
    }

    /**
     * Get the stage that should contain this `taskInfo`. The stage doesn't necessarily contain
     * this task (yet) so this can also be used to identify which stage to put a task into.
     */
    private StageTaskListener getStageOfTask(ActivityManager.RunningTaskInfo taskInfo) {
        // TODO(b/184679596): Find a way to either include task-org information in the transition,
        //                    or synchronize task-org callbacks so we can use stage.containsTask
        if (mMainStage.mRootTaskInfo != null
                && taskInfo.parentTaskId == mMainStage.mRootTaskInfo.taskId) {
            return mMainStage;
        } else if (mSideStage.mRootTaskInfo != null
                && taskInfo.parentTaskId == mSideStage.mRootTaskInfo.taskId) {
            return mSideStage;
        }
        return null;
    }

    @StageType
    private int getStageType(StageTaskListener stage) {
        if (stage == null) return STAGE_TYPE_UNDEFINED;
        return stage == mMainStage ? STAGE_TYPE_MAIN : STAGE_TYPE_SIDE;
    }

    @Override
    public WindowContainerTransaction handleRequest(@NonNull IBinder transition,
            @Nullable TransitionRequestInfo request) {
        final ActivityManager.RunningTaskInfo triggerTask = request.getTriggerTask();
        if (triggerTask == null) {
            if (isSplitActive()) {
                ProtoLog.d(WM_SHELL_SPLIT_SCREEN, "handleRequest: transition=%d display rotation",
                        request.getDebugId());
                // Check if the display is rotating.
                final TransitionRequestInfo.DisplayChange displayChange =
                        request.getDisplayChange();
                if (request.getType() == TRANSIT_CHANGE && displayChange != null
                        && displayChange.getStartRotation() != displayChange.getEndRotation()) {
                    mSplitLayout.setFreezeDividerWindow(true);
                }
                if (request.getRemoteTransition() != null) {
                    mSplitTransitions.setRemotePassThroughTransition(transition,
                            request.getRemoteTransition());
                }
                // Still want to monitor everything while in split-screen, so return non-null.
                return new WindowContainerTransaction();
            } else {
                return null;
            }
        } else if (triggerTask.displayId != mDisplayId) {
            // Skip handling task on the other display.
            return null;
        }

        WindowContainerTransaction out = null;
        final @WindowManager.TransitionType int type = request.getType();
        final boolean isOpening = isOpeningType(type);
        final boolean inFullscreen = triggerTask.getWindowingMode() == WINDOWING_MODE_FULLSCREEN;

        if (isOpening && inFullscreen) {
            // One task is opening into fullscreen mode, remove the corresponding split record.
            mRecentTasks.ifPresent(recentTasks -> recentTasks.removeSplitPair(triggerTask.taskId));
        }

        if (isSplitActive()) {
            ProtoLog.d(WM_SHELL_SPLIT_SCREEN, "handleRequest: transition=%d split active",
                    request.getDebugId());
            // Try to handle everything while in split-screen, so return a WCT even if it's empty.
            ProtoLog.v(ShellProtoLogGroup.WM_SHELL_TRANSITIONS, "  split is active so using split"
                            + "Transition to handle request. triggerTask=%d type=%s mainChildren=%d"
                            + " sideChildren=%d", triggerTask.taskId, transitTypeToString(type),
                    mMainStage.getChildCount(), mSideStage.getChildCount());
            out = new WindowContainerTransaction();
            final StageTaskListener stage = getStageOfTask(triggerTask);
            if (stage != null) {
                if (isClosingType(type) && stage.getChildCount() == 1) {
                    // Dismiss split if the last task in one of the stages is going away
                    // The top should be the opposite side that is closing:
                    int dismissTop = getStageType(stage) == STAGE_TYPE_MAIN
                            ? STAGE_TYPE_SIDE : STAGE_TYPE_MAIN;
                    prepareExitSplitScreen(dismissTop, out);
                    mSplitTransitions.setDismissTransition(transition, dismissTop,
                            EXIT_REASON_APP_FINISHED);
                } else if (isOpening && !mPausingTasks.isEmpty()) {
                    // One of the splitting task is opening while animating the split pair in
                    // recents, which means to dismiss the split pair to this task.
                    int dismissTop = getStageType(stage) == STAGE_TYPE_MAIN
                            ? STAGE_TYPE_MAIN : STAGE_TYPE_SIDE;
                    prepareExitSplitScreen(dismissTop, out);
                    mSplitTransitions.setDismissTransition(transition, dismissTop,
                            EXIT_REASON_APP_FINISHED);
                } else if (!isSplitScreenVisible() && isOpening) {
                    // If split is running in the background and the trigger task is appearing into
                    // split, prepare to enter split screen.
                    prepareEnterSplitScreen(out);
                    mSplitTransitions.setEnterTransition(transition, request.getRemoteTransition(),
                            TRANSIT_SPLIT_SCREEN_PAIR_OPEN, !mIsDropEntering);
                } else if (inFullscreen && isSplitScreenVisible()) {
                    // If the trigger task is in fullscreen and in split, exit split and place
                    // task on top
                    final int stageType = getStageOfTask(triggerTask.taskId);
                    prepareExitSplitScreen(stageType, out);
                    mSplitTransitions.setDismissTransition(transition, stageType,
                            EXIT_REASON_FULLSCREEN_REQUEST);
                }
            } else if (isOpening && inFullscreen) {
                final int activityType = triggerTask.getActivityType();
                if (activityType == ACTIVITY_TYPE_HOME || activityType == ACTIVITY_TYPE_RECENTS) {
                    // starting recents/home, so don't handle this and let it fall-through to
                    // the remote handler.
                    return null;
                }

                if ((mMainStage.containsTask(triggerTask.taskId)
                            && mMainStage.getChildCount() == 1)
                        || (mSideStage.containsTask(triggerTask.taskId)
                            && mSideStage.getChildCount() == 1)) {
                    // A splitting task is opening to fullscreen causes one side of the split empty,
                    // so appends operations to exit split.
                    prepareExitSplitScreen(STAGE_TYPE_UNDEFINED, out);
                }
            } else if (type == TRANSIT_KEYGUARD_OCCLUDE && triggerTask.topActivity != null
                    && isSplitScreenVisible()) {
                // Split include show when lock activity case, check the top activity under which
                // stage and move it to the top.
                int top = triggerTask.topActivity.equals(mMainStage.mRootTaskInfo.topActivity)
                        ? STAGE_TYPE_MAIN : STAGE_TYPE_SIDE;
                prepareExitSplitScreen(top, out);
                mSplitTransitions.setDismissTransition(transition, top,
                        EXIT_REASON_SCREEN_LOCKED_SHOW_ON_TOP);
            }

            if (!out.isEmpty()) {
                // One of the cases above handled it
                return out;
            } else if (isSplitScreenVisible()) {
                // If split is visible, only defer handling this transition if it's launching
                // adjacent while there is already a split pair -- this may trigger PIP and
                // that should be handled by the mixed handler.
                final boolean deferTransition = requestHasLaunchAdjacentFlag(request)
                    && mMainStage.getChildCount() != 0 && mSideStage.getChildCount() != 0;
                return !deferTransition ? out : null;
            }
            // Don't intercept the transition if we are not handling it as a part of one of the
            // cases above and it is not already visible
            return null;
        } else {
            if (triggerTask.parentTaskId == mMainStage.mRootTaskInfo.taskId
                    || triggerTask.parentTaskId == mSideStage.mRootTaskInfo.taskId) {
                ProtoLog.d(WM_SHELL_SPLIT_SCREEN, "handleRequest: transition=%d "
                                + "restoring to split", request.getDebugId());
                out = new WindowContainerTransaction();
                mSplitTransitions.setEnterTransition(transition, request.getRemoteTransition(),
                        TRANSIT_SPLIT_SCREEN_OPEN_TO_SIDE, false /* resizeAnim */);
            }
            if (isOpening && getStageOfTask(triggerTask) != null) {
                ProtoLog.d(WM_SHELL_SPLIT_SCREEN, "handleRequest: transition=%d enter split",
                        request.getDebugId());
                // One task is appearing into split, prepare to enter split screen.
                out = new WindowContainerTransaction();
                prepareEnterSplitScreen(out);
                mSplitTransitions.setEnterTransition(transition, request.getRemoteTransition(),
                        TRANSIT_SPLIT_SCREEN_PAIR_OPEN, !mIsDropEntering);
            }
            return out;
        }
    }

    /**
     * This is used for mixed scenarios. For such scenarios, just make sure to include exiting
     * split or entering split when appropriate.
     */
    public void addEnterOrExitIfNeeded(@Nullable TransitionRequestInfo request,
            @NonNull WindowContainerTransaction outWCT) {
        ProtoLog.d(WM_SHELL_SPLIT_SCREEN, "addEnterOrExitIfNeeded: transition=%d",
                request.getDebugId());
        final ActivityManager.RunningTaskInfo triggerTask = request.getTriggerTask();
        if (triggerTask != null && triggerTask.displayId != mDisplayId) {
            // Skip handling task on the other display.
            return;
        }
        final @WindowManager.TransitionType int type = request.getType();
        if (isSplitActive() && !isOpeningType(type)
                && (mMainStage.getChildCount() == 0 || mSideStage.getChildCount() == 0)) {
            ProtoLog.v(ShellProtoLogGroup.WM_SHELL_TRANSITIONS, "  One of the splits became "
                            + "empty during a mixed transition (one not handled by split),"
                            + " so make sure split-screen state is cleaned-up. "
                            + "mainStageCount=%d sideStageCount=%d", mMainStage.getChildCount(),
                    mSideStage.getChildCount());
            if (triggerTask != null) {
                mRecentTasks.ifPresent(
                        recentTasks -> recentTasks.removeSplitPair(triggerTask.taskId));
            }
            @StageType int topStage = STAGE_TYPE_UNDEFINED;
            if (isSplitScreenVisible()) {
                // Get the stage where a child exists to keep that stage onTop
                if (mMainStage.getChildCount() != 0 && mSideStage.getChildCount() == 0) {
                    topStage = STAGE_TYPE_MAIN;
                } else if (mSideStage.getChildCount() != 0 && mMainStage.getChildCount() == 0) {
                    topStage = STAGE_TYPE_SIDE;
                }
            }
            prepareExitSplitScreen(topStage, outWCT);
        }
    }

    @Override
    public void mergeAnimation(IBinder transition, TransitionInfo info,
            SurfaceControl.Transaction t, IBinder mergeTarget,
            Transitions.TransitionFinishCallback finishCallback) {
        ProtoLog.d(WM_SHELL_SPLIT_SCREEN, "mergeAnimation: transition=%d", info.getDebugId());
        mSplitTransitions.mergeAnimation(transition, info, t, mergeTarget, finishCallback);
    }

    /** Jump the current transition animation to the end. */
    public boolean end() {
        return mSplitTransitions.end();
    }

    @Override
    public void onTransitionConsumed(@NonNull IBinder transition, boolean aborted,
            @Nullable SurfaceControl.Transaction finishT) {
        ProtoLog.d(WM_SHELL_SPLIT_SCREEN, "onTransitionConsumed");
        mSplitTransitions.onTransitionConsumed(transition, aborted, finishT);
    }

    @Override
    public boolean startAnimation(@NonNull IBinder transition,
            @NonNull TransitionInfo info,
            @NonNull SurfaceControl.Transaction startTransaction,
            @NonNull SurfaceControl.Transaction finishTransaction,
            @NonNull Transitions.TransitionFinishCallback finishCallback) {
        if (!mSplitTransitions.isPendingTransition(transition)) {
            // Not entering or exiting, so just do some house-keeping and validation.

            // If we're not in split-mode, just abort so something else can handle it.
            if (!mMainStage.isActive()) return false;

            ProtoLog.d(WM_SHELL_SPLIT_SCREEN, "startAnimation: transition=%d", info.getDebugId());
            mSplitLayout.setFreezeDividerWindow(false);
            final StageChangeRecord record = new StageChangeRecord();
            final int transitType = info.getType();
            TransitionInfo.Change pipChange = null;
            for (int iC = 0; iC < info.getChanges().size(); ++iC) {
                final TransitionInfo.Change change = info.getChanges().get(iC);
                if (change.getMode() == TRANSIT_CHANGE
                        && (change.getFlags() & FLAG_IS_DISPLAY) != 0) {
                    // Don't reset the IME state since those updates are not in sync with the
                    // display change transition
                    mSplitLayout.update(startTransaction, false /* resetImePosition */);
                }

                if (mMixedHandler.isEnteringPip(change, transitType)) {
                    pipChange = change;
                }

                final ActivityManager.RunningTaskInfo taskInfo = change.getTaskInfo();
                if (taskInfo == null) continue;
                if (taskInfo.token.equals(mRootTaskInfo.token)) {
                    if (isOpeningType(change.getMode())) {
                        // Split is opened by someone so set it as visible.
                        setSplitsVisible(true);
                        // TODO(b/275664132): Find a way to integrate this with finishWct.
                        //  This is setting the flag to a task and not interfering with the
                        //  transition.
                        final WindowContainerTransaction wct = new WindowContainerTransaction();
                        wct.setReparentLeafTaskIfRelaunch(mRootTaskInfo.token,
                                false /* reparentLeafTaskIfRelaunch */);
                        mTaskOrganizer.applyTransaction(wct);
                    } else if (isClosingType(change.getMode())) {
                        // Split is closed by someone so set it as invisible.
                        setSplitsVisible(false);
                        // TODO(b/275664132): Find a way to integrate this with finishWct.
                        //  This is setting the flag to a task and not interfering with the
                        //  transition.
                        final WindowContainerTransaction wct = new WindowContainerTransaction();
                        wct.setReparentLeafTaskIfRelaunch(mRootTaskInfo.token,
                                true /* reparentLeafTaskIfRelaunch */);
                        mTaskOrganizer.applyTransaction(wct);
                    }
                    continue;
                }
                final StageTaskListener stage = getStageOfTask(taskInfo);
                if (stage == null) {
                    if (change.getParent() == null && !isClosingType(change.getMode())
                            && taskInfo.getWindowingMode() == WINDOWING_MODE_FULLSCREEN) {
                        record.mContainShowFullscreenChange = true;
                    }
                    continue;
                }
                if (isOpeningType(change.getMode())) {
                    if (!stage.containsTask(taskInfo.taskId)) {
                        Log.w(TAG, "Expected onTaskAppeared on " + stage + " to have been called"
                                + " with " + taskInfo.taskId + " before startAnimation().");
                        record.addRecord(stage, true, taskInfo.taskId);
                    }
                } else if (change.getMode() == TRANSIT_CLOSE) {
                    if (stage.containsTask(taskInfo.taskId)) {
                        record.addRecord(stage, false, taskInfo.taskId);
                        Log.w(TAG, "Expected onTaskVanished on " + stage + " to have been called"
                                + " with " + taskInfo.taskId + " before startAnimation().");
                    }
                }
            }

            if (pipChange != null) {
                TransitionInfo.Change pipReplacingChange = getPipReplacingChange(info, pipChange,
                        mMainStage.mRootTaskInfo.taskId, mSideStage.mRootTaskInfo.taskId,
                        getSplitItemStage(pipChange.getLastParent()));
                if (pipReplacingChange != null) {
                    // Set an enter transition for when startAnimation gets called again
                    mSplitTransitions.setEnterTransition(transition, /*remoteTransition*/ null,
                            TRANSIT_SPLIT_SCREEN_OPEN_TO_SIDE, /*resizeAnim*/ false);
                }

                mMixedHandler.animatePendingEnterPipFromSplit(transition, info,
                        startTransaction, finishTransaction, finishCallback,
                        pipReplacingChange != null);
                notifySplitAnimationFinished();
                return true;
            }

            final ArraySet<StageTaskListener> dismissStages = record.getShouldDismissedStage();
            if (mMainStage.getChildCount() == 0 || mSideStage.getChildCount() == 0
                    || dismissStages.size() == 1) {
                // If the size of dismissStages == 1, one of the task is closed without prepare
                // pending transition, which could happen if all activities were finished after
                // finish top activity in a task, so the trigger task is null when handleRequest.
                // Note if the size of dismissStages == 2, it's starting a new task,
                // so don't handle it.
                Log.e(TAG, "Somehow removed the last task in a stage outside of a proper "
                        + "transition.");
                // This new transition would be merged to current one so we need to clear
                // tile manually here.
                clearSplitPairedInRecents(EXIT_REASON_APP_FINISHED);
                final WindowContainerTransaction wct = new WindowContainerTransaction();
                final int dismissTop = (dismissStages.size() == 1
                        && getStageType(dismissStages.valueAt(0)) == STAGE_TYPE_MAIN)
                        || mMainStage.getChildCount() == 0 ? STAGE_TYPE_SIDE : STAGE_TYPE_MAIN;
                // If there is a fullscreen opening change, we should not bring stage to top.
                prepareExitSplitScreen(
                        !record.mContainShowFullscreenChange && isSplitScreenVisible()
                        ? dismissTop : STAGE_TYPE_UNDEFINED, wct);
                mSplitTransitions.startDismissTransition(wct, this, dismissTop,
                        EXIT_REASON_APP_FINISHED);
                // This can happen in some pathological cases. For example:
                // 1. main has 2 tasks [Task A (Single-task), Task B], side has one task [Task C]
                // 2. Task B closes itself and starts Task A in LAUNCH_ADJACENT at the same time
                // In this case, the result *should* be that we leave split.
                // TODO(b/184679596): Find a way to either include task-org information in
                //                    the transition, or synchronize task-org callbacks.
            }
            // Use normal animations.
            notifySplitAnimationFinished();
            return false;
        } else if (mMixedHandler != null && TransitionUtil.hasDisplayChange(info)) {
            // A display-change has been un-expectedly inserted into the transition. Redirect
            // handling to the mixed-handler to deal with splitting it up.
            if (mMixedHandler.animatePendingSplitWithDisplayChange(transition, info,
                    startTransaction, finishTransaction, finishCallback)) {
                if (mSplitTransitions.isPendingResize(transition)) {
                    ProtoLog.d(WM_SHELL_SPLIT_SCREEN,
                            "startAnimation: transition=%d display change", info.getDebugId());
                    // Only need to update in resize because divider exist before transition.
                    mSplitLayout.update(startTransaction, true /* resetImePosition */);
                    startTransaction.apply();
                }
                notifySplitAnimationFinished();
                return true;
            }
        } else if (mSplitTransitions.isPendingPassThrough(transition)) {
            ProtoLog.d(WM_SHELL_SPLIT_SCREEN,
                    "startAnimation: passThrough transition=%d", info.getDebugId());
            mSplitTransitions.mPendingRemotePassthrough.mRemoteHandler.startAnimation(transition,
                    info, startTransaction, finishTransaction, finishCallback);
            notifySplitAnimationFinished();
            return true;
        }

        return startPendingAnimation(transition, info, startTransaction, finishTransaction,
                finishCallback);
    }

    static class StageChangeRecord {
        boolean mContainShowFullscreenChange = false;
        static class StageChange {
            final StageTaskListener mStageTaskListener;
            final IntArray mAddedTaskId = new IntArray();
            final IntArray mRemovedTaskId = new IntArray();
            StageChange(StageTaskListener stage) {
                mStageTaskListener = stage;
            }

            boolean shouldDismissStage() {
                if (mAddedTaskId.size() > 0 || mRemovedTaskId.size() == 0) {
                    return false;
                }
                int removeChildTaskCount = 0;
                for (int i = mRemovedTaskId.size() - 1; i >= 0; --i) {
                    if (mStageTaskListener.containsTask(mRemovedTaskId.get(i))) {
                        ++removeChildTaskCount;
                    }
                }
                return removeChildTaskCount == mStageTaskListener.getChildCount();
            }
        }
        private final ArrayMap<StageTaskListener, StageChange> mChanges = new ArrayMap<>();

        void addRecord(StageTaskListener stage, boolean open, int taskId) {
            final StageChange next;
            if (!mChanges.containsKey(stage)) {
                next = new StageChange(stage);
                mChanges.put(stage, next);
            } else {
                next = mChanges.get(stage);
            }
            if (open) {
                next.mAddedTaskId.add(taskId);
            } else {
                next.mRemovedTaskId.add(taskId);
            }
        }

        ArraySet<StageTaskListener> getShouldDismissedStage() {
            final ArraySet<StageTaskListener> dismissTarget = new ArraySet<>();
            for (int i = mChanges.size() - 1; i >= 0; --i) {
                final StageChange change = mChanges.valueAt(i);
                if (change.shouldDismissStage()) {
                    dismissTarget.add(change.mStageTaskListener);
                }
            }
            return dismissTarget;
        }
    }

    /** Starts the pending transition animation. */
    public boolean startPendingAnimation(@NonNull IBinder transition,
            @NonNull TransitionInfo info,
            @NonNull SurfaceControl.Transaction startTransaction,
            @NonNull SurfaceControl.Transaction finishTransaction,
            @NonNull Transitions.TransitionFinishCallback finishCallback) {
        ProtoLog.d(WM_SHELL_SPLIT_SCREEN, "startPendingAnimation: transition=%d",
                info.getDebugId());
        boolean shouldAnimate = true;
        if (mSplitTransitions.isPendingEnter(transition)) {
            shouldAnimate = startPendingEnterAnimation(transition,
                    mSplitTransitions.mPendingEnter, info, startTransaction, finishTransaction);
        } else if (mSplitTransitions.isPendingDismiss(transition)) {
            final SplitScreenTransitions.DismissSession dismiss = mSplitTransitions.mPendingDismiss;
            shouldAnimate = startPendingDismissAnimation(
                    dismiss, info, startTransaction, finishTransaction);
            if (shouldAnimate && dismiss.mReason == EXIT_REASON_DRAG_DIVIDER) {
                final StageTaskListener toTopStage =
                        dismiss.mDismissTop == STAGE_TYPE_MAIN ? mMainStage : mSideStage;
                mSplitTransitions.playDragDismissAnimation(transition, info, startTransaction,
                        finishTransaction, finishCallback, toTopStage.mRootTaskInfo.token,
                        toTopStage.getSplitDecorManager(), mRootTaskInfo.token);
                return true;
            }
        } else if (mSplitTransitions.isPendingResize(transition)) {
            mSplitTransitions.playResizeAnimation(transition, info, startTransaction,
                    finishTransaction, finishCallback, mMainStage.mRootTaskInfo.token,
                    mSideStage.mRootTaskInfo.token, mMainStage.getSplitDecorManager(),
                    mSideStage.getSplitDecorManager());
            return true;
        }
        if (!shouldAnimate) return false;

        mSplitTransitions.playAnimation(transition, info, startTransaction, finishTransaction,
                finishCallback, mMainStage.mRootTaskInfo.token, mSideStage.mRootTaskInfo.token,
                mRootTaskInfo.token);
        return true;
    }

    /** Called to clean-up state and do house-keeping after the animation is done. */
    public void onTransitionAnimationComplete() {
        ProtoLog.d(WM_SHELL_SPLIT_SCREEN, "onTransitionAnimationComplete");
        // If still playing, let it finish.
        if (!mMainStage.isActive() && !mIsExiting) {
            // Update divider state after animation so that it is still around and positioned
            // properly for the animation itself.
            mSplitLayout.release();
        }
    }

    private boolean startPendingEnterAnimation(@NonNull IBinder transition,
            @NonNull SplitScreenTransitions.EnterSession enterTransition,
            @NonNull TransitionInfo info, @NonNull SurfaceControl.Transaction t,
            @NonNull SurfaceControl.Transaction finishT) {
        ProtoLog.d(WM_SHELL_SPLIT_SCREEN, "startPendingEnterAnimation: enterTransition=%s",
                enterTransition);
        // First, verify that we actually have opened apps in both splits.
        TransitionInfo.Change mainChild = null;
        TransitionInfo.Change sideChild = null;
        final WindowContainerTransaction evictWct = new WindowContainerTransaction();
        for (int iC = 0; iC < info.getChanges().size(); ++iC) {
            final TransitionInfo.Change change = info.getChanges().get(iC);
            final ActivityManager.RunningTaskInfo taskInfo = change.getTaskInfo();
            if (taskInfo == null || !taskInfo.hasParentTask()) continue;
            if (mPausingTasks.contains(taskInfo.taskId)) {
                continue;
            }
            final @StageType int stageType = getStageType(getStageOfTask(taskInfo));
            if (mainChild == null && stageType == STAGE_TYPE_MAIN
                    && (isOpeningType(change.getMode()) || change.getMode() == TRANSIT_CHANGE)) {
                // Includes TRANSIT_CHANGE to cover reparenting top-most task to split.
                mainChild = change;
            } else if (sideChild == null && stageType == STAGE_TYPE_SIDE
                    && (isOpeningType(change.getMode()) || change.getMode() == TRANSIT_CHANGE)) {
                sideChild = change;
            } else if (stageType != STAGE_TYPE_UNDEFINED && change.getMode() == TRANSIT_TO_BACK) {
                // Collect all to back task's and evict them when transition finished.
                evictWct.reparent(taskInfo.token, null /* parent */, false /* onTop */);
            }
        }

        SplitScreenTransitions.EnterSession pendingEnter = mSplitTransitions.mPendingEnter;
        if (pendingEnter.mExtraTransitType
                == TRANSIT_SPLIT_SCREEN_OPEN_TO_SIDE) {
            // Open to side should only be used when split already active and foregorund or when
            // app is restoring to split from fullscreen.
            if (mainChild == null && sideChild == null) {
                Log.w(TAG, splitFailureMessage("startPendingEnterAnimation",
                        "Launched a task in split, but didn't receive any task in transition."));
                // This should happen when the target app is already on front, so just cancel.
                pendingEnter.cancel(null);
                return true;
            }
        } else {
            if (mainChild == null || sideChild == null) {
                final int dismissTop = mainChild != null ? STAGE_TYPE_MAIN :
                        (sideChild != null ? STAGE_TYPE_SIDE : STAGE_TYPE_UNDEFINED);
                pendingEnter.cancel(
                        (cancelWct, cancelT) -> prepareExitSplitScreen(dismissTop, cancelWct));
                Log.w(TAG, splitFailureMessage("startPendingEnterAnimation",
                        "launched 2 tasks in split, but didn't receive "
                        + "2 tasks in transition. Possibly one of them failed to launch"));
                if (mRecentTasks.isPresent() && mainChild != null) {
                    mRecentTasks.get().removeSplitPair(mainChild.getTaskInfo().taskId);
                }
                if (mRecentTasks.isPresent() && sideChild != null) {
                    mRecentTasks.get().removeSplitPair(sideChild.getTaskInfo().taskId);
                }
                if (pendingEnter.mRemoteHandler != null) {
                    // Pass false for aborted since WM didn't abort, business logic chose to
                    // terminate/exit early
                    pendingEnter.mRemoteHandler.onTransitionConsumed(transition,
                            false /*aborted*/, finishT);
                }
                handleUnsupportedSplitStart();
                return true;
            }
        }

        // Make some noise if things aren't totally expected. These states shouldn't effect
        // transitions locally, but remotes (like Launcher) may get confused if they were
        // depending on listener callbacks. This can happen because task-organizer callbacks
        // aren't serialized with transition callbacks.
        // This usually occurred on app use trampoline launch new task and finish itself.
        // TODO(b/184679596): Find a way to either include task-org information in
        //                    the transition, or synchronize task-org callbacks.
        final boolean mainNotContainOpenTask =
                mainChild != null && !mMainStage.containsTask(mainChild.getTaskInfo().taskId);
        final boolean sideNotContainOpenTask =
                sideChild != null && !mSideStage.containsTask(sideChild.getTaskInfo().taskId);
        if (mainNotContainOpenTask) {
            Log.w(TAG, "Expected onTaskAppeared on " + mMainStage
                    + " to have been called with " + mainChild.getTaskInfo().taskId
                    + " before startAnimation().");
        }
        if (sideNotContainOpenTask) {
            Log.w(TAG, "Expected onTaskAppeared on " + mSideStage
                    + " to have been called with " + sideChild.getTaskInfo().taskId
                    + " before startAnimation().");
        }
        final TransitionInfo.Change finalMainChild = mainChild;
        final TransitionInfo.Change finalSideChild = sideChild;
        enterTransition.setFinishedCallback((callbackWct, callbackT) -> {
            if (!enterTransition.mResizeAnim) {
                // If resizing, we'll call notify at the end of the resizing animation (below)
                notifySplitAnimationFinished();
            }
            if (finalMainChild != null) {
                if (!mainNotContainOpenTask) {
                    mMainStage.evictOtherChildren(callbackWct, finalMainChild.getTaskInfo().taskId);
                } else {
                    mMainStage.evictInvisibleChildren(callbackWct);
                }
            }
            if (finalSideChild != null) {
                if (!sideNotContainOpenTask) {
                    mSideStage.evictOtherChildren(callbackWct, finalSideChild.getTaskInfo().taskId);
                } else {
                    mSideStage.evictInvisibleChildren(callbackWct);
                }
            }
            if (!evictWct.isEmpty()) {
                callbackWct.merge(evictWct, true);
            }
            if (enterTransition.mResizeAnim) {
                mShowDecorImmediately = true;
                mSplitLayout.flingDividerToCenter(this::notifySplitAnimationFinished);
            }
            callbackWct.setReparentLeafTaskIfRelaunch(mRootTaskInfo.token, false);
            mPausingTasks.clear();
        });

        if (info.getType() == TRANSIT_CHANGE && !isSplitActive()
                && pendingEnter.mExtraTransitType == TRANSIT_SPLIT_SCREEN_OPEN_TO_SIDE) {
            if (finalMainChild != null && finalSideChild == null) {
                requestEnterSplitSelect(finalMainChild.getTaskInfo(),
                        new WindowContainerTransaction(),
                        getMainStagePosition(), finalMainChild.getStartAbsBounds());
            } else if (finalSideChild != null && finalMainChild == null) {
                requestEnterSplitSelect(finalSideChild.getTaskInfo(),
                        new WindowContainerTransaction(),
                        getSideStagePosition(), finalSideChild.getStartAbsBounds());
            } else {
                throw new IllegalStateException(
                        "Attempting to restore to split but reparenting change not found");
            }
        }

        finishEnterSplitScreen(finishT);
        addDividerBarToTransition(info, true /* show */);
        return true;
    }

    public void goToFullscreenFromSplit() {
        ProtoLog.d(WM_SHELL_SPLIT_SCREEN, "goToFullscreenFromSplit");
        // If main stage is focused, toEnd = true if
        // mSideStagePosition = SPLIT_POSITION_BOTTOM_OR_RIGHT. Otherwise toEnd = false
        // If side stage is focused, toEnd = true if
        // mSideStagePosition = SPLIT_POSITION_TOP_OR_LEFT. Otherwise toEnd = false
        final boolean toEnd;
        if (mMainStage.isFocused()) {
            toEnd = (mSideStagePosition == SPLIT_POSITION_BOTTOM_OR_RIGHT);
        } else {
            toEnd = (mSideStagePosition == SPLIT_POSITION_TOP_OR_LEFT);
        }
        mSplitLayout.flingDividerToDismiss(toEnd, EXIT_REASON_FULLSCREEN_SHORTCUT);
    }

    /** Move the specified task to fullscreen, regardless of focus state. */
    public void moveTaskToFullscreen(int taskId, int exitReason) {
        ProtoLog.d(WM_SHELL_SPLIT_SCREEN, "moveTaskToFullscreen");
        boolean leftOrTop;
        if (mMainStage.containsTask(taskId)) {
            leftOrTop = (mSideStagePosition == SPLIT_POSITION_TOP_OR_LEFT);
        } else if (mSideStage.containsTask(taskId)) {
            leftOrTop = (mSideStagePosition == SPLIT_POSITION_BOTTOM_OR_RIGHT);
        } else {
            return;
        }
        mSplitLayout.flingDividerToDismiss(!leftOrTop, exitReason);

    }

    /**
     * Performs previous child eviction and such to prepare for the pip task expending into one of
     * the split stages
     *
     * @param taskInfo TaskInfo of the pip task
     */
    public void onPipExpandToSplit(WindowContainerTransaction wct,
            ActivityManager.RunningTaskInfo taskInfo) {
        ProtoLog.d(WM_SHELL_SPLIT_SCREEN, "onPipExpandToSplit: task=%s", taskInfo);
        prepareEnterSplitScreen(wct, taskInfo, getActivateSplitPosition(taskInfo),
                false /*resizeAnim*/);

        if (!isSplitScreenVisible() || mSplitRequest == null) {
            return;
        }

        boolean replacingMainStage = getMainStagePosition() == mSplitRequest.mActivatePosition;
        (replacingMainStage ? mMainStage : mSideStage).evictOtherChildren(wct, taskInfo.taskId);
    }

    boolean isLaunchToSplit(TaskInfo taskInfo) {
        return getActivateSplitPosition(taskInfo) != SPLIT_POSITION_UNDEFINED;
    }

    int getActivateSplitPosition(TaskInfo taskInfo) {
        if (mSplitRequest == null || taskInfo == null) {
            return SPLIT_POSITION_UNDEFINED;
        }
        if (mSplitRequest.mActivateTaskId != 0
                && mSplitRequest.mActivateTaskId2 == taskInfo.taskId) {
            return mSplitRequest.mActivatePosition;
        }
        if (mSplitRequest.mActivateTaskId == taskInfo.taskId) {
            return mSplitRequest.mActivatePosition;
        }
        final String packageName1 = SplitScreenUtils.getPackageName(mSplitRequest.mStartIntent);
        final String basePackageName = SplitScreenUtils.getPackageName(taskInfo.baseIntent);
        if (packageName1 != null && packageName1.equals(basePackageName)) {
            return mSplitRequest.mActivatePosition;
        }
        final String packageName2 = SplitScreenUtils.getPackageName(mSplitRequest.mStartIntent2);
        if (packageName2 != null && packageName2.equals(basePackageName)) {
            return mSplitRequest.mActivatePosition;
        }
        return SPLIT_POSITION_UNDEFINED;
    }

    /**
     * Synchronize split-screen state with transition and make appropriate preparations.
     * @param toStage The stage that will not be dismissed. If set to
     *        {@link SplitScreen#STAGE_TYPE_UNDEFINED} then both stages will be dismissed
     */
    public void prepareDismissAnimation(@StageType int toStage, @ExitReason int dismissReason,
            @NonNull TransitionInfo info, @NonNull SurfaceControl.Transaction t,
            @NonNull SurfaceControl.Transaction finishT) {
        ProtoLog.d(WM_SHELL_SPLIT_SCREEN, 
                "prepareDismissAnimation: transition=%d toStage=%d reason=%s",
                info.getDebugId(), toStage, exitReasonToString(dismissReason));
        // Make some noise if things aren't totally expected. These states shouldn't effect
        // transitions locally, but remotes (like Launcher) may get confused if they were
        // depending on listener callbacks. This can happen because task-organizer callbacks
        // aren't serialized with transition callbacks.
        // TODO(b/184679596): Find a way to either include task-org information in
        //                    the transition, or synchronize task-org callbacks.
        if (toStage == STAGE_TYPE_UNDEFINED) {
            if (mMainStage.getChildCount() != 0) {
                final StringBuilder tasksLeft = new StringBuilder();
                for (int i = 0; i < mMainStage.getChildCount(); ++i) {
                    tasksLeft.append(i != 0 ? ", " : "");
                    tasksLeft.append(mMainStage.mChildrenTaskInfo.keyAt(i));
                }
                Log.w(TAG, "Expected onTaskVanished on " + mMainStage
                        + " to have been called with [" + tasksLeft.toString()
                        + "] before startAnimation().");
            }
            if (mSideStage.getChildCount() != 0) {
                final StringBuilder tasksLeft = new StringBuilder();
                for (int i = 0; i < mSideStage.getChildCount(); ++i) {
                    tasksLeft.append(i != 0 ? ", " : "");
                    tasksLeft.append(mSideStage.mChildrenTaskInfo.keyAt(i));
                }
                Log.w(TAG, "Expected onTaskVanished on " + mSideStage
                        + " to have been called with [" + tasksLeft.toString()
                        + "] before startAnimation().");
            }
        }

        final ArrayMap<Integer, SurfaceControl> dismissingTasks = new ArrayMap<>();
        for (int i = info.getChanges().size() - 1; i >= 0; --i) {
            final TransitionInfo.Change change = info.getChanges().get(i);
            final ActivityManager.RunningTaskInfo taskInfo = change.getTaskInfo();
            if (taskInfo == null) continue;
            if (getStageOfTask(taskInfo) != null
                    || getSplitItemPosition(change.getLastParent()) != SPLIT_POSITION_UNDEFINED) {
                dismissingTasks.put(taskInfo.taskId, change.getLeash());
            }
        }


        if (shouldBreakPairedTaskInRecents(dismissReason)) {
            // Notify recents if we are exiting in a way that breaks the pair, and disable further
            // updates to splits in the recents until we enter split again
            mRecentTasks.ifPresent(recentTasks -> {
                for (int i = dismissingTasks.keySet().size() - 1; i >= 0; --i) {
                    recentTasks.removeSplitPair(dismissingTasks.keyAt(i));
                }
            });
        }
        mSplitRequest = null;

        // Update local states.
        setSplitsVisible(false);
        // Wait until after animation to update divider

        // Reset crops so they don't interfere with subsequent launches
        t.setCrop(mMainStage.mRootLeash, null);
        t.setCrop(mSideStage.mRootLeash, null);
        // Hide the non-top stage and set the top one to the fullscreen position.
        if (toStage != STAGE_TYPE_UNDEFINED) {
            t.hide(toStage == STAGE_TYPE_MAIN ? mSideStage.mRootLeash : mMainStage.mRootLeash);
            t.setPosition(toStage == STAGE_TYPE_MAIN
                    ? mMainStage.mRootLeash : mSideStage.mRootLeash, 0, 0);
        } else {
            for (int i = dismissingTasks.keySet().size() - 1; i >= 0; --i) {
                finishT.hide(dismissingTasks.valueAt(i));
            }
        }

        if (toStage == STAGE_TYPE_UNDEFINED) {
            logExit(dismissReason);
        } else {
            logExitToStage(dismissReason, toStage == STAGE_TYPE_MAIN);
        }

        // Hide divider and dim layer on transition finished.
        setDividerVisibility(false, t);
        finishT.hide(mMainStage.mDimLayer);
        finishT.hide(mSideStage.mDimLayer);
    }

    private boolean startPendingDismissAnimation(
            @NonNull SplitScreenTransitions.DismissSession dismissTransition,
            @NonNull TransitionInfo info, @NonNull SurfaceControl.Transaction t,
            @NonNull SurfaceControl.Transaction finishT) {
        ProtoLog.d(WM_SHELL_SPLIT_SCREEN,
                "startPendingDismissAnimation: transition=%d dismissTransition=%s",
                info.getDebugId(), dismissTransition);
        prepareDismissAnimation(dismissTransition.mDismissTop, dismissTransition.mReason, info,
                t, finishT);
        if (dismissTransition.mDismissTop == STAGE_TYPE_UNDEFINED) {
            // TODO: Have a proper remote for this. Until then, though, reset state and use the
            //       normal animation stuff (which falls back to the normal launcher remote).
            setDividerVisibility(false, t);
            mSplitLayout.release(t);
            mSplitTransitions.mPendingDismiss = null;
            return false;
        }
        dismissTransition.setFinishedCallback((callbackWct, callbackT) -> {
            mMainStage.getSplitDecorManager().release(callbackT);
            mSideStage.getSplitDecorManager().release(callbackT);
            callbackWct.setReparentLeafTaskIfRelaunch(mRootTaskInfo.token, false);
        });
        return true;
    }

    /** Call this when starting the open-recents animation while split-screen is active. */
    public void onRecentsInSplitAnimationStart(TransitionInfo info) {
        ProtoLog.d(WM_SHELL_SPLIT_SCREEN, "onRecentsInSplitAnimationStart: transition=%d",
                info.getDebugId());
        if (isSplitScreenVisible()) {
            // Cache tasks on live tile.
            for (int i = 0; i < info.getChanges().size(); ++i) {
                final TransitionInfo.Change change = info.getChanges().get(i);
                if (TransitionUtil.isClosingType(change.getMode())
                        && change.getTaskInfo() != null) {
                    final int taskId = change.getTaskInfo().taskId;
                    if (mMainStage.getTopVisibleChildTaskId() == taskId
                            || mSideStage.getTopVisibleChildTaskId() == taskId) {
                        mPausingTasks.add(taskId);
                    }
                }
            }
        }

        addDividerBarToTransition(info, false /* show */);
    }

    /** Call this when the recents animation canceled during split-screen. */
    public void onRecentsInSplitAnimationCanceled() {
        mPausingTasks.clear();
        setSplitsVisible(false);

        final WindowContainerTransaction wct = new WindowContainerTransaction();
        wct.setReparentLeafTaskIfRelaunch(mRootTaskInfo.token,
                true /* reparentLeafTaskIfRelaunch */);
        mTaskOrganizer.applyTransaction(wct);
    }

    /** Call this when the recents animation during split-screen finishes. */
    public void onRecentsInSplitAnimationFinish(WindowContainerTransaction finishWct,
            SurfaceControl.Transaction finishT) {
        ProtoLog.d(WM_SHELL_SPLIT_SCREEN, "onRecentsInSplitAnimationFinish");
        mPausingTasks.clear();
        // Check if the recent transition is finished by returning to the current
        // split, so we can restore the divider bar.
        for (int i = 0; i < finishWct.getHierarchyOps().size(); ++i) {
            final WindowContainerTransaction.HierarchyOp op =
                    finishWct.getHierarchyOps().get(i);
            final IBinder container = op.getContainer();
            if (op.getType() == HIERARCHY_OP_TYPE_REORDER && op.getToTop()
                    && (mMainStage.containsContainer(container)
                    || mSideStage.containsContainer(container))) {
                updateSurfaceBounds(mSplitLayout, finishT,
                        false /* applyResizingOffset */);
                finishT.reparent(mSplitLayout.getDividerLeash(), mRootTaskLeash);
                setDividerVisibility(true, finishT);
                return;
            }
        }

        setSplitsVisible(false);
        finishWct.setReparentLeafTaskIfRelaunch(mRootTaskInfo.token,
                true /* reparentLeafTaskIfRelaunch */);
    }

    /** Call this when the animation from split screen to desktop is started. */
    public void onSplitToDesktop() {
        setSplitsVisible(false);
    }

    /** Call this when the recents animation finishes by doing pair-to-pair switch. */
    public void onRecentsPairToPairAnimationFinish(WindowContainerTransaction finishWct) {
        ProtoLog.d(WM_SHELL_SPLIT_SCREEN, "onRecentsPairToPairAnimationFinish");
        // Pair-to-pair switch happened so here should evict the live tile from its stage.
        // Otherwise, the task will remain in stage, and occluding the new task when next time
        // user entering recents.
        for (int i = mPausingTasks.size() - 1; i >= 0; --i) {
            final int taskId = mPausingTasks.get(i);
            if (mMainStage.containsTask(taskId)) {
                mMainStage.evictChildren(finishWct, taskId);
            } else if (mSideStage.containsTask(taskId)) {
                mSideStage.evictChildren(finishWct, taskId);
            }
        }
        // If pending enter hasn't consumed, the mix handler will invoke start pending
        // animation within following transition.
        if (mSplitTransitions.mPendingEnter == null) {
            mPausingTasks.clear();
            updateRecentTasksSplitPair();
        }
    }

    private void addDividerBarToTransition(@NonNull TransitionInfo info, boolean show) {
        final SurfaceControl leash = mSplitLayout.getDividerLeash();
        if (leash == null || !leash.isValid()) {
            Slog.w(TAG, "addDividerBarToTransition but leash was released or not be created");
            return;
        }

        final TransitionInfo.Change barChange = new TransitionInfo.Change(null /* token */, leash);
        mSplitLayout.getRefDividerBounds(mTempRect1);
        barChange.setParent(mRootTaskInfo.token);
        barChange.setStartAbsBounds(mTempRect1);
        barChange.setEndAbsBounds(mTempRect1);
        barChange.setMode(show ? TRANSIT_TO_FRONT : TRANSIT_TO_BACK);
        barChange.setFlags(FLAG_IS_DIVIDER_BAR);
        // Technically this should be order-0, but this is running after layer assignment
        // and it's a special case, so just add to end.
        info.addChange(barChange);
    }

    RemoteAnimationTarget getDividerBarLegacyTarget() {
        final Rect bounds = mSplitLayout.getDividerBounds();
        return new RemoteAnimationTarget(-1 /* taskId */, -1 /* mode */,
                mSplitLayout.getDividerLeash(), false /* isTranslucent */, null /* clipRect */,
                null /* contentInsets */, Integer.MAX_VALUE /* prefixOrderIndex */,
                new android.graphics.Point(0, 0) /* position */, bounds, bounds,
                new WindowConfiguration(), true, null /* startLeash */, null /* startBounds */,
                null /* taskInfo */, false /* allowEnterPip */, TYPE_DOCK_DIVIDER);
    }

    @NeverCompile
    @Override
    public void dump(@NonNull PrintWriter pw, String prefix) {
        final String innerPrefix = prefix + "  ";
        final String childPrefix = innerPrefix + "  ";
        pw.println(prefix + TAG + " mDisplayId=" + mDisplayId);
        pw.println(innerPrefix + "mDividerVisible=" + mDividerVisible);
        pw.println(innerPrefix + "isSplitActive=" + isSplitActive());
        pw.println(innerPrefix + "isSplitVisible=" + isSplitScreenVisible());
        pw.println(innerPrefix + "isLeftRightSplit=" + mSplitLayout.isLeftRightSplit());
        pw.println(innerPrefix + "MainStage");
        pw.println(childPrefix + "stagePosition=" + splitPositionToString(getMainStagePosition()));
        pw.println(childPrefix + "isActive=" + mMainStage.isActive());
        mMainStage.dump(pw, childPrefix);
        pw.println(innerPrefix + "MainStageListener");
        mMainStageListener.dump(pw, childPrefix);
        pw.println(innerPrefix + "SideStage");
        pw.println(childPrefix + "stagePosition=" + splitPositionToString(getSideStagePosition()));
        mSideStage.dump(pw, childPrefix);
        pw.println(innerPrefix + "SideStageListener");
        mSideStageListener.dump(pw, childPrefix);
        mSplitLayout.dump(pw, childPrefix);
        if (!mPausingTasks.isEmpty()) {
            pw.println(childPrefix + "mPausingTasks=" + mPausingTasks);
        }
    }

    /**
     * Directly set the visibility of both splits. This assumes hasChildren matches visibility.
     * This is intended for batch use, so it assumes other state management logic is already
     * handled.
     */
    private void setSplitsVisible(boolean visible) {
        ProtoLog.d(WM_SHELL_SPLIT_SCREEN, "setSplitsVisible: visible=%b", visible);
        mMainStageListener.mVisible = mSideStageListener.mVisible = visible;
        mMainStageListener.mHasChildren = mSideStageListener.mHasChildren = visible;
    }

    /**
     * Sets drag info to be logged when splitscreen is next entered.
     */
    public void onDroppedToSplit(@SplitPosition int position, InstanceId dragSessionId) {
        ProtoLog.d(WM_SHELL_SPLIT_SCREEN, "onDroppedToSplit: position=%d", position);
        if (!isSplitScreenVisible()) {
            mIsDropEntering = true;
            mSkipEvictingMainStageChildren = true;
        }
        if (!isSplitScreenVisible() && !ENABLE_SHELL_TRANSITIONS) {
            // If split running background, exit split first.
            // Skip this on shell transition due to we could evict existing tasks on transition
            // finished.
            exitSplitScreen(null /* childrenToTop */, EXIT_REASON_RECREATE_SPLIT);
        }
        mLogger.enterRequestedByDrag(position, dragSessionId);
    }

    /**
     * Sets info to be logged when splitscreen is next entered.
     */
    public void onRequestToSplit(InstanceId sessionId, @SplitEnterReason int enterReason) {
        ProtoLog.d(WM_SHELL_SPLIT_SCREEN, "onRequestToSplit: reason=%d", enterReason);
        if (!isSplitScreenVisible() && !ENABLE_SHELL_TRANSITIONS) {
            // If split running background, exit split first.
            // Skip this on shell transition due to we could evict existing tasks on transition
            // finished.
            exitSplitScreen(null /* childrenToTop */, EXIT_REASON_RECREATE_SPLIT);
        }
        mLogger.enterRequested(sessionId, enterReason);
    }

    /**
     * Logs the exit of splitscreen.
     */
    private void logExit(@ExitReason int exitReason) {
        mLogger.logExit(exitReason,
                SPLIT_POSITION_UNDEFINED, 0 /* mainStageUid */,
                SPLIT_POSITION_UNDEFINED, 0 /* sideStageUid */,
                mSplitLayout.isLeftRightSplit());
    }

    private void handleUnsupportedSplitStart() {
        mSplitUnsupportedToast.show();
        notifySplitAnimationFinished();
    }

    void notifySplitAnimationFinished() {
        if (mSplitInvocationListener == null || mSplitInvocationListenerExecutor == null) {
            return;
        }
        mSplitInvocationListenerExecutor.execute(() ->
                mSplitInvocationListener.onSplitAnimationInvoked(false /*animationRunning*/));
    }

    /**
     * Logs the exit of splitscreen to a specific stage. This must be called before the exit is
     * executed.
     */
    private void logExitToStage(@ExitReason int exitReason, boolean toMainStage) {
        mLogger.logExit(exitReason,
                toMainStage ? getMainStagePosition() : SPLIT_POSITION_UNDEFINED,
                toMainStage ? mMainStage.getTopChildTaskUid() : 0 /* mainStageUid */,
                !toMainStage ? getSideStagePosition() : SPLIT_POSITION_UNDEFINED,
                !toMainStage ? mSideStage.getTopChildTaskUid() : 0 /* sideStageUid */,
                mSplitLayout.isLeftRightSplit());
    }

    class StageListenerImpl implements StageTaskListener.StageListenerCallbacks {
        boolean mHasRootTask = false;
        boolean mVisible = false;
        boolean mHasChildren = false;

        @Override
        public void onRootTaskAppeared() {
            mHasRootTask = true;
            StageCoordinator.this.onRootTaskAppeared();
        }

        @Override
        public void onChildTaskAppeared(int taskId) {
            StageCoordinator.this.onChildTaskAppeared(this, taskId);
        }

        @Override
        public void onStatusChanged(boolean visible, boolean hasChildren) {
            if (!mHasRootTask) return;

            if (mHasChildren != hasChildren) {
                mHasChildren = hasChildren;
                StageCoordinator.this.onStageHasChildrenChanged(this);
            }
            if (mVisible != visible) {
                mVisible = visible;
                StageCoordinator.this.onStageVisibilityChanged(this);
            }
        }

        @Override
        public void onChildTaskStatusChanged(int taskId, boolean present, boolean visible) {
            StageCoordinator.this.onStageChildTaskStatusChanged(this, taskId, present, visible);
        }

        @Override
        public void onRootTaskVanished() {
            reset();
            StageCoordinator.this.onRootTaskVanished();
        }

        @Override
        public void onNoLongerSupportMultiWindow(ActivityManager.RunningTaskInfo taskInfo) {
            ProtoLog.d(WM_SHELL_SPLIT_SCREEN, "onNoLongerSupportMultiWindow: task=%s", taskInfo);
            if (mMainStage.isActive()) {
                final boolean isMainStage = mMainStageListener == this;
                if (!ENABLE_SHELL_TRANSITIONS) {
                    StageCoordinator.this.exitSplitScreen(isMainStage ? mMainStage : mSideStage,
                            EXIT_REASON_APP_DOES_NOT_SUPPORT_MULTIWINDOW);
                    handleUnsupportedSplitStart();
                    return;
                }

                // If visible, we preserve the app and keep it running. If an app becomes
                // unsupported in the bg, break split without putting anything on top
                boolean splitScreenVisible = isSplitScreenVisible();
                int stageType = STAGE_TYPE_UNDEFINED;
                if (splitScreenVisible) {
                    stageType = isMainStage ? STAGE_TYPE_MAIN : STAGE_TYPE_SIDE;
                }
                final WindowContainerTransaction wct = new WindowContainerTransaction();
                prepareExitSplitScreen(stageType, wct);
                clearSplitPairedInRecents(EXIT_REASON_APP_DOES_NOT_SUPPORT_MULTIWINDOW);
                mSplitTransitions.startDismissTransition(wct, StageCoordinator.this, stageType,
                        EXIT_REASON_APP_DOES_NOT_SUPPORT_MULTIWINDOW);
                Log.w(TAG, splitFailureMessage("onNoLongerSupportMultiWindow",
                        "app package " + taskInfo.baseActivity.getPackageName()
                        + " does not support splitscreen, or is a controlled activity type"));
                if (splitScreenVisible) {
                    handleUnsupportedSplitStart();
                }
            }
        }

        private void reset() {
            mHasRootTask = false;
            mVisible = false;
            mHasChildren = false;
        }

        public void dump(@NonNull PrintWriter pw, String prefix) {
            pw.println(prefix + "mHasRootTask=" + mHasRootTask);
            pw.println(prefix + "mVisible=" + mVisible);
            pw.println(prefix + "mHasChildren=" + mHasChildren);
        }
    }
}
