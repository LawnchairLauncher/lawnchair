/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.launcher3.popup;

import static com.android.launcher3.anim.Interpolators.ACCEL_DEACCEL;
import static com.android.launcher3.popup.PopupPopulator.MAX_SHORTCUTS;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ArgbEvaluator;
import android.animation.ObjectAnimator;
import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Outline;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.util.Pair;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;

import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.BaseDraggingActivity;
import com.android.launcher3.InsettableFrameLayout;
import com.android.launcher3.LauncherAnimUtils;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.anim.RevealOutlineAnimation;
import com.android.launcher3.anim.RoundedRectRevealOutlineProvider;
import com.android.launcher3.dragndrop.DragLayer;
import com.android.launcher3.shortcuts.DeepShortcutView;
import com.android.launcher3.util.Themes;
import com.android.launcher3.views.BaseDragLayer;

import java.util.ArrayList;
import java.util.Collections;

/**
 * A container for shortcuts to deep links and notifications associated with an app.
 *
 * @param <T> The activity on with the popup shows
 */
public abstract class ArrowPopup<T extends BaseDraggingActivity> extends AbstractFloatingView {

    // +1 for system shortcut view
    private static final int MAX_NUM_CHILDREN = MAX_SHORTCUTS + 1;

    private final Rect mTempRect = new Rect();

    protected final LayoutInflater mInflater;
    private final float mOutlineRadius;
    protected final T mLauncher;
    protected final boolean mIsRtl;

    private final int mArrowOffsetVertical;
    private final int mArrowOffsetHorizontal;
    private final int mArrowWidth;
    private final int mArrowHeight;
    private final int mArrowPointRadius;
    private final View mArrow;

    private final int mMargin;

    protected boolean mIsLeftAligned;
    protected boolean mIsAboveIcon;
    private int mGravity;

    protected Animator mOpenCloseAnimator;
    protected boolean mDeferContainerRemoval;
    private final Rect mStartRect = new Rect();
    private final Rect mEndRect = new Rect();

    private final GradientDrawable mRoundedTop;
    private final GradientDrawable mRoundedBottom;

    private Runnable mOnCloseCallback = () -> { };

    private int mArrowColor;
    private final int[] mColors;

    public ArrowPopup(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mInflater = LayoutInflater.from(context);
        mOutlineRadius = Themes.getDialogCornerRadius(context);
        mLauncher = BaseDraggingActivity.fromContext(context);
        mIsRtl = Utilities.isRtl(getResources());

        setClipToOutline(true);
        setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), mOutlineRadius);
            }
        });

        // Initialize arrow view
        final Resources resources = getResources();
        mMargin = resources.getDimensionPixelSize(R.dimen.popup_margin);
        mArrowWidth = resources.getDimensionPixelSize(R.dimen.popup_arrow_width);
        mArrowHeight = resources.getDimensionPixelSize(R.dimen.popup_arrow_height);
        mArrow = new View(context);
        mArrow.setLayoutParams(new DragLayer.LayoutParams(mArrowWidth, mArrowHeight));
        mArrowOffsetVertical = resources.getDimensionPixelSize(R.dimen.popup_arrow_vertical_offset);
        mArrowOffsetHorizontal = resources.getDimensionPixelSize(
                R.dimen.popup_arrow_horizontal_center_offset) - (mArrowWidth / 2);
        mArrowPointRadius = resources.getDimensionPixelSize(R.dimen.popup_arrow_corner_radius);

        int smallerRadius = resources.getDimensionPixelSize(R.dimen.popup_smaller_radius);
        mRoundedTop = new GradientDrawable();
        mRoundedTop.setCornerRadii(new float[] { mOutlineRadius, mOutlineRadius, mOutlineRadius,
                mOutlineRadius, smallerRadius, smallerRadius, smallerRadius, smallerRadius});

        mRoundedBottom = new GradientDrawable();
        mRoundedBottom.setCornerRadii(new float[] { smallerRadius, smallerRadius, smallerRadius,
                smallerRadius, mOutlineRadius, mOutlineRadius, mOutlineRadius, mOutlineRadius});

        int primaryColor = Themes.getAttrColor(context, R.attr.popupColorPrimary);
        int secondaryColor = Themes.getAttrColor(context, R.attr.popupColorSecondary);
        ArgbEvaluator argb = new ArgbEvaluator();
        mColors = new int[MAX_NUM_CHILDREN];
        // Interpolate between the two colors, exclusive.
        float step = 1f / (MAX_NUM_CHILDREN + 1);
        for (int i = 0; i < mColors.length; ++i) {
            mColors[i] = (int) argb.evaluate((i + 1) * step, primaryColor, secondaryColor);
        }
    }

    public ArrowPopup(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ArrowPopup(Context context) {
        this(context, null, 0);
    }

    @Override
    protected void handleClose(boolean animate) {
        if (animate) {
            animateClose();
        } else {
            closeComplete();
        }
    }

    /**
     * Utility method for inflating and adding a view
     */
    public <R extends View> R inflateAndAdd(int resId, ViewGroup container) {
        View view = mInflater.inflate(resId, container, false);
        container.addView(view);
        return (R) view;
    }

    /**
     * Utility method for inflating and adding a view
     */
    public <R extends View> R inflateAndAdd(int resId, ViewGroup container, int index) {
        View view = mInflater.inflate(resId, container, false);
        container.addView(view, index);
        return (R) view;
    }

    /**
     * Called when all view inflation and reordering in complete.
     */
    protected void onInflationComplete(boolean isReversed) { }

    /**
     * Set the margins and radius of backgrounds after views are properly ordered.
     */
    protected void assignMarginsAndBackgrounds() {
        int count = getChildCount();
        int totalVisibleShortcuts = 0;
        for (int i = 0; i < count; i++) {
            View view = getChildAt(i);
            if (view.getVisibility() == VISIBLE && view instanceof DeepShortcutView) {
                totalVisibleShortcuts++;
            }
        }

        int numVisibleShortcut = 0;
        View lastView = null;
        int numVisibleChild = 0;
        for (int i = 0; i < count; i++) {
            View view = getChildAt(i);
            boolean isShortcut = view instanceof DeepShortcutView;
            if (view.getVisibility() == VISIBLE) {
                if (lastView != null) {
                    MarginLayoutParams mlp = (MarginLayoutParams) lastView.getLayoutParams();
                    mlp.bottomMargin = mMargin;
                }
                lastView = view;
                MarginLayoutParams mlp = (MarginLayoutParams) lastView.getLayoutParams();
                mlp.bottomMargin = 0;

                if (isShortcut) {
                    if (totalVisibleShortcuts == 1) {
                        view.setBackgroundResource(R.drawable.single_item_primary);
                    } else if (totalVisibleShortcuts > 1) {
                        if (numVisibleShortcut == 0) {
                            view.setBackground(mRoundedTop);
                        } else if (numVisibleShortcut == (totalVisibleShortcuts - 1)) {
                            view.setBackground(mRoundedBottom);
                        } else {
                            view.setBackgroundResource(R.drawable.middle_item_primary);
                        }
                        numVisibleShortcut++;
                    }
                }

                int color = mColors[numVisibleChild % mColors.length];
                setChildColor(view, color);

                // Arrow color matches the first child or the last child.
                if (!mIsAboveIcon && numVisibleChild == 0) {
                    mArrowColor = color;
                } else if (mIsAboveIcon) {
                    mArrowColor = color;
                }

                numVisibleChild++;
            }
        }
        measure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED);
    }

    /**
     * Sets the background color of the child.
     */
    protected void setChildColor(View view, int color) {
        Drawable bg = view.getBackground();
        if (bg instanceof GradientDrawable) {
            ((GradientDrawable) bg.mutate()).setColor(color);
        } else if (bg instanceof ColorDrawable) {
            ((ColorDrawable) bg.mutate()).setColor(color);
        }
    }

    /**
     * Shows the popup at the desired location, optionally reversing the children.
     * @param viewsToFlip number of views from the top to to flip in case of reverse order
     */
    protected void reorderAndShow(int viewsToFlip) {
        setupForDisplay();
        boolean reverseOrder = mIsAboveIcon;
        if (reverseOrder) {
            reverseOrder(viewsToFlip);
        }
        onInflationComplete(reverseOrder);
        assignMarginsAndBackgrounds();
        if (shouldAddArrow()) {
            addArrow();
        }
        animateOpen();
    }

    /**
     * Shows the popup at the desired location.
     */
    protected void show() {
        setupForDisplay();
        onInflationComplete(false);
        assignMarginsAndBackgrounds();
        if (shouldAddArrow()) {
            addArrow();
        }
        animateOpen();
    }

    private void setupForDisplay() {
        setVisibility(View.INVISIBLE);
        mIsOpen = true;
        getPopupContainer().addView(this);
        orientAboutObject();
    }

    private void reverseOrder(int viewsToFlip) {
        int count = getChildCount();
        ArrayList<View> allViews = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            if (i == viewsToFlip) {
                Collections.reverse(allViews);
            }
            allViews.add(getChildAt(i));
        }
        Collections.reverse(allViews);
        removeAllViews();
        for (int i = 0; i < count; i++) {
            addView(allViews.get(i));
        }
    }

    private int getArrowLeft() {
        if (mIsLeftAligned) {
            return mArrowOffsetHorizontal;
        }
        return getMeasuredWidth() - mArrowOffsetHorizontal - mArrowWidth;
    }

    private void addArrow() {
        getPopupContainer().addView(mArrow);
        mArrow.setX(getX() + getArrowLeft());

        if (Gravity.isVertical(mGravity)) {
            // This is only true if there wasn't room for the container next to the icon,
            // so we centered it instead. In that case we don't want to showDefaultOptions the arrow.
            mArrow.setVisibility(INVISIBLE);
        } else {
            mArrow.setBackground(new RoundedArrowDrawable(
                    mArrowWidth, mArrowHeight, mArrowPointRadius,
                    mOutlineRadius, getMeasuredWidth(), getMeasuredHeight(),
                    mArrowOffsetHorizontal, -mArrowOffsetVertical,
                    !mIsAboveIcon, mIsLeftAligned,
                    mArrowColor));
            mArrow.setElevation(getElevation());
        }

        mArrow.setPivotX(mArrowWidth / 2.0f);
        mArrow.setPivotY(mIsAboveIcon ? mArrowHeight : 0);
    }

    /**
     * Returns whether or not we should add the arrow.
     */
    protected boolean shouldAddArrow() {
        return true;
    }

    /**
     * Provide the location of the target object relative to the dragLayer.
     */
    protected abstract void getTargetObjectLocation(Rect outPos);

    /**
     * Orients this container above or below the given icon, aligning with the left or right.
     *
     * These are the preferred orientations, in order (RTL prefers right-aligned over left):
     * - Above and left-aligned
     * - Above and right-aligned
     * - Below and left-aligned
     * - Below and right-aligned
     *
     * So we always align left if there is enough horizontal space
     * and align above if there is enough vertical space.
     */
    protected void orientAboutObject() {
        orientAboutObject(true /* allowAlignLeft */, true /* allowAlignRight */);
    }

    /**
     * @see #orientAboutObject()
     *
     * @param allowAlignLeft Set to false if we already tried aligning left and didn't have room.
     * @param allowAlignRight Set to false if we already tried aligning right and didn't have room.
     * TODO: Can we test this with all permutations of widths/heights and icon locations + RTL?
     */
    private void orientAboutObject(boolean allowAlignLeft, boolean allowAlignRight) {
        measure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED);

        int extraVerticalSpace = mArrowHeight + mArrowOffsetVertical
                + getResources().getDimensionPixelSize(R.dimen.popup_vertical_padding);
        // The margins are added after we call this method, so we need to account for them here.
        int numVisibleChildren = 0;
        for (int i = getChildCount() - 1; i >= 0; --i) {
            if (getChildAt(i).getVisibility() == VISIBLE) {
                numVisibleChildren++;
            }
        }
        int childMargins = (numVisibleChildren - 1) * mMargin;
        int height = getMeasuredHeight() + extraVerticalSpace + childMargins;
        int width = getMeasuredWidth();

        getTargetObjectLocation(mTempRect);
        InsettableFrameLayout dragLayer = getPopupContainer();
        Rect insets = dragLayer.getInsets();

        // Align left (right in RTL) if there is room.
        int leftAlignedX = mTempRect.left;
        int rightAlignedX = mTempRect.right - width;
        mIsLeftAligned = !mIsRtl ? allowAlignLeft : !allowAlignRight;
        int x = mIsLeftAligned ? leftAlignedX : rightAlignedX;

        // Offset x so that the arrow and shortcut icons are center-aligned with the original icon.
        int iconWidth = mTempRect.width();
        int xOffset = iconWidth / 2 - mArrowOffsetHorizontal - mArrowWidth / 2;
        x += mIsLeftAligned ? xOffset : -xOffset;

        // Check whether we can still align as we originally wanted, now that we've calculated x.
        if (!allowAlignLeft && !allowAlignRight) {
            // We've already tried both ways and couldn't make it fit. onLayout() will set the
            // gravity to CENTER_HORIZONTAL, but continue below to update y.
        } else {
            boolean canBeLeftAligned = x + width + insets.left
                    < dragLayer.getWidth() - insets.right;
            boolean canBeRightAligned = x > insets.left;
            boolean alignmentStillValid = mIsLeftAligned && canBeLeftAligned
                    || !mIsLeftAligned && canBeRightAligned;
            if (!alignmentStillValid) {
                // Try again, but don't allow this alignment we already know won't work.
                orientAboutObject(allowAlignLeft && !mIsLeftAligned /* allowAlignLeft */,
                        allowAlignRight && mIsLeftAligned /* allowAlignRight */);
                return;
            }
        }

        // Open above icon if there is room.
        int iconHeight = mTempRect.height();
        int y = mTempRect.top - height;
        mIsAboveIcon = y > dragLayer.getTop() + insets.top;
        if (!mIsAboveIcon) {
            y = mTempRect.top + iconHeight + extraVerticalSpace;
        }

        // Insets are added later, so subtract them now.
        x -= insets.left;
        y -= insets.top;

        mGravity = 0;
        if (y + height > dragLayer.getBottom() - insets.bottom) {
            // The container is opening off the screen, so just center it in the drag layer instead.
            mGravity = Gravity.CENTER_VERTICAL;
            // Put the container next to the icon, preferring the right side in ltr (left in rtl).
            int rightSide = leftAlignedX + iconWidth - insets.left;
            int leftSide = rightAlignedX - iconWidth - insets.left;
            if (!mIsRtl) {
                if (rightSide + width < dragLayer.getRight()) {
                    x = rightSide;
                    mIsLeftAligned = true;
                } else {
                    x = leftSide;
                    mIsLeftAligned = false;
                }
            } else {
                if (leftSide > dragLayer.getLeft()) {
                    x = leftSide;
                    mIsLeftAligned = false;
                } else {
                    x = rightSide;
                    mIsLeftAligned = true;
                }
            }
            mIsAboveIcon = true;
        }

        setX(x);
        if (Gravity.isVertical(mGravity)) {
            return;
        }

        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) getLayoutParams();
        FrameLayout.LayoutParams arrowLp = (FrameLayout.LayoutParams) mArrow.getLayoutParams();
        if (mIsAboveIcon) {
            arrowLp.gravity = lp.gravity = Gravity.BOTTOM;
            lp.bottomMargin =
                    getPopupContainer().getHeight() - y - getMeasuredHeight() - insets.top;
            arrowLp.bottomMargin =
                    lp.bottomMargin - arrowLp.height - mArrowOffsetVertical - insets.bottom;
        } else {
            arrowLp.gravity = lp.gravity = Gravity.TOP;
            lp.topMargin = y + insets.top;
            arrowLp.topMargin = lp.topMargin - insets.top - arrowLp.height - mArrowOffsetVertical;
        }
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);

        // enforce contained is within screen
        BaseDragLayer dragLayer = getPopupContainer();
        Rect insets = dragLayer.getInsets();
        if (getTranslationX() + l < insets.left
                || getTranslationX() + r > dragLayer.getWidth() - insets.right) {
            // If we are still off screen, center horizontally too.
            mGravity |= Gravity.CENTER_HORIZONTAL;
        }

        if (Gravity.isHorizontal(mGravity)) {
            setX(dragLayer.getWidth() / 2 - getMeasuredWidth() / 2);
            mArrow.setVisibility(INVISIBLE);
        }
        if (Gravity.isVertical(mGravity)) {
            setY(dragLayer.getHeight() / 2 - getMeasuredHeight() / 2);
        }
    }

    @Override
    protected Pair<View, String> getAccessibilityTarget() {
        return Pair.create(this, "");
    }

    @Override
    protected View getAccessibilityInitialFocusView() {
        return getChildCount() > 0 ? getChildAt(0) : this;
    }

    private int getArrowDuration() {
        return shouldAddArrow()
                ? getResources().getInteger(R.integer.config_popupArrowOpenCloseDuration)
                : 0;
    }

    private void animateOpen() {
        setVisibility(View.VISIBLE);

        final AnimatorSet openAnim = new AnimatorSet();
        final Resources res = getResources();
        final long revealDuration = (long) res.getInteger(R.integer.config_popupOpenCloseDuration);
        final long arrowDuration = getArrowDuration();
        final TimeInterpolator revealInterpolator = ACCEL_DEACCEL;

        // Rectangular reveal.
        mEndRect.set(0, 0, getMeasuredWidth(), getMeasuredHeight());
        final ValueAnimator revealAnim = createOpenCloseOutlineProvider()
                .createRevealAnimator(this, false);
        revealAnim.setDuration(revealDuration);
        revealAnim.setInterpolator(revealInterpolator);
        // Clip the popup to the initial outline while the notification dot and arrow animate.
        revealAnim.start();
        revealAnim.pause();

        ValueAnimator fadeIn = ValueAnimator.ofFloat(0, 1);
        fadeIn.setDuration(revealDuration + arrowDuration);
        fadeIn.setInterpolator(revealInterpolator);
        fadeIn.addUpdateListener(anim -> {
            float alpha = (float) anim.getAnimatedValue();
            mArrow.setAlpha(alpha);
            setAlpha(revealAnim.isStarted() ? alpha : 0);
        });
        openAnim.play(fadeIn);

        // Animate the arrow.
        mArrow.setScaleX(0);
        mArrow.setScaleY(0);
        Animator arrowScale = ObjectAnimator.ofFloat(mArrow, LauncherAnimUtils.SCALE_PROPERTY, 1)
                .setDuration(arrowDuration);

        openAnim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                setAlpha(1f);
                announceAccessibilityChanges();
                mOpenCloseAnimator = null;
            }
        });

        mOpenCloseAnimator = openAnim;
        openAnim.playSequentially(arrowScale, revealAnim);
        openAnim.start();
    }

    protected void animateClose() {
        if (!mIsOpen) {
            return;
        }
        if (getOutlineProvider() instanceof RevealOutlineAnimation) {
            ((RevealOutlineAnimation) getOutlineProvider()).getOutline(mEndRect);
        } else {
            mEndRect.set(0, 0, getMeasuredWidth(), getMeasuredHeight());
        }
        if (mOpenCloseAnimator != null) {
            mOpenCloseAnimator.cancel();
        }
        mIsOpen = false;


        final AnimatorSet closeAnim = new AnimatorSet();
        final Resources res = getResources();
        final TimeInterpolator revealInterpolator = ACCEL_DEACCEL;
        final long revealDuration = res.getInteger(R.integer.config_popupOpenCloseDuration);
        final long arrowDuration = getArrowDuration();

        // Hide the arrow
        Animator scaleArrow = ObjectAnimator.ofFloat(mArrow, LauncherAnimUtils.SCALE_PROPERTY, 0)
                .setDuration(arrowDuration);

        // Rectangular reveal (reversed).
        final ValueAnimator revealAnim = createOpenCloseOutlineProvider()
                .createRevealAnimator(this, true);
        revealAnim.setDuration(revealDuration);
        revealAnim.setInterpolator(revealInterpolator);
        closeAnim.playSequentially(revealAnim, scaleArrow);

        ValueAnimator fadeOut = ValueAnimator.ofFloat(getAlpha(), 0);
        fadeOut.setDuration(revealDuration + arrowDuration);
        fadeOut.setInterpolator(revealInterpolator);
        fadeOut.addUpdateListener(anim -> {
            float alpha = (float) anim.getAnimatedValue();
            mArrow.setAlpha(alpha);
            setAlpha(scaleArrow.isStarted() ? 0 : alpha);
        });
        closeAnim.play(fadeOut);

        onCreateCloseAnimation(closeAnim);
        closeAnim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mOpenCloseAnimator = null;
                if (mDeferContainerRemoval) {
                    setVisibility(INVISIBLE);
                } else {
                    closeComplete();
                }
            }
        });
        mOpenCloseAnimator = closeAnim;
        closeAnim.start();
    }

    /**
     * Called when creating the close transition allowing subclass can add additional animations.
     */
    protected void onCreateCloseAnimation(AnimatorSet anim) { }

    private RoundedRectRevealOutlineProvider createOpenCloseOutlineProvider() {
        int arrowLeft = getArrowLeft();
        int arrowCenterY = mIsAboveIcon ? getMeasuredHeight() : 0;

        mStartRect.set(arrowLeft, arrowCenterY, arrowLeft + mArrowWidth, arrowCenterY);

        return new RoundedRectRevealOutlineProvider(
                mArrowPointRadius, mOutlineRadius, mStartRect, mEndRect);
    }

    /**
     * Closes the popup without animation.
     */
    protected void closeComplete() {
        if (mOpenCloseAnimator != null) {
            mOpenCloseAnimator.cancel();
            mOpenCloseAnimator = null;
        }
        mIsOpen = false;
        mDeferContainerRemoval = false;
        getPopupContainer().removeView(this);
        getPopupContainer().removeView(mArrow);
        mOnCloseCallback.run();
    }

    /**
     * Callback to be called when the popup is closed
     */
    public void setOnCloseCallback(@NonNull Runnable callback) {
        mOnCloseCallback = callback;
    }

    protected BaseDragLayer getPopupContainer() {
        return mLauncher.getDragLayer();
    }
}
