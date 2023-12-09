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

package com.android.launcher3.anim;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.animation.ValueAnimator.AnimatorUpdateListener;
import android.view.View;
import android.view.ViewGroup;

/**
 * A convenience class to update a view's visibility state after an alpha animation.
 */
public class AlphaUpdateListener extends AnimatorListenerAdapter
        implements AnimatorUpdateListener {
    public static final float ALPHA_CUTOFF_THRESHOLD = 0.01f;

    private View mView;

    public AlphaUpdateListener(View v) {
        mView = v;
    }

    @Override
    public void onAnimationUpdate(ValueAnimator arg0) {
        updateVisibility(mView);
    }

    @Override
    public void onAnimationEnd(Animator animator) {
        updateVisibility(mView);
    }

    @Override
    public void onAnimationStart(Animator arg0) {
        // We want the views to be visible for animation, so fade-in/out is visible
        mView.setVisibility(View.VISIBLE);
    }

    public static void updateVisibility(View view) {
        updateVisibility(view, View.INVISIBLE);
    }

    /**
     * Update view's visibility.
     *
     * @param view View that needs to update visibility.
     * @param hiddenVisibility {@link View#GONE} or {@link View#INVISIBLE}
     */
    public static void updateVisibility(View view, int hiddenVisibility) {
        if (view.getAlpha() < ALPHA_CUTOFF_THRESHOLD && view.getVisibility() != hiddenVisibility) {
            view.setVisibility(hiddenVisibility);
        } else if (view.getAlpha() > ALPHA_CUTOFF_THRESHOLD
                && view.getVisibility() != View.VISIBLE) {
            if (view instanceof ViewGroup) {
                ViewGroup viewGroup = ((ViewGroup) view);
                int oldFocusability = viewGroup.getDescendantFocusability();
                viewGroup.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
                viewGroup.setVisibility(View.VISIBLE);
                viewGroup.setDescendantFocusability(oldFocusability);
            } else {
                view.setVisibility(View.VISIBLE);
            }
        }
    }
}