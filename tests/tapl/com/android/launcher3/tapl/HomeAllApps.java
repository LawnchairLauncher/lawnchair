/*
 * Copyright (C) 2022 The Android Open Source Project
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

import static com.android.launcher3.testing.shared.TestProtocol.NORMAL_STATE_ORDINAL;

import android.graphics.Rect;

import androidx.annotation.NonNull;
import androidx.test.uiautomator.UiObject2;

import com.android.launcher3.testing.shared.TestProtocol;

import java.util.Objects;

public class HomeAllApps extends AllApps {
    private static final String BOTTOM_SHEET_RES_ID = "bottom_sheet_background";

    HomeAllApps(LauncherInstrumentation launcher) {
        super(launcher);
    }

    /**
     * Swipes down to Workspace.
     *
     * @return the Workspace object.
     */
    @NonNull
    public Workspace switchToWorkspace() {
        try (LauncherInstrumentation.Closable e = mLauncher.eventsCheck();
             LauncherInstrumentation.Closable c =
                     mLauncher.addContextLayer("want to switch from all apps to workspace")) {
            UiObject2 allAppsContainer = verifyActiveContainer();

            final Rect searchBoxBounds = Objects.requireNonNull(
                    mLauncher.getVisibleBounds(getSearchBox(allAppsContainer)));
            final int startX = searchBoxBounds.centerX();
            final int startY = searchBoxBounds.bottom;
            final int endY = mLauncher.getDevice().getDisplayHeight();
            LauncherInstrumentation.log(
                    "switchToWorkspace: startY = " + startY + ", endY = " + endY
                            + ", slop = " + mLauncher.getTouchSlop());

            mLauncher.swipeToState(
                    startX,
                    startY,
                    startX,
                    endY,
                    12 /* steps */,
                    NORMAL_STATE_ORDINAL, LauncherInstrumentation.GestureScope.INSIDE);

            try (LauncherInstrumentation.Closable c1 = mLauncher.addContextLayer(
                    "swiped to workspace")) {
                return mLauncher.getWorkspace();
            }
        }
    }

    @Override
    protected LauncherInstrumentation.ContainerType getContainerType() {
        return LauncherInstrumentation.ContainerType.HOME_ALL_APPS;
    }

    @NonNull
    @Override
    public HomeAppIcon getAppIcon(String appName) {
        return (AllAppsAppIcon) super.getAppIcon(appName);
    }

    @NonNull
    @Override
    protected HomeAppIcon createAppIcon(UiObject2 icon) {
        return new AllAppsAppIcon(mLauncher, icon);
    }

    @Override
    protected boolean hasSearchBox() {
        return true;
    }

    @Override
    protected int getAppsListRecyclerTopPadding() {
        return mLauncher.getTestInfo(TestProtocol.REQUEST_ALL_APPS_TOP_PADDING)
                .getInt(TestProtocol.TEST_INFO_RESPONSE_FIELD);
    }

    /**
     * Taps outside bottom sheet to dismiss and return to workspace. Available on tablets only.
     * @param tapRight Tap on the right of bottom sheet if true, or left otherwise.
     */
    public Workspace dismissByTappingOutsideForTablet(boolean tapRight) {
        try (LauncherInstrumentation.Closable e = mLauncher.eventsCheck();
             LauncherInstrumentation.Closable c = mLauncher.addContextLayer(
                     "want to tap outside AllApps bottom sheet on the "
                             + (tapRight ? "right" : "left"))) {
            final UiObject2 allAppsBottomSheet =
                    mLauncher.waitForLauncherObject(BOTTOM_SHEET_RES_ID);
            mLauncher.touchOutsideContainer(allAppsBottomSheet, tapRight);
            try (LauncherInstrumentation.Closable tapped = mLauncher.addContextLayer(
                    "tapped outside AllApps bottom sheet")) {
                return mLauncher.getWorkspace();
            }
        }
    }
}
