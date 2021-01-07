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

import static com.android.launcher3.tapl.OverviewTask.TASK_START_EVENT;
import static com.android.launcher3.testing.TestProtocol.BACKGROUND_APP_STATE_ORDINAL;
import static com.android.launcher3.testing.TestProtocol.OVERVIEW_STATE_ORDINAL;

import android.graphics.Point;
import android.os.SystemClock;
import android.view.MotionEvent;

import androidx.annotation.NonNull;
import androidx.test.uiautomator.UiObject2;

import com.android.launcher3.testing.TestProtocol;

import java.util.regex.Pattern;

/**
 * Indicates the base state with a UI other than Overview running as foreground. It can also
 * indicate Launcher as long as Launcher is not in Overview state.
 */
public class Background extends LauncherInstrumentation.VisibleContainer {
    private static final int ZERO_BUTTON_SWIPE_UP_GESTURE_DURATION = 500;
    private static final Pattern SQUARE_BUTTON_EVENT = Pattern.compile("onOverviewToggle");

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
        try (LauncherInstrumentation.Closable e = mLauncher.eventsCheck();
             LauncherInstrumentation.Closable c = mLauncher.addContextLayer(
                     "want to switch from background to overview")) {
            verifyActiveContainer();
            goToOverviewUnchecked();
            return mLauncher.isFallbackOverview() ?
                    new BaseOverview(mLauncher) : new Overview(mLauncher);
        }
    }

    protected boolean zeroButtonToOverviewGestureStartsInLauncher() {
        return false;
    }

    protected void goToOverviewUnchecked() {
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
                final LauncherInstrumentation.GestureScope gestureScope =
                        zeroButtonToOverviewGestureStartsInLauncher()
                                ? LauncherInstrumentation.GestureScope.INSIDE_TO_OUTSIDE
                                : LauncherInstrumentation.GestureScope.OUTSIDE_WITH_PILFER;

                // b/156044202
                mLauncher.log("Hierarchy before swiping up to overview:");
                mLauncher.dumpViewHierarchy();

                mLauncher.sendPointer(
                        downTime, downTime, MotionEvent.ACTION_DOWN, start, gestureScope);
                mLauncher.executeAndWaitForEvent(
                        () -> mLauncher.movePointer(
                                downTime,
                                downTime,
                                ZERO_BUTTON_SWIPE_UP_GESTURE_DURATION,
                                start,
                                end,
                                gestureScope),
                        event -> TestProtocol.PAUSE_DETECTED_MESSAGE.equals(event.getClassName()),
                        () -> "Pause wasn't detected");
                mLauncher.runToState(
                        () -> mLauncher.sendPointer(
                                downTime, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, end,
                                gestureScope),
                        OVERVIEW_STATE_ORDINAL);
                break;
            }

            case TWO_BUTTON: {
                final int startX;
                final int startY;
                final int endX;
                final int endY;
                final int swipeLength = mLauncher.getTestInfo(getSwipeHeightRequestName()).
                        getInt(TestProtocol.TEST_INFO_RESPONSE_FIELD) + mLauncher.getTouchSlop();

                if (mLauncher.getDevice().isNaturalOrientation()) {
                    startX = endX = mLauncher.getDevice().getDisplayWidth() / 2;
                    startY = getSwipeStartY();
                    endY = startY - swipeLength;
                } else {
                    startX = getSwipeStartX();
                    endX = startX - swipeLength;
                    startY = endY = mLauncher.getDevice().getDisplayHeight() / 2;
                }

                mLauncher.swipeToState(startX, startY, endX, endY, 10, OVERVIEW_STATE_ORDINAL,
                        LauncherInstrumentation.GestureScope.OUTSIDE_WITH_PILFER);
                break;
            }

            case THREE_BUTTON:
                mLauncher.expectEvent(TestProtocol.SEQUENCE_MAIN, SQUARE_BUTTON_EVENT);
                mLauncher.runToState(
                        () -> mLauncher.waitForSystemUiObject("recent_apps").click(),
                        OVERVIEW_STATE_ORDINAL);
                break;
        }
        expectSwitchToOverviewEvents();
    }

    private void expectSwitchToOverviewEvents() {
    }

    /**
     * Swipes right or double presses the square button to switch to the previous app.
     */
    public Background quickSwitchToPreviousApp() {
        try (LauncherInstrumentation.Closable e = mLauncher.eventsCheck();
             LauncherInstrumentation.Closable c = mLauncher.addContextLayer(
                     "want to quick switch to the previous app")) {
            verifyActiveContainer();
            quickSwitchToPreviousApp(getExpectedStateForQuickSwitch());
            return new Background(mLauncher);
        }
    }

    protected int getExpectedStateForQuickSwitch() {
        return BACKGROUND_APP_STATE_ORDINAL;
    }

    protected void quickSwitchToPreviousApp(int expectedState) {
        final boolean launcherWasVisible = mLauncher.isLauncherVisible();
        boolean transposeInLandscape = false;
        switch (mLauncher.getNavigationModel()) {
            case TWO_BUTTON:
                transposeInLandscape = true;
                // Fall through, zero button and two button modes behave the same.
            case ZERO_BUTTON: {
                final int startX;
                final int startY;
                final int endX;
                final int endY;
                if (mLauncher.getDevice().isNaturalOrientation() || !transposeInLandscape) {
                    // Swipe from the bottom left to the bottom right of the screen.
                    startX = 0;
                    startY = getSwipeStartY();
                    endX = mLauncher.getDevice().getDisplayWidth();
                    endY = startY;
                } else {
                    // Swipe from the bottom right to the top right of the screen.
                    startX = getSwipeStartX();
                    startY = mLauncher.getRealDisplaySize().y - 1;
                    endX = startX;
                    endY = 0;
                }
                final boolean isZeroButton = mLauncher.getNavigationModel()
                        == LauncherInstrumentation.NavigationModel.ZERO_BUTTON;
                mLauncher.swipeToState(startX, startY, endX, endY, 20, expectedState,
                        launcherWasVisible && isZeroButton
                                ? LauncherInstrumentation.GestureScope.INSIDE_TO_OUTSIDE
                                : LauncherInstrumentation.GestureScope.OUTSIDE_WITH_PILFER);
                break;
            }

            case THREE_BUTTON:
                // Double press the recents button.
                UiObject2 recentsButton = mLauncher.waitForSystemUiObject("recent_apps");
                mLauncher.expectEvent(TestProtocol.SEQUENCE_MAIN, SQUARE_BUTTON_EVENT);
                mLauncher.runToState(() -> recentsButton.click(), OVERVIEW_STATE_ORDINAL);
                mLauncher.getOverview();
                mLauncher.expectEvent(TestProtocol.SEQUENCE_MAIN, SQUARE_BUTTON_EVENT);
                recentsButton.click();
                break;
        }
        mLauncher.expectEvent(TestProtocol.SEQUENCE_MAIN, TASK_START_EVENT);
    }
    /** Swipes left to switch to the previous app. */
    public Background quickSwitchToPreviousAppSwipeLeft() {
        try (LauncherInstrumentation.Closable e = mLauncher.eventsCheck();
            LauncherInstrumentation.Closable c =
                mLauncher.addContextLayer("want to quick switch to the previous app")) {
            verifyActiveContainer();
            quickSwitchToPreviousAppSwipeLeft(getExpectedStateForQuickSwitch());
            return new Background(mLauncher);
        }
    }

    protected void quickSwitchToPreviousAppSwipeLeft(int expectedState) {
        final boolean launcherWasVisible = mLauncher.isLauncherVisible();
        boolean transposeInLandscape = false;
        switch (mLauncher.getNavigationModel()) {
            case TWO_BUTTON:
                transposeInLandscape = true;
                // Fall through, zero button and two button modes behave the same.
            case ZERO_BUTTON: {
                final int startX;
                final int startY;
                final int endX;
                final int endY;
                if (mLauncher.getDevice().isNaturalOrientation() || !transposeInLandscape) {
                    // Swipe from the bottom right to the bottom left of the screen.
                    startX = mLauncher.getDevice().getDisplayWidth();
                    startY = getSwipeStartY();
                    endX = 0;
                    endY = startY;
                } else {
                    // Swipe from the bottom right to the top right of the screen.
                    startX = getSwipeStartX();
                    startY = mLauncher.getRealDisplaySize().y - 1;
                    endX = startX;
                    endY = 0;
                }
                final boolean isZeroButton =
                        mLauncher.getNavigationModel()
                        == LauncherInstrumentation.NavigationModel.ZERO_BUTTON;
                mLauncher.swipeToState(startX, startY, endX, endY, 20, expectedState,
                        launcherWasVisible && isZeroButton
                                ? LauncherInstrumentation.GestureScope.INSIDE_TO_OUTSIDE
                                : LauncherInstrumentation.GestureScope.OUTSIDE_WITH_PILFER);
                break;
            }

            case THREE_BUTTON:
                // Double press the recents button.
                UiObject2 recentsButton = mLauncher.waitForSystemUiObject("recent_apps");
                mLauncher.expectEvent(TestProtocol.SEQUENCE_MAIN, SQUARE_BUTTON_EVENT);
                mLauncher.runToState(() -> recentsButton.click(), OVERVIEW_STATE_ORDINAL);
                mLauncher.getOverview();
                mLauncher.expectEvent(TestProtocol.SEQUENCE_MAIN, SQUARE_BUTTON_EVENT);
                recentsButton.click();
                break;
        }
        mLauncher.expectEvent(TestProtocol.SEQUENCE_MAIN, TASK_START_EVENT);
    }

    protected String getSwipeHeightRequestName() {
        return TestProtocol.REQUEST_BACKGROUND_TO_OVERVIEW_SWIPE_HEIGHT;
    }

    protected int getSwipeStartX() {
        return mLauncher.getRealDisplaySize().x - 1;
    }

    protected int getSwipeStartY() {
        return mLauncher.getRealDisplaySize().y - 1;
    }
}
