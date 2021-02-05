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
import android.view.SurfaceControl;
import android.view.SurfaceControl.Transaction;
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

    public static final int FLAG_ALL = 0xffffffff;
    public static final int FLAG_ALPHA = 1;
    public static final int FLAG_MATRIX = 1 << 1;
    public static final int FLAG_WINDOW_CROP = 1 << 2;
    public static final int FLAG_LAYER = 1 << 3;
    public static final int FLAG_CORNER_RADIUS = 1 << 4;
    public static final int FLAG_BACKGROUND_BLUR_RADIUS = 1 << 5;
    public static final int FLAG_VISIBILITY = 1 << 6;
    public static final int FLAG_RELATIVE_LAYER = 1 << 7;

    private static final int MSG_UPDATE_SEQUENCE_NUMBER = 0;

    private final SurfaceControl mBarrierSurfaceControl;
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
        mBarrierSurfaceControl = mTargetViewRootImpl != null
            ? mTargetViewRootImpl.getRenderSurfaceControl() : null;

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
                if (mBarrierSurfaceControl == null || !mBarrierSurfaceControl.isValid()) {
                    Message.obtain(mApplyHandler, MSG_UPDATE_SEQUENCE_NUMBER, toApplySeqNo, 0)
                            .sendToTarget();
                    return;
                }
                Trace.traceBegin(Trace.TRACE_TAG_VIEW, "Sync transaction frameNumber=" + frame);
                Transaction t = new Transaction();
                for (int i = params.length - 1; i >= 0; i--) {
                    SyncRtSurfaceTransactionApplierCompat.SurfaceParams surfaceParams =
                            params[i];
                    t.deferTransactionUntil(surfaceParams.surface, mBarrierSurfaceControl, frame);
                    surfaceParams.applyTo(t);
                }
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
        params.applyTo(t.mTransaction);
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
        public static class Builder {
            final SurfaceControl surface;
            int flags;
            float alpha;
            float cornerRadius;
            int backgroundBlurRadius;
            Matrix matrix;
            Rect windowCrop;
            int layer;
            SurfaceControl relativeTo;
            int relativeLayer;
            boolean visible;

            /**
             * @param surface The surface to modify.
             */
            public Builder(SurfaceControlCompat surface) {
                this(surface.mSurfaceControl);
            }

            /**
             * @param surface The surface to modify.
             */
            public Builder(SurfaceControl surface) {
                this.surface = surface;
            }

            /**
             * @param alpha The alpha value to apply to the surface.
             * @return this Builder
             */
            public Builder withAlpha(float alpha) {
                this.alpha = alpha;
                flags |= FLAG_ALPHA;
                return this;
            }

            /**
             * @param matrix The matrix to apply to the surface.
             * @return this Builder
             */
            public Builder withMatrix(Matrix matrix) {
                this.matrix = matrix;
                flags |= FLAG_MATRIX;
                return this;
            }

            /**
             * @param windowCrop The window crop to apply to the surface.
             * @return this Builder
             */
            public Builder withWindowCrop(Rect windowCrop) {
                this.windowCrop = windowCrop;
                flags |= FLAG_WINDOW_CROP;
                return this;
            }

            /**
             * @param layer The layer to assign the surface.
             * @return this Builder
             */
            public Builder withLayer(int layer) {
                this.layer = layer;
                flags |= FLAG_LAYER;
                return this;
            }

            /**
             * @param relativeTo The surface that's set relative layer to.
             * @param relativeLayer The relative layer.
             * @return this Builder
             */
            public Builder withRelativeLayerTo(SurfaceControl relativeTo, int relativeLayer) {
                this.relativeTo = relativeTo;
                this.relativeLayer = relativeLayer;
                flags |= FLAG_RELATIVE_LAYER;
                return this;
            }

            /**
             * @param radius the Radius for rounded corners to apply to the surface.
             * @return this Builder
             */
            public Builder withCornerRadius(float radius) {
                this.cornerRadius = radius;
                flags |= FLAG_CORNER_RADIUS;
                return this;
            }

            /**
             * @param radius the Radius for blur to apply to the background surfaces.
             * @return this Builder
             */
            public Builder withBackgroundBlur(int radius) {
                this.backgroundBlurRadius = radius;
                flags |= FLAG_BACKGROUND_BLUR_RADIUS;
                return this;
            }

            /**
             * @param visible The visibility to apply to the surface.
             * @return this Builder
             */
            public Builder withVisibility(boolean visible) {
                this.visible = visible;
                flags |= FLAG_VISIBILITY;
                return this;
            }

            /**
             * @return a new SurfaceParams instance
             */
            public SurfaceParams build() {
                return new SurfaceParams(surface, flags, alpha, matrix, windowCrop, layer,
                        relativeTo, relativeLayer, cornerRadius, backgroundBlurRadius, visible);
            }
        }

        /**
         * Constructs surface parameters to be applied when the current view state gets pushed to
         * RenderThread.
         *
         * @param surface The surface to modify.
         * @param alpha Alpha to apply.
         * @param matrix Matrix to apply.
         * @param windowCrop Crop to apply, only applied if not {@code null}
         */
        public SurfaceParams(SurfaceControlCompat surface, float alpha, Matrix matrix,
                Rect windowCrop, int layer, SurfaceControl relativeTo, int relativeLayer,
                float cornerRadius) {
            this(surface.mSurfaceControl,
                    FLAG_ALL & ~(FLAG_VISIBILITY | FLAG_BACKGROUND_BLUR_RADIUS), alpha,
                    matrix, windowCrop, layer, relativeTo, relativeLayer, cornerRadius,
                    0 /* backgroundBlurRadius */, true);
        }

        private SurfaceParams(SurfaceControl surface, int flags, float alpha, Matrix matrix,
                Rect windowCrop, int layer, SurfaceControl relativeTo, int relativeLayer,
                float cornerRadius, int backgroundBlurRadius, boolean visible) {
            this.flags = flags;
            this.surface = surface;
            this.alpha = alpha;
            this.matrix = new Matrix(matrix);
            this.windowCrop = windowCrop != null ? new Rect(windowCrop) : null;
            this.layer = layer;
            this.relativeTo = relativeTo;
            this.relativeLayer = relativeLayer;
            this.cornerRadius = cornerRadius;
            this.backgroundBlurRadius = backgroundBlurRadius;
            this.visible = visible;
        }

        private final int flags;
        private final float[] mTmpValues = new float[9];

        public final SurfaceControl surface;
        public final float alpha;
        public final float cornerRadius;
        public final int backgroundBlurRadius;
        public final Matrix matrix;
        public final Rect windowCrop;
        public final int layer;
        public final SurfaceControl relativeTo;
        public final int relativeLayer;
        public final boolean visible;

        public void applyTo(SurfaceControl.Transaction t) {
            if ((flags & FLAG_MATRIX) != 0) {
                t.setMatrix(surface, matrix, mTmpValues);
            }
            if ((flags & FLAG_WINDOW_CROP) != 0) {
                t.setWindowCrop(surface, windowCrop);
            }
            if ((flags & FLAG_ALPHA) != 0) {
                t.setAlpha(surface, alpha);
            }
            if ((flags & FLAG_LAYER) != 0) {
                t.setLayer(surface, layer);
            }
            if ((flags & FLAG_CORNER_RADIUS) != 0) {
                t.setCornerRadius(surface, cornerRadius);
            }
            if ((flags & FLAG_BACKGROUND_BLUR_RADIUS) != 0) {
                t.setBackgroundBlurRadius(surface, backgroundBlurRadius);
            }
            if ((flags & FLAG_VISIBILITY) != 0) {
                if (visible) {
                    t.show(surface);
                } else {
                    t.hide(surface);
                }
            }
            if ((flags & FLAG_RELATIVE_LAYER) != 0) {
                t.setRelativeLayer(surface, relativeTo, relativeLayer);
            }
        }
    }
}
