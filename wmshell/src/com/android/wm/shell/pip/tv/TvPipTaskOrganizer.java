/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.wm.shell.pip.tv;

import android.app.PictureInPictureParams;
import android.content.Context;

import androidx.annotation.NonNull;

import com.android.wm.shell.ShellTaskOrganizer;
import com.android.wm.shell.common.DisplayController;
import com.android.wm.shell.common.ShellExecutor;
import com.android.wm.shell.common.SyncTransactionQueue;
import com.android.wm.shell.common.pip.PipBoundsAlgorithm;
import com.android.wm.shell.common.pip.PipBoundsState;
import com.android.wm.shell.common.pip.PipDisplayLayoutState;
import com.android.wm.shell.common.pip.PipMenuController;
import com.android.wm.shell.common.pip.PipPerfHintController;
import com.android.wm.shell.common.pip.PipUiEventLogger;
import com.android.wm.shell.common.pip.PipUtils;
import com.android.wm.shell.pip.PipAnimationController;
import com.android.wm.shell.pip.PipParamsChangedForwarder;
import com.android.wm.shell.pip.PipSurfaceTransactionHelper;
import com.android.wm.shell.pip.PipTaskOrganizer;
import com.android.wm.shell.pip.PipTransitionState;
import com.android.wm.shell.splitscreen.SplitScreenController;

import java.util.Objects;
import java.util.Optional;

/**
 * TV specific changes to the PipTaskOrganizer.
 */
public class TvPipTaskOrganizer extends PipTaskOrganizer {
    private final TvPipTransition mTvPipTransition;

    public TvPipTaskOrganizer(Context context,
            @NonNull SyncTransactionQueue syncTransactionQueue,
            @NonNull PipTransitionState pipTransitionState,
            @NonNull PipBoundsState pipBoundsState,
            @NonNull PipDisplayLayoutState pipDisplayLayoutState,
            @NonNull PipBoundsAlgorithm boundsHandler,
            @NonNull PipMenuController pipMenuController,
            @NonNull PipAnimationController pipAnimationController,
            @NonNull PipSurfaceTransactionHelper surfaceTransactionHelper,
            @NonNull TvPipTransition tvPipTransition,
            @NonNull PipParamsChangedForwarder pipParamsChangedForwarder,
            Optional<SplitScreenController> splitScreenOptional,
            Optional<PipPerfHintController> pipPerfHintControllerOptional,
            @NonNull DisplayController displayController,
            @NonNull PipUiEventLogger pipUiEventLogger,
            @NonNull ShellTaskOrganizer shellTaskOrganizer,
            ShellExecutor mainExecutor) {
        super(context, syncTransactionQueue, pipTransitionState, pipBoundsState,
                pipDisplayLayoutState, boundsHandler, pipMenuController, pipAnimationController,
                surfaceTransactionHelper, tvPipTransition, pipParamsChangedForwarder,
                splitScreenOptional, pipPerfHintControllerOptional, displayController,
                pipUiEventLogger, shellTaskOrganizer, mainExecutor);
        mTvPipTransition = tvPipTransition;
    }

    @Override
    protected void applyNewPictureInPictureParams(@NonNull PictureInPictureParams params) {
        super.applyNewPictureInPictureParams(params);
        if (PipUtils.aspectRatioChanged(params.getExpandedAspectRatioFloat(),
                mPictureInPictureParams.getExpandedAspectRatioFloat())) {
            mPipParamsChangedForwarder.notifyExpandedAspectRatioChanged(
                    params.getExpandedAspectRatioFloat());
        }
        if (!Objects.equals(params.getTitle(), mPictureInPictureParams.getTitle())) {
            mPipParamsChangedForwarder.notifyTitleChanged(params.getTitle());
        }
        if (!Objects.equals(params.getSubtitle(), mPictureInPictureParams.getSubtitle())) {
            mPipParamsChangedForwarder.notifySubtitleChanged(params.getSubtitle());
        }
    }

    /**
     * Override for TV since the menu bounds affect the PiP location. Additionally, we want to
     * ensure that menu is shown immediately since it should always be visible on TV as it creates
     * a border with rounded corners around the PiP.
     */
    protected boolean shouldAttachMenuEarly() {
        return true;
    }

    protected boolean shouldAlwaysFadeIn() {
        return true;
    }

    @Override
    protected boolean shouldSyncPipTransactionWithMenu() {
        // We always have a menu visible and want to sync the pip transaction with the menu, even
        // when the menu alpha is 0 (e.g. when a fade-in animation starts).
        return true;
    }

    @Override
    protected void cancelAnimationOnTaskVanished() {
        mTvPipTransition.cancelAnimations();
        if (mLeash != null) {
            mSurfaceControlTransactionFactory.getTransaction()
                    .setAlpha(mLeash, 0f)
                    .apply();
        }
    }
}
