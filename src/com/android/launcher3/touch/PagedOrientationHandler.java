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

package com.android.launcher3.touch;

import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.ShapeDrawable;
import android.util.FloatProperty;
import android.util.Pair;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.util.SplitConfigurationOptions;
import com.android.launcher3.util.SplitConfigurationOptions.SplitPositionOption;
import com.android.launcher3.util.SplitConfigurationOptions.StagePosition;
import com.android.launcher3.util.SplitConfigurationOptions.StagedSplitBounds;

import java.util.List;

/**
 * Abstraction layer to separate horizontal and vertical specific implementations
 * for {@link com.android.launcher3.PagedView}. Majority of these implementations are (should be) as
 * simple as choosing the correct X and Y analogous methods.
 */
public interface PagedOrientationHandler {

    int SPLIT_TRANSLATE_PRIMARY_POSITIVE = 0;
    int SPLIT_TRANSLATE_PRIMARY_NEGATIVE = 1;
    int SPLIT_TRANSLATE_SECONDARY_NEGATIVE = 2;

    PagedOrientationHandler PORTRAIT = new PortraitPagedViewHandler();
    PagedOrientationHandler LANDSCAPE = new LandscapePagedViewHandler();
    PagedOrientationHandler SEASCAPE = new SeascapePagedViewHandler();

    interface Int2DAction<T> {
        void call(T target, int x, int y);
    }
    interface Float2DAction<T> {
        void call(T target, float x, float y);
    }
    Int2DAction<View> VIEW_SCROLL_BY = View::scrollBy;
    Int2DAction<View> VIEW_SCROLL_TO = View::scrollTo;
    Float2DAction<Canvas> CANVAS_TRANSLATE = Canvas::translate;
    Float2DAction<Matrix> MATRIX_POST_TRANSLATE = Matrix::postTranslate;

    <T> void setPrimary(T target, Int2DAction<T> action, int param);
    <T> void setPrimary(T target, Float2DAction<T> action, float param);
    <T> void setSecondary(T target, Float2DAction<T> action, float param);
    <T> void set(T target, Int2DAction<T> action, int primaryParam, int secondaryParam);
    float getPrimaryDirection(MotionEvent event, int pointerIndex);
    float getPrimaryVelocity(VelocityTracker velocityTracker, int pointerId);
    int getMeasuredSize(View view);
    int getPrimarySize(View view);
    float getPrimarySize(RectF rect);
    float getStart(RectF rect);
    float getEnd(RectF rect);
    int getClearAllSidePadding(View view, boolean isRtl);
    int getSecondaryDimension(View view);
    FloatProperty<View> getPrimaryViewTranslate();
    FloatProperty<View> getSecondaryViewTranslate();

    /**
     * @param stagePosition The position where the view to be split will go
     * @return {@link #SPLIT_TRANSLATE_*} constants to indicate which direction the
     * dismissal should happen
     */
    int getSplitTaskViewDismissDirection(@StagePosition int stagePosition, DeviceProfile dp);
    int getPrimaryScroll(View view);
    float getPrimaryScale(View view);
    int getChildStart(View view);
    int getCenterForPage(View view, Rect insets);
    int getScrollOffsetStart(View view, Rect insets);
    int getScrollOffsetEnd(View view, Rect insets);
    int getSecondaryTranslationDirectionFactor();
    int getSplitTranslationDirectionFactor(@StagePosition int stagePosition,
            DeviceProfile deviceProfile);
    ChildBounds getChildBounds(View child, int childStart, int pageCenter, boolean layoutChild);
    void setMaxScroll(AccessibilityEvent event, int maxScroll);
    boolean getRecentsRtlSetting(Resources resources);
    float getDegreesRotated();
    int getRotation();
    void setPrimaryScale(View view, float scale);
    void setSecondaryScale(View view, float scale);

    <T> T getPrimaryValue(T x, T y);
    <T> T getSecondaryValue(T x, T y);

    int getPrimaryValue(int x, int y);
    int getSecondaryValue(int x, int y);

    float getPrimaryValue(float x, float y);
    float getSecondaryValue(float x, float y);

    boolean isLayoutNaturalToLauncher();
    Pair<FloatProperty, FloatProperty> getSplitSelectTaskOffset(FloatProperty primary,
            FloatProperty secondary, DeviceProfile deviceProfile);
    int getDistanceToBottomOfRect(DeviceProfile dp, Rect rect);
    List<SplitPositionOption> getSplitPositionOptions(DeviceProfile dp);
    /**
     * @param splitholderSize height of placeholder view in portrait, width in landscape
     */
    void getInitialSplitPlaceholderBounds(int splitholderSize, DeviceProfile dp,
            @StagePosition int stagePosition, Rect out);

    /**
     * @param splitDividerSize height of split screen drag handle in portrait, width in landscape
     * @param stagePosition the split position option (top/left, bottom/right) of the first
     *                           task selected for entering split
     * @param out1 the bounds for where the first selected app will be
     * @param out2 the bounds for where the second selected app will be, complimentary to
     *             {@param out1} based on {@param initialSplitOption}
     */
    void getFinalSplitPlaceholderBounds(int splitDividerSize, DeviceProfile dp,
            @StagePosition int stagePosition, Rect out1, Rect out2);

    int getDefaultSplitPosition(DeviceProfile deviceProfile);

    /**
     * @param outRect This is expected to be the rect that has the dimensions for a non-split,
     *                fullscreen task in overview. This will directly be modified.
     * @param desiredStagePosition Which stage position (topLeft/rightBottom) we want to resize
     *                           outRect for
     */
    void setSplitTaskSwipeRect(DeviceProfile dp, Rect outRect, StagedSplitBounds splitInfo,
            @SplitConfigurationOptions.StagePosition int desiredStagePosition);

    void measureGroupedTaskViewThumbnailBounds(View primarySnapshot, View secondarySnapshot,
            int parentWidth, int parentHeight,
            StagedSplitBounds splitBoundsConfig, DeviceProfile dp);

    // Overview TaskMenuView methods
    void setIconAndSnapshotParams(View iconView, int taskIconMargin, int taskIconHeight,
            FrameLayout.LayoutParams snapshotParams, boolean isRtl);
    void setSplitIconParams(View primaryIconView, View secondaryIconView,
            int taskIconHeight, int primarySnapshotWidth, int primarySnapshotHeight,
            boolean isRtl, DeviceProfile deviceProfile, StagedSplitBounds splitConfig);

    /*
     * The following two methods try to center the TaskMenuView in landscape by finding the center
     * of the thumbnail view and then subtracting half of the taskMenu width. In this case, the
     * taskMenu width is the same size as the thumbnail width (what got set below in
     * getTaskMenuWidth()), so we directly use that in the calculations.
     */
    float getTaskMenuX(float x, View thumbnailView, int overScroll, DeviceProfile deviceProfile);
    float getTaskMenuY(float y, View thumbnailView, int overScroll);
    int getTaskMenuWidth(View view, DeviceProfile deviceProfile);
    /**
     * Sets linear layout orientation for {@link com.android.launcher3.popup.SystemShortcut} items
     * inside task menu view.
     */
    void setTaskOptionsMenuLayoutOrientation(DeviceProfile deviceProfile,
            LinearLayout taskMenuLayout, int dividerSpacing,
            ShapeDrawable dividerDrawable);
    /**
     * Sets layout param attributes for {@link com.android.launcher3.popup.SystemShortcut} child
     * views inside task menu view.
     */
    void setLayoutParamsForTaskMenuOptionItem(LinearLayout.LayoutParams lp,
            LinearLayout viewGroup, DeviceProfile deviceProfile);
    /**
     * Adjusts margins for the entire task menu view itself, which comprises of both app title and
     * shortcut options.
     */
    void setTaskMenuAroundTaskView(LinearLayout taskView, float margin);
    /**
     * Since the task menu layout is manually positioned on top of recents view, this method returns
     * additional adjustments to the positioning based on fake land/seascape
     */
    PointF getAdditionalInsetForTaskMenu(float margin);

    /**
     * Calculates the position where a Digital Wellbeing Banner should be placed on its parent
     * TaskView.
     * @return A Pair of Floats representing the proper x and y translations.
     */
    Pair<Float, Float> getDwbLayoutTranslations(int taskViewWidth,
            int taskViewHeight, StagedSplitBounds splitBounds, DeviceProfile deviceProfile,
            View[] thumbnailViews, int desiredTaskId, View banner);

    // The following are only used by TaskViewTouchHandler.
    /** @return Either VERTICAL or HORIZONTAL. */
    SingleAxisSwipeDetector.Direction getUpDownSwipeDirection();
    /** @return Given {@link #getUpDownSwipeDirection()}, whether POSITIVE or NEGATIVE is up. */
    int getUpDirection(boolean isRtl);
    /** @return Whether the displacement is going towards the top of the screen. */
    boolean isGoingUp(float displacement, boolean isRtl);
    /** @return Either 1 or -1, a factor to multiply by so the animation goes the correct way. */
    int getTaskDragDisplacementFactor(boolean isRtl);

    /**
     * Maps the velocity from the coordinate plane of the foreground app to that
     * of Launcher's (which now will always be portrait)
     */
    void adjustFloatingIconStartVelocity(PointF velocity);

    class ChildBounds {

        public final int primaryDimension;
        public final int secondaryDimension;
        public final int childPrimaryEnd;
        public final int childSecondaryEnd;

        ChildBounds(int primaryDimension, int secondaryDimension, int childPrimaryEnd,
            int childSecondaryEnd) {
            this.primaryDimension = primaryDimension;
            this.secondaryDimension = secondaryDimension;
            this.childPrimaryEnd = childPrimaryEnd;
            this.childSecondaryEnd = childSecondaryEnd;
        }
    }
}
