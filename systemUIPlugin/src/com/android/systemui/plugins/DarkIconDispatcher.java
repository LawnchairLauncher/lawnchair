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
import android.widget.ImageView;

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
     * Adds a receiver to receive callbacks onDarkChanged
     */
    void addDarkReceiver(ImageView imageView);

    /**
     * Must have been previously been added through one of the addDarkReceive methods above.
     */
    void removeDarkReceiver(DarkReceiver object);

    /**
     * Must have been previously been added through one of the addDarkReceive methods above.
     */
    void removeDarkReceiver(ImageView object);

    /**
     * Used to reapply darkness on an object, must have previously been added through
     * addDarkReceiver.
      */
    void applyDark(DarkReceiver object);

    int DEFAULT_ICON_TINT = Color.WHITE;
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
     * @return true if more than half of the view area are in any of the given
     *         areas, false otherwise
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
     * @return true if more than half of the view area are in area, false
     *         otherwise
     */
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
        int VERSION = 2;
        void onDarkChanged(ArrayList<Rect> areas, float darkIntensity, int tint);
    }
}
