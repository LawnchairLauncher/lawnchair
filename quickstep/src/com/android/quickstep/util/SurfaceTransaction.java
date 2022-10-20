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
import android.view.SurfaceControl;
import android.view.SurfaceControl.Transaction;

/**
 * Helper class for building a {@link Transaction}.
 */
public class SurfaceTransaction {

    private final Transaction mTransaction = new Transaction();
    private final float[] mTmpValues = new float[9];

    /**
     * Creates a new builder for the provided surface
     */
    public SurfaceProperties forSurface(SurfaceControl surface) {
        return surface.isValid() ? new SurfaceProperties(surface) : new MockProperties();
    }

    /**
     * Returns the final transaction
     */
    public Transaction getTransaction() {
        return mTransaction;
    }

    /**
     * Utility class to update surface params in a transaction
     */
    public class SurfaceProperties {

        private final SurfaceControl mSurface;

        SurfaceProperties(SurfaceControl surface) {
            mSurface = surface;
        }

        /**
         * @param alpha The alpha value to apply to the surface.
         * @return this Builder
         */
        public SurfaceProperties setAlpha(float alpha) {
            mTransaction.setAlpha(mSurface, alpha);
            return this;
        }

        /**
         * @param matrix The matrix to apply to the surface.
         * @return this Builder
         */
        public SurfaceProperties setMatrix(Matrix matrix) {
            mTransaction.setMatrix(mSurface, matrix, mTmpValues);
            return this;
        }

        /**
         * @param windowCrop The window crop to apply to the surface.
         * @return this Builder
         */
        public SurfaceProperties setWindowCrop(Rect windowCrop) {
            mTransaction.setWindowCrop(mSurface, windowCrop);
            return this;
        }

        /**
         * @param relativeLayer The relative layer.
         * @return this Builder
         */
        public SurfaceProperties setLayer(int relativeLayer) {
            mTransaction.setLayer(mSurface, relativeLayer);
            return this;
        }

        /**
         * @param radius the Radius for rounded corners to apply to the surface.
         * @return this Builder
         */
        public SurfaceProperties setCornerRadius(float radius) {
            mTransaction.setCornerRadius(mSurface, radius);
            return this;
        }

        /**
         * @param radius the Radius for the shadows to apply to the surface.
         * @return this Builder
         */
        public SurfaceProperties setShadowRadius(float radius) {
            mTransaction.setShadowRadius(mSurface, radius);
            return this;
        }
    }

    /**
     * Extension of {@link SurfaceProperties} which just stores all the values locally
     */
    public class MockProperties extends SurfaceProperties {

        public float alpha = -1;
        public Matrix matrix = null;
        public Rect windowCrop = null;
        public float cornerRadius = 0;
        public float shadowRadius = 0;

        protected MockProperties() {
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
