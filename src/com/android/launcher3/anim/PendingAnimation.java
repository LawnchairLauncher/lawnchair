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

import static com.android.launcher3.anim.AnimatorPlaybackController.addAnimationHoldersRecur;

import android.animation.Animator;
import android.animation.Animator.AnimatorListener;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.util.FloatProperty;
import android.util.IntProperty;
import android.view.View;

import com.android.launcher3.anim.AnimatorPlaybackController.Holder;

import java.util.ArrayList;
import java.util.function.Consumer;

/**
 * Utility class to keep track of a running animation.
 *
 * This class allows attaching end callbacks to an animation is intended to be used with
 * {@link com.android.launcher3.anim.AnimatorPlaybackController}, since in that case
 * AnimationListeners are not properly dispatched.
 *
 * TODO: Find a better name
 */
public class PendingAnimation implements PropertySetter {

    private final ArrayList<Consumer<EndState>> mEndListeners = new ArrayList<>();

    private final ArrayList<Holder> mAnimHolders = new ArrayList<>();
    private final AnimatorSet mAnim;
    private final long mDuration;

    private ValueAnimator mProgressAnimator;

    public PendingAnimation(long  duration) {
        mDuration = duration;
        mAnim = new AnimatorSet();
    }

    /**
     * Utility method to sent an interpolator on an animation and add it to the list
     */
    public void add(Animator anim, TimeInterpolator interpolator, SpringProperty springProperty) {
        anim.setInterpolator(interpolator);
        add(anim, springProperty);
    }

    public void add(Animator anim) {
        add(anim, SpringProperty.DEFAULT);
    }

    public void add(Animator a, SpringProperty springProperty) {
        mAnim.play(a.setDuration(mDuration));
        addAnimationHoldersRecur(a, mDuration, springProperty, mAnimHolders);
    }

    public void finish(boolean isSuccess, int logAction) {
        for (Consumer<EndState> listeners : mEndListeners) {
            listeners.accept(new EndState(isSuccess, logAction));
        }
        mEndListeners.clear();
    }

    @Override
    public void setViewAlpha(View view, float alpha, TimeInterpolator interpolator) {
        if (view == null || view.getAlpha() == alpha) {
            return;
        }
        ObjectAnimator anim = ObjectAnimator.ofFloat(view, View.ALPHA, alpha);
        anim.addListener(new AlphaUpdateListener(view));
        anim.setInterpolator(interpolator);
        add(anim);
    }

    @Override
    public <T> void setFloat(T target, FloatProperty<T> property, float value,
            TimeInterpolator interpolator) {
        if (property.get(target) == value) {
            return;
        }
        Animator anim = ObjectAnimator.ofFloat(target, property, value);
        anim.setDuration(mDuration).setInterpolator(interpolator);
        add(anim);
    }

    public <T> void addFloat(T target, FloatProperty<T> property, float from, float to,
            TimeInterpolator interpolator) {
        Animator anim = ObjectAnimator.ofFloat(target, property, from, to);
        anim.setInterpolator(interpolator);
        add(anim);
    }

    @Override
    public <T> void setInt(T target, IntProperty<T> property, int value,
            TimeInterpolator interpolator) {
        if (property.get(target) == value) {
            return;
        }
        Animator anim = ObjectAnimator.ofInt(target, property, value);
        anim.setInterpolator(interpolator);
        add(anim);
    }

    /**
     * Adds a callback to be run on every frame of the animation
     */
    public void addOnFrameCallback(Runnable runnable) {
        if (mProgressAnimator == null) {
            mProgressAnimator = ValueAnimator.ofFloat(0, 1);
        }

        mProgressAnimator.addUpdateListener(anim -> runnable.run());
    }

    /**
     * @see AnimatorSet#addListener(AnimatorListener)
     */
    public void addListener(Animator.AnimatorListener listener) {
        mAnim.addListener(listener);
    }

    /**
     * Creates and returns the underlying AnimatorSet
     */
    public AnimatorSet buildAnim() {
        // Add progress animation to the end, so that frame callback is called after all the other
        // animation update.
        if (mProgressAnimator != null) {
            add(mProgressAnimator);
            mProgressAnimator = null;
        }
        if (mAnimHolders.isEmpty()) {
            // Add a dummy animation to that the duration is respected
            add(ValueAnimator.ofFloat(0, 1).setDuration(mDuration));
        }
        return mAnim;
    }

    /**
     * Creates a controller for this animation
     */
    public AnimatorPlaybackController createPlaybackController() {
        return new AnimatorPlaybackController(buildAnim(), mDuration, mAnimHolders);
    }

    /**
     * Add a listener of receiving the end state.
     * Note that the listeners are called as a result of calling {@link #finish(boolean, int)}
     * and not automatically
     */
    public void addEndListener(Consumer<EndState> listener) {
        mEndListeners.add(listener);
    }

    public static class EndState {
        public boolean isSuccess;
        public int logAction;

        public EndState(boolean isSuccess, int logAction) {
            this.isSuccess = isSuccess;
            this.logAction = logAction;
        }
    }
}
