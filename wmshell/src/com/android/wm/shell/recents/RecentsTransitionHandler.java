/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.wm.shell.recents;

import static android.app.ActivityTaskManager.INVALID_TASK_ID;
import static android.app.WindowConfiguration.ACTIVITY_TYPE_HOME;
import static android.app.WindowConfiguration.ACTIVITY_TYPE_RECENTS;
import static android.view.WindowManager.KEYGUARD_VISIBILITY_TRANSIT_FLAGS;
import static android.view.WindowManager.TRANSIT_CHANGE;
import static android.view.WindowManager.TRANSIT_FLAG_KEYGUARD_LOCKED;
import static android.view.WindowManager.TRANSIT_PIP;
import static android.view.WindowManager.TRANSIT_SLEEP;
import static android.view.WindowManager.TRANSIT_TO_FRONT;
import static android.window.TransitionInfo.FLAG_TRANSLUCENT;

import static com.android.wm.shell.sysui.ShellSharedConstants.KEY_EXTRA_SHELL_CAN_HAND_OFF_ANIMATION;
import static com.android.wm.shell.util.SplitBounds.KEY_EXTRA_SPLIT_BOUNDS;

import android.annotation.Nullable;
import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.app.ActivityTaskManager;
import android.app.IApplicationThread;
import android.app.PendingIntent;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.ArrayMap;
import android.util.IntArray;
import android.util.Pair;
import android.util.Slog;
import android.view.Display;
import android.view.IRecentsAnimationController;
import android.view.IRecentsAnimationRunner;
import android.view.RemoteAnimationTarget;
import android.view.SurfaceControl;
import android.window.PictureInPictureSurfaceTransaction;
import android.window.TaskSnapshot;
import android.window.TransitionInfo;
import android.window.TransitionRequestInfo;
import android.window.WindowAnimationState;
import android.window.WindowContainerToken;
import android.window.WindowContainerTransaction;

import androidx.annotation.NonNull;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.os.IResultReceiver;
import com.android.internal.protolog.common.ProtoLog;
import com.android.wm.shell.common.ShellExecutor;
import com.android.wm.shell.common.pip.PipUtils;
import com.android.wm.shell.protolog.ShellProtoLogGroup;
import com.android.wm.shell.shared.TransitionUtil;
import com.android.wm.shell.sysui.ShellInit;
import com.android.wm.shell.transition.HomeTransitionObserver;
import com.android.wm.shell.transition.Transitions;

import java.util.ArrayList;
import java.util.function.Consumer;

/**
 * Handles the Recents (overview) animation. Only one of these can run at a time. A recents
 * transition must be created via {@link #startRecentsTransition}. Anything else will be ignored.
 */
public class RecentsTransitionHandler implements Transitions.TransitionHandler {
    private static final String TAG = "RecentsTransitionHandler";

    private final Transitions mTransitions;
    private final ShellExecutor mExecutor;
    @Nullable
    private final RecentTasksController mRecentTasksController;
    private IApplicationThread mAnimApp = null;
    private final ArrayList<RecentsController> mControllers = new ArrayList<>();
    private final ArrayList<RecentsTransitionStateListener> mStateListeners = new ArrayList<>();

    /**
     * List of other handlers which might need to mix recents with other things. These are checked
     * in the order they are added. Ideally there should only be one.
     */
    private final ArrayList<RecentsMixedHandler> mMixers = new ArrayList<>();

    private final HomeTransitionObserver mHomeTransitionObserver;
    private @Nullable Color mBackgroundColor;

    public RecentsTransitionHandler(ShellInit shellInit, Transitions transitions,
            @Nullable RecentTasksController recentTasksController,
            HomeTransitionObserver homeTransitionObserver) {
        mTransitions = transitions;
        mExecutor = transitions.getMainExecutor();
        mRecentTasksController = recentTasksController;
        mHomeTransitionObserver = homeTransitionObserver;
        if (!Transitions.ENABLE_SHELL_TRANSITIONS) return;
        if (recentTasksController == null) return;
        shellInit.addInitCallback(() -> {
            recentTasksController.setTransitionHandler(this);
            transitions.addHandler(this);
        }, this);
    }

    /** Register a mixer handler. {@see RecentsMixedHandler}*/
    public void addMixer(RecentsMixedHandler mixer) {
        mMixers.add(mixer);
    }

    /** Unregister a Mixed Handler */
    public void removeMixer(RecentsMixedHandler mixer) {
        mMixers.remove(mixer);
    }

    /** Adds the callback for receiving the state change of transition. */
    public void addTransitionStateListener(RecentsTransitionStateListener listener) {
        mStateListeners.add(listener);
    }

    /**
     * Sets a background color on the transition root layered behind the outgoing task. {@code null}
     * may be used to clear any previously set colors to avoid showing a background at all. The
     * color is always shown at full opacity.
     */
    public void setTransitionBackgroundColor(@Nullable Color color) {
        mBackgroundColor = color;
    }

    @VisibleForTesting
    public IBinder startRecentsTransition(PendingIntent intent, Intent fillIn, Bundle options,
            IApplicationThread appThread, IRecentsAnimationRunner listener) {
        ProtoLog.v(ShellProtoLogGroup.WM_SHELL_RECENTS_TRANSITION,
                "RecentsTransitionHandler.startRecentsTransition");

        // only care about latest one.
        mAnimApp = appThread;
        WindowContainerTransaction wct = new WindowContainerTransaction();
        wct.sendPendingIntent(intent, fillIn, options);
        final RecentsController controller = new RecentsController(listener);
        RecentsMixedHandler mixer = null;
        Consumer<IBinder> setTransitionForMixer = null;
        for (int i = 0; i < mMixers.size(); ++i) {
            setTransitionForMixer = mMixers.get(i).handleRecentsRequest(wct);
            if (setTransitionForMixer != null) {
                mixer = mMixers.get(i);
                break;
            }
        }
        final IBinder transition = mTransitions.startTransition(TRANSIT_TO_FRONT, wct,
                mixer == null ? this : mixer);
        for (int i = 0; i < mStateListeners.size(); i++) {
            mStateListeners.get(i).onTransitionStarted(transition);
        }
        if (mixer != null) {
            setTransitionForMixer.accept(transition);
        }
        if (transition != null) {
            controller.setTransition(transition);
            mControllers.add(controller);
        } else {
            controller.cancel("startRecentsTransition");
        }
        return transition;
    }

    @Override
    public WindowContainerTransaction handleRequest(IBinder transition,
            TransitionRequestInfo request) {
        if (mControllers.isEmpty()) {
            // Ignore if there is no running recents transition
            return null;
        }
        final RecentsController controller = mControllers.get(mControllers.size() - 1);
        controller.handleMidTransitionRequest(request);
        return null;
    }

    private int findController(IBinder transition) {
        for (int i = mControllers.size() - 1; i >= 0; --i) {
            if (mControllers.get(i).mTransition == transition) return i;
        }
        return -1;
    }

    @Override
    public boolean startAnimation(IBinder transition, TransitionInfo info,
            SurfaceControl.Transaction startTransaction,
            SurfaceControl.Transaction finishTransaction,
            Transitions.TransitionFinishCallback finishCallback) {
        final int controllerIdx = findController(transition);
        if (controllerIdx < 0) {
            ProtoLog.v(ShellProtoLogGroup.WM_SHELL_RECENTS_TRANSITION,
                    "RecentsTransitionHandler.startAnimation: no controller found");
            return false;
        }
        final RecentsController controller = mControllers.get(controllerIdx);
        final IApplicationThread animApp = mAnimApp;
        mAnimApp = null;
        if (!controller.start(info, startTransaction, finishTransaction, finishCallback)) {
            ProtoLog.v(ShellProtoLogGroup.WM_SHELL_RECENTS_TRANSITION,
                    "RecentsTransitionHandler.startAnimation: failed to start animation");
            return false;
        }
        Transitions.setRunningRemoteTransitionDelegate(animApp);
        return true;
    }

    @Override
    public void mergeAnimation(IBinder transition, TransitionInfo info,
            SurfaceControl.Transaction t, IBinder mergeTarget,
            Transitions.TransitionFinishCallback finishCallback) {
        final int targetIdx = findController(mergeTarget);
        if (targetIdx < 0) {
            ProtoLog.v(ShellProtoLogGroup.WM_SHELL_RECENTS_TRANSITION,
                    "RecentsTransitionHandler.mergeAnimation: no controller found");
            return;
        }
        final RecentsController controller = mControllers.get(targetIdx);
        controller.merge(info, t, finishCallback);
    }

    @Override
    public void onTransitionConsumed(IBinder transition, boolean aborted,
            SurfaceControl.Transaction finishTransaction) {
        // Only one recents transition can be handled at a time, but currently the first transition
        // will trigger a no-op in the second transition which holds the active recents animation
        // runner on the launcher side.  For now, cancel all existing animations to ensure we
        // don't get into a broken state with an orphaned animation runner, and later we can try to
        // merge the latest transition into the currently running one
        for (int i = mControllers.size() - 1; i >= 0; i--) {
            mControllers.get(i).cancel("onTransitionConsumed");
        }
    }

    /** There is only one of these and it gets reset on finish. */
    private class RecentsController extends IRecentsAnimationController.Stub {
        private final int mInstanceId;

        private IRecentsAnimationRunner mListener;
        private IBinder.DeathRecipient mDeathHandler;
        private Transitions.TransitionFinishCallback mFinishCB = null;
        private SurfaceControl.Transaction mFinishTransaction = null;

        /**
         * List of tasks that we are switching away from via this transition. Upon finish, these
         * pausing tasks will become invisible.
         * These need to be ordered since the order must be restored if there is no task-switch.
         */
        private ArrayList<TaskState> mPausingTasks = null;

        /**
         * List of tasks were pausing but closed in a subsequent merged transition. If a
         * closing task is reopened, the leash is not initially hidden since it is already
         * visible.
         */
        private ArrayList<TaskState> mClosingTasks = null;

        /**
         * List of tasks that we are switching to. Upon finish, these will remain visible and
         * on top.
         */
        private ArrayList<TaskState> mOpeningTasks = null;

        private WindowContainerToken mPipTask = null;
        private int mPipTaskId = -1;
        private WindowContainerToken mRecentsTask = null;
        private int mRecentsTaskId = -1;
        private TransitionInfo mInfo = null;
        private boolean mOpeningSeparateHome = false;
        private boolean mPausingSeparateHome = false;
        private ArrayMap<SurfaceControl, SurfaceControl> mLeashMap = null;
        private PictureInPictureSurfaceTransaction mPipTransaction = null;
        private IBinder mTransition = null;
        private boolean mKeyguardLocked = false;
        private boolean mWillFinishToHome = false;
        private Transitions.TransitionHandler mTakeoverHandler = null;

        /** The animation is idle, waiting for the user to choose a task to switch to. */
        private static final int STATE_NORMAL = 0;

        /** The user chose a new task to switch to and the animation is animating to it. */
        private static final int STATE_NEW_TASK = 1;

        /** The latest state that the recents animation is operating in. */
        private int mState = STATE_NORMAL;

        // Snapshots taken when a new display change transition is requested, prior to the display
        // change being applied.  This pending set of snapshots will only be applied when cancel is
        // next called.
        private Pair<int[], TaskSnapshot[]> mPendingPauseSnapshotsForCancel;

        RecentsController(IRecentsAnimationRunner listener) {
            mInstanceId = System.identityHashCode(this);
            mListener = listener;
            mDeathHandler = () -> {
                ProtoLog.v(ShellProtoLogGroup.WM_SHELL_RECENTS_TRANSITION,
                        "[%d] RecentsController.DeathRecipient: binder died", mInstanceId);
                finish(mWillFinishToHome, false /* leaveHint */, null /* finishCb */);
            };
            try {
                mListener.asBinder().linkToDeath(mDeathHandler, 0 /* flags */);
            } catch (RemoteException e) {
                Slog.e(TAG, "RecentsController: failed to link to death", e);
                mListener = null;
            }
        }

        void setTransition(IBinder transition) {
            ProtoLog.v(ShellProtoLogGroup.WM_SHELL_RECENTS_TRANSITION,
                    "[%d] RecentsController.setTransition: id=%s", mInstanceId, transition);
            mTransition = transition;
        }

        void cancel(String reason) {
            // restoring (to-home = false) involves submitting more WM changes, so by default, use
            // toHome = true when canceling.
            cancel(true /* toHome */, false /* withScreenshots */, reason);
        }

        void cancel(boolean toHome, boolean withScreenshots, String reason) {
            ProtoLog.v(ShellProtoLogGroup.WM_SHELL_RECENTS_TRANSITION,
                    "[%d] RecentsController.cancel: toHome=%b reason=%s",
                    mInstanceId, toHome, reason);
            if (mListener != null) {
                if (withScreenshots) {
                    sendCancelWithSnapshots();
                } else {
                    sendCancel(null, null);
                }
            }
            if (mFinishCB != null) {
                finishInner(toHome, false /* userLeave */, null /* finishCb */);
            } else {
                cleanUp();
            }
        }

        /**
         * Sends a cancel message to the recents animation with snapshots. Used to trigger a
         * "replace-with-screenshot" like behavior.
         */
        private boolean sendCancelWithSnapshots() {
            Pair<int[], TaskSnapshot[]> snapshots = mPendingPauseSnapshotsForCancel != null
                    ? mPendingPauseSnapshotsForCancel
                    : getSnapshotsForPausingTasks();
            return sendCancel(snapshots.first, snapshots.second);
        }

        /**
         * Snapshots the pausing tasks and returns the mapping of the taskId -> snapshot.
         */
        private Pair<int[], TaskSnapshot[]> getSnapshotsForPausingTasks() {
            int[] taskIds = null;
            TaskSnapshot[] snapshots = null;
            if (mPausingTasks != null && mPausingTasks.size() > 0) {
                taskIds = new int[mPausingTasks.size()];
                snapshots = new TaskSnapshot[mPausingTasks.size()];
                try {
                    for (int i = 0; i < mPausingTasks.size(); ++i) {
                        TaskState state = mPausingTasks.get(0);
                        ProtoLog.v(ShellProtoLogGroup.WM_SHELL_RECENTS_TRANSITION,
                                "[%d] RecentsController.sendCancel: Snapshotting task=%d",
                                mInstanceId, state.mTaskInfo.taskId);
                        snapshots[i] = ActivityTaskManager.getService().takeTaskSnapshot(
                                state.mTaskInfo.taskId, true /* updateCache */);
                    }
                } catch (RemoteException e) {
                    taskIds = null;
                    snapshots = null;
                }
            }
            return new Pair(taskIds, snapshots);
        }

        /**
         * Sends a cancel message to the recents animation.
         */
        private boolean sendCancel(@Nullable int[] taskIds,
                @Nullable TaskSnapshot[] taskSnapshots) {
            try {
                final String cancelDetails = taskSnapshots != null ? "with snapshots" : "";
                ProtoLog.v(ShellProtoLogGroup.WM_SHELL_RECENTS_TRANSITION,
                        "[%d] RecentsController.cancel: calling onAnimationCanceled %s",
                        mInstanceId, cancelDetails);
                mListener.onAnimationCanceled(taskIds, taskSnapshots);
                return true;
            } catch (RemoteException e) {
                Slog.e(TAG, "Error canceling recents animation", e);
                return false;
            }
        }

        /**
         * Cleans up the recents transition.  This should generally not be called directly
         * to cancel a transition after it has started, instead callers should call one of
         * the cancel() methods to ensure that Launcher is notified.
         */
        void cleanUp() {
            ProtoLog.v(ShellProtoLogGroup.WM_SHELL_RECENTS_TRANSITION,
                    "[%d] RecentsController.cleanup", mInstanceId);
            if (mListener != null && mDeathHandler != null) {
                mListener.asBinder().unlinkToDeath(mDeathHandler, 0 /* flags */);
                mDeathHandler = null;
            }
            mListener = null;
            mFinishCB = null;
            // clean-up leash surfacecontrols and anything that might reference them.
            if (mLeashMap != null) {
                for (int i = 0; i < mLeashMap.size(); ++i) {
                    mLeashMap.valueAt(i).release();
                }
                mLeashMap = null;
            }
            mFinishTransaction = null;
            mPausingTasks = null;
            mClosingTasks = null;
            mOpeningTasks = null;
            mInfo = null;
            mTransition = null;
            mPendingPauseSnapshotsForCancel = null;
            mControllers.remove(this);
            for (int i = 0; i < mStateListeners.size(); i++) {
                mStateListeners.get(i).onAnimationStateChanged(false);
            }
        }

        boolean start(TransitionInfo info, SurfaceControl.Transaction t,
                SurfaceControl.Transaction finishT, Transitions.TransitionFinishCallback finishCB) {
            ProtoLog.v(ShellProtoLogGroup.WM_SHELL_RECENTS_TRANSITION,
                    "[%d] RecentsController.start", mInstanceId);
            if (mListener == null || mTransition == null) {
                Slog.e(TAG, "Missing listener or transition, hasListener=" + (mListener != null) +
                        " hasTransition=" + (mTransition != null));
                cancel("No listener (" + (mListener == null)
                        + ") or no transition (" + (mTransition == null) + ")");
                return false;
            }
            // First see if this is a valid recents transition.
            boolean hasPausingTasks = false;
            for (int i = 0; i < info.getChanges().size(); ++i) {
                final TransitionInfo.Change change = info.getChanges().get(i);
                if (TransitionUtil.isWallpaper(change)) continue;
                if (TransitionUtil.isClosingType(change.getMode())) {
                    hasPausingTasks = true;
                    continue;
                }
                final ActivityManager.RunningTaskInfo taskInfo = change.getTaskInfo();
                if (taskInfo != null && taskInfo.topActivityType == ACTIVITY_TYPE_RECENTS) {
                    mRecentsTask = taskInfo.token;
                    mRecentsTaskId = taskInfo.taskId;
                } else if (taskInfo != null && taskInfo.topActivityType == ACTIVITY_TYPE_HOME) {
                    mRecentsTask = taskInfo.token;
                    mRecentsTaskId = taskInfo.taskId;
                }
            }
            if (mRecentsTask == null && !hasPausingTasks) {
                // Recents is already running apparently, so this is a no-op.
                Slog.e(TAG, "Tried to start recents while it is already running.");
                cancel("No recents task and no pausing tasks");
                return false;
            }

            mInfo = info;
            mFinishCB = finishCB;
            mFinishTransaction = finishT;
            mPausingTasks = new ArrayList<>();
            mClosingTasks = new ArrayList<>();
            mOpeningTasks = new ArrayList<>();
            mLeashMap = new ArrayMap<>();
            mKeyguardLocked = (info.getFlags() & TRANSIT_FLAG_KEYGUARD_LOCKED) != 0;

            int closingSplitTaskId = INVALID_TASK_ID;
            final ArrayList<RemoteAnimationTarget> apps = new ArrayList<>();
            final ArrayList<RemoteAnimationTarget> wallpapers = new ArrayList<>();
            TransitionUtil.LeafTaskFilter leafTaskFilter = new TransitionUtil.LeafTaskFilter();
            // About layering: we divide up the "layer space" into 3 regions (each the size of
            // the change count). This lets us categorize things into above/below/between
            // while maintaining their relative ordering.
            final int belowLayers = info.getChanges().size();
            final int middleLayers = info.getChanges().size() * 2;
            final int aboveLayers = info.getChanges().size() * 3;

            // Add a background color to each transition root in this transition.
            if (mBackgroundColor != null) {
                info.getChanges().stream()
                        .mapToInt((change) -> TransitionUtil.rootIndexFor(change, info))
                        .distinct()
                        .mapToObj((rootIndex) -> info.getRoot(rootIndex).getLeash())
                        .forEach((root) -> createBackgroundSurface(t, root, middleLayers));
            }

            for (int i = 0; i < info.getChanges().size(); ++i) {
                final TransitionInfo.Change change = info.getChanges().get(i);
                final ActivityManager.RunningTaskInfo taskInfo = change.getTaskInfo();
                if (TransitionUtil.isWallpaper(change)) {
                    final RemoteAnimationTarget target = TransitionUtil.newTarget(change,
                            // wallpapers go into the "below" layer space
                            belowLayers - i, info, t, mLeashMap);
                    wallpapers.add(target);
                    // Make all the wallpapers opaque since we want them visible from the start
                    t.setAlpha(target.leash, 1);
                } else if (leafTaskFilter.test(change)) {
                    // start by putting everything into the "below" layer space.
                    final RemoteAnimationTarget target = TransitionUtil.newTarget(change,
                            belowLayers - i, info, t, mLeashMap);
                    apps.add(target);
                    if (TransitionUtil.isClosingType(change.getMode())) {
                        mPausingTasks.add(new TaskState(change, target.leash));
                        closingSplitTaskId = change.getTaskInfo().taskId;
                        if (taskInfo.topActivityType == ACTIVITY_TYPE_HOME) {
                            ProtoLog.v(ShellProtoLogGroup.WM_SHELL_RECENTS_TRANSITION,
                                    "  adding pausing leaf home taskId=%d", taskInfo.taskId);
                            // This can only happen if we have a separate recents/home (3p launcher)
                            mPausingSeparateHome = true;
                        } else {
                            final int layer = aboveLayers - i;
                            ProtoLog.v(ShellProtoLogGroup.WM_SHELL_RECENTS_TRANSITION,
                                    "  adding pausing leaf taskId=%d at layer=%d",
                                    taskInfo.taskId, layer);
                            // raise closing (pausing) task to "above" layer so it isn't covered
                            t.setLayer(target.leash, layer);
                        }
                        if (taskInfo.pictureInPictureParams != null
                                && taskInfo.pictureInPictureParams.isAutoEnterEnabled()) {
                            mPipTask = taskInfo.token;
                        }
                    } else if (taskInfo != null
                            && taskInfo.topActivityType == ACTIVITY_TYPE_RECENTS) {
                        final int layer = middleLayers - i;
                        ProtoLog.v(ShellProtoLogGroup.WM_SHELL_RECENTS_TRANSITION,
                                "  setting recents activity layer=%d", layer);
                        // There's a 3p launcher, so make sure recents goes above that, but under
                        // the pausing apps.
                        t.setLayer(target.leash, layer);
                    } else if (taskInfo != null && taskInfo.topActivityType == ACTIVITY_TYPE_HOME) {
                        // do nothing
                    } else if (TransitionUtil.isOpeningType(change.getMode())) {
                        ProtoLog.v(ShellProtoLogGroup.WM_SHELL_RECENTS_TRANSITION,
                                "  adding opening leaf taskId=%d", taskInfo.taskId);
                        mOpeningTasks.add(new TaskState(change, target.leash));
                    }
                } else if (taskInfo != null && TransitionInfo.isIndependent(change, info)) {
                    // Root tasks
                    if (TransitionUtil.isClosingType(change.getMode())) {
                        final int layer = aboveLayers - i;
                        ProtoLog.v(ShellProtoLogGroup.WM_SHELL_RECENTS_TRANSITION,
                                "  adding pausing taskId=%d at layer=%d", taskInfo.taskId, layer);
                        // raise closing (pausing) task to "above" layer so it isn't covered
                        t.setLayer(change.getLeash(), layer);
                        mPausingTasks.add(new TaskState(change, null /* leash */));
                    } else if (TransitionUtil.isOpeningType(change.getMode())) {
                        final int layer = belowLayers - i;
                        ProtoLog.v(ShellProtoLogGroup.WM_SHELL_RECENTS_TRANSITION,
                                "  adding opening taskId=%d at layer=%d", taskInfo.taskId, layer);
                        // Put into the "below" layer space.
                        t.setLayer(change.getLeash(), layer);
                        mOpeningTasks.add(new TaskState(change, null /* leash */));
                    } else {
                        ProtoLog.v(ShellProtoLogGroup.WM_SHELL_RECENTS_TRANSITION,
                                "  unhandled root taskId=%d", taskInfo.taskId);
                    }
                } else if (TransitionUtil.isDividerBar(change)) {
                    final RemoteAnimationTarget target = TransitionUtil.newTarget(change,
                            belowLayers - i, info, t, mLeashMap);
                    // Add this as a app and we will separate them on launcher side by window type.
                    apps.add(target);
                } else {
                    ProtoLog.v(ShellProtoLogGroup.WM_SHELL_RECENTS_TRANSITION,
                            "  unhandled change taskId=%d",
                            taskInfo != null ? taskInfo.taskId : -1);
                }
            }
            ProtoLog.v(ShellProtoLogGroup.WM_SHELL_RECENTS_TRANSITION,
                    "Applying transaction=%d", t.getId());
            t.apply();

            mTakeoverHandler = mTransitions.getHandlerForTakeover(mTransition, info);

            Bundle b = new Bundle(2 /*capacity*/);
            b.putParcelable(KEY_EXTRA_SPLIT_BOUNDS,
                    mRecentTasksController.getSplitBoundsForTaskId(closingSplitTaskId));
            b.putBoolean(KEY_EXTRA_SHELL_CAN_HAND_OFF_ANIMATION, mTakeoverHandler != null);
            try {
                ProtoLog.v(ShellProtoLogGroup.WM_SHELL_RECENTS_TRANSITION,
                        "[%d] RecentsController.start: calling onAnimationStart with %d apps",
                        mInstanceId, apps.size());
                mListener.onAnimationStart(this,
                        apps.toArray(new RemoteAnimationTarget[apps.size()]),
                        wallpapers.toArray(new RemoteAnimationTarget[wallpapers.size()]),
                        new Rect(0, 0, 0, 0), new Rect(), b);
                for (int i = 0; i < mStateListeners.size(); i++) {
                    mStateListeners.get(i).onAnimationStateChanged(true);
                }
            } catch (RemoteException e) {
                Slog.e(TAG, "Error starting recents animation", e);
                cancel("onAnimationStart() failed");
            }
            return true;
        }

        @Override
        public void handOffAnimation(
                RemoteAnimationTarget[] targets, WindowAnimationState[] states) {
            mExecutor.execute(() -> {
                ProtoLog.v(ShellProtoLogGroup.WM_SHELL_RECENTS_TRANSITION,
                        "[%d] RecentsController.handOffAnimation", mInstanceId);

                if (mTakeoverHandler == null) {
                    Slog.e(TAG, "Tried to hand off an animation without a valid takeover "
                            + "handler.");
                    return;
                }

                if (targets.length != states.length) {
                    Slog.e(TAG, "Tried to hand off an animation, but the number of targets "
                            + "(" + targets.length + ") doesn't match the number of states "
                            + "(" + states.length + ")");
                    return;
                }

                ProtoLog.v(ShellProtoLogGroup.WM_SHELL_RECENTS_TRANSITION,
                        "[%d] RecentsController.handOffAnimation: got %d states for %d "
                                + "changes", mInstanceId, states.length, mInfo.getChanges().size());
                WindowAnimationState[] updatedStates =
                        new WindowAnimationState[mInfo.getChanges().size()];

                // Ensure that the ordering of animation states is the same as that of  matching
                // changes in mInfo. prefixOrderIndex is set up in reverse order to that of the
                // changes, so that's what we use to get to the correct ordering.
                for (int i = 0; i < targets.length; i++) {
                    RemoteAnimationTarget target = targets[i];
                    updatedStates[updatedStates.length - target.prefixOrderIndex] = states[i];
                }

                Transitions.TransitionFinishCallback finishCB = mFinishCB;
                // Reset the callback here, so any stray calls that aren't coming from the new
                // handler are ignored.
                mFinishCB = null;

                ProtoLog.v(ShellProtoLogGroup.WM_SHELL_RECENTS_TRANSITION,
                        "[%d] RecentsController.handOffAnimation: calling "
                                + "takeOverAnimation with %d states", mInstanceId,
                        updatedStates.length);
                mTakeoverHandler.takeOverAnimation(
                        mTransition, mInfo, new SurfaceControl.Transaction(),
                        wct -> {
                            ProtoLog.v(ShellProtoLogGroup.WM_SHELL_RECENTS_TRANSITION,
                                    "[%d] RecentsController.handOffAnimation: finish "
                                            + "callback", mInstanceId);
                            // Set the callback once again so we can finish correctly.
                            mFinishCB = finishCB;
                            finishInner(true /* toHome */, false /* userLeave */,
                                    null /* finishCb */);
                        }, updatedStates);
            });
        }

        /**
         * Updates this controller when a new transition is requested mid-recents transition.
         */
        void handleMidTransitionRequest(TransitionRequestInfo request) {
            if (request.getType() == TRANSIT_CHANGE && request.getDisplayChange() != null) {
                final TransitionRequestInfo.DisplayChange dispChange = request.getDisplayChange();
                if (dispChange.getStartRotation() != dispChange.getEndRotation()) {
                    ProtoLog.v(ShellProtoLogGroup.WM_SHELL_RECENTS_TRANSITION,
                            "[%d] RecentsController.prepareForMerge: "
                                    + "Snapshot due to requested display change",
                            mInstanceId);
                    mPendingPauseSnapshotsForCancel = getSnapshotsForPausingTasks();
                }
            }
        }

        @SuppressLint("NewApi")
        void merge(TransitionInfo info, SurfaceControl.Transaction t,
                Transitions.TransitionFinishCallback finishCallback) {
            if (mFinishCB == null) {
                ProtoLog.v(ShellProtoLogGroup.WM_SHELL_RECENTS_TRANSITION,
                        "[%d] RecentsController.merge: skip, no finish callback",
                        mInstanceId);
                // This was no-op'd (likely a repeated start) and we've already sent finish.
                return;
            }
            if (info.getType() == TRANSIT_SLEEP) {
                ProtoLog.v(ShellProtoLogGroup.WM_SHELL_RECENTS_TRANSITION,
                        "[%d] RecentsController.merge: transit_sleep", mInstanceId);
                // A sleep event means we need to stop animations immediately, so cancel here.
                cancel("transit_sleep");
                return;
            }
            if (mKeyguardLocked || (info.getFlags() & KEYGUARD_VISIBILITY_TRANSIT_FLAGS) != 0) {
                ProtoLog.v(ShellProtoLogGroup.WM_SHELL_RECENTS_TRANSITION,
                        "[%d] RecentsController.merge: keyguard is locked", mInstanceId);
                // We will not accept new changes if we are swiping over the keyguard.
                cancel(true /* toHome */, false /* withScreenshots */, "keyguard_locked");
                return;
            }
            ProtoLog.v(ShellProtoLogGroup.WM_SHELL_RECENTS_TRANSITION,
                    "[%d] RecentsController.merge", mInstanceId);
            // Keep all tasks in one list because order matters.
            ArrayList<TransitionInfo.Change> openingTasks = null;
            IntArray openingTaskIsLeafs = null;
            ArrayList<TransitionInfo.Change> closingTasks = null;
            mOpeningSeparateHome = false;
            TransitionInfo.Change recentsOpening = null;
            boolean foundRecentsClosing = false;
            boolean hasChangingApp = false;
            final TransitionUtil.LeafTaskFilter leafTaskFilter =
                    new TransitionUtil.LeafTaskFilter();
            boolean hasTaskChange = false;
            for (int i = 0; i < info.getChanges().size(); ++i) {
                final TransitionInfo.Change change = info.getChanges().get(i);
                final ActivityManager.RunningTaskInfo taskInfo = change.getTaskInfo();
                if (taskInfo != null
                        && taskInfo.configuration.windowConfiguration.isAlwaysOnTop()) {
                    // Tasks that are always on top (e.g. bubbles), will handle their own transition
                    // as they are on top of everything else. So cancel the merge here.
                    cancel(false /* toHome */, false /* withScreenshots */,
                            "task #" + taskInfo.taskId + " is always_on_top");
                    return;
                }
                final boolean isRootTask = taskInfo != null
                        && TransitionInfo.isIndependent(change, info);
                final boolean isRecentsTask = mRecentsTask != null
                        && mRecentsTask.equals(change.getContainer());
                hasTaskChange = hasTaskChange || isRootTask;
                final boolean isLeafTask = leafTaskFilter.test(change);
                if (TransitionUtil.isOpeningType(change.getMode())
                        || TransitionUtil.isOrderOnly(change)) {
                    if (isRecentsTask) {
                        recentsOpening = change;
                    } else if (isRootTask || isLeafTask) {
                        if (isLeafTask && taskInfo.topActivityType == ACTIVITY_TYPE_HOME) {
                            // This is usually a 3p launcher
                            mOpeningSeparateHome = true;
                        }
                        if (openingTasks == null) {
                            openingTasks = new ArrayList<>();
                            openingTaskIsLeafs = new IntArray();
                        }
                        openingTasks.add(change);
                        openingTaskIsLeafs.add(isLeafTask ? 1 : 0);
                    }
                } else if (TransitionUtil.isClosingType(change.getMode())) {
                    if (isRecentsTask) {
                        foundRecentsClosing = true;
                    } else if (isRootTask || isLeafTask) {
                        if (closingTasks == null) {
                            closingTasks = new ArrayList<>();
                        }
                        closingTasks.add(change);
                    }
                } else if (change.getMode() == TRANSIT_CHANGE) {
                    // Finish recents animation if the display is changed, so the default
                    // transition handler can play the animation such as rotation effect.
                    if (change.hasFlags(TransitionInfo.FLAG_IS_DISPLAY)
                            && info.getType() == TRANSIT_CHANGE) {
                        // This call to cancel will use the screenshots taken preemptively in
                        // handleMidTransitionRequest() prior to the display changing
                        cancel(mWillFinishToHome, true /* withScreenshots */, "display change");
                        return;
                    }
                    // Don't consider order-only & non-leaf changes as changing apps.
                    if (!TransitionUtil.isOrderOnly(change) && isLeafTask) {
                        hasChangingApp = true;
                    } else if (isLeafTask && taskInfo.topActivityType == ACTIVITY_TYPE_HOME
                            && !isRecentsTask ) {
                        // Unless it is a 3p launcher. This means that the 3p launcher was already
                        // visible (eg. the "pausing" task is translucent over the 3p launcher).
                        // Treat it as if we are "re-opening" the 3p launcher.
                        if (openingTasks == null) {
                            openingTasks = new ArrayList<>();
                            openingTaskIsLeafs = new IntArray();
                        }
                        openingTasks.add(change);
                        openingTaskIsLeafs.add(1);
                    }
                }
            }
            if (hasChangingApp && foundRecentsClosing) {
                // This happens when a visible app is expanding (usually PiP). In this case,
                // that transition probably has a special-purpose animation, so finish recents
                // now and let it do its animation (since recents is going to be occluded).
                sendCancelWithSnapshots();
                mExecutor.executeDelayed(
                        () -> finishInner(true /* toHome */, false /* userLeaveHint */,
                                null /* finishCb */), 0);
                return;
            }
            if (recentsOpening != null) {
                // the recents task re-appeared. This happens if the user gestures before the
                // task-switch (NEW_TASK) animation finishes.
                if (mState == STATE_NORMAL) {
                    Slog.e(TAG, "Returning to recents while recents is already idle.");
                }
                if (closingTasks == null || closingTasks.size() == 0) {
                    Slog.e(TAG, "Returning to recents without closing any opening tasks.");
                }
                // Setup may hide it initially since it doesn't know that overview was still active.
                t.show(recentsOpening.getLeash());
                t.setAlpha(recentsOpening.getLeash(), 1.f);
                mState = STATE_NORMAL;
            }
            boolean didMergeThings = false;
            if (closingTasks != null) {
                // Potentially cancelling a task-switch. Move the tasks back to mPausing if they
                // are in mOpening.
                for (int i = 0; i < closingTasks.size(); ++i) {
                    final TransitionInfo.Change change = closingTasks.get(i);
                    final int pausingIdx = TaskState.indexOf(mPausingTasks, change);
                    if (pausingIdx >= 0) {
                        // We are closing the pausing task, but it is still visible and can be
                        // restart by another transition prior to this transition finishing
                        final TaskState closingTask = mPausingTasks.remove(pausingIdx);
                        mClosingTasks.add(closingTask);
                        didMergeThings = true;
                        ProtoLog.v(ShellProtoLogGroup.WM_SHELL_RECENTS_TRANSITION,
                                "  closing pausing taskId=%d", change.getTaskInfo().taskId);
                        continue;
                    }
                    int openingIdx = TaskState.indexOf(mOpeningTasks, change);
                    if (openingIdx < 0) {
                        Slog.w(TAG, "Closing a task that wasn't opening, this may be split or"
                                + " something unexpected: " + change.getTaskInfo().taskId);
                        continue;
                    }
                    final TaskState openingTask = mOpeningTasks.remove(openingIdx);
                    ProtoLog.v(ShellProtoLogGroup.WM_SHELL_RECENTS_TRANSITION,
                            "  pausing opening %staskId=%d", openingTask.isLeaf() ? "leaf " : "",
                            openingTask.mTaskInfo.taskId);
                    mPausingTasks.add(openingTask);
                    didMergeThings = true;
                }
            }
            RemoteAnimationTarget[] appearedTargets = null;
            if (openingTasks != null && openingTasks.size() > 0) {
                // Switching to some new tasks, add to mOpening and remove from mPausing. Also,
                // enter NEW_TASK state since this will start the switch-to animation.
                final int layer = mInfo.getChanges().size() * 3;
                int openingLeafCount = 0;
                for (int i = 0; i < openingTaskIsLeafs.size(); ++i) {
                    openingLeafCount += openingTaskIsLeafs.get(i);
                }
                if (openingLeafCount > 0) {
                    appearedTargets = new RemoteAnimationTarget[openingLeafCount];
                }
                int nextTargetIdx = 0;
                for (int i = 0; i < openingTasks.size(); ++i) {
                    final TransitionInfo.Change change = openingTasks.get(i);
                    final boolean isLeaf = openingTaskIsLeafs.get(i) == 1;
                    final int closingIdx = TaskState.indexOf(mClosingTasks, change);
                    if (closingIdx >= 0) {
                        // Remove opening tasks from closing set
                        mClosingTasks.remove(closingIdx);
                    }
                    final int pausingIdx = TaskState.indexOf(mPausingTasks, change);
                    if (pausingIdx >= 0) {
                        // Something is showing/opening a previously-pausing app.
                        if (isLeaf) {
                            appearedTargets[nextTargetIdx++] = TransitionUtil.newTarget(
                                    change, layer, mPausingTasks.get(pausingIdx).mLeash);
                        }
                        final TaskState pausingTask = mPausingTasks.remove(pausingIdx);
                        ProtoLog.v(ShellProtoLogGroup.WM_SHELL_RECENTS_TRANSITION,
                                "  opening pausing %staskId=%d", isLeaf ? "leaf " : "",
                                pausingTask.mTaskInfo.taskId);
                        mOpeningTasks.add(pausingTask);
                        // Setup hides opening tasks initially, so make it visible again (since we
                        // are already showing it).
                        t.show(change.getLeash());
                        t.setAlpha(change.getLeash(), 1.f);
                    } else if (isLeaf) {
                        // We are receiving new opening leaf tasks, so convert to onTasksAppeared.
                        final RemoteAnimationTarget target = TransitionUtil.newTarget(
                                change, layer, info, t, mLeashMap);
                        appearedTargets[nextTargetIdx++] = target;
                        // reparent into the original `mInfo` since that's where we are animating.
                        final int rootIdx = TransitionUtil.rootIndexFor(change, mInfo);
                        final boolean wasClosing = closingIdx >= 0;
                        t.reparent(target.leash, mInfo.getRoot(rootIdx).getLeash());
                        t.setLayer(target.leash, layer);
                        if (wasClosing) {
                            // App was previously visible and is closing
                            t.show(target.leash);
                            t.setAlpha(target.leash, 1f);
                            // Also override the task alpha as it was set earlier when dispatching
                            // the transition and setting up the leash to hide the
                            t.setAlpha(change.getLeash(), 1f);
                        } else {
                            // Hide the animation leash, let the listener show it
                            t.hide(target.leash);
                        }
                        ProtoLog.v(ShellProtoLogGroup.WM_SHELL_RECENTS_TRANSITION,
                                "  opening new leaf taskId=%d wasClosing=%b",
                                target.taskId, wasClosing);
                        mOpeningTasks.add(new TaskState(change, target.leash));
                    } else {
                        ProtoLog.v(ShellProtoLogGroup.WM_SHELL_RECENTS_TRANSITION,
                                "  opening new taskId=%d", change.getTaskInfo().taskId);
                        t.setLayer(change.getLeash(), layer);
                        // Setup hides opening tasks initially, so make it visible since recents
                        // is only animating the leafs.
                        t.show(change.getLeash());
                        mOpeningTasks.add(new TaskState(change, null));
                    }
                }
                didMergeThings = true;
                mState = STATE_NEW_TASK;
            }
            if (mPausingTasks.isEmpty()) {
                // The pausing tasks may be removed by the incoming closing tasks.
                ProtoLog.v(ShellProtoLogGroup.WM_SHELL_RECENTS_TRANSITION,
                        "[%d] RecentsController.merge: empty pausing tasks", mInstanceId);
            }
            if (!hasTaskChange) {
                // Activity only transition, so consume the merge as it doesn't affect the rest of
                // recents.
                Slog.d(TAG, "Got an activity only transition during recents, so apply directly");
                mergeActivityOnly(info, t);
            } else if (!didMergeThings) {
                // Didn't recognize anything in incoming transition so don't merge it.
                Slog.w(TAG, "Don't know how to merge this transition, foundRecentsClosing="
                        + foundRecentsClosing + " recentsTaskId=" + mRecentsTaskId);
                if (foundRecentsClosing || mRecentsTaskId < 0) {
                    mWillFinishToHome = false;
                    cancel(false /* toHome */, false /* withScreenshots */, "didn't merge");
                }
                return;
            }
            // At this point, we are accepting the merge.
            t.apply();
            // not using the incoming anim-only surfaces
            info.releaseAnimSurfaces();
            if (appearedTargets != null) {
                try {
                    ProtoLog.v(ShellProtoLogGroup.WM_SHELL_RECENTS_TRANSITION,
                            "[%d] RecentsController.merge: calling onTasksAppeared", mInstanceId);
                    mListener.onTasksAppeared(appearedTargets);
                } catch (RemoteException e) {
                    Slog.e(TAG, "Error sending appeared tasks to recents animation", e);
                }
            }
            finishCallback.onTransitionFinished(null /* wct */);
        }

        /** For now, just set-up a jump-cut to the new activity. */
        private void mergeActivityOnly(TransitionInfo info, SurfaceControl.Transaction t) {
            for (int i = 0; i < info.getChanges().size(); ++i) {
                final TransitionInfo.Change change = info.getChanges().get(i);
                if (TransitionUtil.isOpeningType(change.getMode())) {
                    t.show(change.getLeash());
                    t.setAlpha(change.getLeash(), 1.f);
                } else if (TransitionUtil.isClosingType(change.getMode())) {
                    t.hide(change.getLeash());
                }
            }
        }

        @Override
        public TaskSnapshot screenshotTask(int taskId) {
            try {
                ProtoLog.v(ShellProtoLogGroup.WM_SHELL_RECENTS_TRANSITION,
                        "[%d] RecentsController.screenshotTask: taskId=%d", mInstanceId, taskId);
                return ActivityTaskManager.getService().takeTaskSnapshot(taskId,
                        true /* updateCache */);
            } catch (RemoteException e) {
                Slog.e(TAG, "Failed to screenshot task", e);
            }
            return null;
        }

        @Override
        public void setInputConsumerEnabled(boolean enabled) {
            mExecutor.execute(() -> {
                if (mFinishCB == null || !enabled) {
                    ProtoLog.v(ShellProtoLogGroup.WM_SHELL_RECENTS_TRANSITION,
                            "RecentsController.setInputConsumerEnabled: skip, cb?=%b enabled?=%b",
                            mFinishCB != null, enabled);
                    return;
                }
                final int displayId = mInfo.getRootCount() > 0 ? mInfo.getRoot(0).getDisplayId()
                        : Display.DEFAULT_DISPLAY;
                // transient launches don't receive focus automatically. Since we are taking over
                // the gesture now, take focus explicitly.
                // This also moves recents back to top if the user gestured before a switch
                // animation finished.
                try {
                    ProtoLog.v(ShellProtoLogGroup.WM_SHELL_RECENTS_TRANSITION,
                            "[%d] RecentsController.setInputConsumerEnabled: set focus to recents",
                            mInstanceId);
                    ActivityTaskManager.getService().focusTopTask(displayId);
                } catch (RemoteException e) {
                    Slog.e(TAG, "Failed to set focused task", e);
                }
            });
        }

        @Override
        public void setAnimationTargetsBehindSystemBars(boolean behindSystemBars) {
        }

        @Override
        public void setFinishTaskTransaction(int taskId,
                PictureInPictureSurfaceTransaction finishTransaction, SurfaceControl overlay) {
            mExecutor.execute(() -> {
                ProtoLog.v(ShellProtoLogGroup.WM_SHELL_RECENTS_TRANSITION,
                        "[%d] RecentsController.setFinishTaskTransaction: taskId=%d,"
                                + " [mFinishCB is non-null]=%b",
                        mInstanceId, taskId, mFinishCB != null);
                if (mFinishCB == null) return;
                mPipTransaction = finishTransaction;
                mPipTaskId = taskId;
            });
        }

        @Override
        @SuppressLint("NewApi")
        public void finish(boolean toHome, boolean sendUserLeaveHint, IResultReceiver finishCb) {
            mExecutor.execute(() -> finishInner(toHome, sendUserLeaveHint, finishCb));
        }

        private void finishInner(boolean toHome, boolean sendUserLeaveHint,
                IResultReceiver runnerFinishCb) {
            if (mFinishCB == null) {
                Slog.e(TAG, "Duplicate call to finish");
                return;
            }

            boolean returningToApp = !toHome
                    && !mWillFinishToHome
                    && mPausingTasks != null
                    && mState == STATE_NORMAL;
            if (returningToApp && allAppsAreTranslucent(mPausingTasks)) {
                mHomeTransitionObserver.notifyHomeVisibilityChanged(true);
            } else if (!toHome) {
                // For some transitions, we may have notified home activity that it became visible.
                // We need to notify the observer that we are no longer going home.
                mHomeTransitionObserver.notifyHomeVisibilityChanged(false);
            }
            ProtoLog.v(ShellProtoLogGroup.WM_SHELL_RECENTS_TRANSITION,
                    "[%d] RecentsController.finishInner: toHome=%b userLeave=%b "
                            + "willFinishToHome=%b state=%d",
                    mInstanceId, toHome, sendUserLeaveHint, mWillFinishToHome, mState);
            final Transitions.TransitionFinishCallback finishCB = mFinishCB;
            mFinishCB = null;

            final SurfaceControl.Transaction t = mFinishTransaction;
            final WindowContainerTransaction wct = new WindowContainerTransaction();

            if (mKeyguardLocked && mRecentsTask != null) {
                if (toHome) wct.reorder(mRecentsTask, true /* toTop */);
                else wct.restoreTransientOrder(mRecentsTask);
            }
            if (returningToApp) {
                ProtoLog.v(ShellProtoLogGroup.WM_SHELL_RECENTS_TRANSITION, "  returning to app");
                // The gesture is returning to the pausing-task(s) rather than continuing with
                // recents, so end the transition by moving the app back to the top (and also
                // re-showing it's task).
                for (int i = mPausingTasks.size() - 1; i >= 0; --i) {
                    // reverse order so that index 0 ends up on top
                    wct.reorder(mPausingTasks.get(i).mToken, true /* onTop */);
                    t.show(mPausingTasks.get(i).mTaskSurface);
                }
                if (!mKeyguardLocked && mRecentsTask != null) {
                    wct.restoreTransientOrder(mRecentsTask);
                }
            } else if (toHome && mOpeningSeparateHome && mPausingTasks != null) {
                ProtoLog.v(ShellProtoLogGroup.WM_SHELL_RECENTS_TRANSITION, "  3p launching home");
                // Special situation where 3p launcher was changed during recents (this happens
                // during tapltests...). Here we get both "return to home" AND "home opening".
                // This is basically going home, but we have to restore the recents and home order.
                for (int i = 0; i < mOpeningTasks.size(); ++i) {
                    final TaskState state = mOpeningTasks.get(i);
                    if (state.mTaskInfo.topActivityType == ACTIVITY_TYPE_HOME) {
                        // Make sure it is on top.
                        wct.reorder(state.mToken, true /* onTop */);
                    }
                    t.show(state.mTaskSurface);
                }
                for (int i = mPausingTasks.size() - 1; i >= 0; --i) {
                    t.hide(mPausingTasks.get(i).mTaskSurface);
                }
                if (!mKeyguardLocked && mRecentsTask != null) {
                    wct.restoreTransientOrder(mRecentsTask);
                }
            } else {
                if (mPausingSeparateHome) {
                    if (mOpeningTasks.isEmpty()) {
                        ProtoLog.v(ShellProtoLogGroup.WM_SHELL_RECENTS_TRANSITION,
                                "  recents occluded 3p home");
                    } else {
                        ProtoLog.v(ShellProtoLogGroup.WM_SHELL_RECENTS_TRANSITION,
                                "  switch task by recents on 3p home");
                    }
                }
                ProtoLog.v(ShellProtoLogGroup.WM_SHELL_RECENTS_TRANSITION, "  normal finish");
                // The general case: committing to recents, going home, or switching tasks.
                for (int i = 0; i < mOpeningTasks.size(); ++i) {
                    t.show(mOpeningTasks.get(i).mTaskSurface);
                }
                for (int i = 0; i < mPausingTasks.size(); ++i) {
                    cleanUpPausingOrClosingTask(mPausingTasks.get(i), wct, t, sendUserLeaveHint);
                }
                for (int i = 0; i < mClosingTasks.size(); ++i) {
                    cleanUpPausingOrClosingTask(mClosingTasks.get(i), wct, t, sendUserLeaveHint);
                }
                if (mPipTransaction != null && sendUserLeaveHint) {
                    SurfaceControl pipLeash = null;
                    TransitionInfo.Change pipChange = null;
                    if (mPipTask != null) {
                        pipChange = mInfo.getChange(mPipTask);
                        pipLeash = pipChange.getLeash();
                    } else if (mPipTaskId != -1) {
                        // find a task with taskId from #setFinishTaskTransaction()
                        for (TransitionInfo.Change change : mInfo.getChanges()) {
                            if (change.getTaskInfo() != null
                                    && change.getTaskInfo().taskId == mPipTaskId) {
                                pipChange = change;
                                pipLeash = change.getLeash();
                                ProtoLog.v(ShellProtoLogGroup.WM_SHELL_RECENTS_TRANSITION,
                                        "RecentsController.finishInner:"
                                                + " found a change with taskId=%d", mPipTaskId);
                            }
                        }
                    }
                    if (pipLeash == null) {
                        ProtoLog.v(ShellProtoLogGroup.WM_SHELL_RECENTS_TRANSITION,
                                "RecentsController.finishInner: no valid PiP leash;"
                                        + "mPipTransaction=%s, mPipTask=%s, mPipTaskId=%d",
                                mPipTransaction, mPipTask, mPipTaskId);
                    } else {
                        t.show(pipLeash);
                        PictureInPictureSurfaceTransaction.apply(mPipTransaction, pipLeash, t);
                        ProtoLog.v(ShellProtoLogGroup.WM_SHELL_RECENTS_TRANSITION,
                                "RecentsController.finishInner: PiP transaction %s merged",
                                mPipTransaction);
                        if (PipUtils.isPip2ExperimentEnabled()) {
                            // If this path is triggered, we are in auto-enter PiP flow in gesture
                            // navigation mode, which means "Recents" transition should be followed
                            // by a TRANSIT_PIP. Hence, we take the WCT was about to be sent
                            // to Core to be applied during finishTransition(), we modify it to
                            // factor in PiP changes, and we send it as a direct startWCT for
                            // a new TRANSIT_PIP type transition. Recents still sends
                            // finishTransition() to update visibilities, but with finishWCT=null.
                            TransitionRequestInfo requestInfo = new TransitionRequestInfo(
                                    TRANSIT_PIP, null /* triggerTask */, pipChange.getTaskInfo(),
                                    null /* remote */, null /* displayChange */, 0 /* flags */);
                            // Use mTransition IBinder token temporarily just to get PipTransition
                            // to return from its handleRequest(). The actual TRANSIT_PIP will have
                            // anew token once it arrives into PipTransition#startAnimation().
                            Pair<Transitions.TransitionHandler, WindowContainerTransaction>
                                    requestRes = mTransitions.dispatchRequest(mTransition,
                                            requestInfo, null /* skip */);
                            wct.merge(requestRes.second, true);
                            mTransitions.startTransition(TRANSIT_PIP, wct, null /* handler */);
                            // We need to clear the WCT to send finishWCT=null for Recents.
                            wct.clear();
                        }
                    }
                    mPipTaskId = -1;
                    mPipTask = null;
                    mPipTransaction = null;
                }
            }
            cleanUp();
            finishCB.onTransitionFinished(wct.isEmpty() ? null : wct);
            if (runnerFinishCb != null) {
                try {
                    ProtoLog.v(ShellProtoLogGroup.WM_SHELL_RECENTS_TRANSITION,
                            "[%d] RecentsController.finishInner: calling finish callback",
                            mInstanceId);
                    runnerFinishCb.send(0, null);
                } catch (RemoteException e) {
                    Slog.e(TAG, "Failed to report transition finished", e);
                }
            }
        }

        private boolean allAppsAreTranslucent(ArrayList<TaskState> tasks) {
            if (tasks == null) {
                return false;
            }
            for (int i = tasks.size() - 1; i >= 0; --i) {
                if (!tasks.get(i).mIsTranslucent) {
                    return false;
                }
            }
            return true;
        }

        private void createBackgroundSurface(SurfaceControl.Transaction transaction,
                SurfaceControl parent, int layer) {
            if (mBackgroundColor == null) {
                return;
            }
            ProtoLog.v(ShellProtoLogGroup.WM_SHELL_RECENTS_TRANSITION,
                    "  adding background color to layer=%d", layer);
            final SurfaceControl background = new SurfaceControl.Builder()
                    .setName("recents_background")
                    .setColorLayer()
                    .setOpaque(true)
                    .setParent(parent)
                    .build();
            transaction.setColor(background, colorToFloatArray(mBackgroundColor));
            transaction.setLayer(background, layer);
            transaction.setAlpha(background, 1F);
            transaction.show(background);
        }

        private static float[] colorToFloatArray(@NonNull Color color) {
            return new float[]{color.red(), color.green(), color.blue()};
        }

        private void cleanUpPausingOrClosingTask(TaskState task, WindowContainerTransaction wct,
                SurfaceControl.Transaction finishTransaction, boolean sendUserLeaveHint) {
            if (!sendUserLeaveHint && task.isLeaf()) {
                // This means recents is not *actually* finishing, so of course we gotta
                // do special stuff in WMCore to accommodate.
                wct.setDoNotPip(task.mToken);
            }
            // Since we will reparent out of the leashes, pre-emptively hide the child
            // surface to match the leash. Otherwise, there will be a flicker before the
            // visibility gets committed in Core when using split-screen (in splitscreen,
            // the leaf-tasks are not "independent" so aren't hidden by normal setup).
            finishTransaction.hide(task.mTaskSurface);
        }

        @Override
        public void setDeferCancelUntilNextTransition(boolean defer, boolean screenshot) {
        }

        @Override
        public void cleanupScreenshot() {
        }

        @Override
        public void setWillFinishToHome(boolean willFinishToHome) {
            mExecutor.execute(() -> {
                mWillFinishToHome = willFinishToHome;
            });
        }

        /**
         * @see IRecentsAnimationController#removeTask
         */
        @Override
        public boolean removeTask(int taskId) {
            return false;
        }

        /**
         * @see IRecentsAnimationController#detachNavigationBarFromApp
         */
        @Override
        public void detachNavigationBarFromApp(boolean moveHomeToTop) {
            ProtoLog.v(ShellProtoLogGroup.WM_SHELL_RECENTS_TRANSITION,
                    "[%d] RecentsController.detachNavigationBarFromApp", mInstanceId);
            mExecutor.execute(() -> {
                if (mTransition == null) return;
                try {
                    ActivityTaskManager.getService().detachNavigationBarFromApp(mTransition);
                } catch (RemoteException e) {
                    Slog.e(TAG, "Failed to detach the navigation bar from app", e);
                }
            });
        }

        /**
         * @see IRecentsAnimationController#animateNavigationBarToApp(long)
         */
        @Override
        public void animateNavigationBarToApp(long duration) {
        }
    };

    /** Utility class to track the state of a task as-seen by recents. */
    private static class TaskState {
        WindowContainerToken mToken;
        ActivityManager.RunningTaskInfo mTaskInfo;

        /** The surface/leash of the task provided by Core. */
        SurfaceControl mTaskSurface;

        /** True when the task is translucent.  */
        final boolean mIsTranslucent;

        /** The (local) animation-leash created for this task. Only non-null for leafs. */
        @Nullable
        SurfaceControl mLeash;

        TaskState(TransitionInfo.Change change, SurfaceControl leash) {
            mToken = change.getContainer();
            mTaskInfo = change.getTaskInfo();
            mTaskSurface = change.getLeash();
            mIsTranslucent = (change.getFlags() & FLAG_TRANSLUCENT) != 0;
            mLeash = leash;
        }

        static int indexOf(ArrayList<TaskState> list, TransitionInfo.Change change) {
            for (int i = list.size() - 1; i >= 0; --i) {
                if (list.get(i).mToken.equals(change.getContainer())) {
                    return i;
                }
            }
            return -1;
        }

        boolean isLeaf() {
            return mLeash != null;
        }

        public String toString() {
            return "" + mToken + " : " + mLeash;
        }
    }

    /**
     * An interface for a mixed handler to receive information about recents requests (since these
     * come into this handler directly vs from WMCore request).
     */
    public interface RecentsMixedHandler extends Transitions.TransitionHandler {
        /**
         * Called when a recents request comes in. The handler can add operations to outWCT. If
         * the handler wants to "accept" the transition, it should return a Consumer accepting the
         * IBinder for the transition. If not, it should return `null`.
         *
         * If a mixed-handler accepts this recents, it will be the de-facto handler for this
         * transition and is required to call the associated {@link #startAnimation},
         * {@link #mergeAnimation}, and {@link #onTransitionConsumed} methods.
         */
        @Nullable
        Consumer<IBinder> handleRecentsRequest(WindowContainerTransaction outWCT);
    }
}
