/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.launcher3.allapps;

import static android.view.HapticFeedbackConstants.CLOCK_TICK;

import androidx.recyclerview.widget.LinearSmoothScroller;
import androidx.recyclerview.widget.RecyclerView.ViewHolder;

import com.android.launcher3.allapps.AlphabeticalAppsList.FastScrollSectionInfo;

public class AllAppsFastScrollHelper {

    private static final int NO_POSITION = -1;

    private int mTargetFastScrollPosition = NO_POSITION;

    private AllAppsRecyclerView mRv;
    private ViewHolder mLastSelectedViewHolder;

    public AllAppsFastScrollHelper(AllAppsRecyclerView rv) {
        mRv = rv;
    }

    /**
     * Smooth scrolls the recycler view to the given section.
     */
    public void smoothScrollToSection(FastScrollSectionInfo info) {
        if (mTargetFastScrollPosition == info.position) {
            return;
        }
        mTargetFastScrollPosition = info.position;
        mRv.getLayoutManager().startSmoothScroll(new MyScroller(mTargetFastScrollPosition));
    }

    public void onFastScrollCompleted() {
        mTargetFastScrollPosition = NO_POSITION;
        setLastHolderSelected(false);
        mLastSelectedViewHolder = null;
    }


    private void setLastHolderSelected(boolean isSelected) {
        if (mLastSelectedViewHolder != null) {
            mLastSelectedViewHolder.itemView.setActivated(isSelected);
            mLastSelectedViewHolder.setIsRecyclable(!isSelected);
        }
    }

    private class MyScroller extends LinearSmoothScroller {

        private final int mTargetPosition;

        public MyScroller(int targetPosition) {
            super(mRv.getContext());

            mTargetPosition = targetPosition;
            setTargetPosition(targetPosition);
        }

        @Override
        protected int getVerticalSnapPreference() {
            mRv.performHapticFeedback(CLOCK_TICK);
            return SNAP_TO_ANY;
        }

        @Override
        protected void onStop() {
            super.onStop();
            if (mTargetPosition != mTargetFastScrollPosition) {
                // Target changed, before the last scroll can finish
                return;
            }

            ViewHolder currentHolder = mRv.findViewHolderForAdapterPosition(mTargetPosition);
            if (currentHolder == mLastSelectedViewHolder) {
                return;
            }

            setLastHolderSelected(false);
            mLastSelectedViewHolder = currentHolder;
            setLastHolderSelected(true);
        }

        @Override
        protected void onStart() {
            super.onStart();
            if (mTargetPosition != mTargetFastScrollPosition) {
                setLastHolderSelected(false);
                mLastSelectedViewHolder = null;
            }
        }
    }
}
