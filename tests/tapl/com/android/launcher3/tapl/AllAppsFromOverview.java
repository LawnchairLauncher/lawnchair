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

import android.graphics.Point;
import android.support.annotation.NonNull;
import android.support.test.uiautomator.UiObject2;

/**
 * Operations on AllApps opened from Overview.
 * Scroll gestures that are OK for {@link AllAppsFromHome} may close it, so they are not supported.
 */
public final class AllAppsFromOverview {
    private final Launcher mLauncher;

    AllAppsFromOverview(Launcher launcher) {
        mLauncher = launcher;
        assertState();
    }

    /**
     * Asserts that we are in all apps.
     *
     * @return All apps container.
     */
    @NonNull
    private UiObject2 assertState() {
        return mLauncher.assertState(Launcher.State.ALL_APPS);
    }

    /**
     * Swipes down to switch back to Overview whence we came from.
     *
     * @return the overview panel.
     */
    @NonNull
    public Overview switchBackToOverview() {
        final UiObject2 allAppsContainer = assertState();
        // Swipe from the search box to the bottom.
        final UiObject2 qsb = mLauncher.waitForObjectInContainer(
                allAppsContainer, "search_container_all_apps");
        final Point start = qsb.getVisibleCenter();
        final int endY = (int) (mLauncher.getDevice().getDisplayHeight() * 0.6);
        mLauncher.swipe(start.x, start.y, start.x, endY, (endY - start.y) / 100);  // 100 px/step

        return new Overview(mLauncher);
    }

}
