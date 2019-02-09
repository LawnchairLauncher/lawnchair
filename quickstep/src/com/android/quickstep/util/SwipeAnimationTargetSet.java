/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.quickstep.util;

import static com.android.systemui.shared.system.RemoteAnimationTargetCompat.MODE_CLOSING;

import android.graphics.Rect;

import com.android.systemui.shared.system.RecentsAnimationControllerCompat;
import com.android.systemui.shared.system.RemoteAnimationTargetCompat;

/**
 * Extension of {@link RemoteAnimationTargetSet} with additional information about swipe
 * up animation
 */
public class SwipeAnimationTargetSet extends RemoteAnimationTargetSet {

    public final RecentsAnimationControllerCompat controller;
    public final Rect homeContentInsets;
    public final Rect minimizedHomeBounds;

    public SwipeAnimationTargetSet(RecentsAnimationControllerCompat controller,
            RemoteAnimationTargetCompat[] targets, Rect homeContentInsets,
            Rect minimizedHomeBounds) {
        super(targets, MODE_CLOSING);
        this.controller = controller;
        this.homeContentInsets = homeContentInsets;
        this.minimizedHomeBounds = minimizedHomeBounds;
    }


    public interface SwipeAnimationListener {

        void onRecentsAnimationStart(SwipeAnimationTargetSet targetSet);

        void onRecentsAnimationCanceled();
    }
}
