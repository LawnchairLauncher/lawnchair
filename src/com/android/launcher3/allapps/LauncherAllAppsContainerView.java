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
package com.android.launcher3.allapps;

import android.content.Context;
import android.util.AttributeSet;
import android.view.WindowInsets;

import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherState;
import com.android.launcher3.statemanager.StateManager;

/**
 * AllAppsContainerView with launcher specific callbacks
 */
public class LauncherAllAppsContainerView extends ActivityAllAppsContainerView<Launcher> {

    public LauncherAllAppsContainerView(Context context) {
        this(context, null);
    }

    public LauncherAllAppsContainerView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public LauncherAllAppsContainerView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected int computeNavBarScrimHeight(WindowInsets insets) {
        return insets.getTappableElementInsets().bottom;
    }

    @Override
    public boolean isInAllApps() {
        return mActivityContext.getStateManager().isInStableState(LauncherState.ALL_APPS);
    }

    @Override
    public boolean shouldFloatingSearchBarBePillWhenUnfocused() {
        if (!isSearchBarFloating()) {
            return false;
        }
        Launcher launcher = mActivityContext;
        StateManager<LauncherState, Launcher> manager = launcher.getStateManager();
        if (manager.isInTransition() && manager.getTargetState() != null) {
            return manager.getTargetState().shouldFloatingSearchBarUsePillWhenUnfocused(launcher);
        }
        return manager.getCurrentStableState()
                .shouldFloatingSearchBarUsePillWhenUnfocused(launcher);
    }

    @Override
    public int getFloatingSearchBarRestingMarginBottom() {
        if (!isSearchBarFloating()) {
            return super.getFloatingSearchBarRestingMarginBottom();
        }
        Launcher launcher = mActivityContext;
        StateManager<LauncherState, Launcher> stateManager = launcher.getStateManager();

        // We want to rest at the current state's resting position, unless we are in transition and
        // the target state's resting position is higher (that way if we are closing the keyboard,
        // we can stop translating at that point).
        int currentStateMarginBottom = stateManager.getCurrentStableState()
                .getFloatingSearchBarRestingMarginBottom(launcher);
        int targetStateMarginBottom = -1;
        if (stateManager.isInTransition() && stateManager.getTargetState() != null) {
            targetStateMarginBottom = stateManager.getTargetState()
                    .getFloatingSearchBarRestingMarginBottom(launcher);
            if (targetStateMarginBottom < 0) {
                // Go ahead and move offscreen.
                return targetStateMarginBottom;
            }
        }
        return Math.max(targetStateMarginBottom, currentStateMarginBottom);
    }

    @Override
    public int getFloatingSearchBarRestingMarginStart() {
        if (!isSearchBarFloating()) {
            return super.getFloatingSearchBarRestingMarginStart();
        }

        StateManager<LauncherState, Launcher> stateManager = mActivityContext.getStateManager();

        // Special case to not expand the search bar when exiting All Apps on phones.
        if (stateManager.getCurrentStableState() == LauncherState.ALL_APPS
                && mActivityContext.getDeviceProfile().isPhone) {
            return LauncherState.ALL_APPS.getFloatingSearchBarRestingMarginStart(mActivityContext);
        }

        if (stateManager.isInTransition() && stateManager.getTargetState() != null) {
            return stateManager.getTargetState()
                    .getFloatingSearchBarRestingMarginStart(mActivityContext);
        }
        return stateManager.getCurrentStableState()
                .getFloatingSearchBarRestingMarginStart(mActivityContext);
    }

    @Override
    public int getFloatingSearchBarRestingMarginEnd() {
        if (!isSearchBarFloating()) {
            return super.getFloatingSearchBarRestingMarginEnd();
        }

        StateManager<LauncherState, Launcher> stateManager = mActivityContext.getStateManager();

        // Special case to not expand the search bar when exiting All Apps on phones.
        if (stateManager.getCurrentStableState() == LauncherState.ALL_APPS
                && mActivityContext.getDeviceProfile().isPhone) {
            return LauncherState.ALL_APPS.getFloatingSearchBarRestingMarginEnd(mActivityContext);
        }

        if (stateManager.isInTransition() && stateManager.getTargetState() != null) {
            return stateManager.getTargetState()
                    .getFloatingSearchBarRestingMarginEnd(mActivityContext);
        }
        return stateManager.getCurrentStableState()
                .getFloatingSearchBarRestingMarginEnd(mActivityContext);
    }
}
