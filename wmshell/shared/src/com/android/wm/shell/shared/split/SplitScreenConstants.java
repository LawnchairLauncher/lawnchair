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
package com.android.wm.shell.shared.split;

import static android.app.WindowConfiguration.ACTIVITY_TYPE_STANDARD;
import static android.app.WindowConfiguration.WINDOWING_MODE_FREEFORM;
import static android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN;
import static android.app.WindowConfiguration.WINDOWING_MODE_MULTI_WINDOW;
import static android.app.WindowConfiguration.WINDOWING_MODE_UNDEFINED;

import android.annotation.IntDef;

import com.android.wm.shell.shared.TransitionUtil;

/** Helper utility class of methods and constants that are available to be imported in Launcher. */
public class SplitScreenConstants {
    /** Duration used for every split fade-in or fade-out. */
    public static final int FADE_DURATION = 133;
    /** Duration where we keep an app veiled to allow it to redraw itself behind the scenes. */
    public static final int VEIL_DELAY_DURATION = 300;

    /** Key for passing in widget intents when invoking split from launcher workspace. */
    public static final String KEY_EXTRA_WIDGET_INTENT = "key_extra_widget_intent";

    ///////////////
    // IMPORTANT for the following SPLIT_POSITION and SNAP_TO constants:
    // These int values must not be changed -- they are persisted to user-defined app pairs, and
    // will break things if changed.
    //

    /**
     * Split position isn't specified normally meaning to use what ever it is currently set to.
     */
    public static final int SPLIT_POSITION_UNDEFINED = -1;

    /**
     * Specifies that a split is positioned at the top half of the screen if
     * in portrait mode or at the left half of the screen if in landscape mode.
     */
    public static final int SPLIT_POSITION_TOP_OR_LEFT = 0;

    /**
     * Specifies that a split is positioned at the bottom half of the screen if
     * in portrait mode or at the right half of the screen if in landscape mode.
     */
    public static final int SPLIT_POSITION_BOTTOM_OR_RIGHT = 1;

    /**
     * Deprecated and will be replaced fully by @SplitIndex. With support for 3+ apps in split,
     * existing references to top/left and bottom/right will be replaced by INDEX_0 and INDEX_1
     * respectively. For now they can be used interchangeably, the underlying ints are the same.
     */
    @IntDef(prefix = {"SPLIT_POSITION_"}, value = {
            SPLIT_POSITION_UNDEFINED,
            SPLIT_POSITION_TOP_OR_LEFT,
            SPLIT_POSITION_BOTTOM_OR_RIGHT,
    })
    public @interface SplitPosition {
    }

    // These SPLIT_INDEX constants will be used in the same way as the above SPLIT_POSITION ints,
    // but scalable to n apps. Eventually, SPLIT_POSITION can be deprecated and only the below
    // will be used.
    public static final int SPLIT_INDEX_UNDEFINED = -1;
    public static final int SPLIT_INDEX_0 = 0;
    public static final int SPLIT_INDEX_1 = 1;
    public static final int SPLIT_INDEX_2 = 2;
    public static final int SPLIT_INDEX_3 = 3;

    @IntDef(prefix = {"SPLIT_INDEX_"}, value = {
            SPLIT_INDEX_UNDEFINED,
            SPLIT_INDEX_0,
            SPLIT_INDEX_1,
            SPLIT_INDEX_2,
            SPLIT_INDEX_3
    })
    public @interface SplitIndex {
    }

    /**
     * Return the @SplitIndex constant for a given integer index. @SplitIndex is the replacement
     * for @SplitPosition, and will be used interchangeably with @SplitPosition to support 3+ apps
     * in split.
     */
    public static int getIndex(int i) {
        return switch (i) {
            case 0 -> SPLIT_INDEX_0;
            case 1 -> SPLIT_INDEX_1;
            case 2 -> SPLIT_INDEX_2;
            case 3 -> SPLIT_INDEX_3;
            default -> SPLIT_INDEX_UNDEFINED;
        };
    }

    /** Signifies that user is currently not in split screen. */
    public static final int NOT_IN_SPLIT = -1;

    /**
     * A snap target for two apps, where the split is 33-66. With FLAG_ENABLE_FLEXIBLE_SPLIT,
     * only used on tablets.
     */
    public static final int SNAP_TO_2_33_66 = 0;

    /** A snap target for two apps, where the split is 50-50.  */
    public static final int SNAP_TO_2_50_50 = 1;

    /**
     * A snap target for two apps, where the split is 66-33. With FLAG_ENABLE_FLEXIBLE_SPLIT,
     * only used on tablets.
     */
    public static final int SNAP_TO_2_66_33 = 2;

    /**
     * A snap target for two apps, where the split is 90-10. The "10" app extends off the screen,
     * and is actually the same size as the onscreen app, but the visible portion takes up 10% of
     * the screen. With FLAG_ENABLE_FLEXIBLE_SPLIT, used on phones and foldables.
     */
    public static final int SNAP_TO_2_90_10 = 3;

    /**
     * A snap target for two apps, where the split is 10-90. The "10" app extends off the screen,
     * and is actually the same size as the onscreen app, but the visible portion takes up 10% of
     * the screen. With FLAG_ENABLE_FLEXIBLE_SPLIT, used on phones and foldables.
     */
    public static final int SNAP_TO_2_10_90 = 4;

    /**
     * A snap target for three apps, where the split is 33-33-33. With FLAG_ENABLE_FLEXIBLE_SPLIT,
     * only used on tablets.
     */
    public static final int SNAP_TO_3_33_33_33 = 5;

    /**
     * A snap target for three apps, where the split is 45-45-10. The "10" app extends off the
     * screen, and is actually the same size as the onscreen apps, but the visible portion takes
     * up 10% of the screen. With FLAG_ENABLE_FLEXIBLE_SPLIT, only used on unfolded foldables.
     */
    public static final int SNAP_TO_3_45_45_10 = 6;

    /**
     * A snap target for three apps, where the split is 10-45-45. The "10" app extends off the
     * screen, and is actually the same size as the onscreen apps, but the visible portion takes
     * up 10% of the screen. With FLAG_ENABLE_FLEXIBLE_SPLIT, only used on unfolded foldables.
     */
    public static final int SNAP_TO_3_10_45_45 = 7;

    /**
     * These snap targets are used for split pairs in a stable, non-transient state. They may be
     * persisted in Launcher when the user saves an app pair. They are a subset of
     * {@link SnapPosition}.
     */
    @IntDef(prefix = { "SNAP_TO_" }, value = {
            SNAP_TO_2_33_66,
            SNAP_TO_2_50_50,
            SNAP_TO_2_66_33,
            SNAP_TO_2_90_10,
            SNAP_TO_2_10_90,
            SNAP_TO_3_33_33_33,
            SNAP_TO_3_45_45_10,
            SNAP_TO_3_10_45_45,
    })
    public @interface PersistentSnapPosition {}

    /**
     * These are all the valid "states" that split screen can be in. It's the set of
     * {@link PersistentSnapPosition} + {@link #NOT_IN_SPLIT}.
     */
    @IntDef(value = {
            NOT_IN_SPLIT, // user is not in split screen
            SNAP_TO_NONE, // in "free snap mode," where apps are fully resizable
            SNAP_TO_2_33_66,
            SNAP_TO_2_50_50,
            SNAP_TO_2_66_33,
            SNAP_TO_2_90_10,
            SNAP_TO_2_10_90,
            SNAP_TO_3_33_33_33,
            SNAP_TO_3_45_45_10,
            SNAP_TO_3_10_45_45,
    })
    public @interface SplitScreenState {}

    /** Converts a {@link SplitScreenState} to a human-readable string. */
    public static String stateToString(@SplitScreenState int state) {
        return switch (state) {
            case NOT_IN_SPLIT -> "NOT_IN_SPLIT";
            case SNAP_TO_NONE -> "SNAP_TO_NONE";
            case SNAP_TO_2_33_66 -> "SNAP_TO_2_33_66";
            case SNAP_TO_2_50_50 -> "SNAP_TO_2_50_50";
            case SNAP_TO_2_66_33 -> "SNAP_TO_2_66_33";
            case SNAP_TO_2_90_10 -> "SNAP_TO_2_90_10";
            case SNAP_TO_2_10_90 -> "SNAP_TO_2_10_90";
            case SNAP_TO_3_33_33_33 -> "SNAP_TO_3_33_33_33";
            case SNAP_TO_3_45_45_10 -> "SNAP_TO_3_45_45_10";
            case SNAP_TO_3_10_45_45 -> "SNAP_TO_3_10_45_45";
            default -> "UNKNOWN";
        };
    }

    /**
     * Checks if the snapPosition in question is a {@link PersistentSnapPosition}.
     */
    public static boolean isPersistentSnapPosition(@SnapPosition int snapPosition) {
        return snapPosition == SNAP_TO_2_33_66
                || snapPosition == SNAP_TO_2_50_50
                || snapPosition == SNAP_TO_2_66_33
                || snapPosition == SNAP_TO_2_90_10
                || snapPosition == SNAP_TO_2_10_90
                || snapPosition == SNAP_TO_3_33_33_33
                || snapPosition == SNAP_TO_3_45_45_10
                || snapPosition == SNAP_TO_3_10_45_45;
    }

    /** The divider doesn't snap to any target and is freely placeable. */
    public static final int SNAP_TO_NONE = 10;

    /** If the divider reaches this value, the left/top task should be dismissed. */
    public static final int SNAP_TO_START_AND_DISMISS = 11;

    /** If the divider reaches this value, the right/bottom task should be dismissed. */
    public static final int SNAP_TO_END_AND_DISMISS = 12;

    /** A snap target positioned near the screen edge for a minimized task */
    public static final int SNAP_TO_MINIMIZE = 13;

    @IntDef(prefix = { "SNAP_TO_" }, value = {
            SNAP_TO_2_33_66,
            SNAP_TO_2_50_50,
            SNAP_TO_2_66_33,
            SNAP_TO_2_90_10,
            SNAP_TO_2_10_90,
            SNAP_TO_3_33_33_33,
            SNAP_TO_3_45_45_10,
            SNAP_TO_3_10_45_45,
            SNAP_TO_NONE,
            SNAP_TO_START_AND_DISMISS,
            SNAP_TO_END_AND_DISMISS,
            SNAP_TO_MINIMIZE,
    })
    public @interface SnapPosition {}

    ///////////////

    public static final int[] CONTROLLED_ACTIVITY_TYPES = {ACTIVITY_TYPE_STANDARD};
    public static final int[] CONTROLLED_WINDOWING_MODES =
            {WINDOWING_MODE_FULLSCREEN, WINDOWING_MODE_UNDEFINED};
    public static final int[] CONTROLLED_WINDOWING_MODES_WHEN_ACTIVE =
            {WINDOWING_MODE_FULLSCREEN, WINDOWING_MODE_UNDEFINED, WINDOWING_MODE_MULTI_WINDOW,
            WINDOWING_MODE_FREEFORM};

    /** Flag applied to a transition change to identify it as a divider bar for animation. */
    public static final int FLAG_IS_DIVIDER_BAR = TransitionUtil.FLAG_IS_DIVIDER_BAR;
    public static final int FLAG_IS_DIM_LAYER = TransitionUtil.FLAG_IS_DIM_LAYER;

    public static final String splitPositionToString(@SplitPosition int pos) {
        switch (pos) {
            case SPLIT_POSITION_UNDEFINED:
                return "SPLIT_POSITION_UNDEFINED";
            case SPLIT_POSITION_TOP_OR_LEFT:
                return "SPLIT_POSITION_TOP_OR_LEFT";
            case SPLIT_POSITION_BOTTOM_OR_RIGHT:
                return "SPLIT_POSITION_BOTTOM_OR_RIGHT";
            default:
                return "UNKNOWN";
        }
    }
}
