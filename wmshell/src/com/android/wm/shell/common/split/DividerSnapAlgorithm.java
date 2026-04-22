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

package com.android.wm.shell.common.split;

import static android.view.WindowManager.DOCKED_LEFT;
import static android.view.WindowManager.DOCKED_RIGHT;

import static com.android.wm.shell.shared.split.SplitScreenConstants.SNAP_TO_2_10_90;
import static com.android.wm.shell.shared.split.SplitScreenConstants.SNAP_TO_2_33_66;
import static com.android.wm.shell.shared.split.SplitScreenConstants.SNAP_TO_2_50_50;
import static com.android.wm.shell.shared.split.SplitScreenConstants.SNAP_TO_2_66_33;
import static com.android.wm.shell.shared.split.SplitScreenConstants.SNAP_TO_2_90_10;
import static com.android.wm.shell.shared.split.SplitScreenConstants.SNAP_TO_END_AND_DISMISS;
import static com.android.wm.shell.shared.split.SplitScreenConstants.SNAP_TO_MINIMIZE;
import static com.android.wm.shell.shared.split.SplitScreenConstants.SNAP_TO_NONE;
import static com.android.wm.shell.shared.split.SplitScreenConstants.SNAP_TO_START_AND_DISMISS;
import static com.android.wm.shell.shared.split.SplitScreenConstants.SnapPosition;

import android.content.res.Resources;
import android.graphics.Rect;

import androidx.annotation.Nullable;

import com.android.mechanics.spec.MotionSpec;
import com.android.wm.shell.Flags;
import com.android.wm.shell.shared.split.SplitScreenConstants.PersistentSnapPosition;

import java.util.ArrayList;
import java.util.stream.IntStream;

/**
 * Calculates the snap targets and the snap position given a position and a velocity. All positions
 * here are to be interpreted as the left/top edge of the divider rectangle.
 *
 * @hide
 */
public class DividerSnapAlgorithm {

    private static final int MIN_FLING_VELOCITY_DP_PER_SECOND = 400;
    private static final int MIN_DISMISS_VELOCITY_DP_PER_SECOND = 600;

    /**
     * 3 snap targets: left/top has 16:9 ratio (for videos), 1:1, and right/bottom has 16:9 ratio
     */
    private static final int SNAP_MODE_16_9 = 0;

    /**
     * 3 snap targets: fixed ratio, 1:1, (1 - fixed ratio)
     */
    private static final int SNAP_FIXED_RATIO = 1;

    /**
     * 1 snap target: 1:1
     */
    private static final int SNAP_ONLY_1_1 = 2;

    /**
     * 1 snap target: minimized height, (1 - minimized height)
     */
    private static final int SNAP_MODE_MINIMIZED = 3;

    /**
     * A mode where apps can be "flexibly offscreen" on smaller displays.
     */
    private static final int SNAP_FLEXIBLE_SPLIT = 4;

    private final float mMinFlingVelocityPxPerSecond;
    private final float mMinDismissVelocityPxPerSecond;
    private final int mDisplayWidth;
    private final int mDisplayHeight;
    private final int mDividerSize;
    private final ArrayList<SnapTarget> mTargets = new ArrayList<>();
    private final Rect mInsets = new Rect();
    private final Rect mPinnedTaskbarInsets = new Rect();
    private final int mSnapMode;
    private final boolean mFreeSnapMode;
    private final int mMinimalSizeResizableTask;
    private final int mTaskHeightInMinimizedMode;
    private final float mFixedRatio;
    /** Allows split ratios to calculated dynamically instead of using {@link #mFixedRatio}. */
    private final boolean mCalculateRatiosBasedOnAvailableSpace;
    /** Allows split ratios that go offscreen (a.k.a. "flexible split") */
    private final boolean mAllowOffscreenRatios;
    private final boolean mIsLeftRightSplit;
    /** In SNAP_MODE_MINIMIZED, the side of the screen on which an app will "dock" when minimized */
    private final int mDockSide;

    /** The first target which is still splitting the screen */
    private final SnapTarget mFirstSplitTarget;

    /** The last target which is still splitting the screen */
    private final SnapTarget mLastSplitTarget;

    private final SnapTarget mDismissStartTarget;
    private final SnapTarget mDismissEndTarget;
    private final SnapTarget mMiddleTarget;

    /** A spec used for "magnetic snap" user-controlled movement. */
    private final MotionSpec mMotionSpec;

    public DividerSnapAlgorithm(Resources res, int displayWidth, int displayHeight, int dividerSize,
            boolean isLeftRightSplit, Rect insets, Rect pinnedTaskbarInsets, int dockSide) {
        this(res, displayWidth, displayHeight, dividerSize, isLeftRightSplit, insets,
                pinnedTaskbarInsets, dockSide, false /* minimized */, true /* resizable */);
    }

    public DividerSnapAlgorithm(Resources res, int displayWidth, int displayHeight, int dividerSize,
            boolean isLeftRightSplit, Rect insets, Rect pinnedTaskbarInsets, int dockSide,
            boolean isMinimizedMode, boolean isHomeResizable) {
        mMinFlingVelocityPxPerSecond =
                MIN_FLING_VELOCITY_DP_PER_SECOND * res.getDisplayMetrics().density;
        mMinDismissVelocityPxPerSecond =
                MIN_DISMISS_VELOCITY_DP_PER_SECOND * res.getDisplayMetrics().density;
        mDividerSize = dividerSize;
        mDisplayWidth = displayWidth;
        mDisplayHeight = displayHeight;
        mIsLeftRightSplit = isLeftRightSplit;
        mDockSide = dockSide;
        mInsets.set(insets);
        mPinnedTaskbarInsets.set(pinnedTaskbarInsets);
        if (Flags.enableFlexibleTwoAppSplit()) {
            mSnapMode = SNAP_FLEXIBLE_SPLIT;
        } else {
            // Set SNAP_MODE_MINIMIZED, SNAP_MODE_16_9, or SNAP_FIXED_RATIO depending on config
            mSnapMode = isMinimizedMode
                    ? SNAP_MODE_MINIMIZED
                    : res.getInteger(
                            com.android.internal.R.integer.config_dockedStackDividerSnapMode);
        }
        mFreeSnapMode = res.getBoolean(
                com.android.internal.R.bool.config_dockedStackDividerFreeSnapMode);
        mFixedRatio = res.getFraction(
                com.android.internal.R.fraction.docked_stack_divider_fixed_ratio, 1, 1);
        mMinimalSizeResizableTask = res.getDimensionPixelSize(
                com.android.internal.R.dimen.default_minimal_size_resizable_task);
        mCalculateRatiosBasedOnAvailableSpace = res.getBoolean(
                com.android.internal.R.bool.config_flexibleSplitRatios);
        // If this is a small screen or a foldable, use offscreen ratios
        mAllowOffscreenRatios = SplitScreenUtils.allowOffscreenRatios(res);
        mTaskHeightInMinimizedMode = isHomeResizable ? res.getDimensionPixelSize(
                com.android.internal.R.dimen.task_height_of_minimized_mode) : 0;
        calculateTargets();
        mFirstSplitTarget = mTargets.get(1);
        mLastSplitTarget = mTargets.get(mTargets.size() - 2);
        mDismissStartTarget = mTargets.get(0);
        mDismissEndTarget = mTargets.get(mTargets.size() - 1);
        mMiddleTarget = mTargets.get(mTargets.size() / 2);
        mMiddleTarget.isMiddleTarget = true;
        mMotionSpec = Flags.enableMagneticSplitDivider()
                ? MagneticDividerUtils.generateMotionSpec(mTargets, res) : null;
    }

    /**
     * @param position the top/left position of the divider
     * @param velocity current dragging velocity
     * @param hardToDismiss if set, make it a bit harder to get reach the dismiss targets
     */
    public SnapTarget calculateSnapTarget(int position, float velocity, boolean hardToDismiss) {
        if (position < mFirstSplitTarget.position && velocity < -mMinDismissVelocityPxPerSecond) {
            return mDismissStartTarget;
        }
        if (position > mLastSplitTarget.position && velocity > mMinDismissVelocityPxPerSecond) {
            return mDismissEndTarget;
        }
        if (Math.abs(velocity) < mMinFlingVelocityPxPerSecond) {
            return snap(position, hardToDismiss);
        }
        if (velocity < 0) {
            return mFirstSplitTarget;
        } else {
            return mLastSplitTarget;
        }
    }

    public SnapTarget calculateNonDismissingSnapTarget(int position) {
        SnapTarget target = snap(position, false /* hardDismiss */);
        if (target == mDismissStartTarget) {
            return mFirstSplitTarget;
        } else if (target == mDismissEndTarget) {
            return mLastSplitTarget;
        } else {
            return target;
        }
    }

    /**
     * Gets the SnapTarget corresponding to the given {@link SnapPosition}, or null if no such
     * SnapTarget exists.
     */
    @Nullable
    public SnapTarget findSnapTarget(@SnapPosition int snapPosition) {
        for (SnapTarget t : mTargets) {
            if (t.snapPosition == snapPosition) {
                return t;
            }
        }

        return null;
    }

    public float calculateDismissingFraction(int position) {
        if (position < mFirstSplitTarget.position) {
            return 1f - (float) (position - getStartInset())
                    / (mFirstSplitTarget.position - getStartInset());
        } else if (position > mLastSplitTarget.position) {
            return (float) (position - mLastSplitTarget.position)
                    / (mDismissEndTarget.position - mLastSplitTarget.position - mDividerSize);
        }
        return 0f;
    }

    public SnapTarget getFirstSplitTarget() {
        return mFirstSplitTarget;
    }

    public SnapTarget getLastSplitTarget() {
        return mLastSplitTarget;
    }

    public SnapTarget getDismissStartTarget() {
        return mDismissStartTarget;
    }

    public SnapTarget getDismissEndTarget() {
        return mDismissEndTarget;
    }

    private int getStartInset() {
        if (mIsLeftRightSplit) {
            return mInsets.left;
        } else {
            return mInsets.top;
        }
    }

    private int getEndInset() {
        if (mIsLeftRightSplit) {
            return mInsets.right;
        } else {
            return mInsets.bottom;
        }
    }

    private boolean shouldApplyFreeSnapMode(int position) {
        if (!mFreeSnapMode) {
            return false;
        }
        if (!isFirstSplitTargetAvailable() || !isLastSplitTargetAvailable()) {
            return false;
        }
        return mFirstSplitTarget.position < position && position < mLastSplitTarget.position;
    }

    /** Returns if we are currently on a device/screen that supports split apps going offscreen. */
    public boolean areOffscreenRatiosSupported() {
        return mAllowOffscreenRatios;
    }

    private SnapTarget snap(int position, boolean hardDismiss) {
        if (shouldApplyFreeSnapMode(position)) {
            return new SnapTarget(position, SNAP_TO_NONE);
        }
        int minIndex = -1;
        float minDistance = Float.MAX_VALUE;
        int size = mTargets.size();
        for (int i = 0; i < size; i++) {
            SnapTarget target = mTargets.get(i);
            float distance = Math.abs(position - target.position);
            if (hardDismiss) {
                distance /= target.distanceMultiplier;
            }
            if (distance < minDistance) {
                minIndex = i;
                minDistance = distance;
            }
        }
        return mTargets.get(minIndex);
    }

    private void calculateTargets() {
        mTargets.clear();
        int dividerMax = mIsLeftRightSplit
                ? mDisplayWidth
                : mDisplayHeight;
        int startPos = -mDividerSize;
        if (mDockSide == DOCKED_RIGHT) {
            startPos += mInsets.left;
        }
        mTargets.add(new SnapTarget(startPos, SNAP_TO_START_AND_DISMISS, 0.35f));
        switch (mSnapMode) {
            case SNAP_MODE_16_9:
                addRatio16_9Targets(mIsLeftRightSplit, dividerMax);
                break;
            case SNAP_FIXED_RATIO:
                addFixedDivisionTargets(mIsLeftRightSplit, dividerMax);
                break;
            case SNAP_ONLY_1_1:
                addMiddleTarget(mIsLeftRightSplit);
                break;
            case SNAP_MODE_MINIMIZED:
                addMinimizedTarget(mIsLeftRightSplit, mDockSide);
                break;
            case SNAP_FLEXIBLE_SPLIT:
                addFlexSplitTargets(mIsLeftRightSplit, dividerMax);
                break;
        }
        mTargets.add(new SnapTarget(dividerMax, SNAP_TO_END_AND_DISMISS, 0.35f));
    }

    private void addNonDismissingTargets(boolean isLeftRightSplit, int topPosition,
            int bottomPosition, int dividerMax) {
        @PersistentSnapPosition int firstTarget =
                areOffscreenRatiosSupported() ? SNAP_TO_2_10_90 : SNAP_TO_2_33_66;
        @PersistentSnapPosition int lastTarget =
                areOffscreenRatiosSupported() ? SNAP_TO_2_90_10 : SNAP_TO_2_66_33;
        maybeAddTarget(topPosition, topPosition - getStartInset(), firstTarget);
        addMiddleTarget(isLeftRightSplit);
        maybeAddTarget(bottomPosition,
                dividerMax - getEndInset() - (bottomPosition + mDividerSize), lastTarget);
    }

    private void addFixedDivisionTargets(boolean isLeftRightSplit, int dividerMax) {
        int start = isLeftRightSplit ? mInsets.left : mInsets.top;
        int end = isLeftRightSplit
                ? mDisplayWidth - mInsets.right
                : mDisplayHeight - mInsets.bottom;

        int size = (int) (mFixedRatio * (end - start)) - mDividerSize / 2;
        if (mCalculateRatiosBasedOnAvailableSpace) {
            size = Math.max(size, mMinimalSizeResizableTask);
        }

        int topPosition = start + size;
        int bottomPosition = end - size - mDividerSize;
        addNonDismissingTargets(isLeftRightSplit, topPosition, bottomPosition, dividerMax);
    }

    private void addFlexSplitTargets(boolean isLeftRightSplit, int dividerMax) {
        int start = 0;
        int end = isLeftRightSplit ? mDisplayWidth : mDisplayHeight;
        int pinnedTaskbarShiftStart = isLeftRightSplit
                ? mPinnedTaskbarInsets.left : mPinnedTaskbarInsets.top;
        int pinnedTaskbarShiftEnd = isLeftRightSplit
                ? mPinnedTaskbarInsets.right : mPinnedTaskbarInsets.bottom;

        float ratio = areOffscreenRatiosSupported()
                ? SplitSpec.OFFSCREEN_ASYMMETRIC_RATIO
                : SplitSpec.ONSCREEN_ONLY_ASYMMETRIC_RATIO;

        // The intended size of the smaller app, in pixels
        int size = (int) (ratio * (end - start)) - mDividerSize / 2;

        // If there are insets that interfere with the smaller app (visually or blocking touch
        // targets), make the smaller app bigger by that amount to compensate. This applies to
        // pinned taskbar, 3-button nav (both create an opaque bar at bottom) and status bar (blocks
        // touch targets at top).
        int extraSpace = IntStream.of(
                getStartInset(), getEndInset(), pinnedTaskbarShiftStart, pinnedTaskbarShiftEnd
        ).max().getAsInt();

        int leftTopPosition = start + extraSpace + size;
        int rightBottomPosition = end - extraSpace - size - mDividerSize;
        addNonDismissingTargets(isLeftRightSplit, leftTopPosition, rightBottomPosition, dividerMax);
    }

    private void addRatio16_9Targets(boolean isLeftRightSplit, int dividerMax) {
        int start = isLeftRightSplit ? mInsets.left : mInsets.top;
        int end = isLeftRightSplit
                ? mDisplayWidth - mInsets.right
                : mDisplayHeight - mInsets.bottom;
        int startOther = isLeftRightSplit ? mInsets.top : mInsets.left;
        int endOther = isLeftRightSplit
                ? mDisplayHeight - mInsets.bottom
                : mDisplayWidth - mInsets.right;
        float size = 9.0f / 16.0f * (endOther - startOther);
        int sizeInt = (int) Math.floor(size);
        int topPosition = start + sizeInt;
        int bottomPosition = end - sizeInt - mDividerSize;
        addNonDismissingTargets(isLeftRightSplit, topPosition, bottomPosition, dividerMax);
    }

    /**
     * Adds a target at {@param position} but only if the area with size of {@param smallerSize}
     * meets the minimal size requirement.
     */
    private void maybeAddTarget(int position, int smallerSize, @SnapPosition int snapPosition) {
        if (smallerSize >= mMinimalSizeResizableTask || areOffscreenRatiosSupported()) {
            mTargets.add(new SnapTarget(position, snapPosition));
        }
    }

    private void addMiddleTarget(boolean isLeftRightSplit) {
        int position = DockedDividerUtils.calculateMiddlePosition(isLeftRightSplit,
                mInsets, mDisplayWidth, mDisplayHeight, mDividerSize);
        mTargets.add(new SnapTarget(position, SNAP_TO_2_50_50));
    }

    private void addMinimizedTarget(boolean isLeftRightSplit, int dockedSide) {
        // In portrait offset the position by the statusbar height, in landscape add the statusbar
        // height as well to match portrait offset
        int position = mTaskHeightInMinimizedMode + mInsets.top;
        if (isLeftRightSplit) {
            if (dockedSide == DOCKED_LEFT) {
                position += mInsets.left;
            } else if (dockedSide == DOCKED_RIGHT) {
                position = mDisplayWidth - position - mInsets.right - mDividerSize;
            }
        }
        mTargets.add(new SnapTarget(position, SNAP_TO_MINIMIZE));
    }

    public SnapTarget getMiddleTarget() {
        return mMiddleTarget;
    }

    /**
     * @return whether or not there are more than 1 split targets that do not include the two
     * dismiss targets, used in deciding to display the middle target for accessibility
     */
    public boolean showMiddleSplitTargetForAccessibility() {
        return (mTargets.size() - 2) > 1;
    }

    public boolean isFirstSplitTargetAvailable() {
        return mFirstSplitTarget != mMiddleTarget;
    }

    public boolean isLastSplitTargetAvailable() {
        return mLastSplitTarget != mMiddleTarget;
    }

    /**
     * Finds the {@link SnapPosition} nearest to the given position.
     */
    public int calculateNearestSnapPosition(int currentPosition) {
        return snap(currentPosition, /* hardDismiss */ true).snapPosition;
    }

    public MotionSpec getMotionSpec() {
        return mMotionSpec;
    }

    /**
     * An object, calculated at boot time, representing a legal position for the split screen
     * divider (i.e. the divider can be dragged to this spot).
     */
    public static class SnapTarget {
        /** Position of this snap target. The right/bottom edge of the top/left task snaps here. */
        public final int position;

        /**
         * An int (enum) describing the placement of the divider in this snap target.
         */
        public final @SnapPosition int snapPosition;

        public boolean isMiddleTarget;

        /**
         * Multiplier used to calculate distance to snap position. The lower this value, the harder
         * it's to snap on this target
         */
        private final float distanceMultiplier;

        public SnapTarget(int position, @SnapPosition int snapPosition) {
            this(position, snapPosition, 1f);
        }

        public SnapTarget(int position, @SnapPosition int snapPosition,
                float distanceMultiplier) {
            this.position = position;
            this.snapPosition = snapPosition;
            this.distanceMultiplier = distanceMultiplier;
        }

        public int getPosition() {
            return position;
        }
    }
}
