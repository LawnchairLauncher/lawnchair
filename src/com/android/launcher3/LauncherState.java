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
package com.android.launcher3;

import static android.view.View.IMPORTANT_FOR_ACCESSIBILITY_AUTO;
import static android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS;

import static com.android.launcher3.LauncherAnimUtils.ALL_APPS_TRANSITION_MS;
import static com.android.launcher3.LauncherAnimUtils.OVERVIEW_TRANSITION_MS;
import static com.android.launcher3.LauncherAnimUtils.SPRING_LOADED_TRANSITION_MS;
import static com.android.launcher3.StateFlags.FLAG_DISABLE_ACCESSIBILITY;
import static com.android.launcher3.StateFlags.FLAG_HIDE_HOTSEAT;
import static com.android.launcher3.StateFlags.FLAG_MULTI_PAGE;
import static com.android.launcher3.StateFlags.FLAG_SHOW_SCRIM;

import com.android.launcher3.userevent.nano.LauncherLogProto.ContainerType;

interface StateFlags {
    int FLAG_SHOW_SCRIM = 1 << 0;
    int FLAG_MULTI_PAGE = 1 << 1;
    int FLAG_HIDE_HOTSEAT = 1 << 2;
    int FLAG_DISABLE_ACCESSIBILITY = 1 << 3;
}

/**
 * Various states for launcher
 */
public enum LauncherState {

    NORMAL          (ContainerType.WORKSPACE, 0, 0),
    ALL_APPS        (ContainerType.ALLAPPS, ALL_APPS_TRANSITION_MS, FLAG_DISABLE_ACCESSIBILITY),
    SPRING_LOADED   (ContainerType.WORKSPACE, SPRING_LOADED_TRANSITION_MS,
            FLAG_SHOW_SCRIM | FLAG_MULTI_PAGE | FLAG_DISABLE_ACCESSIBILITY),
    OVERVIEW        (ContainerType.OVERVIEW, OVERVIEW_TRANSITION_MS,
            FLAG_SHOW_SCRIM | FLAG_MULTI_PAGE | FLAG_HIDE_HOTSEAT);

    public final int containerType;

    public final boolean hasMultipleVisiblePages;
    public final int workspaceAccessibilityFlag;

    // Properties related to state transition animation.
    public final boolean hasScrim;
    public final boolean hideHotseat;
    public final int transitionDuration;

    LauncherState(int containerType, int transitionDuration, int flags) {
        this.containerType = containerType;
        this.transitionDuration = transitionDuration;

        this.hasScrim = (flags & FLAG_SHOW_SCRIM) != 0;
        this.hasMultipleVisiblePages = (flags & FLAG_MULTI_PAGE) != 0;
        this.hideHotseat = (flags & FLAG_HIDE_HOTSEAT) != 0;
        this.workspaceAccessibilityFlag = (flags & FLAG_DISABLE_ACCESSIBILITY) != 0
                ? IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                : IMPORTANT_FOR_ACCESSIBILITY_AUTO;
    }
}
