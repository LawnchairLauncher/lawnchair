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

import static com.android.app.animation.Interpolators.EMPHASIZED;
import static com.android.launcher3.Flags.enableOverviewIconMenu;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.drawable.AnimatedVectorDrawable;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.view.ViewAnimationUtils;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.R;
import com.android.launcher3.touch.LandscapePagedViewHandler;
import com.android.launcher3.touch.PagedOrientationHandler;
import com.android.launcher3.touch.SeascapePagedViewHandler;
import com.android.launcher3.views.ActivityContext;
import com.android.quickstep.util.RecentsOrientedState;

/**
 * An icon app menu view which can be used in place of an IconView in overview TaskViews.
 */
public class IconAppChipView extends FrameLayout implements TaskViewIcon {

    private static final int MENU_BACKGROUND_REVEAL_DURATION = 417;
    private static final int MENU_BACKGROUND_HIDE_DURATION = 333;

    private IconView mIconView;
    private TextView mIconTextView;
    private ImageView mIconArrowView;
    private ImageView mIconViewBackground;

    private int mMaxIconBackgroundWidth;
    private int mMinIconBackgroundWidth;
    private int mMaxIconBackgroundHeight;
    private int mMinIconBackgroundHeight;
    private int mIconTextMinWidth;
    private int mIconTextMaxWidth;
    private int mInnerMargin;
    private int mIconArrowSize;
    private int mIconMenuMarginStart;
    private int mArrowMaxTranslationX;

    public IconAppChipView(Context context) {
        this(context, null);
    }

    public IconAppChipView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public IconAppChipView(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public IconAppChipView(Context context, @Nullable AttributeSet attrs, int defStyleAttr,
            int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);

        mMaxIconBackgroundWidth = getResources().getDimensionPixelSize(
                R.dimen.task_thumbnail_icon_menu_max_width);
        mMinIconBackgroundWidth = getResources().getDimensionPixelSize(
                R.dimen.task_thumbnail_icon_menu_min_width);
        mMaxIconBackgroundHeight = getResources().getDimensionPixelSize(
                R.dimen.task_thumbnail_icon_menu_max_height);
        mMinIconBackgroundHeight = getResources().getDimensionPixelSize(
                R.dimen.task_thumbnail_icon_menu_min_height);
        mIconTextMaxWidth = getResources().getDimensionPixelSize(
                R.dimen.task_thumbnail_icon_menu_text_max_width);
        mInnerMargin = (int) getResources().getDimension(
                R.dimen.task_thumbnail_icon_menu_arrow_margin);
        mIconTextMinWidth = getResources().getDimensionPixelSize(
                R.dimen.task_thumbnail_icon_menu_text_width) + (2 * mInnerMargin);
        int taskIconHeight = getResources().getDimensionPixelSize(
                R.dimen.task_thumbnail_icon_menu_drawable_touch_size);
        int arrowWidth = getResources().getDimensionPixelSize(
                R.dimen.task_thumbnail_icon_menu_arrow_size);
        mIconArrowSize = getResources().getDimensionPixelSize(
                R.dimen.task_thumbnail_icon_menu_arrow_drawable_size);
        mIconMenuMarginStart = getResources().getDimensionPixelSize(
                R.dimen.task_thumbnail_icon_menu_margin);
        mArrowMaxTranslationX =
                mMaxIconBackgroundWidth - taskIconHeight - mIconTextMaxWidth + arrowWidth;
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mIconView = findViewById(R.id.icon_view);
        mIconTextView = findViewById(R.id.icon_text);
        mIconArrowView = findViewById(R.id.icon_arrow);
        mIconViewBackground = findViewById(R.id.icon_view_background);
    }

    protected IconView getIconView() {
        return mIconView;
    }

    @Override
    public void setText(CharSequence text) {
        if (mIconTextView != null) {
            mIconTextView.setText(text);
        }
    }

    @Override
    public Drawable getDrawable() {
        return mIconView == null ? null : mIconView.getDrawable();
    }

    @Override
    public void setDrawable(Drawable icon) {
        if (mIconView != null) {
            mIconView.setDrawable(icon);
        }
    }

    @Override
    public void setDrawableSize(int iconWidth, int iconHeight) {
        if (mIconView != null) {
            mIconView.setDrawableSize(iconWidth, iconHeight);
        }
    }

    @Override
    public void setIconOrientation(RecentsOrientedState orientationState, boolean isGridTask) {

        PagedOrientationHandler orientationHandler = orientationState.getOrientationHandler();
        boolean isRtl = getLayoutDirection() == LAYOUT_DIRECTION_RTL;
        DeviceProfile deviceProfile =
                ActivityContext.lookupContext(getContext()).getDeviceProfile();

        int thumbnailTopMargin = deviceProfile.overviewTaskThumbnailTopMarginPx;
        int taskIconSize = deviceProfile.overviewTaskIconSizePx;
        int taskMargin = deviceProfile.overviewTaskMarginPx;

        LayoutParams iconMenuParams = (LayoutParams) getLayoutParams();
        orientationHandler.setTaskIconMenuParams(iconMenuParams, mIconMenuMarginStart,
                thumbnailTopMargin);
        iconMenuParams.width = mMinIconBackgroundWidth;
        iconMenuParams.height = taskIconSize;
        if (orientationHandler instanceof SeascapePagedViewHandler) {
            // Use half menu height to place the pivot within the X/Y center of icon in the menu.
            setPivotX(getHeight() / 2f);
            setPivotY(getHeight() / 2f - mIconMenuMarginStart);
        } else if (orientationHandler instanceof LandscapePagedViewHandler) {
            setPivotX(getWidth());
            setPivotY(0);
        }
        // Pivot not updated for PortraitPagedViewHandler case, as it has 0 rotation.

        setTranslationY(0);
        setRotation(orientationHandler.getDegreesRotated());

        LayoutParams iconParams = (LayoutParams) mIconView.getLayoutParams();
        orientationHandler.setTaskIconParams(iconParams, taskMargin, taskIconSize,
                thumbnailTopMargin, isRtl);
        iconParams.width = iconParams.height = taskIconSize;
        iconParams.gravity = Gravity.START | Gravity.CENTER_VERTICAL;
        mIconView.setLayoutParams(iconParams);

        int iconDrawableSize = enableOverviewIconMenu()
                ? deviceProfile.overviewTaskIconAppChipMenuDrawableSizePx
                : isGridTask ? deviceProfile.overviewTaskIconDrawableSizeGridPx
                        : deviceProfile.overviewTaskIconDrawableSizePx;
        mIconView.setDrawableSize(iconDrawableSize, iconDrawableSize);

        LayoutParams iconTextParams = (LayoutParams) mIconTextView.getLayoutParams();
        orientationHandler.setTaskIconParams(iconTextParams, 0, taskIconSize,
                thumbnailTopMargin, isRtl);
        iconTextParams.width = mIconTextMaxWidth;
        iconTextParams.height = taskIconSize;
        iconTextParams.setMarginStart(taskIconSize);
        iconTextParams.topMargin = (getHeight() - mIconTextView.getHeight()) / 2;
        iconTextParams.gravity = Gravity.CENTER_VERTICAL | Gravity.START;
        mIconTextView.setLayoutParams(iconTextParams);
        mIconTextView.setRevealClip(true, 0, taskIconSize / 2f, mIconTextMinWidth);

        LayoutParams iconArrowParams = (LayoutParams) mIconArrowView.getLayoutParams();
        iconArrowParams.gravity = Gravity.CENTER_VERTICAL | Gravity.END;
        iconArrowParams.setMarginStart(taskIconSize + mIconTextMinWidth);
        iconArrowParams.setMarginEnd(mInnerMargin);
        mIconArrowView.setLayoutParams(iconArrowParams);
        mIconArrowView.getDrawable().setBounds(0, 0, mIconArrowSize, mIconArrowSize);

        LayoutParams backgroundParams = (LayoutParams) mIconViewBackground.getLayoutParams();
        backgroundParams.width = mMinIconBackgroundWidth;
        backgroundParams.height = taskIconSize;
        mIconViewBackground.setPivotX(
                isRtl ? mMinIconBackgroundWidth - (taskIconSize / 2f - mInnerMargin)
                        : taskIconSize / 2f - mInnerMargin);
        mIconViewBackground.setPivotY(taskIconSize / 2f);

        requestLayout();
    }

    @Override
    public void setIconColorTint(int color, float amount) {
        if (mIconView != null) {
            mIconView.setIconColorTint(color, amount);
        }
    }

    @Override
    public int getDrawableWidth() {
        return mIconView == null ? 0 : mIconView.getDrawableWidth();
    }

    @Override
    public int getDrawableHeight() {
        return mIconView == null ? 0 : mIconView.getDrawableHeight();
    }

    protected void revealAnim(boolean isRevealing) {
        if (isRevealing) {
            ((AnimatedVectorDrawable) mIconArrowView.getDrawable()).start();
            AnimatorSet anim = new AnimatorSet();
            anim.playTogether(
                    ViewAnimationUtils.createCircularReveal(mIconTextView, 0,
                            mIconTextView.getHeight() / 2, mIconTextMinWidth, mIconTextMaxWidth),
                    ObjectAnimator.ofFloat(mIconViewBackground, SCALE_X,
                            mMaxIconBackgroundWidth / (float) mMinIconBackgroundWidth),
                    ObjectAnimator.ofFloat(mIconViewBackground, SCALE_Y,
                            mMaxIconBackgroundHeight / (float) mMinIconBackgroundHeight),
                    ObjectAnimator.ofFloat(mIconArrowView, TRANSLATION_X,
                            isLayoutRtl() ? -mArrowMaxTranslationX : mArrowMaxTranslationX));
            anim.setDuration(MENU_BACKGROUND_REVEAL_DURATION);
            anim.setInterpolator(EMPHASIZED);
            anim.start();
        } else {
            ((AnimatedVectorDrawable) mIconArrowView.getDrawable()).reverse();
            AnimatorSet anim = new AnimatorSet();
            Animator textRevealAnim = ViewAnimationUtils.createCircularReveal(mIconTextView, 0,
                    mIconTextView.getHeight() / 2, mIconTextMaxWidth, mIconTextMinWidth);
            textRevealAnim.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    // createCircularReveal removes clip on finish, restore it here to clip text.
                    mIconTextView.setRevealClip(true, 0, mIconTextView.getHeight() / 2f,
                            mIconTextMinWidth);
                }
            });
            anim.playTogether(
                    textRevealAnim,
                    ObjectAnimator.ofFloat(mIconViewBackground, SCALE_X, 1),
                    ObjectAnimator.ofFloat(mIconViewBackground, SCALE_Y, 1),
                    ObjectAnimator.ofFloat(mIconArrowView, TRANSLATION_X, 0));
            anim.setDuration(MENU_BACKGROUND_HIDE_DURATION);
            anim.setInterpolator(EMPHASIZED);
            anim.start();
        }
    }

    @Override
    public View asView() {
        return this;
    }
}
