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

import com.android.launcher3.Flags;
import com.android.launcher3.PagedView;

/**
 * {@link FocusIndicatorHelper} for a generic view group.
 */
public class ViewGroupFocusHelper extends FocusIndicatorHelper {

    private final View mContainer;
    private static final Rect sTempRect = new Rect();

    public ViewGroupFocusHelper(View container) {
        super(container);
        mContainer = container;
    }

    @Override
    protected boolean shouldDraw(View item) {
        if (Flags.enableFocusOutline()) {
            // Not draw outline in page transition because the outline just remains fully
            // persistent during the transition and does not look smooth
            return super.shouldDraw(item) && !isInPageTransition(item);
        } else {
            return super.shouldDraw(item);
        }
    }

    private boolean isInPageTransition(View view) {
        if (view == null || !(view.getParent() instanceof View)) {
            return false;
        }
        boolean isInTransition = false;
        if (view instanceof PagedView) {
            isInTransition = ((PagedView<?>) view).isPageInTransition();
        }
        return isInTransition || isInPageTransition((View) view.getParent());
    }

    @Override
    public void viewToRect(View v, Rect outRect) {
        // Using FocusedRect here allows views to provide their custom rect for drawing outline,
        // e.g. making the Rect bigger than the content to leave some padding between view and
        // outline
        v.getFocusedRect(sTempRect);
        outRect.left = sTempRect.left;
        outRect.top = sTempRect.top;

        computeLocationRelativeToContainer(v, outRect);

        // If a view is scaled, its position will also shift accordingly. For optimization, only
        // consider this for the last node.
        outRect.left = (int) (outRect.left + (1 - v.getScaleX()) * sTempRect.width() / 2);
        outRect.top = (int) (outRect.top + (1 - v.getScaleY()) * sTempRect.height() / 2);

        outRect.right = outRect.left + (int) (v.getScaleX() * sTempRect.width());
        outRect.bottom = outRect.top + (int) (v.getScaleY() * sTempRect.height());
    }

    private void computeLocationRelativeToContainer(View child, Rect outRect) {
        if (child == null) {
            return;
        }

        outRect.left += child.getX();
        outRect.top += child.getY();

        if (child.getParent() == null || !(child.getParent() instanceof View)) {
            return;
        }

        View parent = (View) child.getParent();
        if (parent != mContainer) {
            if (parent instanceof PagedView) {
                PagedView page = (PagedView) parent;
                outRect.left -= page.getScrollForPage(page.indexOfChild(child));
            }

            computeLocationRelativeToContainer(parent, outRect);
        }
    }
}
