/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.launcher3.util;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.animation.ValueAnimator.AnimatorUpdateListener;
import android.content.Context;
import android.view.View;
import android.view.accessibility.AccessibilityManager;

import java.util.Arrays;

/**
 * A utility class which divides the alpha for a view across multiple states.
 */
public class MultiStateAlphaController {

    private final View mTargetView;
    private final float[] mAlphas;
    private final AccessibilityManager mAm;
    private int mZeroAlphaListenerCount = 0;

    public MultiStateAlphaController(View view, int stateCount) {
        mTargetView = view;
        mAlphas = new float[stateCount];
        Arrays.fill(mAlphas, 1);

        mAm = (AccessibilityManager) view.getContext().getSystemService(Context.ACCESSIBILITY_SERVICE);
    }

    public void setAlphaAtIndex(float alpha, int index) {
        mAlphas[index] = alpha;
        updateAlpha();
    }

    private void updateAlpha() {
        // Only update the alpha if no zero-alpha animation is running.
        if (mZeroAlphaListenerCount > 0) {
            return;
        }
        float finalAlpha = 1;
        for (float a : mAlphas) {
            finalAlpha = finalAlpha * a;
        }
        mTargetView.setAlpha(finalAlpha);
        mTargetView.setVisibility(finalAlpha > 0 ? View.VISIBLE
                : (mAm.isEnabled() ? View.GONE : View.INVISIBLE));
    }

    /**
     * Returns an animator which changes the alpha at the index {@param index}
     * to {@param finalAlpha}. Alphas at other index are not affected.
     */
    public Animator animateAlphaAtIndex(float finalAlpha, final int index) {
        final ValueAnimator anim;

        if (Float.compare(finalAlpha, mAlphas[index]) == 0) {
            // Return a dummy animator to avoid null checks.
            anim = ValueAnimator.ofFloat(0, 0);
        } else {
            ValueAnimator animator = ValueAnimator.ofFloat(mAlphas[index], finalAlpha);
            animator.addUpdateListener(new AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator valueAnimator) {
                    float value = (Float) valueAnimator.getAnimatedValue();
                    setAlphaAtIndex(value, index);
                }
            });
            anim = animator;
        }

        if (Float.compare(finalAlpha, 0f) == 0) {
            // In case when any channel is animating to 0, and the current alpha is also 0, do not
            // update alpha of the target view while the animation is running.
            // We special case '0' because if any channel is set to 0, values of other
            // channels do not matter.
            anim.addListener(new ZeroAlphaAnimatorListener());
        }
        return anim;
    }

    private class ZeroAlphaAnimatorListener extends AnimatorListenerAdapter {

        private boolean mStartedAtZero = false;

        @Override
        public void onAnimationStart(Animator animation) {
            mStartedAtZero = Float.compare(mTargetView.getAlpha(), 0f) == 0;
            if (mStartedAtZero) {
                mZeroAlphaListenerCount++;
                mTargetView.setAlpha(0);
            }
        }

        @Override
        public void onAnimationEnd(Animator animation) {
            if (mStartedAtZero) {
                mZeroAlphaListenerCount--;
                updateAlpha();
            }
        }
    }
}
