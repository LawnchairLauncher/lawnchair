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

import static androidx.core.content.ContextCompat.getColorStateList;

import static com.android.app.animation.Interpolators.EMPHASIZED_ACCELERATE;
import static com.android.app.animation.Interpolators.EMPHASIZED_DECELERATE;
import static com.android.app.animation.Interpolators.LINEAR;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.util.Pair;
import android.util.Property;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Interpolator;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;

import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.InsettableFrameLayout;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.dragndrop.DragLayer;
import com.android.launcher3.shortcuts.DeepShortcutView;
import com.android.launcher3.util.RunnableList;
import com.android.launcher3.util.Themes;
import com.android.launcher3.views.ActivityContext;
import com.android.launcher3.views.BaseDragLayer;

import java.util.Arrays;

/**
 * A container for shortcuts to deep links and notifications associated with an app.
 *
 * @param <T> The activity on with the popup shows
 */
public abstract class ArrowPopup<T extends Context & ActivityContext>
        extends AbstractFloatingView {

    // Duration values (ms) for popup open and close animations.
    protected int mOpenDuration = 276;
    protected int mOpenFadeStartDelay = 0;
    protected int mOpenFadeDuration = 38;
    protected int mOpenChildFadeStartDelay = 38;
    protected int mOpenChildFadeDuration = 76;

    protected int mCloseDuration = 200;
    protected int mCloseFadeStartDelay = 140;
    protected int mCloseFadeDuration = 50;
    protected int mCloseChildFadeStartDelay = 0;
    protected int mCloseChildFadeDuration = 140;

    private static final int OPEN_DURATION_U = 200;
    private static final int OPEN_FADE_START_DELAY_U = 0;
    private static final int OPEN_FADE_DURATION_U = 83;
    private static final int OPEN_CHILD_FADE_START_DELAY_U = 0;
    private static final int OPEN_CHILD_FADE_DURATION_U = 83;
    private static final int OPEN_OVERSHOOT_DURATION_U = 200;

    private static final int CLOSE_DURATION_U  = 233;
    private static final int CLOSE_FADE_START_DELAY_U = 150;
    private static final int CLOSE_FADE_DURATION_U = 83;
    private static final int CLOSE_CHILD_FADE_START_DELAY_U = 150;
    private static final int CLOSE_CHILD_FADE_DURATION_U = 83;

    protected final Rect mTempRect = new Rect();

    protected final LayoutInflater mInflater;
    protected final float mOutlineRadius;
    protected final T mActivityContext;
    protected final boolean mIsRtl;

    protected final int mArrowOffsetVertical;
    protected final int mArrowOffsetHorizontal;
    protected final int mArrowWidth;
    protected final int mArrowHeight;
    protected final int mArrowPointRadius;
    protected final View mArrow;

    protected final int mChildContainerMargin;

    protected boolean mIsLeftAligned;
    protected boolean mIsAboveIcon;
    protected int mGravity;

    protected AnimatorSet mOpenCloseAnimator;
    protected boolean mDeferContainerRemoval;
    protected boolean shouldScaleArrow = false;
    protected boolean mIsArrowRotated = false;

    private final GradientDrawable mRoundedTop;
    private final GradientDrawable mRoundedBottom;

    private RunnableList mOnCloseCallbacks = new RunnableList();

    // The rect string of the view that the arrow is attached to, in screen reference frame.
    protected int mArrowColor;

    protected final float mElevation;

    // Tag for Views that have children that will need to be iterated to add styling.
    private final String mIterateChildrenTag;

    protected final int[] mColorIds;

    public ArrowPopup(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mInflater = LayoutInflater.from(context);
        mOutlineRadius = Themes.getDialogCornerRadius(context);
        mActivityContext = ActivityContext.lookupContext(context);
        mIsRtl = Utilities.isRtl(getResources());
        mElevation = getResources().getDimension(R.dimen.deep_shortcuts_elevation);

        // Initialize arrow view
        final Resources resources = getResources();
        mArrowColor = getColorStateList(getContext(), R.color.popup_color_background)
                .getDefaultColor();
        mChildContainerMargin = resources.getDimensionPixelSize(R.dimen.popup_margin);
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
        int popupPrimaryColor = Themes.getAttrColor(context, R.attr.popupColorPrimary);
        mRoundedTop.setColor(popupPrimaryColor);
        mRoundedTop.setCornerRadii(new float[] { mOutlineRadius, mOutlineRadius, mOutlineRadius,
                mOutlineRadius, smallerRadius, smallerRadius, smallerRadius, smallerRadius});

        mRoundedBottom = new GradientDrawable();
        mRoundedBottom.setColor(popupPrimaryColor);
        mRoundedBottom.setCornerRadii(new float[] { smallerRadius, smallerRadius, smallerRadius,
                smallerRadius, mOutlineRadius, mOutlineRadius, mOutlineRadius, mOutlineRadius});

        mIterateChildrenTag = getContext().getString(R.string.popup_container_iterate_children);

        if (mActivityContext.canUseMultipleShadesForPopup()) {
            mColorIds = new int[]{R.color.popup_shade_first, R.color.popup_shade_second,
                    R.color.popup_shade_third};
        } else {
            mColorIds = new int[]{R.color.popup_color_background};
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
     * Set the margins and radius of backgrounds after views are properly ordered.
     */
    public void assignMarginsAndBackgrounds(ViewGroup viewGroup) {
        assignMarginsAndBackgrounds(viewGroup, Color.TRANSPARENT);
    }

    /**
     * @param backgroundColor When Color.TRANSPARENT, we get color from {@link #mColorIds}.
     *                        Otherwise, we will use this color for all child views.
     */
    protected void assignMarginsAndBackgrounds(ViewGroup viewGroup, int backgroundColor) {
        int[] colors = null;
        if (backgroundColor == Color.TRANSPARENT) {
            // Lazily get the colors so they match the current wallpaper colors.
            colors = Arrays.stream(mColorIds).map(
                    r -> getColorStateList(getContext(), r).getDefaultColor()).toArray();
        }

        int count = viewGroup.getChildCount();
        int totalVisibleShortcuts = 0;
        for (int i = 0; i < count; i++) {
            View view = viewGroup.getChildAt(i);
            if (view.getVisibility() == VISIBLE && isShortcutOrWrapper(view)) {
                totalVisibleShortcuts++;
            }
        }

        int numVisibleShortcut = 0;
        View lastView = null;
        AnimatorSet colorAnimator = new AnimatorSet();
        for (int i = 0; i < count; i++) {
            View view = viewGroup.getChildAt(i);
            if (view.getVisibility() == VISIBLE) {
                if (lastView != null && (isShortcutContainer(lastView))) {
                    MarginLayoutParams mlp = (MarginLayoutParams) lastView.getLayoutParams();
                    mlp.bottomMargin = mChildContainerMargin;
                }
                lastView = view;
                MarginLayoutParams mlp = (MarginLayoutParams) lastView.getLayoutParams();
                mlp.bottomMargin = 0;

                if (colors != null && isShortcutContainer(view)) {
                    setChildColor(view, colors[0], colorAnimator);
                    mArrowColor = colors[0];
                }

                if (view instanceof ViewGroup && isShortcutContainer(view)) {
                    assignMarginsAndBackgrounds((ViewGroup) view, backgroundColor);
                    continue;
                }

                if (isShortcutOrWrapper(view)) {
                    if (totalVisibleShortcuts == 1) {
                        view.setBackgroundResource(R.drawable.single_item_primary);
                    } else if (totalVisibleShortcuts > 1) {
                        if (numVisibleShortcut == 0) {
                            view.setBackground(mRoundedTop.getConstantState().newDrawable());
                        } else if (numVisibleShortcut == (totalVisibleShortcuts - 1)) {
                            view.setBackground(mRoundedBottom.getConstantState().newDrawable());
                        } else {
                            view.setBackgroundResource(R.drawable.middle_item_primary);
                        }
                        numVisibleShortcut++;
                    }
                }

                setChildColor(view, backgroundColor, colorAnimator);
            }
        }

        colorAnimator.setDuration(0).start();
        measure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED);
    }

    /**
     * Returns {@code true} if the child is a shortcut or wraps a shortcut.
     */
    protected boolean isShortcutOrWrapper(View view) {
        return view instanceof DeepShortcutView;
    }

    /**
     * Returns {@code true} if view is a layout container of shortcuts
     */
    boolean isShortcutContainer(View view) {
        return mIterateChildrenTag.equals(view.getTag());
    }

    /**
     * Sets the background color of the child.
     */
    protected void setChildColor(View view, int color, AnimatorSet animatorSetOut) {
        Drawable bg = view.getBackground();
        if (bg instanceof GradientDrawable) {
            GradientDrawable gd = (GradientDrawable) bg.mutate();
            int oldColor = ((GradientDrawable) bg).getColor().getDefaultColor();
            animatorSetOut.play(ObjectAnimator.ofArgb(gd, "color", oldColor, color));
        } else if (bg instanceof ColorDrawable) {
            ColorDrawable cd = (ColorDrawable) bg.mutate();
            int oldColor = ((ColorDrawable) bg).getColor();
            animatorSetOut.play(ObjectAnimator.ofArgb(cd, "color", oldColor, color));
        }
    }

    /**
     * Shows the popup at the desired location.
     */
    public void show() {
        setupForDisplay();
        assignMarginsAndBackgrounds(this);
        if (shouldAddArrow()) {
            addArrow();
        }
        animateOpen();
    }

    protected void setupForDisplay() {
        setVisibility(View.INVISIBLE);
        mIsOpen = true;
        getPopupContainer().addView(this);
        orientAboutObject();
    }

    private int getArrowLeft() {
        if (mIsLeftAligned) {
            return mArrowOffsetHorizontal;
        }
        return getMeasuredWidth() - mArrowOffsetHorizontal - mArrowWidth;
    }

    /**
     * @param show If true, shows arrow (when applicable), otherwise hides arrow.
     */
    public void showArrow(boolean show) {
        mArrow.setVisibility(show && shouldAddArrow() ? VISIBLE : INVISIBLE);
    }

    protected void addArrow() {
        getPopupContainer().addView(mArrow);
        mArrow.setX(getX() + getArrowLeft());

        if (Gravity.isVertical(mGravity)) {
            // This is only true if there wasn't room for the container next to the icon,
            // so we centered it instead. In that case we don't want to showDefaultOptions the arrow.
            mArrow.setVisibility(INVISIBLE);
        } else {
            updateArrowColor();
        }

        mArrow.setPivotX(mArrowWidth / 2.0f);
        mArrow.setPivotY(mIsAboveIcon ? mArrowHeight : 0);
    }

    protected void updateArrowColor() {
        if (!Gravity.isVertical(mGravity)) {
            mArrow.setBackground(new RoundedArrowDrawable(
                    mArrowWidth, mArrowHeight, mArrowPointRadius,
                    mOutlineRadius, getMeasuredWidth(), getMeasuredHeight(),
                    mArrowOffsetHorizontal, -mArrowOffsetVertical,
                    !mIsAboveIcon, mIsLeftAligned,
                    mArrowColor));
            setElevation(mElevation);
            mArrow.setElevation(mElevation);
        }
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

        int extraVerticalSpace = mArrowHeight + mArrowOffsetVertical + getExtraVerticalOffset();
        // The margins are added after we call this method, so we need to account for them here.
        int numVisibleChildren = 0;
        for (int i = getChildCount() - 1; i >= 0; --i) {
            if (getChildAt(i).getVisibility() == VISIBLE) {
                numVisibleChildren++;
            }
        }
        int childMargins = (numVisibleChildren - 1) * mChildContainerMargin;
        int height = getMeasuredHeight() + extraVerticalSpace + childMargins;
        int width = getMeasuredWidth() + getPaddingLeft() + getPaddingRight();

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
            height -= extraVerticalSpace;
        }

        // Insets are added later, so subtract them now.
        x -= insets.left;
        y -= insets.top;

        mGravity = 0;
        if ((insets.top + y + height) > (dragLayer.getBottom() - insets.bottom)) {
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

    protected void animateOpen() {
        setVisibility(View.VISIBLE);
        mOpenCloseAnimator = getOpenCloseAnimator(
                        true,
                        OPEN_DURATION_U,
                        OPEN_FADE_START_DELAY_U,
                        OPEN_FADE_DURATION_U,
                        OPEN_CHILD_FADE_START_DELAY_U,
                        OPEN_CHILD_FADE_DURATION_U,
                        EMPHASIZED_DECELERATE);

        onCreateOpenAnimation(mOpenCloseAnimator);
        mOpenCloseAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                setAlpha(1f);
                announceAccessibilityChanges();
                mOpenCloseAnimator = null;
            }
        });
        mOpenCloseAnimator.start();
    }

    private void fadeInChildViews(ViewGroup group, float[] alphaValues, long startDelay,
            long duration, AnimatorSet out) {
        for (int i = group.getChildCount() - 1; i >= 0; --i) {
            View view = group.getChildAt(i);
            if (view.getVisibility() == VISIBLE && view instanceof ViewGroup) {
                if (isShortcutContainer(view)) {
                    fadeInChildViews((ViewGroup) view, alphaValues, startDelay, duration, out);
                    continue;
                }
                for (int j = ((ViewGroup) view).getChildCount() - 1; j >= 0; --j) {
                    View childView = ((ViewGroup) view).getChildAt(j);
                    childView.setAlpha(alphaValues[0]);
                    ValueAnimator childFade = ObjectAnimator.ofFloat(childView, ALPHA, alphaValues);
                    childFade.setStartDelay(startDelay);
                    childFade.setDuration(duration);
                    childFade.setInterpolator(LINEAR);

                    out.play(childFade);
                }
            }
        }
    }

    protected void animateClose() {
        if (!mIsOpen) {
            return;
        }
        if (mOpenCloseAnimator != null) {
            mOpenCloseAnimator.cancel();
        }
        mIsOpen = false;

        mOpenCloseAnimator = getOpenCloseAnimator(
                        false,
                        CLOSE_DURATION_U,
                        CLOSE_FADE_START_DELAY_U,
                        CLOSE_FADE_DURATION_U,
                        CLOSE_CHILD_FADE_START_DELAY_U,
                        CLOSE_CHILD_FADE_DURATION_U,
                        EMPHASIZED_ACCELERATE);

        onCreateCloseAnimation(mOpenCloseAnimator);
        mOpenCloseAnimator.addListener(new AnimatorListenerAdapter() {
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
        mOpenCloseAnimator.start();
    }

    public int getExtraVerticalOffset() {
        return getResources().getDimensionPixelSize(R.dimen.popup_vertical_padding);
    }

    /**
     * Sets X and Y pivots for the view animation considering arrow position.
     */
    protected void setPivotForOpenCloseAnimation() {
        int arrowCenter = mArrowOffsetHorizontal + mArrowWidth / 2;
        if (mIsArrowRotated) {
            setPivotX(mIsLeftAligned ? 0f : getMeasuredWidth());
            setPivotY(arrowCenter);
        } else {
            setPivotX(mIsLeftAligned ? arrowCenter : getMeasuredWidth() - arrowCenter);
            setPivotY(mIsAboveIcon ? getMeasuredHeight() : 0f);
        }
    }


    protected AnimatorSet getOpenCloseAnimator(boolean isOpening, int scaleDuration,
            int fadeStartDelay, int fadeDuration, int childFadeStartDelay, int childFadeDuration,
            Interpolator interpolator) {

        setPivotForOpenCloseAnimation();

        float[] alphaValues = isOpening ? new float[] {0, 1} : new float[] {1, 0};
        float[] scaleValues = isOpening ? new float[] {0.5f, 1.02f} : new float[] {1f, 0.5f};
        Animator alpha = getAnimatorOfFloat(this, View.ALPHA, fadeDuration, fadeStartDelay,
                LINEAR, alphaValues);
        Animator arrowAlpha = getAnimatorOfFloat(mArrow, View.ALPHA, fadeDuration, fadeStartDelay,
                LINEAR, alphaValues);
        Animator scaleY = getAnimatorOfFloat(this, View.SCALE_Y, scaleDuration, 0, interpolator,
                scaleValues);
        Animator scaleX = getAnimatorOfFloat(this, View.SCALE_X, scaleDuration, 0, interpolator,
                scaleValues);

        final AnimatorSet animatorSet = new AnimatorSet();
        if (isOpening) {
            float[] scaleValuesOvershoot = new float[] {1.02f, 1f};
            PathInterpolator overshootInterpolator = new PathInterpolator(0.3f, 0, 0.33f, 1f);
            Animator overshootY = getAnimatorOfFloat(this, View.SCALE_Y,
                    OPEN_OVERSHOOT_DURATION_U, scaleDuration, overshootInterpolator,
                    scaleValuesOvershoot);
            Animator overshootX = getAnimatorOfFloat(this, View.SCALE_X,
                    OPEN_OVERSHOOT_DURATION_U, scaleDuration, overshootInterpolator,
                    scaleValuesOvershoot);

            animatorSet.playTogether(alpha, arrowAlpha, scaleY, scaleX, overshootX, overshootY);
        } else {
            animatorSet.playTogether(alpha, arrowAlpha, scaleY, scaleX);
        }

        fadeInChildViews(this, alphaValues, childFadeStartDelay, childFadeDuration, animatorSet);
        return animatorSet;
    }

    private Animator getAnimatorOfFloat(View view, Property<View, Float> property,
            int duration, int startDelay, Interpolator interpolator,  float... values) {
        Animator animator = ObjectAnimator.ofFloat(view, property, values);
        animator.setDuration(duration);
        animator.setInterpolator(interpolator);
        animator.setStartDelay(startDelay);
        return animator;
    }

    /**
     * Called when creating the open transition allowing subclass can add additional animations.
     */
    protected void onCreateOpenAnimation(AnimatorSet anim) { }

    /**
     * Called when creating the close transition allowing subclass can add additional animations.
     */
    protected void onCreateCloseAnimation(AnimatorSet anim) { }

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
        mOnCloseCallbacks.executeAllAndClear();
    }

    /**
     * Callbacks to be called when the popup is closed
     */
    public void addOnCloseCallback(Runnable callback) {
        mOnCloseCallbacks.add(callback);
    }

    protected BaseDragLayer getPopupContainer() {
        return mActivityContext.getDragLayer();
    }
}
