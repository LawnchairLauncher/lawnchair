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
import static com.android.launcher3.LauncherAnimUtils.SCALE_PROPERTY;
import static com.android.launcher3.LauncherAnimUtils.VIEW_TRANSLATE_X;
import static com.android.launcher3.LauncherAnimUtils.VIEW_TRANSLATE_Y;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.RectEvaluator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Outline;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewAnimationUtils;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.util.MultiPropertyFactory;
import com.android.launcher3.util.MultiValueAlpha;
import com.android.quickstep.orientation.RecentsPagedOrientationHandler;
import com.android.quickstep.util.RecentsOrientedState;

/**
 * An icon app menu view which can be used in place of an IconView in overview TaskViews.
 */
public class IconAppChipView extends FrameLayout implements TaskViewIcon {

    private static final int MENU_BACKGROUND_REVEAL_DURATION = 417;
    private static final int MENU_BACKGROUND_HIDE_DURATION = 333;

    private static final int NUM_ALPHA_CHANNELS = 3;
    private static final int INDEX_CONTENT_ALPHA = 0;
    private static final int INDEX_COLOR_FILTER_ALPHA = 1;
    private static final int INDEX_MODAL_ALPHA = 2;

    private final MultiValueAlpha mMultiValueAlpha;

    private View mMenuAnchorView;
    private IconView mIconView;
    // Two textview so we can ellipsize the collapsed view and crossfade on expand to the full name.
    private TextView mIconTextCollapsedView;
    private TextView mIconTextExpandedView;
    private ImageView mIconArrowView;
    private final Rect mBackgroundRelativeLtrLocation = new Rect();
    final RectEvaluator mBackgroundAnimationRectEvaluator =
            new RectEvaluator(mBackgroundRelativeLtrLocation);
    private final int mCollapsedMenuDefaultWidth;
    private final int mExpandedMenuDefaultWidth;
    private final int mCollapsedMenuDefaultHeight;
    private final int mExpandedMenuDefaultHeight;
    private final int mIconMenuMarginTopStart;
    private final int mMenuToChipGap;
    private final int mBackgroundMarginTopStart;
    private final int mAppNameHorizontalMargin;
    private final int mIconViewMarginStart;
    private final int mAppIconSize;
    private final int mArrowSize;
    private final int mIconViewDrawableExpandedSize;
    private final int mArrowMarginEnd;
    private AnimatorSet mAnimator;

    private int mMaxWidth = Integer.MAX_VALUE;

    private static final int INDEX_SPLIT_TRANSLATION = 0;
    private static final int INDEX_MENU_TRANSLATION = 1;
    private static final int INDEX_COUNT_TRANSLATION = 2;

    private final MultiPropertyFactory<View> mViewTranslationX;
    private final MultiPropertyFactory<View> mViewTranslationY;

    /**
     * Gets the view split x-axis translation
     */
    public MultiPropertyFactory<View>.MultiProperty getSplitTranslationX() {
        return mViewTranslationX.get(INDEX_SPLIT_TRANSLATION);
    }

    /**
     * Sets the view split x-axis translation
     * @param translationX x-axis translation
     */
    public void setSplitTranslationX(float translationX) {
        getSplitTranslationX().setValue(translationX);
    }

    /**
     * Gets the view split y-axis translation
     */
    public MultiPropertyFactory<View>.MultiProperty getSplitTranslationY() {
        return mViewTranslationY.get(INDEX_SPLIT_TRANSLATION);
    }

    /**
     * Sets the view split y-axis translation
     * @param translationY y-axis translation
     */
    public void setSplitTranslationY(float translationY) {
        getSplitTranslationY().setValue(translationY);
    }

    /**
     * Gets the menu x-axis translation for split task
     */
    public MultiPropertyFactory<View>.MultiProperty getMenuTranslationX() {
        return mViewTranslationX.get(INDEX_MENU_TRANSLATION);
    }

    /**
     * Gets the menu y-axis translation for split task
     */
    public MultiPropertyFactory<View>.MultiProperty getMenuTranslationY() {
        return mViewTranslationY.get(INDEX_MENU_TRANSLATION);
    }

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
        mCollapsedMenuDefaultWidth =
                res.getDimensionPixelSize(R.dimen.task_thumbnail_icon_menu_collapsed_width);
        mExpandedMenuDefaultWidth =
                res.getDimensionPixelSize(R.dimen.task_thumbnail_icon_menu_expanded_width);
        mCollapsedMenuDefaultHeight =
                res.getDimensionPixelSize(R.dimen.task_thumbnail_icon_menu_collapsed_height);
        mExpandedMenuDefaultHeight =
                res.getDimensionPixelSize(R.dimen.task_thumbnail_icon_menu_expanded_height);
        mIconMenuMarginTopStart = res.getDimensionPixelSize(
                R.dimen.task_thumbnail_icon_menu_expanded_top_start_margin);
        mMenuToChipGap = res.getDimensionPixelSize(
                R.dimen.task_thumbnail_icon_menu_expanded_gap);

        // Background dimensions
        mBackgroundMarginTopStart = res.getDimensionPixelSize(
                R.dimen.task_thumbnail_icon_menu_background_margin_top_start);

        // Contents dimensions
        mAppNameHorizontalMargin = res.getDimensionPixelSize(
                R.dimen.task_thumbnail_icon_menu_app_name_margin_horizontal_collapsed);
        mArrowMarginEnd = res.getDimensionPixelSize(R.dimen.task_thumbnail_icon_menu_arrow_margin);
        mIconViewMarginStart = res.getDimensionPixelSize(
                R.dimen.task_thumbnail_icon_view_start_margin);
        mAppIconSize = res.getDimensionPixelSize(
                R.dimen.task_thumbnail_icon_menu_app_icon_collapsed_size);
        mArrowSize = res.getDimensionPixelSize(
                R.dimen.task_thumbnail_icon_menu_arrow_size);
        mIconViewDrawableExpandedSize = res.getDimensionPixelSize(
                R.dimen.task_thumbnail_icon_menu_app_icon_expanded_size);

        mViewTranslationX = new MultiPropertyFactory<>(this, VIEW_TRANSLATE_X,
                INDEX_COUNT_TRANSLATION,
                Float::sum);
        mViewTranslationY = new MultiPropertyFactory<>(this, VIEW_TRANSLATE_Y,
                INDEX_COUNT_TRANSLATION,
                Float::sum);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mIconView = findViewById(R.id.icon_view);
        mIconTextCollapsedView = findViewById(R.id.icon_text_collapsed);
        mIconTextExpandedView = findViewById(R.id.icon_text_expanded);
        mIconArrowView = findViewById(R.id.icon_arrow);
        mMenuAnchorView = findViewById(R.id.icon_view_menu_anchor);
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
     * Sets the maximum width of this Icon Menu. This is usually used when space is limited for
     * split screen.
     */
    public void setMaxWidth(int maxWidth) {
        // Width showing only the app icon and arrow. Max width should not be set to less than this.
        int minimumMaxWidth = mIconViewMarginStart + mAppIconSize + mArrowSize + mArrowMarginEnd;
        mMaxWidth = Math.max(maxWidth, minimumMaxWidth);
    }

    @Override
    public void setIconOrientation(RecentsOrientedState orientationState, boolean isGridTask) {
        RecentsPagedOrientationHandler orientationHandler =
                orientationState.getOrientationHandler();
        // Layout params for anchor view
        LayoutParams anchorLayoutParams = (LayoutParams) mMenuAnchorView.getLayoutParams();
        anchorLayoutParams.topMargin = mExpandedMenuDefaultHeight + mMenuToChipGap;
        mMenuAnchorView.setLayoutParams(anchorLayoutParams);

        // Layout Params for the Menu View (this)
        LayoutParams iconMenuParams = (LayoutParams) getLayoutParams();
        iconMenuParams.width = mExpandedMenuDefaultWidth;
        iconMenuParams.height = mExpandedMenuDefaultHeight;
        orientationHandler.setIconAppChipMenuParams(this, iconMenuParams, mIconMenuMarginTopStart,
                mIconMenuMarginTopStart);
        setLayoutParams(iconMenuParams);

        // Layout params for the background
        Rect collapsedBackgroundBounds = getCollapsedBackgroundLtrBounds();
        mBackgroundRelativeLtrLocation.set(collapsedBackgroundBounds);
        setOutlineProvider(new ViewOutlineProvider() {
            final Rect mRtlAppliedOutlineBounds = new Rect();
            @Override
            public void getOutline(View view, Outline outline) {
                mRtlAppliedOutlineBounds.set(mBackgroundRelativeLtrLocation);
                if (isLayoutRtl()) {
                    int width = getWidth();
                    mRtlAppliedOutlineBounds.left = width - mBackgroundRelativeLtrLocation.right;
                    mRtlAppliedOutlineBounds.right = width - mBackgroundRelativeLtrLocation.left;
                }
                outline.setRoundRect(
                        mRtlAppliedOutlineBounds, mRtlAppliedOutlineBounds.height() / 2f);
            }
        });

        // Layout Params for the Icon View
        LayoutParams iconParams = (LayoutParams) mIconView.getLayoutParams();
        int iconMarginStartRelativeToParent = mIconViewMarginStart + mBackgroundMarginTopStart;
        orientationHandler.setIconAppChipChildrenParams(
                iconParams, iconMarginStartRelativeToParent);

        mIconView.setLayoutParams(iconParams);
        mIconView.setDrawableSize(mAppIconSize, mAppIconSize);

        // Layout Params for the collapsed Icon Text View
        int textMarginStart =
                iconMarginStartRelativeToParent + mAppIconSize + mAppNameHorizontalMargin;
        LayoutParams iconTextCollapsedParams =
                (LayoutParams) mIconTextCollapsedView.getLayoutParams();
        orientationHandler.setIconAppChipChildrenParams(iconTextCollapsedParams, textMarginStart);
        int collapsedTextWidth = collapsedBackgroundBounds.width() - mIconViewMarginStart
                - mAppIconSize - mArrowSize - mAppNameHorizontalMargin - mArrowMarginEnd;
        iconTextCollapsedParams.width = collapsedTextWidth;
        mIconTextCollapsedView.setLayoutParams(iconTextCollapsedParams);
        mIconTextCollapsedView.setAlpha(1f);

        // Layout Params for the expanded Icon Text View
        LayoutParams iconTextExpandedParams =
                (LayoutParams) mIconTextExpandedView.getLayoutParams();
        orientationHandler.setIconAppChipChildrenParams(iconTextExpandedParams, textMarginStart);
        mIconTextExpandedView.setLayoutParams(iconTextExpandedParams);
        mIconTextExpandedView.setAlpha(0f);
        mIconTextExpandedView.setRevealClip(true, 0, mAppIconSize / 2f, collapsedTextWidth);

        // Layout Params for the Icon Arrow View
        LayoutParams iconArrowParams = (LayoutParams) mIconArrowView.getLayoutParams();
        int arrowMarginStart = collapsedBackgroundBounds.right - mArrowMarginEnd - mArrowSize;
        orientationHandler.setIconAppChipChildrenParams(iconArrowParams, arrowMarginStart);
        mIconArrowView.setPivotY(iconArrowParams.height / 2f);
        mIconArrowView.setLayoutParams(iconArrowParams);

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
    public void setModalAlpha(float alpha) {
        mMultiValueAlpha.get(INDEX_MODAL_ALPHA).setValue(alpha);
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
        cancelInProgressAnimations();
        final Rect collapsedBackgroundBounds = getCollapsedBackgroundLtrBounds();
        final Rect expandedBackgroundBounds = getExpandedBackgroundLtrBounds();
        final Rect initialBackground = new Rect(mBackgroundRelativeLtrLocation);
        mAnimator = new AnimatorSet();

        if (isRevealing) {
            boolean isRtl = isLayoutRtl();
            bringToFront();
            // Clip expanded text with reveal animation so it doesn't go beyond the edge of the menu
            Animator expandedTextRevealAnim = ViewAnimationUtils.createCircularReveal(
                    mIconTextExpandedView, 0, mIconTextExpandedView.getHeight() / 2,
                    mIconTextCollapsedView.getWidth(), mIconTextExpandedView.getWidth());
            // Animate background clipping
            ValueAnimator backgroundAnimator = ValueAnimator.ofObject(
                    mBackgroundAnimationRectEvaluator,
                    initialBackground,
                    expandedBackgroundBounds);
            backgroundAnimator.addUpdateListener(valueAnimator -> invalidateOutline());

            float iconViewScaling = mIconViewDrawableExpandedSize / (float) mAppIconSize;
            float arrowTranslationX =
                    expandedBackgroundBounds.right - collapsedBackgroundBounds.right;
            float iconCenterToTextCollapsed = mAppIconSize / 2f + mAppNameHorizontalMargin;
            float iconCenterToTextExpanded =
                    mIconViewDrawableExpandedSize / 2f + mAppNameHorizontalMargin;
            float textTranslationX = iconCenterToTextExpanded - iconCenterToTextCollapsed;

            float textTranslationXWithRtl = isRtl ? -textTranslationX : textTranslationX;
            float arrowTranslationWithRtl = isRtl ? -arrowTranslationX : arrowTranslationX;

            mAnimator.playTogether(
                    expandedTextRevealAnim,
                    backgroundAnimator,
                    ObjectAnimator.ofFloat(mIconView, SCALE_X, iconViewScaling),
                    ObjectAnimator.ofFloat(mIconView, SCALE_Y, iconViewScaling),
                    ObjectAnimator.ofFloat(mIconTextCollapsedView, TRANSLATION_X,
                            textTranslationXWithRtl),
                    ObjectAnimator.ofFloat(mIconTextExpandedView, TRANSLATION_X,
                            textTranslationXWithRtl),
                    ObjectAnimator.ofFloat(mIconTextCollapsedView, ALPHA, 0),
                    ObjectAnimator.ofFloat(mIconTextExpandedView, ALPHA, 1),
                    ObjectAnimator.ofFloat(mIconArrowView, TRANSLATION_X, arrowTranslationWithRtl),
                    ObjectAnimator.ofFloat(mIconArrowView, SCALE_Y, -1));
            mAnimator.setDuration(MENU_BACKGROUND_REVEAL_DURATION);
        } else {
            // Clip expanded text with reveal animation so it doesn't go beyond the edge of the menu
            Animator expandedTextClipAnim = ViewAnimationUtils.createCircularReveal(
                    mIconTextExpandedView, 0, mIconTextExpandedView.getHeight() / 2,
                    mIconTextExpandedView.getWidth(), mIconTextCollapsedView.getWidth());

            // Animate background clipping
            ValueAnimator backgroundAnimator = ValueAnimator.ofObject(
                    mBackgroundAnimationRectEvaluator,
                    initialBackground,
                    collapsedBackgroundBounds);
            backgroundAnimator.addUpdateListener(valueAnimator -> invalidateOutline());

            mAnimator.playTogether(
                    expandedTextClipAnim,
                    backgroundAnimator,
                    ObjectAnimator.ofFloat(mIconView, SCALE_PROPERTY, 1),
                    ObjectAnimator.ofFloat(mIconTextCollapsedView, TRANSLATION_X, 0),
                    ObjectAnimator.ofFloat(mIconTextExpandedView, TRANSLATION_X, 0),
                    ObjectAnimator.ofFloat(mIconTextCollapsedView, ALPHA, 1),
                    ObjectAnimator.ofFloat(mIconTextExpandedView, ALPHA, 0),
                    ObjectAnimator.ofFloat(mIconArrowView, TRANSLATION_X, 0),
                    ObjectAnimator.ofFloat(mIconArrowView, SCALE_Y, 1));
            mAnimator.setDuration(MENU_BACKGROUND_HIDE_DURATION);
        }

        mAnimator.setInterpolator(EMPHASIZED);
        mAnimator.start();
    }

    private Rect getCollapsedBackgroundLtrBounds() {
        Rect bounds = new Rect(
                0,
                0,
                Math.min(mMaxWidth, mCollapsedMenuDefaultWidth),
                mCollapsedMenuDefaultHeight);
        bounds.offset(mBackgroundMarginTopStart, mBackgroundMarginTopStart);
        return bounds;
    }

    private Rect getExpandedBackgroundLtrBounds() {
        return new Rect(0, 0, mExpandedMenuDefaultWidth, mExpandedMenuDefaultHeight);
    }

    private void cancelInProgressAnimations() {
        // We null the `AnimatorSet` because it holds references to the `Animators` which aren't
        // expecting to be mutable and will cause a crash if they are re-used.
        if (mAnimator != null && mAnimator.isStarted()) {
            mAnimator.cancel();
            mAnimator = null;
        }
    }

    @Override
    public View asView() {
        return this;
    }
}
