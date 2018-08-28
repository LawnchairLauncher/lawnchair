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

import androidx.test.uiautomator.UiObject2;

import androidx.annotation.NonNull;

/**
 * Operations on the home screen.
 *
 * Launcher can be invoked both when its activity is in the foreground and when it is in the
 * background. This class is a parent of the two classes {@link Background} and {@link Workspace}
 * that essentially represents these two activity states. Any gestures (e.g., switchToOverview) that
 * can be performed in both of these states can be defined here.
 */
public abstract class Home extends LauncherInstrumentation.VisibleContainer {

    protected Home(LauncherInstrumentation launcher) {
        super(launcher);
        verifyActiveContainer();
    }

    /**
     * Swipes up or presses the square button to switch to Overview.
     *
     * @return the Overview panel object.
     */
    @NonNull
    public Overview switchToOverview() {
        verifyActiveContainer();
        if (mLauncher.isSwipeUpEnabled()) {
            final int height = mLauncher.getDevice().getDisplayHeight();
            final UiObject2 navBar = mLauncher.getSystemUiObject("navigation_bar_frame");

            mLauncher.swipe(
                    navBar.getVisibleBounds().centerX(), navBar.getVisibleBounds().centerY(),
                    navBar.getVisibleBounds().centerX(), height - 300
            );
        } else {
            mLauncher.getSystemUiObject("recent_apps").click();
        }

        return new Overview(mLauncher);
    }
}