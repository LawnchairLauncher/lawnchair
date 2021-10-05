/*
 * Copyright (C) 2019 The Android Open Source Project
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

import static com.android.launcher3.LauncherAnimUtils.VIEW_TRANSLATE_X;
import static com.android.launcher3.LauncherAnimUtils.VIEW_TRANSLATE_Y;
import static com.android.launcher3.touch.SingleAxisSwipeDetector.VERTICAL;
import static com.android.launcher3.util.SplitConfigurationOptions.STAGE_POSITION_BOTTOM_OR_RIGHT;
import static com.android.launcher3.util.SplitConfigurationOptions.STAGE_POSITION_TOP_OR_LEFT;
import static com.android.launcher3.util.SplitConfigurationOptions.STAGE_TYPE_MAIN;

import android.content.res.Resources;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.ShapeDrawable;
import android.util.FloatProperty;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.VelocityTracker;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import android.widget.LinearLayout;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.util.SplitConfigurationOptions.SplitPositionOption;
import com.android.launcher3.views.BaseDragLayer;

import java.util.ArrayList;
import java.util.List;

public class PortraitPagedViewHandler implements PagedOrientationHandler {

    @Override
    public <T> T getPrimaryValue(T x, T y) {
        return x;
    }

    @Override
    public <T> T getSecondaryValue(T x, T y) {
        return y;
    }

    @Override
    public int getPrimaryValue(int x, int y) {
        return x;
    }

    @Override
    public int getSecondaryValue(int x, int y) {
        return y;
    }

    @Override
    public float getPrimaryValue(float x, float y) {
        return x;
    }

    @Override
    public float getSecondaryValue(float x, float y) {
        return y;
    }

    @Override
    public boolean isLayoutNaturalToLauncher() {
        return true;
    }

    @Override
    public void adjustFloatingIconStartVelocity(PointF velocity) {
        //no-op
    }

    @Override
    public <T> void set(T target, Int2DAction<T> action, int param) {
        action.call(target, param, 0);
    }

    @Override
    public <T> void set(T target, Float2DAction<T> action, float param) {
        action.call(target, param, 0);
    }

    @Override
    public <T> void setSecondary(T target, Float2DAction<T> action, float param) {
        action.call(target, 0, param);
    }

    @Override
    public float getPrimaryDirection(MotionEvent event, int pointerIndex) {
        return event.getX(pointerIndex);
    }

    @Override
    public float getPrimaryVelocity(VelocityTracker velocityTracker, int pointerId) {
        return velocityTracker.getXVelocity(pointerId);
    }

    @Override
    public int getMeasuredSize(View view) {
        return view.getMeasuredWidth();
    }

    @Override
    public int getPrimarySize(View view) {
        return view.getWidth();
    }

    @Override
    public float getPrimarySize(RectF rect) {
        return rect.width();
    }

    @Override
    public float getStart(RectF rect) {
        return rect.left;
    }

    @Override
    public float getEnd(RectF rect) {
        return rect.right;
    }

    @Override
    public int getClearAllSidePadding(View view, boolean isRtl) {
        return (isRtl ? view.getPaddingRight() : - view.getPaddingLeft()) / 2;
    }

    @Override
    public int getSecondaryDimension(View view) {
        return view.getHeight();
    }

    @Override
    public FloatProperty<View> getPrimaryViewTranslate() {
        return VIEW_TRANSLATE_X;
    }

    @Override
    public FloatProperty<View> getSecondaryViewTranslate() {
        return VIEW_TRANSLATE_Y;
    }

    @Override
    public int getSplitTaskViewDismissDirection(SplitPositionOption splitPosition,
            DeviceProfile dp) {
        if (splitPosition.mStagePosition == STAGE_POSITION_TOP_OR_LEFT) {
            if (dp.isLandscape) {
                // Left side
                return SPLIT_TRANSLATE_PRIMARY_NEGATIVE;
            } else {
                // Top side
                return SPLIT_TRANSLATE_SECONDARY_NEGATIVE;
            }
        } else if (splitPosition.mStagePosition == STAGE_POSITION_BOTTOM_OR_RIGHT) {
            // We don't have a bottom option, so should be right
            return SPLIT_TRANSLATE_PRIMARY_POSITIVE;
        }
        throw new IllegalStateException("Invalid split stage position: " +
                splitPosition.mStagePosition);
    }

    @Override
    public int getPrimaryScroll(View view) {
        return view.getScrollX();
    }

    @Override
    public float getPrimaryScale(View view) {
        return view.getScaleX();
    }

    @Override
    public void setMaxScroll(AccessibilityEvent event, int maxScroll) {
        event.setMaxScrollX(maxScroll);
    }

    @Override
    public boolean getRecentsRtlSetting(Resources resources) {
        return !Utilities.isRtl(resources);
    }

    @Override
    public float getDegreesRotated() {
        return 0;
    }

    @Override
    public int getRotation() {
        return Surface.ROTATION_0;
    }

    @Override
    public int getChildStart(View view) {
        return view.getLeft();
    }

    @Override
    public float getChildStartWithTranslation(View view) {
        return view.getLeft() + view.getTranslationX();
    }

    @Override
    public int getCenterForPage(View view, Rect insets) {
        return (view.getPaddingTop() + view.getMeasuredHeight() + insets.top
            - insets.bottom - view.getPaddingBottom()) / 2;
    }

    @Override
    public int getScrollOffsetStart(View view, Rect insets) {
        return insets.left + view.getPaddingLeft();
    }

    @Override
    public int getScrollOffsetEnd(View view, Rect insets) {
        return view.getWidth() - view.getPaddingRight() - insets.right;
    }

    @Override
    public int getPrimaryTranslationDirectionFactor() {
        return 1;
    }

    public int getSecondaryTranslationDirectionFactor() {
        return -1;
    }

    @Override
    public int getSplitTranslationDirectionFactor(int stagePosition) {
        if (stagePosition == STAGE_POSITION_BOTTOM_OR_RIGHT) {
            return -1;
        } else {
            return 1;
        }
    }

    @Override
    public int getSplitAnimationTranslation(int translationOffset, DeviceProfile dp) {
        if (dp.isLandscape) {
            return translationOffset;
        }
        return 0;
    }

    @Override
    public float getTaskMenuX(float x, View thumbnailView, int overScroll) {
        return x + overScroll;
    }

    @Override
    public float getTaskMenuY(float y, View thumbnailView, int overScroll) {
        return y;
    }

    @Override
    public int getTaskMenuWidth(View view) {
        return view.getMeasuredWidth();
    }

    @Override
    public void setTaskOptionsMenuLayoutOrientation(DeviceProfile deviceProfile,
            LinearLayout taskMenuLayout, int dividerSpacing,
            ShapeDrawable dividerDrawable) {
        if (deviceProfile.isLandscape && !deviceProfile.isTablet) {
            // Phone landscape
            taskMenuLayout.setOrientation(LinearLayout.HORIZONTAL);
            dividerDrawable.setIntrinsicWidth(dividerSpacing);
        } else {
            // Phone Portrait, LargeScreen Landscape/Portrait
            taskMenuLayout.setOrientation(LinearLayout.VERTICAL);
            dividerDrawable.setIntrinsicHeight(dividerSpacing);
        }
        taskMenuLayout.setDividerDrawable(dividerDrawable);
    }

    @Override
    public void setLayoutParamsForTaskMenuOptionItem(LinearLayout.LayoutParams lp,
            LinearLayout viewGroup, DeviceProfile deviceProfile) {
        if (deviceProfile.isLandscape && !deviceProfile.isTablet) {
            // Phone landscape
            viewGroup.setOrientation(LinearLayout.VERTICAL);
            lp.width = 0;
            lp.weight = 1;
            Utilities.setStartMarginForView(viewGroup.findViewById(R.id.text), 0);
            Utilities.setStartMarginForView(viewGroup.findViewById(R.id.icon), 0);
        } else {
            // Phone Portrait, LargeScreen Landscape/Portrait
            viewGroup.setOrientation(LinearLayout.HORIZONTAL);
            lp.width = LinearLayout.LayoutParams.MATCH_PARENT;
        }

        lp.height = LinearLayout.LayoutParams.WRAP_CONTENT;
    }

    @Override
    public void setTaskMenuAroundTaskView(LinearLayout taskView, float margin) {
        BaseDragLayer.LayoutParams lp = (BaseDragLayer.LayoutParams) taskView.getLayoutParams();
        lp.topMargin += margin;
        lp.leftMargin += margin;
    }

    @Override
    public PointF getAdditionalInsetForTaskMenu(float margin) {
        return new PointF(0, 0);
    }

    /* ---------- The following are only used by TaskViewTouchHandler. ---------- */

    @Override
    public SingleAxisSwipeDetector.Direction getUpDownSwipeDirection() {
        return VERTICAL;
    }

    @Override
    public int getUpDirection(boolean isRtl) {
        // Ignore rtl since it only affects X value displacement, Y displacement doesn't change
        return SingleAxisSwipeDetector.DIRECTION_POSITIVE;
    }

    @Override
    public boolean isGoingUp(float displacement, boolean isRtl) {
        // Ignore rtl since it only affects X value displacement, Y displacement doesn't change
        return displacement < 0;
    }

    @Override
    public int getTaskDragDisplacementFactor(boolean isRtl) {
        // Ignore rtl since it only affects X value displacement, Y displacement doesn't change
        return 1;
    }

    /* -------------------- */

    @Override
    public ChildBounds getChildBounds(View child, int childStart, int pageCenter,
        boolean layoutChild) {
        final int childWidth = child.getMeasuredWidth();
        final int childRight = childStart + childWidth;
        final int childHeight = child.getMeasuredHeight();
        final int childTop = pageCenter - childHeight / 2;
        if (layoutChild) {
            child.layout(childStart, childTop, childRight, childTop + childHeight);
        }
        return new ChildBounds(childWidth, childHeight, childRight, childTop);
    }

    @Override
    public int getDistanceToBottomOfRect(DeviceProfile dp, Rect rect) {
        return dp.heightPx - rect.bottom;
    }

    @Override
    public List<SplitPositionOption> getSplitPositionOptions(DeviceProfile dp) {
        List<SplitPositionOption> options = new ArrayList<>(1);
        // Add both left and right options if we're in tablet mode
        // TODO: Add in correct icons
        if (dp.isTablet && dp.isLandscape) {
            options.add(new SplitPositionOption(
                    R.drawable.ic_split_screen, R.string.split_screen_position_right,
                    STAGE_POSITION_BOTTOM_OR_RIGHT, STAGE_TYPE_MAIN));
            options.add(new SplitPositionOption(
                    R.drawable.ic_split_screen, R.string.split_screen_position_left,
                    STAGE_POSITION_TOP_OR_LEFT, STAGE_TYPE_MAIN));
        } else {
            if (dp.isSeascape()) {
                // Add left/right options
                options.add(new SplitPositionOption(
                        R.drawable.ic_split_screen, R.string.split_screen_position_right,
                        STAGE_POSITION_BOTTOM_OR_RIGHT, STAGE_TYPE_MAIN));
            } else if (dp.isLandscape) {
                options.add(new SplitPositionOption(
                        R.drawable.ic_split_screen, R.string.split_screen_position_left,
                        STAGE_POSITION_TOP_OR_LEFT, STAGE_TYPE_MAIN));
            } else {
                // Only add top option
                options.add(new SplitPositionOption(
                        R.drawable.ic_split_screen, R.string.split_screen_position_top,
                        STAGE_POSITION_TOP_OR_LEFT, STAGE_TYPE_MAIN));
            }
        }
        return options;
    }

    @Override
    public FloatProperty getSplitSelectTaskOffset(FloatProperty primary, FloatProperty secondary,
            DeviceProfile dp) {
        if (dp.isLandscape) { // or seascape
            return primary;
        } else {
            return secondary;
        }
    }
}
