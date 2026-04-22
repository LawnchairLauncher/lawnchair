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
import static android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN;
import static android.view.Display.DEFAULT_DISPLAY;
import static android.view.Display.INVALID_DISPLAY;
import static android.view.WindowManager.KEYGUARD_VISIBILITY_TRANSIT_FLAGS;
import static android.view.WindowManager.TRANSIT_CHANGE;
import static android.view.WindowManager.TRANSIT_CLOSE;
import static android.view.WindowManager.TRANSIT_FLAG_KEYGUARD_LOCKED;
import static android.view.WindowManager.TRANSIT_OPEN;
import static android.view.WindowManager.TRANSIT_PIP;
import static android.view.WindowManager.TRANSIT_SLEEP;
import static android.view.WindowManager.TRANSIT_TO_FRONT;
import static android.window.DesktopModeFlags.ENABLE_DESKTOP_RECENTS_TRANSITIONS_CORNERS_BUGFIX;
import static android.window.TransitionInfo.FLAG_MOVED_TO_TOP;
import static android.window.TransitionInfo.FLAG_TRANSLUCENT;

import static com.android.wm.shell.recents.RecentsTransitionStateListener.TRANSITION_STATE_ANIMATING;
import static com.android.wm.shell.recents.RecentsTransitionStateListener.TRANSITION_STATE_NOT_RUNNING;
import static com.android.wm.shell.recents.RecentsTransitionStateListener.TRANSITION_STATE_REQUESTED;
import static com.android.wm.shell.shared.ShellSharedConstants.KEY_EXTRA_SHELL_CAN_HAND_OFF_ANIMATION;
import static com.android.wm.shell.shared.split.SplitBounds.KEY_EXTRA_SPLIT_BOUNDS;
import static com.android.wm.shell.transition.Transitions.TRANSIT_END_RECENTS_TRANSITION;
import static com.android.wm.shell.transition.Transitions.TRANSIT_REMOVE_PIP;
import static com.android.wm.shell.transition.Transitions.TRANSIT_START_RECENTS_TRANSITION;

import android.annotation.Nullable;
import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.app.ActivityOptions;
import android.app.ActivityTaskManager;
import android.app.IApplicationThread;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Rect;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.ArrayMap;
import android.util.IntArray;
import android.util.Pair;
import android.util.Slog;
import android.view.RemoteAnimationTarget;
import android.view.SurfaceControl;
import android.window.DesktopExperienceFlags;
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
import com.android.internal.protolog.ProtoLog;
import com.android.wm.shell.Flags;
import com.android.wm.shell.ShellTaskOrganizer;
import com.android.wm.shell.common.DisplayController;
import com.android.wm.shell.common.ShellExecutor;
import com.android.wm.shell.desktopmode.multidesks.DesksOrganizer;
import com.android.wm.shell.protolog.ShellProtoLogGroup;
import com.android.wm.shell.shared.R;
import com.android.wm.shell.shared.TransitionUtil;
import com.android.wm.shell.shared.pip.PipFlags;
import com.android.wm.shell.sysui.ShellInit;
import com.android.wm.shell.transition.HomeTransitionObserver;
import com.android.wm.shell.transition.Transitions;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Handles the Recents (overview) animation. Only one of these can run at a time. A recents
 * transition must be created via {@link #startRecentsTransition}. Anything else will be ignored.
 */
public class RecentsTransitionHandler implements Transitions.TransitionHandler,
        Transitions.TransitionObserver {
    private static final String TAG = "RecentsTransitionHandler";

    // A placeholder for a synthetic transition that isn't backed by a true system transition
    public static final IBinder SYNTHETIC_TRANSITION = new Binder();

    private final Transitions mTransitions;
    private final ShellTaskOrganizer mShellTaskOrganizer;
    private final ShellExecutor mExecutor;
    @Nullable
    private final RecentTasksController mRecentTasksController;
    private IApplicationThread mAnimApp = null;
    private final ArrayList<RecentsController> mControllers = new ArrayList<>();
    private final ArrayList<RecentsTransitionStateListener> mStateListeners = new ArrayList<>();
    private Supplier<SurfaceControl.Transaction> mFinishTransactionSupplier = null;

    /**
     * List of other handlers which might need to mix recents with other things. These are checked
     * in the order they are added. Ideally there should only be one.
     */
    private final ArrayList<RecentsMixedHandler> mMixers = new ArrayList<>();

    private final HomeTransitionObserver mHomeTransitionObserver;
    private @Nullable Color mBackgroundColor;
    private final DisplayController mDisplayController;
    private final DesksOrganizer mDesksOrganizer;

    public RecentsTransitionHandler(
            @NonNull ShellInit shellInit,
            @NonNull ShellTaskOrganizer shellTaskOrganizer,
            @NonNull Transitions transitions,
            @Nullable RecentTasksController recentTasksController,
            @NonNull HomeTransitionObserver homeTransitionObserver,
            @NonNull DisplayController displayController,
            @NonNull DesksOrganizer desksOrganizer) {
        mShellTaskOrganizer = shellTaskOrganizer;
        mTransitions = transitions;
        mExecutor = transitions.getMainExecutor();
        mRecentTasksController = recentTasksController;
        mHomeTransitionObserver = homeTransitionObserver;
        mDisplayController = displayController;
        mDesksOrganizer = desksOrganizer;
        if (recentTasksController == null) return;
        shellInit.addInitCallback(this::onInit, this);
    }

    private void onInit() {
        mRecentTasksController.setTransitionHandler(this);
        mTransitions.addHandler(this);
        mTransitions.registerObserver(this);
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

    /**
     * Used for testing to provide a supplier for the transaction used in
     * RecentsController#finishInner() which later gets merged into the final finish transaction.
     */
    @VisibleForTesting
    public void setFinishTransactionSupplier(
            Supplier<SurfaceControl.Transaction> finishTransactionSupplier) {
        mFinishTransactionSupplier = finishTransactionSupplier;
    }

    /**
     * Starts a new real/synthetic recents transition.
     */
    @VisibleForTesting
    public IBinder startRecentsTransition(PendingIntent intent, Intent fillIn, Bundle options,
            @Nullable WindowContainerTransaction wct,
            IApplicationThread appThread, IRecentsAnimationRunner listener) {
        // only care about latest one.
        mAnimApp = appThread;

        for (int i = 0; i < mStateListeners.size(); i++) {
            mStateListeners.get(i).onTransitionStateChanged(TRANSITION_STATE_REQUESTED);
        }
        // TODO(b/366021931): Formalize this later
        final boolean isSyntheticRequest = options.getBoolean(
                "is_synthetic_recents_transition", /* defaultValue= */ false);
        final IBinder transition;
        ActivityOptions activityOptions = ActivityOptions.fromBundle(options);
        int displayId = activityOptions.getLaunchDisplayId();
        if (displayId == INVALID_DISPLAY) {
            displayId = DEFAULT_DISPLAY;
        }
        if (isSyntheticRequest) {
            transition = startSyntheticRecentsTransition(listener, displayId);
        } else {
            transition = startRealRecentsTransition(intent, fillIn, options, wct, listener,
                    displayId);
        }
        return transition;
    }

    /**
     * Starts a synthetic recents transition that is not backed by a real WM transition.
     */
    private IBinder startSyntheticRecentsTransition(@NonNull IRecentsAnimationRunner listener,
            int displayId) {
        ProtoLog.v(ShellProtoLogGroup.WM_SHELL_RECENTS_TRANSITION,
                "RecentsTransitionHandler.startRecentsTransition(synthetic)");
        final RecentsController lastController = getLastController();
        if (lastController != null) {
            lastController.cancel(lastController.isSyntheticTransition()
                    ? "existing_running_synthetic_transition"
                    : "existing_running_transition");
            return null;
        }

        // Create a new synthetic transition and start it immediately
        final RecentsController controller = new RecentsController(listener);
        controller.startSyntheticTransition(displayId);
        mControllers.add(controller);
        return SYNTHETIC_TRANSITION;
    }

    /**
     * Starts a real WM-backed recents transition.
     */
    private IBinder startRealRecentsTransition(PendingIntent intent, Intent fillIn, Bundle options,
            @Nullable WindowContainerTransaction requestWct, IRecentsAnimationRunner listener,
            int displayId) {
        ProtoLog.v(ShellProtoLogGroup.WM_SHELL_RECENTS_TRANSITION,
                "RecentsTransitionHandler.startRecentsTransition, displayId=%d", displayId);

        final WindowContainerTransaction wct = requestWct != null
                ? requestWct : new WindowContainerTransaction();
        wct.sendPendingIntent(intent, fillIn, options);

        // Find the mixed handler which should handle this request (if we are in a state where a
        // mixed handler is needed).  This is slightly convoluted because starting the transition
        // requires the handler, but the mixed handler also needs a reference to the transition.
        RecentsMixedHandler mixer = null;
        Consumer<IBinder> setTransitionForMixer = null;
        for (int i = 0; i < mMixers.size(); ++i) {
            setTransitionForMixer = mMixers.get(i).handleRecentsRequest(displayId);
            if (setTransitionForMixer != null) {
                mixer = mMixers.get(i);
                break;
            }
        }
        final int transitionType = Flags.enableRecentsBookendTransition()
                ? TRANSIT_START_RECENTS_TRANSITION
                : TRANSIT_TO_FRONT;
        final IBinder transition = mTransitions.startTransition(transitionType,
                wct, mixer == null ? this : mixer);
        if (mixer != null) {
            setTransitionForMixer.accept(transition);
        }

        final RecentsController controller = new RecentsController(listener);
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

    /**
     * Returns if there is currently a pending or active recents transition.
     */
    @Nullable
    private RecentsController getLastController() {
        return !mControllers.isEmpty() ? mControllers.getLast() : null;
    }

    /**
     * Finds an existing controller for the provided {@param transition}, or {@code null} if none
     * exists.
     */
    @Nullable
    @VisibleForTesting
    RecentsController findController(@NonNull IBinder transition) {
        for (int i = mControllers.size() - 1; i >= 0; --i) {
            final RecentsController controller = mControllers.get(i);
            if (controller.mTransition == transition) {
                return controller;
            }
        }
        return null;
    }

    @Override
    public boolean startAnimation(IBinder transition, TransitionInfo info,
            SurfaceControl.Transaction startTransaction,
            SurfaceControl.Transaction finishTransaction,
            Transitions.TransitionFinishCallback finishCallback) {
        final RecentsController controller = findController(transition);
        if (controller == null) {
            ProtoLog.v(ShellProtoLogGroup.WM_SHELL_RECENTS_TRANSITION,
                    "RecentsTransitionHandler.startAnimation: no controller found");
            return false;
        }
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
            @NonNull SurfaceControl.Transaction startT,
            @NonNull SurfaceControl.Transaction finishT,
            IBinder mergeTarget,
            Transitions.TransitionFinishCallback finishCallback) {
        final RecentsController controller = findController(mergeTarget);
        if (controller == null) {
            ProtoLog.v(ShellProtoLogGroup.WM_SHELL_RECENTS_TRANSITION,
                    "RecentsTransitionHandler.mergeAnimation: no controller found");
            return;
        }
        controller.merge(info, startT, finishT, finishCallback);
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

    @Override
    public void onTransitionReady(@NonNull IBinder transition, @NonNull TransitionInfo info,
            @NonNull SurfaceControl.Transaction startTransaction,
            @NonNull SurfaceControl.Transaction finishTransaction) {
        RecentsController controller = findController(SYNTHETIC_TRANSITION);
        if (controller != null) {
            // Cancel the existing synthetic transition if there is one
            controller.cancel("incoming_transition");
        }
    }

    /** There is only one of these and it gets reset on finish. */
    @VisibleForTesting
    class RecentsController extends IRecentsAnimationController.Stub {

        private final int mInstanceId;

        private IRecentsAnimationRunner mListener;
        private IBinder.DeathRecipient mDeathHandler;
        private Transitions.TransitionFinishCallback mFinishCB = null;
        private SurfaceControl.Transaction mFinishTransaction = null;

        /**
         * List of tasks that we are switching away from via this transition, ordered from top most
         * to bottom most in z-order. Upon finish, these pausing tasks will become invisible.
         * These need to be ordered since the order must be restored if there is no task-switch.
         */
        private ArrayList<TaskState> mPausingTasks = null;
        /**
         * The desk that we are switching away from via this transition. Upon finish it will become
         * invisible. It may be included in {@link RecentsController#mPausingTasks}.
         */
        private WindowContainerToken mPausingDesk = null;

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
        // This is the transition that backs the entire recents transition, and the one that the
        // pending finish transition below will be merged into
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

        // Used to track a pending finish transition, this is only non-null if
        // enableRecentsBookendTransition() is enabled
        private IBinder mPendingFinishTransition;
        private IResultReceiver mPendingRunnerFinishCb;
        // This stores the pending finish transaction to merge with the actual finish transaction
        private SurfaceControl.Transaction mPendingFinishTransaction;

        RecentsController(IRecentsAnimationRunner listener) {
            mInstanceId = System.identityHashCode(this);
            mListener = listener;
            mDeathHandler = () -> {
                mExecutor.execute(() -> {
                    ProtoLog.v(ShellProtoLogGroup.WM_SHELL_RECENTS_TRANSITION,
                            "[%d] RecentsController.DeathRecipient: binder died", mInstanceId);
                    finishInner(mWillFinishToHome, false /* leaveHint */, null /* finishCb */,
                            "deathRecipient");
                });
            };
            try {
                mListener.asBinder().linkToDeath(mDeathHandler, 0 /* flags */);
            } catch (RemoteException e) {
                Slog.e(TAG, "RecentsController: failed to link to death", e);
                mListener = null;
            }
        }

        /**
         * Sets the started transition for this instance of the recents transition.
         */
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
            if (cancelSyntheticTransition(reason)) {
                return;
            }

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
                finishInner(toHome, false /* userLeave */, null /* finishCb */, "cancel");
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
            mPausingDesk = null;
            mClosingTasks = null;
            mOpeningTasks = null;
            mInfo = null;
            mTransition = null;
            mPendingPauseSnapshotsForCancel = null;
            mPipTaskId = -1;
            mPipTask = null;
            mPipTransaction = null;
            mPendingRunnerFinishCb = null;
            mPendingFinishTransition = null;
            if (mPendingFinishTransaction != null) {
                mPendingFinishTransaction.close();
            }
            mPendingFinishTransaction = null;
            mControllers.remove(this);
            for (int i = 0; i < mStateListeners.size(); i++) {
                mStateListeners.get(i).onTransitionStateChanged(TRANSITION_STATE_NOT_RUNNING);
            }
        }

        /**
         * Starts a new transition that is not backed by a system transition.
         */
        void startSyntheticTransition(int displayId) {
            mTransition = SYNTHETIC_TRANSITION;

            // TODO(b/366021931): Update mechanism for pulling the home task, for now add home as
            //                    both opening and closing since there's some pre-existing
            //                    dependencies on having a closing task
            final ActivityManager.RunningTaskInfo homeTask =
                    mShellTaskOrganizer.getRunningTasks(displayId).stream()
                            .filter(task -> task.getActivityType() == ACTIVITY_TYPE_HOME)
                            .findFirst()
                            .get();
            final RemoteAnimationTarget openingTarget = TransitionUtil.newSyntheticTarget(
                    homeTask, mShellTaskOrganizer.getHomeTaskSurface(displayId), TRANSIT_OPEN,
                    0, true /* isTranslucent */);
            final RemoteAnimationTarget closingTarget = TransitionUtil.newSyntheticTarget(
                    homeTask, mShellTaskOrganizer.getHomeTaskSurface(displayId), TRANSIT_CLOSE,
                    0, true /* isTranslucent */);
            final ArrayList<RemoteAnimationTarget> apps = new ArrayList<>();
            apps.add(openingTarget);
            apps.add(closingTarget);
            try {
                ProtoLog.v(ShellProtoLogGroup.WM_SHELL_RECENTS_TRANSITION,
                        "[%d] RecentsController.start: calling onAnimationStart with %d apps, "
                                + "displayId=%d",
                        mInstanceId, apps.size(), displayId);
                mListener.onAnimationStart(this,
                        apps.toArray(new RemoteAnimationTarget[apps.size()]),
                        new RemoteAnimationTarget[0],
                        new Rect(0, 0, 0, 0), new Rect(), new Bundle(),
                        null);
                for (int i = 0; i < mStateListeners.size(); i++) {
                    mStateListeners.get(i).onTransitionStateChanged(TRANSITION_STATE_ANIMATING);
                }
            } catch (RemoteException e) {
                Slog.e(TAG, "Error starting recents animation", e);
                cancel("startSynthetricTransition() failed");
            }
        }

        /**
         * Returns whether this transition is backed by a real system transition or not.
         */
        boolean isSyntheticTransition() {
            return mTransition == SYNTHETIC_TRANSITION;
        }

        /**
         * Called when a synthetic transition is canceled.
         */
        boolean cancelSyntheticTransition(String reason) {
            if (!isSyntheticTransition()) {
                return false;
            }

            ProtoLog.v(ShellProtoLogGroup.WM_SHELL_RECENTS_TRANSITION,
                    "[%d] RecentsController.cancelSyntheticTransition: reason=%s",
                    mInstanceId, reason);
            try {
                // TODO(b/366021931): Notify the correct tasks once we build actual targets, and
                //                    clean up leashes accordingly
                mListener.onAnimationCanceled(new int[0], new TaskSnapshot[0]);
            } catch (RemoteException e) {
                Slog.e(TAG, "Error canceling previous recents animation", e);
            }
            cleanUp();
            return true;
        }

        /**
         * Called when a synthetic transition is finished.
         * @return
         */
        boolean finishSyntheticTransition(IResultReceiver runnerFinishCb, String reason) {
            if (!isSyntheticTransition()) {
                return false;
            }

            ProtoLog.v(ShellProtoLogGroup.WM_SHELL_RECENTS_TRANSITION,
                    "[%d] RecentsController.finishSyntheticTransition: reason=%s", mInstanceId,
                    reason);
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
            // TODO(b/366021931): Clean up leashes accordingly
            cleanUp();
            return true;
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
            mPausingDesk = null;
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
                        ProtoLog.v(ShellProtoLogGroup.WM_SHELL_RECENTS_TRANSITION,
                                "  not handling home taskId=%d", taskInfo.taskId);
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
                        // raise closing (pausing) task to "above" layer so it isn't covered
                        t.setLayer(change.getLeash(), layer);
                        mPausingTasks.add(new TaskState(change, null /* leash */));
                        if (mDesksOrganizer.isDeskChange(change)) {
                            mPausingDesk = change.getContainer();
                            ProtoLog.v(ShellProtoLogGroup.WM_SHELL_RECENTS_TRANSITION,
                                    "  adding pausing deskId=%d at layer=%d", taskInfo.taskId,
                                    layer);
                        } else {
                            ProtoLog.v(ShellProtoLogGroup.WM_SHELL_RECENTS_TRANSITION,
                                    "  adding pausing taskId=%d at layer=%d", taskInfo.taskId,
                                    layer);
                        }
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
                } else if (TransitionUtil.isDividerBar(change)
                        || TransitionUtil.isDimLayer(change)) {
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
                        new Rect(0, 0, 0, 0), new Rect(), b, info);
                for (int i = 0; i < mStateListeners.size(); i++) {
                    mStateListeners.get(i).onTransitionStateChanged(TRANSITION_STATE_ANIMATING);
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
                                    null /* finishCb */, "takeOverAnimation");
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

        /**
         * Note: because we use a book-end transition to finish the recents transition, we must
         * either always merge the incoming transition, or always cancel the recents transition
         * if we don't handle the incoming transition to ensure that the end transition is queued
         * before any unhandled transitions.
         */
        @SuppressLint("NewApi")
        void merge(TransitionInfo info, SurfaceControl.Transaction startT,
                SurfaceControl.Transaction finishT,
                Transitions.TransitionFinishCallback finishCallback) {
            if (mFinishCB == null) {
                ProtoLog.v(ShellProtoLogGroup.WM_SHELL_RECENTS_TRANSITION,
                        "[%d] RecentsController.merge: skip, no finish callback",
                        mInstanceId);
                // This was no-op'd (likely a repeated start) and we've already completed finish.
                return;
            }

            if (Flags.enableRecentsBookendTransition()) {
                if (info.getType() == TRANSIT_END_RECENTS_TRANSITION) {
                    // This is a pending finish, so merge the end transition to trigger completing
                    // the cleanup of the recents transition
                    ProtoLog.v(ShellProtoLogGroup.WM_SHELL_RECENTS_TRANSITION,
                            "[%d] RecentsController.merge: TRANSIT_END_RECENTS_TRANSITION",
                            mInstanceId);
                    consumeMerge(info, startT, finishT, finishCallback);
                    return;
                } else if (mPendingFinishTransition != null) {
                    // This transition is interrupting a pending finish that was already sent, so
                    // pre-empt the pending finish transition since the state has already changed
                    // in the core
                    ProtoLog.v(ShellProtoLogGroup.WM_SHELL_RECENTS_TRANSITION,
                            "[%d] RecentsController.merge: Awaiting TRANSIT_END_RECENTS_TRANSITION",
                            mInstanceId);
                    onFinishInner(null /* wct */);
                    return;
                }
            }

            if (info.getType() == TRANSIT_SLEEP) {
                ProtoLog.v(ShellProtoLogGroup.WM_SHELL_RECENTS_TRANSITION,
                        "[%d] RecentsController.merge: transit_sleep", mInstanceId);
                // A sleep event means we need to stop animations immediately, so cancel here.
                cancel("transit_sleep");
                return;
            }

            if (info.getType() == TRANSIT_REMOVE_PIP) {
                ProtoLog.v(ShellProtoLogGroup.WM_SHELL_RECENTS_TRANSITION,
                        "[%d] RecentsController.merge: transit_remove_pip", mInstanceId);
                // Cancel the merge if transition is removing PiP; PiP is on top of everything else.
                cancel(true /* toHome */, mWillFinishToHome /* withScreenshots */,
                        "transit_remove_pip");
                return;
            }

            if (mKeyguardLocked || (info.getFlags() & KEYGUARD_VISIBILITY_TRANSIT_FLAGS) != 0) {
                ProtoLog.v(ShellProtoLogGroup.WM_SHELL_RECENTS_TRANSITION,
                        "[%d] RecentsController.merge: keyguard is locked", mInstanceId);
                // We will not accept new changes if we are swiping over the keyguard.
                cancel("keyguard_locked");
                return;
            }
            ProtoLog.v(ShellProtoLogGroup.WM_SHELL_RECENTS_TRANSITION,
                    "[%d] RecentsController.merge", mInstanceId);
            // Keep all tasks in one list because order matters.
            ArrayList<TransitionInfo.Change> openingTasks = null;
            TransitionInfo.Change openingDesk = null;
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
                    ProtoLog.v(ShellProtoLogGroup.WM_SHELL_RECENTS_TRANSITION,
                            "[%d]   Canceling due to always on top task", mInstanceId);
                    // Tasks that are always on top (e.g. bubbles), will handle their own transition
                    // as they are on top of everything else. So cancel the merge here.
                    cancel("task #" + taskInfo.taskId + " is always_on_top");
                    return;
                }
                if (TransitionUtil.isClosingType(change.getMode())
                        && taskInfo != null && taskInfo.lastParentTaskIdBeforePip > 0) {
                    ProtoLog.v(ShellProtoLogGroup.WM_SHELL_RECENTS_TRANSITION,
                            "[%d]   Canceling due to restoring PIP activity", mInstanceId);
                    // Pinned task is closing as a side effect of the removal of its original Task,
                    // such transition should be handled by PiP. So cancel the merge here.
                    cancel("task #" + taskInfo.taskId + " is removed with its original parent");
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
                    final String chgTypeMsg = TransitionUtil.isOpeningType(change.getMode())
                            ? "Opening" : "Changing";
                    if (isRecentsTask) {
                        ProtoLog.v(ShellProtoLogGroup.WM_SHELL_RECENTS_TRANSITION,
                                "[%d]   %s recents", mInstanceId, chgTypeMsg);
                        recentsOpening = change;
                    } else if (isRootTask || isLeafTask) {
                        String containerTypeMsg = "task";
                        if (isLeafTask && taskInfo.topActivityType == ACTIVITY_TYPE_HOME) {
                            // This is usually a 3p launcher
                            mOpeningSeparateHome = true;
                        }
                        if (openingTasks == null) {
                            openingTasks = new ArrayList<>();
                            openingTaskIsLeafs = new IntArray();
                        }
                        openingTasks.add(change);
                        if (mDesksOrganizer.isDeskChange(change)) {
                            openingDesk = change;
                            containerTypeMsg = "desk";
                        }
                        openingTaskIsLeafs.add(isLeafTask ? 1 : 0);
                        ProtoLog.v(ShellProtoLogGroup.WM_SHELL_RECENTS_TRANSITION,
                                "[%d]   %s %s", mInstanceId, chgTypeMsg, containerTypeMsg);
                    }
                } else if (TransitionUtil.isClosingType(change.getMode())) {
                    if (isRecentsTask) {
                        ProtoLog.v(ShellProtoLogGroup.WM_SHELL_RECENTS_TRANSITION,
                                "[%d]   Closing recents", mInstanceId);
                        foundRecentsClosing = true;
                    } else if (isRootTask || isLeafTask) {
                        ProtoLog.v(ShellProtoLogGroup.WM_SHELL_RECENTS_TRANSITION,
                                "[%d]   Closing task", mInstanceId);
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
                        ProtoLog.v(ShellProtoLogGroup.WM_SHELL_RECENTS_TRANSITION,
                                "[%d]   Canceling due to display change", mInstanceId);
                        // This call to cancel will use the screenshots taken preemptively in
                        // handleMidTransitionRequest() prior to the display changing
                        cancel(true /* toHome */, true /* withScreenshots */, "display change");
                        return;
                    }
                    // Don't consider order-only & non-leaf changes as changing apps.
                    if (!TransitionUtil.isOrderOnly(change) && isLeafTask) {
                        hasChangingApp = true;
                        // Check if the changing app is moving to top and fullscreen. This handles
                        // the case where we moved from desktop to recents and launching a desktop
                        // task in fullscreen.
                        if ((change.getFlags() & FLAG_MOVED_TO_TOP) != 0
                                && taskInfo != null
                                && taskInfo.getWindowingMode()
                                == WINDOWING_MODE_FULLSCREEN) {
                            ProtoLog.v(ShellProtoLogGroup.WM_SHELL_RECENTS_TRANSITION,
                                    "[%d]   Moving task to fullscreen", mInstanceId);
                            if (openingTasks == null) {
                                openingTasks = new ArrayList<>();
                                openingTaskIsLeafs = new IntArray();
                            }
                            openingTasks.add(change);
                            openingTaskIsLeafs.add(1);
                        }
                    } else if (isLeafTask && taskInfo.topActivityType == ACTIVITY_TYPE_HOME
                            && !isRecentsTask) {
                        ProtoLog.v(ShellProtoLogGroup.WM_SHELL_RECENTS_TRANSITION,
                                "[%d]   Opening home", mInstanceId);
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
                                null /* finishCb */, "merge"), 0);
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
                startT.show(recentsOpening.getLeash());
                startT.setAlpha(recentsOpening.getLeash(), 1.f);
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
            ArrayList<RemoteAnimationTarget> appearedTargets = null;
            if (openingTasks != null && openingTasks.size() > 0) {
                // Switching to some new tasks, add to mOpening and remove from mPausing. Also,
                // enter NEW_TASK state since this will start the switch-to animation.
                final int layer = mInfo.getChanges().size() * 3;
                for (int i = 0; i < openingTaskIsLeafs.size(); ++i) {
                    if (openingTaskIsLeafs.get(i) > 0) {
                        // There is at least one opening leaf task, so initialize the appeared
                        // targets list
                        appearedTargets = new ArrayList<>();
                        break;
                    }
                }

                // An initially paused and now opening desk means we're returning to the desk. We
                // save it here to identify whether any of the opening tasks below belong to the
                // desk, and are thus part of a returning-to-desk operation.
                final TransitionInfo.Change openingPausedDesk = mPausingDesk != null
                        && openingDesk != null
                        && mPausingDesk.equals(openingDesk.getContainer()) ? openingDesk : null;
                boolean onlyOpeningPausedTasksOrPausedDesk = true;
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
                            appearedTargets.add(TransitionUtil.newTarget(
                                    change, layer, mPausingTasks.get(pausingIdx).mLeash));
                            // In multi-desk mode, we can have a root task which the leaf tasks are
                            // relative to, but if both the root and leaf are a part of the
                            // transition then Transitions#setupStartState() will incorrectly apply
                            // an offset to the task surface which is already leashed. Unlike the
                            // start of the transition, we don't end up going through
                            // TransitionUtil#createLeash(), which normally resets the position of
                            // the task within the leash, so we have to do it manually here
                            if (DesktopExperienceFlags.ENABLE_MULTIPLE_DESKTOPS_BACKEND.isTrue()) {
                                startT.setPosition(change.getLeash(), /* x= */ 0, /* y= */ 0);
                            }
                        }
                        final TaskState pausingTask = mPausingTasks.remove(pausingIdx);
                        ProtoLog.v(ShellProtoLogGroup.WM_SHELL_RECENTS_TRANSITION,
                                "  opening pausing %staskId=%d", isLeaf ? "leaf " : "non-leaf ",
                                pausingTask.mTaskInfo.taskId);
                        mOpeningTasks.add(pausingTask);
                        // Setup hides opening tasks initially, so make it visible again (since we
                        // are already showing it).
                        startT.show(change.getLeash());
                        startT.setAlpha(change.getLeash(), 1.f);
                    } else if (isLeaf) {
                        // We are receiving new opening leaf tasks, so convert to onTasksAppeared.
                        final RemoteAnimationTarget target = TransitionUtil.newTarget(
                                change, layer, info, startT, mLeashMap);
                        appearedTargets.add(target);
                        // reparent into the original `mInfo` since that's where we are animating.
                        final TransitionInfo.Root root = TransitionUtil.getRootFor(change, mInfo);
                        final boolean wasClosing = closingIdx >= 0;
                        startT.reparent(target.leash, root.getLeash());
                        startT.setPosition(target.leash,
                                change.getStartAbsBounds().left - root.getOffset().x,
                                change.getStartAbsBounds().top - root.getOffset().y);
                        startT.setLayer(target.leash, layer);
                        if (wasClosing) {
                            // App was previously visible and is closing
                            startT.show(target.leash);
                            startT.setAlpha(target.leash, 1f);
                            // Also override the task alpha as it was set earlier when dispatching
                            // the transition and setting up the leash to hide the
                            startT.setAlpha(change.getLeash(), 1f);
                        } else {
                            // Hide the animation leash, let the listener show it
                            startT.hide(target.leash);
                        }
                        mOpeningTasks.add(new TaskState(change, target.leash));
                        if (!DesktopExperienceFlags.ENABLE_MULTIPLE_DESKTOPS_BACKEND.isTrue()) {
                            ProtoLog.v(ShellProtoLogGroup.WM_SHELL_RECENTS_TRANSITION,
                                    "  opening new leaf taskId=%d wasClosing=%b",
                                    target.taskId, wasClosing);
                            onlyOpeningPausedTasksOrPausedDesk = false;
                        } else {
                            final boolean childOfOpeningPausedDesk = openingPausedDesk != null
                                     && change.getTaskInfo().parentTaskId
                                    == openingPausedDesk.getTaskInfo().taskId;
                            ProtoLog.v(ShellProtoLogGroup.WM_SHELL_RECENTS_TRANSITION,
                                    "  opening new leaf taskId=%d wasClosing=%b "
                                            + "childOfOpeningPausedDesk=%b",
                                    target.taskId, wasClosing, childOfOpeningPausedDesk);
                            if (!childOfOpeningPausedDesk) {
                                onlyOpeningPausedTasksOrPausedDesk = false;
                            }
                        }
                    } else {
                        ProtoLog.v(ShellProtoLogGroup.WM_SHELL_RECENTS_TRANSITION,
                                "  opening new taskId=%d", change.getTaskInfo().taskId);
                        startT.setLayer(change.getLeash(), layer);
                        // Setup hides opening tasks initially, so make it visible since recents
                        // is only animating the leafs.
                        startT.show(change.getLeash());
                        mOpeningTasks.add(new TaskState(change, null));
                        onlyOpeningPausedTasksOrPausedDesk = false;
                    }
                }
                didMergeThings = true;
                if (!onlyOpeningPausedTasksOrPausedDesk) {
                    // If we are only opening paused leaf tasks, then we aren't actually quick
                    // switching or launching a new task from overview, and if Launcher requests to
                    // finish(toHome=false) as a response to the pausing tasks being opened again,
                    // we should allow that to be considered returningToApp
                    mState = STATE_NEW_TASK;
                }
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
                mergeActivityOnly(info, startT);
            } else if (!didMergeThings) {
                // Didn't recognize anything in incoming transition so don't merge it.
                Slog.w(TAG, "Don't know how to merge this transition, foundRecentsClosing="
                        + foundRecentsClosing + " recentsTaskId=" + mRecentsTaskId);
                if (foundRecentsClosing || mRecentsTaskId < 0) {
                    mWillFinishToHome = false;
                    cancel("didn't merge");
                }
                return;
            }

            // At this point, we are accepting the merge.
            consumeMerge(info, startT, finishT, finishCallback);

            // Notify Launcher of the new opening tasks if necessary
            boolean passTransitionInfo = ENABLE_DESKTOP_RECENTS_TRANSITIONS_CORNERS_BUGFIX.isTrue();
            if (appearedTargets != null) {
                try {
                    ProtoLog.v(ShellProtoLogGroup.WM_SHELL_RECENTS_TRANSITION,
                            "[%d] RecentsController.merge: calling onTasksAppeared", mInstanceId);
                    final RemoteAnimationTarget[] targets = appearedTargets.toArray(
                            new RemoteAnimationTarget[0]);
                    mListener.onTasksAppeared(targets, passTransitionInfo ? info : null);
                } catch (RemoteException e) {
                    Slog.e(TAG, "Error sending appeared tasks to recents animation", e);
                }
            }
        }

        /**
         * Consumes the merge of the other given transition.
         */
        private void consumeMerge(TransitionInfo info, SurfaceControl.Transaction startT,
                SurfaceControl.Transaction finishT,
                Transitions.TransitionFinishCallback finishCallback) {
            ProtoLog.v(ShellProtoLogGroup.WM_SHELL_RECENTS_TRANSITION,
                    "[%d] RecentsController.merge: consuming merge",
                    mInstanceId);

            startT.apply();
            // Since we're accepting the merge, update the finish transaction so that changes via
            // that transaction will be applied on top of those of the merged transitions
            mFinishTransaction = finishT;
            boolean passTransitionInfo = ENABLE_DESKTOP_RECENTS_TRANSITIONS_CORNERS_BUGFIX.isTrue();
            if (!passTransitionInfo) {
                // not using the incoming anim-only surfaces
                info.releaseAnimSurfaces();
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
        public void setInputConsumerEnabled(boolean enabled) {
            mExecutor.execute(() -> {
                if (mFinishCB == null || !enabled) {
                    ProtoLog.v(ShellProtoLogGroup.WM_SHELL_RECENTS_TRANSITION,
                            "RecentsController.setInputConsumerEnabled: skip, cb?=%b enabled?=%b",
                            mFinishCB != null, enabled);
                    return;
                }
                final int displayId = mInfo.getRootCount() > 0 ? mInfo.getRoot(0).getDisplayId()
                        : DEFAULT_DISPLAY;
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
            mExecutor.execute(() -> finishInner(toHome, sendUserLeaveHint, finishCb,
                    "requested"));
        }

        /**
         * @param runnerFinishCb The remote finish callback to run after finish is complete, this is
         *                       not the same as mFinishCb which reports the transition is finished
         *                       to WM.
         */
        private void finishInner(boolean toHome, boolean sendUserLeaveHint,
                IResultReceiver runnerFinishCb, String reason) {
            if (finishSyntheticTransition(runnerFinishCb, reason)) {
                return;
            }

            if (mFinishCB == null || (Flags.enableRecentsBookendTransition()
                    && mPendingFinishTransition != null)) {
                Slog.e(TAG, "Duplicate call to finish");
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
                return;
            }

            // Note: returning to a desk tile, whether it be by clicking the tile or one of the
            // existing exploded tasks is considered to be returning to app.
            boolean returningToApp = !toHome
                    && !mWillFinishToHome
                    && mPausingTasks != null
                    && mState == STATE_NORMAL;
            if (!Flags.enableRecentsBookendTransition()) {
                // This is only necessary when the recents transition is finished using a finishWCT,
                // otherwise a new transition will notify the relevant observers
                if (returningToApp && allAppsAreTranslucent(mPausingTasks)) {
                    mHomeTransitionObserver.notifyHomeVisibilityChanged(true);
                } else if (!toHome && mState == STATE_NEW_TASK
                        && allAppsAreTranslucent(mOpeningTasks)) {
                    // We are opening a translucent app. Launcher is still visible so we do nothing.
                } else if (!toHome) {
                    // For some transitions, we may have notified home activity that it became
                    // visible. We need to notify the observer that we are no longer going home.
                    mHomeTransitionObserver.notifyHomeVisibilityChanged(false);
                }
            }

            ProtoLog.v(ShellProtoLogGroup.WM_SHELL_RECENTS_TRANSITION,
                    "[%d] RecentsController.finishInner: toHome=%b userLeave=%b "
                            + "willFinishToHome=%b state=%d hasPausingTasks=%b reason=%s",
                    mInstanceId, toHome, sendUserLeaveHint, mWillFinishToHome, mState,
                    mPausingTasks != null, reason);

            final SurfaceControl.Transaction t = mFinishTransactionSupplier != null
                    ? mFinishTransactionSupplier.get()
                    : new SurfaceControl.Transaction();
            final WindowContainerTransaction wct = new WindowContainerTransaction();

            // The following code must set this if it is changing anything in core that might affect
            // transitions as a part of finishing the recents transition
            boolean requiresBookendTransition = false;

            if (mKeyguardLocked && mRecentsTask != null) {
                if (toHome) wct.reorder(mRecentsTask, true /* toTop */);
                else wct.restoreTransientOrder(mRecentsTask);
                // We are manipulating the window hierarchy, which should only be done with the
                // bookend transition
                requiresBookendTransition = true;
            }
            if (returningToApp) {
                ProtoLog.v(ShellProtoLogGroup.WM_SHELL_RECENTS_TRANSITION, "  returning to app");
                // The gesture is returning to the pausing-task(s) rather than continuing with
                // recents, so end the transition by moving the app(s) back to the top (and also
                // re-showing their tasks).
                final List<TaskState> tasksToShowFrontToBack = new ArrayList<>();
                if (DesktopExperienceFlags.ENABLE_MULTIPLE_DESKTOPS_BACKEND.isTrue()) {
                    // Opening tasks can also exist in a |returningToApp| case, such as when
                    // returning to the desk tile by clicking on one of the exploded view tasks.
                    // These are really "pausing" tasks that became "opening" because they were
                    // re-brought to front. They should be on top of pausing tasks, so insert them
                    // first.
                    tasksToShowFrontToBack.addAll(mOpeningTasks);
                }
                // The remaining pausing tasks should also be moved back to top, but below the
                // opening ones.
                tasksToShowFrontToBack.addAll(mPausingTasks);
                for (int i = tasksToShowFrontToBack.size() - 1; i >= 0; --i) {
                    // reverse order so that index 0 ends up on top
                    final TaskState taskState = tasksToShowFrontToBack.get(i);
                    ProtoLog.v(ShellProtoLogGroup.WM_SHELL_RECENTS_TRANSITION,
                            "  reorder task#%d to top", taskState.mTaskInfo.taskId);
                    wct.reorder(taskState.mToken, true /* onTop */);
                    t.show(taskState.mTaskSurface);
                }
                setCornerRadiusForFreeformTasks(mRecentTasksController.getContext(), t,
                        tasksToShowFrontToBack);
                if (!mKeyguardLocked && mRecentsTask != null) {
                    wct.restoreTransientOrder(mRecentsTask);
                }
                // We are manipulating the window hierarchy, which should only be done with the
                // bookend transition
                requiresBookendTransition = true;
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
                // We are manipulating the window hierarchy, which should only be done with the
                // bookend transition
                requiresBookendTransition = true;
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
                setCornerRadiusForFreeformTasks(
                        mRecentTasksController.getContext(), t, mOpeningTasks);
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
                        if (PipFlags.isPip2ExperimentEnabled()) {
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

                            if (Flags.enableRecentsBookendTransition()) {
                                // Notify the mixers of the pending finish
                                for (int i = 0; i < mMixers.size(); ++i) {
                                    mMixers.get(i).handleFinishRecents(returningToApp, wct, t);
                                }

                                // In this case, we've already started the PIP transition, so we can
                                // clean up immediately
                                mPendingRunnerFinishCb = runnerFinishCb;
                                mPendingFinishTransaction = t;
                                onFinishInner(null);
                                return;
                            }
                        }
                    }
                }
            }

            // Notify the mixers of the pending finish
            for (int i = 0; i < mMixers.size(); ++i) {
                mMixers.get(i).handleFinishRecents(returningToApp, wct, t);
            }

            if (Flags.enableRecentsBookendTransition()) {
                if (!wct.isEmpty()) {
                    if (requiresBookendTransition) {
                        ProtoLog.v(ShellProtoLogGroup.WM_SHELL_RECENTS_TRANSITION,
                                "[%d] RecentsController.finishInner: "
                                        + "Queuing TRANSIT_END_RECENTS_TRANSITION", mInstanceId);
                        mPendingRunnerFinishCb = runnerFinishCb;
                        mPendingFinishTransaction = t;
                        mPendingFinishTransition = mTransitions.startTransition(
                                TRANSIT_END_RECENTS_TRANSITION, wct,
                                new PendingFinishTransitionHandler());
                    } else {
                        ProtoLog.v(ShellProtoLogGroup.WM_SHELL_RECENTS_TRANSITION,
                                "[%d] RecentsController.finishInner: Non-transition affecting wct",
                                mInstanceId);
                        mPendingRunnerFinishCb = runnerFinishCb;
                        mPendingFinishTransaction = t;
                        onFinishInner(wct);
                    }
                } else {
                    // If there's no work to do, just go ahead and clean up
                    mPendingRunnerFinishCb = runnerFinishCb;
                    mPendingFinishTransaction = t;
                    onFinishInner(null /* wct */);
                }
            } else {
                mPendingRunnerFinishCb = runnerFinishCb;
                mPendingFinishTransaction = t;
                onFinishInner(wct);
            }
        }

        /**
         * Runs the actual logic to finish the recents transition.
         */
        private void onFinishInner(@Nullable WindowContainerTransaction wct) {
            ProtoLog.v(ShellProtoLogGroup.WM_SHELL_RECENTS_TRANSITION,
                    "[%d] RecentsController.finishInner: Completing finish", mInstanceId);
            final Transitions.TransitionFinishCallback finishCb = mFinishCB;
            final IResultReceiver runnerFinishCb = mPendingRunnerFinishCb;

            // We merge the cleanup transaction (prepared in finishInner()) with the last accepted
            // finish transaction to ensure that it's applied after the default finish transaction
            // created for the bookend transition (if it's used)
            mFinishTransaction.merge(mPendingFinishTransaction);

            cleanUp();
            finishCb.onTransitionFinished(wct);
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

        private void setCornerRadiusForFreeformTasks(
                Context context,
                SurfaceControl.Transaction t,
                List<TaskState> tasks) {
            if (!ENABLE_DESKTOP_RECENTS_TRANSITIONS_CORNERS_BUGFIX.isTrue()) {
                return;
            }
            for (int i = 0; i < tasks.size(); ++i) {
                TaskState task = tasks.get(i);
                if (task.mTaskInfo != null && task.mTaskInfo.isFreeform()) {
                    int cornerRadius = getCornerRadius(context, task.mTaskInfo.displayId);
                    t.setCornerRadius(task.mTaskSurface, cornerRadius);
                }
            }
        }

        private int getCornerRadius(Context context, int displayId) {
            Context displayContext = mDisplayController.getDisplayContext(displayId);
            if (displayContext == null) {
                displayContext = context;
            }
            return displayContext.getResources().getDimensionPixelSize(
                    R.dimen.desktop_windowing_freeform_rounded_corner_radius);
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
        public void setWillFinishToHome(boolean willFinishToHome) {
            mExecutor.execute(() -> {
                mWillFinishToHome = willFinishToHome;
            });
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
         * A temporary transition handler used with the pending finish transition, which runs the
         * cleanup/finish logic once the pending transition is merged/handled.
         * This is only initialized if Flags.enableRecentsBookendTransition() is enabled.
         */
        private class PendingFinishTransitionHandler implements Transitions.TransitionHandler {
            @Override
            public boolean startAnimation(@NonNull IBinder transition,
                    @NonNull TransitionInfo info,
                    @NonNull SurfaceControl.Transaction startTransaction,
                    @NonNull SurfaceControl.Transaction finishTransaction,
                    @NonNull Transitions.TransitionFinishCallback finishCallback) {
                ProtoLog.v(ShellProtoLogGroup.WM_SHELL_RECENTS_TRANSITION,
                        "[%d] PendingFinishTransitionHandler.startAnimation: "
                                + "Started pending finish transition", mInstanceId);
                return false;
            }

            @Nullable
            @Override
            public WindowContainerTransaction handleRequest(@NonNull IBinder transition,
                    @NonNull TransitionRequestInfo request) {
                return null;
            }

            @Override
            public void onTransitionConsumed(@NonNull IBinder transition, boolean aborted,
                    @Nullable SurfaceControl.Transaction finishTransaction) {
                if (mPendingFinishTransition == null) {
                    // The cleanup was pre-empted by an earlier transition, nothing there is nothing
                    // to do here
                    return;
                }
                // Once we have merged (or not if the WCT didn't result in any changes), then we can
                // run the pending finish logic
                ProtoLog.v(ShellProtoLogGroup.WM_SHELL_RECENTS_TRANSITION,
                        "[%d] PendingFinishTransitionHandler.onTransitionConsumed: "
                                + "Consumed pending finish transition", mInstanceId);
                onFinishInner(null /* wct */);
            }
        };
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
         * Called when a recents request comes in. If the handler wants to "accept" the transition,
         * it should return a Consumer accepting the IBinder for the transition. If not, it should
         * return `null`.
         *
         * If a mixed-handler accepts this recents, it will be the de-facto handler for this
         * transition and is required to call the associated {@link #startAnimation},
         * {@link #mergeAnimation}, and {@link #onTransitionConsumed} methods.
         */
        @Nullable
        Consumer<IBinder> handleRecentsRequest(int displayId);

        /**
         * Called when a recents transition has finished, with a WCT and SurfaceControl Transaction
         * that can be used to add to any changes needed to restore the state.
         */
        void handleFinishRecents(boolean returnToApp, @NonNull WindowContainerTransaction finishWct,
                @NonNull SurfaceControl.Transaction finishT);
    }
}
