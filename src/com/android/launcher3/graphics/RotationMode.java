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
package com.android.launcher3.graphics;

import android.content.Context;
import android.graphics.Rect;

public abstract class RotationMode {

    public static RotationMode NORMAL = new RotationMode(0) { };

    public final float surfaceRotation;
    public final boolean isTransposed;

    public RotationMode(float surfaceRotation) {
        this.surfaceRotation = surfaceRotation;
        isTransposed = surfaceRotation != 0;
    }

    public final void mapRect(Rect rect, Rect out) {
        mapRect(rect.left, rect.top, rect.right, rect.bottom, out);
    }

    public void mapRect(int left, int top, int right, int bottom, Rect out) {
        out.set(left, top, right, bottom);
    }

    public void mapInsets(Context context, Rect insets, Rect out) {
        out.set(insets);
    }

    public int toNaturalGravity(int absoluteGravity) {
        return absoluteGravity;
    }
}
