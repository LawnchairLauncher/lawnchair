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
import static android.view.WindowManager.DOCKED_BOTTOM;
import static android.view.WindowManager.DOCKED_INVALID;
import static android.view.WindowManager.DOCKED_LEFT;
import static android.view.WindowManager.DOCKED_RIGHT;
import static android.view.WindowManager.DOCKED_TOP;

import static com.android.internal.jank.InteractionJankMonitor.CUJ_SPLIT_SCREEN_DOUBLE_TAP_DIVIDER;
import static com.android.internal.jank.InteractionJankMonitor.CUJ_SPLIT_SCREEN_RESIZE;
import static com.android.wm.shell.animation.Interpolators.DIM_INTERPOLATOR;
import static com.android.wm.shell.animation.Interpolators.SLOWDOWN_INTERPOLATOR;
import static com.android.wm.shell.common.split.SplitScreenConstants.SNAP_TO_END_AND_DISMISS;
import static com.android.wm.shell.common.split.SplitScreenConstants.SNAP_TO_START_AND_DISMISS;
import static com.android.wm.shell.common.split.SplitScreenConstants.SPLIT_POSITION_BOTTOM_OR_RIGHT;
import static com.android.wm.shell.common.split.SplitScreenConstants.SPLIT_POSITION_TOP_OR_LEFT;
import static com.android.wm.shell.common.split.SplitScreenConstants.SPLIT_POSITION_UNDEFINED;
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
import android.graphics.Point;
import android.graphics.Rect;
import android.view.Display;
import android.view.InsetsSourceControl;
import android.view.InsetsState;
import android.view.RoundedCorner;
import android.view.SurfaceControl;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.window.WindowContainerToken;
import android.window.WindowContainerTransaction;

import androidx.annotation.Nullable;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.protolog.common.ProtoLog;
import com.android.wm.shell.R;
import com.android.wm.shell.ShellTaskOrganizer;
import com.android.wm.shell.animation.Interpolators;
import com.android.wm.shell.common.DisplayController;
import com.android.wm.shell.common.DisplayImeController;
import com.android.wm.shell.common.DisplayInsetsController;
import com.android.wm.shell.common.DisplayLayout;
import com.android.wm.shell.common.InteractionJankMonitorUtils;
import com.android.wm.shell.common.split.SplitScreenConstants.PersistentSnapPosition;
import com.android.wm.shell.common.split.SplitScreenConstants.SnapPosition;
import com.android.wm.shell.common.split.SplitScreenConstants.SplitPosition;
import com.android.wm.shell.protolog.ShellProtoLogGroup;

import java.io.PrintWriter;
import java.util.function.Consumer;

/**
 * Records and handles layout of splits. Helps to calculate proper bounds when configuration or
 * divider position changes.
 */
public final class SplitLayout implements DisplayInsetsController.OnInsetsChangedListener {
    private static final String TAG = "SplitLayout";
    public static final int PARALLAX_NONE = 0;
    public static final int PARALLAX_DISMISSING = 1;
    public static final int PARALLAX_ALIGN_CENTER = 2;

    public static final int FLING_RESIZE_DURATION = 250;
    private static final int FLING_SWITCH_DURATION = 350;
    private static final int FLING_ENTER_DURATION = 450;
    private static final int FLING_EXIT_DURATION = 450;

    private int mDividerWindowWidth;
    private int mDividerInsets;
    private int mDividerSize;

    private final Rect mTempRect = new Rect();
    private final Rect mRootBounds = new Rect();
    private final Rect mDividerBounds = new Rect();
    // Bounds1 final position should be always at top or left
    private final Rect mBounds1 = new Rect();
    // Bounds2 final position should be always at bottom or right
    private final Rect mBounds2 = new Rect();
    // The temp bounds outside of display bounds for side stage when split screen inactive to avoid
    // flicker next time active split screen.
    private final Rect mInvisibleBounds = new Rect();
    private final Rect mWinBounds1 = new Rect();
    private final Rect mWinBounds2 = new Rect();
    private final SplitLayoutHandler mSplitLayoutHandler;
    private final SplitWindowManager mSplitWindowManager;
    private final DisplayController mDisplayController;
    private final DisplayImeController mDisplayImeController;
    private final ImePositionProcessor mImePositionProcessor;
    private final ResizingEffectPolicy mSurfaceEffectPolicy;
    private final ShellTaskOrganizer mTaskOrganizer;
    private final InsetsState mInsetsState = new InsetsState();

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
    private boolean mIsLeftRightSplit;
    private ValueAnimator mDividerFlingAnimator;

    public SplitLayout(String windowName, Context context, Configuration configuration,
            SplitLayoutHandler splitLayoutHandler,
            SplitWindowManager.ParentContainerCallbacks parentContainerCallbacks,
            DisplayController displayController, DisplayImeController displayImeController,
            ShellTaskOrganizer taskOrganizer, int parallaxType) {
        mContext = context.createConfigurationContext(configuration);
        mOrientation = configuration.orientation;
        mRotation = configuration.windowConfiguration.getRotation();
        mDensity = configuration.densityDpi;
        mIsLargeScreen = configuration.smallestScreenWidthDp >= 600;
        mSplitLayoutHandler = splitLayoutHandler;
        mDisplayController = displayController;
        mDisplayImeController = displayImeController;
        mSplitWindowManager = new SplitWindowManager(windowName, mContext, configuration,
                parentContainerCallbacks);
        mTaskOrganizer = taskOrganizer;
        mImePositionProcessor = new ImePositionProcessor(mContext.getDisplayId());
        mSurfaceEffectPolicy = new ResizingEffectPolicy(parallaxType);

        final Resources res = mContext.getResources();
        mDimNonImeSide = res.getBoolean(R.bool.config_dimNonImeAttachedSide);
        mAllowLeftRightSplitInPortrait = SplitScreenUtils.allowLeftRightSplitInPortrait(res);
        mIsLeftRightSplit = SplitScreenUtils.isLeftRightSplit(mAllowLeftRightSplitInPortrait,
                configuration);

        updateDividerConfig(mContext);

        mRootBounds.set(configuration.windowConfiguration.getBounds());
        mDividerSnapAlgorithm = getSnapAlgorithm(mContext, mRootBounds);
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

    /** Gets bounds of the primary split with screen based coordinate. */
    public Rect getBounds1() {
        return new Rect(mBounds1);
    }

    /** Gets bounds of the primary split with parent based coordinate. */
    public Rect getRefBounds1() {
        Rect outBounds = getBounds1();
        outBounds.offset(-mRootBounds.left, -mRootBounds.top);
        return outBounds;
    }

    /** Gets bounds of the secondary split with screen based coordinate. */
    public Rect getBounds2() {
        return new Rect(mBounds2);
    }

    /** Gets bounds of the secondary split with parent based coordinate. */
    public Rect getRefBounds2() {
        final Rect outBounds = getBounds2();
        outBounds.offset(-mRootBounds.left, -mRootBounds.top);
        return outBounds;
    }

    /** Gets root bounds of the whole split layout */
    public Rect getRootBounds() {
        return new Rect(mRootBounds);
    }

    /** Gets bounds of divider window with screen based coordinate. */
    public Rect getDividerBounds() {
        return new Rect(mDividerBounds);
    }

    /** Gets bounds of divider window with parent based coordinate. */
    public Rect getRefDividerBounds() {
        final Rect outBounds = getDividerBounds();
        outBounds.offset(-mRootBounds.left, -mRootBounds.top);
        return outBounds;
    }

    /** Gets bounds of the primary split with screen based coordinate on the param Rect. */
    public void getBounds1(Rect rect) {
        rect.set(mBounds1);
    }

    /** Gets bounds of the primary split with parent based coordinate on the param Rect. */
    public void getRefBounds1(Rect rect) {
        getBounds1(rect);
        rect.offset(-mRootBounds.left, -mRootBounds.top);
    }

    /** Gets bounds of the secondary split with screen based coordinate on the param Rect. */
    public void getBounds2(Rect rect) {
        rect.set(mBounds2);
    }

    /** Gets bounds of the secondary split with parent based coordinate on the param Rect. */
    public void getRefBounds2(Rect rect) {
        getBounds2(rect);
        rect.offset(-mRootBounds.left, -mRootBounds.top);
    }

    /** Gets root bounds of the whole split layout on the param Rect. */
    public void getRootBounds(Rect rect) {
        rect.set(mRootBounds);
    }

    /** Gets bounds of divider window with screen based coordinate on the param Rect. */
    public void getDividerBounds(Rect rect) {
        rect.set(mDividerBounds);
    }

    /** Gets bounds of divider window with parent based coordinate on the param Rect. */
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

    /**
     * Finds the {@link SnapPosition} nearest to the current divider position.
     */
    public int calculateCurrentSnapPosition() {
        return mDividerSnapAlgorithm.calculateNearestSnapPosition(mDividerPosition);
    }

    /**
     * Returns the divider position as a fraction from 0 to 1.
     */
    public float getDividerPositionAsFraction() {
        return Math.min(1f, Math.max(0f, mIsLeftRightSplit
                ? (float) ((mBounds1.right + mBounds2.left) / 2f) / mBounds2.right
                : (float) ((mBounds1.bottom + mBounds2.top) / 2f) / mBounds2.bottom));
    }

    private void updateInvisibleRect() {
        mInvisibleBounds.set(mRootBounds.left, mRootBounds.top,
                mIsLeftRightSplit ? mRootBounds.right / 2 : mRootBounds.right,
                mIsLeftRightSplit ? mRootBounds.bottom : mRootBounds.bottom / 2);
        mInvisibleBounds.offset(mIsLeftRightSplit ? mRootBounds.right : 0,
                mIsLeftRightSplit ? 0 : mRootBounds.bottom);
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
        mDividerSnapAlgorithm = getSnapAlgorithm(mContext, mRootBounds);
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
        mDividerSnapAlgorithm = getSnapAlgorithm(mContext, mRootBounds);
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
        updateBounds(position, mBounds1, mBounds2, mDividerBounds, true /* setEffectBounds */);
    }

    /** Updates recording bounds of divider window and both of the splits. */
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
        } else {
            position += mRootBounds.top;
            dividerBounds.top = position - mDividerInsets;
            dividerBounds.bottom = dividerBounds.top + mDividerWindowWidth;
            bounds1.bottom = position;
            bounds2.top = bounds1.bottom + mDividerSize;
        }
        DockedDividerUtils.sanitizeStackBounds(bounds1, true /** topLeft */);
        DockedDividerUtils.sanitizeStackBounds(bounds2, false /** topLeft */);
        if (setEffectBounds) {
            mSurfaceEffectPolicy.applyDividerPosition(position, mIsLeftRightSplit);
        }
    }

    /** Inflates {@link DividerView} on the root surface. */
    public void init() {
        if (mInitialized) return;
        mInitialized = true;
        mSplitWindowManager.init(this, mInsetsState, false /* isRestoring */);
        mDisplayImeController.addPositionProcessor(mImePositionProcessor);
    }

    /** Releases the surface holding the current {@link DividerView}. */
    public void release(SurfaceControl.Transaction t) {
        if (!mInitialized) return;
        mInitialized = false;
        mSplitWindowManager.release(t);
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
        mSplitWindowManager.init(this, mInsetsState, true /* isRestoring */);
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
        mSplitWindowManager.onInsetsChanged(insetsState);
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
        mSplitLayoutHandler.onLayoutSizeChanging(this, mSurfaceEffectPolicy.mParallaxOffset.x,
                mSurfaceEffectPolicy.mParallaxOffset.y, shouldUseParallaxEffect);
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
        final DividerSnapAlgorithm.SnapTarget snapTarget = mDividerSnapAlgorithm.findSnapTarget(
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
        mWinBounds1.setEmpty();
        mWinBounds2.setEmpty();
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
    public void snapToTarget(int currentPosition, DividerSnapAlgorithm.SnapTarget snapTarget) {
        switch (snapTarget.snapPosition) {
            case SNAP_TO_START_AND_DISMISS:
                flingDividerPosition(currentPosition, snapTarget.position, FLING_RESIZE_DURATION,
                        () -> mSplitLayoutHandler.onSnappedToDismiss(false /* bottomOrRight */,
                                EXIT_REASON_DRAG_DIVIDER));
                break;
            case SNAP_TO_END_AND_DISMISS:
                flingDividerPosition(currentPosition, snapTarget.position, FLING_RESIZE_DURATION,
                        () -> mSplitLayoutHandler.onSnappedToDismiss(true /* bottomOrRight */,
                                EXIT_REASON_DRAG_DIVIDER));
                break;
            default:
                flingDividerPosition(currentPosition, snapTarget.position, FLING_RESIZE_DURATION,
                        () -> setDividerPosition(snapTarget.position, true /* applyLayoutChange */));
                break;
        }
    }

    void onStartDragging() {
        InteractionJankMonitorUtils.beginTracing(CUJ_SPLIT_SCREEN_RESIZE, mContext,
                getDividerLeash(), null /* tag */);
    }

    void onDraggingCancelled() {
        InteractionJankMonitorUtils.cancelTracing(CUJ_SPLIT_SCREEN_RESIZE);
    }

    void onDoubleTappedDivider() {
        mSplitLayoutHandler.onDoubleTappedDivider();
    }

    /**
     * Returns {@link DividerSnapAlgorithm.SnapTarget} which matches passing position and velocity.
     * If hardDismiss is set to {@code true}, it will be harder to reach dismiss target.
     */
    public DividerSnapAlgorithm.SnapTarget findSnapTarget(int position, float velocity,
            boolean hardDismiss) {
        return mDividerSnapAlgorithm.calculateSnapTarget(position, velocity, hardDismiss);
    }

    private DividerSnapAlgorithm getSnapAlgorithm(Context context, Rect rootBounds) {
        final Rect insets = getDisplayStableInsets(context);

        // Make split axis insets value same as the larger one to avoid bounds1 and bounds2
        // have difference for avoiding size-compat mode when switching unresizable apps in
        // landscape while they are letterboxed.
        if (!mIsLeftRightSplit) {
            final int largerInsets = Math.max(insets.top, insets.bottom);
            insets.set(insets.left, largerInsets, insets.right, largerInsets);
        }

        return new DividerSnapAlgorithm(
                context.getResources(),
                rootBounds.width(),
                rootBounds.height(),
                mDividerSize,
                !mIsLeftRightSplit,
                insets,
                mIsLeftRightSplit ? DOCKED_LEFT : DOCKED_TOP /* dockSide */);
    }

    /** Fling divider from current position to end or start position then exit */
    public void flingDividerToDismiss(boolean toEnd, int reason) {
        final int target = toEnd ? mDividerSnapAlgorithm.getDismissEndTarget().position
                : mDividerSnapAlgorithm.getDismissStartTarget().position;
        flingDividerPosition(getDividerPosition(), target, FLING_EXIT_DURATION,
                () -> mSplitLayoutHandler.onSnappedToDismiss(toEnd, reason));
    }

    /** Fling divider from current position to center position. */
    public void flingDividerToCenter(@Nullable Runnable finishCallback) {
        final int pos = mDividerSnapAlgorithm.getMiddleTarget().position;
        flingDividerPosition(getDividerPosition(), pos, FLING_ENTER_DURATION,
                () -> {
                    setDividerPosition(pos, true /* applyLayoutChange */);
                    if (finishCallback != null) {
                        finishCallback.run();
                    }
                });
    }

    @VisibleForTesting
    void flingDividerPosition(int from, int to, int duration,
            @Nullable Runnable flingFinishedCallback) {
        if (from == to) {
            if (flingFinishedCallback != null) {
                flingFinishedCallback.run();
            }
            InteractionJankMonitorUtils.endTracing(
                    CUJ_SPLIT_SCREEN_RESIZE);
            return;
        }

        if (mDividerFlingAnimator != null) {
            mDividerFlingAnimator.cancel();
        }

        mDividerFlingAnimator = ValueAnimator
                .ofInt(from, to)
                .setDuration(duration);
        mDividerFlingAnimator.setInterpolator(Interpolators.FAST_OUT_SLOW_IN);
        mDividerFlingAnimator.addUpdateListener(
                animation -> updateDividerBounds(
                        (int) animation.getAnimatedValue(), false /* shouldUseParallaxEffect */)
        );
        mDividerFlingAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (flingFinishedCallback != null) {
                    flingFinishedCallback.run();
                }
                InteractionJankMonitorUtils.endTracing(
                        CUJ_SPLIT_SCREEN_RESIZE);
                mDividerFlingAnimator = null;
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                mDividerFlingAnimator = null;
            }
        });
        mDividerFlingAnimator.start();
    }

    /** Switch both surface position with animation. */
    public void splitSwitching(SurfaceControl.Transaction t, SurfaceControl leash1,
            SurfaceControl leash2, Consumer<Rect> finishCallback) {
        final Rect insets = getDisplayStableInsets(mContext);
        insets.set(mIsLeftRightSplit ? insets.left : 0, mIsLeftRightSplit ? 0 : insets.top,
                mIsLeftRightSplit ? insets.right : 0, mIsLeftRightSplit ? 0 : insets.bottom);

        final int dividerPos = mDividerSnapAlgorithm.calculateNonDismissingSnapTarget(
                mIsLeftRightSplit ? mBounds2.width() : mBounds2.height()).position;
        final Rect distBounds1 = new Rect();
        final Rect distBounds2 = new Rect();
        final Rect distDividerBounds = new Rect();
        // Compute dist bounds.
        updateBounds(dividerPos, distBounds2, distBounds1, distDividerBounds,
                false /* setEffectBounds */);
        // Offset to real position under root container.
        distBounds1.offset(-mRootBounds.left, -mRootBounds.top);
        distBounds2.offset(-mRootBounds.left, -mRootBounds.top);
        distDividerBounds.offset(-mRootBounds.left, -mRootBounds.top);

        ValueAnimator animator1 = moveSurface(t, leash1, getRefBounds1(), distBounds1,
                -insets.left, -insets.top);
        ValueAnimator animator2 = moveSurface(t, leash2, getRefBounds2(), distBounds2,
                insets.left, insets.top);
        ValueAnimator animator3 = moveSurface(t, getDividerLeash(), getRefDividerBounds(),
                distDividerBounds, 0 /* offsetX */, 0 /* offsetY */);

        AnimatorSet set = new AnimatorSet();
        set.playTogether(animator1, animator2, animator3);
        set.setDuration(FLING_SWITCH_DURATION);
        set.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                InteractionJankMonitorUtils.beginTracing(CUJ_SPLIT_SCREEN_DOUBLE_TAP_DIVIDER,
                        mContext, getDividerLeash(), null /*tag*/);
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                mDividerPosition = dividerPos;
                updateBounds(mDividerPosition);
                finishCallback.accept(insets);
                InteractionJankMonitorUtils.endTracing(CUJ_SPLIT_SCREEN_DOUBLE_TAP_DIVIDER);
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                InteractionJankMonitorUtils.cancelTracing(CUJ_SPLIT_SCREEN_DOUBLE_TAP_DIVIDER);
            }
        });
        set.start();
    }

    private ValueAnimator moveSurface(SurfaceControl.Transaction t, SurfaceControl leash,
            Rect start, Rect end, float offsetX, float offsetY) {
        Rect tempStart = new Rect(start);
        Rect tempEnd = new Rect(end);
        final float diffX = tempEnd.left - tempStart.left;
        final float diffY = tempEnd.top - tempStart.top;
        final float diffWidth = tempEnd.width() - tempStart.width();
        final float diffHeight = tempEnd.height() - tempStart.height();
        ValueAnimator animator = ValueAnimator.ofFloat(0, 1);
        animator.addUpdateListener(animation -> {
            if (leash == null) return;

            final float scale = (float) animation.getAnimatedValue();
            final float distX = tempStart.left + scale * diffX;
            final float distY = tempStart.top + scale * diffY;
            final int width = (int) (tempStart.width() + scale * diffWidth);
            final int height = (int) (tempStart.height() + scale * diffHeight);
            if (offsetX == 0 && offsetY == 0) {
                t.setPosition(leash, distX, distY);
                t.setWindowCrop(leash, width, height);
            } else {
                final int diffOffsetX = (int) (scale * offsetX);
                final int diffOffsetY = (int) (scale * offsetY);
                t.setPosition(leash, distX + diffOffsetX, distY + diffOffsetY);
                mTempRect.set(0, 0, width, height);
                mTempRect.offsetTo(-diffOffsetX, -diffOffsetY);
                t.setCrop(leash, mTempRect);
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
            t.setLayer(dividerLeash, Integer.MAX_VALUE);
        }
        getRefBounds1(mTempRect);
        t.setPosition(leash1, mTempRect.left, mTempRect.top)
                .setWindowCrop(leash1, mTempRect.width(), mTempRect.height());
        getRefBounds2(mTempRect);
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
        if (!mBounds1.equals(mWinBounds1) || !task1.token.equals(mWinToken1)) {
            setTaskBounds(wct, task1, mBounds1);
            mWinBounds1.set(mBounds1);
            mWinToken1 = task1.token;
            boundsChanged = true;
        }
        if (!mBounds2.equals(mWinBounds2) || !task2.token.equals(mWinToken2)) {
            setTaskBounds(wct, task2, mBounds2);
            mWinBounds2.set(mBounds2);
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
    }

    private int getSmallestWidthDp(Rect bounds) {
        mTempRect.set(bounds);
        mTempRect.inset(getDisplayStableInsets(mContext));
        final int minWidth = Math.min(mTempRect.width(), mTempRect.height());
        final float density = mContext.getResources().getDisplayMetrics().density;
        return (int) (minWidth / density);
    }

    /**
     * Shift configuration bounds to prevent client apps get configuration changed or relaunch. And
     * restore shifted configuration bounds if it's no longer shifted.
     */
    public void applyLayoutOffsetTarget(WindowContainerTransaction wct, int offsetX, int offsetY,
            ActivityManager.RunningTaskInfo taskInfo1, ActivityManager.RunningTaskInfo taskInfo2) {
        if (offsetX == 0 && offsetY == 0) {
            wct.setBounds(taskInfo1.token, mBounds1);
            wct.setScreenSizeDp(taskInfo1.token,
                    SCREEN_WIDTH_DP_UNDEFINED, SCREEN_HEIGHT_DP_UNDEFINED);

            wct.setBounds(taskInfo2.token, mBounds2);
            wct.setScreenSizeDp(taskInfo2.token,
                    SCREEN_WIDTH_DP_UNDEFINED, SCREEN_HEIGHT_DP_UNDEFINED);
        } else {
            getBounds1(mTempRect);
            mTempRect.offset(offsetX, offsetY);
            wct.setBounds(taskInfo1.token, mTempRect);
            wct.setScreenSizeDp(taskInfo1.token,
                    taskInfo1.configuration.screenWidthDp,
                    taskInfo1.configuration.screenHeightDp);

            getBounds2(mTempRect);
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
        pw.println(innerPrefix + "bounds1=" + mBounds1.toShortString());
        pw.println(innerPrefix + "dividerBounds=" + mDividerBounds.toShortString());
        pw.println(innerPrefix + "bounds2=" + mBounds2.toShortString());
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

        /** Returns split position of the token. */
        @SplitPosition
        int getSplitItemPosition(WindowContainerToken token);
    }

    /**
     * Calculates and applies proper dismissing parallax offset and dimming value to hint users
     * dismissing gesture.
     */
    private class ResizingEffectPolicy {
        /** Indicates whether to offset splitting bounds to hint dismissing progress or not. */
        private final int mParallaxType;

        int mShrinkSide = DOCKED_INVALID;

        // The current dismissing side.
        int mDismissingSide = DOCKED_INVALID;

        // The parallax offset to hint the dismissing side and progress.
        final Point mParallaxOffset = new Point();

        // The dimming value to hint the dismissing side and progress.
        float mDismissingDimValue = 0.0f;
        final Rect mContentBounds = new Rect();
        final Rect mSurfaceBounds = new Rect();

        ResizingEffectPolicy(int parallaxType) {
            mParallaxType = parallaxType;
        }

        /**
         * Applies a parallax to the task to hint dismissing progress.
         *
         * @param position    the split position to apply dismissing parallax effect
         * @param isLeftRightSplit indicates whether it's splitting horizontally or vertically
         */
        void applyDividerPosition(int position, boolean isLeftRightSplit) {
            mDismissingSide = DOCKED_INVALID;
            mParallaxOffset.set(0, 0);
            mDismissingDimValue = 0;

            int totalDismissingDistance = 0;
            if (position < mDividerSnapAlgorithm.getFirstSplitTarget().position) {
                mDismissingSide = isLeftRightSplit ? DOCKED_LEFT : DOCKED_TOP;
                totalDismissingDistance = mDividerSnapAlgorithm.getDismissStartTarget().position
                        - mDividerSnapAlgorithm.getFirstSplitTarget().position;
            } else if (position > mDividerSnapAlgorithm.getLastSplitTarget().position) {
                mDismissingSide = isLeftRightSplit ? DOCKED_RIGHT : DOCKED_BOTTOM;
                totalDismissingDistance = mDividerSnapAlgorithm.getLastSplitTarget().position
                        - mDividerSnapAlgorithm.getDismissEndTarget().position;
            }

            final boolean topLeftShrink = isLeftRightSplit
                    ? position < mWinBounds1.right : position < mWinBounds1.bottom;
            if (topLeftShrink) {
                mShrinkSide = isLeftRightSplit ? DOCKED_LEFT : DOCKED_TOP;
                mContentBounds.set(mWinBounds1);
                mSurfaceBounds.set(mBounds1);
            } else {
                mShrinkSide = isLeftRightSplit ? DOCKED_RIGHT : DOCKED_BOTTOM;
                mContentBounds.set(mWinBounds2);
                mSurfaceBounds.set(mBounds2);
            }

            if (mDismissingSide != DOCKED_INVALID) {
                float fraction = Math.max(0,
                        Math.min(mDividerSnapAlgorithm.calculateDismissingFraction(position), 1f));
                mDismissingDimValue = DIM_INTERPOLATOR.getInterpolation(fraction);
                if (mParallaxType == PARALLAX_DISMISSING) {
                    fraction = calculateParallaxDismissingFraction(fraction, mDismissingSide);
                    if (isLeftRightSplit) {
                        mParallaxOffset.x = (int) (fraction * totalDismissingDistance);
                    } else {
                        mParallaxOffset.y = (int) (fraction * totalDismissingDistance);
                    }
                }
            }

            if (mParallaxType == PARALLAX_ALIGN_CENTER) {
                if (isLeftRightSplit) {
                    mParallaxOffset.x =
                            (mSurfaceBounds.width() - mContentBounds.width()) / 2;
                } else {
                    mParallaxOffset.y =
                            (mSurfaceBounds.height() - mContentBounds.height()) / 2;
                }
            }
        }

        /**
         * @return for a specified {@code fraction}, this returns an adjusted value that simulates a
         * slowing down parallax effect
         */
        private float calculateParallaxDismissingFraction(float fraction, int dockSide) {
            float result = SLOWDOWN_INTERPOLATOR.getInterpolation(fraction) / 3.5f;

            // Less parallax at the top, just because.
            if (dockSide == WindowManager.DOCKED_TOP) {
                result /= 2f;
            }
            return result;
        }

        /** Applies parallax offset and dimming value to the root surface at the dismissing side. */
        void adjustRootSurface(SurfaceControl.Transaction t,
                SurfaceControl leash1, SurfaceControl leash2) {
            SurfaceControl targetLeash = null;

            if (mParallaxType == PARALLAX_DISMISSING) {
                switch (mDismissingSide) {
                    case DOCKED_TOP:
                    case DOCKED_LEFT:
                        targetLeash = leash1;
                        mTempRect.set(mBounds1);
                        break;
                    case DOCKED_BOTTOM:
                    case DOCKED_RIGHT:
                        targetLeash = leash2;
                        mTempRect.set(mBounds2);
                        break;
                }
            } else if (mParallaxType == PARALLAX_ALIGN_CENTER) {
                switch (mShrinkSide) {
                    case DOCKED_TOP:
                    case DOCKED_LEFT:
                        targetLeash = leash1;
                        mTempRect.set(mBounds1);
                        break;
                    case DOCKED_BOTTOM:
                    case DOCKED_RIGHT:
                        targetLeash = leash2;
                        mTempRect.set(mBounds2);
                        break;
                }
            }
            if (mParallaxType != PARALLAX_NONE && targetLeash != null) {
                t.setPosition(targetLeash,
                        mTempRect.left + mParallaxOffset.x, mTempRect.top + mParallaxOffset.y);
                // Transform the screen-based split bounds to surface-based crop bounds.
                mTempRect.offsetTo(-mParallaxOffset.x, -mParallaxOffset.y);
                t.setWindowCrop(targetLeash, mTempRect);
            }
        }

        void adjustDimSurface(SurfaceControl.Transaction t,
                SurfaceControl dimLayer1, SurfaceControl dimLayer2) {
            SurfaceControl targetDimLayer;
            switch (mDismissingSide) {
                case DOCKED_TOP:
                case DOCKED_LEFT:
                    targetDimLayer = dimLayer1;
                    break;
                case DOCKED_BOTTOM:
                case DOCKED_RIGHT:
                    targetDimLayer = dimLayer2;
                    break;
                case DOCKED_INVALID:
                default:
                    t.setAlpha(dimLayer1, 0).hide(dimLayer1);
                    t.setAlpha(dimLayer2, 0).hide(dimLayer2);
                    return;
            }
            t.setAlpha(targetDimLayer, mDismissingDimValue)
                    .setVisibility(targetDimLayer, mDismissingDimValue > 0.001f);
        }
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
        public int onImeStartPositioning(int displayId, int hiddenTop, int shownTop,
                boolean showing, boolean isFloating, SurfaceControl.Transaction t) {
            if (displayId != mDisplayId || !mInitialized) {
                return 0;
            }

            final int imeTargetPosition = getImeTargetPosition();
            mHasImeFocus = imeTargetPosition != SPLIT_POSITION_UNDEFINED;
            if (!mHasImeFocus) {
                return 0;
            }

            mStartImeTop = showing ? hiddenTop : shownTop;
            mEndImeTop = showing ? shownTop : hiddenTop;
            mImeShown = showing;

            // Update target dim values
            mLastDim1 = mDimValue1;
            mTargetDim1 = imeTargetPosition == SPLIT_POSITION_BOTTOM_OR_RIGHT && mImeShown
                    && mDimNonImeSide ? ADJUSTED_NONFOCUS_DIM : 0.0f;
            mLastDim2 = mDimValue2;
            mTargetDim2 = imeTargetPosition == SPLIT_POSITION_TOP_OR_LEFT && mImeShown
                    && mDimNonImeSide ? ADJUSTED_NONFOCUS_DIM : 0.0f;

            // Calculate target bounds offset for IME
            mLastYOffset = mYOffsetForIme;
            final boolean needOffset = imeTargetPosition == SPLIT_POSITION_BOTTOM_OR_RIGHT
                    && !isFloating && !mIsLeftRightSplit && mImeShown;
            mTargetYOffset = needOffset ? getTargetYOffset() : 0;

            if (mTargetYOffset != mLastYOffset) {
                ProtoLog.v(ShellProtoLogGroup.WM_SHELL_SPLIT_SCREEN,
                        "Split IME animation starting, fromY=%d toY=%d",
                        mLastYOffset, mTargetYOffset);
                // Freeze the configuration size with offset to prevent app get a configuration
                // changed or relaunch. This is required to make sure client apps will calculate
                // insets properly after layout shifted.
                if (mTargetYOffset == 0) {
                    mSplitLayoutHandler.setLayoutOffsetTarget(0, 0, SplitLayout.this);
                } else {
                    mSplitLayoutHandler.setLayoutOffsetTarget(0, mTargetYOffset, SplitLayout.this);
                }
            }

            // Make {@link DividerView} non-interactive while IME showing in split mode. Listen to
            // ImePositionProcessor#onImeVisibilityChanged directly in DividerView is not enough
            // because DividerView won't receive onImeVisibilityChanged callback after it being
            // re-inflated.
            setDividerInteractive(!mImeShown || !mHasImeFocus || isFloating, true,
                    "onImeStartPositioning");

            return mTargetYOffset != mLastYOffset ? IME_ANIMATION_NO_ALPHA : 0;
        }

        @Override
        public void onImePositionChanged(int displayId, int imeTop, SurfaceControl.Transaction t) {
            if (displayId != mDisplayId || !mHasImeFocus) return;
            onProgress(getProgress(imeTop));
            mSplitLayoutHandler.onLayoutPositionChanging(SplitLayout.this);
        }

        @Override
        public void onImeEndPositioning(int displayId, boolean cancel,
                SurfaceControl.Transaction t) {
            if (displayId != mDisplayId || !mHasImeFocus || cancel) return;
            ProtoLog.v(ShellProtoLogGroup.WM_SHELL_SPLIT_SCREEN,
                    "Split IME animation ending, canceled=%b", cancel);
            onProgress(1.0f);
            mSplitLayoutHandler.onLayoutPositionChanging(SplitLayout.this);
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

        private int getTargetYOffset() {
            final int desireOffset = Math.abs(mEndImeTop - mStartImeTop);
            // Make sure to keep at least 30% visible for the top split.
            final int maxOffset = (int) (mBounds1.height() * ADJUSTED_SPLIT_FRACTION_MAX);
            return -Math.min(desireOffset, maxOffset);
        }

        @SplitPosition
        private int getImeTargetPosition() {
            final WindowContainerToken token = mTaskOrganizer.getImeTarget(mDisplayId);
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

                getRefBounds1(mTempRect);
                mTempRect.offset(0, mYOffsetForIme);
                t.setPosition(leash1, mTempRect.left, mTempRect.top);

                getRefBounds2(mTempRect);
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
