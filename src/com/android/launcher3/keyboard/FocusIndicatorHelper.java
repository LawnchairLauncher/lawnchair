/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.launcher3.keyboard;

import android.graphics.Rect;
import android.view.View;
import android.view.View.OnFocusChangeListener;

import com.android.launcher3.Flags;
import com.android.launcher3.R;
import com.android.launcher3.util.Themes;

/**
 * A helper class to draw background of a focused view.
 */
public abstract class FocusIndicatorHelper extends ItemFocusIndicatorHelper<View>
        implements OnFocusChangeListener {

    public FocusIndicatorHelper(View container) {
        super(container, Flags.enableFocusOutline() ? Themes.getAttrColor(container.getContext(),
                R.attr.focusOutlineColor)
                : container.getResources().getColor(R.color.focused_background));
    }

    @Override
    public void onFocusChange(View v, boolean hasFocus) {
        changeFocus(v, hasFocus);
    }

    @Override
    protected boolean shouldDraw(View item) {
        return item.isAttachedToWindow();
    }

    /**
     * Simple subclass which assumes that the target view is a child of the container.
     */
    public static class SimpleFocusIndicatorHelper extends FocusIndicatorHelper {

        public SimpleFocusIndicatorHelper(View container) {
            super(container);
        }

        @Override
        public void viewToRect(View v, Rect outRect) {
            if (Flags.enableFocusOutline()) {
                // Ensure the left and top would not be negative and drawn outside of canvas
                outRect.set(Math.max(0, v.getLeft()), Math.max(0, v.getTop()), v.getRight(),
                        v.getBottom());
                // Stroke is drawn with half outside and half inside the view. Inset by half
                // stroke width to move the whole stroke inside the view and avoid other views
                // occluding it
                int halfStrokeWidth = (int) mPaint.getStrokeWidth() / 2;
                outRect.inset(halfStrokeWidth, halfStrokeWidth);
            } else {
                outRect.set(v.getLeft(), v.getTop(), v.getRight(), v.getBottom());
            }
        }
    }
}
