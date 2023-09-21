/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.launcher3.util;

import static android.view.Surface.ROTATION_0;
import static android.view.Surface.ROTATION_180;
import static android.view.Surface.ROTATION_270;
import static android.view.Surface.ROTATION_90;

import android.graphics.Point;
import android.graphics.Rect;

/**
 * Utility methods based on {@code frameworks/base/core/java/android/util/RotationUtils.java}
 */
public class RotationUtils {

    /**
     * Rotates an Rect according to the given rotation.
     */
    public static void rotateRect(Rect rect, int rotation) {
        switch (rotation) {
            case ROTATION_0:
                return;
            case ROTATION_90:
                rect.set(rect.top, rect.right, rect.bottom, rect.left);
                return;
            case ROTATION_180:
                rect.set(rect.right, rect.bottom, rect.left, rect.top);
                return;
            case ROTATION_270:
                rect.set(rect.bottom, rect.left, rect.top, rect.right);
                return;
            default:
                throw new IllegalArgumentException("unknown rotation: " + rotation);
        }
    }

    /**
     * Rotates an size according to the given rotation.
     */
    public static void rotateSize(Point size, int rotation) {
        switch (rotation) {
            case ROTATION_0:
            case ROTATION_180:
                return;
            case ROTATION_90:
            case ROTATION_270:
                size.set(size.y, size.x);
                return;
            default:
                throw new IllegalArgumentException("unknown rotation: " + rotation);
        }
    }

    /** @return the rotation needed to rotate from oldRotation to newRotation. */
    public static int deltaRotation(int oldRotation, int newRotation) {
        int delta = newRotation - oldRotation;
        if (delta < 0) delta += 4;
        return delta;
    }
}
