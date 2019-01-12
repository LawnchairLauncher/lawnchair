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

package ch.deletescape.lawnchair.util;

import android.support.v4.util.Consumer;
import android.util.Property;
import android.view.View;

/**
 * Utility class to handle separating a single value as a factor of multiple values
 */
public class InvertedMultiValueAlpha {

    public static final Property<InvertedAlphaProperty, Float> VALUE =
            new Property<InvertedAlphaProperty, Float>(Float.TYPE, "value") {

                @Override
                public Float get(InvertedAlphaProperty alphaProperty) {
                    return 1 - alphaProperty.mValue;
                }

                @Override
                public void set(InvertedAlphaProperty object, Float value) {
                    object.setValue(value);
                }
            };

    private final Consumer<Float> mConsumer;
    private final InvertedAlphaProperty[] mMyProperties;

    private int mValidMask;

    public InvertedMultiValueAlpha(Consumer<Float> consumer, int size) {
        mConsumer = consumer;
        mMyProperties = new InvertedAlphaProperty[size];

        mValidMask = 0;
        for (int i = 0; i < size; i++) {
            int myMask = 1 << i;
            mValidMask |= myMask;
            mMyProperties[i] = new InvertedAlphaProperty(myMask);
        }
    }

    public InvertedAlphaProperty getProperty(int index) {
        return mMyProperties[index];
    }

    public class InvertedAlphaProperty {

        private final int mMyMask;

        private float mValue = 1;
        // Factor of all other alpha channels, only valid if mMyMask is present in mValidMask.
        private float mOthers = 1;

        InvertedAlphaProperty(int myMask) {
            mMyMask = myMask;
        }

        public void setValue(float value) {
            value = 1 - value;
            if (mValue == value) {
                return;
            }

            if ((mValidMask & mMyMask) == 0) {
                // Our cache value is not correct, recompute it.
                mOthers = 1;
                for (InvertedAlphaProperty prop : mMyProperties) {
                    if (prop != this) {
                        mOthers *= prop.mValue;
                    }
                }
            }

            // Since we have changed our value, all other caches except our own need to be
            // recomputed. Change mValidMask to indicate the new valid caches (only our own).
            mValidMask = mMyMask;
            mValue = value;

            mConsumer.accept(1 - mOthers * mValue);
        }

        public float getValue() {
            return 1 - mValue;
        }
    }
}
