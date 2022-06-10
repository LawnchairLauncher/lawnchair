/*
 * Copyright (C) 2022 The Android Open Source Project
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

import static com.android.launcher3.LauncherAnimUtils.VIEW_BACKGROUND_COLOR;

import android.animation.Animator;
import android.animation.Animator.AnimatorListener;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.graphics.drawable.ColorDrawable;
import android.util.FloatProperty;
import android.util.IntProperty;
import android.view.View;

import androidx.annotation.NonNull;

import java.util.function.Consumer;

/**
 * Extension of {@link PropertySetter} which applies the property through an animation
 */
public class AnimatedPropertySetter extends PropertySetter {

    protected final AnimatorSet mAnim = new AnimatorSet();
    protected ValueAnimator mProgressAnimator;

    @Override
    public Animator setViewAlpha(View view, float alpha, TimeInterpolator interpolator) {
        if (view == null) {
            return NO_OP;
        }

        // Short-circuit if the view already has this alpha value, but make sure the visibility is
        // set correctly for the requested alpha.
        if (Float.compare(view.getAlpha(), alpha) == 0) {
            AlphaUpdateListener.updateVisibility(view);
            return NO_OP;
        }

        ObjectAnimator anim = ObjectAnimator.ofFloat(view, View.ALPHA, alpha);
        anim.addListener(new AlphaUpdateListener(view));
        anim.setInterpolator(interpolator);
        add(anim);
        return anim;
    }

    @Override
    public Animator setViewBackgroundColor(View view, int color, TimeInterpolator interpolator) {
        if (view == null || (view.getBackground() instanceof ColorDrawable
                && ((ColorDrawable) view.getBackground()).getColor() == color)) {
            return NO_OP;
        }
        ObjectAnimator anim = ObjectAnimator.ofArgb(view, VIEW_BACKGROUND_COLOR, color);
        anim.setInterpolator(interpolator);
        add(anim);
        return anim;
    }

    @Override
    public <T> Animator setFloat(T target, FloatProperty<T> property, float value,
            TimeInterpolator interpolator) {
        if (property.get(target) == value) {
            return NO_OP;
        }
        Animator anim = ObjectAnimator.ofFloat(target, property, value);
        anim.setInterpolator(interpolator);
        add(anim);
        return anim;
    }

    @Override
    public <T> Animator setInt(T target, IntProperty<T> property, int value,
            TimeInterpolator interpolator) {
        if (property.get(target) == value) {
            return NO_OP;
        }
        Animator anim = ObjectAnimator.ofInt(target, property, value);
        anim.setInterpolator(interpolator);
        add(anim);
        return anim;
    }

    @NonNull
    @Override
    public <T> Animator setColor(T target, IntProperty<T> property, int value,
            TimeInterpolator interpolator) {
        if (property.get(target) == value) {
            return NO_OP;
        }
        Animator anim = ObjectAnimator.ofArgb(target, property, value);
        anim.setInterpolator(interpolator);
        add(anim);
        return anim;
    }

    /**
     * Adds a callback to be run on every frame of the animation
     */
    public void addOnFrameCallback(Runnable runnable) {
        addOnFrameListener(anim -> runnable.run());
    }

    /**
     * Adds a listener to be run on every frame of the animation
     */
    public void addOnFrameListener(ValueAnimator.AnimatorUpdateListener listener) {
        if (mProgressAnimator == null) {
            mProgressAnimator = ValueAnimator.ofFloat(0, 1);
        }

        mProgressAnimator.addUpdateListener(listener);
    }

    @Override
    public void addEndListener(Consumer<Boolean> listener) {
        if (mProgressAnimator == null) {
            mProgressAnimator = ValueAnimator.ofFloat(0, 1);
        }
        mProgressAnimator.addListener(AnimatorListeners.forEndCallback(listener));
    }

    /**
     * @see AnimatorSet#addListener(AnimatorListener)
     */
    public void addListener(Animator.AnimatorListener listener) {
        mAnim.addListener(listener);
    }

    @Override
    public void add(Animator a) {
        mAnim.play(a);
    }

    /**
     * Creates and returns the underlying AnimatorSet
     */
    @NonNull
    public AnimatorSet buildAnim() {
        // Add progress animation to the end, so that frame callback is called after all the other
        // animation update.
        if (mProgressAnimator != null) {
            add(mProgressAnimator);
            mProgressAnimator = null;
        }
        return mAnim;
    }
}
