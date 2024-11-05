/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.wm.shell.common.bubbles;

import android.content.Context;
import android.content.res.Configuration;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.annotation.DimenRes;
import androidx.annotation.DrawableRes;
import androidx.core.content.ContextCompat;

/**
 * Circular view with a semitransparent, circular background with an 'X' inside it.
 *
 * This is used by both Bubbles and PIP as the dismiss target.
 */
public class DismissCircleView extends FrameLayout {
    @DrawableRes int mBackgroundResId;
    @DimenRes int mIconSizeResId;

    private final ImageView mIconView = new ImageView(getContext());

    public DismissCircleView(Context context) {
        super(context);
        addView(mIconView);
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        setBackground(ContextCompat.getDrawable(getContext(), mBackgroundResId));
        setViewSizes();
    }

    /**
     * Sets up view with the provided resource ids.
     * Decouples resource dependency in order to be used externally (e.g. Launcher)
     *
     * @param backgroundResId drawable resource id of the circle background
     * @param iconResId drawable resource id of the icon for the dismiss view
     * @param iconSizeResId dimen resource id of the icon size
     */
    public void setup(@DrawableRes int backgroundResId, @DrawableRes int iconResId,
            @DimenRes int iconSizeResId) {
        mBackgroundResId = backgroundResId;
        mIconSizeResId = iconSizeResId;

        setBackground(ContextCompat.getDrawable(getContext(), backgroundResId));
        mIconView.setImageDrawable(ContextCompat.getDrawable(getContext(), iconResId));
        setViewSizes();
    }

    /** Retrieves the current dimensions for the icon and circle and applies them. */
    private void setViewSizes() {
        final int iconSize = getResources().getDimensionPixelSize(mIconSizeResId);
        mIconView.setLayoutParams(
                new FrameLayout.LayoutParams(iconSize, iconSize, Gravity.CENTER));
    }
}
