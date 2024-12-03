/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.systemui.plugins;

import android.graphics.Color;
import android.graphics.Rect;
import android.view.View;

import com.android.systemui.plugins.DarkIconDispatcher.DarkReceiver;
import com.android.systemui.plugins.annotations.DependsOn;
import com.android.systemui.plugins.annotations.ProvidesInterface;

import java.util.ArrayList;
import java.util.Collection;

/**
 * Dispatches events to {@link DarkReceiver}s about changes in darkness, tint area and dark
 * intensity. Accessible through {@link PluginDependency}
 */
@ProvidesInterface(version = DarkIconDispatcher.VERSION)
@DependsOn(target = DarkReceiver.class)
public interface DarkIconDispatcher {
    int VERSION = 2;

    /**
     * Sets the dark area so {@link #applyDark} only affects the icons in the specified area.
     *
     * @param r the areas in which icons should change its tint, in logical screen
     *                 coordinates
     */
    void setIconsDarkArea(ArrayList<Rect> r);

    /**
     * Adds a receiver to receive callbacks onDarkChanged
     */
    void addDarkReceiver(DarkReceiver receiver);

    /**
     * Must have been previously been added through one of the addDarkReceive methods above.
     */
    void removeDarkReceiver(DarkReceiver object);

    /**
     * Used to reapply darkness on an object, must have previously been added through
     * addDarkReceiver.
      */
    void applyDark(DarkReceiver object);

    /** The default tint (applicable for dark backgrounds) is white */
    int DEFAULT_ICON_TINT = Color.WHITE;
    /** To support an icon which wants to create contrast, the default tint is black-on-white. */
    int DEFAULT_INVERSE_ICON_TINT = Color.BLACK;

    Rect sTmpRect = new Rect();
    int[] sTmpInt2 = new int[2];

    /**
     * @return the tint to apply to view depending on the desired tint color and
     *         the screen tintArea in which to apply that tint
     */
    static int getTint(Collection<Rect> tintAreas, View view, int color) {
        if (isInAreas(tintAreas, view)) {
            return color;
        } else {
            return DEFAULT_ICON_TINT;
        }
    }

    /**
     * @return the tint to apply to a foreground, given that the background is tinted
     *         per {@link #getTint}
     */
    static int getInverseTint(Collection<Rect> tintAreas, View view, int inverseColor) {
        if (isInAreas(tintAreas, view)) {
            return inverseColor;
        } else {
            return DEFAULT_INVERSE_ICON_TINT;
        }
    }

    /**
     * @return true if more than half of the view's area is in any of the given area Rects, false
     *         otherwise
     */
    static boolean isInAreas(Collection<Rect> areas, View view) {
        if (areas.isEmpty()) {
            return true;
        }
        for (Rect area : areas) {
            if (isInArea(area, view)) {
                return true;
            }
        }
        return false;
    }

    /**
     * @return true if more than half of the viewBounds are in any of the given area Rects, false
     *         otherwise
     */
    static boolean isInAreas(Collection<Rect> areas, Rect viewBounds) {
        if (areas.isEmpty()) {
            return true;
        }
        for (Rect area : areas) {
            if (isInArea(area, viewBounds)) {
                return true;
            }
        }
        return false;
    }

    /** @return true if more than half of the viewBounds are in the area Rect, false otherwise */
    static boolean isInArea(Rect area, Rect viewBounds) {
        if (area.isEmpty()) {
            return true;
        }
        sTmpRect.set(area);
        int left = viewBounds.left;
        int width = viewBounds.width();

        int intersectStart = Math.max(left, area.left);
        int intersectEnd = Math.min(left + width, area.right);
        int intersectAmount = Math.max(0, intersectEnd - intersectStart);

        boolean coversFullStatusBar = area.top <= 0;
        boolean majorityOfWidth = 2 * intersectAmount > width;
        return majorityOfWidth && coversFullStatusBar;
    }

    /** @return true if more than half of the view's area is in the area Rect, false otherwise */
    static boolean isInArea(Rect area, View view) {
        if (area.isEmpty()) {
            return true;
        }
        sTmpRect.set(area);
        view.getLocationOnScreen(sTmpInt2);
        int left = sTmpInt2[0];

        int intersectStart = Math.max(left, area.left);
        int intersectEnd = Math.min(left + view.getWidth(), area.right);
        int intersectAmount = Math.max(0, intersectEnd - intersectStart);

        boolean coversFullStatusBar = area.top <= 0;
        boolean majorityOfWidth = 2 * intersectAmount > view.getWidth();
        return majorityOfWidth && coversFullStatusBar;
    }

    /**
     * Receives a callback on darkness changes
     */
    @ProvidesInterface(version = DarkReceiver.VERSION)
    interface DarkReceiver {
        int VERSION = 3;

        /**
         * @param areas list of regions on screen where the tint applies
         * @param darkIntensity float representing the level of tint. In the range [0,1]
         * @param tint the tint applicable as a foreground contrast to the dark regions. This value
         *             is interpolated between a default light and dark tone, and is therefore
         *             usable as-is, as long as the view is in one of the areas defined in
         *             {@code areas}.
         *
         * @see DarkIconDispatcher#isInArea(Rect, View) for utilizing {@code areas}
         *
         * Note: only one of {@link #onDarkChanged(ArrayList, float, int)} or
         * {@link #onDarkChangedWithContrast(ArrayList, int, int)} need to be implemented, as both
         * will be called in the same circumstances.
         */
        void onDarkChanged(ArrayList<Rect> areas, float darkIntensity, int tint);

        /**
         * New version of onDarkChanged, which describes a tint plus an optional contrastTint
         * that can be used if the tint is applied to the background of an icon.
         *
         * We use the 2 here to avoid the case where an existing override of onDarkChanged
         * might pass in parameters as bare numbers (e.g. 0 instead of 0f) which might get
         * mistakenly cast to (int) and therefore trigger this method.
         *
         * @param areas list of areas where dark tint applies
         * @param tint int describing the tint color to use
         * @param contrastTint if desired, a contrasting color that can be used for a foreground
         *
         * Note: only one of {@link #onDarkChanged(ArrayList, float, int)} or
         * {@link #onDarkChangedWithContrast(ArrayList, int, int)} need to be implemented, as both
         * will be called in the same circumstances.
         */
        default void onDarkChangedWithContrast(ArrayList<Rect> areas, int tint, int contrastTint) {}
    }
}
