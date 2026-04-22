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

package com.android.wm.shell.appzoomout;

import android.annotation.Nullable;
import android.content.Context;
import android.util.ArrayMap;
import android.view.SurfaceControl;
import android.window.DisplayAreaAppearedInfo;
import android.window.DisplayAreaInfo;
import android.window.DisplayAreaOrganizer;
import android.window.WindowContainerToken;

import com.android.internal.policy.ScreenDecorationsUtils;
import com.android.wm.shell.common.DisplayLayout;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

/** Display area organizer that manages the app zoom out UI and states. */
public class AppZoomOutDisplayAreaOrganizer extends DisplayAreaOrganizer {

    private static final float PUSHBACK_SCALE_FOR_LAUNCHER = 0.05f;
    private static final float PUSHBACK_SCALE_FOR_APP = 0.025f;
    private static final float INVALID_PROGRESS = -1;

    private final DisplayLayout mDisplayLayout = new DisplayLayout();
    private final Context mContext;
    private final float mCornerRadius;
    private final Map<WindowContainerToken, SurfaceControl> mDisplayAreaTokenMap =
            new ArrayMap<>();

    private float mProgress = INVALID_PROGRESS;
    // Denote whether the home task is focused, null when it's not yet initialized.
    @Nullable private Boolean mIsHomeTaskFocused;

    public AppZoomOutDisplayAreaOrganizer(Context context,
            DisplayLayout displayLayout, Executor mainExecutor) {
        super(mainExecutor);
        mContext = context;
        mCornerRadius = ScreenDecorationsUtils.getWindowCornerRadius(mContext);
        setDisplayLayout(displayLayout);
    }

    @Override
    public void onDisplayAreaAppeared(DisplayAreaInfo displayAreaInfo, SurfaceControl leash) {
        leash.setUnreleasedWarningCallSite(
                "AppZoomOutDisplayAreaOrganizer.onDisplayAreaAppeared");
        mDisplayAreaTokenMap.put(displayAreaInfo.token, leash);
    }

    @Override
    public void onDisplayAreaVanished(DisplayAreaInfo displayAreaInfo) {
        final SurfaceControl leash = mDisplayAreaTokenMap.get(displayAreaInfo.token);
        if (leash != null) {
            leash.release();
        }
        mDisplayAreaTokenMap.remove(displayAreaInfo.token);
    }

    public void registerOrganizer() {
        final List<DisplayAreaAppearedInfo> displayAreaInfos = registerOrganizer(
                AppZoomOutDisplayAreaOrganizer.FEATURE_APP_ZOOM_OUT);
        for (int i = 0; i < displayAreaInfos.size(); i++) {
            final DisplayAreaAppearedInfo info = displayAreaInfos.get(i);
            onDisplayAreaAppeared(info.getDisplayAreaInfo(), info.getLeash());
        }
    }

    @Override
    public void unregisterOrganizer() {
        super.unregisterOrganizer();
        reset();
    }

    void setProgress(float progress) {
        if (mProgress == progress) {
            return;
        }

        mProgress = progress;
        apply();
    }

    void setIsHomeTaskFocused(boolean isHomeTaskFocused) {
        if (mIsHomeTaskFocused != null && mIsHomeTaskFocused == isHomeTaskFocused) {
            return;
        }

        mIsHomeTaskFocused = isHomeTaskFocused;
        apply();
    }

    private void apply() {
        if (mIsHomeTaskFocused == null || mProgress == INVALID_PROGRESS) {
            return;
        }

        SurfaceControl.Transaction tx = new SurfaceControl.Transaction();
        float scale = mProgress * (mIsHomeTaskFocused
                ? PUSHBACK_SCALE_FOR_LAUNCHER : PUSHBACK_SCALE_FOR_APP);
        mDisplayAreaTokenMap.forEach((token, leash) -> updateSurface(tx, leash, scale));
        tx.apply();
    }

    void setDisplayLayout(DisplayLayout displayLayout) {
        mDisplayLayout.set(displayLayout);
    }

    private void reset() {
        setProgress(0);
        mProgress = INVALID_PROGRESS;
        mIsHomeTaskFocused = null;
    }

    private void updateSurface(SurfaceControl.Transaction tx, SurfaceControl leash, float scale) {
        if (scale == 0) {
            // Reset when scale is set back to 0.
            tx
                    .setCrop(leash, null)
                    .setScale(leash, 1, 1)
                    .setPosition(leash, 0, 0)
                    .setCornerRadius(leash, 0);
            return;
        }

        tx
                // Rounded corner can only be applied if a crop is set.
                .setCrop(leash, 0, 0, mDisplayLayout.width(), mDisplayLayout.height())
                .setScale(leash, 1 - scale, 1 - scale)
                .setPosition(leash, scale * mDisplayLayout.width() * 0.5f,
                        scale * mDisplayLayout.height() * 0.5f)
                .setCornerRadius(leash, mCornerRadius * (1 - scale));
    }

    void onRotateDisplay(Context context, int toRotation) {
        if (mDisplayLayout.rotation() == toRotation) {
            return;
        }
        mDisplayLayout.rotateTo(context.getResources(), toRotation);
    }
}
