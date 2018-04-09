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

import static com.android.launcher3.anim.Interpolators.FAST_OUT_SLOW_IN;
import static com.android.systemui.shared.system.RemoteAnimationTargetCompat.MODE_CLOSING;

import android.animation.AnimatorSet;
import android.animation.ValueAnimator;
import android.annotation.TargetApi;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.graphics.Rect;
import android.os.Build;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.view.ViewConfiguration;

import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.BaseDraggingActivity;
import com.android.launcher3.MainThreadExecutor;
import com.android.launcher3.anim.AnimatorPlaybackController;
import com.android.quickstep.ActivityControlHelper.ActivityInitListener;
import com.android.quickstep.ActivityControlHelper.FallbackActivityControllerHelper;
import com.android.quickstep.ActivityControlHelper.LauncherActivityControllerHelper;
import com.android.quickstep.util.ClipAnimationHelper;
import com.android.quickstep.util.RemoteAnimationProvider;
import com.android.quickstep.util.SysuiEventLogger;
import com.android.quickstep.views.RecentsView;
import com.android.systemui.shared.system.ActivityManagerWrapper;
import com.android.systemui.shared.system.RemoteAnimationTargetCompat;

/**
 * Helper class to handle various atomic commands for switching between Overview.
 */
@TargetApi(Build.VERSION_CODES.P)
public class OverviewCommandHelper {

    private static final long RECENTS_LAUNCH_DURATION = 200;

    private static final String TAG = "OverviewCommandHelper";
    private static final boolean DEBUG_START_FALLBACK_ACTIVITY = false;

    private final Context mContext;
    private final ActivityManagerWrapper mAM;
    private final RecentsModel mRecentsModel;
    private final MainThreadExecutor mMainThreadExecutor;

    public final Intent homeIntent;
    public final ComponentName launcher;

    private long mLastToggleTime;

    public OverviewCommandHelper(Context context) {
        mContext = context;
        mAM = ActivityManagerWrapper.getInstance();
        mMainThreadExecutor = new MainThreadExecutor();
        mRecentsModel = RecentsModel.getInstance(mContext);

        homeIntent = new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .setPackage(context.getPackageName())
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        ResolveInfo info = context.getPackageManager().resolveActivity(homeIntent, 0);

        if (DEBUG_START_FALLBACK_ACTIVITY) {
            launcher = new ComponentName(context, RecentsActivity.class);
            homeIntent.addCategory(Intent.CATEGORY_DEFAULT)
                    .removeCategory(Intent.CATEGORY_HOME);
        } else {
            launcher = new ComponentName(context.getPackageName(), info.activityInfo.name);
        }

        // Clear the packageName as system can fail to dedupe it b/64108432
        homeIntent.setComponent(launcher).setPackage(null);
    }

    public void onOverviewToggle() {
        // If currently screen pinning, do not enter overview
        if (mAM.isScreenPinningActive()) {
            return;
        }

        mAM.closeSystemWindows("recentapps");
        mMainThreadExecutor.execute(new RecentsActivityCommand<>());
    }

    public void onOverviewShown() {
        mMainThreadExecutor.execute(new ShowRecentsCommand());
    }

    public ActivityControlHelper getActivityControlHelper() {
        if (DEBUG_START_FALLBACK_ACTIVITY) {
            return new FallbackActivityControllerHelper();
        } else {
            return new LauncherActivityControllerHelper();
        }
    }

    private class ShowRecentsCommand extends RecentsActivityCommand {

        @Override
        protected boolean handleCommand(long elapsedTime) {
            RecentsView recents = mHelper.getVisibleRecentsView();
            if (recents != null) {
                recents.snapToTaskAfterNext();
                return true;
            } else {
                return false;
            }
        }
    }

    private class RecentsActivityCommand<T extends BaseDraggingActivity> implements Runnable {

        protected final ActivityControlHelper<T> mHelper;
        private final long mCreateTime;
        private final int mRunningTaskId;

        private ActivityInitListener mListener;
        private T mActivity;

        public RecentsActivityCommand() {
            mHelper = getActivityControlHelper();
            mCreateTime = SystemClock.elapsedRealtime();
            mRunningTaskId = mAM.getRunningTask().id;

            // Preload the plan
            mRecentsModel.loadTasks(mRunningTaskId, null);
        }

        @Override
        public void run() {
            long elapsedTime = mCreateTime - mLastToggleTime;
            mLastToggleTime = mCreateTime;

            if (!handleCommand(elapsedTime)) {
                // Start overview
                if (mHelper.switchToRecentsIfVisible()) {
                    SysuiEventLogger.writeDummyRecentsTransition(0);
                    // Do nothing
                } else {
                    mListener = mHelper.createActivityInitListener(this::onActivityReady);
                    mListener.registerAndStartActivity(homeIntent, this::createWindowAnimation,
                            mContext, mMainThreadExecutor.getHandler(), RECENTS_LAUNCH_DURATION);
                }
            }
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
            activity.<RecentsView>getOverviewPanel().setCurrentTask(mRunningTaskId);
            AbstractFloatingView.closeAllOpenViews(activity, wasVisible);
            mHelper.prepareRecentsUI(activity, wasVisible);
            if (wasVisible) {
                AnimatorPlaybackController controller =
                        mHelper.createControllerForVisibleActivity(activity);
                controller.dispatchOnStart();
                ValueAnimator anim =
                        controller.getAnimationPlayer().setDuration(RECENTS_LAUNCH_DURATION);
                anim.setInterpolator(FAST_OUT_SLOW_IN);
                anim.start();
            }
            mActivity = activity;
            return false;
        }

        private AnimatorSet createWindowAnimation(RemoteAnimationTargetCompat[] targetCompats) {
            if (mListener != null) {
                mListener.unregister();
            }
            RemoteAnimationProvider.showOpeningTarget(targetCompats);
            AnimatorSet anim = new AnimatorSet();
            if (mActivity == null) {
                Log.e(TAG, "Animation created, before activity");
                anim.play(ValueAnimator.ofInt(0, 1).setDuration(100));
                return anim;
            }

            RemoteAnimationTargetCompat closingTarget = null;
            // Use the top closing app to determine the insets for the animation
            for (RemoteAnimationTargetCompat target : targetCompats) {
                if (target.mode == MODE_CLOSING) {
                    closingTarget = target;
                    break;
                }
            }
            if (closingTarget == null) {
                Log.e(TAG, "No closing app");
                anim.play(ValueAnimator.ofInt(0, 1).setDuration(100));
                return anim;
            }

            final ClipAnimationHelper clipHelper = new ClipAnimationHelper();

            // At this point, the activity is already started and laid-out. Get the home-bounds
            // relative to the screen using the rootView of the activity.
            int loc[] = new int[2];
            View rootView = mActivity.getRootView();
            rootView.getLocationOnScreen(loc);
            Rect homeBounds = new Rect(loc[0], loc[1],
                    loc[0] + rootView.getWidth(), loc[1] + rootView.getHeight());
            clipHelper.updateSource(homeBounds, closingTarget);

            Rect targetRect = new Rect();
            mHelper.getSwipeUpDestinationAndLength(
                    mActivity.getDeviceProfile(), mActivity, targetRect);
            clipHelper.updateTargetRect(targetRect);


            ValueAnimator valueAnimator = ValueAnimator.ofFloat(0, 1);
            valueAnimator.setDuration(RECENTS_LAUNCH_DURATION).setInterpolator(FAST_OUT_SLOW_IN);
            valueAnimator.addUpdateListener((v) -> {
                clipHelper.applyTransform(targetCompats, (float) v.getAnimatedValue());
            });
            anim.play(valueAnimator);
            return anim;
        }
    }
}
