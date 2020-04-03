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

package com.android.quickstep.views;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.Nullable;

import com.android.launcher3.R;

/**
 * View for showing action buttons in Overview
 */
public class OverviewActionsView extends FrameLayout {

    private final View mScreenshotButton;
    private final View mShareButton;

    /**
     * Listener for taps on the various actions.
     */
    public interface Listener {
        /** User has initiated the share actions. */
        void onShare();

        /** User has initiated the screenshot action. */
        void onScreenshot();
    }

    public OverviewActionsView(Context context) {
        this(context, null);
    }

    public OverviewActionsView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public OverviewActionsView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public OverviewActionsView(Context context, AttributeSet attrs, int defStyleAttr,
            int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        LayoutInflater.from(context).inflate(R.layout.overview_actions, this, true);
        mShareButton = findViewById(R.id.action_share);
        mScreenshotButton = findViewById(R.id.action_screenshot);
    }

    /**
     * Set listener for callbacks on action button taps.
     *
     * @param listener for callbacks, or {@code null} to clear the listener.
     */
    public void setListener(@Nullable OverviewActionsView.Listener listener) {
        mShareButton.setOnClickListener(
                listener == null ? null : view -> listener.onShare());
        mScreenshotButton.setOnClickListener(
                listener == null ? null : view -> listener.onScreenshot());
    }
}
