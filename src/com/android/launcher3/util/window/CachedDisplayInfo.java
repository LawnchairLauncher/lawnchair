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
package com.android.launcher3.util.window;

import static com.android.launcher3.util.RotationUtils.deltaRotation;
import static com.android.launcher3.util.RotationUtils.rotateSize;

import android.graphics.Insets;
import android.graphics.Point;
import android.view.DisplayCutout;
import android.view.Surface;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Objects;

/**
 * Properties on a display
 */
public class CachedDisplayInfo {

    private static final DisplayCutout NO_CUTOUT =
            new DisplayCutout(Insets.NONE, null, null, null, null);

    public final Point size;
    public final int rotation;
    @NonNull
    public final DisplayCutout cutout;

    public CachedDisplayInfo() {
        this(new Point(0, 0), 0);
    }

    public CachedDisplayInfo(Point size, int rotation) {
        this(size, rotation, NO_CUTOUT);
    }

    public CachedDisplayInfo(Point size, int rotation, @Nullable DisplayCutout cutout) {
        this.size = size;
        this.rotation = rotation;
        this.cutout = cutout == null ? NO_CUTOUT : cutout;
    }

    /**
     * Returns a CachedDisplayInfo where the properties are normalized to {@link Surface#ROTATION_0}
     */
    public CachedDisplayInfo normalize(WindowManagerProxy windowManagerProxy) {
        if (rotation == Surface.ROTATION_0) {
            return this;
        }
        Point newSize = new Point(size);
        rotateSize(newSize, deltaRotation(rotation, Surface.ROTATION_0));

        DisplayCutout newCutout = windowManagerProxy.rotateCutout(
                cutout, size.x, size.y, rotation, Surface.ROTATION_0);
        return new CachedDisplayInfo(newSize, Surface.ROTATION_0, newCutout);
    }

    @Override
    public String toString() {
        return "CachedDisplayInfo{"
                + "size=" + size
                + ", cutout=" + cutout
                + ", rotation=" + rotation
                + '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CachedDisplayInfo)) return false;
        CachedDisplayInfo that = (CachedDisplayInfo) o;
        return rotation == that.rotation
                && Objects.equals(size, that.size)
                && cutout.getSafeInsetLeft() == that.cutout.getSafeInsetLeft()
                && cutout.getSafeInsetTop() == that.cutout.getSafeInsetTop()
                && cutout.getSafeInsetRight() == that.cutout.getSafeInsetRight()
                && cutout.getSafeInsetBottom() == that.cutout.getSafeInsetBottom();
    }

    @Override
    public int hashCode() {
        return Objects.hash(size, rotation,
                cutout.getSafeInsetLeft(), cutout.getSafeInsetTop(),
                cutout.getSafeInsetRight(), cutout.getSafeInsetBottom());
    }
}
