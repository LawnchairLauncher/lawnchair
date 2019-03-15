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

import static com.android.launcher3.TestProtocol.BACKGROUND_APP_STATE_ORDINAL;
import static com.android.launcher3.tapl.LauncherInstrumentation.WAIT_TIME_MS;
import static com.android.launcher3.tapl.TestHelpers.getOverviewPackageName;

import static org.junit.Assert.assertTrue;

import android.view.ViewConfiguration;

import androidx.annotation.NonNull;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.Until;

import com.android.launcher3.TestProtocol;

/**
 * Indicates the base state with a UI other than Overview running as foreground. It can also
 * indicate Launcher as long as Launcher is not in Overview state.
 */
public class Background extends LauncherInstrumentation.VisibleContainer {

    Background(LauncherInstrumentation launcher) {
        super(launcher);
    }

    @Override
    protected LauncherInstrumentation.ContainerType getContainerType() {
        return LauncherInstrumentation.ContainerType.BACKGROUND;
    }

    /**
     * Swipes up or presses the square button to switch to Overview.
     * Returns the base overview, which can be either in Launcher or the fallback recents.
     *
     * @return the Overview panel object.
     */
    @NonNull
    public BaseOverview switchToOverview() {
        verifyActiveContainer();
        goToOverviewUnchecked(BACKGROUND_APP_STATE_ORDINAL);
        assertTrue("Overview not visible", mLauncher.getDevice().wait(
                Until.hasObject(By.pkg(getOverviewPackageName())), WAIT_TIME_MS));
        return new BaseOverview(mLauncher);
    }

    protected void goToOverviewUnchecked(int expectedState) {
        if (mLauncher.isSwipeUpEnabled()) {
            final int centerX = mLauncher.getDevice().getDisplayWidth() / 2;
            final int startY = getSwipeStartY();
            final int swipeHeight = mLauncher.getTestInfo(
                    getSwipeHeightRequestName()).
                    getInt(TestProtocol.TEST_INFO_RESPONSE_FIELD);
            final int slop = ViewConfiguration.get(mLauncher.getContext()).getScaledTouchSlop();

            mLauncher.swipe(centerX, startY, centerX, startY - swipeHeight - slop, expectedState);
        } else {
            mLauncher.getSystemUiObject("recent_apps").click();
        }
    }

    protected String getSwipeHeightRequestName() {
        return TestProtocol.REQUEST_BACKGROUND_TO_OVERVIEW_SWIPE_HEIGHT;
    }

    protected int getSwipeStartY() {
        return mLauncher.getSystemUiObject("home").getVisibleBounds().centerY();
    }
}
