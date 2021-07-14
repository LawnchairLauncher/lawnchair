/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.launcher3.widget.picker;

import static com.android.launcher3.widget.picker.WidgetsListDrawableState.FIRST;
import static com.android.launcher3.widget.picker.WidgetsListDrawableState.FIRST_EXPANDED;
import static com.android.launcher3.widget.picker.WidgetsListDrawableState.LAST;
import static com.android.launcher3.widget.picker.WidgetsListDrawableState.MIDDLE;
import static com.android.launcher3.widget.picker.WidgetsListDrawableState.MIDDLE_EXPANDED;
import static com.android.launcher3.widget.picker.WidgetsListDrawableState.SINGLE;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.StateListDrawable;

import com.android.launcher3.R;
import com.android.launcher3.util.Themes;

/** Factory for creating drawables to use as background for list elements. */
final class WidgetsListDrawableFactory {

    private final float mTopBottomCornerRadius;
    private final float mMiddleCornerRadius;
    private final ColorStateList mSurfaceColor;
    private final ColorStateList mRippleColor;

    WidgetsListDrawableFactory(Context context) {
        Resources res = context.getResources();
        mTopBottomCornerRadius = res.getDimension(R.dimen.widget_list_top_bottom_corner_radius);
        mMiddleCornerRadius = res.getDimension(R.dimen.widget_list_content_corner_radius);
        mSurfaceColor = context.getColorStateList(R.color.surface);
        mRippleColor = ColorStateList.valueOf(
                Themes.getAttrColor(context, android.R.attr.colorControlHighlight));
    }

    /**
     * Creates a drawable for widget header list items. This drawable supports all positions
     * in {@link WidgetsListDrawableState}.
     */
    Drawable createHeaderBackgroundDrawable() {
        StateListDrawable stateList = new StateListDrawable();
        stateList.addState(
                SINGLE.mStateSet,
                createRoundedRectDrawable(mTopBottomCornerRadius, mTopBottomCornerRadius));
        stateList.addState(
                FIRST_EXPANDED.mStateSet,
                createRoundedRectDrawable(mTopBottomCornerRadius, 0));
        stateList.addState(
                FIRST.mStateSet,
                createRoundedRectDrawable(mTopBottomCornerRadius, mMiddleCornerRadius));
        stateList.addState(
                MIDDLE_EXPANDED.mStateSet,
                createRoundedRectDrawable(mMiddleCornerRadius, 0));
        stateList.addState(
                MIDDLE.mStateSet,
                createRoundedRectDrawable(mMiddleCornerRadius, mMiddleCornerRadius));
        stateList.addState(
                LAST.mStateSet,
                createRoundedRectDrawable(mMiddleCornerRadius, mTopBottomCornerRadius));
        return new RippleDrawable(mRippleColor, /* content= */ stateList, /* mask= */ stateList);
    }

    /**
     * Creates a drawable for widget content list items. This state list supports the middle and
     * last states.
     */
    Drawable createContentBackgroundDrawable() {
        StateListDrawable stateList = new StateListDrawable();
        stateList.addState(
                MIDDLE.mStateSet,
                createRoundedRectDrawable(0, mMiddleCornerRadius));
        stateList.addState(
                LAST.mStateSet,
                createRoundedRectDrawable(0, mTopBottomCornerRadius));
        return new RippleDrawable(mRippleColor, /* content= */ stateList, /* mask= */ stateList);
    }

    /** Creates a rounded-rect drawable with the specified radii. */
    private Drawable createRoundedRectDrawable(float topRadius, float bottomRadius) {
        GradientDrawable backgroundMask = new GradientDrawable();
        backgroundMask.setColor(mSurfaceColor);
        backgroundMask.setShape(GradientDrawable.RECTANGLE);
        backgroundMask.setCornerRadii(
                new float[]{
                        topRadius,
                        topRadius,
                        topRadius,
                        topRadius,
                        bottomRadius,
                        bottomRadius,
                        bottomRadius,
                        bottomRadius
                });
        return backgroundMask;
    }
}
