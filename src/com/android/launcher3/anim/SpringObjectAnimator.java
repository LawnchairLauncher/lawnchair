/*
 * Copyright (C) 2019 The Android Open Source Project
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

import static com.android.launcher3.config.FeatureFlags.QUICKSTEP_SPRINGS;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.util.Log;
import android.util.Property;

import com.android.launcher3.ProgressInterface;

import java.util.ArrayList;

import androidx.dynamicanimation.animation.DynamicAnimation.OnAnimationEndListener;
import androidx.dynamicanimation.animation.FloatPropertyCompat;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;

/**
 * This animator allows for an object's property to be be controlled by an {@link ObjectAnimator} or
 * a {@link SpringAnimation}. It extends ValueAnimator so it can be used in an AnimatorSet.
 */
public class SpringObjectAnimator<T extends ProgressInterface> extends ValueAnimator {

    private static final String TAG = "SpringObjectAnimator";
    private static boolean DEBUG = false;

    private T mObject;
    private ObjectAnimator mObjectAnimator;
    private float[] mValues;

    private SpringAnimation mSpring;
    private SpringProperty<T> mProperty;

    private ArrayList<AnimatorListener> mListeners;
    private boolean mSpringEnded = true;
    private boolean mAnimatorEnded = true;
    private boolean mEnded = true;

    private static final FloatPropertyCompat<ProgressInterface> sFloatProperty =
            new FloatPropertyCompat<ProgressInterface>("springObjectAnimator") {
        @Override
        public float getValue(ProgressInterface object) {
            return object.getProgress();
        }

        @Override
        public void setValue(ProgressInterface object, float progress) {
            object.setProgress(progress);
        }
    };

    public SpringObjectAnimator(T object, String name, float minimumVisibleChange, float damping,
            float stiffness, float... values) {
        mObject = object;
        mSpring = new SpringAnimation(object, sFloatProperty);
        mSpring.setMinimumVisibleChange(minimumVisibleChange);
        mSpring.setSpring(new SpringForce(0)
                .setDampingRatio(damping)
                .setStiffness(stiffness));
        mSpring.setStartVelocity(0.01f);
        mProperty = new SpringProperty<T>(name, mSpring);
        mObjectAnimator = ObjectAnimator.ofFloat(object, mProperty, values);
        mValues = values;
        mListeners = new ArrayList<>();
        setFloatValues(values);

        // We use this listener and track mListeners so that we can sync the animator and spring
        // listeners.
        mObjectAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                mAnimatorEnded = false;
                mEnded = false;
                for (AnimatorListener l : mListeners) {
                    l.onAnimationStart(animation);
                }
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                mAnimatorEnded = true;
                tryEnding();
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                for (AnimatorListener l : mListeners) {
                    l.onAnimationCancel(animation);
                }
                mSpring.cancel();
            }
        });

        mSpring.addUpdateListener((animation, value, velocity) -> mSpringEnded = false);
        mSpring.addEndListener((animation, canceled, value, velocity) -> {
            mSpringEnded = true;
            tryEnding();
        });
    }

    private void tryEnding() {
        if (DEBUG) {
            Log.d(TAG, "tryEnding#mAnimatorEnded=" + mAnimatorEnded + ", mSpringEnded="
                    + mSpringEnded + ", mEnded=" + mEnded);
        }

        // If springs are disabled, ignore value of mSpringEnded
        if (mAnimatorEnded && (mSpringEnded || !QUICKSTEP_SPRINGS.get()) && !mEnded) {
            for (AnimatorListener l : mListeners) {
                l.onAnimationEnd(this);
            }
            mEnded = true;
        }
    }

    public SpringAnimation getSpring() {
        return mSpring;
    }

    /**
     * Initializes and sets up the spring to take over controlling the object.
     */
    void startSpring(float end, float velocity, OnAnimationEndListener endListener) {
        // Cancel the spring so we can set new start velocity and final position. We need to remove
        // the listener since the spring is not actually ending.
        mSpring.removeEndListener(endListener);
        mSpring.cancel();
        mSpring.addEndListener(endListener);

        mProperty.switchToSpring();

        mSpring.setStartVelocity(velocity);
        mSpring.animateToFinalPosition(end == 0 ? mValues[0] : mValues[1]);
    }

    @Override
    public void addListener(AnimatorListener listener) {
        mListeners.add(listener);
    }

    public ArrayList<AnimatorListener> getObjectAnimatorListeners() {
        return mObjectAnimator.getListeners();
    }

    @Override
    public ArrayList<AnimatorListener> getListeners() {
        return mListeners;
    }

    @Override
    public void removeAllListeners() {
        mListeners.clear();
    }

    @Override
    public void removeListener(AnimatorListener listener) {
        mListeners.remove(listener);
    }

    @Override
    public void addPauseListener(AnimatorPauseListener listener) {
        mObjectAnimator.addPauseListener(listener);
    }

    @Override
    public void cancel() {
        mObjectAnimator.cancel();
        mSpring.cancel();
    }

    @Override
    public void end() {
        mObjectAnimator.end();
    }

    @Override
    public long getDuration() {
        return mObjectAnimator.getDuration();
    }

    @Override
    public TimeInterpolator getInterpolator() {
        return mObjectAnimator.getInterpolator();
    }

    @Override
    public long getStartDelay() {
        return mObjectAnimator.getStartDelay();
    }

    @Override
    public long getTotalDuration() {
        return mObjectAnimator.getTotalDuration();
    }

    @Override
    public boolean isPaused() {
        return mObjectAnimator.isPaused();
    }

    @Override
    public boolean isRunning() {
        return mObjectAnimator.isRunning();
    }

    @Override
    public boolean isStarted() {
        return mObjectAnimator.isStarted();
    }

    @Override
    public void pause() {
        mObjectAnimator.pause();
    }

    @Override
    public void removePauseListener(AnimatorPauseListener listener) {
        mObjectAnimator.removePauseListener(listener);
    }

    @Override
    public void resume() {
        mObjectAnimator.resume();
    }

    @Override
    public ValueAnimator setDuration(long duration) {
        return mObjectAnimator.setDuration(duration);
    }

    @Override
    public void setInterpolator(TimeInterpolator value) {
        mObjectAnimator.setInterpolator(value);
    }

    @Override
    public void setStartDelay(long startDelay) {
        mObjectAnimator.setStartDelay(startDelay);
    }

    @Override
    public void setTarget(Object target) {
        mObjectAnimator.setTarget(target);
    }

    @Override
    public void start() {
        mObjectAnimator.start();
    }

    @Override
    public void setCurrentFraction(float fraction) {
        mObjectAnimator.setCurrentFraction(fraction);
    }

    @Override
    public void setCurrentPlayTime(long playTime) {
        mObjectAnimator.setCurrentPlayTime(playTime);
    }

    public static class SpringProperty<T extends ProgressInterface> extends Property<T, Float> {

        boolean useSpring = false;
        final SpringAnimation mSpring;

        public SpringProperty(String name, SpringAnimation spring) {
            super(Float.class, name);
            mSpring = spring;
        }

        public void switchToSpring() {
            useSpring = true;
        }

        @Override
        public Float get(T object) {
            return object.getProgress();
        }

        @Override
        public void set(T object, Float progress) {
            if (useSpring) {
                mSpring.animateToFinalPosition(progress);
            } else {
                object.setProgress(progress);
            }
        }
    }
}
