/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.launcher3.widget;

import android.content.Context;
import android.graphics.Outline;
import android.graphics.Rect;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.RemoteViews;

import androidx.annotation.UiThread;

import com.android.launcher3.R;
import com.android.launcher3.util.Executors;

/**
 * Launcher AppWidgetHostView with support for rounded corners and a fallback View.
 */
public abstract class BaseLauncherAppWidgetHostView extends NavigableAppWidgetHostView {

    protected final LayoutInflater mInflater;

    private final Rect mEnforcedRectangle = new Rect();
    private final float mEnforcedCornerRadius;
    private final ViewOutlineProvider mCornerRadiusEnforcementOutline = new ViewOutlineProvider() {
        @Override
        public void getOutline(View view, Outline outline) {
            if (mEnforcedRectangle.isEmpty() || mEnforcedCornerRadius <= 0) {
                outline.setEmpty();
            } else {
                outline.setRoundRect(mEnforcedRectangle, mEnforcedCornerRadius);
            }
        }
    };

    public BaseLauncherAppWidgetHostView(Context context) {
        super(context);

        setExecutor(Executors.THREAD_POOL_EXECUTOR);

        mInflater = LayoutInflater.from(context);
        mEnforcedCornerRadius = RoundedCornerEnforcement.computeEnforcedRadius(getContext());
    }

    @Override
    protected View getErrorView() {
        return mInflater.inflate(R.layout.appwidget_error, this, false);
    }

    /**
     * Fall back to error layout instead of showing widget.
     */
    public void switchToErrorView() {
        // Update the widget with 0 Layout id, to reset the view to error view.
        updateAppWidget(new RemoteViews(getAppWidgetInfo().provider.getPackageName(), 0));
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        try {
            super.onLayout(changed, left, top, right, bottom);
        } catch (final RuntimeException e) {
            post(this::switchToErrorView);
        }

        enforceRoundedCorners();
    }

    @UiThread
    private void resetRoundedCorners() {
        setOutlineProvider(ViewOutlineProvider.BACKGROUND);
        setClipToOutline(false);
    }

    @UiThread
    private void enforceRoundedCorners() {
        if (mEnforcedCornerRadius <= 0 || !RoundedCornerEnforcement.isRoundedCornerEnabled()) {
            resetRoundedCorners();
            return;
        }
        View background = RoundedCornerEnforcement.findBackground(this);
        if (background == null
                || RoundedCornerEnforcement.hasAppWidgetOptedOut(this, background)) {
            resetRoundedCorners();
            return;
        }
        RoundedCornerEnforcement.computeRoundedRectangle(this,
                background,
                mEnforcedRectangle);
        setOutlineProvider(mCornerRadiusEnforcementOutline);
        setClipToOutline(true);
        invalidateOutline();
    }

    /** Returns the corner radius currently enforced, in pixels. */
    public float getEnforcedCornerRadius() {
        return mEnforcedCornerRadius;
    }

    /** Returns true if the corner radius are enforced for this App Widget. */
    public boolean hasEnforcedCornerRadius() {
        return getClipToOutline();
    }
}
