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

import android.util.ArrayMap;
import android.util.FloatProperty;
import android.util.Log;
import android.util.Property;

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

    private static final boolean DEBUG = false;
    private static final String TAG = "MultiPropertyFactory";
    private final String mName;
    private final ArrayMap<Integer, MultiProperty> mProperties = new ArrayMap<>();

    // This is an optimization for cases when set is called repeatedly with the same setterIndex.
    private float mAggregationOfOthers = 0f;
    private Integer mLastIndexSet = -1;
    private final Property<T, Float> mProperty;
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

    public MultiPropertyFactory(String name, Property<T, Float> property,
            FloatBiFunction aggregator) {
        mName = name;
        mProperty = property;
        mAggregator = aggregator;
    }

    /** Returns the [MultiFloatProperty] associated with [inx], creating it if not present. */
    public MultiProperty get(Integer index) {
        return mProperties.computeIfAbsent(index,
                (k) -> new MultiProperty(index, mName + "_" + index));
    }

    /**
     * Each [setValue] will be aggregated with the other properties values created by the
     * corresponding factory.
     */
    class MultiProperty extends FloatProperty<T> {
        private final int mInx;
        private float mValue = 0f;

        MultiProperty(int inx, String name) {
            super(name);
            mInx = inx;
        }

        @Override
        public void setValue(T obj, float newValue) {
            if (mLastIndexSet != mInx) {
                mAggregationOfOthers = 0f;
                mProperties.forEach((key, property) -> {
                    if (key != mInx) {
                        mAggregationOfOthers =
                                mAggregator.apply(mAggregationOfOthers, property.mValue);
                    }
                });
                mLastIndexSet = mInx;
            }
            float lastAggregatedValue = mAggregator.apply(mAggregationOfOthers, newValue);
            mValue = newValue;
            apply(obj, lastAggregatedValue);

            if (DEBUG) {
                Log.d(TAG, "name=" + mName
                        + " newValue=" + newValue + " mInx=" + mInx
                        + " aggregated=" + lastAggregatedValue + " others= " + mProperties);
            }
        }

        @Override
        public Float get(T object) {
            // The scale of the view should match mLastAggregatedValue. Still, if it has been
            // changed without using this property, it can differ. As this get method is usually
            // used to set the starting point on an animation, this would result in some jumps
            // when the view scale is different than the last aggregated value. To stay on the
            // safe side, let's return the real view scale.
            return mProperty.get(object);
        }

        @Override
        public String toString() {
            return String.valueOf(mValue);
        }
    }

    protected void apply(T object, float value) {
        mProperty.set(object, value);
    }
}
