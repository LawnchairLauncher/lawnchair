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

package com.android.wm.shell.bubbles;

import static com.android.wm.shell.protolog.ShellProtoLogGroup.WM_SHELL_BUBBLES;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Insets;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.Surface;
import android.view.WindowManager;

import androidx.annotation.VisibleForTesting;

import com.android.internal.protolog.common.ProtoLog;
import com.android.launcher3.icons.IconNormalizer;
import com.android.wm.shell.R;
import com.android.wm.shell.common.bubbles.BubbleBarLocation;

/**
 * Keeps track of display size, configuration, and specific bubble sizes. One place for all
 * placement and positioning calculations to refer to.
 */
public class BubblePositioner {

    /** The screen edge the bubble stack is pinned to */
    public enum StackPinnedEdge {
        LEFT,
        RIGHT
    }

    /** When the bubbles are collapsed in a stack only some of them are shown, this is how many. **/
    public static final int NUM_VISIBLE_WHEN_RESTING = 2;
    /** Indicates a bubble's height should be the maximum available space. **/
    public static final int MAX_HEIGHT = -1;
    /** The max percent of screen width to use for the flyout on large screens. */
    public static final float FLYOUT_MAX_WIDTH_PERCENT_LARGE_SCREEN = 0.3f;
    /** The max percent of screen width to use for the flyout on phone. */
    public static final float FLYOUT_MAX_WIDTH_PERCENT = 0.6f;
    /** The percent of screen width for the expanded view on a small tablet. **/
    private static final float EXPANDED_VIEW_SMALL_TABLET_WIDTH_PERCENT = 0.72f;
    /** The percent of screen width for the expanded view when shown in the bubble bar. **/
    private static final float EXPANDED_VIEW_BUBBLE_BAR_PORTRAIT_WIDTH_PERCENT = 0.7f;
    /** The percent of screen width for the expanded view when shown in the bubble bar. **/
    private static final float EXPANDED_VIEW_BUBBLE_BAR_LANDSCAPE_WIDTH_PERCENT = 0.4f;

    private Context mContext;
    private DeviceConfig mDeviceConfig;
    private Rect mScreenRect;
    private @Surface.Rotation int mRotation = Surface.ROTATION_0;
    private Insets mInsets;
    private boolean mImeVisible;
    private int mImeHeight;
    private Rect mPositionRect;
    private int mDefaultMaxBubbles;
    private int mMaxBubbles;
    private int mBubbleSize;
    private int mSpacingBetweenBubbles;
    private int mBubblePaddingTop;
    private int mBubbleOffscreenAmount;
    private int mStackOffset;
    private int mBubbleElevation;

    private int mExpandedViewMinHeight;
    private int mExpandedViewLargeScreenWidth;
    private int mExpandedViewLargeScreenInsetClosestEdge;
    private int mExpandedViewLargeScreenInsetFurthestEdge;

    private int mOverflowWidth;
    private int mExpandedViewPadding;
    private int mPointerMargin;
    private int mPointerWidth;
    private int mPointerHeight;
    private int mPointerOverlap;
    private int mManageButtonHeightIncludingMargins;
    private int mManageButtonHeight;
    private int mOverflowHeight;
    private int mMinimumFlyoutWidthLargeScreen;

    private PointF mRestingStackPosition;

    private boolean mShowingInBubbleBar;
    private BubbleBarLocation mBubbleBarLocation = BubbleBarLocation.DEFAULT;
    private int mBubbleBarTopOnScreen;

    public BubblePositioner(Context context, WindowManager windowManager) {
        mContext = context;
        mDeviceConfig = DeviceConfig.create(context, windowManager);
        update(mDeviceConfig);
    }

    /**
     * Available space and inset information. Call this when config changes
     * occur or when added to a window.
     */
    public void update(DeviceConfig deviceConfig) {
        mDeviceConfig = deviceConfig;
        ProtoLog.d(WM_SHELL_BUBBLES, "update positioner: "
                        + "rotation=%d insets=%s largeScreen=%b "
                        + "smallTablet=%b isBubbleBar=%b bounds=%s",
                mRotation, deviceConfig.getInsets(), deviceConfig.isLargeScreen(),
                deviceConfig.isSmallTablet(), mShowingInBubbleBar,
                deviceConfig.getWindowBounds());
        updateInternal(mRotation, deviceConfig.getInsets(), deviceConfig.getWindowBounds());
    }

    @VisibleForTesting
    public void updateInternal(int rotation, Insets insets, Rect bounds) {
        BubbleStackView.RelativeStackPosition prevStackPosition = null;
        if (mRestingStackPosition != null && mScreenRect != null && !mScreenRect.equals(bounds)) {
            // Save the resting position as a relative position with the previous bounds, at the
            // end of the update we'll restore it based on the new bounds.
            prevStackPosition = new BubbleStackView.RelativeStackPosition(getRestingPosition(),
                    getAllowableStackPositionRegion(1));
        }
        mRotation = rotation;
        mInsets = insets;

        mScreenRect = new Rect(bounds);
        mPositionRect = new Rect(bounds);
        mPositionRect.left += mInsets.left;
        mPositionRect.top += mInsets.top;
        mPositionRect.right -= mInsets.right;
        mPositionRect.bottom -= mInsets.bottom;

        Resources res = mContext.getResources();
        mBubbleSize = res.getDimensionPixelSize(R.dimen.bubble_size);
        mSpacingBetweenBubbles = res.getDimensionPixelSize(R.dimen.bubble_spacing);
        mDefaultMaxBubbles = res.getInteger(R.integer.bubbles_max_rendered);
        mExpandedViewPadding = res.getDimensionPixelSize(R.dimen.bubble_expanded_view_padding);
        mBubblePaddingTop = res.getDimensionPixelSize(R.dimen.bubble_padding_top);
        mBubbleOffscreenAmount = res.getDimensionPixelSize(R.dimen.bubble_stack_offscreen);
        mStackOffset = res.getDimensionPixelSize(R.dimen.bubble_stack_offset);
        mBubbleElevation = res.getDimensionPixelSize(R.dimen.bubble_elevation);

        if (mShowingInBubbleBar) {
            mExpandedViewLargeScreenWidth = Math.min(
                    res.getDimensionPixelSize(R.dimen.bubble_bar_expanded_view_width),
                    mPositionRect.width() - 2 * mExpandedViewPadding
            );
        } else if (mDeviceConfig.isSmallTablet()) {
            mExpandedViewLargeScreenWidth = (int) (bounds.width()
                    * EXPANDED_VIEW_SMALL_TABLET_WIDTH_PERCENT);
        } else {
            mExpandedViewLargeScreenWidth =
                    res.getDimensionPixelSize(R.dimen.bubble_expanded_view_largescreen_width);
        }
        if (mDeviceConfig.isLargeScreen()) {
            if (mDeviceConfig.isSmallTablet()) {
                final int centeredInset = (bounds.width() - mExpandedViewLargeScreenWidth) / 2;
                mExpandedViewLargeScreenInsetClosestEdge = centeredInset;
                mExpandedViewLargeScreenInsetFurthestEdge = centeredInset;
            } else {
                mExpandedViewLargeScreenInsetClosestEdge = res.getDimensionPixelSize(
                        R.dimen.bubble_expanded_view_largescreen_landscape_padding);
                mExpandedViewLargeScreenInsetFurthestEdge = bounds.width()
                        - mExpandedViewLargeScreenInsetClosestEdge
                        - mExpandedViewLargeScreenWidth;
            }
        } else {
            mExpandedViewLargeScreenInsetClosestEdge = mExpandedViewPadding;
            mExpandedViewLargeScreenInsetFurthestEdge = mExpandedViewPadding;
        }

        mOverflowWidth = res.getDimensionPixelSize(R.dimen.bubble_expanded_view_overflow_width);
        mPointerWidth = res.getDimensionPixelSize(R.dimen.bubble_pointer_width);
        mPointerHeight = res.getDimensionPixelSize(R.dimen.bubble_pointer_height);
        mPointerMargin = res.getDimensionPixelSize(R.dimen.bubble_pointer_margin);
        mPointerOverlap = res.getDimensionPixelSize(R.dimen.bubble_pointer_overlap);
        mManageButtonHeight = res.getDimensionPixelSize(R.dimen.bubble_manage_button_height);
        mManageButtonHeightIncludingMargins =
                mManageButtonHeight
                + 2 * res.getDimensionPixelSize(R.dimen.bubble_manage_button_margin);
        mExpandedViewMinHeight = res.getDimensionPixelSize(R.dimen.bubble_expanded_default_height);
        mOverflowHeight = res.getDimensionPixelSize(R.dimen.bubble_overflow_height);
        mMinimumFlyoutWidthLargeScreen = res.getDimensionPixelSize(
                R.dimen.bubbles_flyout_min_width_large_screen);

        mMaxBubbles = calculateMaxBubbles();

        if (prevStackPosition != null) {
            // Get the new resting position based on the updated values
            mRestingStackPosition = prevStackPosition.getAbsolutePositionInRegion(
                    getAllowableStackPositionRegion(1));
        }
    }

    /**
     * @return the maximum number of bubbles that can fit on the screen when expanded. If the
     * screen size / screen density is too small to support the default maximum number, then
     * the number will be adjust to something lower to ensure everything is presented nicely.
     */
    private int calculateMaxBubbles() {
        // Use the shortest edge.
        // In portrait the bubbles should align with the expanded view so subtract its padding.
        // We always show the overflow so subtract one bubble size.
        int padding = showBubblesVertically() ? 0 : (mExpandedViewPadding * 2);
        int availableSpace = Math.min(mPositionRect.width(), mPositionRect.height())
                - padding
                - mBubbleSize;
        // Each of the bubbles have spacing because the overflow is at the end.
        int howManyFit = availableSpace / (mBubbleSize + mSpacingBetweenBubbles);
        if (howManyFit < mDefaultMaxBubbles) {
            // Not enough space for the default.
            return howManyFit;
        }
        return mDefaultMaxBubbles;
    }


    /**
     * @return a rect of available screen space accounting for orientation, system bars and cutouts.
     * Does not account for IME.
     */
    public Rect getAvailableRect() {
        return mPositionRect;
    }

    /**
     * @return a rect of the screen size.
     */
    public Rect getScreenRect() {
        return mScreenRect;
    }

    /**
     * @return the relevant insets (status bar, nav bar, cutouts). If taskbar is showing, its
     * inset is not included here.
     */
    public Insets getInsets() {
        return mInsets;
    }

    /** @return whether the device is in landscape orientation. */
    public boolean isLandscape() {
        return mDeviceConfig.isLandscape();
    }

    /**
     * On large screen (not small tablet), while in portrait, expanded bubbles are aligned to
     * the bottom of the screen.
     *
     * @return whether bubbles are bottom aligned while expanded
     */
    public boolean areBubblesBottomAligned() {
        return isLargeScreen()
                && !mDeviceConfig.isSmallTablet()
                && !isLandscape();
    }

    /** @return whether the screen is considered large. */
    public boolean isLargeScreen() {
        return mDeviceConfig.isLargeScreen();
    }

    /**
     * Indicates how bubbles appear when expanded.
     *
     * When false, bubbles display at the top of the screen with the expanded view
     * below them. When true, bubbles display at the edges of the screen with the expanded view
     * to the left or right side.
     */
    public boolean showBubblesVertically() {
        return isLandscape() || mDeviceConfig.isLargeScreen();
    }

    /** Size of the bubble. */
    public int getBubbleSize() {
        return mBubbleSize;
    }

    /** The amount of padding at the top of the screen that the bubbles avoid when being placed. */
    public int getBubblePaddingTop() {
        return mBubblePaddingTop;
    }

    /** The amount the stack hang off of the screen when collapsed. */
    public int getStackOffScreenAmount() {
        return mBubbleOffscreenAmount;
    }

    /** Offset of bubbles in the stack (i.e. how much they overlap). */
    public int getStackOffset() {
        return mStackOffset;
    }

    /** Size of the visible (non-overlapping) part of the pointer. */
    public int getPointerSize() {
        return mPointerHeight - mPointerOverlap;
    }

    /** The maximum number of bubbles that can be displayed comfortably on screen. */
    public int getMaxBubbles() {
        return mMaxBubbles;
    }

    /** The height for the IME if it's visible. **/
    public int getImeHeight() {
        return mImeVisible ? mImeHeight : 0;
    }

    /** Return top position of the IME if it's visible */
    public int getImeTop() {
        if (mImeVisible) {
            return getScreenRect().bottom - getImeHeight() - getInsets().bottom;
        }
        return 0;
    }

    /** Returns whether the IME is visible. */
    public boolean isImeVisible() {
        return mImeVisible;
    }

    /** Sets whether the IME is visible. **/
    public void setImeVisible(boolean visible, int height) {
        mImeVisible = visible;
        mImeHeight = height;
    }

    private int getExpandedViewLargeScreenInsetFurthestEdge(boolean isOverflow) {
        if (isOverflow && mDeviceConfig.isLargeScreen()) {
            return mScreenRect.width()
                    - mExpandedViewLargeScreenInsetClosestEdge
                    - mOverflowWidth;
        }
        return mExpandedViewLargeScreenInsetFurthestEdge;
    }

    /**
     * Calculates the padding for the bubble expanded view.
     *
     * Some specifics:
     * On large screens the width of the expanded view is restricted via this padding.
     * On phone landscape the bubble overflow expanded view is also restricted via this padding.
     * On large screens & landscape no top padding is set, the top position is set via translation.
     * On phone portrait top padding is set as the space between the tip of the pointer and the
     * bubble.
     * When the overflow is shown it doesn't have the manage button to pad out the bottom so
     * padding is added.
     */
    public int[] getExpandedViewContainerPadding(boolean onLeft, boolean isOverflow) {
        final int pointerTotalHeight = getPointerSize();
        final int expandedViewLargeScreenInsetFurthestEdge =
                getExpandedViewLargeScreenInsetFurthestEdge(isOverflow);
        int[] paddings = new int[4];
        if (mDeviceConfig.isLargeScreen()) {
            // Note:
            // If we're in portrait OR if we're a small tablet, then the two insets values will
            // be equal. If we're landscape and a large tablet, the two values will be different.
            // [left, top, right, bottom]
            paddings[0] = onLeft
                    ? mExpandedViewLargeScreenInsetClosestEdge - pointerTotalHeight
                    : expandedViewLargeScreenInsetFurthestEdge;
            paddings[1] = 0;
            paddings[2] = onLeft
                    ? expandedViewLargeScreenInsetFurthestEdge
                    : mExpandedViewLargeScreenInsetClosestEdge - pointerTotalHeight;
            // Overflow doesn't show manage button / get padding from it so add padding here
            paddings[3] = isOverflow ? mExpandedViewPadding : 0;
            return paddings;
        } else {
            int leftPadding = mInsets.left + mExpandedViewPadding;
            int rightPadding = mInsets.right + mExpandedViewPadding;
            if (showBubblesVertically()) {
                if (!onLeft) {
                    rightPadding += mBubbleSize - pointerTotalHeight;
                    leftPadding += isOverflow
                            ? (mPositionRect.width() - rightPadding - mOverflowWidth)
                            : 0;
                } else {
                    leftPadding += mBubbleSize - pointerTotalHeight;
                    rightPadding += isOverflow
                            ? (mPositionRect.width() - leftPadding - mOverflowWidth)
                            : 0;
                }
            }
            // [left, top, right, bottom]
            paddings[0] = leftPadding;
            paddings[1] = showBubblesVertically() ? 0 : mPointerMargin;
            paddings[2] = rightPadding;
            paddings[3] = 0;
            return paddings;
        }
    }

    /** Returns the width of the task view content. */
    public int getTaskViewContentWidth(boolean onLeft) {
        int[] paddings = getExpandedViewContainerPadding(onLeft, /* isOverflow = */ false);
        int pointerOffset = showBubblesVertically() ? getPointerSize() : 0;
        return mScreenRect.width() - paddings[0] - paddings[2] - pointerOffset;
    }

    /** Gets the y position of the expanded view if it was top-aligned. */
    public int getExpandedViewYTopAligned() {
        final int top = getAvailableRect().top;
        if (showBubblesVertically()) {
            return top - mPointerWidth + mExpandedViewPadding;
        } else {
            return top + mBubbleSize + mPointerMargin;
        }
    }

    /**
     * Calculate the maximum height the expanded view can be depending on where it's placed on
     * the screen and the size of the elements around it (e.g. padding, pointer, manage button).
     */
    public int getMaxExpandedViewHeight(boolean isOverflow) {
        if (mDeviceConfig.isLargeScreen() && !mDeviceConfig.isSmallTablet() && !isOverflow) {
            return getExpandedViewHeightForLargeScreen();
        }
        // Subtract top insets because availableRect.height would account for that
        int expandedContainerY = getExpandedViewYTopAligned() - getInsets().top;
        int paddingTop = showBubblesVertically()
                ? 0
                : mPointerHeight;
        // Subtract pointer size because it's laid out in LinearLayout with the expanded view.
        int pointerSize = showBubblesVertically()
                ? mPointerWidth
                : (mPointerHeight + mPointerMargin);
        int bottomPadding = isOverflow ? mExpandedViewPadding : mManageButtonHeightIncludingMargins;
        return getAvailableRect().height()
                - expandedContainerY
                - paddingTop
                - pointerSize
                - bottomPadding;
    }

    /**
     * Returns the height to use for the expanded view when showing on a large screen.
     */
    public int getExpandedViewHeightForLargeScreen() {
        // the expanded view height on large tablets is calculated based on the shortest screen
        // size and is the same in both portrait and landscape
        int maxVerticalInset = Math.max(mInsets.top, mInsets.bottom);
        int shortestScreenSide = Math.min(getScreenRect().height(), getScreenRect().width());
        // Subtract pointer size because it's laid out in LinearLayout with the expanded view.
        return shortestScreenSide - maxVerticalInset * 2
                - mManageButtonHeight - mPointerWidth - mExpandedViewPadding * 2;
    }

    /**
     * Determines the height for the bubble, ensuring a minimum height. If the height should be as
     * big as available, returns {@link #MAX_HEIGHT}.
     */
    public float getExpandedViewHeight(BubbleViewProvider bubble) {
        boolean isOverflow = bubble == null || BubbleOverflow.KEY.equals(bubble.getKey());
        if (isOverflow && showBubblesVertically() && !mDeviceConfig.isLargeScreen()) {
            // overflow in landscape on phone is max
            return MAX_HEIGHT;
        }
        float desiredHeight = isOverflow
                ? mOverflowHeight
                : ((Bubble) bubble).getDesiredHeight(mContext);
        desiredHeight = Math.max(desiredHeight, mExpandedViewMinHeight);
        if (desiredHeight > getMaxExpandedViewHeight(isOverflow)) {
            return MAX_HEIGHT;
        }
        return desiredHeight;
    }

    /**
     * Gets the y position for the expanded view. This is the position on screen of the top
     * horizontal line of the expanded view.
     *
     * @param bubble the bubble being positioned.
     * @param bubblePosition the x position of the bubble if showing on top, the y position of the
     *                       bubble if showing vertically.
     * @return the y position for the expanded view.
     */
    public float getExpandedViewY(BubbleViewProvider bubble, float bubblePosition) {
        boolean isOverflow = bubble == null || BubbleOverflow.KEY.equals(bubble.getKey());
        float expandedViewHeight = getExpandedViewHeight(bubble);
        int topAlignment = getExpandedViewYTopAligned();
        int manageButtonHeight =
                isOverflow ? mExpandedViewPadding : mManageButtonHeightIncludingMargins;

        // On large screen portrait bubbles are bottom aligned.
        if (areBubblesBottomAligned() && expandedViewHeight == MAX_HEIGHT) {
            return mPositionRect.bottom - manageButtonHeight
                    - getExpandedViewHeightForLargeScreen() - mPointerWidth;
        }

        if (!showBubblesVertically() || expandedViewHeight == MAX_HEIGHT) {
            // Top-align when bubbles are shown at the top or are max size.
            return topAlignment;
        }

        // If we're here, we're showing vertically & developer has made height less than maximum.
        float pointerPosition = getPointerPosition(bubblePosition);
        float bottomIfCentered = pointerPosition + (expandedViewHeight / 2) + manageButtonHeight;
        float topIfCentered = pointerPosition - (expandedViewHeight / 2);
        if (topIfCentered > mPositionRect.top && mPositionRect.bottom > bottomIfCentered) {
            // Center it
            return pointerPosition - mPointerWidth - (expandedViewHeight / 2f);
        } else if (topIfCentered <= mPositionRect.top) {
            // Top align
            return topAlignment;
        } else {
            // Bottom align
            return mPositionRect.bottom - manageButtonHeight - expandedViewHeight - mPointerWidth;
        }
    }

    /**
     * The position the pointer points to, the center of the bubble.
     *
     * @param bubblePosition the x position of the bubble if showing on top, the y position of the
     *                       bubble if showing vertically.
     * @return the position the tip of the pointer points to. The x position if showing on top, the
     * y position if showing vertically.
     */
    public float getPointerPosition(float bubblePosition) {
        // TODO: I don't understand why it works but it does - why normalized in portrait
        //  & not in landscape? Am I missing ~2dp in the portrait expandedViewY calculation?
        final float normalizedSize = IconNormalizer.getNormalizedCircleSize(
                getBubbleSize());
        return showBubblesVertically()
                ? bubblePosition + (getBubbleSize() / 2f)
                : bubblePosition + (normalizedSize / 2f) - mPointerWidth;
    }

    private int getExpandedStackSize(int numberOfBubbles) {
        return (numberOfBubbles * mBubbleSize)
                + ((numberOfBubbles - 1) * mSpacingBetweenBubbles);
    }

    /**
     * Returns the position of the bubble on-screen when the stack is expanded.
     *
     * @param index the index of the bubble in the stack.
     * @param state state information about the stack to help with calculations.
     * @return the position of the bubble on-screen when the stack is expanded.
     */
    public PointF getExpandedBubbleXY(int index, BubbleStackView.StackViewState state) {
        boolean showBubblesVertically = showBubblesVertically();

        int onScreenIndex;
        if (showBubblesVertically || !mDeviceConfig.isRtl()) {
            onScreenIndex = index;
        } else {
            // If bubbles are shown horizontally, check if RTL language is used.
            // If RTL is active, position first bubble on the right and last on the left.
            // Last bubble has screen index 0 and first bubble has max screen index value.
            onScreenIndex = state.numberOfBubbles - 1 - index;
        }
        final float positionInRow = onScreenIndex * (mBubbleSize + mSpacingBetweenBubbles);
        final float rowStart = getBubbleRowStart(state);
        float x;
        float y;
        if (showBubblesVertically) {
            int inset = mExpandedViewLargeScreenInsetClosestEdge;
            y = rowStart + positionInRow;
            int left = mDeviceConfig.isLargeScreen()
                    ? inset - mExpandedViewPadding - mBubbleSize
                    : mPositionRect.left;
            int right = mDeviceConfig.isLargeScreen()
                    ? mPositionRect.right - inset + mExpandedViewPadding
                    : mPositionRect.right - mBubbleSize;
            x = state.onLeft
                    ? left
                    : right;
        } else {
            y = mPositionRect.top + mExpandedViewPadding;
            x = rowStart + positionInRow;
        }

        if (showBubblesVertically && mImeVisible) {
            return new PointF(x, getExpandedBubbleYForIme(onScreenIndex, state));
        }
        return new PointF(x, y);
    }

    private float getBubbleRowStart(BubbleStackView.StackViewState state) {
        final float expandedStackSize = getExpandedStackSize(state.numberOfBubbles);
        final float rowStart;
        if (areBubblesBottomAligned()) {
            final float expandedViewHeight = getExpandedViewHeightForLargeScreen();
            final float expandedViewBottom = mScreenRect.bottom
                    - Math.max(mInsets.bottom, mInsets.top)
                    - mManageButtonHeight - mPointerWidth;
            final float expandedViewCenter = expandedViewBottom - (expandedViewHeight / 2f);
            rowStart = expandedViewCenter - (expandedStackSize / 2f);
        } else {
            final float centerPosition = showBubblesVertically()
                    ? mPositionRect.centerY()
                    : mPositionRect.centerX();
            rowStart = centerPosition - (expandedStackSize / 2f);
        }
        return rowStart;
    }

    /**
     * Returns the position of the bubble on-screen when the stack is expanded and the IME
     * is showing.
     *
     * @param index the index of the bubble in the stack.
     * @param state information about the stack state (# of bubbles, selected bubble).
     * @return y position of the bubble on-screen when the stack is expanded.
     */
    private float getExpandedBubbleYForIme(int index, BubbleStackView.StackViewState state) {
        final float top = getAvailableRect().top + mExpandedViewPadding;
        if (!showBubblesVertically()) {
            // Showing horizontally: align to top
            return top;
        }

        // Showing vertically: might need to translate the bubbles above the IME.
        // Add spacing here to provide a margin between top of IME and bottom of bubble row.
        final float bottomHeight = getImeHeight() + mInsets.bottom + (mSpacingBetweenBubbles * 2);
        final float bottomInset = mScreenRect.bottom - bottomHeight;
        final float expandedStackSize = getExpandedStackSize(state.numberOfBubbles);
        final float rowTop = getBubbleRowStart(state);
        final float rowBottom = rowTop + expandedStackSize;
        float rowTopForIme = rowTop;
        if (rowBottom > bottomInset) {
            // We overlap with IME, must shift the bubbles
            float translationY = rowBottom - bottomInset;
            rowTopForIme = Math.max(rowTop - translationY, top);
            if (rowTop - translationY < top) {
                // Even if we shift the bubbles, they will still overlap with the IME.
                // Hide the overflow for a lil more space:
                final float expandedStackSizeNoO = getExpandedStackSize(state.numberOfBubbles - 1);
                final float centerPositionNoO = showBubblesVertically()
                        ? mPositionRect.centerY()
                        : mPositionRect.centerX();
                final float rowBottomNoO = centerPositionNoO + (expandedStackSizeNoO / 2f);
                final float rowTopNoO = centerPositionNoO - (expandedStackSizeNoO / 2f);
                translationY = rowBottomNoO - bottomInset;
                rowTopForIme = rowTopNoO - translationY;
            }
        }
        // Check if the selected bubble is within the appropriate space
        final float selectedPosition = rowTopForIme
                + (state.selectedIndex * (mBubbleSize + mSpacingBetweenBubbles));
        if (selectedPosition < top) {
            // We must always keep the selected bubble in view so we'll have to allow more overlap.
            rowTopForIme = top;
        }
        return rowTopForIme + (index * (mBubbleSize + mSpacingBetweenBubbles));
    }

    /**
     * @return the width of the bubble flyout (message originating from the bubble).
     */
    public float getMaxFlyoutSize() {
        if (isLargeScreen()) {
            return Math.max(mScreenRect.width() * FLYOUT_MAX_WIDTH_PERCENT_LARGE_SCREEN,
                    mMinimumFlyoutWidthLargeScreen);
        }
        return mScreenRect.width() * FLYOUT_MAX_WIDTH_PERCENT;
    }

    /**
     * Returns the z translation a specific bubble should use. When expanded we keep a slight
     * translation to ensure proper ordering when animating to / from collapsed state. When
     * collapsed, only the top two bubbles appear so only their shadows show.
     */
    public float getZTranslation(int index, boolean isOverflow, boolean isExpanded) {
        if (isOverflow) {
            return 0f; // overflow is lowest
        }
        return isExpanded
                // When expanded use minimal amount to keep order
                ? getMaxBubbles() - index
                // When collapsed, only the top two bubbles have elevation
                : index < NUM_VISIBLE_WHEN_RESTING
                        ? (getMaxBubbles() * mBubbleElevation) - index
                        : 0;
    }

    /** The elevation to use for bubble UI elements. */
    public int getBubbleElevation() {
        return mBubbleElevation;
    }

    /**
     * @return whether the stack is considered on the left side of the screen.
     */
    public boolean isStackOnLeft(PointF currentStackPosition) {
        if (currentStackPosition == null) {
            currentStackPosition = getRestingPosition();
        }
        final int stackCenter = (int) currentStackPosition.x + mBubbleSize / 2;
        return stackCenter < mScreenRect.width() / 2;
    }

    /**
     * Sets the stack's most recent position along the edge of the screen. This is saved when the
     * last bubble is removed, so that the stack can be restored in its previous position.
     */
    public void setRestingPosition(PointF position) {
        if (mRestingStackPosition == null) {
            mRestingStackPosition = new PointF(position);
        } else {
            mRestingStackPosition.set(position);
        }
    }

    /** The position the bubble stack should rest at when collapsed. */
    public PointF getRestingPosition() {
        if (mRestingStackPosition == null) {
            return getDefaultStartPosition();
        }
        return mRestingStackPosition;
    }

    /**
     * Returns whether the {@link #getRestingPosition()} is equal to the default start position
     * initialized for bubbles, if {@code true} this means the user hasn't moved the bubble
     * from the initial start position (or they haven't received a bubble yet).
     */
    public boolean hasUserModifiedDefaultPosition() {
        PointF defaultStart = getDefaultStartPosition();
        return mRestingStackPosition != null
                && !mRestingStackPosition.equals(defaultStart);
    }

    /**
     * Returns the stack position to use if we don't have a saved location or if user education
     * is being shown, for a normal bubble.
     */
    public PointF getDefaultStartPosition() {
        return getDefaultStartPosition(false /* isAppBubble */);
    }

    /**
     * The stack position to use if we don't have a saved location or if user education
     * is being shown.
     *
     * @param isAppBubble whether this start position is for an app bubble or not.
     */
    public PointF getDefaultStartPosition(boolean isAppBubble) {
        // Normal bubbles start on the left if we're in LTR, right otherwise.
        // TODO (b/294284894): update language around "app bubble" here
        // App bubbles start on the right in RTL, left otherwise.
        final boolean startOnLeft = isAppBubble ? mDeviceConfig.isRtl() : !mDeviceConfig.isRtl();
        return getStartPosition(startOnLeft ? StackPinnedEdge.LEFT : StackPinnedEdge.RIGHT);
    }

    /**
     * The stack position to use if user education is being shown.
     *
     * @param stackPinnedEdge the screen edge the stack is pinned to.
     */
    public PointF getStartPosition(StackPinnedEdge stackPinnedEdge) {
        final RectF allowableStackPositionRegion = getAllowableStackPositionRegion(
                1 /* default starts with 1 bubble */);
        if (isLargeScreen()) {
            // We want the stack to be visually centered on the edge, so we need to base it
            // of a rect that includes insets.
            final float desiredY = mScreenRect.height() / 2f - (mBubbleSize / 2f);
            final float offset = desiredY / mScreenRect.height();
            return new BubbleStackView.RelativeStackPosition(
                    stackPinnedEdge == StackPinnedEdge.LEFT,
                    offset)
                    .getAbsolutePositionInRegion(allowableStackPositionRegion);
        } else {
            final float startingVerticalOffset = mContext.getResources().getDimensionPixelOffset(
                    R.dimen.bubble_stack_starting_offset_y);
            // TODO: placement bug here because mPositionRect doesn't handle the overhanging edge
            return new BubbleStackView.RelativeStackPosition(
                    stackPinnedEdge == StackPinnedEdge.LEFT,
                    startingVerticalOffset / mPositionRect.height())
                    .getAbsolutePositionInRegion(allowableStackPositionRegion);
        }
    }

    /**
     * Returns the region that the stack position must stay within. This goes slightly off the left
     * and right sides of the screen, below the status bar/cutout and above the navigation bar.
     * While the stack position is not allowed to rest outside of these bounds, it can temporarily
     * be animated or dragged beyond them.
     */
    public RectF getAllowableStackPositionRegion(int bubbleCount) {
        final RectF allowableRegion = new RectF(getAvailableRect());
        final int imeHeight = getImeHeight();
        final float bottomPadding = bubbleCount > 1
                ? mBubblePaddingTop + mStackOffset
                : mBubblePaddingTop;
        allowableRegion.left -= mBubbleOffscreenAmount;
        allowableRegion.top += mBubblePaddingTop;
        allowableRegion.right += mBubbleOffscreenAmount - mBubbleSize;
        allowableRegion.bottom -= imeHeight + bottomPadding + mBubbleSize;
        return allowableRegion;
    }

    /**
     * Navigation bar has an area where system gestures can be started from.
     *
     * @return {@link Rect} for system navigation bar gesture zone
     */
    public Rect getNavBarGestureZone() {
        // Gesture zone height from the bottom
        int gestureZoneHeight = mContext.getResources().getDimensionPixelSize(
                com.android.internal.R.dimen.navigation_bar_gesture_height);
        Rect screen = getScreenRect();
        return new Rect(
                screen.left,
                screen.bottom - gestureZoneHeight,
                screen.right,
                screen.bottom);
    }

    //
    // Bubble bar specific sizes below.
    //

    /**
     * Sets whether bubbles are showing in the bubble bar from launcher.
     */
    public void setShowingInBubbleBar(boolean showingInBubbleBar) {
        mShowingInBubbleBar = showingInBubbleBar;
    }

    public void setBubbleBarLocation(BubbleBarLocation location) {
        mBubbleBarLocation = location;
    }

    public BubbleBarLocation getBubbleBarLocation() {
        return mBubbleBarLocation;
    }

    /**
     * @return <code>true</code> when bubble bar is on the left and <code>false</code> when on right
     */
    public boolean isBubbleBarOnLeft() {
        return mBubbleBarLocation.isOnLeft(mDeviceConfig.isRtl());
    }

    /**
     * Set top coordinate of bubble bar on screen
     */
    public void setBubbleBarTopOnScreen(int topOnScreen) {
        mBubbleBarTopOnScreen = topOnScreen;
    }

    /**
     * Returns the top coordinate of bubble bar on screen
     */
    public int getBubbleBarTopOnScreen() {
        return mBubbleBarTopOnScreen;
    }

    /**
     * How wide the expanded view should be when showing from the bubble bar.
     */
    public int getExpandedViewWidthForBubbleBar(boolean isOverflow) {
        return isOverflow ? mOverflowWidth : mExpandedViewLargeScreenWidth;
    }

    /**
     * How tall the expanded view should be when showing from the bubble bar.
     */
    public int getExpandedViewHeightForBubbleBar(boolean isOverflow) {
        if (isOverflow) {
            return mOverflowHeight;
        } else {
            return getBubbleBarExpandedViewHeightForLandscape();
        }
    }

    /**
     * Calculate the height of expanded view in landscape mode regardless current orientation.
     * Here is an explanation:
     * ------------------------ mScreenRect.top
     * |         top inset ↕  |
     * |-----------------------
     * |      16dp spacing ↕  |
     * |           ---------  | --- expanded view top
     * |           |       |  |   ↑
     * |           |       |  |   ↓ expanded view height
     * |           ---------  | --- expanded view bottom
     * |      16dp spacing ↕  |   ↑
     * |         @bubble bar@ |   | height of the bubble bar container
     * ------------------------   | already includes bottom inset and spacing
     * |      bottom inset ↕  |   ↓
     * |----------------------| --- mScreenRect.bottom
     */
    private int getBubbleBarExpandedViewHeightForLandscape() {
        int heightOfBubbleBarContainer =
                mScreenRect.height() - getExpandedViewBottomForBubbleBar();
        // getting landscape height from screen rect
        int expandedViewHeight = Math.min(mScreenRect.width(), mScreenRect.height());
        expandedViewHeight -= heightOfBubbleBarContainer; /* removing bubble container height */
        expandedViewHeight -= mInsets.top; /* removing top inset */
        expandedViewHeight -= mExpandedViewPadding; /* removing spacing */
        return expandedViewHeight;
    }


    /** The bottom position of the expanded view when showing above the bubble bar. */
    public int getExpandedViewBottomForBubbleBar() {
        return mBubbleBarTopOnScreen - mExpandedViewPadding;
    }

    /**
     * The amount of padding from the edge of the screen to the expanded view when in bubble bar.
     */
    public int getBubbleBarExpandedViewPadding() {
        return mExpandedViewPadding;
    }

    /**
     * Get bubble bar expanded view bounds on screen
     */
    public void getBubbleBarExpandedViewBounds(boolean onLeft, boolean isOverflowExpanded,
            Rect out) {
        final int padding = getBubbleBarExpandedViewPadding();
        final int width = getExpandedViewWidthForBubbleBar(isOverflowExpanded);
        final int height = getExpandedViewHeightForBubbleBar(isOverflowExpanded);

        out.set(0, 0, width, height);
        int left;
        if (onLeft) {
            left = getInsets().left + padding;
        } else {
            left = getAvailableRect().right - width - padding;
        }
        int top = getExpandedViewBottomForBubbleBar() - height;
        out.offsetTo(left, top);
    }
}
