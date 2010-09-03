/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.launcher2;

import android.content.Context;
import android.util.AttributeSet;
import android.view.animation.Interpolator;
import android.widget.Scroller;


public abstract class SmoothPagedView extends PagedView {
    private static final float SMOOTHING_SPEED = 0.75f;
    private static final float SMOOTHING_CONSTANT = (float) (0.016 / Math.log(SMOOTHING_SPEED));


    private static final float BASELINE_FLING_VELOCITY = 2500.f;
    private static final float FLING_VELOCITY_INFLUENCE = 0.4f;

    private WorkspaceOvershootInterpolator mScrollInterpolator;

    private static class WorkspaceOvershootInterpolator implements Interpolator {
        private static final float DEFAULT_TENSION = 1.3f;
        private float mTension;

        public WorkspaceOvershootInterpolator() {
            mTension = DEFAULT_TENSION;
        }

        public void setDistance(int distance) {
            mTension = distance > 0 ? DEFAULT_TENSION / distance : DEFAULT_TENSION;
        }

        public void disableSettle() {
            mTension = 0.f;
        }

        public float getInterpolation(float t) {
            // _o(t) = t * t * ((tension + 1) * t + tension)
            // o(t) = _o(t - 1) + 1
            t -= 1.0f;
            return t * t * ((mTension + 1) * t + mTension) + 1.0f;
        }
    }

    /**
     * Used to inflate the Workspace from XML.
     *
     * @param context The application's context.
     * @param attrs The attributes set containing the Workspace's customization values.
     */
    public SmoothPagedView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    /**
     * Used to inflate the Workspace from XML.
     *
     * @param context The application's context.
     * @param attrs The attributes set containing the Workspace's customization values.
     * @param defStyle Unused.
     */
    public SmoothPagedView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        mUsePagingTouchSlop = false;

        // This means that we'll take care of updating the scroll parameter ourselves (we do it
        // in computeScroll)
        mDeferScrollUpdate = true;
    }

    /**
     * Initializes various states for this workspace.
     */
    @Override
    protected void init() {
        super.init();
        mScrollInterpolator = new WorkspaceOvershootInterpolator();
        // overwrite the previous mScroller
        mScroller = new Scroller(getContext(), mScrollInterpolator);
    }

    @Override
    protected void snapToDestination() {
        snapToPageWithVelocity(mCurrentPage, 0);
    }

    @Override
    protected void snapToPageWithVelocity(int whichPage, int velocity) {
        snapToPageWithVelocity(whichPage, 0, true);
    }

    void snapToPageWithVelocity(int whichPage, int velocity, boolean settle) {
            // if (!mScroller.isFinished()) return;

        whichPage = Math.max(0, Math.min(whichPage, getChildCount() - 1));

        final int screenDelta = Math.max(1, Math.abs(whichPage - mCurrentPage));
        final int newX = getChildOffset(whichPage) - getRelativeChildOffset(whichPage);
        final int delta = newX - mScrollX;
        int duration = (screenDelta + 1) * 100;

        if (!mScroller.isFinished()) {
            mScroller.abortAnimation();
        }

        if (settle) {
            mScrollInterpolator.setDistance(screenDelta);
        } else {
            mScrollInterpolator.disableSettle();
        }

        velocity = Math.abs(velocity);
        if (velocity > 0) {
            duration += (duration / (velocity / BASELINE_FLING_VELOCITY))
                    * FLING_VELOCITY_INFLUENCE;
        } else {
            duration += 100;
        }
        snapToPage(whichPage, delta, duration);
    }

    @Override
    protected void snapToPage(int whichPage) {
        snapToPageWithVelocity(whichPage, 0, false);
    }

    @Override
    public void computeScroll() {
        boolean scrollComputed = computeScrollHelper();

        if (!scrollComputed && mTouchState == TOUCH_STATE_SCROLLING) {
            final float now = System.nanoTime() / NANOTIME_DIV;
            final float e = (float) Math.exp((now - mSmoothingTime) / SMOOTHING_CONSTANT);
            final float dx = mTouchX - mScrollX;
            mScrollX += dx * e;
            mSmoothingTime = now;

            // Keep generating points as long as we're more than 1px away from the target
            if (dx > 1.f || dx < -1.f) {
                invalidate();
            }
        }
    }
}
