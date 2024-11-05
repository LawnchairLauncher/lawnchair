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

import android.graphics.Insets;
import android.graphics.Point;
import android.graphics.Rect;
import android.view.WindowInsets.Type;
import android.view.WindowMetrics;

import androidx.annotation.Nullable;

import java.util.Objects;

/**
 * Utility class to hold information about window position and layout
 */
public class WindowBounds {

    public final Rect bounds;
    public final Rect insets;
    public final Point availableSize;
    public final int rotationHint;

    public WindowBounds(Rect bounds, Rect insets) {
        this(bounds, insets, -1);
    }

    public WindowBounds(Rect bounds, Rect insets, int rotationHint) {
        this.bounds = bounds;
        this.insets = insets;
        this.rotationHint = rotationHint;
        availableSize = new Point(bounds.width() - insets.left - insets.right,
                bounds.height() - insets.top - insets.bottom);
    }

    public WindowBounds(int width, int height, int availableWidth, int availableHeight,
            int rotationHint) {
        this.bounds = new Rect(0, 0, width, height);
        this.availableSize = new Point(availableWidth, availableHeight);
        // We don't care about insets in this case
        this.insets = new Rect(0, 0, width - availableWidth, height - availableHeight);
        this.rotationHint = rotationHint;
    }

    @Override
    public int hashCode() {
        return Objects.hash(bounds, insets);
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if (!(obj instanceof WindowBounds)) {
            return false;
        }
        WindowBounds other = (WindowBounds) obj;
        return other.bounds.equals(bounds) && other.insets.equals(insets)
                && other.rotationHint == rotationHint;
    }

    @Override
    public String toString() {
        return "WindowBounds{"
                + "bounds=" + bounds
                + ", insets=" + insets
                + ", availableSize=" + availableSize
                + ", rotationHint=" + rotationHint
                + '}';
    }

    /**
     * Returns true if the device is in landscape orientation
     */
    public final boolean isLandscape() {
        return bounds.width() > bounds.height();
    }

    /**
     * Returns the bounds corresponding to the provided WindowMetrics
     */
    @SuppressWarnings("NewApi")
    public static WindowBounds fromWindowMetrics(WindowMetrics wm) {
        Insets insets = wm.getWindowInsets().getInsets(Type.systemBars());
        return new WindowBounds(wm.getBounds(),
                new Rect(insets.left, insets.top, insets.right, insets.bottom));
    }
}
