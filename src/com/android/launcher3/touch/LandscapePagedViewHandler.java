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

import static android.widget.ListPopupWindow.WRAP_CONTENT;

import static com.android.launcher3.LauncherAnimUtils.VIEW_TRANSLATE_X;
import static com.android.launcher3.LauncherAnimUtils.VIEW_TRANSLATE_Y;
import static com.android.launcher3.touch.SingleAxisSwipeDetector.HORIZONTAL;
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

import java.util.Collections;
import java.util.List;

public class LandscapePagedViewHandler implements PagedOrientationHandler {

    @Override
    public <T> T getPrimaryValue(T x, T y) {
        return y;
    }

    @Override
    public <T> T getSecondaryValue(T x, T y) {
        return x;
    }

    @Override
    public int getPrimaryValue(int x, int y) {
        return y;
    }

    @Override
    public int getSecondaryValue(int x, int y) {
        return x;
    }

    @Override
    public float getPrimaryValue(float x, float y) {
        return y;
    }

    @Override
    public float getSecondaryValue(float x, float y) {
        return x;
    }

    @Override
    public boolean isLayoutNaturalToLauncher() {
        return false;
    }

    @Override
    public void adjustFloatingIconStartVelocity(PointF velocity) {
        float oldX = velocity.x;
        float oldY = velocity.y;
        velocity.set(-oldY, oldX);
    }

    @Override
    public <T> void set(T target, Int2DAction<T> action, int param) {
        action.call(target, 0, param);
    }

    @Override
    public <T> void set(T target, Float2DAction<T> action, float param) {
        action.call(target, 0, param);
    }

    @Override
    public <T> void setSecondary(T target, Float2DAction<T> action, float param) {
        action.call(target, param, 0);
    }

    @Override
    public float getPrimaryDirection(MotionEvent event, int pointerIndex) {
        return event.getY(pointerIndex);
    }

    @Override
    public float getPrimaryVelocity(VelocityTracker velocityTracker, int pointerId) {
        return velocityTracker.getYVelocity(pointerId);
    }

    @Override
    public int getMeasuredSize(View view) {
        return view.getMeasuredHeight();
    }

    @Override
    public int getPrimarySize(View view) {
        return view.getHeight();
    }

    @Override
    public float getPrimarySize(RectF rect) {
        return rect.height();
    }

    @Override
    public float getStart(RectF rect) {
        return rect.top;
    }

    @Override
    public float getEnd(RectF rect) {
        return rect.bottom;
    }

    @Override
    public int getClearAllSidePadding(View view, boolean isRtl) {
        return (isRtl ? view.getPaddingBottom() : - view.getPaddingTop()) / 2;
    }

    @Override
    public int getSecondaryDimension(View view) {
        return view.getWidth();
    }

    @Override
    public FloatProperty<View> getPrimaryViewTranslate() {
        return VIEW_TRANSLATE_Y;
    }

    @Override
    public FloatProperty<View> getSecondaryViewTranslate() {
        return VIEW_TRANSLATE_X;
    }

    @Override
    public int getSplitTaskViewDismissDirection(SplitPositionOption splitPosition,
            DeviceProfile dp) {
        // Don't use device profile here because we know we're in fake landscape, only split option
        // available is top/left
        if (splitPosition.mStagePosition == STAGE_POSITION_TOP_OR_LEFT) {
            // Top (visually left) side
            return SPLIT_TRANSLATE_PRIMARY_NEGATIVE;
        }
        throw new IllegalStateException("Invalid split stage position: " +
                splitPosition.mStagePosition);
    }

    @Override
    public int getPrimaryScroll(View view) {
        return view.getScrollY();
    }

    @Override
    public float getPrimaryScale(View view) {
        return view.getScaleY();
    }

    @Override
    public void setMaxScroll(AccessibilityEvent event, int maxScroll) {
        event.setMaxScrollY(maxScroll);
    }

    @Override
    public boolean getRecentsRtlSetting(Resources resources) {
        return !Utilities.isRtl(resources);
    }

    @Override
    public float getDegreesRotated() {
        return 90;
    }

    @Override
    public int getRotation() {
        return Surface.ROTATION_90;
    }

    @Override
    public int getChildStart(View view) {
        return view.getTop();
    }

    @Override
    public float getChildStartWithTranslation(View view) {
        return view.getTop() + view.getTranslationY();
    }

    @Override
    public int getCenterForPage(View view, Rect insets) {
        return (view.getPaddingLeft() + view.getMeasuredWidth() + insets.left
            - insets.right - view.getPaddingRight()) / 2;
    }

    @Override
    public int getScrollOffsetStart(View view, Rect insets) {
        return insets.top + view.getPaddingTop();
    }

    @Override
    public int getScrollOffsetEnd(View view, Rect insets) {
        return view.getHeight() - view.getPaddingBottom() - insets.bottom;
    }

    @Override
    public int getPrimaryTranslationDirectionFactor() {
        return -1;
    }

    public int getSecondaryTranslationDirectionFactor() {
        return 1;
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
        return translationOffset;
    }

    @Override
    public float getTaskMenuX(float x, View thumbnailView, int overScroll) {
        return thumbnailView.getMeasuredWidth() + x;
    }

    @Override
    public float getTaskMenuY(float y, View thumbnailView, int overScroll) {
        return y + overScroll;
    }

    @Override
    public int getTaskMenuWidth(View view) {
        return view.getMeasuredHeight();
    }

    @Override
    public void setTaskOptionsMenuLayoutOrientation(DeviceProfile deviceProfile,
            LinearLayout taskMenuLayout, int dividerSpacing,
            ShapeDrawable dividerDrawable) {
        taskMenuLayout.setOrientation(LinearLayout.HORIZONTAL);
        dividerDrawable.setIntrinsicWidth(dividerSpacing);
        taskMenuLayout.setDividerDrawable(dividerDrawable);
    }

    @Override
    public void setLayoutParamsForTaskMenuOptionItem(LinearLayout.LayoutParams lp,
            LinearLayout viewGroup, DeviceProfile deviceProfile) {
        // Phone fake landscape
        viewGroup.setOrientation(LinearLayout.VERTICAL);
        lp.width = 0;
        lp.height = WRAP_CONTENT;
        lp.weight = 1;
        Utilities.setStartMarginForView(viewGroup.findViewById(R.id.text), 0);
        Utilities.setStartMarginForView(viewGroup.findViewById(R.id.icon), 0);
    }

    @Override
    public void setTaskMenuAroundTaskView(LinearLayout taskView, float margin) {
        BaseDragLayer.LayoutParams lp = (BaseDragLayer.LayoutParams) taskView.getLayoutParams();
        lp.topMargin += margin;
    }

    @Override
    public PointF getAdditionalInsetForTaskMenu(float margin) {
        return new PointF(margin, 0);
    }

    /* ---------- The following are only used by TaskViewTouchHandler. ---------- */

    @Override
    public SingleAxisSwipeDetector.Direction getUpDownSwipeDirection() {
        return HORIZONTAL;
    }

    @Override
    public int getUpDirection(boolean isRtl) {
        return isRtl ? SingleAxisSwipeDetector.DIRECTION_NEGATIVE
                : SingleAxisSwipeDetector.DIRECTION_POSITIVE;
    }

    @Override
    public boolean isGoingUp(float displacement, boolean isRtl) {
        return isRtl ? displacement < 0 : displacement > 0;
    }

    @Override
    public int getTaskDragDisplacementFactor(boolean isRtl) {
        return isRtl ? 1 : -1;
    }

    /* -------------------- */

    @Override
    public ChildBounds getChildBounds(View child, int childStart, int pageCenter,
        boolean layoutChild) {
        final int childHeight = child.getMeasuredHeight();
        final int childBottom = childStart + childHeight;
        final int childWidth = child.getMeasuredWidth();
        final int childLeft = pageCenter - childWidth/ 2;
        if (layoutChild) {
            child.layout(childLeft, childStart, childLeft + childWidth, childBottom);
        }
        return new ChildBounds(childHeight, childWidth, childBottom, childLeft);
    }

    @SuppressWarnings("SuspiciousNameCombination")
    @Override
    public int getDistanceToBottomOfRect(DeviceProfile dp, Rect rect) {
        return rect.left;
    }

    @Override
    public List<SplitPositionOption> getSplitPositionOptions(DeviceProfile dp) {
        // Add "left" side of phone which is actually the top
        return Collections.singletonList(new SplitPositionOption(
                R.drawable.ic_split_screen, R.string.split_screen_position_left,
                STAGE_POSITION_TOP_OR_LEFT, STAGE_TYPE_MAIN));
    }

    @Override
    public FloatProperty getSplitSelectTaskOffset(FloatProperty primary, FloatProperty secondary,
            DeviceProfile deviceProfile) {
        return primary;
    }
}
