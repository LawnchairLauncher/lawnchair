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

import static com.android.launcher3.testing.TestProtocol.OVERVIEW_STATE_ORDINAL;

import android.graphics.Point;

import androidx.annotation.NonNull;
import androidx.test.uiautomator.UiObject2;

import com.android.launcher3.testing.TestProtocol;

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
        try (LauncherInstrumentation.Closable c = mLauncher.addContextLayer(
                "want to switch back from all apps to overview")) {
            final UiObject2 allAppsContainer = verifyActiveContainer();
            // Swipe from the search box to the bottom.
            final UiObject2 qsb = mLauncher.waitForObjectInContainer(
                    allAppsContainer, "search_container_all_apps");
            final Point start = qsb.getVisibleCenter();
            final int swipeHeight = mLauncher.getTestInfo(
                    TestProtocol.REQUEST_ALL_APPS_TO_OVERVIEW_SWIPE_HEIGHT).
                    getInt(TestProtocol.TEST_INFO_RESPONSE_FIELD);

            final int endY = start.y + swipeHeight;
            LauncherInstrumentation.log("AllAppsFromOverview.switchBackToOverview before swipe");
            mLauncher.swipeToState(start.x, start.y, start.x, endY, 60, OVERVIEW_STATE_ORDINAL);

            try (LauncherInstrumentation.Closable c1 = mLauncher.addContextLayer("swiped down")) {
                return new Overview(mLauncher);
            }
        }
    }
}
