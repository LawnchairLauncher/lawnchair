/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.launcher3.taskbar.navbutton;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Redirects touches that aren't handled by any child view to the nearest
 * clickable child. Only takes effect on <sw600dp.
 */
public class NearestTouchFrame extends FrameLayout {

    private final List<View> mClickableChildren = new ArrayList<>();
    private final List<View> mAttachedChildren = new ArrayList<>();
    private final boolean mIsActive;
    private final int[] mTmpInt = new int[2];

    // Offset (as the base) to translate window cords to view cords.
    private final int[] mWindowOffset = new int[2];
    private boolean mIsVertical;
    private View mTouchingChild;
    private final Map<View, Rect> mTouchableRegions = new HashMap<>();
    /**
     * Used to sort all child views either by their left position or their top position,
     * depending on if this layout is used horizontally or vertically, respectively
     */
    private final Comparator<View> mChildRegionComparator =
            (view1, view2) -> {
                int leftTopIndex = mIsVertical ? 1 : 0;
                view1.getLocationInWindow(mTmpInt);
                int startingCoordView1 = mTmpInt[leftTopIndex] - mWindowOffset[leftTopIndex];
                view2.getLocationInWindow(mTmpInt);
                int startingCoordView2 = mTmpInt[leftTopIndex] - mWindowOffset[leftTopIndex];

                return startingCoordView1 - startingCoordView2;
            };

    public NearestTouchFrame(Context context, AttributeSet attrs) {
        this(context, attrs, context.getResources().getConfiguration());
    }

    public NearestTouchFrame(Context context, AttributeSet attrs, Configuration c) {
        super(context, attrs);
        mIsActive = c.smallestScreenWidthDp < 600;
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        mClickableChildren.clear();
        mAttachedChildren.clear();
        mTouchableRegions.clear();
        addClickableChildren(this);
        getLocationInWindow(mWindowOffset);
        cacheClosestChildLocations();
    }

    /**
     * Populates {@link #mTouchableRegions} with the regions where each clickable child is the
     * closest for a given point on this layout.
     */
    private void cacheClosestChildLocations() {
        if (getWidth() == 0 || getHeight() == 0) {
            return;
        }

        // Sort by either top or left depending on mIsVertical, then take out all children
        // that are not attached to window
        mClickableChildren.sort(mChildRegionComparator);
        mClickableChildren.stream()
                .filter(View::isAttachedToWindow)
                .forEachOrdered(mAttachedChildren::add);

        // Cache bounds of children
        // Mark coordinates where the actual child layout resides in this frame's window
        for (int i = 0; i < mAttachedChildren.size(); i++) {
            View child = mAttachedChildren.get(i);
            if (!child.isAttachedToWindow()) {
                continue;
            }
            Rect childRegion = getChildsBounds(child);

            // We compute closest child from this child to the previous one
            if (i == 0) {
                // First child, nothing to the left/top of it
                if (mIsVertical) {
                    childRegion.top = 0;
                } else {
                    childRegion.left = 0;
                }
                mTouchableRegions.put(child, childRegion);
                continue;
            }

            View previousChild = mAttachedChildren.get(i - 1);
            Rect previousChildBounds = mTouchableRegions.get(previousChild);
            int midPoint;
            if (mIsVertical) {
                int distance = childRegion.top - previousChildBounds.bottom;
                midPoint = distance / 2;
                childRegion.top -= midPoint;
                previousChildBounds.bottom += midPoint - ((distance % 2) == 0 ? 1 : 0);
            } else {
                int distance = childRegion.left - previousChildBounds.right;
                midPoint = distance / 2;
                childRegion.left -= midPoint;
                previousChildBounds.right += midPoint - ((distance % 2) == 0 ? 1 : 0);
            }

            if (i == mClickableChildren.size() - 1) {
                // Last child, nothing to right/bottom of it
                if (mIsVertical) {
                    childRegion.bottom = getHeight();
                } else {
                    childRegion.right = getWidth();
                }
            }

            mTouchableRegions.put(child, childRegion);
        }
    }

    void setIsVertical(boolean isVertical) {
        mIsVertical = isVertical;
    }

    private Rect getChildsBounds(View child) {
        child.getLocationInWindow(mTmpInt);
        int left = mTmpInt[0] - mWindowOffset[0];
        int top = mTmpInt[1] - mWindowOffset[1];
        int right = left + child.getWidth();
        int bottom = top + child.getHeight();
        return new Rect(left, top, right, bottom);
    }

    private void addClickableChildren(ViewGroup group) {
        final int N = group.getChildCount();
        for (int i = 0; i < N; i++) {
            View child = group.getChildAt(i);
            if (child.isClickable()) {
                mClickableChildren.add(child);
            } else if (child instanceof ViewGroup) {
                addClickableChildren((ViewGroup) child);
            }
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (mIsActive) {
            int x = (int) event.getX();
            int y = (int) event.getY();
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                mTouchingChild = mClickableChildren
                        .stream()
                        .filter(View::isAttachedToWindow)
                        .filter(view -> mTouchableRegions.get(view).contains(x, y))
                        .findFirst()
                        .orElse(null);

            }
            if (mTouchingChild != null) {
                // Translate the touch event to the view center of the touching child.
                event.offsetLocation(mTouchingChild.getWidth() / 2 - x,
                        mTouchingChild.getHeight() / 2 - y);
                return mTouchingChild.getVisibility() == VISIBLE
                        && mTouchingChild.dispatchTouchEvent(event);
            }
        }
        return super.onTouchEvent(event);
    }

    public void dumpLogs(String prefix, PrintWriter pw) {
        pw.println(prefix + "NearestTouchFrame:");

        pw.println(String.format("%s\tmWindowOffset=%s", prefix, Arrays.toString(mWindowOffset)));
        pw.println(String.format("%s\tmIsVertical=%s", prefix, mIsVertical));
        pw.println(String.format("%s\tmTouchingChild=%s", prefix, mTouchingChild));
        pw.println(String.format("%s\tmTouchableRegions=%s", prefix,
                mTouchableRegions.keySet().stream()
                        .map(key -> key + "=" + mTouchableRegions.get(key))
                        .collect(Collectors.joining(", ", "{", "}"))));
    }
}
