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

import static android.app.WindowConfiguration.ACTIVITY_TYPE_HOME;

import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;
import static com.android.launcher3.util.Executors.UI_HELPER_EXECUTOR;
import static com.android.launcher3.util.NavigationMode.NO_BUTTON;
import static com.android.quickstep.GestureState.GestureEndTarget.RECENTS;
import static com.android.quickstep.GestureState.STATE_RECENTS_ANIMATION_INITIALIZED;
import static com.android.quickstep.GestureState.STATE_RECENTS_ANIMATION_STARTED;
import static com.android.quickstep.util.ActiveGestureErrorDetector.GestureEvent.START_RECENTS_ANIMATION;

import android.app.ActivityManager;
import android.app.ActivityOptions;
import android.content.Context;
import android.content.Intent;
import android.os.SystemProperties;
import android.util.Log;
import android.view.RemoteAnimationTarget;

import androidx.annotation.Nullable;
import androidx.annotation.UiThread;

import com.android.launcher3.Utilities;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.testing.shared.TestProtocol;
import com.android.launcher3.util.DisplayController;
import com.android.quickstep.TopTaskTracker.CachedTaskInfo;
import com.android.quickstep.util.ActiveGestureLog;
import com.android.quickstep.views.DesktopTaskView;
import com.android.quickstep.views.RecentsView;
import com.android.systemui.shared.recents.model.ThumbnailData;
import com.android.systemui.shared.system.ActivityManagerWrapper;
import com.android.systemui.shared.system.TaskStackChangeListener;
import com.android.systemui.shared.system.TaskStackChangeListeners;

import java.util.HashMap;

public class TaskAnimationManager implements RecentsAnimationCallbacks.RecentsAnimationListener {
    public static final boolean ENABLE_SHELL_TRANSITIONS =
            SystemProperties.getBoolean("persist.wm.debug.shell_transit", true);
    public static final boolean SHELL_TRANSITIONS_ROTATION = ENABLE_SHELL_TRANSITIONS
            && SystemProperties.getBoolean("persist.wm.debug.shell_transit_rotate", false);

    private RecentsAnimationController mController;
    private RecentsAnimationCallbacks mCallbacks;
    private RecentsAnimationTargets mTargets;
    // Temporary until we can hook into gesture state events
    private GestureState mLastGestureState;
    private RemoteAnimationTarget[] mLastAppearedTaskTargets;
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
            if (activityInterface.isInLiveTileMode()
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
        ActiveGestureLog.INSTANCE.addLog(
                /* event= */ "startRecentsAnimation",
                /* gestureEvent= */ START_RECENTS_ANIMATION);
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
        finishRunningRecentsAnimation(false /* toHome */, true /* forceFinish */);

        if (mCallbacks != null) {
            // If mCallbacks still != null, that means we are getting this startRecentsAnimation()
            // before the previous one got onRecentsAnimationStart(). In that case, cleanup the
            // previous animation so it doesn't mess up/listen to state changes in this animation.
            cleanUpRecentsAnimation(mCallbacks);
        }

        final BaseActivityInterface activityInterface = gestureState.getActivityInterface();
        mLastGestureState = gestureState;
        RecentsAnimationCallbacks newCallbacks = new RecentsAnimationCallbacks(
                SystemUiProxy.INSTANCE.get(mCtx), activityInterface.allowMinimizeSplitScreen());
        mCallbacks = newCallbacks;
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
                // TODO(b/236226779): We can probably get away w/ setting mLastAppearedTaskTargets
                //  to all appeared targets directly vs just looking at running ones
                int[] runningTaskIds = mLastGestureState.getRunningTaskIds(targets.apps.length > 1);
                mLastAppearedTaskTargets = new RemoteAnimationTarget[runningTaskIds.length];
                for (int i = 0; i < runningTaskIds.length; i++) {
                    RemoteAnimationTarget task = mTargets.findTask(runningTaskIds[i]);
                    mLastAppearedTaskTargets[i] = task;
                }
                mLastGestureState.updateLastAppearedTaskTargets(mLastAppearedTaskTargets);
            }

            @Override
            public void onRecentsAnimationCanceled(HashMap<Integer, ThumbnailData> thumbnailDatas) {
                cleanUpRecentsAnimation(newCallbacks);
            }

            @Override
            public void onRecentsAnimationFinished(RecentsAnimationController controller) {
                cleanUpRecentsAnimation(newCallbacks);
            }

            @Override
            public void onTasksAppeared(RemoteAnimationTarget[] appearedTaskTargets) {
                RemoteAnimationTarget appearedTaskTarget = appearedTaskTargets[0];
                BaseActivityInterface activityInterface = mLastGestureState.getActivityInterface();

                for (RemoteAnimationTarget compat : appearedTaskTargets) {
                    if (compat.windowConfiguration.getActivityType() == ACTIVITY_TYPE_HOME
                            && activityInterface.getCreatedActivity() instanceof RecentsActivity
                            && DisplayController.getNavigationMode(mCtx) != NO_BUTTON) {
                        // The only time we get onTasksAppeared() in button navigation with a
                        // 3p launcher is if the user goes to overview first, and in this case we
                        // can immediately finish the transition
                        RecentsView recentsView =
                                activityInterface.getCreatedActivity().getOverviewPanel();
                        if (recentsView != null) {
                            Log.d(TestProtocol.INCORRECT_HOME_STATE,
                                    "finish recents animation on "
                                            + compat.taskInfo.description);
                            recentsView.finishRecentsAnimation(true, null);
                        }
                        return;
                    }
                }

                RemoteAnimationTarget[] nonAppTargets = SystemUiProxy.INSTANCE.get(mCtx)
                        .onStartingSplitLegacy(appearedTaskTargets);
                if (nonAppTargets == null) {
                    nonAppTargets = new RemoteAnimationTarget[0];
                }
                if ((activityInterface.isInLiveTileMode()
                            || mLastGestureState.getEndTarget() == RECENTS)
                        && activityInterface.getCreatedActivity() != null) {
                    RecentsView recentsView =
                            activityInterface.getCreatedActivity().getOverviewPanel();
                    if (recentsView != null) {
                        ActiveGestureLog.INSTANCE.addLog("Launching side task id="
                                + appearedTaskTarget.taskId);
                        recentsView.launchSideTaskInLiveTileMode(appearedTaskTarget.taskId,
                                appearedTaskTargets,
                                new RemoteAnimationTarget[0] /* wallpaper */,
                                nonAppTargets /* nonApps */);
                        return;
                    } else {
                        ActiveGestureLog.INSTANCE.addLog("Unable to launch side task (no recents)");
                    }
                } else if (nonAppTargets.length > 0) {
                    TaskViewUtils.createSplitAuxiliarySurfacesAnimator(nonAppTargets /* nonApps */,
                            true /*shown*/, null /* animatorHandler */);
                }
                if (mController != null) {
                    if (mLastAppearedTaskTargets != null) {
                        for (RemoteAnimationTarget lastTarget : mLastAppearedTaskTargets) {
                            for (RemoteAnimationTarget appearedTarget : appearedTaskTargets) {
                                if (lastTarget != null &&
                                        appearedTarget.taskId != lastTarget.taskId) {
                                    mController.removeTaskTarget(lastTarget.taskId);
                                }
                            }
                        }
                    }
                    mLastAppearedTaskTargets = appearedTaskTargets;
                    mLastGestureState.updateLastAppearedTaskTargets(mLastAppearedTaskTargets);
                }
            }

            @Override
            public boolean onSwitchToScreenshot(Runnable onFinished) {
                if (!activityInterface.isInLiveTileMode()
                        || activityInterface.getCreatedActivity() == null) {
                    // No need to switch since tile is already a screenshot.
                    onFinished.run();
                } else {
                    final RecentsView recentsView =
                            activityInterface.getCreatedActivity().getOverviewPanel();
                    if (recentsView != null) {
                        recentsView.switchToScreenshot(onFinished);
                    } else {
                        onFinished.run();
                    }
                }
                return true;
            }
        });
        final long eventTime = gestureState.getSwipeUpStartTimeMs();
        mCallbacks.addListener(gestureState);
        mCallbacks.addListener(listener);

        if (ENABLE_SHELL_TRANSITIONS) {
            final ActivityOptions options = ActivityOptions.makeBasic();
            // Allowing to pause Home if Home is top activity and Recents is not Home. So when user
            // start home when recents animation is playing, the home activity can be resumed again
            // to let the transition controller collect Home activity.
            CachedTaskInfo cti = gestureState.getRunningTask();
            boolean homeIsOnTop = cti != null && cti.isHomeTask();
            if (DesktopTaskView.DESKTOP_MODE_SUPPORTED) {
                if (cti != null && cti.isFreeformTask()) {
                    // No transient launch when desktop task is on top
                    homeIsOnTop = true;
                }
            }
            if (activityInterface.allowAllAppsFromOverview()) {
                homeIsOnTop = true;
            }
            if (!homeIsOnTop) {
                options.setTransientLaunch();
            }
            options.setSourceInfo(ActivityOptions.SourceInfo.TYPE_RECENTS_ANIMATION, eventTime);
            SystemUiProxy.INSTANCE.getNoCreate().startRecentsActivity(intent, options, mCallbacks);
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
        ActiveGestureLog.INSTANCE.addLog(/* event= */ "continueRecentsAnimation");
        mCallbacks.removeListener(mLastGestureState);
        mLastGestureState = gestureState;
        mCallbacks.addListener(gestureState);
        gestureState.setState(STATE_RECENTS_ANIMATION_INITIALIZED
                | STATE_RECENTS_ANIMATION_STARTED);
        gestureState.updateLastAppearedTaskTargets(mLastAppearedTaskTargets);
        return mCallbacks;
    }

    public void endLiveTile() {
        if (mLastGestureState == null) {
            return;
        }
        BaseActivityInterface activityInterface = mLastGestureState.getActivityInterface();
        if (activityInterface.isInLiveTileMode()
                && activityInterface.getCreatedActivity() != null) {
            RecentsView recentsView = activityInterface.getCreatedActivity().getOverviewPanel();
            if (recentsView != null) {
                recentsView.switchToScreenshot(null,
                        () -> recentsView.finishRecentsAnimation(true /* toRecents */,
                                false /* shouldPip */, null));
            }
        }
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
        finishRunningRecentsAnimation(toHome, false /* forceFinish */);
    }

    /**
     * Finishes the running recents animation.
     * @param forceFinish will synchronously finish the controller
     */
    public void finishRunningRecentsAnimation(boolean toHome, boolean forceFinish) {
        if (mController != null) {
            ActiveGestureLog.INSTANCE.addLog(
                    /* event= */ "finishRunningRecentsAnimation", toHome);
            if (forceFinish) {
                mController.finishController(toHome, null, false /* sendUserLeaveHint */,
                        true /* forceFinish */);
            } else {
                Utilities.postAsyncCallback(MAIN_EXECUTOR.getHandler(), toHome
                        ? mController::finishAnimationToHome
                        : mController::finishAnimationToApp);
            }
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
    private void cleanUpRecentsAnimation(RecentsAnimationCallbacks targetCallbacks) {
        if (mCallbacks != targetCallbacks) {
            ActiveGestureLog.INSTANCE.addLog(
                    /* event= */ "cleanUpRecentsAnimation skipped due to wrong callbacks");
            return;
        }
        ActiveGestureLog.INSTANCE.addLog(/* event= */ "cleanUpRecentsAnimation");
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
        mLastAppearedTaskTargets = null;
    }

    @Nullable
    public RecentsAnimationCallbacks getCurrentCallbacks() {
        return mCallbacks;
    }

    public void dump() {
        // TODO
    }
}
