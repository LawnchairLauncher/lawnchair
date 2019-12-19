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
import android.graphics.Matrix;
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
import static com.android.launcher3.touch.SingleAxisSwipeDetector.HORIZONTAL;

public class LandscapePagedViewHandler implements PagedOrientationHandler {
    @Override
    public float getCurrentAppAnimationScale(RectF src, RectF target) {
        return src.height() / target.height();
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
    public CurveProperties getCurveProperties(PagedView pagedView, Rect mInsets) {
        int scroll = pagedView.getScrollY();
        final int halfPageSize = pagedView.getNormalChildHeight() / 2;
        final int screenCenter = mInsets.top + pagedView.getPaddingTop() + scroll + halfPageSize;
        final int halfScreenSize = pagedView.getMeasuredHeight() / 2;
        return new CurveProperties(scroll, halfPageSize, screenCenter, halfScreenSize);
    }

    @Override
    public float getDragLengthFactor(int dimension, int transitionDragLength) {
        return Math.min(1.0f, (float) dimension / transitionDragLength);
    }

    @Override
    public boolean isGoingUp(float displacement) {
        return displacement > 0;
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
    public int getPrimarySize(Rect rect) {
        return rect.height();
    }

    @Override
    public float getPrimarySize(RectF rect) {
        return rect.height();
    }

    @Override
    public int getSecondaryDimension(View view) {
        return view.getWidth();
    }

    @Override
    public ScaleAndTranslation getScaleAndTranslation(DeviceProfile dp, View view) {
        float offscreenTranslationY = dp.heightPx - view.getPaddingTop();
        return new ScaleAndTranslation(1f, 0f, offscreenTranslationY);
    }

    @Override
    public float getTranslationValue(ScaleAndTranslation scaleAndTranslation) {
        return scaleAndTranslation.translationY;
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
    public void setPrimaryAndResetSecondaryTranslate(View view, float translation) {
        view.setTranslationX(0);
        view.setTranslationY(translation);
    }

    @Override
    public float getViewCenterPosition(View view) {
        return view.getTop() + view.getTranslationY();
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
        Matrix m = new Matrix();
        m.setRotate(270);
        m.postTranslate(0, screenWidth);
        RectF newTarget = new RectF();
        RectF oldTarget = new RectF(src);
        m.mapRect(newTarget, oldTarget);
        src.set((int)newTarget.left, (int)newTarget.top, (int)newTarget.right, (int)newTarget.bottom);
    }

    @Override
    public int getChildStart(View view) {
        return view.getTop();
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
    public SingleAxisSwipeDetector.Direction getOppositeSwipeDirection() {
        return HORIZONTAL;
    }

    @Override
    public int getShortEdgeLength(DeviceProfile dp) {
        return dp.heightPx;
    }

    @Override
    public int getTaskDismissDirectionFactor() {
        return 1;
    }

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
}
