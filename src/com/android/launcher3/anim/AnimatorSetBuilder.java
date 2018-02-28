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

import com.android.launcher3.LauncherAnimUtils;

import java.util.ArrayList;

/**
 * Utility class for building animator set
 */
public class AnimatorSetBuilder {

    protected final ArrayList<Animator> mAnims = new ArrayList<>();
    private long mStartDelay = 0;

    /**
     * Associates a tag with all the animations added after this call.
     */
    public void startTag(Object obj) { }

    public void play(Animator anim) {
        mAnims.add(anim);
    }

    public void setStartDelay(long startDelay) {
        mStartDelay = startDelay;
    }

    public AnimatorSet build() {
        AnimatorSet anim = LauncherAnimUtils.createAnimatorSet();
        anim.playTogether(mAnims);
        anim.setStartDelay(mStartDelay);
        return anim;
    }
}
