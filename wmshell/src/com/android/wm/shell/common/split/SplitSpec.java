/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.wm.shell.common.split;

import static com.android.wm.shell.shared.split.SplitScreenConstants.SNAP_TO_2_10_90;
import static com.android.wm.shell.shared.split.SplitScreenConstants.SNAP_TO_2_33_66;
import static com.android.wm.shell.shared.split.SplitScreenConstants.SNAP_TO_2_50_50;
import static com.android.wm.shell.shared.split.SplitScreenConstants.SNAP_TO_2_66_33;
import static com.android.wm.shell.shared.split.SplitScreenConstants.SNAP_TO_2_90_10;
import static com.android.wm.shell.shared.split.SplitScreenConstants.SNAP_TO_3_10_45_45;
import static com.android.wm.shell.shared.split.SplitScreenConstants.SNAP_TO_3_33_33_33;
import static com.android.wm.shell.shared.split.SplitScreenConstants.SNAP_TO_3_45_45_10;
import static com.android.wm.shell.shared.split.SplitScreenConstants.stateToString;

import android.graphics.Rect;
import android.graphics.RectF;
import android.util.Log;

import com.android.wm.shell.shared.split.SplitScreenConstants.SplitScreenState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A reference class that stores the split layouts available in this device/orientation. Layouts are
 * available as lists of RectFs, where each RectF represents the bounds of an app.
 */
public class SplitSpec {
    private static final String TAG = "SplitSpec";
    private static final boolean DEBUG = false;

    /** A split ratio used on larger screens, where we can fit both apps onscreen. */
    public static final float ONSCREEN_ONLY_ASYMMETRIC_RATIO = 0.33f;
    /** A split ratio used on smaller screens, where we place one app mostly offscreen. */
    public static final float OFFSCREEN_ASYMMETRIC_RATIO = 0.1f;
    /** A 50-50 split ratio. */
    public static final float MIDDLE_RATIO = 0.5f;

    private final boolean mIsLeftRightSplit;
    /** The physical size of the display. */
    private final Rect mDisplayBounds;
    /** The usable display area, considering insets that affect split bounds. */
    private final RectF mUsableArea;
    /** Half the divider size. */
    private final float mHalfDiv;

    /** A large map that stores all valid split layouts. */
    private final Map<Integer, List<RectF>> mLayouts = new HashMap<>();

    /** Constructor; initializes the layout map. */
    public SplitSpec(Rect displayBounds, int dividerSize, boolean isLeftRightSplit,
            Rect pinnedTaskbarInsets) {
        mIsLeftRightSplit = isLeftRightSplit;
        mDisplayBounds = new Rect(displayBounds);
        mUsableArea = new RectF(displayBounds);
        mUsableArea.left += pinnedTaskbarInsets.left;
        mUsableArea.top += pinnedTaskbarInsets.top;
        mUsableArea.right -= pinnedTaskbarInsets.right;
        mUsableArea.bottom -= pinnedTaskbarInsets.bottom;
        mHalfDiv = dividerSize / 2f;

        // The "start" position, considering insets.
        float s = isLeftRightSplit ? mUsableArea.left : mUsableArea.top;
        // The "end" position, considering insets.
        float e = isLeftRightSplit ? mUsableArea.right : mUsableArea.bottom;
        // The "length" of the usable display (width or height). Apps are arranged along this axis.
        float l = e - s;
        float divPos;
        float divPos2;

        // SNAP_TO_2_10_90
        divPos = s + (l * OFFSCREEN_ASYMMETRIC_RATIO);
        createAppLayout(SNAP_TO_2_10_90, divPos);

        // SNAP_TO_2_33_66
        divPos = s + (l * ONSCREEN_ONLY_ASYMMETRIC_RATIO);
        createAppLayout(SNAP_TO_2_33_66, divPos);

        // SNAP_TO_2_50_50
        divPos = s + (l * MIDDLE_RATIO);
        createAppLayout(SNAP_TO_2_50_50, divPos);

        // SNAP_TO_2_66_33
        divPos = s + (l * (1 - ONSCREEN_ONLY_ASYMMETRIC_RATIO));
        createAppLayout(SNAP_TO_2_66_33, divPos);

        // SNAP_TO_2_90_10
        divPos = s + (l * (1 - OFFSCREEN_ASYMMETRIC_RATIO));
        createAppLayout(SNAP_TO_2_90_10, divPos);

        // SNAP_TO_3_10_45_45
        divPos = s + (l * OFFSCREEN_ASYMMETRIC_RATIO);
        divPos2 = e - ((l * (1 - OFFSCREEN_ASYMMETRIC_RATIO)) / 2f);
        createAppLayout(SNAP_TO_3_10_45_45, divPos, divPos2);

        // SNAP_TO_3_33_33_33
        divPos = s + (l * ONSCREEN_ONLY_ASYMMETRIC_RATIO);
        divPos2 = e - (l * ONSCREEN_ONLY_ASYMMETRIC_RATIO);
        createAppLayout(SNAP_TO_3_33_33_33, divPos, divPos2);

        // SNAP_TO_3_45_45_10
        divPos = s + ((l * (1 - OFFSCREEN_ASYMMETRIC_RATIO)) / 2f);
        divPos2 = e - (l * OFFSCREEN_ASYMMETRIC_RATIO);
        createAppLayout(SNAP_TO_3_45_45_10, divPos, divPos2);

        if (DEBUG) {
            dump();
        }
    }

    /**
     * Creates a two-app layout and enters it into the layout map.
     * @param divPos The position of the divider.
     */
    private void createAppLayout(@SplitScreenState int state, float divPos) {
        List<RectF> list = new ArrayList<>();
        RectF rect1 = new RectF(mUsableArea);
        RectF rect2 = new RectF(mUsableArea);
        if (mIsLeftRightSplit) {
            rect1.right = divPos - mHalfDiv;
            rect2.left = divPos + mHalfDiv;
        } else {
            rect1.top = divPos - mHalfDiv;
            rect2.bottom = divPos + mHalfDiv;
        }
        list.add(rect1);
        list.add(rect2);
        mLayouts.put(state, list);
    }

    /**
     * Creates a three-app layout and enters it into the layout map.
     * @param divPos1 The position of the first divider.
     * @param divPos2 The position of the second divider.
     */
    private void createAppLayout(@SplitScreenState int state, float divPos1, float divPos2) {
        List<RectF> list = new ArrayList<>();
        RectF rect1 = new RectF(mUsableArea);
        RectF rect2 = new RectF(mUsableArea);
        RectF rect3 = new RectF(mUsableArea);
        if (mIsLeftRightSplit) {
            rect1.right = divPos1 - mHalfDiv;
            rect2.left = divPos1 + mHalfDiv;
            rect2.right = divPos2 - mHalfDiv;
            rect3.left = divPos2 + mHalfDiv;
        } else {
            rect1.right = divPos1 - mHalfDiv;
            rect2.left = divPos1 + mHalfDiv;
            rect3.right = divPos2 - mHalfDiv;
            rect3.left = divPos2 + mHalfDiv;
        }
        list.add(rect1);
        list.add(rect2);
        list.add(rect3);
        mLayouts.put(state, list);
    }

    /** Logs all calculated layouts */
    private void dump() {
        mLayouts.forEach((k, v) -> {
            Log.d(TAG, stateToString(k));
            v.forEach(rect -> Log.d(TAG, " - " + rect.toShortString()));
        });
    }

    /** Returns the layout associated with a given split state. */
    List<RectF> getSpec(@SplitScreenState int state) {
        return mLayouts.get(state);
    }

    /** Returns whether a given Rect is partially offscreen on the current display. */
    boolean isOffscreen(Rect rect) {
        return !mDisplayBounds.contains(rect);
    }
}
