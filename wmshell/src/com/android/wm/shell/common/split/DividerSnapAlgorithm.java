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

import static android.view.Display.DEFAULT_DISPLAY;
import static android.view.WindowManager.DOCKED_LEFT;
import static android.view.WindowManager.DOCKED_RIGHT;

import static com.android.wm.shell.common.split.SplitSpec.DISMISS_TARGETS;
import static com.android.wm.shell.common.split.SplitSpec.ONE_TARGET;
import static com.android.wm.shell.common.split.SplitSpec.THREE_TARGETS_ONSCREEN;
import static com.android.wm.shell.shared.split.SplitScreenConstants.SNAP_TO_2_50_50;
import static com.android.wm.shell.shared.split.SplitScreenConstants.SNAP_TO_END_AND_DISMISS;
import static com.android.wm.shell.shared.split.SplitScreenConstants.SNAP_TO_MINIMIZE;
import static com.android.wm.shell.shared.split.SplitScreenConstants.SNAP_TO_NONE;
import static com.android.wm.shell.shared.split.SplitScreenConstants.SNAP_TO_START_AND_DISMISS;
import static com.android.wm.shell.shared.split.SplitScreenConstants.SnapPosition;

import android.content.res.Resources;
import android.graphics.Rect;
import android.window.DesktopExperienceFlags;

import androidx.annotation.Nullable;

import com.android.mechanics.spec.MotionSpec;
import com.android.wm.shell.Flags;

import java.util.ArrayList;
import java.util.List;
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
    static final int SNAP_MODE_16_9 = 0;

    /**
     * 3 snap targets: fixed ratio, 1:1, (1 - fixed ratio)
     */
    static final int SNAP_FIXED_RATIO = 1;

    /**
     * 1 snap target: 1:1
     */
    static final int SNAP_ONLY_1_1 = 2;

    /**
     * 1 snap target: minimized height, (1 - minimized height)
     */
    static final int SNAP_MODE_MINIMIZED = 3;
    /**
     * A mode that has 5 split screen snap points on smaller devices (10:90, 33:66, 50:50, 66:33,
     * 90:10). Except if it's *too* small, then it will only have 3 (10:90, 50:50, 90:10). Larger
     * devices (tablet size) will also have 3 (33:66, 50:50, 66:33).
     */
    static final int SNAP_FLEXIBLE_HYBRID = 5;

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

    /**
     * The first snap target, counting from the top/left, and not including dismiss targets.
     * On modes with 5 snap targets, this will refer to the 10% target.
     * On modes with 3 snap targets, this will refer to the 10% or 33% target.
     * On modes with only 1 target, it will refer to the middle target.
     */
    private final SnapTarget mFirstSplitTarget;
    /**
     * The second snap target, counting from the top/left, and not including dismiss targets.
     * On modes with 5 snap targets, this will refer to the 33% target.
     * On modes with 3 snap targets, this will refer to the middle target.
     * On modes with only 1 target, it will also refer to the middle target.
     */
    private final SnapTarget mSecondSplitTarget;
    /**
     * The last snap target, counting from the top/left, and not including dismiss targets.
     * On modes with 5 snap targets, this will refer to the 90% target.
     * On modes with 3 snap targets, this will refer to the 66% or 90% target.
     * On modes with only 1 target, it will refer to the middle target.
     */
    private final SnapTarget mLastSplitTarget;
    /**
     * The second-last snap target, counting from the top/left, and not including dismiss targets.
     * On modes with 5 snap targets, this will refer to the 66% target.
     * On modes with 3 snap targets, this will refer to the middle target.
     * On modes with only 1 target, it will also refer to the middle target.
     */
    private final SnapTarget mSecondLastSplitTarget;

    private final SnapTarget mDismissStartTarget;
    private final SnapTarget mDismissEndTarget;
    private final SnapTarget mMiddleTarget;

    /** A spec used for "magnetic snap" user-controlled movement. Lazy-initialized, {@code null} if
     * the flex split flag is not enabled
     */
    @Nullable
    private MotionSpec mMotionSpec;

    public DividerSnapAlgorithm(Resources res, int displayWidth, int displayHeight, int dividerSize,
            boolean isLeftRightSplit, Rect insets, Rect pinnedTaskbarInsets, int dockSide,
            int displayId) {
        this(res, displayWidth, displayHeight, dividerSize, isLeftRightSplit, insets,
                pinnedTaskbarInsets, dockSide, displayId, false /* minimized */,
                true /* resizable */);
    }

    public DividerSnapAlgorithm(Resources res, int displayWidth, int displayHeight, int dividerSize,
            boolean isLeftRightSplit, Rect insets, Rect pinnedTaskbarInsets, int dockSide,
            int displayId, boolean isMinimizedMode, boolean isHomeResizable) {
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
        final boolean enableNonDefaultDisplaySplit =
                DesktopExperienceFlags.ENABLE_NON_DEFAULT_DISPLAY_SPLIT.isTrue();
        if (Flags.enableFlexibleTwoAppSplit()
                && (!enableNonDefaultDisplaySplit || displayId == DEFAULT_DISPLAY)) {
            mSnapMode = SNAP_FLEXIBLE_HYBRID;
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
        mMiddleTarget = mTargets.get(mTargets.size() / 2);
        mMiddleTarget.isMiddleTarget = true;
        mDismissStartTarget = mTargets.get(0);
        mFirstSplitTarget = mTargets.get(1);
        if (mTargets.size() == ONE_TARGET.size() + DISMISS_TARGETS.size()) {
            mSecondSplitTarget = mSecondLastSplitTarget = null;
        } else if (mTargets.size() == THREE_TARGETS_ONSCREEN.size() + DISMISS_TARGETS.size()) {
            mSecondSplitTarget = mSecondLastSplitTarget = mMiddleTarget;
        } else {
            mSecondSplitTarget = mTargets.get(2);
            mSecondLastSplitTarget = mTargets.get(mTargets.size() - 3);
        }
        mLastSplitTarget = mTargets.get(mTargets.size() - 2);
        mDismissEndTarget = mTargets.get(mTargets.size() - 1);
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
            return Flags.enableFlexibleTwoAppSplit() ? snapToPrev(position) : mFirstSplitTarget;
        } else {
            return Flags.enableFlexibleTwoAppSplit() ? snapToNext(position) : mLastSplitTarget;
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

    public SnapTarget getSecondSplitTarget() {
        return mSecondSplitTarget;
    }

    public SnapTarget getSecondLastSplitTarget() {
        return mSecondLastSplitTarget;
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

    /**
     * From the given position, returns the closest SnapTarget on the left/top side. If there is
     * no such target, return the left/top-most target.
     */
    private SnapTarget snapToPrev(int position) {
        // Iterate backwards until we reach the first target "smaller" than the given position.
        for (int i = mTargets.size() - 1; i >= 0; i--) {
            SnapTarget currentTarget = mTargets.get(i);
            if (currentTarget.getPosition() < position) {
                return currentTarget;
            }
        }
        return mDismissStartTarget;
    }

    /**
     * From the given position, returns the closest SnapTarget on the right/bottom side. If there is
     * no such target, return the right/bottom-most target.
     */
    private SnapTarget snapToNext(int position) {
        // Iterate until we reach the first target "larger" than the given position.
        for (int i = 0; i < mTargets.size(); i++) {
            SnapTarget currentTarget = mTargets.get(i);
            if (currentTarget.getPosition() > position) {
                return currentTarget;
            }
        }
        return mDismissEndTarget;
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
                addRatio16_9Targets(dividerMax);
                break;
            case SNAP_FIXED_RATIO:
                addFixedDivisionTargets(dividerMax);
                break;
            case SNAP_ONLY_1_1:
                mTargets.add(new SnapTarget(getMiddleTargetPos(), SNAP_TO_2_50_50));
                break;
            case SNAP_MODE_MINIMIZED:
                addMinimizedTarget(mDockSide);
                break;
            case SNAP_FLEXIBLE_HYBRID:
                addFlexHybridSplitTargets(dividerMax);
                break;
        }
        mTargets.add(new SnapTarget(dividerMax, SNAP_TO_END_AND_DISMISS, 0.35f));
    }

    /**
     * Adds the non-dismissing snap targets (i.e. not the dismiss targets on the screen edges).
     *
     * @param positions The int positions of each non-dismissing snap target. (i.e. has size 3 for a
     *                  3-target layout, and size 5 for a 5-target layout.) Should always be in
     *                  ascending order.
     */
    private void addNonDismissingTargets(List<Integer> positions, List<Integer> targetSpec) {
        if (positions.size() != targetSpec.size()) {
            throw new IllegalStateException("unexpected number of snap positions");
        }

        // Iterate through the spec, adding a target for each.
        for (int i = 0; i < targetSpec.size(); i++) {
            mTargets.add(new SnapTarget(positions.get(i), targetSpec.get(i)));
        }
    }

    private void addFixedDivisionTargets(int dividerMax) {
        int start = mIsLeftRightSplit ? mInsets.left : mInsets.top;
        int end = mIsLeftRightSplit
                ? mDisplayWidth - mInsets.right
                : mDisplayHeight - mInsets.bottom;

        int size = (int) (mFixedRatio * (end - start)) - mDividerSize / 2;
        if (mCalculateRatiosBasedOnAvailableSpace) {
            size = Math.max(size, mMinimalSizeResizableTask);
        }

        int topPosition = start + size;
        int bottomPosition = end - size - mDividerSize;

        // Get the desired layout for our current device/display/rotation.
        boolean bigEnoughFor33 = size >= mMinimalSizeResizableTask;
        List<Integer> targetSpec = SplitSpec.getSnapTargetLayout(SNAP_FIXED_RATIO,
                areOffscreenRatiosSupported(), bigEnoughFor33);

        if (bigEnoughFor33) {
            // Add 3 targets
            addNonDismissingTargets(List.of(topPosition, getMiddleTargetPos(), bottomPosition),
                    targetSpec);
        } else {
            // Add 1 target
            addNonDismissingTargets(List.of(getMiddleTargetPos()), targetSpec);
        }
    }

    private void addFlexHybridSplitTargets(int dividerMax) {
        int start = mIsLeftRightSplit ? mInsets.left : mInsets.top;
        int end = mIsLeftRightSplit
                ? mDisplayWidth - mInsets.right
                : mDisplayHeight - mInsets.bottom;
        int pinnedTaskbarShiftStart = mIsLeftRightSplit
                ? mPinnedTaskbarInsets.left : mPinnedTaskbarInsets.top;
        int pinnedTaskbarShiftEnd = mIsLeftRightSplit
                ? mPinnedTaskbarInsets.right : mPinnedTaskbarInsets.bottom;

        // If offscreen apps are supported, we are looking to add 5 targets.
        if (areOffscreenRatiosSupported()) {
            // Find the desired sizes for a 10% app and a 33% app.
            float ratio10 = SplitSpec.OFFSCREEN_ASYMMETRIC_RATIO;
            float ratio33 = SplitSpec.ONSCREEN_ONLY_ASYMMETRIC_RATIO;
            // Insets affect where we want to put the snap targets. For the 10% target: If there are
            // insets that interfere with the divider position (visually or blocking touch targets),
            // we make the size bigger by that amount to compensate. This happens with an enabled
            // pinned taskbar, 3-button nav (both create an opaque bar at bottom) or status bar
            // (blocks touch targets at top).
            int extraSpaceFor10 = IntStream.of(
                    getStartInset(), getEndInset(), pinnedTaskbarShiftStart, pinnedTaskbarShiftEnd
            ).max().getAsInt();
            int size10 = (int) (ratio10 * dividerMax) + extraSpaceFor10 - mDividerSize / 2;
            // For the 33% target, we bake the insets into the position calculation below.
            int size33 = (int) (ratio33 * (end - start)) - mDividerSize / 2;
            // If the resulting size is too small, bump it up to the minimum required size.
            size33 = Math.max(size33, mMinimalSizeResizableTask);

            int leftTop10Position = size10;
            int rightBottom10Position = dividerMax - size10 - mDividerSize;
            int leftTop33Position = start + size33;
            int rightBottom33Position = end - size33 - mDividerSize;

            // Get the desired layout for our current device/display/rotation.
            List<Integer> targetSpec = SplitSpec.getSnapTargetLayout(SNAP_FLEXIBLE_HYBRID,
                    areOffscreenRatiosSupported(), true /* bigEnoughFor33 */);

            // Add 5 targets
            addNonDismissingTargets(List.of(leftTop10Position, leftTop33Position,
                            getMiddleTargetPos(), rightBottom33Position, rightBottom10Position),
                    targetSpec);
        } else {
            // If offscreen apps are not supported, just add the regular 1-3 targets.
            float ratio = SplitSpec.ONSCREEN_ONLY_ASYMMETRIC_RATIO;

            // The intended size of the smaller app, in pixels
            int size = (int) (ratio * (end - start)) - mDividerSize / 2;
            // If the resulting size is too small, bump it up to the minimum required size.
            size = Math.max(size, mMinimalSizeResizableTask);

            int leftTopPosition = start + size;
            int rightBottomPosition = end - size - mDividerSize;

            // Get the desired layout for our current device/display/rotation.
            List<Integer> targetSpec = SplitSpec.getSnapTargetLayout(SNAP_FLEXIBLE_HYBRID,
                    areOffscreenRatiosSupported(), true /* bigEnoughFor33 */);

            // Add 3 targets
            addNonDismissingTargets(List.of(leftTopPosition, getMiddleTargetPos(),
                    rightBottomPosition), targetSpec);
        }
    }

    private void addRatio16_9Targets(int dividerMax) {
        int start = mIsLeftRightSplit ? mInsets.left : mInsets.top;
        int end = mIsLeftRightSplit
                ? mDisplayWidth - mInsets.right
                : mDisplayHeight - mInsets.bottom;
        int startOther = mIsLeftRightSplit ? mInsets.top : mInsets.left;
        int endOther = mIsLeftRightSplit
                ? mDisplayHeight - mInsets.bottom
                : mDisplayWidth - mInsets.right;
        float size = 9.0f / 16.0f * (endOther - startOther);
        int sizeInt = (int) Math.floor(size);
        int topPosition = start + sizeInt;
        int bottomPosition = end - sizeInt - mDividerSize;

        // Get the desired layout for our current device/display/rotation.
        boolean bigEnoughFor33 = sizeInt >= mMinimalSizeResizableTask;
        List<Integer> targetSpec = SplitSpec.getSnapTargetLayout(SNAP_MODE_16_9,
                areOffscreenRatiosSupported(), bigEnoughFor33);

        if (bigEnoughFor33) {
            // Add 3 targets
            addNonDismissingTargets(List.of(topPosition, getMiddleTargetPos(), bottomPosition),
                    targetSpec);
        } else {
            // Add 1 target
            addNonDismissingTargets(List.of(getMiddleTargetPos()), targetSpec);
        }
    }

    /** Calculates the screen position of the middle snap target. */
    private int getMiddleTargetPos() {
        return DockedDividerUtils.calculateMiddlePosition(mIsLeftRightSplit, mInsets, mDisplayWidth,
                mDisplayHeight, mDividerSize);
    }

    private void addMinimizedTarget(int dockedSide) {
        // In portrait offset the position by the statusbar height, in landscape add the statusbar
        // height as well to match portrait offset
        int position = mTaskHeightInMinimizedMode + mInsets.top;
        if (mIsLeftRightSplit) {
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

    public boolean isSecondSplitTargetAvailable() {
        return mSecondSplitTarget != mMiddleTarget && mSecondSplitTarget != null;
    }

    public boolean isSecondLastSplitTargetAvailable() {
        return mSecondLastSplitTarget != mMiddleTarget && mSecondLastSplitTarget != null;
    }

    /**
     * Finds the {@link SnapPosition} nearest to the given position.
     */
    public int calculateNearestSnapPosition(int currentPosition) {
        return snap(currentPosition, /* hardDismiss */ true).snapPosition;
    }

    /**
     * Gets the on-screen position of a SnapTarget matching the provided @SnapPosition, if one
     * exists. If not, return null.
     */
    public Integer getPositionBySnapPosition(@SnapPosition int snapPosition) {
        for (SnapTarget t : mTargets) {
            if (t.snapPosition == snapPosition) {
                return t.getPosition();
            }
        }
        return null;
    }

    @Nullable
    public MotionSpec getMotionSpec(Resources resources) {
        if (Flags.enableFlexibleTwoAppSplit() && mMotionSpec == null) {
            mMotionSpec = MagneticDividerUtils.generateMotionSpec(mTargets, resources);
        }
        return mMotionSpec;
    }

    public int getSnapMode() {
        return mSnapMode;
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
