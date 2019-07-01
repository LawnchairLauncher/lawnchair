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

package com.android.systemui.shared.recents.utilities;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ValueAnimator;
import android.annotation.IntDef;
import android.util.SparseArray;
import android.util.SparseLongArray;
import android.view.View;
import android.view.animation.Interpolator;
import android.view.animation.LinearInterpolator;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.List;

/**
 * The generic set of animation properties to animate a {@link View}. The animation can have
 * different interpolators, start delays and durations for each of the different properties.
 */
public class AnimationProps {

    private static final Interpolator LINEAR_INTERPOLATOR = new LinearInterpolator();
    public static final AnimationProps IMMEDIATE = new AnimationProps(0, LINEAR_INTERPOLATOR);

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({ALL, TRANSLATION_X, TRANSLATION_Y, TRANSLATION_Z, ALPHA, SCALE, BOUNDS})
    public @interface PropType {}

    public static final int ALL = 0;
    public static final int TRANSLATION_X = 1;
    public static final int TRANSLATION_Y = 2;
    public static final int TRANSLATION_Z = 3;
    public static final int ALPHA = 4;
    public static final int SCALE = 5;
    public static final int BOUNDS = 6;
    public static final int DIM_ALPHA = 7;

    private SparseLongArray mPropStartDelay;
    private SparseLongArray mPropDuration;
    private SparseArray<Interpolator> mPropInterpolators;
    private Animator.AnimatorListener mListener;

    /**
     * The builder constructor.
     */
    public AnimationProps() {}

    /**
     * Creates an animation with a default {@param duration} and {@param interpolator} for all
     * properties in this animation.
     */
    public AnimationProps(int duration, Interpolator interpolator) {
        this(0, duration, interpolator, null);
    }

    /**
     * Creates an animation with a default {@param duration} and {@param interpolator} for all
     * properties in this animation.
     */
    public AnimationProps(int duration, Interpolator interpolator,
            Animator.AnimatorListener listener) {
        this(0, duration, interpolator, listener);
    }

    /**
     * Creates an animation with a default {@param startDelay}, {@param duration} and
     * {@param interpolator} for all properties in this animation.
     */
    public AnimationProps(int startDelay, int duration, Interpolator interpolator) {
        this(startDelay, duration, interpolator, null);
    }

    /**
     * Creates an animation with a default {@param startDelay}, {@param duration} and
     * {@param interpolator} for all properties in this animation.
     */
    public AnimationProps(int startDelay, int duration, Interpolator interpolator,
            Animator.AnimatorListener listener) {
        setStartDelay(ALL, startDelay);
        setDuration(ALL, duration);
        setInterpolator(ALL, interpolator);
        setListener(listener);
    }

    /**
     * Creates a new {@link AnimatorSet} that will animate the given animators.  Callers need to
     * manually apply the individual animation properties for each of the animators respectively.
     */
    public AnimatorSet createAnimator(List<Animator> animators) {
        AnimatorSet anim = new AnimatorSet();
        if (mListener != null) {
            anim.addListener(mListener);
        }
        anim.playTogether(animators);
        return anim;
    }

    /**
     * Applies the specific start delay, duration and interpolator to the given {@param animator}
     * for the specified {@param propertyType}.
     */
    public <T extends ValueAnimator> T apply(@PropType int propertyType, T animator) {
        animator.setStartDelay(getStartDelay(propertyType));
        animator.setDuration(getDuration(propertyType));
        animator.setInterpolator(getInterpolator(propertyType));
        return animator;
    }

    /**
     * Sets a start delay for a specific property.
     */
    public AnimationProps setStartDelay(@PropType int propertyType, int startDelay) {
        if (mPropStartDelay == null) {
            mPropStartDelay = new SparseLongArray();
        }
        mPropStartDelay.append(propertyType, startDelay);
        return this;
    }

    /**
     * Returns the start delay for a specific property.
     */
    public long getStartDelay(@PropType int propertyType) {
        if (mPropStartDelay != null) {
            long startDelay = mPropStartDelay.get(propertyType, -1);
            if (startDelay != -1) {
                return startDelay;
            }
            return mPropStartDelay.get(ALL, 0);
        }
        return 0;
    }

    /**
     * Sets a duration for a specific property.
     */
    public AnimationProps setDuration(@PropType int propertyType, int duration) {
        if (mPropDuration == null) {
            mPropDuration = new SparseLongArray();
        }
        mPropDuration.append(propertyType, duration);
        return this;
    }

    /**
     * Returns the duration for a specific property.
     */
    public long getDuration(@PropType int propertyType) {
        if (mPropDuration != null) {
            long duration = mPropDuration.get(propertyType, -1);
            if (duration != -1) {
                return duration;
            }
            return mPropDuration.get(ALL, 0);
        }
        return 0;
    }

    /**
     * Sets an interpolator for a specific property.
     */
    public AnimationProps setInterpolator(@PropType int propertyType, Interpolator interpolator) {
        if (mPropInterpolators == null) {
            mPropInterpolators = new SparseArray<>();
        }
        mPropInterpolators.append(propertyType, interpolator);
        return this;
    }

    /**
     * Returns the interpolator for a specific property, falling back to the general interpolator
     * if there is no specific property interpolator.
     */
    public Interpolator getInterpolator(@PropType int propertyType) {
        if (mPropInterpolators != null) {
            Interpolator interp = mPropInterpolators.get(propertyType);
            if (interp != null) {
                return interp;
            }
            return mPropInterpolators.get(ALL, LINEAR_INTERPOLATOR);
        }
        return LINEAR_INTERPOLATOR;
    }

    /**
     * Sets an animator listener for this animation.
     */
    public AnimationProps setListener(Animator.AnimatorListener listener) {
        mListener = listener;
        return this;
    }

    /**
     * Returns the animator listener for this animation.
     */
    public Animator.AnimatorListener getListener() {
        return mListener;
    }

    /**
     * Returns whether this animation has any duration.
     */
    public boolean isImmediate() {
        int count = mPropDuration.size();
        for (int i = 0; i < count; i++) {
            if (mPropDuration.valueAt(i) > 0) {
                return false;
            }
        }
        return true;
    }
}
