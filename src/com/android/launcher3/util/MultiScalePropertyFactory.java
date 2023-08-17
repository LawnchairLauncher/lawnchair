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
import android.view.View;

import com.android.launcher3.Utilities;

/**
 * Allows to combine multiple values set by several sources.
 *
 * The various sources are meant to use [set], providing different `setterIndex` params. When it is
 * not set, 0 is used. This is meant to cover the case multiple animations are going on at the same
 * time.
 *
 * This class behaves similarly to [MultiValueAlpha], but is meant to be more abstract and reusable.
 * It sets the multiplication of all values, bounded to the max and the min values.
 *
 * @param <T> Type where to apply the property.
 */
public class MultiScalePropertyFactory<T extends View> {

    private static final boolean DEBUG = false;
    private static final String TAG = "MultiScaleProperty";
    private final String mName;
    private final ArrayMap<Integer, MultiScaleProperty> mProperties = new ArrayMap<>();

    // This is an optimization for cases when set is called repeatedly with the same setterIndex.
    private float mMinOfOthers = 0;
    private float mMaxOfOthers = 0;
    private float mMultiplicationOfOthers = 0;
    private Integer mLastIndexSet = -1;
    private float mLastAggregatedValue = 1.0f;

    public MultiScalePropertyFactory(String name) {
        mName = name;
    }

    /** Returns the [MultiFloatProperty] associated with [inx], creating it if not present. */
    public FloatProperty<T> get(Integer index) {
        return mProperties.computeIfAbsent(index,
                (k) -> new MultiScaleProperty(index, mName + "_" + index));
    }

    /**
     * Each [setValue] will be aggregated with the other properties values created by the
     * corresponding factory.
     */
    class MultiScaleProperty extends FloatProperty<T> {
        private final int mInx;
        private float mValue = 1.0f;

        MultiScaleProperty(int inx, String name) {
            super(name);
            mInx = inx;
        }

        @Override
        public void setValue(T obj, float newValue) {
            if (mLastIndexSet != mInx) {
                mMinOfOthers = Float.MAX_VALUE;
                mMaxOfOthers = Float.MIN_VALUE;
                mMultiplicationOfOthers = 1.0f;
                mProperties.forEach((key, property) -> {
                    if (key != mInx) {
                        mMinOfOthers = Math.min(mMinOfOthers, property.mValue);
                        mMaxOfOthers = Math.max(mMaxOfOthers, property.mValue);
                        mMultiplicationOfOthers *= property.mValue;
                    }
                });
                mLastIndexSet = mInx;
            }
            float minValue = Math.min(mMinOfOthers, newValue);
            float maxValue = Math.max(mMaxOfOthers, newValue);
            float multValue = mMultiplicationOfOthers * newValue;
            mLastAggregatedValue = Utilities.boundToRange(multValue, minValue, maxValue);
            mValue = newValue;
            apply(obj, mLastAggregatedValue);

            if (DEBUG) {
                Log.d(TAG, "name=" + mName
                        + " newValue=" + newValue + " mInx=" + mInx
                        + " aggregated=" + mLastAggregatedValue + " others= " + mProperties);
            }
        }

        @Override
        public Float get(T view) {
            // The scale of the view should match mLastAggregatedValue. Still, if it has been
            // changed without using this property, it can differ. As this get method is usually
            // used to set the starting point on an animation, this would result in some jumps
            // when the view scale is different than the last aggregated value. To stay on the
            // safe side, let's return the real view scale.
            return view.getScaleX();
        }

        @Override
        public String toString() {
            return String.valueOf(mValue);
        }
    }

    protected void apply(View view, float value) {
        view.setScaleX(value);
        view.setScaleY(value);
    }
}
