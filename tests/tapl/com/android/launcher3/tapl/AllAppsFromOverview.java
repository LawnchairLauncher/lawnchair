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

import static com.android.launcher3.TestProtocol.OVERVIEW_STATE_ORDINAL;

import android.graphics.Point;

import androidx.annotation.NonNull;
import androidx.test.uiautomator.UiObject2;

/**
 * Operations on AllApps opened from Overview.
 */
public final class AllAppsFromOverview extends AllApps {

    AllAppsFromOverview(LauncherInstrumentation launcher) {
        super(launcher);
        verifyActiveContainer();
    }

    /**
     * Swipes down to switch back to Overview whence we came from.
     *
     * @return the overview panel.
     */
    @NonNull
    public Overview switchBackToOverview() {
        final UiObject2 allAppsContainer = verifyActiveContainer();
        // Swipe from the search box to the bottom.
        final UiObject2 qsb = mLauncher.waitForObjectInContainer(
                allAppsContainer, "search_container_all_apps");
        final Point start = qsb.getVisibleCenter();
        final int endY = (int) (mLauncher.getDevice().getDisplayHeight() * 0.6);
        LauncherInstrumentation.log("AllAppsFromOverview.switchBackToOverview before swipe");
        mLauncher.swipe(start.x, start.y, start.x, endY, OVERVIEW_STATE_ORDINAL);

        return new Overview(mLauncher);
    }

}
