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
import static com.android.launcher3.util.SplitConfigurationOptions.STAGE_TYPE_SIDE;

import android.content.res.Resources;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.FloatProperty;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.VelocityTracker;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import android.widget.LinearLayout;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.PagedView;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.util.OverScroller;
import com.android.launcher3.util.SplitConfigurationOptions.SplitPositionOption;

import java.util.ArrayList;
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
    public void delegateScrollTo(PagedView pagedView, int secondaryScroll, int minMaxScroll) {
        pagedView.superScrollTo(secondaryScroll, minMaxScroll);
    }

    @Override
    public void delegateScrollBy(PagedView pagedView, int unboundedScroll, int x, int y) {
        pagedView.scrollTo(pagedView.getScrollX() + x, unboundedScroll + y);
    }

    @Override
    public void scrollerStartScroll(OverScroller scroller, int newPosition) {
        scroller.startScroll(scroller.getCurrPos(), newPosition - scroller.getCurrPos());
    }

    @Override
    public void getCurveProperties(PagedView view, Rect insets, CurveProperties out) {
        out.scroll = view.getScrollY();
        out.halfPageSize = view.getNormalChildHeight() / 2;
        out.halfScreenSize = view.getMeasuredHeight() / 2;
        out.screenCenter = insets.top + view.getPaddingTop() + out.scroll + out.halfPageSize;
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
    public void delegateScrollTo(PagedView pagedView, int primaryScroll) {
        pagedView.superScrollTo(pagedView.getScrollX(), primaryScroll);
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
    public float getPrimarySize(RectF rect) {
        return rect.height();
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
    public float getTaskMenuX(float x, View thumbnailView) {
        return thumbnailView.getMeasuredWidth() + x;
    }

    @Override
    public float getTaskMenuY(float y, View thumbnailView) {
        return y;
    }

    @Override
    public int getTaskMenuWidth(View view) {
        return view.getMeasuredHeight();
    }

    @Override
    public int getTaskMenuLayoutOrientation(boolean canRecentsActivityRotate,
        LinearLayout taskMenuLayout) {
        return LinearLayout.HORIZONTAL;
    }

    @Override
    public void setLayoutParamsForTaskMenuOptionItem(LinearLayout.LayoutParams lp) {
        lp.width = 0;
        lp.height = WRAP_CONTENT;
        lp.weight = 1;
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
        List<SplitPositionOption> options = new ArrayList<>(2);
        // Add left/right options where left => position top, right => position bottom
        options.add(new SplitPositionOption(
                R.drawable.ic_split_screen, R.string.split_screen_position_left,
                STAGE_POSITION_TOP_OR_LEFT, STAGE_TYPE_MAIN));
        options.add(new SplitPositionOption(
                R.drawable.ic_split_screen, R.string.split_screen_position_right,
                STAGE_POSITION_BOTTOM_OR_RIGHT, STAGE_TYPE_SIDE));
        return options;
    }

    @Override
    public FloatProperty getSplitSelectTaskOffset(FloatProperty primary, FloatProperty secondary,
            DeviceProfile deviceProfile) {
        return primary;
    }
}
