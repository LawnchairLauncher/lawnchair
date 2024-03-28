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

import static android.app.WindowConfiguration.WINDOWING_MODE_FREEFORM;
import static android.view.RemoteAnimationTarget.MODE_CLOSING;
import static com.android.quickstep.views.DesktopTaskView.isDesktopModeSupported;

import android.app.WindowConfiguration;
import android.graphics.Rect;
import android.os.Bundle;
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
            Rect homeContentInsets, Rect minimizedHomeBounds, Bundle extras) {
        super(apps, wallpapers, nonApps, MODE_CLOSING, extras);
        this.homeContentInsets = homeContentInsets;
        this.minimizedHomeBounds = minimizedHomeBounds;
    }

    public boolean hasTargets() {
        return unfilteredApps.length != 0;
    }

    /**
     * Check if target apps contain desktop tasks which have windowing mode set to {@link
     * WindowConfiguration#WINDOWING_MODE_FREEFORM}
     *
     * @return {@code true} if at least one target app is a desktop task
     */
    public boolean hasDesktopTasks() {
        if (!isDesktopModeSupported()) {
            return false;
        }
        for (RemoteAnimationTarget target : apps) {
            if (target.windowConfiguration.getWindowingMode() == WINDOWING_MODE_FREEFORM) {
                return true;
            }
        }
        return false;
    }
}
