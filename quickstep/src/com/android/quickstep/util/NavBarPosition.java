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
package com.android.quickstep.util;

import static com.android.quickstep.SysUINavigationMode.Mode.NO_BUTTON;

import android.content.Context;
import android.graphics.Rect;
import android.view.Gravity;
import android.view.Surface;

import com.android.launcher3.graphics.RotationMode;
import com.android.launcher3.util.DefaultDisplay;
import com.android.quickstep.SysUINavigationMode;

/**
 * Utility class to check nav bar position.
 */
public class NavBarPosition {

    public static RotationMode ROTATION_LANDSCAPE = new RotationMode(-90) {
        @Override
        public void mapRect(int left, int top, int right, int bottom, Rect out) {
            out.left = top;
            out.top = right;
            out.right = bottom;
            out.bottom = left;
        }

        @Override
        public void mapInsets(Context context, Rect insets, Rect out) {
            // If there is a display cutout, the top insets in portrait would also include the
            // cutout, which we will get as the left inset in landscape. Using the max of left and
            // top allows us to cover both cases (with or without cutout).
            if (SysUINavigationMode.getMode(context) == NO_BUTTON) {
                out.top = Math.max(insets.top, insets.left);
                out.bottom = Math.max(insets.right, insets.bottom);
                out.left = out.right = 0;
            } else {
                out.top = Math.max(insets.top, insets.left);
                out.bottom = insets.right;
                out.left = insets.bottom;
                out.right = 0;
            }
        }
    };

    public static RotationMode ROTATION_SEASCAPE = new RotationMode(90) {
        @Override
        public void mapRect(int left, int top, int right, int bottom, Rect out) {
            out.left = bottom;
            out.top = left;
            out.right = top;
            out.bottom = right;
        }

        @Override
        public void mapInsets(Context context, Rect insets, Rect out) {
            if (SysUINavigationMode.getMode(context) == NO_BUTTON) {
                out.top = Math.max(insets.top, insets.right);
                out.bottom = Math.max(insets.left, insets.bottom);
                out.left = out.right = 0;
            } else {
                out.top = Math.max(insets.top, insets.right);
                out.bottom = insets.left;
                out.right = insets.bottom;
                out.left = 0;
            }
        }

        @Override
        public int toNaturalGravity(int absoluteGravity) {
            int horizontalGravity = absoluteGravity & Gravity.HORIZONTAL_GRAVITY_MASK;
            int verticalGravity = absoluteGravity & Gravity.VERTICAL_GRAVITY_MASK;

            if (horizontalGravity == Gravity.RIGHT) {
                horizontalGravity = Gravity.LEFT;
            } else if (horizontalGravity == Gravity.LEFT) {
                horizontalGravity = Gravity.RIGHT;
            }

            if (verticalGravity == Gravity.TOP) {
                verticalGravity = Gravity.BOTTOM;
            } else if (verticalGravity == Gravity.BOTTOM) {
                verticalGravity = Gravity.TOP;
            }

            return ((absoluteGravity & ~Gravity.HORIZONTAL_GRAVITY_MASK)
                    & ~Gravity.VERTICAL_GRAVITY_MASK)
                    | horizontalGravity | verticalGravity;
        }
    };

    private final SysUINavigationMode.Mode mMode;
    private final int mDisplayRotation;

    public NavBarPosition(SysUINavigationMode.Mode mode, DefaultDisplay.Info info) {
        mMode = mode;
        mDisplayRotation = info.rotation;
    }

    public boolean isRightEdge() {
        return mMode != NO_BUTTON && mDisplayRotation == Surface.ROTATION_90;
    }

    public boolean isLeftEdge() {
        return mMode != NO_BUTTON && mDisplayRotation == Surface.ROTATION_270;
    }

    public RotationMode getRotationMode() {
        return isLeftEdge() ? ROTATION_SEASCAPE
                : (isRightEdge() ? ROTATION_LANDSCAPE : RotationMode.NORMAL);
    }
}
