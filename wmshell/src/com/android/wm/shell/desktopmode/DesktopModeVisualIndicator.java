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

package com.android.wm.shell.desktopmode;

import static android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN;
import static android.app.WindowConfiguration.WINDOWING_MODE_MULTI_WINDOW;

import static com.android.internal.policy.SystemBarUtils.getDesktopViewAppHeaderHeightPx;
import static com.android.wm.shell.desktopmode.DesktopModeVisualIndicator.IndicatorType.NO_INDICATOR;
import static com.android.wm.shell.desktopmode.DesktopModeVisualIndicator.IndicatorType.TO_BUBBLE_LEFT_INDICATOR;
import static com.android.wm.shell.desktopmode.DesktopModeVisualIndicator.IndicatorType.TO_BUBBLE_RIGHT_INDICATOR;
import static com.android.wm.shell.desktopmode.DesktopModeVisualIndicator.IndicatorType.TO_DESKTOP_INDICATOR;
import static com.android.wm.shell.desktopmode.DesktopModeVisualIndicator.IndicatorType.TO_FULLSCREEN_INDICATOR;
import static com.android.wm.shell.desktopmode.DesktopModeVisualIndicator.IndicatorType.TO_SPLIT_LEFT_INDICATOR;
import static com.android.wm.shell.desktopmode.DesktopModeVisualIndicator.IndicatorType.TO_SPLIT_RIGHT_INDICATOR;
import static com.android.wm.shell.shared.ShellSharedConstants.SMALL_TABLET_MAX_EDGE_DP;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.ActivityManager;
import android.content.Context;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.Region;
import android.util.Pair;
import android.view.Display;
import android.view.SurfaceControl;
import android.window.DesktopExperienceFlags;
import android.window.DesktopModeFlags;

import androidx.annotation.VisibleForTesting;

import com.android.internal.policy.SystemBarUtils;
import com.android.wm.shell.R;
import com.android.wm.shell.RootTaskDisplayAreaOrganizer;
import com.android.wm.shell.common.DisplayController;
import com.android.wm.shell.common.DisplayLayout;
import com.android.wm.shell.common.ShellExecutor;
import com.android.wm.shell.common.SyncTransactionQueue;
import com.android.wm.shell.common.split.SplitScreenUtils;
import com.android.wm.shell.shared.annotations.ShellDesktopThread;
import com.android.wm.shell.shared.annotations.ShellMainThread;
import com.android.wm.shell.shared.bubbles.BubbleAnythingFlagHelper;
import com.android.wm.shell.shared.bubbles.BubbleDropTargetBoundsProvider;
import com.android.wm.shell.windowdecor.tiling.SnapEventHandler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Animated visual indicator for Desktop Mode windowing transitions.
 */
public class DesktopModeVisualIndicator {
    public enum IndicatorType {
        /** To be used when we don't want to indicate any transition */
        NO_INDICATOR,
        /** Indicates impending transition into desktop mode */
        TO_DESKTOP_INDICATOR,
        /** Indicates impending transition into fullscreen */
        TO_FULLSCREEN_INDICATOR,
        /** Indicates impending transition into split select on the left side */
        TO_SPLIT_LEFT_INDICATOR,
        /** Indicates impending transition into split select on the right side */
        TO_SPLIT_RIGHT_INDICATOR,
        /** Indicates impending transition into bubble on the left side */
        TO_BUBBLE_LEFT_INDICATOR,
        /** Indicates impending transition into bubble on the right side */
        TO_BUBBLE_RIGHT_INDICATOR
    }

    /**
     * The conditions surrounding the drag event that led to the indicator's creation.
     */
    public enum DragStartState {
        /** The indicator is resulting from a freeform task drag. */
        FROM_FREEFORM,
        /** The indicator is resulting from a split screen task drag */
        FROM_SPLIT,
        /** The indicator is resulting from a fullscreen task drag */
        FROM_FULLSCREEN,
        /** The indicator is resulting from an Intent generated during a drag-and-drop event */
        DRAGGED_INTENT;

        /**
         * Get the {@link DragStartState} of a drag event based on the windowing mode of the task.
         * Note that DRAGGED_INTENT will be specified by the caller if needed and not returned
         * here.
         */
        public static DesktopModeVisualIndicator.DragStartState getDragStartState(
                ActivityManager.RunningTaskInfo taskInfo
        ) {
            if (taskInfo.getWindowingMode() == WINDOWING_MODE_FULLSCREEN) {
                return FROM_FULLSCREEN;
            } else if (taskInfo.getWindowingMode() == WINDOWING_MODE_MULTI_WINDOW) {
                return FROM_SPLIT;
            } else if (taskInfo.isFreeform()) {
                return FROM_FREEFORM;
            } else {
                return null;
            }
        }

        private static boolean isDragToDesktopStartState(DragStartState startState) {
            return startState == FROM_FULLSCREEN || startState == FROM_SPLIT;
        }
    }

    private final VisualIndicatorViewContainer mVisualIndicatorViewContainer;

    private final Context mContext;
    private final DisplayController mDisplayController;
    private final ActivityManager.RunningTaskInfo mTaskInfo;

    private IndicatorType mCurrentType;
    private final DragStartState mDragStartState;
    private final SnapEventHandler mSnapEventHandler;

    private final boolean mUseSmallTabletRegions;
    private boolean mIsReleased = false;
    /**
     * Ordered list of {@link Rect} zones that we will match an input coordinate against.
     * List is traversed from first to last element. The first rect that contains the input event
     * will be used and the matching {@link IndicatorType} is returned.
     * Empty rect matches all.
     */
    private final List<Pair<Rect, IndicatorType>> mSortedRegions;

    public DesktopModeVisualIndicator(@ShellDesktopThread ShellExecutor desktopExecutor,
            @ShellMainThread ShellExecutor mainExecutor,
            SyncTransactionQueue syncQueue,
            ActivityManager.RunningTaskInfo taskInfo, DisplayController displayController,
            Context context, SurfaceControl taskSurface,
            RootTaskDisplayAreaOrganizer taskDisplayAreaOrganizer,
            DragStartState dragStartState,
            @Nullable BubbleDropTargetBoundsProvider bubbleBoundsProvider,
            SnapEventHandler snapEventHandler) {
        this(desktopExecutor, mainExecutor, syncQueue, taskInfo, displayController, context,
                taskSurface, taskDisplayAreaOrganizer, dragStartState, bubbleBoundsProvider,
                snapEventHandler, useSmallTabletRegions(displayController, taskInfo),
                isLeftRightSplit(context, displayController, taskInfo));
    }

    @VisibleForTesting
    DesktopModeVisualIndicator(@ShellDesktopThread ShellExecutor desktopExecutor,
            @ShellMainThread ShellExecutor mainExecutor,
            SyncTransactionQueue syncQueue,
            ActivityManager.RunningTaskInfo taskInfo, DisplayController displayController,
            Context context, SurfaceControl taskSurface,
            RootTaskDisplayAreaOrganizer taskDisplayAreaOrganizer,
            DragStartState dragStartState,
            @Nullable BubbleDropTargetBoundsProvider bubbleBoundsProvider,
            SnapEventHandler snapEventHandler,
            boolean useSmallTabletRegions,
            boolean isLeftRightSplit) {
        SurfaceControl.Builder builder = new SurfaceControl.Builder();
        if (!DragStartState.isDragToDesktopStartState(dragStartState)
                || !DesktopModeFlags.ENABLE_VISUAL_INDICATOR_IN_TRANSITION_BUGFIX.isTrue()) {
            // In the DragToDesktop transition we attach the indicator to the transition root once
            // that is available - for all other cases attach the indicator here.
            taskDisplayAreaOrganizer.attachToDisplayArea(taskInfo.displayId, builder);
        }
        mVisualIndicatorViewContainer = new VisualIndicatorViewContainer(
                DesktopModeFlags.ENABLE_DESKTOP_INDICATOR_IN_SEPARATE_THREAD_BUGFIX.isTrue()
                        ? desktopExecutor : mainExecutor,
                mainExecutor, builder, syncQueue, bubbleBoundsProvider, snapEventHandler);
        mTaskInfo = taskInfo;
        mDisplayController = displayController;
        mContext = context;
        mCurrentType = NO_INDICATOR;
        mDragStartState = dragStartState;
        mSnapEventHandler = snapEventHandler;
        Display display = mDisplayController.getDisplay(mTaskInfo.displayId);
        DisplayLayout displayLayout = mDisplayController.getDisplayLayout(mTaskInfo.displayId);
        mVisualIndicatorViewContainer.createView(
                mContext,
                display,
                displayLayout,
                mTaskInfo,
                taskSurface
        );

        mUseSmallTabletRegions = useSmallTabletRegions;

        if (useSmallTabletRegions) {
            mSortedRegions = initSmallTabletRegions(displayLayout, isLeftRightSplit);
        } else {
            // TODO(b/401596837): add support for initializing regions for large tablets
            mSortedRegions = Collections.emptyList();
        }
    }

    private static boolean useSmallTabletRegions(DisplayController displayController,
            ActivityManager.RunningTaskInfo taskInfo) {
        if (!BubbleAnythingFlagHelper.enableBubbleToFullscreen()) {
            // Small tablet regions get enabled with bubbles feature
            return false;
        }
        Display display = displayController.getDisplay(taskInfo.displayId);
        DisplayLayout displayLayout = displayController.getDisplayLayout(taskInfo.displayId);
        if (displayLayout == null) return false;
        return displayLayout.pxToDp(display.getMaximumSizeDimension()) < SMALL_TABLET_MAX_EDGE_DP;
    }

    private static boolean isLeftRightSplit(Context context, DisplayController displayController,
            ActivityManager.RunningTaskInfo taskInfo) {
        DisplayLayout layout = displayController.getDisplayLayout(taskInfo.displayId);
        boolean landscape = layout != null && layout.isLandscape();
        boolean leftRightSplitInPortrait = SplitScreenUtils.allowLeftRightSplitInPortrait(
                context.getResources());
        return SplitScreenUtils.isLeftRightSplit(leftRightSplitInPortrait,
                /* isLargeScreen= */ true, landscape);
    }

    /** Start the fade out animation, running the callback on the main thread once it is done. */
    public void fadeOutIndicator(
            @NonNull Runnable callback) {
        mVisualIndicatorViewContainer.fadeOutIndicator(
                mDisplayController.getDisplayLayout(mTaskInfo.displayId), mCurrentType, callback,
                mTaskInfo.displayId, mSnapEventHandler
        );
    }

    /** Release the visual indicator view and its viewhost. */
    public void releaseVisualIndicator() {
        mIsReleased = true;
        mVisualIndicatorViewContainer.releaseVisualIndicator();
    }

    /** Reparent the visual indicator to {@code newParent}. */
    void reparentLeash(SurfaceControl.Transaction t, SurfaceControl newParent) {
        mVisualIndicatorViewContainer.reparentLeash(t, newParent);
    }

    /** Start the fade-in animation. */
    void fadeInIndicator() {
        if (mCurrentType == NO_INDICATOR) return;
        mVisualIndicatorViewContainer.fadeInIndicator(
                mDisplayController.getDisplayLayout(mTaskInfo.displayId), mCurrentType,
                mTaskInfo.displayId);
    }

    /**
     * Based on the coordinates of the current drag event, determine which indicator type we should
     * display, including no visible indicator, and update the indicator.
     */
    @NonNull
    IndicatorType updateIndicatorType(int displayId, PointF inputCoordinates) {
        final IndicatorType result = calculateIndicatorType(displayId, inputCoordinates);
        updateIndicatorWithType(result);
        return result;
    }

    /**
     * Based on the coordinates of the current drag event, determine which indicator type we should
     * display, including no visible indicator.
     */
    @NonNull
    IndicatorType calculateIndicatorType(int displayId, PointF inputCoordinates) {
        if (DesktopExperienceFlags.ENABLE_CONNECTED_DISPLAYS_WINDOW_DRAG.isTrue()
                && mTaskInfo.displayId != displayId) {
            // TODO(b/411292927): Allow indicator to show on the target display (`displayId`)
            // even if it differs from the task's original display.
            return NO_INDICATOR;
        }
        final IndicatorType result;
        if (mUseSmallTabletRegions) {
            result = getIndicatorSmallTablet(inputCoordinates);
        } else {
            result = getIndicatorLargeTablet(inputCoordinates);
        }
        return result;
    }

    /**
     * Update the indicator based on IndicatorType.
     */
    @NonNull
    void updateIndicatorWithType(IndicatorType type) {
        if (!mIsReleased && mDragStartState != DragStartState.DRAGGED_INTENT) {
            mVisualIndicatorViewContainer.transitionIndicator(
                    mTaskInfo, mDisplayController, mCurrentType, type
            );
            mCurrentType = type;
        }
    }

    @NonNull
    private IndicatorType getIndicatorLargeTablet(PointF inputCoordinates) {
        // TODO(b/401596837): cache the regions to avoid recalculating on each motion event
        final DisplayLayout layout = mDisplayController.getDisplayLayout(mTaskInfo.displayId);
        // Perform a quick check first: any input off the left edge of the display should be split
        // left, and split right for the right edge. This is universal across all drag event types.
        if (inputCoordinates.x < 0) return TO_SPLIT_LEFT_INDICATOR;
        if (inputCoordinates.x > layout.width()) return TO_SPLIT_RIGHT_INDICATOR;
        // If we are in freeform, we don't want a visible indicator in the "freeform" drag zone.
        // In drags not originating on a freeform caption, we should default to a TO_DESKTOP
        // indicator.
        IndicatorType result = mDragStartState == DragStartState.FROM_FREEFORM
                    ? NO_INDICATOR
                    : TO_DESKTOP_INDICATOR;
        final int transitionAreaWidth = mContext.getResources().getDimensionPixelSize(
                com.android.wm.shell.R.dimen.desktop_mode_transition_region_thickness);
        // Because drags in freeform use task position for indicator calculation, we need to
        // account for the possibility of the task going off the top of the screen by captionHeight
        final int captionHeight = getDesktopViewAppHeaderHeightPx(mContext);
        final Region fullscreenRegion = calculateFullscreenRegion(layout, captionHeight);
        final Rect splitLeftRegion = calculateSplitLeftRegion(layout, transitionAreaWidth,
                captionHeight);
        final Rect splitRightRegion = calculateSplitRightRegion(layout, transitionAreaWidth,
                captionHeight);
        final int x = (int) inputCoordinates.x;
        final int y = (int) inputCoordinates.y;
        if (fullscreenRegion.contains(x, y)) {
            result = TO_FULLSCREEN_INDICATOR;
        }
        if (splitLeftRegion.contains(x, y)) {
            result = IndicatorType.TO_SPLIT_LEFT_INDICATOR;
        }
        if (splitRightRegion.contains(x, y)) {
            result = IndicatorType.TO_SPLIT_RIGHT_INDICATOR;
        }
        if (BubbleAnythingFlagHelper.enableBubbleToFullscreen()
                && mDragStartState == DragStartState.FROM_FULLSCREEN) {
            if (calculateBubbleLeftRegion(layout).contains(x, y)) {
                result = IndicatorType.TO_BUBBLE_LEFT_INDICATOR;
            } else if (calculateBubbleRightRegion(layout).contains(x, y)) {
                result = TO_BUBBLE_RIGHT_INDICATOR;
            }
        }
        return result;
    }

    @NonNull
    private IndicatorType getIndicatorSmallTablet(PointF inputCoordinates) {
        for (Pair<Rect, IndicatorType> region : mSortedRegions) {
            if (region.first.isEmpty()) return region.second; // empty rect matches all
            if (region.first.contains((int) inputCoordinates.x, (int) inputCoordinates.y)) {
                return region.second;
            }
        }
        return NO_INDICATOR;
    }

    /**
     * Returns the [DragStartState] of the visual indicator.
     */
    DragStartState getDragStartState() {
        return mDragStartState;
    }

    @VisibleForTesting
    Region calculateFullscreenRegion(DisplayLayout layout, int captionHeight) {
        final Region region = new Region();
        int transitionHeight = mDragStartState == DragStartState.FROM_FREEFORM
                || mDragStartState == DragStartState.DRAGGED_INTENT
                ? SystemBarUtils.getStatusBarHeight(mContext)
                : 2 * layout.stableInsets().top;
        // A Rect at the top of the screen that takes up the center 40%.
        if (mDragStartState == DragStartState.FROM_FREEFORM) {
            final float toFullscreenScale = mContext.getResources().getFloat(
                    R.dimen.desktop_mode_fullscreen_region_scale);
            final float toFullscreenWidth = (layout.width() * toFullscreenScale);
            region.union(new Rect((int) ((layout.width() / 2f) - (toFullscreenWidth / 2f)),
                    Short.MIN_VALUE,
                    (int) ((layout.width() / 2f) + (toFullscreenWidth / 2f)),
                    transitionHeight));
        }
        // A screen-wide Rect if the task is in fullscreen, split, or a dragged intent.
        if (mDragStartState == DragStartState.FROM_FULLSCREEN
                || mDragStartState == DragStartState.FROM_SPLIT
                || mDragStartState == DragStartState.DRAGGED_INTENT
        ) {
            region.union(new Rect(0,
                    Short.MIN_VALUE,
                    layout.width(),
                    transitionHeight));
        }
        return region;
    }

    @VisibleForTesting
    Rect calculateSplitLeftRegion(DisplayLayout layout,
            int transitionEdgeWidth, int captionHeight) {
        // In freeform, keep the top corners clear.
        int transitionHeight = mDragStartState == DragStartState.FROM_FREEFORM
                ? mContext.getResources().getDimensionPixelSize(
                com.android.wm.shell.R.dimen.desktop_mode_split_from_desktop_height) :
                -captionHeight;
        return new Rect(0, transitionHeight, transitionEdgeWidth, layout.height());
    }

    @VisibleForTesting
    Rect calculateSplitRightRegion(DisplayLayout layout,
            int transitionEdgeWidth, int captionHeight) {
        // In freeform, keep the top corners clear.
        int transitionHeight = mDragStartState == DragStartState.FROM_FREEFORM
                ? mContext.getResources().getDimensionPixelSize(
                com.android.wm.shell.R.dimen.desktop_mode_split_from_desktop_height) :
                -captionHeight;
        return new Rect(layout.width() - transitionEdgeWidth, transitionHeight,
                layout.width(), layout.height());
    }

    @VisibleForTesting
    Rect calculateBubbleLeftRegion(DisplayLayout layout) {
        int regionSize = getBubbleRegionSize();
        return new Rect(0, layout.height() - regionSize, regionSize, layout.height());
    }

    @VisibleForTesting
    Rect calculateBubbleRightRegion(DisplayLayout layout) {
        int regionSize = getBubbleRegionSize();
        return new Rect(layout.width() - regionSize, layout.height() - regionSize,
                layout.width(), layout.height());
    }

    private int getBubbleRegionSize() {
        int resId = mUseSmallTabletRegions
                ? com.android.wm.shell.shared.R.dimen.drag_zone_bubble_fold
                : com.android.wm.shell.shared.R.dimen.drag_zone_bubble_tablet;
        return mContext.getResources().getDimensionPixelSize(resId);
    }

    @VisibleForTesting
    Rect getIndicatorBounds() {
        return mVisualIndicatorViewContainer.getIndicatorBounds();
    }

    private List<Pair<Rect, IndicatorType>> initSmallTabletRegions(DisplayLayout layout,
            boolean isLeftRightSplit) {
        return switch (mDragStartState) {
            case FROM_FULLSCREEN -> initSmallTabletRegionsFromFullscreen(layout, isLeftRightSplit);
            case FROM_SPLIT -> initSmallTabletRegionsFromSplit(layout, isLeftRightSplit);
            default -> Collections.emptyList();
        };
    }

    private List<Pair<Rect, IndicatorType>> initSmallTabletRegionsFromFullscreen(
            DisplayLayout layout, boolean isLeftRightSplit) {

        List<Pair<Rect, IndicatorType>> result = new ArrayList<>();
        if (BubbleAnythingFlagHelper.enableBubbleToFullscreen()) {
            result.add(new Pair<>(calculateBubbleLeftRegion(layout), TO_BUBBLE_LEFT_INDICATOR));
            result.add(new Pair<>(calculateBubbleRightRegion(layout), TO_BUBBLE_RIGHT_INDICATOR));
        }

        if (isLeftRightSplit) {
            int splitRegionWidth = mContext.getResources().getDimensionPixelSize(
                    com.android.wm.shell.shared.R.dimen.drag_zone_h_split_from_app_width_fold);
            result.add(new Pair<>(calculateSplitLeftRegion(layout, splitRegionWidth,
                    /* captionHeight= */ 0), TO_SPLIT_LEFT_INDICATOR));
            result.add(new Pair<>(calculateSplitRightRegion(layout, splitRegionWidth,
                    /* captionHeight= */ 0), TO_SPLIT_RIGHT_INDICATOR));
        }
        // TODO(b/401352409): add support for top/bottom split zones
        // default to fullscreen
        result.add(new Pair<>(new Rect(), TO_FULLSCREEN_INDICATOR));
        return result;
    }

    private List<Pair<Rect, IndicatorType>> initSmallTabletRegionsFromSplit(DisplayLayout layout,
            boolean isLeftRightSplit) {
        if (!isLeftRightSplit) {
            // Dragging a top/bottom split is not supported on small tablets
            return Collections.emptyList();
        }

        List<Pair<Rect, IndicatorType>> result = new ArrayList<>();
        if (BubbleAnythingFlagHelper.enableBubbleAnything()) {
            result.add(new Pair<>(calculateBubbleLeftRegion(layout), TO_BUBBLE_LEFT_INDICATOR));
            result.add(new Pair<>(calculateBubbleRightRegion(layout), TO_BUBBLE_RIGHT_INDICATOR));
        }

        int splitRegionWidth = mContext.getResources().getDimensionPixelSize(
                com.android.wm.shell.shared.R.dimen.drag_zone_h_split_from_app_width_fold);
        result.add(new Pair<>(calculateSplitLeftRegion(layout, splitRegionWidth,
                /* captionHeight= */ 0), TO_SPLIT_LEFT_INDICATOR));
        result.add(new Pair<>(calculateSplitRightRegion(layout, splitRegionWidth,
                /* captionHeight= */ 0), TO_SPLIT_RIGHT_INDICATOR));
        // default to fullscreen
        result.add(new Pair<>(new Rect(), TO_FULLSCREEN_INDICATOR));
        return result;
    }
}
