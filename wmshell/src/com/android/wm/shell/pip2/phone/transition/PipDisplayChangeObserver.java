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
import android.os.Bundle;
import android.os.IBinder;
import android.util.ArrayMap;
import android.view.SurfaceControl;
import android.window.TransitionInfo;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import com.android.wm.shell.common.pip.PipBoundsState;
import com.android.wm.shell.pip2.phone.PipTransitionState;
import com.android.wm.shell.shared.TransitionUtil;
import com.android.wm.shell.transition.Transitions;

/**
 * An implementation of {@link Transitions.TransitionObserver} to track external display change
 * transitions that might affect a PiP task as well.
 */
public class PipDisplayChangeObserver implements Transitions.TransitionObserver {
    private final PipTransitionState mPipTransitionState;
    private final PipBoundsState mPipBoundsState;
    private final ArrayMap<IBinder, TransitionInfo> mDisplayChangeTransitions = new ArrayMap<>();

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
            mDisplayChangeTransitions.put(transition, info);
        }
    }

    @Override
    public void onTransitionStarting(@NonNull IBinder transition) {
        if (mDisplayChangeTransitions.containsKey(transition)) {
            // The display change transition is being played now, so it's safe to reset
            // the display change scheduled flag.
            mPipTransitionState.setIsDisplayChangeScheduled(false);
            onDisplayChangeStarting(mDisplayChangeTransitions.get(transition));
        }
    }

    @Override
    public void onTransitionMerged(@NonNull IBinder merged, @NonNull IBinder playing) {
        if (mDisplayChangeTransitions.containsKey(merged)) {
            onDisplayChangeFinished(mDisplayChangeTransitions.get(merged));
            mDisplayChangeTransitions.remove(merged);
        }
    }

    @Override
    public void onTransitionFinished(@NonNull IBinder transition, boolean aborted) {
        if (mDisplayChangeTransitions.containsKey(transition)) {
            onDisplayChangeFinished(mDisplayChangeTransitions.get(transition));
            mDisplayChangeTransitions.remove(transition);
        }
    }

    @VisibleForTesting
    ArrayMap<IBinder, TransitionInfo> getDisplayChangeTransitions() {
        return mDisplayChangeTransitions;
    }

    private void onDisplayChangeStarting(@NonNull TransitionInfo info) {
        final TransitionInfo.Change pipChange = PipTransitionUtils.getPipChange(info);
        if (pipChange == null || !mPipTransitionState.isPipStateIdle()) return;

        // We do not care about the extras in this case - just make sure to send a non-empty one;
        // since otherwise PipTransitionState might throw an exception.
        final Bundle extra = new Bundle();
        extra.putInt("", 0);
        // It is safe to advance to PiP bounds changing states, since this display change
        // transition can never start playing while another PiP transition is already playing.
        // This should help us block any interactions with PiP during this period.
        mPipTransitionState.setState(PipTransitionState.SCHEDULED_BOUNDS_CHANGE, extra);
    }

    private void onDisplayChangeFinished(@NonNull TransitionInfo info) {
        final TransitionInfo.Change pipChange = PipTransitionUtils.getPipChange(info);
        if (pipChange == null || !mPipTransitionState.isInPip()) return;

        final Rect endBounds = pipChange.getEndAbsBounds();
        mPipBoundsState.setBounds(endBounds);
        // Indicate that this display changing transition that was altering PiP bounds is over.
        mPipTransitionState.setState(PipTransitionState.CHANGED_PIP_BOUNDS);
    }
}
