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
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.FloatProperty;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.LauncherState;
import com.android.launcher3.PagedView;
import com.android.launcher3.util.OverScroller;

/**
 * Abstraction layer to separate horizontal and vertical specific implementations
 * for {@link com.android.launcher3.PagedView}. Majority of these implementations are (should be) as
 * simple as choosing the correct X and Y analogous methods.
 */
public interface PagedOrientationHandler {

    interface Int2DAction<T> {
        void call(T target, int x, int y);
    }
    interface Float2DAction<T> {
        void call(T target, float x, float y);
    }
    Int2DAction<View> VIEW_SCROLL_BY = View::scrollBy;
    Int2DAction<View> VIEW_SCROLL_TO = View::scrollTo;
    Float2DAction<Canvas> CANVAS_TRANSLATE = Canvas::translate;
    <T> void set(T target, Int2DAction<T> action, int param);
    <T> void set(T target, Float2DAction<T> action, float param);
    float getPrimaryDirection(MotionEvent event, int pointerIndex);
    float getPrimaryVelocity(VelocityTracker velocityTracker, int pointerId);
    int getMeasuredSize(View view);
    int getPrimarySize(Rect rect);
    float getPrimarySize(RectF rect);
    int getSecondaryDimension(View view);
    LauncherState.ScaleAndTranslation getScaleAndTranslation(DeviceProfile dp, View view);
    float getTranslationValue(LauncherState.ScaleAndTranslation scaleAndTranslation);
    FloatProperty<View> getPrimaryViewTranslate();
    FloatProperty<View> getSecondaryViewTranslate();
    void setPrimaryAndResetSecondaryTranslate(View view, float translation);
    float getViewCenterPosition(View view);
    int getPrimaryScroll(View view);
    float getPrimaryScale(View view);
    int getChildStart(View view);
    int getCenterForPage(View view, Rect insets);
    int getScrollOffsetStart(View view, Rect insets);
    int getScrollOffsetEnd(View view, Rect insets);
    SingleAxisSwipeDetector.Direction getOppositeSwipeDirection();
    int getShortEdgeLength(DeviceProfile dp);
    int getTaskDismissDirectionFactor();
    ChildBounds getChildBounds(View child, int childStart, int pageCenter, boolean layoutChild);
    void setMaxScroll(AccessibilityEvent event, int maxScroll);
    boolean getRecentsRtlSetting(Resources resources);
    float getDegreesRotated();
    void offsetTaskRect(RectF rect, float value, int delta);
    int getPrimaryValue(int x, int y);
    int getSecondaryValue(int x, int y);
    void delegateScrollTo(PagedView pagedView, int secondaryScroll, int primaryScroll);
    /** Uses {@params pagedView}.getScroll[X|Y]() method for the secondary amount*/
    void delegateScrollTo(PagedView pagedView, int primaryScroll);
    void delegateScrollBy(PagedView pagedView, int unboundedScroll, int x, int y);
    void scrollerStartScroll(OverScroller scroller, int newPosition);
    CurveProperties getCurveProperties(PagedView pagedView, Rect insets);
    boolean isGoingUp(float displacement);

    /**
     * Maps the velocity from the coordinate plane of the foreground app to that
     * of Launcher's (which now will always be portrait)
     */
    void adjustFloatingIconStartVelocity(PointF velocity);

    class CurveProperties {
        public final int scroll;
        public final int halfPageSize;
        public final int screenCenter;
        public final int halfScreenSize;

        public CurveProperties(int scroll, int halfPageSize, int screenCenter, int halfScreenSize) {
            this.scroll = scroll;
            this.halfPageSize = halfPageSize;
            this.screenCenter = screenCenter;
            this.halfScreenSize = halfScreenSize;
        }
    }

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
