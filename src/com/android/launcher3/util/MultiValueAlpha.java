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

package com.android.launcher3.util;

import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.util.FloatProperty;
import android.view.View;

import com.android.launcher3.anim.AlphaUpdateListener;

import java.io.PrintWriter;
import java.util.Arrays;
import java.util.function.Consumer;

/**
 * Utility class to handle separating a single value as a factor of multiple values
 */
public class MultiValueAlpha {

    public static final FloatProperty<AlphaProperty> VALUE =
            new FloatProperty<AlphaProperty>("value") {

                @Override
                public Float get(AlphaProperty alphaProperty) {
                    return alphaProperty.mValue;
                }

                @Override
                public void setValue(AlphaProperty object, float value) {
                    object.setValue(value);
                }
            };

    private final View mView;
    private final AlphaProperty[] mMyProperties;

    private int mValidMask;
    // Whether we should change from INVISIBLE to VISIBLE and vice versa at low alpha values.
    private boolean mUpdateVisibility;

    public MultiValueAlpha(View view, int size) {
        mView = view;
        mMyProperties = new AlphaProperty[size];

        mValidMask = 0;
        for (int i = 0; i < size; i++) {
            int myMask = 1 << i;
            mValidMask |= myMask;
            mMyProperties[i] = new AlphaProperty(myMask);
        }
    }

    @Override
    public String toString() {
        return Arrays.toString(mMyProperties);
    }

    public AlphaProperty getProperty(int index) {
        return mMyProperties[index];
    }

    /** Sets whether we should update between INVISIBLE and VISIBLE based on alpha. */
    public void setUpdateVisibility(boolean updateVisibility) {
        mUpdateVisibility = updateVisibility;
    }

    /**
     * Dumps the alpha channel values to the given PrintWriter
     *
     * @param prefix String to be used before every line
     * @param pw PrintWriter where the logs should be dumped
     * @param label String used to help identify this object
     * @param alphaIndexLabels Strings that represent each alpha channel, these should be entered
     *                         in the order of the indexes they represent, starting from 0.
     */
    public void dump(String prefix, PrintWriter pw, String label, String... alphaIndexLabels) {
        pw.println(prefix + label);

        String innerPrefix = prefix + '\t';
        for (int i = 0; i < alphaIndexLabels.length; i++) {
            if (i >= mMyProperties.length) {
                pw.println(innerPrefix + alphaIndexLabels[i] + " given for alpha index " + i
                        + " however there are only " + mMyProperties.length + " alpha channels.");
                continue;
            }
            pw.println(innerPrefix + alphaIndexLabels[i] + "=" + getProperty(i).getValue());
        }
    }

    public class AlphaProperty {

        private final int mMyMask;

        private float mValue = 1;
        // Factor of all other alpha channels, only valid if mMyMask is present in mValidMask.
        private float mOthers = 1;

        private Consumer<Float> mConsumer;

        AlphaProperty(int myMask) {
            mMyMask = myMask;
        }

        public void setValue(float value) {
            if (mValue == value) {
                return;
            }

            if ((mValidMask & mMyMask) == 0) {
                // Our cache value is not correct, recompute it.
                mOthers = 1;
                for (AlphaProperty prop : mMyProperties) {
                    if (prop != this) {
                        mOthers *= prop.mValue;
                    }
                }
            }

            // Since we have changed our value, all other caches except our own need to be
            // recomputed. Change mValidMask to indicate the new valid caches (only our own).
            mValidMask = mMyMask;
            mValue = value;

            final float alpha = mOthers * mValue;
            mView.setAlpha(alpha);
            if (mUpdateVisibility) {
                AlphaUpdateListener.updateVisibility(mView);
            }
            if (mConsumer != null) {
                mConsumer.accept(mValue);
            }
        }

        public float getValue() {
            return mValue;
        }

        public void setConsumer(Consumer<Float> consumer) {
            mConsumer = consumer;
            if (mConsumer != null) {
                mConsumer.accept(mValue);
            }
        }

        @Override
        public String toString() {
            return Float.toString(mValue);
        }

        /**
         * Creates and returns an Animator from the current value to the given value. Future
         * animator on the same target automatically cancels the previous one.
         */
        public Animator animateToValue(float value) {
            ObjectAnimator animator = ObjectAnimator.ofFloat(this, VALUE, value);
            animator.setAutoCancel(true);
            return animator;
        }
    }
}
