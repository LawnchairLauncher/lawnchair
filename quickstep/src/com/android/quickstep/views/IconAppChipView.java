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
import static com.android.app.animation.Interpolators.LINEAR;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.res.Resources;
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
import com.android.launcher3.Utilities;
import com.android.launcher3.touch.PagedOrientationHandler;
import com.android.launcher3.util.MultiValueAlpha;
import com.android.launcher3.views.ActivityContext;
import com.android.quickstep.util.RecentsOrientedState;

/**
 * An icon app menu view which can be used in place of an IconView in overview TaskViews.
 */
public class IconAppChipView extends FrameLayout implements TaskViewIcon {

    private static final int MENU_BACKGROUND_REVEAL_DURATION = 417;
    private static final int MENU_BACKGROUND_HIDE_DURATION = 333;

    private static final int NUM_ALPHA_CHANNELS = 2;
    private static final int INDEX_CONTENT_ALPHA = 0;
    private static final int INDEX_COLOR_FILTER_ALPHA = 1;

    private final MultiValueAlpha mMultiValueAlpha;

    private IconView mIconView;
    // Two textview so we can ellipsize the collapsed view and crossfade on expand to the full name.
    private TextView mIconTextCollapsedView;
    private TextView mIconTextExpandedView;
    private ImageView mIconArrowView;
    private ImageView mIconViewBackground;
    // Use separate views for the rounded corners so we can scale the background view without
    // warping the corners.
    private ImageView mIconViewBackgroundCornersStart;
    private ImageView mIconViewBackgroundCornersEnd;

    private final int mMinimumMenuSize;
    private final int mMaxMenuWidth;
    private final int mIconMenuMarginTop;
    private final int mIconMenuMarginStart;
    private final int mIconViewMarginStart;
    private final int mIconViewDrawableSize;
    private final int mIconViewDrawableMaxSize;
    private final int mIconTextMinWidth;
    private final int mIconTextMaxWidth;
    private final int mTextMaxTranslationX;
    private final int mInnerMargin;
    private final float mArrowMaxTranslationX;
    private final int mMinIconBackgroundWidth;
    private final int mMaxIconBackgroundHeight;
    private final int mMinIconBackgroundHeight;
    private final int mMaxIconBackgroundCornerRadius;
    private final float mMinIconBackgroundCornerRadius;

    private int mMaxWidth = Integer.MAX_VALUE;

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
        Resources res = getResources();
        mMultiValueAlpha = new MultiValueAlpha(this, NUM_ALPHA_CHANNELS);
        mMultiValueAlpha.setUpdateVisibility(/* updateVisibility= */ true);

        // Menu dimensions
        mMaxMenuWidth = res.getDimensionPixelSize(R.dimen.task_thumbnail_icon_menu_max_width);
        mIconMenuMarginTop = res.getDimensionPixelSize(R.dimen.task_thumbnail_icon_menu_top_margin);
        mIconMenuMarginStart = res.getDimensionPixelSize(
                R.dimen.task_thumbnail_icon_menu_start_margin);

        // Background dimensions
        mMinIconBackgroundWidth = res.getDimensionPixelSize(
                R.dimen.task_thumbnail_icon_menu_background_min_width);
        mMaxIconBackgroundHeight = res.getDimensionPixelSize(
                R.dimen.task_thumbnail_icon_menu_max_height);
        mMinIconBackgroundHeight = res.getDimensionPixelSize(
                R.dimen.task_thumbnail_icon_menu_min_height);
        mMaxIconBackgroundCornerRadius = res.getDimensionPixelSize(
                R.dimen.task_thumbnail_icon_menu_corner_radius);

        // TextView dimensions
        mInnerMargin = (int) res.getDimension(R.dimen.task_thumbnail_icon_menu_arrow_margin);
        mIconTextMinWidth = res.getDimensionPixelSize(R.dimen.task_thumbnail_icon_menu_text_width);
        mIconTextMaxWidth = res.getDimensionPixelSize(
                R.dimen.task_thumbnail_icon_menu_text_max_width);

        // IconView dimensions
        mIconViewMarginStart = res.getDimensionPixelSize(
                R.dimen.task_thumbnail_icon_view_start_margin);
        mIconViewDrawableSize = res.getDimensionPixelSize(
                R.dimen.task_thumbnail_icon_menu_drawable_size);
        mIconViewDrawableMaxSize = res.getDimensionPixelSize(
                R.dimen.task_thumbnail_icon_menu_drawable_max_size);
        mTextMaxTranslationX =
                (mIconViewDrawableMaxSize - mIconViewDrawableSize - mIconViewMarginStart)
                        + (mInnerMargin / 2);

        // ArrowView dimensions
        int iconArrowViewWidth = res.getDimensionPixelSize(
                R.dimen.task_thumbnail_icon_menu_arrow_size);
        mMinIconBackgroundCornerRadius = mMinIconBackgroundHeight / 2f;
        float maxCornerSize = Math.min(mMaxIconBackgroundHeight / 2f,
                mMaxIconBackgroundCornerRadius);
        mArrowMaxTranslationX = (mMaxMenuWidth - maxCornerSize) - (Math.min(mMaxWidth,
                mMinIconBackgroundWidth + (2 * mMinIconBackgroundCornerRadius)
                        - mMinIconBackgroundCornerRadius)) - mInnerMargin;

        // Menu dimensions
        mMinimumMenuSize =
                mIconViewMarginStart + mIconViewDrawableSize + mInnerMargin + iconArrowViewWidth;
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mIconView = findViewById(R.id.icon_view);
        mIconTextCollapsedView = findViewById(R.id.icon_text_collapsed);
        mIconTextExpandedView = findViewById(R.id.icon_text_expanded);
        mIconArrowView = findViewById(R.id.icon_arrow);
        mIconViewBackground = findViewById(R.id.icon_view_background);
        mIconViewBackgroundCornersStart = findViewById(R.id.icon_view_background_corners_start);
        mIconViewBackgroundCornersEnd = findViewById(R.id.icon_view_background_corners_end);
    }

    protected IconView getIconView() {
        return mIconView;
    }

    @Override
    public void setText(CharSequence text) {
        if (mIconTextCollapsedView != null) {
            mIconTextCollapsedView.setText(text);
        }
        if (mIconTextExpandedView != null) {
            mIconTextExpandedView.setText(text);
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

    /**
     * Sets the maximum width of this Icon Menu.
     */
    public void setMaxWidth(int maxWidth) {
        // Only the app icon and caret are visible at its minimum width.
        mMaxWidth = Math.max(maxWidth, mMinimumMenuSize);
    }

    @Override
    public void setIconOrientation(RecentsOrientedState orientationState, boolean isGridTask) {
        PagedOrientationHandler orientationHandler = orientationState.getOrientationHandler();
        boolean isRtl = isLayoutRtl();
        DeviceProfile deviceProfile =
                ActivityContext.lookupContext(getContext()).getDeviceProfile();

        // Layout Params for the Menu View
        int thumbnailTopMargin =
                deviceProfile.overviewTaskThumbnailTopMarginPx + mIconMenuMarginTop;
        LayoutParams iconMenuParams = (LayoutParams) getLayoutParams();
        orientationHandler.setIconAppChipMenuParams(this, iconMenuParams, mIconMenuMarginStart,
                thumbnailTopMargin);
        iconMenuParams.width = Math.min(mMaxWidth,
                mMinIconBackgroundWidth + (int) (2 * mMinIconBackgroundCornerRadius));
        iconMenuParams.height = mMinIconBackgroundHeight;
        setLayoutParams(iconMenuParams);

        // Layout Params for the Icon View
        LayoutParams iconParams = (LayoutParams) mIconView.getLayoutParams();
        orientationHandler.setTaskIconParams(iconParams, mIconViewMarginStart,
                mIconViewDrawableSize, thumbnailTopMargin, isRtl);
        iconParams.width = iconParams.height = mIconViewDrawableSize;
        mIconView.setLayoutParams(iconParams);
        mIconView.setDrawableSize(mIconViewDrawableSize, mIconViewDrawableSize);

        // Layout Params for the collapsed Icon Text View
        LayoutParams iconTextCollapsedParams =
                (LayoutParams) mIconTextCollapsedView.getLayoutParams();
        orientationHandler.setTaskIconParams(iconTextCollapsedParams, 0, mIconViewDrawableSize,
                thumbnailTopMargin, isRtl);
        iconTextCollapsedParams.setMarginStart(
                mIconViewDrawableSize + mIconViewMarginStart + mInnerMargin);
        iconTextCollapsedParams.topMargin = (mMinIconBackgroundHeight - mIconViewDrawableSize) / 2;
        iconTextCollapsedParams.gravity = Gravity.TOP | Gravity.START;
        iconTextCollapsedParams.width = Math.min(
                Math.max(mMaxWidth - mMinimumMenuSize - (2 * mInnerMargin), 0), mIconTextMinWidth);
        mIconTextCollapsedView.setLayoutParams(iconTextCollapsedParams);
        mIconTextCollapsedView.setAlpha(1f);

        // Layout Params for the expanded Icon Text View
        LayoutParams iconTextExpandedParams =
                (LayoutParams) mIconTextExpandedView.getLayoutParams();
        orientationHandler.setTaskIconParams(iconTextExpandedParams, 0, mIconViewDrawableSize,
                thumbnailTopMargin, isRtl);
        iconTextExpandedParams.setMarginStart(
                mIconViewDrawableSize + mIconViewMarginStart + mInnerMargin);
        iconTextExpandedParams.topMargin = (mMinIconBackgroundHeight - mIconViewDrawableSize) / 2;
        iconTextExpandedParams.gravity = Gravity.TOP | Gravity.START;
        mIconTextExpandedView.setLayoutParams(iconTextExpandedParams);
        mIconTextExpandedView.setAlpha(0f);
        mIconTextExpandedView.setRevealClip(true, 0, mIconViewDrawableSize / 2f, mIconTextMinWidth);

        // Layout Params for the Icon Arrow View
        LayoutParams iconArrowParams = (LayoutParams) mIconArrowView.getLayoutParams();
        iconArrowParams.gravity = Gravity.CENTER_VERTICAL | Gravity.END;
        iconArrowParams.setMarginEnd(mInnerMargin);
        mIconArrowView.setLayoutParams(iconArrowParams);

        // Layout Params for the Icon View Background and its corners
        int cornerlessBackgroundWidth = (int) Math.min(
                mMaxWidth - (2 * mMinIconBackgroundCornerRadius), mMinIconBackgroundWidth);
        LayoutParams backgroundCornerEndParams =
                (LayoutParams) mIconViewBackgroundCornersEnd.getLayoutParams();
        backgroundCornerEndParams.setMarginStart(cornerlessBackgroundWidth);
        mIconViewBackgroundCornersEnd.setLayoutParams(backgroundCornerEndParams);
        LayoutParams backgroundParams = (LayoutParams) mIconViewBackground.getLayoutParams();
        backgroundParams.width = cornerlessBackgroundWidth;
        backgroundParams.height = mMinIconBackgroundHeight;
        backgroundParams.setMarginStart((int) mMinIconBackgroundCornerRadius);
        mIconViewBackground.setLayoutParams(backgroundParams);
        mIconViewBackground.setPivotX(isRtl ? cornerlessBackgroundWidth : 0);
        mIconViewBackground.setPivotY(mMinIconBackgroundCornerRadius);

        // This method is called twice sometimes (like when rotating split tasks). It is called
        // once before onMeasure and onLayout, and again after onMeasure but before onLayout with
        // a new width. This happens because we update widths on rotation and on measure of
        // grouped task views. Calling requestLayout() does not guarantee a call to onMeasure if
        // it has just measured, so we explicitly call it here.
        measure(MeasureSpec.makeMeasureSpec(getLayoutParams().width, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(getLayoutParams().height, MeasureSpec.EXACTLY));
    }

    @Override
    public void setIconColorTint(int color, float amount) {
        // RecentsView's COLOR_TINT animates between 0 and 0.5f, we want to hide the app chip menu.
        float colorTintAlpha = Utilities.mapToRange(amount, 0f, 0.5f, 1f, 0f, LINEAR);
        mMultiValueAlpha.get(INDEX_COLOR_FILTER_ALPHA).setValue(colorTintAlpha);
    }

    @Override
    public void setContentAlpha(float alpha) {
        mMultiValueAlpha.get(INDEX_CONTENT_ALPHA).setValue(alpha);
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
            boolean isRtl = isLayoutRtl();
            bringToFront();
            ((AnimatedVectorDrawable) mIconArrowView.getDrawable()).start();
            AnimatorSet anim = new AnimatorSet();
            float backgroundScaleY = mMaxIconBackgroundHeight / (float) mMinIconBackgroundHeight;
            float maxCornerSize = Math.min(mMaxIconBackgroundHeight / 2f,
                    mMaxIconBackgroundCornerRadius);
            float backgroundScaleX = (mMaxMenuWidth - (2 * maxCornerSize)) / Math.min(
                    mMaxWidth - (2 * mMinIconBackgroundCornerRadius), mMinIconBackgroundWidth);
            float arrowTranslationX = mArrowMaxTranslationX + (mMinIconBackgroundWidth - Math.min(
                    mMaxWidth - (2 * mMinIconBackgroundCornerRadius), mMinIconBackgroundWidth));
            // Clip expanded text with reveal animation so it doesn't go beyond the edge of the menu
            Animator expandedTextRevealAnim = ViewAnimationUtils.createCircularReveal(
                    mIconTextExpandedView, 0, mIconTextExpandedView.getHeight() / 2, 0,
                    mIconTextMaxWidth + maxCornerSize);
            expandedTextRevealAnim.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    // createCircularReveal removes clip on finish, restore it here to clip text.
                    mIconTextExpandedView.setRevealClip(true, 0,
                            mIconTextExpandedView.getHeight() / 2f,
                            mIconTextMaxWidth + maxCornerSize);
                }
            });
            anim.playTogether(
                    expandedTextRevealAnim,
                    ObjectAnimator.ofFloat(mIconViewBackgroundCornersStart, SCALE_Y,
                            backgroundScaleY),
                    ObjectAnimator.ofFloat(mIconViewBackgroundCornersStart, SCALE_X,
                            backgroundScaleY),
                    ObjectAnimator.ofFloat(mIconViewBackgroundCornersEnd, SCALE_Y,
                            backgroundScaleY),
                    ObjectAnimator.ofFloat(mIconViewBackgroundCornersEnd, SCALE_X,
                            backgroundScaleY),
                    ObjectAnimator.ofFloat(mIconViewBackgroundCornersEnd, TRANSLATION_X,
                            isRtl ? -arrowTranslationX : arrowTranslationX),
                    ObjectAnimator.ofFloat(mIconViewBackground, SCALE_X, backgroundScaleX),
                    ObjectAnimator.ofFloat(mIconViewBackground, SCALE_Y, backgroundScaleY),
                    ObjectAnimator.ofFloat(mIconView, SCALE_X,
                            mIconViewDrawableMaxSize / (float) mIconViewDrawableSize),
                    ObjectAnimator.ofFloat(mIconView, SCALE_Y,
                            mIconViewDrawableMaxSize / (float) mIconViewDrawableSize),
                    ObjectAnimator.ofFloat(mIconTextCollapsedView, TRANSLATION_X,
                            isLayoutRtl() ? -mTextMaxTranslationX : mTextMaxTranslationX),
                    ObjectAnimator.ofFloat(mIconTextExpandedView, TRANSLATION_X,
                            isLayoutRtl() ? -mTextMaxTranslationX : mTextMaxTranslationX),
                    ObjectAnimator.ofFloat(mIconTextCollapsedView, ALPHA, 0),
                    ObjectAnimator.ofFloat(mIconTextExpandedView, ALPHA, 1),
                    ObjectAnimator.ofFloat(mIconArrowView, TRANSLATION_X,
                            isRtl ? -arrowTranslationX : arrowTranslationX));
            anim.setDuration(MENU_BACKGROUND_REVEAL_DURATION);
            anim.setInterpolator(EMPHASIZED);
            anim.start();
        } else {
            ((AnimatedVectorDrawable) mIconArrowView.getDrawable()).reverse();
            float maxCornerSize = Math.min(mMaxIconBackgroundHeight / 2f,
                    mMaxIconBackgroundCornerRadius);
            // Clip expanded text with reveal animation so it doesn't go beyond the edge of the menu
            Animator expandedTextClipAnim = ViewAnimationUtils.createCircularReveal(
                    mIconTextExpandedView, 0, mIconTextExpandedView.getHeight() / 2,
                    mIconTextMaxWidth + maxCornerSize, 0);
            expandedTextClipAnim.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    // createCircularReveal removes clip on finish, restore it here to clip text.
                    mIconTextExpandedView.setRevealClip(true, 0,
                            mIconTextExpandedView.getHeight() / 2f, 0);
                }
            });
            AnimatorSet anim = new AnimatorSet();
            anim.playTogether(
                    expandedTextClipAnim,
                    ObjectAnimator.ofFloat(mIconViewBackgroundCornersStart, SCALE_X, 1),
                    ObjectAnimator.ofFloat(mIconViewBackgroundCornersStart, SCALE_Y, 1),
                    ObjectAnimator.ofFloat(mIconViewBackgroundCornersEnd, SCALE_X, 1),
                    ObjectAnimator.ofFloat(mIconViewBackgroundCornersEnd, SCALE_Y, 1),
                    ObjectAnimator.ofFloat(mIconViewBackgroundCornersEnd, TRANSLATION_X, 0),
                    ObjectAnimator.ofFloat(mIconViewBackground, SCALE_X, 1),
                    ObjectAnimator.ofFloat(mIconViewBackground, SCALE_Y, 1),
                    ObjectAnimator.ofFloat(mIconView, SCALE_X, 1),
                    ObjectAnimator.ofFloat(mIconView, SCALE_Y, 1),
                    ObjectAnimator.ofFloat(mIconTextCollapsedView, TRANSLATION_X, 0),
                    ObjectAnimator.ofFloat(mIconTextExpandedView, TRANSLATION_X, 0),
                    ObjectAnimator.ofFloat(mIconTextCollapsedView, ALPHA, 1),
                    ObjectAnimator.ofFloat(mIconTextExpandedView, ALPHA, 0),
                    ObjectAnimator.ofFloat(mIconArrowView, TRANSLATION_X, 0));
            anim.setDuration(MENU_BACKGROUND_HIDE_DURATION);
            anim.setInterpolator(EMPHASIZED);
            anim.start();
        }
    }

    protected void reset() {
        mIconViewBackgroundCornersStart.setScaleX(1);
        mIconViewBackgroundCornersStart.setScaleY(1);
        mIconViewBackgroundCornersEnd.setScaleX(1);
        mIconViewBackgroundCornersEnd.setScaleY(1);
        mIconViewBackgroundCornersEnd.setTranslationX(0);
        mIconViewBackground.setScaleX(1);
        mIconViewBackground.setScaleY(1);
        mIconView.setScaleX(1);
        mIconView.setScaleY(1);
        mIconTextCollapsedView.setTranslationX(0);
        mIconTextExpandedView.setTranslationX(0);
        mIconTextCollapsedView.setAlpha(1);
        mIconTextExpandedView.setAlpha(0);
        mIconArrowView.setTranslationX(0);
        ((AnimatedVectorDrawable) mIconArrowView.getDrawable()).reset();
    }

    @Override
    public View asView() {
        return this;
    }
}
