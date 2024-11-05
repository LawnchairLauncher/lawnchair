/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.wm.shell.pip2.phone;

import static android.app.WindowConfiguration.WINDOWING_MODE_UNDEFINED;

import static com.android.wm.shell.transition.Transitions.TRANSIT_EXIT_PIP;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.view.SurfaceControl;
import android.window.WindowContainerTransaction;

import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.android.internal.protolog.common.ProtoLog;
import com.android.wm.shell.common.ShellExecutor;
import com.android.wm.shell.common.pip.PipBoundsState;
import com.android.wm.shell.common.pip.PipUtils;
import com.android.wm.shell.pip.PipTransitionController;
import com.android.wm.shell.protolog.ShellProtoLogGroup;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Scheduler for Shell initiated PiP transitions and animations.
 */
public class PipScheduler {
    private static final String TAG = PipScheduler.class.getSimpleName();
    private static final String BROADCAST_FILTER = PipScheduler.class.getCanonicalName();

    private final Context mContext;
    private final PipBoundsState mPipBoundsState;
    private final ShellExecutor mMainExecutor;
    private final PipTransitionState mPipTransitionState;
    private PipSchedulerReceiver mSchedulerReceiver;
    private PipTransitionController mPipTransitionController;

    /**
     * Temporary PiP CUJ codes to schedule PiP related transitions directly from Shell.
     * This is used for a broadcast receiver to resolve intents. This should be removed once
     * there is an equivalent of PipTouchHandler and PipResizeGestureHandler for PiP2.
     */
    private static final int PIP_EXIT_VIA_EXPAND_CODE = 0;
    private static final int PIP_DOUBLE_TAP = 1;

    @IntDef(value = {
            PIP_EXIT_VIA_EXPAND_CODE,
            PIP_DOUBLE_TAP
    })
    @Retention(RetentionPolicy.SOURCE)
    @interface PipUserJourneyCode {}

    /**
     * A temporary broadcast receiver to initiate PiP CUJs.
     */
    private class PipSchedulerReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            int userJourneyCode = intent.getIntExtra("cuj_code_extra", 0);
            switch (userJourneyCode) {
                case PIP_EXIT_VIA_EXPAND_CODE:
                    scheduleExitPipViaExpand();
                    break;
                case PIP_DOUBLE_TAP:
                    scheduleDoubleTapToResize();
                    break;
                default:
                    throw new IllegalStateException("unexpected CUJ code=" + userJourneyCode);
            }
        }
    }

    public PipScheduler(Context context,
            PipBoundsState pipBoundsState,
            ShellExecutor mainExecutor,
            PipTransitionState pipTransitionState) {
        mContext = context;
        mPipBoundsState = pipBoundsState;
        mMainExecutor = mainExecutor;
        mPipTransitionState = pipTransitionState;

        if (PipUtils.isPip2ExperimentEnabled()) {
            // temporary broadcast receiver to initiate exit PiP via expand
            mSchedulerReceiver = new PipSchedulerReceiver();
            ContextCompat.registerReceiver(mContext, mSchedulerReceiver,
                    new IntentFilter(BROADCAST_FILTER), ContextCompat.RECEIVER_EXPORTED);
        }
    }

    ShellExecutor getMainExecutor() {
        return mMainExecutor;
    }

    void setPipTransitionController(PipTransitionController pipTransitionController) {
        mPipTransitionController = pipTransitionController;
    }

    @Nullable
    private WindowContainerTransaction getExitPipViaExpandTransaction() {
        if (mPipTransitionState.mPipTaskToken == null) {
            return null;
        }
        WindowContainerTransaction wct = new WindowContainerTransaction();
        // final expanded bounds to be inherited from the parent
        wct.setBounds(mPipTransitionState.mPipTaskToken, null);
        // if we are hitting a multi-activity case
        // windowing mode change will reparent to original host task
        wct.setWindowingMode(mPipTransitionState.mPipTaskToken, WINDOWING_MODE_UNDEFINED);
        return wct;
    }

    /**
     * Schedules exit PiP via expand transition.
     */
    public void scheduleExitPipViaExpand() {
        WindowContainerTransaction wct = getExitPipViaExpandTransaction();
        if (wct != null) {
            mMainExecutor.execute(() -> {
                mPipTransitionController.startExitTransition(TRANSIT_EXIT_PIP, wct,
                        null /* destinationBounds */);
            });
        }
    }

    /**
     * Schedules resize PiP via double tap.
     */
    public void scheduleDoubleTapToResize() {}

    /**
     * Animates resizing of the pinned stack given the duration.
     */
    public void scheduleAnimateResizePip(Rect toBounds) {
        scheduleAnimateResizePip(toBounds, false /* configAtEnd */);
    }

    /**
     * Animates resizing of the pinned stack given the duration.
     *
     * @param configAtEnd true if we are delaying config updates until the transition ends.
     */
    public void scheduleAnimateResizePip(Rect toBounds, boolean configAtEnd) {
        if (mPipTransitionState.mPipTaskToken == null || !mPipTransitionState.isInPip()) {
            return;
        }
        WindowContainerTransaction wct = new WindowContainerTransaction();
        wct.setBounds(mPipTransitionState.mPipTaskToken, toBounds);
        if (configAtEnd) {
            wct.deferConfigToTransitionEnd(mPipTransitionState.mPipTaskToken);
        }
        mPipTransitionController.startResizeTransition(wct);
    }

    /**
     * Signals to Core to finish the PiP resize transition.
     * Note that we do not allow any actual WM Core changes at this point.
     *
     * @param configAtEnd true if we are waiting for config updates at the end of the transition.
     */
    public void scheduleFinishResizePip(boolean configAtEnd) {
        SurfaceControl.Transaction tx = null;
        if (configAtEnd) {
            tx = new SurfaceControl.Transaction();
            tx.addTransactionCommittedListener(mMainExecutor, () -> {
                mPipTransitionState.setState(PipTransitionState.CHANGED_PIP_BOUNDS);
            });
        } else {
            mPipTransitionState.setState(PipTransitionState.CHANGED_PIP_BOUNDS);
        }
        mPipTransitionController.finishTransition(tx);
    }

    /**
     * Directly perform a scaled matrix transformation on the leash. This will not perform any
     * {@link WindowContainerTransaction}.
     */
    public void scheduleUserResizePip(Rect toBounds) {
        scheduleUserResizePip(toBounds, 0f /* degrees */);
    }

    /**
     * Directly perform a scaled matrix transformation on the leash. This will not perform any
     * {@link WindowContainerTransaction}.
     *
     * @param degrees the angle to rotate the bounds to.
     */
    public void scheduleUserResizePip(Rect toBounds, float degrees) {
        if (toBounds.isEmpty()) {
            ProtoLog.w(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                    "%s: Attempted to user resize PIP to empty bounds, aborting.", TAG);
            return;
        }
        SurfaceControl leash = mPipTransitionState.mPinnedTaskLeash;
        final SurfaceControl.Transaction tx = new SurfaceControl.Transaction();

        Matrix transformTensor = new Matrix();
        final float[] mMatrixTmp = new float[9];
        final float scale = (float) toBounds.width() / mPipBoundsState.getBounds().width();

        transformTensor.setScale(scale, scale);
        transformTensor.postTranslate(toBounds.left, toBounds.top);
        transformTensor.postRotate(degrees, toBounds.centerX(), toBounds.centerY());

        tx.setMatrix(leash, transformTensor, mMatrixTmp);
        tx.apply();
    }
}
