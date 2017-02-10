/*
 * Copyright (C) 2012 The Android Open Source Project
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

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.view.View;

import com.android.launcher3.anim.AnimationLayerSet;

import java.util.ArrayList;

/**
 * Extension of {@link ValueAnimator} to provide an interface similar to
 * {@link android.view.ViewPropertyAnimator}.
 */
public class LauncherViewPropertyAnimator extends ValueAnimator {

    private final View mTarget;
    private final ArrayList<PropertyValuesHolder> mProperties;

    private boolean mPrepared = false;

    public LauncherViewPropertyAnimator(View view) {
        mTarget = view;
        mProperties = new ArrayList<>();
        setTarget(mTarget);
        addListener(new TransientStateUpdater(mTarget));
    }

    @Override
    public void start() {
        if (!mPrepared) {
            mPrepared = true;
            setValues(mProperties.toArray(new PropertyValuesHolder[mProperties.size()]));
        }
        LauncherAnimUtils.cancelOnDestroyActivity(this);
        super.start();
    }

    public LauncherViewPropertyAnimator translationX(float value) {
        mProperties.add(PropertyValuesHolder.ofFloat(View.TRANSLATION_X, value));
        return this;
    }

    public LauncherViewPropertyAnimator translationY(float value) {
        mProperties.add(PropertyValuesHolder.ofFloat(View.TRANSLATION_Y, value));
        return this;
    }

    public LauncherViewPropertyAnimator scaleX(float value) {
        mProperties.add(PropertyValuesHolder.ofFloat(View.SCALE_X, value));
        return this;
    }

    public LauncherViewPropertyAnimator scaleY(float value) {
        mProperties.add(PropertyValuesHolder.ofFloat(View.SCALE_Y, value));
        return this;
    }

    public LauncherViewPropertyAnimator alpha(float value) {
        mProperties.add(PropertyValuesHolder.ofFloat(View.ALPHA, value));
        return this;
    }

    public LauncherViewPropertyAnimator withLayer() {
        AnimationLayerSet listener = new AnimationLayerSet();
        listener.addView(mTarget);
        addListener(listener);
        return this;
    }

    private static class TransientStateUpdater extends AnimatorListenerAdapter {
        private final View mView;

        TransientStateUpdater(View v) {
            mView = v;
        }

        @Override
        public void onAnimationStart(Animator animation) {
            mView.setHasTransientState(true);
        }

        @Override
        public void onAnimationEnd(Animator animation) {
            mView.setHasTransientState(false);
        }
    }
}
