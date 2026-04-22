/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.launcher3.util.launcheremulator;

import com.android.launcher3.util.launcheremulator.models.DeviceEmulationData;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Utility class to get the different densities used when going to setting and changing the density
 * of the display
 */
public class DensityPicker {

    private static final Density[] SUMMARIES_SMALLER = new Density[] { Density.SMALL };
    private static final Density[] SUMMARIES_LARGER = new Density[] {
            Density.LARGE, Density.LARGER, Density.LARGEST};

    /**
     * Defines the available densities to pick from
     */
    public enum Density {
        SMALL, NORMAL, LARGE, LARGER, LARGEST;

        @Override
        public String toString() {
            return name().toLowerCase(Locale.ENGLISH);
        }
    }

    /**
     * @return returns a map defining the different density for a given device, the map entries are
     * defined in {@code Densities}
     */
    public static Map<Density, Integer> getDisplayEntries(DeviceEmulationData device) {
        // Display logic copied from
        // packages/SettingsLib/src/com/android/settingslib/display/DisplayDensityUtils.java
        // Compute number of "larger" and "smaller" scales for this display.
        final int minDimensionPx = Math.min(device.width, device.height);
        final int maxDensity = minDimensionPx / 2;
        final float maxScale = Math.min(device.densityMaxScale,
                maxDensity / (float) device.density);
        final float minScale = device.densityMinScale;
        final float minScaleInterval = device.densityMinScaleInterval;
        final int defaultDensity = device.density;

        final int numLarger = (int) Math.max(0, Math.min((maxScale - 1) / minScaleInterval,
                SUMMARIES_LARGER.length));
        final int numSmaller = (int) Math.max(0, Math.min((1 - minScale) / minScaleInterval,
                SUMMARIES_SMALLER.length));

        Map<Density, Integer> displayEntries = new HashMap<>();
        displayEntries.put(Density.NORMAL, defaultDensity);
        if (numSmaller > 0) {
            final float interval = (1 - minScale) / numSmaller;
            for (int i = numSmaller - 1; i >= 0; i--) {
                // Round down to a multiple of 2 by truncating the low bit.
                final int density = ((int) (defaultDensity * (1 - (i + 1) * interval))) & ~1;
                displayEntries.put(SUMMARIES_SMALLER[i], density);
            }
        }

        if (numLarger > 0) {
            final float interval = (maxScale - 1) / numLarger;
            for (int i = 0; i < numLarger; i++) {
                // Round down to a multiple of 2 by truncating the low bit.
                final int density = ((int) (defaultDensity * (1 + (i + 1) * interval))) & ~1;
                displayEntries.put(SUMMARIES_LARGER[i], density);
            }
        }
        return displayEntries;
    }
}
