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
 * limitations under the License.
 */
package com.android.launcher3.views;

import static com.android.launcher3.anim.Interpolators.ACCEL;
import static com.android.launcher3.util.SystemUiController.UI_STATE_SCRIM_VIEW;

import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.Interpolator;

import androidx.core.graphics.ColorUtils;

import com.android.launcher3.Insettable;
import com.android.launcher3.Launcher;
import com.android.launcher3.R;
import com.android.launcher3.anim.Interpolators;
import com.android.launcher3.util.SystemUiController;
import com.android.launcher3.util.Themes;

/**
 * Simple scrim which draws a flat color
 */
public class ScrimView extends View implements Insettable {
    private static final float STATUS_BAR_COLOR_FORCE_UPDATE_THRESHOLD = 0.9f;


    private static final float TINT_DECAY_MULTIPLIER = .5f;

    //min progress for scrim to become visible
    private static final float SCRIM_VISIBLE_THRESHOLD = .1f;
    //max progress where scrim alpha animates.
    private static final float SCRIM_SOLID_THRESHOLD = .5f;
    private final Interpolator mScrimInterpolator = Interpolators.clampToProgress(ACCEL,
            SCRIM_VISIBLE_THRESHOLD,
            SCRIM_SOLID_THRESHOLD);

    private final boolean mIsScrimDark;
    private SystemUiController mSystemUiController;

    private float mProgress;

    public ScrimView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mIsScrimDark = ColorUtils.calculateLuminance(
                Themes.getAttrColor(context, R.attr.allAppsScrimColor)) < 0.5f;
        setFocusable(false);
    }

    @Override
    public void setInsets(Rect insets) {
    }

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }

    /**
     * Set progress of scrim animation.
     * Note: progress should range from 0 for transparent to 1 for solid
     */
    public void setProgress(float progress) {
        if (mProgress != progress) {
            mProgress = progress;
            setAlpha(mScrimInterpolator.getInterpolation(progress));
        }
    }

    @Override
    protected void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        updateSysUiColors();
    }

    private void updateSysUiColors() {
        // Use a light system UI (dark icons) if all apps is behind at least half of the
        // status bar.
        boolean forceChange =
                getVisibility() == VISIBLE && getAlpha() > STATUS_BAR_COLOR_FORCE_UPDATE_THRESHOLD;
        if (forceChange) {
            getSystemUiController().updateUiState(UI_STATE_SCRIM_VIEW, !mIsScrimDark);
        } else {
            getSystemUiController().updateUiState(UI_STATE_SCRIM_VIEW, 0);
        }
    }

    private SystemUiController getSystemUiController() {
        if (mSystemUiController == null) {
            mSystemUiController = Launcher.getLauncher(getContext()).getSystemUiController();
        }
        return mSystemUiController;
    }
}
