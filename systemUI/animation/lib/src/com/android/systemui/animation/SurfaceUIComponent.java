/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.systemui.animation;

import android.annotation.Nullable;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.SurfaceControl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.Executor;

/**
 * A {@link UIComponent} representing a {@link SurfaceControl}.
 * @hide
 */
public class SurfaceUIComponent implements UIComponent {
    private final ArrayList<SurfaceControl> mSurfaces = new ArrayList<>();
    private final RectF mBaseBounds;
    private final float[] mFloat9 = new float[9];

    private float mAlpha;
    private boolean mVisible;
    private RectF mBounds;
    @Nullable
    private final SurfaceControl mBackgroundDimmingSurface;

    public SurfaceUIComponent(
            SurfaceControl sc,
            float alpha,
            boolean visible,
            Rect bounds,
            Rect baseBounds,
            boolean enableBackgroundDimming) {
        this(Arrays.asList(sc),
                alpha,
                visible,
                bounds,
                baseBounds,
                enableBackgroundDimming);
    }

    public SurfaceUIComponent(
            Collection<SurfaceControl> surfaces,
            float alpha,
            boolean visible,
            Rect bounds,
            Rect baseBounds,
            boolean enableBackgroundDimming) {
        mSurfaces.addAll(surfaces);
        mAlpha = alpha;
        mVisible = visible;
        mBounds = new RectF(bounds);
        mBaseBounds = new RectF(baseBounds);
        if (enableBackgroundDimming) {
          mBackgroundDimmingSurface = new SurfaceControl.Builder()
                  .setName("SurfaceUIComponent-BackgroundDimming")
                  .setColorLayer()
                  .setBufferSize(baseBounds.width(), baseBounds.height())
                  .setHidden(!visible)
                  .build();
          if (!bounds.equals(baseBounds)) {
              Matrix matrix = new Matrix();
              matrix.setRectToRect(
                      mBaseBounds,
                      mBounds,
                      Matrix.ScaleToFit.CENTER);
              new SurfaceControl.Transaction()
                  .setMatrix(mBackgroundDimmingSurface, matrix, mFloat9).apply();
          }

            mSurfaces.add(mBackgroundDimmingSurface);
        } else {
            mBackgroundDimmingSurface = null;
        }
    }

    @Override
    public float getAlpha() {
        return mAlpha;
    }

    @Override
    public boolean isVisible() {
        return mVisible;
    }

    @Override
    public RectF getBounds() {
        return mBounds;
    }

    @Override
    public Transaction newTransaction() {
        return new Transaction(new SurfaceControl.Transaction());
    }

    @Override
    public String toString() {
        return "SurfaceUIComponent{mSurfaces="
                + mSurfaces
                + ", mAlpha="
                + mAlpha
                + ", mVisible="
                + mVisible
                + ", mBounds="
                + mBounds
                + ", mBaseBounds="
                + mBaseBounds
                + "}";
    }

    /**
     * A {@link Transaction} wrapping a {@link SurfaceControl.Transaction}.
     * @hide
     */
    public static class Transaction implements UIComponent.Transaction<SurfaceUIComponent> {
        private final SurfaceControl.Transaction mTransaction;
        private final ArrayList<Runnable> mChanges = new ArrayList<>();

        public Transaction(SurfaceControl.Transaction transaction) {
            mTransaction = transaction;
        }

        @Override
        public Transaction setAlpha(SurfaceUIComponent ui, float alpha) {
            mChanges.add(
                    () -> {
                        ui.mAlpha = alpha;
                        ui.mSurfaces.forEach(
                                s -> mTransaction.setAlpha(
                                        s, s != ui.mBackgroundDimmingSurface ? alpha : 1));
                    });
            return this;
        }

        @Override
        public Transaction setVisible(SurfaceUIComponent ui, boolean visible) {
            mChanges.add(
                    () -> {
                        ui.mVisible = visible;
                        if (visible) {
                            ui.mSurfaces.forEach(s -> mTransaction.show(s));
                        } else {
                            ui.mSurfaces.forEach(s -> mTransaction.hide(s));
                        }
                    });
            return this;
        }

        @Override
        public Transaction setBounds(SurfaceUIComponent ui, RectF bounds) {
            mChanges.add(
                    () -> {
                        if (ui.mBounds.equals(bounds)) {
                            return;
                        }
                        ui.mBounds = bounds;
                        Matrix matrix = new Matrix();
                        matrix.setRectToRect(
                                ui.mBaseBounds,
                                ui.mBounds,
                                Matrix.ScaleToFit.CENTER);
                        ui.mSurfaces.forEach(
                                s -> mTransaction.setMatrix(s, matrix, ui.mFloat9));
                    });
            return this;
        }

        @Override
        public Transaction attachToTransitionLeash(
                SurfaceUIComponent ui, SurfaceControl transitionLeash, int w, int h) {
            mChanges.add(
                    () -> {
                        ui.mSurfaces.forEach(
                                s -> mTransaction.reparent(s, transitionLeash));
                    });
            return this;
        }

        @Override
        public Transaction detachFromTransitionLeash(
                SurfaceUIComponent ui, Executor executor, Runnable onDone) {
            mChanges.add(
                    () -> {
                        ui.mSurfaces.forEach(
                                s -> mTransaction.reparent(s, null));
                        mTransaction.addTransactionCommittedListener(executor, onDone::run);
                    });
            return this;
        }

        @Override
        public void commit() {
            mChanges.forEach(Runnable::run);
            mChanges.clear();
            mTransaction.apply();
        }
    }
}
