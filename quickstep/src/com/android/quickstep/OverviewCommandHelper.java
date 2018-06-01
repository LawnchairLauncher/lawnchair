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

import static android.content.Intent.ACTION_PACKAGE_ADDED;
import static android.content.Intent.ACTION_PACKAGE_CHANGED;
import static android.content.Intent.ACTION_PACKAGE_REMOVED;

import static com.android.launcher3.anim.Interpolators.FAST_OUT_SLOW_IN;
import static com.android.launcher3.anim.Interpolators.TOUCH_RESPONSE_INTERPOLATOR;
import static com.android.quickstep.TouchConsumer.INTERACTION_NORMAL;
import static com.android.systemui.shared.system.ActivityManagerWrapper
        .CLOSE_SYSTEM_WINDOWS_REASON_RECENTS;
import static com.android.systemui.shared.system.PackageManagerWrapper
        .ACTION_PREFERRED_ACTIVITY_CHANGED;
import static com.android.systemui.shared.system.RemoteAnimationTargetCompat.MODE_CLOSING;
import static com.android.systemui.shared.system.RemoteAnimationTargetCompat.MODE_OPENING;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ValueAnimator;
import android.annotation.TargetApi;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ResolveInfo;
import android.graphics.Rect;
import android.os.Build;
import android.os.PatternMatcher;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.view.ViewConfiguration;

import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.BaseDraggingActivity;
import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.MainThreadExecutor;
import com.android.launcher3.anim.AnimationSuccessListener;
import com.android.launcher3.logging.UserEventDispatcher;
import com.android.launcher3.userevent.nano.LauncherLogProto.Action;
import com.android.launcher3.userevent.nano.LauncherLogProto.ContainerType;
import com.android.quickstep.ActivityControlHelper.ActivityInitListener;
import com.android.quickstep.ActivityControlHelper.AnimationFactory;
import com.android.quickstep.ActivityControlHelper.FallbackActivityControllerHelper;
import com.android.quickstep.ActivityControlHelper.LauncherActivityControllerHelper;
import com.android.quickstep.util.ClipAnimationHelper;
import com.android.quickstep.util.TransformedRect;
import com.android.quickstep.util.RemoteAnimationTargetSet;
import com.android.quickstep.views.RecentsView;
import com.android.systemui.shared.system.ActivityManagerWrapper;
import com.android.systemui.shared.system.LatencyTrackerCompat;
import com.android.systemui.shared.system.PackageManagerWrapper;
import com.android.systemui.shared.system.RemoteAnimationTargetCompat;
import com.android.systemui.shared.system.SyncRtSurfaceTransactionApplier;
import com.android.systemui.shared.system.TransactionCompat;

import java.util.ArrayList;

/**
 * Helper class to handle various atomic commands for switching between Overview.
 */
@TargetApi(Build.VERSION_CODES.P)
public class OverviewCommandHelper {

    private static final long RECENTS_LAUNCH_DURATION = 250;

    private static final String TAG = "OverviewCommandHelper";

    private final Context mContext;
    private final ActivityManagerWrapper mAM;
    private final RecentsModel mRecentsModel;
    private final MainThreadExecutor mMainThreadExecutor;
    private final ComponentName mMyHomeComponent;

    private final BroadcastReceiver mUserPreferenceChangeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            initOverviewTargets();
        }
    };
    private final BroadcastReceiver mOtherHomeAppUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            initOverviewTargets();
        }
    };
    private String mUpdateRegisteredPackage;

    public Intent overviewIntent;
    public ComponentName overviewComponent;
    private ActivityControlHelper mActivityControlHelper;

    private long mLastToggleTime;

    public OverviewCommandHelper(Context context) {
        mContext = context;
        mAM = ActivityManagerWrapper.getInstance();
        mMainThreadExecutor = new MainThreadExecutor();
        mRecentsModel = RecentsModel.getInstance(mContext);

        Intent myHomeIntent = new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .setPackage(mContext.getPackageName());
        ResolveInfo info = context.getPackageManager().resolveActivity(myHomeIntent, 0);
        mMyHomeComponent = new ComponentName(context.getPackageName(), info.activityInfo.name);

        mContext.registerReceiver(mUserPreferenceChangeReceiver,
                new IntentFilter(ACTION_PREFERRED_ACTIVITY_CHANGED));
        initOverviewTargets();
    }

    private void initOverviewTargets() {
        ComponentName defaultHome = PackageManagerWrapper.getInstance()
                .getHomeActivities(new ArrayList<>());

        final String overviewIntentCategory;
        if (defaultHome == null || mMyHomeComponent.equals(defaultHome)) {
            // User default home is same as out home app. Use Overview integrated in Launcher.
            overviewComponent = mMyHomeComponent;
            mActivityControlHelper = new LauncherActivityControllerHelper();
            overviewIntentCategory = Intent.CATEGORY_HOME;

            if (mUpdateRegisteredPackage != null) {
                // Remove any update listener as we don't care about other packages.
                mContext.unregisterReceiver(mOtherHomeAppUpdateReceiver);
                mUpdateRegisteredPackage = null;
            }
        } else {
            // The default home app is a different launcher. Use the fallback Overview instead.
            overviewComponent = new ComponentName(mContext, RecentsActivity.class);
            mActivityControlHelper = new FallbackActivityControllerHelper(defaultHome);
            overviewIntentCategory = Intent.CATEGORY_DEFAULT;

            // User's default home app can change as a result of package updates of this app (such
            // as uninstalling the app or removing the "Launcher" feature in an update).
            // Listen for package updates of this app (and remove any previously attached
            // package listener).
            if (!defaultHome.getPackageName().equals(mUpdateRegisteredPackage)) {
                if (mUpdateRegisteredPackage != null) {
                    mContext.unregisterReceiver(mOtherHomeAppUpdateReceiver);
                }

                mUpdateRegisteredPackage = defaultHome.getPackageName();
                IntentFilter updateReceiver = new IntentFilter(ACTION_PACKAGE_ADDED);
                updateReceiver.addAction(ACTION_PACKAGE_CHANGED);
                updateReceiver.addAction(ACTION_PACKAGE_REMOVED);
                updateReceiver.addDataScheme("package");
                updateReceiver.addDataSchemeSpecificPart(mUpdateRegisteredPackage,
                        PatternMatcher.PATTERN_LITERAL);
                mContext.registerReceiver(mOtherHomeAppUpdateReceiver, updateReceiver);
            }
        }

        overviewIntent = new Intent(Intent.ACTION_MAIN)
                .addCategory(overviewIntentCategory)
                .setComponent(overviewComponent)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }

    public void onDestroy() {
        mContext.unregisterReceiver(mUserPreferenceChangeReceiver);

        if (mUpdateRegisteredPackage != null) {
            mContext.unregisterReceiver(mOtherHomeAppUpdateReceiver);
            mUpdateRegisteredPackage = null;
        }
    }

    public void onOverviewToggle() {
        // If currently screen pinning, do not enter overview
        if (mAM.isScreenPinningActive()) {
            return;
        }

        mAM.closeSystemWindows(CLOSE_SYSTEM_WINDOWS_REASON_RECENTS);
        mMainThreadExecutor.execute(new RecentsActivityCommand<>());
    }

    public void onOverviewShown() {
        mMainThreadExecutor.execute(new ShowRecentsCommand());
    }

    public void onTip(int actionType, int viewType) {
        mMainThreadExecutor.execute(new Runnable() {
            @Override
            public void run() {
                UserEventDispatcher.newInstance(mContext,
                        new InvariantDeviceProfile(mContext).getDeviceProfile(mContext))
                        .logActionTip(actionType, viewType);
            }
        });
    }

    public ActivityControlHelper getActivityControlHelper() {
        return mActivityControlHelper;
    }

    private class ShowRecentsCommand extends RecentsActivityCommand {

        @Override
        protected boolean handleCommand(long elapsedTime) {
            return mHelper.getVisibleRecentsView() != null;
        }
    }

    private class RecentsActivityCommand<T extends BaseDraggingActivity> implements Runnable {

        protected final ActivityControlHelper<T> mHelper;
        private final long mCreateTime;
        private final int mRunningTaskId;

        private ActivityInitListener mListener;
        private T mActivity;
        private RecentsView mRecentsView;
        private final long mToggleClickedTime = SystemClock.uptimeMillis();
        private boolean mUserEventLogged;

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
                if (!mHelper.switchToRecentsIfVisible(true)) {
                    mListener = mHelper.createActivityInitListener(this::onActivityReady);
                    mListener.registerAndStartActivity(overviewIntent, this::createWindowAnimation,
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
            AnimationFactory factory = mHelper.prepareRecentsUI(activity, wasVisible,
                    (controller) -> {
                        controller.dispatchOnStart();
                        ValueAnimator anim = controller.getAnimationPlayer()
                                .setDuration(RECENTS_LAUNCH_DURATION);
                        anim.setInterpolator(FAST_OUT_SLOW_IN);
                        anim.start();
                });
            factory.onRemoteAnimationReceived(null);
            if (wasVisible) {
                factory.createActivityController(RECENTS_LAUNCH_DURATION, INTERACTION_NORMAL);
            }
            mActivity = activity;
            mRecentsView = mActivity.getOverviewPanel();
            mRecentsView.setRunningTaskIconScaledDown(true /* isScaledDown */, false /* animate */);
            if (!mUserEventLogged) {
                activity.getUserEventDispatcher().logActionCommand(Action.Command.RECENTS_BUTTON,
                        mHelper.getContainerType(), ContainerType.TASKSWITCHER);
                mUserEventLogged = true;
            }
            return false;
        }

        private AnimatorSet createWindowAnimation(RemoteAnimationTargetCompat[] targetCompats) {
            if (LatencyTrackerCompat.isEnabled(mContext)) {
                LatencyTrackerCompat.logToggleRecents(
                        (int) (SystemClock.uptimeMillis() - mToggleClickedTime));
            }

            if (mListener != null) {
                mListener.unregister();
            }
            AnimatorSet anim = new AnimatorSet();
            anim.addListener(new AnimationSuccessListener() {
                @Override
                public void onAnimationSuccess(Animator animator) {
                    if (mRecentsView != null) {
                        mRecentsView.setRunningTaskIconScaledDown(false /* isScaledDown */,
                                true /* animate */);
                    }
                }
            });
            if (mActivity == null) {
                Log.e(TAG, "Animation created, before activity");
                anim.play(ValueAnimator.ofInt(0, 1).setDuration(100));
                return anim;
            }

            RemoteAnimationTargetSet targetSet =
                    new RemoteAnimationTargetSet(targetCompats, MODE_CLOSING);

            // Use the top closing app to determine the insets for the animation
            RemoteAnimationTargetCompat runningTaskTarget = targetSet.findTask(mRunningTaskId);
            if (runningTaskTarget == null) {
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
            clipHelper.updateSource(homeBounds, runningTaskTarget);

            TransformedRect targetRect = new TransformedRect();
            mHelper.getSwipeUpDestinationAndLength(mActivity.getDeviceProfile(), mActivity,
                    INTERACTION_NORMAL, targetRect);
            clipHelper.updateTargetRect(targetRect);
            clipHelper.prepareAnimation(false /* isOpening */);

            SyncRtSurfaceTransactionApplier syncTransactionApplier =
                    new SyncRtSurfaceTransactionApplier(rootView);
            ValueAnimator valueAnimator = ValueAnimator.ofFloat(0, 1);
            valueAnimator.setDuration(RECENTS_LAUNCH_DURATION);
            valueAnimator.setInterpolator(TOUCH_RESPONSE_INTERPOLATOR);
            valueAnimator.addUpdateListener((v) ->
                    clipHelper.applyTransform(targetSet, (float) v.getAnimatedValue(),
                            syncTransactionApplier));

            if (targetSet.isAnimatingHome()) {
                // If we are animating home, fade in the opening targets
                RemoteAnimationTargetSet openingSet =
                        new RemoteAnimationTargetSet(targetCompats, MODE_OPENING);

                TransactionCompat transaction = new TransactionCompat();
                valueAnimator.addUpdateListener((v) -> {
                    for (RemoteAnimationTargetCompat app : openingSet.apps) {
                        transaction.setAlpha(app.leash, (float) v.getAnimatedValue());
                    }
                    transaction.apply();
                });
            }
            anim.play(valueAnimator);
            return anim;
        }
    }
}
