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
import static com.android.launcher3.util.RotationUtils.rotateRect;
import static com.android.launcher3.util.RotationUtils.rotateSize;

import android.graphics.Point;
import android.graphics.Rect;
import android.view.Surface;

import java.util.Objects;

/**
 * Properties on a display
 */
public class CachedDisplayInfo {

    public final Point size;
    public final int rotation;
    public final Rect cutout;

    public CachedDisplayInfo() {
        this(new Point(0, 0), 0);
    }

    public CachedDisplayInfo(Point size, int rotation) {
        this(size, rotation, new Rect());
    }

    public CachedDisplayInfo(Point size, int rotation, Rect cutout) {
        this.size = size;
        this.rotation = rotation;
        this.cutout = cutout;
    }

    /**
     * Returns a CachedDisplayInfo where the properties are normalized to {@link Surface#ROTATION_0}
     */
    public CachedDisplayInfo normalize() {
        if (rotation == Surface.ROTATION_0) {
            return this;
        }
        Point newSize = new Point(size);
        rotateSize(newSize, deltaRotation(rotation, Surface.ROTATION_0));

        Rect newCutout = new Rect(cutout);
        rotateRect(newCutout, deltaRotation(rotation, Surface.ROTATION_0));
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
                && Objects.equals(cutout, that.cutout);
    }

    @Override
    public int hashCode() {
        return Objects.hash(size, rotation, cutout);
    }
}
