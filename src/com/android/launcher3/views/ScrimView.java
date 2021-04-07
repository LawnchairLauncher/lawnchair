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

import static com.android.launcher3.util.SystemUiController.UI_STATE_SCRIM_VIEW;

import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;

import androidx.core.graphics.ColorUtils;

import com.android.launcher3.Insettable;
import com.android.launcher3.Launcher;
import com.android.launcher3.R;
import com.android.launcher3.util.SystemUiController;
import com.android.launcher3.util.Themes;

/**
 * Simple scrim which draws a flat color
 */
public class ScrimView extends View implements Insettable {
    private static final float STATUS_BAR_COLOR_FORCE_UPDATE_THRESHOLD = 0.9f;

    private final boolean mIsScrimDark;
    private SystemUiController mSystemUiController;

    public ScrimView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mIsScrimDark = ColorUtils.calculateLuminance(
                Themes.getAttrColor(context, R.attr.allAppsScrimColor)) < 0.5f;
        setFocusable(false);
    }

    @Override
    public void setInsets(Rect insets) { }

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }

    @Override
    protected boolean onSetAlpha(int alpha) {
        updateSysUiColors();
        return super.onSetAlpha(alpha);
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
