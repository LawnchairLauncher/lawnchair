/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.wm.shell.compatui;

import static android.app.TaskInfo.PROPERTY_VALUE_UNSET;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.TaskInfo;
import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.FrameLayout;

import com.android.wm.shell.R;

import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Container for reachability education which handles all the show/hide animations.
 */
public class ReachabilityEduLayout extends FrameLayout {

    private static final float ALPHA_FULL_TRANSPARENT = 0f;

    private static final float ALPHA_FULL_OPAQUE = 1f;

    private static final long VISIBILITY_ANIMATION_DURATION_MS = 400;

    private static final long MARGINS_ANIMATION_DURATION_MS = 250;

    private static final String ALPHA_PROPERTY_NAME = "alpha";

    private ReachabilityEduWindowManager mWindowManager;

    private View mMoveLeftButton;
    private View mMoveRightButton;
    private View mMoveUpButton;
    private View mMoveDownButton;

    private int mLastLeftMargin = PROPERTY_VALUE_UNSET;
    private int mLastRightMargin = PROPERTY_VALUE_UNSET;
    private int mLastTopMargin = PROPERTY_VALUE_UNSET;
    private int mLastBottomMargin = PROPERTY_VALUE_UNSET;

    public ReachabilityEduLayout(Context context) {
        this(context, null);
    }

    public ReachabilityEduLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ReachabilityEduLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public ReachabilityEduLayout(Context context, AttributeSet attrs, int defStyleAttr,
            int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    void inject(ReachabilityEduWindowManager windowManager) {
        mWindowManager = windowManager;
    }

    void handleVisibility(boolean horizontalEnabled, boolean verticalEnabled,
            int letterboxVerticalPosition,
            int letterboxHorizontalPosition, int availableWidth, int availableHeight,
            CompatUIConfiguration compatUIConfiguration, TaskInfo taskInfo) {
        hideAllImmediately();
        if (horizontalEnabled && letterboxHorizontalPosition != PROPERTY_VALUE_UNSET) {
            handleLetterboxHorizontalPosition(availableWidth, letterboxHorizontalPosition);
            compatUIConfiguration.setUserHasSeenHorizontalReachabilityEducation(taskInfo);
        } else if (verticalEnabled && letterboxVerticalPosition != PROPERTY_VALUE_UNSET) {
            handleLetterboxVerticalPosition(availableHeight, letterboxVerticalPosition);
            compatUIConfiguration.setUserHasSeenVerticalReachabilityEducation(taskInfo);
        }
    }

    void hideAllImmediately() {
        hideImmediately(mMoveLeftButton);
        hideImmediately(mMoveRightButton);
        hideImmediately(mMoveUpButton);
        hideImmediately(mMoveDownButton);
        mLastLeftMargin = PROPERTY_VALUE_UNSET;
        mLastRightMargin = PROPERTY_VALUE_UNSET;
        mLastTopMargin = PROPERTY_VALUE_UNSET;
        mLastBottomMargin = PROPERTY_VALUE_UNSET;
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mMoveLeftButton = findViewById(R.id.reachability_move_left_button);
        mMoveRightButton = findViewById(R.id.reachability_move_right_button);
        mMoveUpButton = findViewById(R.id.reachability_move_up_button);
        mMoveDownButton = findViewById(R.id.reachability_move_down_button);
        mMoveLeftButton.measure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED);
        mMoveRightButton.measure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED);
        mMoveUpButton.measure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED);
        mMoveDownButton.measure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED);
    }

    private void hideImmediately(View view) {
        view.setAlpha(0);
        view.setVisibility(View.INVISIBLE);
    }

    private Animator marginAnimator(View view, Function<LayoutParams, Integer> marginSupplier,
            BiConsumer<LayoutParams, Integer> marginConsumer, int from, int to) {
        final LayoutParams layoutParams = ((LayoutParams) view.getLayoutParams());
        ValueAnimator animator = ValueAnimator.ofInt(marginSupplier.apply(layoutParams), from, to);
        animator.addUpdateListener(valueAnimator -> {
            marginConsumer.accept(layoutParams, (Integer) valueAnimator.getAnimatedValue());
            view.requestLayout();
        });
        animator.setDuration(MARGINS_ANIMATION_DURATION_MS);
        return animator;
    }

    private void handleLetterboxHorizontalPosition(int availableWidth,
            int letterboxHorizontalPosition) {
        hideItem(mMoveUpButton);
        hideItem(mMoveDownButton);
        mLastTopMargin = PROPERTY_VALUE_UNSET;
        mLastBottomMargin = PROPERTY_VALUE_UNSET;
        // We calculate the available space on the left and right
        final int horizontalGap = availableWidth / 2;
        final int leftAvailableSpace = letterboxHorizontalPosition * horizontalGap;
        final int rightAvailableSpace = availableWidth - leftAvailableSpace;
        // We show the button if we have enough space
        if (leftAvailableSpace >= mMoveLeftButton.getMeasuredWidth()) {
            int newLeftMargin = (horizontalGap - mMoveLeftButton.getMeasuredWidth()) / 2;
            if (mLastLeftMargin == PROPERTY_VALUE_UNSET) {
                mLastLeftMargin = newLeftMargin;
            }
            if (mLastLeftMargin != newLeftMargin) {
                marginAnimator(mMoveLeftButton, layoutParams -> layoutParams.leftMargin,
                        (layoutParams, margin) -> layoutParams.leftMargin = margin,
                        mLastLeftMargin, newLeftMargin).start();
            } else {
                final LayoutParams leftParams = ((LayoutParams) mMoveLeftButton.getLayoutParams());
                leftParams.leftMargin = mLastLeftMargin;
                mMoveLeftButton.setLayoutParams(leftParams);
            }
            showItem(mMoveLeftButton);
        } else {
            hideItem(mMoveLeftButton);
            mLastLeftMargin = PROPERTY_VALUE_UNSET;
        }
        if (rightAvailableSpace >= mMoveRightButton.getMeasuredWidth()) {
            int newRightMargin = (horizontalGap - mMoveRightButton.getMeasuredWidth()) / 2;
            if (mLastRightMargin == PROPERTY_VALUE_UNSET) {
                mLastRightMargin = newRightMargin;
            }
            if (mLastRightMargin != newRightMargin) {
                marginAnimator(mMoveRightButton, layoutParams -> layoutParams.rightMargin,
                        (layoutParams, margin) -> layoutParams.rightMargin = margin,
                        mLastRightMargin, newRightMargin).start();
            } else {
                final LayoutParams rightParams =
                        ((LayoutParams) mMoveRightButton.getLayoutParams());
                rightParams.rightMargin = mLastRightMargin;
                mMoveRightButton.setLayoutParams(rightParams);
            }
            showItem(mMoveRightButton);
        } else {
            hideItem(mMoveRightButton);
            mLastRightMargin = PROPERTY_VALUE_UNSET;
        }
    }

    private void handleLetterboxVerticalPosition(int availableHeight,
            int letterboxVerticalPosition) {
        hideItem(mMoveLeftButton);
        hideItem(mMoveRightButton);
        mLastLeftMargin = PROPERTY_VALUE_UNSET;
        mLastRightMargin = PROPERTY_VALUE_UNSET;
        // We calculate the available space on the left and right
        final int verticalGap = availableHeight / 2;
        final int topAvailableSpace = letterboxVerticalPosition * verticalGap;
        final int bottomAvailableSpace = availableHeight - topAvailableSpace;
        if (topAvailableSpace >= mMoveUpButton.getMeasuredHeight()) {
            int newTopMargin = (verticalGap - mMoveUpButton.getMeasuredHeight()) / 2;
            if (mLastTopMargin == PROPERTY_VALUE_UNSET) {
                mLastTopMargin = newTopMargin;
            }
            if (mLastTopMargin != newTopMargin) {
                marginAnimator(mMoveUpButton, layoutParams -> layoutParams.topMargin,
                        (layoutParams, margin) -> layoutParams.topMargin = margin,
                        mLastTopMargin, newTopMargin).start();
            } else {
                final LayoutParams topParams = ((LayoutParams) mMoveUpButton.getLayoutParams());
                topParams.topMargin = mLastTopMargin;
                mMoveUpButton.setLayoutParams(topParams);
            }
            showItem(mMoveUpButton);
        } else {
            hideItem(mMoveUpButton);
            mLastTopMargin = PROPERTY_VALUE_UNSET;
        }
        if (bottomAvailableSpace >= mMoveDownButton.getMeasuredHeight()) {
            int newBottomMargin = (verticalGap - mMoveDownButton.getMeasuredHeight()) / 2;
            if (mLastBottomMargin == PROPERTY_VALUE_UNSET) {
                mLastBottomMargin = newBottomMargin;
            }
            if (mLastBottomMargin != newBottomMargin) {
                marginAnimator(mMoveDownButton, layoutParams -> layoutParams.bottomMargin,
                        (layoutParams, margin) -> layoutParams.bottomMargin = margin,
                        mLastBottomMargin, newBottomMargin).start();
            } else {
                final LayoutParams bottomParams =
                        ((LayoutParams) mMoveDownButton.getLayoutParams());
                bottomParams.bottomMargin = mLastBottomMargin;
                mMoveDownButton.setLayoutParams(bottomParams);
            }
            showItem(mMoveDownButton);
        } else {
            hideItem(mMoveDownButton);
            mLastBottomMargin = PROPERTY_VALUE_UNSET;
        }
    }

    private void showItem(View view) {
        view.setVisibility(View.VISIBLE);
        ObjectAnimator fadeIn = ObjectAnimator.ofFloat(view, ALPHA_PROPERTY_NAME,
                ALPHA_FULL_TRANSPARENT, ALPHA_FULL_OPAQUE);
        fadeIn.setDuration(VISIBILITY_ANIMATION_DURATION_MS);
        fadeIn.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                view.setVisibility(View.VISIBLE);
            }
        });
        fadeIn.start();
    }

    private void hideItem(View view) {
        ObjectAnimator fadeOut = ObjectAnimator.ofFloat(view, ALPHA_PROPERTY_NAME,
                ALPHA_FULL_OPAQUE, ALPHA_FULL_TRANSPARENT);
        fadeOut.setDuration(VISIBILITY_ANIMATION_DURATION_MS);
        fadeOut.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                view.setVisibility(View.INVISIBLE);
            }
        });
        fadeOut.start();
    }

}
