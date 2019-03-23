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

import android.graphics.Point;
import android.os.SystemClock;
import android.view.MotionEvent;

import androidx.annotation.NonNull;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.Until;

import com.android.launcher3.TestProtocol;

/**
 * Indicates the base state with a UI other than Overview running as foreground. It can also
 * indicate Launcher as long as Launcher is not in Overview state.
 */
public class Background extends LauncherInstrumentation.VisibleContainer {
    private static final int ZERO_BUTTON_SWIPE_UP_GESTURE_DURATION = 500;
    private static final int ZERO_BUTTON_SWIPE_UP_HOLD_DURATION = 400;

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
        switch (mLauncher.getNavigationModel()) {
            case ZERO_BUTTON: {
                final int centerX = mLauncher.getDevice().getDisplayWidth() / 2;
                final int startY = getSwipeStartY();
                final int swipeHeight = mLauncher.getTestInfo(getSwipeHeightRequestName()).
                        getInt(TestProtocol.TEST_INFO_RESPONSE_FIELD);
                final Point start = new Point(centerX, startY);
                final Point end =
                        new Point(centerX, startY - swipeHeight - mLauncher.getTouchSlop());

                final long downTime = SystemClock.uptimeMillis();
                mLauncher.sendPointer(downTime, downTime, MotionEvent.ACTION_DOWN, start);
                mLauncher.movePointer(downTime, ZERO_BUTTON_SWIPE_UP_GESTURE_DURATION, start, end);
                LauncherInstrumentation.sleep(ZERO_BUTTON_SWIPE_UP_HOLD_DURATION);
                mLauncher.sendPointer(
                        downTime, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, end);
                break;
            }

            case TWO_BUTTON: {
                final int centerX = mLauncher.getDevice().getDisplayWidth() / 2;
                final int startY = getSwipeStartY();
                final int swipeHeight = mLauncher.getTestInfo(getSwipeHeightRequestName()).
                        getInt(TestProtocol.TEST_INFO_RESPONSE_FIELD);

                mLauncher.swipe(
                        centerX, startY, centerX,
                        startY - swipeHeight - mLauncher.getTouchSlop(),
                        expectedState);
                break;
            }

            case THREE_BUTTON:
                mLauncher.waitForSystemUiObject("recent_apps").click();
                break;
        }
    }

    protected String getSwipeHeightRequestName() {
        return TestProtocol.REQUEST_BACKGROUND_TO_OVERVIEW_SWIPE_HEIGHT;
    }

    protected int getSwipeStartY() {
        return mLauncher.waitForSystemUiObject("navigation_bar_frame").getVisibleBounds().centerY();
    }
}
