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
import android.view.View.OnAttachStateChangeListener;
import android.view.ViewRootImpl;

import androidx.annotation.NonNull;

import com.android.quickstep.RemoteAnimationTargets.ReleaseCheck;

/**
 * Helper class to apply surface transactions in sync with RenderThread similar to
 *   android.view.SyncRtSurfaceTransactionApplier
 * with some Launcher specific utility methods
 */
@TargetApi(Build.VERSION_CODES.R)
public class SurfaceTransactionApplier extends ReleaseCheck {

    private static final int MSG_UPDATE_SEQUENCE_NUMBER = 0;

    private final Handler mApplyHandler;

    private boolean mInitialized;
    private SurfaceControl mBarrierSurfaceControl;
    private ViewRootImpl mTargetViewRootImpl;

    private int mLastSequenceNumber = 0;

    /**
     * @param targetView The view in the surface that acts as synchronization anchor.
     */
    public SurfaceTransactionApplier(@NonNull View targetView) {
        if (targetView.isAttachedToWindow()) {
            initialize(targetView);
        } else {
            mInitialized = false;
            targetView.addOnAttachStateChangeListener(new OnAttachStateChangeListener() {
                @Override
                public void onViewAttachedToWindow(View v) {
                    if (!mInitialized) {
                        targetView.removeOnAttachStateChangeListener(this);
                        initialize(targetView);
                    }
                }

                @Override
                public void onViewDetachedFromWindow(View v) {
                    // Do nothing
                }
            });
        }
        mApplyHandler = new Handler(this::onApplyMessage);
        setCanRelease(true);
    }

    private void initialize(View view) {
        mTargetViewRootImpl = view.getViewRootImpl();
        mBarrierSurfaceControl = mTargetViewRootImpl.getSurfaceControl();
        mInitialized = true;
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
        if (!mInitialized) {
            params.getTransaction().apply();
            return;
        }
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
}
