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
package com.android.launcher3.pageindicators;

import static com.android.launcher3.anim.AnimatorListeners.forEndCallback;
import static com.android.launcher3.util.MultiPropertyFactory.MULTI_PROPERTY_VALUE;

import android.animation.ObjectAnimator;
import android.animation.TimeInterpolator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.ViewConfiguration;

import androidx.annotation.Nullable;

import com.android.launcher3.Alarm;
import com.android.launcher3.Insettable;
import com.android.launcher3.anim.PropertySetter;
import com.android.launcher3.util.MultiValueAlpha;

/**
 * Extension of {@link PageIndicatorDots} with Launcher specific page-indicator functionality
 */
public class LauncherDotsPageIndicator extends PageIndicatorDots
        implements Insettable, PageIndicator {

    private static final int PAGINATION_FADE_DELAY = ViewConfiguration.getScrollDefaultDelay();
    private static final int PAGINATION_FADE_IN_DURATION = 83;
    private static final int PAGINATION_FADE_OUT_DURATION = 167;

    private static final int INDEX_VIEW_ALPHA = 0;
    private static final int INDEX_AUTO_HIDE = 1;
    private static final int ALPHA_CHANNEL_COUNT = 2;

    private final Alarm mAutoHideAlarm;
    private final MultiValueAlpha mMultiValueAlpha;

    private @Nullable ObjectAnimator mAlphaAnimator;
    private boolean mShouldAutoHide;
    private float mTargetAutoHideAlpha;

    private boolean mIsSettled = true;
    private int mTotalScroll;

    public LauncherDotsPageIndicator(Context context) {
        this(context, null);
    }

    public LauncherDotsPageIndicator(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public LauncherDotsPageIndicator(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mMultiValueAlpha = new MultiValueAlpha(this, ALPHA_CHANNEL_COUNT);
        mMultiValueAlpha.setUpdateVisibility(true);

        mTargetAutoHideAlpha = mMultiValueAlpha.get(INDEX_AUTO_HIDE).getValue();

        mAutoHideAlarm = new Alarm();
        mAutoHideAlarm.setOnAlarmListener(a -> animatePaginationToAlpha(0));
    }

    @Override
    public void setScroll(int currentScroll, int totalScroll) {
        mTotalScroll = totalScroll;
        super.setScroll(currentScroll, totalScroll);
    }

    @Override
    public void setShouldAutoHide(boolean shouldAutoHide) {
        mShouldAutoHide = shouldAutoHide;
        mAutoHideAlarm.cancelAlarm();
        if (!mIsSettled || !mShouldAutoHide) {
            animatePaginationToAlpha(1);
        } else {
            mAutoHideAlarm.setAlarm(PAGINATION_FADE_DELAY);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (mShouldAutoHide && mTotalScroll == 0) {
            return;
        }
        super.onDraw(canvas);
    }

    @Override
    public void setActiveMarker(int activePage) {
        super.setActiveMarker(activePage);
    }

    @Override
    public void setMarkersCount(int numMarkers) {
        super.setMarkersCount(numMarkers);
    }

    @Override
    public void pauseAnimations() {
        if (mAlphaAnimator != null) {
            mAlphaAnimator.pause();
        }
    }

    @Override
    public void skipAnimationsToEnd() {
        if (mAlphaAnimator != null) {
            mAlphaAnimator.end();
        }
    }

    @Override
    protected void onAnimationStateChanged(boolean isSettled) {
        mIsSettled = isSettled;
        if (!mShouldAutoHide) {
            return;
        }
        mAutoHideAlarm.cancelAlarm();
        if (isSettled) {
            mAutoHideAlarm.setAlarm(PAGINATION_FADE_DELAY);
        } else {
            animatePaginationToAlpha(1f);
        }
    }

    private void animatePaginationToAlpha(float targetAlpha) {
        if (mTargetAutoHideAlpha == targetAlpha) {
            // Ignore the new animation if it is going to the same alpha as the current animation.
            return;
        }

        if (mAlphaAnimator != null) {
            mAlphaAnimator.cancel();
        }
        mAlphaAnimator = ObjectAnimator.ofFloat(mMultiValueAlpha.get(INDEX_AUTO_HIDE),
                MULTI_PROPERTY_VALUE, targetAlpha);
        // If we are animating to decrease the alpha, then it's a fade out animation
        // whereas if we are animating to increase the alpha, it's a fade in animation.
        mAlphaAnimator.setDuration(targetAlpha == 0
                ? PAGINATION_FADE_OUT_DURATION
                : PAGINATION_FADE_IN_DURATION);
        mAlphaAnimator.addListener(forEndCallback(() -> mAlphaAnimator = null));
        mAlphaAnimator.start();
        mTargetAutoHideAlpha = targetAlpha;
    }


    @Override
    public void stopAllAnimations() {
        super.stopAllAnimations();
    }

    @Override
    public void prepareEntryAnimation() {
        super.prepareEntryAnimation();
    }

    @Override
    public void playEntryAnimation() {
        super.playEntryAnimation();
    }

    /**
     * We need to override setInsets to prevent InsettableFrameLayout from applying different
     * margins on the pagination.
     */
    @Override
    public void setInsets(Rect insets) {
    }

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }

    @Override
    public void setAlpha(PropertySetter setter, float alpha, TimeInterpolator interpolator) {
        setter.setFloat(mMultiValueAlpha.get(INDEX_VIEW_ALPHA),
                MULTI_PROPERTY_VALUE, alpha, interpolator);
    }
}
