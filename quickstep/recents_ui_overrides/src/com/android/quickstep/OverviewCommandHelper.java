/*
 * Copyright (C) 2018 The Android Open Source Project
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

import static com.android.systemui.shared.system.ActivityManagerWrapper
        .CLOSE_SYSTEM_WINDOWS_REASON_RECENTS;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.os.SystemClock;
import android.util.Log;
import android.view.ViewConfiguration;

import com.android.launcher3.BaseDraggingActivity;
import com.android.launcher3.MainThreadExecutor;
import com.android.launcher3.logging.UserEventDispatcher;
import com.android.launcher3.testing.TestProtocol;
import com.android.launcher3.userevent.nano.LauncherLogProto;
import com.android.quickstep.ActivityControlHelper.ActivityInitListener;
import com.android.quickstep.views.RecentsView;
import com.android.quickstep.views.TaskView;
import com.android.systemui.shared.system.ActivityManagerWrapper;
import com.android.systemui.shared.system.LatencyTrackerCompat;
import com.android.systemui.shared.system.RemoteAnimationTargetCompat;

/**
 * Helper class to handle various atomic commands for switching between Overview.
 */
@TargetApi(Build.VERSION_CODES.P)
public class OverviewCommandHelper {

    private final Context mContext;
    private final ActivityManagerWrapper mAM;
    private final RecentsModel mRecentsModel;
    private final MainThreadExecutor mMainThreadExecutor;
    private final OverviewComponentObserver mOverviewComponentObserver;

    private long mLastToggleTime;

    public OverviewCommandHelper(Context context, OverviewComponentObserver observer) {
        mContext = context;
        mAM = ActivityManagerWrapper.getInstance();
        mMainThreadExecutor = new MainThreadExecutor();
        mRecentsModel = RecentsModel.INSTANCE.get(mContext);
        mOverviewComponentObserver = observer;
    }

    public void onOverviewToggle() {
        // If currently screen pinning, do not enter overview
        if (mAM.isScreenPinningActive()) {
            return;
        }

        mAM.closeSystemWindows(CLOSE_SYSTEM_WINDOWS_REASON_RECENTS);
        mMainThreadExecutor.execute(new RecentsActivityCommand<>());
    }

    public void onOverviewShown(boolean triggeredFromAltTab) {
        mMainThreadExecutor.execute(new ShowRecentsCommand(triggeredFromAltTab));
    }

    public void onOverviewHidden() {
        mMainThreadExecutor.execute(new HideRecentsCommand());
    }

    public void onTip(int actionType, int viewType) {
        mMainThreadExecutor.execute(() ->
                UserEventDispatcher.newInstance(mContext).logActionTip(actionType, viewType));
    }

    private class ShowRecentsCommand extends RecentsActivityCommand {

        private final boolean mTriggeredFromAltTab;

        ShowRecentsCommand(boolean triggeredFromAltTab) {
            mTriggeredFromAltTab = triggeredFromAltTab;
        }

        @Override
        protected boolean handleCommand(long elapsedTime) {
            // TODO: Go to the next page if started from alt-tab.
            return mHelper.getVisibleRecentsView() != null;
        }

        @Override
        protected void onTransitionComplete() {
            // TODO(b/138729100) This doesn't execute first time launcher is run
            if (mTriggeredFromAltTab) {
                RecentsView rv = (RecentsView) mHelper.getVisibleRecentsView();
                if (rv == null) {
                    return;
                }

                // Ensure that recents view has focus so that it receives the followup key inputs
                TaskView taskView = rv.getNextTaskView();
                if (taskView == null) {
                    if (rv.getTaskViewCount() > 0) {
                        taskView = (TaskView) rv.getPageAt(0);
                        taskView.requestFocus();
                    } else {
                        rv.requestFocus();
                    }
                } else {
                    taskView.requestFocus();
                }
            }
        }
    }

    private class HideRecentsCommand extends RecentsActivityCommand {

        @Override
        protected boolean handleCommand(long elapsedTime) {
            RecentsView recents = (RecentsView) mHelper.getVisibleRecentsView();
            if (recents == null) {
                return false;
            }
            int currentPage = recents.getNextPage();
            if (currentPage >= 0 && currentPage < recents.getTaskViewCount()) {
                ((TaskView) recents.getPageAt(currentPage)).launchTask(true);
            } else {
                recents.startHome();
            }
            return true;
        }
    }

    private class RecentsActivityCommand<T extends BaseDraggingActivity> implements Runnable {

        protected final ActivityControlHelper<T> mHelper;
        private final long mCreateTime;
        private final AppToOverviewAnimationProvider<T> mAnimationProvider;

        private final long mToggleClickedTime = SystemClock.uptimeMillis();
        private boolean mUserEventLogged;
        private ActivityInitListener mListener;

        public RecentsActivityCommand() {
            mHelper = mOverviewComponentObserver.getActivityControlHelper();
            mCreateTime = SystemClock.elapsedRealtime();
            mAnimationProvider =
                    new AppToOverviewAnimationProvider<>(mHelper, RecentsModel.getRunningTaskId());

            // Preload the plan
            mRecentsModel.getTasks(null);
        }

        @Override
        public void run() {
            if (TestProtocol.sDebugTracing) {
                Log.d(TestProtocol.ALL_APPS_UPON_RECENTS, "RecentsActivityCommand.run");
            }
            long elapsedTime = mCreateTime - mLastToggleTime;
            mLastToggleTime = mCreateTime;

            if (handleCommand(elapsedTime)) {
                // Command already handled.
                return;
            }

            if (mHelper.switchToRecentsIfVisible(this::onTransitionComplete)) {
                // If successfully switched, then return
                return;
            }

            // Otherwise, start overview.
            mListener = mHelper.createActivityInitListener(this::onActivityReady);
            mListener.registerAndStartActivity(mOverviewComponentObserver.getOverviewIntent(),
                    this::createWindowAnimation, mContext, mMainThreadExecutor.getHandler(),
                    mAnimationProvider.getRecentsLaunchDuration());
        }

        protected boolean handleCommand(long elapsedTime) {
            // TODO: We need to fix this case with PIP, when an activity first enters PIP, it shows
            //       the menu activity which takes window focus, preventing the right condition from
            //       being run below
            RecentsView recents = mHelper.getVisibleRecentsView();
            if (recents != null) {
                // Launch the next task
                recents.showNextTask();
                return true;
            } else if (elapsedTime < ViewConfiguration.getDoubleTapTimeout()) {
                // The user tried to launch back into overview too quickly, either after
                // launching an app, or before overview has actually shown, just ignore for now
                return true;
            }
            return false;
        }

        private boolean onActivityReady(T activity, Boolean wasVisible) {
            if (!mUserEventLogged) {
                activity.getUserEventDispatcher().logActionCommand(
                        LauncherLogProto.Action.Command.RECENTS_BUTTON,
                        mHelper.getContainerType(),
                        LauncherLogProto.ContainerType.TASKSWITCHER);
                mUserEventLogged = true;
            }
            return mAnimationProvider.onActivityReady(activity, wasVisible);
        }

        private AnimatorSet createWindowAnimation(RemoteAnimationTargetCompat[] targetCompats) {
            if (LatencyTrackerCompat.isEnabled(mContext)) {
                LatencyTrackerCompat.logToggleRecents(
                        (int) (SystemClock.uptimeMillis() - mToggleClickedTime));
            }

            mListener.unregister();

            AnimatorSet animatorSet = mAnimationProvider.createWindowAnimation(targetCompats);
            animatorSet.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    onTransitionComplete();
                }
            });
            return animatorSet;
        }

        protected void onTransitionComplete() { }
    }
}
