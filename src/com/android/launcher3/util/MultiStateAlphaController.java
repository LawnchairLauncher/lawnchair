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

package com.android.launcher3.util;

import android.animation.Animator;
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

    public MultiStateAlphaController(View view, int stateCount) {
        mTargetView = view;
        mAlphas = new float[stateCount];
        Arrays.fill(mAlphas, 1);

        mAm = (AccessibilityManager) view.getContext().getSystemService(Context.ACCESSIBILITY_SERVICE);
    }

    public void setAlphaAtIndex(float alpha, int index) {
        mAlphas[index] = alpha;
        float finalAlpha = 1;
        for (float a : mAlphas) {
            finalAlpha = finalAlpha * a;
        }
        mTargetView.setAlpha(finalAlpha);
        mTargetView.setVisibility(alpha > 0 ? View.VISIBLE
                : (mAm.isEnabled() ? View.GONE : View.INVISIBLE));
    }

    public Animator animateAlphaAtIndex(float finalAlpha, final int index) {
        if (Float.compare(finalAlpha, mAlphas[index]) == 0) {
            // Return a dummy animator to avoid null checks.
            return ValueAnimator.ofFloat(0, 0);
        } else {
            ValueAnimator animator = ValueAnimator.ofFloat(mAlphas[index], finalAlpha);
            animator.addUpdateListener(new AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator valueAnimator) {
                    float value = (Float) valueAnimator.getAnimatedValue();
                    setAlphaAtIndex(value, index);
                }
            });
            return animator;
        }
    }
}
