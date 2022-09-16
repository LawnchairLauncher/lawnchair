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
package com.android.quickstep.util;

import android.graphics.Matrix;
import android.graphics.Rect;

/**
 * Extension for {@link SurfaceTransaction} which records the commands for mocking
 */
public class RecordingSurfaceTransaction extends SurfaceTransaction {

    /**
     * A mock builder which can be used for recording values
     */
    public final MockProperties mockProperties = new MockProperties();

    /**
     * Extension of {@link SurfaceProperties} which just stores all the values locally
     */
    public class MockProperties extends SurfaceProperties {

        public float alpha = -1;
        public Matrix matrix = null;
        public Rect windowCrop = null;
        public float cornerRadius = 0;
        public float shadowRadius = 0;

        MockProperties() {
            super(null);
        }

        @Override
        public SurfaceProperties setAlpha(float alpha) {
            this.alpha = alpha;
            return this;
        }

        @Override
        public SurfaceProperties setMatrix(Matrix matrix) {
            this.matrix = matrix;
            return this;
        }

        @Override
        public SurfaceProperties setWindowCrop(Rect windowCrop) {
            this.windowCrop = windowCrop;
            return this;
        }

        @Override
        public SurfaceProperties setLayer(int relativeLayer) {
            return this;
        }

        @Override
        public SurfaceProperties setCornerRadius(float radius) {
            this.cornerRadius = radius;
            return this;
        }

        @Override
        public SurfaceProperties setShadowRadius(float radius) {
            this.shadowRadius = radius;
            return this;
        }
    }
}
