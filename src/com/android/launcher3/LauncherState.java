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
import static android.view.accessibility.AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED;

import android.view.View;

import com.android.launcher3.states.AllAppsState;
import com.android.launcher3.states.OverviewState;
import com.android.launcher3.states.SpringLoadedState;
import com.android.launcher3.userevent.nano.LauncherLogProto.ContainerType;

import java.util.Arrays;


/**
 * Base state for various states used for the Launcher
 */
public class LauncherState {

    protected static final int FLAG_SHOW_SCRIM = 1 << 0;
    protected static final int FLAG_MULTI_PAGE = 1 << 1;
    protected static final int FLAG_HIDE_HOTSEAT = 1 << 2;
    protected static final int FLAG_DISABLE_ACCESSIBILITY = 1 << 3;
    protected static final int FLAG_DO_NOT_RESTORE = 1 << 4;
    protected static final int FLAG_HAS_SPRING = 1 << 5;

    private static final LauncherState[] sAllStates = new LauncherState[4];

    public static final LauncherState NORMAL = new LauncherState(0, ContainerType.WORKSPACE,
            0, 1f, FLAG_DO_NOT_RESTORE);

    public static final LauncherState ALL_APPS = new AllAppsState(1);

    public static final LauncherState SPRING_LOADED = new SpringLoadedState(2);

    public static final LauncherState OVERVIEW = new OverviewState(3);

    public final int ordinal;

    /**
     * Used for containerType in {@link com.android.launcher3.logging.UserEventDispatcher}
     */
    public final int containerType;

    /**
     * True if the state can be persisted across activity restarts.
     */
    public final boolean doNotRestore;

    /**
     * True if workspace has multiple pages visible.
     */
    public final boolean hasMultipleVisiblePages;

    /**
     * Accessibility flag for workspace and its pages.
     * @see android.view.View#setImportantForAccessibility(int)
     */
    public final int workspaceAccessibilityFlag;

    /**
     * Properties related to state transition animation
     *
     * @see WorkspaceStateTransitionAnimation
     */
    public final boolean hasScrim;
    public final boolean hideHotseat;
    public final int transitionDuration;

    /**
     * Fraction shift in the vertical translation UI and related properties
     *
     * @see com.android.launcher3.allapps.AllAppsTransitionController
     */
    public final float verticalProgress;
    public final boolean hasVerticalSpring;

    public LauncherState(int id, int containerType, int transitionDuration, float verticalProgress,
            int flags) {
        this.containerType = containerType;
        this.transitionDuration = transitionDuration;

        this.hasScrim = (flags & FLAG_SHOW_SCRIM) != 0;
        this.hasMultipleVisiblePages = (flags & FLAG_MULTI_PAGE) != 0;
        this.hideHotseat = (flags & FLAG_HIDE_HOTSEAT) != 0;
        this.workspaceAccessibilityFlag = (flags & FLAG_DISABLE_ACCESSIBILITY) != 0
                ? IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                : IMPORTANT_FOR_ACCESSIBILITY_AUTO;
        this.doNotRestore = (flags & FLAG_DO_NOT_RESTORE) != 0;

        this.verticalProgress = verticalProgress;
        this.hasVerticalSpring = (flags & FLAG_HAS_SPRING) != 0;

        this.ordinal = id;
        sAllStates[id] = this;
    }

    public static LauncherState[] values() {
        return Arrays.copyOf(sAllStates, sAllStates.length);
    }

    public float[] getWorkspaceScaleAndTranslation(Launcher launcher) {
        return new float[] {1, 0};
    }

    public void onStateEnabled(Launcher launcher) {
        dispatchWindowStateChanged(launcher);
    }

    public void onStateDisabled(Launcher launcher) { }

    public View getFinalFocus(Launcher launcher) {
        return launcher.getWorkspace();
    }

    public String getDescription(Launcher launcher) {
        return launcher.getWorkspace().getCurrentPageDescription();
    }

    protected static void dispatchWindowStateChanged(Launcher launcher) {
        launcher.getWindow().getDecorView().sendAccessibilityEvent(TYPE_WINDOW_STATE_CHANGED);
    }
}
