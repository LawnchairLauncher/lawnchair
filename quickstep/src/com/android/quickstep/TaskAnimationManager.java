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
package com.android.quickstep;

import static com.android.launcher3.config.FeatureFlags.ENABLE_QUICKSTEP_LIVE_TILE;
import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;
import static com.android.launcher3.util.Executors.UI_HELPER_EXECUTOR;
import static com.android.quickstep.GestureState.STATE_RECENTS_ANIMATION_INITIALIZED;
import static com.android.quickstep.GestureState.STATE_RECENTS_ANIMATION_STARTED;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemProperties;
import android.util.Log;

import androidx.annotation.UiThread;

import com.android.launcher3.Utilities;
import com.android.launcher3.config.FeatureFlags;
import com.android.quickstep.views.RecentsView;
import com.android.systemui.shared.recents.model.ThumbnailData;
import com.android.systemui.shared.system.ActivityManagerWrapper;
import com.android.systemui.shared.system.ActivityOptionsCompat;
import com.android.systemui.shared.system.RemoteAnimationTargetCompat;
import com.android.systemui.shared.system.RemoteTransitionCompat;
import com.android.systemui.shared.system.TaskStackChangeListener;
import com.android.systemui.shared.system.TaskStackChangeListeners;

public class TaskAnimationManager implements RecentsAnimationCallbacks.RecentsAnimationListener {
    public static final boolean ENABLE_SHELL_TRANSITIONS =
            SystemProperties.getBoolean("persist.debug.shell_transit", false);

    private RecentsAnimationController mController;
    private RecentsAnimationCallbacks mCallbacks;
    private RecentsAnimationTargets mTargets;
    // Temporary until we can hook into gesture state events
    private GestureState mLastGestureState;
    private RemoteAnimationTargetCompat mLastAppearedTaskTarget;
    private Runnable mLiveTileCleanUpHandler;
    private Context mCtx;

    private final TaskStackChangeListener mLiveTileRestartListener = new TaskStackChangeListener() {
        @Override
        public void onActivityRestartAttempt(ActivityManager.RunningTaskInfo task,
                boolean homeTaskVisible, boolean clearedTask, boolean wasVisible) {
            if (mLastGestureState == null) {
                TaskStackChangeListeners.getInstance().unregisterTaskStackListener(
                        mLiveTileRestartListener);
                return;
            }
            BaseActivityInterface activityInterface = mLastGestureState.getActivityInterface();
            if (ENABLE_QUICKSTEP_LIVE_TILE.get() && activityInterface.isInLiveTileMode()
                    && activityInterface.getCreatedActivity() != null) {
                RecentsView recentsView = activityInterface.getCreatedActivity().getOverviewPanel();
                if (recentsView != null) {
                    recentsView.launchSideTaskInLiveTileModeForRestartedApp(task.taskId);
                    TaskStackChangeListeners.getInstance().unregisterTaskStackListener(
                            mLiveTileRestartListener);
                }
            }
        }
    };

    TaskAnimationManager(Context ctx) {
        mCtx = ctx;
    }
    /**
     * Preloads the recents animation.
     */
    public void preloadRecentsAnimation(Intent intent) {
        // Pass null animation handler to indicate this start is for preloading
        UI_HELPER_EXECUTOR.execute(() -> ActivityManagerWrapper.getInstance()
                .startRecentsActivity(intent, 0, null, null, null));
    }

    /**
     * Starts a new recents animation for the activity with the given {@param intent}.
     */
    @UiThread
    public RecentsAnimationCallbacks startRecentsAnimation(GestureState gestureState,
            Intent intent, RecentsAnimationCallbacks.RecentsAnimationListener listener) {
        // Notify if recents animation is still running
        if (mController != null) {
            String msg = "New recents animation started before old animation completed";
            if (FeatureFlags.IS_STUDIO_BUILD) {
                throw new IllegalArgumentException(msg);
            } else {
                Log.e("TaskAnimationManager", msg, new Exception());
            }
        }
        // But force-finish it anyways
        finishRunningRecentsAnimation(false /* toHome */);

        final BaseActivityInterface activityInterface = gestureState.getActivityInterface();
        mLastGestureState = gestureState;
        mCallbacks = new RecentsAnimationCallbacks(activityInterface.allowMinimizeSplitScreen());
        mCallbacks.addListener(new RecentsAnimationCallbacks.RecentsAnimationListener() {
            @Override
            public void onRecentsAnimationStart(RecentsAnimationController controller,
                    RecentsAnimationTargets targets) {
                if (mCallbacks == null) {
                    // It's possible for the recents animation to have finished and be cleaned up
                    // by the time we process the start callback, and in that case, just we can skip
                    // handling this call entirely
                    return;
                }
                mController = controller;
                mTargets = targets;
                mLastAppearedTaskTarget = mTargets.findTask(mLastGestureState.getRunningTaskId());
                mLastGestureState.updateLastAppearedTaskTarget(mLastAppearedTaskTarget);
            }

            @Override
            public void onRecentsAnimationCanceled(ThumbnailData thumbnailData) {
                cleanUpRecentsAnimation();
            }

            @Override
            public void onRecentsAnimationFinished(RecentsAnimationController controller) {
                cleanUpRecentsAnimation();
            }

            @Override
            public void onTaskAppeared(RemoteAnimationTargetCompat appearedTaskTarget) {
                BaseActivityInterface activityInterface = mLastGestureState.getActivityInterface();
                if (ENABLE_QUICKSTEP_LIVE_TILE.get() && activityInterface.isInLiveTileMode()
                        && activityInterface.getCreatedActivity() != null) {
                    RecentsView recentsView =
                            activityInterface.getCreatedActivity().getOverviewPanel();
                    if (recentsView != null) {
                        RemoteAnimationTargetCompat[] apps = new RemoteAnimationTargetCompat[1];
                        apps[0] = appearedTaskTarget;
                        recentsView.launchSideTaskInLiveTileMode(appearedTaskTarget.taskId, apps,
                                new RemoteAnimationTargetCompat[0] /* wallpaper */,
                                new RemoteAnimationTargetCompat[0] /* nonApps */);
                        return;
                    }
                }
                if (mController != null) {
                    if (mLastAppearedTaskTarget == null
                            || appearedTaskTarget.taskId != mLastAppearedTaskTarget.taskId) {
                        if (mLastAppearedTaskTarget != null) {
                            mController.removeTaskTarget(mLastAppearedTaskTarget);
                        }
                        mLastAppearedTaskTarget = appearedTaskTarget;
                        mLastGestureState.updateLastAppearedTaskTarget(mLastAppearedTaskTarget);
                    }
                }
            }
        });
        final long eventTime = gestureState.getSwipeUpStartTimeMs();
        mCallbacks.addListener(gestureState);
        mCallbacks.addListener(listener);

        if (ENABLE_SHELL_TRANSITIONS) {
            RemoteTransitionCompat transition = new RemoteTransitionCompat(mCallbacks,
                    mController != null ? mController.getController() : null);
            Bundle options = ActivityOptionsCompat.makeRemoteTransition(transition)
                    .setTransientLaunch().toBundle();
            mCtx.startActivity(intent, options);
        } else {
            UI_HELPER_EXECUTOR.execute(() -> ActivityManagerWrapper.getInstance()
                    .startRecentsActivity(intent, eventTime, mCallbacks, null, null));
        }
        gestureState.setState(STATE_RECENTS_ANIMATION_INITIALIZED);
        return mCallbacks;
    }

    /**
     * Continues the existing running recents animation for a new gesture.
     */
    public RecentsAnimationCallbacks continueRecentsAnimation(GestureState gestureState) {
        mCallbacks.removeListener(mLastGestureState);
        mLastGestureState = gestureState;
        mCallbacks.addListener(gestureState);
        gestureState.setState(STATE_RECENTS_ANIMATION_INITIALIZED
                | STATE_RECENTS_ANIMATION_STARTED);
        gestureState.updateLastAppearedTaskTarget(mLastAppearedTaskTarget);
        return mCallbacks;
    }

    public void setLiveTileCleanUpHandler(Runnable cleanUpHandler) {
        mLiveTileCleanUpHandler = cleanUpHandler;
    }

    public void enableLiveTileRestartListener() {
        TaskStackChangeListeners.getInstance().registerTaskStackListener(mLiveTileRestartListener);
    }

    /**
     * Finishes the running recents animation.
     */
    public void finishRunningRecentsAnimation(boolean toHome) {
        if (mController != null) {
            mCallbacks.notifyAnimationCanceled();
            Utilities.postAsyncCallback(MAIN_EXECUTOR.getHandler(), toHome
                    ? mController::finishAnimationToHome
                    : mController::finishAnimationToApp);
            cleanUpRecentsAnimation();
        }
    }

    /**
     * Used to notify a listener of the current recents animation state (used if the listener was
     * not yet added to the callbacks at the point that the listener callbacks would have been
     * made).
     */
    public void notifyRecentsAnimationState(
            RecentsAnimationCallbacks.RecentsAnimationListener listener) {
        if (isRecentsAnimationRunning()) {
            listener.onRecentsAnimationStart(mController, mTargets);
        }
        // TODO: Do we actually need to report canceled/finished?
    }

    /**
     * @return whether there is a recents animation running.
     */
    public boolean isRecentsAnimationRunning() {
        return mController != null;
    }

    /**
     * Cleans up the recents animation entirely.
     */
    private void cleanUpRecentsAnimation() {
        if (mLiveTileCleanUpHandler != null) {
            mLiveTileCleanUpHandler.run();
            mLiveTileCleanUpHandler = null;
        }
        TaskStackChangeListeners.getInstance().unregisterTaskStackListener(mLiveTileRestartListener);

        // Release all the target leashes
        if (mTargets != null) {
            mTargets.release();
        }

        // Clean up all listeners to ensure we don't get subsequent callbacks
        if (mCallbacks != null) {
            mCallbacks.removeAllListeners();
        }

        mController = null;
        mCallbacks = null;
        mTargets = null;
        mLastGestureState = null;
        mLastAppearedTaskTarget = null;
    }

    public void dump() {
        // TODO
    }
}
