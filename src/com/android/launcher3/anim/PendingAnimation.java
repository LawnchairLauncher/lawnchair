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
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.os.Trace;
import android.util.FloatProperty;

import com.android.launcher3.anim.AnimatorPlaybackController.Holder;

import java.util.ArrayList;

/**
 * Utility class to keep track of a running animation.
 *
 * This class allows attaching end callbacks to an animation is intended to be used with
 * {@link com.android.launcher3.anim.AnimatorPlaybackController}, since in that case
 * AnimationListeners are not properly dispatched.
 *
 * TODO: Find a better name
 */
public class PendingAnimation extends AnimatedPropertySetter {

    private final ArrayList<Holder> mAnimHolders = new ArrayList<>();
    private final long mDuration;

    public PendingAnimation(long  duration) {
        mDuration = duration;
    }

    public long getDuration() {
        return mDuration;
    }

    /**
     * Utility method to sent an interpolator on an animation and add it to the list
     */
    public void add(Animator anim, TimeInterpolator interpolator, SpringProperty springProperty) {
        anim.setInterpolator(interpolator);
        add(anim, springProperty);
    }

    /**
     * Utility method to sent an interpolator on an animation and add it to the list
     */
    public void add(Animator anim, TimeInterpolator interpolator) {
        add(anim, interpolator, SpringProperty.DEFAULT);
    }

    @Override
    public void add(Animator anim) {
        add(anim, SpringProperty.DEFAULT);
    }

    public void add(Animator a, SpringProperty springProperty) {
        mAnim.play(a.setDuration(mDuration));
        addAnimationHoldersRecur(a, mDuration, springProperty, mAnimHolders);
    }

    /**
     * Configures interpolator of the underlying AnimatorSet.
     */
    public void setInterpolator(TimeInterpolator interpolator) {
        mAnim.setInterpolator(interpolator);
    }

    public <T> void addFloat(T target, FloatProperty<T> property, float from, float to,
            TimeInterpolator interpolator) {
        Animator anim = ObjectAnimator.ofFloat(target, property, from, to);
        anim.setInterpolator(interpolator);
        add(anim);
    }

    /**
     * Add an {@link AnimatedFloat} to the animation.
     * <p>
     * Different from {@link #addFloat}, this method use animator provided by
     * {@link AnimatedFloat#animateToValue}, which tracks the animator inside the AnimatedFloat,
     * allowing the animation to be canceled and animate again from AnimatedFloat side.
     */
    public void addAnimatedFloat(AnimatedFloat target, float from, float to,
            TimeInterpolator interpolator) {
        Animator anim = target.animateToValue(from, to);
        anim.setInterpolator(interpolator);
        add(anim);
    }

    /** If trace is enabled, add counter to trace animation progress. */
    public void logAnimationProgressToTrace(String counterName) {
        if (Trace.isEnabled()) {
            super.addOnFrameListener(
                    animation -> Trace.setCounter(
                            counterName, (long) (animation.getAnimatedFraction() * 100)));
        }
    }

    /**
     * Creates and returns the underlying AnimatorSet
     */
    @Override
    public AnimatorSet buildAnim() {
        if (mAnimHolders.isEmpty()) {
            // Add a placeholder animation to that the duration is respected
            add(ValueAnimator.ofFloat(0, 1).setDuration(mDuration));
        }
        return super.buildAnim();
    }

    /**
     * Creates a controller for this animation
     */
    public AnimatorPlaybackController createPlaybackController() {
        return new AnimatorPlaybackController(buildAnim(), mDuration, mAnimHolders);
    }
}
