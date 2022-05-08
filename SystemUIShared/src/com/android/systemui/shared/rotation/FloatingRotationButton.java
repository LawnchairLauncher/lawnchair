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

package com.android.systemui.shared.rotation;

import android.annotation.DimenRes;
import android.annotation.IdRes;
import android.annotation.LayoutRes;
import android.annotation.StringRes;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.ActivityInfo.Config;
import android.content.res.Resources;
import android.graphics.PixelFormat;
import android.graphics.drawable.AnimatedVectorDrawable;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.WindowManager.LayoutParams;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.FrameLayout;

import androidx.core.view.OneShotPreDrawListener;

import com.android.systemui.shared.rotation.FloatingRotationButtonPositionCalculator.Position;

/**
 * Containing logic for the rotation button on the physical left bottom corner of the screen.
 */
public class FloatingRotationButton implements RotationButton {

    private static final int MARGIN_ANIMATION_DURATION_MILLIS = 300;

    private final WindowManager mWindowManager;
    private final ViewGroup mKeyButtonContainer;
    private final FloatingRotationButtonView mKeyButtonView;

    private int mContainerSize;
    private final Context mContext;

    @StringRes
    private final int mContentDescriptionResource;
    @DimenRes
    private final int mMinMarginResource;
    @DimenRes
    private final int mRoundedContentPaddingResource;
    @DimenRes
    private final int mTaskbarLeftMarginResource;
    @DimenRes
    private final int mTaskbarBottomMarginResource;
    @DimenRes
    private final int mButtonDiameterResource;

    private AnimatedVectorDrawable mAnimatedDrawable;
    private boolean mIsShowing;
    private boolean mCanShow = true;
    private int mDisplayRotation;

    private boolean mIsTaskbarVisible = false;
    private boolean mIsTaskbarStashed = false;

    private FloatingRotationButtonPositionCalculator mPositionCalculator;

    private RotationButtonController mRotationButtonController;
    private RotationButtonUpdatesCallback mUpdatesCallback;
    private Position mPosition;

    public FloatingRotationButton(Context context, @StringRes int contentDescriptionResource,
            @LayoutRes int layout, @IdRes int keyButtonId, @DimenRes int minMargin,
            @DimenRes int roundedContentPadding, @DimenRes int taskbarLeftMargin,
            @DimenRes int taskbarBottomMargin, @DimenRes int buttonDiameter,
            @DimenRes int rippleMaxWidth) {
        mWindowManager = context.getSystemService(WindowManager.class);
        mKeyButtonContainer = (ViewGroup) LayoutInflater.from(context).inflate(layout, null);
        mKeyButtonView = mKeyButtonContainer.findViewById(keyButtonId);
        mKeyButtonView.setVisibility(View.VISIBLE);
        mKeyButtonView.setContentDescription(context.getString(contentDescriptionResource));
        mKeyButtonView.setRipple(rippleMaxWidth);

        mContext = context;

        mContentDescriptionResource = contentDescriptionResource;
        mMinMarginResource = minMargin;
        mRoundedContentPaddingResource = roundedContentPadding;
        mTaskbarLeftMarginResource = taskbarLeftMargin;
        mTaskbarBottomMarginResource = taskbarBottomMargin;
        mButtonDiameterResource = buttonDiameter;

        updateDimensionResources();
    }

    private void updateDimensionResources() {
        Resources res = mContext.getResources();

        int defaultMargin = Math.max(
                res.getDimensionPixelSize(mMinMarginResource),
                res.getDimensionPixelSize(mRoundedContentPaddingResource));

        int taskbarMarginLeft =
                res.getDimensionPixelSize(mTaskbarLeftMarginResource);
        int taskbarMarginBottom =
                res.getDimensionPixelSize(mTaskbarBottomMarginResource);

        mPositionCalculator = new FloatingRotationButtonPositionCalculator(defaultMargin,
                taskbarMarginLeft, taskbarMarginBottom);

        final int diameter = res.getDimensionPixelSize(mButtonDiameterResource);
        mContainerSize = diameter + Math.max(defaultMargin, Math.max(taskbarMarginLeft,
                taskbarMarginBottom));
    }

    @Override
    public void setRotationButtonController(RotationButtonController rotationButtonController) {
        mRotationButtonController = rotationButtonController;
        updateIcon(mRotationButtonController.getLightIconColor(),
                mRotationButtonController.getDarkIconColor());
    }

    @Override
    public void setUpdatesCallback(RotationButtonUpdatesCallback updatesCallback) {
        mUpdatesCallback = updatesCallback;
    }

    @Override
    public View getCurrentView() {
        return mKeyButtonView;
    }

    @Override
    public boolean show() {
        if (!mCanShow || mIsShowing) {
            return false;
        }

        mIsShowing = true;

        final LayoutParams layoutParams = adjustViewPositionAndCreateLayoutParams();
        mWindowManager.addView(mKeyButtonContainer, layoutParams);

        if (mAnimatedDrawable != null) {
            mAnimatedDrawable.reset();
            mAnimatedDrawable.start();
        }

        // Notify about visibility only after first traversal so we can properly calculate
        // the touch region for the button
        OneShotPreDrawListener.add(mKeyButtonView, () -> {
            if (mIsShowing && mUpdatesCallback != null) {
                mUpdatesCallback.onVisibilityChanged(true);
            }
        });

        return true;
    }

    @Override
    public boolean hide() {
        if (!mIsShowing) {
            return false;
        }
        mWindowManager.removeViewImmediate(mKeyButtonContainer);
        mIsShowing = false;
        if (mUpdatesCallback != null) {
            mUpdatesCallback.onVisibilityChanged(false);
        }
        return true;
    }

    @Override
    public boolean isVisible() {
        return mIsShowing;
    }

    @Override
    public void updateIcon(int lightIconColor, int darkIconColor) {
        mAnimatedDrawable = (AnimatedVectorDrawable) mKeyButtonView.getContext()
                .getDrawable(mRotationButtonController.getIconResId());
        mKeyButtonView.setImageDrawable(mAnimatedDrawable);
        mKeyButtonView.setColors(lightIconColor, darkIconColor);
    }

    @Override
    public void setOnClickListener(View.OnClickListener onClickListener) {
        mKeyButtonView.setOnClickListener(onClickListener);
    }

    @Override
    public void setOnHoverListener(View.OnHoverListener onHoverListener) {
        mKeyButtonView.setOnHoverListener(onHoverListener);
    }

    @Override
    public Drawable getImageDrawable() {
        return mAnimatedDrawable;
    }

    @Override
    public void setDarkIntensity(float darkIntensity) {
        mKeyButtonView.setDarkIntensity(darkIntensity);
    }

    @Override
    public void setCanShowRotationButton(boolean canShow) {
        mCanShow = canShow;
        if (!mCanShow) {
            hide();
        }
    }

    @Override
    public void onTaskbarStateChanged(boolean taskbarVisible, boolean taskbarStashed) {
        mIsTaskbarVisible = taskbarVisible;
        mIsTaskbarStashed = taskbarStashed;

        if (!mIsShowing) return;

        final Position newPosition = mPositionCalculator
                .calculatePosition(mDisplayRotation, mIsTaskbarVisible, mIsTaskbarStashed);

        if (newPosition.getTranslationX() != mPosition.getTranslationX()
                || newPosition.getTranslationY() != mPosition.getTranslationY()) {
            updateTranslation(newPosition, /* animate */ true);
            mPosition = newPosition;
        }
    }

    /**
     * Updates resources that could be changed in runtime, should be called on configuration
     * change with changes diff integer mask
     * @param configurationChanges - configuration changes with flags from ActivityInfo e.g.
     * {@link android.content.pm.ActivityInfo#CONFIG_DENSITY}
     */
    public void onConfigurationChanged(@Config int configurationChanges) {
        if ((configurationChanges & ActivityInfo.CONFIG_DENSITY) != 0
                || (configurationChanges & ActivityInfo.CONFIG_SCREEN_SIZE) != 0) {
            updateDimensionResources();

            if (mIsShowing) {
                final LayoutParams layoutParams = adjustViewPositionAndCreateLayoutParams();
                mWindowManager.updateViewLayout(mKeyButtonContainer, layoutParams);
            }
        }

        if ((configurationChanges & ActivityInfo.CONFIG_LOCALE) != 0) {
            mKeyButtonView.setContentDescription(mContext.getString(mContentDescriptionResource));
        }
    }

    private LayoutParams adjustViewPositionAndCreateLayoutParams() {
        final LayoutParams lp = new LayoutParams(
                mContainerSize,
                mContainerSize,
                /* xpos */ 0, /* ypos */ 0, LayoutParams.TYPE_NAVIGATION_BAR_PANEL,
                LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);

        lp.privateFlags |= LayoutParams.SYSTEM_FLAG_SHOW_FOR_ALL_USERS;
        lp.setTitle("FloatingRotationButton");
        lp.setFitInsetsTypes(/* types */ 0);

        mDisplayRotation = mWindowManager.getDefaultDisplay().getRotation();
        mPosition = mPositionCalculator
                .calculatePosition(mDisplayRotation, mIsTaskbarVisible, mIsTaskbarStashed);

        lp.gravity = mPosition.getGravity();
        ((FrameLayout.LayoutParams) mKeyButtonView.getLayoutParams()).gravity =
                mPosition.getGravity();

        updateTranslation(mPosition, /* animate */ false);

        return lp;
    }

    private void updateTranslation(Position position, boolean animate) {
        final int translationX = position.getTranslationX();
        final int translationY = position.getTranslationY();

        if (animate) {
            mKeyButtonView
                    .animate()
                    .translationX(translationX)
                    .translationY(translationY)
                    .setDuration(MARGIN_ANIMATION_DURATION_MILLIS)
                    .setInterpolator(new AccelerateDecelerateInterpolator())
                    .withEndAction(() -> {
                        if (mUpdatesCallback != null && mIsShowing) {
                            mUpdatesCallback.onPositionChanged();
                        }
                    })
                    .start();
        } else {
            mKeyButtonView.setTranslationX(translationX);
            mKeyButtonView.setTranslationY(translationY);
        }
    }
}
