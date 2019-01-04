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

import static com.android.launcher3.Utilities.SINGLE_FRAME_MS;
import static com.android.launcher3.anim.Interpolators.ACCEL;
import static com.android.launcher3.anim.Interpolators.DEACCEL_3;
import static com.android.launcher3.anim.Interpolators.FAST_OUT_SLOW_IN;
import static com.android.launcher3.anim.Interpolators.LINEAR;
import static com.android.launcher3.config.FeatureFlags.ENABLE_TASK_STABILIZER;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.annotation.TargetApi;
import android.os.Build;
import android.util.FloatProperty;
import android.util.Log;
import android.view.HapticFeedbackConstants;
import android.view.animation.Interpolator;

import com.android.launcher3.Alarm;
import com.android.launcher3.BaseActivity;
import com.android.launcher3.OnAlarmListener;
import com.android.launcher3.Utilities;
import com.android.launcher3.userevent.nano.LauncherLogProto;
import com.android.launcher3.userevent.nano.LauncherLogProto.Action.Touch;
import com.android.quickstep.views.RecentsView;
import com.android.quickstep.views.TaskView;

/**
 * Responds to quick scrub callbacks to page through and launch recent tasks.
 *
 * The behavior is to evenly divide the progress into sections, each of which scrolls one page.
 * The first and last section set an alarm to auto-advance backwards or forwards, respectively.
 */
@TargetApi(Build.VERSION_CODES.P)
public class QuickScrubController implements OnAlarmListener {

    public static final int QUICK_SWITCH_FROM_APP_START_DURATION = 0;
    public static final int QUICK_SCRUB_FROM_APP_START_DURATION = 240;
    public static final int QUICK_SCRUB_FROM_HOME_START_DURATION = 200;
    // We want the translation y to finish faster than the rest of the animation.
    public static final float QUICK_SCRUB_TRANSLATION_Y_FACTOR = 5f / 6;
    public static final Interpolator QUICK_SCRUB_START_INTERPOLATOR = FAST_OUT_SLOW_IN;

    /**
     * Snap to a new page when crossing these thresholds. The first and last auto-advance.
     */
    private static final float[] QUICK_SCRUB_THRESHOLDS = new float[] {
            0.05f, 0.20f, 0.35f, 0.50f, 0.65f, 0.80f, 0.95f
    };

    private static final FloatProperty<QuickScrubController> PROGRESS
            = new FloatProperty<QuickScrubController>("progress") {
        @Override
        public void setValue(QuickScrubController quickScrubController, float progress) {
            quickScrubController.onQuickScrubProgress(progress);
        }

        @Override
        public Float get(QuickScrubController quickScrubController) {
            return quickScrubController.mEndProgress;
        }
    };

    private static final String TAG = "QuickScrubController";
    private static final boolean ENABLE_AUTO_ADVANCE = true;
    private static final long AUTO_ADVANCE_DELAY = 500;
    private static final int QUICKSCRUB_SNAP_DURATION_PER_PAGE = 325;
    private static final int QUICKSCRUB_END_SNAP_DURATION_PER_PAGE = 60;

    private final Alarm mAutoAdvanceAlarm;
    private final RecentsView mRecentsView;
    private final BaseActivity mActivity;

    private boolean mInQuickScrub;
    private boolean mWaitingForTaskLaunch;
    private int mQuickScrubSection;
    private boolean mStartedFromHome;
    private boolean mFinishedTransitionToQuickScrub;
    private int mLaunchingTaskId;
    private Runnable mOnFinishedTransitionToQuickScrubRunnable;
    private ActivityControlHelper mActivityControlHelper;
    private TouchInteractionLog mTouchInteractionLog;

    private boolean mIsQuickSwitch;
    private float mStartProgress;
    private float mEndProgress;
    private float mPrevProgressDelta;
    private float mPrevPrevProgressDelta;
    private boolean mShouldSwitchToNext;

    public QuickScrubController(BaseActivity activity, RecentsView recentsView) {
        mActivity = activity;
        mRecentsView = recentsView;
        if (ENABLE_AUTO_ADVANCE) {
            mAutoAdvanceAlarm = new Alarm();
            mAutoAdvanceAlarm.setOnAlarmListener(this);
        }
    }

    public void onQuickScrubStart(boolean startingFromHome, ActivityControlHelper controlHelper,
            TouchInteractionLog touchInteractionLog) {
        prepareQuickScrub(TAG);
        mInQuickScrub = true;
        mStartedFromHome = startingFromHome;
        mQuickScrubSection = 0;
        mFinishedTransitionToQuickScrub = false;
        mActivityControlHelper = controlHelper;
        mTouchInteractionLog = touchInteractionLog;

        if (mIsQuickSwitch) {
            mShouldSwitchToNext = true;
            mPrevProgressDelta = 0;
            TaskView runningTaskView = mRecentsView.getRunningTaskView();
            TaskView nextTaskView = mRecentsView.getNextTaskView();
            if (runningTaskView != null) {
                runningTaskView.setFullscreenProgress(1);
            }
            if (nextTaskView != null) {
                nextTaskView.setFullscreenProgress(1);
            }
        }

        snapToNextTaskIfAvailable();
        mActivity.getUserEventDispatcher().resetActionDurationMillis();
    }

    public void onQuickScrubEnd() {
        mInQuickScrub = false;

        Runnable launchTaskRunnable = () -> {
            int page = mRecentsView.getPageNearestToCenterOfScreen();
            TaskView taskView = mRecentsView.getTaskViewAt(page);
            if (taskView != null) {
                mWaitingForTaskLaunch = true;
                mTouchInteractionLog.launchTaskStart();
                mLaunchingTaskId = taskView.getTask().key.id;
                taskView.launchTask(true, (result) -> {
                    mTouchInteractionLog.launchTaskEnd(result);
                    if (!result) {
                        taskView.notifyTaskLaunchFailed(TAG);
                        breakOutOfQuickScrub();
                    } else {
                        mActivity.getUserEventDispatcher().logTaskLaunchOrDismiss(Touch.DRAGDROP,
                                LauncherLogProto.Action.Direction.NONE, page,
                                TaskUtils.getLaunchComponentKeyForTask(taskView.getTask().key));
                    }
                    mWaitingForTaskLaunch = false;
                    if (mIsQuickSwitch) {
                        mIsQuickSwitch = false;
                        TaskView runningTaskView = mRecentsView.getRunningTaskView();
                        TaskView nextTaskView = mRecentsView.getNextTaskView();
                        if (runningTaskView != null) {
                            runningTaskView.setFullscreenProgress(0);
                        }
                        if (nextTaskView != null) {
                            nextTaskView.setFullscreenProgress(0);
                        }
                    }

                }, taskView.getHandler());
            } else {
                breakOutOfQuickScrub();
            }
            mActivityControlHelper = null;
        };

        if (mIsQuickSwitch) {
            float progressVelocity = mPrevPrevProgressDelta / SINGLE_FRAME_MS;
            // Move to the next frame immediately, then start the animation from the
            // following frame since it starts a frame later.
            float singleFrameProgress = progressVelocity * SINGLE_FRAME_MS;
            float fromProgress = mEndProgress + singleFrameProgress;
            onQuickScrubProgress(fromProgress);
            fromProgress += singleFrameProgress;
            float toProgress = mShouldSwitchToNext ? 1 : 0;
            int duration = (int) Math.abs((toProgress - fromProgress) / progressVelocity);
            duration = Utilities.boundToRange(duration, 80, 300);
            Animator anim = ObjectAnimator.ofFloat(this, PROGRESS, fromProgress, toProgress);
            anim.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    launchTaskRunnable.run();
                }
            });
            anim.setDuration(duration).start();
            return;
        }

        if (ENABLE_AUTO_ADVANCE) {
            mAutoAdvanceAlarm.cancelAlarm();
        }
        int page = mRecentsView.getNextPage();
        int snapDuration = Math.abs(page - mRecentsView.getPageNearestToCenterOfScreen())
                * QUICKSCRUB_END_SNAP_DURATION_PER_PAGE;
        if (mRecentsView.getChildCount() > 0 && mRecentsView.snapToPage(page, snapDuration)) {
            // Settle on the page then launch it
            mRecentsView.setNextPageSwitchRunnable(launchTaskRunnable);
        } else {
            // No page move needed, just launch it
            if (mFinishedTransitionToQuickScrub) {
                launchTaskRunnable.run();
            } else {
                mOnFinishedTransitionToQuickScrubRunnable = launchTaskRunnable;
            }
        }
    }

    public void cancelActiveQuickscrub() {
        if (!mInQuickScrub) {
            return;
        }
        Log.d(TAG, "Quickscrub was active, cancelling");
        mInQuickScrub = false;
        mActivityControlHelper = null;
        mOnFinishedTransitionToQuickScrubRunnable = null;
        mRecentsView.setNextPageSwitchRunnable(null);
        mLaunchingTaskId = 0;
    }

    public boolean prepareQuickScrub(String tag) {
        return prepareQuickScrub(tag, mIsQuickSwitch);
    }

    /**
     * Initializes the UI for quick scrub, returns true if success.
     */
    public boolean prepareQuickScrub(String tag, boolean isQuickSwitch) {
        if (mWaitingForTaskLaunch || mInQuickScrub) {
            Log.d(tag, "Waiting for last scrub to finish, will skip this interaction");
            return false;
        }
        mOnFinishedTransitionToQuickScrubRunnable = null;
        mRecentsView.setNextPageSwitchRunnable(null);
        mIsQuickSwitch = isQuickSwitch;
        return true;
    }

    public boolean isQuickSwitch() {
        return mIsQuickSwitch;
    }

    public boolean isWaitingForTaskLaunch() {
        return mWaitingForTaskLaunch;
    }

    /**
     * Attempts to go to normal overview or back to home, so UI doesn't prevent user interaction.
     */
    private void breakOutOfQuickScrub() {
        if (mRecentsView.getChildCount() == 0 || mActivityControlHelper == null
                || !mActivityControlHelper.switchToRecentsIfVisible(false)) {
            mActivity.onBackPressed();
        }
    }

    public void onQuickScrubProgress(float progress) {
        if (mIsQuickSwitch) {
            TaskView currentPage = mRecentsView.getRunningTaskView();
            TaskView nextPage = mRecentsView.getNextTaskView();
            if (currentPage == null || nextPage == null) {
                return;
            }
            if (!mFinishedTransitionToQuickScrub || mStartProgress <= 0) {
                mStartProgress = mEndProgress = progress;
            } else {
                float progressDelta = progress - mEndProgress;
                mEndProgress = progress;
                progress = Utilities.boundToRange(progress, mStartProgress, 1);
                progress = Utilities.mapToRange(progress, mStartProgress, 1, 0, 1, LINEAR);
                if (mInQuickScrub) {
                    mShouldSwitchToNext = mPrevProgressDelta > 0.007f || progressDelta > 0.007f
                            || progress >= 0.5f;
                }
                mPrevPrevProgressDelta = mPrevProgressDelta;
                mPrevProgressDelta = progressDelta;
                int startScroll = mRecentsView.getScrollForPage(
                        mRecentsView.indexOfChild(currentPage));
                int scrollDiff = mRecentsView.getScrollForPage(mRecentsView.indexOfChild(nextPage))
                        - startScroll;

                int linearScrollDiff = (int) (progress * scrollDiff);
                currentPage.setZoomScale(1 - DEACCEL_3.getInterpolation(progress)
                        * TaskView.EDGE_SCALE_DOWN_FACTOR);
                if (!ENABLE_TASK_STABILIZER.get()) {
                    float accelScrollDiff = ACCEL.getInterpolation(progress) * scrollDiff;
                    currentPage.setTranslationX(linearScrollDiff + accelScrollDiff);
                }
                nextPage.setTranslationZ(1);
                nextPage.setTranslationY(currentPage.getTranslationY());
                mRecentsView.setScrollX(startScroll + linearScrollDiff);
            }
            return;
        }

        int quickScrubSection = 0;
        for (float threshold : QUICK_SCRUB_THRESHOLDS) {
            if (progress < threshold) {
                break;
            }
            quickScrubSection++;
        }
        if (quickScrubSection != mQuickScrubSection) {
            boolean cameFromAutoAdvance = mQuickScrubSection == QUICK_SCRUB_THRESHOLDS.length
                    || mQuickScrubSection == 0;
            int pageToGoTo = mRecentsView.getNextPage() + quickScrubSection - mQuickScrubSection;
            if (mFinishedTransitionToQuickScrub && !cameFromAutoAdvance) {
                goToPageWithHaptic(pageToGoTo);
            }
            if (ENABLE_AUTO_ADVANCE) {
                if (quickScrubSection == QUICK_SCRUB_THRESHOLDS.length || quickScrubSection == 0) {
                    mAutoAdvanceAlarm.setAlarm(AUTO_ADVANCE_DELAY);
                } else {
                    mAutoAdvanceAlarm.cancelAlarm();
                }
            }
            mQuickScrubSection = quickScrubSection;
        }
    }

    public void onFinishedTransitionToQuickScrub() {
        mFinishedTransitionToQuickScrub = true;
        Runnable action = mOnFinishedTransitionToQuickScrubRunnable;
        // Clear the runnable before executing it, to prevent potential recursion.
        mOnFinishedTransitionToQuickScrubRunnable = null;
        if (action != null) {
            action.run();
        }
    }

    public void onTaskRemoved(int taskId) {
        if (mLaunchingTaskId == taskId) {
            // The task has been removed mid-launch, break out of quickscrub and return the user
            // to where they were before (and notify the launch failed)
            TaskView taskView = mRecentsView.getTaskView(taskId);
            if (taskView != null) {
                taskView.notifyTaskLaunchFailed(TAG);
            }
            breakOutOfQuickScrub();
        }
    }

    public void snapToNextTaskIfAvailable() {
        if (mInQuickScrub && mRecentsView.getChildCount() > 0) {
            int duration = mIsQuickSwitch
                    ? QUICK_SWITCH_FROM_APP_START_DURATION
                    : mStartedFromHome
                        ? QUICK_SCRUB_FROM_HOME_START_DURATION
                        : QUICK_SCRUB_FROM_APP_START_DURATION;
            final int pageToGoTo;
            if (mStartedFromHome) {
                pageToGoTo = 0;
            } else if (mIsQuickSwitch) {
                TaskView tv = mRecentsView.getRunningTaskView();
                pageToGoTo = tv != null ? mRecentsView.indexOfChild(tv)
                        : mRecentsView.getNextPage();
            } else {
                pageToGoTo = mRecentsView.getNextPage() + 1;
            }
            goToPageWithHaptic(pageToGoTo, duration, true /* forceHaptic */,
                    QUICK_SCRUB_START_INTERPOLATOR);
        }
    }

    private void goToPageWithHaptic(int pageToGoTo) {
        goToPageWithHaptic(pageToGoTo, -1 /* overrideDuration */, false /* forceHaptic */, null);
    }

    private void goToPageWithHaptic(int pageToGoTo, int overrideDuration, boolean forceHaptic,
            Interpolator interpolator) {
        pageToGoTo = Utilities.boundToRange(pageToGoTo, 0, mRecentsView.getTaskViewCount() - 1);
        boolean snappingToPage = pageToGoTo != mRecentsView.getNextPage();
        if (snappingToPage) {
            int duration = overrideDuration > -1 ? overrideDuration
                    : Math.abs(pageToGoTo - mRecentsView.getNextPage())
                            * QUICKSCRUB_SNAP_DURATION_PER_PAGE;
            mRecentsView.snapToPage(pageToGoTo, duration, interpolator);
        }
        if (snappingToPage || forceHaptic) {
            mRecentsView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY,
                    HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING);
        }
    }

    @Override
    public void onAlarm(Alarm alarm) {
        int currPage = mRecentsView.getNextPage();
        boolean recentsVisible = mActivityControlHelper != null
                && mActivityControlHelper.getVisibleRecentsView() != null;
        if (!recentsVisible) {
            Log.w(TAG, "Failed to auto advance; recents not visible");
            return;
        }
        if (mQuickScrubSection == QUICK_SCRUB_THRESHOLDS.length
                && currPage < mRecentsView.getTaskViewCount() - 1) {
            goToPageWithHaptic(currPage + 1);
        } else if (mQuickScrubSection == 0 && currPage > 0) {
            goToPageWithHaptic(currPage - 1);
        }
        if (ENABLE_AUTO_ADVANCE) {
            mAutoAdvanceAlarm.setAlarm(AUTO_ADVANCE_DELAY);
        }
    }
}
