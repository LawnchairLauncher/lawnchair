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
package com.android.launcher3.states;

import android.view.animation.Interpolator;

import androidx.annotation.IntDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Utility class for building animator set
 */
public class StateAnimationConfig {

    @IntDef(flag = true, value = {
            SKIP_ALL_ANIMATIONS,
            SKIP_OVERVIEW,
            SKIP_DEPTH_CONTROLLER,
            SKIP_SCRIM,
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface AnimationFlags {}
    public static final int SKIP_ALL_ANIMATIONS = 1 << 0;
    public static final int SKIP_OVERVIEW = 1 << 1;
    public static final int SKIP_DEPTH_CONTROLLER = 1 << 2;
    public static final int SKIP_SCRIM = 1 << 3;

    public long duration;
    public boolean userControlled;
    public @AnimationFlags int animFlags = 0;


    // Various types of animation state transition
    @IntDef(value = {
            ANIM_VERTICAL_PROGRESS,
            ANIM_WORKSPACE_SCALE,
            ANIM_WORKSPACE_TRANSLATE,
            ANIM_WORKSPACE_FADE,
            ANIM_HOTSEAT_SCALE,
            ANIM_HOTSEAT_TRANSLATE,
            ANIM_HOTSEAT_FADE,
            ANIM_OVERVIEW_SCALE,
            ANIM_OVERVIEW_TRANSLATE_X,
            ANIM_OVERVIEW_TRANSLATE_Y,
            ANIM_OVERVIEW_FADE,
            ANIM_ALL_APPS_FADE,
            ANIM_SCRIM_FADE,
            ANIM_OVERVIEW_MODAL,
            ANIM_DEPTH,
            ANIM_OVERVIEW_ACTIONS_FADE,
            ANIM_WORKSPACE_PAGE_TRANSLATE_X,
            ANIM_OVERVIEW_SPLIT_SELECT_FLOATING_TASK_TRANSLATE_OFFSCREEN,
            ANIM_OVERVIEW_SPLIT_SELECT_INSTRUCTIONS_FADE,
            ANIM_ALL_APPS_BOTTOM_SHEET_FADE,
            ANIM_ALL_APPS_KEYBOARD_FADE
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface AnimType {}
    public static final int ANIM_VERTICAL_PROGRESS = 0;
    public static final int ANIM_WORKSPACE_SCALE = 1;
    public static final int ANIM_WORKSPACE_TRANSLATE = 2;
    public static final int ANIM_WORKSPACE_FADE = 3;
    public static final int ANIM_HOTSEAT_SCALE = 4;
    public static final int ANIM_HOTSEAT_TRANSLATE = 5;
    public static final int ANIM_HOTSEAT_FADE = 16;
    public static final int ANIM_OVERVIEW_SCALE = 6;
    public static final int ANIM_OVERVIEW_TRANSLATE_X = 7;
    public static final int ANIM_OVERVIEW_TRANSLATE_Y = 8;
    public static final int ANIM_OVERVIEW_FADE = 9;
    public static final int ANIM_ALL_APPS_FADE = 10;
    public static final int ANIM_SCRIM_FADE = 11;
    public static final int ANIM_OVERVIEW_MODAL = 12;
    public static final int ANIM_DEPTH = 13;
    public static final int ANIM_OVERVIEW_ACTIONS_FADE = 14;
    public static final int ANIM_WORKSPACE_PAGE_TRANSLATE_X = 15;
    public static final int ANIM_OVERVIEW_SPLIT_SELECT_FLOATING_TASK_TRANSLATE_OFFSCREEN = 17;
    public static final int ANIM_OVERVIEW_SPLIT_SELECT_INSTRUCTIONS_FADE = 18;
    public static final int ANIM_ALL_APPS_BOTTOM_SHEET_FADE = 19;
    public static final int ANIM_ALL_APPS_KEYBOARD_FADE = 20;

    private static final int ANIM_TYPES_COUNT = 21;

    protected final Interpolator[] mInterpolators = new Interpolator[ANIM_TYPES_COUNT];

    public StateAnimationConfig() { }

    /**
     * Copies the config to target
     */
    public void copyTo(StateAnimationConfig target) {
        target.duration = duration;
        target.animFlags = animFlags;
        target.userControlled = userControlled;
        for (int i = 0; i < ANIM_TYPES_COUNT; i++) {
            target.mInterpolators[i] = mInterpolators[i];
        }
    }

    /**
     * Returns the interpolator set for animId or fallback if nothing is set
     *
     * @see #setInterpolator(int, Interpolator)
     */
    public Interpolator getInterpolator(@AnimType int animId, Interpolator fallback) {
        return mInterpolators[animId] == null ? fallback : mInterpolators[animId];
    }

    /**
     * Sets an interpolator for a given animation type
     */
    public void setInterpolator(@AnimType int animId, Interpolator interpolator) {
        mInterpolators[animId] = interpolator;
    }

    /**
     * Returns true if the config and any of the provided component flags
     */
    public boolean hasAnimationFlag(@AnimationFlags int a) {
        return (animFlags & a) != 0;
    }
}
