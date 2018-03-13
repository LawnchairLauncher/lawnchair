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

import android.view.HapticFeedbackConstants;

import com.android.launcher3.Alarm;
import com.android.launcher3.Launcher;
import com.android.launcher3.OnAlarmListener;
import com.android.launcher3.Utilities;
import com.android.launcher3.userevent.nano.LauncherLogProto.Action.Touch;
import com.android.launcher3.userevent.nano.LauncherLogProto.ContainerType;
import com.android.launcher3.userevent.nano.LauncherLogProto.ControlType;

/**
 * Responds to quick scrub callbacks to page through and launch recent tasks.
 *
 * The behavior is to evenly divide the progress into sections, each of which scrolls one page.
 * The first and last section set an alarm to auto-advance backwards or forwards, respectively.
 */
public class QuickScrubController implements OnAlarmListener {

    public static final int QUICK_SWITCH_START_DURATION = 210;

    private static final boolean ENABLE_AUTO_ADVANCE = true;
    private static final int NUM_QUICK_SCRUB_SECTIONS = 3;
    private static final long INITIAL_AUTO_ADVANCE_DELAY = 1000;
    private static final long AUTO_ADVANCE_DELAY = 500;
    private static final int QUICKSCRUB_SNAP_DURATION_PER_PAGE = 325;
    private static final int QUICKSCRUB_END_SNAP_DURATION_PER_PAGE = 60;

    private final Alarm mAutoAdvanceAlarm;
    private final RecentsView mRecentsView;
    private final Launcher mLauncher;

    private boolean mInQuickScrub;
    private int mQuickScrubSection;
    private boolean mStartedFromHome;
    private boolean mHasAlarmRun;
    private boolean mQuickSwitched;
    private boolean mFinishedTransitionToQuickScrub;

    public QuickScrubController(Launcher launcher, RecentsView recentsView) {
        mLauncher = launcher;
        mRecentsView = recentsView;
        if (ENABLE_AUTO_ADVANCE) {
            mAutoAdvanceAlarm = new Alarm();
            mAutoAdvanceAlarm.setOnAlarmListener(this);
        }
    }

    public void onQuickScrubStart(boolean startingFromHome) {
        mInQuickScrub = true;
        mStartedFromHome = startingFromHome;
        mQuickScrubSection = 0;
        mHasAlarmRun = false;
        mQuickSwitched = false;
        mFinishedTransitionToQuickScrub = false;

        snapToNextTaskIfAvailable();
        mLauncher.getUserEventDispatcher().resetActionDurationMillis();
    }

    public void onQuickScrubEnd() {
        mInQuickScrub = false;
        if (ENABLE_AUTO_ADVANCE) {
            mAutoAdvanceAlarm.cancelAlarm();
        }
        int page = mRecentsView.getNextPage();
        Runnable launchTaskRunnable = () -> {
            ((TaskView) mRecentsView.getPageAt(page)).launchTask(true);
        };
        int snapDuration = Math.abs(page - mRecentsView.getPageNearestToCenterOfScreen())
                * QUICKSCRUB_END_SNAP_DURATION_PER_PAGE;
        if (mRecentsView.snapToPage(page, snapDuration)) {
            // Settle on the page then launch it
            mRecentsView.setNextPageSwitchRunnable(launchTaskRunnable);
        } else {
            // No page move needed, just launch it
            launchTaskRunnable.run();
        }
        mLauncher.getUserEventDispatcher().logActionOnControl(Touch.DRAGDROP,
                ControlType.QUICK_SCRUB_BUTTON, null, mStartedFromHome ?
                        ContainerType.WORKSPACE : ContainerType.APP);
    }

    public void onQuickScrubProgress(float progress) {
        int quickScrubSection = Math.round(progress * NUM_QUICK_SCRUB_SECTIONS);
        if (quickScrubSection != mQuickScrubSection) {
            int pageToGoTo = mRecentsView.getNextPage() + quickScrubSection - mQuickScrubSection;
            if (mFinishedTransitionToQuickScrub) {
                goToPageWithHaptic(pageToGoTo);
            }
            if (ENABLE_AUTO_ADVANCE) {
                if (quickScrubSection == NUM_QUICK_SCRUB_SECTIONS || quickScrubSection == 0) {
                    mAutoAdvanceAlarm.setAlarm(mHasAlarmRun
                            ? AUTO_ADVANCE_DELAY : INITIAL_AUTO_ADVANCE_DELAY);
                } else {
                    mAutoAdvanceAlarm.cancelAlarm();
                }
            }
            mQuickScrubSection = quickScrubSection;
        }
    }

    public void onQuickSwitch() {
        mQuickSwitched = true;
        quickSwitchIfReady();
    }

    public void onFinishedTransitionToQuickScrub() {
        mFinishedTransitionToQuickScrub = true;
        quickSwitchIfReady();
    }

    /**
     * Immediately launches the current task (which we snapped to in onQuickScrubStart) if we've
     * gotten the onQuickSwitch callback and the transition to quick scrub has completed.
     */
    private void quickSwitchIfReady() {
        if (mQuickSwitched && mFinishedTransitionToQuickScrub) {
            onQuickScrubEnd();
            mLauncher.getUserEventDispatcher().logActionOnControl(Touch.FLING,
                    ControlType.QUICK_SCRUB_BUTTON, null, mStartedFromHome ?
                            ContainerType.WORKSPACE : ContainerType.APP);
        }
    }

    public void snapToNextTaskIfAvailable() {
        if (mInQuickScrub && mRecentsView.getChildCount() > 0) {
            int toPage = mStartedFromHome ? 0 : mRecentsView.getNextPage() + 1;
            goToPageWithHaptic(toPage, QUICK_SWITCH_START_DURATION);
        }
    }

    private void goToPageWithHaptic(int pageToGoTo) {
        goToPageWithHaptic(pageToGoTo, -1);
    }

    private void goToPageWithHaptic(int pageToGoTo, int overrideDuration) {
        pageToGoTo = Utilities.boundToRange(pageToGoTo, 0, mRecentsView.getPageCount() - 1);
        if (pageToGoTo != mRecentsView.getNextPage()) {
            int duration = overrideDuration > -1 ? overrideDuration
                    : Math.abs(pageToGoTo - mRecentsView.getNextPage())
                            * QUICKSCRUB_SNAP_DURATION_PER_PAGE;
            mRecentsView.snapToPage(pageToGoTo, duration);
            mRecentsView.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP,
                    HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING);
        }
    }

    @Override
    public void onAlarm(Alarm alarm) {
        int currPage = mRecentsView.getNextPage();
        if (mQuickScrubSection == NUM_QUICK_SCRUB_SECTIONS
                && currPage < mRecentsView.getPageCount() - 1) {
            goToPageWithHaptic(currPage + 1);
        } else if (mQuickScrubSection == 0 && currPage > 0) {
            goToPageWithHaptic(currPage - 1);
        }
        mHasAlarmRun = true;
        if (ENABLE_AUTO_ADVANCE) {
            mAutoAdvanceAlarm.setAlarm(AUTO_ADVANCE_DELAY);
        }
    }
}
