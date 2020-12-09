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
import android.util.FloatProperty;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import android.widget.LinearLayout;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.PagedView;
import com.android.launcher3.util.OverScroller;

/**
 * Abstraction layer to separate horizontal and vertical specific implementations
 * for {@link com.android.launcher3.PagedView}. Majority of these implementations are (should be) as
 * simple as choosing the correct X and Y analogous methods.
 */
public interface PagedOrientationHandler {

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

    <T> void set(T target, Int2DAction<T> action, int param);
    <T> void set(T target, Float2DAction<T> action, float param);
    <T> void setSecondary(T target, Float2DAction<T> action, float param);
    float getPrimaryDirection(MotionEvent event, int pointerIndex);
    float getPrimaryVelocity(VelocityTracker velocityTracker, int pointerId);
    int getMeasuredSize(View view);
    float getPrimarySize(RectF rect);
    int getClearAllScrollOffset(View view, boolean isRtl);
    int getSecondaryDimension(View view);
    FloatProperty<View> getPrimaryViewTranslate();
    FloatProperty<View> getSecondaryViewTranslate();
    void setPrimaryAndResetSecondaryTranslate(View view, float translation);
    int getPrimaryScroll(View view);
    float getPrimaryScale(View view);
    int getChildStart(View view);
    float getChildStartWithTranslation(View view);
    int getCenterForPage(View view, Rect insets);
    int getScrollOffsetStart(View view, Rect insets);
    int getScrollOffsetEnd(View view, Rect insets);
    SingleAxisSwipeDetector.Direction getOppositeSwipeDirection();
    int getPrimaryTranslationDirectionFactor();
    int getSecondaryTranslationDirectionFactor();
    int getTaskDragDisplacementFactor(boolean isRtl);
    ChildBounds getChildBounds(View child, int childStart, int pageCenter, boolean layoutChild);
    void setMaxScroll(AccessibilityEvent event, int maxScroll);
    boolean getRecentsRtlSetting(Resources resources);
    float getDegreesRotated();
    int getRotation();
    int getPrimaryValue(int x, int y);
    int getSecondaryValue(int x, int y);
    void delegateScrollTo(PagedView pagedView, int secondaryScroll, int primaryScroll);
    /** Uses {@params pagedView}.getScroll[X|Y]() method for the secondary amount*/
    void delegateScrollTo(PagedView pagedView, int primaryScroll);
    void delegateScrollBy(PagedView pagedView, int unboundedScroll, int x, int y);
    void scrollerStartScroll(OverScroller scroller, int newPosition);
    void getCurveProperties(PagedView view, Rect insets, CurveProperties out);
    boolean isGoingUp(float displacement, boolean isRtl);
    boolean isLayoutNaturalToLauncher();
    float getTaskMenuX(float x, View thumbnailView);
    float getTaskMenuY(float y, View thumbnailView);
    int getTaskMenuWidth(View view);
    int getTaskMenuLayoutOrientation(boolean canRecentsActivityRotate, LinearLayout taskMenuLayout);
    void setLayoutParamsForTaskMenuOptionItem(LinearLayout.LayoutParams lp);
    int getDistanceToBottomOfRect(DeviceProfile dp, Rect rect);

    /**
     * Maps the velocity from the coordinate plane of the foreground app to that
     * of Launcher's (which now will always be portrait)
     */
    void adjustFloatingIconStartVelocity(PointF velocity);

    class CurveProperties {
        public int scroll;
        public int halfPageSize;
        public int screenCenter;
        public int halfScreenSize;
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
