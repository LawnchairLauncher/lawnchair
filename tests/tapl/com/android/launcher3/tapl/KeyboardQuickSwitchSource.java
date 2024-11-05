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

/**
 * {@link com.android.launcher3.tapl.LauncherInstrumentation.VisibleContainer} that can be used to
 * show the keyboard quick switch view.
 */
interface KeyboardQuickSwitchSource {

    /**
     * Shows the Keyboard Quick Switch view.
     */
    default KeyboardQuickSwitch showQuickSwitchView() {
        LauncherInstrumentation launcher = getLauncher();

        try (LauncherInstrumentation.Closable c1 = launcher.addContextLayer(
                "want to show keyboard quick switch object");
             LauncherInstrumentation.Closable e = launcher.eventsCheck()) {
            launcher.pressAndHoldKeyCode(KeyEvent.KEYCODE_TAB, KeyEvent.META_ALT_LEFT_ON);

            try (LauncherInstrumentation.Closable c2 = launcher.addContextLayer(
                    "press and held alt+tab")) {
                launcher.waitForLauncherObject(KEYBOARD_QUICK_SWITCH_RES_ID);
                launcher.unpressKeyCode(KeyEvent.KEYCODE_TAB, 0);

                return new KeyboardQuickSwitch(
                        launcher, getStartingContainerType(), isHomeState());
            }
        }
    }

    /** This method requires public access, however should not be called in tests. */
    LauncherInstrumentation getLauncher();

    /** This method requires public access, however should not be called in tests. */
    LauncherInstrumentation.ContainerType getStartingContainerType();

    /** This method requires public access, however should not be called in tests. */
    boolean isHomeState();
}
