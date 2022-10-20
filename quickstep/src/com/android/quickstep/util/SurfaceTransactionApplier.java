/*
 * Copyright (C) 2020 The Android Open Source Project
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

import android.annotation.TargetApi;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.view.SurfaceControl;
import android.view.SurfaceControl.Transaction;
import android.view.View;
import android.view.ViewRootImpl;

import com.android.quickstep.RemoteAnimationTargets.ReleaseCheck;

import java.util.function.Consumer;


/**
 * Helper class to apply surface transactions in sync with RenderThread similar to
 *   android.view.SyncRtSurfaceTransactionApplier
 * with some Launcher specific utility methods
 */
@TargetApi(Build.VERSION_CODES.R)
public class SurfaceTransactionApplier extends ReleaseCheck {

    private static final int MSG_UPDATE_SEQUENCE_NUMBER = 0;

    private final SurfaceControl mBarrierSurfaceControl;
    private final ViewRootImpl mTargetViewRootImpl;
    private final Handler mApplyHandler;

    private int mLastSequenceNumber = 0;

    /**
     * @param targetView The view in the surface that acts as synchronization anchor.
     */
    public SurfaceTransactionApplier(View targetView) {
        mTargetViewRootImpl = targetView.getViewRootImpl();
        mBarrierSurfaceControl = mTargetViewRootImpl.getSurfaceControl();
        mApplyHandler = new Handler(this::onApplyMessage);
        setCanRelease(true);
    }

    protected boolean onApplyMessage(Message msg) {
        if (msg.what == MSG_UPDATE_SEQUENCE_NUMBER) {
            setCanRelease(msg.arg1 == mLastSequenceNumber);
            return true;
        }
        return false;
    }

    /**
     * Schedules applying surface parameters on the next frame.
     *
     * @param params The surface parameters to apply. DO NOT MODIFY the list after passing into
     *               this method to avoid synchronization issues.
     */
    public void scheduleApply(SurfaceTransaction params) {
        View view = mTargetViewRootImpl.getView();
        if (view == null) {
            return;
        }
        Transaction t = params.getTransaction();

        mLastSequenceNumber++;
        final int toApplySeqNo = mLastSequenceNumber;
        setCanRelease(false);
        mTargetViewRootImpl.registerRtFrameCallback(frame -> {
            if (mBarrierSurfaceControl == null || !mBarrierSurfaceControl.isValid()) {
                Message.obtain(mApplyHandler, MSG_UPDATE_SEQUENCE_NUMBER, toApplySeqNo, 0)
                        .sendToTarget();
                return;
            }
            mTargetViewRootImpl.mergeWithNextTransaction(t, frame);
            Message.obtain(mApplyHandler, MSG_UPDATE_SEQUENCE_NUMBER, toApplySeqNo, 0)
                    .sendToTarget();
        });

        // Make sure a frame gets scheduled.
        view.invalidate();
    }

    /**
     * Creates an instance of SurfaceTransactionApplier, deferring until the target view is
     * attached if necessary.
     */
    public static void create(
            final View targetView, final Consumer<SurfaceTransactionApplier> callback) {
        if (targetView == null) {
            // No target view, no applier
            callback.accept(null);
        } else if (targetView.isAttachedToWindow()) {
            // Already attached, we're good to go
            callback.accept(new SurfaceTransactionApplier(targetView));
        } else {
            // Haven't been attached before we can get the view root
            targetView.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                @Override
                public void onViewAttachedToWindow(View v) {
                    targetView.removeOnAttachStateChangeListener(this);
                    callback.accept(new SurfaceTransactionApplier(targetView));
                }

                @Override
                public void onViewDetachedFromWindow(View v) {
                    // Do nothing
                }
            });
        }
    }
}
