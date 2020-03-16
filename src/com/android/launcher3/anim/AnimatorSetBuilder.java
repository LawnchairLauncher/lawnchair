/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.launcher3.anim;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.util.SparseArray;
import android.view.animation.Interpolator;

import java.util.ArrayList;
import java.util.List;

/**
 * Utility class for building animator set
 */
public class AnimatorSetBuilder {

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

    public static final int FLAG_DONT_ANIMATE_OVERVIEW = 1 << 0;

    protected final ArrayList<Animator> mAnims = new ArrayList<>();

    private final SparseArray<Interpolator> mInterpolators = new SparseArray<>();
    private List<Runnable> mOnFinishRunnables = new ArrayList<>();
    private int mFlags = 0;

    public void play(Animator anim) {
        mAnims.add(anim);
    }

    public void addOnFinishRunnable(Runnable onFinishRunnable) {
        mOnFinishRunnables.add(onFinishRunnable);
    }

    public AnimatorSet build() {
        AnimatorSet anim = new AnimatorSet();
        anim.playTogether(mAnims);
        if (!mOnFinishRunnables.isEmpty()) {
            anim.addListener(new AnimationSuccessListener() {
                @Override
                public void onAnimationSuccess(Animator animation) {
                    for (Runnable onFinishRunnable : mOnFinishRunnables) {
                        onFinishRunnable.run();
                    }
                    mOnFinishRunnables.clear();
                }
            });
        }
        return anim;
    }

    public Interpolator getInterpolator(int animId, Interpolator fallback) {
        return mInterpolators.get(animId, fallback);
    }

    public void setInterpolator(int animId, Interpolator interpolator) {
        mInterpolators.put(animId, interpolator);
    }

    public void addFlag(int flag) {
        mFlags |= flag;
    }

    public boolean hasFlag(int flag) {
        return (mFlags & flag) != 0;
    }
}
