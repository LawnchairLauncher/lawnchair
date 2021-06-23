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
import android.animation.Animator.AnimatorListener;
import android.animation.AnimatorListenerAdapter;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.util.FloatProperty;
import android.util.IntProperty;
import android.view.View;
import android.view.ViewGroup.LayoutParams;

public class LauncherAnimUtils {
    /**
     * Durations for various state animations. These are not defined in resources to allow
     * easier access from static classes and enums
     */
    public static final int SPRING_LOADED_EXIT_DELAY = 500;

    // Progress after which the transition is assumed to be a success
    public static final float SUCCESS_TRANSITION_PROGRESS = 0.5f;

    public static final IntProperty<Drawable> DRAWABLE_ALPHA =
            new IntProperty<Drawable>("drawableAlpha") {
                @Override
                public Integer get(Drawable drawable) {
                    return drawable.getAlpha();
                }

                @Override
                public void setValue(Drawable drawable, int alpha) {
                    drawable.setAlpha(alpha);
                }
            };

    public static final FloatProperty<View> SCALE_PROPERTY =
            new FloatProperty<View>("scale") {
                @Override
                public Float get(View view) {
                    return view.getScaleX();
                }

                @Override
                public void setValue(View view, float scale) {
                    view.setScaleX(scale);
                    view.setScaleY(scale);
                }
            };

    /** Increase the duration if we prevented the fling, as we are going against a high velocity. */
    public static int blockedFlingDurationFactor(float velocity) {
        return (int) Utilities.boundToRange(Math.abs(velocity) / 2, 2f, 6f);
    }

    public static final IntProperty<LayoutParams> LAYOUT_WIDTH =
            new IntProperty<LayoutParams>("width") {
                @Override
                public Integer get(LayoutParams lp) {
                    return lp.width;
                }

                @Override
                public void setValue(LayoutParams lp, int width) {
                    lp.width = width;
                }
            };

    public static final IntProperty<LayoutParams> LAYOUT_HEIGHT =
            new IntProperty<LayoutParams>("height") {
                @Override
                public Integer get(LayoutParams lp) {
                    return lp.height;
                }

                @Override
                public void setValue(LayoutParams lp, int height) {
                    lp.height = height;
                }
            };

    public static final FloatProperty<View> VIEW_TRANSLATE_X =
            View.TRANSLATION_X instanceof FloatProperty ? (FloatProperty) View.TRANSLATION_X
                    : new FloatProperty<View>("translateX") {
                        @Override
                        public void setValue(View view, float v) {
                            view.setTranslationX(v);
                        }

                        @Override
                        public Float get(View view) {
                            return view.getTranslationX();
                        }
                    };

    public static final FloatProperty<View> VIEW_TRANSLATE_Y =
            View.TRANSLATION_Y instanceof FloatProperty ? (FloatProperty) View.TRANSLATION_Y
                    : new FloatProperty<View>("translateY") {
                        @Override
                        public void setValue(View view, float v) {
                            view.setTranslationY(v);
                        }

                        @Override
                        public Float get(View view) {
                            return view.getTranslationY();
                        }
                    };

    public static final FloatProperty<View> VIEW_ALPHA =
            View.ALPHA instanceof FloatProperty ? (FloatProperty) View.ALPHA
                    : new FloatProperty<View>("alpha") {
                        @Override
                        public void setValue(View view, float v) {
                            view.setAlpha(v);
                        }

                        @Override
                        public Float get(View view) {
                            return view.getAlpha();
                        }
                    };

    public static final IntProperty<View> VIEW_BACKGROUND_COLOR =
            new IntProperty<View>("backgroundColor") {
                @Override
                public void setValue(View view, int color) {
                    view.setBackgroundColor(color);
                }

                @Override
                public Integer get(View view) {
                    if (!(view.getBackground() instanceof ColorDrawable)) {
                        return Color.TRANSPARENT;
                    }
                    return ((ColorDrawable) view.getBackground()).getColor();
                }
            };

    /**
     * Utility method to create an {@link AnimatorListener} which executes a callback on animation
     * cancel.
     */
    public static AnimatorListener newCancelListener(Runnable callback) {
        return new AnimatorListenerAdapter() {

            boolean mDispatched = false;

            @Override
            public void onAnimationCancel(Animator animation) {
                if (!mDispatched) {
                    mDispatched = true;
                    callback.run();
                }
            }
        };
    }
}
