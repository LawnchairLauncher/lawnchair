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

import static com.android.launcher3.tapl.LauncherInstrumentation.eventListToString;
import static com.android.launcher3.testing.shared.TestProtocol.OVERVIEW_MODAL_TASK_STATE_ORDINAL;
import static com.android.launcher3.testing.shared.TestProtocol.OVERVIEW_SPLIT_SELECT_ORDINAL;

import androidx.annotation.NonNull;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiObject2;

import com.android.launcher3.testing.shared.TestProtocol;

import java.util.ArrayList;
import java.util.List;

/** Represents the menu of an overview task. */
public class OverviewTaskMenu {

    private final LauncherInstrumentation mLauncher;
    private final UiObject2 mMenu;

    OverviewTaskMenu(LauncherInstrumentation launcher) {
        mLauncher = launcher;

        mMenu = mLauncher.waitForLauncherObject("menu_option_layout");
        mLauncher.assertTrue("The overview task menus is not visible",
                !mMenu.getVisibleBounds().isEmpty());
    }

    /** Taps the split menu item from the overview task menu. */
    @NonNull
    public SplitScreenSelect tapSplitMenuItem() {
        try (LauncherInstrumentation.Closable e = mLauncher.eventsCheck();
             LauncherInstrumentation.Closable c = mLauncher.addContextLayer(
                     "tap split menu item")) {
            boolean[] isSplitState = new boolean[]{false};
            boolean[] isDismissEnded = new boolean[]{false};
            final List<Integer> actualEvents = new ArrayList<>();
            mLauncher.executeAndWaitForLauncherEvent(
                    () -> mLauncher.clickLauncherObject(
                            mLauncher.findObjectInContainer(mMenu, By.textStartsWith("Split"))),
                    event -> {
                        // Wait for state changed to Split Select.
                        if (!isSplitState[0] && mLauncher.isSwitchToStateEvent(event,
                                OVERVIEW_SPLIT_SELECT_ORDINAL, actualEvents)) {
                            isSplitState[0] = true;
                        }

                        // Wait for dismiss animation to end.
                        if (!isDismissEnded[0]
                                && TestProtocol.DISMISS_ANIMATION_ENDS_MESSAGE.equals(
                                event.getClassName())) {
                            isDismissEnded[0] = true;
                        }

                        return isSplitState[0] && isDismissEnded[0];
                    },
                    () -> {
                        StringBuilder failureMessage = new StringBuilder();
                        if (!isSplitState[0]) {
                            failureMessage.append(
                                    "Failed to receive event for state change to Split Select. "
                                            + "Actual events: ").append(
                                    eventListToString(actualEvents));
                        }
                        if (!isDismissEnded[0]) {
                            failureMessage.append(
                                    "Failed to receive dismiss animation ends message.");
                        }
                        return failureMessage.toString();
                    },
                    "tapping split menu item");

            try (LauncherInstrumentation.Closable c1 = mLauncher.addContextLayer(
                    "tapped split menu item")) {
                return new SplitScreenSelect(mLauncher);
            }
        }
    }

    /**
     * Taps the app info item from the overview task menu and returns the LaunchedAppState
     * representing the App info settings page.
     */
    @NonNull
    public LaunchedAppState tapAppInfoMenuItem() {
        try (LauncherInstrumentation.Closable e = mLauncher.eventsCheck();
             LauncherInstrumentation.Closable c = mLauncher.addContextLayer(
                     "before tapping the app info menu item")) {
            mLauncher.executeAndWaitForLauncherStop(
                    () -> mLauncher.clickLauncherObject(
                            mLauncher.findObjectInContainer(mMenu, By.text("App info"))),
                    "tapped app info menu item");

            try (LauncherInstrumentation.Closable c1 = mLauncher.addContextLayer(
                    "tapped app info menu item")) {
                mLauncher.waitUntilSystemLauncherObjectGone("overview_panel");
                return new LaunchedAppState(mLauncher);
            }
        }
    }

    /** Taps the select menu item from the overview task menu. */
    @NonNull
    public SelectModeButtons tapSelectMenuItem() {
        try (LauncherInstrumentation.Closable e = mLauncher.eventsCheck();
             LauncherInstrumentation.Closable c = mLauncher.addContextLayer(
                     "before tapping the select menu item")) {

            mLauncher.runToState(
                    () -> mLauncher.clickLauncherObject(
                            mLauncher.findObjectInContainer(mMenu, By.text("Select"))),
                    OVERVIEW_MODAL_TASK_STATE_ORDINAL, "tapping select menu item");

            try (LauncherInstrumentation.Closable c1 = mLauncher.addContextLayer(
                    "select menu item opened")) {
                return new SelectModeButtons(mLauncher);
            }
        }
    }

    /**
     * Taps the Desktop item from the overview task menu and returns the LaunchedAppState
     * representing the Desktop.
     */
    @NonNull
    public LaunchedAppState tapDesktopMenuItem() {
        try (LauncherInstrumentation.Closable ignored = mLauncher.eventsCheck();
             LauncherInstrumentation.Closable ignored1 = mLauncher.addContextLayer(
                     "before tapping the desktop menu item")) {
            mLauncher.executeAndWaitForLauncherStop(
                    () -> mLauncher.clickLauncherObject(
                            mLauncher.findObjectInContainer(mMenu, By.text("Desktop"))),
                    "tapped desktop menu item");

            try (LauncherInstrumentation.Closable ignored2 = mLauncher.addContextLayer(
                    "tapped desktop menu item")) {
                mLauncher.waitUntilSystemLauncherObjectGone("overview_panel");
                mLauncher.waitForSystemUiObject("desktop_mode_caption");
                return new LaunchedAppState(mLauncher);
            }
        }
    }

    /** Returns true if an item matching the given string is present in the menu. */
    public boolean hasMenuItem(String expectedMenuItemText) {
        UiObject2 menuItem = mLauncher.findObjectInContainer(mMenu, By.text(expectedMenuItemText));
        return menuItem != null;
    }

    /**
     * Taps outside task menu to dismiss it.
     */
    public void touchOutsideTaskMenuToDismiss() {
        mLauncher.touchOutsideContainer(mMenu, false);
    }
}
