/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.wm.shell.pip2.phone.transition;

import android.graphics.Rect;
import android.os.IBinder;
import android.util.Pair;
import android.view.SurfaceControl;
import android.window.TransitionInfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.android.wm.shell.common.pip.PipBoundsState;
import com.android.wm.shell.pip2.phone.PipTransitionState;
import com.android.wm.shell.shared.TransitionUtil;
import com.android.wm.shell.transition.Transitions;

/**
 * An implementation of {@link Transitions.TransitionObserver} to track of external transitions
 * that might affect a PiP task as well.
 */
public class PipDisplayChangeObserver implements Transitions.TransitionObserver {
    private final PipTransitionState mPipTransitionState;
    private final PipBoundsState mPipBoundsState;

    @Nullable
    private Pair<IBinder, TransitionInfo> mDisplayChangeTransition;

    public PipDisplayChangeObserver(PipTransitionState pipTransitionState,
            PipBoundsState pipBoundsState) {
        mPipTransitionState = pipTransitionState;
        mPipBoundsState = pipBoundsState;
    }

    @Override
    public void onTransitionReady(@NonNull IBinder transition, @NonNull TransitionInfo info,
            @NonNull SurfaceControl.Transaction startTransaction,
            @NonNull SurfaceControl.Transaction finishTransaction) {
        if (TransitionUtil.hasDisplayChange(info)) {
            mDisplayChangeTransition = new Pair<>(transition, info);
        }
    }

    @Override
    public void onTransitionMerged(@NonNull IBinder merged, @NonNull IBinder playing) {
        if (mDisplayChangeTransition != null
                && mDisplayChangeTransition.first == merged) {
            maybeUpdatePipStateOnDisplayChange(mDisplayChangeTransition.second /* info */);
            mPipTransitionState.setIsPipBoundsChangingWithDisplay(false);
            mDisplayChangeTransition = null;
        }
    }

    @Override
    public void onTransitionFinished(@NonNull IBinder transition, boolean aborted) {
        if (mDisplayChangeTransition != null
                && mDisplayChangeTransition.first == transition) {
            maybeUpdatePipStateOnDisplayChange(mDisplayChangeTransition.second /* info */);
            mPipTransitionState.setIsPipBoundsChangingWithDisplay(false);
            mDisplayChangeTransition = null;
        }
    }

    @VisibleForTesting
    @Nullable
    Pair<IBinder, TransitionInfo> getDisplayChangeTransition() {
        return mDisplayChangeTransition;
    }

    private void maybeUpdatePipStateOnDisplayChange(@NonNull TransitionInfo info) {
        final TransitionInfo.Change pipChange = PipTransitionUtils.getPipChange(info);
        if (pipChange == null) return;

        final Rect endBounds = pipChange.getEndAbsBounds();
        mPipBoundsState.setBounds(endBounds);
    }
}
