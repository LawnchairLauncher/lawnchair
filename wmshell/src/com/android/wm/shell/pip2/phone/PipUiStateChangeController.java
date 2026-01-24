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

import android.app.ActivityTaskManager;
import android.app.Flags;
import android.app.PictureInPictureUiState;
import android.os.Bundle;
import android.os.RemoteException;

import androidx.annotation.Nullable;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.protolog.ProtoLog;
import com.android.wm.shell.protolog.ShellProtoLogGroup;

import java.util.function.Consumer;

/**
 * Controller class manages the {@link android.app.PictureInPictureUiState} callbacks sent to app.
 */
public class PipUiStateChangeController implements
        PipTransitionState.PipTransitionStateChangedListener {

    private final PipTransitionState mPipTransitionState;

    private Consumer<PictureInPictureUiState> mPictureInPictureUiStateConsumer;

    public PipUiStateChangeController(PipTransitionState pipTransitionState) {
        mPipTransitionState = pipTransitionState;
        mPipTransitionState.addPipTransitionStateChangedListener(this);
        mPictureInPictureUiStateConsumer = pictureInPictureUiState -> {
            try {
                ActivityTaskManager.getService().onPictureInPictureUiStateChanged(
                        pictureInPictureUiState);
            } catch (RemoteException | IllegalStateException e) {
                ProtoLog.e(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                        "Failed to send PictureInPictureUiState.");
            }
        };
    }

    @Override
    public void onPipTransitionStateChanged(@PipTransitionState.TransitionState int oldState,
            @PipTransitionState.TransitionState int newState, @Nullable Bundle extra) {
        if (newState == PipTransitionState.SWIPING_TO_PIP) {
            onIsTransitioningToPipUiStateChange(true /* isTransitioningToPip */);
        } else if (newState == PipTransitionState.ENTERING_PIP
                && !mPipTransitionState.isInSwipePipToHomeTransition()) {
            onIsTransitioningToPipUiStateChange(true /* isTransitioningToPip */);
        } else if (newState == PipTransitionState.ENTERED_PIP) {
            onIsTransitioningToPipUiStateChange(false /* isTransitioningToPip */);
        }
    }

    @VisibleForTesting
    void setPictureInPictureUiStateConsumer(Consumer<PictureInPictureUiState> consumer) {
        mPictureInPictureUiStateConsumer = consumer;
    }

    private void onIsTransitioningToPipUiStateChange(boolean isTransitioningToPip) {
        if (Flags.enablePipUiStateCallbackOnEntering()
                && mPictureInPictureUiStateConsumer != null) {
            mPictureInPictureUiStateConsumer.accept(new PictureInPictureUiState.Builder()
                    .setTransitioningToPip(isTransitioningToPip)
                    .build());
        }
    }
}
