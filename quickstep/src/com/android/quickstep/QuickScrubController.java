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
    public static final int QUICK_SCRUB_FROM_HOME_START_DURATION = 150;
    // We want the translation y to finish faster than the rest of the animation.
    public static final float QUICK_SCRUB_TRANSLATION_Y_FACTOR = 5f / 6;
    public static final Interpolator QUICK_SCRUB_START_INTERPOLATOR = FAST_OUT_SLOW_IN;

    /**
     * Snap to a new page when crossing these thresholds. The first and last auto-advance.
     */
    private static final float[] QUICK_SCRUB_THRESHOLDS = new float[] {
            0.04f, 0.27f, 0.50f, 0.73f, 0.96f
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
        mInQuickScrub = true;
        mStartedFromHome = startingFromHome;
        mQuickScrubSection = 0;
        mFinishedTransitionToQuickScrub = false;
        mOnFinishedTransitionToQuickScrubRunnable = null;
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
            TaskView taskView = mRecentsView.getPageAt(page);
            if (taskView != null) {
                taskView.launchTask(true, (result) -> {
                    if (!result) {
                        taskView.notifyTaskLaunchFailed(TAG);
                        breakOutOfQuickScrub();
                    }
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
        mActivity.getUserEventDispatcher().logTaskLaunchOrDismiss(Touch.DRAGDROP,
                LauncherLogProto.Action.Direction.NONE, page,
                TaskUtils.getComponentKeyForTask(mRecentsView.getPageAt(page).getTask().key));
    }

    /**
     * Attempts to go to normal overview or back to home, so UI doesn't prevent user interaction.
     */
    private void breakOutOfQuickScrub() {
        if (mRecentsView.getChildCount() == 0 || mActivityControlHelper == null
                || !mActivityControlHelper.switchToRecentsIfVisible()) {
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
        if (mOnFinishedTransitionToQuickScrubRunnable != null) {
            mOnFinishedTransitionToQuickScrubRunnable.run();
            mOnFinishedTransitionToQuickScrubRunnable = null;
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
        pageToGoTo = Utilities.boundToRange(pageToGoTo, 0, mRecentsView.getPageCount() - 1);
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
                && currPage < mRecentsView.getPageCount() - 1) {
            goToPageWithHaptic(currPage + 1);
        } else if (mQuickScrubSection == 0 && currPage > 0) {
            goToPageWithHaptic(currPage - 1);
        }
        if (ENABLE_AUTO_ADVANCE) {
            mAutoAdvanceAlarm.setAlarm(AUTO_ADVANCE_DELAY);
        }
    }
}
