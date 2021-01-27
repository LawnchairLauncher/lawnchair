/*
 * Copyright (C) 2020 The Android Open Source Project
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

import android.graphics.Point;
import android.graphics.Rect;

import androidx.annotation.Nullable;

/**
 * Utility class to hold information about window position and layout
 */
public class WindowBounds {

    public final Rect bounds;
    public final Rect insets;
    public final Point availableSize;

    public WindowBounds(Rect bounds, Rect insets) {
        this.bounds = bounds;
        this.insets = insets;
        availableSize = new Point(bounds.width() - insets.left - insets.right,
                bounds.height() - insets.top - insets.bottom);
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if (!(obj instanceof WindowBounds)) {
            return false;
        }
        WindowBounds other = (WindowBounds) obj;
        return other.bounds.equals(bounds) && other.insets.equals(insets);
    }
}
