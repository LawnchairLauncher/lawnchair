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
public class QuickScrubController implements OnAlarmListener {

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
    private Runnable mOnFinishedTransitionToQuickScrubRunnable;
    private ActivityControlHelper mActivityControlHelper;

    public QuickScrubController(BaseActivity activity, RecentsView recentsView) {
        mActivity = activity;
        mRecentsView = recentsView;
        if (ENABLE_AUTO_ADVANCE) {
            mAutoAdvanceAlarm = new Alarm();
            mAutoAdvanceAlarm.setOnAlarmListener(this);
        }
    }

    public void onQuickScrubStart(boolean startingFromHome, ActivityControlHelper controlHelper) {
        prepareQuickScrub(TAG);
        mInQuickScrub = true;
        mStartedFromHome = startingFromHome;
        mQuickScrubSection = 0;
        mFinishedTransitionToQuickScrub = false;
        mActivityControlHelper = controlHelper;

        snapToNextTaskIfAvailable();
        mActivity.getUserEventDispatcher().resetActionDurationMillis();
    }

    public void onQuickScrubEnd() {
        mInQuickScrub = false;
        if (ENABLE_AUTO_ADVANCE) {
            mAutoAdvanceAlarm.cancelAlarm();
        }
        int page = mRecentsView.getNextPage();
        Runnable launchTaskRunnable = () -> {
            TaskView taskView = mRecentsView.getTaskViewAt(page);
            if (taskView != null) {
                mWaitingForTaskLaunch = true;
                taskView.launchTask(true, (result) -> {
                    if (!result) {
                        taskView.notifyTaskLaunchFailed(TAG);
                        breakOutOfQuickScrub();
                    } else {
                        mActivity.getUserEventDispatcher().logTaskLaunchOrDismiss(Touch.DRAGDROP,
                                LauncherLogProto.Action.Direction.NONE, page,
                                TaskUtils.getLaunchComponentKeyForTask(taskView.getTask().key));
                    }
                    mWaitingForTaskLaunch = false;
                }, taskView.getHandler());
            } else {
                breakOutOfQuickScrub();
            }
            mActivityControlHelper = null;
        };
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
    }

    /**
     * Initializes the UI for quick scrub, returns true if success.
     */
    public boolean prepareQuickScrub(String tag) {
        if (mWaitingForTaskLaunch || mInQuickScrub) {
            Log.d(tag, "Waiting for last scrub to finish, will skip this interaction");
            return false;
        }
        mOnFinishedTransitionToQuickScrubRunnable = null;
        mRecentsView.setNextPageSwitchRunnable(null);
        return true;
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

    public void snapToNextTaskIfAvailable() {
        if (mInQuickScrub && mRecentsView.getChildCount() > 0) {
            int duration = mStartedFromHome ? QUICK_SCRUB_FROM_HOME_START_DURATION
                    : QUICK_SCRUB_FROM_APP_START_DURATION;
            int pageToGoTo = mStartedFromHome ? 0 : mRecentsView.getNextPage() + 1;
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
