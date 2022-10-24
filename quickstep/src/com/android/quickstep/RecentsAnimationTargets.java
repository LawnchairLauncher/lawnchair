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
package com.android.quickstep;

import static android.view.RemoteAnimationTarget.MODE_CLOSING;

import android.graphics.Rect;
import android.view.RemoteAnimationTarget;

/**
 * Extension of {@link RemoteAnimationTargets} with additional information about swipe
 * up animation
 */
public class RecentsAnimationTargets extends RemoteAnimationTargets {

    public final Rect homeContentInsets;
    public final Rect minimizedHomeBounds;

    public RecentsAnimationTargets(RemoteAnimationTarget[] apps,
            RemoteAnimationTarget[] wallpapers, RemoteAnimationTarget[] nonApps,
            Rect homeContentInsets, Rect minimizedHomeBounds) {
        super(apps, wallpapers, nonApps, MODE_CLOSING);
        this.homeContentInsets = homeContentInsets;
        this.minimizedHomeBounds = minimizedHomeBounds;
    }

    public boolean hasTargets() {
        return unfilteredApps.length != 0;
    }
}
