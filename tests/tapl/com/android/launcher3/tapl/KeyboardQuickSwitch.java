/*
 * Copyright (C) 2023 The Android Open Source Project
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

import static com.android.launcher3.tapl.LauncherInstrumentation.KEYBOARD_QUICK_SWITCH_RES_ID;

import android.view.KeyEvent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.launcher3.testing.shared.TestProtocol;

import java.util.regex.Pattern;

/**
 * Operations on the Keyboard Quick Switch View
 */
public final class KeyboardQuickSwitch {

    private static final Pattern EVENT_ALT_TAB_DOWN = Pattern.compile(
            "KeyboardQuickSwitchView key event: KeyEvent.*?action=ACTION_DOWN.*?keyCode=KEYCODE_TAB"
                    + ".*?metaState=META_ALT_ON");
    private static final Pattern EVENT_ALT_TAB_UP = Pattern.compile(
            "KeyboardQuickSwitchView key event: KeyEvent.*?action=ACTION_UP.*?keyCode=KEYCODE_TAB"
                    + ".*?metaState=META_ALT_ON");
    private static final Pattern EVENT_ALT_SHIFT_TAB_UP = Pattern.compile(
            "KeyboardQuickSwitchView key event: KeyEvent.*?action=ACTION_UP.*?keyCode=KEYCODE_TAB"
                    + ".*?metaState=META_ALT_ON|META_SHIFT_ON");
    private static final Pattern EVENT_ALT_ESC_UP = Pattern.compile(
            "KeyboardQuickSwitchView key event: KeyEvent.*?action=ACTION_UP"
                    + ".*?keyCode=KEYCODE_ESCAPE.*?metaState=META_ALT_ON");
    private static final Pattern EVENT_KQS_ALT_LEFT_UP = Pattern.compile(
            "KeyboardQuickSwitchView key event: KeyEvent.*?action=ACTION_UP"
                    + ".*?keyCode=KEYCODE_ALT_LEFT");
    private static final Pattern EVENT_HOME_ALT_LEFT_UP = Pattern.compile(
            "Key event: KeyEvent.*?action=ACTION_UP"
                    + ".*?keyCode=KEYCODE_ALT_LEFT");

    private final LauncherInstrumentation mLauncher;
    private final LauncherInstrumentation.ContainerType mStartingContainerType;
    private final boolean mIsHomeState;

    KeyboardQuickSwitch(
            LauncherInstrumentation launcher,
            LauncherInstrumentation.ContainerType startingContainerType,
            boolean isHomeState) {
        mLauncher = launcher;
        mStartingContainerType = startingContainerType;
        mIsHomeState = isHomeState;
    }

    /**
     * Focuses the next task in the Keyboard quick switch view.
     * <p>
     * Tasks are ordered left-to-right in LTR, and vice versa in RLT, in a carousel.
     * <ul>
     *      <li>If no task has been focused yet, and there is only one task, then that task will be
     *          focused</li>
     *      <li>If no task has been focused yet, and there are two or more tasks, then the second
     *          task will be focused</li>
     *      <li>If the currently-focused task is at the end of the list, the first task will be
     *          focused</li>
     * </ul>
     */
    public KeyboardQuickSwitch moveFocusForward() {
        try (LauncherInstrumentation.Closable c1 = mLauncher.addContextLayer(
                "want to move keyboard quick switch focus forward");
             LauncherInstrumentation.Closable e = mLauncher.eventsCheck()) {
            mLauncher.waitForLauncherObject(KEYBOARD_QUICK_SWITCH_RES_ID);
            mLauncher.expectEvent(TestProtocol.SEQUENCE_MAIN, EVENT_ALT_TAB_UP);
            mLauncher.assertTrue("Failed to press alt+tab",
                    mLauncher.getDevice().pressKeyCode(
                            KeyEvent.KEYCODE_TAB, KeyEvent.META_ALT_ON));

            try (LauncherInstrumentation.Closable c2 = mLauncher.addContextLayer(
                    "pressed alt+tab")) {
                mLauncher.waitForLauncherObject(KEYBOARD_QUICK_SWITCH_RES_ID);

                return this;
            }
        }
    }

    /**
     * Focuses the next task in the Keyboard quick switch view.
     * <p>
     * Tasks are ordered left-to-right in LTR, and vice versa in RLT, in a carousel.
     * <ul>
     *      <li>If no task has been focused yet, and there is only one task, then that task will be
     *          focused</li>
     *      <li>If no task has been focused yet, and there are two or more tasks, then the second
     *          task will be focused</li>
     *      <li>If the currently-focused task is at the start of the list, the last task will be
     *          focused</li>
     * </ul>
     */
    public KeyboardQuickSwitch moveFocusBackward() {
        try (LauncherInstrumentation.Closable c1 = mLauncher.addContextLayer(
                "want to move keyboard quick switch focus backward");
             LauncherInstrumentation.Closable e = mLauncher.eventsCheck()) {
            mLauncher.waitForLauncherObject(KEYBOARD_QUICK_SWITCH_RES_ID);

            mLauncher.expectEvent(TestProtocol.SEQUENCE_MAIN, EVENT_ALT_SHIFT_TAB_UP);
            mLauncher.assertTrue("Failed to press alt+shift+tab",
                    mLauncher.getDevice().pressKeyCode(
                            KeyEvent.KEYCODE_TAB,
                            KeyEvent.META_ALT_ON | KeyEvent.META_SHIFT_ON));

            try (LauncherInstrumentation.Closable c2 = mLauncher.addContextLayer(
                    "pressed alt+shift+tab")) {
                mLauncher.waitForLauncherObject(KEYBOARD_QUICK_SWITCH_RES_ID);

                return this;
            }
        }
    }

    /**
     * Dismisses the Keyboard Quick Switch view without launching the focused task.
     * <p>
     * The device will return to the same state it started in before displaying the Keyboard Quick
     * Switch view.
     */
    public void dismiss() {
        try (LauncherInstrumentation.Closable c1 = mLauncher.addContextLayer(
                "want to dismiss keyboard quick switch view");
             LauncherInstrumentation.Closable e = mLauncher.eventsCheck()) {
            mLauncher.waitForLauncherObject(KEYBOARD_QUICK_SWITCH_RES_ID);

            mLauncher.expectEvent(TestProtocol.SEQUENCE_MAIN, EVENT_ALT_ESC_UP);
            mLauncher.assertTrue("Failed to press alt+tab",
                    mLauncher.getDevice().pressKeyCode(
                            KeyEvent.KEYCODE_ESCAPE, KeyEvent.META_ALT_ON));

            try (LauncherInstrumentation.Closable c2 = mLauncher.addContextLayer(
                    "pressed alt+esc")) {
                mLauncher.waitUntilLauncherObjectGone(KEYBOARD_QUICK_SWITCH_RES_ID);

                // Verify the final state is the same as the initial state
                mLauncher.verifyContainerType(mStartingContainerType);

                // Wait until the device has fully settled before unpressing the key code
                if (mIsHomeState) {
                    mLauncher.expectEvent(TestProtocol.SEQUENCE_MAIN, EVENT_HOME_ALT_LEFT_UP);
                }
                mLauncher.unpressKeyCode(KeyEvent.KEYCODE_ALT_LEFT, 0);
            }
        }
    }

    /**
     * Launches the currently-focused app task.
     * <p>
     * This method should only be used if the focused task is for a recent running app, otherwise
     * use {@link #launchFocusedOverviewTask()}.
     *
     * @param expectedPackageName the package name of the expected launched app
     */
    public LaunchedAppState launchFocusedAppTask(@NonNull String expectedPackageName) {
        try (LauncherInstrumentation.Closable e = mLauncher.eventsCheck()) {
            return (LaunchedAppState) launchFocusedTask(expectedPackageName);
        }
    }

    /**
     * Launches the currently-focused overview task.
     * <p>
     * This method only should be used if the focused task is for overview, otherwise use
     * {@link #launchFocusedAppTask(String)}.
     */
    public Overview launchFocusedOverviewTask() {
        try (LauncherInstrumentation.Closable e = mLauncher.eventsCheck()) {
            return (Overview) launchFocusedTask(null);
        }
    }

    private LauncherInstrumentation.VisibleContainer launchFocusedTask(
            @Nullable String expectedPackageName) {
        try (LauncherInstrumentation.Closable c1 = mLauncher.addContextLayer(
                "want to launch focused task: "
                        + (expectedPackageName == null ? "Overview" : expectedPackageName))) {
            mLauncher.expectEvent(TestProtocol.SEQUENCE_MAIN, EVENT_KQS_ALT_LEFT_UP);

            if (expectedPackageName == null || !mIsHomeState) {
                mLauncher.unpressKeyCode(KeyEvent.KEYCODE_ALT_LEFT, 0);
            } else {
                mLauncher.executeAndWaitForLauncherStop(
                        () -> mLauncher.unpressKeyCode(KeyEvent.KEYCODE_ALT_LEFT, 0),
                        "unpressing left alt");
            }

            try (LauncherInstrumentation.Closable c2 = mLauncher.addContextLayer(
                    "un-pressed left alt")) {
                mLauncher.waitUntilLauncherObjectGone(KEYBOARD_QUICK_SWITCH_RES_ID);

                if (expectedPackageName != null) {
                    mLauncher.assertAppLaunched(expectedPackageName);
                    return mLauncher.getLaunchedAppState();
                } else {
                    return mLauncher.getOverview();
                }
            }
        }
    }
}
