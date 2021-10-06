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
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.util.AttributeSet;
import android.view.View;

import androidx.core.graphics.ColorUtils;

import com.android.launcher3.BaseActivity;
import com.android.launcher3.Insettable;
import com.android.launcher3.util.SystemUiController;

/**
 * Simple scrim which draws a flat color
 */
public class ScrimView extends View implements Insettable {
    private static final float STATUS_BAR_COLOR_FORCE_UPDATE_THRESHOLD = 0.9f;

    private SystemUiController mSystemUiController;

    private ScrimDrawingController mDrawingController;
    private int mBackgroundColor;
    private boolean mIsVisible = true;

    public ScrimView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setFocusable(false);
    }

    @Override
    public void setInsets(Rect insets) {
    }

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
    public void setBackgroundColor(int color) {
        mBackgroundColor = color;
        updateSysUiColors();
        super.setBackgroundColor(color);
    }

    @Override
    public void onVisibilityAggregated(boolean isVisible) {
        super.onVisibilityAggregated(isVisible);
        mIsVisible = isVisible;
    }

    public boolean isFullyOpaque() {
        return mIsVisible && getAlpha() == 1 && Color.alpha(mBackgroundColor) == 255;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (mDrawingController != null) {
            mDrawingController.drawOnScrim(canvas);
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
        final float threshold = STATUS_BAR_COLOR_FORCE_UPDATE_THRESHOLD;
        boolean forceChange = getVisibility() == VISIBLE
                && getAlpha() > threshold
                && (Color.alpha(mBackgroundColor) / 255f) > threshold;
        if (forceChange) {
            getSystemUiController().updateUiState(UI_STATE_SCRIM_VIEW, !isScrimDark());
        } else {
            getSystemUiController().updateUiState(UI_STATE_SCRIM_VIEW, 0);
        }
    }

    private SystemUiController getSystemUiController() {
        if (mSystemUiController == null) {
            mSystemUiController = BaseActivity.fromContext(getContext()).getSystemUiController();
        }
        return mSystemUiController;
    }

    private boolean isScrimDark() {
        if (!(getBackground() instanceof ColorDrawable)) {
            throw new IllegalStateException(
                    "ScrimView must have a ColorDrawable background, this one has: "
                            + getBackground());
        }
        return ColorUtils.calculateLuminance(
                ((ColorDrawable) getBackground()).getColor()) < 0.5f;
    }

    /**
     * Sets drawing controller. Invalidates ScrimView if drawerController has changed.
     */
    public void setDrawingController(ScrimDrawingController drawingController) {
        if (mDrawingController != drawingController) {
            mDrawingController = drawingController;
            invalidate();
        }
    }

    /**
     * A Utility interface allowing for other surfaces to draw on ScrimView
     */
    public interface ScrimDrawingController {
        /**
         * Called inside ScrimView#OnDraw
         */
        void drawOnScrim(Canvas canvas);
    }
}
