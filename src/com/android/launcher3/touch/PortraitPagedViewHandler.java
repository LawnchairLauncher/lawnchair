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

import android.content.res.Resources;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.FloatProperty;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.LauncherState.ScaleAndTranslation;
import com.android.launcher3.PagedView;
import com.android.launcher3.Utilities;
import com.android.launcher3.util.OverScroller;

import static com.android.launcher3.LauncherAnimUtils.VIEW_TRANSLATE_X;
import static com.android.launcher3.LauncherAnimUtils.VIEW_TRANSLATE_Y;
import static com.android.launcher3.touch.SingleAxisSwipeDetector.VERTICAL;

public class PortraitPagedViewHandler implements PagedOrientationHandler {

    @Override
    public float getCurrentAppAnimationScale(RectF src, RectF target) {
        return src.width() / target.width();
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
    public void delegateScrollTo(PagedView pagedView, int secondaryScroll, int primaryScroll) {
        pagedView.superScrollTo(primaryScroll, secondaryScroll);
    }

    @Override
    public void delegateScrollBy(PagedView pagedView, int unboundedScroll, int x, int y) {
        pagedView.scrollTo(unboundedScroll + x, pagedView.getScrollY() + y);
    }

    @Override
    public void scrollerStartScroll(OverScroller scroller, int newPosition) {
        scroller.startScroll(newPosition - scroller.getCurrPos(), scroller.getCurrPos());
    }

    @Override
    public CurveProperties getCurveProperties(PagedView pagedView, Rect mInsets) {
        int scroll = pagedView.getScrollX();
        final int halfPageSize = pagedView.getNormalChildWidth() / 2;
        final int screenCenter = mInsets.left + pagedView.getPaddingLeft() + scroll + halfPageSize;
        final int halfScreenSize = pagedView.getMeasuredWidth() / 2;
        return new CurveProperties(scroll, halfPageSize, screenCenter, halfScreenSize);
    }

    @Override
    public float getDragLengthFactor(int dimension, int transitionDragLength) {
        return (float) dimension / transitionDragLength;
    }

    @Override
    public boolean isGoingUp(float displacement) {
        return displacement < 0;
    }

    @Override
    public void delegateScrollTo(PagedView pagedView, int primaryScroll) {
        pagedView.superScrollTo(primaryScroll, pagedView.getScrollY());
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
    public int getPrimarySize(Rect rect) {
        return rect.width();
    }

    @Override
    public float getPrimarySize(RectF rect) {
        return rect.width();
    }

    @Override
    public int getSecondaryDimension(View view) {
        return view.getHeight();
    }

    @Override
    public ScaleAndTranslation getScaleAndTranslation(DeviceProfile dp, View view) {
        float offscreenTranslationX = dp.widthPx - view.getPaddingStart();
        return new ScaleAndTranslation(1f, offscreenTranslationX, 0f);
    }

    @Override
    public float getTranslationValue(ScaleAndTranslation scaleAndTranslation) {
        return scaleAndTranslation.translationX;
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
    public void setPrimaryAndResetSecondaryTranslate(View view, float translation) {
        view.setTranslationX(translation);
        view.setTranslationY(0);
    }

    @Override
    public float getViewCenterPosition(View view) {
        return view.getLeft() + view.getTranslationX();
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
    public void offsetTaskRect(RectF rect, float value, int delta) {
        if (delta == 0) {
            rect.offset(value, 0);
        } else if (delta == 1) {
            rect.offset(0, -value);
        } else if (delta == 2) {
            rect.offset(-value, 0);
        } else {
            rect.offset(0, value);
        }
    }

    @Override
    public void mapRectFromNormalOrientation(Rect src, int screenWidth, int screenHeight) {
        //no-op
    }

    @Override
    public int getChildStart(View view) {
        return view.getLeft();
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
    public SingleAxisSwipeDetector.Direction getOppositeSwipeDirection() {
        return VERTICAL;
    }

    @Override
    public int getShortEdgeLength(DeviceProfile dp) {
        return dp.widthPx;
    }

    @Override
    public int getTaskDismissDirectionFactor() {
        return -1;
    }

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
}
