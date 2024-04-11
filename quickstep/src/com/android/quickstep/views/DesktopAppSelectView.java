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

import static com.android.app.animation.Interpolators.EMPHASIZED_DECELERATE;
import static com.android.app.animation.Interpolators.LINEAR;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;

import com.android.launcher3.Launcher;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.uioverrides.QuickstepLauncher;

/**
 * Floating view show on launcher home screen that notifies the user that an app will be launched to
 * the desktop.
 */
public class DesktopAppSelectView extends LinearLayout {

    private static final int SHOW_INITIAL_HEIGHT_DP = 7;
    private static final int SHOW_CONTAINER_SCALE_DURATION = 333;
    private static final int SHOW_CONTAINER_ALPHA_DURATION = 83;
    private static final int SHOW_CONTENT_ALPHA_DELAY = 67;
    private static final int SHOW_CONTENT_ALPHA_DURATION = 83;
    private static final int HIDE_DURATION = 83;

    private final RecentsViewContainer mContainer;

    private View mText;
    private View mCloseButton;
    @Nullable
    private Runnable mOnCloseCallback;
    private AnimatorSet mShowAnimation;
    private Animator mHideAnimation;

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
        mContainer = RecentsViewContainer.containerFromContext(context);
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
        view.show();
        return view;
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mText = findViewById(R.id.desktop_app_select_text);
        mCloseButton = findViewById(R.id.close_button);
        mCloseButton.setOnClickListener(v -> {
            if (mHideAnimation == null) {
                hide();
                if (mOnCloseCallback != null) {
                    mOnCloseCallback.run();
                }
            }
        });
    }

    private void show() {
        mContainer.getDragLayer().addView(this);

        // Set up initial values
        getBackground().setAlpha(0);
        mText.setAlpha(0);
        mCloseButton.setAlpha(0);
        int initialHeightPx = Utilities.dpToPx(SHOW_INITIAL_HEIGHT_DP);
        int finalHeight = getResources().getDimensionPixelSize(
                R.dimen.desktop_mode_floating_app_select_height);
        float initialScale = initialHeightPx / (float) finalHeight;
        setScaleY(initialScale);
        setPivotY(0);

        // Animate the container
        ValueAnimator containerBackground = ValueAnimator.ofInt(0, 255);
        containerBackground.addUpdateListener(
                animation -> getBackground().setAlpha((Integer) animation.getAnimatedValue()));
        containerBackground.setDuration(SHOW_CONTAINER_ALPHA_DURATION);
        containerBackground.setInterpolator(LINEAR);

        ObjectAnimator containerSize = ObjectAnimator.ofFloat(this, SCALE_Y, 1f);
        containerSize.setDuration(SHOW_CONTAINER_SCALE_DURATION);
        containerSize.setInterpolator(EMPHASIZED_DECELERATE);

        // Animate the contents
        ObjectAnimator textAlpha = ObjectAnimator.ofFloat(mText, ALPHA, 1);
        ObjectAnimator buttonAlpha = ObjectAnimator.ofFloat(mCloseButton, ALPHA, 1);
        AnimatorSet contentAlpha = new AnimatorSet();
        contentAlpha.playTogether(textAlpha, buttonAlpha);
        contentAlpha.setStartDelay(SHOW_CONTENT_ALPHA_DELAY);
        contentAlpha.setDuration(SHOW_CONTENT_ALPHA_DURATION);
        contentAlpha.setInterpolator(LINEAR);

        // Start the animation
        mShowAnimation = new AnimatorSet();
        mShowAnimation.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                super.onAnimationEnd(animation);
                mShowAnimation = null;
            }
        });
        mShowAnimation.playTogether(containerBackground, containerSize, contentAlpha);
        mShowAnimation.start();
    }

    /**
     * Hide the floating view
     */
    public void hide() {
        if (mShowAnimation != null) {
            mShowAnimation.cancel();
        }
        mHideAnimation = ObjectAnimator.ofFloat(this, ALPHA, 0);
        mHideAnimation.setDuration(HIDE_DURATION).setInterpolator(LINEAR);
        mHideAnimation.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                super.onAnimationEnd(animation);
                mContainer.getDragLayer().removeView(DesktopAppSelectView.this);
                mHideAnimation = null;
            }
        });
        mHideAnimation.start();
    }

    /**
     * Add a callback that is called when close button is clicked
     */
    public void setOnCloseClickCallback(@Nullable Runnable callback) {
        mOnCloseCallback = callback;
    }
}
