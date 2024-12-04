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

package com.android.wm.shell.compatui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.annotation.IdRes;
import android.annotation.NonNull;
import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.Interpolator;
import android.view.animation.LinearInterpolator;
import android.view.animation.PathInterpolator;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.wm.shell.R;

/**
 * Layout for the user aspect ratio button which opens the app list page in settings
 * and allows users to change apps aspect ratio.
 */
public class UserAspectRatioSettingsLayout extends LinearLayout {

    private static final Interpolator LINEAR_INTERPOLATOR = new LinearInterpolator();

    private static final Interpolator PATH_INTERPOLATOR =
            new PathInterpolator(0.2f, 0f, 0f, 1f);

    private static final float ALPHA_FULL_TRANSPARENT = 0f;

    private static final float ALPHA_FULL_OPAQUE = 1f;

    private static final float SCALE_START = 0.8f;

    private static final float SCALE_END = 1f;

    private static final long FADE_ANIMATION_DURATION_MS = 167;

    private static final long SCALE_ANIMATION_DURATION_MS = 300;

    private static final String ALPHA_PROPERTY_NAME = "alpha";

    private static final String SCALE_X_PROPERTY_NAME = "scaleX";

    private static final String SCALE_Y_PROPERTY_NAME = "scaleY";

    private UserAspectRatioSettingsWindowManager mWindowManager;

    public UserAspectRatioSettingsLayout(Context context) {
        this(context, null);
    }

    public UserAspectRatioSettingsLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public UserAspectRatioSettingsLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public UserAspectRatioSettingsLayout(Context context, AttributeSet attrs, int defStyleAttr,
            int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    void inject(@NonNull UserAspectRatioSettingsWindowManager windowManager) {
        mWindowManager = windowManager;
    }

    void setUserAspectRatioSettingsHintVisibility(boolean show) {
        setViewVisibility(R.id.user_aspect_ratio_settings_hint, show);
    }

    void setUserAspectRatioButtonVisibility(boolean show) {
        setViewVisibility(R.id.user_aspect_ratio_settings_button, show);
        // Hint should never be visible without button.
        if (!show) {
            setUserAspectRatioSettingsHintVisibility(/* show= */ false);
        }
    }

    private void setViewVisibility(@IdRes int resId, boolean show) {
        final View view = findViewById(resId);
        int visibility = show ? View.VISIBLE : View.GONE;
        if (view.getVisibility() == visibility) {
            return;
        }
        if (show) {
            showItem(view);
        } else {
            hideItem(view);
        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        // Need to relayout after changes like hiding / showing a hint since they affect size.
        // Doing this directly in setUserAspectRatioButtonVisibility can result in flaky animation.
        mWindowManager.relayout();
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        final ImageButton userAspectRatioButton =
                findViewById(R.id.user_aspect_ratio_settings_button);
        userAspectRatioButton.setOnClickListener(
                view -> mWindowManager.onUserAspectRatioSettingsButtonClicked());
        userAspectRatioButton.setOnLongClickListener(view -> {
            mWindowManager.onUserAspectRatioSettingsButtonLongClicked();
            return true;
        });

        final LinearLayout sizeCompatHint = findViewById(R.id.user_aspect_ratio_settings_hint);
        ((TextView) sizeCompatHint.findViewById(R.id.compat_mode_hint_text))
                .setText(R.string.user_aspect_ratio_settings_button_hint);
        sizeCompatHint.setOnClickListener(
                view -> setUserAspectRatioSettingsHintVisibility(/* show= */ false));
    }

    private void showItem(@NonNull View view) {
        final AnimatorSet animatorSet = new AnimatorSet();
        final ObjectAnimator fadeIn = ObjectAnimator.ofFloat(view, ALPHA_PROPERTY_NAME,
                ALPHA_FULL_TRANSPARENT, ALPHA_FULL_OPAQUE);
        fadeIn.setDuration(FADE_ANIMATION_DURATION_MS);
        fadeIn.setInterpolator(LINEAR_INTERPOLATOR);
        final ObjectAnimator scaleY =
                ObjectAnimator.ofFloat(view, SCALE_Y_PROPERTY_NAME, SCALE_START, SCALE_END);
        final ObjectAnimator scaleX =
                ObjectAnimator.ofFloat(view, SCALE_X_PROPERTY_NAME, SCALE_START, SCALE_END);
        scaleX.setDuration(SCALE_ANIMATION_DURATION_MS);
        scaleX.setInterpolator(PATH_INTERPOLATOR);
        scaleY.setDuration(SCALE_ANIMATION_DURATION_MS);
        scaleY.setInterpolator(PATH_INTERPOLATOR);
        animatorSet.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                view.setVisibility(View.VISIBLE);
            }
        });
        animatorSet.playTogether(fadeIn, scaleY, scaleX);
        animatorSet.start();
    }

    private void hideItem(@NonNull View view) {
        final ObjectAnimator fadeOut = ObjectAnimator.ofFloat(view, ALPHA_PROPERTY_NAME,
                ALPHA_FULL_OPAQUE, ALPHA_FULL_TRANSPARENT);
        fadeOut.setDuration(FADE_ANIMATION_DURATION_MS);
        fadeOut.setInterpolator(LINEAR_INTERPOLATOR);
        fadeOut.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                view.setVisibility(View.GONE);
            }
        });
        fadeOut.start();
    }
}
