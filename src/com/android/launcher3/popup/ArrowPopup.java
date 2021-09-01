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

import static com.android.launcher3.anim.Interpolators.ACCELERATED_EASE;
import static com.android.launcher3.anim.Interpolators.DECELERATED_EASE;
import static com.android.launcher3.anim.Interpolators.LINEAR;
import static com.android.launcher3.config.FeatureFlags.ENABLE_LOCAL_COLOR_POPUPS;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.util.AttributeSet;
import android.util.Pair;
import android.util.SparseIntArray;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.animation.Interpolator;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.BaseDraggingActivity;
import com.android.launcher3.InsettableFrameLayout;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherState;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.Workspace;
import com.android.launcher3.dragndrop.DragLayer;
import com.android.launcher3.shortcuts.DeepShortcutView;
import com.android.launcher3.statemanager.StatefulActivity;
import com.android.launcher3.util.Themes;
import com.android.launcher3.views.BaseDragLayer;
import com.android.launcher3.widget.LocalColorExtractor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

/**
 * A container for shortcuts to deep links and notifications associated with an app.
 *
 * @param <T> The activity on with the popup shows
 */
public abstract class ArrowPopup<T extends StatefulActivity<LauncherState>>
        extends AbstractFloatingView {

    // Duration values (ms) for popup open and close animations.
    private static final int OPEN_DURATION = 276;
    private static final int OPEN_FADE_START_DELAY = 0;
    private static final int OPEN_FADE_DURATION = 38;
    private static final int OPEN_CHILD_FADE_START_DELAY = 38;
    private static final int OPEN_CHILD_FADE_DURATION = 76;

    private static final int CLOSE_DURATION = 200;
    private static final int CLOSE_FADE_START_DELAY = 140;
    private static final int CLOSE_FADE_DURATION = 50;
    private static final int CLOSE_CHILD_FADE_START_DELAY = 0;
    private static final int CLOSE_CHILD_FADE_DURATION = 140;

    // Index used to get background color when using local wallpaper color extraction,
    private static final int DARK_COLOR_EXTRACTION_INDEX = android.R.color.system_neutral2_800;
    private static final int LIGHT_COLOR_EXTRACTION_INDEX = android.R.color.system_accent2_50;

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

    protected AnimatorSet mOpenCloseAnimator;
    protected boolean mDeferContainerRemoval;

    private final GradientDrawable mRoundedTop;
    private final GradientDrawable mRoundedBottom;

    private Runnable mOnCloseCallback = () -> { };

    // The rect string of the view that the arrow is attached to, in screen reference frame.
    protected String mArrowColorRectString;
    private int mArrowColor;
    protected final HashMap<String, View> mViewForRect = new HashMap<>();

    @Nullable protected LocalColorExtractor mColorExtractor;

    private final float mElevation;
    private final int mBackgroundColor;

    private final String mIterateChildrenTag;

    private final int[] mColors;

    public ArrowPopup(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mInflater = LayoutInflater.from(context);
        mOutlineRadius = Themes.getDialogCornerRadius(context);
        mLauncher = BaseDraggingActivity.fromContext(context);
        mIsRtl = Utilities.isRtl(getResources());

        mBackgroundColor = Themes.getAttrColor(context, R.attr.popupColorPrimary);
        mArrowColor = mBackgroundColor;
        mElevation = getResources().getDimension(R.dimen.deep_shortcuts_elevation);

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
        mRoundedTop.setColor(mBackgroundColor);
        mRoundedTop.setCornerRadii(new float[] { mOutlineRadius, mOutlineRadius, mOutlineRadius,
                mOutlineRadius, smallerRadius, smallerRadius, smallerRadius, smallerRadius});

        mRoundedBottom = new GradientDrawable();
        mRoundedBottom.setColor(mBackgroundColor);
        mRoundedBottom.setCornerRadii(new float[] { smallerRadius, smallerRadius, smallerRadius,
                smallerRadius, mOutlineRadius, mOutlineRadius, mOutlineRadius, mOutlineRadius});

        mIterateChildrenTag = getContext().getString(R.string.popup_container_iterate_children);

        boolean isAboveAnotherSurface = getTopOpenViewWithType(mLauncher, TYPE_FOLDER) != null
                || mLauncher.getStateManager().getState() == LauncherState.ALL_APPS;
        if (!isAboveAnotherSurface && Utilities.ATLEAST_S && ENABLE_LOCAL_COLOR_POPUPS.get()) {
            setupColorExtraction();
        }

        if (isAboveAnotherSurface) {
            mColors = new int[] {
                    getColorStateList(context, R.color.popup_shade_first).getDefaultColor()};
        } else {
            mColors = new int[] {
                    getColorStateList(context, R.color.popup_shade_first).getDefaultColor(),
                    getColorStateList(context, R.color.popup_shade_second).getDefaultColor(),
                    getColorStateList(context, R.color.popup_shade_third).getDefaultColor()};
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
    public void assignMarginsAndBackgrounds(ViewGroup viewGroup) {
        assignMarginsAndBackgrounds(viewGroup, Color.TRANSPARENT);
    }

    /**
     * @param backgroundColor When Color.TRANSPARENT, we get color from {@link #mColors}.
     *                        Otherwise, we will use this color for all child views.
     */
    private void assignMarginsAndBackgrounds(ViewGroup viewGroup, int backgroundColor) {
        final boolean getColorFromColorArray = backgroundColor == Color.TRANSPARENT;

        int count = viewGroup.getChildCount();
        int totalVisibleShortcuts = 0;
        for (int i = 0; i < count; i++) {
            View view = viewGroup.getChildAt(i);
            if (view.getVisibility() == VISIBLE && view instanceof DeepShortcutView) {
                totalVisibleShortcuts++;
            }
        }

        int numVisibleChild = 0;
        int numVisibleShortcut = 0;
        View lastView = null;
        AnimatorSet colorAnimator = new AnimatorSet();
        for (int i = 0; i < count; i++) {
            View view = viewGroup.getChildAt(i);
            if (view.getVisibility() == VISIBLE) {
                if (lastView != null) {
                    MarginLayoutParams mlp = (MarginLayoutParams) lastView.getLayoutParams();
                    mlp.bottomMargin = mMargin;
                }
                lastView = view;
                MarginLayoutParams mlp = (MarginLayoutParams) lastView.getLayoutParams();
                mlp.bottomMargin = 0;


                if (getColorFromColorArray) {
                    backgroundColor = mColors[numVisibleChild % mColors.length];
                }

                if (view instanceof ViewGroup && mIterateChildrenTag.equals(view.getTag())) {
                    assignMarginsAndBackgrounds((ViewGroup) view, backgroundColor);
                    numVisibleChild++;
                    continue;
                }

                if (view instanceof DeepShortcutView) {
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

                if (!ENABLE_LOCAL_COLOR_POPUPS.get()) {
                    setChildColor(view, backgroundColor, colorAnimator);
                    // Arrow color matches the first child or the last child.
                    if (!mIsAboveIcon && numVisibleChild == 0) {
                        mArrowColor = backgroundColor;
                    } else if (mIsAboveIcon) {
                        mArrowColor = backgroundColor;
                    }
                }

                numVisibleChild++;
            }
        }

        colorAnimator.setDuration(0).start();
        measure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED);
    }


    @TargetApi(Build.VERSION_CODES.S)
    private int getExtractedColor(SparseIntArray colors) {
        int index = Utilities.isDarkTheme(getContext())
                ? DARK_COLOR_EXTRACTION_INDEX
                : LIGHT_COLOR_EXTRACTION_INDEX;
        return colors.get(index, mBackgroundColor);
    }

    @TargetApi(Build.VERSION_CODES.S)
    private void setupColorExtraction() {
        Workspace workspace = mLauncher.findViewById(R.id.workspace);
        if (workspace == null) {
            return;
        }

        mColorExtractor = LocalColorExtractor.newInstance(mLauncher);
        mColorExtractor.setListener((rect, extractedColors) -> {
            String rectString = rect.toShortString();
            View v = mViewForRect.get(rectString);
            AnimatorSet colors = new AnimatorSet();
            if (v != null) {
                int newColor = getExtractedColor(extractedColors);
                setChildColor(v, newColor, colors);
                int numChildren = v instanceof ViewGroup ? ((ViewGroup) v).getChildCount() : 0;
                for (int i = 0; i < numChildren; ++i) {
                    View childView = ((ViewGroup) v).getChildAt(i);
                    setChildColor(childView, newColor, colors);

                }
                if (rectString.equals(mArrowColorRectString)) {
                    mArrowColor = newColor;
                    updateArrowColor();
                }
            }
            colors.setDuration(150);
            v.post(colors::start);
        });
    }

    protected void addPreDrawForColorExtraction(Launcher launcher) {
        getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                getViewTreeObserver().removeOnPreDrawListener(this);
                initColorExtractionLocations(launcher);
                return true;
            }
        });
    }

    /**
     * Returns list of child views that will receive local color extraction treatment.
     * Note: Order should match the view hierarchy.
     */
    protected List<View> getChildrenForColorExtraction() {
        return Collections.emptyList();
    }

    private void initColorExtractionLocations(Launcher launcher) {
        if (mColorExtractor == null) {
            return;
        }
        ArrayList<RectF> locations = new ArrayList<>();

        boolean firstVisibleChild = true;
        // Order matters here, since we need the arrow to match the color of its adjacent view.
        for (View view : getChildrenForColorExtraction()) {
            if (view != null && view.getVisibility() == VISIBLE) {
                RectF rf = new RectF();
                mColorExtractor.getExtractedRectForView(launcher,
                        launcher.getWorkspace().getCurrentPage(), view, rf);
                if (!rf.isEmpty()) {
                    locations.add(rf);
                    String rectString = rf.toShortString();
                    mViewForRect.put(rectString, view);
                    if (mIsAboveIcon) {
                        mArrowColorRectString = rectString;
                    } else {
                        if (firstVisibleChild) {
                            mArrowColorRectString = rectString;
                        }
                    }

                    if (firstVisibleChild) {
                        firstVisibleChild = false;
                    }

                }
            }
        }
        if (!locations.isEmpty()) {
            mColorExtractor.addLocation(locations);
        }
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
        assignMarginsAndBackgrounds(this);
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
        assignMarginsAndBackgrounds(this);
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
            updateArrowColor();
        }

        mArrow.setPivotX(mArrowWidth / 2.0f);
        mArrow.setPivotY(mIsAboveIcon ? mArrowHeight : 0);
    }

    private void updateArrowColor() {
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

    private void animateOpen() {
        setVisibility(View.VISIBLE);

        mOpenCloseAnimator = getOpenCloseAnimator(true, OPEN_DURATION, OPEN_FADE_START_DELAY,
                OPEN_FADE_DURATION, OPEN_CHILD_FADE_START_DELAY, OPEN_CHILD_FADE_DURATION,
                DECELERATED_EASE);
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

    private AnimatorSet getOpenCloseAnimator(boolean isOpening, int totalDuration,
            int fadeStartDelay, int fadeDuration, int childFadeStartDelay,
            int childFadeDuration, Interpolator interpolator) {
        final AnimatorSet animatorSet = new AnimatorSet();
        float[] alphaValues = isOpening ? new float[] {0, 1} : new float[] {1, 0};
        float[] scaleValues = isOpening ? new float[] {0.5f, 1} : new float[] {1, 0.5f};

        ValueAnimator fade = ValueAnimator.ofFloat(alphaValues);
        fade.setStartDelay(fadeStartDelay);
        fade.setDuration(fadeDuration);
        fade.setInterpolator(LINEAR);
        fade.addUpdateListener(anim -> {
            float alpha = (float) anim.getAnimatedValue();
            mArrow.setAlpha(alpha);
            setAlpha(alpha);
        });
        animatorSet.play(fade);

        setPivotX(mIsLeftAligned ? 0 : getMeasuredWidth());
        setPivotY(mIsAboveIcon ? getMeasuredHeight() : 0);
        Animator scale = ObjectAnimator.ofFloat(this, View.SCALE_Y, scaleValues);
        scale.setDuration(totalDuration);
        scale.setInterpolator(interpolator);
        animatorSet.play(scale);

        fadeInChildViews(this, alphaValues, childFadeStartDelay, childFadeDuration, animatorSet);

        return animatorSet;
    }

    private void fadeInChildViews(ViewGroup group, float[] alphaValues, long startDelay,
            long duration, AnimatorSet out) {
        for (int i = group.getChildCount() - 1; i >= 0; --i) {
            View view = group.getChildAt(i);
            if (view.getVisibility() == VISIBLE && view instanceof ViewGroup) {
                if (mIterateChildrenTag.equals(view.getTag())) {
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

        mOpenCloseAnimator = getOpenCloseAnimator(false, CLOSE_DURATION, CLOSE_FADE_START_DELAY,
                CLOSE_FADE_DURATION, CLOSE_CHILD_FADE_START_DELAY, CLOSE_CHILD_FADE_DURATION,
                ACCELERATED_EASE);
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
        mOnCloseCallback.run();
        mArrowColorRectString = null;
        mViewForRect.clear();
        if (mColorExtractor != null) {
            mColorExtractor.removeLocations();
            mColorExtractor.setListener(null);
        }
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
