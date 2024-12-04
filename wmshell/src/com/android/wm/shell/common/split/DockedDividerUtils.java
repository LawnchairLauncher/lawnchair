/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.wm.shell.common.split;

import static android.view.WindowManager.DOCKED_BOTTOM;
import static android.view.WindowManager.DOCKED_INVALID;
import static android.view.WindowManager.DOCKED_LEFT;
import static android.view.WindowManager.DOCKED_RIGHT;
import static android.view.WindowManager.DOCKED_TOP;

import android.content.res.Resources;
import android.graphics.Rect;

/**
 * Utility functions for docked stack divider used by both window manager and System UI.
 *
 * @hide
 */
public class DockedDividerUtils {

    public static void calculateBoundsForPosition(int position, int dockSide, Rect outRect,
            int displayWidth, int displayHeight, int dividerSize) {
        outRect.set(0, 0, displayWidth, displayHeight);
        switch (dockSide) {
            case DOCKED_LEFT:
                outRect.right = position;
                break;
            case DOCKED_TOP:
                outRect.bottom = position;
                break;
            case DOCKED_RIGHT:
                outRect.left = position + dividerSize;
                break;
            case DOCKED_BOTTOM:
                outRect.top = position + dividerSize;
                break;
        }
        sanitizeStackBounds(outRect, dockSide == DOCKED_LEFT || dockSide == DOCKED_TOP);
    }

    /**
     * Makes sure that the bounds are always valid, i. e. they are at least one pixel high and wide.
     *
     * @param bounds The bounds to sanitize.
     * @param topLeft Pass true if the bounds are at the top/left of the screen, false if they are
     *                at the bottom/right. This is used to determine in which direction to extend
     *                the bounds.
     */
    public static void sanitizeStackBounds(Rect bounds, boolean topLeft) {

        // If the bounds are either on the top or left of the screen, rather move it further to the
        // left/top to make it more offscreen. If they are on the bottom or right, push them off the
        // screen by moving it even more to the bottom/right.
        if (topLeft) {
            if (bounds.left >= bounds.right) {
                bounds.left = bounds.right - 1;
            }
            if (bounds.top >= bounds.bottom) {
                bounds.top = bounds.bottom - 1;
            }
        } else {
            if (bounds.right <= bounds.left) {
                bounds.right = bounds.left + 1;
            }
            if (bounds.bottom <= bounds.top) {
                bounds.bottom = bounds.top + 1;
            }
        }
    }

    public static int calculatePositionForBounds(Rect bounds, int dockSide, int dividerSize) {
        switch (dockSide) {
            case DOCKED_LEFT:
                return bounds.right;
            case DOCKED_TOP:
                return bounds.bottom;
            case DOCKED_RIGHT:
                return bounds.left - dividerSize;
            case DOCKED_BOTTOM:
                return bounds.top - dividerSize;
            default:
                return 0;
        }
    }

    public static int calculateMiddlePosition(boolean isHorizontalDivision, Rect insets,
            int displayWidth, int displayHeight, int dividerSize) {
        int start = isHorizontalDivision ? insets.top : insets.left;
        int end = isHorizontalDivision
                ? displayHeight - insets.bottom
                : displayWidth - insets.right;
        return start + (end - start) / 2 - dividerSize / 2;
    }

    public static int invertDockSide(int dockSide) {
        switch (dockSide) {
            case DOCKED_LEFT:
                return DOCKED_RIGHT;
            case DOCKED_TOP:
                return DOCKED_BOTTOM;
            case DOCKED_RIGHT:
                return DOCKED_LEFT;
            case DOCKED_BOTTOM:
                return DOCKED_TOP;
            default:
                return DOCKED_INVALID;
        }
    }

    /** Returns the inset distance from the divider window edge to the dividerview. */
    public static int getDividerInsets(Resources res) {
        return res.getDimensionPixelSize(com.android.internal.R.dimen.docked_stack_divider_insets);
    }

    /** Returns the size of the divider */
    public static int getDividerSize(Resources res, int dividerInsets) {
        final int windowWidth = res.getDimensionPixelSize(
                com.android.internal.R.dimen.docked_stack_divider_thickness);
        return windowWidth - 2 * dividerInsets;
    }

    /** Returns the docked-stack side */
    public static int getDockSide(int displayWidth, int displayHeight) {
        return displayWidth > displayHeight ? DOCKED_LEFT : DOCKED_TOP;
    }
}
