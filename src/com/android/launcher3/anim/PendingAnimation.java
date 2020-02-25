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
package com.android.launcher3.anim;

import static com.android.launcher3.anim.AnimatorPlaybackController.addAnimationHoldersRecur;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.TimeInterpolator;
import android.annotation.TargetApi;
import android.os.Build;

import com.android.launcher3.anim.AnimatorPlaybackController.Holder;

import java.util.ArrayList;
import java.util.function.Consumer;

/**
 * Utility class to keep track of a running animation.
 *
 * This class allows attaching end callbacks to an animation is intended to be used with
 * {@link com.android.launcher3.anim.AnimatorPlaybackController}, since in that case
 * AnimationListeners are not properly dispatched.
 *
 * TODO: Find a better name
 */
@TargetApi(Build.VERSION_CODES.O)
public class PendingAnimation {

    private final ArrayList<Consumer<EndState>> mEndListeners = new ArrayList<>();

    /** package private **/
    final AnimatorSet anim = new AnimatorSet();
    final ArrayList<Holder> animHolders = new ArrayList<>();

    /**
     * Utility method to sent an interpolator on an animation and add it to the list
     */
    public void add(Animator anim, TimeInterpolator interpolator) {
        add(anim, interpolator, SpringProperty.DEFAULT);
    }

    public void add(Animator anim, TimeInterpolator interpolator, SpringProperty springProperty) {
        anim.setInterpolator(interpolator);
        add(anim, springProperty);
    }

    public void add(Animator anim) {
        add(anim, SpringProperty.DEFAULT);
    }

    public void add(Animator a, SpringProperty springProperty) {
        anim.play(a);
        addAnimationHoldersRecur(a, springProperty, animHolders);
    }

    public void finish(boolean isSuccess, int logAction) {
        for (Consumer<EndState> listeners : mEndListeners) {
            listeners.accept(new EndState(isSuccess, logAction));
        }
        mEndListeners.clear();
    }

    public void addEndListener(Consumer<EndState> listener) {
        mEndListeners.add(listener);
    }

    public static class EndState {
        public boolean isSuccess;
        public int logAction;

        public EndState(boolean isSuccess, int logAction) {
            this.isSuccess = isSuccess;
            this.logAction = logAction;
        }
    }
}
