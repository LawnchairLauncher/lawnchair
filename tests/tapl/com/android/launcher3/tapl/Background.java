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

import static com.android.launcher3.tapl.BaseOverview.TASK_SELECTOR;
import static com.android.launcher3.tapl.OverviewTask.TASK_START_EVENT;
import static com.android.launcher3.testing.shared.TestProtocol.OVERVIEW_STATE_ORDINAL;

import android.graphics.Point;
import android.os.SystemClock;
import android.view.MotionEvent;

import androidx.annotation.NonNull;
import androidx.test.uiautomator.UiObject2;

import com.android.launcher3.tapl.LauncherInstrumentation.NavigationModel;
import com.android.launcher3.tapl.LauncherInstrumentation.TrackpadGestureType;
import com.android.launcher3.tapl.OverviewTask.TaskViewType;
import com.android.launcher3.testing.shared.TestProtocol;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Indicates the base state with a UI other than Overview running as foreground. It can also
 * indicate Launcher as long as Launcher is not in Overview state.
 */
public abstract class Background extends LauncherInstrumentation.VisibleContainer
        implements KeyboardQuickSwitchSource {
    private static final int ZERO_BUTTON_SWIPE_UP_GESTURE_DURATION = 500;
    private static final Pattern SQUARE_BUTTON_EVENT = Pattern.compile("onOverviewToggle");

    Background(LauncherInstrumentation launcher) {
        super(launcher);
    }

    @Override
    public LauncherInstrumentation getLauncher() {
        return mLauncher;
    }

    @Override
    public LauncherInstrumentation.ContainerType getStartingContainerType() {
        return getContainerType();
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
            return mLauncher.is3PLauncher()
                    ? new BaseOverview(mLauncher) : new Overview(mLauncher);
        }
    }


    protected boolean zeroButtonToOverviewGestureStateTransitionWhileHolding() {
        return false;
    }

    protected void goToOverviewUnchecked() {
        if (mLauncher.getNavigationModel() == NavigationModel.ZERO_BUTTON
                || mLauncher.getTrackpadGestureType() == TrackpadGestureType.THREE_FINGER) {
            final long downTime = SystemClock.uptimeMillis();
            sendDownPointerToEnterOverviewToLauncher(downTime);
            String swipeAndHoldToEnterOverviewActionName =
                    "swiping and holding to enter overview";
            // If swiping from an app (e.g. Overview is in Background), we pause and hold on
            // swipe up to make overview appear, or else swiping without holding would take
            // us to the Home state. If swiping up from Home (e.g. Overview in Home or
            // Workspace state where the below condition is true), there is no need to pause,
            // and we will not test for an intermediate carousel as one will not exist.
            if (zeroButtonToOverviewGestureStateTransitionWhileHolding()) {
                mLauncher.runToState(
                        () -> sendSwipeUpAndHoldToEnterOverviewGestureToLauncher(downTime),
                        OVERVIEW_STATE_ORDINAL, swipeAndHoldToEnterOverviewActionName);
                sendUpPointerToEnterOverviewToLauncher(downTime);
            } else {
                // If swiping up from an app to overview, pause on intermediate carousel
                // until snapshots are visible. No intermediate carousel when swiping from
                // Home. The task swiped up is not a snapshot but the TaskViewSimulator. If
                // only a single task exists, no snapshots will be available during swipe up.
                mLauncher.executeAndWaitForLauncherEvent(
                        () -> sendSwipeUpAndHoldToEnterOverviewGestureToLauncher(downTime),
                        event -> TestProtocol.PAUSE_DETECTED_MESSAGE.equals(
                                event.getClassName().toString()),
                        () -> "Pause wasn't detected",
                        swipeAndHoldToEnterOverviewActionName);
                try (LauncherInstrumentation.Closable c = mLauncher.addContextLayer(
                        "paused on swipe up to overview")) {
                    if (mLauncher.getRecentTasks().size() > 1) {
                        // When swiping up to grid-overview for tablets, the swiped tab will be
                        // in the middle of the screen (TaskViewSimulator, not a snapshot), and
                        // all remaining snapshots will be to the left of that task. In
                        // non-tablet overview, snapshots can be on either side of the swiped
                        // task, but we still check that they become visible after swiping and
                        // pausing.
                        mLauncher.waitForObjectBySelector(TASK_SELECTOR);
                        if (mLauncher.isTablet()) {
                            List<UiObject2> tasks = mLauncher.getDevice().findObjects(
                                    TASK_SELECTOR);

                            final int centerX = mLauncher.getDevice().getDisplayWidth() / 2;
                            UiObject2 centerTask = tasks.stream()
                                    .filter(t -> t.getVisibleCenter().x == centerX)
                                    .findFirst()
                                    .orElse(null);

                            if (centerTask != null) {
                                mLauncher.assertTrue(
                                        "Task(s) found to the right of the swiped task",
                                        tasks.stream()
                                                .filter(t -> t != centerTask
                                                        && OverviewTask.getType(t)
                                                        != TaskViewType.DESKTOP)
                                                .allMatch(t -> t.getVisibleBounds().right
                                                        < centerTask.getVisibleBounds().left));
                                mLauncher.assertTrue(
                                        "DesktopTask(s) found to the left of the swiped task",
                                        tasks.stream()
                                                .filter(t -> t != centerTask
                                                        && OverviewTask.getType(t)
                                                        == TaskViewType.DESKTOP)
                                                .allMatch(t -> t.getVisibleBounds().left
                                                        > centerTask.getVisibleBounds().right));
                            }
                        }

                    }
                    String upPointerToEnterOverviewActionName =
                            "sending UP pointer to enter overview";
                    mLauncher.runToState(() -> sendUpPointerToEnterOverviewToLauncher(downTime),
                            OVERVIEW_STATE_ORDINAL, upPointerToEnterOverviewActionName);
                }
            }
        } else {
            mLauncher.expectEvent(TestProtocol.SEQUENCE_MAIN, SQUARE_BUTTON_EVENT);
            mLauncher.runToState(
                    () -> mLauncher.waitForNavigationUiObject("recent_apps").click(),
                    OVERVIEW_STATE_ORDINAL, "clicking Recents button");
        }
        expectSwitchToOverviewEvents();
    }

    private void expectSwitchToOverviewEvents() {
    }

    private void sendDownPointerToEnterOverviewToLauncher(long downTime) {
        final int centerX = mLauncher.getDevice().getDisplayWidth() / 2;
        final int startY = getSwipeStartY();
        final Point start = new Point(centerX, startY);

        mLauncher.sendPointer(downTime, downTime, MotionEvent.ACTION_DOWN, start,
                LauncherInstrumentation.GestureScope.EXPECT_PILFER);

        if (!mLauncher.isLauncher3()) {
            mLauncher.expectEvent(TestProtocol.SEQUENCE_PILFER,
                    LauncherInstrumentation.EVENT_PILFER_POINTERS);
        }
    }

    private void sendSwipeUpAndHoldToEnterOverviewGestureToLauncher(long downTime) {
        final int centerX = mLauncher.getDevice().getDisplayWidth() / 2;
        final int startY = getSwipeStartY();
        final int swipeHeight = mLauncher.getTestInfo(getSwipeHeightRequestName()).getInt(
                TestProtocol.TEST_INFO_RESPONSE_FIELD);
        final Point start = new Point(centerX, startY);
        final Point end =
                new Point(centerX, startY - swipeHeight - mLauncher.getTouchSlop());

        mLauncher.movePointer(
                downTime,
                downTime,
                ZERO_BUTTON_SWIPE_UP_GESTURE_DURATION,
                start,
                end,
                LauncherInstrumentation.GestureScope.EXPECT_PILFER);
    }

    private void sendUpPointerToEnterOverviewToLauncher(long downTime) {
        final int centerX = mLauncher.getDevice().getDisplayWidth() / 2;
        final int startY = getSwipeStartY();
        final int swipeHeight = mLauncher.getTestInfo(getSwipeHeightRequestName()).getInt(
                TestProtocol.TEST_INFO_RESPONSE_FIELD);
        final Point end =
                new Point(centerX, startY - swipeHeight - mLauncher.getTouchSlop());

        mLauncher.sendPointer(downTime, SystemClock.uptimeMillis(),
                MotionEvent.ACTION_UP, end,
                LauncherInstrumentation.GestureScope.DONT_EXPECT_PILFER);
    }

    /**
     * Quick switching to the app with swiping to right.
     */
    @NonNull
    public LaunchedAppState quickSwitchToPreviousApp() {
        quickSwitch(true /* toRight */);
        return new LaunchedAppState(mLauncher);
    }

    /**
     * Quick switching to the app with swiping to left.
     */
    @NonNull
    public LaunchedAppState quickSwitchToPreviousAppSwipeLeft() {
        quickSwitch(false /* toRight */);
        return new LaunchedAppState(mLauncher);
    }

    /**
     * Making swipe gesture to quick-switch app tasks.
     *
     * @param toRight {@code true} means swiping right, {@code false} means swiping left.
     * @throws {@link AssertionError} when failing to verify the visible UI in the container.
     */
    private void quickSwitch(boolean toRight) {
        try (LauncherInstrumentation.Closable e = mLauncher.eventsCheck();
             LauncherInstrumentation.Closable c = mLauncher.addContextLayer(
                     "want to quick switch to the previous app")) {
            verifyActiveContainer();
            if (mLauncher.getNavigationModel() == NavigationModel.ZERO_BUTTON
                    || mLauncher.getTrackpadGestureType() == TrackpadGestureType.FOUR_FINGER) {
                final int startX;
                final int startY;
                final int endX;
                final int endY;
                final int cornerRadius = (int) Math.ceil(mLauncher.getWindowCornerRadius());
                if (toRight) {
                    // Swipe from the bottom left to the bottom right of the screen.
                    startX = cornerRadius;
                    startY = getSwipeStartY();
                    endX = mLauncher.getDevice().getDisplayWidth() - cornerRadius;
                    endY = startY;
                } else {
                    // Swipe from the bottom right to the bottom left of the screen.
                    startX = mLauncher.getDevice().getDisplayWidth() - cornerRadius;
                    startY = getSwipeStartY();
                    endX = cornerRadius;
                    endY = startY;
                }

                mLauncher.executeAndWaitForLauncherStop(
                        () -> mLauncher.linearGesture(
                                startX, startY, endX, endY, 20, false,
                                LauncherInstrumentation.GestureScope.EXPECT_PILFER),
                        "swiping");
            } else {
                // Double press the recents button.
                UiObject2 recentsButton = mLauncher.waitForNavigationUiObject("recent_apps");
                mLauncher.expectEvent(TestProtocol.SEQUENCE_MAIN, SQUARE_BUTTON_EVENT);
                mLauncher.runToState(() -> recentsButton.click(), OVERVIEW_STATE_ORDINAL,
                        "clicking Recents button for the first time");
                mLauncher.getOverview();
                mLauncher.expectEvent(TestProtocol.SEQUENCE_MAIN, SQUARE_BUTTON_EVENT);
                mLauncher.executeAndWaitForLauncherStop(
                        () -> recentsButton.click(),
                        "clicking Recents button for the second time");
            }
            mLauncher.expectEvent(TestProtocol.SEQUENCE_MAIN, TASK_START_EVENT);
        }
    }

    protected String getSwipeHeightRequestName() {
        return TestProtocol.REQUEST_BACKGROUND_TO_OVERVIEW_SWIPE_HEIGHT;
    }

    protected int getSwipeStartX() {
        return mLauncher.getRealDisplaySize().x - 1;
    }

    protected int getSwipeStartY() {
        return mLauncher.getTrackpadGestureType() == TrackpadGestureType.THREE_FINGER
                ? mLauncher.getDevice().getDisplayHeight() * 3 / 4
                : mLauncher.getRealDisplaySize().y - 1;
    }
}
