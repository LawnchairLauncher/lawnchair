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

import com.android.launcher3.util.Thunk;

import java.util.HashSet;
import java.util.List;

import androidx.recyclerview.widget.RecyclerView;

public class AllAppsFastScrollHelper implements AllAppsGridAdapter.BindViewCallback {

    private static final int INITIAL_TOUCH_SETTLING_DURATION = 100;
    private static final int REPEAT_TOUCH_SETTLING_DURATION = 200;

    private AllAppsRecyclerView mRv;
    private AlphabeticalAppsList mApps;

    // Keeps track of the current and targeted fast scroll section (the section to scroll to after
    // the initial delay)
    int mTargetFastScrollPosition = -1;
    @Thunk String mCurrentFastScrollSection;
    @Thunk String mTargetFastScrollSection;

    // The settled states affect the delay before the fast scroll animation is applied
    private boolean mHasFastScrollTouchSettled;
    private boolean mHasFastScrollTouchSettledAtLeastOnce;

    // Set of all views animated during fast scroll.  We keep track of these ourselves since there
    // is no way to reset a view once it gets scrapped or recycled without other hacks
    private HashSet<RecyclerView.ViewHolder> mTrackedFastScrollViews = new HashSet<>();

    // Smooth fast-scroll animation frames
    @Thunk int mFastScrollFrameIndex;
    @Thunk final int[] mFastScrollFrames = new int[10];

    /**
     * This runnable runs a single frame of the smooth scroll animation and posts the next frame
     * if necessary.
     */
    @Thunk Runnable mSmoothSnapNextFrameRunnable = new Runnable() {
        @Override
        public void run() {
            if (mFastScrollFrameIndex < mFastScrollFrames.length) {
                mRv.scrollBy(0, mFastScrollFrames[mFastScrollFrameIndex]);
                mFastScrollFrameIndex++;
                mRv.postOnAnimation(mSmoothSnapNextFrameRunnable);
            }
        }
    };

    /**
     * This runnable updates the current fast scroll section to the target fastscroll section.
     */
    Runnable mFastScrollToTargetSectionRunnable = new Runnable() {
        @Override
        public void run() {
            // Update to the target section
            mCurrentFastScrollSection = mTargetFastScrollSection;
            mHasFastScrollTouchSettled = true;
            mHasFastScrollTouchSettledAtLeastOnce = true;
            updateTrackedViewsFastScrollFocusState();
        }
    };

    public AllAppsFastScrollHelper(AllAppsRecyclerView rv, AlphabeticalAppsList apps) {
        mRv = rv;
        mApps = apps;
    }

    public void onSetAdapter(AllAppsGridAdapter adapter) {
        adapter.setBindViewCallback(this);
    }

    /**
     * Smooth scrolls the recycler view to the given section.
     *
     * @return whether the fastscroller can scroll to the new section.
     */
    public boolean smoothScrollToSection(int scrollY, int availableScrollHeight,
            AlphabeticalAppsList.FastScrollSectionInfo info) {
        if (mTargetFastScrollPosition != info.fastScrollToItem.position) {
            mTargetFastScrollPosition = info.fastScrollToItem.position;
            smoothSnapToPosition(scrollY, availableScrollHeight, info);
            return true;
        }
        return false;
    }

    /**
     * Smoothly snaps to a given position.  We do this manually by calculating the keyframes
     * ourselves and animating the scroll on the recycler view.
     */
    private void smoothSnapToPosition(int scrollY, int availableScrollHeight,
            AlphabeticalAppsList.FastScrollSectionInfo info) {
        mRv.removeCallbacks(mSmoothSnapNextFrameRunnable);
        mRv.removeCallbacks(mFastScrollToTargetSectionRunnable);

        trackAllChildViews();
        if (mHasFastScrollTouchSettled) {
            // In this case, the user has already settled once (and the fast scroll state has
            // animated) and they are just fine-tuning their section from the last section, so
            // we should make it feel fast and update immediately.
            mCurrentFastScrollSection = info.sectionName;
            mTargetFastScrollSection = null;
            updateTrackedViewsFastScrollFocusState();
        } else {
            // Otherwise, the user has scrubbed really far, and we don't want to distract the user
            // with the flashing fast scroll state change animation in addition to the fast scroll
            // section popup, so reset the views to normal, and wait for the touch to settle again
            // before animating the fast scroll state.
            mCurrentFastScrollSection = null;
            mTargetFastScrollSection = info.sectionName;
            mHasFastScrollTouchSettled = false;
            updateTrackedViewsFastScrollFocusState();

            // Delay scrolling to a new section until after some duration.  If the user has been
            // scrubbing a while and makes multiple big jumps, then reduce the time needed for the
            // fast scroll to settle so it doesn't feel so long.
            mRv.postDelayed(mFastScrollToTargetSectionRunnable,
                    mHasFastScrollTouchSettledAtLeastOnce ?
                            REPEAT_TOUCH_SETTLING_DURATION :
                            INITIAL_TOUCH_SETTLING_DURATION);
        }

        // Calculate the full animation from the current scroll position to the final scroll
        // position, and then run the animation for the duration.  If we are scrolling to the
        // first fast scroll section, then just scroll to the top of the list itself.
        List<AlphabeticalAppsList.FastScrollSectionInfo> fastScrollSections =
                mApps.getFastScrollerSections();
        int newPosition = info.fastScrollToItem.position;
        int newScrollY = fastScrollSections.size() > 0 && fastScrollSections.get(0) == info
                        ? 0
                        : Math.min(availableScrollHeight, mRv.getCurrentScrollY(newPosition, 0));
        int numFrames = mFastScrollFrames.length;
        int deltaY = newScrollY - scrollY;
        float ySign = Math.signum(deltaY);
        int step = (int) (ySign * Math.ceil((float) Math.abs(deltaY) / numFrames));
        for (int i = 0; i < numFrames; i++) {
            // TODO(winsonc): We can interpolate this as well.
            mFastScrollFrames[i] = (int) (ySign * Math.min(Math.abs(step), Math.abs(deltaY)));
            deltaY -= step;
        }
        mFastScrollFrameIndex = 0;
        mRv.postOnAnimation(mSmoothSnapNextFrameRunnable);
    }

    public void onFastScrollCompleted() {
        // TODO(winsonc): Handle the case when the user scrolls and releases before the animation
        //                runs

        // Stop animating the fast scroll position and state
        mRv.removeCallbacks(mSmoothSnapNextFrameRunnable);
        mRv.removeCallbacks(mFastScrollToTargetSectionRunnable);

        // Reset the tracking variables
        mHasFastScrollTouchSettled = false;
        mHasFastScrollTouchSettledAtLeastOnce = false;
        mCurrentFastScrollSection = null;
        mTargetFastScrollSection = null;
        mTargetFastScrollPosition = -1;

        updateTrackedViewsFastScrollFocusState();
        mTrackedFastScrollViews.clear();
    }

    @Override
    public void onBindView(AllAppsGridAdapter.ViewHolder holder) {
        // Update newly bound views to the current fast scroll state if we are fast scrolling
        if (mCurrentFastScrollSection != null || mTargetFastScrollSection != null) {
            mTrackedFastScrollViews.add(holder);
        }
    }

    /**
     * Starts tracking all the recycler view's children which are FastScrollFocusableViews.
     */
    private void trackAllChildViews() {
        int childCount = mRv.getChildCount();
        for (int i = 0; i < childCount; i++) {
            RecyclerView.ViewHolder viewHolder = mRv.getChildViewHolder(mRv.getChildAt(i));
            if (viewHolder != null) {
                mTrackedFastScrollViews.add(viewHolder);
            }
        }
    }

    /**
     * Updates the fast scroll focus on all the children.
     */
    private void updateTrackedViewsFastScrollFocusState() {
        for (RecyclerView.ViewHolder viewHolder : mTrackedFastScrollViews) {
            int pos = viewHolder.getAdapterPosition();
            boolean isActive = false;
            if (mCurrentFastScrollSection != null
                    && pos > RecyclerView.NO_POSITION
                    && pos < mApps.getAdapterItems().size()) {
                AlphabeticalAppsList.AdapterItem item = mApps.getAdapterItems().get(pos);
                isActive = item != null &&
                        mCurrentFastScrollSection.equals(item.sectionName) &&
                        item.position == mTargetFastScrollPosition;
            }
            viewHolder.itemView.setActivated(isActive);
        }
    }
}
