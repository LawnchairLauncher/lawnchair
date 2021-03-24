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

import android.util.FloatProperty;
import android.view.View;

import com.android.launcher3.anim.AlphaUpdateListener;

import java.util.Arrays;

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

    public class AlphaProperty {

        private final int mMyMask;

        private float mValue = 1;
        // Factor of all other alpha channels, only valid if mMyMask is present in mValidMask.
        private float mOthers = 1;

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

            mView.setAlpha(mOthers * mValue);
            if (mUpdateVisibility) {
                AlphaUpdateListener.updateVisibility(mView);
            }
        }

        public float getValue() {
            return mValue;
        }

        @Override
        public String toString() {
            return Float.toString(mValue);
        }
    }
}
