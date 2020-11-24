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

    // We separate the state animations into "atomic" and "non-atomic" components. The atomic
    // components may be run atomically - that is, all at once, instead of user-controlled. However,
    // atomic components are not restricted to this purpose; they can be user-controlled alongside
    // non atomic components as well. Note that each gesture model has exactly one atomic component,
    // PLAY_ATOMIC_OVERVIEW_SCALE *or* PLAY_ATOMIC_OVERVIEW_PEEK.
    @IntDef(flag = true, value = {
            PLAY_NON_ATOMIC,
            PLAY_ATOMIC_OVERVIEW_SCALE,
            PLAY_ATOMIC_OVERVIEW_PEEK,
            SKIP_OVERVIEW,
            SKIP_DEPTH_CONTROLLER
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface AnimationFlags {}
    public static final int PLAY_NON_ATOMIC = 1 << 0;
    public static final int PLAY_ATOMIC_OVERVIEW_SCALE = 1 << 1;
    public static final int PLAY_ATOMIC_OVERVIEW_PEEK = 1 << 2;
    public static final int SKIP_OVERVIEW = 1 << 3;
    public static final int SKIP_DEPTH_CONTROLLER = 1 << 4;

    public long duration;
    public boolean userControlled;
    public @AnimationFlags int animFlags = ANIM_ALL_COMPONENTS;

    public static final int ANIM_ALL_COMPONENTS = PLAY_NON_ATOMIC | PLAY_ATOMIC_OVERVIEW_SCALE
            | PLAY_ATOMIC_OVERVIEW_PEEK;

    // Various types of animation state transition
    @IntDef(value = {
            ANIM_VERTICAL_PROGRESS,
            ANIM_WORKSPACE_SCALE,
            ANIM_WORKSPACE_TRANSLATE,
            ANIM_WORKSPACE_FADE,
            ANIM_HOTSEAT_SCALE,
            ANIM_HOTSEAT_TRANSLATE,
            ANIM_OVERVIEW_SCALE,
            ANIM_OVERVIEW_TRANSLATE_X,
            ANIM_OVERVIEW_TRANSLATE_Y,
            ANIM_OVERVIEW_FADE,
            ANIM_ALL_APPS_FADE,
            ANIM_OVERVIEW_SCRIM_FADE,
            ANIM_ALL_APPS_HEADER_FADE,
            ANIM_OVERVIEW_MODAL,
            ANIM_DEPTH,
            ANIM_OVERVIEW_ACTIONS_FADE,
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface AnimType {}
    public static final int ANIM_VERTICAL_PROGRESS = 0;
    public static final int ANIM_WORKSPACE_SCALE = 1;
    public static final int ANIM_WORKSPACE_TRANSLATE = 2;
    public static final int ANIM_WORKSPACE_FADE = 3;
    public static final int ANIM_HOTSEAT_SCALE = 4;
    public static final int ANIM_HOTSEAT_TRANSLATE = 5;
    public static final int ANIM_OVERVIEW_SCALE = 6;
    public static final int ANIM_OVERVIEW_TRANSLATE_X = 7;
    public static final int ANIM_OVERVIEW_TRANSLATE_Y = 8;
    public static final int ANIM_OVERVIEW_FADE = 9;
    public static final int ANIM_ALL_APPS_FADE = 10;
    public static final int ANIM_OVERVIEW_SCRIM_FADE = 11;
    public static final int ANIM_ALL_APPS_HEADER_FADE = 12; // e.g. predictions
    public static final int ANIM_OVERVIEW_MODAL = 13;
    public static final int ANIM_DEPTH = 14;
    public static final int ANIM_OVERVIEW_ACTIONS_FADE = 15;

    private static final int ANIM_TYPES_COUNT = 16;

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
     * @return Whether Overview is scaling as part of this animation. If this is the only
     * component (i.e. NON_ATOMIC_COMPONENT isn't included), then this scaling is happening
     * atomically, rather than being part of a normal state animation. StateHandlers can use
     * this to designate part of their animation that should scale with Overview.
     */
    public boolean playAtomicOverviewScaleComponent() {
        return hasAnimationFlag(StateAnimationConfig.PLAY_ATOMIC_OVERVIEW_SCALE);
    }

    /**
     * @return Whether this animation will play atomically at the same time as a different,
     * user-controlled state transition. StateHandlers, which contribute to both animations, can
     * use this to avoid animating the same properties in both animations, since they'd conflict
     * with one another.
     */
    public boolean onlyPlayAtomicComponent() {
        return getAnimComponents() == StateAnimationConfig.PLAY_ATOMIC_OVERVIEW_SCALE
                || getAnimComponents() == StateAnimationConfig.PLAY_ATOMIC_OVERVIEW_PEEK;
    }

    /**
     * Returns true if the config and any of the provided component flags
     */
    public boolean hasAnimationFlag(@AnimationFlags int a) {
        return (animFlags & a) != 0;
    }

    /**
     * @return Only the flags that determine which animation components to play.
     */
    public @AnimationFlags int getAnimComponents() {
        return animFlags & StateAnimationConfig.ANIM_ALL_COMPONENTS;
    }
}
