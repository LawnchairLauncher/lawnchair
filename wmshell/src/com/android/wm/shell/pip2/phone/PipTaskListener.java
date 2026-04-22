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

package com.android.wm.shell.pip2.phone;

import static com.android.wm.shell.pip2.phone.PipTransition.ANIMATING_BOUNDS_CHANGE_DURATION;

import android.app.ActivityManager;
import android.app.PictureInPictureParams;
import android.app.RemoteAction;
import android.content.Context;
import android.graphics.Rect;
import android.os.Bundle;
import android.view.SurfaceControl;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.protolog.ProtoLog;
import com.android.internal.util.Preconditions;
import com.android.wm.shell.ShellTaskOrganizer;
import com.android.wm.shell.common.ShellExecutor;
import com.android.wm.shell.common.pip.PipBoundsAlgorithm;
import com.android.wm.shell.common.pip.PipBoundsState;
import com.android.wm.shell.common.pip.PipUtils;
import com.android.wm.shell.pip2.PipSurfaceTransactionHelper;
import com.android.wm.shell.pip2.animation.PipResizeAnimator;
import com.android.wm.shell.protolog.ShellProtoLogGroup;
import com.android.wm.shell.shared.annotations.ShellMainThread;
import com.android.wm.shell.shared.pip.PipFlags;

import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

/**
 * A Task Listener implementation used only for CUJs and trigger paths that cannot be initiated via
 * Transitions framework directly.
 * Hence, it's the intention to keep the usage of this class for a very limited set of cases.
 */
public class PipTaskListener implements ShellTaskOrganizer.TaskListener,
        PipTransitionState.PipTransitionStateChangedListener {
    private static final int ASPECT_RATIO_CHANGE_DURATION = 250;
    @VisibleForTesting
    static final String ANIMATING_ASPECT_RATIO_CHANGE = "animating_aspect_ratio_change";

    private final Context mContext;
    private final PipTransitionState mPipTransitionState;
    private final PipScheduler mPipScheduler;
    private final PipBoundsState mPipBoundsState;
    private final PipBoundsAlgorithm mPipBoundsAlgorithm;
    private final ShellExecutor mMainExecutor;
    private PictureInPictureParams mPictureInPictureParams =
            new PictureInPictureParams.Builder().build();

    private boolean mWaitingForAspectRatioChange = false;
    private final List<PipParamsChangedCallback> mPipParamsChangedListeners = new ArrayList<>();

    private PipResizeAnimatorSupplier mPipResizeAnimatorSupplier;

    private final @NonNull PipSurfaceTransactionHelper mSurfaceTransactionHelper;

    public PipTaskListener(Context context,
            @NonNull PipSurfaceTransactionHelper pipSurfaceTransactionHelper,
            ShellTaskOrganizer shellTaskOrganizer,
            PipTransitionState pipTransitionState,
            PipScheduler pipScheduler,
            PipBoundsState pipBoundsState,
            PipBoundsAlgorithm pipBoundsAlgorithm,
            @ShellMainThread ShellExecutor mainExecutor) {
        mContext = context;
        mPipTransitionState = pipTransitionState;
        mPipScheduler = pipScheduler;
        mPipBoundsState = pipBoundsState;
        mPipBoundsAlgorithm = pipBoundsAlgorithm;
        mMainExecutor = mainExecutor;

        mPipTransitionState.addPipTransitionStateChangedListener(this);
        if (PipFlags.isPip2ExperimentEnabled()) {
            mMainExecutor.execute(() -> {
                shellTaskOrganizer.addListenerForType(this,
                        ShellTaskOrganizer.TASK_LISTENER_TYPE_PIP);
            });
        }
        mPipResizeAnimatorSupplier = PipResizeAnimator::new;
        mPipScheduler.setPipParamsSupplier(this::getPictureInPictureParams);
        // Reset {@link #mPictureInPictureParams} after exiting PiP. For instance, next Activity
        // with null aspect ratio would accidentally inherit the aspect ratio from a previous
        // PiP Activity.
        mPipBoundsState.addOnPipComponentChangedListener(((oldPipComponent, newPipComponent) ->
                mPictureInPictureParams = new PictureInPictureParams.Builder().build()));

        mSurfaceTransactionHelper = pipSurfaceTransactionHelper;
    }

    void setPictureInPictureParams(@Nullable PictureInPictureParams params) {
        if (mPictureInPictureParams.equals(params)) {
            return;
        }
        if (params != null && (PipUtils.remoteActionsChanged(params.getActions(),
                mPictureInPictureParams.getActions())
                || !PipUtils.remoteActionsMatch(params.getCloseAction(),
                mPictureInPictureParams.getCloseAction()))) {
            for (PipParamsChangedCallback listener : mPipParamsChangedListeners) {
                listener.onActionsChanged(params.getActions(), params.getCloseAction());
            }
        }
        // Set the new params but make sure mPictureInPictureParams is not null.
        mPictureInPictureParams = params == null
                ? new PictureInPictureParams.Builder().build() : params;
        logRemoteActions(mPictureInPictureParams);
    }

    private void logRemoteActions(@android.annotation.NonNull PictureInPictureParams params) {
        StringJoiner sj = new StringJoiner("|", "[", "]");
        if (params.hasSetActions()) {
            params.getActions().forEach((action) -> sj.add(action.getTitle()));
        }

        ProtoLog.d(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                "PIP remote actions=%s", sj.toString());
    }

    /** Add a PipParamsChangedCallback listener. */
    public void addParamsChangedListener(PipParamsChangedCallback listener) {
        if (mPipParamsChangedListeners.contains(listener)) {
            return;
        }
        mPipParamsChangedListeners.add(listener);
    }

    @NonNull
    public PictureInPictureParams getPictureInPictureParams() {
        return mPictureInPictureParams;
    }

    @Override
    public void onTaskInfoChanged(ActivityManager.RunningTaskInfo taskInfo) {
        PictureInPictureParams params = taskInfo.pictureInPictureParams;
        if (mPictureInPictureParams.equals(params)) {
            return;
        }
        ProtoLog.d(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                "onTaskInfoChanged: %s, state=%s oldParams=%s newParams=%s",
                taskInfo.topActivity, mPipTransitionState, mPictureInPictureParams, params);
        setPictureInPictureParams(params);
        // Note: params is nullable while mPictureInPictureParams is never null
        float newAspectRatio = mPictureInPictureParams.getAspectRatioFloat();
        if (mPictureInPictureParams.hasSetAspectRatio()
                && mPipBoundsAlgorithm.isValidPictureInPictureAspectRatio(newAspectRatio)
                && PipUtils.aspectRatioChanged(newAspectRatio, mPipBoundsState.getAspectRatio())) {
            mPipTransitionState.setOnIdlePipTransitionStateRunnable(
                    () -> onAspectRatioChanged(newAspectRatio));
        }
    }

    private void onAspectRatioChanged(float ratio) {
        mPipBoundsState.setAspectRatio(ratio);

        final Rect destinationBounds = mPipBoundsAlgorithm.getAdjustedDestinationBounds(
                mPipBoundsState.getBounds(), mPipBoundsState.getAspectRatio());
        // Avoid scheduling a resize transition if destination bounds are unchanged, otherise
        // we could end up with a no-op transition.
        if (!destinationBounds.equals(mPipBoundsState.getBounds())) {
            Bundle extra = new Bundle();
            extra.putBoolean(ANIMATING_ASPECT_RATIO_CHANGE, true);
            mPipTransitionState.setState(PipTransitionState.SCHEDULED_BOUNDS_CHANGE, extra);
        }
    }

    @Override
    public void onPipTransitionStateChanged(@PipTransitionState.TransitionState int oldState,
            @PipTransitionState.TransitionState int newState, @Nullable Bundle extra) {
        switch (newState) {
            case PipTransitionState.SCHEDULED_BOUNDS_CHANGE:
                mWaitingForAspectRatioChange = extra.getBoolean(ANIMATING_ASPECT_RATIO_CHANGE);
                if (!mWaitingForAspectRatioChange) break;

                mPipScheduler.scheduleAnimateResizePip(
                        mPipBoundsAlgorithm.getAdjustedDestinationBounds(
                                mPipBoundsState.getBounds(), mPipBoundsState.getAspectRatio()),
                        false /* configAtEnd */, ASPECT_RATIO_CHANGE_DURATION);
                break;
            case PipTransitionState.CHANGING_PIP_BOUNDS:
                final SurfaceControl.Transaction startTx = extra.getParcelable(
                        PipTransition.PIP_START_TX, SurfaceControl.Transaction.class);
                final SurfaceControl.Transaction finishTx = extra.getParcelable(
                        PipTransition.PIP_FINISH_TX, SurfaceControl.Transaction.class);
                final Rect destinationBounds = extra.getParcelable(
                        PipTransition.PIP_DESTINATION_BOUNDS, Rect.class);
                final int duration = extra.getInt(ANIMATING_BOUNDS_CHANGE_DURATION,
                        PipTransition.BOUNDS_CHANGE_JUMPCUT_DURATION);

                Preconditions.checkNotNull(mPipTransitionState.getPinnedTaskLeash(),
                        "Leash is null for bounds transition.");

                if (mWaitingForAspectRatioChange) {
                    mWaitingForAspectRatioChange = false;
                    PipResizeAnimator animator = mPipResizeAnimatorSupplier.get(mContext,
                            mSurfaceTransactionHelper,
                            mPipTransitionState.getPinnedTaskLeash(), startTx, finishTx,
                            destinationBounds,
                            mPipBoundsState.getBounds(), destinationBounds, duration,
                            0f /* delta */);
                    animator.setAnimationEndCallback(
                            () -> mPipScheduler.scheduleFinishPipBoundsChange(destinationBounds));
                    animator.start();
                }
                break;
        }
    }

    public interface PipParamsChangedCallback {
        /**
         * Called if either the actions or the close action changed.
         */
        default void onActionsChanged(List<RemoteAction> actions, RemoteAction closeAction) {
        }
    }

    @VisibleForTesting
    interface PipResizeAnimatorSupplier {
        PipResizeAnimator get(@NonNull Context context,
                @NonNull PipSurfaceTransactionHelper pipSurfaceTransactionHelper,
                @NonNull SurfaceControl leash,
                @Nullable SurfaceControl.Transaction startTx,
                @Nullable SurfaceControl.Transaction finishTx,
                @NonNull Rect baseBounds,
                @NonNull Rect startBounds,
                @NonNull Rect endBounds,
                int duration,
                float delta);
    }

    @VisibleForTesting
    void setPipResizeAnimatorSupplier(@NonNull PipResizeAnimatorSupplier supplier) {
        mPipResizeAnimatorSupplier = supplier;
    }
}
