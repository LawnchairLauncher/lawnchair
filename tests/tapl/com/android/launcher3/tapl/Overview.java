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

import androidx.annotation.NonNull;
import androidx.test.uiautomator.UiObject2;

import com.android.launcher3.tapl.LauncherInstrumentation.ContainerType;

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
        try (LauncherInstrumentation.Closable c = mLauncher.addContextLayer(
                "want to switch from overview to all apps")) {
            verifyActiveContainer();

            // Swipe from navbar to the top.
            final UiObject2 navBar = mLauncher.waitForSystemUiObject("navigation_bar_frame");
            LauncherInstrumentation.log("Overview.switchToAllApps before swipe");
            final int x = navBar.getVisibleCenter().x;
            mLauncher.swipe(x, navBar.getVisibleBounds().top - 1, x, 0, ALL_APPS_STATE_ORDINAL);

            try (LauncherInstrumentation.Closable c1 = mLauncher.addContextLayer(
                    "swiped all way up from overview")) {
                return new AllAppsFromOverview(mLauncher);
            }
        }
    }
}
