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

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;

import com.android.launcher3.R;
import com.android.launcher3.util.Themes;

/** Helper class for creating drawables to use as background for list elements. */
final class WidgetsListDrawables {

    private WidgetsListDrawables() {}

    /** Creates a list background drawable with the specified radii. */
    static Drawable createListBackgroundDrawable(
            Context context,
            float topRadius,
            float bottomRadius) {
        GradientDrawable backgroundMask = new GradientDrawable();
        backgroundMask.setColor(context.getColorStateList(R.color.surface));
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

        return new RippleDrawable(
                /* color= */ ColorStateList.valueOf(
                        Themes.getAttrColor(context, android.R.attr.colorControlHighlight)),
                /* content= */ backgroundMask,
                /* mask= */ backgroundMask);
    }

}
