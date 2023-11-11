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
package com.android.launcher3.allapps;

import static com.android.launcher3.ui.TaplTestsLauncher3.initialize;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.view.KeyEvent;
import android.view.View;
import android.view.WindowInsets;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.launcher3.LauncherState;
import com.android.launcher3.tapl.HomeAllApps;
import com.android.launcher3.ui.AbstractLauncherUiTest;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class TaplKeyboardFocusTest extends AbstractLauncherUiTest {

    @Before
    public void setUp() throws Exception {
        super.setUp();
        initialize(this);
    }

    @Test
    public void testAllAppsFocusApp() {
        final HomeAllApps allApps = mLauncher.goHome().switchToAllApps();
        assertTrue("Launcher internal state is not All Apps",
                isInState(() -> LauncherState.ALL_APPS));
        allApps.freeze();
        try {
            mLauncher.pressAndHoldKeyCode(KeyEvent.KEYCODE_DPAD_DOWN, 0);
            executeOnLauncher(launcher -> assertNotNull("No focused child.",
                    launcher.getAppsView().getActiveRecyclerView().getApps().getFocusedChild()));
        } finally {
            allApps.unfreeze();
        }
    }

    @Test
    public void testAllAppsExitSearchAndFocusApp() {
        final HomeAllApps allApps = mLauncher.goHome().switchToAllApps();
        assertTrue("Launcher internal state is not All Apps",
                isInState(() -> LauncherState.ALL_APPS));
        allApps.freeze();
        try {
            executeOnLauncher(launcher -> launcher.getAppsView().getSearchView().requestFocus());
            waitForLauncherCondition("Search view does not have focus.",
                    launcher -> launcher.getAppsView().getSearchView().hasFocus());

            mLauncher.pressAndHoldKeyCode(KeyEvent.KEYCODE_DPAD_DOWN, 0);
            executeOnLauncher(launcher -> assertNotNull("No focused child.",
                    launcher.getAppsView().getActiveRecyclerView().getApps().getFocusedChild()));
        } finally {
            allApps.unfreeze();
        }
    }

    @Test
    public void testAllAppsExitSearchAndFocusSearchResults() {
        final HomeAllApps allApps = mLauncher.goHome().switchToAllApps();
        assertTrue("Launcher internal state is not All Apps",
                isInState(() -> LauncherState.ALL_APPS));
        allApps.freeze();
        try {
            executeOnLauncher(launcher -> launcher.getAppsView().getSearchView().requestFocus());
            waitForLauncherCondition("Search view does not have focus.",
                    launcher -> launcher.getAppsView().getSearchView().hasFocus());

            mLauncher.pressAndHoldKeyCode(KeyEvent.KEYCODE_C, 0);
            waitForLauncherCondition("Search view not active.",
                    launcher -> launcher.getAppsView().getActiveRecyclerView()
                            instanceof SearchRecyclerView);
            mLauncher.unpressKeyCode(KeyEvent.KEYCODE_C, 0);

            executeOnLauncher(launcher -> launcher.getAppsView().getSearchUiManager().getEditText()
                    .hideKeyboard(/* clearFocus= */ false));
            waitForLauncherCondition("Keyboard still visible.", launcher -> {
                View root = launcher.getDragLayer();
                WindowInsets insets = root.getRootWindowInsets();
                return insets != null && !insets.isVisible(WindowInsets.Type.ime());
            });

            mLauncher.pressAndHoldKeyCode(KeyEvent.KEYCODE_DPAD_DOWN, 0);
            mLauncher.unpressKeyCode(KeyEvent.KEYCODE_DPAD_DOWN, 0);
            waitForLauncherCondition("No focused child", launcher ->
                    launcher.getAppsView().getActiveRecyclerView().getApps().getFocusedChild()
                            != null);
        } finally {
            allApps.unfreeze();
        }
    }
}
