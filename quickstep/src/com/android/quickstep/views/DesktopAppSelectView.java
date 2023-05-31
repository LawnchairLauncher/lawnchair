/*
 * Copyright (C) 2023 The Android Open Source Project
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

import static com.android.app.animation.Interpolators.LINEAR;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;

import com.android.launcher3.Launcher;
import com.android.launcher3.R;

/**
 * Floating view show on launcher home screen that notifies the user that an app will be launched to
 * the desktop.
 */
public class DesktopAppSelectView extends LinearLayout {

    private static final int HIDE_DURATION = 83;

    private final Launcher mLauncher;

    @Nullable
    private Runnable mOnCloseCallback = null;
    private boolean mIsHideAnimationRunning;

    public DesktopAppSelectView(Context context) {
        this(context, null);
    }

    public DesktopAppSelectView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public DesktopAppSelectView(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public DesktopAppSelectView(Context context, AttributeSet attrs, int defStyleAttr,
            int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        mLauncher = Launcher.getLauncher(context);
    }

    /**
     * Show the popup on launcher home screen
     *
     * @param onCloseCallback optional callback that is called when user clicks the close button
     * @return the created view
     */
    public static DesktopAppSelectView show(Launcher launcher, @Nullable Runnable onCloseCallback) {
        DesktopAppSelectView view = (DesktopAppSelectView) launcher.getLayoutInflater().inflate(
                R.layout.floating_desktop_app_select, launcher.getDragLayer(), false);
        view.setOnCloseClickCallback(onCloseCallback);
        launcher.getDragLayer().addView(view);
        return view;
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        findViewById(R.id.close_button).setOnClickListener(v -> {
            if (!mIsHideAnimationRunning) {
                hide();
                if (mOnCloseCallback != null) {
                    mOnCloseCallback.run();
                }
            }
        });
    }

    /**
     * Hide the floating view
     */
    public void hide() {
        if (!mIsHideAnimationRunning) {
            mIsHideAnimationRunning = true;
            animate().alpha(0).setDuration(HIDE_DURATION).setInterpolator(LINEAR).withEndAction(
                    () -> {
                        mLauncher.getDragLayer().removeView(this);
                        mIsHideAnimationRunning = false;
                    });
        }
    }

    /**
     * Add a callback that is called when close button is clicked
     */
    public void setOnCloseClickCallback(@Nullable Runnable callback) {
        mOnCloseCallback = callback;
    }
}
