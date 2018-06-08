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
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.graphics.drawable.Drawable;
import android.util.Property;
import android.view.View;
import android.view.ViewTreeObserver;

import java.util.HashSet;
import java.util.WeakHashMap;

public class LauncherAnimUtils {
    /**
     * Durations for various state animations. These are not defined in resources to allow
     * easier access from static classes and enums
     */
    public static final int ALL_APPS_TRANSITION_MS = 320;
    public static final int OVERVIEW_TRANSITION_MS = 250;
    public static final int SPRING_LOADED_TRANSITION_MS = 150;
    public static final int SPRING_LOADED_EXIT_DELAY = 500;

    // The progress of an animation to all apps must be at least this far along to snap to all apps.
    public static final float MIN_PROGRESS_TO_ALL_APPS = 0.5f;

    static WeakHashMap<Animator, Object> sAnimators = new WeakHashMap<Animator, Object>();
    static Animator.AnimatorListener sEndAnimListener = new Animator.AnimatorListener() {
        public void onAnimationStart(Animator animation) {
            sAnimators.put(animation, null);
        }

        public void onAnimationRepeat(Animator animation) {
        }

        public void onAnimationEnd(Animator animation) {
            sAnimators.remove(animation);
        }

        public void onAnimationCancel(Animator animation) {
            sAnimators.remove(animation);
        }
    };

    public static void cancelOnDestroyActivity(Animator a) {
        a.addListener(sEndAnimListener);
    }

    // Helper method. Assumes a draw is pending, and that if the animation's duration is 0
    // it should be cancelled
    public static void startAnimationAfterNextDraw(final Animator animator, final View view) {
        view.getViewTreeObserver().addOnDrawListener(new ViewTreeObserver.OnDrawListener() {
            private boolean mStarted = false;

            public void onDraw() {
                if (mStarted) return;
                mStarted = true;
                // Use this as a signal that the animation was cancelled
                if (animator.getDuration() == 0) {
                    return;
                }
                animator.start();

                final ViewTreeObserver.OnDrawListener listener = this;
                view.post(new Runnable() {
                    public void run() {
                        view.getViewTreeObserver().removeOnDrawListener(listener);
                    }
                });
            }
        });
    }

    public static void onDestroyActivity() {
        HashSet<Animator> animators = new HashSet<Animator>(sAnimators.keySet());
        for (Animator a : animators) {
            if (a.isRunning()) {
                a.cancel();
            }
            sAnimators.remove(a);
        }
    }

    public static AnimatorSet createAnimatorSet() {
        AnimatorSet anim = new AnimatorSet();
        cancelOnDestroyActivity(anim);
        return anim;
    }

    public static ValueAnimator ofFloat(float... values) {
        ValueAnimator anim = new ValueAnimator();
        anim.setFloatValues(values);
        cancelOnDestroyActivity(anim);
        return anim;
    }

    public static ObjectAnimator ofFloat(View target, Property<View, Float> property,
            float... values) {
        ObjectAnimator anim = ObjectAnimator.ofFloat(target, property, values);
        cancelOnDestroyActivity(anim);
        new FirstFrameAnimatorHelper(anim, target);
        return anim;
    }

    public static ObjectAnimator ofViewAlphaAndScale(View target,
            float alpha, float scaleX, float scaleY) {
        return ofPropertyValuesHolder(target,
                PropertyValuesHolder.ofFloat(View.ALPHA, alpha),
                PropertyValuesHolder.ofFloat(View.SCALE_X, scaleX),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, scaleY));
    }

    public static ObjectAnimator ofPropertyValuesHolder(View target,
            PropertyValuesHolder... values) {
        return ofPropertyValuesHolder(target, target, values);
    }

    public static ObjectAnimator ofPropertyValuesHolder(Object target,
            View view, PropertyValuesHolder... values) {
        ObjectAnimator anim = ObjectAnimator.ofPropertyValuesHolder(target, values);
        cancelOnDestroyActivity(anim);
        new FirstFrameAnimatorHelper(anim, view);
        return anim;
    }

    public static final Property<Drawable, Integer> DRAWABLE_ALPHA =
            new Property<Drawable, Integer>(Integer.TYPE, "drawableAlpha") {
                @Override
                public Integer get(Drawable drawable) {
                    return drawable.getAlpha();
                }

                @Override
                public void set(Drawable drawable, Integer alpha) {
                    drawable.setAlpha(alpha);
                }
            };

    public static final Property<View, Float> SCALE_PROPERTY =
            new Property<View, Float>(Float.class, "scale") {
                @Override
                public Float get(View view) {
                    return view.getScaleX();
                }

                @Override
                public void set(View view, Float scale) {
                    view.setScaleX(scale);
                    view.setScaleY(scale);
                }
            };

    /** Increase the duration if we prevented the fling, as we are going against a high velocity. */
    public static int blockedFlingDurationFactor(float velocity) {
        return (int) Utilities.boundToRange(Math.abs(velocity) / 2, 2f, 6f);
    }
}
