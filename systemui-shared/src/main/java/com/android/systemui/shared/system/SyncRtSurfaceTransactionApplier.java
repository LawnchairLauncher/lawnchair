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
 * limitations under the License
 */

package com.android.systemui.shared.system;

import android.graphics.Matrix;
import android.graphics.Rect;
import android.view.Surface;
import android.view.SurfaceControl;
import android.view.SurfaceControl.Transaction;
import android.view.View;
import android.view.ViewRootImpl;
import android.view.ThreadedRenderer.FrameDrawingCallback;

/**
 * Helper class to apply surface transactions in sync with RenderThread.
 */
public class SyncRtSurfaceTransactionApplier {

    private final Surface mTargetSurface;
    private final ViewRootImpl mTargetViewRootImpl;
    private final float[] mTmpFloat9 = new float[9];

    /**
     * @param targetView The view in the surface that acts as synchronization anchor.
     */
    public SyncRtSurfaceTransactionApplier(View targetView) {
        mTargetViewRootImpl = targetView != null ? targetView.getViewRootImpl() : null;
        mTargetSurface = mTargetViewRootImpl != null ? mTargetViewRootImpl.mSurface : null;
    }

    /**
     * Schedules applying surface parameters on the next frame.
     *
     * @param params The surface parameters to apply. DO NOT MODIFY the list after passing into
     *               this method to avoid synchronization issues.
     */
    public void scheduleApply(SurfaceParams... params) {
        if (mTargetViewRootImpl == null) {
            return;
        }
        mTargetViewRootImpl.registerRtFrameCallback(new FrameDrawingCallback() {
            public void onFrameDraw(long frame) {
                if (mTargetSurface == null || !mTargetSurface.isValid()) {
                    return;
                }
                Transaction t = new Transaction();
                for (int i = params.length - 1; i >= 0; i--) {
                    SurfaceParams surfaceParams = params[i];
                    SurfaceControl surface = surfaceParams.surface;
                    t.deferTransactionUntilSurface(surface, mTargetSurface, frame);
                    applyParams(t, surfaceParams, mTmpFloat9);
                }
                t.setEarlyWakeup();
                t.apply();
            }
        });

        // Make sure a frame gets scheduled.
        mTargetViewRootImpl.getView().invalidate();
    }

    public static void applyParams(TransactionCompat t, SurfaceParams params) {
        applyParams(t.mTransaction, params, t.mTmpValues);
    }

    private static void applyParams(Transaction t, SurfaceParams params, float[] tmpFloat9) {
        t.setMatrix(params.surface, params.matrix, tmpFloat9);
        t.setWindowCrop(params.surface, params.windowCrop);
        t.setAlpha(params.surface, params.alpha);
        t.setLayer(params.surface, params.layer);
        t.show(params.surface);
    }

    public static class SurfaceParams {

        /**
         * Constructs surface parameters to be applied when the current view state gets pushed to
         * RenderThread.
         *
         * @param surface The surface to modify.
         * @param alpha Alpha to apply.
         * @param matrix Matrix to apply.
         * @param windowCrop Crop to apply.
         */
        public SurfaceParams(SurfaceControlCompat surface, float alpha, Matrix matrix,
                Rect windowCrop, int layer) {
            this.surface = surface.mSurfaceControl;
            this.alpha = alpha;
            this.matrix = new Matrix(matrix);
            this.windowCrop = new Rect(windowCrop);
            this.layer = layer;
        }

        final SurfaceControl surface;
        final float alpha;
        final Matrix matrix;
        final Rect windowCrop;
        final int layer;
    }
}
