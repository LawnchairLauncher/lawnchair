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

package com.android.launcher3.tapl;

import static com.android.launcher3.TestProtocol.ALL_APPS_STATE_ORDINAL;

import android.graphics.Point;

import com.android.launcher3.tapl.LauncherInstrumentation.ContainerType;

import androidx.annotation.NonNull;
import androidx.test.uiautomator.UiObject2;

/**
 * Overview pane.
 */
public final class Overview extends BaseOverview {

    Overview(LauncherInstrumentation launcher) {
        super(launcher);
        verifyActiveContainer();
    }

    @Override
    protected ContainerType getContainerType() {
        return LauncherInstrumentation.ContainerType.OVERVIEW;
    }

    /**
     * Swipes up to All Apps.
     *
     * @return the App Apps object.
     */
    @NonNull
    public AllAppsFromOverview switchToAllApps() {
        verifyActiveContainer();

        // Swipe from navbar to the top.
        final UiObject2 navBar = mLauncher.getSystemUiObject("navigation_bar_frame");
        final Point start = navBar.getVisibleCenter();
        LauncherInstrumentation.log("Overview.switchToAllApps before swipe");
        mLauncher.swipe(start.x, start.y, start.x, 0, ALL_APPS_STATE_ORDINAL);

        return new AllAppsFromOverview(mLauncher);
    }
}
