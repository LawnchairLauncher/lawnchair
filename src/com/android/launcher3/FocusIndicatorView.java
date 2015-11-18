/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.launcher3;

import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.util.Pair;
import android.view.View;

import com.android.launcher3.util.Thunk;

public class FocusIndicatorView extends View implements View.OnFocusChangeListener {

    // It can be any number >0. The view is resized using scaleX and scaleY.
    static final int DEFAULT_LAYOUT_SIZE = 100;

    private static final float MIN_VISIBLE_ALPHA = 0.2f;
    private static final long ANIM_DURATION = 150;

    private final int[] mIndicatorPos = new int[2];
    private final int[] mTargetViewPos = new int[2];

    private ObjectAnimator mCurrentAnimation;
    private ViewAnimState mTargetState;

    private View mLastFocusedView;
    private boolean mInitiated;
    private final OnFocusChangeListener mHideIndicatorOnFocusListener;

    private Pair<View, Boolean> mPendingCall;

    public FocusIndicatorView(Context context) {
        this(context, null);
    }

    public FocusIndicatorView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setAlpha(0);
        setBackgroundColor(getResources().getColor(R.color.focused_background));

        mHideIndicatorOnFocusListener = new OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (hasFocus) {
                    endCurrentAnimation();
                    setAlpha(0);
                }
            }
        };
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);

        // Redraw if it is already showing. This avoids a bug where the height changes by a small
        // amount on connecting/disconnecting a bluetooth keyboard.
        if (mLastFocusedView != null) {
            mPendingCall = Pair.create(mLastFocusedView, Boolean.TRUE);
            invalidate();
        }
    }

    /**
     * Sets the alpha of this FocusIndicatorView to 0 when a view with this listener receives focus.
     */
    public View.OnFocusChangeListener getHideIndicatorOnFocusListener() {
        return mHideIndicatorOnFocusListener;
    }

    @Override
    public void onFocusChange(View v, boolean hasFocus) {
        mPendingCall = null;
        if (!mInitiated && (getWidth() == 0)) {
            // View not yet laid out. Wait until the view is ready to be drawn, so that be can
            // get the location on screen.
            mPendingCall = Pair.create(v, hasFocus);
            invalidate();
            return;
        }

        if (!mInitiated) {
            // The parent view should always the a parent of the target view.
            computeLocationRelativeToParent(this, (View) getParent(), mIndicatorPos);
            mInitiated = true;
        }

        if (hasFocus) {
            int indicatorWidth = getWidth();
            int indicatorHeight = getHeight();

            endCurrentAnimation();
            ViewAnimState nextState = new ViewAnimState();
            nextState.scaleX = v.getScaleX() * v.getWidth() / indicatorWidth;
            nextState.scaleY = v.getScaleY() * v.getHeight() / indicatorHeight;

            computeLocationRelativeToParent(v, (View) getParent(), mTargetViewPos);
            nextState.x = mTargetViewPos[0] - mIndicatorPos[0] - (1 - nextState.scaleX) * indicatorWidth / 2;
            nextState.y = mTargetViewPos[1] - mIndicatorPos[1] - (1 - nextState.scaleY) * indicatorHeight / 2;

            if (getAlpha() > MIN_VISIBLE_ALPHA) {
                mTargetState = nextState;
                mCurrentAnimation = LauncherAnimUtils.ofPropertyValuesHolder(this,
                        PropertyValuesHolder.ofFloat(View.ALPHA, 1),
                        PropertyValuesHolder.ofFloat(View.TRANSLATION_X, mTargetState.x),
                        PropertyValuesHolder.ofFloat(View.TRANSLATION_Y, mTargetState.y),
                        PropertyValuesHolder.ofFloat(View.SCALE_X, mTargetState.scaleX),
                        PropertyValuesHolder.ofFloat(View.SCALE_Y, mTargetState.scaleY));
            } else {
                applyState(nextState);
                mCurrentAnimation = LauncherAnimUtils.ofPropertyValuesHolder(this,
                        PropertyValuesHolder.ofFloat(View.ALPHA, 1));
            }
            mLastFocusedView = v;
        } else {
            if (mLastFocusedView == v) {
                mLastFocusedView = null;
                endCurrentAnimation();
                mCurrentAnimation = LauncherAnimUtils.ofPropertyValuesHolder(this,
                        PropertyValuesHolder.ofFloat(View.ALPHA, 0));
            }
        }
        if (mCurrentAnimation != null) {
            mCurrentAnimation.setDuration(ANIM_DURATION).start();
        }
    }

    private void endCurrentAnimation() {
        if (mCurrentAnimation != null) {
            mCurrentAnimation.cancel();
            mCurrentAnimation = null;
        }
        if (mTargetState != null) {
            applyState(mTargetState);
            mTargetState = null;
        }
    }

    private void applyState(ViewAnimState state) {
        setTranslationX(state.x);
        setTranslationY(state.y);
        setScaleX(state.scaleX);
        setScaleY(state.scaleY);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (mPendingCall != null) {
            onFocusChange(mPendingCall.first, mPendingCall.second);
        }
    }

    /**
     * Computes the location of a view relative to {@param parent}, off-setting
     * any shift due to page view scroll.
     * @param pos an array of two integers in which to hold the coordinates
     */
    private static void computeLocationRelativeToParent(View v, View parent, int[] pos) {
        pos[0] = pos[1] = 0;
        computeLocationRelativeToParentHelper(v, parent, pos);

        // If a view is scaled, its position will also shift accordingly. For optimization, only
        // consider this for the last node.
        pos[0] += (1 - v.getScaleX()) * v.getWidth() / 2;
        pos[1] += (1 - v.getScaleY()) * v.getHeight() / 2;
    }

    private static void computeLocationRelativeToParentHelper(View child,
            View commonParent, int[] shift) {
        View parent = (View) child.getParent();
        shift[0] += child.getLeft();
        shift[1] += child.getTop();
        if (parent instanceof PagedView) {
            PagedView page = (PagedView) parent;
            shift[0] -= page.getScrollForPage(page.indexOfChild(child));
        }

        if (parent != commonParent) {
            computeLocationRelativeToParentHelper(parent, commonParent, shift);
        }
    }

    @Thunk static final class ViewAnimState {
        float x, y, scaleX, scaleY;
    }
}
