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

package com.android.wm.shell.common.split;

import static android.content.res.Configuration.SCREEN_HEIGHT_DP_UNDEFINED;
import static android.content.res.Configuration.SCREEN_WIDTH_DP_UNDEFINED;
import static android.view.WindowManager.DOCKED_LEFT;
import static android.view.WindowManager.DOCKED_TOP;

import static com.android.internal.jank.InteractionJankMonitor.CUJ_SPLIT_SCREEN_DOUBLE_TAP_DIVIDER;
import static com.android.internal.jank.InteractionJankMonitor.CUJ_SPLIT_SCREEN_RESIZE;
import static com.android.wm.shell.shared.animation.Interpolators.EMPHASIZED;
import static com.android.wm.shell.shared.animation.Interpolators.FAST_OUT_SLOW_IN;
import static com.android.wm.shell.shared.animation.Interpolators.LINEAR;
import static com.android.wm.shell.shared.split.SplitScreenConstants.ANIMATING_OFFSCREEN_TAP;
import static com.android.wm.shell.shared.split.SplitScreenConstants.SNAP_TO_2_10_90;
import static com.android.wm.shell.shared.split.SplitScreenConstants.SNAP_TO_2_90_10;
import static com.android.wm.shell.shared.split.SplitScreenConstants.SNAP_TO_3_10_45_45;
import static com.android.wm.shell.shared.split.SplitScreenConstants.SNAP_TO_3_45_45_10;
import static com.android.wm.shell.shared.split.SplitScreenConstants.SNAP_TO_END_AND_DISMISS;
import static com.android.wm.shell.shared.split.SplitScreenConstants.SNAP_TO_START_AND_DISMISS;
import static com.android.wm.shell.shared.split.SplitScreenConstants.SPLIT_POSITION_BOTTOM_OR_RIGHT;
import static com.android.wm.shell.shared.split.SplitScreenConstants.SPLIT_POSITION_TOP_OR_LEFT;
import static com.android.wm.shell.shared.split.SplitScreenConstants.SPLIT_POSITION_UNDEFINED;
import static com.android.wm.shell.splitscreen.SplitScreenController.EXIT_REASON_DRAG_DIVIDER;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ValueAnimator;
import android.annotation.NonNull;
import android.app.ActivityManager;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Insets;
import android.graphics.Rect;
import android.os.Handler;
import android.util.Log;
import android.view.Display;
import android.view.InsetsController;
import android.view.InsetsSource;
import android.view.InsetsSourceControl;
import android.view.InsetsState;
import android.view.RoundedCorner;
import android.view.SurfaceControl;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.animation.Interpolator;
import android.view.animation.PathInterpolator;
import android.window.WindowContainerToken;
import android.window.WindowContainerTransaction;

import androidx.annotation.Nullable;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.jank.InteractionJankMonitor;
import com.android.internal.protolog.ProtoLog;
import com.android.wm.shell.Flags;
import com.android.wm.shell.R;
import com.android.wm.shell.ShellTaskOrganizer;
import com.android.wm.shell.common.DisplayController;
import com.android.wm.shell.common.DisplayImeController;
import com.android.wm.shell.common.DisplayInsetsController;
import com.android.wm.shell.common.DisplayLayout;
import com.android.wm.shell.common.pip.PipUtils;
import com.android.wm.shell.common.split.DividerSnapAlgorithm.SnapTarget;
import com.android.wm.shell.common.split.SplitWindowManager.ParentContainerCallbacks;
import com.android.wm.shell.protolog.ShellProtoLogGroup;
import com.android.wm.shell.shared.annotations.ShellMainThread;
import com.android.wm.shell.shared.desktopmode.DesktopState;
import com.android.wm.shell.shared.split.SplitScreenConstants.PersistentSnapPosition;
import com.android.wm.shell.shared.split.SplitScreenConstants.SnapPosition;
import com.android.wm.shell.shared.split.SplitScreenConstants.SplitPosition;
import com.android.wm.shell.splitscreen.SplitStatusBarHider;
import com.android.wm.shell.splitscreen.StageTaskListener;

import com.google.android.msdl.domain.MSDLPlayer;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Records and handles layout of splits. Helps to calculate proper bounds when configuration or
 * divider position changes.
 */
public final class SplitLayout implements DisplayInsetsController.OnInsetsChangedListener {
    private static final String TAG = "SplitLayout";
    /** No parallax effect when the user is dragging the divider */
    public static final int PARALLAX_NONE = 0;
    public static final int PARALLAX_DISMISSING = 1;
    /** Parallax effect (center-aligned) when the user is dragging the divider */
    public static final int PARALLAX_ALIGN_CENTER = 2;
    /**
     * A custom parallax effect for flexible split. When an app is being pushed/pulled offscreen,
     * we use a specific parallax to give the impression that it is stuck to the divider.
     * Otherwise, we fall back to PARALLAX_ALIGN_CENTER behavior.
     */
    public static final int PARALLAX_FLEX = 3;

    public static final int FLING_RESIZE_DURATION = 250;
    private static final int FLING_ENTER_DURATION = 450;
    private static final int FLING_EXIT_DURATION = 450;
    private static final int FLING_OFFSCREEN_DURATION = 500;

    // Here are some (arbitrarily decided) layer definitions used during animations to make sure the
    // layers stay in order. (During transitions, everything is reparented onto a transition root
    // and can be freely relayered.)
    public static final int ANIMATING_DIVIDER_LAYER = 0;
    public static final int ANIMATING_FRONT_APP_VEIL_LAYER = ANIMATING_DIVIDER_LAYER + 20;
    public static final int ANIMATING_FRONT_APP_LAYER = ANIMATING_DIVIDER_LAYER + 10;
    public static final int ANIMATING_BACK_APP_VEIL_LAYER = ANIMATING_DIVIDER_LAYER - 10;
    public static final int ANIMATING_BACK_APP_LAYER = ANIMATING_DIVIDER_LAYER - 20;
    // The divider is on the split root, and is sibling with the stage roots. We want to keep it
    // above the app stages.
    public static final int RESTING_DIVIDER_LAYER = Integer.MAX_VALUE;
    // The touch layer is on a stage root, and is sibling with things like the app activity itself
    // and the app veil. We want it to be above all those.
    public static final int RESTING_TOUCH_LAYER = Integer.MAX_VALUE;
    // The dim layer is also on the stage root, and stays under the touch layer.
    public static final int RESTING_DIM_LAYER = RESTING_TOUCH_LAYER - 1;

    // Animation specs for the swap animation
    private static final int SWAP_ANIMATION_TOTAL_DURATION = 500;
    private static final float SWAP_ANIMATION_SHRINK_DURATION = 83;
    private static final float SWAP_ANIMATION_SHRINK_MARGIN_DP = 14;
    private static final Interpolator SHRINK_INTERPOLATOR =
            new PathInterpolator(0.2f, 0f, 0f, 1f);
    private static final Interpolator GROW_INTERPOLATOR =
            new PathInterpolator(0.45f, 0f, 0.5f, 1f);
    @ShellMainThread
    private final Handler mHandler;
    private final SplitStatusBarHider mStatusBarHider;

    /** Singleton source of truth for the current state of split screen on this device. */
    private final SplitState mSplitState;

    /** A haptics controller that plays haptic effects. */
    private final MSDLPlayer mMSDLPlayer;

    private int mDividerWindowWidth;
    private int mDividerInsets;
    private int mDividerSize;

    private final Rect mTempRect = new Rect();
    private final Rect mTempRect2 = new Rect();
    private final Rect mRootBounds = new Rect();
    private final Rect mDividerBounds = new Rect();
    /**
     * A list of stage bounds, kept in order from top/left to bottom/right. These are the sizes of
     * the app surfaces, not necessarily the same as the size of the rendered content.
     * See {@link #mContentBounds}.
     */
    private final List<Rect> mStageBounds = List.of(new Rect(), new Rect());
    /**
     * A list of app content bounds, kept in order from top/left to bottom/right. These are the
     * sizes of the rendered app contents, not necessarily the same as the size of the drawn app
     * surfaces. See {@link #mStageBounds}.
     */
    private final List<Rect> mContentBounds = List.of(new Rect(), new Rect());
    // The temp bounds outside of display bounds for side stage when split screen inactive to avoid
    // flicker next time active split screen.
    private final Rect mInvisibleBounds = new Rect();
    /**
     * Areas on the screen that the user can touch to shift the layout, bringing offscreen apps
     * onscreen. If n apps are offscreen, there should be n such areas. Empty otherwise.
     */
    private final List<OffscreenTouchZone> mOffscreenTouchZones = new ArrayList<>();
    private final SplitLayoutHandler mSplitLayoutHandler;
    private final SplitWindowManager mSplitWindowManager;
    private final DisplayController mDisplayController;
    private final DisplayImeController mDisplayImeController;
    private final ParentContainerCallbacks mParentContainerCallbacks;
    private final ImePositionProcessor mImePositionProcessor;
    private final ResizingEffectPolicy mSurfaceEffectPolicy;
    private final ShellTaskOrganizer mTaskOrganizer;
    private final InsetsState mInsetsState = new InsetsState();
    private final DesktopState mDesktopState;
    private Insets mPinnedTaskbarInsets = Insets.NONE;

    private Context mContext;
    @VisibleForTesting DividerSnapAlgorithm mDividerSnapAlgorithm;
    private WindowContainerToken mWinToken1;
    private WindowContainerToken mWinToken2;
    private int mDividerPosition;
    private boolean mInitialized = false;
    private boolean mFreezeDividerWindow = false;
    private boolean mIsLargeScreen = false;
    private int mOrientation;
    private int mRotation;
    private int mDensity;
    private int mUiMode;

    private final boolean mDimNonImeSide;
    private final boolean mAllowLeftRightSplitInPortrait;
    private final InteractionJankMonitor mInteractionJankMonitor;
    private boolean mIsLeftRightSplit;
    private ValueAnimator mDividerFlingAnimator;
    private AnimatorSet mSwapAnimator;

    public SplitLayout(String windowName, Context context, Configuration configuration,
            SplitLayoutHandler splitLayoutHandler,
            ParentContainerCallbacks parentContainerCallbacks,
            DisplayController displayController, DisplayImeController displayImeController,
            ShellTaskOrganizer taskOrganizer, int parallaxType, SplitState splitState,
            @ShellMainThread Handler handler, SplitStatusBarHider statusBarHider,
            DesktopState desktopState, MSDLPlayer msdlPlayer) {
        mHandler = handler;
        mStatusBarHider = statusBarHider;
        mContext = context.createConfigurationContext(configuration);
        mOrientation = configuration.orientation;
        mRotation = configuration.windowConfiguration.getRotation();
        mDensity = configuration.densityDpi;
        mIsLargeScreen = configuration.smallestScreenWidthDp >= 600;
        mSplitLayoutHandler = splitLayoutHandler;
        mDisplayController = displayController;
        mDisplayImeController = displayImeController;
        mParentContainerCallbacks = parentContainerCallbacks;
        mSplitWindowManager = new SplitWindowManager(windowName, mContext, configuration,
                parentContainerCallbacks);
        mTaskOrganizer = taskOrganizer;
        mImePositionProcessor = new ImePositionProcessor(mContext.getDisplayId());
        mSurfaceEffectPolicy = new ResizingEffectPolicy(parallaxType, this);
        mSplitState = splitState;
        mDesktopState = desktopState;
        mMSDLPlayer = msdlPlayer;

        final Resources res = mContext.getResources();
        mDimNonImeSide = res.getBoolean(R.bool.config_dimNonImeAttachedSide);
        mAllowLeftRightSplitInPortrait = SplitScreenUtils.allowLeftRightSplitInPortrait(res);
        mIsLeftRightSplit = SplitScreenUtils.isLeftRightSplit(mAllowLeftRightSplitInPortrait,
                configuration);
        statusBarHider.onLeftRightSplitUpdated(mIsLeftRightSplit);
        updateDividerConfig(mContext);

        mRootBounds.set(configuration.windowConfiguration.getBounds());
        updateLayouts();
        mInteractionJankMonitor = InteractionJankMonitor.getInstance();
        resetDividerPosition();
        updateInvisibleRect();
    }

    private void updateDividerConfig(Context context) {
        final Resources resources = context.getResources();
        final Display display = context.getDisplay();
        final int dividerInset = resources.getDimensionPixelSize(
                com.android.internal.R.dimen.docked_stack_divider_insets);
        int radius = 0;
        RoundedCorner corner = display.getRoundedCorner(RoundedCorner.POSITION_TOP_LEFT);
        radius = corner != null ? Math.max(radius, corner.getRadius()) : radius;
        corner = display.getRoundedCorner(RoundedCorner.POSITION_TOP_RIGHT);
        radius = corner != null ? Math.max(radius, corner.getRadius()) : radius;
        corner = display.getRoundedCorner(RoundedCorner.POSITION_BOTTOM_RIGHT);
        radius = corner != null ? Math.max(radius, corner.getRadius()) : radius;
        corner = display.getRoundedCorner(RoundedCorner.POSITION_BOTTOM_LEFT);
        radius = corner != null ? Math.max(radius, corner.getRadius()) : radius;

        mDividerInsets = Math.max(dividerInset, radius);
        mDividerSize = resources.getDimensionPixelSize(R.dimen.split_divider_bar_width);
        mDividerWindowWidth = mDividerSize + 2 * mDividerInsets;
    }

    /** Gets the bounds of the top/left app in screen-based coordinates. */
    public Rect getTopLeftBounds() {
        return mStageBounds.getFirst();
    }

    /** Gets the bounds of the bottom/right app in screen-based coordinates. */
    public Rect getBottomRightBounds() {
        return mStageBounds.getLast();
    }

    /** Gets the bounds of the top/left app in parent-based coordinates. */
    public Rect getTopLeftRefBounds() {
        Rect outBounds = getTopLeftBounds();
        outBounds.offset(-mRootBounds.left, -mRootBounds.top);
        return outBounds;
    }

    /** Gets the bounds of the bottom/right app in parent-based coordinates. */
    public Rect getBottomRightRefBounds() {
        Rect outBounds = getBottomRightBounds();
        outBounds.offset(-mRootBounds.left, -mRootBounds.top);
        return outBounds;
    }

    /** Gets root bounds of the whole split layout */
    public Rect getRootBounds() {
        return new Rect(mRootBounds);
    }

    /** Copies the top/left bounds to the provided Rect (screen-based coordinates). */
    public void copyTopLeftBounds(Rect rect) {
        rect.set(getTopLeftBounds());
    }

    /** Copies the top/left bounds to the provided Rect (parent-based coordinates). */
    public void copyTopLeftRefBounds(Rect rect) {
        copyTopLeftBounds(rect);
        rect.offset(-mRootBounds.left, -mRootBounds.top);
    }

    /** Copies the bottom/right bounds to the provided Rect (screen-based coordinates). */
    public void copyBottomRightBounds(Rect rect) {
        rect.set(getBottomRightBounds());
    }

    /** Copies the bottom/right bounds to the provided Rect (parent-based coordinates). */
    public void copyBottomRightRefBounds(Rect rect) {
        copyBottomRightBounds(rect);
        rect.offset(-mRootBounds.left, -mRootBounds.top);
    }

    /**
     * Gets the content bounds of the top/left app (the bounds of where the app contents would be
     * drawn). Might be larger than the available surface space.
     */
    public Rect getTopLeftContentBounds() {
        return mContentBounds.getFirst();
    }

    /**
     * Gets the content bounds of the bottom/right app (the bounds of where the app contents would
     * be drawn). Might be larger than the available surface space.
     */
    public Rect getBottomRightContentBounds() {
        return mContentBounds.getLast();
    }

    /**
     * Gets the bounds of divider window, in screen-based coordinates. This is not the visible
     * bounds you see on screen, but the actual behind-the-scenes window bounds, which is larger.
     */
    public Rect getDividerBounds() {
        return new Rect(mDividerBounds);
    }

    /**
     * Gets the bounds of divider window, in parent-based coordinates. This is not the visible
     * bounds you see on screen, but the actual behind-the-scenes window bounds, which is larger.
     */
    public Rect getRefDividerBounds() {
        final Rect outBounds = getDividerBounds();
        outBounds.offset(-mRootBounds.left, -mRootBounds.top);
        return outBounds;
    }

    /**
     * Gets the bounds of divider window, in screen-based coordinates. This is not the visible
     * bounds you see on screen, but the actual behind-the-scenes window bounds, which is larger.
     */
    public void getDividerBounds(Rect rect) {
        rect.set(mDividerBounds);
    }

    /**
     * Gets the bounds of divider window, in parent-based coordinates. This is not the visible
     * bounds you see on screen, but the actual behind-the-scenes window bounds, which is larger.
     */
    public void getRefDividerBounds(Rect rect) {
        getDividerBounds(rect);
        rect.offset(-mRootBounds.left, -mRootBounds.top);
    }

    /** Gets bounds size equal to root bounds but outside of screen, used for position side stage
     * when split inactive to avoid flicker when next time active. */
    public void getInvisibleBounds(Rect rect) {
        rect.set(mInvisibleBounds);
    }

    /** Returns leash of the current divider bar. */
    @Nullable
    public SurfaceControl getDividerLeash() {
        return mSplitWindowManager == null ? null : mSplitWindowManager.getSurfaceControl();
    }

    int getDividerPosition() {
        return mDividerPosition;
    }

    /** Returns the haptic player used in this class. */
    public MSDLPlayer getHapticPlayer() {
        return mMSDLPlayer;
    }

    /**
     * Finds the {@link SnapPosition} nearest to the current divider position.
     */
    public int calculateCurrentSnapPosition() {
        return mDividerSnapAlgorithm.calculateNearestSnapPosition(mDividerPosition);
    }

    /** Updates the {@link SplitState} using the current divider position. */
    public void updateStateWithCurrentPosition() {
        mSplitState.set(calculateCurrentSnapPosition());
    }

    /**
     * Returns the divider position as a fraction from 0 to 1.
     */
    public float getDividerPositionAsFraction() {
        if (Flags.enableFlexibleTwoAppSplit()) {
            return Math.min(1f, Math.max(0f, mIsLeftRightSplit
                    ? (getTopLeftBounds().right + getBottomRightBounds().left) / 2f
                    / getDisplayWidth()
                    : (getTopLeftBounds().bottom + getBottomRightBounds().top) / 2f
                            / getDisplayHeight()));
        } else {
            return Math.min(1f, Math.max(0f, mIsLeftRightSplit
                    ? (float) ((getTopLeftBounds().right + getBottomRightBounds().left) / 2f)
                    / getBottomRightBounds().right
                    : (float) ((getTopLeftBounds().bottom + getBottomRightBounds().top) / 2f)
                            / getBottomRightBounds().bottom));
        }
    }

    private void updateInvisibleRect() {
        mInvisibleBounds.set(mRootBounds.left, mRootBounds.top,
                mIsLeftRightSplit ? mRootBounds.right / 2 : mRootBounds.right,
                mIsLeftRightSplit ? mRootBounds.bottom : mRootBounds.bottom / 2);
        mInvisibleBounds.offset(mIsLeftRightSplit ? mRootBounds.right : 0,
                mIsLeftRightSplit ? 0 : mRootBounds.bottom);
    }

    /**
     * (Re)calculates and activates any needed touch zones, so the user can tap them and retrieve
     * offscreen apps.
     */
    public void populateTouchZones() {
        if (!Flags.enableFlexibleTwoAppSplit()) {
            return;
        }

        if (!mOffscreenTouchZones.isEmpty()) {
            removeTouchZones();
        }

        int currentPosition = mSplitState.get();
        // TODO (b/349828130): Can delete this warning after brief soak time.
        if (currentPosition != calculateCurrentSnapPosition()) {
            Log.wtf(TAG, "SplitState is " + mSplitState.get()
                    + ", expected " + calculateCurrentSnapPosition());
        }

        switch (currentPosition) {
            case SNAP_TO_2_10_90:
            case SNAP_TO_3_10_45_45:
                mOffscreenTouchZones.add(new OffscreenTouchZone(true /* isTopLeft */,
                        () -> flingDividerToOtherSide(currentPosition)));
                break;
            case SNAP_TO_2_90_10:
            case SNAP_TO_3_45_45_10:
                mOffscreenTouchZones.add(new OffscreenTouchZone(false /* isTopLeft */,
                        () -> flingDividerToOtherSide(currentPosition)));
                break;
        }

        mOffscreenTouchZones.forEach(mParentContainerCallbacks::inflateOnStageRoot);
    }

    /** Removes all touch zones. */
    public void removeTouchZones() {
        if (!Flags.enableFlexibleTwoAppSplit()) {
            return;
        }

        // TODO (b/349828130): It would be good to reuse a Transaction from StageCoordinator's
        //  mTransactionPool here, but passing it through SplitLayout and specifically
        //  SplitLayout.release() is complicated because that function is purposely called with a
        //  null value sometimes. When that function is refactored, we should also pass the
        //  Transaction in here.
        SurfaceControl.Transaction t = new SurfaceControl.Transaction();
        mOffscreenTouchZones.forEach(touchZone -> touchZone.release(t));
        t.apply();
        mOffscreenTouchZones.clear();
    }

    /** Applies new configuration, returns {@code false} if there's no effect to the layout. */
    public boolean updateConfiguration(Configuration configuration) {
        // Update the split bounds when necessary. Besides root bounds changed, split bounds need to
        // be updated when the rotation changed to cover the case that users rotated the screen 180
        // degrees.
        // Make sure to render the divider bar with proper resources that matching the screen
        // orientation.
        final int rotation = configuration.windowConfiguration.getRotation();
        final Rect rootBounds = configuration.windowConfiguration.getBounds();
        final int orientation = configuration.orientation;
        final int density = configuration.densityDpi;
        final int uiMode = configuration.uiMode;
        final boolean wasLeftRightSplit = mIsLeftRightSplit;

        if (mOrientation == orientation
                && mRotation == rotation
                && mDensity == density
                && mUiMode == uiMode
                && mRootBounds.equals(rootBounds)) {
            return false;
        }

        mContext = mContext.createConfigurationContext(configuration);
        mSplitWindowManager.setConfiguration(configuration);
        mOrientation = orientation;
        mTempRect.set(mRootBounds);
        mRootBounds.set(rootBounds);
        mRotation = rotation;
        mDensity = density;
        mUiMode = uiMode;
        mIsLargeScreen = configuration.smallestScreenWidthDp >= 600;
        mIsLeftRightSplit = SplitScreenUtils.isLeftRightSplit(mAllowLeftRightSplitInPortrait,
                configuration);
        mStatusBarHider.onLeftRightSplitUpdated(mIsLeftRightSplit);
        updateLayouts();
        updateDividerConfig(mContext);
        initDividerPosition(mTempRect, wasLeftRightSplit);
        updateInvisibleRect();

        return true;
    }

    /** Rotate the layout to specific rotation and calculate new bounds. The stable insets value
     *  should be calculated by display layout. */
    public void rotateTo(int newRotation) {
        final int rotationDelta = (newRotation - mRotation + 4) % 4;
        final boolean changeOrient = (rotationDelta % 2) != 0;

        mRotation = newRotation;
        Rect tmpRect = new Rect(mRootBounds);
        if (changeOrient) {
            tmpRect.set(mRootBounds.top, mRootBounds.left, mRootBounds.bottom, mRootBounds.right);
        }

        // We only need new bounds here, other configuration should be update later.
        final boolean wasLeftRightSplit = SplitScreenUtils.isLeftRightSplit(
                mAllowLeftRightSplitInPortrait, mIsLargeScreen,
                mRootBounds.width() >= mRootBounds.height());
        mTempRect.set(mRootBounds);
        mRootBounds.set(tmpRect);
        mIsLeftRightSplit = SplitScreenUtils.isLeftRightSplit(mAllowLeftRightSplitInPortrait,
                mIsLargeScreen, mRootBounds.width() >= mRootBounds.height());
        mStatusBarHider.onLeftRightSplitUpdated(mIsLeftRightSplit);

        updateLayouts();
        initDividerPosition(mTempRect, wasLeftRightSplit);
    }

    /**
     * Updates the divider position to the position in the current orientation and bounds using the
     * snap fraction calculated based on the previous orientation and bounds.
     */
    private void initDividerPosition(Rect oldBounds, boolean wasLeftRightSplit) {
        final float snapRatio = (float) mDividerPosition
                / (float) (wasLeftRightSplit ? oldBounds.width() : oldBounds.height());
        // Estimate position by previous ratio.
        final float length =
                (float) (mIsLeftRightSplit ? mRootBounds.width() : mRootBounds.height());
        final int estimatePosition = (int) (length * snapRatio);
        // Init divider position by estimated position using current bounds snap algorithm.
        mDividerPosition = mDividerSnapAlgorithm.calculateNonDismissingSnapTarget(
                estimatePosition).position;
        updateBounds(mDividerPosition);
    }

    private void updateBounds(int position) {
        updateBounds(position, getTopLeftBounds(), getBottomRightBounds(), mDividerBounds,
                true /* setEffectBounds */);
    }

    /**
     * Updates the bounds of the divider window and both split apps.
     * @param position The left/top edge of the visual divider, where the edge of app A meets the
     *                 divider. Not to be confused with the actual divider surface, which is larger
     *                 and overlaps the apps a bit.
     */
    private void updateBounds(int position, Rect bounds1, Rect bounds2, Rect dividerBounds,
            boolean setEffectBounds) {
        dividerBounds.set(mRootBounds);
        bounds1.set(mRootBounds);
        bounds2.set(mRootBounds);
        if (mIsLeftRightSplit) {
            position += mRootBounds.left;
            dividerBounds.left = position - mDividerInsets;
            dividerBounds.right = dividerBounds.left + mDividerWindowWidth;
            bounds1.right = position;
            bounds2.left = bounds1.right + mDividerSize;

            // For flexible split, expand app offscreen as well
            if (mDividerSnapAlgorithm.areOffscreenRatiosSupported()) {
                int distanceToCenter = position - mDividerSnapAlgorithm.getMiddleTarget().position;
                if (position < mDividerSnapAlgorithm.getMiddleTarget().position) {
                    bounds1.left += distanceToCenter * 2;
                } else {
                    bounds2.right += distanceToCenter * 2;
                }
            }

        } else {
            position += mRootBounds.top;
            dividerBounds.top = position - mDividerInsets;
            dividerBounds.bottom = dividerBounds.top + mDividerWindowWidth;
            bounds1.bottom = position;
            bounds2.top = bounds1.bottom + mDividerSize;

            // For flexible split, expand app offscreen as well
            if (mDividerSnapAlgorithm.areOffscreenRatiosSupported()) {
                int distanceToCenter = position - mDividerSnapAlgorithm.getMiddleTarget().position;
                if (position < mDividerSnapAlgorithm.getMiddleTarget().position) {
                    bounds1.top += distanceToCenter * 2;
                } else {
                    bounds2.bottom += distanceToCenter * 2;
                }
            }
        }
        DockedDividerUtils.sanitizeStackBounds(bounds1, true /** topLeft */);
        DockedDividerUtils.sanitizeStackBounds(bounds2, false /** topLeft */);
        if (setEffectBounds) {
            mSurfaceEffectPolicy.applyDividerPosition(
                    position, mIsLeftRightSplit, mDividerSnapAlgorithm, mSplitState);
        }
    }

    /** Inflates {@link DividerView} on the root surface. */
    public void init() {
        if (mInitialized) return;
        mInitialized = true;
        mSplitWindowManager.init(this, mInsetsState, false /* isRestoring */, mDesktopState);
        populateTouchZones();
        mDisplayImeController.addPositionProcessor(mImePositionProcessor);
    }

    /** Releases the surface holding the current {@link DividerView}. */
    public void release(SurfaceControl.Transaction t) {
        if (!mInitialized) return;
        mInitialized = false;
        mSplitWindowManager.release(t);
        removeTouchZones();
        mDisplayImeController.removePositionProcessor(mImePositionProcessor);
        mImePositionProcessor.reset();
        if (mDividerFlingAnimator != null) {
            mDividerFlingAnimator.cancel();
        }
        resetDividerPosition();
    }

    public void release() {
        release(null /* t */);
    }

    /** Releases and re-inflates {@link DividerView} on the root surface. */
    public void update(SurfaceControl.Transaction t, boolean resetImePosition) {
        if (!mInitialized) {
            init();
            return;
        }
        mSplitWindowManager.release(t);
        if (resetImePosition) {
            mImePositionProcessor.reset();
        }
        mSplitWindowManager.init(this, mInsetsState, true /* isRestoring */, mDesktopState);
        populateTouchZones();
        // Update the surface positions again after recreating the divider in case nothing else
        // triggers it
        mSplitLayoutHandler.onLayoutPositionChanging(SplitLayout.this);
    }

    @Override
    public void insetsChanged(InsetsState insetsState) {
        mInsetsState.set(insetsState);

        if (!mInitialized) {
            return;
        }
        if (mFreezeDividerWindow) {
            // DO NOT change its layout before transition actually run because it might cause
            // flicker.
            return;
        }

        // Check to see if insets changed in such a way that the divider needs to be animated to
        // a new position. (We only do this when switching to pinned taskbar mode and back).
        Insets pinnedTaskbarInsets = calculatePinnedTaskbarInsets(insetsState);
        if (!mPinnedTaskbarInsets.equals(pinnedTaskbarInsets)) {
            mPinnedTaskbarInsets = pinnedTaskbarInsets;
            // Refresh the DividerSnapAlgorithm.
            updateLayouts();
            // If the divider is no longer placed on a snap point, animate it to the nearest one
            DividerSnapAlgorithm.SnapTarget snapTarget =
                    findSnapTarget(mDividerPosition, 0, false /* hardDismiss */);
            if (snapTarget.position != mDividerPosition) {
                snapToTarget(mDividerPosition, snapTarget,
                        InsetsController.ANIMATION_DURATION_RESIZE,
                        InsetsController.RESIZE_INTERPOLATOR);
            }
        }

        mSplitWindowManager.onInsetsChanged(insetsState);
    }

    /**
     * Calculates the insets that might trigger a divider algorithm recalculation.
     */
    private Insets calculatePinnedTaskbarInsets(InsetsState insetsState) {
        for (int i = insetsState.sourceSize() - 1; i >= 0; i--) {
            final InsetsSource source = insetsState.sourceAt(i);
            // If Taskbar is pinned...
            if (source.getType() == WindowInsets.Type.navigationBars()
                    && source.hasFlags(InsetsSource.FLAG_INSETS_ROUNDED_CORNER)) {
                // Return Insets representing the pinned taskbar state.
                return source.calculateVisibleInsets(mRootBounds, mRootBounds);
            }
        }

        // Else, divider can calculate based on the full display.
        return Insets.NONE;
    }

    @Override
    public void insetsControlChanged(InsetsState insetsState,
            InsetsSourceControl[] activeControls) {
        if (!mInsetsState.equals(insetsState)) {
            insetsChanged(insetsState);
        }
    }

    public void setFreezeDividerWindow(boolean freezeDividerWindow) {
        mFreezeDividerWindow = freezeDividerWindow;
    }

    /** Update current layout as divider put on start or end position. */
    public void setDividerAtBorder(boolean start) {
        final int pos = start ? mDividerSnapAlgorithm.getDismissStartTarget().position
                : mDividerSnapAlgorithm.getDismissEndTarget().position;
        setDividerPosition(pos, false /* applyLayoutChange */);
    }

    /**
     * Updates bounds with the passing position. Usually used to update recording bounds while
     * performing animation or dragging divider bar to resize the splits.
     */
    void updateDividerBounds(int position, boolean shouldUseParallaxEffect) {
        updateBounds(position);
        mSplitLayoutHandler.onLayoutSizeChanging(this,
                mSurfaceEffectPolicy.mRetreatingSideParallax.x,
                mSurfaceEffectPolicy.mRetreatingSideParallax.y, shouldUseParallaxEffect);
    }

    void setDividerPosition(int position, boolean applyLayoutChange) {
        mDividerPosition = position;
        updateBounds(mDividerPosition);
        if (applyLayoutChange) {
            mSplitLayoutHandler.onLayoutSizeChanged(this);
        }
    }

    /**
     * Updates divider position and split bounds base on the ratio within root bounds. Falls back
     * to middle position if the provided SnapTarget is not supported.
     */
    public void setDivideRatio(@PersistentSnapPosition int snapPosition) {
        final SnapTarget snapTarget = mDividerSnapAlgorithm.findSnapTarget(
                snapPosition);

        setDividerPosition(snapTarget != null
                ? snapTarget.position
                : mDividerSnapAlgorithm.getMiddleTarget().position,
                false /* applyLayoutChange */);
    }

    /** Resets divider position. */
    public void resetDividerPosition() {
        mDividerPosition = mDividerSnapAlgorithm.getMiddleTarget().position;
        updateBounds(mDividerPosition);
        mWinToken1 = null;
        mWinToken2 = null;
        getTopLeftContentBounds().setEmpty();
        getBottomRightContentBounds().setEmpty();
    }

    /**
     * Set divider should interactive to user or not.
     *
     * @param interactive divider interactive.
     * @param hideHandle divider handle hidden or not, only work when interactive is false.
     * @param from caller from where.
     */
    public void setDividerInteractive(boolean interactive, boolean hideHandle, String from) {
        mSplitWindowManager.setInteractive(interactive, hideHandle, from);
    }

    /**
     * Sets new divider position and updates bounds correspondingly. Notifies listener if the new
     * target indicates dismissing split.
     */
    public void snapToTarget(int currentPosition, SnapTarget snapTarget, int duration,
            Interpolator interpolator) {
        switch (snapTarget.snapPosition) {
            case SNAP_TO_START_AND_DISMISS:
                flingDividerPosition(currentPosition, snapTarget.position, duration, interpolator,
                        () -> mSplitLayoutHandler.onSnappedToDismiss(false /* bottomOrRight */,
                                EXIT_REASON_DRAG_DIVIDER));
                break;
            case SNAP_TO_END_AND_DISMISS:
                flingDividerPosition(currentPosition, snapTarget.position, duration, interpolator,
                        () -> mSplitLayoutHandler.onSnappedToDismiss(true /* bottomOrRight */,
                                EXIT_REASON_DRAG_DIVIDER));
                break;
            default:
                flingDividerPosition(currentPosition, snapTarget.position, duration, interpolator,
                        () -> {
                            setDividerPosition(snapTarget.position, true /* applyLayoutChange */);
                            mSplitState.set(snapTarget.snapPosition);
                        });
                break;
        }
    }

    /**
     * Same as {@link #snapToTarget(int, SnapTarget, int, Interpolator)}, with default animation
     * duration and interpolator.
     */
    public void snapToTarget(int currentPosition, SnapTarget snapTarget) {
        snapToTarget(currentPosition, snapTarget, FLING_RESIZE_DURATION,
                FAST_OUT_SLOW_IN);
    }

    void onStartDragging() {
        mInteractionJankMonitor.begin(getDividerLeash(), mContext, mHandler,
                CUJ_SPLIT_SCREEN_RESIZE);
    }

    void onDraggingCancelled() {
        mInteractionJankMonitor.cancel(CUJ_SPLIT_SCREEN_RESIZE);
    }

    void onDoubleTappedDivider() {
        if (isCurrentlySwapping()) {
            return;
        }

        mSplitLayoutHandler.onDoubleTappedDivider();
    }

    /**
     * Returns {@link SnapTarget} which matches passing position and velocity.
     * If hardDismiss is set to {@code true}, it will be harder to reach dismiss target.
     */
    public SnapTarget findSnapTarget(int position, float velocity,
            boolean hardDismiss) {
        return mDividerSnapAlgorithm.calculateSnapTarget(position, velocity, hardDismiss);
    }

    /**
     * (Re)calculates the split screen logic for this particular display/orientation. Refreshes the
     * DividerSnapAlgorithm, which controls divider snap points, and populates a map in SplitState
     * with bounds for all valid split layouts.
     */
    private void updateLayouts() {
        // Update SplitState map

        if (Flags.enableFlexibleTwoAppSplit()) {
            mSplitState.populateLayouts(
                    mRootBounds, mDividerSize, mIsLeftRightSplit, mPinnedTaskbarInsets.toRect());
        }

        // Get new DividerSnapAlgorithm

        final Rect insets = getDisplayStableInsets(mContext);

        // Make split axis insets value same as the larger one to avoid bounds1 and bounds2
        // have difference for avoiding size-compat mode when switching unresizable apps in
        // landscape while they are letterboxed.
        if (!mIsLeftRightSplit) {
            final int largerInsets = Math.max(insets.top, insets.bottom);
            insets.set(insets.left, largerInsets, insets.right, largerInsets);
        }

        mDividerSnapAlgorithm = new DividerSnapAlgorithm(
                mContext.getResources(),
                mRootBounds.width(),
                mRootBounds.height(),
                mDividerSize,
                mIsLeftRightSplit,
                insets,
                mPinnedTaskbarInsets.toRect(),
                mIsLeftRightSplit ? DOCKED_LEFT : DOCKED_TOP /* dockSide */);
    }

    /** Fling divider from current position to end or start position then exit */
    public void flingDividerToDismiss(boolean toEnd, int reason) {
        final int target = toEnd ? mDividerSnapAlgorithm.getDismissEndTarget().position
                : mDividerSnapAlgorithm.getDismissStartTarget().position;
        flingDividerPosition(getDividerPosition(), target, FLING_EXIT_DURATION, FAST_OUT_SLOW_IN,
                () -> mSplitLayoutHandler.onSnappedToDismiss(toEnd, reason));
    }

    /** Fling divider from current position to center position. */
    public void flingDividerToCenter(@Nullable Runnable finishCallback) {
        final SnapTarget target = mDividerSnapAlgorithm.getMiddleTarget();
        final int pos = target.position;
        flingDividerPosition(getDividerPosition(), pos, FLING_ENTER_DURATION, FAST_OUT_SLOW_IN,
                () -> {
                    setDividerPosition(pos, true /* applyLayoutChange */);
                    mSplitState.set(target.snapPosition);
                    if (finishCallback != null) {
                        finishCallback.run();
                    }
                });
    }

    /**
     * Moves the divider to the other side of the screen. Does nothing if the divider is in the
     * center.
     * TODO (b/349828130): Currently only supports the two-app case. For n-apps,
     *  DividerSnapAlgorithm will need to be refactored, and this function will change as well.
     */
    public void flingDividerToOtherSide(@PersistentSnapPosition int currentSnapPosition) {
        // If a fling animation is already running, just return.
        if (mDividerFlingAnimator != null) return;

        mSplitState.set(ANIMATING_OFFSCREEN_TAP);
        switch (currentSnapPosition) {
            case SNAP_TO_2_10_90 ->
                    snapToTarget(mDividerPosition, mDividerSnapAlgorithm.getLastSplitTarget(),
                            FLING_OFFSCREEN_DURATION, EMPHASIZED);
            case SNAP_TO_2_90_10 ->
                    snapToTarget(mDividerPosition, mDividerSnapAlgorithm.getFirstSplitTarget(),
                            FLING_OFFSCREEN_DURATION, EMPHASIZED);
        }
    }

    @VisibleForTesting
    void flingDividerPosition(int from, int to, int duration, Interpolator interpolator,
            @Nullable Runnable flingFinishedCallback) {
        if (from == to) {
            if (flingFinishedCallback != null) {
                flingFinishedCallback.run();
            }
            mInteractionJankMonitor.end(
                    CUJ_SPLIT_SCREEN_RESIZE);
            return;
        }

        if (mDividerFlingAnimator != null) {
            mDividerFlingAnimator.cancel();
        }

        mDividerFlingAnimator = ValueAnimator
                .ofInt(from, to)
                .setDuration(duration);
        mDividerFlingAnimator.setInterpolator(interpolator);

        // If the divider is being physically controlled by the user, we use a cool parallax effect
        // on the task windows. So if this "snap" animation is an extension of a user-controlled
        // movement, we pass in true here to continue the parallax effect smoothly.
        boolean isBeingMovedByUser = mSplitWindowManager.getDividerView() != null
                && mSplitWindowManager.getDividerView().isMoving();
        boolean isAnimatingOffscreenTap = mSplitState.get() == ANIMATING_OFFSCREEN_TAP;
        boolean needsParallax = isBeingMovedByUser || isAnimatingOffscreenTap;

        mDividerFlingAnimator.addUpdateListener(
                animation -> updateDividerBounds(
                        (int) animation.getAnimatedValue(),
                        needsParallax /* shouldUseParallaxEffect */
                )
        );
        mDividerFlingAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                super.onAnimationStart(animation);
                mParentContainerCallbacks.onSplitLayoutAnimating(true /*animating*/);
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                if (flingFinishedCallback != null) {
                    flingFinishedCallback.run();
                }
                mInteractionJankMonitor.end(
                        CUJ_SPLIT_SCREEN_RESIZE);
                mDividerFlingAnimator = null;
                mParentContainerCallbacks.onSplitLayoutAnimating(false /*animating*/);
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                mDividerFlingAnimator = null;
                mParentContainerCallbacks.onSplitLayoutAnimating(false /*animating*/);
            }
        });
        mDividerFlingAnimator.start();
    }

    /** Switch both surface position with animation. */
    public void playSwapAnimation(SurfaceControl.Transaction t, StageTaskListener topLeftStage,
            StageTaskListener bottomRightStage, Consumer<Rect> finishCallback) {
        final Rect insets = getDisplayStableInsets(mContext);
        // If we have insets in the direction of the swap, the animation won't look correct because
        // window contents will shift and redraw again at the end. So we show a veil to hide that.
        insets.set(mIsLeftRightSplit ? insets.left : 0, mIsLeftRightSplit ? 0 : insets.top,
                mIsLeftRightSplit ? insets.right : 0, mIsLeftRightSplit ? 0 : insets.bottom);
        final boolean shouldVeil =
                insets.left != 0 || insets.top != 0 || insets.right != 0 || insets.bottom != 0;

        // Find the "left/top"-most position of the app surface -- usually 0, but sometimes negative
        // if the left/top app is offscreen.
        int leftTop = 0;
        if (Flags.enableFlexibleTwoAppSplit()) {
            leftTop = mIsLeftRightSplit ? getTopLeftBounds().left : getTopLeftBounds().top;
        }

        final int dividerPos = mDividerSnapAlgorithm.calculateNonDismissingSnapTarget(
                leftTop + (mIsLeftRightSplit
                        ? getBottomRightBounds().width() : getBottomRightBounds().height())
        ).position;
        final Rect endBounds1 = new Rect();
        final Rect endBounds2 = new Rect();
        final Rect endDividerBounds = new Rect();
        // Compute destination bounds.
        updateBounds(dividerPos, endBounds2, endBounds1, endDividerBounds,
                false /* setEffectBounds */);
        // Offset to real position under root container.
        endBounds1.offset(-mRootBounds.left, -mRootBounds.top);
        endBounds2.offset(-mRootBounds.left, -mRootBounds.top);
        endDividerBounds.offset(-mRootBounds.left, -mRootBounds.top);

        ValueAnimator animator1 = moveSurface(t, topLeftStage, getTopLeftRefBounds(), endBounds1,
                -insets.left, -insets.top, true /* roundCorners */, true /* isGoingBehind */,
                shouldVeil);
        ValueAnimator animator2 = moveSurface(t, bottomRightStage, getBottomRightRefBounds(),
                endBounds2, insets.left, insets.top, true /* roundCorners */,
                false /* isGoingBehind */, shouldVeil);
        ValueAnimator animator3 = moveSurface(t, null /* stage */, getRefDividerBounds(),
                endDividerBounds, 0 /* offsetX */, 0 /* offsetY */, false /* roundCorners */,
                false /* isGoingBehind */, false /* addVeil */);

        mSwapAnimator = new AnimatorSet();
        mSwapAnimator.playTogether(animator1, animator2, animator3);
        mSwapAnimator.setDuration(SWAP_ANIMATION_TOTAL_DURATION);
        mSwapAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                mInteractionJankMonitor.begin(getDividerLeash(),
                        mContext, mHandler, CUJ_SPLIT_SCREEN_DOUBLE_TAP_DIVIDER);
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                mDividerPosition = dividerPos;
                updateBounds(mDividerPosition);
                finishCallback.accept(insets);
                mInteractionJankMonitor.end(CUJ_SPLIT_SCREEN_DOUBLE_TAP_DIVIDER);
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                mInteractionJankMonitor.cancel(CUJ_SPLIT_SCREEN_DOUBLE_TAP_DIVIDER);
            }
        });
        mSwapAnimator.start();
    }

    /** Returns true if a swap animation is currently playing. */
    public boolean isCurrentlySwapping() {
        return mSwapAnimator != null && mSwapAnimator.isRunning();
    }

    /**
     * Animates a task leash across the screen. Currently used only for the swap animation.
     *
     * @param stage The stage holding the task being animated. If null, it is the divider.
     * @param roundCorners Whether we should round the corners of the task while animating.
     * @param isGoingBehind Whether we should a shrink-and-grow effect to the task while it is
     *                           moving. (Simulates moving behind the divider.)
     */
    private ValueAnimator moveSurface(SurfaceControl.Transaction t, StageTaskListener stage,
            Rect start, Rect end, float offsetX, float offsetY, boolean roundCorners,
            boolean isGoingBehind, boolean addVeil) {
        final boolean isApp = stage != null; // check if this is an app or a divider
        final SurfaceControl leash = isApp ? stage.getRootLeash() : getDividerLeash();
        final ActivityManager.RunningTaskInfo taskInfo = isApp ? stage.getRunningTaskInfo() : null;
        final SplitDecorManager decorManager = isApp ? stage.getDecorManager() : null;
        final SurfaceControl dimLayer =  isApp ? stage.getDimLayer() : null;
        boolean goingOffscreen = Flags.enableFlexibleTwoAppSplit()
                ? !mSplitState.isOffscreen(start) && mSplitState.isOffscreen(end) : false;
        boolean comingOnscreen = Flags.enableFlexibleTwoAppSplit()
                ? mSplitState.isOffscreen(start) && !mSplitState.isOffscreen(end) : false;

        Rect tempStart = new Rect(start);
        Rect tempEnd = new Rect(end);
        final float diffX = tempEnd.left - tempStart.left;
        final float diffY = tempEnd.top - tempStart.top;
        final float diffWidth = tempEnd.width() - tempStart.width();
        final float diffHeight = tempEnd.height() - tempStart.height();

        // Get display measurements (for possible shrink animation).
        final RoundedCorner roundedCorner = mSplitWindowManager.getDividerView().getDisplay()
                .getRoundedCorner(0 /* position */);
        float cornerRadius = roundedCorner == null ? 0 : roundedCorner.getRadius();
        float shrinkMarginPx = PipUtils.dpToPx(
                SWAP_ANIMATION_SHRINK_MARGIN_DP, mContext.getResources().getDisplayMetrics());
        float shrinkAmountPx = shrinkMarginPx * 2;

        // Timing calculations
        float shrinkPortion = SWAP_ANIMATION_SHRINK_DURATION / SWAP_ANIMATION_TOTAL_DURATION;
        float growPortion = 1 - shrinkPortion;

        ValueAnimator animator = ValueAnimator.ofFloat(0, 1);
        // Set the base animation to proceed linearly. Each component of the animation (movement,
        // shrinking, growing) overrides it with a different interpolator later.
        animator.setInterpolator(LINEAR);
        animator.addUpdateListener(animation -> {
            if (leash == null) return;
            if (roundCorners) {
                // Add rounded corners to the task leash while it is animating.
                t.setCornerRadius(leash, cornerRadius);
            }

            final float progress = (float) animation.getAnimatedValue();
            final float moveProgress = EMPHASIZED.getInterpolation(progress);
            float instantaneousX = tempStart.left + moveProgress * diffX;
            float instantaneousY = tempStart.top + moveProgress * diffY;
            int width = (int) (tempStart.width() + moveProgress * diffWidth);
            int height = (int) (tempStart.height() + moveProgress * diffHeight);

            if (isGoingBehind) {
                float shrinkDiffX; // the position adjustments needed for this frame
                float shrinkDiffY;
                float shrinkScaleX; // the scale adjustments needed for this frame
                float shrinkScaleY;

                // Find the max amount we will be shrinking this leash, as a proportion (e.g. 0.1f).
                float maxShrinkX = shrinkAmountPx / height;
                float maxShrinkY = shrinkAmountPx / width;

                // Find if we are in the shrinking part of the animation, or the growing part.
                boolean shrinking = progress <= shrinkPortion;

                if (shrinking) {
                    // Find how far into the shrink portion we are (e.g. 0.5f).
                    float shrinkProgress = progress / shrinkPortion;
                    // Find how much we should have progressed in shrinking the leash (e.g. 0.8f).
                    float interpolatedShrinkProgress =
                            SHRINK_INTERPOLATOR.getInterpolation(shrinkProgress);
                    // Find how much width proportion we should be taking off (e.g. 0.1f)
                    float widthProportionLost =  maxShrinkX * interpolatedShrinkProgress;
                    shrinkScaleX = 1 - widthProportionLost;
                    // Find how much height proportion we should be taking off (e.g. 0.1f)
                    float heightProportionLost =  maxShrinkY * interpolatedShrinkProgress;
                    shrinkScaleY = 1 - heightProportionLost;
                    // Add a small amount to the leash's position to keep the task centered.
                    shrinkDiffX = (width * widthProportionLost) / 2;
                    shrinkDiffY = (height * heightProportionLost) / 2;
                } else {
                    // Find how far into the grow portion we are (e.g. 0.5f).
                    float growProgress = (progress - shrinkPortion) / growPortion;
                    // Find how much we should have progressed in growing the leash (e.g. 0.8f).
                    float interpolatedGrowProgress =
                            GROW_INTERPOLATOR.getInterpolation(growProgress);
                    // Find how much width proportion we should be taking off (e.g. 0.1f)
                    float widthProportionLost =  maxShrinkX * (1 - interpolatedGrowProgress);
                    shrinkScaleX = 1 - widthProportionLost;
                    // Find how much height proportion we should be taking off (e.g. 0.1f)
                    float heightProportionLost =  maxShrinkY * (1 - interpolatedGrowProgress);
                    shrinkScaleY = 1 - heightProportionLost;
                    // Add a small amount to the leash's position to keep the task centered.
                    shrinkDiffX = (width * widthProportionLost) / 2;
                    shrinkDiffY = (height * heightProportionLost) / 2;
                }

                instantaneousX += shrinkDiffX;
                instantaneousY += shrinkDiffY;
                width *= shrinkScaleX;
                height *= shrinkScaleY;
                // Set scale on the leash's contents.
                t.setScale(leash, shrinkScaleX, shrinkScaleY);
            }

            // Set layers
            if (taskInfo != null) {
                t.setLayer(leash, isGoingBehind
                        ? ANIMATING_BACK_APP_LAYER
                        : ANIMATING_FRONT_APP_LAYER);
            } else {
                t.setLayer(leash, ANIMATING_DIVIDER_LAYER);
            }

            if (offsetX == 0 && offsetY == 0) {
                t.setPosition(leash, instantaneousX, instantaneousY);
                mTempRect.set((int) instantaneousX, (int) instantaneousY,
                        (int) (instantaneousX + width), (int) (instantaneousY + height));
                t.setWindowCrop(leash, width, height);
                if (addVeil) {
                    decorManager.drawNextVeilFrameForSwapAnimation(
                            taskInfo, mTempRect, t, isGoingBehind, leash, 0, 0);
                }
            } else {
                final int diffOffsetX = (int) (moveProgress * offsetX);
                final int diffOffsetY = (int) (moveProgress * offsetY);
                t.setPosition(leash, instantaneousX + diffOffsetX, instantaneousY + diffOffsetY);
                mTempRect.set(0, 0, width, height);
                mTempRect.offsetTo(-diffOffsetX, -diffOffsetY);
                t.setCrop(leash, mTempRect);
                if (addVeil) {
                    decorManager.drawNextVeilFrameForSwapAnimation(
                            taskInfo, mTempRect, t, isGoingBehind, leash, diffOffsetX, diffOffsetY);
                }
            }

            // App surfaces are dimmed when offscreen. So if the app is moving from onscreen to
            // offscreen or vice versa, we set the dim layer's alpha on every frame for a smooth
            // transition.
            if (Flags.enableFlexibleTwoAppSplit()
                    && mSplitState.currentStateSupportsOffscreenApps()
                    && dimLayer != null) {
                float instantaneousAlpha = 0f;
                if (goingOffscreen) {
                    instantaneousAlpha = moveProgress * ResizingEffectPolicy.DEFAULT_OFFSCREEN_DIM;
                }
                if (comingOnscreen) {
                    instantaneousAlpha =
                            (1f - moveProgress) * ResizingEffectPolicy.DEFAULT_OFFSCREEN_DIM;
                }
                t.setAlpha(dimLayer, instantaneousAlpha);
                t.setVisibility(dimLayer, instantaneousAlpha > 0.001f);
            }

            t.apply();
        });
        return animator;
    }

    private Rect getDisplayStableInsets(Context context) {
        final DisplayLayout displayLayout =
                mDisplayController.getDisplayLayout(context.getDisplayId());
        return displayLayout != null
                ? displayLayout.stableInsets()
                : context.getSystemService(WindowManager.class)
                        .getMaximumWindowMetrics()
                        .getWindowInsets()
                        .getInsetsIgnoringVisibility(WindowInsets.Type.systemBars()
                                | WindowInsets.Type.displayCutout())
                        .toRect();
    }

    /**
     * @return {@code true} if we should create a left-right split, {@code false} if we should
     * create a top-bottom split.
     */
    public boolean isLeftRightSplit() {
        return mIsLeftRightSplit;
    }

    /** Apply recorded surface layout to the {@link SurfaceControl.Transaction}. */
    public void applySurfaceChanges(SurfaceControl.Transaction t, SurfaceControl leash1,
            SurfaceControl leash2, SurfaceControl dimLayer1, SurfaceControl dimLayer2,
            boolean applyResizingOffset) {
        final SurfaceControl dividerLeash = getDividerLeash();
        if (dividerLeash != null) {
            getRefDividerBounds(mTempRect);
            t.setPosition(dividerLeash, mTempRect.left, mTempRect.top);
            // Resets layer of divider bar to make sure it is always on top.
            t.setLayer(dividerLeash, RESTING_DIVIDER_LAYER);
        }
        if (dimLayer1 != null) {
            t.setLayer(dimLayer1, RESTING_DIM_LAYER);
        }
        if (dimLayer2 != null) {
            t.setLayer(dimLayer2, RESTING_DIM_LAYER);
        }
        copyTopLeftRefBounds(mTempRect);
        t.setPosition(leash1, mTempRect.left, mTempRect.top)
                .setWindowCrop(leash1, mTempRect.width(), mTempRect.height());
        copyBottomRightRefBounds(mTempRect);
        t.setPosition(leash2, mTempRect.left, mTempRect.top)
                .setWindowCrop(leash2, mTempRect.width(), mTempRect.height());

        if (mImePositionProcessor.adjustSurfaceLayoutForIme(
                t, dividerLeash, leash1, leash2, dimLayer1, dimLayer2)) {
            return;
        }

        mSurfaceEffectPolicy.adjustDimSurface(t, dimLayer1, dimLayer2);
        if (applyResizingOffset) {
            mSurfaceEffectPolicy.adjustRootSurface(t, leash1, leash2);
        }
    }

    /** Apply recorded task layout to the {@link WindowContainerTransaction}.
     *
     * @return true if stage bounds actually update.
     */
    public boolean applyTaskChanges(WindowContainerTransaction wct,
            ActivityManager.RunningTaskInfo task1, ActivityManager.RunningTaskInfo task2) {
        boolean boundsChanged = false;
        if (!getTopLeftBounds().equals(getTopLeftContentBounds())
                || !task1.token.equals(mWinToken1)) {
            setTaskBounds(wct, task1, getTopLeftBounds());
            getTopLeftContentBounds().set(getTopLeftBounds());
            mWinToken1 = task1.token;
            boundsChanged = true;
        }
        if (!getBottomRightBounds().equals(getBottomRightContentBounds())
                || !task2.token.equals(mWinToken2)) {
            setTaskBounds(wct, task2, getBottomRightBounds());
            getBottomRightContentBounds().set(getBottomRightBounds());
            mWinToken2 = task2.token;
            boundsChanged = true;
        }
        return boundsChanged;
    }

    /** Set bounds to the {@link WindowContainerTransaction} for single task. */
    public void setTaskBounds(WindowContainerTransaction wct,
            ActivityManager.RunningTaskInfo task, Rect bounds) {
        wct.setBounds(task.token, bounds);
        wct.setSmallestScreenWidthDp(task.token, getSmallestWidthDp(bounds));
        wct.setScreenSizeDp(task.token, task.configuration.screenWidthDp,
                task.configuration.screenHeightDp);
    }

    private int getSmallestWidthDp(Rect bounds) {
        mTempRect.set(bounds);
        mTempRect.inset(getDisplayStableInsets(mContext));
        final int minWidth = Math.min(mTempRect.width(), mTempRect.height());
        final float density = mContext.getResources().getDisplayMetrics().density;
        return (int) (minWidth / density);
    }

    public int getDisplayWidth() {
        return mRootBounds.width();
    }

    public int getDisplayHeight() {
        return mRootBounds.height();
    }

    /**
     * Shift configuration bounds to prevent client apps get configuration changed or relaunch. And
     * restore shifted configuration bounds if it's no longer shifted.
     */
    public void applyLayoutOffsetTarget(WindowContainerTransaction wct, int offsetX, int offsetY,
            ActivityManager.RunningTaskInfo taskInfo1, ActivityManager.RunningTaskInfo taskInfo2) {
        if (offsetX == 0 && offsetY == 0) {
            wct.setBounds(taskInfo1.token, getTopLeftBounds());
            wct.setScreenSizeDp(taskInfo1.token,
                    SCREEN_WIDTH_DP_UNDEFINED, SCREEN_HEIGHT_DP_UNDEFINED);

            wct.setBounds(taskInfo2.token, getBottomRightBounds());
            wct.setScreenSizeDp(taskInfo2.token,
                    SCREEN_WIDTH_DP_UNDEFINED, SCREEN_HEIGHT_DP_UNDEFINED);
        } else {
            copyTopLeftBounds(mTempRect);
            mTempRect.offset(offsetX, offsetY);
            wct.setBounds(taskInfo1.token, mTempRect);
            wct.setScreenSizeDp(taskInfo1.token,
                    taskInfo1.configuration.screenWidthDp,
                    taskInfo1.configuration.screenHeightDp);

            copyBottomRightBounds(mTempRect);
            mTempRect.offset(offsetX, offsetY);
            wct.setBounds(taskInfo2.token, mTempRect);
            wct.setScreenSizeDp(taskInfo2.token,
                    taskInfo2.configuration.screenWidthDp,
                    taskInfo2.configuration.screenHeightDp);
        }
    }

    /** Dumps the current split bounds recorded in this layout. */
    public void dump(@NonNull PrintWriter pw, String prefix) {
        final String innerPrefix = prefix + "\t";
        pw.println(prefix + TAG + ":");
        pw.println(innerPrefix + "mAllowLeftRightSplitInPortrait=" + mAllowLeftRightSplitInPortrait);
        pw.println(innerPrefix + "mIsLeftRightSplit=" + mIsLeftRightSplit);
        pw.println(innerPrefix + "mFreezeDividerWindow=" + mFreezeDividerWindow);
        pw.println(innerPrefix + "mDimNonImeSide=" + mDimNonImeSide);
        pw.println(innerPrefix + "mDividerPosition=" + mDividerPosition);
        pw.println(innerPrefix + "bounds1=" + getTopLeftBounds().toShortString());
        pw.println(innerPrefix + "dividerBounds=" + mDividerBounds.toShortString());
        pw.println(innerPrefix + "bounds2=" + getBottomRightBounds().toShortString());
    }

    /** Handles layout change event. */
    public interface SplitLayoutHandler {

        /** Calls when dismissing split. */
        void onSnappedToDismiss(boolean snappedToEnd, int reason);

        /**
         * Calls when resizing the split bounds.
         *
         * @see #applySurfaceChanges(SurfaceControl.Transaction, SurfaceControl, SurfaceControl,
         * SurfaceControl, SurfaceControl, boolean)
         */
        void onLayoutSizeChanging(SplitLayout layout, int offsetX, int offsetY,
                boolean shouldUseParallaxEffect);

        /**
         * Calls when finish resizing the split bounds.
         *
         * @see #applyTaskChanges(WindowContainerTransaction, ActivityManager.RunningTaskInfo,
         * ActivityManager.RunningTaskInfo)
         * @see #applySurfaceChanges(SurfaceControl.Transaction, SurfaceControl, SurfaceControl,
         * SurfaceControl, SurfaceControl, boolean)
         */
        void onLayoutSizeChanged(SplitLayout layout);

        /**
         * Calls when re-positioning the split bounds. Like moving split bounds while showing IME
         * panel.
         *
         * @see #applySurfaceChanges(SurfaceControl.Transaction, SurfaceControl, SurfaceControl,
         * SurfaceControl, SurfaceControl, boolean)
         */
        void onLayoutPositionChanging(SplitLayout layout);

        /**
         * Notifies the target offset for shifting layout. So layout handler can shift configuration
         * bounds correspondingly to make sure client apps won't get configuration changed or
         * relaunched. If the layout is no longer shifted, layout handler should restore shifted
         * configuration bounds.
         *
         * @see #applyLayoutOffsetTarget(WindowContainerTransaction, int, int,
         * ActivityManager.RunningTaskInfo, ActivityManager.RunningTaskInfo)
         */
        void setLayoutOffsetTarget(int offsetX, int offsetY, SplitLayout layout);

        /** Calls when user double tapped on the divider bar. */
        default void onDoubleTappedDivider() {
        }

        /**
         * Sets the excludedInsetsTypes for the IME in the root WindowContainer.
         */
        void setExcludeImeInsets(boolean exclude);

        /** Returns split position of the token. */
        @SplitPosition
        int getSplitItemPosition(WindowContainerToken token);
    }

    /** Records IME top offset changes and updates SplitLayout correspondingly. */
    private class ImePositionProcessor implements DisplayImeController.ImePositionProcessor {
        /**
         * Maximum size of an adjusted split bounds relative to original stack bounds. Used to
         * restrict IME adjustment so that a min portion of top split remains visible.
         */
        private static final float ADJUSTED_SPLIT_FRACTION_MAX = 0.7f;
        private static final float ADJUSTED_NONFOCUS_DIM = 0.3f;

        private final int mDisplayId;

        private boolean mHasImeFocus;
        private boolean mImeShown;
        private int mYOffsetForIme;
        private float mDimValue1;
        private float mDimValue2;

        private int mStartImeTop;
        private int mEndImeTop;

        private int mTargetYOffset;
        private int mLastYOffset;
        private float mTargetDim1;
        private float mTargetDim2;
        private float mLastDim1;
        private float mLastDim2;

        private ImePositionProcessor(int displayId) {
            mDisplayId = displayId;
        }

        @Override
        public void onImeRequested(int displayId, boolean isRequested) {
            if (displayId != mDisplayId) return;
            ProtoLog.v(ShellProtoLogGroup.WM_SHELL_SPLIT_SCREEN, "IME was set to requested=%s",
                    isRequested);
            mSplitLayoutHandler.setExcludeImeInsets(true);
        }

        @Override
        public int onImeStartPositioning(int displayId, int hiddenTop, int shownTop,
                boolean showing, boolean isFloating, SurfaceControl.Transaction t) {
            if (displayId != mDisplayId || !mInitialized) {
                return 0;
            }

            final int imeLayeringTargetPosition = getImeLayeringTargetPosition();
            mHasImeFocus = imeLayeringTargetPosition != SPLIT_POSITION_UNDEFINED;
            if (!mHasImeFocus) {
                if (showing) {
                    return 0;
                }
            }

            mStartImeTop = showing ? hiddenTop : shownTop;
            mEndImeTop = showing ? shownTop : hiddenTop;
            mImeShown = showing;

            // Update target dim values
            mLastDim1 = mDimValue1;
            mTargetDim1 = imeLayeringTargetPosition == SPLIT_POSITION_BOTTOM_OR_RIGHT && mImeShown
                    && mDimNonImeSide ? ADJUSTED_NONFOCUS_DIM : 0.0f;
            mLastDim2 = mDimValue2;
            mTargetDim2 = imeLayeringTargetPosition == SPLIT_POSITION_TOP_OR_LEFT && mImeShown
                    && mDimNonImeSide ? ADJUSTED_NONFOCUS_DIM : 0.0f;

            // Calculate target bounds offset for IME
            mLastYOffset = mYOffsetForIme;
            final boolean needOffset = imeLayeringTargetPosition == SPLIT_POSITION_BOTTOM_OR_RIGHT
                    && !isFloating && !mIsLeftRightSplit && mImeShown;
            mTargetYOffset = needOffset ? getTargetYOffset() : 0;

            if (mTargetYOffset != mLastYOffset) {
                ProtoLog.v(ShellProtoLogGroup.WM_SHELL_SPLIT_SCREEN,
                        "Split IME animation starting, fromY=%d toY=%d",
                        mLastYOffset, mTargetYOffset);
                // Freeze the configuration size with offset to prevent app get a configuration
                // changed or relaunch. This is required to make sure client apps will calculate
                // insets properly after layout shifted.
                mSplitLayoutHandler.setLayoutOffsetTarget(0, mTargetYOffset, SplitLayout.this);
            }

            // Make {@link DividerView} non-interactive while IME showing in split mode. Listen to
            // ImePositionProcessor#onImeVisibilityChanged directly in DividerView is not enough
            // because DividerView won't receive onImeVisibilityChanged callback after it being
            // re-inflated.
            setDividerInteractive(!mImeShown || !mHasImeFocus || isFloating, true,
                    "onImeStartPositioning");

            if (mImeShown) {
                mSplitLayoutHandler.setExcludeImeInsets(false);
            }

            return mTargetYOffset != mLastYOffset ? IME_ANIMATION_NO_ALPHA : 0;
        }

        @Override
        public void onImePositionChanged(int displayId, int imeTop, SurfaceControl.Transaction t) {
            if (displayId != mDisplayId || !mHasImeFocus) {
                if (mImeShown) {
                    return;
                }
            }
            onProgress(getProgress(imeTop));
            mSplitLayoutHandler.onLayoutPositionChanging(SplitLayout.this);
        }

        @Override
        public void onImeEndPositioning(int displayId, boolean cancel,
                SurfaceControl.Transaction t) {
            if (displayId != mDisplayId || cancel) return;
            if (!mHasImeFocus) {
                if (mImeShown) {
                    return;
                }
            }
            ProtoLog.v(ShellProtoLogGroup.WM_SHELL_SPLIT_SCREEN,
                    "Split IME animation ending, canceled=%b", cancel);
            onProgress(1.0f);
            mSplitLayoutHandler.onLayoutPositionChanging(SplitLayout.this);
            if (!mImeShown) {
                // The IME hide animation is started immediately and at that point, the IME
                // insets are not yet set to hidden. Therefore only resetting the
                // excludedTypes at the end of the animation. Note: InsetsPolicy will only
                // set the IME height to zero, when it is visible. When it becomes invisible,
                // we dispatch the insets (the height there is zero as well)
                mSplitLayoutHandler.setExcludeImeInsets(false);
            }
        }

        @Override
        public void onImeControlTargetChanged(int displayId, boolean controlling) {
            if (displayId != mDisplayId) return;
            // Restore the split layout when wm-shell is not controlling IME insets anymore.
            if (!controlling && mImeShown) {
                reset();
                setDividerInteractive(true, true, "onImeControlTargetChanged");
                mSplitLayoutHandler.setLayoutOffsetTarget(0, 0, SplitLayout.this);
                mSplitLayoutHandler.onLayoutPositionChanging(SplitLayout.this);
            }
        }

        /**
         * When IME is triggered on the bottom app in split screen, we want to translate the bottom
         * app up by a certain amount so that it's not covered too much by the IME. But there's also
         * an upper limit to the amount we want to translate (since we still need some of the top
         * app to be visible too). So this function essentially says "try to translate the bottom
         * app up, but stop before you make the top app too small."
         */
        private int getTargetYOffset() {
            // We want to translate up the bottom app by this amount.
            final int desiredOffset = Math.abs(mEndImeTop - mStartImeTop);

            // But we also want to keep this much of the top app visible.
            final float amountOfTopAppToKeepVisible =
                    getTopLeftBounds().height() * (1 - ADJUSTED_SPLIT_FRACTION_MAX);

            // So the current onscreen size of the top app, minus the minimum size, is the max
            // translation we will allow.
            final float currentOnScreenSizeOfTopApp = getTopLeftBounds().bottom;
            final int maxOffset =
                    (int) Math.max(currentOnScreenSizeOfTopApp - amountOfTopAppToKeepVisible, 0);

            return -Math.min(desiredOffset, maxOffset);
        }

        @SplitPosition
        private int getImeLayeringTargetPosition() {
            final WindowContainerToken token = mTaskOrganizer.getImeLayeringTarget(mDisplayId);
            return mSplitLayoutHandler.getSplitItemPosition(token);
        }

        private float getProgress(int currImeTop) {
            return ((float) currImeTop - mStartImeTop) / (mEndImeTop - mStartImeTop);
        }

        private void onProgress(float progress) {
            mDimValue1 = getProgressValue(mLastDim1, mTargetDim1, progress);
            mDimValue2 = getProgressValue(mLastDim2, mTargetDim2, progress);
            mYOffsetForIme =
                    (int) getProgressValue((float) mLastYOffset, (float) mTargetYOffset, progress);
        }

        private float getProgressValue(float start, float end, float progress) {
            return start + (end - start) * progress;
        }

        void reset() {
            mHasImeFocus = false;
            mImeShown = false;
            mYOffsetForIme = mLastYOffset = mTargetYOffset = 0;
            mDimValue1 = mLastDim1 = mTargetDim1 = 0.0f;
            mDimValue2 = mLastDim2 = mTargetDim2 = 0.0f;
        }

        /**
         * Adjusts surface layout while showing IME.
         *
         * @return {@code false} if there's no need to adjust, otherwise {@code true}
         */
        boolean adjustSurfaceLayoutForIme(SurfaceControl.Transaction t,
                SurfaceControl dividerLeash, SurfaceControl leash1, SurfaceControl leash2,
                SurfaceControl dimLayer1, SurfaceControl dimLayer2) {
            final boolean showDim = mDimValue1 > 0.001f || mDimValue2 > 0.001f;
            boolean adjusted = false;
            if (mYOffsetForIme != 0) {
                if (dividerLeash != null) {
                    getRefDividerBounds(mTempRect);
                    mTempRect.offset(0, mYOffsetForIme);
                    t.setPosition(dividerLeash, mTempRect.left, mTempRect.top);
                }

                copyTopLeftRefBounds(mTempRect);
                mTempRect.offset(0, mYOffsetForIme);
                t.setPosition(leash1, mTempRect.left, mTempRect.top);

                copyBottomRightRefBounds(mTempRect);
                mTempRect.offset(0, mYOffsetForIme);
                t.setPosition(leash2, mTempRect.left, mTempRect.top);
                adjusted = true;
            }

            if (showDim) {
                t.setAlpha(dimLayer1, mDimValue1).setVisibility(dimLayer1, mDimValue1 > 0.001f);
                t.setAlpha(dimLayer2, mDimValue2).setVisibility(dimLayer2, mDimValue2 > 0.001f);
                adjusted = true;
            }
            return adjusted;
        }
    }
}
