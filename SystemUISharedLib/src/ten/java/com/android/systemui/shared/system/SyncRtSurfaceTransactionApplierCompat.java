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

import android.graphics.HardwareRenderer;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Handler.Callback;
import android.os.Message;
import android.os.Trace;
import android.view.Surface;
import android.view.View;
import android.view.ViewRootImpl;

import java.util.function.Consumer;

/**
 * Helper class to apply surface transactions in sync with RenderThread.
 *
 * NOTE: This is a modification of {@link android.view.SyncRtSurfaceTransactionApplier}, we can't 
 *       currently reference that class from the shared lib as it is hidden.
 */
public class SyncRtSurfaceTransactionApplierCompat {

    private static final int MSG_UPDATE_SEQUENCE_NUMBER = 0;

    private final Surface mTargetSurface;
    private final ViewRootImpl mTargetViewRootImpl;
    private final Handler mApplyHandler;

    private int mSequenceNumber = 0;
    private int mPendingSequenceNumber = 0;
    private Runnable mAfterApplyCallback;

    /**
     * @param targetView The view in the surface that acts as synchronization anchor.
     */
    public SyncRtSurfaceTransactionApplierCompat(View targetView) {
        mTargetViewRootImpl = targetView != null ? targetView.getViewRootImpl() : null;
        mTargetSurface = mTargetViewRootImpl != null ? mTargetViewRootImpl.mSurface : null;

        mApplyHandler = new Handler(new Callback() {
            @Override
            public boolean handleMessage(Message msg) {
                if (msg.what == MSG_UPDATE_SEQUENCE_NUMBER) {
                    onApplyMessage(msg.arg1);
                    return true;
                }
                return false;
            }
        });
    }

    private void onApplyMessage(int seqNo) {
        mSequenceNumber = seqNo;
        if (mSequenceNumber == mPendingSequenceNumber && mAfterApplyCallback != null) {
            Runnable r = mAfterApplyCallback;
            mAfterApplyCallback = null;
            r.run();
        }
    }

    /**
     * Schedules applying surface parameters on the next frame.
     *
     * @param params The surface parameters to apply. DO NOT MODIFY the list after passing into
     *               this method to avoid synchronization issues.
     */
    public void scheduleApply(final SyncRtSurfaceTransactionApplierCompat.SurfaceParams... params) {
        if (mTargetViewRootImpl == null || mTargetViewRootImpl.getView() == null) {
            return;
        }

        mPendingSequenceNumber++;
        final int toApplySeqNo = mPendingSequenceNumber;
        mTargetViewRootImpl.registerRtFrameCallback(new HardwareRenderer.FrameDrawingCallback() {
            @Override
            public void onFrameDraw(long frame) {
                if (mTargetSurface == null || !mTargetSurface.isValid()) {
                    Message.obtain(mApplyHandler, MSG_UPDATE_SEQUENCE_NUMBER, toApplySeqNo, 0)
                            .sendToTarget();
                    return;
                }
                Trace.traceBegin(Trace.TRACE_TAG_VIEW, "Sync transaction frameNumber=" + frame);
                TransactionCompat t = new TransactionCompat();
                for (int i = params.length - 1; i >= 0; i--) {
                    SyncRtSurfaceTransactionApplierCompat.SurfaceParams surfaceParams =
                            params[i];
                    SurfaceControlCompat surface = surfaceParams.surface;
                    t.deferTransactionUntil(surface, mTargetSurface, frame);
                    applyParams(t, surfaceParams);
                }
                t.setEarlyWakeup();
                t.apply();
                Trace.traceEnd(Trace.TRACE_TAG_VIEW);
                Message.obtain(mApplyHandler, MSG_UPDATE_SEQUENCE_NUMBER, toApplySeqNo, 0)
                        .sendToTarget();
            }
        });

        // Make sure a frame gets scheduled.
        mTargetViewRootImpl.getView().invalidate();
    }

    /**
     * Calls the runnable when any pending apply calls have completed
     */
    public void addAfterApplyCallback(final Runnable afterApplyCallback) {
        if (mSequenceNumber == mPendingSequenceNumber) {
            afterApplyCallback.run();
        } else {
            if (mAfterApplyCallback == null) {
                mAfterApplyCallback = afterApplyCallback;
            } else {
                final Runnable oldCallback = mAfterApplyCallback;
                mAfterApplyCallback = new Runnable() {
                    @Override
                    public void run() {
                        afterApplyCallback.run();
                        oldCallback.run();
                    }
                };
            }
        }
    }

    public static void applyParams(TransactionCompat t,
            SyncRtSurfaceTransactionApplierCompat.SurfaceParams params) {
        t.setMatrix(params.surface, params.matrix);
        t.setWindowCrop(params.surface, params.windowCrop);
        t.setAlpha(params.surface, params.alpha);
        t.setLayer(params.surface, params.layer);
        t.setCornerRadius(params.surface, params.cornerRadius);
        t.show(params.surface);
    }

    /**
     * Creates an instance of SyncRtSurfaceTransactionApplier, deferring until the target view is
     * attached if necessary.
     */
    public static void create(final View targetView,
            final Consumer<SyncRtSurfaceTransactionApplierCompat> callback) {
        if (targetView == null) {
            // No target view, no applier
            callback.accept(null);
        } else if (targetView.getViewRootImpl() != null) {
            // Already attached, we're good to go
            callback.accept(new SyncRtSurfaceTransactionApplierCompat(targetView));
        } else {
            // Haven't been attached before we can get the view root
            targetView.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                @Override
                public void onViewAttachedToWindow(View v) {
                    targetView.removeOnAttachStateChangeListener(this);
                    callback.accept(new SyncRtSurfaceTransactionApplierCompat(targetView));
                }

                @Override
                public void onViewDetachedFromWindow(View v) {
                    // Do nothing
                }
            });
        }
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
                Rect windowCrop, int layer, float cornerRadius) {
            this.surface = surface;
            this.alpha = alpha;
            this.matrix = new Matrix(matrix);
            this.windowCrop = new Rect(windowCrop);
            this.layer = layer;
            this.cornerRadius = cornerRadius;
        }

        public final SurfaceControlCompat surface;
        public final float alpha;
        final float cornerRadius;
        public final Matrix matrix;
        public final Rect windowCrop;
        public final int layer;
    }
}
