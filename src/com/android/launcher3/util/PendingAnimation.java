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
package com.android.launcher3.util;

import android.animation.AnimatorSet;
import android.annotation.TargetApi;
import android.os.Build;

import java.util.ArrayList;
import java.util.function.Consumer;

/**
 * Utility class to keep track of a running animation.
 *
 * This class allows attaching end callbacks to an animation is intended to be used with
 * {@link com.android.launcher3.anim.AnimatorPlaybackController}, since in that case
 * AnimationListeners are not properly dispatched.
 */
@TargetApi(Build.VERSION_CODES.O)
public class PendingAnimation {

    private final ArrayList<Consumer<OnEndListener>> mEndListeners = new ArrayList<>();

    public final AnimatorSet anim;

    public PendingAnimation(AnimatorSet anim) {
        this.anim = anim;
    }

    public void finish(boolean isSuccess, int logAction) {
        for (Consumer<OnEndListener> listeners : mEndListeners) {
            listeners.accept(new OnEndListener(isSuccess, logAction));
        }
        mEndListeners.clear();
    }

    public void addEndListener(Consumer<OnEndListener> listener) {
        mEndListeners.add(listener);
    }

    public static class OnEndListener {
        public boolean isSuccess;
        public int logAction;

        public OnEndListener(boolean isSuccess, int logAction) {
            this.isSuccess = isSuccess;
            this.logAction = logAction;
        }
    }
}
