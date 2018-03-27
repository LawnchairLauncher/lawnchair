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
package com.android.quickstep;

import android.annotation.TargetApi;
import android.os.Build;
import android.support.annotation.IntDef;
import android.view.Choreographer;
import android.view.MotionEvent;

import com.android.systemui.shared.system.NavigationBarCompat.HitTarget;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.function.Consumer;

@TargetApi(Build.VERSION_CODES.O)
@FunctionalInterface
public interface TouchConsumer extends Consumer<MotionEvent> {

    static boolean isInteractionQuick(@InteractionType int interactionType) {
        return interactionType == INTERACTION_QUICK_SCRUB ||
                interactionType == INTERACTION_QUICK_SWITCH;
    }

    @IntDef(flag = true, value = {
            INTERACTION_NORMAL,
            INTERACTION_QUICK_SWITCH,
            INTERACTION_QUICK_SCRUB
    })
    @Retention(RetentionPolicy.SOURCE)
    @interface InteractionType {}
    int INTERACTION_NORMAL = 0;
    int INTERACTION_QUICK_SWITCH = 1;
    int INTERACTION_QUICK_SCRUB = 2;

    default void reset() { }

    default void updateTouchTracking(@InteractionType int interactionType) { }

    default void onQuickScrubEnd() { }

    default void onQuickScrubProgress(float progress) { }

    /**
     * Called on the binder thread to allow the consumer to process the motion event before it is
     * posted on a handler thread.
     */
    default void preProcessMotionEvent(MotionEvent ev) { }

    default Choreographer getIntrimChoreographer(MotionEventQueue queue) {
        return null;
    }
}
