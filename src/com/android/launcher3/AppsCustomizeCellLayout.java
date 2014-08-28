/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.launcher3;

import android.content.Context;
import android.view.View;

public class AppsCustomizeCellLayout extends CellLayout implements Page {

    final FocusIndicatorView mFocusHandlerView;

    public AppsCustomizeCellLayout(Context context) {
        super(context);

        mFocusHandlerView = new FocusIndicatorView(context);
        addView(mFocusHandlerView, 0);
        mFocusHandlerView.getLayoutParams().width = FocusIndicatorView.DEFAULT_LAYOUT_SIZE;
        mFocusHandlerView.getLayoutParams().height = FocusIndicatorView.DEFAULT_LAYOUT_SIZE;
    }

    @Override
    public void removeAllViewsOnPage() {
        removeAllViews();
        setLayerType(LAYER_TYPE_NONE, null);
    }

    @Override
    public void removeViewOnPageAt(int index) {
        removeViewAt(index);
    }

    @Override
    public int getPageChildCount() {
        return getChildCount();
    }

    @Override
    public View getChildOnPageAt(int i) {
        return getChildAt(i);
    }

    @Override
    public int indexOfChildOnPage(View v) {
        return indexOfChild(v);
    }

    /**
     * Clears all the key listeners for the individual icons.
     */
    public void resetChildrenOnKeyListeners() {
        ShortcutAndWidgetContainer children = getShortcutsAndWidgets();
        int childCount = children.getChildCount();
        for (int j = 0; j < childCount; ++j) {
            children.getChildAt(j).setOnKeyListener(null);
        }
    }
}
