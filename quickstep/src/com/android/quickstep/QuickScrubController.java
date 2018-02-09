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

/**
 * Responds to quick scrub callbacks to page through and launch recent tasks.
 *
 * The behavior is to evenly divide the progress into sections, each of which scrolls one page.
 * The first and last section set an alarm to auto-advance backwards or forwards, respectively.
 */
public class QuickScrubController implements OnAlarmListener {

    private static final int NUM_QUICK_SCRUB_SECTIONS = 5;
    private static final long AUTO_ADVANCE_DELAY = 500;
    private static final int QUICKSCRUB_SNAP_DURATION_PER_PAGE = 325;
    private static final int QUICKSCRUB_END_SNAP_DURATION_PER_PAGE = 60;

    private Launcher mLauncher;
    private Alarm mAutoAdvanceAlarm;
    private RecentsView mRecentsView;

    private int mQuickScrubSection;
    private int mStartPage;

    public QuickScrubController(Launcher launcher) {
        mLauncher = launcher;
        mAutoAdvanceAlarm = new Alarm();
        mAutoAdvanceAlarm.setOnAlarmListener(this);
    }

    public void onQuickScrubStart(boolean startingFromHome) {
        mRecentsView = mLauncher.getOverviewPanel();
        mStartPage = startingFromHome ? 0 : mRecentsView.getFirstTaskIndex();
        mQuickScrubSection = 0;
    }

    public void onQuickScrubEnd() {
        mAutoAdvanceAlarm.cancelAlarm();
        if (mRecentsView == null) {
        } else {
            int page = mRecentsView.getNextPage();
            Runnable launchTaskRunnable = () -> {
                if (page < mRecentsView.getFirstTaskIndex()) {
                    mRecentsView.getPageAt(page).performClick();
                } else {
                    ((TaskView) mRecentsView.getPageAt(page)).launchTask(true);
                }
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
        }
    }

    public void onQuickScrubProgress(float progress) {
        int quickScrubSection = Math.round(progress * NUM_QUICK_SCRUB_SECTIONS);
        if (quickScrubSection != mQuickScrubSection) {
            int pageToGoTo = mRecentsView.getNextPage() + quickScrubSection - mQuickScrubSection;
            goToPageWithHaptic(pageToGoTo);
            if (quickScrubSection == NUM_QUICK_SCRUB_SECTIONS || quickScrubSection == 0) {
                mAutoAdvanceAlarm.setAlarm(AUTO_ADVANCE_DELAY);
            } else {
                mAutoAdvanceAlarm.cancelAlarm();
            }
            mQuickScrubSection = quickScrubSection;
        }
    }

    public void snapToPageForCurrentQuickScrubSection() {
        goToPageWithHaptic(mRecentsView.getCurrentPage() + mQuickScrubSection);
    }

    private void goToPageWithHaptic(int pageToGoTo) {
        if (pageToGoTo != mRecentsView.getNextPage()) {
            int duration = Math.abs(pageToGoTo - mRecentsView.getNextPage())
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
        } else if (mQuickScrubSection == 0 && currPage > mStartPage) {
            goToPageWithHaptic(currPage - 1);
        }
        mAutoAdvanceAlarm.setAlarm(AUTO_ADVANCE_DELAY);
    }
}
