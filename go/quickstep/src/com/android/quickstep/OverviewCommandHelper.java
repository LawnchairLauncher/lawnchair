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

import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;
import static com.android.systemui.shared.system.ActivityManagerWrapper.CLOSE_SYSTEM_WINDOWS_REASON_RECENTS;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.os.SystemClock;
import android.view.ViewConfiguration;

import com.android.launcher3.BaseDraggingActivity;
import com.android.launcher3.logging.UserEventDispatcher;
import com.android.launcher3.userevent.nano.LauncherLogProto;
import com.android.quickstep.ActivityControlHelper.ActivityInitListener;
import com.android.quickstep.AppToOverviewAnimationProvider.AppToOverviewAnimationListener;
import com.android.quickstep.views.IconRecentsView;
import com.android.systemui.shared.system.ActivityManagerWrapper;
import com.android.systemui.shared.system.LatencyTrackerCompat;

/**
 * Helper class to handle various atomic commands for switching between Overview.
 */
@TargetApi(Build.VERSION_CODES.P)
public class OverviewCommandHelper {

    private final Context mContext;
    private final ActivityManagerWrapper mAM;
    private final RecentsModel mRecentsModel;
    private final OverviewComponentObserver mOverviewComponentObserver;

    private long mLastToggleTime;

    public OverviewCommandHelper(Context context, OverviewComponentObserver observer) {
        mContext = context;
        mAM = ActivityManagerWrapper.getInstance();
        mRecentsModel = RecentsModel.INSTANCE.get(mContext);
        mOverviewComponentObserver = observer;
    }

    public void onOverviewToggle() {
        // If currently screen pinning, do not enter overview
        if (mAM.isScreenPinningActive()) {
            return;
        }

        mAM.closeSystemWindows(CLOSE_SYSTEM_WINDOWS_REASON_RECENTS);
        MAIN_EXECUTOR.execute(new RecentsActivityCommand<>());
    }

    public void onOverviewShown(boolean triggeredFromAltTab) {
        MAIN_EXECUTOR.execute(new ShowRecentsCommand());
    }

    public void onOverviewHidden() {
        MAIN_EXECUTOR.execute(new HideRecentsCommand());
    }

    public void onTip(int actionType, int viewType) {
        MAIN_EXECUTOR.execute(() ->
                UserEventDispatcher.newInstance(mContext).logActionTip(actionType, viewType));
    }

    private class ShowRecentsCommand extends RecentsActivityCommand {

        @Override
        protected boolean handleCommand(long elapsedTime) {
            return mHelper.getVisibleRecentsView() != null;
        }
    }

    private class HideRecentsCommand extends RecentsActivityCommand {

        @Override
        protected boolean handleCommand(long elapsedTime) {
            IconRecentsView recents = (IconRecentsView) mHelper.getVisibleRecentsView();
            if (recents == null) {
                return false;
            }
            recents.handleOverviewCommand();
            return true;
        }
    }

    private class RecentsActivityCommand<T extends BaseDraggingActivity> implements Runnable {

        protected final ActivityControlHelper<T> mHelper;
        private final long mCreateTime;

        private final long mToggleClickedTime = SystemClock.uptimeMillis();
        private boolean mUserEventLogged;
        private ActivityInitListener mListener;

        public RecentsActivityCommand() {
            mHelper = mOverviewComponentObserver.getActivityControlHelper();
            mCreateTime = SystemClock.elapsedRealtime();

            // Preload the plan
            mRecentsModel.getTasks(null);
        }

        @Override
        public void run() {
            long elapsedTime = mCreateTime - mLastToggleTime;
            mLastToggleTime = mCreateTime;

            if (handleCommand(elapsedTime)) {
                // Command already handled.
                return;
            }

            if (mHelper.switchToRecentsIfVisible(null /* onCompleteCallback */)) {
                // If successfully switched, then return
                return;
            }

            AppToOverviewAnimationProvider<T> provider =
                    new AppToOverviewAnimationProvider<>(mHelper, RecentsModel.getRunningTaskId());
            provider.setAnimationListener(
                    new AppToOverviewAnimationListener() {
                        @Override
                        public void onActivityReady(BaseDraggingActivity activity) {
                            if (!mUserEventLogged) {
                                activity.getUserEventDispatcher().logActionCommand(
                                        LauncherLogProto.Action.Command.RECENTS_BUTTON,
                                        mHelper.getContainerType(),
                                        LauncherLogProto.ContainerType.TASKSWITCHER);
                                mUserEventLogged = true;
                            }
                        }

                        @Override
                        public void onWindowAnimationCreated() {
                            if (LatencyTrackerCompat.isEnabled(mContext)) {
                                LatencyTrackerCompat.logToggleRecents(
                                        (int) (SystemClock.uptimeMillis() - mToggleClickedTime));
                            }

                            mListener.unregister();
                        }
                    });

            // Otherwise, start overview.
            mListener = mHelper.createActivityInitListener(provider::onActivityReady);
            mListener.registerAndStartActivity(mOverviewComponentObserver.getOverviewIntent(),
                    provider, mContext, MAIN_EXECUTOR.getHandler(),
                    provider.getRecentsLaunchDuration());
        }

        protected boolean handleCommand(long elapsedTime) {
            IconRecentsView recents = mHelper.getVisibleRecentsView();
            if (recents != null) {
                recents.handleOverviewCommand();
                return true;
            } else if (elapsedTime < ViewConfiguration.getDoubleTapTimeout()) {
                // The user tried to launch back into overview too quickly, either after
                // launching an app, or before overview has actually shown, just ignore for now
                return true;
            }
            return false;
        }
    }
}
