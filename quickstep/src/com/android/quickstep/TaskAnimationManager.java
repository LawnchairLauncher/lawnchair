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

import static com.android.launcher3.Flags.enableHandleDelayedGestureCallbacks;
import static com.android.launcher3.Flags.enableScalingRevealHomeAnimation;
import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;
import static com.android.launcher3.util.NavigationMode.NO_BUTTON;
import static com.android.quickstep.GestureState.GestureEndTarget.RECENTS;
import static com.android.quickstep.GestureState.STATE_END_TARGET_ANIMATION_FINISHED;
import static com.android.quickstep.GestureState.STATE_RECENTS_ANIMATION_INITIALIZED;
import static com.android.quickstep.GestureState.STATE_RECENTS_ANIMATION_STARTED;
import static com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_NOTIFICATION_PANEL_EXPANDED;
import static com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_QUICK_SETTINGS_EXPANDED;

import android.app.ActivityManager;
import android.app.ActivityOptions;
import android.content.Context;
import android.content.Intent;
import android.os.SystemProperties;
import android.util.Log;
import android.view.RemoteAnimationTarget;
import android.window.TransitionInfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;

import com.android.internal.util.ArrayUtils;
import com.android.launcher3.Utilities;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.taskbar.TaskbarUIController;
import com.android.launcher3.util.DisplayController;
import com.android.quickstep.fallback.window.RecentsDisplayModel;
import com.android.quickstep.fallback.window.RecentsWindowFlags;
import com.android.quickstep.fallback.window.RecentsWindowManager;
import com.android.quickstep.util.ActiveGestureProtoLogProxy;
import com.android.quickstep.util.SystemUiFlagUtils;
import com.android.quickstep.views.RecentsView;
import com.android.systemui.shared.recents.model.ThumbnailData;
import com.android.systemui.shared.system.QuickStepContract;
import com.android.systemui.shared.system.TaskStackChangeListener;
import com.android.systemui.shared.system.TaskStackChangeListeners;

import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Locale;

public class TaskAnimationManager implements RecentsAnimationCallbacks.RecentsAnimationListener {
    public static final boolean SHELL_TRANSITIONS_ROTATION =
            SystemProperties.getBoolean("persist.wm.debug.shell_transit_rotate", false);
    private final Context mCtx;
    private RecentsAnimationController mController;
    private RecentsAnimationCallbacks mCallbacks;
    private RecentsAnimationTargets mTargets;
    private TransitionInfo mTransitionInfo;
    private RecentsAnimationDeviceState mDeviceState;

    // Temporary until we can hook into gesture state events
    private GestureState mLastGestureState;
    private RemoteAnimationTarget[] mLastAppearedTaskTargets;
    private Runnable mLiveTileCleanUpHandler;

    private boolean mRecentsAnimationStartPending = false;
    private boolean mShouldIgnoreMotionEvents = false;
    private final int mDisplayId;

    private final TaskStackChangeListener mLiveTileRestartListener = new TaskStackChangeListener() {
        @Override
        public void onActivityRestartAttempt(ActivityManager.RunningTaskInfo task,
                boolean homeTaskVisible, boolean clearedTask, boolean wasVisible) {
            if (mLastGestureState == null) {
                TaskStackChangeListeners.getInstance().unregisterTaskStackListener(
                        mLiveTileRestartListener);
                return;
            }
            BaseContainerInterface containerInterface = mLastGestureState.getContainerInterface();
            if (containerInterface.isInLiveTileMode()
                    && containerInterface.getCreatedContainer() != null) {
                RecentsView recentsView = containerInterface.getCreatedContainer()
                        .getOverviewPanel();
                if (recentsView != null) {
                    recentsView.launchSideTaskInLiveTileModeForRestartedApp(task.taskId);
                    TaskStackChangeListeners.getInstance().unregisterTaskStackListener(
                            mLiveTileRestartListener);
                }
            }
        }
    };

    public TaskAnimationManager(Context ctx, RecentsAnimationDeviceState deviceState,
            int displayId) {
        mCtx = ctx;
        mDeviceState = deviceState;
        mDisplayId = displayId;
    }

    SystemUiProxy getSystemUiProxy() {
        return SystemUiProxy.INSTANCE.get(mCtx);
    }

    boolean shouldIgnoreMotionEvents() {
        return mShouldIgnoreMotionEvents;
    }

    void notifyNewGestureStart() {
        // If mRecentsAnimationStartPending is true at the beginning of a gesture, block all motion
        // events for this new gesture so that this new gesture does not interfere with the
        // previously-requested recents animation. Otherwise, clean up mShouldIgnoreMotionEvents.
        // NOTE: this can lead to misleading logs
        mShouldIgnoreMotionEvents = mRecentsAnimationStartPending;
    }

    /**
     * Starts a new recents animation for the activity with the given {@param intent}.
     */
    @UiThread
    public RecentsAnimationCallbacks startRecentsAnimation(@NonNull GestureState gestureState,
            Intent intent, RecentsAnimationCallbacks.RecentsAnimationListener listener) {
        ActiveGestureProtoLogProxy.logStartRecentsAnimation();
        // Check displayId
        if (mDisplayId != gestureState.getDisplayId()) {
            String msg = String.format(Locale.ENGLISH,
                    "Constructor displayId %d does not equal gestureState display id %d",
                    mDisplayId, gestureState.getDisplayId());
            if (FeatureFlags.IS_STUDIO_BUILD) {
                throw new IllegalArgumentException(msg);
            } else {
                Log.e("TaskAnimationManager", msg, new Exception());
            }
        }
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
        finishRunningRecentsAnimation(false /* toHome */, true /* forceFinish */,
                null /* forceFinishCb */);

        if (mCallbacks != null) {
            // If mCallbacks still != null, that means we are getting this startRecentsAnimation()
            // before the previous one got onRecentsAnimationStart(). In that case, cleanup the
            // previous animation so it doesn't mess up/listen to state changes in this animation.
            cleanUpRecentsAnimation(mCallbacks);
        }

        final BaseContainerInterface containerInterface = gestureState.getContainerInterface();
        mLastGestureState = gestureState;
        RecentsAnimationCallbacks newCallbacks = new RecentsAnimationCallbacks(getSystemUiProxy());
        mCallbacks = newCallbacks;
        mCallbacks.addListener(new RecentsAnimationCallbacks.RecentsAnimationListener() {
            @Override
            public void onRecentsAnimationStart(RecentsAnimationController controller,
                    RecentsAnimationTargets targets, @Nullable TransitionInfo transitionInfo) {
                if (enableHandleDelayedGestureCallbacks() && mRecentsAnimationStartPending) {
                    ActiveGestureProtoLogProxy.logStartRecentsAnimationCallback(
                            "onRecentsAnimationStart");
                    mRecentsAnimationStartPending = false;
                }
                if (mCallbacks == null) {
                    // It's possible for the recents animation to have finished and be cleaned up
                    // by the time we process the start callback, and in that case, just we can skip
                    // handling this call entirely
                    return;
                }
                mController = controller;
                mTargets = targets;
                mTransitionInfo = transitionInfo;
                // TODO(b/236226779): We can probably get away w/ setting mLastAppearedTaskTargets
                //  to all appeared targets directly vs just looking at running ones
                int[] runningTaskIds = mLastGestureState.getRunningTaskIds(targets.apps.length > 1);
                mLastAppearedTaskTargets = new RemoteAnimationTarget[runningTaskIds.length];
                for (int i = 0; i < runningTaskIds.length; i++) {
                    RemoteAnimationTarget task = mTargets.findTask(runningTaskIds[i]);
                    mLastAppearedTaskTargets[i] = task;
                }
                mLastGestureState.updateLastAppearedTaskTargets(mLastAppearedTaskTargets);

                if (mTargets.hasRecents
                        // The filtered (MODE_CLOSING) targets only contain 1 home activity.
                        && mTargets.apps.length == 1
                        && mTargets.apps[0].windowConfiguration.getActivityType()
                        == ACTIVITY_TYPE_HOME) {
                    // This is launching RecentsActivity on top of a 3p launcher. There are no
                    // other apps need to keep visible so finish the animating state after the
                    // enter animation of overview is done. Then 3p launcher can be stopped.
                    mLastGestureState.runOnceAtState(STATE_END_TARGET_ANIMATION_FINISHED, () -> {
                        if (mLastGestureState != gestureState) return;
                        // Only finish if the end target is RECENTS. Otherwise, if the target is
                        // NEW_TASK, startActivityFromRecents will be skipped.
                        if (mLastGestureState.getEndTarget() == RECENTS) {
                            finishRunningRecentsAnimation(false /* toHome */);
                        }
                    });
                }
            }

            @Override
            public void onRecentsAnimationCanceled(HashMap<Integer, ThumbnailData> thumbnailDatas) {
                if (enableHandleDelayedGestureCallbacks() && mRecentsAnimationStartPending) {
                    ActiveGestureProtoLogProxy.logStartRecentsAnimationCallback(
                            "onRecentsAnimationCanceled");
                    mRecentsAnimationStartPending = false;
                }
                cleanUpRecentsAnimation(newCallbacks);
            }

            @Override
            public void onRecentsAnimationFinished(RecentsAnimationController controller) {
                if (enableHandleDelayedGestureCallbacks() && mRecentsAnimationStartPending) {
                    ActiveGestureProtoLogProxy.logStartRecentsAnimationCallback(
                            "onRecentsAnimationFinished");
                    mRecentsAnimationStartPending = false;
                }
                cleanUpRecentsAnimation(newCallbacks);
            }

            private boolean isNonRecentsStartedTasksAppeared(
                    RemoteAnimationTarget[] appearedTaskTargets) {
                // For example, right after swiping from task X to task Y (e.g. from
                // AbsSwipeUpHandler#startNewTask), and then task Y starts X immediately
                // (e.g. in Y's onResume). The case will be: lastStartedTask=Y and appearedTask=X.
                return mLastGestureState.getEndTarget() == GestureState.GestureEndTarget.NEW_TASK
                        && ArrayUtils.find(appearedTaskTargets,
                                mLastGestureState.mLastStartedTaskIdPredicate) == null;
            }

            @Override
            public void onTasksAppeared(RemoteAnimationTarget[] appearedTaskTargets,
                    @Nullable TransitionInfo transitionInfo) {
                RemoteAnimationTarget appearedTaskTarget = appearedTaskTargets[0];
                BaseContainerInterface containerInterface =
                        mLastGestureState.getContainerInterface();
                for (RemoteAnimationTarget compat : appearedTaskTargets) {
                    if (compat.windowConfiguration.getActivityType() == ACTIVITY_TYPE_HOME
                            && containerInterface.getCreatedContainer() instanceof RecentsActivity
                            && DisplayController.INSTANCE.get(mCtx).getInfoForDisplay(
                            mDisplayId).getNavigationMode() != NO_BUTTON) {
                        // The only time we get onTasksAppeared() in button navigation with a
                        // 3p launcher is if the user goes to overview first, and in this case we
                        // can immediately finish the transition
                        RecentsView recentsView =
                                containerInterface.getCreatedContainer().getOverviewPanel();
                        if (recentsView != null) {
                            recentsView.finishRecentsAnimation(true, null);
                        }
                        return;
                    }
                }

                RemoteAnimationTarget[] nonAppTargets = new RemoteAnimationTarget[0];
                if ((containerInterface.isInLiveTileMode()
                            || mLastGestureState.getEndTarget() == RECENTS
                            || isNonRecentsStartedTasksAppeared(appearedTaskTargets))
                        && containerInterface.getCreatedContainer() != null) {
                    RecentsView recentsView =
                            containerInterface.getCreatedContainer().getOverviewPanel();
                    if (recentsView != null) {
                        ActiveGestureProtoLogProxy.logLaunchingSideTask(appearedTaskTarget.taskId);
                        recentsView.launchSideTaskInLiveTileMode(appearedTaskTarget.taskId,
                                appearedTaskTargets,
                                new RemoteAnimationTarget[0] /* wallpaper */,
                                nonAppTargets /* nonApps */,
                                transitionInfo);
                        return;
                    } else {
                        ActiveGestureProtoLogProxy.logLaunchingSideTaskFailed();
                    }
                } else if (nonAppTargets.length > 0) {
                    TaskViewUtils.createSplitAuxiliarySurfacesAnimator(nonAppTargets /* nonApps */,
                            true /*shown*/, null /* animatorHandler */);
                }
                if (mController != null) {
                    mLastAppearedTaskTargets = appearedTaskTargets;
                    mLastGestureState.updateLastAppearedTaskTargets(mLastAppearedTaskTargets);
                }
            }
        });
        final long eventTime = gestureState.getSwipeUpStartTimeMs();
        mCallbacks.addListener(gestureState);
        mCallbacks.addListener(listener);

        final ActivityOptions options = ActivityOptions.makeBasic();
        options.setPendingIntentBackgroundActivityStartMode(
                ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_ALWAYS);
        options.setTransientLaunch();
        options.setSourceInfo(ActivityOptions.SourceInfo.TYPE_RECENTS_ANIMATION, eventTime);

        // Notify taskbar that we should skip reacting to launcher visibility change to
        // avoid a jumping taskbar.
        TaskbarUIController taskbarUIController = containerInterface.getTaskbarController();
        if (enableScalingRevealHomeAnimation() && taskbarUIController != null) {
            taskbarUIController.setSkipLauncherVisibilityChange(true);

            mCallbacks.addListener(new RecentsAnimationCallbacks.RecentsAnimationListener() {
                @Override
                public void onRecentsAnimationCanceled(
                        @NonNull HashMap<Integer, ThumbnailData> thumbnailDatas) {
                    taskbarUIController.setSkipLauncherVisibilityChange(false);
                }

                @Override
                public void onRecentsAnimationFinished(
                        @NonNull RecentsAnimationController controller) {
                    taskbarUIController.setSkipLauncherVisibilityChange(false);
                }
            });
        }

        if(containerInterface.getCreatedContainer() instanceof RecentsWindowManager
                && RecentsWindowFlags.Companion.getEnableOverviewInWindow()) {
            mRecentsAnimationStartPending = getSystemUiProxy().startRecentsActivity(intent, options,
                    mCallbacks, gestureState.useSyntheticRecentsTransition());
            RecentsDisplayModel.getINSTANCE().get(mCtx)
                    .getRecentsWindowManager(gestureState.getDisplayId())
                    .startRecentsWindow(mCallbacks);
        } else {
            mRecentsAnimationStartPending = getSystemUiProxy().startRecentsActivity(intent,
                    options, mCallbacks, false /* useSyntheticRecentsTransition */);
        }

        if (enableHandleDelayedGestureCallbacks()) {
            ActiveGestureProtoLogProxy.logSettingRecentsAnimationStartPending(
                    mRecentsAnimationStartPending);
        }
        gestureState.setState(STATE_RECENTS_ANIMATION_INITIALIZED);
        return mCallbacks;
    }

    /**
     * Continues the existing running recents animation for a new gesture.
     */
    public RecentsAnimationCallbacks continueRecentsAnimation(GestureState gestureState) {
        ActiveGestureProtoLogProxy.logContinueRecentsAnimation();
        mCallbacks.removeListener(mLastGestureState);
        mLastGestureState = gestureState;
        mCallbacks.addListener(gestureState);
        gestureState.setState(STATE_RECENTS_ANIMATION_INITIALIZED
                | STATE_RECENTS_ANIMATION_STARTED);
        gestureState.updateLastAppearedTaskTargets(mLastAppearedTaskTargets);
        return mCallbacks;
    }

    public void onSystemUiFlagsChanged(@QuickStepContract.SystemUiStateFlags long lastSysUIFlags,
            @QuickStepContract.SystemUiStateFlags long newSysUIFlags) {
        long isShadeExpandedFlagMask =
                SYSUI_STATE_NOTIFICATION_PANEL_EXPANDED | SYSUI_STATE_QUICK_SETTINGS_EXPANDED;
        boolean wasExpanded = hasAnyFlag(lastSysUIFlags, isShadeExpandedFlagMask);
        boolean isExpanded = hasAnyFlag(newSysUIFlags, isShadeExpandedFlagMask);
        if (wasExpanded != isExpanded && isExpanded) {
            // End live tile when expanding the notification panel for the first time from
            // overview.
            if (endLiveTile()) {
                return;
            }
        }

        boolean wasLocked = SystemUiFlagUtils.isLocked(lastSysUIFlags);
        boolean isLocked = SystemUiFlagUtils.isLocked(newSysUIFlags);
        if (wasLocked != isLocked && isLocked) {
            // Finish the running recents animation when locking the device.
            finishRunningRecentsAnimation(
                    mController != null && mController.getFinishTargetIsLauncher());
        }
    }

    private boolean hasAnyFlag(long flags, long flagMask) {
        return (flags & flagMask) != 0;
    }

    /**
     * Switches the {@link RecentsView} to screenshot if in live tile mode.
     *
     * @return true iff the {@link RecentsView} was in live tile mode and was switched to screenshot
     */
    public boolean endLiveTile() {
        if (mLastGestureState == null) {
            return false;
        }
        BaseContainerInterface containerInterface = mLastGestureState.getContainerInterface();
        if (!containerInterface.isInLiveTileMode()
                || containerInterface.getCreatedContainer() == null) {
            return false;
        }
        RecentsView recentsView = containerInterface.getCreatedContainer().getOverviewPanel();
        if (recentsView == null) {
            return false;
        }
        recentsView.switchToScreenshot(null, () -> recentsView.finishRecentsAnimation(
                true /* toRecents */, false /* shouldPip */, null));
        return true;
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
        finishRunningRecentsAnimation(toHome, false /* forceFinish */, null /* forceFinishCb */);
    }
    public void finishRunningRecentsAnimation(
            boolean toHome, boolean forceFinish, Runnable forceFinishCb) {
        finishRunningRecentsAnimation(toHome, forceFinish, forceFinishCb, mController);
    }

    /**
     * Finishes the running recents animation.
     * @param forceFinish will synchronously finish the controller
     */
    public void finishRunningRecentsAnimation(
            boolean toHome,
            boolean forceFinish,
            @Nullable Runnable forceFinishCb,
            @Nullable RecentsAnimationController controller) {
        if (controller != null) {
            ActiveGestureProtoLogProxy.logFinishRunningRecentsAnimation(toHome);
            if (forceFinish) {
                controller.finishController(toHome, forceFinishCb, false /* sendUserLeaveHint */,
                        true /* forceFinish */);
            } else {
                Utilities.postAsyncCallback(MAIN_EXECUTOR.getHandler(), toHome
                        ? controller::finishAnimationToHome
                        : controller::finishAnimationToApp);
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
            listener.onRecentsAnimationStart(mController, mTargets, mTransitionInfo);
        }
        // TODO: Do we actually need to report canceled/finished?
    }

    /**
     * @return whether there is a recents animation running.
     */
    public boolean isRecentsAnimationRunning() {
        return mController != null;
    }

    void onLauncherDestroyed() {
        if (!mRecentsAnimationStartPending) {
            return;
        }
        if (mCallbacks == null) {
            return;
        }
        ActiveGestureProtoLogProxy.logQueuingForceFinishRecentsAnimation();
        mCallbacks.addListener(new RecentsAnimationCallbacks.RecentsAnimationListener() {
            @Override
            public void onRecentsAnimationStart(
                    RecentsAnimationController controller,
                    RecentsAnimationTargets targets,
                    @Nullable TransitionInfo transitionInfo) {
                finishRunningRecentsAnimation(
                        /* toHome= */ false,
                        /* forceFinish= */ true,
                        /* forceFinishCb= */ null,
                        controller);
            }
        });
    }

    /**
     * Cleans up the recents animation entirely.
     */
    private void cleanUpRecentsAnimation(RecentsAnimationCallbacks targetCallbacks) {
        if (mCallbacks != targetCallbacks) {
            ActiveGestureProtoLogProxy.logCleanUpRecentsAnimationSkipped();
            return;
        }
        ActiveGestureProtoLogProxy.logCleanUpRecentsAnimation();
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
        mTransitionInfo = null;
        mLastGestureState = null;
        mLastAppearedTaskTargets = null;
    }

    @Nullable
    public RecentsAnimationCallbacks getCurrentCallbacks() {
        return mCallbacks;
    }

    public void dump(String prefix, PrintWriter pw) {
        pw.println(prefix + "TaskAnimationManager:");
        pw.println(prefix + "\tmDisplayId=" + mDisplayId);

        if (enableHandleDelayedGestureCallbacks()) {
            pw.println(prefix + "\tmRecentsAnimationStartPending=" + mRecentsAnimationStartPending);
            pw.println(prefix + "\tmShouldIgnoreUpcomingGestures=" + mShouldIgnoreMotionEvents);
        }
        if (mController != null) {
            mController.dump(prefix + '\t', pw);
        }
        if (mCallbacks != null) {
            mCallbacks.dump(prefix + '\t', pw);
        }
        if (mTargets != null) {
            mTargets.dump(prefix + '\t', pw);
        }
        if (mLastGestureState != null) {
            mLastGestureState.dump(prefix + '\t', pw);
        }
    }
}
