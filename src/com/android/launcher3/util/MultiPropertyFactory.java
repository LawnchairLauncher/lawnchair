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

package com.android.launcher3.util;

import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.util.FloatProperty;
import android.util.Log;

import java.io.PrintWriter;
import java.util.Arrays;

/**
 * Allows to combine multiple values set by several sources.
 *
 * The various sources are meant to use [set], providing different `setterIndex` params. When it is
 * not set, 0 is used. This is meant to cover the case multiple animations are going on at the same
 * time.
 *
 * This class behaves similarly to [MultiValueAlpha], but is meant to be more abstract and reusable.
 * It aggregate all values using the provided [aggregator].
 *
 * @param <T> Type where to apply the property.
 */
public class MultiPropertyFactory<T> {

    public static final FloatProperty<MultiPropertyFactory<?>.MultiProperty> MULTI_PROPERTY_VALUE =
            new FloatProperty<MultiPropertyFactory<?>.MultiProperty>("value") {

                @Override
                public Float get(MultiPropertyFactory<?>.MultiProperty property) {
                    return property.mValue;
                }

                @Override
                public void setValue(MultiPropertyFactory<?>.MultiProperty property, float value) {
                    property.setValue(value);
                }
            };

    private static final boolean DEBUG = false;
    private static final String TAG = "MultiPropertyFactory";
    private final MultiPropertyFactory<?>.MultiProperty[] mProperties;

    // This is an optimization for cases when set is called repeatedly with the same setterIndex.
    private float mAggregationOfOthers = 0f;
    private int mLastIndexSet = -1;

    protected final T mTarget;
    private final FloatProperty<T> mProperty;
    private final FloatBiFunction mAggregator;

    /**
     * Represents a function that accepts two float and produces a float.
     */
    public interface FloatBiFunction {
        /**
         * Applies this function to the given arguments.
         */
        float apply(float a, float b);
    }

    public MultiPropertyFactory(T target, FloatProperty<T> property, int size,
            FloatBiFunction aggregator) {
        this(target, property, size, aggregator, 0);
    }

    public MultiPropertyFactory(T target, FloatProperty<T> property, int size,
            FloatBiFunction aggregator, float defaultPropertyValue) {
        mTarget = target;
        mProperty = property;
        mAggregator = aggregator;

        mProperties = new MultiPropertyFactory<?>.MultiProperty[size];
        for (int i = 0; i < size; i++) {
            mProperties[i] = new MultiProperty(i, defaultPropertyValue);
        }
    }

    /** Returns the [MultiFloatProperty] associated with [inx], creating it if not present. */
    public MultiProperty get(int index) {
        return (MultiProperty) mProperties[index];
    }

    @Override
    public String toString() {
        return Arrays.deepToString(mProperties);
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
            if (i >= mProperties.length) {
                pw.println(innerPrefix + alphaIndexLabels[i] + " given for alpha index " + i
                        + " however there are only " + mProperties.length + " alpha channels.");
                continue;
            }
            pw.println(innerPrefix + alphaIndexLabels[i] + "=" + get(i).getValue());
        }
    }

    /**
     * Each [setValue] will be aggregated with the other properties values created by the
     * corresponding factory.
     */
    public class MultiProperty {

        private final int mInx;
        private final float mDefaultValue;
        private float mValue;

        MultiProperty(int inx, float defaultValue) {
            mInx = inx;
            mDefaultValue = defaultValue;
            mValue = defaultValue;
        }

        public void setValue(float newValue) {
            if (mLastIndexSet != mInx) {
                mAggregationOfOthers = mDefaultValue;
                for (MultiPropertyFactory<?>.MultiProperty other : mProperties) {
                    if (other.mInx != mInx) {
                        mAggregationOfOthers =
                                mAggregator.apply(mAggregationOfOthers, other.mValue);
                    }
                }

                mLastIndexSet = mInx;
            }
            float lastAggregatedValue = mAggregator.apply(mAggregationOfOthers, newValue);
            mValue = newValue;
            apply(lastAggregatedValue);

            if (DEBUG) {
                Log.d(TAG, "name=" + mProperty.getName()
                        + " target=" + mTarget.getClass()
                        + " newValue=" + newValue
                        + " mInx=" + mInx
                        + " aggregated=" + lastAggregatedValue
                        + " others= " + Arrays.deepToString(mProperties));
            }
        }

        public float getValue() {
            return mValue;
        }

        @Override
        public String toString() {
            return String.valueOf(mValue);
        }

        /**
         * Creates and returns an Animator from the current value to the given value. Future
         * animator on the same target automatically cancels the previous one.
         */
        public Animator animateToValue(float value) {
            ObjectAnimator animator = ObjectAnimator.ofFloat(this, MULTI_PROPERTY_VALUE, value);
            animator.setAutoCancel(true);
            return animator;
        }
    }

    protected void apply(float value) {
        mProperty.set(mTarget, value);
    }
}
